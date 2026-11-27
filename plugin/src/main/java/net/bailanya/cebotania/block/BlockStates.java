package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.property.Property;

/**
 * 方块状态切换：把方块在"空 / 有内容"两个外观之间换。
 *
 * <p>魔力池里有没有魔力、花药台里有没有水，都是靠这个把方块换到另一个
 * appearance（另一个原版载体 + 另一套模型）。CE 里同一时刻方块只能有一个外观，
 * 所以必须整体换状态而不是叠加图层。</p>
 */
public final class BlockStates {

    private BlockStates() {
    }

    /** 方块状态里表示"有内容"的属性名。 */
    public static final String FILLED = "filled";

    /** 精灵门核心的开启属性名。 */
    public static final String OPEN = "open";

    /** 精灵门通道的薄片朝向属性名。 */
    public static final String ALONG_X = "along_x";

    /**
     * 把方块的 {@code filled} 属性设成指定值。
     *
     * @return 真的改了才返回 true
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    /** 精灵门通道：切换薄片朝向（true = 沿 x 平面，南北框架用）。 */
    public static boolean setAlongX(BlockEntity entity, boolean alongX) {
        return setBoolean(entity, ALONG_X, alongX);
    }

    /** 精灵门核心：切换开启/关闭外观。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setPortalOpen(BlockEntity entity, boolean open) {
        if (entity == null || entity.world == null || entity.world.getBlockEntityAtIfLoaded(entity.pos) != entity) {
            return false;
        }
        ImmutableBlockState state = entity.blockState();
        if (state == null) {
            return false;
        }
        Property property = state.getProperty(OPEN);
        if (property == null) {
            return false;
        }
        var pos = entity.pos;
        var world = entity.world.world();
        var next = ImmutableBlockState.with(state, property, open);
        var liveState = world.getBlockState(pos.x(), pos.y(), pos.z());
        if (!liveState.ownerId().equals(next.customBlockState().ownerId())) {
            return false;
        }
        if (liveState.registryId() != next.customBlockState().registryId()) {
            // 更新服务器原版区块并同步客户端，同时保留 CE 方块实体和控制器。
            world.setBlockState(pos.x(), pos.y(), pos.z(), next.customBlockState(),
                    UpdateFlags.UPDATE_CLIENTS | UpdateFlags.UPDATE_SKIP_ALL_SIDEEFFECTS);
        } else if (state == next) {
            return false;
        }
        entity.world.setBlockStateAtIfLoaded(pos, next);
        entity.setBlockState(next);
        return true;
    }

    public static boolean setFilled(BlockEntity entity, boolean filled) {
        if (entity == null || entity.world == null || entity.pos == null) {
            return false;
        }
        ImmutableBlockState state = entity.blockState();
        if (state == null) {
            return false;
        }
        Property property = state.getProperty(FILLED);
        if (property == null) {
            return false;
        }
        Object current = state.get(property);
        if (Boolean.valueOf(filled).equals(current)) {
            return false;
        }
        ImmutableBlockState next = ImmutableBlockState.with(state, property, filled);
        entity.world.setBlockStateAtIfLoaded(entity.pos, next);
        entity.setBlockState(next);
        return true;
    }

    /** 通用：把某个布尔属性设成指定值。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean setBoolean(BlockEntity entity, String name, boolean value) {
        if (entity == null || entity.world == null || entity.pos == null) {
            return false;
        }
        ImmutableBlockState state = entity.blockState();
        if (state == null) {
            return false;
        }
        Property property = state.getProperty(name);
        if (property == null) {
            return false;
        }
        Object current = state.get(property);
        if (Boolean.valueOf(value).equals(current)) {
            return false;
        }
        ImmutableBlockState next = ImmutableBlockState.with(state, property, value);
        entity.world.setBlockStateAtIfLoaded(entity.pos, next);
        entity.setBlockState(next);
        return true;
    }
}
