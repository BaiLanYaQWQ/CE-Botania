package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纯洁雏菊：把相邻的方块"净化"成另一种。
 *
 * <p>它是植物魔法的入门必需品 —— 活木和活石只能靠它变出来，而活木/活石是
 * 花药台、魔力池、魔力发射器的合成材料。上游每 8 tick 转一格。</p>
 *
 * <p>转换表来自上游 datagen 的 {@code data/botania/recipe/pure_daisy/*.json}，
 * 一共 9 条；木头是一类（tag）而不是单个方块，单独处理。</p>
 */
public final class PureDaisyController extends ManaBlockController {

    private static final String ROOT = "cebotania:pure_daisy";
    private static final String TICK_KEY = "tick";

    /** 多久扫一次，找一个能转的方块。 */
    private static final int SCAN_INTERVAL = 10;
    /** 找到目标后还要"酝酿"多久才真的变（上游是 2 秒左右的慢慢净化）。 */
    private static final int CHARGE_TICKS = 40;

    /** 一次转换的目标。 */
    private static final Map<String, String> CONVERSIONS = new LinkedHashMap<>();

    static {
        CONVERSIONS.put("minecraft:stone", "botania:livingrock");
        CONVERSIONS.put("minecraft:netherrack", "minecraft:cobblestone");
        CONVERSIONS.put("minecraft:end_stone", "minecraft:cobbled_deepslate");
        CONVERSIONS.put("minecraft:ice", "minecraft:packed_ice");
        CONVERSIONS.put("minecraft:packed_ice", "minecraft:blue_ice");
        CONVERSIONS.put("minecraft:soul_sand", "minecraft:sand");
        CONVERSIONS.put("minecraft:water", "minecraft:snow_block");
        CONVERSIONS.put("botania:blaze_block", "minecraft:obsidian");
    }

    /** 转换范围：以自身为中心的 3×3×1 水平面（上游就是横着扫一圈）。 */
    private static final int[][] AREA = {
            {-1, 0, -1}, {-1, 0, 0}, {-1, 0, 1},
            {0, 0, -1}, {0, 0, 1},
            {1, 0, -1}, {1, 0, 0}, {1, 0, 1}
    };

    private int tickCounter;
    private int cursor;

    /** 正在净化的目标（相对自机的偏移）；null 表示还没有目标。 */
    private int[] pending;
    /** 已经酝酿了多少刻。 */
    private int charge;

    /** 诊断用：累计转换了多少个方块。 */
    private int converted;

    public PureDaisyController(BlockEntity blockEntity) {
        super(blockEntity, 0L);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    @Override
    protected void saveExtra(net.momirealms.craftengine.libraries.nbt.CompoundTag tag) {
        tag.putInt(TICK_KEY, this.tickCounter);
    }

    @Override
    protected void loadExtra(net.momirealms.craftengine.libraries.nbt.CompoundTag tag) {
        this.tickCounter = tag.getInt(TICK_KEY, 0);
    }

    /** 诊断用：被调度了多少次。 */
    public int ticks() {
        return this.tickCounter;
    }

    @Override
    public void serverTick() {
        this.tickCounter++;
        CEWorld world = world();
        BlockPos pos = position();
        org.bukkit.World bukkit = Platform.bukkit(world);
        if (bukkit == null || pos == null) {
            return;
        }
        Block self = bukkit.getBlockAt(pos.x(), pos.y(), pos.z());

        // 1) 还没锁定目标：每隔一会儿扫一圈 3×3
        if (this.pending == null) {
            if (this.tickCounter % SCAN_INTERVAL != 0) {
                return;
            }
            for (int i = 0; i < AREA.length; i++) {
                int[] offset = AREA[(this.cursor + i) % AREA.length];
                Block target = self.getRelative(offset[0], offset[1], offset[2]);
                if (canConvert(target)) {
                    this.pending = offset;
                    this.charge = 0;
                    this.cursor = (this.cursor + i + 1) % AREA.length;
                    return;
                }
            }
            return;
        }

        // 2) 锁定后要酝酿一段时间——不是放下就变
        Block target = self.getRelative(this.pending[0], this.pending[1], this.pending[2]);
        if (!canConvert(target)) {
            this.pending = null;
            this.charge = 0;
            return;
        }
        this.charge++;
        if (this.charge % 6 == 0) {
            // 净化中的小提示：方块中心冒白点
            try {
                bukkit.spawnParticle(org.bukkit.Particle.WHITE_ASH,
                        target.getLocation().add(0.5, 0.6, 0.5), 2, 0.25, 0.15, 0.25, 0.005);
            } catch (Throwable ignored) {
                // 粒子缺失不影响功能
            }
        }
        if (this.charge < CHARGE_TICKS) {
            return;
        }
        if (convert(target, bukkit)) {
            this.converted++;
        }
        this.pending = null;
        this.charge = 0;
    }

    /** 这格能不能转（只看输入对不对，不真的动它）。 */
    private boolean canConvert(Block target) {
        String id = blockId(target);
        if (id == null) {
            return false;
        }
        if (CONVERSIONS.containsKey(id)) {
            return true;
        }
        return id.startsWith("minecraft:") && (id.endsWith("_log") || id.endsWith("_wood"));
    }

    /** 试着转换一格；转成了返回 true。 */
    private boolean convert(Block target, org.bukkit.World world) {
        String id = blockId(target);
        if (id == null) {
            return false;
        }
        String output = CONVERSIONS.get(id);
        if (output == null) {
            // 木头：任何原木/木头都变成活木（上游用 tag 描述这一类）
            if (id.startsWith("minecraft:") && (id.endsWith("_log") || id.endsWith("_wood"))) {
                output = "botania:livingwood_log";
            } else {
                return false;
            }
        }
        var location = target.getLocation();
        if (output.startsWith("botania:")) {
            if (!CraftEngineBlocks.place(location, Key.of(output), true)) {
                return false;
            }
        } else {
            Material material = Material.matchMaterial(output);
            if (material == null) {
                return false;
            }
            target.setType(material, false);
        }
        spawnEffect(world, location);
        return true;
    }

    /** 方块 id：自定义方块取注册 id，原版取 material 键。 */
    private static String blockId(Block block) {
        var state = CraftEngineBlocks.getCustomBlockState(block);
        if (state != null && !state.isEmpty()) {
            return state.owner().value().id().asString();
        }
        Material material = block.getType();
        if (material.isAir()) {
            return null;
        }
        return material.getKey().toString();
    }

    private void spawnEffect(org.bukkit.World world, org.bukkit.Location location) {
        try {
            world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                    location.clone().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.01);
            world.playSound(location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
        } catch (Throwable ignored) {
            // 粒子/音效缺失不影响功能
        }
    }
}
