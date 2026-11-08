package net.bailanya.cebotania;

import net.bailanya.cebotania.block.ApothecaryBehavior;
import net.bailanya.cebotania.block.FlowerBehavior;
import net.bailanya.cebotania.block.GroundPlantBehavior;
import net.bailanya.cebotania.block.PureDaisyBehavior;
import net.bailanya.cebotania.block.RunicAltarBehavior;
import net.bailanya.cebotania.block.ManaPoolBehavior;
import net.bailanya.cebotania.block.SpreaderBehavior;
import net.bailanya.cebotania.command.BotaniaCommand;
import net.bailanya.cebotania.recipe.RecipeBook;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 植物魔法（Botania）在 Paper + CraftEngine 上的复刻。
 *
 * <p>分工：资源包（{@code pack/}）负责物品、方块、模型与语言；CraftEngine 负责
 * 注册它们并把配方写进原版工作台；本插件只承担玩法逻辑 —— 魔力账本、
 * 花的行为、灌注与仪式。</p>
 */
public final class CeBotaniaPlugin extends JavaPlugin {

    private RecipeBook recipes;
    private net.bailanya.cebotania.listener.ManaRepair manaRepair;
    private net.bailanya.cebotania.core.TickBus tickBus;

    public RecipeBook recipes() {
        return this.recipes;
    }

    @Override
    public void onEnable() {
        // 0) 语言表：物品名在服务端也要能查（客户端翻译拿不到）
        net.bailanya.cebotania.i18n.I18n.install(this);

        // 1) 配方与标签：一次读完，之后再怎么重载 CE 都还在内存里
        this.recipes = RecipeBook.load(getClassLoader());
        net.bailanya.cebotania.core.BotaniaRuntime.installRecipes(this.recipes);
        getLogger().info(this.recipes.summary());

        // 2) 方块行为必须在 CE 载包之前注册（config.yml 的 delay-configuration-load 默认开启）
        BlockBehaviors.register(GroundPlantBehavior.KEY, GroundPlantBehavior.factory());
        BlockBehaviors.register(FlowerBehavior.KEY, FlowerBehavior.flowerFactory());
        BlockBehaviors.register(PureDaisyBehavior.KEY, PureDaisyBehavior.pureDaisyFactory());
        BlockBehaviors.register(RunicAltarBehavior.KEY, RunicAltarBehavior.runicFactory(this));
        BlockBehaviors.register(ManaPoolBehavior.KEY, ManaPoolBehavior.factory(this));
        BlockBehaviors.register(SpreaderBehavior.KEY, SpreaderBehavior.factory());
        BlockBehaviors.register(ApothecaryBehavior.KEY, ApothecaryBehavior.factory(this));

        // 3) 逐刻调度：方块实体自己登记，这里只负责按刻推
        this.tickBus = new net.bailanya.cebotania.core.TickBus(this);
        net.bailanya.cebotania.core.BotaniaRuntime.install(this.tickBus);
        this.tickBus.start();

        // 4) 自检（默认开；不想看日志就关掉 debug.self-test）
        saveDefaultConfig();
        if (getConfig().getBoolean("debug.self-test", true)) {
            Bukkit.getScheduler().runTaskLater(this,
                    () -> new net.bailanya.cebotania.debug.SelfTest(this).run(), 100L);
        }

        // 5) 事件：打花掉花瓣
        Bukkit.getPluginManager().registerEvents(
                new net.bailanya.cebotania.listener.PetalDropListener(), this);
        Bukkit.getPluginManager().registerEvents(
                new net.bailanya.cebotania.listener.GroundPlantSupport(this), this);
        Bukkit.getPluginManager().registerEvents(
                new net.bailanya.cebotania.listener.TickAutoRegister(this), this);
        this.manaRepair = new net.bailanya.cebotania.listener.ManaRepair(this);
        Bukkit.getPluginManager().registerEvents(this.manaRepair, this);

        // 6) 命令
        PluginCommand command = getCommand("botania");
        if (command != null) {
            BotaniaCommand executor = new BotaniaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("植物魔法已启用（CraftEngine 侧注册 " + CraftEngineItems.loadedItems().size() + " 个物品）");
    }

    @Override
    public void onDisable() {
        if (this.tickBus != null) {
            this.tickBus.stop();
        }
        net.bailanya.cebotania.core.BotaniaRuntime.uninstall();
        getLogger().info("植物魔法已停用");
    }

    public net.bailanya.cebotania.listener.ManaRepair manaRepair() {
        return this.manaRepair;
    }

    public net.bailanya.cebotania.core.TickBus tickBus() {
        return this.tickBus;
    }
}
