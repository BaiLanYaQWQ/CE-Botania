package acceptance;

import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockHitResult;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Registered state and live block interaction checks for all connecting blocks. */
final class ConnectionChecks {
    private enum Family {
        PANE("_pane", "glass_pane", 3, 32),
        FENCE("_fence", "oak_fence", 2, 32),
        GATE("_fence_gate", "oak_fence_gate", 2, 32),
        WALL("_wall", "cobblestone_wall", 33, 324);

        final String suffix;
        final String vanilla;
        final int variants;
        final int states;

        Family(String suffix, String vanilla, int variants, int states) {
            this.suffix = suffix;
            this.vanilla = vanilla;
            this.variants = variants;
            this.states = states;
        }
    }

    static void run(JavaPlugin plugin, World world, Consumer<String> complete, Consumer<Throwable> fail)
            throws ReflectiveOperationException {
        int y = world.getMaxHeight() - 18;
        var reference = new Location(world, 670, y, 669);
        var registry = CraftEngineBlocks.loadedBlocks();
        var vanillaShapes = new HashMap<String, Set<BoundingBox>>();
        int totalStates = 0;
        for (var family : Family.values()) {
            var entries = registry.entrySet().stream()
                    .filter(entry -> "botania".equals(entry.getKey().namespace())
                            && entry.getKey().value().endsWith(family.suffix))
                    .toList();
            require(entries.size() == family.variants,
                    "Unexpected " + family + " count: " + entries.size());
            for (var entry : entries) {
                var states = entry.getValue().variantProvider().states();
                require(states.size() == family.states, entry.getKey() + " has " + states.size() + " states");
                for (var state : states) {
                    var vanillaData = vanillaData(family, state);
                    var expected = vanillaShapes.computeIfAbsent(vanillaData, data -> new HashSet<>(
                            Bukkit.createBlockData(data).getCollisionShape(reference).getBoundingBoxes()));
                    var actual = CraftEngineBlocks.getBukkitBlockData(state);
                    require(actual != null, "Missing server block state for " + state);
                    compare(expected, new HashSet<>(actual.getCollisionShape(reference).getBoundingBoxes()), state);
                }
                totalStates += states.size();
            }
        }

        var creatorType = Class.forName("org.leavesmc.leaves.entity.bot.BotCreator");
        var creator = creatorType.getMethod("of", String.class, Location.class)
                .invoke(null, "CeConnectCheck", new Location(world, 670.5, y, 669.5));
        var player = (Player) creatorType.getMethod("spawn").invoke(creator);
        require(player != null, "Leaves could not create the connecting-block placement player");
        try {
            player.setGameMode(GameMode.CREATIVE);
            var cePlayer = BukkitAdaptor.adapt(player);
            if (cePlayer == null) {
                var channel = Class.forName("io.netty.channel.Channel", false,
                        BukkitServerPlayer.class.getClassLoader());
                cePlayer = BukkitServerPlayer.class.getConstructor(BukkitCraftEngine.class, channel)
                        .newInstance(BukkitCraftEngine.instance(), null);
                cePlayer.setPlayer(player);
            }
            var probe = new ConnectionChecks(world, y, player, cePlayer);
            for (var family : Family.values()) {
                var keys = registry.keySet().stream()
                        .filter(key -> "botania".equals(key.namespace()) && key.value().endsWith(family.suffix))
                        .sorted(Comparator.comparing(Key::value))
                        .toList();
                for (var key : keys) {
                    try {
                        if (family == Family.GATE) {
                            probe.checkGate(key);
                        } else {
                            probe.checkConnections(family, key);
                        }
                    } catch (IllegalStateException failure) {
                        probe.failures.add(key + ": " + failure.getMessage());
                    } finally {
                        probe.clear();
                    }
                }
            }
            require(probe.failures.isEmpty(), "Collision states verified: " + totalStates
                    + ". Live connection failures (" + probe.failures.size() + "): "
                    + String.join(" | ", probe.failures));
            String summary = "panes=3 fences=2 gates=2 walls=33 states=" + totalStates
                    + " connections=placed,removed native_panes=both_directions gates=manual,redstone";
            probe.checkVanillaPanes(plugin, error -> {
                probe.clear();
                try {
                    removePlayer(player);
                } catch (ReflectiveOperationException cleanupFailure) {
                    if (error != null) {
                        error.addSuppressed(cleanupFailure);
                    } else {
                        error = cleanupFailure;
                    }
                }
                if (error == null) {
                    complete.accept(summary);
                } else {
                    fail.accept(error);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            removePlayer(player);
            throw failure;
        }
    }

    private static void removePlayer(Player player) throws ReflectiveOperationException {
        Class.forName("org.leavesmc.leaves.entity.bot.Bot").getMethod("remove", boolean.class)
                .invoke(player, false);
    }

    private final World world;
    private final int y;
    private final Player player;
    private final BukkitServerPlayer cePlayer;
    private final Set<Block> fixtures = new LinkedHashSet<>();
    private final List<String> failures = new ArrayList<>();

    private ConnectionChecks(World world, int y, Player player, BukkitServerPlayer cePlayer) {
        this.world = world;
        this.y = y;
        this.player = player;
        this.cePlayer = cePlayer;
    }

    private void checkConnections(Family family, Key key) {
        // The center and east neighbour straddle a chunk border.
        var center = fixture(671, y, 672);
        var east = fixture(672, y, 672);
        var west = fixture(670, y, 672);
        var above = fixture(671, y + 1, 672);
        var reference = fixture(674, y, 670);
        String disconnected = family == Family.WALL ? "none" : "false";
        String connected = family == Family.WALL ? "low" : "true";
        place(center, key);
        expect(center, family, key, reference, "east", disconnected, "west", disconnected);
        place(east, key);
        expect(center, family, key, reference, "east", connected);
        expect(east, family, key, reference, "west", connected);
        if (family == Family.WALL) {
            place(west, key);
            expect(center, family, key, reference, "east", "low", "west", "low", "up", "false");
            above.setType(Material.STONE);
            expect(center, family, key, reference, "east", "tall", "west", "tall", "up", "false");
            above.setType(Material.AIR);
            expect(center, family, key, reference, "east", "low", "west", "low", "up", "false");
            place(above, key);
            expect(center, family, key, reference, "up", "true");
            require(CraftEngineBlocks.remove(above), "Could not remove wall above " + key);
            expect(center, family, key, reference, "east", "low", "west", "low", "up", "false");
        }
        require(CraftEngineBlocks.remove(east), "Could not remove the east neighbour for " + key);
        expect(center, family, key, reference, "east", disconnected);
        east.setType(Material.STONE);
        expect(center, family, key, reference, "east", connected);
        east.setType(Material.AIR);
        expect(center, family, key, reference, "east", disconnected);
        checkVanillaNeighbour(center, east, reference, family, key, connected, disconnected);

        var wet = fixture(674, y, 672);
        wet.setType(Material.WATER, false);
        place(wet, key);
        expect(wet, family, key, reference, "waterlogged", "true");
    }

    private void checkVanillaNeighbour(Block center, Block east, Block reference, Family family, Key key,
                                       String connected, String disconnected) {
        require(CraftEngineBlocks.remove(center), "Could not reset center fixture for " + key);
        east.setBlockData(Bukkit.createBlockData("minecraft:" + family.vanilla), false);
        place(center, key);
        expect(center, family, key, reference, "east", connected);
        if (family != Family.PANE) {
            match(vanillaConnectsWest(east), "Vanilla " + family + " did not connect west to " + key);
        }
        require(CraftEngineBlocks.remove(center), "Could not remove " + key + " beside vanilla " + family);
        if (family != Family.PANE) {
            match(!vanillaConnectsWest(east), "Vanilla " + family + " kept a connection after removing " + key);
        }
        place(center, key);
        east.setType(Material.AIR);
        expect(center, family, key, reference, "east", disconnected);

        if (family == Family.PANE) {
            east.setType(Material.OAK_FENCE);
            expect(center, family, key, reference, "east", "false");
            match(!vanillaConnectsWest(east), "Vanilla fence incorrectly connected to " + key);
            east.setType(Material.AIR);
        }
        if (family == Family.WALL) {
            require(CraftEngineBlocks.remove(center), "Could not reset wall before native gate fixture");
            east.setBlockData(Bukkit.createBlockData("minecraft:oak_fence_gate[facing=north]"), false);
            place(center, key);
            expect(center, family, key, reference, "east", "low");
            match(((Gate) east.getBlockData()).isInWall(),
                    "Vanilla gate did not recognize " + key + " on its west side");
            require(CraftEngineBlocks.remove(center), "Could not remove wall beside native gate");
            match(!((Gate) east.getBlockData()).isInWall(), "Vanilla gate stayed in_wall after wall removal: " + key);
            east.setBlockData(Bukkit.createBlockData("minecraft:oak_fence_gate[facing=east]"), false);
            place(center, key);
            expect(center, family, key, reference, "east", "none");
            match(!((Gate) east.getBlockData()).isInWall(), "Vanilla gate joined a wall along its facing axis: " + key);
            east.setType(Material.AIR);
        }
    }

    private record PanePair(Key key, Block custom, Block vanilla, Block reference) {
    }

    private void checkVanillaPanes(JavaPlugin plugin, Consumer<Throwable> finish) {
        var pairs = new ArrayList<PanePair>();
        try {
            for (String id : List.of("bifrost_pane", "elf_glass_pane", "mana_glass_pane")) {
                int z = 680 + pairs.size() * 3;
                var key = Key.of("botania", id);
                var custom = fixture(671, y, z);
                var vanilla = fixture(672, y, z);
                var reference = fixture(674, y, z);
                vanilla.setType(Material.GLASS_PANE, false);
                place(custom, key);
                pairs.add(new PanePair(key, custom, vanilla, reference));
            }
        } catch (RuntimeException | LinkageError failure) {
            finish.accept(failure);
            return;
        }
        // Vanilla updates its own shape first. The Botania bridge repairs its side next tick.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                for (var pair : pairs) {
                    expect(pair.custom(), Family.PANE, pair.key(), pair.reference(), "east", "true");
                    require(vanillaConnectsWest(pair.vanilla()), "Vanilla pane did not connect to " + pair.key());
                    require(CraftEngineBlocks.remove(pair.custom()), "Could not remove " + pair.key());
                }
            } catch (RuntimeException | LinkageError failure) {
                finish.accept(failure);
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    for (var pair : pairs) {
                        require(!vanillaConnectsWest(pair.vanilla()),
                                "Vanilla pane kept connection after removing " + pair.key());
                    }
                } catch (RuntimeException | LinkageError failure) {
                    finish.accept(failure);
                    return;
                }
                finish.accept(null);
            }, 2L);
        }, 2L);
    }

    private static boolean vanillaConnectsWest(Block block) {
        return switch (block.getBlockData()) {
            case Wall wall -> wall.getHeight(BlockFace.WEST) != Wall.Height.NONE;
            case MultipleFacing facing -> facing.hasFace(BlockFace.WEST);
            default -> throw new IllegalStateException("Unexpected vanilla connection fixture: " + block);
        };
    }

    private void checkGate(Key key) {
        var gate = fixture(671, y, 672);
        var east = fixture(672, y, 672);
        var north = fixture(671, y, 671);
        var reference = fixture(674, y, 670);
        player.setRotation(180, 0);
        place(gate, key);
        expect(gate, Family.GATE, key, reference, "facing", "north", "open", "false",
                "powered", "false", "in_wall", "false");

        east.setType(Material.COBBLESTONE_WALL);
        expect(gate, Family.GATE, key, reference, "in_wall", "true");
        east.setType(Material.AIR);
        expect(gate, Family.GATE, key, reference, "in_wall", "false");
        north.setType(Material.COBBLESTONE_WALL);
        expect(gate, Family.GATE, key, reference, "in_wall", "false");
        north.setType(Material.AIR);

        place(east, Key.of("botania:livingrock_wall"));
        expect(gate, Family.GATE, key, reference, "in_wall", "true");
        expect(east, Family.WALL, Key.of("botania:livingrock_wall"), reference, "west", "low");
        require(CraftEngineBlocks.remove(east), "Could not remove custom wall beside " + key);
        expect(gate, Family.GATE, key, reference, "in_wall", "false");

        toggle(gate);
        expect(gate, Family.GATE, key, reference, "open", "true", "powered", "false");
        toggle(gate);
        expect(gate, Family.GATE, key, reference, "open", "false", "powered", "false");

        north.setType(Material.REDSTONE_BLOCK);
        expect(gate, Family.GATE, key, reference, "open", "true", "powered", "true");
        north.setType(Material.AIR);
        expect(gate, Family.GATE, key, reference, "open", "false", "powered", "false");
    }

    private void place(Block target, Key key) {
        var support = target.getRelative(BlockFace.DOWN);
        if (!fixtures.contains(support)) {
            fixture(support.getX(), support.getY(), support.getZ());
            support.setType(Material.STONE);
        }
        var definition = CraftEngineItems.byId(key);
        require(definition != null, "Missing item definition " + key);
        var item = definition.buildBukkitItem();
        player.getInventory().setItemInMainHand(item);
        var result = definition.behavior().useOnBlock(useOn(support, item, 1));
        require(result.success(), key + " could not be placed at " + target.getLocation() + ": " + result);
    }

    private void toggle(Block gate) {
        var state = CraftEngineBlocks.getCustomBlockState(gate);
        require(state != null && !state.isEmpty(), "Gate missing before manual use");
        var empty = new ItemStack(Material.AIR);
        player.getInventory().setItemInMainHand(empty);
        var result = state.behavior().useWithoutItem(useOn(gate, empty, 0.5), state);
        require(result.success(), "Manual gate interaction failed: " + result);
    }

    private UseOnContext useOn(Block block, ItemStack item, double offsetY) {
        var pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        var hit = new BlockHitResult(new Vec3d(pos.x() + 0.5, pos.y() + offsetY, pos.z() + 0.5),
                Direction.UP, pos, false);
        return new UseOnContext(BukkitAdaptor.adapt(world), cePlayer, InteractionHand.MAIN_HAND,
                BukkitAdaptor.adapt(item), hit);
    }

    private void expect(Block block, Family family, Key key, Block reference, String... properties) {
        var state = CraftEngineBlocks.getCustomBlockState(block);
        require(state != null && !state.isEmpty() && key.equals(state.owner().value().id()),
                "Wrong block instead of " + key + " at " + block.getLocation());
        for (int i = 0; i < properties.length; i += 2) {
            var property = state.getProperty(properties[i]);
            String actual = property == null ? "missing" : state.get(property).toString().toLowerCase(Locale.ROOT);
            require(properties[i + 1].equals(actual), key + " " + properties[i] + " expected "
                    + properties[i + 1] + " but got " + actual + " at " + block.getLocation());
        }
        reference.setBlockData(Bukkit.createBlockData(vanillaData(family, state)), false);
        compare(new HashSet<>(reference.getCollisionShape().getBoundingBoxes()),
                new HashSet<>(block.getCollisionShape().getBoundingBoxes()), state);
    }

    private static String vanillaData(Family family, ImmutableBlockState state) {
        return "minecraft:" + family.vanilla + "[" + state.getPropertiesAsString() + "]";
    }

    private static void compare(Set<BoundingBox> expected, Set<BoundingBox> actual, ImmutableBlockState state) {
        require(expected.equals(actual), state + " collision expected " + expected + " but got " + actual);
    }

    private Block fixture(int x, int y, int z) {
        var block = world.getBlockAt(x, y, z);
        require(block.getType().isAir(), "Occupied connecting-block fixture at " + block.getLocation());
        fixtures.add(block);
        return block;
    }

    private void clear() {
        for (var block : fixtures) {
            if (CraftEngineBlocks.isCustomBlock(block)) {
                CraftEngineBlocks.remove(block);
            }
            block.setType(Material.AIR, false);
        }
        fixtures.clear();
    }

    private void match(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
