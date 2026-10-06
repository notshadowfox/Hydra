package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.TeamProtectionService;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class KnightsSaddleListener implements Listener {

    public static final int SADDLE_COOLDOWN_SECONDS = 45;
    public static final int PREPARE_TICKS = 12;
    public static final int RECOVERY_TICKS = 36;
    private static final int BASE_CHARGE_TICKS = 54;
    private static final int EXTENSION_PER_HIT_TICKS = 5;
    private static final int MAX_EXTENSION_TICKS = 20;
    private static final int MIN_CHAIN_HITS_PER_TARGET = 2;
    private static final int MAX_CHAIN_HITS_PER_TARGET = 4;
    private static final double TRAMPLE_MIN_DAMAGE = 2.0D;
    private static final double TRAMPLE_MAX_DAMAGE = 4.0D;
    private static final double TRAMPLE_SIDEWAYS_KNOCKBACK = 0.16D;
    private static final double TRAMPLE_FORWARD_CARRY = 1.08D;
    private static final double TRAMPLE_UPWARD_LIFT = 0.34D;
    private static final double CHAIN_HOLD_DISTANCE = 1.8D;
    private static final double CHAIN_CORRECTION_STRENGTH = 0.34D;
    private static final double CHAIN_MAX_CORRECTION = 0.44D;
    private static final long SAME_TARGET_TRAMPLE_COOLDOWN_MS = 300L;
    private static final long DISRUPTION_PROTECTION_MS = 1250L;
    private static final Map<UUID, ChargeState> chargeStates = new HashMap<>();

    public static void beginCharge(Horse horse, Vector direction) {
        if (horse == null) {
            return;
        }
        Vector chargeDirection = direction == null ? new Vector(0.0D, 0.0D, 1.0D) : direction.clone().setY(0.0D);
        if (chargeDirection.lengthSquared() <= 0.0001D) {
            chargeDirection = new Vector(0.0D, 0.0D, 1.0D);
        }
        chargeStates.put(horse.getUniqueId(), new ChargeState(chargeDirection.normalize()));
    }

    public static Vector getChargeDirection(Horse horse) {
        if (horse == null) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        ChargeState state = chargeStates.get(horse.getUniqueId());
        if (state == null) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return state.direction.clone();
    }

    public static void endCharge(Horse horse) {
        if (horse == null) {
            return;
        }
        UUID horseId = horse.getUniqueId();
        chargeStates.remove(horseId);
    }

    public static int getChargeDurationTicks(Horse horse) {
        if (horse == null) {
            return BASE_CHARGE_TICKS;
        }
        ChargeState state = chargeStates.get(horse.getUniqueId());
        if (state == null) {
            return BASE_CHARGE_TICKS;
        }
        return BASE_CHARGE_TICKS + state.extensionTicks;
    }

    public static boolean isChargeActive(Horse horse) {
        return horse != null && chargeStates.containsKey(horse.getUniqueId());
    }

    public static boolean triggerTerrainSmashIfBlocked(Horse horse, Player rider, Vector forward) {
        if (horse == null || rider == null || forward == null || !chargeStates.containsKey(horse.getUniqueId())) {
            return false;
        }

        Vector direction = forward.clone();
        if (direction.lengthSquared() <= 0.0001D) {
            return false;
        }
        direction.normalize();

        Location probe = horse.getLocation().clone().add(direction.clone().multiply(1.15D));
        Block body = probe.clone().add(0.0D, 1.05D, 0.0D).getBlock();
        Block head = probe.clone().add(0.0D, 1.85D, 0.0D).getBlock();
        if (!isMajorCollisionBlock(body) && !isMajorCollisionBlock(head)) {
            return false;
        }

        Location center = horse.getLocation().add(0.0D, 0.6D, 0.0D);
        horse.getWorld().spawnParticle(Particle.EXPLOSION, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        horse.getWorld().spawnParticle(Particle.CLOUD, center, 34, 0.85D, 0.24D, 0.85D, 0.04D);
        horse.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, center, 26, 0.75D, 0.18D, 0.75D, 0.02D, Material.DIRT.createBlockData());
        horse.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.05f);
        horse.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);

        for (Entity nearby : horse.getNearbyEntities(3.0D, 1.6D, 3.0D)) {
            if (!(nearby instanceof LivingEntity target) || target.isDead()) {
                continue;
            }
            if (target.getUniqueId().equals(rider.getUniqueId()) || target.getUniqueId().equals(horse.getUniqueId())) {
                continue;
            }

            if (TeamProtectionService.blocks(rider, target)) continue;
            Vector away = target.getLocation().toVector().subtract(horse.getLocation().toVector());
            if (away.lengthSquared() > 0.0001D) {
                target.setVelocity(away.normalize().multiply(0.62D).setY(0.28D));
            }
        }

        return true;
    }

    private static boolean isMajorCollisionBlock(Block block) {
        if (block == null || block.isPassable() || block.isLiquid()) {
            return false;
        }
        Material type = block.getType();
        return type.isSolid()
            && type != Material.SHORT_GRASS
            && type != Material.TALL_GRASS
            && type != Material.FERN
            && type != Material.LARGE_FERN
            && type != Material.SNOW;
    }

    public static void applyChargeDamage(Horse horse, Player rider) {
        UUID horseId = horse.getUniqueId();
        ChargeState state = chargeStates.computeIfAbsent(horseId, ignored -> new ChargeState(new Vector(0.0D, 0.0D, 1.0D)));
        Set<Entity> candidates = new HashSet<>(horse.getNearbyEntities(2.75D, 1.85D, 2.75D));
        Vector direction = state.direction.clone();
        if (direction.lengthSquared() > 0.0001D) {
            Location sweepCenter = horse.getLocation().clone().add(direction.normalize().multiply(2.25D));
            candidates.addAll(horse.getWorld().getNearbyEntities(sweepCenter, 2.75D, 1.85D, 2.75D));
        }

        for (Entity nearby : candidates) {
            if (!(nearby instanceof LivingEntity target)) {
                continue;
            }
            if (target.getUniqueId().equals(rider.getUniqueId()) || target.getUniqueId().equals(horse.getUniqueId()) || target.isDead()) {
                continue;
            }

            long now = System.currentTimeMillis();
            long protectedUntil = state.disruptedUntil.getOrDefault(target.getUniqueId(), 0L);
            if (now < protectedUntil) {
                continue;
            }
            int hitCount = state.hitCounts.getOrDefault(target.getUniqueId(), 0);
            int maxHits = state.maxHits.computeIfAbsent(
                target.getUniqueId(),
                ignored -> ThreadLocalRandom.current().nextInt(MIN_CHAIN_HITS_PER_TARGET, MAX_CHAIN_HITS_PER_TARGET + 1)
            );
            if (hitCount >= maxHits) {
                continue;
            }
            long lastHit = state.recentHitTimes.getOrDefault(target.getUniqueId(), 0L);
            if (now - lastHit < SAME_TARGET_TRAMPLE_COOLDOWN_MS) {
                continue;
            }
            int nextHitCount = hitCount + 1;
            state.recentHitTimes.put(target.getUniqueId(), now);
            state.hitCounts.put(target.getUniqueId(), nextHitCount);

            if (TeamProtectionService.blocks(rider, target)) continue;
            double damage = ThreadLocalRandom.current().nextDouble(TRAMPLE_MIN_DAMAGE, TRAMPLE_MAX_DAMAGE + 0.0001D);
            target.setNoDamageTicks(0);
            target.damage(damage, rider);
            if (nextHitCount >= maxHits) {
                target.setVelocity(sidewaysTrampleVelocity(horse, target, state.direction));
            } else {
                carryTargetInTrampleLane(horse, target, state.direction);
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 28, 0, false, true, true));
            state.stabCount++;
            if (state.extendedTargets.add(target.getUniqueId())) {
                state.extensionTicks = Math.min(MAX_EXTENSION_TICKS, state.extensionTicks + EXTENSION_PER_HIT_TICKS);
            }
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0.0D, 1.0D, 0.0D), 2, 0.2D, 0.2D, 0.2D, 0.0D);
            target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0.0D, 0.35D, 0.0D), 12, 0.32D, 0.12D, 0.32D, 0.02D);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.72f);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_HORSE_GALLOP, 0.8f, 0.75f);
        }
    }

    private static void maintainChainedTargets(Horse horse, ChargeState state) {
        if (horse == null || state == null || state.hitCounts.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Entity nearby : horse.getNearbyEntities(6.5D, 2.5D, 6.5D)) {
            if (!(nearby instanceof LivingEntity target) || target.isDead()) {
                continue;
            }
            UUID targetId = target.getUniqueId();
            int hitCount = state.hitCounts.getOrDefault(targetId, 0);
            int maxHits = state.maxHits.getOrDefault(targetId, 0);
            if (hitCount <= 0 || maxHits <= 0 || hitCount >= maxHits) {
                continue;
            }
            if (now < state.disruptedUntil.getOrDefault(targetId, 0L)) {
                continue;
            }
            carryTargetInTrampleLane(horse, target, state.direction);
        }
    }

    private static void carryTargetInTrampleLane(Horse horse, LivingEntity target, Vector chargeDirection) {
        Vector forward = normalizedHorizontal(chargeDirection);
        Vector desired = horse.getLocation().toVector().add(forward.clone().multiply(CHAIN_HOLD_DISTANCE));
        Vector correction = desired.subtract(target.getLocation().toVector()).setY(0.0D).multiply(CHAIN_CORRECTION_STRENGTH);
        if (correction.length() > CHAIN_MAX_CORRECTION) {
            correction.normalize().multiply(CHAIN_MAX_CORRECTION);
        }
        Vector carry = forward.multiply(Math.max(TRAMPLE_FORWARD_CARRY, horse.getVelocity().length()))
            .add(correction)
            .setY(TRAMPLE_UPWARD_LIFT);
        target.setFallDistance(0.0F);
        target.setVelocity(carry);
    }

    private static void disruptChainFor(Horse horse, Player player) {
        if (horse == null || player == null) {
            return;
        }
        ChargeState state = chargeStates.get(horse.getUniqueId());
        if (state == null) {
            return;
        }
        state.disruptedUntil.put(player.getUniqueId(), System.currentTimeMillis() + DISRUPTION_PROTECTION_MS);
        state.recentHitTimes.put(player.getUniqueId(), System.currentTimeMillis());
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.35f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.28D, 0.35D, 0.28D, 0.03D);
    }

    private static boolean isKnightsHorse(Entity entity) {
        return entity instanceof Horse horse
            && Boolean.TRUE.equals(horse.getPersistentDataContainer().get(Items.knightsHorse, PersistentDataType.BOOLEAN));
    }

    private static Vector sidewaysTrampleVelocity(Horse horse, LivingEntity target, Vector chargeDirection) {
        Vector forward = normalizedHorizontal(chargeDirection);

        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
        Vector left = right.clone().multiply(-1.0D);
        Vector fromHorse = target.getLocation().toVector().subtract(horse.getLocation().toVector()).setY(0.0D);
        Vector preferred = fromHorse.lengthSquared() > 0.0001D && fromHorse.dot(right) < 0.0D ? left : right;

        Vector alternate = preferred == right ? left : right;
        Vector chosen = hasSaferLanding(target, preferred) || !hasSaferLanding(target, alternate) ? preferred : alternate;
        return chosen.multiply(TRAMPLE_SIDEWAYS_KNOCKBACK)
            .add(forward.multiply(TRAMPLE_FORWARD_CARRY))
            .setY(TRAMPLE_UPWARD_LIFT);
    }

    private static Vector normalizedHorizontal(Vector direction) {
        Vector forward = direction == null ? new Vector(0.0D, 0.0D, 1.0D) : direction.clone().setY(0.0D);
        if (forward.lengthSquared() <= 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        return forward.normalize();
    }

    private static boolean hasSaferLanding(LivingEntity target, Vector direction) {
        Location landing = target.getLocation().clone().add(direction.clone().normalize().multiply(1.45D));
        Block feet = landing.getBlock();
        Block head = landing.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        Block floor = landing.clone().add(0.0D, -1.0D, 0.0D).getBlock();
        return feet.isPassable() && head.isPassable() && (!floor.isPassable() || floor.isLiquid());
    }

    public static void applyRecovery(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, RECOVERY_TICKS, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, RECOVERY_TICKS, 0, false, true, true));
    }

    @EventHandler
    public void onHorseDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || !isKnightsHorse(horse)) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            disruptChainFor(horse, player);
        }
    }

    @EventHandler
    public void onHorseInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse) || !isKnightsHorse(horse)) {
            return;
        }
        event.setCancelled(true);
        disruptChainFor(horse, event.getPlayer());
    }

    @EventHandler
    public void onHorseInteractAt(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse) || !isKnightsHorse(horse)) {
            return;
        }
        event.setCancelled(true);
        disruptChainFor(horse, event.getPlayer());
    }

    @EventHandler
    public void onHorseEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Horse horse) || !isKnightsHorse(horse)) {
            return;
        }
        if (event.getEntered() instanceof Player player
            && horse.getOwner() != null
            && horse.getOwner().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onHorseInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof HorseInventory inventory)) {
            return;
        }
        if (inventory.getHolder() instanceof Entity holder && isKnightsHorse(holder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Horse horse)) {
            return;
        }
        if (!Boolean.TRUE.equals(horse.getPersistentDataContainer().get(Items.knightsHorse, PersistentDataType.BOOLEAN))) {
            return;
        }
        if (!chargeStates.containsKey(horse.getUniqueId())) {
            return;
        }
        if (!(horse.getPassengers().stream().findFirst().orElse(null) instanceof Player rider)) {
            return;
        }
        ChargeState state = chargeStates.get(horse.getUniqueId());
        if (state != null) {
            Vector locked = state.direction.clone().multiply(Math.max(0.55D, Math.abs(horse.getVelocity().getX()) + Math.abs(horse.getVelocity().getZ())));
            horse.setVelocity(new Vector(locked.getX(), horse.getVelocity().getY(), locked.getZ()));
            maintainChainedTargets(horse, state);
        }
        applyChargeDamage(horse, rider);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player && event.getDismounted() instanceof Horse horse)) {
            return;
        }
        if (!Boolean.TRUE.equals(horse.getPersistentDataContainer().get(Items.knightsHorse, PersistentDataType.BOOLEAN))) {
            return;
        }
        if (!isChargeActive(horse)) {
            return;
        }

        endCharge(horse);
        horse.remove();
        applyRecovery(player);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, -1, 0), 40, 0.5, 0.5, 0.5, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_HORSE_BREATHE, 1.0f, 0.95f);
        CooldownRegistry.setCooldown(player, UltraRoyales.saddleCooldown, SADDLE_COOLDOWN_SECONDS);
    }

    private static final class ChargeState {
        private final Vector direction;
        private int extensionTicks = 0;
        private int stabCount = 0;
        private final Set<UUID> extendedTargets = new HashSet<>();
        private final Map<UUID, Long> recentHitTimes = new HashMap<>();
        private final Map<UUID, Long> disruptedUntil = new HashMap<>();
        private final Map<UUID, Integer> hitCounts = new HashMap<>();
        private final Map<UUID, Integer> maxHits = new HashMap<>();

        private ChargeState(Vector direction) {
            this.direction = direction;
        }
    }
}
