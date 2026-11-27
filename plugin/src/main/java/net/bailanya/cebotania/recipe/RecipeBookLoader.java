package net.bailanya.cebotania.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bailanya.cebotania.recipe.RecipeBook.Apothecary;
import net.bailanya.cebotania.recipe.RecipeBook.Elven;
import net.bailanya.cebotania.recipe.RecipeBook.Infusion;
import net.bailanya.cebotania.recipe.RecipeBook.Runic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 按资源文件的顺序加载配方和标签。 */
final class RecipeBookLoader {

    private final ClassLoader loader;
    private final Tags tags = new Tags();
    private final List<Apothecary> apothecary = new ArrayList<>();
    private final List<Infusion> infusions = new ArrayList<>();
    private final List<Runic> runics = new ArrayList<>();
    private final List<Elven> elvens = new ArrayList<>();

    RecipeBookLoader(ClassLoader loader) {
        this.loader = loader;
    }

    RecipeBook load() {
        loadTags();
        loadApothecary();
        loadInfusions();
        loadRunics();
        loadElven();
        return new RecipeBook(this.tags, this.apothecary, this.infusions, this.runics, this.elvens);
    }

    private JsonElement read(String name) {
        try (InputStream in = this.loader.getResourceAsStream("data/" + name)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void loadTags() {
        JsonElement root = read("tags.json");
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

    private void loadApothecary() {
        JsonElement root = read("recipes/petal_apothecary.json");
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

    private void loadInfusions() {
        JsonElement root = read("recipes/mana_infusion.json");
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

    private void loadRunics() {
        JsonElement root = read("recipes/runic_altar.json");
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

    private void loadElven() {
        try (var in = this.loader.getResourceAsStream("data/recipes/elven_trade.json")) {
            if (in == null) {
                return;
            }
            var reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            var array = JsonParser.parseReader(reader).getAsJsonArray();
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
}
