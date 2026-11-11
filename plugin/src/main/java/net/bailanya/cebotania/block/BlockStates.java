package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.ImmutableBlockState;
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

    /**
     * 把方块的 {@code filled} 属性设成指定值。
     *
     * @return 真的改了才返回 true
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    /** 精灵门核心：切换开启/关闭外观。 */
    public static boolean setPortalOpen(BlockEntity entity, boolean open) {
        return setBoolean(entity, OPEN, open);
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
