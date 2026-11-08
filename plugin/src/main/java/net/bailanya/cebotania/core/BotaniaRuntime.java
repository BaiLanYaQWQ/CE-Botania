package net.bailanya.cebotania.core;

/**
 * 极简服务定位：方块实体拿不到插件实例（CE 的构造只给 BlockEntity），
 * 于是把调度器放在这里，插件启用时装上、停用时摘掉。
 */
public final class BotaniaRuntime {

    private static TickBus tickBus;
    private static net.bailanya.cebotania.recipe.RecipeBook recipes;

    private BotaniaRuntime() {
    }

    public static void install(TickBus bus) {
        tickBus = bus;
    }

    public static void uninstall() {
        tickBus = null;
    }

    public static TickBus tickBus() {
        return tickBus;
    }

    public static void installRecipes(net.bailanya.cebotania.recipe.RecipeBook book) {
        recipes = book;
    }

    public static net.bailanya.cebotania.recipe.RecipeBook recipes() {
        return recipes;
    }

}
