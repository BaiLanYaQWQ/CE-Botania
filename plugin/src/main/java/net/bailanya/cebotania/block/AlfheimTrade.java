package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.recipe.RecipeBook;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 开启的精灵门每 5 tick 检查一次自身的 3×3 通道。 */
public final class AlfheimTrade {

    private final RecipeBook recipes;
    private final Set<UUID> tradedItems = new HashSet<>();
    private int scanTick;

    public AlfheimTrade(RecipeBook recipes) {
        this.recipes = recipes;
    }

    void tick(World world, BlockPos core, boolean alongX, int currentTick) {
        if (this.scanTick != currentTick) {
            this.scanTick = currentTick;
            this.tradedItems.clear();
        }

        var center = new Location(world, core.x() + 0.5, core.y() + 2.5, core.z() + 0.5);
        double widthX = alongX ? 1.5 : 0.5;
        double widthZ = alongX ? 0.5 : 1.5;
        for (Item input : world.getNearbyEntitiesByType(Item.class, center, widthX, 1.5, widthZ)) {
            // Bukkit 按碰撞箱查询。用实体坐标精确判定是否进入门内。
            var location = input.getLocation();
            if (!inside(core, alongX, location) || !input.isValid()
                    || this.tradedItems.contains(input.getUniqueId())) {
                continue;
            }
            trade(world, core, input);
        }

        center.setY(core.y() + 2.0);
        double spreadX = alongX ? 1.2 : 0.1;
        double spreadZ = alongX ? 0.1 : 1.2;
        world.spawnParticle(Particle.PORTAL, center, 12, spreadX, 1.2, spreadZ, 0.05);
        world.spawnParticle(Particle.WARPED_SPORE, center, 6, spreadX, 1.2, spreadZ, 0.01);
    }

    private static boolean inside(BlockPos core, boolean alongX, Location location) {
        int dx = location.getBlockX() - core.x();
        int dy = location.getBlockY() - core.y();
        int dz = location.getBlockZ() - core.z();
        return dy >= 1 && dy <= 3
                && (alongX ? Math.abs(dx) <= 1 && dz == 0 : dx == 0 && Math.abs(dz) <= 1);
    }

    private void trade(World world, BlockPos core, Item input) {
        var stack = input.getItemStack().clone();
        if (stack.isEmpty()) {
            return;
        }
        String id = BotaniaItems.idOf(stack);
        if (id.isEmpty()) {
            return;
        }
        var recipe = this.recipes.matchElven(List.of(id));
        if (recipe == null) {
            return;
        }
        ItemStack result = BotaniaItems.create(recipe.output(), recipe.count());
        if (result == null || result.isEmpty()) {
            return;
        }

        this.tradedItems.add(input.getUniqueId());
        var spawnAt = new Location(world, core.x() + 0.5, core.y() + 1.5, core.z() + 0.5);
        var output = world.dropItem(spawnAt, result, item -> {
            this.tradedItems.add(item.getUniqueId());
            item.setPickupDelay(10);
        });
        if (!output.isValid()) {
            return;
        }
        // 掉落事件可取消生成或改变原掉落物，只有双方都存在时才扣除输入。
        if (!input.isValid() || !input.getItemStack().equals(stack)) {
            output.remove();
            return;
        }
        if (stack.getAmount() == 1) {
            input.remove();
        } else {
            stack.setAmount(stack.getAmount() - 1);
            input.setItemStack(stack);
        }
        world.spawnParticle(Particle.PORTAL, spawnAt, 40, 0.6, 0.6, 0.6, 0.3);
    }
}
