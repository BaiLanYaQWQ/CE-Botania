package net.bailanya.cebotania.bauble;

import net.bailanya.cebotania.platform.BotaniaItems;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

/** 玩家数据保存两枚戒指的完整物品堆。槽位编号为 0 和 1。 */
public final class BaubleEquipment implements Listener {

    public static final String MAGNET_RING = "botania:magnet_ring";
    public static final String GREATER_MAGNET_RING = "botania:magnet_ring_greater";

    private static final NamespacedKey RINGS = new NamespacedKey("cebotania", "bauble_rings");
    private static final NamespacedKey[] SLOTS = {
            new NamespacedKey("cebotania", "ring_0"),
            new NamespacedKey("cebotania", "ring_1")
    };

    private static final NamespacedKey[] SLOT_IDS = {
            new NamespacedKey("cebotania", "ring_0_id"),
            new NamespacedKey("cebotania", "ring_1_id")
    };

    public static boolean isRing(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isRingId(BotaniaItems.idOf(stack));
    }

    public ItemStack get(Player player, int slot) {
        var key = slotKey(slot);
        return read(player.getPersistentDataContainer().get(RINGS, PersistentDataType.TAG_CONTAINER), key);
    }

    public void set(Player player, int slot, ItemStack stack) {
        var key = slotKey(slot);
        byte[] encoded = null;
        String id = null;
        if (stack != null && !stack.isEmpty()) {
            id = BotaniaItems.idOf(stack);
            if (stack.getAmount() != 1 || !isRingId(id)) {
                throw new IllegalArgumentException("戒指槽只接受一枚磁化戒指或高级磁化戒指");
            }
            encoded = stack.serializeAsBytes();
        }

        var data = player.getPersistentDataContainer();
        var rings = data.get(RINGS, PersistentDataType.TAG_CONTAINER);
        if (rings == null) {
            if (encoded == null) {
                return;
            }
            rings = data.getAdapterContext().newPersistentDataContainer();
        }
        if (encoded == null) {
            rings.remove(key);
            rings.remove(SLOT_IDS[slot]);
        } else {
            rings.set(key, PersistentDataType.BYTE_ARRAY, encoded);
            rings.set(SLOT_IDS[slot], PersistentDataType.STRING, id);
        }
        if (rings.isEmpty()) {
            data.remove(RINGS);
        } else {
            data.set(RINGS, PersistentDataType.TAG_CONTAINER, rings);
        }
    }

    public boolean hasEquipped(Player player, String id) {
        var rings = player.getPersistentDataContainer().get(RINGS, PersistentDataType.TAG_CONTAINER);
        if (rings == null || !isRingId(id)) {
            return false;
        }
        for (int slot = 0; slot < SLOTS.length; slot++) {
            if (id.equals(rings.get(SLOT_IDS[slot], PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRingId(String id) {
        return MAGNET_RING.equals(id) || GREATER_MAGNET_RING.equals(id);
    }

    private static NamespacedKey slotKey(int slot) {
        if (slot < 0 || slot >= SLOTS.length) {
            throw new IndexOutOfBoundsException(slot);
        }
        return SLOTS[slot];
    }

    private static ItemStack read(PersistentDataContainer rings, NamespacedKey key) {
        if (rings == null) {
            return null;
        }
        byte[] bytes = rings.get(key, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        var data = event.getEntity().getPersistentDataContainer();
        var rings = data.get(RINGS, PersistentDataType.TAG_CONTAINER);
        if (rings == null) {
            return;
        }
        var equipped = new ArrayList<ItemStack>(SLOTS.length);
        for (var key : SLOTS) {
            var item = read(rings, key);
            if (item != null) {
                equipped.add(item);
            }
        }
        // 先完整读取再交换所有权，解码错误时保留原玩家数据。
        var drops = event.getDrops();
        var firstDrop = drops.size();
        drops.addAll(equipped);
        try {
            data.remove(RINGS);
        } catch (RuntimeException failure) {
            drops.subList(firstDrop, drops.size()).clear();
            throw failure;
        }
    }
}
