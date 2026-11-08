package net.bailanya.cebotania.listener;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 花瓣的来源：打掉神秘花（自定义方块）或原版小花，掉同色花瓣。
 *
 * <p>上游里神秘花被破坏掉 2~3 个花瓣，花瓣是花药台的唯一原料。
 * 只有"花"才掉花瓣：原版小花（蒲公英、虞美人这些）和本模组的神秘花。
 * 矮草、蕨这类不算花，打掉不掉东西 —— 别把它们当花瓣来源。</p>
 */
public final class PetalDropListener implements Listener {

    /** 原版花 → 花瓣颜色。 */
    private static final Map<Material, String> VANILLA_FLOWERS = Map.ofEntries(
            Map.entry(Material.DANDELION, "yellow"),
            Map.entry(Material.POPPY, "red"),
            Map.entry(Material.BLUE_ORCHID, "light_blue"),
            Map.entry(Material.ALLIUM, "magenta"),
            Map.entry(Material.AZURE_BLUET, "light_gray"),
            Map.entry(Material.RED_TULIP, "red"),
            Map.entry(Material.ORANGE_TULIP, "orange"),
            Map.entry(Material.WHITE_TULIP, "white"),
            Map.entry(Material.PINK_TULIP, "pink"),
            Map.entry(Material.OXEYE_DAISY, "white"),
            Map.entry(Material.CORNFLOWER, "blue"),
            Map.entry(Material.LILY_OF_THE_VALLEY, "white"),
            Map.entry(Material.WITHER_ROSE, "black"),
            Map.entry(Material.TORCHFLOWER, "orange")
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String color = colorOf(block);
        if (color == null) {
            return;
        }
        // 剪刀剪野花：整朵花带回家（原版就能这么干，用来做花坛/移植）
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getType() == Material.SHEARS) {
            String id = flowerId(block);
            if (id != null) {
                ItemStack whole = BotaniaItems.create(id, 1);
                if (whole != null) {
                    block.getWorld().dropItemNaturally(
                            block.getLocation().add(0.5, 0.3, 0.5), whole);
                }
            }
            return;
        }
        int count = 2 + ThreadLocalRandom.current().nextInt(2);
        ItemStack petal = BotaniaItems.create("botania:" + color + "_petal", count);
        if (petal == null) {
            return;
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.3, 0.5), petal);
    }

    /** 方块本身的 id（剪刀采集用）。 */
    private static String flowerId(Block block) {
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.isEmpty()) {
            return null;
        }
        return state.owner().value().id().asString();
    }

    /** 这个方块属于哪种花瓣颜色；不是"花"就返回 null。 */
    private static String colorOf(Block block) {
        // 先看是不是 CE 的自定义花（botania:xxx_mystical_flower 等）
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state != null && !state.isEmpty()) {
            String id = state.owner().value().id().asString();
            if (id.startsWith("botania:")) {
                String path = id.substring("botania:".length());
                if (path.endsWith("_mystical_flower")) {
                    return path.substring(0, path.length() - "_mystical_flower".length());
                }
                if (path.endsWith("_shiny_flower")) {
                    return path.substring(0, path.length() - "_shiny_flower".length());
                }
                if (path.endsWith("_double_flower")) {
                    return path.substring(0, path.length() - "_double_flower".length());
                }
                if (path.endsWith("_mushroom")) {
                    return path.substring(0, path.length() - "_mushroom".length());
                }
            }
            return null;
        }
        return VANILLA_FLOWERS.get(block.getType());
    }
}
