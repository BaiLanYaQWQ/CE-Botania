package acceptance;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.util.Direction;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Exercises the registered CraftEngine item behavior with a Leaves server-side player. */
final class StepChecks {
    private static final String SLAB = "botania:livingrock_slab";
    private static final String STAIRS = "botania:livingrock_stairs";
    private static final String BRICK_STAIRS = "botania:livingrock_bricks_stairs";

    static void run(World world) throws ReflectiveOperationException {
        int y = world.getMaxHeight() - 18;
        checkVariantCollisions(new Location(world, 608, y, 609));
        Class<?> creatorType = Class.forName("org.leavesmc.leaves.entity.bot.BotCreator");
        var creator = creatorType.getMethod("of", String.class, Location.class)
                .invoke(null, "CeStepCheck", new Location(world, 609.5, y, 610.5));
        Player player = (Player) creatorType.getMethod("spawn").invoke(creator);
        require(player != null, "Leaves could not create the placement player");
        try {
            player.setGameMode(GameMode.CREATIVE);
            var cePlayer = BukkitAdaptor.adapt(player);
            if (cePlayer == null) {
                // A Leaves bot may have a server player before it has a CE network user.
                var channelType = Class.forName("io.netty.channel.Channel", false,
                        BukkitServerPlayer.class.getClassLoader());
                cePlayer = BukkitServerPlayer.class.getConstructor(BukkitCraftEngine.class, channelType)
                        .newInstance(BukkitCraftEngine.instance(), null);
                cePlayer.setPlayer(player);
            }
            var checks = new StepChecks(world, y, player, cePlayer);
            try {
                checks.checkSlabs();
                checks.checkStairs();
            } finally {
                checks.clear();
            }
        } finally {
            Class.forName("org.leavesmc.leaves.entity.bot.Bot").getMethod("remove", boolean.class)
                    .invoke(player, false);
        }
    }

    private static void checkVariantCollisions(Location spot) {
        var vanillaShapes = new HashMap<String, Set<BoundingBox>>();
        int slabs = 0;
        int stairs = 0;
        for (var entry : CraftEngineBlocks.loadedBlocks().entrySet()) {
            var key = entry.getKey();
            if (!key.namespace().equals("botania")) {
                continue;
            }
            String material;
            int expectedStates;
            if (key.value().endsWith("_slab")) {
                material = "minecraft:stone_slab";
                slabs++;
                expectedStates = 6;
            } else if (key.value().endsWith("_stairs")) {
                material = "minecraft:stone_stairs";
                stairs++;
                expectedStates = 80;
            } else {
                continue;
            }
            var states = entry.getValue().variantProvider().states();
            require(states.size() == expectedStates, key + " has " + states.size() + " states");
            for (var state : states) {
                String data = material + "[" + state.getPropertiesAsString() + "]";
                var expectedBoxes = vanillaShapes.computeIfAbsent(data, keyData ->
                        new HashSet<>(Bukkit.createBlockData(keyData).getCollisionShape(spot).getBoundingBoxes()));
                var actual = CraftEngineBlocks.getBukkitBlockData(state);
                require(actual != null, "unbound step state " + state);
                var actualBoxes = new HashSet<>(actual.getCollisionShape(spot).getBoundingBoxes());
                require(expectedBoxes.equals(actualBoxes), state + " collision mismatch for " + data);
            }
        }
        require(slabs == 51 && stairs == 51, "expected 51 slab and stair materials, got " + slabs + " and " + stairs);
    }

    private final World world;
    private final int y;
    private final Player player;
    private final BukkitServerPlayer cePlayer;
    private final Set<Block> fixtures = new LinkedHashSet<>();

    private StepChecks(World world, int y, Player player, BukkitServerPlayer cePlayer) {
        this.world = world;
        this.y = y;
        this.player = player;
        this.cePlayer = cePlayer;
    }

    private void checkSlabs() {
        var lower = world.getBlockAt(612, y, 612);
        place(SLAB, lower, Direction.UP, Direction.NORTH);
        expect(lower, SLAB, "stone_slab[type=bottom]", "type", "bottom");
        use(SLAB, lower, Direction.UP, 0.5, Direction.NORTH);
        expect(lower, SLAB, "stone_slab[type=double]", "type", "double");
        expectSlabDrops(lower, 2);

        var upper = world.getBlockAt(614, y, 612);
        place(SLAB, upper, Direction.DOWN, Direction.NORTH);
        expect(upper, SLAB, "stone_slab[type=top]", "type", "top");
        use(SLAB, upper, Direction.DOWN, 0.5, Direction.NORTH);
        expect(upper, SLAB, "stone_slab[type=double]", "type", "double");

        var wet = world.getBlockAt(616, y, 612);
        place(SLAB, wet, Direction.UP, Direction.NORTH, true);
        expect(wet, SLAB, "stone_slab[type=bottom,waterlogged=true]",
                "type", "bottom", "waterlogged", "true");
        use(SLAB, wet, Direction.UP, 0.5, Direction.NORTH);
        expect(wet, SLAB, "stone_slab[type=double]", "type", "double", "waterlogged", "false");
    }

    private void checkStairs() {
        int x = 612;
        for (var direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            var bottom = world.getBlockAt(x, y, 617);
            place(STAIRS, bottom, Direction.UP, direction);
            expectStairs(bottom, direction, "bottom", "straight");
            var top = world.getBlockAt(x, y, 620);
            place(STAIRS, top, Direction.DOWN, direction);
            expectStairs(top, direction, "top", "straight");
            x += 3;
        }

        checkCorner(612, -1, Direction.WEST, "outer_left", STAIRS);
        checkCorner(615, -1, Direction.EAST, "outer_right", BRICK_STAIRS);
        checkCorner(618, 1, Direction.WEST, "inner_left", STAIRS);
        checkCorner(621, 1, Direction.EAST, "inner_right", BRICK_STAIRS);

        var wet = world.getBlockAt(624, y, 620);
        place(STAIRS, wet, Direction.UP, Direction.EAST, true);
        expect(wet, STAIRS, "stone_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]",
                "facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "true");
    }

    private void checkCorner(int x, int neighborZ, Direction neighborFacing, String shape, String neighborId) {
        // The north neighbour lies in the adjacent chunk.
        var middle = world.getBlockAt(x, y, 624);
        var neighbor = world.getBlockAt(x, y, 624 + neighborZ);
        place(STAIRS, middle, Direction.UP, Direction.NORTH);
        expectStairs(middle, Direction.NORTH, "bottom", "straight");
        place(neighborId, neighbor, Direction.UP, neighborFacing);
        expectStairs(middle, Direction.NORTH, "bottom", shape);
        require(CraftEngineBlocks.remove(neighbor), "could not remove the stair corner neighbor");
        expectStairs(middle, Direction.NORTH, "bottom", "straight");
    }

    private void place(String id, Block target, Direction face, Direction facing) {
        place(id, target, face, facing, false);
    }

    private void place(String id, Block target, Direction face, Direction facing, boolean wet) {
        boolean lower = face == Direction.UP;
        var support = world.getBlockAt(target.getX(), target.getY() + (lower ? -1 : 1), target.getZ());
        fixture(target);
        fixture(support);
        support.setType(Material.STONE);
        if (wet) {
            target.setType(Material.WATER, false);
        }
        use(id, support, face, lower ? 1.0 : 0.0, facing);
    }

    private void expectSlabDrops(Block block, int expectedCount) {
        require(CraftEngineBlocks.remove(block, null, false, true, false),
                "could not break slab for drop check");
        var center = block.getLocation().add(0.5, 0.5, 0.5);
        var drops = world.getNearbyEntitiesByType(Item.class, center, 1.0);
        int count = 0;
        for (var drop : drops) {
            var item = drop.getItemStack();
            if (SLAB.equals(BotaniaItems.idOf(item))) {
                count += item.getAmount();
                drop.remove();
            }
        }
        require(count == expectedCount, "double slab dropped " + count + " slab items");
    }

    private void use(String id, Block clicked, Direction face, double offsetY, Direction facing) {
        float yaw = switch (facing) {
            case EAST -> -90;
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            default -> throw new IllegalArgumentException("not horizontal: " + facing);
        };
        player.setRotation(yaw, 0);
        var definition = CraftEngineItems.byId(id);
        require(definition != null, "missing item definition " + id);
        player.getInventory().setItemInMainHand(definition.buildBukkitItem());
        var hit = new BlockHitResult(new Vec3d(clicked.getX() + 0.5, clicked.getY() + offsetY,
                clicked.getZ() + 0.5), face, new BlockPos(clicked.getX(), clicked.getY(), clicked.getZ()), false);
        var context = new UseOnContext(BukkitAdaptor.adapt(world), cePlayer, InteractionHand.MAIN_HAND,
                BukkitAdaptor.adapt(player.getInventory().getItemInMainHand()), hit);
        var result = definition.behavior().useOnBlock(context);
        require(result.success(), id + " useOnBlock " + result + " at " + clicked.getLocation());
    }

    private void expectStairs(Block block, Direction facing, String half, String shape) {
        String direction = facing.name().toLowerCase(Locale.ROOT);
        expect(block, STAIRS, "stone_stairs[facing=" + direction + ",half=" + half + ",shape=" + shape + "]",
                "facing", direction, "half", half, "shape", shape);
    }

    private void expect(Block block, String id, String vanillaData, String... properties) {
        var state = CraftEngineBlocks.getCustomBlockState(block);
        require(state != null && id.equals(state.owner().value().id().asString()),
                "missing " + id + " at " + block.getLocation());
        for (int i = 0; i < properties.length; i += 2) {
            var property = state.getProperty(properties[i]);
            String actual = property == null ? "missing"
                    : state.get(property).toString().toLowerCase(Locale.ROOT);
            require(properties[i + 1].equals(actual), id + " " + properties[i] + " expected "
                    + properties[i + 1] + " but got " + actual + " at " + block.getLocation());
        }

        var reference = world.getBlockAt(608, y, 610);
        if (!this.fixtures.contains(reference)) {
            fixture(reference);
        }
        reference.setBlockData(Bukkit.createBlockData("minecraft:" + vanillaData), false);
        var expected = new HashSet<>(reference.getCollisionShape().getBoundingBoxes());
        var actual = new HashSet<>(block.getCollisionShape().getBoundingBoxes());
        require(expected.equals(actual), id + " collision for " + vanillaData + " expected "
                + expected + " but got " + actual);
    }

    private void fixture(Block block) {
        require(block.getType().isAir(), "occupied step fixture at " + block.getLocation());
        this.fixtures.add(block);
    }

    private void clear() {
        for (var block : this.fixtures) {
            if (CraftEngineBlocks.isCustomBlock(block)) {
                CraftEngineBlocks.remove(block);
            }
            block.setType(Material.AIR, false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
