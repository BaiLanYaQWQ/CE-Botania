package net.bailanya.cebotania.block;

import net.bailanya.cebotania.platform.BotaniaItems;
import net.bailanya.cebotania.platform.Platform;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 火花网络：实体方案。
 *
 * <p>火花是挂在池子 / 祭坛 / 凝聚板上方的 item_display。它只认自己**下方**那个方块。
 * 火花之间按**圆形半径**接力：源火花（下方有魔力）把魔力搬给范围内的汇火花
 * （下方缺魔力的祭坛 / 凝聚板）。只有真的搬运时才放粒子。</p>
 */
public final class SparkNetwork {

    /** 接力半径（圆形，格）。 */
    public static final double LINK_RADIUS = 16.0;
    /** 每次搬运上限。 */
    private static final long TRANSFER = 2000L;

    private static final org.bukkit.NamespacedKey KEY_SPARK =
            new org.bukkit.NamespacedKey("cebotania", "spark");
    private static final org.bukkit.NamespacedKey KEY_HOST =
            new org.bukkit.NamespacedKey("cebotania", "spark_host");
    private static final org.bukkit.NamespacedKey KEY_POS =
            new org.bukkit.NamespacedKey("cebotania", "spark_pos");

    private SparkNetwork() {
    }

    /** 在某坐标上方生成一个火花实体，并记录宿主。 */
    public static ItemDisplay spawn(org.bukkit.World world, int x, int y, int z, String host) {
        try {
            var display = (ItemDisplay) world.spawnEntity(
                    new Location(world, x + 0.5, y + 1.35, z + 0.5),
                    org.bukkit.entity.EntityType.ITEM_DISPLAY);
            var stack = BotaniaItems.create("botania:spark", 1);
            if (stack != null) {
                display.setItemStack(stack);
            }
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setInvulnerable(true);
            display.setPersistent(true);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setViewRange(1.0f);
            display.setDisplayWidth(0.6f);
            display.setDisplayHeight(0.6f);
            var pdc = display.getPersistentDataContainer();
            pdc.set(KEY_SPARK, PersistentDataType.BYTE, (byte) 1);
            pdc.set(KEY_HOST, PersistentDataType.STRING, host);
            pdc.set(KEY_POS, PersistentDataType.STRING, x + "," + y + "," + z);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 找某坐标上方是否已有火花。 */
    public static ItemDisplay findAt(org.bukkit.World world, int x, int y, int z) {
        String target = x + "," + y + "," + z;
        for (var entity : world.getNearbyEntities(
                new Location(world, x + 0.5, y + 1.35, z + 0.5), 1.2, 1.2, 1.2)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            var pdc = display.getPersistentDataContainer();
            if (pdc.has(KEY_SPARK, PersistentDataType.BYTE)
                    && target.equals(pdc.get(KEY_POS, PersistentDataType.STRING))) {
                return display;
            }
        }
        return null;
    }

    /** 拆掉某坐标上方的火花实体。 */
    public static void despawn(org.bukkit.World world, int x, int y, int z) {
        var existing = findAt(world, x, y, z);
        if (existing != null) {
            existing.remove();
        }
    }

    /** 收集世界里所有火花（带宿主坐标）。 */
    public static List<Spark> collect(org.bukkit.World world) {
        List<Spark> out = new ArrayList<>();
        for (var entity : world.getEntitiesByClass(ItemDisplay.class)) {
            var pdc = entity.getPersistentDataContainer();
            if (!pdc.has(KEY_SPARK, PersistentDataType.BYTE)) {
                continue;
            }
            String pos = pdc.get(KEY_POS, PersistentDataType.STRING);
            String host = pdc.get(KEY_HOST, PersistentDataType.STRING);
            if (pos == null) {
                continue;
            }
            String[] parts = pos.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                out.add(new Spark(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        host == null ? "?" : host,
                        entity));
            } catch (NumberFormatException ignored) {
                // 坏数据跳过
            }
        }
        return out;
    }

    /** 火花数据。 */
    public record Spark(int x, int y, int z, String host, ItemDisplay entity) {
        public Location location() {
            return entity.getLocation();
        }
    }

    /** 每 tick 搬运一次：源 → 汇，圆形范围内接力。 */
    public static void tick(org.bukkit.World world) {
        var sparks = collect(world);
        if (sparks.size() < 2) {
            return;
        }
        // 按下方方块归类
        List<Source> sources = new ArrayList<>();
        List<Sink> sinks = new ArrayList<>();
        var ceWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor
                .adapt(world).storageWorld();
        if (ceWorld == null) {
            return;
        }
        for (var spark : sparks) {
            var entity = ceWorld.getBlockEntityAtIfLoaded(
                    new net.momirealms.craftengine.core.world.BlockPos(
                            spark.x(), spark.y(), spark.z()));
            if (entity == null) {
                continue;
            }
            var pool = entity.controller.get(ManaPoolController.class, 0);
            if (pool != null && pool.mana() > 0) {
                sources.add(new Source(spark, pool));
                continue;
            }
            var altar = entity.controller.get(RunicAltarController.class, 0);
            if (altar != null && altar.room() > 0) {
                sinks.add(new Sink(spark, altar));
                continue;
            }
            var plate = entity.controller.get(TerrasteelController.class, 0);
            if (plate != null && plate.room() > 0) {
                sinks.add(new Sink(spark, plate));
            }
        }
        // 圆形范围接力
        for (var sink : sinks) {
            for (var source : sources) {
                if (source.pool.mana() <= 0) {
                    continue;
                }
                double dist = sink.spark.location().distance(source.spark.location());
                if (dist > LINK_RADIUS) {
                    continue;
                }
                long moved = Math.min(TRANSFER,
                        Math.min(source.pool.mana(), sink.target.room()));
                if (moved <= 0) {
                    continue;
                }
                long taken = source.pool.extract(moved);
                if (taken <= 0) {
                    continue;
                }
                sink.target.insert(taken);
                // 只有真的搬运了才放粒子
                drawLink(source.spark.location(), sink.spark.location());
            }
        }
    }

    private static void drawLink(Location from, Location to) {
        try {
            var world = from.getWorld();
            if (world == null) {
                return;
            }
            int steps = 8;
            for (int i = 1; i < steps; i++) {
                double t = i / (double) steps;
                var point = from.clone().add(
                        (to.getX() - from.getX()) * t,
                        (to.getY() - from.getY()) * t,
                        (to.getZ() - from.getZ()) * t);
                world.spawnParticle(org.bukkit.Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
        } catch (Throwable ignored) {
            // 粒子缺失不影响功能
        }
    }

    private record Source(Spark spark, ManaPoolController pool) {
    }

    private record Sink(Spark spark, ManaBlockController target) {
    }
}
