package net.bailanya.cebotania.listener;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 空手潜行右键泥土/草方块 —— 翻土找活根。
 *
 * <p>上游（水晶花园开局）就是靠这招在空岛上找第一批资源：土翻成"活根"方块，
 * 挖掉掉 4 个活根。这里照搬，不限制世界。</p>
 */
public final class SoilTurning implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        var player = event.getPlayer();
        if (!player.isSneaking() || !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        var block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material type = block.getType();
        if (type != Material.DIRT && type != Material.GRASS_BLOCK
                && type != Material.PODZOL && type != Material.MYCELIUM
                && type != Material.COARSE_DIRT && type != Material.ROOTED_DIRT) {
            return;
        }
        boolean placed = CraftEngineBlocks.place(
                block.getLocation(), Key.of("botania:root"), false);
        if (placed) {
            event.setCancelled(true);
            try {
                block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK,
                        block.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.2, 0.3, 0.01,
                        type.createBlockData());
            } catch (Throwable ignored) {
                // 粒子缺失不影响功能
            }
        }
    }
}
