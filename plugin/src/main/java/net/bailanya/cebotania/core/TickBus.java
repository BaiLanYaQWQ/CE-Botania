package net.bailanya.cebotania.core;

import net.bailanya.cebotania.block.ManaBlockController;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自己管的刻调度：登记所有"活着"的方块实体，由一个 Bukkit 任务每刻推一遍。
 *
 * <p>为什么不用 CraftEngine 的 {@code createBlockEntityTicker}：实测它的 ticker
 * 只被调用一次就再也没来过（用透明载体 + 实体渲染的方块上尤其明显），而机器、
 * 花这类东西必须长期逐刻跑。这里的做法和 ce-mekanism 一致 —— 控制器自己在
 * onLoad/onUnload 里登记注销，调度器只负责按刻遍历。</p>
 *
 * <p>线程约定：所有 tick 都在主线程跑，注册表用并发集合只是为了在区块异步
 * 卸载时不至于抛 ConcurrentModificationException。</p>
 */
public final class TickBus {

    /** 一次 tick 里跑多少个方块实体之后让出一次（避免单刻卡顿）。 */
    private static final int YIELD_EVERY = 512;

    private final JavaPlugin plugin;
    private final Set<ManaBlockController> controllers = ConcurrentHashMap.newKeySet();
    private int taskId = -1;

    public TickBus(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ManaBlockController controller) {
        this.controllers.add(controller);
    }

    public void unregister(ManaBlockController controller) {
        this.controllers.remove(controller);
    }

    public int size() {
        return this.controllers.size();
    }

    /** 按世界 + 坐标找已登记的控制器（自检和调试用）。 */
    public <T extends ManaBlockController> T find(org.bukkit.World world, int x, int y, int z,
                                                  Class<T> type) {
        if (world == null) {
            return null;
        }
        for (ManaBlockController controller : this.controllers) {
            var entity = controller.blockEntity();
            if (entity == null || entity.world == null) {
                continue;
            }
            org.bukkit.World controllerWorld =
                    net.bailanya.cebotania.platform.Platform.bukkit(entity.world);
            if (controllerWorld == null || !controllerWorld.equals(world)) {
                continue;
            }
            if (entity.pos.x() == x && entity.pos.y() == y && entity.pos.z() == z
                    && type.isInstance(controller)) {
                return type.cast(controller);
            }
        }
        return null;
    }

    public void start() {
        if (this.taskId != -1) {
            return;
        }
        this.taskId = Bukkit.getScheduler()
                .runTaskTimer(this.plugin, this::pump, 1L, 1L)
                .getTaskId();
    }

    public void stop() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
        this.controllers.clear();
    }

    private void pump() {
        int processed = 0;
        for (ManaBlockController controller : this.controllers) {
            try {
                controller.serverTick();
            } catch (Throwable throwable) {
                // 单个方块炸了不能拖垮整条产线：摘掉它并留个日志
                this.plugin.getLogger().warning("方块实体 tick 失败，已从调度里摘除："
                        + throwable);
                this.controllers.remove(controller);
            }
            if (++processed % YIELD_EVERY == 0) {
                return;  // 这一批到此为止，下一批下个服务器刻再来
            }
        }
    }
}
