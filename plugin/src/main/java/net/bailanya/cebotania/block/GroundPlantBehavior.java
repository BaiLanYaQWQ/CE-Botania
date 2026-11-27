package net.bailanya.cebotania.block;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;

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
        // 区块生成时这里是 WorldGenRegion；直接通过当前视图读取土壤。
        Object below = BlockPosProxy.INSTANCE.offset(args[2], 0, -1, 0);
        Object soil = BlockGetterProxy.INSTANCE.getBlockState(args[1], below);
        return SOIL.contains(BlockStateUtils.fromBlockData(soil).getMaterial());
    }
}
