package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.Platform;
import org.bukkit.Location;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力发射器：从附近的产魔花吸魔力，攒够一发就往魔力池打。
 *
 * <p>上游的发射器有朝向和棱镜，这里先把"吸—攒—打"这条主链路做通：
 * 吸附近 6 格内所有花的缓冲，攒够 {@link #BURST} 就往 12 格内最近的池子灌。</p>
 */
public final class SpreaderController extends ManaBlockController {

    private static final String ROOT = "cebotania:spreader";
    private static final String COOLDOWN_KEY = "cooldown";

    /** 缓存上限 = 一发。 */
    public static final long CAPACITY = 1000L;
    /** 一次打出去的上限（一小步，不是攒满才射）。 */
    public static final long BURST = 200L;
    /** 吸花的半径。 */
    private static final int PULL_RADIUS = 6;
    /** 找池子的半径。 */
    private static final int FIND_RADIUS = 12;
    /** 每 tick 最多从花那里吸多少。 */
    private static final long PULL_PER_TICK = 200L;
    /** 两次发射之间的刻数：小步快跑，池子数字涨得肉眼可见。 */
    private static final int BURST_INTERVAL = 10;

    private int cooldown;

    /** 朝向：直接读方块状态的 facing 属性（模型也跟着转）。 */
    public net.momirealms.craftengine.core.util.Direction facingDir() {
        BlockEntity entity = blockEntity();
        if (entity != null) {
            var state = entity.blockState();
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

    /** 给提示语用的中文方向名。 */
    public String facingName() {
        return switch (facingDir()) {
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            default -> "北";
        };
    }

    /** 被森林法杖绑定过来的花（坐标）；null = 不挑食，吸附近所有的花。 */
    private String boundFlower;

    public String boundFlower() {
        return this.boundFlower;
    }

    public void bindFlower(String position) {
        this.boundFlower = position;
        markChanged();
    }

    /** 森林法杖右键：顺时针转 90°（同时把方块状态和模型一起换掉）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String rotate() {
        BlockEntity entity = blockEntity();
        if (entity == null || entity.world == null) {
            return facingName();
        }
        var state = entity.blockState();
        if (state == null) {
            return facingName();
        }
        var property = state.getProperty("facing");
        if (property == null) {
            return facingName();
        }
        var next = state.cycle((net.momirealms.craftengine.core.block.property.Property) property);
        entity.world.setBlockStateAtIfLoaded(entity.pos, next);
        entity.setBlockState(next);
        markChanged();
        return facingName();
    }

    /**
     * 朝这个方向找第一个能收魔力的方块。
     *
     * <p>不只是魔力池 —— 符文祭坛同样要吃魔力（攒够配方需求就自动合成）。</p>
     */
    private ManaBlockController targetAhead(CEWorld ceWorld, BlockPos pos) {
        var dir = facingDir();
        for (int step = 1; step <= FIND_RADIUS; step++) {
            BlockPos probe = new BlockPos(
                    pos.x() + dir.stepX() * step, pos.y() + dir.stepY() * step,
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
        }
        return null;
    }

    public SpreaderController(BlockEntity blockEntity) {
        super(blockEntity, CAPACITY);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

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

    @Override
    public void serverTick() {
        net.momirealms.craftengine.core.world.CEWorld world = world();
        net.momirealms.craftengine.core.world.BlockPos pos = position();
        if (world == null || pos == null) {
            return;
        }
        tick(world, pos);
    }

    private int tickDebug;

    /** 诊断用。 */
    public int ticks() {
        return this.tickDebug;
    }

    public void tick(CEWorld ceWorld, BlockPos pos) {
        this.tickDebug++;
        org.bukkit.World world = Platform.bukkit(ceWorld);
        if (world == null) {
            return;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        // 1) 缓存没满：从附近的花吸
        if (room() > 0) {
            pullFromFlowers(ceWorld, pos);
        }
        // 2) 攒够一发且冷却好了：往池子打
        if (this.mana > 0 && this.cooldown <= 0) {
            ManaBlockController pool = targetAhead(ceWorld, pos);
            if (pool != null) {
                long sent = pool.insert(Math.min(this.mana, BURST));
                if (sent > 0) {
                    extract(sent);
                    this.cooldown = BURST_INTERVAL;
                    spawnBurst(ceWorld, pos, pool);
                }
            }
        }
    }

    /** 扫描附近的花，按 tick 预算抽取；花是独立方块实体，用 CE 的区块缓存找。 */
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
                    // 绑定过就只认那一朵
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

    /** 最近的魔力池（同世界、半径内）。 */
    private ManaPoolController nearestPool(CEWorld ceWorld, BlockPos pos) {
        ManaPoolController best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -FIND_RADIUS; dx <= FIND_RADIUS; dx++) {
            for (int dz = -FIND_RADIUS; dz <= FIND_RADIUS; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    BlockPos target = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(target);
                    if (entity == null) {
                        continue;
                    }
                    ManaPoolController pool = entity.controller.get(ManaPoolController.class, 0);
                    if (pool == null || pool.room() <= 0) {
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

    /**
     * 发射时画一条从发射器到池子的粒子流。
     *
     * <p>注意不能走 {@code pool.blockEntity()} —— 它可能是 null（方块实体正在
     * 重载/被缓存挡住），那样这里会直接 return，玩家就什么都看不见。
     * 用控制器自己缓存的 world/position 稳一点。</p>
     */
    private void spawnBurst(CEWorld ceWorld, BlockPos pos, ManaBlockController pool) {
        org.bukkit.World bukkit = Platform.bukkit(ceWorld);
        var poolPos = pool.position();
        if (bukkit == null || poolPos == null) {
            return;
        }
        Location from = new Location(bukkit, pos.x() + 0.5, pos.y() + 0.75, pos.z() + 0.5);
        Location to = new Location(bukkit, poolPos.x() + 0.5, poolPos.y() + 0.6, poolPos.z() + 0.5);
        try {
            // 直线：发射器 → 池子，按方向等距撒点（上游就是一束直线魔力）
            int steps = Math.max(8, (int) (from.distance(to) * 3));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                Location point = from.clone().add(
                        (to.getX() - from.getX()) * t,
                        (to.getY() - from.getY()) * t,
                        (to.getZ() - from.getZ()) * t);
                bukkit.spawnParticle(org.bukkit.Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
            bukkit.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, to, 5, 0.25, 0.15, 0.25, 0.01);
        } catch (Throwable ignored) {
            // 粒子 API 在个别核心上可能缺，不影响功能
        }
    }
}
