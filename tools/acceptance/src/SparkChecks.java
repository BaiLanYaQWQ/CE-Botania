package acceptance;

import net.bailanya.cebotania.block.ManaBlockController;
import net.bailanya.cebotania.block.ManaPoolController;
import net.bailanya.cebotania.block.SparkNetwork;
import net.bailanya.cebotania.block.TerrasteelController;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

final class SparkChecks {
    private static final int SOURCE_X = -769;
    private static final int SINK_X = SOURCE_X + 16;
    private static final int Z = 784;
    private static final int SOURCE_CHUNK = SOURCE_X >> 4;
    private static final int SINK_CHUNK = SINK_X >> 4;
    private static final int CHUNK_Z = Z >> 4;

    static void run(JavaPlugin plugin, World world, Consumer<String> done) {
        var check = new SparkChecks(plugin, world, done);
        check.guard(check::start);
    }

    private final JavaPlugin plugin;
    private final World world;
    private final Consumer<String> done;
    private final int y;
    private UUID sinkSparkId;
    private boolean finished;

    private SparkChecks(JavaPlugin plugin, World world, Consumer<String> done) {
        this.plugin = plugin;
        this.world = world;
        this.done = done;
        this.y = world.getMaxHeight() - 16;
    }

    private void start() {
        world.addPluginChunkTicket(SOURCE_CHUNK, CHUNK_Z, plugin);
        world.addPluginChunkTicket(SINK_CHUNK, CHUNK_Z, plugin);
        place(SOURCE_X, Z, "botania:mana_pool");
        place(SINK_X, Z, "botania:terra_plate");
        // Same target chunk as the in-range sink, but sqrt(16² + 1²) blocks away.
        place(SINK_X, Z + 1, "botania:terra_plate");
        later(5, () -> seedAndUnload(40));
    }

    private void seedAndUnload(int remaining) {
        var source = controller(SOURCE_X, Z, ManaPoolController.class);
        var sink = controller(SINK_X, Z, TerrasteelController.class);
        var outside = controller(SINK_X, Z + 1, TerrasteelController.class);
        if (source == null || sink == null || outside == null) {
            require(remaining > 0, "CraftEngine did not register all three spark host controllers");
            later(2, () -> seedAndUnload(remaining - 1));
            return;
        }
        spawn(SOURCE_X, Z);
        sinkSparkId = spawn(SINK_X, Z);
        spawn(SINK_X, Z + 1);
        require(source.insert(2000) == 2000, "could not seed the source with 2000 mana");
        SparkNetwork.tick(world);
        require(sink.mana() == 2000 && source.mana() == 0 && outside.mana() == 0,
                "unexpected transfer at the 16-block boundary: " + mana(source, sink, outside));

        world.removePluginChunkTicket(SOURCE_CHUNK, CHUNK_Z, plugin);
        world.removePluginChunkTicket(SINK_CHUNK, CHUNK_Z, plugin);
        world.unloadChunkRequest(SOURCE_CHUNK, CHUNK_Z);
        require(world.unloadChunkRequest(SINK_CHUNK, CHUNK_Z),
                "Bukkit refused the destination chunk unload request");
        later(10, () -> checkUnloaded(40));
    }

    private void checkUnloaded(int remaining) {
        boolean chunkLoaded = world.isChunkLoaded(SINK_CHUNK, CHUNK_Z);
        boolean sparkLoaded = SparkNetwork.collect(world).stream()
                .anyMatch(spark -> spark.entity().getUniqueId().equals(sinkSparkId));
        if (chunkLoaded || sparkLoaded) {
            require(remaining > 0, "destination did not unload: chunk=" + chunkLoaded
                    + ", spark=" + sparkLoaded);
            later(10, () -> checkUnloaded(remaining - 1));
            return;
        }

        world.addPluginChunkTicket(SOURCE_CHUNK, CHUNK_Z, plugin);
        world.addPluginChunkTicket(SINK_CHUNK, CHUNK_Z, plugin);
        later(2, () -> checkReloaded(60));
    }

    private void checkReloaded(int remaining) {
        var source = controller(SOURCE_X, Z, ManaPoolController.class);
        var sink = controller(SINK_X, Z, TerrasteelController.class);
        var outside = controller(SINK_X, Z + 1, TerrasteelController.class);
        var spark = SparkNetwork.findAt(world, SINK_X, y, Z);
        if (source == null || sink == null || outside == null || spark == null) {
            require(remaining > 0, "destination spark or CraftEngine host did not reload");
            later(2, () -> checkReloaded(remaining - 1));
            return;
        }
        require(spark.getUniqueId().equals(sinkSparkId),
                "destination spark UUID changed after saving and reloading the chunk");
        require(sink.mana() == 2000 && source.mana() == 0 && outside.mana() == 0,
                "unexpected persisted balance after reload: " + mana(source, sink, outside));
        require(source.insert(2000) == 2000, "could not refill the source after reload");
        SparkNetwork.tick(world);
        require(sink.mana() == 4000 && source.mana() == 0 && outside.mana() == 0,
                "unexpected balance after reload and resumed transfer: " + mana(source, sink, outside));
        plugin.getLogger().info("CE_ACCEPT SPARK boundary=16 transferred=2000 outside=0 unloaded=true uuid_restored=true final_mana=4000");
        finish(null);
    }

    private void place(int x, int z, String id) {
        require(CraftEngineBlocks.place(new Location(world, x, y, z), Key.of(id), true),
                "could not place " + id + " at " + x + "," + y + "," + z);
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
    }

    private UUID spawn(int x, int z) {
        var spark = SparkNetwork.spawn(world, x, y, z, "acceptance");
        require(spark != null, "could not spawn spark at " + x + "," + y + "," + z);
        return spark.getUniqueId();
    }

    private <T extends ManaBlockController> T controller(int x, int z, Class<T> type) {
        var storage = BukkitAdaptor.adapt(world).storageWorld();
        var entity = storage == null ? null : storage.getBlockEntityAtIfLoaded(new BlockPos(x, y, z));
        return entity == null ? null : entity.controller.get(type, 0);
    }

    private void later(long ticks, Runnable step) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> guard(step), ticks);
    }

    private void guard(Runnable step) {
        try {
            step.run();
        } catch (Throwable failure) {
            finish(failure.toString());
        }
    }

    private void finish(String failure) {
        if (finished) {
            return;
        }
        finished = true;
        world.removePluginChunkTicket(SOURCE_CHUNK, CHUNK_Z, plugin);
        world.removePluginChunkTicket(SINK_CHUNK, CHUNK_Z, plugin);
        done.accept(failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String mana(ManaPoolController source, TerrasteelController sink,
                               TerrasteelController outside) {
        return "source=" + source.mana() + ", boundary=" + sink.mana() + ", outside=" + outside.mana();
    }
}
