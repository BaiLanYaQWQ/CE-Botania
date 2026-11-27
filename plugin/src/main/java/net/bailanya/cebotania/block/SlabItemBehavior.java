package net.bailanya.cebotania.block;

import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.type.SlabType;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.util.Key;

/** 在 CraftEngine 完成含水和物品属性计算后，让双层台阶排出水。 */
public final class SlabItemBehavior extends BlockItemBehavior {

    public static final Key KEY = Key.of("botania", "slab_item");

    public static ItemBehaviorFactory<SlabItemBehavior> factory() {
        return (pack, path, id, section) -> new SlabItemBehavior(
                section.getNonNullValue("block", "resource.argument.type.section").getAsIdentifier());
    }

    private SlabItemBehavior(Key block) {
        super(block);
    }

    @Override
    protected ImmutableBlockState updateBlockStateProperties(ImmutableBlockState state, Item item) {
        var placement = super.updateBlockStateProperties(state, item);
        var type = placement.getProperty("type");
        var waterlogged = placement.getProperty("waterlogged");
        if (type == null || waterlogged == null || placement.get(type) != SlabType.DOUBLE) {
            return placement;
        }
        return ImmutableBlockState.with(placement, waterlogged, false);
    }
}
