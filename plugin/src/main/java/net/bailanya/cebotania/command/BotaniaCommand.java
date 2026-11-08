package net.bailanya.cebotania.command;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.flower.FlowerType;
import net.bailanya.cebotania.recipe.RecipeBook;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /botania}：给物品、自检、查配方。
 */
public final class BotaniaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of(
            "give", "selfcheck", "mana", "flower", "recipe", "demo", "inspect",
            "scout", "place", "block", "manaset", "altaradd", "altarclear", "rotate");

    private final CeBotaniaPlugin plugin;

    public BotaniaCommand(CeBotaniaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§2植物魔法 §7/" + label + " <give|selfcheck|mana|flower|recipe>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "selfcheck" -> selfCheck(sender);
            case "mana" -> mana(sender);
            case "flower" -> flower(sender);
            case "recipe" -> recipe(sender, args);
            case "demo" -> demo(sender, args);
            case "inspect" -> inspect(sender, args);
            case "scout" -> scout(sender, args);
            case "place" -> place(sender, args);
            case "block" -> blockAt(sender, args);
            case "manaset" -> manaSet(sender, args);
            case "altaradd" -> altarAdd(sender, args);
            case "altarclear" -> altarClear(sender, args);
            case "rotate" -> rotateSpreader(sender, args);
            default -> sender.sendMessage("§c未知子命令：" + args[0]);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能给物品");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法：/botania give <id> [数量]");
            return;
        }
        String id = args[1].contains(":") ? args[1] : "botania:" + args[1];
        int count = args.length > 2 ? parseInt(args[2], 1) : 1;
        ItemStack stack = BotaniaItems.create(id, count);
        if (stack == null) {
            sender.sendMessage("§c找不到物品：" + id);
            return;
        }
        var leftovers = player.getInventory().addItem(stack);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        sender.sendMessage("§a已给予 §f" + id + " §a×" + count);
    }

    private void selfCheck(CommandSender sender) {
        int items = CraftEngineItems.loadedItems().size();
        int blocks = CraftEngineBlocks.loadedBlocks().size();
        RecipeBook book = this.plugin.recipes();
        sender.sendMessage("§2—— 植物魔法自检 ——");
        sender.sendMessage("§7CE 物品：§f" + items + " §7方块：§f" + blocks);
        sender.sendMessage("§7配方：§f" + book.summary());
        sender.sendMessage("§7花型：§f" + FlowerType.all().size() + " 种");
        sender.sendMessage("§7语言表：§f"
                + net.bailanya.cebotania.i18n.I18n.displayName("botania:white_petal")
                + " / " + net.bailanya.cebotania.i18n.I18n.displayName("botania:twig_wand"));
        boolean pool = CraftEngineBlocks.byId(net.momirealms.craftengine.core.util.Key.of("botania:mana_pool")) != null;
        boolean apothecary = CraftEngineBlocks.byId(net.momirealms.craftengine.core.util.Key.of("botania:apothecary_default")) != null;
        boolean spreader = CraftEngineBlocks.byId(net.momirealms.craftengine.core.util.Key.of("botania:mana_spreader")) != null;
        sender.sendMessage("§7关键方块：§f魔力池 " + (pool ? "§a✓" : "§c✗")
                + "§f 花药台 " + (apothecary ? "§a✓" : "§c✗")
                + "§f 发射器 " + (spreader ? "§a✓" : "§c✗"));
    }

    private void mana(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        sender.sendMessage("§7手持：§f" + BotaniaItems.idOf(held));
    }

    private void flower(CommandSender sender) {
        sender.sendMessage("§2已知花型：");
        List<String> generating = new ArrayList<>();
        List<String> functional = new ArrayList<>();
        FlowerType.all().forEach((id, type) -> {
            String line = id + "(" + type.manaPerAction + "/" + type.intervalTicks + "t)";
            if (type.kind == FlowerType.Kind.GENERATING) {
                generating.add(line);
            } else {
                functional.add(line);
            }
        });
        sender.sendMessage("§a产魔：§f" + String.join(", ", generating));
        sender.sendMessage("§b功能：§f" + String.join(", ", functional));
    }

    private void recipe(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            sender.sendMessage("§c用法：/botania recipe <物品id>（看它怎么注魔出来）");
            return;
        }
        String id = args[1].contains(":") ? args[1] : "botania:" + args[1];
        RecipeBook.Infusion infusion = this.plugin.recipes().matchInfusion(id);
        if (infusion == null) {
            sender.sendMessage("§7没有直接匹配的灌注配方（可以直接丢进魔力池试试）");
            return;
        }
        sender.sendMessage("§a" + id + " §7需要 §f" + infusion.mana() + " §7mana（输入 "
                + infusion.input() + "）");
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    /**
     * {@code /botania demo <x> <y> <z>}：在指定位置搭一套最小产线
     * （花药台 + 萤火之焰 + 发射器 + 魔力池），并丢一块煤给花吃。
     * 控制台也能跑，用来做无人值守的端到端自检。
     */
    private void demo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania demo <x> <y> <z> [世界名]");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        org.bukkit.World world = args.length > 4
                ? org.bukkit.Bukkit.getWorld(args[4])
                : org.bukkit.Bukkit.getWorlds().get(0);
        if (world == null) {
            sender.sendMessage("§c世界不存在");
            return;
        }
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            sender.sendMessage("§cCraftEngine 未就绪");
            return;
        }
        // 先清出一小块空地，避免摆进石头里
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(org.bukkit.Material.AIR);
                }
            }
        }
        // 铺一层草方块当花床：花只认土类方块，不然会被 canSurvive 弹掉
        for (int dx = -1; dx <= 4; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(org.bukkit.Material.GRASS_BLOCK, false);
            }
        }
        boolean ok = true;
        ok &= CraftEngineBlocks.place(new org.bukkit.Location(world, x, y, z), Key.of("botania:petal_apothecary_default"), true)
                || CraftEngineBlocks.place(new org.bukkit.Location(world, x, y, z), Key.of("botania:apothecary_default"), true);
        ok &= CraftEngineBlocks.place(new org.bukkit.Location(world, x + 1, y, z), Key.of("botania:endoflame"), true);
        ok &= CraftEngineBlocks.place(new org.bukkit.Location(world, x + 2, y, z), Key.of("botania:mana_spreader"), true);
        ok &= CraftEngineBlocks.place(new org.bukkit.Location(world, x + 3, y, z), Key.of("botania:mana_pool"), true);
        // 发射器朝东（池子方向）——它是方向性的，默认朝北打不到
        var demoWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        if (demoWorld != null) {
            var entity = demoWorld.getBlockEntityAtIfLoaded(
                    new net.momirealms.craftengine.core.world.BlockPos(x + 2, y, z));
            if (entity != null) {
                var spreader = entity.controller.get(
                        net.bailanya.cebotania.block.SpreaderController.class, 0);
                if (spreader != null) {
                    for (int i = 0; i < 4 && !"东".equals(spreader.facingName()); i++) {
                        spreader.rotate();
                    }
                }
            }
        }
        // 给花一块煤当燃料
        world.dropItem(new org.bukkit.Location(world, x + 1, y + 1, z),
                new ItemStack(org.bukkit.Material.COAL, 8));
        sender.sendMessage("§a产线已摆放：花药台(" + x + "," + y + "," + z + ")"
                + " 花(" + (x + 1) + ") 发射器(" + (x + 2) + ") 池子(" + (x + 3) + ")"
                + (ok ? "" : " §c（有方块放置失败，检查 CE 是否加载）"));
    }

    /** {@code /botania inspect <x> <y> <z>}：读一个方块实体里的魔力。 */
    private void inspect(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania inspect <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            sender.sendMessage("§cCraftEngine 未就绪");
            return;
        }
        // 区块没加载时 CE 不会建方块实体：先加载再摸一下方块，让 CE 把状态同步进缓存。
        world.getChunkAt(x >> 4, z >> 4).load();
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        var entity = ceWorld.getBlockEntityAtIfLoaded(new net.momirealms.craftengine.core.world.BlockPos(x, y, z));
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var state = entity.blockState();
        sender.sendMessage("§7方块：" + (state == null ? "?" : state.owner().value().id()));
        if (state != null) {
            try {
                sender.sendMessage("§7载体状态：§f" + state.vanillaBlockState()
                        + " §7属性：" + state.getPropertiesAsString());
            } catch (Throwable ignored) {
                // 拿不到就跳过
            }
        }
        var pool = entity.controller.get(net.bailanya.cebotania.block.ManaPoolController.class, 0);
        if (pool != null) {
            sender.sendMessage("§a魔力池：§f" + pool.mana() + " / " + pool.capacity());
        }
        var spreader = entity.controller.get(net.bailanya.cebotania.block.SpreaderController.class, 0);
        if (spreader != null) {
            sender.sendMessage("§d发射器缓存：§f" + spreader.mana() + " / " + spreader.capacity()
                    + " §7tick=" + spreader.ticks() + " 朝向=" + spreader.facingName());
        }
        var flower = entity.controller.get(net.bailanya.cebotania.block.FlowerController.class, 0);
        if (flower != null) {
            sender.sendMessage("§2花：§f" + flower.flowerId() + " §7魔力 §f" + flower.mana()
                    + " §7tick=" + flower.ticks());
        }
        var altarCtl = entity.controller.get(
                net.bailanya.cebotania.block.RunicAltarController.class, 0);
        if (altarCtl != null) {
            sender.sendMessage("§6符文祭坛：魔力 " + altarCtl.mana() + "，投入 "
                    + altarCtl.items().size() + " 样：" + altarCtl.items());
        }
        var daisy = entity.controller.get(net.bailanya.cebotania.block.PureDaisyController.class, 0);
        if (daisy != null) {
            sender.sendMessage("§a纯洁雏菊：§7tick=" + daisy.ticks());
        }
        var apothecary = entity.controller.get(net.bailanya.cebotania.block.ApothecaryController.class, 0);
        if (apothecary != null) {
            sender.sendMessage("§6花药台：水 " + (apothecary.hasWater() ? "有" : "无")
                    + "，投入 " + apothecary.items());
        }
    }


    /**
     * {@code /botania scout <区块x> <区块z>}：加载/生成那个区块，数一数里面有多少神秘花。
     * 验证世界生成用 —— 老区块不会补花，必须看新生成的。
     */
    private void scout(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法：/botania scout <区块x> <区块z>");
            return;
        }
        int cx = parseInt(args[1], 0);
        int cz = parseInt(args[2], 0);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        // getChunkAt 会强制生成新区块（新区块才会跑 placed_feature）
        org.bukkit.Chunk chunk = world.getChunkAt(cx, cz);
        int found = 0;
        java.util.List<String> colors = new java.util.ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 40; y <= 130; y++) {
                    org.bukkit.block.Block block = world.getBlockAt(
                            chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                    if (!CraftEngineBlocks.isCustomBlock(block)) {
                        continue;
                    }
                    var state = CraftEngineBlocks.getCustomBlockState(block);
                    if (state == null || state.isEmpty()) {
                        continue;
                    }
                    String id = state.owner().value().id().asString();
                    if (id.contains("mystical_flower")) {
                        found++;
                        if (colors.size() < 6) {
                            colors.add(id.substring(id.indexOf(':') + 1));
                        }
                    }
                }
            }
        }
        sender.sendMessage("§2区块 (" + cx + "," + cz + ") §7神秘花：§f" + found
                + (colors.isEmpty() ? "" : " §7如 " + String.join(", ", colors))
                + " §7已生成=" + chunk.isGenerated());
    }


    /** {@code /botania place <id> <x> <y> <z>}：放一个自定义方块（测试用）。 */
    private void place(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法：/botania place <id> <x> <y> <z>");
            return;
        }
        String id = args[1].contains(":") ? args[1] : "botania:" + args[1];
        int x = parseInt(args[2], 0);
        int y = parseInt(args[3], 64);
        int z = parseInt(args[4], 0);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        boolean ok = CraftEngineBlocks.place(new org.bukkit.Location(world, x, y, z),
                Key.of(id), true);
        sender.sendMessage(ok ? "§a已放置 " + id : "§c放置失败 " + id);
    }

    /** {@code /botania block <x> <y> <z>}：报这个位置是什么方块。 */
    private void blockAt(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania block <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        org.bukkit.block.Block block = world.getBlockAt(x, y, z);
        var state = CraftEngineBlocks.getCustomBlockState(block);
        if (state != null && !state.isEmpty()) {
            sender.sendMessage("§7(" + x + "," + y + "," + z + ") §f"
                    + state.owner().value().id());
        } else {
            sender.sendMessage("§7(" + x + "," + y + "," + z + ") §f"
                    + block.getType().getKey());
        }
    }


    /** {@code /botania manaset <x> <y> <z> <数量>}：直接给方块实体灌魔力（调试用）。 */
    private void manaSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法：/botania manaset <x> <y> <z> <数量>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        long amount = Long.parseLong(args[4]);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            sender.sendMessage("§cCraftEngine 未就绪");
            return;
        }
        world.getChunkAt(x >> 4, z >> 4).load();
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        var entity = ceWorld.getBlockEntityAtIfLoaded(
                new net.momirealms.craftengine.core.world.BlockPos(x, y, z));
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var pool = entity.controller.get(net.bailanya.cebotania.block.ManaPoolController.class, 0);
        if (pool != null) {
            pool.insert(amount);
            sender.sendMessage("§a灌入 " + amount + "，池子现在 " + pool.mana());
            return;
        }
        var spreader = entity.controller.get(
                net.bailanya.cebotania.block.SpreaderController.class, 0);
        if (spreader != null) {
            spreader.insert(amount);
            sender.sendMessage("§a灌入 " + amount + "，发射器现在 " + spreader.mana());
            return;
        }
        var altar = entity.controller.get(
                net.bailanya.cebotania.block.RunicAltarController.class, 0);
        if (altar != null) {
            altar.insert(amount);
            sender.sendMessage("§a灌入 " + amount + "，祭坛现在 " + altar.mana());
            return;
        }
        sender.sendMessage("§7这里既不是池子也不是发射器/祭坛");
    }


    /** {@code /botania altaradd <x> <y> <z> <物品id>}：给祭坛投料（调试用）。 */
    private void altarAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法：/botania altaradd <x> <y> <z> <物品id>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        String id = args[4].contains(":") ? args[4] : "botania:" + args[4];
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        world.getChunkAt(x >> 4, z >> 4).load();
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        var entity = ceWorld == null ? null : ceWorld.getBlockEntityAtIfLoaded(
                new net.momirealms.craftengine.core.world.BlockPos(x, y, z));
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var altar = entity.controller.get(
                net.bailanya.cebotania.block.RunicAltarController.class, 0);
        if (altar == null) {
            sender.sendMessage("§7这里不是符文祭坛");
            return;
        }
        altar.addItem(id);
        sender.sendMessage("§a已投入 " + id + "，现在 " + altar.items().size() + " 样");
    }



    /** {@code /botania altarclear <x> <y> <z>}：清空祭坛材料与魔力（调试用）。 */
    private void altarClear(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania altarclear <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        world.getChunkAt(x >> 4, z >> 4).load();
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        var entity = ceWorld == null ? null : ceWorld.getBlockEntityAtIfLoaded(
                new net.momirealms.craftengine.core.world.BlockPos(x, y, z));
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var altar = entity.controller.get(
                net.bailanya.cebotania.block.RunicAltarController.class, 0);
        if (altar == null) {
            sender.sendMessage("§7这里不是符文祭坛");
            return;
        }
        altar.clear();
        sender.sendMessage("§a祭坛已清空");
    }



    /** {@code /botania rotate <x> <y> <z>}：把发射器顺时针转 90°（调试用）。 */
    private void rotateSpreader(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania rotate <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        world.getChunkAt(x >> 4, z >> 4).load();
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        var entity = ceWorld == null ? null : ceWorld.getBlockEntityAtIfLoaded(
                new net.momirealms.craftengine.core.world.BlockPos(x, y, z));
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var spreader = entity.controller.get(
                net.bailanya.cebotania.block.SpreaderController.class, 0);
        if (spreader == null) {
            sender.sendMessage("§7这里不是魔力发射器");
            return;
        }
        sender.sendMessage("§a发射方向 → " + spreader.rotate());
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return CraftEngineItems.loadedItems().keySet().stream()
                    .map(net.momirealms.craftengine.core.util.Key::asString)
                    .filter(s -> s.startsWith(prefix))
                    .limit(60)
                    .toList();
        }
        return Arrays.asList();
    }
}
