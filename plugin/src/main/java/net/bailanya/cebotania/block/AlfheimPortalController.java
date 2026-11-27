package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

/** 精灵门核心的开关、通道朝向及魔力维持状态。 */
public final class AlfheimPortalController extends ManaBlockController {

    private static final String ROOT = "cebotania:alfheim_portal";
    private static final String FRAME_ALONG_X = "frameAlongX";
    private static final int TRADE_INTERVAL = 5;
    private static final int MAINTAIN_INTERVAL = 10;
    private static final long MAINTAIN_MANA = 250;

    private final AlfheimTrade trade;
    private boolean open;
    private Boolean frameAlongX;
    private int maintainTick;

    public AlfheimPortalController(BlockEntity blockEntity, AlfheimTrade trade) {
        super(blockEntity, 0L);
        this.trade = trade;
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
        BlockStates.setPortalOpen(blockEntity(), value);
    }

    void setOpen(boolean value, boolean alongX) {
        this.frameAlongX = alongX;
        setOpen(value);
    }

    private boolean frameAlongX() {
        if (this.frameAlongX == null) {
            this.frameAlongX = AlfheimPortalBehavior.storedFrameAlongX(world(), position());
            markChanged();
        }
        return this.frameAlongX;
    }

    void closePortal(World bukkit, float pitch) {
        var pos = position();
        boolean alongX = frameAlongX();
        setOpen(false);
        AlfheimPortalBehavior.clearPortal(bukkit, pos, alongX);
        var center = AlfheimPortalBehavior.effectCenter(bukkit, pos);
        bukkit.spawnParticle(Particle.SMOKE, center, 30,
                alongX ? 1.2 : 0.1, 1.2, alongX ? 0.1 : 1.2, 0.01);
        bukkit.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, pitch);
    }

    @Override
    public void onRemove() {
        super.onRemove();
        if (this.open && world() != null && position() != null) {
            var bukkit = Platform.bukkit(world());
            if (bukkit != null) {
                AlfheimPortalBehavior.clearPortal(bukkit, position(), frameAlongX());
            }
            this.open = false;
        }
    }

    @Override
    public void serverTick() {
        if (!this.open) {
            return;
        }
        int currentTick = Bukkit.getCurrentTick();
        boolean maintain = ++this.maintainTick % MAINTAIN_INTERVAL == 0;
        boolean scan = currentTick % TRADE_INTERVAL == 0;
        if (!maintain && !scan) {
            return;
        }
        var ceWorld = world();
        var pos = position();
        var entity = blockEntity();
        if (ceWorld == null || pos == null || entity == null
                || ceWorld.getBlockEntityAtIfLoaded(pos) != entity) {
            return;
        }
        var bukkit = Platform.bukkit(ceWorld);
        if (bukkit == null) {
            return;
        }
        if (maintain && AlfheimPortalBehavior.drainPools(
                AlfheimPortalBehavior.findPools(ceWorld, pos), MAINTAIN_MANA) < MAINTAIN_MANA) {
            closePortal(bukkit, 0.8f);
            return;
        }
        if (scan) {
            this.trade.tick(bukkit, pos, frameAlongX(), currentTick);
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("open", this.open);
        if (this.frameAlongX != null) {
            tag.putBoolean(FRAME_ALONG_X, this.frameAlongX);
        }
        tag.putInt("maintainTick", this.maintainTick);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.open = tag.getBoolean("open", false);
        this.frameAlongX = tag.containsKey(FRAME_ALONG_X) ? tag.getBoolean(FRAME_ALONG_X, false) : null;
        this.maintainTick = tag.getInt("maintainTick", 0);
    }
}
