package net.bailanya.cebotania.block;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

/**
 * "只能种在土上"的方块：花、蘑菇这类贴地植物。
 *
 * <p>原版的花只能长在草方块/泥土/灰化土这类"土壤"上，这里用一个
 * {@code canSurvive} 钩子实现：CE 会在放置和邻居变化时问这个方块还能不能待着，
 * 答 false 就把花弹成掉落物。</p>
 *
 * <p>注意它不创建方块实体（纯判定），所以野花用它、功能花用
 * {@link FlowerBehavior}（后者继承本类，额外带魔力账本）。</p>
 */
public class GroundPlantBehavior extends BukkitBlockBehavior {

    public static final Key KEY = Key.of("botania", "ground_plant");

    public static BlockBehaviorFactory<GroundPlantBehavior> factory() {
        return (block, section) -> new GroundPlantBehavior(block);
    }

    /** 能当花床的方块。 */
    private static final Set<Material> SOIL = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM,
            Material.MOSS_BLOCK, Material.MUD, Material.FARMLAND,
            Material.DIRT_PATH, Material.MUDDY_MANGROVE_ROOTS,
            Material.SOUL_SOIL, Material.CLAY
    );

    public GroundPlantBehavior(BlockDefinition block) {
        super(block);
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        World world = worldOf(args[1]);
        if (world == null) {
            return true;  // 拿不到世界就别拦，免得把方块全弹了
        }
        var pos = LocationUtils.fromBlockPos(args[2]);
        Block below = world.getBlockAt(pos.x(), pos.y() - 1, pos.z());
        return SOIL.contains(below.getType());
    }

    /**
     * NMS Level → Bukkit World。
     *
     * <p>CE 的 proxy 包不是编译期依赖（它跟着插件一起发），所以这里走反射，
     * 拿不到就返回 null，由调用方决定放行。</p>
     */
    protected static World worldOf(Object minecraftLevel) {
        if (minecraftLevel == null) {
            return null;
        }
        if (minecraftLevel instanceof World world) {
            return world;
        }
        try {
            Class<?> proxy = Class.forName(
                    "net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy");
            Object instance = proxy.getField("INSTANCE").get(null);
            Object result = proxy.getMethod("getWorld", Object.class).invoke(instance, minecraftLevel);
            return result instanceof World world ? world : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
