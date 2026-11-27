package net.bailanya.cebotania.command;

import net.bailanya.cebotania.block.SpreaderController;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.bailanya.cebotania.command.BotaniaCommand.parseInt;

final class WorldCommands {

    private WorldCommands() {
    }

    /**
     * {@code /botania demo <x> <y> <z>}：在指定位置搭一套最小产线
     * （花药台 + 萤火之焰 + 发射器 + 魔力池），并丢一块煤给花吃。
     * 控制台也能跑，用来做无人值守的端到端自检。
     */
    static void demo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania demo <x> <y> <z> [世界名]");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        World world = args.length > 4
                ? Bukkit.getWorld(args[4])
                : Bukkit.getWorlds().get(0);
        if (world == null) {
            sender.sendMessage("§c世界不存在");
            return;
        }
        var ceWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            sender.sendMessage("§cCraftEngine 未就绪");
            return;
        }
        // 先清出一小块空地，避免摆进石头里
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
        // 铺一层草方块当花床：花只认土类方块，不然会被 canSurvive 弹掉
        for (int dx = -1; dx <= 4; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.GRASS_BLOCK, false);
            }
        }
        boolean ok = true;
        ok &= CraftEngineBlocks.place(new Location(world, x, y, z), Key.of("botania:petal_apothecary_default"), true)
                || CraftEngineBlocks.place(new Location(world, x, y, z), Key.of("botania:apothecary_default"), true);
        ok &= CraftEngineBlocks.place(new Location(world, x + 1, y, z), Key.of("botania:endoflame"), true);
        ok &= CraftEngineBlocks.place(new Location(world, x + 2, y, z), Key.of("botania:mana_spreader"), true);
        ok &= CraftEngineBlocks.place(new Location(world, x + 3, y, z), Key.of("botania:mana_pool"), true);
        // 发射器朝东（池子方向）——它是方向性的，默认朝北打不到
        var demoWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        if (demoWorld != null) {
            var entity = demoWorld.getBlockEntityAtIfLoaded(
                    new BlockPos(x + 2, y, z));
            if (entity != null) {
                var spreader = entity.controller.get(
                        SpreaderController.class, 0);
                if (spreader != null) {
                    for (int i = 0; i < 4 && !"东".equals(spreader.facingName()); i++) {
                        spreader.rotate();
                    }
                }
            }
        }
        // 给花一块煤当燃料
        world.dropItem(new Location(world, x + 1, y + 1, z),
                new ItemStack(Material.COAL, 8));
        sender.sendMessage("§a产线已摆放：花药台(" + x + "," + y + "," + z + ")"
                + " 花(" + (x + 1) + ") 发射器(" + (x + 2) + ") 池子(" + (x + 3) + ")"
                + (ok ? "" : " §c（有方块放置失败，检查 CE 是否加载）"));
    }

    /**
     * {@code /botania scout <区块x> <区块z>}：加载/生成那个区块，数一数里面有多少神秘花。
     * 验证世界生成用 —— 老区块不会补花，必须看新生成的。
     */
    static void scout(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法：/botania scout <区块x> <区块z>");
            return;
        }
        int cx = parseInt(args[1], 0);
        int cz = parseInt(args[2], 0);
        World world = Bukkit.getWorlds().get(0);
        // getChunkAt 会强制生成新区块（新区块才会跑 placed_feature）
        Chunk chunk = world.getChunkAt(cx, cz);
        int found = 0;
        List<String> colors = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;
                // WORLD_SURFACE 包含不阻挡移动的方块，例如花的载体。
                int surface = world.getHighestBlockYAt(worldX, worldZ, HeightMap.WORLD_SURFACE);
                for (int y = world.getMinHeight(); y <= surface && y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(worldX, y, worldZ);
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
    static void place(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法：/botania place <id> <x> <y> <z>");
            return;
        }
        String id = args[1].contains(":") ? args[1] : "botania:" + args[1];
        int x = parseInt(args[2], 0);
        int y = parseInt(args[3], 64);
        int z = parseInt(args[4], 0);
        World world = Bukkit.getWorlds().get(0);
        boolean ok = CraftEngineBlocks.place(new Location(world, x, y, z),
                Key.of(id), true);
        sender.sendMessage(ok ? "§a已放置 " + id : "§c放置失败 " + id);
    }

    /** {@code /botania block <x> <y> <z>}：报这个位置是什么方块。 */
    static void blockAt(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania block <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        World world = Bukkit.getWorlds().get(0);
        Block block = world.getBlockAt(x, y, z);
        var state = CraftEngineBlocks.getCustomBlockState(block);
        if (state != null && !state.isEmpty()) {
            sender.sendMessage("§7(" + x + "," + y + "," + z + ") §f"
                    + state.owner().value().id());
        } else {
            sender.sendMessage("§7(" + x + "," + y + "," + z + ") §f"
                    + block.getType().getKey());
        }
    }
}
