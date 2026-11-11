package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/**
 * 精灵门核心的方块实体：记录开/关状态 + 累计魔力消耗。
 */
public final class AlfheimPortalController extends ManaBlockController {

    private static final String ROOT = "cebotania:alfheim_portal";

    private boolean open;
    /** 上次扣维持魔力的 tick。 */
    private int maintainTick;

    public AlfheimPortalController(BlockEntity blockEntity) {
        super(blockEntity, 0L);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean value) {
        this.open = value;
        markChanged();
    }

    public int maintainTick() {
        return this.maintainTick;
    }

    public void setMaintainTick(int value) {
        this.maintainTick = value;
    }

    /** 每 tick 检查：开着的话持续扣魔力，不够就关。 */
    @Override
    public void serverTick() {
        if (!this.open) {
            return;
        }
        this.maintainTick++;
        if (this.maintainTick % 10 != 0) {
            return;
        }
        var ceWorld = world();
        var pos = position();
        if (ceWorld == null || pos == null) {
            return;
        }
        long need = 250;   // 每 10 tick 扣 250（约每秒 500）
        long drained = 0;
        for (int dx = -5; dx <= 5 && drained < need; dx++) {
            for (int dy = -5; dy <= 5 && drained < need; dy++) {
                for (int dz = -5; dz <= 5 && drained < need; dz++) {
                    var probe = new net.momirealms.craftengine.core.world.BlockPos(
                            pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    var pe = ceWorld.getBlockEntityAtIfLoaded(probe);
                    if (pe == null) {
                        continue;
                    }
                    var pool = pe.controller.get(ManaPoolController.class, 0);
                    if (pool == null || pool.mana() <= 0) {
                        continue;
                    }
                    var above = new net.momirealms.craftengine.core.world.BlockPos(
                            probe.x(), probe.y() + 1, probe.z());
                    var aboveBlock = net.bailanya.cebotania.platform.Platform.blockAt(ceWorld, above);
                    boolean hasPylon = false;
                    if (aboveBlock != null) {
                        var st = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                                .getCustomBlockState(aboveBlock);
                        hasPylon = st != null && !st.isEmpty()
                                && st.owner().value().id().value().contains("pylon");
                    }
                    if (!hasPylon) {
                        continue;
                    }
                    drained += pool.extract(need - drained);
                }
            }
        }
        java.util.logging.Logger.getLogger("CeBotania").info(
                "[精灵门] 位置 " + pos.x() + "," + pos.y() + "," + pos.z()
                        + " 扣魔力 " + drained + " / " + need + " open=" + this.open);
        if (drained < need) {
            // 魔力耗尽：关门
            this.open = false;
            markChanged();
            var blockEntity = blockEntity();
            if (blockEntity != null) {
                BlockStates.setPortalOpen(blockEntity, false);
            }
            var bukkit = net.bailanya.cebotania.platform.Platform.bukkit(ceWorld);
            if (bukkit != null) {
                try {
                    var loc = new org.bukkit.Location(bukkit, pos.x() + 0.5, pos.y() + 2.0,
                            pos.z() + 0.5);
                    bukkit.spawnParticle(org.bukkit.Particle.SMOKE, loc, 30, 1.2, 1.2, 0.1, 0.01);
                    bukkit.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("open", this.open);
        tag.putInt("maintainTick", this.maintainTick);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.open = tag.getBoolean("open", false);
        this.maintainTick = tag.getInt("maintainTick", 0);
    }
}
