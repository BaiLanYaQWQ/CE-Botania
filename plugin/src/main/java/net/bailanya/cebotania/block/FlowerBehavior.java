package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.inventory.ItemStack;

/**
 * 花的方块行为：右键查看魔力、把自己挂到 CE 的 ticker 上。
 *
 * <p>资源包里 {@code behavior: {type: botania:flower}} 指到这里。</p>
 */
public final class FlowerBehavior extends GroundPlantBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "flower");

    public static BlockBehaviorFactory<FlowerBehavior> flowerFactory() {
        return (block, section) -> new FlowerBehavior(block);
    }

    private final String flowerId;
    private int controllerId;

    private FlowerBehavior(BlockDefinition block) {
        super(block);
        this.flowerId = block.id().value();
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new FlowerController(blockEntity, this.flowerId);
    }

    /** 手持燃料右键：直接喂给产魔花（掉落物拾取那条路在某些核心上不稳）。 */
    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        FlowerController controller = controllerAt(context.getLevel(), context.getClickedPos());
        if (player == null || controller == null || controller.type() == null) {
            return InteractionResult.PASS;
        }
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return InteractionResult.PASS;
        }
        if (BotaniaItems.holdingWand(bukkit)) {
            // 森林法杖：看魔力 / 潜行选中
            if (bukkit.isSneaking()) {
                var wandPos = context.getClickedPos();
                net.bailanya.cebotania.flower.WandBinding.select(bukkit,
                        wandPos.x() + "," + wandPos.y() + "," + wandPos.z());
                Platform.send(bukkit, "<green>已选中这朵花 <gray>· 再潜行右键发射器完成绑定");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            // actionbar 会被下一条消息顶掉，所以魔力 + 功能说明合成一条发
            Platform.send(bukkit, "<green>魔力 <white>" + Platform.mana(controller.mana())
                    + "<gray>/<white>" + Platform.mana(controller.capacity())
                    + " <dark_gray>| <aqua>" + controller.type().describe());
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        var held = bukkit.getInventory().getItemInMainHand();
        String logic = controller.type().logic;
        boolean consumable = switch (logic) {
            case "fuel" -> net.bailanya.cebotania.flower.FlowerLogic.isFuel(held.getType());
            case "food" -> net.bailanya.cebotania.flower.FlowerLogic.isFood(held.getType());
            default -> false;
        };
        if (!consumable) {
            return InteractionResult.PASS;
        }
        long produced = controller.type().manaPerAction * ("fuel".equals(logic) ? 30 : 10);
        controller.insert(produced);
        held.setAmount(held.getAmount() - 1);
        bukkit.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
        Platform.send(bukkit, "<green>投喂成功 <gray>+"
                + Platform.mana(produced) + " 魔力");
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        FlowerController controller = controllerAt(context.getLevel(), context.getClickedPos());
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (player.platformPlayer() instanceof org.bukkit.entity.Player bukkit) {
            // 只有手持森林法杖才报魔力，空手点就是普通右键
            if (!BotaniaItems.holdingWand(bukkit)) {
                return InteractionResult.PASS;
            }
            if (bukkit.isSneaking()) {
                // 潜行 + 森林法杖：把这朵花选为绑定目标
                var pos = context.getClickedPos();
                net.bailanya.cebotania.flower.WandBinding.select(bukkit,
                        pos.x() + "," + pos.y() + "," + pos.z());
                Platform.send(bukkit, "<green>已选中这朵花 <gray>· 再潜行右键发射器完成绑定");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            Platform.send(bukkit, "<green>魔力 <white>" + Platform.mana(controller.mana())
                    + "<gray>/<white>" + Platform.mana(controller.capacity()));
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private FlowerController controllerAt(net.momirealms.craftengine.core.world.World world,
                                          net.momirealms.craftengine.core.world.BlockPos pos) {
        if (world == null) {
            return null;
        }
        BlockEntity entity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.getAt(FlowerController.class, this.controllerId);
    }
}
