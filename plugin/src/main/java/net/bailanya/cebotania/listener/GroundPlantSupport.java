package net.bailanya.cebotania.listener;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 花床没了以后把花弹下来。
 *
 * <p>CE 虽然把 {@code canSurvive} 注入进了原版方块逻辑，但我们的花挂在
 * 甘蔗/玻璃这些<b>载体方块</b>上，邻居更新走的是载体自己的逻辑，实测并不可靠。
 * 所以这里在"下方方块被破坏"时兜一刀：延迟一 tick 检查花还站不站得住，
 * 站不住就抹掉方块并按规则掉东西。</p>
 */
public final class GroundPlantSupport implements Listener {

    /** 能当花床的方块（和 GroundPlantBehavior 保持一致）。 */
    private static final Set<Material> SOIL = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM,
            Material.MOSS_BLOCK, Material.MUD, Material.FARMLAND,
            Material.DIRT_PATH, Material.MUDDY_MANGROVE_ROOTS,
            Material.SOUL_SOIL, Material.CLAY
    );

    private final JavaPlugin plugin;

    public GroundPlantSupport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        scheduleCheck(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            scheduleCheck(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            scheduleCheck(block);
        }
    }

    /** 下一 tick 再看：方块此刻还没真的消失。 */
    private void scheduleCheck(Block broken) {
        Block above = broken.getRelative(BlockFace.UP);
        if (!CraftEngineBlocks.isCustomBlock(above)) {
            return;
        }
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> dropIfUnsupported(above));
    }

    private void dropIfUnsupported(Block flower) {
        if (!CraftEngineBlocks.isCustomBlock(flower)) {
            return;
        }
        Block below = flower.getRelative(BlockFace.DOWN);
        if (SOIL.contains(below.getType())) {
            return;
        }
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(flower);
        if (state == null || state.isEmpty()) {
            return;
        }
        String id = state.owner().value().id().asString();
        if (!id.contains(":")) {
            return;
        }
        var location = flower.getLocation().add(0.5, 0.2, 0.5);
        CraftEngineBlocks.remove(flower);
        if (id.endsWith("_mystical_flower") || id.endsWith("_shiny_flower")
                || id.endsWith("_double_flower") || id.endsWith("_mushroom")) {
            // 野花掉花瓣，和其他路径一致
            String path = id.substring(id.indexOf(':') + 1);
            String color = path.contains("_")
                    ? path.substring(0, path.indexOf('_')) : path;
            int count = 2 + ThreadLocalRandom.current().nextInt(2);
            ItemStack petal = BotaniaItems.create("botania:" + color + "_petal", count);
            if (petal != null) {
                flower.getWorld().dropItemNaturally(location, petal);
            }
        } else {
            // 功能花掉自己
            ItemStack item = BotaniaItems.create(id, 1);
            if (item != null) {
                flower.getWorld().dropItemNaturally(location, item);
            }
        }
    }
}
