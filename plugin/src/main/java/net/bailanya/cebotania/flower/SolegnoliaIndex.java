package net.bailanya.cebotania.flower;

import net.bailanya.cebotania.block.ManaBlockController;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 按世界和区块索引当前生效的弱磁兰。 */
public final class SolegnoliaIndex {
    public static final int RANGE = 5;
    public static final int CHIBI_RANGE = 1;

    private final Map<ManaBlockController, Flower> flowers = new HashMap<>();
    private final Map<Cell, Set<Flower>> cells = new HashMap<>();

    public void activate(ManaBlockController controller, Block block, int range) {
        UUID world = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        Flower existing = this.flowers.get(controller);
        if (existing != null && existing.cell.world.equals(world)
                && existing.x == x && existing.y == y && existing.z == z && existing.range == range) {
            return;
        }
        remove(controller);
        Cell cell = new Cell(world, x >> 4, z >> 4);
        Flower flower = new Flower(controller, cell, x, y, z, range);
        this.flowers.put(controller, flower);
        this.cells.computeIfAbsent(cell, key -> new HashSet<>()).add(flower);
    }

    public void remove(ManaBlockController controller) {
        Flower flower = this.flowers.remove(controller);
        if (flower == null) {
            return;
        }
        Set<Flower> cell = this.cells.get(flower.cell);
        cell.remove(flower);
        if (cell.isEmpty()) {
            this.cells.remove(flower.cell);
        }
    }

    public void clear() {
        this.flowers.clear();
        this.cells.clear();
    }

    public boolean suppresses(Location location) {
        if (this.cells.isEmpty()) {
            return false;
        }
        UUID world = location.getWorld().getUID();
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        int maxChunkX = (blockX + RANGE) >> 4;
        int maxChunkZ = (blockZ + RANGE) >> 4;
        for (int chunkX = (blockX - RANGE) >> 4; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = (blockZ - RANGE) >> 4; chunkZ <= maxChunkZ; chunkZ++) {
                Set<Flower> flowersInCell = this.cells.get(new Cell(world, chunkX, chunkZ));
                if (flowersInCell == null) {
                    continue;
                }
                for (Flower flower : flowersInCell) {
                    double dx = location.getX() - (flower.x + 0.5);
                    double dy = location.getY() - (flower.y + 0.5);
                    double dz = location.getZ() - (flower.z + 0.5);
                    if (dx * dx + dy * dy + dz * dz <= flower.range * flower.range) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record Cell(UUID world, int x, int z) {
    }

    private record Flower(ManaBlockController controller, Cell cell, int x, int y, int z, int range) {
    }
}
