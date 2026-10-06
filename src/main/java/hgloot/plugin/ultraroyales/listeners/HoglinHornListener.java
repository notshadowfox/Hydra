package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.TeamProtectionService;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.ultraroyales.UltraRoyales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HoglinHornListener implements Listener {
    private static final int MINI_HOG_COUNT = 4;
    private static final long SUMMON_DURATION_TICKS = 20L * 28L;
    private static final long DASH_DURATION_TICKS = 18L;
    private static final long DASH_COOLDOWN_TICKS = 45L;
    private static final long STOMP_COOLDOWN_TICKS = 70L;
    private static final double PRIMARY_DASH_DAMAGE = 5.5D;
    private static final double MINI_PRESSURE_DAMAGE = 2.0D;
    private static final Map<UUID, UUID> primaryByOwner = new HashMap<>();
    private static final Map<UUID, UUID> ownerByHoglin = new HashMap<>();
    private static final Map<UUID, List<UUID>> supportsByOwner = new HashMap<>();
    private static final Map<UUID, Long> despawnAtTick = new HashMap<>();
    private static final Map<UUID, DashState> dashStates = new HashMap<>();
    private static final Map<UUID, Long> nextDashTick = new HashMap<>();
    private static final Map<UUID, Long> nextStompTick = new HashMap<>();
    private static boolean taskStarted;

    public HoglinHornListener() {
        startTask();
    }

    public static void use(Player player) {
        Hoglin primary = getPrimary(player);
        if (primary != null) {
            triggerDash(player, primary);
            return;
        }

        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.hoglinHornCooldown);
        if (cooldown > 0) {
            player.sendMessage(HydraMessages.cooldown("Hoglin Horn", cooldown));
            return;
        }

        summonPack(player);
        CooldownRegistry.setCooldown(player, UltraRoyales.hoglinHornCooldown, 20, false);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Hoglin hoglin)) {
            return;
        }
        UUID ownerId = ownerByHoglin.remove(hoglin.getUniqueId());
        if (ownerId == null) {
            return;
        }
        UUID primaryId = primaryByOwner.get(ownerId);
        if (hoglin.getUniqueId().equals(primaryId)) {
            cleanup(ownerId);
            return;
        }
        List<UUID> supports = supportsByOwner.get(ownerId);
        if (supports != null) {
            supports.remove(hoglin.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isHoglinHorn(player.getInventory().getItemInMainHand())) {
            return;
        }
        Hoglin primary = getPrimary(player);
        if (primary == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        triggerStomp(player, primary);
    }

    private static void summonPack(Player owner) {
        Location spawn = owner.getLocation();
        Hoglin primary = spawnHoglin(owner, spawn, true);
        primaryByOwner.put(owner.getUniqueId(), primary.getUniqueId());
        ownerByHoglin.put(primary.getUniqueId(), owner.getUniqueId());
        despawnAtTick.put(primary.getUniqueId(), Bukkit.getCurrentTick() + SUMMON_DURATION_TICKS);
        primary.addPassenger(owner);

        List<UUID> supports = new ArrayList<>();
        for (int i = 0; i < MINI_HOG_COUNT; i++) {
            double angle = (Math.PI * 2.0D / MINI_HOG_COUNT) * i;
            Location supportSpawn = spawn.clone().add(Math.cos(angle) * 1.6D, 0.0D, Math.sin(angle) * 1.6D);
            Hoglin support = spawnHoglin(owner, supportSpawn, false);
            supports.add(support.getUniqueId());
            ownerByHoglin.put(support.getUniqueId(), owner.getUniqueId());
        }
        supportsByOwner.put(owner.getUniqueId(), supports);

        owner.getWorld().playSound(spawn, Sound.ITEM_GOAT_HORN_SOUND_0, 1.2F, 0.72F);
        owner.getWorld().playSound(spawn, Sound.ENTITY_HOGLIN_ANGRY, 1.0F, 0.8F);
        owner.getWorld().spawnParticle(Particle.CLOUD, spawn, 42, 0.9D, 0.25D, 0.9D, 0.04D);
    }

    private static Hoglin spawnHoglin(Player owner, Location location, boolean primary) {
        return owner.getWorld().spawn(location, Hoglin.class, hoglin -> {
            hoglin.setCollidable(primary);
            if (hoglin.getAttribute(Attribute.MAX_HEALTH) != null) {
                hoglin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(primary ? 70.0D : 18.0D);
            }
            if (hoglin.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                hoglin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(primary ? 0.28D : 0.42D);
            }
            if (hoglin.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                hoglin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(primary ? 7.0D : 3.0D);
            }
            if (hoglin.getAttribute(Attribute.SCALE) != null) {
                hoglin.getAttribute(Attribute.SCALE).setBaseValue(primary ? 1.55D : 0.72D);
            }
            hoglin.setHealth(primary ? 70.0D : 18.0D);
            if (primary) {
                hoglin.setAdult();
                hoglin.setAI(false);
                hoglin.customName(Component.text(owner.getName() + "'s War Hog", NamedTextColor.GOLD));
            } else {
                hoglin.setBaby();
                hoglin.setAI(true);
                hoglin.customName(Component.text(owner.getName() + "'s Hunting Hog", NamedTextColor.YELLOW));
            }
            hoglin.setRemoveWhenFarAway(false);
            hoglin.setImmuneToZombification(true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHoglinDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Hoglin damager)) {
            return;
        }
        UUID ownerId = ownerByHoglin.get(damager.getUniqueId());
        if (ownerId == null) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim.getUniqueId().equals(ownerId) || isOwnedHoglin(victim)) {
            event.setCancelled(true);
            clearUnsafeTarget(damager, ownerId);
        }
    }

    private static void triggerDash(Player owner, Hoglin primary) {
        long nowTick = Bukkit.getCurrentTick();
        long readyAt = nextDashTick.getOrDefault(owner.getUniqueId(), 0L);
        if (readyAt > nowTick) {
            owner.sendActionBar(HydraMessages.cooldown("Hoglin Horn dash", (readyAt - nowTick) / 20.0D));
            return;
        }
        Vector direction = owner.getLocation().getDirection().clone().setY(0.0D);
        if (direction.lengthSquared() <= 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        direction.normalize();
        dashStates.put(primary.getUniqueId(), new DashState(owner.getUniqueId(), direction, nowTick + DASH_DURATION_TICKS));
        nextDashTick.put(owner.getUniqueId(), nowTick + DASH_COOLDOWN_TICKS);
        primary.getWorld().playSound(primary.getLocation(), Sound.ENTITY_HOGLIN_ANGRY, 1.2F, 0.65F);
        primary.getWorld().playSound(primary.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
    }

    private static void triggerStomp(Player owner, Hoglin primary) {
        long nowTick = Bukkit.getCurrentTick();
        long readyAt = nextStompTick.getOrDefault(owner.getUniqueId(), 0L);
        if (readyAt > nowTick) {
            owner.sendActionBar(HydraMessages.cooldown("Hoglin Horn stomp", (readyAt - nowTick) / 20.0D));
            return;
        }
        nextStompTick.put(owner.getUniqueId(), nowTick + STOMP_COOLDOWN_TICKS);
        Location center = primary.getLocation().add(0.0D, 0.35D, 0.0D);
        primary.getWorld().spawnParticle(Particle.EXPLOSION, center, 2, 0.4D, 0.1D, 0.4D, 0.0D);
        primary.getWorld().spawnParticle(Particle.CLOUD, center, 48, 1.6D, 0.25D, 1.6D, 0.05D);
        primary.getWorld().playSound(center, Sound.ENTITY_HOGLIN_ANGRY, 1.3F, 0.55F);
        for (Entity entity : primary.getNearbyEntities(5.0D, 2.4D, 5.0D)) {
            if (!(entity instanceof LivingEntity target) || isOwnedHoglin(entity) || target.getUniqueId().equals(owner.getUniqueId()) || target.isDead()) {
                continue;
            }
            if (TeamProtectionService.blocks(owner, target)) continue;
            target.damage(6.0D, owner);
            Vector knockback = target.getLocation().toVector().subtract(primary.getLocation().toVector());
            if (knockback.lengthSquared() > 0.0001D) {
                target.setVelocity(knockback.normalize().multiply(1.05D).setY(0.42D));
            }
        }
    }

    private static void startTask() {
        if (taskStarted) {
            return;
        }
        taskStarted = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                tickPacks();
            }
        }.runTaskTimer(UltraRoyales.getPlugin(), 1L, 1L);
    }

    private static void tickPacks() {
        long nowTick = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, UUID>> iterator = primaryByOwner.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            UUID ownerId = entry.getKey();
            Player owner = Bukkit.getPlayer(ownerId);
            Entity primaryEntity = Bukkit.getEntity(entry.getValue());
            if (!(primaryEntity instanceof Hoglin primary) || owner == null || !owner.isOnline() || primary.isDead() || !primary.isValid()) {
                iterator.remove();
                cleanup(ownerId);
                continue;
            }
            if (nowTick >= despawnAtTick.getOrDefault(primary.getUniqueId(), 0L)) {
                iterator.remove();
                cleanup(ownerId);
                continue;
            }

            clearUnsafeTarget(primary, ownerId);
            tickMountedPrimary(owner, primary);
            tickPrimaryDash(owner, primary, nowTick);
            tickSupportHogs(owner, primary, nowTick);
        }
    }

    private static void tickMountedPrimary(Player owner, Hoglin primary) {
        if (!primary.getPassengers().contains(owner) || dashStates.containsKey(primary.getUniqueId())) {
            return;
        }
        Vector direction = owner.getLocation().getDirection().clone().setY(0.0D);
        if (direction.lengthSquared() <= 0.0001D) {
            return;
        }
        direction.normalize();
        primary.setRotation(owner.getLocation().getYaw(), primary.getLocation().getPitch());
        Vector velocity = direction.multiply(0.46D);
        velocity.setY(primary.getVelocity().getY());
        primary.setVelocity(velocity);
    }

    private static void tickPrimaryDash(Player owner, Hoglin primary, long nowTick) {
        DashState dash = dashStates.get(primary.getUniqueId());
        if (dash == null) {
            return;
        }
        if (dash.endsAtTick <= nowTick || primary.isDead() || !primary.isValid()) {
            dashStates.remove(primary.getUniqueId());
            return;
        }
        primary.setVelocity(dash.direction.clone().multiply(1.15D).setY(primary.getVelocity().getY()));
        primary.getWorld().spawnParticle(Particle.CLOUD, primary.getLocation().add(0.0D, 0.25D, 0.0D), 5, 0.35D, 0.1D, 0.35D, 0.01D);
        for (Entity entity : primary.getNearbyEntities(2.6D, 1.8D, 2.6D)) {
            if (!(entity instanceof LivingEntity target) || target.isDead() || isUnsafePackTarget(entity, owner.getUniqueId())) {
                continue;
            }
            if (!dash.hitTargets.add(target.getUniqueId())) {
                continue;
            }
            if (TeamProtectionService.blocks(owner, target)) continue;
            target.setNoDamageTicks(0);
            target.damage(PRIMARY_DASH_DAMAGE, owner);
            target.setVelocity(dash.direction.clone().multiply(1.2D).setY(0.42D));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.65F);
        }
    }

    private static void tickSupportHogs(Player owner, Hoglin primary, long nowTick) {
        List<UUID> supports = supportsByOwner.getOrDefault(owner.getUniqueId(), List.of());
        LivingEntity target = findPackTarget(owner, primary);
        for (UUID supportId : new ArrayList<>(supports)) {
            Entity entity = Bukkit.getEntity(supportId);
            if (!(entity instanceof Hoglin support) || support.isDead() || !support.isValid()) {
                supports.remove(supportId);
                ownerByHoglin.remove(supportId);
                continue;
            }
            if (support.getLocation().distanceSquared(primary.getLocation()) > 400.0D) {
                support.teleport(supportRingLocation(primary, supports.indexOf(supportId), supports.size()));
            }
            keepSupportOffPrimary(support, primary);
            clearUnsafeTarget(support, owner.getUniqueId());
            if (target != null && support instanceof Mob mob) {
                mob.setTarget(target);
                if (dashStates.containsKey(primary.getUniqueId()) && nowTick % 5L == 0L) {
                    Vector pressure = target.getLocation().toVector().subtract(support.getLocation().toVector());
                    if (pressure.lengthSquared() > 0.0001D) {
                        support.setVelocity(pressure.normalize().multiply(0.78D).setY(0.18D));
                    }
                }
                if (support.getLocation().distanceSquared(target.getLocation()) <= 2.25D && nowTick % 10L == 0L) {
                    target.damage(MINI_PRESSURE_DAMAGE, owner);
                }
            } else if (support instanceof Mob mob) {
                mob.setTarget(null);
                assistPrimary(support, primary);
            }
        }
    }

    private static void assistPrimary(Hoglin support, Hoglin primary) {
        double distance = support.getLocation().distanceSquared(primary.getLocation());
        if (distance <= 16.0D) {
            return;
        }
        Location goal = supportRingLocation(primary, Math.abs(support.getUniqueId().hashCode()), 8);
        Vector follow = goal.toVector().subtract(support.getLocation().toVector());
        if (follow.lengthSquared() <= 0.0001D) {
            return;
        }
        double speed = distance > 64.0D ? 0.68D : 0.36D;
        support.setVelocity(follow.normalize().multiply(speed).setY(Math.min(0.08D, Math.max(0.0D, support.getVelocity().getY()))));
    }

    private static void keepSupportOffPrimary(Hoglin support, Hoglin primary) {
        double distance = support.getLocation().distanceSquared(primary.getLocation());
        if (distance >= 4.0D) {
            return;
        }
        Vector away = support.getLocation().toVector().subtract(primary.getLocation().toVector());
        if (away.lengthSquared() <= 0.0001D) {
            away = new Vector(1.0D, 0.0D, 0.0D);
        }
        support.setVelocity(away.normalize().multiply(0.28D).setY(0.0D));
    }

    private static Location supportRingLocation(Hoglin primary, int index, int count) {
        int normalizedCount = Math.max(1, count);
        double angle = (Math.PI * 2.0D / normalizedCount) * Math.floorMod(index, normalizedCount);
        return primary.getLocation().clone().add(Math.cos(angle) * 3.2D, 0.0D, Math.sin(angle) * 3.2D);
    }

    private static LivingEntity findPackTarget(Player owner, Hoglin primary) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : primary.getNearbyEntities(12.0D, 5.0D, 12.0D)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || living.getUniqueId().equals(owner.getUniqueId()) || isOwnedHoglin(entity)) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(primary.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private static Hoglin getPrimary(Player player) {
        UUID primaryId = primaryByOwner.get(player.getUniqueId());
        if (primaryId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(primaryId);
        if (entity instanceof Hoglin hoglin && hoglin.isValid() && !hoglin.isDead()) {
            return hoglin;
        }
        cleanup(player.getUniqueId());
        return null;
    }

    private static boolean isOwnedHoglin(Entity entity) {
        return entity instanceof Hoglin && ownerByHoglin.containsKey(entity.getUniqueId());
    }

    private static boolean isUnsafePackTarget(Entity entity, UUID ownerId) {
        return entity.getUniqueId().equals(ownerId) || isOwnedHoglin(entity);
    }

    private static void clearUnsafeTarget(Hoglin hoglin, UUID ownerId) {
        if (!(hoglin instanceof Mob mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && isUnsafePackTarget(target, ownerId)) {
            mob.setTarget(null);
        }
    }

    private static boolean isHoglinHorn(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelDataComponent()) {
            return false;
        }
        return meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:hoglin_horn");
    }

    private static void cleanup(UUID ownerId) {
        UUID primaryId = primaryByOwner.remove(ownerId);
        if (primaryId != null) {
            ownerByHoglin.remove(primaryId);
            dashStates.remove(primaryId);
            despawnAtTick.remove(primaryId);
            Entity primary = Bukkit.getEntity(primaryId);
            if (primary != null && primary.isValid()) {
                primary.remove();
            }
        }
        List<UUID> supports = supportsByOwner.remove(ownerId);
        if (supports != null) {
            for (UUID supportId : supports) {
                ownerByHoglin.remove(supportId);
                Entity support = Bukkit.getEntity(supportId);
                if (support != null && support.isValid()) {
                    support.remove();
                }
            }
        }
        nextDashTick.remove(ownerId);
        nextStompTick.remove(ownerId);
    }

    private static final class DashState {
        private final Vector direction;
        private final long endsAtTick;
        private final Set<UUID> hitTargets = new HashSet<>();

        private DashState(UUID ownerId, Vector direction, long endsAtTick) {
            this.direction = direction;
            this.endsAtTick = endsAtTick;
        }
    }
}
