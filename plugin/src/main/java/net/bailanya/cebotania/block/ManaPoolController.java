package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;

/**
 * 魔力池：存 1,000,000 魔力的账本。
 *
 * <p>它是整条产线的终点，也是灌注配方的工作台 —— 由 {@link ManaPoolBehavior}
 * 负责右键交互，这里只管数字。</p>
 */
public final class ManaPoolController extends ManaBlockController {

    /** 上游 TilePool 的容量：一个满池 = 1,000,000 mana。 */
    public static final long CAPACITY = 1_000_000L;

    private static final String ROOT = "cebotania:mana_pool";

    public ManaPoolController(BlockEntity blockEntity) {
        super(blockEntity, CAPACITY);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    /** 有魔力的时候在池面上冒一点蓝色魔力粒子，让"里面有东西"看得出来。 */
    private int particleTick;

    /** 换方块期间不要递归触发外观同步。 */
    private boolean swapping = true;

    /** 有魔力的池子是另一个方块 id（mana_pool_filled），模型里烘了蓝色魔力面。 */
    private static final String EMPTY_ID = "botania:mana_pool";
    private static final String FILLED_ID = "botania:mana_pool_filled";

    @Override
    public void onLoad() {
        super.onLoad();
        this.swapping = false;
        syncAppearance();
    }

    @Override
    public long insert(long amount) {
        long accepted = super.insert(amount);
        if (accepted > 0) {
            syncAppearance();
        }
        return accepted;
    }

    @Override
    public long extract(long amount) {
        long taken = super.extract(amount);
        if (taken > 0) {
            syncAppearance();
        }
        return taken;
    }

    /** 有魔力 → 切到"装满"外观（CE 的多外观，模型是 mana_pool_full）。 */
    private void syncAppearance() {
        BlockEntity entity = blockEntity();
        if (entity != null) {
            BlockStates.setFilled(entity, this.mana > 0);
        }
    }

    /** 满池比例，给外观/提示用。 */
    public double fillRatio() {
        return this.mana / (double) CAPACITY;
    }

    public void setMana(long value) {
        this.mana = Math.max(0, Math.min(CAPACITY, value));
        markChanged();
    }
}
