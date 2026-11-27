package net.bailanya.cebotania.block;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.SupportTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Wall;

import java.lang.reflect.Method;
import java.util.Locale;

/** Delegates connections and the post/arm geometry to the server's native wall. */
public final class WallBehavior extends BukkitBlockBehavior {
    public static final Key KEY = Key.of("botania", "wall");
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public static BlockBehaviorFactory<WallBehavior> factory() {
        return (block, config) -> new WallBehavior(block);
    }

    private WallBehavior(BlockDefinition block) {
        super(block);
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        return calculate(state, context.getWorld().minecraftWorld(), context.getClickedPos(), null, null);
    }

    @Override
    public Object updateShape(Object owner, Object[] args) {
        Object original = args[0];
        var state = BlockStateUtils.getOptionalCustomBlockState(original).orElse(null);
        Direction changed = DirectionUtils.fromNMSDirection(args[updateShape$direction]);
        if (state == null || changed == Direction.DOWN) {
            return original;
        }
        var updated = calculate(state, args[updateShape$level],
                LocationUtils.fromBlockPos(args[updateShape$blockPos]), changed, args[updateShape$neighborState]);
        return updated.customBlockState().minecraftState();
    }

    private static ImmutableBlockState calculate(ImmutableBlockState state, Object world, BlockPos pos,
                                                  Direction changed, Object changedState) {
        NativeWalls nativeWalls = NativeWalls.INSTANCE;
        BlockPos above = pos.relative(Direction.UP);
        Object abovePos = LocationUtils.toBlockPos(above);
        Object aboveState = changed == Direction.UP ? changedState
                : BlockGetterProxy.INSTANCE.getBlockState(world, abovePos);
        aboveState = vanillaNeighbor(aboveState);
        Wall current = (Wall) BlockStateUtils.fromBlockData(state.visualBlockState().minecraftState());
        boolean[] connected = new boolean[HORIZONTAL.length];
        for (int i = 0; i < HORIZONTAL.length; i++) {
            Direction side = HORIZONTAL[i];
            // A single neighbour update changes one arm. The vanilla rule recalculates every arm's height.
            if (changed != null && changed != side) {
                connected[i] = current.getHeight(FACES[i]) != Wall.Height.NONE;
                continue;
            }
            Object neighborPos = LocationUtils.toBlockPos(pos.relative(side));
            Object neighbor = changed != null ? changedState
                    : BlockGetterProxy.INSTANCE.getBlockStateIfLoaded(world, neighborPos);
            if (neighbor != null) {
                Object nativeSide = DirectionUtils.toNMSDirection(side.opposite());
                Object neighborState = vanillaNeighbor(neighbor);
                boolean sturdy = BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isFaceSturdy(
                        neighborState, world, neighborPos, nativeSide, SupportTypeProxy.FULL);
                connected[i] = nativeWalls.connects(neighborState, sturdy, nativeSide);
            }
        }
        Wall shape = nativeWalls.shape(world, abovePos, aboveState, connected);
        for (int i = 0; i < HORIZONTAL.length; i++) {
            state = state.with(state.<String>getProperty(HORIZONTAL[i].name().toLowerCase(Locale.ROOT)),
                    shape.getHeight(FACES[i]).name().toLowerCase(Locale.ROOT));
        }
        return state.with(state.<Boolean>getProperty("up"), shape.isUp());
    }

    private static Object vanillaNeighbor(Object neighbor) {
        var custom = BlockStateUtils.getOptionalCustomBlockState(neighbor).orElse(null);
        if (custom == null) {
            return neighbor;
        }
        var appearance = custom.visualBlockState();
        String material = appearance.ownerId().value();
        // The native wall identifies some neighbours by their block class, not by shape alone.
        if (material.endsWith("_wall") || material.endsWith("_fence_gate")
                || material.endsWith("_pane") || material.equals("iron_bars")) {
            return appearance.minecraftState();
        }
        return neighbor;
    }

    private static final class NativeWalls {
        private static final NativeWalls INSTANCE = new NativeWalls();

        private final Object state;
        private final Object wall;
        private final Method connectsTo;
        private final Method updateShape;

        private NativeWalls() {
            this.state = BlockStateUtils.blockDataToBlockState(Bukkit.createBlockData("minecraft:cobblestone_wall"));
            this.wall = BlockStateUtils.getBlockOwner(this.state);
            Class<?> wallClass = this.wall.getClass();
            this.connectsTo = method(wallClass, "connectsTo", 3, boolean.class);
            this.updateShape = method(wallClass, "updateShape", 8, this.state.getClass(), boolean.class);
        }

        private boolean connects(Object neighbor, boolean sturdy, Object face) {
            return (boolean) invoke(this.connectsTo, neighbor, sturdy, face);
        }

        private Wall shape(Object world, Object abovePos, Object aboveState, boolean[] adjacent) {
            Object newState = invoke(this.updateShape, world, this.state, abovePos, aboveState,
                    adjacent[0], adjacent[1], adjacent[2], adjacent[3]);
            return (Wall) BlockStateUtils.fromBlockData(newState);
        }

        private Object invoke(Method method, Object... args) {
            try {
                return method.invoke(this.wall, args);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Vanilla wall shape calculation failed", failure);
            }
        }

        private static Method method(Class<?> type, String name, int parameters, Class<?> returns, Class<?>... tail) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters
                        && method.getReturnType().equals(returns)
                        && (tail.length == 0 || method.getParameterTypes()[parameters - 1].equals(tail[0]))) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new IllegalStateException("Vanilla wall method unavailable: " + name);
        }
    }
}
