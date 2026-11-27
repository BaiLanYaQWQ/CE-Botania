package acceptance;

import com.google.gson.JsonParser;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.pack.AbstractPackManager;
import net.momirealms.craftengine.core.pack.PackGenerationOptions;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

final class PotChecks {
    private static final int EXPECTED_POTS = 103;
    private static final Key VANILLA_POT = Key.of("minecraft:flower_pot");
    private static final String PLANT_MODEL_PARENT = "botania:block/shapes/potted_plant";

    private PotChecks() {
    }

    static List<Key> run(World world) throws ReflectiveOperationException {
        var pots = CraftEngineBlocks.loadedBlocks().keySet().stream()
                .filter(key -> "botania".equals(key.namespace()) && key.value().startsWith("potted_"))
                .sorted(Comparator.comparing(Key::value))
                .toList();
        check(pots.size() == EXPECTED_POTS, "Expected " + EXPECTED_POTS + " pots, found " + pots.size());

        int y = world.getMaxHeight() - 16;
        var vanilla = world.getBlockAt(560, y, 528);
        var support = vanilla.getRelative(0, -1, 0);
        var custom = world.getBlockAt(562, y, 528);
        check(vanilla.getType().isAir() && support.getType().isAir() && custom.getType().isAir(),
                "Occupied flower pot fixtures");
        try {
            support.setType(Material.STONE, false);
            vanilla.setType(Material.FLOWER_POT, false);
            var collision = localCollision(vanilla);
            check(!collision.isEmpty(), "Vanilla flower pot has no collision shape");
            checkVanillaInteraction(world, vanilla, collision);

            for (var key : pots) {
                check(CraftEngineBlocks.place(custom.getLocation(), key, false), "Could not place " + key);
                var state = CraftEngineBlocks.getCustomBlockState(custom);
                check(state != null && !state.isEmpty() && key.equals(state.owner().value().id()),
                        "Wrong custom block at pot fixture for " + key);
                check(VANILLA_POT.equals(state.visualBlockState().ownerId()), "Wrong pot appearance for " + key);
                check(state.hasConstantBlockEntityRenderer(), "Missing plant renderer for " + key);
                check(localCollision(custom).equals(collision), "Collision differs from vanilla flower pot for " + key);
                check(CraftEngineBlocks.remove(custom), "Could not remove " + key);
            }
        } finally {
            if (!custom.getType().isAir()) {
                CraftEngineBlocks.remove(custom);
            }
            vanilla.setType(Material.AIR, false);
            support.setType(Material.AIR, false);
        }

        return pots;
    }

    private static HashSet<BoundingBox> localCollision(Block block) {
        // Bukkit collision shapes are in block-local coordinates.
        return new HashSet<>(block.getCollisionShape().getBoundingBoxes());
    }

    /**
     * The acceptance runs without connected clients. Invoke the actual server block-use methods
     * with an isolated player so vanilla still handles the insert, removal and inventory changes.
     */
    private static void checkVanillaInteraction(World world, Block vanilla, HashSet<BoundingBox> collision)
            throws ReflectiveOperationException {
        check(!CraftEngineBlocks.isCustomBlock(vanilla), "Vanilla empty pot was claimed by CraftEngine");

        var minecraftServer = Class.forName("net.minecraft.server.MinecraftServer");
        var serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
        var level = Class.forName("net.minecraft.world.level.Level");
        var playerType = Class.forName("net.minecraft.world.entity.player.Player");
        var serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        var profileType = Class.forName("com.mojang.authlib.GameProfile");
        var clientInfo = Class.forName("net.minecraft.server.level.ClientInformation");
        var blockPos = Class.forName("net.minecraft.core.BlockPos");
        var direction = Class.forName("net.minecraft.core.Direction");
        var vec3 = Class.forName("net.minecraft.world.phys.Vec3");
        var blockHit = Class.forName("net.minecraft.world.phys.BlockHitResult");
        var hand = Class.forName("net.minecraft.world.InteractionHand");
        var nmsStack = Class.forName("net.minecraft.world.item.ItemStack");

        var server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        var nmsLevel = world.getClass().getMethod("getHandle").invoke(world);
        var profile = profileType.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "PotAcceptance");
        var player = serverPlayer.getConstructor(minecraftServer, serverLevel, profileType, clientInfo)
                .newInstance(server, nmsLevel, profile, clientInfo.getMethod("createDefault").invoke(null));
        var bukkitPlayer = (Player) serverPlayer.getMethod("getBukkitEntity").invoke(player);
        var pos = vanilla.getClass().getMethod("getPosition").invoke(vanilla);
        var point = vec3.getConstructor(double.class, double.class, double.class)
                .newInstance(vanilla.getX() + 0.5, vanilla.getY() + 0.25, vanilla.getZ() + 0.5);
        var hit = blockHit.getConstructor(vec3, direction, blockPos, boolean.class)
                .newInstance(point, direction.getField("UP").get(null), pos, false);
        var item = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                .getMethod("asNMSCopy", ItemStack.class).invoke(null, new ItemStack(Material.DANDELION));

        var getState = vanilla.getClass().getMethod("getBlockState");
        var empty = getState.invoke(vanilla);
        empty.getClass().getMethod("useItemOn", nmsStack, level, playerType, hand, blockHit)
                .invoke(empty, item, nmsLevel, player, hand.getField("MAIN_HAND").get(null), hit);
        check(vanilla.getType() == Material.POTTED_DANDELION && !CraftEngineBlocks.isCustomBlock(vanilla),
                "Vanilla flower insertion did not produce a vanilla potted dandelion");
        check(localCollision(vanilla).equals(collision), "Vanilla potted dandelion collision changed");
        check((int) nmsStack.getMethod("getCount").invoke(item) == 0, "Vanilla planting did not consume a flower");

        var planted = getState.invoke(vanilla);
        planted.getClass().getMethod("useWithoutItem", level, playerType, blockHit)
                .invoke(planted, nmsLevel, player, hit);
        check(vanilla.getType() == Material.FLOWER_POT && !CraftEngineBlocks.isCustomBlock(vanilla),
                "Vanilla flower removal did not restore a vanilla empty pot");
        check(bukkitPlayer.getInventory().contains(Material.DANDELION, 1),
                "Vanilla flower removal did not return the flower");
    }

    static void checkGeneratedModels(List<Key> pots) throws Exception {
        // Run only CraftEngine's generation phase, without a template's optional pack-upload steps.
        var engine = CraftEngine.instance();
        var generate = AbstractPackManager.class.getDeclaredMethod("generatePackAssets", PackGenerationOptions.class);
        generate.setAccessible(true);
        try (var lease = engine.resourceOperations().acquire();
             var pack = (AutoCloseable) generate.invoke(engine.packManager(), PackGenerationOptions.defaults())) {
            var packPath = generate.getReturnType().getMethod("path");
            packPath.setAccessible(true);
            var root = (Path) packPath.invoke(pack);
            try (var paths = Files.walk(root)) {
                var overrides = paths.filter(path -> root.relativize(path).toString().matches(
                                "(?:.*/)?assets/minecraft/(?:blockstates|models/block)/"
                                        + "(?:flower_pot(?:_[^/]+)?|potted_[^/]+)\\.json"))
                        .toList();
                for (var path : overrides) {
                    // CraftEngine may emit the explicit vanilla mapping for the shared empty pot.
                    var contents = Files.readString(path);
                    var data = JsonParser.parseString(contents).getAsJsonObject();
                    var variants = data.getAsJsonObject("variants");
                    var empty = variants == null ? null : variants.getAsJsonObject("");
                    var model = empty == null ? null : empty.get("model");
                    check(path.toString().endsWith("/blockstates/flower_pot.json")
                                    && data.size() == 1 && variants != null && variants.size() == 1
                                    && empty != null && empty.size() == 1 && model != null
                                    && List.of("block/flower_pot", "minecraft:block/flower_pot").contains(model.getAsString()),
                            "Generated resource pack changes a vanilla flower pot asset: " + path + " " + contents);
                }
            }
            check(Files.isRegularFile(root.resolve("assets/botania/models/block/shapes/potted_plant.json")),
                    "Generated pack is missing the plant-only parent model");
            for (var key : pots) {
                var path = root.resolve("assets/botania/models/block/potted_plant/" + key.value() + ".json");
                check(Files.isRegularFile(path), "Generated resource pack is missing " + path);
                try (var reader = Files.newBufferedReader(path)) {
                    var data = JsonParser.parseReader(reader).getAsJsonObject();
                    var plant = data.getAsJsonObject("textures").get("plant").getAsString();
                    check(PLANT_MODEL_PARENT.equals(data.get("parent").getAsString()) && plant.startsWith("botania:"),
                            "Generated flower model does not contain the plant-only parent and texture: " + path);
                    check(Files.isRegularFile(root.resolve("assets/botania/textures/" + plant.substring(8) + ".png")),
                            "Generated pack is missing plant texture " + plant);
                } catch (IOException failure) {
                    throw new IOException("Could not read generated plant model " + path, failure);
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
