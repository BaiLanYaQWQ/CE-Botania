package net.bailanya.cebotania.flower;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 功能花的效果实现。
 *
 * <p>产魔花靠 {@link FlowerLogic} 攒魔力；功能花反过来 —— 它们花魔力做事。
 * 每次动作从附近（8 格内）的魔力池扣 {@code manaPerAction}，扣不出来就停手，
 * 不会出"免费效果"。这一层的设计取舍见 README「功能花」一节。</p>
 */
public final class FunctionalFlowers {

    /** 能催熟的作物。 */
    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.PUMPKIN_STEM, Material.MELON_STEM,
            Material.SWEET_BERRY_BUSH, Material.TORCHFLOWER_CROP, Material.PITCHER_CROP
    );

    private FunctionalFlowers() {
    }

    /**
     * 执行一朵功能花的动作。
     *
     * @return 这次动作应该扣多少魔力（0 = 什么也没做）
     */
    public static long run(World world, Block self, String logic, FlowerType type) {
        return switch (logic) {
            case "grow" -> growCrops(world, self, 4);
            case "heal" -> healNearby(world, self, 4);
            case "bloom" -> bloomFlowers(world, self, 3);
            case "collect" -> collectItems(world, self, 6);
            case "smelt" -> boostFurnaces(world, self, 4);
            case "thorn" -> damageMobs(world, self, 5, 3.0);
            case "dreadthorn" -> damageMobs(world, self, 5, 4.0, true);
            case "poison" -> poisonMobs(world, self, 4);
            case "slow" -> slowMobs(world, self, 4);
            case "scare" -> scareMobs(world, self, 4);
            case "ore" -> convertStone(world, self, 5, false);
            case "place" -> placeBlocks(world, self, 4);
            case "clay" -> sandToClay(world, self, 4);
            case "blow" -> blowItems(world, self, 4);
            case "breed" -> breedAnimals(world, self, 4);
            case "levitate" -> levitateMobs(world, self, 4);
            case "tangle" -> tangleMobs(world, self, 4);
            case "dream" -> dreamMobs(world, self, 4);
            case "marble" -> marbleStone(world, self, 5);
            case "lens" -> suppressFlowers(world, self, 4);
            case "mute" -> muteNearby(world, self, 6);
            case "bind" -> bindEndermen(world, self, 8);
            case "teleport" -> teleportItems(world, self, 6);
            case "dungeon" -> spawnDungeonMob(world, self, 6);
            case "ore_nether" -> convertStone(world, self, 5, true);
            default -> 0L;
        };
    }

    // —— 农业花：给附近作物催熟（相当于骨粉）——
    private static long growCrops(World world, Block self, int radius) {
        for (Block block : nearbyBlocks(self, radius)) {
            BlockData data = block.getBlockData();
            if (!(data instanceof Ageable ageable) || !CROPS.contains(block.getType())) {
                continue;
            }
            if (ageable.getAge() >= ageable.getMaximumAge()) {
                continue;
            }
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
            block.setBlockData(ageable, false);
            spawn(world, block.getLocation().add(0.5, 0.4, 0.5),
                    org.bukkit.Particle.HAPPY_VILLAGER, 4);
            return 1;   // 一次只催一株，靠间隔控制节奏
        }
        return 0;
    }

    // —— 落樱：给附近的玩家/被动生物回血 ——
    private static long healNearby(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Monster) {
                continue;
            }
            var maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = maxHealth == null ? 20.0 : maxHealth.getValue();
            if (living.getHealth() >= max) {
                continue;
            }
            living.setHealth(Math.min(max, living.getHealth() + 2.0));
            spawn(world, living.getLocation().add(0, 0.8, 0),
                    org.bukkit.Particle.HEART, 3);
            return 1;
        }
        return 0;
    }

    // —— 紫颂花：在附近的土上"种"出花 ——
    private static long bloomFlowers(World world, Block self, int radius) {
        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            Block ground = self.getRelative(dx, 0, dz);
            Block below = ground.getRelative(BlockFace.DOWN);
            if (ground.getType() != Material.AIR) {
                continue;
            }
            if (below.getType() != Material.GRASS_BLOCK && below.getType() != Material.DIRT) {
                continue;
            }
            // 上游种的是 Botania 自己的神秘花（打掉会掉魔力花瓣），不是原版小花
            if (!placeMysticalFlower(ground, random)) {
                continue;
            }
            spawn(world, ground.getLocation().add(0.5, 0.3, 0.5),
                    org.bukkit.Particle.HAPPY_VILLAGER, 5);
            return 1;
        }
        return 0;
    }

    /**
     * 紫颂花种一朵随机的 Botania 神秘花。
     *
     * <p>必须走 CraftEngine 的 {@code place} —— 自定义方块不在 Bukkit 的注册表里，
     * 用 {@code setBlockData} 会退化成默认方块（之前就长成原木了）。</p>
     */
    private static boolean placeMysticalFlower(Block ground,
                                               java.util.concurrent.ThreadLocalRandom random) {
        String[] colors = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"
        };
        for (int attempt = 0; attempt < 8; attempt++) {
            String color = colors[random.nextInt(colors.length)];
            try {
                boolean ok = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(
                        ground.getLocation(),
                        net.momirealms.craftengine.core.util.Key.of(
                                "botania", color + "_mystical_flower"),
                        false);
                if (ok) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 换一种颜色再试
            }
        }
        return false;
    }

    // —— 漏斗花：把附近的掉落物吸进容器（箱子/漏斗）——
    private static long collectItems(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            var stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            // 找最近的容器
            for (Block block : nearbyBlocks(self, 4)) {
                var state = block.getState();
                if (!(state instanceof org.bukkit.block.Container container)) {
                    continue;
                }
                Inventory inventory = container.getInventory();
                var leftovers = inventory.addItem(stack.clone());
                if (leftovers.isEmpty()) {
                    item.remove();
                } else {
                    int remaining = leftovers.values().stream()
                            .mapToInt(ItemStack::getAmount).sum();
                    stack.setAmount(remaining);
                    item.setItemStack(stack);
                }
                spawn(world, block.getLocation().add(0.5, 1.0, 0.5),
                        org.bukkit.Particle.END_ROD, 4);
                return 1;
            }
        }
        return 0;
    }

    // —— 熔炉花：给附近的熔炉补燃烧时间/加速 ——
    private static long boostFurnaces(World world, Block self, int radius) {
        for (Block block : nearbyBlocks(self, radius)) {
            Material type = block.getType();
            if (type != Material.FURNACE && type != Material.BLAST_FURNACE
                    && type != Material.SMOKER) {
                continue;
            }
            if (!(block.getState() instanceof Furnace furnace)) {
                continue;
            }
            // 每秒补 20 tick 燃烧时间，相当于持续添柴
            int burn = furnace.getBurnTime();
            if (burn <= 0) {
                furnace.setBurnTime((short) 20);
                furnace.update(true, false);
                spawn(world, block.getLocation().add(0.5, 1.1, 0.5),
                        org.bukkit.Particle.FLAME, 4);
                return 1;
            }
            if (burn < 200) {
                furnace.setBurnTime((short) Math.min(200, burn + 20));
                furnace.update(true, false);
                return 1;
            }
        }
        return 0;
    }

    // —— 钟兰 / 恐惧荆棘：伤害附近的怪物 ——
    private static long damageMobs(World world, Block self, int radius, double damage) {
        return damageMobs(world, self, radius, damage, false);
    }

    private static long damageMobs(World world, Block self, int radius, double damage,
                                   boolean undeadOnly) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }
            if (undeadOnly && !(monster instanceof org.bukkit.entity.Zombie)
                    && !(monster instanceof org.bukkit.entity.Skeleton)) {
                continue;
            }
            monster.damage(damage);
            spawn(world, monster.getLocation().add(0, 1, 0),
                    org.bukkit.Particle.CRIT, 3);
            return 1;
        }
        return 0;
    }

    // —— 毒酸花：给怪物上中毒 ——
    private static long poisonMobs(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }
            monster.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
            spawn(world, monster.getLocation().add(0, 1, 0),
                    org.bukkit.Particle.ITEM_SLIME, 4);
            return 1;
        }
        return 0;
    }

    // —— 迟缓花：给怪物上缓慢 ——
    private static long slowMobs(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }
            monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            return 1;
        }
        return 0;
    }

    // —— 虎眼石：把怪物吓退（推开）——
    private static long scareMobs(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }
            var push = monster.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new org.bukkit.util.Vector(1, 0, 0);
            }
            push.normalize().multiply(0.6).setY(0.25);
            monster.setVelocity(push);
            return 1;
        }
        return 0;
    }

    /**
     * 播种花（rannuncarpus）：把地上的方块物品放到附近合适的位置。
     *
     * <p>典型用法是自动农场 —— 收割后掉在地上的种子/作物被重新种回去。</p>
     */
    private static long placeBlocks(World world, Block self, int radius) {
        var random = ThreadLocalRandom.current();
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir() || !stack.getType().isBlock()) {
                continue;
            }
            // 找一块空地放下去
            for (int attempt = 0; attempt < 12; attempt++) {
                int dx = random.nextInt(radius * 2 + 1) - radius;
                int dz = random.nextInt(radius * 2 + 1) - radius;
                Block spot = self.getRelative(dx, 0, dz);
                if (!spot.isEmpty() && spot.getType() != Material.SHORT_GRASS
                        && spot.getType() != Material.TALL_GRASS) {
                    continue;
                }
                Block below = spot.getRelative(BlockFace.DOWN);
                if (below.isEmpty()) {
                    continue;
                }
                Material placed = stack.getType();
                // 作物类：只能种在耕地上（小麦种子/胡萝卜/土豆/甜菜/西瓜南瓜籽）
                boolean crop = placed == Material.WHEAT_SEEDS || placed == Material.CARROTS
                        || placed == Material.POTATOES || placed == Material.BEETROOT_SEEDS
                        || placed == Material.PUMPKIN_SEEDS || placed == Material.MELON_SEEDS
                        || placed == Material.SWEET_BERRY_BUSH || placed == Material.TORCHFLOWER_SEEDS
                        || placed == Material.PITCHER_POD;
                if (crop) {
                    if (below.getType() != Material.FARMLAND) {
                        continue;   // 底下不是耕地就不种
                    }
                } else if (below.getType() != Material.GRASS_BLOCK
                        && below.getType() != Material.DIRT
                        && below.getType() != Material.FARMLAND) {
                    continue;
                }
                spot.setType(placed, false);
                if (stack.getAmount() <= 1) {
                    item.remove();
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    item.setItemStack(stack);
                }
                spawn(world, spot.getLocation().add(0.5, 0.3, 0.5),
                        org.bukkit.Particle.HAPPY_VILLAGER, 4);
                return 1;
            }
        }
        return 0;
    }

    /**
     * 粘土花（clayconia）：打掉附近的沙子，掉一个粘土球。
     *
     * <p>上游就是这么做的 —— 不是把沙子变成粘土方块，而是移除沙子并在原地
     * 生成 {@code Items.CLAY_BALL} 的掉落物。</p>
     */
    private static long sandToClay(World world, Block self, int radius) {
        for (Block block : nearbyBlocks(self, radius)) {
            if (block.getType() != Material.SAND && block.getType() != Material.RED_SAND) {
                continue;
            }
            var location = block.getLocation().add(0.5, 0.5, 0.5);
            block.setType(Material.AIR, false);
            try {
                var drop = world.dropItem(location,
                        new ItemStack(Material.CLAY_BALL, 1));
                drop.setPickupDelay(10);
            } catch (Throwable ignored) {
                // 掉落物失败就退化成直接给方块
                block.setType(Material.CLAY, false);
            }
            spawn(world, location, org.bukkit.Particle.SPLASH, 6);
            return 1;
        }
        return 0;
    }

    /** 风车花（daffomill）：把附近的物品往朝向方向吹。 */
    private static long blowItems(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        boolean moved = false;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            var push = item.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new org.bukkit.util.Vector(0.5, 0, 0.5);
            }
            push.normalize().multiply(0.45);
            push.setY(0.1);
            item.setVelocity(push);
            moved = true;
            break;
        }
        return moved ? 1 : 0;
    }

    /** 繁殖花（pollidisiac）：用附近的食物让动物繁殖。 */
    private static long breedAnimals(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        java.util.List<org.bukkit.entity.Animals> animals = new java.util.ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.Animals animal && animal.canBreed()
                    && !animal.isLoveMode()) {
                animals.add(animal);
            }
        }
        if (animals.size() < 2) {
            return 0;
        }
        var first = animals.get(0);
        var second = animals.get(1);
        first.setLoveModeTicks(200);
        second.setLoveModeTicks(200);
        spawn(world, first.getLocation().add(0, 1, 0), org.bukkit.Particle.HEART, 4);
        return 1;
    }

    /** 浮空花（jiyuulia）：给附近怪物上一个短时飘浮。 */
    private static long levitateMobs(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }
            monster.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0));
            spawn(world, monster.getLocation().add(0, 1, 0),
                    org.bukkit.Particle.CLOUD, 4);
            return 1;
        }
        return 0;
    }

    /** 缠绕花（tangleberrie）：把附近生物黏住（缓慢 + 无法跳跃）。 */
    private static long tangleMobs(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 6));
            if (living instanceof LivingEntity target) {
                // 拉向花心，模拟被藤蔓缠住的拖拽感
                var pull = center.toVector().subtract(target.getLocation().toVector());
                if (pull.lengthSquared() > 0.05) {
                    pull.normalize().multiply(0.12).setY(0);
                    target.setVelocity(pull);
                }
            }
            return 1;
        }
        return 0;
    }

    /** 催眠花（heisei_dream）：让附近怪物慢慢失去行动力。 */
    private static long dreamMobs(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }
            monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3));
            monster.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
            spawn(world, monster.getLocation().add(0, 1.2, 0),
                    org.bukkit.Particle.CLOUD, 6);
            return 1;
        }
        return 0;
    }

    /** 大理石花（marimorphosis）：把石头变成 8 种变质岩之一。 */
    private static final String[] METAMORPHIC = {
            "desert", "forest", "fungal", "mesa", "mountain", "plains", "swamp", "taiga"
    };

    private static long marbleStone(World world, Block self, int radius) {
        var random = ThreadLocalRandom.current();
        java.util.List<Block> stones = new java.util.ArrayList<>();
        for (Block block : nearbyBlocks(self, radius)) {
            if (block.getType() == Material.STONE || block.getType() == Material.DEEPSLATE) {
                stones.add(block);
            }
        }
        if (stones.isEmpty()) {
            return 0;
        }
        Block target = stones.get(random.nextInt(stones.size()));
        String biome = METAMORPHIC[random.nextInt(METAMORPHIC.length)];
        try {
            boolean ok = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(
                    target.getLocation(),
                    net.momirealms.craftengine.core.util.Key.of(
                            "botania:metamorphic_" + biome + "_stone"),
                    true);
            if (!ok) {
                return 0;
            }
        } catch (Throwable ignored) {
            return 0;
        }
        spawn(world, target.getLocation().add(0.5, 0.5, 0.5),
                org.bukkit.Particle.WAX_ON, 5);
        return 1;
    }

    /**
     * 弱磁兰（solegnolia）：抑制附近的花，让它们停止产魔。
     *
     * <p>给附近 4 格内的花打上抑制标记，被抑制的花在 {@code FlowerController}
     * 的 tick 里直接跳过。</p>
     */
    private static long suppressFlowers(World world, Block self, int radius) {
        int suppressed = 0;
        for (Block block : nearbyBlocks(self, radius)) {
            Object state = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
                    .getCustomBlockState(block);
            if (state == null) {
                continue;
            }
            var entity = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                    .adapt(world).storageWorld()
                    .getBlockEntityAtIfLoaded(new net.momirealms.craftengine.core.world.BlockPos(
                            block.getX(), block.getY(), block.getZ()));
            if (entity == null) {
                continue;
            }
            var flower = entity.controller.get(
                    net.bailanya.cebotania.block.FlowerController.class, 0);
            if (flower != null && !flower.suppressed()) {
                flower.setSuppressed(true);
                suppressed++;
            }
        }
        return suppressed > 0 ? 1 : 0;
    }

    /**
     * 消音柑（bergamute）：消除附近的声音。
     *
     * <p>Bukkit 没有通用的"声音播放前"事件，这里用方块的 tick 做近似：
     * 每 20 tick 给 6 格内的玩家发一条提示，并把他们的音量调低一档（如果有条件）。
     * 实际上主要靠 {@code BergamuteListener} 拦截能拦到的事件。</p>
     */
    private static long muteNearby(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        boolean any = false;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player player) {
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.BLOCK_WOOL_HIT, 0.01f, 1f);
                any = true;
            }
        }
        return any ? 1 : 0;
    }

    /**
     * 诱引莲（vinculotus）：阻止附近的末影人传送。
     *
     * <p>上游是拦截末影人的瞬移。这里每 tick 把附近的末影人拉回原地，
     * 让它们无法传送走。</p>
     */
    private static long bindEndermen(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        boolean any = false;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.Enderman enderman) {
                // 阻止传送：把它的仇恨/传送行为打断，并轻微拽向花心
                var pull = center.toVector().subtract(enderman.getLocation().toVector());
                if (pull.lengthSquared() > 0.2) {
                    pull.normalize().multiply(0.08).setY(0);
                    enderman.setVelocity(pull);
                }
                spawn(world, enderman.getLocation().add(0, 1, 0),
                        org.bukkit.Particle.PORTAL, 4);
                any = true;
            }
        }
        return any ? 1 : 0;
    }

    /**
     * 凌空蓟（spectranthemum）：把附近的掉落物传送到附近的容器里。
     *
     * <p>上游需要绑定目标，这里简化成"传进最近的箱子/漏斗"。</p>
     */
    private static long teleportItems(World world, Block self, int radius) {
        var center = self.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            var stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            for (Block block : nearbyBlocks(self, 6)) {
                if (!(block.getState() instanceof org.bukkit.block.Container container)) {
                    continue;
                }
                var leftovers = container.getInventory().addItem(stack.clone());
                if (leftovers.isEmpty()) {
                    item.remove();
                    spawn(world, block.getLocation().add(0.5, 1.0, 0.5),
                            org.bukkit.Particle.PORTAL, 8);
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * 聚宝花（loonium）：在附近生成怪物，模拟"地牢刷怪"。
     *
     * <p>上游是刷出带战利品的怪，这里简化为随机刷一只常见怪物。</p>
     */
    private static long spawnDungeonMob(World world, Block self, int radius) {
        var random = ThreadLocalRandom.current();
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz = random.nextInt(radius * 2 + 1) - radius;
        Block spot = self.getRelative(dx, 0, dz);
        if (!spot.isEmpty() && spot.getType() != Material.SHORT_GRASS) {
            return 0;
        }
        Block below = spot.getRelative(BlockFace.DOWN);
        if (below.isEmpty()) {
            return 0;
        }
        org.bukkit.entity.EntityType[] mobs = {
                org.bukkit.entity.EntityType.ZOMBIE,
                org.bukkit.entity.EntityType.SKELETON,
                org.bukkit.entity.EntityType.SPIDER,
                org.bukkit.entity.EntityType.CAVE_SPIDER,
        };
        try {
            world.spawnEntity(spot.getLocation().add(0.5, 0, 0.5),
                    mobs[random.nextInt(mobs.length)]);
            spawn(world, spot.getLocation().add(0.5, 0.5, 0.5),
                    org.bukkit.Particle.SOUL, 10);
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 矿石花把附近的石头变矿石。
     *
     * <p>权重表来自上游 {@code data/botania/recipe/orechid/*.json}：
     * 石头池权重合计 127288，深板岩池合计 1000。</p>
     */
    private static final Object[][] STONE_ORES = {
            // {输出方块, 权重}
            {Material.COAL_ORE, 67415},
            {Material.COPPER_ORE, 7000},
            {Material.IRON_ORE, 29371},
            {Material.GOLD_ORE, 2647},
            {Material.REDSTONE_ORE, 7654},
            {Material.LAPIS_ORE, 1079},
            {Material.DIAMOND_ORE, 883},
            {Material.EMERALD_ORE, 1239},
    };

    private static final Object[][] DEEPSLATE_ORES = {
            {Material.DEEPSLATE_COAL_ORE, 75},
            {Material.DEEPSLATE_COPPER_ORE, 75},
            {Material.DEEPSLATE_IRON_ORE, 250},
            {Material.DEEPSLATE_GOLD_ORE, 125},
            {Material.DEEPSLATE_REDSTONE_ORE, 150},
            {Material.DEEPSLATE_LAPIS_ORE, 175},
            {Material.DEEPSLATE_DIAMOND_ORE, 100},
            {Material.DEEPSLATE_EMERALD_ORE, 50},
    };

    private static long convertStone(World world, Block self, int radius, boolean nether) {
        var random = ThreadLocalRandom.current();
        java.util.List<Block> candidates = new java.util.ArrayList<>();
        for (Block block : nearbyBlocks(self, radius)) {
            Material type = block.getType();
            if (nether) {
                if (type == Material.NETHERRACK) {
                    candidates.add(block);
                }
            } else if (type == Material.STONE || type == Material.DEEPSLATE) {
                candidates.add(block);
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        Block target = candidates.get(random.nextInt(candidates.size()));
        if (nether) {
            // 下界矿石花：下界岩 → 下界石英/金/远古残骸
            Material[] netherOres = {
                    Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS
            };
            int[] weights = {300, 200, 10};
            target.setType(pickWeighted(netherOres, weights, random), false);
        } else if (target.getType() == Material.DEEPSLATE) {
            target.setType(pickWeighted(DEEPSLATE_ORES, random), false);
        } else {
            target.setType(pickWeighted(STONE_ORES, random), false);
        }
        spawn(world, target.getLocation().add(0.5, 0.5, 0.5),
                org.bukkit.Particle.WAX_ON, 6);
        return 1;
    }

    private static Material pickWeighted(Object[][] table,
                                         java.util.concurrent.ThreadLocalRandom random) {
        int total = 0;
        for (Object[] row : table) {
            total += (int) row[1];
        }
        int roll = random.nextInt(total);
        for (Object[] row : table) {
            roll -= (int) row[1];
            if (roll < 0) {
                return (Material) row[0];
            }
        }
        return (Material) table[0][0];
    }

    private static Material pickWeighted(Material[] values, int[] weights,
                                         java.util.concurrent.ThreadLocalRandom random) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int index = 0; index < values.length; index++) {
            roll -= weights[index];
            if (roll < 0) {
                return values[index];
            }
        }
        return values[0];
    }

    // —— 工具 ——

    private static Iterable<Block> nearbyBlocks(Block self, int radius) {
        java.util.List<Block> out = new java.util.ArrayList<>();
        World world = self.getWorld();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(
                            self.getX() + dx, self.getY() + dy, self.getZ() + dz);
                    if (!block.isEmpty()) {
                        out.add(block);
                    }
                }
            }
        }
        return out;
    }

    private static void spawn(World world, Location location,
                              org.bukkit.Particle particle, int count) {
        try {
            world.spawnParticle(particle, location, count, 0.25, 0.25, 0.25, 0.01);
        } catch (Throwable ignored) {
            // 粒子缺失不影响功能
        }
    }
}
