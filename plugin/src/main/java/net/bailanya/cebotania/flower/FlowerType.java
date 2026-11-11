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
        // 萤火之焰 Endoflame：吃掉一份燃料后**持续烧**，1 mana/tick。
        // 上游 FUEL_CAP=32000，实际燃烧刻数 = min(FUEL_CAP, 物品燃烧时间) / 2；
        // 煤 1600 刻 → 烧 800 刻产 800 魔力（40 秒），烧完才吃下一份。
        gen("endoflame", 1, 1, 4000, "fuel");
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
        func("agricarnation", 5, 200, "grow");
        func("fallen_kanade", 120, 1, "heal");
        func("jaded_amaranthus", 100, 30, "bloom");
        func("hopperhock", 3, 5, "collect");
        func("exoflame", 300, 2, "smelt");
        func("bellethorn", 8, 10, "thorn");
        func("dreadthorn", 8, 10, "dreadthorn");
        func("heisei_dream", 100, 10, "dream");
        func("hyacidus", 20, 10, "poison");
        func("medumone", 8, 10, "slow");
        func("tigerseye", 70, 10, "scare");
        func("vinculotus", 8, 10, "bind");
        func("tangleberrie", 6, 10, "tangle");
        func("jiyuulia", 6, 10, "levitate");
        func("solegnolia", 0, 20, "lens");
        func("bergamute", 0, 20, "mute");
        func("daffomill", 6, 4, "blow");
        func("clayconia", 80, 5, "clay");
        func("pollidisiac", 12, 10, "breed");
        func("rannuncarpus", 8, 10, "place");
        func("spectranthemum", 12, 10, "teleport");
        func("loonium", 500, 100, "dungeon");
        func("orechid", 17500, 100, "ore");
        func("orechid_ignem", 20000, 100, "ore_nether");
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

    /**
     * 给森林法杖用的功能说明（中文一句话）。
     *
     * <p>产魔花说"怎么产魔"，功能花说"做什么事"，两者都带上消耗/产出。</p>
     */
    public String describe() {
        String what = switch (this.logic) {
            case "grow" -> "催熟附近的作物（相当于骨粉）";
            case "heal" -> "治疗附近的玩家和被动生物";
            case "bloom" -> "在附近的草地/泥土上种出小花";
            case "collect" -> "把附近的掉落物吸进最近的容器";
            case "smelt" -> "给附近的熔炉持续续燃料";
            case "thorn" -> "攻击附近的怪物";
            case "dreadthorn" -> "攻击附近的亡灵（僵尸/骷髅）";
            case "poison" -> "让附近的怪物中毒";
            case "slow" -> "让附近的怪物变慢";
            case "scare" -> "把附近的怪物推开";
            case "ore" -> "把附近的石头变成矿石";
            case "ore_nether" -> "把附近的下界岩变成下界矿石";
            case "place" -> "把地上的方块物品种回附近";
            case "clay" -> "把附近的沙子变成粘土";
            case "blow" -> "把附近的掉落物吹走";
            case "breed" -> "让附近的两只动物进入繁殖状态";
            case "levitate" -> "让附近的怪物飘起来";
            case "tangle" -> "把附近的生物拽向花心并黏住";
            case "dream" -> "让附近的怪物变慢、变得虚弱";
            case "marble" -> "把附近的石头变成变质岩";
            case "lens" -> "压制附近的花，让它们停止产魔";
            case "mute" -> "降低附近的音量（近似实现，插件端无法真正静音）";
            case "bind" -> "拦住附近的末影人，阻止它们传送";
            case "teleport" -> "把附近的掉落物传送进最近的容器";
            case "dungeon" -> "在附近刷出怪物";
            case "water" -> "贴着水就能产魔";
            case "lava" -> "贴着岩浆就能产魔";
            case "fuel" -> "吃掉可燃物后持续燃烧产魔";
            case "food" -> "吃掉附近掉落的食物产魔";
            case "tnt" -> "引爆附近的 TNT 产魔";
            case "cake" -> "吃掉旁边的蛋糕产魔";
            case "leaves" -> "吃掉附近的树叶产魔";
            case "wool" -> "吃掉附近的羊毛产魔";
            case "xp" -> "吸收附近的经验球产魔";
            case "flower" -> "吃掉附近的其他花产魔";
            default -> "未知效果";
        };
        String how;
        if (this.kind == Kind.FUNCTIONAL) {
            how = this.manaPerAction <= 0
                    ? "免费"
                    : this.manaPerAction + "魔力/" + this.intervalTicks + "t";
        } else {
            how = "+" + this.manaPerAction + "魔力/" + this.intervalTicks + "t";
        }
        return what + " · " + how;
    }
}
