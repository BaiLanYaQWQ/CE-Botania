package net.bailanya.cebotania.block;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockShape;
import net.momirealms.craftengine.core.block.DelegatingBlock;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy.BlockStateBaseProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.AABBProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.shapes.ShapesProxy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public final class MachineShapes implements Listener {

    @EventHandler
    public void applyShapes(CraftEngineReloadEvent event) {
        applyShape("mana_pool", basin(8, 2));
        applyShape("fabulous_pool", basin(8, 2));
        applyShape("diluted_pool", basin(6, 1));
        applyShape("creative_pool", basin(10, 2));

        // Botania 1.20.x ManaSpreaderBlock uses the same inset cube for every bare spreader.
        Object spreader = box(2, 2, 2, 14, 14, 14);
        for (String id : List.of("mana_spreader", "elven_spreader", "gaia_spreader", "redstone_spreader")) {
            applyShape(id, spreader);
        }
    }

    private static void applyShape(String id, Object shape) {
        var definition = CraftEngineBlocks.byId(Key.of("botania", id));
        if (definition == null) {
            return;
        }
        for (var variant : definition.variantProvider().states()) {
            Object state = variant.customBlockState().minecraftState();
            DelegatingBlock block = (DelegatingBlock) BlockStateUtils.getBlockOwner(state);
            var delegate = block.shapeDelegate();
            delegate.bindValue(withShape(shape, delegate.value()));
            // CraftEngine has already cached the carrier's collision by the time it fires this event.
            BlockStateBaseProxy.INSTANCE.initCache(state);
        }
    }

    private static BlockShape withShape(Object shape, BlockShape original) {
        return new BlockShape() {
            @Override
            public Object getShape(Object block, Object[] args) {
                return shape;
            }

            @Override
            public Object getCollisionShape(Object block, Object[] args) {
                return shape;
            }

            @Override
            public Object getSupportShape(Object block, Object[] args) {
                return original.getSupportShape(block, args);
            }
        };
    }

    // Botania 1.20.x ManaPoolBlock uses the same thickness for the base and walls.
    private static Object basin(int height, int thickness) {
        int innerEnd = 16 - thickness;
        ShapesProxy shapes = ShapesProxy.INSTANCE;
        Object result = box(0, 0, 0, 16, thickness, 16);
        result = shapes.or(result, box(0, thickness, 0, thickness, height, 16));
        result = shapes.or(result, box(innerEnd, thickness, 0, 16, height, 16));
        result = shapes.or(result, box(thickness, thickness, 0, innerEnd, height, thickness));
        return shapes.or(result, box(thickness, thickness, innerEnd, innerEnd, height, 16));
    }

    private static Object box(int x0, int y0, int z0, int x1, int y1, int z1) {
        return ShapesProxy.INSTANCE.create(AABBProxy.INSTANCE.newInstance(
                x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0));
    }
}
