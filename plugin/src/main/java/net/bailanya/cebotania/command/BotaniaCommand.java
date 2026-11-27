package net.bailanya.cebotania.command;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.flower.FlowerType;
import net.bailanya.cebotania.i18n.I18n;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.recipe.RecipeBook;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
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
            case "demo" -> WorldCommands.demo(sender, args);
            case "inspect" -> MachineCommands.inspect(sender, args);
            case "scout" -> WorldCommands.scout(sender, args);
            case "place" -> WorldCommands.place(sender, args);
            case "block" -> WorldCommands.blockAt(sender, args);
            case "manaset" -> MachineCommands.manaSet(sender, args);
            case "altaradd" -> MachineCommands.altarAdd(sender, args);
            case "altarclear" -> MachineCommands.altarClear(sender, args);
            case "rotate" -> MachineCommands.rotateSpreader(sender, args);
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
                + I18n.displayName("botania:white_petal")
                + " / " + I18n.displayName("botania:twig_wand"));
        boolean pool = CraftEngineBlocks.byId(Key.of("botania:mana_pool")) != null;
        boolean apothecary = CraftEngineBlocks.byId(Key.of("botania:apothecary_default")) != null;
        boolean spreader = CraftEngineBlocks.byId(Key.of("botania:mana_spreader")) != null;
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

    static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
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
                    .map(Key::asString)
                    .filter(s -> s.startsWith(prefix))
                    .limit(60)
                    .toList();
        }
        return Arrays.asList();
    }
}
