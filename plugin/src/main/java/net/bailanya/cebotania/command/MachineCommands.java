package net.bailanya.cebotania.command;

import net.bailanya.cebotania.block.ApothecaryController;
import net.bailanya.cebotania.block.FlowerController;
import net.bailanya.cebotania.block.ManaPoolController;
import net.bailanya.cebotania.block.PureDaisyController;
import net.bailanya.cebotania.block.RunicAltarController;
import net.bailanya.cebotania.block.SpreaderController;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import static net.bailanya.cebotania.command.BotaniaCommand.parseInt;

final class MachineCommands {

    private MachineCommands() {
    }

    /** {@code /botania inspect <x> <y> <z>}：读一个方块实体里的魔力。 */
    static void inspect(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania inspect <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        World world = Bukkit.getWorlds().get(0);
        var ceWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            sender.sendMessage("§cCraftEngine 未就绪");
            return;
        }
        var entity = loadBlockEntity(ceWorld, world, x, y, z);
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
        var pool = entity.controller.get(ManaPoolController.class, 0);
        if (pool != null) {
            sender.sendMessage("§a魔力池：§f" + pool.mana() + " / " + pool.capacity());
        }
        var spreader = entity.controller.get(SpreaderController.class, 0);
        if (spreader != null) {
            sender.sendMessage("§d发射器缓存：§f" + spreader.mana() + " / " + spreader.capacity()
                    + " §7tick=" + spreader.ticks() + " 朝向=" + spreader.facingName());
        }
        var flower = entity.controller.get(FlowerController.class, 0);
        if (flower != null) {
            sender.sendMessage("§2花：§f" + flower.flowerId() + " §7魔力 §f" + flower.mana()
                    + " §7tick=" + flower.ticks());
        }
        var altarCtl = entity.controller.get(
                RunicAltarController.class, 0);
        if (altarCtl != null) {
            sender.sendMessage("§6符文祭坛：魔力 " + altarCtl.mana() + "，投入 "
                    + altarCtl.items().size() + " 样：" + altarCtl.items());
        }
        var daisy = entity.controller.get(PureDaisyController.class, 0);
        if (daisy != null) {
            sender.sendMessage("§a纯洁雏菊：§7tick=" + daisy.ticks());
        }
        var apothecary = entity.controller.get(ApothecaryController.class, 0);
        if (apothecary != null) {
            sender.sendMessage("§6花药台：水 " + (apothecary.hasWater() ? "有" : "无")
                    + "，投入 " + apothecary.items());
        }
    }

    /** {@code /botania manaset <x> <y> <z> <数量>}：直接给方块实体灌魔力（调试用）。 */
    static void manaSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法：/botania manaset <x> <y> <z> <数量>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        long amount = Long.parseLong(args[4]);
        World world = Bukkit.getWorlds().get(0);
        var ceWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            sender.sendMessage("§cCraftEngine 未就绪");
            return;
        }
        var entity = loadBlockEntity(ceWorld, world, x, y, z);
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var pool = entity.controller.get(ManaPoolController.class, 0);
        if (pool != null) {
            pool.insert(amount);
            sender.sendMessage("§a灌入 " + amount + "，池子现在 " + pool.mana());
            return;
        }
        var spreader = entity.controller.get(
                SpreaderController.class, 0);
        if (spreader != null) {
            spreader.insert(amount);
            sender.sendMessage("§a灌入 " + amount + "，发射器现在 " + spreader.mana());
            return;
        }
        var altar = entity.controller.get(
                RunicAltarController.class, 0);
        if (altar != null) {
            altar.insert(amount);
            sender.sendMessage("§a灌入 " + amount + "，祭坛现在 " + altar.mana());
            return;
        }
        sender.sendMessage("§7这里既不是池子也不是发射器/祭坛");
    }

    /** {@code /botania altaradd <x> <y> <z> <物品id>}：给祭坛投料（调试用）。 */
    static void altarAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法：/botania altaradd <x> <y> <z> <物品id>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        String id = args[4].contains(":") ? args[4] : "botania:" + args[4];
        World world = Bukkit.getWorlds().get(0);
        var ceWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        var entity = loadBlockEntity(ceWorld, world, x, y, z);
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var altar = entity.controller.get(
                RunicAltarController.class, 0);
        if (altar == null) {
            sender.sendMessage("§7这里不是符文祭坛");
            return;
        }
        altar.addItem(id);
        sender.sendMessage("§a已投入 " + id + "，现在 " + altar.items().size() + " 样");
    }

    /** {@code /botania altarclear <x> <y> <z>}：清空祭坛材料与魔力（调试用）。 */
    static void altarClear(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania altarclear <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        World world = Bukkit.getWorlds().get(0);
        var ceWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        var entity = loadBlockEntity(ceWorld, world, x, y, z);
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var altar = entity.controller.get(
                RunicAltarController.class, 0);
        if (altar == null) {
            sender.sendMessage("§7这里不是符文祭坛");
            return;
        }
        altar.clear();
        sender.sendMessage("§a祭坛已清空");
    }

    /** {@code /botania rotate <x> <y> <z>}：把发射器顺时针转 90°（调试用）。 */
    static void rotateSpreader(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法：/botania rotate <x> <y> <z>");
            return;
        }
        int x = parseInt(args[1], 0);
        int y = parseInt(args[2], 64);
        int z = parseInt(args[3], 0);
        World world = Bukkit.getWorlds().get(0);
        var ceWorld = BukkitAdaptor
                .adapt(world).storageWorld();
        var entity = loadBlockEntity(ceWorld, world, x, y, z);
        if (entity == null) {
            sender.sendMessage("§7该坐标没有方块实体");
            return;
        }
        var spreader = entity.controller.get(
                SpreaderController.class, 0);
        if (spreader == null) {
            sender.sendMessage("§7这里不是魔力发射器");
            return;
        }
        sender.sendMessage("§a发射方向 → " + spreader.rotate());
    }

    private static BlockEntity loadBlockEntity(CEWorld ceWorld, World world, int x, int y, int z) {
        // 先加载区块并读取方块状态，让 CE 把状态同步进缓存。
        world.getChunkAt(x >> 4, z >> 4).load();
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        return ceWorld == null ? null : ceWorld.getBlockEntityAtIfLoaded(new BlockPos(x, y, z));
    }
}
