package net.bailanya.cebotania.listener;

import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 魔法水晶增强原版附魔台。
 *
 * <p>上游的 {@code getEnchantPowerBonus()}：</p>
 * <ul>
 *   <li>魔法水晶 = 8（相当于 8 个书架）</li>
 *   <li>自然水晶 = 15（相当于 15 个书架，单独一个就够 30 级）</li>
 *   <li>盖亚水晶 = 15</li>
 * </ul>
 *
 * <p>Bukkit 无法修改附魔台的书架计数，所以这里走"额外附加附魔"：
 * 按水晶提供的等效书架数，给附好的物品再追加 1~2 条附魔。</p>
 */
public final class PylonEnchant implements Listener {

    /** 搜索半径（附魔台周围 2 格，和书架判定一致）。 */
    private static final int RANGE = 2;

    /**
     * 附魔台打开时：把水晶的等效书架数折算成附魔等级，直接改三个候选。
     *
     * <p>这是唯一能影响「界面显示等级」的入口 —— {@link EnchantmentOffer}
     * 的等级和花费都是可写的。</p>
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(org.bukkit.event.enchantment.PrepareItemEnchantEvent event) {
        int power = countPylonPower(event.getEnchantBlock());
        if (power <= 0) {
            return;
        }
        // power 是等效书架数，MC 规则：15 书架 = 30 级 → 等级 = 书架 × 2
        int targetLevel = Math.min(30, power * 2);
        var offers = event.getOffers();
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < offers.length; i++) {
            var offer = offers[i];
            if (offer == null) {
                continue;
            }
            // 提升花费（等级），上限 30
            int newCost = Math.min(30, Math.max(offer.getCost(), targetLevel - i * 3));
            offer.setCost(Math.max(1, newCost));
        }
    }

    /** 数附魔台周围的水晶，返回等效书架数。 */
    private static int countPylonPower(Block table) {
        var ceWorld = BukkitAdaptor.adapt(table.getWorld()).storageWorld();
        if (ceWorld == null) {
            return 0;
        }
        int power = 0;
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    var probe = new BlockPos(
                            table.getX() + dx, table.getY() + dy, table.getZ() + dz);
                    var block = net.bailanya.cebotania.platform.Platform.blockAt(ceWorld, probe);
                    if (block == null || block.getType() == Material.AIR) {
                        continue;
                    }
                    var st = CraftEngineBlocks.getCustomBlockState(block);
                    if (st == null || st.isEmpty()) {
                        continue;
                    }
                    String id = st.owner().value().id().value();
                    if (id.equals("mana_pylon")) {
                        power += 8;
                    } else if (id.equals("natura_pylon") || id.equals("gaia_pylon")) {
                        power += 15;
                    }
                }
            }
        }
        return power;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Block table = event.getEnchantBlock();
        var world = table.getWorld();
        var ceWorld = BukkitAdaptor.adapt(world).storageWorld();
        if (ceWorld == null) {
            return;
        }
        // 统计周围的水晶提供的等效书架数
        int power = 0;
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    var probe = new BlockPos(
                            table.getX() + dx, table.getY() + dy, table.getZ() + dz);
                    var block = net.bailanya.cebotania.platform.Platform.blockAt(ceWorld, probe);
                    if (block == null || block.getType() == Material.AIR) {
                        continue;
                    }
                    var st = CraftEngineBlocks.getCustomBlockState(block);
                    if (st == null || st.isEmpty()) {
                        continue;
                    }
                    String id = st.owner().value().id().value();
                    if (id.equals("mana_pylon")) {
                        power += 8;
                    } else if (id.equals("natura_pylon") || id.equals("gaia_pylon")) {
                        power += 15;
                    }
                }
            }
        }
        if (power <= 0) {
            return;
        }
        // 等效书架数 → 等级 = 书架 × 2（15 书架 = 30 级）
        int targetLevel = Math.min(30, Math.max(event.getExpLevelCost(), power * 2));
        ItemStack item = event.getItem();
        int extraCount = power >= 30 ? 2 : 1;
        for (int i = 0; i < extraCount; i++) {
            applyRandomEnchant(item, targetLevel);
        }
    }

    /** 按目标等级挑一条附魔追加（不覆盖已有的，只加高的）。 */
    private static void applyRandomEnchant(ItemStack item, int targetLevel) {
        Enchantment[] pool = {
                Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.UNBREAKING,
                Enchantment.EFFICIENCY, Enchantment.FORTUNE, Enchantment.MENDING,
                Enchantment.FEATHER_FALLING, Enchantment.LOOTING, Enchantment.POWER,
                Enchantment.LUCK_OF_THE_SEA, Enchantment.THORNS,
        };
        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            Enchantment pick = pool[random.nextInt(pool.length)];
            if (!pick.canEnchantItem(item)) {
                continue;
            }
            int current = item.getEnchantmentLevel(pick);
            int max = pick.getMaxLevel();
            // 目标等级越高，可能给的等级越高
            int want = targetLevel >= 30 ? max : Math.max(1, max / 2);
            if (current >= want) {
                continue;
            }
            item.addUnsafeEnchantment(pick, Math.min(max, want));
            return;
        }
    }
}
