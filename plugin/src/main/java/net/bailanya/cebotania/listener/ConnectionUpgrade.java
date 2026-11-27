package net.bailanya.cebotania.listener;

import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/** 旧存档只有方块 ID。加载时按真实相邻方块补算连接，边界等待相邻区块加载。 */
public final class ConnectionUpgrade implements Listener {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int INTERIOR = 16;
    private static final int COMPLETE = 31;
    private final JavaPlugin plugin;
    private final NamespacedKey progress;

    public ConnectionUpgrade(JavaPlugin plugin) {
        this.plugin = plugin;
        this.progress = new NamespacedKey(plugin, "connection_upgrade_1");
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        int x = event.getChunk().getX();
        int z = event.getChunk().getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            CEWorld storage = BukkitAdaptor.adapt(world).storageWorld();
            if (storage == null || !world.isChunkLoaded(x, z)) {
                return;
            }
            refresh(world, storage, x, z);
            var center = new BlockPos(x, 0, z);
            for (Direction side : SIDES) {
                var adjacent = center.relative(side);
                refresh(world, storage, adjacent.x(), adjacent.z());
            }
        });
    }

    private void refresh(World world, CEWorld storage, int x, int z) {
        if (!world.isChunkLoaded(x, z)) {
            return;
        }
        var stored = storage.getChunkAtIfLoaded(x, z);
        if (stored == null || stored.isEmpty()) {
            return;
        }
        Chunk chunk = world.getChunkAt(x, z);
        var data = chunk.getPersistentDataContainer();
        int done = data.getOrDefault(progress, PersistentDataType.INTEGER, 0);
        if (done == COMPLETE) {
            return;
        }
        int ready = INTERIOR;
        var chunkPos = new BlockPos(x, 0, z);
        for (int i = 0; i < SIDES.length; i++) {
            var other = chunkPos.relative(SIDES[i]);
            if (world.isChunkLoaded(other.x(), other.z()) && storage.getChunkAtIfLoaded(other.x(), other.z()) != null) {
                ready |= 1 << i;
            }
        }
        int pending = ready & ~done;
        if (pending == 0) {
            return;
        }
        boolean found = false;
        for (var pos : stored.constantBlockEntityRendererPositions()) {
            var state = stored.getBlockState(pos);
            if (!needsUpgrade(state)) {
                continue;
            }
            found = true;
            String id = state.owner().value().id().value();
            boolean allNeighborsAvailable = true;
            for (int i = 0; i < SIDES.length; i++) {
                Direction side = SIDES[i];
                var adjacent = pos.relative(side);
                int area = (adjacent.x() >> 4) == x && (adjacent.z() >> 4) == z ? INTERIOR : 1 << i;
                allNeighborsAvailable &= (ready & area) != 0;
                if ((pending & area) != 0) {
                    updateShape(storage, pos, side);
                }
            }
            if ((pending & INTERIOR) != 0 && id.endsWith("_wall") && pos.y() + 1 < world.getMaxHeight()) {
                updateShape(storage, pos, Direction.UP);
            }
            if (allNeighborsAvailable && id.endsWith("_fence_gate")) {
                var gate = world.getBlockAt(pos.x(), pos.y(), pos.z());
                boolean powered = state.get(state.<Boolean>getProperty("powered"));
                if (gate.isBlockIndirectlyPowered() != powered) {
                    refreshPower(storage, pos, state);
                }
            }
        }
        if (found) {
            data.set(progress, PersistentDataType.INTEGER, done | ready);
        }
    }

    private static boolean needsUpgrade(ImmutableBlockState state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        var key = state.owner().value().id();
        String id = key.value();
        return key.namespace().equals("botania") && (id.endsWith("_pane") || id.endsWith("_fence")
                || id.endsWith("_fence_gate") || id.endsWith("_wall"));
    }

    private static void refreshPower(CEWorld storage, BlockPos pos, ImmutableBlockState state) {
        try {
            Native.NEIGHBOR_CHANGED.invoke(storage.world().minecraftWorld(), LocationUtils.toBlockPos(pos),
                    BlockStateUtils.getBlockOwner(state.customBlockState().minecraftState()), null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法恢复栅栏门的红石状态", e);
        }
    }

    private static void updateShape(CEWorld storage, BlockPos pos, Direction side) {
        var world = storage.world();
        var neighbor = pos.relative(side);
        var other = world.getBlockState(neighbor.x(), neighbor.y(), neighbor.z());
        try {
            Native.SHAPE_CHANGED.invoke(world.minecraftWorld(), DirectionUtils.toNMSDirection(side),
                    LocationUtils.toBlockPos(pos), LocationUtils.toBlockPos(neighbor), other.minecraftState(),
                    UpdateFlags.UPDATE_CLIENTS | UpdateFlags.UPDATE_KNOWN_SHAPE, 1);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法恢复已放置方块的连接", e);
        }
    }

    private static final class Native {
        static final Method SHAPE_CHANGED;
        static final Method NEIGHBOR_CHANGED;
        static {
            try {
                Class<?> pos = Class.forName("net.minecraft.core.BlockPos");
                Class<?> level = Class.forName("net.minecraft.world.level.Level");
                NEIGHBOR_CHANGED = level.getMethod("neighborChanged", pos,
                        Class.forName("net.minecraft.world.level.block.Block"),
                        Class.forName("net.minecraft.world.level.redstone.Orientation"));
                SHAPE_CHANGED = level.getMethod("neighborShapeChanged",
                        Class.forName("net.minecraft.core.Direction"), pos, pos,
                        Class.forName("net.minecraft.world.level.block.state.BlockState"), int.class, int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
