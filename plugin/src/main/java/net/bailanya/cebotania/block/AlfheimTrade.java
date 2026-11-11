package net.bailanya.cebotania.block;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 精灵交易：把物品扔进传送门中间 3×3 区域，门会把交换品吐出来。
 *
 * <p>每 10 tick 扫一遍所有精灵门核心，检查中空区域里的掉落物。</p>
 */
public final class AlfheimTrade {

    /** 每 tick 维持消耗。 */
    private static final long MAINTAIN_PER_TICK = 25L;
    /** 每次交易维持的魔力。 */
    private static final long MANA_PER_TRADE = 500;

    private AlfheimTrade() {
    }

    /**
     * 已开启的传送门持续消耗魔力，池子空了就关门。
     *
     * <p>上游每秒约 500 魔力，这里每 20 tick 扣一次。</p>
     */
    /** 给开着的门画特效（绿色粒子填充 3×3 区域）。 */
    public static void drawPortals(org.bukkit.World world) {
        for (var entity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
            // 只在交易扫描时顺带处理，避免额外遍历
        }
        // 遍历已加载区块里的传送门核心（按 chunk PDC 标记）
        for (var chunk : world.getLoadedChunks()) {
            var pdc = chunk.getPersistentDataContainer();
            for (var key : pdc.getKeys()) {
                if (!key.getNamespace().equals("cebotania") || !key.getKey().startsWith("open_")) {
                    continue;
                }
                String[] parts = key.getKey().substring(5).split("_");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    var loc = new org.bukkit.Location(world, x + 0.5, y + 2.0, z + 0.5);
                    world.spawnParticle(org.bukkit.Particle.PORTAL, loc, 12, 1.2, 1.2, 0.1, 0.05);
                    world.spawnParticle(org.bukkit.Particle.WARPED_SPORE, loc, 6, 1.2, 1.2, 0.1, 0.01);
                } catch (NumberFormatException ignored) {
                    // 坏 key 跳过
                }
            }
        }
    }

    public static void tick(CeBotaniaPlugin plugin, org.bukkit.World world) {
        var ceWorld = BukkitAdaptor.adapt(world).storageWorld();
        if (ceWorld == null) {
            return;
        }
        // 遍历世界上所有已加载的方块实体太贵，改成遍历掉落物反查
        for (var entity : world.getEntitiesByClass(Item.class)) {
            var stack = entity.getItemStack();
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            String id = BotaniaItems.idOf(stack);
            if (id.isEmpty()) {
                continue;
            }
            var recipe = plugin.recipes().matchElven(List.of(id));
            if (recipe == null) {
                continue;
            }
            // 找它是否落在某个传送门的中空区域里
            var loc = entity.getLocation();
            var core = findPortalCoreNear(ceWorld, loc);
            if (core == null) {
                continue;
            }
            // 门必须处于开启状态才能交易
            var coreEntity = ceWorld.getBlockEntityAtIfLoaded(core);
            var portal = coreEntity == null ? null
                    : coreEntity.controller.get(AlfheimPortalController.class, 0);
            if (portal == null || !portal.isOpen()) {
                continue;
            }
            // 扣一个物品
            if (stack.getAmount() <= 1) {
                entity.remove();
            } else {
                stack.setAmount(stack.getAmount() - 1);
                entity.setItemStack(stack);
            }
            // 吐产物
            ItemStack result = BotaniaItems.create(recipe.output(), recipe.count());
            if (result != null) {
                var spawnAt = new org.bukkit.Location(world,
                        core.x() + 0.5, core.y() + 1.5, core.z() + 0.5);
                var drop = world.dropItem(spawnAt, result);
                drop.setPickupDelay(10);
                try {
                    world.spawnParticle(org.bukkit.Particle.PORTAL, spawnAt, 40,
                            0.6, 0.6, 0.6, 0.3);
                    // 交易不放传送门音效
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        }
    }

    /** 判断掉落物是否落在某个精灵门的中空区域，返回核心坐标。 */
    private static BlockPos findPortalCoreNear(
            net.momirealms.craftengine.core.world.CEWorld ceWorld,
            org.bukkit.Location loc) {
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        // 核心可能在 (bx, by-1..by-3, bz)，也可能在左右 ±1
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                var probe = new BlockPos(bx + dx, by - dy, bz);
                var block = Platform.blockAt(ceWorld, probe);
                if (block == null) {
                    continue;
                }
                var st = CraftEngineBlocks.getCustomBlockState(block);
                if (st != null && !st.isEmpty()
                        && st.owner().value().id().value().equals("alfheim_portal")) {
                    // 再确认掉落物在这个核心的中空区
                    boolean ax = true;
                    for (var p : AlfheimPortalBehavior.portalInterior(probe, ax)) {
                        if (p.x() == bx && p.y() == by && p.z() == bz) {
                            return probe;
                        }
                    }
                }
            }
        }
        return null;
    }
}
