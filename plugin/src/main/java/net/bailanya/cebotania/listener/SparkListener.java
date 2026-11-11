package net.bailanya.cebotania.listener;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/**
 * 火花的交互。
 *
 * <p>火花是一个独立的 item_display 实体，只有**直接点它**才会有反应：
 * 普通点击看信息，潜行 + 森林法杖点击拆掉。</p>
 */
public final class SparkListener implements Listener {

    /** 火花实体上记录「宿主池子坐标」的 PDC key 前缀。 */
    public static final String HOST_PREFIX = "cebotania:spark_host:";

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof ItemDisplay display)) {
            return;
        }
        var pdc = display.getPersistentDataContainer();
        if (!pdc.has(new org.bukkit.NamespacedKey("cebotania", "spark"),
                PersistentDataType.BYTE)) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);

        boolean holdingWand = BotaniaItems.holdingWand(player);
        String host = pdc.getOrDefault(
                new org.bukkit.NamespacedKey("cebotania", "spark_host"),
                PersistentDataType.STRING, "?");

        if (holdingWand && player.isSneaking()) {
            display.remove();
            Platform.send(player, "<green>已拆除火花 <gray>(" + host + ")");
            return;
        }
        if (holdingWand) {
            Platform.send(player, "<light_purple>火花 <gray>宿主：<white>" + host);
            Platform.send(player, "<dark_gray>潜行 + 森林法杖点击可拆除");
            return;
        }
        Platform.send(player, "<light_purple>火花 <gray>（手持森林法杖查看详情）");
    }
}
