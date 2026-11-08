package net.bailanya.cebotania.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物品标签的展开表。
 *
 * <p>上游配方里大量用 {@code #botania:petals/lime} 这样的标签，插件侧要能判断
 * "这朵花瓣算不算 lime"。标签可以嵌套，这里在加载时一次性展开成扁平集合。</p>
 */
public final class Tags {

    private final Map<String, Set<String>> members = new HashMap<>();

    public void put(String tag, Collection<String> values) {
        this.members.put(tag, new HashSet<>(values));
    }

    /** 标签的最终成员（递归展开嵌套标签）。 */
    public Set<String> membersOf(String tag) {
        Set<String> resolved = new HashSet<>();
        collect(tag, resolved, new HashSet<>());
        return resolved;
    }

    private void collect(String tag, Set<String> out, Set<String> visiting) {
        if (out.contains(tag) || !visiting.add(tag)) {
            return;
        }
        Set<String> direct = this.members.get(tag);
        if (direct == null) {
            return;
        }
        for (String value : direct) {
            if (value.startsWith("#")) {
                collect(value.substring(1), out, visiting);
            } else if (this.members.containsKey(value)) {
                // 同一个标签既可能以名字出现也可能以 #name 出现，两种都收
                collect(value, out, visiting);
            } else {
                out.add(value);
            }
        }
        // 标签本身也算成员之外的东西：整组交给调用方按 id 判断
        for (String value : direct) {
            if (!value.startsWith("#")) {
                out.add(value);
            }
        }
    }

    /**
     * 判断物品 id 是否属于标签。
     *
     * <p>我们的标签表只收了 Botania 自己的（{@code botania:*}）；配方里还有大量
     * 原版标签（{@code #minecraft:wool_carpets}、{@code #minecraft:logs} 这类），
     * 那些回退到 Bukkit 的标签系统去查。</p>
     */
    public boolean contains(String tag, String itemId) {
        Set<String> members = membersOf(tag);
        if (!members.isEmpty()) {
            return members.contains(itemId);
        }
        return vanillaContains(tag, itemId);
    }

    /** 查原版标签（Bukkit 自带）。 */
    private boolean vanillaContains(String tag, String itemId) {
        try {
            var key = org.bukkit.NamespacedKey.fromString(tag);
            if (key == null) {
                return false;
            }
            var material = org.bukkit.Material.matchMaterial(itemId);
            if (material == null) {
                return false;
            }
            var itemTag = org.bukkit.Bukkit.getTag("items", key, org.bukkit.Material.class);
            if (itemTag != null && itemTag.isTagged(material)) {
                return true;
            }
            var blockTag = org.bukkit.Bukkit.getTag("blocks", key, org.bukkit.Material.class);
            return blockTag != null && blockTag.isTagged(material);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public List<String> allTagIds() {
        return new ArrayList<>(this.members.keySet());
    }
}
