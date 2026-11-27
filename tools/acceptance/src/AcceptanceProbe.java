package acceptance;

import net.bailanya.cebotania.block.AlfheimPortalBehavior;
import net.bailanya.cebotania.block.AlfheimPortalController;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class AcceptanceProbe extends JavaPlugin {

    private static final int PORTAL_X = 528;
    private static final int PORTAL_Z = 528;
    private static final int MAX_FLOWER_CHUNKS = 16;
    private static final Key PORTAL_GATE = Key.of("botania:alfheim_portal_block");

    private boolean running;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)
                || args.length != 0) {
            return false;
        }
        if (running) {
            getLogger().info("CE_ACCEPT RUNNING");
            return true;
        }

        running = true;
        getLogger().info("CE_ACCEPT START");
        if (Bukkit.getWorlds().isEmpty()) {
            finish("No test world is loaded");
            return true;
        }
        var world = Bukkit.getWorlds().getFirst();
        validatePortalsOnTradeTick(world);
        return true;
    }

    private void validatePortalsOnTradeTick(World world) {
        // Match the portal controller's five-tick trade cadence.
        if (Bukkit.getCurrentTick() % 5 != 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> validatePortalsOnTradeTick(world), 1L);
            return;
        }
        try {
            checkPortal(world, true);
            checkPortal(world, false);
            checkNaturalFlowers(world);
            SparkChecks.run(this, world, this::finish);
        } catch (Exception | LinkageError failure) {
            getLogger().log(Level.WARNING, "Acceptance check failed", failure);
            finish(failure.toString());
        }
    }

    private void checkPortal(World world, boolean alongX) throws ReflectiveOperationException {
        String direction = alongX ? "X" : "Z";
        int y = world.getMaxHeight() - 16;
        var core = new BlockPos(PORTAL_X, y, PORTAL_Z);
        var blocks = new ArrayList<Block>();
        var drops = new ArrayList<Item>();
        try {
            place(world, core, "alfheim_portal", blocks);
            for (int side = -2; side <= 2; side++) {
                for (int height = 0; height <= 4; height++) {
                    if (isFrameBlock(side, height)) {
                        place(world, portalCell(core, alongX, side, height), "livingwood", blocks);
                    }
                }
            }

            CEWorld storage = BukkitAdaptor.adapt(world).storageWorld();
            require(storage != null, "CraftEngine storage world is unavailable");
            var coreEntity = storage.getBlockEntityAtIfLoaded(core);
            var controller = coreEntity == null ? null
                    : coreEntity.controller.get(AlfheimPortalController.class, 0);
            require(controller != null, "Portal controller is unavailable");

            var interior = new ArrayList<Block>();
            for (int side = -1; side <= 1; side++) {
                for (int height = 1; height <= 3; height++) {
                    var block = blockAt(world, portalCell(core, alongX, side, height));
                    require(block.getType().isAir(), "Portal interior is occupied at " + block.getLocation());
                    interior.add(block);
                }
            }

            // Use the production gate placement method without a connected player wielding a wand.
            var open = AlfheimPortalBehavior.class.getDeclaredMethod(
                    "openPortal", CEWorld.class, World.class, BlockPos.class, boolean.class);
            open.setAccessible(true);
            require(Boolean.TRUE.equals(open.invoke(null, storage, world, core, alongX)),
                    direction + " portal opening failed");
            blocks.addAll(interior);

            for (var block : interior) {
                var state = CraftEngineBlocks.getCustomBlockState(block);
                require(state != null && !state.isEmpty() && PORTAL_GATE.equals(state.owner().value().id()),
                        direction + " portal missing gate block at " + block.getLocation());
                var axis = state.getProperty("along_x");
                require(axis != null && Boolean.valueOf(!alongX).equals(state.get(axis)),
                        direction + " portal has incorrect gate orientation at " + block.getLocation());
            }

            // The public one-argument method leaves orientation unset. The controller must recover it.
            controller.setOpen(true);
            var preexistingOutput = findTradeOutput(world, core);
            require(preexistingOutput.isEmpty(), "Portal fixture has preexisting dreamwood drops");
            Item inside = drop(world, portalCell(core, alongX, -1, 1), 2, drops);
            Item outside = drop(world, portalCell(core, !alongX, 1, 1), 1, drops);
            controller.serverTick();
            var outputItems = findTradeOutput(world, core);
            drops.addAll(outputItems);
            int output = outputItems.stream().mapToInt(item -> item.getItemStack().getAmount()).sum();
            require(inside.isValid() && inside.getItemStack().getAmount() == 1,
                    direction + " portal should consume exactly one item from an off-center stack");
            require(outside.isValid() && outside.getItemStack().getAmount() == 1,
                    direction + " portal traded an item outside its plane");
            require(output == 1, direction + " portal produced " + output + " dreamwood, expected one");

            getLogger().info("CE_ACCEPT PORTAL_" + direction + " gates=9 trade=1 outside_preserved=true");
        } finally {
            for (var item : drops) {
                item.remove();
            }
            for (var block : blocks.reversed()) {
                var state = CraftEngineBlocks.getCustomBlockState(block);
                if (state != null && !state.isEmpty()) {
                    CraftEngineBlocks.remove(block);
                }
            }
        }
    }

    private void checkNaturalFlowers(World world) {
        int generated = 0;
        for (int probe = 0; probe < MAX_FLOWER_CHUNKS * 2 && generated < MAX_FLOWER_CHUNKS; probe++) {
            int chunkX = 1024 + probe * 8;
            int chunkZ = 1024;
            if (world.isChunkGenerated(chunkX, chunkZ)) {
                continue;
            }
            var chunk = world.getChunkAt(chunkX, chunkZ);
            require(chunk.isGenerated(), "World did not generate chunk " + chunkX + "," + chunkZ);
            generated++;
            int flowers = countNaturalFlowers(world, chunkX, chunkZ);
            if (flowers > 0) {
                getLogger().info("CE_ACCEPT NATURAL_FLOWERS count=" + flowers
                        + " new_chunk=" + chunkX + "," + chunkZ + " checked_chunks=" + generated);
                return;
            }
        }
        throw new IllegalStateException("No naturally generated mystical flowers in "
                + generated + " fresh chunks");
    }

    private static int countNaturalFlowers(World world, int chunkX, int chunkZ) {
        int flowers = 0;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;
                int surface = Math.min(world.getMaxHeight() - 1,
                        world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
                for (int y = world.getMinHeight() + 1; y <= surface; y++) {
                    if (world.getBlockAt(x, y - 1, z).getType() != Material.GRASS_BLOCK) {
                        continue;
                    }
                    var state = CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
                    if (state != null && !state.isEmpty()) {
                        var id = state.owner().value().id();
                        if ("botania".equals(id.namespace()) && id.value().endsWith("_mystical_flower")) {
                            flowers++;
                        }
                    }
                }
            }
        }
        return flowers;
    }

    private void finish(String failure) {
        running = false;
        if (failure == null) {
            getLogger().info("CE_ACCEPT PASS");
        } else {
            getLogger().severe("CE_ACCEPT FAIL " + failure);
        }
    }

    private static boolean isFrameBlock(int side, int height) {
        if (Math.abs(side) == 2) {
            return height >= 1 && height <= 3;
        }
        return height == 4 || (height == 0 && side != 0);
    }

    private static BlockPos portalCell(BlockPos core, boolean alongX, int side, int height) {
        return new BlockPos(core.x() + (alongX ? side : 0), core.y() + height,
                core.z() + (alongX ? 0 : side));
    }

    private static Block blockAt(World world, BlockPos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    private static void place(World world, BlockPos pos, String id, List<Block> blocks) {
        world.getChunkAt(pos.x() >> 4, pos.z() >> 4);
        var block = blockAt(world, pos);
        require(block.getType().isAir(), "Occupied fixture block at " + block.getLocation());
        require(CraftEngineBlocks.place(block.getLocation(), Key.of("botania", id), false),
                "Could not place " + id + " at " + block.getLocation());
        blocks.add(block);
    }

    private static Item drop(World world, BlockPos pos, int amount, List<Item> drops) {
        var stack = BotaniaItems.create("botania:livingwood", amount);
        require(stack != null, "The livingwood item is unavailable");
        var item = world.dropItem(center(world, pos), stack);
        drops.add(item);
        return item;
    }

    private static List<Item> findTradeOutput(World world, BlockPos core) {
        return world.getNearbyEntitiesByType(Item.class, center(world, core), 3, 4, 3).stream()
                .filter(item -> "botania:dreamwood".equals(BotaniaItems.idOf(item.getItemStack())))
                .toList();
    }

    private static Location center(World world, BlockPos pos) {
        return new Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    private static void require(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
