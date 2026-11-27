package acceptance;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

final class PoolChecks {

    private PoolChecks() {
    }

    static void run(World world) {
        Location spot = new Location(world, 640, world.getMaxHeight() - 16, 640);
        checkPool(spot, "mana_pool", 8, 2);
        checkPool(spot, "fabulous_pool", 8, 2);
        checkPool(spot, "diluted_pool", 6, 1);
        checkPool(spot, "creative_pool", 10, 2);
    }

    private static void checkPool(Location spot, String name, int height, int thickness) {
        Key id = Key.of("botania", name);
        var block = spot.getBlock();
        if (!block.isEmpty() || !CraftEngineBlocks.place(spot, id, false)) {
            throw new IllegalStateException("Could not place " + id);
        }
        try {
            var boxes = block.getCollisionShape().getBoundingBoxes();
            for (BoundingBox box : boxes) {
                if (!gridAligned(box.getMinX()) || !gridAligned(box.getMinY()) || !gridAligned(box.getMinZ())
                        || !gridAligned(box.getMaxX()) || !gridAligned(box.getMaxY()) || !gridAligned(box.getMaxZ())) {
                    throw new IllegalStateException(id + " has a collision box off the block's pixel grid: " + box);
                }
            }
            // VoxelShape can combine adjacent boxes, so compare occupied cells instead of box order.
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        boolean expected = y < height && (y < thickness
                                || x < thickness || x >= 16 - thickness
                                || z < thickness || z >= 16 - thickness);
                        double cx = (x + 0.5) / 16.0;
                        double cy = (y + 0.5) / 16.0;
                        double cz = (z + 0.5) / 16.0;
                        boolean actual = boxes.stream().anyMatch(box -> box.contains(cx, cy, cz));
                        if (actual != expected) {
                            throw new IllegalStateException(id + " collision mismatch at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        } finally {
            CraftEngineBlocks.remove(block, false);
        }
    }

    private static boolean gridAligned(double coordinate) {
        return coordinate >= 0 && coordinate <= 1 && Math.rint(coordinate * 16) == coordinate * 16;
    }
}
