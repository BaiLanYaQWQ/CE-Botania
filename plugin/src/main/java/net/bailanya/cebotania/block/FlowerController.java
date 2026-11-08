package net.bailanya.cebotania.block;

import net.bailanya.cebotania.flower.FlowerLogic;
import net.bailanya.cebotania.flower.FlowerType;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 一朵花的方块实体：自己攒魔力，等发射器来吸。
 *
 * <p>上游的花把魔力直接连接网络，发射器每刻来抽；这里保持一致：
 * 花只管往缓冲里灌，{@link SpreaderController} 负责吸走并送进池子。</p>
 */
public final class FlowerController extends ManaBlockController {

    private static final String ROOT = "cebotania:flower";
    private static final String TICK_KEY = "tick";

    private final String flowerId;
    private final FlowerType type;
    private int tickCounter;
    /** 方块被悄悄清掉（例如支撑没了）时，自己从调度里退出来。 */
    private int staleTicks;

    public FlowerController(BlockEntity blockEntity, String flowerId) {
        super(blockEntity, FlowerType.byId(flowerId) == null ? 1000L : FlowerType.byId(flowerId).bufferCapacity);
        this.flowerId = flowerId;
        this.type = FlowerType.byId(flowerId);
    }

    public String flowerId() {
        return this.flowerId;
    }

    public FlowerType type() {
        return this.type;
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt(TICK_KEY, this.tickCounter);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.tickCounter = tag.getInt(TICK_KEY, 0);
    }

    /** 由 CE 的方块实体 ticker 每刻调用。 */
    public void tick(CEWorld ceWorld, BlockPos pos) {
        if (this.type == null) {
            return;
        }
        this.tickCounter++;
        // 缓冲没地方放就不判定环境：省掉附近实体的扫描
        if (room() <= 0) {
            return;
        }
        if (this.tickCounter % Math.max(1, this.type.intervalTicks) != 0) {
            return;
        }
        World world = Platform.bukkit(ceWorld);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        // 方块已经不在了（被清掉/被替换）：连续几次就注销，避免在调度池里空转
        if (block.isEmpty()
                || net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                        .getCustomBlockState(block) == null) {
            if (++this.staleTicks > 20) {
                onRemove();
            }
            return;
        }
        this.staleTicks = 0;
        FlowerLogic.Consumed consumed = new FlowerLogic.Consumed();
        long generated = FlowerLogic.generate(world, block, this.type.logic, this.type, consumed);
        if (generated <= 0) {
            return;
        }
        // 产魔了：冒一点对应颜色的粒子
        try {
            var center = block.getLocation().add(0.5, 0.6, 0.5);
            world.spawnParticle(org.bukkit.Particle.END_ROD, center, 2, 0.25, 0.2, 0.25, 0.01);
        } catch (Throwable ignored) {
            // 粒子缺失不影响功能
        }
        if (consumed.any()) {
            consumed.clear();
        }
        insert(generated);
    }

    @Override
    public void serverTick() {
        net.momirealms.craftengine.core.world.CEWorld world = world();
        net.momirealms.craftengine.core.world.BlockPos pos = position();
        if (world == null || pos == null) {
            return;
        }
        tick(world, pos);
    }

    /** 诊断用：这个方块实体被 tick 了多少次。 */
    public int ticks() {
        return this.tickCounter;
    }

    /** 发射器来吸：最多拿走缓冲里现成的量。 */
    public long drain(long amount) {
        return extract(amount);
    }
}
