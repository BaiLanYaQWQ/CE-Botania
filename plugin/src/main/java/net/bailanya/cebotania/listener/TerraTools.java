package net.bailanya.cebotania.listener;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 泰拉工具的特殊能力。
 *
 * <ul>
 *   <li><b>泰拉粉碎者</b>：潜行破坏时一次挖 3×3（水平面）</li>
 *   <li><b>泰拉断裂者</b>：破坏原木时连锁砍倒整棵树</li>
 *   <li><b>泰拉之刃</b>：右键释放剑气，命中前方第一个生物</li>
 * </ul>
 */
public final class TerraTools implements Listener {

    /** 剑气最大距离。 */
    private static final double BEAM_RANGE = 12.0;
    /** 剑气伤害。 */
    private static final double BEAM_DAMAGE = 9.0;

    // —— 泰拉粉碎者：3×3 挖掘 ——

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String id = BotaniaItems.idOf(tool);
        if (id.isEmpty()) {
            return;
        }
        Block origin = event.getBlock();

        if (id.equals("botania:terra_pick") && player.isSneaking()) {
            // 3×3 水平面
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block other = origin.getRelative(dx, dy, dz);
                        if (other.getType().isAir() || other.getType() == Material.BEDROCK) {
                            continue;
                        }
                        // 只挖和主方块同类的（不误伤）
                        if (other.getType() != origin.getType()
                                && !other.getType().name().endsWith("_ORE")) {
                            continue;
                        }
                        other.breakNaturally(tool);
                    }
                }
            }
            return;
        }

        if (id.equals("botania:terra_axe") && isLog(origin.getType())) {
            // 连锁砍树
            chopTree(origin, tool, player);
        }
    }

    private static boolean isLog(Material type) {
        String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.equals("CRIMSON_STEM") || name.equals("WARPED_STEM");
    }

    /** 从起点沿相邻原木泛洪破坏（上限 128 个，防卡服）。 */
    private static void chopTree(Block origin, ItemStack tool, Player player) {
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);
        int broken = 0;
        while (!queue.isEmpty() && broken < 128) {
            Block current = queue.poll();
            if (current != origin) {
                current.breakNaturally(tool);
                broken++;
            }
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN,
                    BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block next = current.getRelative(face);
                if (visited.contains(next) || !isLog(next.getType())) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
        if (broken > 0) {
            Platform.send(player, "<green>泰拉断裂者：砍倒 <white>" + (broken + 1) + " <green>个原木");
        }
    }

    // —— 泰拉之刃：剑气 ——

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!BotaniaItems.idOf(tool).equals("botania:terra_sword")) {
            return;
        }
        event.setCancelled(true);
        var world = player.getWorld();
        var eye = player.getEyeLocation();
        var direction = eye.getDirection().normalize();

        // 沿视线找第一个生物
        LivingEntity target = null;
        RayTraceResult hit = world.rayTraceEntities(
                eye, direction, BEAM_RANGE, 0.8,
                entity -> entity instanceof LivingEntity living
                        && !(entity instanceof Player p2 && p2.getUniqueId().equals(player.getUniqueId())));
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
            target = living;
        }

        // 画剑气
        try {
            for (int i = 1; i <= 20; i++) {
                var point = eye.clone().add(direction.clone().multiply(i * 0.6));
                world.spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
                world.spawnParticle(org.bukkit.Particle.END_ROD, point, 1, 0.05, 0.05, 0.05, 0);
            }
        } catch (Throwable ignored) {
            // 粒子缺失不影响伤害
        }

        if (target != null) {
            target.damage(BEAM_DAMAGE, player);
            world.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                    1f, 1.2f);
        } else {
            world.playSound(eye, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
        }
    }
}
