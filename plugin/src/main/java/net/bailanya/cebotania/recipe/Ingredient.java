package net.bailanya.cebotania.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * 配方里的一个材料槽：要么指定物品，要么指定标签。
 *
 * <p>注意：gson 的 {@link JsonObject} 本身就 implements {@code Map}，所以
 * 不能用 {@code instanceof Map} 来判类型 —— 那样拿到的是 JsonElement 而不是
 * String，全部解析成 null。这里显式走 JsonObject。</p>
 */
public final class Ingredient {

    public final String item;
    public final String tag;

    private Ingredient(String item, String tag) {
        this.item = item;
        this.tag = tag;
    }

    public static Ingredient ofItem(String id) {
        return new Ingredient(id, null);
    }

    public static Ingredient ofTag(String tag) {
        return new Ingredient(null, tag);
    }

    /** 从 {@code {"item":"..."}|{"tag":"..."}} 解析。 */
    public static Ingredient parse(JsonElement node) {
        if (node == null || !node.isJsonObject()) {
            return null;
        }
        JsonObject obj = node.getAsJsonObject();
        JsonElement item = obj.get("item");
        if (item != null && item.isJsonPrimitive()) {
            return ofItem(item.getAsString());
        }
        JsonElement tag = obj.get("tag");
        if (tag != null && tag.isJsonPrimitive()) {
            return ofTag(tag.getAsString());
        }
        return null;
    }

    public boolean matches(String itemId, Tags tags) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        if (this.item != null) {
            return this.item.equals(itemId);
        }
        return tags != null && tags.contains(this.tag, itemId);
    }

    public boolean matchesAny(List<String> itemIds, Tags tags) {
        for (String id : itemIds) {
            if (matches(id, tags)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return this.item != null ? this.item : "#" + this.tag;
    }
}
