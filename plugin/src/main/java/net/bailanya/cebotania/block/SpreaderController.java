package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/**
 * 魔力发射器：从附近的产魔花吸魔力，攒够就往朝向方向打。
 *
 * <p>行为对照上游：</p>
 * <ul>
 *   <li>有朝向（{@code facing} 属性，模型随之旋转）</li>
 *   <li>沿朝向直线探测，碰到能收魔力的方块就送（魔力池 / 符文祭坛 / 另一个发射器）</li>
 *   <li>探测距离 16 格，支持接力传输</li>
 *   <li>视觉上是"隔几秒飞过去一颗魔力球"，不是连续光束</li>
 * </ul>
 */
public final class SpreaderController extends ManaBlockController {

    private static final String ROOT = "cebotania:spreader";
    private static final String COOLDOWN_KEY = "cooldown";

    /** 缓存上限 = 一发。 */
    public static final long CAPACITY = 1000L;
    /** 一次最多送出去多少。 */
    public static final long BURST = 200L;
    /** 吸花的半径。 */
    private static final int PULL_RADIUS = 6;
    /** 沿朝向探测的距离。 */
    private static final int FIND_RADIUS = 16;
    /** 每 tick 最多从花吸多少。 */
    private static final long PULL_PER_TICK = 200L;
    /** 两次发射之间的刻数。 */
    private static final int BURST_INTERVAL = 10;
    /** 视觉粒子：飞完一格约 20 刻，两次之间隔 60 刻（3 秒）。 */
    private static final int PARTICLE_TRAVEL = 20;
    private static final int PARTICLE_GAP = 60;

    private int cooldown;
    private int tickDebug;

    /** 飞行中的魔力粒子：进度 -1 = 没在飞。 */
    private int particleProgress = -1;
    private org.bukkit.Location particleFrom;
    private org.bukkit.Location particleTo;
    private int particleCooldown;

    /** 被森林法杖绑定过来的花（坐标）；null = 吸附近所有花。 */
    private String boundFlower;

    public SpreaderController(BlockEntity blockEntity) {
        super(blockEntity, CAPACITY);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    public int ticks() {
        return this.tickDebug;
    }

    public String boundFlower() {
        return this.boundFlower;
    }

    public void bindFlower(String position) {
        this.boundFlower = position;
        markChanged();
    }

    // —— 朝向（读写方块状态里的 facing）——

    public net.momirealms.craftengine.core.util.Direction facingDir() {
        BlockEntity entity = blockEntity();
        if (entity != null) {
            ImmutableBlockState state = entity.blockState();
            if (state != null) {
                var property = state.getProperty("facing");
                if (property != null) {
                    Object value = state.get(property);
                    if (value instanceof net.momirealms.craftengine.core.util.Direction dir) {
                        return dir;
                    }
                }
            }
        }
        return net.momirealms.craftengine.core.util.Direction.NORTH;
    }

    public String facingName() {
        return switch (facingDir()) {
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            default -> "北";
        };
    }

    /** 森林法杖右键：顺时针转 90°（方块状态和模型一起换）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String rotate() {
        BlockEntity entity = blockEntity();
        if (entity == null || entity.world == null) {
            return facingName();
        }
        ImmutableBlockState state = entity.blockState();
        if (state == null) {
            return facingName();
        }
        var property = state.getProperty("facing");
        if (property == null) {
            return facingName();
        }
        ImmutableBlockState next = state.cycle(
                (net.momirealms.craftengine.core.block.property.Property) property);
        entity.world.setBlockStateAtIfLoaded(entity.pos, next);
        entity.setBlockState(next);
        markChanged();
        return facingName();
    }

    // —— 存档 ——

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt(COOLDOWN_KEY, this.cooldown);
        if (this.boundFlower != null) {
            tag.putString("bound", this.boundFlower);
        }
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.cooldown = tag.getInt(COOLDOWN_KEY, 0);
        String bound = tag.getString("bound", "");
        this.boundFlower = bound.isEmpty() ? null : bound;
    }

    // —— 逐刻 ——

    @Override
    public void serverTick() {
        CEWorld world = world();
        BlockPos pos = position();
        if (world == null || pos == null) {
            return;
        }
        tick(world, pos);
    }

    public void tick(CEWorld ceWorld, BlockPos pos) {
        this.tickDebug++;
        advanceParticle();
        if (this.particleCooldown > 0) {
            this.particleCooldown--;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        // 1) 缓存没满：先看有没有贴着的魔力池（上游可以贴着池子直接抽）
        if (room() > 0) {
            pullFromAdjacentPools(ceWorld, pos);
        }
        // 2) 还不够就从附近的花吸
        if (room() > 0) {
            pullFromFlowers(ceWorld, pos);
        }
        // 2) 攒着就往朝向方向送
        if (this.mana > 0 && this.cooldown <= 0) {
            ManaBlockController target = targetAhead(ceWorld, pos);
            if (target != null) {
                long sent = target.insert(Math.min(this.mana, BURST));
                if (sent > 0) {
                    extract(sent);
                    this.cooldown = BURST_INTERVAL;
                    if (this.particleCooldown <= 0) {
                        launchParticle(ceWorld, pos, target);
                    }
                }
            }
        }
    }

    /** 沿朝向找第一个能收魔力的方块。 */
    private ManaBlockController targetAhead(CEWorld ceWorld, BlockPos pos) {
        var dir = facingDir();
        for (int step = 1; step <= FIND_RADIUS; step++) {
            BlockPos probe = new BlockPos(
                    pos.x() + dir.stepX() * step,
                    pos.y() + dir.stepY() * step,
                    pos.z() + dir.stepZ() * step);
            BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(probe);
            if (entity == null) {
                continue;
            }
            ManaPoolController pool = entity.controller.get(ManaPoolController.class, 0);
            if (pool != null && pool.room() > 0) {
                return pool;
            }
            RunicAltarController altar = entity.controller.get(RunicAltarController.class, 0);
            if (altar != null && altar.room() > 0 && altar.wantsMana()) {
                return altar;
            }
            // 遇到发射器：能接力就接力，不能接力就**挡住**（不穿过它去找后面的池子）
            SpreaderController next = entity.controller.get(SpreaderController.class, 0);
            if (next != null && next != this) {
                if (next.room() > 0 && next.leadsToReceiver(0)) {
                    return next;
                }
                return null;   // 拦下：这台发射器的下游没有接收方
            }
        }
        return null;
    }

    /**
     * 从**贴着**的魔力池直接抽魔力。
     *
     * <p>上游的发射器可以和池子贴在一起，直接把池子当自己的缓存用。
     * 这里照做：六面相邻的池子，按 tick 预算往里搬。</p>
     */
    private void pullFromAdjacentPools(CEWorld ceWorld, BlockPos pos) {
        long budget = Math.min(PULL_PER_TICK, room());
        if (budget <= 0) {
            return;
        }
        for (var dir : net.momirealms.craftengine.core.util.Direction.values()) {
            BlockPos probe = new BlockPos(
                    pos.x() + dir.stepX(), pos.y() + dir.stepY(), pos.z() + dir.stepZ());
            BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(probe);
            if (entity == null) {
                continue;
            }
            ManaPoolController pool = entity.controller.get(ManaPoolController.class, 0);
            if (pool == null || pool.mana() <= 0) {
                continue;
            }
            long taken = pool.extract(Math.min(budget, pool.mana()));
            if (taken > 0) {
                insert(taken);
                budget -= taken;
            }
            if (budget <= 0) {
                break;
            }
        }
    }

    /**
     * 这条接力链最终能不能送到真正的接收方（魔力池 / 符文祭坛）。
     *
     * <p>下游全是发射器、末端什么也没接到的话，传过去就是白费魔力，
     * 所以这种链条既不传魔力、也不放粒子。</p>
     */
    private boolean leadsToReceiver(int depth) {
        if (depth > 8) {
            return false;   // 防环
        }
        CEWorld ceWorld = world();
        BlockPos pos = position();
        if (ceWorld == null || pos == null) {
            return false;
        }
        var dir = facingDir();
        for (int step = 1; step <= FIND_RADIUS; step++) {
            BlockPos probe = new BlockPos(
                    pos.x() + dir.stepX() * step,
                    pos.y() + dir.stepY() * step,
                    pos.z() + dir.stepZ() * step);
            BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(probe);
            if (entity == null) {
                continue;
            }
            if (entity.controller.get(ManaPoolController.class, 0) != null) {
                return true;
            }
            if (entity.controller.get(RunicAltarController.class, 0) != null) {
                return true;
            }
            SpreaderController next = entity.controller.get(SpreaderController.class, 0);
            if (next != null && next != this) {
                return next.leadsToReceiver(depth + 1);
            }
        }
        return false;
    }

    /** 扫描附近的花，按 tick 预算抽取。 */
    private void pullFromFlowers(CEWorld ceWorld, BlockPos pos) {
        long budget = Math.min(PULL_PER_TICK, room());
        if (budget <= 0) {
            return;
        }
        for (int dx = -PULL_RADIUS; dx <= PULL_RADIUS && budget > 0; dx++) {
            for (int dz = -PULL_RADIUS; dz <= PULL_RADIUS && budget > 0; dz++) {
                for (int dy = -3; dy <= 3 && budget > 0; dy++) {
                    BlockPos target = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(target);
                    if (entity == null) {
                        continue;
                    }
                    FlowerController flower = entity.controller.get(FlowerController.class, 0);
                    if (flower == null || flower.mana() <= 0) {
                        continue;
                    }
                    if (this.boundFlower != null) {
                        String key = target.x() + "," + target.y() + "," + target.z();
                        if (!this.boundFlower.equals(key)) {
                            continue;
                        }
                    }
                    long taken = flower.drain(Math.min(budget, flower.mana()));
                    if (taken > 0) {
                        insert(taken);
                        budget -= taken;
                    }
                }
            }
        }
    }

    // —— 视觉：隔几秒飞一颗魔力球 ——

    private void launchParticle(CEWorld ceWorld, BlockPos pos, ManaBlockController target) {
        org.bukkit.World bukkit = Platform.bukkit(ceWorld);
        var targetPos = target.position();
        if (bukkit == null || targetPos == null) {
            return;
        }
        this.particleFrom = new org.bukkit.Location(bukkit,
                pos.x() + 0.5, pos.y() + 0.75, pos.z() + 0.5);
        this.particleTo = new org.bukkit.Location(bukkit,
                targetPos.x() + 0.5, targetPos.y() + 0.6, targetPos.z() + 0.5);
        this.particleProgress = 0;
        this.particleCooldown = PARTICLE_GAP;
    }

    /** 推进飞行粒子：沿直线匀速走完，到终点炸一小簇。 */
    private void advanceParticle() {
        if (this.particleProgress < 0 || this.particleFrom == null || this.particleTo == null) {
            return;
        }
        org.bukkit.World world = this.particleFrom.getWorld();
        if (world == null) {
            this.particleProgress = -1;
            return;
        }
        double t = Math.min(1.0, this.particleProgress / (double) PARTICLE_TRAVEL);
        var point = this.particleFrom.clone().add(
                (this.particleTo.getX() - this.particleFrom.getX()) * t,
                (this.particleTo.getY() - this.particleFrom.getY()) * t,
                (this.particleTo.getZ() - this.particleFrom.getZ()) * t);
        try {
            world.spawnParticle(org.bukkit.Particle.END_ROD, point, 2, 0.02, 0.02, 0.02, 0.0);
        } catch (Throwable ignored) {
            // 粒子缺失不影响功能
        }
        this.particleProgress++;
        if (this.particleProgress > PARTICLE_TRAVEL) {
            try {
                world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME,
                        this.particleTo, 6, 0.25, 0.15, 0.25, 0.01);
            } catch (Throwable ignored) {
                // 同上
            }
            this.particleProgress = -1;
        }
    }
}
