package net.bailanya.cebotania.block;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 泰拉凝聚板（Terrestrial Agglomeration Plate）。
 *
 * <p>上游做法：板子放在 3×3 活石正中心，3 个玩家站上去、各手持一个魔钢锭，
 * 消耗 50 万魔力，产出泰拉钢锭。</p>
 *
 * <p>插件端简化成：手持魔钢锭右键凝聚板，自动检查周围 8 块活石 + 附近魔力池，
 * 凑齐就产出（不需要真的站 3 个人，单人也能合成）。</p>
 */
public final class TerrasteelBehavior extends BukkitBlockBehavior implements
        net.momirealms.craftengine.core.block.behavior.EntityBlock {

    public static final Key KEY = Key.of("botania", "terra_plate");

    /** 一次合成消耗的魔力。 */
    private static final long MANA_COST = 500_000L;
    /** 上游材料：魔力钢锭 + 魔力钻石 + 魔力珍珠。 */
    private static final String[] MATERIALS = {
            "botania:manasteel_ingot", "botania:mana_diamond", "botania:mana_pearl"
    };
    /** 产出的泰拉钢粒数量。 */
    private static final int OUTPUT_COUNT = 1;

    private int controllerId;

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public net.momirealms.craftengine.core.block.entity.BlockEntityController
            createBlockEntityController(net.momirealms.craftengine.core.block.entity.BlockEntity blockEntity) {
        return new TerrasteelController(blockEntity);
    }

    public static BlockBehaviorFactory<TerrasteelBehavior> factory(CeBotaniaPlugin plugin) {
        return (block, section) -> new TerrasteelBehavior(plugin, block);
    }

    private final CeBotaniaPlugin plugin;

    private TerrasteelBehavior(CeBotaniaPlugin plugin, BlockDefinition block) {
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
        var pos = context.getClickedPos();
        // 手持火花 → 优先安装
        ItemStack heldNow = bukkit.getInventory().getItemInMainHand();
        if (BotaniaItems.idOf(heldNow).equals("botania:spark")) {
            org.bukkit.World w0 = Platform.bukkit(context.getLevel());
            if (w0 != null) {
                var ceW = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                        .adapt(w0).storageWorld();
                var ent = ceW == null ? null : ceW.getBlockEntityAtIfLoaded(pos);
                var ctl = ent == null ? null
                        : ent.controller.get(TerrasteelController.class, 0);
                if (ctl == null) {
                    Platform.send(bukkit, "<red>凝聚板控制器没创建（配置问题）");
                } else if (ctl.hasSpark()) {
                    Platform.send(bukkit, "<gray>凝聚板上已经有火花了");
                } else {
                    SparkNetwork.spawn(w0, pos.x(), pos.y(), pos.z(),
                            "泰拉凝聚板 " + pos.x() + "," + pos.y() + "," + pos.z());
                    ctl.installSpark();
                    heldNow.setAmount(heldNow.getAmount() - 1);
                    bukkit.getInventory().setItemInMainHand(
                            heldNow.getAmount() <= 0 ? null : heldNow);
                    Platform.send(bukkit, "<light_purple>火花已装在泰拉凝聚板上");
                }
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        org.bukkit.World bukkitWorld0 = Platform.bukkit(context.getLevel());
        if (bukkitWorld0 == null) {
            return InteractionResult.PASS;
        }
        // 上游：把物品扔在凝聚板所在的方块范围内
        var center = new org.bukkit.Location(bukkitWorld0,
                pos.x() + 0.5, pos.y(), pos.z() + 0.5);
        java.util.Map<String, org.bukkit.entity.Item> found = new java.util.LinkedHashMap<>();
        for (var entity : bukkitWorld0.getNearbyEntities(center, 1.2, 1.2, 1.2)) {
            if (entity instanceof org.bukkit.entity.Item item) {
                String id = BotaniaItems.idOf(item.getItemStack());
                for (String need : MATERIALS) {
                    if (need.equals(id) && !found.containsKey(id)) {
                        found.put(id, item);
                    }
                }
            }
        }
        var world = context.getLevel();
        org.bukkit.World bukkitWorld = Platform.bukkit(world);
        if (bukkitWorld == null) {
            return InteractionResult.PASS;
        }
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(bukkitWorld).storageWorld();
        if (ceWorld == null) {
            return InteractionResult.PASS;
        }

        // 1) 检查结构：凝聚板正下方一层是「4 角活石 + 4 边中点青金石」
        int livingrock = 0;
        int lapis = 0;
        int baseY = pos.y() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var probe = new BlockPos(pos.x() + dx, baseY, pos.z() + dz);
                var block = Platform.blockAt(ceWorld, probe);
                if (block == null) {
                    continue;
                }
                boolean side = (dx == 0 || dz == 0);
                if (!side) {
                    // 对角：活石
                    var st = CraftEngineBlocks.getCustomBlockState(block);
                    if (st != null && !st.isEmpty()
                            && st.owner().value().id().value().equals("livingrock")) {
                        livingrock++;
                    }
                } else if (dx == 0 && dz == 0) {
                    // 正中心：也是活石
                    var st = CraftEngineBlocks.getCustomBlockState(block);
                    if (st != null && !st.isEmpty()
                            && st.owner().value().id().value().equals("livingrock")) {
                        livingrock++;
                    }
                } else if (block.getType() == Material.LAPIS_BLOCK) {
                    lapis++;
                }
            }
        }
        if (livingrock < 5 || lapis < 4) {
            Platform.send(bukkit, "<red>结构不完整：凝聚板下方一层要"
                    + "「5 块活石（4 角 + 中心）+ 4 块青金石（四边中点）」"
                    + "（现在活石 " + livingrock + "/5，青金石 " + lapis + "/4）");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 2) 板上要凑齐三样材料
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String need : MATERIALS) {
            if (!found.containsKey(need)) {
                missing.add(BotaniaItems.displayNameForMessage(need));
            }
        }
        if (!missing.isEmpty()) {
            Platform.send(bukkit, "<red>材料不全：还缺 " + String.join("、", missing)
                    + "（丢在凝聚板上再右键）");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 3) 找魔力池
        // 魔力必须已经通过火花攒在凝聚板里
        var plateEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        TerrasteelController plate = plateEntity == null ? null
                : plateEntity.controller.get(TerrasteelController.class, 0);
        long stored = plate == null ? 0 : plate.mana();
        if (stored < MANA_COST) {
            Platform.send(bukkit, "<red>凝聚板魔力不够：需要 " + Platform.mana(MANA_COST)
                    + "（现在 " + Platform.mana(stored)
                    + "）· 请给凝聚板和魔力池各装一个火花，让火花把魔力搬过来");
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 4) 扣材料（从板上的掉落物里删）与魔力，产出
        plate.extract(MANA_COST);
        for (var item : found.values()) {
            var stack = item.getItemStack();
            if (stack.getAmount() <= 1) {
                item.remove();
            } else {
                stack.setAmount(stack.getAmount() - 1);
                item.setItemStack(stack);
            }
        }
        ItemStack result = BotaniaItems.create("botania:terrasteel_ingot", OUTPUT_COUNT);
        if (result != null) {
            var block = Platform.blockAt(ceWorld, pos);
            if (block != null) {
                var location = block.getLocation().add(0.5, 1.2, 0.5);
                var drop = block.getWorld().dropItem(location, result);
                drop.setPickupDelay(10);
                try {
                    block.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, location,
                            40, 0.6, 0.6, 0.6, 0.05);
                    block.getWorld().playSound(location,
                            org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                } catch (Throwable ignored) {
                    // 粒子/音效缺失不影响功能
                }
            }
        }
        Platform.send(bukkit, "<green>泰拉凝聚完成！ <gray>获得泰拉钢锭 ×" + OUTPUT_COUNT);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /** 给凝聚板装火花。 */
    private boolean installSpark(org.bukkit.entity.Player player,
                                 net.momirealms.craftengine.core.world.context.UseOnContext context) {
        var world = context.getLevel();
        var pos = context.getClickedPos();
        if (world == null) {
            return false;
        }
        var bukkitWorld = Platform.bukkit(world);
        if (bukkitWorld == null) {
            return false;
        }
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(bukkitWorld).storageWorld();
        if (ceWorld == null) {
            return false;
        }
        var entity = ceWorld.getBlockEntityAtIfLoaded(pos);
        if (entity == null) {
            return false;
        }
        var controller = entity.controller.get(TerrasteelController.class, 0);
        if (controller == null) {
            Platform.send(player, "<red>这个方块没有控制器（配置有问题）");
            return false;
        }
        if (controller.hasSpark()) {
            Platform.send(player, "<gray>凝聚板上已经有火花了");
            return false;
        }
        controller.installSpark();
        Platform.send(player, "<light_purple>火花已装在泰拉凝聚板上");
        return true;
    }

    /** 找 8 格内最近的、余额够的魔力池。 */
    private ManaPoolController findPool(net.momirealms.craftengine.core.world.World world,
                                        BlockPos pos) {
        org.bukkit.World bukkit = Platform.bukkit(world);
        if (bukkit == null) {
            return null;
        }
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(bukkit).storageWorld();
        if (ceWorld == null) {
            return null;
        }
        ManaPoolController best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    var probe = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    var entity = ceWorld.getBlockEntityAtIfLoaded(probe);
                    if (entity == null) {
                        continue;
                    }
                    var pool = entity.controller.get(ManaPoolController.class, 0);
                    if (pool == null || pool.mana() < MANA_COST) {
                        continue;
                    }
                    // 上游：魔力要经过火花传进来，池子直连不算
                    if (!pool.hasSpark()) {
                        continue;
                    }
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestDist) {
                        bestDist = d;
                        best = pool;
                    }
                }
            }
        }
        return best;
    }
}
