package net.bailanya.cebotania.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    /** 符文祭坛配方。 */
    /** 精灵交易条目。 */
    public record Elven(String input, String tag, String output, int count) {
    }

    public record Runic(String id, List<Ingredient> ingredients, String output, int count, long mana) {
    }

    private final Tags tags = new Tags();
    private final List<Apothecary> apothecary = new ArrayList<>();
    private final List<Infusion> infusions = new ArrayList<>();
    private final List<Runic> runics = new ArrayList<>();
    private final java.util.List<Elven> elvens = new java.util.ArrayList<>();

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
        RecipeBook book = new RecipeBook();
        book.loadTags(loader);
        book.loadApothecary(loader);
        book.loadInfusions(loader);
        book.loadRunics(loader);
        book.loadElven(loader);
        return book;
    }

    private JsonElement read(ClassLoader loader, String name) {
        try (InputStream in = loader.getResourceAsStream("data/" + name)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void loadTags(ClassLoader loader) {
        JsonElement root = read(loader, "tags.json");
        if (root == null || !root.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            List<String> values = new ArrayList<>();
            for (JsonElement v : entry.getValue().getAsJsonArray()) {
                values.add(v.getAsString());
            }
            this.tags.put(entry.getKey(), values);
        }
    }

    private void loadApothecary(ClassLoader loader) {
        JsonElement root = read(loader, "recipes/petal_apothecary.json");
        if (root == null || !root.isJsonArray()) {
            return;
        }
        for (JsonElement e : root.getAsJsonArray()) {
            JsonObject obj = e.getAsJsonObject();
            List<Ingredient> ingredients = new ArrayList<>();
            for (JsonElement ing : obj.getAsJsonArray("ingredients")) {
                Ingredient parsed = Ingredient.parse(ing);
                if (parsed != null) {
                    ingredients.add(parsed);
                }
            }
            Ingredient reagent = obj.has("reagent") ? Ingredient.parse(obj.get("reagent")) : null;
            String output = outputId(obj.getAsJsonObject("output"));
            if (output != null) {
                this.apothecary.add(new Apothecary(
                        obj.get("id").getAsString(), ingredients, reagent, output));
            }
        }
    }

    private void loadInfusions(ClassLoader loader) {
        JsonElement root = read(loader, "recipes/mana_infusion.json");
        if (root == null || !root.isJsonArray()) {
            return;
        }
        for (JsonElement e : root.getAsJsonArray()) {
            JsonObject obj = e.getAsJsonObject();
            JsonArray ingredients = obj.getAsJsonArray("ingredients");
            if (ingredients == null || ingredients.isEmpty()) {
                continue;
            }
            Ingredient input = Ingredient.parse(ingredients.get(0));
            String output = outputId(obj.getAsJsonObject("output"));
            if (input == null || output == null) {
                continue;
            }
            int count = obj.getAsJsonObject("output").has("count")
                    ? obj.getAsJsonObject("output").get("count").getAsInt() : 1;
            long mana = obj.has("mana") ? obj.get("mana").getAsLong() : 0L;
            String catalyst = obj.has("catalyst_block") ? obj.get("catalyst_block").getAsString() : null;
            this.infusions.add(new Infusion(obj.get("id").getAsString(), input, output, count, mana, catalyst));
        }
    }

    private void loadRunics(ClassLoader loader) {
        JsonElement root = read(loader, "recipes/runic_altar.json");
        if (root == null || !root.isJsonArray()) {
            return;
        }
        for (JsonElement e : root.getAsJsonArray()) {
            JsonObject obj = e.getAsJsonObject();
            List<Ingredient> ingredients = new ArrayList<>();
            for (JsonElement ing : obj.getAsJsonArray("ingredients")) {
                Ingredient parsed = Ingredient.parse(ing);
                if (parsed != null) {
                    ingredients.add(parsed);
                }
            }
            String output = outputId(obj.getAsJsonObject("output"));
            if (output == null) {
                continue;
            }
            int count = obj.getAsJsonObject("output").has("count")
                    ? obj.getAsJsonObject("output").get("count").getAsInt() : 1;
            long mana = obj.has("mana") ? obj.get("mana").getAsLong() : 0L;
            this.runics.add(new Runic(obj.get("id").getAsString(), ingredients, output, count, mana));
        }
    }

    private static String outputId(JsonObject output) {
        if (output == null) {
            return null;
        }
        if (output.has("item")) {
            return output.get("item").getAsString();
        }
        return null;
    }

    // —— 匹配 ——

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

    private void loadElven(ClassLoader loader) {
        try (var in = loader.getResourceAsStream("data/recipes/elven_trade.json")) {
            if (in == null) {
                return;
            }
            var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
            var array = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
            for (var element : array) {
                var object = element.getAsJsonObject();
                var ingredients = object.getAsJsonArray("ingredients");
                var output = object.getAsJsonObject("output");
                if (ingredients == null || ingredients.isEmpty() || output == null) {
                    continue;
                }
                var first = ingredients.get(0).getAsJsonObject();
                String inputId = first.has("item") ? first.get("item").getAsString() : null;
                String inputTag = first.has("tag") ? first.get("tag").getAsString() : null;
                if (inputId == null && inputTag == null) {
                    continue;
                }
                if (!output.has("item")) {
                    continue;
                }
                String outId = output.get("item").getAsString();
                int count = output.has("count") ? output.get("count").getAsInt() : 1;
                this.elvens.add(new Elven(inputId, inputTag, outId, count));
            }
        } catch (Throwable ignored) {
            // 数据缺失就跳过
        }
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
