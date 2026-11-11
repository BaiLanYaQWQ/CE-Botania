package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/**
 * 泰拉凝聚板的方块实体：存魔力 + 火花状态。
 *
 * <p>上游的凝聚板本身是个 ManaReceiver，火花把魔力从池子搬过来灌进它，
 * 攒够配方需求就自动成型。</p>
 */
public final class TerrasteelController extends ManaBlockController {

    private static final String ROOT = "cebotania:terra_plate";

    /** 一次合成需要的魔力。 */
    public static final long CAPACITY = 500_000L;

    private boolean hasSpark;

    public TerrasteelController(BlockEntity blockEntity) {
        super(blockEntity, CAPACITY);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    public boolean hasSpark() {
        return this.hasSpark;
    }

    public void removeSpark() {
        this.hasSpark = false;
        markChanged();
    }

    public void installSpark() {
        this.hasSpark = true;
        markChanged();
    }

    /** 攒够 50 万了没。 */
    public boolean isFull() {
        return this.mana >= CAPACITY;
    }

    /** 宿主被拆时，把火花掉出来。 */
    private void dropSpark() {
        if (!this.hasSpark) {
            return;
        }
        this.hasSpark = false;
        try {
            var w = net.bailanya.cebotania.platform.Platform.bukkit(world());
            var pos = position();
            if (w == null || pos == null) {
                return;
            }
            // 先删掉上面那个火花实体
            net.bailanya.cebotania.block.SparkNetwork.despawn(w, pos.x(), pos.y(), pos.z());
            var stack = net.bailanya.cebotania.platform.BotaniaItems.create("botania:spark", 1);
            if (stack != null) {
                w.dropItem(new org.bukkit.Location(w, pos.x() + 0.5, pos.y() + 0.5,
                        pos.z() + 0.5), stack);
            }
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    @Override
    public void onRemove() {
        dropSpark();
        super.onRemove();
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("hasSpark", this.hasSpark);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.hasSpark = tag.getBoolean("hasSpark", false);
    }
}
