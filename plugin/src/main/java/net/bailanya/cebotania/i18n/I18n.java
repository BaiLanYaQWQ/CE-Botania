package net.bailanya.cebotania.i18n;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 插件侧的中文名表。
 *
 * <p>CE 的物品名写成 {@code <lang:botania.item.white_petal>}，翻译发生在<b>客户端</b>；
 * 服务端拿到的 ItemStack 里只有一个翻译键，直接读 displayName 得到的是原始字符串
 * （就是玩家看到的"一串英文"）。所以这里在启动时把资源包的语言文件读进内存，
 * 服务端自己查表。</p>
 */
public final class I18n {

    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    private I18n() {
    }

    public static void install(JavaPlugin plugin) {
        load(plugin, "lang/pack/zh_cn.yml", ZH);
        load(plugin, "lang/pack/en_us.yml", EN);
        plugin.getLogger().info("语言表已装：中文 " + ZH.size() + " 条");
    }

    private static void load(JavaPlugin plugin, String resource, Map<String, String> target) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            // 翻译键长这样：botania.item.white_petal —— 点号必须当普通字符，
            // 否则 Bukkit 的 YAML 会把它当路径分隔符，getKeys 只能拿到 "botania"。
            yaml.options().pathSeparator('\u0000');
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection lang = yaml.getConfigurationSection("lang");
            if (lang == null) {
                return;
            }
            ConfigurationSection section = lang.getConfigurationSection("zh_cn");
            if (section == null) {
                section = lang.getConfigurationSection("en_us");
            }
            if (section == null) {
                return;
            }
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    target.put(key, value);
                }
            }
        } catch (Exception ignored) {
            // 语言文件缺了不影响玩法，退回 id 显示
        }
    }

    /** 按 CE 的翻译键取值。 */
    public static String tr(String key, String fallback) {
        String value = ZH.get(key);
        if (value == null) {
            value = EN.get(key);
        }
        return value == null ? fallback : value;
    }

    /** 物品/方块 id → 中文名。 */
    public static String displayName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "botania";
        String itemKey = namespace + ".item." + path;
        String blockKey = namespace + ".block." + path;
        String name = ZH.get(itemKey);
        if (name == null) {
            name = ZH.get(blockKey);
        }
        if (name == null) {
            name = EN.get(itemKey);
        }
        if (name == null) {
            name = EN.get(blockKey);
        }
        return name == null ? id : name;
    }
}
