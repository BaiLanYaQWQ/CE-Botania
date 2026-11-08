package net.bailanya.cebotania.listener;

import net.bailanya.cebotania.block.ManaBlockController;
import net.bailanya.cebotania.core.BotaniaRuntime;
import net.bailanya.cebotania.core.TickBus;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 玩家放下自定义方块后，把它挂进逐刻调度。
 *
 * <p>CraftEngine 在"运行时放置"的方块上不一定会调 onLoad，结果方块实体建起来了
 * 却没人 tick（表现就是花永远 0 魔力、池子永远 0）。这里在放置后下一 tick 主动
 * 找到方块实体，把它身上的控制器补注册一遍。</p>
 */
public final class TickAutoRegister implements Listener {

    private final JavaPlugin plugin;

    public TickAutoRegister(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        // 等 CE 把方块实体建好（它可能在下一个 tick 才完成）
        Bukkit.getScheduler().runTask(this.plugin, () -> attach(block));
    }

    private void attach(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) {
            return;
        }
        TickBus bus = BotaniaRuntime.tickBus();
        if (bus == null) {
            return;
        }
        CEWorld ceWorld = BukkitAdaptor.adapt(block.getWorld()).storageWorld();
        if (ceWorld == null) {
            return;
        }
        CraftEngineBlocks.getCustomBlockState(block);   // 触发 CE 同步状态
        var entity = ceWorld.getBlockEntityAtIfLoaded(
                new net.momirealms.craftengine.core.world.BlockPos(
                        block.getX(), block.getY(), block.getZ()));
        if (entity == null) {
            return;
        }
        // 一个方块实体可能挂多个控制器，挨个问一遍
        for (int index = 0; index < 8; index++) {
            ManaBlockController controller = entity.controller.get(ManaBlockController.class, index);
            if (controller != null) {
                bus.register(controller);
            }
        }
    }
}
