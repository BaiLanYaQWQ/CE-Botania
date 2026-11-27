package net.bailanya.cebotania.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方总入口：从 jar 里的 {@code data/} 读配方与标签，提供匹配。
 *
 * <p>数据由 {@code tools/gen_plugin_data.py} 从上游 datagen 归一化而来，
 * 插件启动时一次性读完，之后全在内存里匹配。</p>
 */
public final class RecipeBook {

    /** 花药台配方：若干花瓣 + 一个种子（reagent）。 */
    public record Apothecary(String id, List<Ingredient> ingredients, Ingredient reagent, String output) {
    }

    /** 魔力池灌注配方。 */
    public record Infusion(String id, Ingredient input, String output, int count, long mana,
                           String catalystBlock) {
    }

    /** 精灵交易条目。 */
    public record Elven(String input, String tag, String output, int count) {
    }

    /** 符文祭坛配方。 */
    public record Runic(String id, List<Ingredient> ingredients, String output, int count, long mana) {
    }

    private final Tags tags;
    private final List<Apothecary> apothecary;
    private final List<Infusion> infusions;
    private final List<Runic> runics;
    private final List<Elven> elvens;

    public RecipeBook() {
        this(new Tags(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    RecipeBook(Tags tags, List<Apothecary> apothecary, List<Infusion> infusions,
               List<Runic> runics, List<Elven> elvens) {
        this.tags = tags;
        this.apothecary = apothecary;
        this.infusions = infusions;
        this.runics = runics;
        this.elvens = elvens;
    }

    public Tags tags() {
        return this.tags;
    }

    public List<Apothecary> apothecary() {
        return this.apothecary;
    }

    public List<Infusion> infusions() {
        return this.infusions;
    }

    public List<Runic> runics() {
        return this.runics;
    }

    public String summary() {
        return "配方 " + (this.apothecary.size() + this.infusions.size() + this.runics.size())
                + " 条（花药台 " + this.apothecary.size()
                + " / 灌注 " + this.infusions.size()
                + " / 符文 " + this.runics.size()
                + "），标签 " + this.tags.allTagIds().size() + " 组";
    }

    public static RecipeBook load(ClassLoader loader) {
        return new RecipeBookLoader(loader).load();
    }

    /** 花药台：投入的物品里挑一个当种子，其余必须凑齐配方。 */
    public Apothecary matchApothecary(List<String> thrown) {
        if (thrown.isEmpty()) {
            return null;
        }
        for (Apothecary recipe : this.apothecary) {
            if (recipe.ingredients().size() != thrown.size() - 1) {
                continue;
            }
            for (int reagentIndex = 0; reagentIndex < thrown.size(); reagentIndex++) {
                if (recipe.reagent() != null
                        && !recipe.reagent().matches(thrown.get(reagentIndex), this.tags)) {
                    continue;
                }
                List<String> rest = new ArrayList<>(thrown);
                rest.remove(reagentIndex);
                if (matchMultiset(recipe.ingredients(), rest)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /** 多重集合匹配：每个材料槽消耗掉一个满足它的物品。 */
    private boolean matchMultiset(List<Ingredient> need, List<String> have) {
        if (need.size() != have.size()) {
            return false;
        }
        boolean[] used = new boolean[have.size()];
        for (Ingredient ingredient : need) {
            boolean hit = false;
            for (int i = 0; i < have.size(); i++) {
                if (!used[i] && ingredient.matches(have.get(i), this.tags)) {
                    used[i] = true;
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /** 魔力池灌注：单输入匹配。 */
    public Infusion matchInfusion(String inputId) {
        for (Infusion infusion : this.infusions) {
            if (infusion.input().matches(inputId, this.tags)) {
                return infusion;
            }
        }
        return null;
    }

    /** 精灵交易：单件物品匹配。 */
    public Elven matchElven(List<String> items) {
        if (items.size() != 1) {
            return null;
        }
        String target = items.get(0);
        for (Elven elven : this.elvens) {
            if (elven.input() != null && elven.input().equals(target)) {
                return elven;
            }
            if (elven.tag() != null && this.tags.contains(elven.tag(), target)) {
                return elven;
            }
        }
        return null;
    }

    /** 符文祭坛：材料多重集合匹配。 */
    public Runic matchRunic(List<String> thrown) {
        if (thrown.isEmpty()) {
            return null;
        }
        for (Runic runic : this.runics) {
            if (runic.ingredients().size() != thrown.size()) {
                continue;
            }
            if (matchMultiset(runic.ingredients(), thrown)) {
                return runic;
            }
        }
        return null;
    }

    /** 某个物品能用来做什么灌注（给命令/查询用）。 */
    public Map<String, Object> describe() {
        Map<String, Object> out = new HashMap<>();
        out.put("apothecary", this.apothecary.size());
        out.put("infusion", this.infusions.size());
        out.put("runic", this.runics.size());
        return out;
    }
}
