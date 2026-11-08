package net.bailanya.cebotania.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 永远白天 + 永远晴天。
 *
 * <p>关掉昼夜与天气循环，把时间钉在正午（6000）、天气钉在晴。
 * 每 5 分钟再校正一次，防止别的插件或命令把时间拨走。</p>
 */
public final class DaylightKeeper {

    /** 正午。 */
    private static final long NOON = 6000L;

    private final JavaPlugin plugin;

    public DaylightKeeper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void install() {
        apply();
        // 每 5 分钟兜一次底（20 tick/s * 300s）
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::apply, 6000L, 6000L);
    }

    private void apply() {
        for (World world : Bukkit.getWorlds()) {
            try {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setTime(NOON);
                world.setStorm(false);
                world.setThundering(false);
                world.setClearWeatherDuration(Integer.MAX_VALUE);
            } catch (Throwable throwable) {
                this.plugin.getLogger().warning("设置白天/晴天失败（"
                        + world.getName() + "）：" + throwable);
            }
        }
    }
}
