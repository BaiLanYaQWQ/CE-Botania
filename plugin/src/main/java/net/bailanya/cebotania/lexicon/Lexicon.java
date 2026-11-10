package net.bailanya.cebotania.lexicon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 植物魔法辞典：索引界面 + 成书阅读。
 *
 * <p>结构分两层：</p>
 * <ul>
 *   <li><b>索引</b>用箱子界面 —— 11 个章节按钮 + 当前章节的词条列表（图标 + 标题），
 *       每页放 28 个词条</li>
 *   <li><b>正文</b>用原版成书 —— 客户端原生翻页，一段文字一页，玩家自己拿着看</li>
 * </ul>
 *
 * <p>数据来自 {@code data/lexicon/lexicon.json}，由 {@code tools/gen_lexicon.py}
 * 从上游 Patchouli 格式转换而来（246 个词条 / 细节见 docs/植物魔法辞典方案.md）。</p>
 */
public final class Lexicon {

    /** 一页显示多少个词条。 */
    private static final int ENTRIES_PER_PAGE = 36;
    /** 章节按钮占第一行。 */
    private static final int CATEGORY_ROW = 0;

    public record Entry(String id, String title, String icon, List<String> pages) {
    }

    public record Category(String id, String name, String icon, List<Entry> entries) {
    }

    private final List<Category> categories = new ArrayList<>();

    public List<Category> categories() {
        return this.categories;
    }

    // —— 加载 ——

    public static Lexicon load(ClassLoader loader) {
        Lexicon lexicon = new Lexicon();
        try (InputStream in = loader.getResourceAsStream("data/lexicon/lexicon.json")) {
            if (in == null) {
                return lexicon;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("categories")) {
                JsonObject category = element.getAsJsonObject();
                List<Entry> entries = new ArrayList<>();
                for (JsonElement e : category.getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    List<String> pages = new ArrayList<>();
                    for (JsonElement p : entry.getAsJsonArray("pages")) {
                        pages.add(p.getAsJsonObject().get("text").getAsString());
                    }
                    entries.add(new Entry(
                            entry.get("id").getAsString(),
                            entry.get("title").getAsString(),
                            entry.get("icon").getAsString(),
                            pages));
                }
                lexicon.categories.add(new Category(
                        category.get("id").getAsString(),
                        category.get("name").getAsString(),
                        category.get("icon").getAsString(),
                        entries));
            }
        } catch (Exception e) {
            // 数据坏了不影响服务器启动，只是辞典打不开
            Bukkit.getLogger().warning("[植物魔法] 辞典数据加载失败：" + e);
        }
        return lexicon;
    }

    public String summary() {
        int entries = this.categories.stream().mapToInt(c -> c.entries().size()).sum();
        int pages = this.categories.stream()
                .flatMap(c -> c.entries().stream())
                .mapToInt(e -> e.pages().size()).sum();
        return "辞典 " + this.categories.size() + " 章 / " + entries + " 词条 / " + pages + " 页";
    }

    // —— 界面 ——

    /** 第一级：章节菜单。 */
    public void openCategories(Player player) {
        if (this.categories.isEmpty()) {
            player.sendMessage(net.bailanya.cebotania.platform.Platform.mm(
                    "<red>辞典数据没有加载（缺 data/lexicon/lexicon.json）"));
            return;
        }
        Holder holder = new Holder(this, -1, 0);
        Inventory inv = Bukkit.createInventory(holder, 54,
                net.bailanya.cebotania.platform.Platform.mm(
                        "<!i><white>植物魔法辞典 · <yellow>目录"));
        holder.inventory = inv;

        // 章节按 3 行居中摆（11 个章节）
        int index = 0;
        for (int row = 1; row <= 3 && index < this.categories.size(); row++) {
            for (int col = 0; col < 9 && index < this.categories.size(); col++) {
                if (row == 1 && (col < 3 || col > 5)) {
                    continue;  // 第一行只放中间三格，视觉上居中
                }
                Category c = this.categories.get(index);
                inv.setItem(row * 9 + col, iconItem(c.icon(), c.name(),
                        "<gray>共 " + c.entries().size() + " 个词条<dark_gray>（点击展开）"));
                index++;
            }
        }
        inv.setItem(49, iconItem("minecraft:barrier", "关闭", "<gray>点击关闭辞典"));
        player.openInventory(inv);
    }

    /** 第二级：某个章节的词条列表。 */
    public void open(Player player, int categoryIndex, int page) {
        if (this.categories.isEmpty()) {
            player.sendMessage(net.bailanya.cebotania.platform.Platform.mm(
                    "<red>辞典数据没有加载（缺 data/lexicon/lexicon.json）"));
            return;
        }
        int catIndex = Math.max(0, Math.min(this.categories.size() - 1, categoryIndex));
        Category category = this.categories.get(catIndex);
        int pageCount = Math.max(1, (category.entries().size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
        int pageIndex = Math.max(0, Math.min(pageCount - 1, page));

        Holder holder = new Holder(this, catIndex, pageIndex);
        Inventory inv = Bukkit.createInventory(holder, 54,
                net.bailanya.cebotania.platform.Platform.mm(
                        "<!i><white>植物魔法辞典 · <yellow>" + category.name()));
        holder.inventory = inv;

        // 词条列表：整 4 行（第 1~4 行）
        int start = pageIndex * ENTRIES_PER_PAGE;
        int slot = 0;
        for (int i = start; i < category.entries().size() && slot < 36; i++, slot++) {
            Entry entry = category.entries().get(i);
            inv.setItem(slot, iconItem(entry.icon(), entry.title(),
                    "<gray>" + entry.title() + "<dark_gray>（" + entry.pages().size() + " 页）"));
        }
        // 底部：返回 / 上一页 / 下一页
        inv.setItem(45, iconItem("minecraft:arrow", "返回目录", "<gray>回到章节选择"));
        if (pageIndex > 0) {
            inv.setItem(48, iconItem("minecraft:arrow", "上一页", "<gray>第 "
                    + (pageIndex + 1) + "/" + pageCount + " 页"));
        }
        if (pageIndex + 1 < pageCount) {
            inv.setItem(50, iconItem("minecraft:arrow", "下一页", "<gray>第 "
                    + (pageIndex + 1) + "/" + pageCount + " 页"));
        }
        inv.setItem(49, iconItem("minecraft:barrier", "关闭", "<gray>点击关闭辞典"));
        player.openInventory(inv);
    }

    /** 把一个词条渲染成成书交给玩家。 */
    public void openBook(Player player, Entry entry) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setTitle("植物魔法 · " + entry.title());
        meta.setAuthor("Vazkii");
        List<String> pages = new ArrayList<>(entry.pages());
        // 最后一页加个返回提示
        pages.add("§l返回目录\n\n§r再次右键手中的植物魔法辞典\n即可重新打开索引。");
        meta.setPages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private static ItemStack iconItem(String id, String name, String lore) {
        ItemStack stack = net.bailanya.cebotania.platform.BotaniaItems.create(id, 1);
        if (stack == null) {
            stack = new ItemStack(Material.BOOK);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(net.bailanya.cebotania.platform.Platform.mm("<!i><white>" + name));
            meta.lore(List.of(net.bailanya.cebotania.platform.Platform.mm("<!i>" + lore)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 索引界面的标记，用来认出"这个箱子是我们的"。 */
    public static final class Holder implements InventoryHolder {
        final Lexicon lexicon;
        final int categoryIndex;
        final int page;
        Inventory inventory;

        Holder(Lexicon lexicon, int categoryIndex, int page) {
            this.lexicon = lexicon;
            this.categoryIndex = categoryIndex;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
