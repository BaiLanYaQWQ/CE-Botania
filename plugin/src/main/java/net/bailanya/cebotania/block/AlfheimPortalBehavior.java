package net.bailanya.cebotania.block;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 精灵传送门核心（Alfheim Portal）。
 *
 * <p>上游结构：3×3 活木框架中间放这个核心，11×11 范围内至少 2 个自然水晶
 * 和 2 个魔力池。手持森林法杖右键核心点火，消耗 20 万魔力打开传送门。</p>
 */
public final class AlfheimPortalBehavior extends BukkitBlockBehavior implements
        net.momirealms.craftengine.core.block.behavior.EntityBlock {

    public static final Key KEY = Key.of("botania", "alfheim_portal");

    /** 开门消耗。 */
    private static final long MANA_OPENING = 200_000L;
    /** 搜索自然水晶/魔力池的范围（半径）。 */
    private static final int SEARCH_RANGE = 5;
    /** 至少需要几个自然水晶。 */
    private static final int MIN_PYLONS = 2;
    /** 开门状态存在方块实体的 PDC 里。 */
    private static final org.bukkit.NamespacedKey PORTAL_OPEN =
            new org.bukkit.NamespacedKey("cebotania", "portal_open");

    public static BlockBehaviorFactory<AlfheimPortalBehavior> factory(CeBotaniaPlugin plugin) {
        return (block, section) -> new AlfheimPortalBehavior(plugin, block);
    }

    private final CeBotaniaPlugin plugin;
    private int controllerId;

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public net.momirealms.craftengine.core.block.entity.BlockEntityController
            createBlockEntityController(net.momirealms.craftengine.core.block.entity.BlockEntity blockEntity) {
        return new AlfheimPortalController(blockEntity);
    }

    private AlfheimPortalBehavior(CeBotaniaPlugin plugin, BlockDefinition block) {
        super(block);
        this.plugin = plugin;
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
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(bukkitWorld).storageWorld();
        if (ceWorld == null) {
            return InteractionResult.PASS;
        }

        // 1) 结构：3×3 活木框架（8 块活木，核心在中间）
        int frame = countFrame(ceWorld, pos);
        if (frame < 11) {
            Platform.send(bukkit, "<red>结构不完整：核心周围要 11 块活木"
                    + "（5×5 框架，四角与底边角全空，现在 " + frame + "/11）");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 2) 找「魔力池 + 正上方自然水晶」的组合（上游：水晶坐在池子上取魔）
        java.util.List<ManaPoolController> pools = new java.util.ArrayList<>();
        int pairs = 0;
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -SEARCH_RANGE; dy <= SEARCH_RANGE; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    var probe = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    var entity = ceWorld.getBlockEntityAtIfLoaded(probe);
                    if (entity == null) {
                        continue;
                    }
                    var pool = entity.controller.get(ManaPoolController.class, 0);
                    if (pool == null) {
                        continue;
                    }
                    // 池子上方必须是自然水晶
                    var above = new BlockPos(probe.x(), probe.y() + 1, probe.z());
                    var aboveBlock = Platform.blockAt(ceWorld, above);
                    boolean hasPylon = false;
                    if (aboveBlock != null) {
                        var st = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                                .getCustomBlockState(aboveBlock);
                        if (st != null && !st.isEmpty()
                                && st.owner().value().id().value().contains("pylon")) {
                            hasPylon = true;
                        }
                    }
                    if (hasPylon) {
                        pairs++;
                        pools.add(pool);
                    }
                }
            }
        }
        if (pairs < MIN_PYLONS) {
            Platform.send(bukkit, "<red>有效组合不够：需要 " + MIN_PYLONS
                    + " 组「魔力池 + 上方自然水晶」（现在 " + pairs + "）· 距离核心 5 格内");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        var coreEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        var controller = coreEntity == null ? null
                : coreEntity.controller.get(AlfheimPortalController.class, 0);
        boolean isOpen = controller != null && controller.isOpen();

        // 3) 手持森林法杖 → 开/关切换
        if (BotaniaItems.holdingWand(bukkit)) {
            var coreBlock = Platform.blockAt(ceWorld, pos);
            if (isOpen) {
                closePortal(ceWorld, bukkitWorld, pos, coreBlock);
                if (controller != null) {
                    controller.setOpen(false);
                }
                if (coreEntity != null) {
                    BlockStates.setPortalOpen(coreEntity, false);
                }
                Platform.send(bukkit, "<gray>精灵传送门已关闭");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            // 打开：先查魔力
            long total = 0;
            for (var pool : pools) {
                total += pool.mana();
            }
            if (total < MANA_OPENING) {
                Platform.send(bukkit, "<red>魔力不够开门：需要 "
                        + Platform.mana(MANA_OPENING) + "（现在 " + Platform.mana(total) + "）");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            long need = MANA_OPENING;
            for (var pool : pools) {
                if (need <= 0) {
                    break;
                }
                long taken = pool.extract(need);
                need -= taken;
            }
            // 填充 3×3 玻璃板 + 绿色粒子
            openPortal(ceWorld, bukkitWorld, pos);
            if (controller != null) {
                controller.setOpen(true);
            }
            if (coreEntity != null) {
                BlockStates.setPortalOpen(coreEntity, true);
            }
            Platform.send(bukkit, "<green>精灵传送门已开启 <gray>· 往中间 3×3 丢物品换货");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 交易统一走"往 3×3 里丢物品"，手持点击只报状态
        Platform.send(bukkit, "<gray>精灵传送门核心 <dark_gray>（"
                + (isOpen ? "已开启 · 往中间 3×3 丢物品换货" : "已关闭 · 法杖点火开启") + "）");
        return InteractionResult.SUCCESS_AND_CANCEL;
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
     * @return 框架上活木/微光活木的数量（满分 16）
     */
    private int countFrame(CEWorld ceWorld, BlockPos core) {
        // 两个朝向都试：沿 x 展开 或 沿 z 展开
        int alongX = countFrameInPlane(ceWorld, core, true);
        int alongZ = countFrameInPlane(ceWorld, core, false);
        return Math.max(alongX, alongZ);
    }

    /** 在指定平面上数框架（alongX=true 表示框架沿 x 展开）。 */
    private int countFrameInPlane(CEWorld ceWorld, BlockPos core, boolean alongX) {
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
                var block = Platform.blockAt(ceWorld, probe);
                if (block == null || block.getType() == Material.AIR) {
                    continue;
                }
                var st = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                        .getCustomBlockState(block);
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
    public static java.util.List<BlockPos> portalInterior(BlockPos core, boolean alongX) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int dy = 1; dy <= 3; dy++) {
                out.add(alongX
                        ? new BlockPos(core.x() + a, core.y() + dy, core.z())
                        : new BlockPos(core.x(), core.y() + dy, core.z() + a));
            }
        }
        return out;
    }

    /** 读方块实体的 open 属性。 */
    private boolean isPortalOpen(net.momirealms.craftengine.core.block.entity.BlockEntity entity) {
        if (entity == null) {
            return false;
        }
        var state = entity.blockState();
        if (state == null) {
            return false;
        }
        var property = state.getProperty("open");
        if (property == null) {
            return false;
        }
        Object value = state.get(property);
        return Boolean.TRUE.equals(value);
    }

    /** 开门：3×3 填成绿色传送门方块 + 粒子。 */
    private void openPortal(CEWorld ceWorld, org.bukkit.World bukkitWorld, BlockPos core) {
        var gateKey = net.momirealms.craftengine.core.util.Key
                .of("botania", "alfheim_portal_block");
        boolean alongX = countFrameInPlane(ceWorld, core, true)
                >= countFrameInPlane(ceWorld, core, false);
        for (var p : portalInterior(core, alongX)) {
            try {
                net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                        .place(new org.bukkit.Location(bukkitWorld, p.x(), p.y(), p.z()),
                                gateKey, false);
            } catch (Throwable ignored) {
                // 放置失败跳过
            }
        }
        try {
            var loc = new org.bukkit.Location(bukkitWorld, core.x() + 0.5, core.y() + 2.0,
                    core.z() + 0.5);
            bukkitWorld.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 40,
                    1.2, 1.2, 0.1, 0);
            bukkitWorld.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 关门：清掉 3×3 绿色门方块。 */
    private void closePortal(CEWorld ceWorld, org.bukkit.World bukkitWorld, BlockPos core,
                             org.bukkit.block.Block coreBlock) {
        boolean alongX = countFrameInPlane(ceWorld, core, true)
                >= countFrameInPlane(ceWorld, core, false);
        for (var p : portalInterior(core, alongX)) {
            try {
                var b = bukkitWorld.getBlockAt(p.x(), p.y(), p.z());
                var st = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                        .getCustomBlockState(b);
                if (st != null && !st.isEmpty()
                        && st.owner().value().id().value().equals("alfheim_portal_block")) {
                    b.setType(Material.AIR, false);
                }
            } catch (Throwable ignored) {
                // 忽略
            }
        }
        if (coreBlock != null) {
            var loc = coreBlock.getLocation().add(0.5, 2.0, 0.5);
            try {
                bukkitWorld.spawnParticle(org.bukkit.Particle.SMOKE, loc, 30,
                        1.2, 1.2, 0.1, 0.01);
                bukkitWorld.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
            } catch (Throwable ignored) {
                // 忽略
            }
        }
    }
}
