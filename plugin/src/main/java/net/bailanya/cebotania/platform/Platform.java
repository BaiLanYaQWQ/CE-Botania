package net.bailanya.cebotania.platform;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 平台小工具：把 CE 的抽象世界换成 Bukkit 世界、发消息、格式化魔力。
 */
public final class Platform {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Platform() {
    }

    public static World bukkit(net.momirealms.craftengine.core.world.World ceWorld) {
        if (ceWorld == null) {
            return null;
        }
        Object platform = ceWorld.platformWorld();
        return platform instanceof World w ? w : null;
    }

    public static World bukkit(net.momirealms.craftengine.core.world.CEWorld ceWorld) {
        return ceWorld == null ? null : bukkit(ceWorld.world);
    }

    public static Block blockAt(net.momirealms.craftengine.core.world.CEWorld ceWorld,
                                net.momirealms.craftengine.core.world.BlockPos pos) {
        World world = bukkit(ceWorld);
        return world == null ? null : world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    public static void send(Player player, String miniMessage) {
        if (player != null) {
            player.sendActionBar(mm(miniMessage));
        }
    }

    /** 魔力显示：满池 1,000,000 = 1 个"池"。 */
    public static String mana(long mana) {
        if (mana >= 1000) {
            return String.format("%,d", mana);
        }
        return Long.toString(mana);
    }
}
