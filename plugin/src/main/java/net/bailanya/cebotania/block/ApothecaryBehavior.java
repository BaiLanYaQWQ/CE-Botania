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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 花药台的交互：加水、投料、出锅。
 *
 * <p>注水用空水桶（水桶右键浇一次，桶还你）。投料后立刻按配方表判定，
 * 成了就在台子上方长出一朵花并把台子清空、放水。</p>
 */
public final class ApothecaryBehavior extends BukkitBlockBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "petal_apothecary");

    public static BlockBehaviorFactory<ApothecaryBehavior> factory(CeBotaniaPlugin plugin) {
        return (block, section) -> new ApothecaryBehavior(plugin, block);
    }

    private final CeBotaniaPlugin plugin;
    private int controllerId;

    private ApothecaryBehavior(CeBotaniaPlugin plugin, BlockDefinition block) {
        super(block);
        this.plugin = plugin;
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        ApothecaryController controller = new ApothecaryController(blockEntity);
        controller.applyVariantDefaults();
        return controller;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        ApothecaryController controller = controllerAt(context.getLevel(), context.getClickedPos());
        if (player == null || controller == null) {
            return InteractionResult.PASS;
        }
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return InteractionResult.PASS;
        }
        ItemStack held = bukkit.getInventory().getItemInMainHand();

        // 1) 水桶 → 注水
        if (held.getType() == Material.WATER_BUCKET) {
            if (controller.hasWater()) {
                Platform.send(bukkit, "<gray>这台已经有水了");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            controller.setWater(true);
            controller.items().clear();
            // 换成"有水"的方块：水面是烘在模型里的
            switchToWaterVariant(controller);
            held.setType(Material.BUCKET);
            bukkit.getInventory().setItemInMainHand(held);
            // 有水这件事得让玩家"看得见"：水面粒子 + 水声 + 提示三件套
            var center = bukkit.getLocation();
            BlockEntity watering = controller.blockEntity();
            if (watering != null) {
                var block = Platform.blockAt(watering.world, watering.pos);
                if (block != null) {
                    center = block.getLocation().add(0.5, 0.75, 0.5);
                }
            }
            bukkit.getWorld().spawnParticle(org.bukkit.Particle.SPLASH, center, 18,
                    0.35, 0.1, 0.35, 0.02);
            bukkit.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_POP, center, 8,
                    0.3, 0.05, 0.3, 0.01);
            bukkit.getWorld().playSound(center, org.bukkit.Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.1f);
            Platform.send(bukkit, "<aqua>注入清水 <gray>（台子里现在有水）");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 2) 空手 → 有东西就取回最后一件，没有就报状态
        String heldId = BotaniaItems.idOf(held);
        if (heldId.isEmpty()) {
            return withdraw(bukkit, controller);
        }

        // 3) 投料
        if (!controller.hasWater()) {
            Platform.send(bukkit, "<red>先给它注水（手持水桶右键）");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // 投料：只消耗 1 个。用 clone 操作，避免 CE 自定义物品栈被整体替换掉
        ItemStack after = held.clone();
        int before = after.getAmount();
        if (before <= 0) {
            return InteractionResult.PASS;
        }
        controller.addItem(heldId);
        if (before <= 1) {
            bukkit.getInventory().setItemInMainHand(null);
        } else {
            after.setAmount(before - 1);
            bukkit.getInventory().setItemInMainHand(after);
        }

        RecipeBook.Apothecary recipe = this.plugin.recipes().matchApothecary(controller.items());
        String added = BotaniaItems.displayNameForMessage(heldId);
        if (recipe == null) {
            Platform.send(bukkit, "<green>投入 " + added + " <gray>· 台子里现有：<white>"
                    + describeContents(controller.items())
                    + " <dark_gray>(" + controller.items().size() + " 样)");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        craft(bukkit, controller, recipe);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }


    /** 把普通花药台换成"有水"的方块（只有默认变体做了水面模型）。 */
    private void switchToWaterVariant(ApothecaryController controller) {
        var entity = controller.blockEntity();
        if (entity == null) {
            return;
        }
        var block = Platform.blockAt(entity.world, entity.pos);
        if (block == null) {
            return;
        }
        var state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.isEmpty()) {
            return;
        }
        if (!"botania:apothecary_default".equals(state.owner().value().id().asString())) {
            return;  // 其他生物群系变体没有有水版本
        }
        CraftEngineBlocks.place(block.getLocation(),
                Key.of("botania:apothecary_default_water"), true);
    }

    /** 用水以后换回无水外观。 */
    private void switchBackToDry(org.bukkit.block.Block table) {
        var state = CraftEngineBlocks.getCustomBlockState(table);
        if (state == null || state.isEmpty()) {
            return;
        }
        if ("botania:apothecary_default_water".equals(state.owner().value().id().asString())) {
            CraftEngineBlocks.place(table.getLocation(),
                    Key.of("botania:apothecary_default"), true);
        }
    }

    /**
     * 出锅：花以**掉落物**形式从台子里弹出来，玩家自己捡起来再种。
     *
     * <p>上游就是这么做 —— 花药台不会把花直接种在地上，成品的花是物品。
     * 这里顺手加一点上抛速度和粒子，手感接近原版。</p>
     */
    private void craft(org.bukkit.entity.Player player, ApothecaryController controller,
                       RecipeBook.Apothecary recipe) {
        BlockEntity entity = controller.blockEntity();
        if (entity == null) {
            return;
        }
        org.bukkit.block.Block table = Platform.blockAt(entity.world, entity.pos);
        if (table == null) {
            return;
        }
        ItemStack result = BotaniaItems.create(recipe.output(), 1);
        if (result == null) {
            Platform.send(player, "<red>产物物品不存在：" + recipe.output());
            return;
        }
        Location spawn = table.getLocation().add(0.5, 1.05, 0.5);
        org.bukkit.entity.Item drop = table.getWorld().dropItem(spawn, result);
        drop.setVelocity(new org.bukkit.util.Vector(0, 0.22, 0));
        drop.setPickupDelay(10);
        table.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, spawn, 12,
                0.25, 0.1, 0.25, 0.01);
        table.getWorld().playSound(spawn, org.bukkit.Sound.BLOCK_GRASS_PLACE, 0.7f, 1.5f);
        table.getWorld().playSound(spawn, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.8f);
        controller.clear();
        switchBackToDry(table);
        Platform.send(player, "<green>花开了一朵！ <gray>（"
                + BotaniaItems.displayNameForMessage(recipe.output()) + " 掉在台子上）");
    }

    /** 空手右键：潜行才取回最后一件，普通空手只报状态。 */
    private InteractionResult withdraw(org.bukkit.entity.Player player,
                                       ApothecaryController controller) {
        if (!player.isSneaking()) {
            report(player, controller);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        String popped = controller.popItem();
        if (popped == null) {
            Platform.send(player, "<gray>台子里没有东西");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        ItemStack item = BotaniaItems.create(popped, 1);
        if (item != null) {
            var leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        Platform.send(player, "<green>取回 " + BotaniaItems.displayNameForMessage(popped)
                + " <gray>· 台子里还剩 <white>" + controller.items().size() + " <gray>样");
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private void report(org.bukkit.entity.Player player, ApothecaryController controller) {
        if (!controller.hasWater()) {
            Platform.send(player, "<gray>空的台子 <dark_gray>（手持水桶注水）");
            return;
        }
        if (controller.items().isEmpty()) {
            Platform.send(player, "<aqua>台子里有水 <gray>· 还没投东西");
            return;
        }
        Platform.send(player, "<aqua>台子里有水 <gray>· 已投入：<white>"
                + describeContents(controller.items()));
    }

    /** 把投入的物品 id 列表翻成"名称 ×数量"的可读串。 */
    private static String describeContents(java.util.List<String> items) {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String id : items) {
            counts.merge(id, 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        for (var entry : counts.entrySet()) {
            if (out.length() > 0) {
                out.append("、");
            }
            out.append(BotaniaItems.displayNameForMessage(entry.getKey()));
            if (entry.getValue() > 1) {
                out.append("×").append(entry.getValue());
            }
        }
        return out.toString();
    }

    private ApothecaryController controllerAt(World world, BlockPos pos) {
        if (world == null) {
            return null;
        }
        BlockEntity entity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.getAt(ApothecaryController.class, this.controllerId);
    }
}
