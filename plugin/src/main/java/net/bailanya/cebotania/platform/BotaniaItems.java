package net.bailanya.cebotania.platform;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * CraftEngine 物品 ↔ Bukkit 物品堆的唯一转换点。
 *
 * <p>插件内部一律用 {@code botania:xxx} 这样的 id 说话，只有真正要跟玩家
 * 背包、世界打交道时才在这里变成 ItemStack。</p>
 */
public final class BotaniaItems {

    private BotaniaItems() {
    }

    /** 物品堆的 id：自定义物品取注册 id，原版物品取 material 键。 */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "";
        }
        Key custom = CraftEngineItems.getCustomItemId(stack);
        return custom != null ? custom.asString() : stack.getType().getKey().toString();
    }

    /** id → 物品堆；资源包与 Paper 都不认识就返回 null。 */
    public static ItemStack create(String id, int count) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        BukkitItemDefinition definition = CraftEngineItems.byId(id);
        if (definition != null) {
            ItemStack stack = definition.buildBukkitItem();
            stack.setAmount(Math.max(1, count));
            return stack;
        }
        Material material = Material.matchMaterial(id);
        return material == null ? null : new ItemStack(material, Math.max(1, count));
    }

    public static ItemStack create(String id) {
        return create(id, 1);
    }

    /** 判断物品堆是否是给定 id 之一。 */
    public static boolean isAny(ItemStack stack, String... ids) {
        String actual = idOf(stack);
        if (actual.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            if (actual.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 物品的显示名。
     *
     * <p>不能读 ItemStack 的 displayName —— CE 的物品名是 {@code <lang:...>} 翻译键，
     * 翻译在客户端做，服务端读到的是原始字符串。查插件自己的语言表才拿得到中文。</p>
     */
    public static String displayName(String id) {
        String name = net.bailanya.cebotania.i18n.I18n.displayName(id);
        if (name == null || name.isEmpty() || name.equals(id)) {
            ItemStack stack = create(id);
            if (stack != null) {
                var meta = stack.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    try {
                        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(meta.displayName());
                    } catch (Throwable ignored) {
                        // 退回到 id
                    }
                }
            }
        }
        return name;
    }

    /** 森林法杖（Botania 的 Wand of the Forest）—— 查看魔力要用它点。 */
    public static final String WAND = "botania:twig_wand";

    private static int wandDebugBudget = 10;

    /** 玩家主手是不是拿着森林法杖。 */
    public static boolean holdingWand(org.bukkit.entity.Player player) {
        if (player == null) {
            return false;
        }
        String actual = idOf(player.getInventory().getItemInMainHand());
        boolean ok = WAND.equals(actual);
        if (!ok && wandDebugBudget-- > 0) {
            // 诊断：手上到底拿的是什么 id
            java.util.logging.Logger.getLogger("CeBotania").info(
                    "[诊断] 法杖判定失败：手上是 " + actual + "（期望 " + WAND + "）");
        }
        return ok;
    }

    /**
     * 给聊天/actionbar 用的名字：自定义物品查插件语言表，原版物品交给客户端翻译。
     *
     * <p>原版物品（{@code minecraft:white_wool} 这类）没有我们的语言键，
     * 直接甩 id 很难看。这里返回 MiniMessage 的 {@code <lang:...>} 标签，
     * 客户端会用自己的语言渲染成「白色羊毛」。</p>
     */
    public static String displayNameForMessage(String id) {
        String name = net.bailanya.cebotania.i18n.I18n.displayName(id);
        if (name != null && !name.equals(id)) {
            return name;
        }
        if (id.startsWith("minecraft:")) {
            Material material = Material.matchMaterial(id);
            if (material != null) {
                return "<lang:" + material.translationKey() + ">";
            }
        }
        return id;
    }

    /** {@code botania:white_petal} 这种 id 的路径部分。 */
    public static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }
}
