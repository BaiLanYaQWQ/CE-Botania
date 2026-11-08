package net.bailanya.cebotania.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 永远白天 + 永远晴天。
 *
 * <p><b>默认不启用</b> —— 这只是本地调试时图方便的功能（钉在正午、天气永远晴）。
 * 想用就在 {@code config.yml} 里打开 {@code world.always-day} / {@code world.always-clear}。</p>
 */
public final class DaylightKeeper {

    /** 正午。 */
    private static final long NOON = 6000L;

    private final JavaPlugin plugin;
    private final boolean lockDay;
    private final boolean lockClear;

    public DaylightKeeper(JavaPlugin plugin, boolean lockDay, boolean lockClear) {
        this.plugin = plugin;
        this.lockDay = lockDay;
        this.lockClear = lockClear;
    }

    public void install() {
        apply();
        // 每 5 分钟兜一次底（20 tick/s * 300s）
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::apply, 6000L, 6000L);
    }

    private void apply() {
        for (World world : Bukkit.getWorlds()) {
            try {
                if (this.lockDay) {
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.setTime(NOON);
                }
                if (this.lockClear) {
                    world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    world.setStorm(false);
                    world.setThundering(false);
                    world.setClearWeatherDuration(Integer.MAX_VALUE);
                }
            } catch (Throwable throwable) {
                this.plugin.getLogger().warning("设置白天/晴天失败（"
                        + world.getName() + "）：" + throwable);
            }
        }
    }
}
