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

    /** 这个池子有没有装火花。 */
    private boolean hasSpark;
    /** 火花编号（森林法杖显示用）。 */
    private int sparkId;
    /** 火花的悬浮显示实体。 */
    private org.bukkit.entity.ItemDisplay sparkDisplay;
    private static int SPARK_COUNTER = 1;

    /** 自管的显示实体。 */
    private org.bukkit.entity.ItemDisplay display;
    private boolean displayFull;

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
        // 加载后重建显示实体（火花 + 池子外观）
        refreshDisplay();
        restoreSparkDisplay();
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

    /**
     * 同步"空 / 满"外观。
     *
     * <p>CE 的 {@code entity_renderer} 在方块状态切换时不会重建实体，所以这里
     * 由插件自己维护一个 item_display：状态变化时销毁旧的、按新状态重建。</p>
     */
    private void syncAppearance() {
        BlockEntity entity = blockEntity();
        if (entity != null) {
            BlockStates.setFilled(entity, this.mana > 0);
        }
        refreshDisplay();
    }

    /** 重建 display 实体（魔力从 0 变有 / 从有变 0 时切换模型）。 */
    private void refreshDisplay() {
        boolean wantFull = this.mana > 0;
        if (this.display != null && wantFull == this.displayFull) {
            return;   // 状态没变
        }
        removeDisplay();
        var world = net.bailanya.cebotania.platform.Platform.bukkit(world());
        var pos = position();
        if (world == null || pos == null) {
            return;
        }
        String renderItem = wantFull ? "botania:render/mana_pool_full" : "botania:render/mana_pool";
        var stack = net.bailanya.cebotania.platform.BotaniaItems.create(renderItem, 1);
        if (stack == null) {
            return;
        }
        try {
            var raw = world.spawnEntity(
                    new org.bukkit.Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                    org.bukkit.entity.EntityType.ITEM_DISPLAY);
            if (raw instanceof org.bukkit.entity.ItemDisplay display) {
                display.setItemStack(stack);
                display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                display.setItemDisplayTransform(
                        org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                display.setInvulnerable(true);
                display.setPersistent(false);
                display.setShadowRadius(0f);
                display.setShadowStrength(0f);
                this.display = display;
                this.displayFull = wantFull;
            }
        } catch (Throwable ignored) {
            // 实体创建失败就退回方块自带的渲染
        }
    }

    private void removeDisplay() {
        if (this.display != null) {
            try {
                if (this.display.isValid()) {
                    this.display.remove();
                }
            } catch (Throwable ignored) {
                // 忽略
            }
            this.display = null;
        }
    }

    @Override
    public void onUnload() {
        removeSparkDisplay();
        removeDisplay();
        super.onUnload();
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
            // 掉落失败不影响拆除
        }
    }

    @Override
    public void onRemove() {
        dropSpark();
        removeSparkDisplay();
        removeDisplay();
        super.onRemove();
    }

    /** 满池比例，给外观/提示用。 */
    public double fillRatio() {
        return this.mana / (double) CAPACITY;
    }

    public void setMana(long value) {
        this.mana = Math.max(0, Math.min(CAPACITY, value));
        markChanged();
    }

    @Override
    public void serverTick() {
        tickSpark();
    }

    // —— 火花（Spark）——
    public boolean hasSpark() {
        return this.hasSpark;
    }

    public int sparkId() {
        return this.sparkId;
    }

    public void removeSpark() {
        this.hasSpark = false;
        markChanged();
    }

    public void installSpark() {
        this.hasSpark = true;
        if (this.sparkId == 0) {
            this.sparkId = SPARK_COUNTER++;
        }
        refreshSparkDisplay();
        markChanged();
    }

    @Override
    protected void saveExtra(net.momirealms.craftengine.libraries.nbt.CompoundTag tag) {
        tag.putBoolean("hasSpark", this.hasSpark);
        tag.putInt("sparkId", this.sparkId);
    }

    @Override
    protected void loadExtra(net.momirealms.craftengine.libraries.nbt.CompoundTag tag) {
        this.hasSpark = tag.getBoolean("hasSpark", false);
        this.sparkId = tag.getInt("sparkId", 0);
    }

    /** 火花实体（用 SparkNetwork 统管）。 */
    private void refreshSparkDisplay() {
        removeSparkDisplay();
        if (!this.hasSpark) {
            return;
        }
        var bukkit = net.bailanya.cebotania.platform.Platform.bukkit(world());
        var pos = position();
        if (bukkit == null || pos == null) {
            return;
        }
        var existing = SparkNetwork.findAt(bukkit, pos.x(), pos.y(), pos.z());
        if (existing != null) {
            this.sparkDisplay = existing;
            return;
        }
        this.sparkDisplay = SparkNetwork.spawn(bukkit, pos.x(), pos.y(), pos.z(),
                "魔力池 " + pos.x() + "," + pos.y() + "," + pos.z());
    }

    private void removeSparkDisplay() {
        if (this.sparkDisplay != null) {
            try {
                if (this.sparkDisplay.isValid()) {
                    this.sparkDisplay.remove();
                }
            } catch (Throwable ignored) {
                // 忽略
            }
            this.sparkDisplay = null;
        }
    }

    /** 加载/重载时重建火花显示。 */
    public void restoreSparkDisplay() {
        if (this.hasSpark) {
            refreshSparkDisplay();
        }
    }

    /** 火花每 tick：抽池子魔力灌给附近能消耗魔力的方块（不用发射器对准）。 */
    private void tickSpark() {
        if (!this.hasSpark || this.mana <= 0) {
            return;
        }
        var ceWorld = world();
        var pos = position();
        if (ceWorld == null || pos == null) {
            return;
        }
        long budget = Math.min(500, this.mana);
        for (int dx = -6; dx <= 6 && budget > 0; dx++) {
            for (int dy = -4; dy <= 4 && budget > 0; dy++) {
                for (int dz = -6; dz <= 6 && budget > 0; dz++) {
                    var probe = new net.momirealms.craftengine.core.world.BlockPos(
                            pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    var entity = ceWorld.getBlockEntityAtIfLoaded(probe);
                    if (entity == null) {
                        continue;
                    }
                    ManaBlockController target = null;
                    var spreader = entity.controller.get(SpreaderController.class, 0);
                    if (spreader != null && spreader.room() > 0) {
                        target = spreader;
                    }
                    if (target == null) {
                        var altar = entity.controller.get(RunicAltarController.class, 0);
                        if (altar != null && altar.room() > 0) {
                            target = altar;
                        }
                    }
                    if (target != null) {
                        long sent = target.insert(budget);
                        if (sent > 0) {
                            extract(sent);
                            budget -= sent;
                        }
                    }
                }
            }
        }
    }
}
