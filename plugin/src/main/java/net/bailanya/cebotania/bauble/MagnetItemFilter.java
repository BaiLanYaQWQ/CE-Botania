package net.bailanya.cebotania.bauble;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** 磁化指环保护能储存魔力的物品、遗物及黑名单成员。 */
final class MagnetItemFilter {

    private static final NamespacedKey VANILLA_BLACKLIST =
            new NamespacedKey("botania", "magnet_ring_blacklist");
    private static final Key CUSTOM_BLACKLIST = Key.of("botania", "magnet_ring_blacklist");

    // 与上游 Forge/Fabric 的 MANA_ITEM 和 RELIC 能力注册对应。
    private static final Set<Key> PROTECTED_ITEMS = Set.of(
            Key.of("botania", "mana_mirror"),
            Key.of("botania", "mana_ring"),
            Key.of("botania", "mana_ring_greater"),
            Key.of("botania", "mana_tablet"),
            Key.of("botania", "terra_pick"),
            Key.of("botania", "dice"),
            Key.of("botania", "flugel_eye"),
            Key.of("botania", "infinite_fruit"),
            Key.of("botania", "king_key"),
            Key.of("botania", "loki_ring"),
            Key.of("botania", "odin_ring"),
            Key.of("botania", "thor_ring")
    );

    private MagnetItemFilter() {
    }

    static boolean protects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        var customItem = CraftEngineItems.byItemStack(stack);
        if (customItem != null) {
            return PROTECTED_ITEMS.contains(customItem.id())
                    || customItem.settings().tags().contains(CUSTOM_BLACKLIST);
        }
        var vanillaTag = Bukkit.getTag(Tag.REGISTRY_ITEMS, VANILLA_BLACKLIST, Material.class);
        return vanillaTag != null && vanillaTag.isTagged(stack.getType());
    }
}
