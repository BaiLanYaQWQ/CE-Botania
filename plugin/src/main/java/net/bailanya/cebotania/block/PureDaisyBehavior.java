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
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;

/**
 * 纯洁雏菊的方块行为：装方块实体 + 右键提示它能干什么。
 */
public final class PureDaisyBehavior extends GroundPlantBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "pure_daisy");

    public static BlockBehaviorFactory<PureDaisyBehavior> pureDaisyFactory() {
        return (block, section) -> new PureDaisyBehavior(block);
    }

    private int controllerId;

    private PureDaisyBehavior(BlockDefinition block) {
        super(block);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new PureDaisyController(blockEntity);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return InteractionResult.PASS;
        }
        if (!BotaniaItems.holdingWand(bukkit)) {
            return InteractionResult.PASS;
        }
        Platform.send(bukkit, "<green>纯洁雏菊 <gray>· 把周围的方块净化（石头→活石、原木→活木）");
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private PureDaisyController controllerAt(World world, BlockPos pos) {
        if (world == null) {
            return null;
        }
        BlockEntity entity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.getAt(PureDaisyController.class, this.controllerId);
    }
}
