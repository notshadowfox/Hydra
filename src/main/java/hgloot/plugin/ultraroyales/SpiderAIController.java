package hgloot.plugin.ultraroyales;

import hgloot.plugin.ultraroyales.listeners.SpiderStaffListener;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getServer;

public class SpiderAIController implements Listener {

    private static final Plugin plugin = UltraRoyales.getPlugin();
    private static final int MAX_SPIDERS_PER_PLAYER = 10;
    private static final int HUNGER_GAMES_MAX_SPIDERS = 5;
    private static final int SPIDER_DURATION_TICKS = 240;

    public static final NamespacedKey SPIDER_TAG = new NamespacedKey(plugin, "minion_spider");

    private static final Map<UUID, Set<UUID>> playerSpiders = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> spiderTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitRunnable> spiderTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> spawnSessions = new ConcurrentHashMap<>();

    public static Map<UUID, Set<UUID>> getPlayerSpiders() {
        return playerSpiders;
    }

    public static void spawnPlayerSpiders(Player player) {
        spawnPlayerSpiders(player, null);
    }

    public static void spawnPlayerSpiders(Player player, LivingEntity initialTarget) {
        removePlayerSpiders(player);

        UUID playerUUID = player.getUniqueId();
        Location spawnLocation = player.getLocation();
        Set<UUID> newSpiders = ConcurrentHashMap.newKeySet();
        int session = spawnSessions.getOrDefault(playerUUID, 0) + 1;
        spawnSessions.put(playerUUID, session);
        int spiderCount = LegendaryModeRules.isHungerGamesWorld(player.getWorld())
            ? HUNGER_GAMES_MAX_SPIDERS
            : MAX_SPIDERS_PER_PLAYER;

        for (int i = 0; i < spiderCount; i++) {
            try {
                Location spiderLoc = spawnLocation.clone().add(
                        (Math.random() - 0.5) * 4,
                        0,
                        (Math.random() - 0.5) * 4
                );

                spawnLocation.getWorld().spawnParticle(
                        Particle.LARGE_SMOKE,
                        spiderLoc,
                        100,
                        0.5, 1, 0.5,
                        0.05
                );

                CaveSpider spider = (CaveSpider) spawnLocation.getWorld().spawnEntity(spiderLoc, EntityType.CAVE_SPIDER);
                spider.setRemoveWhenFarAway(false);
                spider.getPersistentDataContainer().set(SPIDER_TAG, PersistentDataType.BYTE, (byte) 1);

                spider.setTarget(null);
                spider.setAggressive(false);
                spider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPIDER_DURATION_TICKS, 0, true, false, false));

                UUID spiderUUID = spider.getUniqueId();
                newSpiders.add(spiderUUID);
                startSpiderControl(spider, player);
                if (initialTarget != null && initialTarget.isValid() && !initialTarget.isDead()) {
                    setSpiderTarget(spider, initialTarget);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn spider for player " + player.getName() + ": " + e.getMessage());
            }
        }

        playerSpiders.put(playerUUID, newSpiders);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (spawnSessions.getOrDefault(playerUUID, 0) == session) {
                removePlayerSpiders(player);
            }
        }, SPIDER_DURATION_TICKS);
    }

    public static void setPlayerSpidersTarget(Player player, LivingEntity target) {
        Set<UUID> spiderUUIDs = playerSpiders.get(player.getUniqueId());
        if (spiderUUIDs == null) return;

        if (target != null && target.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        for (UUID spiderUUID : spiderUUIDs) {
            Entity entity = plugin.getServer().getEntity(spiderUUID);
            if (entity instanceof CaveSpider && entity.isValid()) {
                setSpiderTarget((CaveSpider) entity, target);
            }
        }
    }

    public static void removePlayerSpiders(Player player) {
        UUID playerUUID = player.getUniqueId();
        spawnSessions.remove(playerUUID);
        Set<UUID> spiderUUIDs = playerSpiders.remove(playerUUID);

        if (spiderUUIDs != null) {
            for (UUID spiderUUID : spiderUUIDs) {
                cleanupSpider(spiderUUID);

                Entity entity = plugin.getServer().getEntity(spiderUUID);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
        }
    }

    public static int getPlayerSpiderCount(Player player) {
        Set<UUID> spiderUUIDs = playerSpiders.get(player.getUniqueId());
        if (spiderUUIDs == null) return 0;

        spiderUUIDs.removeIf(uuid -> {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (!(entity instanceof CaveSpider) || !entity.isValid() || entity.isDead()) {
                cleanupSpider(uuid);
                return true;
            }
            return false;
        });
        return spiderUUIDs.size();
    }

    public static void shutdown() {
        for (BukkitRunnable task : spiderTasks.values()) {
            task.cancel();
        }
        spiderTasks.clear();
        spiderTargets.clear();
        playerSpiders.clear();

        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof CaveSpider) {
                    if (entity.getPersistentDataContainer().has(SPIDER_TAG, PersistentDataType.BYTE)) {
                        entity.remove();
                    }
                }
            }
        }
    }

    private static void setSpiderTarget(CaveSpider spider, LivingEntity target) {
        UUID spiderUUID = spider.getUniqueId();

        if (target == null) {
            spiderTargets.remove(spiderUUID);
        } else {
            spiderTargets.put(spiderUUID, target.getUniqueId());
        }
    }

    private static void startSpiderControl(CaveSpider spider, Player owner) {
        UUID spiderUUID = spider.getUniqueId();
        UUID ownerUUID = owner.getUniqueId();

        BukkitRunnable controlTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!spider.isValid() || spider.isDead()) {
                    cancel();
                    cleanupDeadSpider(spiderUUID, ownerUUID);
                    return;
                }

                Player currentOwner = plugin.getServer().getPlayer(ownerUUID);
                if (currentOwner == null || !currentOwner.isOnline()) {
                    return;
                }

                LivingEntity currentTarget = spider.getTarget();
                if (currentTarget != null && currentTarget.getUniqueId().equals(ownerUUID)) {
                    spider.setTarget(null);
                    spider.setAggressive(false);
                }

                UUID targetUUID = spiderTargets.get(spiderUUID);

                if (targetUUID != null) {
                    // Don't allow targeting the owner even if somehow set
                    if (targetUUID.equals(ownerUUID)) {
                        spiderTargets.remove(spiderUUID);
                        return;
                    }

                    Entity targetEntity = plugin.getServer().getEntity(targetUUID);
                    if (targetEntity instanceof LivingEntity target && target.isValid() && !target.isDead()) {
                        if (target != currentOwner && spider.getTarget() != target) {
                            spider.setTarget(target);
                            spider.setAggressive(true);
                        }
                        if (SpiderStaffListener.isMarkedTarget(currentOwner, target.getUniqueId())) {
                            int amplifier = LegendaryModeRules.isHungerGamesWorld(currentOwner.getWorld()) ? 0 : 1;
                            spider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, amplifier, true, false, false));
                        }
                    } else {
                        LivingEntity retarget = findNearestEnemy(spider, currentOwner);
                        if (retarget != null) {
                            setSpiderTarget(spider, retarget);
                            spider.setTarget(retarget);
                            spider.setAggressive(true);
                        } else {
                            spiderTargets.remove(spiderUUID);
                        }
                    }
                } else {
                    followOwner(spider, currentOwner);
                }
            }
        };

        controlTask.runTaskTimer(plugin, 0L, 2L);
        spiderTasks.put(spiderUUID, controlTask);
    }

    private static void followOwner(CaveSpider spider, Player owner) {
        double distance = spider.getLocation().distance(owner.getLocation());

        if (distance > 20) {
            Location teleportLoc = owner.getLocation().add(
                    (Math.random() - 0.5) * 6,
                    0,
                    (Math.random() - 0.5) * 6
            );
            spider.teleport(teleportLoc);
        } else if (distance > 5) {
            spider.setTarget(null);
            spider.setAggressive(false);

            try {
                spider.getPathfinder().moveTo(owner.getLocation(), 1.2);
            } catch (Exception e) {
                spider.setTarget(owner);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (spider.isValid() && spider.getTarget() == owner) {
                        spider.setTarget(null);
                    }
                }, 1L);
            }
        }
    }

    private static void cleanupSpider(UUID spiderUUID) {
        BukkitRunnable task = spiderTasks.remove(spiderUUID);
        if (task != null) {
            task.cancel();
        }
        spiderTargets.remove(spiderUUID);
    }

    private static LivingEntity findNearestEnemy(CaveSpider spider, Player owner) {
        LivingEntity nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (Entity nearby : spider.getNearbyEntities(16.0D, 8.0D, 16.0D)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                continue;
            }
            if (living.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            if (living instanceof CaveSpider caveSpider
                && caveSpider.getPersistentDataContainer().has(SPIDER_TAG, PersistentDataType.BYTE)) {
                continue;
            }

            double distSq = living.getLocation().distanceSquared(spider.getLocation());
            if (distSq < nearestDistanceSq) {
                nearestDistanceSq = distSq;
                nearest = living;
            }
        }

        return nearest;
    }

    private static void cleanupDeadSpider(UUID spiderUUID, UUID ownerUUID) {
        cleanupSpider(spiderUUID);

        Set<UUID> spiders = playerSpiders.get(ownerUUID);
        if (spiders != null) {
            spiders.remove(spiderUUID);
        }
    }

    private static UUID getOwnerUUIDFromSpider(CaveSpider spider) {
        for (Map.Entry<UUID, Set<UUID>> entry : playerSpiders.entrySet()) {
            if (entry.getValue().contains(spider.getUniqueId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof CaveSpider spider)) return;
        if (!(event.getTarget() instanceof Player player)) return;

        // Only cancel if this spider is one of ours
        if (spider.getPersistentDataContainer().has(SPIDER_TAG, PersistentDataType.BYTE)) {
            UUID ownerUUID = getOwnerUUIDFromSpider(spider);
            if (ownerUUID != null && ownerUUID.equals(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

}

