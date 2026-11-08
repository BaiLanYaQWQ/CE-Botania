package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * 花药台：注水 → 投花瓣和种子 → 出锅成一朵花。
 *
 * <p>上游是把东西丢进去，这里两种都认（手持右键投物 / 掉落物自动收），
 * 但状态只有两样：有没有水、投进去了哪些物品 id。</p>
 */
public final class ApothecaryController extends ManaBlockController {

    private static final String ROOT = "cebotania:apothecary";
    private static final String WATER_KEY = "water";
    private static final String ITEMS_KEY = "items";

    private boolean water;
    private final List<String> items = new ArrayList<>();
    private final boolean waterVariant;

    public ApothecaryController(BlockEntity blockEntity) {
        super(blockEntity, 0L);
        // 有水的花药台是另一个方块 id（apothecary_default_water），
        // 一放下来就该显示有水
        var state = blockEntity.blockState();
        this.waterVariant = state != null
                && state.owner().value().id().asString().endsWith("_water");
    }

    /** 这个方块是不是"有水"的那个变体。 */
    public boolean isWaterVariant() {
        return this.waterVariant;
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    public boolean hasWater() {
        return this.water;
    }

    public void setWater(boolean value) {
        this.water = value;
        markChanged();
    }

    /** 换方块之后，新控制器按变体决定初始有没有水。 */
    public void applyVariantDefaults() {
        if (this.waterVariant) {
            this.water = true;
        }
    }

    private int bubbleTick;

    @Override
    public void serverTick() {
        if (!this.water) {
            return;
        }
        if (++this.bubbleTick % 15 != 0) {
            return;
        }
        var world = world();
        var pos = position();
        if (world == null || pos == null) {
            return;
        }
        org.bukkit.World bukkit = net.bailanya.cebotania.platform.Platform.bukkit(world);
        if (bukkit == null) {
            return;
        }
        var center = new org.bukkit.Location(bukkit,
                pos.x() + 0.5, pos.y() + 0.85, pos.z() + 0.5);
        try {
            bukkit.spawnParticle(org.bukkit.Particle.BUBBLE_POP, center, 2,
                    0.25, 0.05, 0.25, 0.005);
        } catch (Throwable ignored) {
            // 同上
        }
    }



    public List<String> items() {
        return this.items;
    }

    public void addItem(String id) {
        this.items.add(id);
        markChanged();
    }

    /** 取回最后投入的一件（防止放错东西）。 */
    public String popItem() {
        if (this.items.isEmpty()) {
            return null;
        }
        String removed = this.items.remove(this.items.size() - 1);
        markChanged();
        return removed;
    }

    public void clear() {
        this.items.clear();
        this.water = false;
        markChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putByte(WATER_KEY, (byte) (this.water ? 1 : 0));
        tag.putString(ITEMS_KEY, String.join(",", this.items));
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.water = tag.getByte(WATER_KEY) != 0;
        this.items.clear();
        String joined = tag.getString(ITEMS_KEY, "");
        if (!joined.isEmpty()) {
            for (String part : joined.split(",")) {
                if (!part.isBlank()) {
                    this.items.add(part.trim());
                }
            }
        }
    }
}
