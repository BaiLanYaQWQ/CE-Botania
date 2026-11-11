package net.bailanya.cebotania.block;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * 符文祭坛：攒材料 → 注入魔力 → 出符文。
 *
 * <p>上游是把材料丢在祭坛上、然后用发射器往祭坛里灌魔力。这里简化成
 * "手持材料右键投入，手持森林法杖右键点火（从附近魔力池抽魔力）"。</p>
 */
public final class RunicAltarController extends ManaBlockController {

    private static final String ROOT = "cebotania:runic_altar";
    private static final String ITEMS_KEY = "items";

    /** 祭坛自己的魔力缓冲，一次最多吃这么多。 */
    public static final long CAPACITY = 20_000L;

    private final List<String> items = new ArrayList<>();

    /** 祭坛上有没有装火花。 */
    private boolean hasSpark;
    /** 魔力满了之后有没有放入活石（上游的最后一步）。 */
    private boolean hasLivingrock;

    /** 环绕在祭坛上方的物品展示实体（每个投料一个）。 */
    private final List<org.bukkit.entity.ItemDisplay> displays = new ArrayList<>();
    /** 旋转角度。 */
    private double spin;
    /** 实体刷新节流。 */
    private int displayTick;

    public RunicAltarController(BlockEntity blockEntity) {
        super(blockEntity, CAPACITY);
    }

    @Override
    protected String rootKey() {
        return ROOT;
    }

    public List<String> items() {
        return this.items;
    }

    /** 现在这套材料对应的配方（没凑齐就是 null）。 */
    public net.bailanya.cebotania.recipe.RecipeBook.Runic currentRecipe() {
        var book = net.bailanya.cebotania.core.BotaniaRuntime.recipes();
        return book == null ? null : book.matchRunic(this.items);
    }

    /** 发射器该不该往这里灌：有配方、而且还没灌够。 */
    public boolean wantsMana() {
        var recipe = currentRecipe();
        return recipe != null && this.mana < recipe.mana();
    }

    /**
     * 收到魔力：攒够配方需求就自动出符文（原版就是发射器灌满后自己成型）。
     */
    @Override
    public long insert(long amount) {
        long accepted = super.insert(amount);
        if (accepted <= 0) {
            return 0;
        }
        // 上游：魔力注满后不自动成型，要再放入一块活石、然后用森林法杖点
        var recipe = currentRecipe();
        if (recipe != null && this.mana >= recipe.mana() && !this.hasLivingrock) {
            // 满魔力提示（只提示一次，靠调用方节流）
        }
        return accepted;
    }

    public boolean hasLivingrock() {
        return this.hasLivingrock;
    }

    public void installLivingrock() {
        this.hasLivingrock = true;
        markChanged();
    }

    /** 成型：清空材料、弹出产物。 */
    private void craft(net.bailanya.cebotania.recipe.RecipeBook.Runic recipe) {
        var entity = blockEntity();
        var world = world();
        var pos = position();
        if (entity == null || world == null || pos == null) {
            return;
        }
        var block = net.bailanya.cebotania.platform.Platform.blockAt(world, pos);
        if (block == null) {
            return;
        }
        var item = net.bailanya.cebotania.platform.BotaniaItems.create(
                recipe.output(), recipe.count());
        if (item == null) {
            return;
        }
        var location = block.getLocation().add(0.5, 1.0, 0.5);
        var drop = block.getWorld().dropItem(location, item);
        drop.setPickupDelay(10);
        try {
            block.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, location, 30,
                    0.4, 0.4, 0.4, 0.4);
            block.getWorld().playSound(location,
                    org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        } catch (Throwable ignored) {
            // 粒子/音效缺失不影响功能
        }
        clear();   // 连带清掉悬浮的物品实体
    }

    public void addItem(String id) {
        this.items.add(id);
        refreshDisplays();
        markChanged();
    }

    /** 取回最后投入的一件。 */
    public String popItem() {
        if (this.items.isEmpty()) {
            return null;
        }
        String removed = this.items.remove(this.items.size() - 1);
        refreshDisplays();
        markChanged();
        return removed;
    }

    public void clear() {
        this.items.clear();
        this.mana = 0;
        this.hasLivingrock = false;
        refreshDisplays();
        markChanged();
    }

    @Override
    public void onUnload() {
        clearDisplays();
        super.onUnload();
    }

    /** 宿主被拆时，把火花掉出来。 */
    private void dropSpark() {
        if (!this.hasSpark) {
            return;
        }
        this.hasSpark = false;
        try {
            var w = net.bailanya.cebotania.platform.Platform.bukkit(world());
            var pos = position();
            if (w == null || pos == null) {
                return;
            }
            // 先删掉上面那个火花实体
            net.bailanya.cebotania.block.SparkNetwork.despawn(w, pos.x(), pos.y(), pos.z());
            var stack = net.bailanya.cebotania.platform.BotaniaItems.create("botania:spark", 1);
            if (stack != null) {
                w.dropItem(new org.bukkit.Location(w, pos.x() + 0.5, pos.y() + 0.5,
                        pos.z() + 0.5), stack);
            }
        } catch (Throwable ignored) {
            // 掉落失败不影响拆除
        }
    }

    @Override
    public void onRemove() {
        dropSpark();
        clearDisplays();
        super.onRemove();
    }

    /** 重新生成环绕实体：每个投料一个 ItemDisplay，围成一圈。 */
    private void refreshDisplays() {
        clearDisplays();

        var world = net.bailanya.cebotania.platform.Platform.bukkit(world());
        var pos = position();
        if (world == null || pos == null || this.items.isEmpty()) {
            return;
        }
        var center = new org.bukkit.Location(world,
                pos.x() + 0.5, pos.y() + 1.15, pos.z() + 0.5);
        for (String id : this.items) {
            org.bukkit.inventory.ItemStack stack =
                    net.bailanya.cebotania.platform.BotaniaItems.create(id, 1);
            if (stack == null) {
                continue;
            }
            try {
                var raw = world.spawnEntity(center, org.bukkit.entity.EntityType.ITEM_DISPLAY);
                if (raw instanceof org.bukkit.entity.ItemDisplay display) {
                    display.setItemStack(stack);
                    display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    display.setItemDisplayTransform(
                            org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GROUND);
                    display.setInvulnerable(true);
                    display.setPersistent(false);
                    display.setShadowRadius(0f);
                    display.setShadowStrength(0f);
                    display.setViewRange(0.35f);
                    this.displays.add(display);
                }
            } catch (Throwable throwable) {
                java.util.logging.Logger.getLogger("CeBotania").warning(
                        "祭坛物品实体创建失败：" + throwable);
            }
        }
        layout(0);

    }

    /** 按当前角度把实体摆成一圈（圆心固定用祭坛方块的位置）。 */
    private void layout(double angle) {
        int count = this.displays.size();
        if (count == 0) {
            return;
        }
        var world = net.bailanya.cebotania.platform.Platform.bukkit(world());
        var pos = position();
        if (world == null || pos == null) {
            return;
        }
        var center = new org.bukkit.Location(world,
                pos.x() + 0.5, pos.y() + 1.15, pos.z() + 0.5);
        double radius = 0.62;
        for (int index = 0; index < count; index++) {
            var display = this.displays.get(index);
            if (display == null || !display.isValid()) {
                continue;
            }
            double theta = angle + index * (2 * Math.PI / count);
            var target = center.clone().add(
                    Math.cos(theta) * radius, 0.0, Math.sin(theta) * radius);
            target.setYaw((float) Math.toDegrees(-theta));
            display.teleport(target);
        }
    }

    private void clearDisplays() {
        for (var display : this.displays) {
            try {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            } catch (Throwable ignored) {
                // 忽略
            }
        }
        this.displays.clear();
    }

    public boolean hasSpark() {
        return this.hasSpark;
    }

    public void removeSpark() {
        this.hasSpark = false;
        markChanged();
    }

    public void installSpark() {
        this.hasSpark = true;
        markChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("hasSpark", this.hasSpark);
        tag.putBoolean("hasLivingrock", this.hasLivingrock);
        tag.putString(ITEMS_KEY, String.join(",", this.items));
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        this.hasSpark = tag.getBoolean("hasSpark", false);
        this.hasLivingrock = tag.getBoolean("hasLivingrock", false);
        this.items.clear();
        String joined = tag.getString(ITEMS_KEY, "");
        if (!joined.isEmpty()) {
            for (String part : joined.split(",")) {
                if (!part.isBlank()) {
                    this.items.add(part.trim());
                }
            }
        }
    }
}
