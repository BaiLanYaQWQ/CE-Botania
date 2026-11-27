package acceptance;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.List;

final class SpreaderChecks {

    private static final BoundingBox BODY = new BoundingBox(2 / 16.0, 2 / 16.0, 2 / 16.0,
            14 / 16.0, 14 / 16.0, 14 / 16.0);

    private SpreaderChecks() {
    }

    static int run(World world) {
        Location spot = new Location(world, 642, world.getMaxHeight() - 16, 640);
        int checked = 0;
        for (String name : List.of("mana_spreader", "elven_spreader", "gaia_spreader", "redstone_spreader")) {
            Key id = Key.of("botania", name);
            var definition = CraftEngineBlocks.byId(id);
            if (definition == null) {
                throw new IllegalStateException("Could not find " + id);
            }
            var facing = definition.getProperty("facing");
            if (facing == null) {
                throw new IllegalStateException(id + " has no facing property");
            }
            var states = definition.variantProvider().states();
            for (Direction direction : Direction.values()) {
                var state = states.stream().filter(variant -> variant.get(facing) == direction)
                        .findFirst().orElseThrow(() -> new IllegalStateException(id + " has no " + direction + " state"));
                var block = spot.getBlock();
                if (!block.isEmpty() || !CraftEngineBlocks.place(spot, state, false)) {
                    throw new IllegalStateException("Could not place " + id + " facing " + direction);
                }
                try {
                    var placed = CraftEngineBlocks.getCustomBlockState(block);
                    if (placed != state) {
                        throw new IllegalStateException("Expected " + id + " facing " + direction + " but got " + placed);
                    }
                    var boxes = block.getCollisionShape().getBoundingBoxes();
                    if (boxes.size() != 1 || !boxes.contains(BODY)) {
                        throw new IllegalStateException(id + " collision mismatch facing " + direction + ": " + boxes);
                    }
                    checked++;
                } finally {
                    CraftEngineBlocks.remove(block, false);
                }
            }
        }
        return checked;
    }
}
