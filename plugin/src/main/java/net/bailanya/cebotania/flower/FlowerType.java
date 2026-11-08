package net.bailanya.cebotania.flower;

import java.util.HashMap;
import java.util.Map;

/**
 * 花的内容表：每种花的产魔/功能参数。
 *
 * <p>数值口径来自上游 {@code vazkii.botania.common.block.flower} 下的各类；
 * 插件只存"每动作多少魔力、多少刻一次、缓冲多大"这三件可配置的事，
 * 具体怎么判定环境由 {@link FlowerLogic} 的实现决定。</p>
 */
public final class FlowerType {

    public enum Kind {
        /** 产魔花：往自己的缓冲里灌魔力，等发射器来吸。 */
        GENERATING,
        /** 功能花：消耗魔力，对周围施加效果。 */
        FUNCTIONAL
    }

    /** 一次产出的魔力。 */
    public final long manaPerAction;
    /** 两次动作之间的刻数。 */
    public final int intervalTicks;
    /** 花内部缓冲上限；发射器来吸也吸不超过它。 */
    public final long bufferCapacity;
    public final Kind kind;
    /** 实现这一朵花行为的逻辑 id。 */
    public final String logic;

    private FlowerType(Kind kind, long manaPerAction, int intervalTicks, long bufferCapacity, String logic) {
        this.kind = kind;
        this.manaPerAction = manaPerAction;
        this.intervalTicks = intervalTicks;
        this.bufferCapacity = bufferCapacity;
        this.logic = logic;
    }

    private static final Map<String, FlowerType> BY_ID = new HashMap<>();

    private static void gen(String id, long mana, int interval, long buffer, String logic) {
        BY_ID.put(id, new FlowerType(Kind.GENERATING, mana, interval, buffer, logic));
    }

    private static void func(String id, long mana, int interval, String logic) {
        BY_ID.put(id, new FlowerType(Kind.FUNCTIONAL, mana, interval, 0, logic));
    }

    static {
        // —— 产魔花：数值取自上游各类（"每 tick 多少 mana"折算到 intervalTicks 一跳）——
        // 萤火之焰 Endoflame：烧一份可燃物，1 mana/tick，单件上限 32000。
        gen("endoflame", 20, 20, 2000, "fuel");
        // 水绣球 Hydroangeas：贴着水源时 1 mana/3 tick（慢，但开局不要钱）。
        gen("hydroangeas", 6, 20, 1000, "water");
        // 热百合 Thermalily：吸岩浆，45 mana/tick，爆发 600 tick。
        gen("thermalily", 900, 20, 9000, "lava");
        // 爆爆花 Entropinnyum：吃掉一次 TNT 爆炸，一次 6500。
        gen("entropinnyum", 6500, 20, 6500, "tnt");
        // 蛋糕花 Kekimurus：吃蛋糕，1800/80 tick。
        gen("kekimurus", 1800, 80, 3600, "cake");
        // 美食花 Gourmaryllis：吃食物，基准 70（上游按食物价值平方再乘连击系数）。
        gen("gourmaryllis", 70, 20, 2000, "food");
        // 啃噬藤 Munchdew：吃树叶，160/4 tick，之后冷却 1600 tick。
        gen("munchdew", 800, 20, 8000, "leaves");
        // 光谱花 Spectrolus：吃对应颜色的羊毛，600/20 tick。
        gen("spectrolus", 600, 20, 4000, "wool");
        // 奥秘玫瑰 Rosa Arcana：吸收经验球，600/20 tick。
        gen("rosa_arcana", 600, 20, 4000, "xp");
        // 噬花者 Rafflowsia：吃掉附近的花，一次 2000 起。
        gen("rafflowsia", 2000, 20, 4000, "flower");

        // —— 功能花：manaPerAction 是每跳消耗的魔力（上游的 COST）——
        func("agricarnation", 5, 3, "grow");
        func("fallen_kanade", 4, 2, "heal");
        func("jaded_amaranthus", 12, 8, "bloom");
        func("hopperhock", 3, 5, "collect");
        func("exoflame", 6, 6, "smelt");
        func("bellethorn", 8, 10, "thorn");
        func("dreadthorn", 8, 10, "dreadthorn");
        func("heisei_dream", 10, 10, "dream");
        func("hyacidus", 6, 10, "poison");
        func("medumone", 8, 10, "slow");
        func("tigerseye", 8, 10, "scare");
        func("vinculotus", 8, 10, "bind");
        func("tangleberrie", 6, 10, "tangle");
        func("jiyuulia", 6, 10, "levitate");
        func("solegnolia", 0, 20, "lens");
        func("bergamute", 0, 20, "mute");
        func("daffomill", 6, 4, "blow");
        func("clayconia", 6, 8, "clay");
        func("pollidisiac", 8, 10, "breed");
        func("rannuncarpus", 8, 10, "place");
        func("spectranthemum", 12, 10, "teleport");
        func("loonium", 500, 100, "dungeon");
        func("orechid", 15000, 100, "ore");
        func("orechid_ignem", 15000, 100, "ore_nether");
        func("marimorphosis", 12000, 100, "marble");
    }

    public static FlowerType byId(String id) {
        return BY_ID.get(id);
    }

    public static boolean isGenerating(String id) {
        FlowerType type = BY_ID.get(id);
        return type != null && type.kind == Kind.GENERATING;
    }

    public static boolean isFunctional(String id) {
        FlowerType type = BY_ID.get(id);
        return type != null && type.kind == Kind.FUNCTIONAL;
    }

    public static Map<String, FlowerType> all() {
        return BY_ID;
    }
}
