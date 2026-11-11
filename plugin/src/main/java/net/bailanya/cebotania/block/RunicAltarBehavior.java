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
 * 符文祭坛的交互：投料 → 森林法杖点火 → 出符文。
 */
public final class RunicAltarBehavior extends BukkitBlockBehavior implements EntityBlock {

    public static final Key KEY = Key.of("botania", "runic_altar");

    public static BlockBehaviorFactory<RunicAltarBehavior> runicFactory(CeBotaniaPlugin plugin) {
        return (block, section) -> new RunicAltarBehavior(plugin, block);
    }

    private final CeBotaniaPlugin plugin;
    private int controllerId;

    private RunicAltarBehavior(CeBotaniaPlugin plugin, BlockDefinition block) {
        super(block);
        this.plugin = plugin;
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new RunicAltarController(blockEntity);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        RunicAltarController altar = controllerAt(context.getLevel(), context.getClickedPos());
        if (player == null || altar == null) {
            return InteractionResult.PASS;
        }
        if (!(player.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return InteractionResult.PASS;
        }
        ItemStack held = bukkit.getInventory().getItemInMainHand();

        // 手持活石 → 魔力注满后放上去
        if (BotaniaItems.idOf(held).equals("botania:livingrock")) {
            if (!altar.hasLivingrock()) {
                var recipe = altar.currentRecipe();
                if (recipe == null) {
                    Platform.send(bukkit, "<red>材料还没凑齐，先投配方材料");
                } else if (altar.mana() < recipe.mana()) {
                    Platform.send(bukkit, "<red>魔力还没注满："
                            + Platform.mana(altar.mana()) + "/" + Platform.mana(recipe.mana())
                            + "（等发射器/火花灌满再放活石）");
                } else {
                    altar.installLivingrock();
                    held.setAmount(held.getAmount() - 1);
                    bukkit.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
                    Platform.send(bukkit, "<green>活石已放入祭坛 <gray>· 再用森林法杖右键点火");
                }
            } else {
                Platform.send(bukkit, "<gray>祭坛上已经有活石了");
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持火花 → 装到祭坛上
        if (BotaniaItems.idOf(held).equals("botania:spark")) {
            if (altar.hasSpark()) {
                Platform.send(bukkit, "<gray>祭坛上已经有火花了");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            var sparkWorld = Platform.bukkit(context.getLevel());
            var sparkPos = context.getClickedPos();
            if (sparkWorld != null) {
                SparkNetwork.spawn(sparkWorld, sparkPos.x(), sparkPos.y(), sparkPos.z(),
                        "符文祭坛 " + sparkPos.x() + "," + sparkPos.y() + "," + sparkPos.z());
                altar.installSpark();
                held.setAmount(held.getAmount() - 1);
                bukkit.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
                Platform.send(bukkit, "<light_purple>火花已装在符文祭坛上");
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 森林法杖：潜行拆火花 / 普通点火
        if (BotaniaItems.holdingWand(bukkit)) {
            ignite(bukkit, altar);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        String heldId = BotaniaItems.idOf(held);
        if (heldId.isEmpty()) {
            // 空手：潜行才是取回，普通空手只报状态（避免误触把材料掏出来）
            if (!bukkit.isSneaking()) {
                report(bukkit, altar);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            String popped = altar.popItem();
            if (popped == null) {
                Platform.send(bukkit, "<gray>祭坛是空的");
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            ItemStack back = BotaniaItems.create(popped, 1);
            if (back != null) {
                var leftovers = bukkit.getInventory().addItem(back);
                leftovers.values().forEach(left ->
                        bukkit.getWorld().dropItemNaturally(bukkit.getLocation(), left));
            }
            Platform.send(bukkit, "<green>取回 " + BotaniaItems.displayNameForMessage(popped)
                    + " <gray>· 祭坛还剩 <white>" + altar.items().size() + " <gray>样");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // 投料
        // 投料：只消耗 1 个。用 clone 操作，避免 CE 自定义物品栈被整体替换掉
        ItemStack after = held.clone();
        int before = after.getAmount();
        if (before <= 0) {
            return InteractionResult.PASS;
        }
        altar.addItem(heldId);
        if (before <= 1) {
            bukkit.getInventory().setItemInMainHand(null);
        } else {
            after.setAmount(before - 1);
            bukkit.getInventory().setItemInMainHand(after);
        }
        Platform.send(bukkit, "<green>投入 " + BotaniaItems.displayNameForMessage(heldId)
                + " <gray>· 祭坛现有 <white>" + altar.items().size() + " <gray>样");
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /** 点火：材料齐 + 魔力满 + 有活石，直接成型。 */
    private void ignite(org.bukkit.entity.Player player, RunicAltarController altar) {
        if (altar.items().isEmpty()) {
            Platform.send(player, "<gray>祭坛是空的 <dark_gray>（手持材料投入）");
            return;
        }
        var recipe = altar.currentRecipe();
        if (recipe == null) {
            Platform.send(player, "<red>材料不对，凑不出任何符文配方");
            return;
        }
        if (altar.mana() < recipe.mana()) {
            Platform.send(player, "<red>魔力还没注满："
                    + Platform.mana(altar.mana()) + "/" + Platform.mana(recipe.mana())
                    + "（用发射器或火花往祭坛里灌）");
            return;
        }
        if (!altar.hasLivingrock()) {
            Platform.send(player, "<red>还没放活石 <gray>（魔力已满，手持活石右键祭坛放上去）");
            return;
        }
        var item = BotaniaItems.create(recipe.output(), recipe.count());
        if (item == null) {
            Platform.send(player, "<red>产物物品不存在：" + recipe.output());
            return;
        }
        var entity = altar.blockEntity();
        var world = altar.world();
        var pos = altar.position();
        if (entity == null || world == null || pos == null) {
            return;
        }
        var block = Platform.blockAt(world, pos);
        if (block == null) {
            return;
        }
        var location = block.getLocation().add(0.5, 1.0, 0.5);
        var drop = block.getWorld().dropItem(location, item);
        drop.setPickupDelay(10);
        try {
            block.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, location, 40,
                    0.5, 0.5, 0.5, 0.4);
            block.getWorld().playSound(location,
                    org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        } catch (Throwable ignored) {
            // 粒子/音效缺失不影响功能
        }
        altar.clear();
        Platform.send(player, "<green>符文完成！ <gray>（"
                + BotaniaItems.displayNameForMessage(recipe.output())
                + " ×" + recipe.count() + "）");
    }

    private ManaPoolController nearestPool(RunicAltarController altar, int radius) {
        var world = altar.world();
        var pos = altar.position();
        if (world == null || pos == null) {
            return null;
        }
        ManaPoolController best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    var target = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    var entity = world.getBlockEntityAtIfLoaded(target);
                    if (entity == null) {
                        continue;
                    }
                    var pool = entity.controller.get(ManaPoolController.class, 0);
                    if (pool == null || pool.mana() <= 0) {
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

    private void report(org.bukkit.entity.Player player, RunicAltarController altar) {
        if (altar.items().isEmpty()) {
            Platform.send(player, "<gray>空祭坛 <dark_gray>（手持材料投入）");
            return;
        }
        StringBuilder sb = new StringBuilder();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String id : altar.items()) {
            counts.merge(id, 1, Integer::sum);
        }
        for (var entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(BotaniaItems.displayNameForMessage(entry.getKey()));
            if (entry.getValue() > 1) {
                sb.append("×").append(entry.getValue());
            }
        }
        Platform.send(player, "<aqua>祭坛现有：<white>" + sb);
    }

    private RunicAltarController controllerAt(World world, BlockPos pos) {
        if (world == null) {
            return null;
        }
        BlockEntity entity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.getAt(RunicAltarController.class, this.controllerId);
    }
}
