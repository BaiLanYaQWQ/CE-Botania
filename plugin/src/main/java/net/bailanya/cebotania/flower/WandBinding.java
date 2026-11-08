package net.bailanya.cebotania.flower;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 森林法杖的"选中 → 绑定"暂存区。
 *
 * <p>原版森林法杖的用法：先对着花潜行右键选中，再对着发射器潜行右键把它绑上去，
 * 之后发射器只从这朵花收魔力。这里只存"玩家当前选了哪个坐标"。</p>
 */
public final class WandBinding {

    private static final Map<UUID, String> SELECTED = new ConcurrentHashMap<>();

    private WandBinding() {
    }

    public static void select(Player player, String position) {
        SELECTED.put(player.getUniqueId(), position);
    }

    /** 取走选中项（用完即清）。 */
    public static String take(Player player) {
        return SELECTED.remove(player.getUniqueId());
    }

    public static void clear(Player player) {
        SELECTED.remove(player.getUniqueId());
    }
}
