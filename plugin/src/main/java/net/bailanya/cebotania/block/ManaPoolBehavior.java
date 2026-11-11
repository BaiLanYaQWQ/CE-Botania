package net.bailanya.cebotania.block;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import net.bailanya.cebotania.recipe.RecipeBook;
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
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.inventory.ItemStack;

/**
 * 魔力池：右键查看储量；手持可灌注物右键即执行灌注。
 *
 * <p>上游是把物品丢进池子再等，这里改成手持右键 —— 交互更直接，
 * 规则（输入 + 魔力 → 产出）完全按上游 datagen 的配方表。</p>
 */
public final class ManaPoolBehavior extends BukkitBlockBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "mana_pool");

    public static BlockBehaviorFactory<ManaPoolBehavior> factory(CeBotaniaPlugin plugin) {
        return (block, section) -> new ManaPoolBehavior(plugin, block);
    }

    private final CeBotaniaPlugin plugin;
    private int controllerId;

    private ManaPoolBehavior(CeBotaniaPlugin plugin, BlockDefinition block) {
        super(block);
        this.plugin = plugin;
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new ManaPoolController(blockEntity);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        ManaPoolController pool = controllerAt(context.getLevel(), context.getClickedPos());
        if (pool == null) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer().platformPlayer() instanceof org.bukkit.entity.Player bukkit) {
            if (!BotaniaItems.holdingWand(bukkit)) {
                return InteractionResult.PASS;
            }
            Platform.send(bukkit, "<aqua>魔力池 <white>" + Platform.mana(pool.mana())
                    + " <gray>/ <white>" + Platform.mana(pool.capacity())
                    + " <dark_gray>(" + Math.round(pool.fillRatio() * 100) + "%)");
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        ManaPoolController pool = controllerAt(context.getLevel(), context.getClickedPos());
        if (player == null || pool == null) {
            return InteractionResult.PASS;
        }
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return InteractionResult.PASS;
        }
        ItemStack held = bukkit.getInventory().getItemInMainHand();
        // 手持森林法杖：查看魔力（玩家手持物品时走的是 useOnBlock，不是 useWithoutItem）
        if (BotaniaItems.holdingWand(bukkit)) {
            Platform.send(bukkit, "<aqua>魔力池 <white>" + Platform.mana(pool.mana())
                    + " <gray>/ <white>" + Platform.mana(pool.capacity())
                    + " <dark_gray>(" + Math.round(pool.fillRatio() * 100) + "%)");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // 手持火花 → 安装
        if (BotaniaItems.idOf(held).equals("botania:spark")) {
            if (pool.hasSpark()) {
                Platform.send(bukkit, "<gray>这个池子已经有火花了");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            pool.installSpark();
            held.setAmount(held.getAmount() - 1);
            bukkit.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
            Platform.send(bukkit, "<light_purple>火花 #" + pool.sparkId()
                    + " <gray>已安装 · 会自动给附近 6 格内的耗魔方块供魔");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // 手持魔钢装备：从池子里给它灌魔力（装备用它顶耐久）
        if (net.bailanya.cebotania.listener.ManaRepair.isManasteel(held)) {
            var repair = this.plugin.manaRepair();
            long room = net.bailanya.cebotania.listener.ManaRepair.CAPACITY - repair.stored(held);
            if (room <= 0) {
                Platform.send(bukkit, "<green>这件装备的魔力已经满了");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            long moved = Math.min(room, pool.mana());
            if (moved <= 0) {
                Platform.send(bukkit, "<red>池子里没有魔力");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            long charged = repair.charge(held, moved);
            pool.extract(charged);
            bukkit.getInventory().setItemInMainHand(held);
            Platform.send(bukkit, "<aqua>已充能 <white>" + Platform.mana(charged)
                    + " <gray>· 装备现在有 <white>"
                    + Platform.mana(repair.stored(held)) + " <gray>魔力");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        String heldId = BotaniaItems.idOf(held);
        if (heldId.isEmpty()) {
            return InteractionResult.PASS;
        }
        RecipeBook.Infusion recipe = this.plugin.recipes().matchInfusion(heldId);
        if (recipe == null) {
            return InteractionResult.PASS;
        }
        // 有些灌注（比如"煤炭翻倍"）需要池子下面垫一块催化剂方块
        if (recipe.catalystBlock() != null && !hasCatalyst(pool, recipe.catalystBlock())) {
            String name = BotaniaItems.displayName(recipe.catalystBlock());
            Platform.send(bukkit, "<red>这个配方需要池子下面垫一块 <white>" + name);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (pool.mana() < recipe.mana()) {
            Platform.send(bukkit, "<red>魔力不足：需要 <white>" + Platform.mana(recipe.mana())
                    + " <red>，池中有 <white>" + Platform.mana(pool.mana()));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        ItemStack result = BotaniaItems.create(recipe.output(), recipe.count());
        if (result == null) {
            return InteractionResult.PASS;
        }
        pool.extract(recipe.mana());
        held.setAmount(held.getAmount() - 1);
        bukkit.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
        var leftovers = bukkit.getInventory().addItem(result);
        for (ItemStack leftover : leftovers.values()) {
            bukkit.getWorld().dropItemNaturally(bukkit.getLocation(), leftover);
        }
        Platform.send(bukkit, "<aqua>灌注完成 <gray>(-" + Platform.mana(recipe.mana()) + " mana)");
        bukkit.playSound(bukkit.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.6f);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }


    /** 池子正下方是不是指定的催化剂方块。 */
    private boolean hasCatalyst(ManaPoolController pool, String expected) {
        var world = pool.world();
        var pos = pool.position();
        if (world == null || pos == null) {
            return false;
        }
        var below = Platform.blockAt(world,
                new net.momirealms.craftengine.core.world.BlockPos(
                        pos.x(), pos.y() - 1, pos.z()));
        if (below == null) {
            return false;
        }
        var state = CraftEngineBlocks.getCustomBlockState(below);
        if (state != null && !state.isEmpty()) {
            return expected.equals(state.owner().value().id().asString());
        }
        return expected.equals(below.getType().getKey().toString());
    }

    private ManaPoolController controllerAt(World world, BlockPos pos) {
        if (world == null) {
            return null;
        }
        BlockEntity entity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.getAt(ManaPoolController.class, this.controllerId);
    }
}
