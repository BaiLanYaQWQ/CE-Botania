package net.bailanya.cebotania.debug;

import net.bailanya.cebotania.CeBotaniaPlugin;
import net.bailanya.cebotania.block.ApothecaryController;
import net.bailanya.cebotania.block.FlowerController;
import net.bailanya.cebotania.block.ManaPoolController;
import net.bailanya.cebotania.block.SpreaderController;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 端到端自检：在空地上摆一套最小产线，跑一段时间后把结果写进控制台。
 *
 * <p>它解决的是"改完插件没人验证"的问题：花有没有吃到燃料、发射器有没有吸到、
 * 池子有没有涨，全看日志里的数字。默认关，配置 {@code debug.self-test: true} 打开。</p>
 */
public final class SelfTest {

    private final JavaPlugin plugin;

    public SelfTest(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 找一块高空平地开始搭产线。 */
    public void run() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            this.plugin.getLogger().warning("[自检] 没有可用世界，跳过");
            return;
        }
        int x = 96;
        int y = world.getHighestBlockYAt(x, 96) + 2;
        int z = 96;
        // 清场：上一次自检留下的自定义方块要走 CE 的 remove，
        // 直接 setType(AIR) 不会触发方块实体的 onRemove/onUnload，旧的会一直在调度里跑。
        for (int dx = -1; dx <= 5; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block old = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (CraftEngineBlocks.isCustomBlock(old)) {
                        CraftEngineBlocks.remove(old);
                    }
                    old.setType(Material.AIR, false);
                }
            }
        }
        world.getBlockAt(x + 1, y - 1, z).setType(Material.GRASS_BLOCK, false);
        world.getBlockAt(x + 2, y - 1, z).setType(Material.GRASS_BLOCK, false);
        world.getBlockAt(x + 3, y - 1, z).setType(Material.GRASS_BLOCK, false);

        boolean apothecary = place(world, x, y, z, "botania:apothecary_default");
        boolean flower = place(world, x + 1, y, z, "botania:endoflame");
        boolean spreader = place(world, x + 2, y, z, "botania:mana_spreader");
        boolean pool = place(world, x + 3, y, z, "botania:mana_pool");
        this.plugin.getLogger().info("[自检] 调度池方块实体数="
                + net.bailanya.cebotania.core.BotaniaRuntime.tickBus().size());
        this.plugin.getLogger().info("[自检] 摆件于 (" + x + "," + y + "," + z + ")：花药台="
                + apothecary + " 花=" + flower + " 发射器=" + spreader + " 池子=" + pool);
        if (!(flower && spreader && pool)) {
            this.plugin.getLogger().warning("[自检] 关键方块放置失败，停下来等人工排查");
            return;
        }
        // 给花一块煤：萤火之焰要吃燃料才产魔
        world.dropItem(new Location(world, x + 1.5, y + 0.5, z + 0.5),
                new ItemStack(Material.COAL, 16));

        // 1 秒、5 秒、15 秒各报一次
        report(world, x, y, z, 20L, "1s");
        report(world, x, y, z, 100L, "5s");
        report(world, x, y, z, 300L, "15s");
    }

    private boolean place(World world, int x, int y, int z, String id) {
        boolean ok = CraftEngineBlocks.place(new Location(world, x, y, z), Key.of(id), true);
        // CE 的 place 只写世界，不刷新区块缓存；不摸一下的话方块实体会按旧状态
        // （hasBlockEntity=false）建起来，或者干脆不建 —— 这是 ce-mekanism 踩过的坑。
        CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x, y, z));
        return ok;
    }

    private void report(World world, int x, int y, int z, long delayTicks, String label) {
        Bukkit.getScheduler().runTaskLater(this.plugin,
                () -> snapshot(world, x, y, z, label), delayTicks);
    }

    private void snapshot(World world, int x, int y, int z, String label) {
        CEWorld ceWorld = BukkitAdaptor.adapt(world).storageWorld();
        if (ceWorld == null) {
            return;
        }
        // 触发状态同步，确保方块实体已经建好
        for (int dx = 0; dx <= 3; dx++) {
            CraftEngineBlocks.getCustomBlockState(world.getBlockAt(x + dx, y, z));
        }
        FlowerController flower = controller(world, ceWorld, x + 1, y, z, FlowerController.class);
        SpreaderController spreader = controller(world, ceWorld, x + 2, y, z, SpreaderController.class);
        ManaPoolController pool = controller(world, ceWorld, x + 3, y, z, ManaPoolController.class);
        ApothecaryController apothecary = controller(world, ceWorld, x, y, z, ApothecaryController.class);
        this.plugin.getLogger().info("[自检 " + label + "] 花="
                + (flower == null ? "无实体" : flower.mana() + "(tick " + flower.ticks() + ")")
                + " 发射器=" + (spreader == null ? "无实体" : spreader.mana())
                + " 池子=" + (pool == null ? "无实体" : pool.mana())
                + " 花药台=" + (apothecary == null ? "无实体" : (apothecary.hasWater() ? "有水" : "空")));
    }

    private <T extends net.bailanya.cebotania.block.ManaBlockController> T controller(
            World world, CEWorld ceWorld, int x, int y, int z, Class<T> type) {
        // 走调度池查：CE 的方块实体在"共享视觉状态"下可能被重建，直接按坐标取
        // 有可能拿到一个从没被 tick 过的实例。
        T fromBus = net.bailanya.cebotania.core.BotaniaRuntime.tickBus().find(world, x, y, z, type);
        if (fromBus != null) {
            return fromBus;
        }
        var entity = ceWorld.getBlockEntityAtIfLoaded(
                new net.momirealms.craftengine.core.world.BlockPos(x, y, z));
        return entity == null ? null : entity.controller.get(type, 0);
    }
}
