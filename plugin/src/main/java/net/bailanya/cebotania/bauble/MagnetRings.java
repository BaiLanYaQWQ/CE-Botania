package net.bailanya.cebotania.bauble;

import net.bailanya.cebotania.core.BotaniaRuntime;
import net.bailanya.cebotania.core.TickBus;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 每刻为已佩戴磁化指环的玩家吸引附近掉落物，拾取仍由服务端处理。 */
public final class MagnetRings implements Runnable, Listener {
    private static final int NORMAL_RANGE = 6;
    private static final int GREATER_RANGE = 16;
    private static final int DROP_COOLDOWN = 100;
    private static final int PICKUP_DELAY_LIMIT = 40;
    private static final int MAX_ITEMS_PER_TICK = 201;
    private static final double PULL_SPEED = 0.45;
    private static final Set<String> PROTECTED_BLOCKS = Set.of(
            "botania:mana_pool", "botania:creative_pool", "botania:diluted_pool",
            "botania:fabulous_pool", "botania:terra_plate", "botania:runic_altar");

    private final BaubleEquipment equipment;
    private final Map<UUID, Integer> dropCooldowns = new HashMap<>();

    public MagnetRings(BaubleEquipment equipment) {
        this.equipment = equipment;
    }

    @Override
    public void run() {
        TickBus bus = BotaniaRuntime.tickBus();
        int now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            Integer cooldown = this.dropCooldowns.get(playerId);
            if (cooldown != null && cooldown - now > 0) {
                continue;
            }
            this.dropCooldowns.remove(playerId);
            int range = range(player);
            if (range > 0) {
                attract(player, playerId, range, bus);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (range(player) > 0) {
            this.dropCooldowns.put(player.getUniqueId(), Bukkit.getCurrentTick() + DROP_COOLDOWN);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.dropCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private int range(Player player) {
        if (this.equipment.hasEquipped(player, BaubleEquipment.GREATER_MAGNET_RING)) {
            return GREATER_RANGE;
        }
        return this.equipment.hasEquipped(player, BaubleEquipment.MAGNET_RING) ? NORMAL_RANGE : 0;
    }

    private static void attract(Player player, UUID playerId, int range, TickBus bus) {
        var target = player.getLocation();
        if (bus != null && bus.suppressesMagnets(target)) {
            return;
        }
        target.add(0, 0.75, 0);
        var targetPosition = target.toVector();
        int pulled = 0;
        for (Item item : player.getWorld().getNearbyEntitiesByType(Item.class, target, range)) {
            var location = item.getLocation();
            if (!canPull(item, playerId, location)
                    || (bus != null && bus.suppressesMagnets(location))) {
                continue;
            }
            var center = item.getBoundingBox().getCenter();
            var motion = targetPosition.clone().subtract(center);
            if (motion.lengthSquared() > 1.0) {
                motion.normalize();
            }
            item.setVelocity(motion.multiply(PULL_SPEED));
            if (++pulled >= MAX_ITEMS_PER_TICK) {
                break;
            }
        }
    }

    private static boolean canPull(Item item, UUID playerId, Location location) {
        UUID owner = item.getOwner();
        if (!item.isValid() || item.isDead() || !item.canPlayerPickup() || item.getPickupDelay() >= PICKUP_DELAY_LIMIT
                || (owner != null && !owner.equals(playerId))
                || MagnetItemFilter.protects(item.getItemStack())) {
            return false;
        }
        Block block = location.getBlock();
        return !protectedBlock(block) && !protectedBlock(block.getRelative(BlockFace.DOWN));
    }

    private static boolean protectedBlock(Block block) {
        var state = CraftEngineBlocks.getCustomBlockState(block);
        return state != null && !state.isEmpty()
                && PROTECTED_BLOCKS.contains(state.owner().value().id().asString());
    }
}
