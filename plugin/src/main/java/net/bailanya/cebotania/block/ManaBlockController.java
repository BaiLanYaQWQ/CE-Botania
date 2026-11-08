package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/**
 * 所有"存魔力"的方块实体的共同底座：一个 long 的账本 + 存档 + 逐刻调度接线。
 *
 * <p>子类只管自己的行为，不用操心 NBT 键名冲突 —— 每个控制器写自己的
 * 根键（{@link #rootKey()}），因为一个方块可能挂多个控制器。</p>
 *
 * <p>坐标和世界在 onLoad 时缓存一份：CE 的 {@code blockEntity()} 字段在方块实体
 * 被替换/重载的瞬间可能是 null，直接解引用会让 tick 永远进不来。</p>
 */
public abstract class ManaBlockController extends BlockEntityController {

    private static final String MANA_KEY = "mana";

    protected long mana;
    protected final long capacity;

    private CEWorld cachedWorld;
    private BlockPos cachedPos;

    protected ManaBlockController(BlockEntity blockEntity, long capacity) {
        super(blockEntity);
        this.capacity = capacity;
        BlockEntity entity = blockEntity();
        if (entity != null) {
            this.cachedWorld = entity.world;
            this.cachedPos = entity.pos;
        }
        // 构造时就挂进调度：运行时 /place 出来的方块实体，CE 不一定会走 onLoad，
        // 等 onLoad 会漏掉；Set 去重，两处都注册也不会重复 tick。
        attach();
    }

    protected abstract String rootKey();

    /** 每个服务器刻调用一次；子类在这里跑自己的逻辑。 */
    public void serverTick() {
    }

    public long mana() {
        return this.mana;
    }

    public long capacity() {
        return this.capacity;
    }

    public long room() {
        return Math.max(0, this.capacity - this.mana);
    }

    /** 注入魔力，返回真正吃进去的量。 */
    public long insert(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long accepted = Math.min(amount, room());
        if (accepted > 0) {
            this.mana += accepted;
            markChanged();
        }
        return accepted;
    }

    /** 抽出魔力，返回真正拿到的量。 */
    public long extract(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long taken = Math.min(amount, this.mana);
        if (taken > 0) {
            this.mana -= taken;
            markChanged();
        }
        return taken;
    }

    /** 世界：优先当前值，退回 onLoad 时那份。 */
    public CEWorld world() {
        BlockEntity entity = blockEntity();
        if (entity != null && entity.world != null) {
            this.cachedWorld = entity.world;
        }
        return this.cachedWorld;
    }

    /** 坐标：同上。 */
    public BlockPos position() {
        BlockEntity entity = blockEntity();
        if (entity != null && entity.pos != null) {
            this.cachedPos = entity.pos;
        }
        return this.cachedPos;
    }

    /** 让 CE 把这个方块实体的存档标记为脏（下次区块保存时落盘）。 */
    protected void markChanged() {
        BlockEntity entity = blockEntity();
        if (entity != null && entity.world != null) {
            entity.world.blockEntityChanged(entity.pos);
        }
    }

    // —— 生命周期：活着就登记进调度，卸载就摘掉 ——

    @Override
    public void onLoad() {
        refreshLocation();
        attach();
    }

    private void attach() {
        net.bailanya.cebotania.core.TickBus bus =
                net.bailanya.cebotania.core.BotaniaRuntime.tickBus();
        if (bus != null) {
            bus.register(this);
        }
    }

    @Override
    public void onUnload() {
        detach();
    }

    @Override
    public void onRemove() {
        detach();
    }

    private void detach() {
        net.bailanya.cebotania.core.TickBus bus =
                net.bailanya.cebotania.core.BotaniaRuntime.tickBus();
        if (bus != null) {
            bus.unregister(this);
        }
    }

    private void refreshLocation() {
        BlockEntity entity = blockEntity();
        if (entity == null) {
            return;
        }
        if (entity.world != null) {
            this.cachedWorld = entity.world;
        }
        if (entity.pos != null) {
            this.cachedPos = entity.pos;
        }
    }

    // —— 存档 ——

    @Override
    public void saveCustomData(CompoundTag root) {
        CompoundTag mine = new CompoundTag();
        mine.putLong(MANA_KEY, this.mana);
        saveExtra(mine);
        root.put(rootKey(), mine);
    }

    @Override
    public void loadCustomData(CompoundTag root) {
        CompoundTag mine = root.getCompound(rootKey());
        if (mine == null) {
            return;
        }
        this.mana = Math.max(0, Math.min(this.capacity, mine.getLong(MANA_KEY, 0)));
        loadExtra(mine);
    }

    /** 子类追加自己的存档字段。 */
    protected void saveExtra(CompoundTag tag) {
    }

    protected void loadExtra(CompoundTag tag) {
    }
}
