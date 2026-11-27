package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 精灵传送门核心（Alfheim Portal）。
 *
 * <p>上游结构：5×5 活木框架底边中央放这个核心，11×11×11 范围内至少 2 个自然水晶
 * 和 2 个魔力池。手持森林法杖右键核心点火，消耗 20 万魔力打开传送门。</p>
 */
public final class AlfheimPortalBehavior extends BukkitBlockBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "alfheim_portal");
    private static final Key GATE_KEY = Key.of("botania", "alfheim_portal_block");

    /** 开门消耗。 */
    private static final long MANA_OPENING = 200_000L;
    /** 搜索自然水晶/魔力池的范围（半径）。 */
    private static final int SEARCH_RANGE = 5;
    /** 至少需要几个自然水晶。 */
    private static final int MIN_PYLONS = 2;
    public static BlockBehaviorFactory<AlfheimPortalBehavior> factory(AlfheimTrade trade) {
        return (block, section) -> new AlfheimPortalBehavior(trade, block);
    }

    private final AlfheimTrade trade;

    @Override
    public void initControllerId(int id) {
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new AlfheimPortalController(blockEntity, this.trade);
    }

    private AlfheimPortalBehavior(AlfheimTrade trade, BlockDefinition block) {
        super(block);
        this.trade = trade;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return InteractionResult.PASS;
        }
        var world = context.getLevel();
        var pos = context.getClickedPos();
        var bukkitWorld = Platform.bukkit(world);
        if (bukkitWorld == null) {
            return InteractionResult.PASS;
        }
        var ceWorld = BukkitAdaptor.adapt(bukkitWorld).storageWorld();
        if (ceWorld == null) {
            return InteractionResult.PASS;
        }

        var coreEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        var controller = coreEntity == null ? null
                : coreEntity.controller.get(AlfheimPortalController.class, 0);
        boolean isOpen = controller != null && controller.isOpen();
        boolean wand = BotaniaItems.holdingWand(bukkit);
        if (wand && isOpen) {
            controller.closePortal(bukkitWorld, 1f);
            Platform.send(bukkit, "<gray>精灵传送门已关闭");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        int countX = countFrameInPlane(ceWorld, pos, true);
        int countZ = countFrameInPlane(ceWorld, pos, false);
        int frame = Math.max(countX, countZ);
        if (frame < 11) {
            Platform.send(bukkit, "<red>结构不完整：核心周围要 11 块活木"
                    + "（5×5 框架，四角与底边角全空，现在 " + frame + "/11）");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        List<ManaPoolController> pools = findPools(ceWorld, pos);
        if (pools.size() < MIN_PYLONS) {
            Platform.send(bukkit, "<red>有效组合不够：需要 " + MIN_PYLONS
                    + " 组「魔力池 + 上方自然水晶」（现在 " + pools.size() + "）· 距离核心 5 格内");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (wand) {
            long total = pools.stream().mapToLong(ManaPoolController::mana).sum();
            if (total < MANA_OPENING) {
                Platform.send(bukkit, "<red>魔力不够开门：需要 "
                        + Platform.mana(MANA_OPENING) + "（现在 " + Platform.mana(total) + "）");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            boolean alongX = countX >= countZ;
            if (controller == null || !openPortal(ceWorld, bukkitWorld, pos, alongX)) {
                Platform.send(bukkit, "<red>精灵传送门无法开启：请确保通道畅通且区块已加载");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            drainPools(pools, MANA_OPENING);
            controller.setOpen(true, alongX);
            Platform.send(bukkit, "<green>精灵传送门已开启 <gray>· 往中间 3×3 丢物品换货");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        Platform.send(bukkit, "<gray>精灵传送门核心 <dark_gray>（"
                + (isOpen ? "已开启 · 往中间 3×3 丢物品换货" : "已关闭 · 法杖点火开启") + "）");
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /** 只查相交区块里已加载的方块实体，保留原来的 x/y/z 扣魔顺序。 */
    static List<ManaPoolController> findPools(CEWorld world, BlockPos core) {
        List<ManaPoolController> pools = new ArrayList<>();
        for (int chunkX = (core.x() - SEARCH_RANGE) >> 4; chunkX <= (core.x() + SEARCH_RANGE) >> 4; chunkX++) {
            for (int chunkZ = (core.z() - SEARCH_RANGE) >> 4; chunkZ <= (core.z() + SEARCH_RANGE) >> 4; chunkZ++) {
                var chunk = world.getChunkAtIfLoaded(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (var entity : chunk.blockEntities()) {
                    BlockPos pos = entity.pos;
                    if (Math.abs(pos.x() - core.x()) > SEARCH_RANGE
                            || Math.abs(pos.y() - core.y()) > SEARCH_RANGE
                            || Math.abs(pos.z() - core.z()) > SEARCH_RANGE) {
                        continue;
                    }
                    var pool = entity.controller.get(ManaPoolController.class, 0);
                    if (pool == null) {
                        continue;
                    }
                    var pylon = world.getBlockStateAtIfLoaded(pos.x(), pos.y() + 1, pos.z());
                    if (pylon != null && !pylon.isEmpty()
                            && pylon.owner().value().id().value().contains("pylon")) {
                        pools.add(pool);
                    }
                }
            }
        }
        pools.sort(Comparator.comparingInt((ManaPoolController pool) -> pool.position().x())
                .thenComparingInt(pool -> pool.position().y())
                .thenComparingInt(pool -> pool.position().z()));
        return pools;
    }

    static long drainPools(List<ManaPoolController> pools, long needed) {
        long drained = 0;
        for (var pool : pools) {
            drained += pool.extract(needed - drained);
            if (drained >= needed) {
                break;
            }
        }
        return drained;
    }

    /**
     * 官方结构：5×5 竖直平面。
     *
     * <pre>
     *   .   活木  微光  活木  .
     *   活木  .     .    .    活木
     *   微光  .     .    .    微光
     *   活木  .     .    .    活木
     *   .   活木  核心  活木  .
     * </pre>
     *
     * 中间 3×3 是中空的（传送门特效区），核心在底边中间。
     *
     * @return 框架上活木/微光活木的数量（满分 11）
     */
    /** 在指定平面上数框架（alongX=true 表示框架沿 x 展开）。 */
    private static int countFrameInPlane(CEWorld ceWorld, BlockPos core, boolean alongX) {
        int count = 0;
        for (int a = -2; a <= 2; a++) {
            for (int dy = 0; dy <= 4; dy++) {
                // 中间 3×3 是空的
                if (a >= -1 && a <= 1 && dy >= 1 && dy <= 3) {
                    continue;
                }
                // 底边中间是核心本身
                if (a == 0 && dy == 0) {
                    continue;
                }
                // 四个角不需要
                if (Math.abs(a) == 2 && (dy == 0 || dy == 4)) {
                    continue;
                }
                var probe = alongX
                        ? new BlockPos(core.x() + a, core.y() + dy, core.z())
                        : new BlockPos(core.x(), core.y() + dy, core.z() + a);
                var st = ceWorld.getBlockStateAtIfLoaded(probe);
                if (st != null && !st.isEmpty()) {
                    String id = st.owner().value().id().value();
                    if (id.contains("livingwood")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** 中空 3×3 区域（传送门特效区），alongX 指定框架平面朝向。 */
    public static List<BlockPos> portalInterior(BlockPos core, boolean alongX) {
        List<BlockPos> out = new ArrayList<>(9);
        for (int a = -1; a <= 1; a++) {
            for (int dy = 1; dy <= 3; dy++) {
                out.add(alongX
                        ? new BlockPos(core.x() + a, core.y() + dy, core.z())
                        : new BlockPos(core.x(), core.y() + dy, core.z() + a));
            }
        }
        return out;
    }

    /** 旧存档没有记录朝向，优先从两侧现存的通道方块恢复。 */
    static boolean storedFrameAlongX(CEWorld world, BlockPos core) {
        int gatesX = countGateSides(world, core, true);
        int gatesZ = countGateSides(world, core, false);
        if (gatesX != gatesZ) {
            return gatesX > gatesZ;
        }
        return countFrameInPlane(world, core, true) >= countFrameInPlane(world, core, false);
    }

    private static int countGateSides(CEWorld world, BlockPos core, boolean alongX) {
        int count = 0;
        for (var pos : portalInterior(core, alongX)) {
            if ((pos.x() != core.x() || pos.z() != core.z()) && isGate(world.getBlockStateAtIfLoaded(pos))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isGate(ImmutableBlockState state) {
        return state != null && !state.isEmpty() && GATE_KEY.equals(state.owner().value().id());
    }

    /** 放置时直接选 CraftEngine 状态，通道方块本身没有方块实体控制器。 */
    private static boolean openPortal(CEWorld ceWorld, World world, BlockPos core, boolean alongX) {
        var gate = CraftEngineBlocks.byId(GATE_KEY);
        if (gate == null) {
            return false;
        }
        var state = gate.defaultState();
        var axisProperty = state.getProperty(BlockStates.ALONG_X);
        if (axisProperty != null) {
            state = ImmutableBlockState.with(state, axisProperty, !alongX);
        }
        var interior = portalInterior(core, alongX);
        var previous = new ArrayList<ImmutableBlockState>(interior.size());
        for (var pos : interior) {
            if (ceWorld.getChunkAtIfLoaded(pos) == null) {
                return false;
            }
            var block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            var existing = CraftEngineBlocks.getCustomBlockState(block);
            if (!block.getType().isAir() && !isGate(existing)) {
                return false;
            }
            previous.add(existing);
        }
        for (int i = 0; i < interior.size(); i++) {
            var pos = interior.get(i);
            if (!CraftEngineBlocks.place(new Location(world, pos.x(), pos.y(), pos.z()), state, false)) {
                for (int j = 0; j < i; j++) {
                    var replaced = interior.get(j);
                    var prior = previous.get(j);
                    var block = world.getBlockAt(replaced.x(), replaced.y(), replaced.z());
                    if (isGate(prior)) {
                        CraftEngineBlocks.place(block.getLocation(), prior, false);
                    } else {
                        CraftEngineBlocks.remove(block);
                    }
                }
                return false;
            }
        }
        var center = effectCenter(world, core);
        world.spawnParticle(Particle.HAPPY_VILLAGER, center, 40,
                alongX ? 1.2 : 0.1, 1.2, alongX ? 0.1 : 1.2, 0);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        return true;
    }

    static void clearPortal(World world, BlockPos core, boolean alongX) {
        for (var pos : portalInterior(core, alongX)) {
            var block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (isGate(CraftEngineBlocks.getCustomBlockState(block))) {
                CraftEngineBlocks.remove(block);
            }
        }
    }

    static Location effectCenter(World world, BlockPos core) {
        return new Location(world, core.x() + 0.5, core.y() + 2.0, core.z() + 0.5);
    }
}
