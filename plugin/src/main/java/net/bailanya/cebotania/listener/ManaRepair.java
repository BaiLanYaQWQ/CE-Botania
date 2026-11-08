package net.bailanya.cebotania.listener;

import net.bailanya.cebotania.platform.BotaniaItems;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 魔钢装备的"用魔力顶耐久"特性。
 *
 * <p>上游 {@code ToolCommons.damageItemIfPossible}：每掉 1 点耐久要花
 * {@code manaPerDamage} 点魔力，只要魔力够就不掉耐久；附魔"耐久"越高，
 * 实际扣魔力的次数越少（{@code random(unbreaking + 1) == 0} 才真扣）。</p>
 *
 * <p>魔力存在物品自己的 PDC 里，用<b>手持装备右键魔力池</b>充能。</p>
 */
public final class ManaRepair implements Listener {

    /** 上游 manaPerDamage。 */
    public static final long MANA_PER_DAMAGE = 5L;
    /** 一件装备最多存多少魔力。 */
    public static final long CAPACITY = 20_000L;

    private final NamespacedKey key;

    public ManaRepair(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "stored_mana");
    }

    /** 这件物品是不是能存魔力的魔钢装备。 */
    public static boolean isManasteel(ItemStack stack) {
        String id = BotaniaItems.idOf(stack);
        return id.startsWith("botania:manasteel_") || id.startsWith("botania:elementium_")
                || id.startsWith("botania:terrasteel_");
    }

    public long stored(ItemStack stack) {
        if (stack == null || !isManasteel(stack)) {
            return 0;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Long value = meta.getPersistentDataContainer().get(this.key, PersistentDataType.LONG);
        return value == null ? 0 : value;
    }

    public void setStored(ItemStack stack, long amount) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        long clamped = Math.max(0, Math.min(CAPACITY, amount));
        meta.getPersistentDataContainer().set(this.key, PersistentDataType.LONG, clamped);
        stack.setItemMeta(meta);
    }

    /** 往装备里灌魔力，返回实际吃进去的量。 */
    public long charge(ItemStack stack, long amount) {
        long current = stored(stack);
        long room = CAPACITY - current;
        long accepted = Math.max(0, Math.min(room, amount));
        if (accepted > 0) {
            setStored(stack, current + accepted);
        }
        return accepted;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        ItemStack stack = event.getItem();
        long stored = stored(stack);
        if (stored < MANA_PER_DAMAGE) {
            return;
        }
        // 附魔耐久越高，越可能"白嫖"这次耐久伤害
        int unbreaking = 0;
        try {
            unbreaking = stack.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
        } catch (Throwable ignored) {
            // 老版本没有这个 API 就按 0 算
        }
        int remaining = event.getDamage();
        long spent = 0;
        while (remaining > 0 && (stored - spent) >= MANA_PER_DAMAGE) {
            if (ThreadLocalRandom.current().nextInt(Math.max(1, unbreaking + 1)) == 0) {
                spent += MANA_PER_DAMAGE;
            }
            remaining--;
        }
        if (remaining == event.getDamage()) {
            return;  // 一点都没抵消就别动了
        }
        if (spent > 0) {
            setStored(stack, stored - spent);
        }
        event.setDamage(remaining);
    }
}
