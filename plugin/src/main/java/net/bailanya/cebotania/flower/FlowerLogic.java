package net.bailanya.cebotania.flower;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * 产魔花的环境判定与产出。
 *
 * <p>上游每种花是一个 {@code *BlockEntity}，在自己的 tick 里判断"我现在能不能产魔"。
 * 这里把判定抽成按 {@link FlowerType#logic} 分派的纯函数：不持有状态，
 * 只回答"这一跳该不该产、产多少"。花自己的缓冲与节流在控制器里。</p>
 */
public final class FlowerLogic {

    private static final BlockFace[] SIDES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };

    /** 可以当燃料的物品（下游 1.21 的燃料表里挑常用的）。 */
    private static final Set<Material> FUELS = EnumSet.of(
            Material.COAL, Material.CHARCOAL, Material.COAL_BLOCK, Material.LAVA_BUCKET,
            Material.OAK_PLANKS, Material.OAK_LOG, Material.STICK, Material.BLAZE_POWDER,
            Material.DRIED_KELP_BLOCK, Material.BAMBOO, Material.OAK_SAPLING
    );

    /** 食物（美食花只认能吃的）。 */
    private static final Set<Material> FOODS = EnumSet.of(
            Material.APPLE, Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
            Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.GOLDEN_APPLE,
            Material.CARROT, Material.POTATO, Material.BEETROOT, Material.MELON_SLICE,
            Material.SWEET_BERRIES, Material.BAKED_POTATO, Material.COOKIE, Material.CAKE
    );

    private FlowerLogic() {
    }

    /** 附近有没有可燃的掉落物；有就返回它，没有返回 null。 */
    public static Item findFuelItem(World world, Block self, double radius) {
        var center = center(self);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Item item) {
                ItemStack stack = item.getItemStack();
                if (stack != null && FUELS.contains(stack.getType())) {
                    return item;
                }
            }
        }
        return null;
    }

    /** 这个材料能不能当燃料喂给萤火之焰。 */
    public static boolean isFuel(org.bukkit.Material material) {
        return FUELS.contains(material);
    }

    /** 这个材料能不能喂给美食花。 */
    public static boolean isFood(org.bukkit.Material material) {
        return FOODS.contains(material);
    }

    /**
     * 这一跳的产出量（已经扣掉"环境不满足"的情况，返回 0 表示没有）。
     *
     * @param consumed 本次判定消耗掉的东西（掉落物/方块），由调用方决定是否真的清掉
     */
    public static long generate(World world, Block self, String logic, FlowerType type,
                                Consumed consumed) {
        if (world == null || self == null) {
            return 0;
        }
        return switch (logic) {
            case "water" -> nearBlock(self, Material.WATER) ? type.manaPerAction : 0;
            case "lava" -> nearBlock(self, Material.LAVA) ? type.manaPerAction : 0;
            // 燃料不在这一层结算：FlowerController 会接管"持续燃烧"，
            // 这里只负责告诉它"附近有可燃物可以吃"。
            case "fuel" -> 0;
            case "food" -> eatDropped(world, self, consumed, mat -> FOODS.contains(mat), 4.0)
                    ? type.manaPerAction * 10 : 0;
            case "tnt" -> {
                for (Entity entity : world.getNearbyEntities(center(self), 4, 4, 4)) {
                    if (entity instanceof TNTPrimed) {
                        consumed.tnt = entity;
                        yield type.manaPerAction;
                    }
                }
                yield 0;
            }
            case "cake" -> {
                for (BlockFace face : SIDES) {
                    if (self.getRelative(face).getType() == Material.CAKE) {
                        consumed.block = self.getRelative(face);
                        yield type.manaPerAction;
                    }
                }
                yield 0;
            }
            case "leaves" -> {
                for (BlockFace face : SIDES) {
                    Block other = self.getRelative(face);
                    if (other.getType().name().endsWith("_LEAVES")) {
                        consumed.block = other;
                        yield type.manaPerAction;
                    }
                }
                yield 0;
            }
            case "wool" -> {
                for (BlockFace face : SIDES) {
                    if (self.getRelative(face).getType().name().endsWith("_WOOL")) {
                        consumed.block = self.getRelative(face);
                        yield type.manaPerAction;
                    }
                }
                yield 0;
            }
            case "xp" -> {
                for (Entity entity : world.getNearbyEntities(center(self), 4, 4, 4)) {
                    if (entity instanceof ExperienceOrb orb) {
                        consumed.entity = orb;
                        yield type.manaPerAction;
                    }
                }
                yield 0;
            }
            default -> 0;
        };
    }

    private static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    private static boolean nearBlock(Block self, Material material) {
        for (BlockFace face : SIDES) {
            if (self.getRelative(face).getType() == material) {
                return true;
            }
        }
        return false;
    }

    /** 在花周围找符合条件的地面掉落物，找到就记进 consumed。 */
    private static boolean eatDropped(World world, Block self, Consumed consumed,
                                      java.util.function.Predicate<Material> test, double radius) {
        for (Entity entity : world.getNearbyEntities(center(self), radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack != null && test.test(stack.getType())) {
                consumed.entity = item;
                return true;
            }
        }
        return false;
    }

    /** 一次判定里被"吃掉"的东西，由调用方真正移除。 */
    public static final class Consumed {
        public Entity entity;
        public Entity tnt;
        public Block block;

        public boolean any() {
            return this.entity != null || this.tnt != null || this.block != null;
        }

        public void clear() {
            if (this.entity != null && this.entity.isValid()) {
                this.entity.remove();
            }
            if (this.tnt != null && this.tnt.isValid()) {
                this.tnt.remove();
            }
            if (this.block != null && this.block.getType() != Material.AIR) {
                this.block.setType(Material.AIR);
            }
        }
    }
}
