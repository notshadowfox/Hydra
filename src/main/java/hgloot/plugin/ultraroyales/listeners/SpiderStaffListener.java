package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.ultraroyales.SpiderAIController;
import hgloot.plugin.ultraroyales.UltraRoyales;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class SpiderStaffListener implements Listener {
    private static final long MARK_DURATION_MS = 5_000L;
    private static final double VENOM_WAVE_RADIUS = 6.0D;
    private static final double VENOM_WAVE_RADIUS_SQUARED = VENOM_WAVE_RADIUS * VENOM_WAVE_RADIUS;
    private static final int VENOM_ZONE_TICKS = 80;
    private static final Map<UUID, MarkData> markedTargets = new HashMap<>();

    public static void castVenomWave(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        LivingEntity primaryTarget = getMarkedTarget(player);
        int affected = 0;
        for (Entity nearby : player.getNearbyEntities(VENOM_WAVE_RADIUS, 3.0D, VENOM_WAVE_RADIUS)) {
            if (!(nearby instanceof LivingEntity target) || target.isDead() || !target.isValid()) {
                continue;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (target instanceof CaveSpider caveSpider
                && caveSpider.getPersistentDataContainer().has(SpiderAIController.SPIDER_TAG)) {
                continue;
            }
            if (target.getLocation().distanceSquared(player.getLocation()) > VENOM_WAVE_RADIUS_SQUARED) {
                continue;
            }

            affected++;
            if (primaryTarget == null) {
                primaryTarget = target;
            }
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 80, 0, false, true, true));
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 0, false, true, true));
            target.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, target.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.45D, 0.35D, 0.01D);
        }

        SpiderAIController.spawnPlayerSpiders(player, primaryTarget);
        if (primaryTarget != null) {
            SpiderAIController.setPlayerSpidersTarget(player, primaryTarget);
            markedTargets.put(player.getUniqueId(), new MarkData(primaryTarget.getUniqueId(), System.currentTimeMillis() + MARK_DURATION_MS));
        }
        startVenomZone(player, primaryTarget);

        player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, player.getLocation().add(0.0D, 0.4D, 0.0D), 90, 3.0D, 0.25D, 3.0D, 0.02D);
        player.getWorld().spawnParticle(Particle.ASH, player.getLocation().add(0.0D, 0.8D, 0.0D), 60, 3.2D, 0.4D, 3.2D, 0.03D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.15F, 0.55F);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SPORE_BLOSSOM_BREAK, 0.9F, 0.65F);
        if (affected > 0) {
            UltraRoyales.holdBossRushDamageActionBar(player, 2000L);
            player.sendActionBar(hgloot.plugin.HydraMessages.success("Venom Wave hit " + affected));
        }
    }

    private static void startVenomZone(Player owner, LivingEntity focus) {
        org.bukkit.Location center = focus == null ? owner.getLocation().clone() : focus.getLocation().clone();
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!owner.isOnline() || ticks >= VENOM_ZONE_TICKS || center.getWorld() == null) {
                    cancel();
                    return;
                }
                ticks += 10;
                center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0.0D, 0.25D, 0.0D), 28, 2.6D, 0.12D, 2.6D, 0.01D);
                for (Entity nearby : center.getWorld().getNearbyEntities(center, 4.5D, 2.2D, 4.5D)) {
                    if (!(nearby instanceof LivingEntity target) || target.isDead() || !target.isValid()) {
                        continue;
                    }
                    if (target.getUniqueId().equals(owner.getUniqueId())) {
                        continue;
                    }
                    if (target instanceof CaveSpider caveSpider
                        && caveSpider.getPersistentDataContainer().has(SpiderAIController.SPIDER_TAG)) {
                        continue;
                    }
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 35, 0, false, true, true));
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 30, 0, false, true, true));
                }
            }
        }.runTaskTimer(hgloot.plugin.ultraroyales.UltraRoyales.getPlugin(), 0L, 10L);
    }

    public static LivingEntity getMarkedTarget(Player player) {
        MarkData mark = markedTargets.get(player.getUniqueId());
        if (mark == null || mark.expiresAtMs < System.currentTimeMillis()) {
            markedTargets.remove(player.getUniqueId());
            return null;
        }
        UUID targetId = mark.targetId;
        if (!(player.getServer().getEntity(targetId) instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
            markedTargets.remove(player.getUniqueId());
            return null;
        }
        return living;
    }

    public static boolean isMarkedTarget(Player owner, UUID targetId) {
        MarkData mark = markedTargets.get(owner.getUniqueId());
        if (mark == null || mark.expiresAtMs < System.currentTimeMillis()) {
            markedTargets.remove(owner.getUniqueId());
            return false;
        }
        return mark.targetId.equals(targetId);
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player) || !(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (SpiderAIController.getPlayerSpiderCount(player) <= 0) {
            return;
        }

        var mainHand = player.getInventory().getItemInMainHand();
        if (!mainHand.hasItemMeta()) {
            return;
        }
        var meta = mainHand.getItemMeta();
        if (meta == null || !meta.hasCustomModelDataComponent()) {
            return;
        }

        if (meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:spiderstaff")) {
            if (!victim.getUniqueId().equals(player.getUniqueId())) {
                markedTargets.put(player.getUniqueId(), new MarkData(victim.getUniqueId(), System.currentTimeMillis() + MARK_DURATION_MS));
            }
            SpiderAIController.setPlayerSpidersTarget(player, victim);
            victim.getWorld().spawnParticle(Particle.ASH, victim.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.25D, 0.35D, 0.25D, 0.01D);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.9F, 0.7F);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        markedTargets.remove(player.getUniqueId());
        SpiderAIController.removePlayerSpiders(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        markedTargets.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSpiderHit(EntityDamageByEntityEvent e){
        if (e.getEntity() instanceof Player player && e.getDamager().getType() == EntityType.CAVE_SPIDER) {
            Map<UUID, Set<UUID>> playerSpiders = SpiderAIController.getPlayerSpiders();
            if (playerSpiders.containsKey(player.getUniqueId()) && playerSpiders.get(player.getUniqueId()).contains(e.getDamager().getUniqueId())) {
                e.setCancelled(true);
            } else if (e.getDamager().getPersistentDataContainer().has(SpiderAIController.SPIDER_TAG)) {
                double damage = e.getDamage();
                double armor = Objects.requireNonNull(player.getAttribute(Attribute.ARMOR)).getValue();
                double maxArmor = 20.0;
                double baseBonus = 5.0;     // maximum bonus
                double exponent = 1.8;      // how sharp the scaling is
                double normalized = armor / maxArmor;
                double bonus = baseBonus * Math.pow(normalized, exponent);
                e.setDamage(damage + bonus);
            }
        }
    }

    private record MarkData(UUID targetId, long expiresAtMs) {
    }
}

