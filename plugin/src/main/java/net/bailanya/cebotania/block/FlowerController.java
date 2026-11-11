package net.bailanya.cebotania.block;

import net.bailanya.cebotania.flower.FlowerLogic;
import net.bailanya.cebotania.flower.FlowerType;
import net.bailanya.cebotania.flower.FunctionalFlowers;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 一朵花的方块实体。
 *
 * <p>两类花走不同的路：</p>
 * <ul>
 *   <li><b>产魔花</b>：自己往缓冲里灌魔力，等发射器来吸（{@link FlowerLogic}）</li>
 *   <li><b>功能花</b>：先做效果，做成了才从附近魔力池扣魔力（{@link FunctionalFlowers}）</li>
 * </ul>
 */
public final class FlowerController extends ManaBlockController {

    private static final String ROOT = "cebotania:flower";
    private static final String TICK_KEY = "tick";

    private final String flowerId;
    private final FlowerType type;
    private int tickCounter;

    /** 萤火之焰：当前这份燃料还能烧多少刻（0 = 没在烧）。 */
    private int burnTicks;
    /** 上一个吃掉的燃料是什么（给玩家看）。 */
    private String burningFuel;

    /** 被弱磁兰抑制时停止一切产出。 */
    private boolean suppressed;

    /** 方块被悄悄清掉（例如支撑没了）时，自己从调度里退出来。 */
    private int staleTicks;

    public FlowerController(BlockEntity blockEntity, String flowerId) {
        super(blockEntity, FlowerType.byId(flowerId) == null
                ? 1000L : FlowerType.byId(flowerId).bufferCapacity);
        this.flowerId = flowerId;
        this.type = FlowerType.byId(flowerId);
    }

    public String flowerId() {
        return this.flowerId;
    }

    public FlowerType type() {
        return this.type;
    }

    public boolean suppressed() {
        return this.suppressed;
    }

    public void setSuppressed(boolean value) {
        this.suppressed = value;
    }

    public int ticks() {
        return this.tickCounter;
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt(TICK_KEY, this.tickCounter);
        tag.putInt("burn", this.burnTicks);
        if (this.burningFuel != null) {
            tag.putString("fuel", this.burningFuel);
        }
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.tickCounter = tag.getInt(TICK_KEY, 0);
        this.burnTicks = tag.getInt("burn", 0);
        String fuel = tag.getString("fuel", "");
        this.burningFuel = fuel.isEmpty() ? null : fuel;
    }

    @Override
    public void serverTick() {
        CEWorld world = world();
        BlockPos pos = position();
        if (world == null || pos == null) {
            return;
        }
        tick(world, pos);
    }

    /** 由逐刻调度调用。 */
    public void tick(CEWorld ceWorld, BlockPos pos) {
        if (this.type == null) {
            return;
        }
        World world = Platform.bukkit(ceWorld);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        // 方块已经不在了：连续几次就注销，避免在调度池里空转
        if (block.isEmpty() || CraftEngineBlocks.getCustomBlockState(block) == null) {
            if (++this.staleTicks > 20) {
                onRemove();
            }
            return;
        }
        this.staleTicks = 0;

        // —— 萤火之焰：一份燃料持续烧，1 mana/tick，烧完才吃下一份 ——
        if ("fuel".equals(this.type.logic)) {
            tickEndoflame(world, block);
            return;
        }

        if (this.suppressed) {
            return;   // 被弱磁兰压制：不产魔、不做事
        }
        this.tickCounter++;
        if (this.tickCounter % Math.max(1, this.type.intervalTicks) != 0) {
            return;
        }

        // —— 功能花：没有魔力池就完全不动，有才做事 ——
        if (this.type.kind == FlowerType.Kind.FUNCTIONAL) {
            if (this.type.manaPerAction > 0) {
                // 先确认附近有付得起的池子；没有就直接不动
                ManaPoolController pool = findPool(this.type.manaPerAction);
                if (pool == null) {
                    return;
                }
                long spent = FunctionalFlowers.run(world, block, this.type.logic, this.type);
                if (spent > 0) {
                    pool.extract(this.type.manaPerAction);
                }
                return;
            }
            // 免费花（镜片/静音那种）
            FunctionalFlowers.run(world, block, this.type.logic, this.type);
            return;
        }

        // —— 产魔花：判定环境，往缓冲里灌 ——
        if (room() <= 0) {
            return;
        }
        FlowerLogic.Consumed consumed = new FlowerLogic.Consumed();
        long generated = FlowerLogic.generate(world, block, this.type.logic, this.type, consumed);
        if (generated <= 0) {
            return;
        }
        if (consumed.any()) {
            consumed.clear();
        }
        insert(generated);
        try {
            world.spawnParticle(org.bukkit.Particle.END_ROD,
                    block.getLocation().add(0.5, 0.6, 0.5),
                    2, 0.25, 0.2, 0.25, 0.01);
        } catch (Throwable ignored) {
            // 粒子缺失不影响功能
        }
    }

    /**
     * 萤火之焰的燃烧循环。
     *
     * <p>上游口径：吃一份可燃物 → 燃烧刻数 = min(32000, 物品燃烧时间) / 2
     * → 每刻产 1 魔力 → 烧完再吃下一份。煤 1600 刻，所以一份煤烧 40 秒产 800 魔力。</p>
     */
    private void tickEndoflame(World world, Block block) {
        if (room() > 0 && this.burnTicks > 0) {
            this.burnTicks--;
            insert(1);
            if (this.burnTicks % 20 == 0) {
                try {
                    world.spawnParticle(org.bukkit.Particle.FLAME,
                            block.getLocation().add(0.5, 0.6, 0.5), 2, 0.2, 0.2, 0.2, 0.01);
                } catch (Throwable ignored) {
                    // 粒子缺失不影响功能
                }
            }
            return;
        }
        if (this.burnTicks > 0) {
            return;   // 缓冲满了，先别烧
        }
        // 找一份新燃料
        org.bukkit.entity.Item fuel = FlowerLogic.findFuelItem(world, block, 4.0);
        if (fuel == null) {
            return;
        }
        var stack = fuel.getItemStack();
        int burn = burnTimeOf(stack.getType());
        if (burn <= 0) {
            return;
        }
        this.burnTicks = Math.min(32000, burn) / 2;
        this.burningFuel = stack.getType().getKey().toString();
        if (stack.getAmount() <= 1) {
            fuel.remove();
        } else {
            stack.setAmount(stack.getAmount() - 1);
            fuel.setItemStack(stack);
        }
        markChanged();
    }

    /** 常见燃料的燃烧刻数（和原版燃料表一致）。 */
    private static int burnTimeOf(org.bukkit.Material material) {
        return switch (material) {
            case COAL, CHARCOAL -> 1600;
            case COAL_BLOCK -> 16000;
            case LAVA_BUCKET -> 20000;
            case BLAZE_ROD -> 2400;
            case DRIED_KELP_BLOCK -> 4000;
            case OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS,
                 ACACIA_PLANKS, DARK_OAK_PLANKS, MANGROVE_PLANKS, CHERRY_PLANKS,
                 BAMBOO_PLANKS, OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG,
                 ACACIA_LOG, DARK_OAK_LOG, OAK_WOOD, SPRUCE_WOOD, BIRCH_WOOD -> 300;
            case STICK, OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING,
                 JUNGLE_SAPLING, ACACIA_SAPLING, DARK_OAK_SAPLING -> 100;
            case WHITE_WOOL -> 100;
            default -> 0;
        };
    }

    /** 找最近的、余额够 cost 的魔力池。 */
    private ManaPoolController findPool(long cost) {
        CEWorld ceWorld = world();
        BlockPos pos = position();
        if (ceWorld == null || pos == null) {
            return null;
        }
        ManaPoolController best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 8;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos probe = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(probe);
                    if (entity == null) {
                        continue;
                    }
                    ManaPoolController pool = entity.controller.get(ManaPoolController.class, 0);
                    if (pool == null || pool.mana() < cost) {
                        continue;
                    }
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pool;
                    }
                }
            }
        }
        return best;
    }

    /** 发射器来吸：最多拿走缓冲里现成的量。 */
    public long drain(long amount) {
        return extract(amount);
    }
}
