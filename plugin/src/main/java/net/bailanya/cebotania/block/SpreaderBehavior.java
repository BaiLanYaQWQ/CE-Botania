package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
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
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;

/**
 * 魔力发射器方块行为：装 ticker，右键看缓存。
 */
public final class SpreaderBehavior extends BukkitBlockBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "mana_spreader");

    /**
     * 放置瞬间记下玩家朝向，等方块实体建好后取走。
     *
     * <p>CE 的放置流程是"先算状态、再建实体"，实体构造时拿不到 BlockPlaceContext，
     * 所以先用这个待办表过一手。</p>
     */
    private static final java.util.Map<String, Integer> PENDING_FACING =
            new java.util.concurrent.ConcurrentHashMap<>();

    static String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** 取走某坐标的待应用朝向；没有就返回 null。 */
    public static Integer takePendingFacing(int x, int y, int z) {
        return PENDING_FACING.remove(posKey(x, y, z));
    }

    /** Bukkit 的朝向 → 我们的 0=北 1=东 2=南 3=西。 */
    private static int facingOf(Player player) {
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return 0;
        }
        return switch (bukkit.getFacing()) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(
            net.momirealms.craftengine.core.world.context.BlockPlaceContext context,
            ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player != null) {
            var pos = context.getClickedPos();
            PENDING_FACING.put(posKey(pos.x(), pos.y(), pos.z()), facingOf(player));
        }
        return state;
    }

    public static BlockBehaviorFactory<SpreaderBehavior> factory() {
        return (block, section) -> new SpreaderBehavior(block);
    }

    private int controllerId;

    private SpreaderBehavior(BlockDefinition block) {
        super(block);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new SpreaderController(blockEntity);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        return useWithoutItem(context, state);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        SpreaderController controller = controllerAt(context.getLevel(), context.getClickedPos());
        if (player == null || controller == null) {
            return InteractionResult.PASS;
        }
        if (player.platformPlayer() instanceof org.bukkit.entity.Player bukkit) {
            if (!BotaniaItems.holdingWand(bukkit)) {
                return InteractionResult.PASS;
            }
            if (bukkit.isSneaking()) {
                // 潜行 + 森林法杖：有选中的花就绑定，否则转 90°
                String selected = net.bailanya.cebotania.flower.WandBinding.take(bukkit);
                if (selected != null) {
                    controller.bindFlower(selected);
                    Platform.send(bukkit, "<green>已绑定花 <gray>" + selected);
                    return InteractionResult.SUCCESS_AND_CANCEL;
                }
                Platform.send(bukkit, "<light_purple>发射方向 → " + controller.rotate());
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            String bound = controller.boundFlower() == null
                    ? "吸附近所有花" : "绑定 " + controller.boundFlower();
            Platform.send(bukkit, "<light_purple>发射器 朝向" + controller.facingName()
                    + " <gray>" + bound + " <light_purple>缓存 <white>"
                    + Platform.mana(controller.mana()) + " <gray>/ <white>"
                    + Platform.mana(controller.capacity()));
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private SpreaderController controllerAt(World world, BlockPos pos) {
        if (world == null) {
            return null;
        }
        BlockEntity entity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.getAt(SpreaderController.class, this.controllerId);
    }
}
