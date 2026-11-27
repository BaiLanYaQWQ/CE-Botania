package net.bailanya.cebotania.bauble;

import net.bailanya.cebotania.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

/** 双戒指菜单。玩家数据保存戒指，菜单里的物品只用于显示。 */
public final class BaubleMenu implements CommandExecutor, Listener {

    private static final int CHESTPLATE_RAW_SLOT = 6;
    private static final int FIRST_RING_SLOT = 3;
    private static final int SECOND_RING_SLOT = 5;
    private static final int MENU_SIZE = 9;

    private final JavaPlugin plugin;
    private final BaubleEquipment equipment;

    public BaubleMenu(JavaPlugin plugin, BaubleEquipment equipment) {
        this.plugin = plugin;
        this.equipment = equipment;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家能打开饰品栏。");
            return true;
        }
        if (args.length != 0) {
            return false;
        }
        open(player);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        var view = event.getView();
        if (view.getTopInventory().getHolder(false) instanceof Menu menu) {
            clickMenu(player, event, menu);
            return;
        }

        if (view.getType() != InventoryType.CRAFTING
                || event.getRawSlot() != CHESTPLATE_RAW_SLOT
                || event.getSlotType() != InventoryType.SlotType.ARMOR
                || event.getClick() != ClickType.RIGHT
                || !isEmpty(view.getCursor())) {
            return;
        }
        event.setCancelled(true);
        // 打开新容器需等当前背包点击处理完。
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && player.getOpenInventory() == view && isEmpty(view.getCursor())) {
                open(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu
                && event.getRawSlots().stream().anyMatch(slot -> slot < MENU_SIZE)) {
            event.setCancelled(true);
        }
    }

    private void clickMenu(Player player, InventoryClickEvent event, Menu menu) {
        int rawSlot = event.getRawSlot();
        if (!menu.owner.equals(player.getUniqueId())
                || rawSlot < 0
                || event.isShiftClick()
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        try {
            if (rawSlot < MENU_SIZE) {
                event.setCancelled(true);
                if (isEmptyCursorClick(event)) {
                    if (rawSlot == FIRST_RING_SLOT) {
                        takeOff(player, menu, 0, rawSlot);
                    } else if (rawSlot == SECOND_RING_SLOT) {
                        takeOff(player, menu, 1, rawSlot);
                    }
                }
            } else if (isEmptyCursorClick(event) && BaubleEquipment.isRing(event.getCurrentItem())) {
                event.setCancelled(true);
                putOn(player, menu, event.getSlot(), event.getCurrentItem());
            }
        } catch (RuntimeException failure) {
            this.plugin.getLogger().log(Level.WARNING, "无法保存玩家饰品 " + player.getUniqueId(), failure);
            player.sendMessage(Platform.mm("<red>暂时无法保存饰品，请稍后再试。"));
        }
    }

    private static boolean isEmptyCursorClick(InventoryClickEvent event) {
        return (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)
                && isEmpty(event.getView().getCursor());
    }

    private void putOn(Player player, Menu menu, int inventorySlot, ItemStack item) {
        int ringSlot = isEmpty(this.equipment.get(player, 0)) ? 0 : 1;
        if (!isEmpty(this.equipment.get(player, ringSlot))) {
            player.sendMessage(Platform.mm("<red>两个戒指槽都已装备。"));
            return;
        }
        if (item.getAmount() != 1) {
            player.sendMessage(Platform.mm("<red>请一次只装备一枚戒指。"));
            return;
        }
        this.equipment.set(player, ringSlot, item);
        player.getInventory().setItem(inventorySlot, null);
        renderSlot(menu.inventory, ringSlot == 0 ? FIRST_RING_SLOT : SECOND_RING_SLOT, item);
    }

    private void takeOff(Player player, Menu menu, int ringSlot, int rawSlot) {
        ItemStack item = this.equipment.get(player, ringSlot);
        if (isEmpty(item)) {
            return;
        }
        int destination = player.getInventory().firstEmpty();
        if (destination == -1) {
            player.sendMessage(Platform.mm("<red>背包已满，磁化指环仍在饰品栏。"));
            return;
        }
        this.equipment.set(player, ringSlot, null);
        player.getInventory().setItem(destination, item);
        renderSlot(menu.inventory, rawSlot, null);
    }

    private void open(Player player) {
        ItemStack first;
        ItemStack second;
        try {
            first = this.equipment.get(player, 0);
            second = this.equipment.get(player, 1);
        } catch (RuntimeException failure) {
            this.plugin.getLogger().log(Level.WARNING, "无法读取玩家饰品 " + player.getUniqueId(), failure);
            player.sendMessage(Platform.mm("<red>暂时无法读取饰品，请稍后再试。"));
            return;
        }

        var menu = new Menu(player.getUniqueId());
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            menu.inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, "<gray>饰品"));
        }
        renderSlot(menu.inventory, FIRST_RING_SLOT, first);
        renderSlot(menu.inventory, SECOND_RING_SLOT, second);
        player.openInventory(menu.inventory);
    }

    private static void renderSlot(Inventory inventory, int rawSlot, ItemStack equipped) {
        // 保留与戒指不兼容的占位物品，阻止快速拖放进入空戒指槽。
        inventory.setItem(rawSlot, isEmpty(equipped)
                ? pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        rawSlot == FIRST_RING_SLOT ? "<gray>磁化指环槽 1" : "<gray>磁化指环槽 2")
                : equipped.clone());
    }

    private static ItemStack pane(Material material, String name) {
        var item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Platform.mm("<!italic>" + name)));
        return item;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static final class Menu implements InventoryHolder {
        private final UUID owner;
        private final Inventory inventory;

        private Menu(UUID owner) {
            this.owner = owner;
            this.inventory = Bukkit.createInventory(this, MENU_SIZE, Platform.mm("<dark_green>植物魔法 · 饰品"));
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
