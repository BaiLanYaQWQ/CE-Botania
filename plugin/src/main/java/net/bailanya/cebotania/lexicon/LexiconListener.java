package net.bailanya.cebotania.lexicon;

import net.bailanya.cebotania.platform.BotaniaItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 辞典的入口与索引界面点击。
 *
 * <p>手持 {@code botania:lexicon} 右键打开索引；在索引里点章节换分类、点词条开成书、
 * 点箭头翻页。</p>
 */
public final class LexiconListener implements Listener {

    private static final String LEXICON_ID = "botania:lexicon";

    private final Lexicon lexicon;
    /** 玩家上次停留的章节，重新打开时回到原处。 */
    private final Map<UUID, Integer> lastCategory = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastPage = new ConcurrentHashMap<>();

    public LexiconListener(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!LEXICON_ID.equals(BotaniaItems.idOf(held))) {
            return;
        }
        event.setCancelled(true);
        this.lexicon.openCategories(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Lexicon.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        // 第一级：章节菜单
        if (holder.categoryIndex < 0) {
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            // 章节格子：从第 2 行中间三格开始，逐个往后排
            int index = chapterIndexAt(slot);
            if (index >= 0 && index < this.lexicon.categories().size()) {
                this.lastCategory.put(player.getUniqueId(), index);
                this.lexicon.open(player, index, 0);
            }
            return;
        }

        Lexicon.Category category = this.lexicon.categories().get(holder.categoryIndex);
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            this.lexicon.openCategories(player);
            return;
        }
        if (slot == 48) {
            this.lexicon.open(player, holder.categoryIndex, holder.page - 1);
            return;
        }
        if (slot == 50) {
            this.lexicon.open(player, holder.categoryIndex, holder.page + 1);
            return;
        }
        if (slot >= 0 && slot < 36) {
            int index = holder.page * 36 + slot;
            if (index >= 0 && index < category.entries().size()) {
                this.lastPage.put(player.getUniqueId(), holder.page);
                this.lexicon.openBook(player, category.entries().get(index));
            }
        }
    }


    /** 第一级界面里，格子 → 章节序号（和 Lexicon.openCategories 的摆放规则一致）。 */
    private static int chapterIndexAt(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 3) {
            return -1;
        }
        if (row == 1 && (col < 3 || col > 5)) {
            return -1;
        }
        int base = (row - 1) * 7;
        if (row == 1) {
            return col - 3;
        }
        return 3 + (row - 2) * 9 + col;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.lastCategory.remove(event.getPlayer().getUniqueId());
        this.lastPage.remove(event.getPlayer().getUniqueId());
    }
}
