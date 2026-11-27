package net.bailanya.cebotania.block;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.block.behavior.FenceBlockBehavior;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.BlockStateWrapper;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.SupportTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import org.bukkit.Bukkit;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** 让 Botania 玻璃板像原版玻璃板一样根据四侧邻接方块改变连接。 */
public final class PaneBehavior extends BukkitBlockBehavior {

    public static final Key KEY = Key.of("botania", "pane");
    private static final Key PANES = Key.of("botania:glass_panes");
    private static final Key WALLS = Key.of("minecraft:walls");
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final Map<Direction, Property<Boolean>> sides;
    private final JavaPlugin plugin;

    public static BlockBehaviorFactory<PaneBehavior> factory(JavaPlugin plugin) {
        return (block, section) -> {
            var sides = new EnumMap<Direction, Property<Boolean>>(Direction.class);
            for (Direction direction : HORIZONTAL) {
                sides.put(direction, BlockBehaviorFactory.getProperty(section.path(), block,
                        direction.name().toLowerCase(Locale.ROOT), Boolean.class));
            }
            return new PaneBehavior(block, sides, plugin);
        };
    }

    private PaneBehavior(BlockDefinition block, Map<Direction, Property<Boolean>> sides, JavaPlugin plugin) {
        super(block);
        this.sides = sides;
        this.plugin = plugin;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        var world = context.getLevel();
        for (Direction direction : HORIZONTAL) {
            var neighbor = context.getClickedPos().relative(direction);
            var adjacent = world.getBlockState(neighbor.x(), neighbor.y(), neighbor.z());
            state = state.with(this.sides.get(direction),
                    connects(adjacent, world.minecraftWorld(), LocationUtils.toBlockPos(neighbor), direction));
        }
        return state;
    }

    @Override
    public Object updateShape(Object block, Object[] args) {
        var direction = DirectionUtils.fromNMSDirection(args[updateShape$direction]);
        var property = this.sides.get(direction);
        var state = BlockStateUtils.getOptionalCustomBlockState(args[0]);
        if (property == null || state.isEmpty()) {
            return super.updateShape(block, args);
        }
        var neighbor = LocationUtils.fromBlockPos(args[updateShape$blockPos]).relative(direction);
        var adjacent = BlockStateUtils.toBlockStateWrapper(args[updateShape$neighborState]);
        var level = args[updateShape$level];
        if (vanillaPane(adjacent) && !Boolean.TRUE.equals(adjacent.getProperty(sideName(direction.opposite())))) {
            refreshVanillaNeighbor(level, neighbor.relative(direction.opposite()), direction);
        }
        return state.get().with(property,
                connects(adjacent, level, LocationUtils.toBlockPos(neighbor), direction))
                .customBlockState().minecraftState();
    }

    @Override
    public void onPlace(Object block, Object[] args) {
        super.onPlace(block, args);
        refreshVanillaNeighbors(args[1], LocationUtils.fromBlockPos(args[2]));
    }

    @Override
    public void affectNeighborsAfterRemoval(Object block, Object[] args) {
        super.affectNeighborsAfterRemoval(block, args);
        refreshVanillaNeighbors(args[1], LocationUtils.fromBlockPos(args[2]));
    }

    private void refreshVanillaNeighbors(Object level, BlockPos pos) {
        if (!LevelProxy.CLASS.isInstance(level)) {
            return;
        }
        var bukkit = LevelProxy.INSTANCE.getWorld(level);
        var world = BukkitAdaptor.adapt(bukkit);
        for (Direction direction : HORIZONTAL) {
            var neighbor = pos.relative(direction);
            if (bukkit.isChunkLoaded(neighbor.x() >> 4, neighbor.z() >> 4)
                    && vanillaPane(world.getBlockState(neighbor.x(), neighbor.y(), neighbor.z()))) {
                refreshVanillaNeighbor(level, pos, direction);
            }
        }
    }

    private void refreshVanillaNeighbor(Object level, BlockPos pos, Direction direction) {
        if (!LevelProxy.CLASS.isInstance(level)) {
            return;
        }
        var bukkit = LevelProxy.INSTANCE.getWorld(level);
        var neighbor = pos.relative(direction);
        var face = DirectionUtils.toBlockFace(direction.opposite());
        // 原版玻璃板按方块类识别玻璃板。等原版自身的形状更新结束再补上相对的连接。
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!bukkit.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)
                    || !bukkit.isChunkLoaded(neighbor.x() >> 4, neighbor.z() >> 4)) {
                return;
            }
            var world = BukkitAdaptor.adapt(bukkit);
            if (!vanillaPane(world.getBlockState(neighbor.x(), neighbor.y(), neighbor.z()))) {
                return;
            }
            var block = bukkit.getBlockAt(neighbor.x(), neighbor.y(), neighbor.z());
            if (block.getBlockData() instanceof MultipleFacing data) {
                var current = world.getBlockState(pos.x(), pos.y(), pos.z());
                boolean connected = connects(current, level, LocationUtils.toBlockPos(pos), direction.opposite());
                if (data.hasFace(face) != connected) {
                    data.setFace(face, connected);
                    block.setBlockData(data, false);
                }
            }
        });
    }

    private static String sideName(Direction direction) {
        return direction.name().toLowerCase(Locale.ROOT);
    }

    private static boolean vanillaPane(BlockStateWrapper state) {
        var id = state.ownerId();
        return !state.isCustom() && "minecraft".equals(id.namespace())
                && (id.value().endsWith("glass_pane") || id.value().endsWith("_bars"));
    }

    private static boolean connects(BlockStateWrapper adjacent, Object world, Object position, Direction direction) {
        if (adjacent.hasTag(PANES) || adjacent.hasTag(WALLS) || vanillaPane(adjacent)) {
            return true;
        }
        return !FenceBlockBehavior.isExceptionForConnection(adjacent)
                && BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isFaceSturdy(adjacent.minecraftState(),
                world, position, DirectionUtils.toNMSDirection(direction.opposite()), SupportTypeProxy.FULL);
    }
}
