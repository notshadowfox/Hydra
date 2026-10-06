package hgloot.plugin;

import hgloot.plugin.ultraroyales.Items;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@SuppressWarnings("deprecation")
public final class CombatMasterService implements Listener {
    private static final double BOSS_TARGET_RANGE = 96.0D;
    private static final String BOSS_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Combat Master";
    private static final int TICK_STEP = 5;
    private static final double MAX_HP = 200.0;
    private static final double MELEE_ABILITY_RANGE = 4.5D;
    private static final double MEDITATION_HEAL = 5.0D;
    private static final int MEDITATION_THRESHOLD = 28;
    private static final int FORM_ANNOUNCE_RADIUS_XZ = 50;
    // Attack keyframes: 0.5s wind-up, 1.0s forward swing. Release at the wind-up transition.
    private static final long MODEL_VOLLEY_RELEASE_TICKS = 10L;
    private static final double MAX_ANCHOR_PARTICIPANT_DAMAGE = 12.0D;
    private static final int MAX_SUSPICIOUS_DAMAGE_REPORTS_PER_BOSS = 8;
    private static final int MAX_DENIED_DAMAGE_REPORTS_PER_BOSS = 8;
    private static final int TRANSITION_TICKS = 240;
    private static final long TRANSITION_WALL_CAP_NANOS = 14_500_000_000L;
    private static final int TRANSITION_RECOVERY_TICKS = 20;
    private static final int CYLINDER_COLUMNS = 12;
    private static final int CYLINDER_ROWS = 6;
    private static final int DENSE_COLUMNS = 64;
    private static final int DENSE_ROWS = 32;
    private static final int DENSE_BATCH_ROWS = 8;
    private static final double CYLINDER_RADIUS = 6.0;
    private static final double DENSE_ROW_SPACING = 0.75;
    private static final float DENSE_START_Y = -24.0f;
    // Dense cutscene tuning: emerge by tick 145, climb another 24 blocks while dissolving through tick 205.
    private static final int DENSE_EMERGE_TICKS = 40;
    private static final int DENSE_ASCEND_TICKS = 60;
    private static final float DENSE_EXTRA_RISE = 24.0f;
    private static final int DENSE_DISSIPATE_START = 150;
    private static final int DENSE_DISSIPATE_END = 205;
    private static final int SPHERICAL_PULSE_POINTS = 96;
    private static final NamespacedKey BLUE_SKULL_MODEL = new NamespacedKey("hydra", "combat_master/blue_wither_skull");

    private final JavaPlugin plugin;
    private final Map<UUID, ActiveBoss> bosses = new HashMap<>();
    private final Map<UUID, PendingRewards> pendingRewards = new HashMap<>();
    private AnchorExplosionContext activeAnchorExplosion;

    public CombatMasterService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean spawnCombatMaster(Player player) {
        return spawnCombatMaster(player, true, false, ignored -> {});
    }

    public boolean spawnCombatMasterForce(Player player) {
        return spawnCombatMaster(player, false, false, ignored -> {});
    }

    public UUID spawnCombatMaster(Location spawn, boolean allWaveMinions, Consumer<Completion> completion) {
        if (spawn == null || spawn.getWorld() == null) {
            return null;
        }
        UUID id = spawnCombatMasterAt(spawn, allWaveMinions, completion == null ? ignored -> {} : completion);
        if (id != null) updateBar(bosses.get(id));
        return id;
    }

    /** Starts the same standard encounter used by the working admin summon path. */
    public UUID spawnCombatMasterEncounter(Location spawn, Consumer<Completion> completion) {
        if (spawn == null || spawn.getWorld() == null) return null;
        // No viewers until bindEncounter assigns an authoritative team roster.
        return spawnCombatMasterAt(spawn, false, completion == null ? ignored -> {} : completion);
    }

    /** Admin-only controlled test: real V1 boss, optional rendering, never a Boss Rush queue spawn. */
    public UUID spawnVisualTest(Location spawn) {
        CombatMasterVisualBridge bridge = visualBridge();
        if (bridge == null) return null;
        UUID id = spawnCombatMasterEncounter(spawn, ignored -> {});
        ActiveBoss active = id == null ? null : bosses.get(id);
        if (active == null) return null;
        try {
            if (bridge.attach(active.boss)) {
                active.visualAttached = true;
                updateBar(active);
                return id;
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[CombatMaster] Visual test attach failed boss=" + id + ": " + ex);
        }
        removeCombatMaster(id);
        return null;
    }

    private CombatMasterVisualBridge visualBridge() {
        return Bukkit.getServicesManager().load(CombatMasterVisualBridge.class);
    }

    private boolean animateAttack(ActiveBoss active) {
        if (!active.visualAttached) return false;
        CombatMasterVisualBridge bridge = visualBridge();
        if (bridge != null) {
            try {
                return bridge.attack(active.boss.getUniqueId());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[CombatMaster] Visual attack failed boss="
                    + active.boss.getUniqueId() + ": " + ex);
            }
        }
        return false;
    }

    public boolean bindEncounter(UUID bossId, Collection<? extends Player> players) {
        ActiveBoss active = bossId == null ? null : bosses.get(bossId);
        if (active == null || !active.boss.isValid() || active.boss.isDead()
            || active.task == null || active.task.isCancelled()) {
            return false;
        }
        active.bar.removeAll();
        active.encounterPlayers = new HashSet<>();
        for (Player player : players) {
            if (player != null) active.encounterPlayers.add(player.getUniqueId());
        }
        active.bar.setVisible(true);
        active.boss.setAI(true);
        active.boss.setGravity(true);
        updateBar(active);
        plugin.getLogger().info("[CombatMaster] Encounter bound boss=" + bossId
            + " players=" + active.bar.getPlayers().size() + " controller=true phase=" + active.phase.name());
        return true;
    }

    public boolean removeCombatMaster(UUID bossId) {
        ActiveBoss active = bossId == null ? null : bosses.get(bossId);
        if (active == null) {
            return false;
        }
        if (active.boss.isValid()) {
            active.boss.remove();
        }
        cleanup(active, Completion.CANCELED);
        return true;
    }

    public boolean isActiveBoss(UUID bossId) {
        ActiveBoss active = bossId == null ? null : bosses.get(bossId);
        return active != null && active.boss.isValid() && !active.boss.isDead();
    }

    public boolean isTransitioning(UUID bossId) {
        ActiveBoss active = bosses.get(bossId);
        return active != null && active.transitionTicks >= 0;
    }

    /** Uses the same threshold transition as combat, but only for a bound Boss Rush boss. */
    public boolean previewNextPhase(UUID bossId) {
        ActiveBoss active = bosses.get(bossId);
        if (active == null || active.encounterPlayers == null || active.transitionTicks >= 0
            || active.phase == Phase.MASTERY || !active.boss.isValid() || active.boss.isDead()) return false;
        active.boss.setHealth(nextPhaseThreshold(active.phase));
        advanceOnePhaseIfNeeded(active);
        return active.transitionTicks >= 0;
    }

    /** Optional local-only appearance toggle; normal V1 encounters remain unchanged by default. */
    public boolean attachEncounterVisual(UUID bossId) {
        ActiveBoss active = bosses.get(bossId);
        CombatMasterVisualBridge bridge = visualBridge();
        if (active == null || bridge == null || active.visualAttached) return false;
        try {
            active.visualAttached = bridge.attach(active.boss);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[CombatMaster] Encounter visual attach failed boss=" + bossId + ": " + ex);
        }
        return active.visualAttached;
    }

    public double healBoss(UUID bossId, double amount) {
        ActiveBoss active = bossId == null ? null : bosses.get(bossId);
        if (active == null || !active.boss.isValid() || active.boss.isDead() || amount <= 0.0D) {
            return 0.0D;
        }
        double before = active.boss.getHealth();
        heal(active.boss, amount);
        return active.boss.getHealth() - before;
    }

    public double consumePhaseGateDamage(UUID bossId) {
        ActiveBoss active = bossId == null ? null : bosses.get(bossId);
        if (active == null) return 0.0D;
        double damage = active.phaseGateDamage;
        active.phaseGateDamage = 0.0D;
        return damage;
    }

    private boolean spawnCombatMaster(Player player, boolean enforceWaveRequirement, boolean allWaveMinions,
                                      Consumer<Completion> completion) {
        Location spawn = player.getLocation().clone().add(0.0, 6.0, 0.0);
        UUID bossId = spawnCombatMasterAt(spawn, allWaveMinions, completion);
        if (bossId == null) {
            return false;
        }
        updateBar(bosses.get(bossId));
        if (enforceWaveRequirement) {
            player.sendMessage(ChatColor.GREEN + "Combat Master spawned.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Combat Master force-spawned.");
        }
        return true;
    }

    private UUID spawnCombatMasterAt(Location spawn, boolean allWaveMinions, Consumer<Completion> completion) {
        WitherSkeleton boss = spawn.getWorld().spawn(spawn, WitherSkeleton.class, mob -> {
            mob.setCustomName(BOSS_NAME);
            mob.setCustomNameVisible(true);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            mob.setAI(true);
            mob.setGravity(true);
            mob.getEquipment().setHelmet(litBulbHelmet());
            mob.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            mob.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            mob.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
            mob.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
        });
        setStats(boss, 0.31, 10.0, 0.8);
        boss.setHealth(MAX_HP);

        BossBar bar = Bukkit.createBossBar(BOSS_NAME, BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.setVisible(false);
        ActiveBoss active = new ActiveBoss(boss, bar, spawn.clone());
        active.allWaveMinions = allWaveMinions;
        active.completion = completion;
        active.phase = Phase.BALLISTIC;
        active.introTicks = 40;
        resetCooldowns(active);
        applyPhase(active, true);
        announceIntro(active);
        bosses.put(boss.getUniqueId(), active);
        plugin.getLogger().info("[CombatMaster] Spawned boss=" + boss.getUniqueId()
            + " phase=" + active.phase.name() + " bossbar_players=" + active.bar.getPlayers().size());

        startController(active);
        scheduleBossBarRefresh(active, 10L);
        scheduleBossBarRefresh(active, 40L);

        return boss.getUniqueId();
    }

    public boolean maintainCombatMaster(UUID bossId) {
        ActiveBoss active = bosses.get(bossId);
        if (active == null) return false;
        if (active.boss.isDead() || !active.boss.isValid()) {
            cleanup(active, active.boss.isDead() ? Completion.DEFEATED : Completion.CANCELED);
            return false;
        }
        if (active.task == null || active.task.isCancelled()) {
            active.introTicks = 0;
            active.pauseTicks = 0;
            startController(active);
            plugin.getLogger().warning("[CombatMaster] Restarted missing controller boss=" + bossId);
        }
        if (active.transitionTicks >= 0) { updateBar(active); return true; }
        active.boss.setGravity(true);
        if (active.introTicks <= 0 && active.pauseTicks <= 0 && !active.boss.hasAI()) {
            active.boss.setAI(true);
            plugin.getLogger().warning("[CombatMaster] Restored disabled AI boss=" + bossId
                + " phase=" + active.phase.name());
        }
        Player target = nearestPlayer(active.boss.getLocation(), BOSS_TARGET_RANGE);
        if (target != null && active.boss.getTarget() != target) active.boss.setTarget(target);
        advanceOnePhaseIfNeeded(active);
        updateBar(active);
        if (active.controllerFailures > 0 && active.controllerTicks == active.lastMaintainedControllerTicks) {
            plugin.getLogger().warning("[CombatMaster] Controller heartbeat stalled boss=" + bossId
                + " failures=" + active.controllerFailures);
        }
        active.lastMaintainedControllerTicks = active.controllerTicks;
        return true;
    }

    public boolean freezeCombatMaster(UUID bossId, int ticks) {
        ActiveBoss active = bossId == null ? null : bosses.get(bossId);
        if (active == null || !active.boss.isValid() || active.boss.isDead()) {
            return false;
        }
        active.introTicks = Math.max(active.introTicks, Math.max(0, ticks));
        active.boss.setAI(false);
        active.boss.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        return true;
    }

    private void startController(ActiveBoss active) {
        active.task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    tick(active);
                    active.controllerTicks++;
                    active.controllerFailures = 0;
                } catch (RuntimeException exception) {
                    if (active.transitionTicks >= 0) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "[CombatMaster] Transition failed; canceling encounter boss=" + active.boss.getUniqueId(), exception);
                        removeCombatMaster(active.boss.getUniqueId());
                        return;
                    }
                    active.controllerFailures++;
                    active.introTicks = 0;
                    active.pauseTicks = 0;
                    active.boss.setGravity(true);
                    active.boss.setAI(true);
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[CombatMaster] Controller tick failed boss=" + active.boss.getUniqueId()
                            + " phase=" + active.phase.name() + " intro=" + active.introTicks
                            + " pause=" + active.pauseTicks,
                        exception);
                }
            }
        }.runTaskTimer(plugin, 0L, TICK_STEP);
    }

    public boolean resetNearestCombatMaster(Player player) {
        ActiveBoss active = nearest(player.getLocation(), 128.0);
        if (active == null) return false;
        if (active.boss.isValid()) active.boss.remove();
        cleanup(active, Completion.CANCELED);
        return true;
    }

    public boolean forceNearestPhase(Player player, String rawPhase) {
        Phase desired = Phase.fromKey(rawPhase);
        ActiveBoss active = nearest(player.getLocation(), 128.0);
        if (desired == null || active == null) return false;
        double hp = switch (desired) {
            case BALLISTIC -> 190.0;
            case GUARDIAN -> 140.0;
            case MONK -> 90.0;
            case MASTERY -> 40.0;
        };
        active.boss.setHealth(Math.min(maxHealth(active.boss), hp));
        active.phase = desired;
        resetCooldowns(active);
        applyPhase(active, false);
        return true;
    }

    public List<String> debugNearest(Player player) {
        ActiveBoss active = nearest(player.getLocation(), 128.0);
        if (active == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("Phase=" + active.phase.display + " (" + active.phase.name().toLowerCase(Locale.ROOT) + ")");
        lines.add("Health=" + String.format(Locale.US, "%.1f/%.1f", active.boss.getHealth(), maxHealth(active.boss)));
        lines.add("Target=" + (active.boss.getTarget() instanceof Player p ? p.getName() : "none"));
        lines.add("Cooldowns A/B/C/D/E=" + active.cdA + "/" + active.cdB + "/" + active.cdC + "/" + active.cdD + "/" + active.cdE);
        lines.add("BulwarkTicks=" + active.bulwarkTicks + ", GuardianCycle=" + active.guardianCycle);
        lines.add("Meditation active=" + active.meditationActive + ", ticks=" + active.meditatingTicks + ", dmg=" + active.meditationDamage);
        lines.add("Omega cooldown/windup=" + active.omegaCooldown + "/" + active.omegaWindupTicks);
        lines.add("Anchors=" + active.anchorIds.size() + ", Sentinels=" + active.sentinelIds.size());
        lines.add("IntroTicks=" + active.introTicks + ", PauseTicks=" + active.pauseTicks);
        lines.add("AI=" + active.boss.hasAI() + ", Gravity=" + active.boss.hasGravity()
            + ", Controller=" + (active.task != null && !active.task.isCancelled()));
        lines.add("ControllerTicks=" + active.controllerTicks + ", Failures=" + active.controllerFailures
            + ", BossBarPlayers=" + active.bar.getPlayers().size() + ", Visible=" + active.bar.isVisible());
        lines.add("Location=" + active.boss.getLocation().getBlockX() + ","
            + active.boss.getLocation().getBlockY() + "," + active.boss.getLocation().getBlockZ());
        return lines;
    }

    public void shutdown() {
        for (ActiveBoss active : new ArrayList<>(bosses.values())) {
            if (active.boss.isValid()) active.boss.remove();
            cleanup(active, Completion.CANCELED);
        }
        for (UUID id : new ArrayList<>(pendingRewards.keySet())) releaseRewards(id);
    }

    private void tick(ActiveBoss a) {
        if (!a.boss.isValid() || a.boss.isDead()) {
            cleanup(a, a.boss.isDead() ? Completion.DEFEATED : Completion.CANCELED);
            return;
        }
        updateBar(a);
        if (a.transitionTicks >= 0) { tickTransition(a); return; }
        if (a.introTicks > 0) {
            a.boss.setVelocity(new Vector(0.0, -0.11, 0.0));
            a.boss.getWorld().spawnParticle(Particle.TRIAL_OMEN, a.boss.getLocation().add(0.0, 1.0, 0.0), 8, 0.4, 0.2, 0.4, 0.01);
            a.introTicks = Math.max(0, a.introTicks - TICK_STEP);
            if (a.introTicks == 0) {
                a.boss.setGravity(true);
                a.boss.setAI(true);
                summonPhaseMinions(a, Phase.BALLISTIC);
                a.boss.getWorld().playSound(a.boss.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.2f, 0.8f);
            }
            return;
        }
        if (a.pauseTicks > 0) {
            a.boss.setVelocity(new Vector(0.0, 0.0, 0.0));
            a.pauseTicks = Math.max(0, a.pauseTicks - TICK_STEP);
            if (a.pauseTicks == 0) a.boss.setAI(true);
            return;
        }

        Player target = nearestPlayer(a.boss.getLocation(), BOSS_TARGET_RANGE);
        if (target != null) a.boss.setTarget(target);

        advanceOnePhaseIfNeeded(a);

        dec(a);
        if (target == null) return;

        switch (a.phase) {
            case BALLISTIC -> runBallistic(a, target);
            case GUARDIAN -> runGuardian(a, target);
            case MONK -> runMonk(a, target);
            case MASTERY -> runMastery(a, target);
        }
    }

    private void runBallistic(ActiveBoss a, Player t) {
        if (a.cdA <= 0) { shootVolley(a, t); a.cdA = 80; }
        if (a.cdB <= 0) { placeAnchor(a, t.getLocation()); a.cdB = 400; }
        if (a.cdC <= 0 && !playersInRange(a.boss.getLocation(), 4.5).isEmpty()) { concussiveBurst(a); a.cdC = 180; }
    }

    private void runGuardian(ActiveBoss a, Player t) {
        if (a.sentinelIds.isEmpty()) summonSentinels(a, 2);
        if (a.guardianCycle <= 0) { a.bulwarkTicks = 160; a.guardianCycle = 320; }
        if (a.cdA <= 0) { shieldBash(a, t); a.cdA = 160; }
    }

    private void runMonk(ActiveBoss a, Player t) {
        if (a.meditationActive) {
            a.boss.setVelocity(new Vector(0.0, 0.0, 0.0));
            a.boss.getWorld().spawnParticle(Particle.ENCHANT, a.boss.getLocation().add(0.0, 1.0, 0.0), 14, 0.4, 0.3, 0.4, 0.03);
            return;
        }
        if (a.cdA <= 0) { phantomStep(a, t); a.cdA = 70; }
        if (a.cdB <= 0 && isValidMeleeTarget(a, t)) { palmBarrage(a, t); a.cdB = 100; }
        if (a.cdD <= 0) { chiDiscs(a, t); a.cdD = 120; }
        if (a.cdC <= 0) { startMeditation(a); a.cdC = 300; }
    }

    private void runMastery(ActiveBoss a, Player t) {
        if (a.guardianCycle <= 0) { a.bulwarkTicks = 80; a.guardianCycle = 260; }
        if (a.cdSentinel <= 0 && a.sentinelIds.isEmpty()) { summonSentinels(a, 1); a.cdSentinel = 280; }
        if (a.cdA <= 0) { shootVolley(a, t); a.cdA = 140; }
        if (a.cdB <= 0) { phantomStep(a, t); a.cdB = 60; }
        if (a.cdC <= 0) { placeAnchor(a, t.getLocation()); a.cdC = 320; }
        if (a.cdD <= 0) { chiDiscs(a, t); a.cdD = 150; }
        if (a.cdE <= 0) { venomArc(a); a.cdE = 170; }
        if (a.omegaCooldown <= 0 && a.omegaWindupTicks <= 0) { startOmegaSlam(a); a.omegaCooldown = 600; }
    }

    private void onTransition(ActiveBoss a, Phase from, Phase to) {
        if (a.encounterPlayers != null) {
            beginTransition(a, from, to);
            return;
        }
        if (from == Phase.BALLISTIC) detonateAnchors(a);
        if (from == Phase.GUARDIAN) { clearSentinels(a); a.bulwarkTicks = 0; igniteFloor(a.boss.getLocation(), 5); }
        if (from == Phase.MONK && to == Phase.MASTERY) {
            a.boss.setAI(false);
            a.pauseTicks = 30;
            a.boss.getWorld().spawnParticle(Particle.OMINOUS_SPAWNING, a.boss.getLocation().add(0.0, 1.0, 0.0), 40, 0.8, 0.8, 0.8, 0.02);
        }
        announceFormTransition(a, to);
    }

    private void beginTransition(ActiveBoss a, Phase from, Phase to) {
        a.transitionTicks = 0;
        a.transitionStartedNanos = System.nanoTime();
        a.transitionMaxTickNanos = 0;
        a.transitionFrom = from;
        a.transitionTo = to;
        a.denseCylinder = plugin.getConfig().getBoolean("combat-master.transition.dense-skull-test", false);
        a.cylinderBuildNanos = 0;
        a.cylinderUpdateNanos = 0;
        a.cylinderCleanupNanos = 0;
        a.cylinderDissipateNanos = 0;
        a.dissolvedRows = 0;
        a.blueSkullCount = 0;
        for (BukkitRunnable task : new ArrayList<>(a.pendingVolleyTasks)) task.cancel();
        a.pendingVolleyTasks.clear();
        for (UUID id : a.projectileIds) {
            Entity projectile = Bukkit.getEntity(id);
            if (projectile != null) projectile.remove();
        }
        a.projectileIds.clear();
        for (UUID id : a.cloudIds) {
            Entity cloud = Bukkit.getEntity(id);
            if (cloud != null) cloud.remove();
        }
        a.cloudIds.clear();
        clearMinions(a);
        for (UUID id : a.anchorIds) {
            Entity anchor = Bukkit.getEntity(id);
            if (anchor != null) anchor.remove();
        }
        a.anchorIds.clear();
        a.omegaWindupTicks = 0;
        a.meditationActive = false;
        a.meditatingTicks = 0;
        a.boss.setTarget(null);
        a.boss.setAI(false);
        a.boss.setInvulnerable(true);
        a.boss.setVelocity(new Vector(0, 0, 0));
        a.boss.teleport(a.arenaSpawn);
        for (Player player : viewers(a)) player.playSound(a.arenaSpawn, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.65f, 1.7f);
        plugin.getLogger().info("[CombatMaster] Cutscene started boss=" + a.boss.getUniqueId()
            + " from=" + from + " to=" + to + " duration_ticks=" + TRANSITION_TICKS);
    }

    private void tickTransition(ActiveBoss a) {
        long effectTickStarted = System.nanoTime();
        int tick = a.transitionTicks;
        if (tick >= TRANSITION_TICKS
            || System.nanoTime() - a.transitionStartedNanos >= TRANSITION_WALL_CAP_NANOS) {
            finishTransition(a);
            return;
        }
        a.boss.setTarget(null);
        a.boss.setVelocity(new Vector(0, 0, 0));
        Location center = a.arenaSpawn;
        if (tick < 40) {
            if (a.denseCylinder) sphericalPulse(a, tick);
            else legacyGroundPulse(a, tick);
        }
        if (tick >= 40 && tick < 200) {
            int smokeCount = a.denseCylinder && tick >= DENSE_DISSIPATE_START
                ? Math.max(0, (int) Math.ceil(18.0 * (DENSE_DISSIPATE_END - tick)
                    / (DENSE_DISSIPATE_END - DENSE_DISSIPATE_START))) : 18;
            if (smokeCount > 0) for (Player player : viewers(a))
                player.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE,
                    center.clone().add(0, 1.5, 0), smokeCount, 1.6, 1.8, 1.6, 0.015);
            if (tick % 10 == 0) {
                Phase shown = (tick / 10) % 2 == 0 ? a.transitionFrom : a.transitionTo;
                a.bar.setTitle(BOSS_NAME + ChatColor.GRAY + " - " + shown.display);
                a.bar.setColor(phaseColor(shown));
            }
        }
        if (tick == 80) distributeSupplies(a);
        if (a.denseCylinder && tick >= 80 && tick <= 95) {
            try {
                stageDenseCylinder(a, (tick - 80) / TICK_STEP);
            } catch (RuntimeException ex) {
                clearCylinder(a);
                a.denseCylinder = false;
                plugin.getLogger().warning("[CombatMaster] Dense cylinder staging failed boss="
                    + a.boss.getUniqueId() + ": " + ex);
            }
        }
        if (tick == 100 && !a.denseCylinder) {
            try {
                spawnCylinder(a);
            } catch (RuntimeException ex) {
                clearCylinder(a);
                plugin.getLogger().warning("[CombatMaster] Optional cutscene cylinder failed boss="
                    + a.boss.getUniqueId() + ": " + ex);
            }
        }
        if (a.denseCylinder) {
            if (tick == 105) {
                riseDenseCylinder(a);
                startEmergenceEffects(a);
            }
            if (tick == 145) continueDenseCylinderRise(a);
            if (tick >= DENSE_DISSIPATE_START && tick <= DENSE_DISSIPATE_END)
                dissipateDenseCylinder(a, tick);
            if (tick == 160) clearEmergenceEffects(a);
        } else if (tick >= 100 && tick <= 205) {
            moveCylinder(a, tick);
        }
        if (tick == 210) {
            clearCylinder(a);
            a.bar.setTitle(BOSS_NAME + ChatColor.GRAY + " - " + a.transitionTo.display);
            a.bar.setColor(phaseColor(a.transitionTo));
        }
        a.transitionTicks += TICK_STEP;
        a.transitionMaxTickNanos = Math.max(a.transitionMaxTickNanos, System.nanoTime() - effectTickStarted);
        if (a.transitionTicks >= TRANSITION_TICKS) finishTransition(a);
    }

    private void legacyGroundPulse(ActiveBoss a, int tick) {
        double radius = 0.5 + tick * 0.19;
        for (int i = 0; i < 32; i++) {
            double angle = 2 * Math.PI * i / 32;
            Location at = a.arenaSpawn.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius);
            for (Player player : viewers(a)) player.spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0, 0, 0, 0);
        }
    }

    private void sphericalPulse(ActiveBoss a, int tick) {
        double radius = 0.5 + tick * 0.19;
        Location origin = a.arenaSpawn.clone().add(0, 1.5, 0);
        List<Player> audience = viewers(a);
        for (int i = 0; i < SPHERICAL_PULSE_POINTS; i++) {
            double vertical = 1.0 - 2.0 * (i + 0.5) / SPHERICAL_PULSE_POINTS;
            double horizontal = Math.sqrt(1.0 - vertical * vertical);
            double angle = i * Math.PI * (3.0 - Math.sqrt(5.0)) + tick * 0.03;
            Location at = origin.clone().add(radius * horizontal * Math.cos(angle),
                radius * vertical, radius * horizontal * Math.sin(angle));
            for (Player player : audience) player.spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0, 0, 0, 0);
        }
    }

    private BarColor phaseColor(Phase phase) {
        return switch (phase) {
            case BALLISTIC -> BarColor.BLUE;
            case GUARDIAN -> BarColor.YELLOW;
            case MONK -> BarColor.GREEN;
            case MASTERY -> BarColor.PURPLE;
        };
    }

    private void distributeSupplies(ActiveBoss a) {
        int apples = Math.max(0, plugin.getConfig().getInt("combat-master.transition.golden-apples", 8));
        int arrows = Math.max(0, plugin.getConfig().getInt("combat-master.transition.arrows", 8));
        int recipients = 0;
        for (Player player : viewers(a)) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            SupplyGrant givenApples = giveSupply(player, Material.GOLDEN_APPLE, apples);
            SupplyGrant givenArrows = giveSupply(player, Material.ARROW, arrows);
            int dropped = givenApples.dropped() + givenArrows.dropped();
            if (plugin instanceof HGLootPlugin hydra && hydra.bossRushService() != null) {
                hydra.bossRushService().holdDamageActionBar(player, 2500L);
            }
            player.sendActionBar(net.kyori.adventure.text.Component.text("Supplies: +" + givenApples.total()
                + " Golden Apples, +" + givenArrows.total() + " Arrows"
                + (dropped > 0 ? " (" + dropped + " dropped at your feet)" : "")));
            recipients++;
        }
        plugin.getLogger().info("[CombatMaster] Cutscene supplies boss=" + a.boss.getUniqueId()
            + " recipients=" + recipients + " apples_each=" + apples + " arrows_each=" + arrows);
    }

    private SupplyGrant giveSupply(Player player, Material material, int count) {
        int dropped = 0;
        for (int remaining = count; remaining > 0; remaining -= Math.min(64, remaining)) {
            ItemStack stack = new ItemStack(material, Math.min(64, remaining));
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                dropped += leftover.getAmount();
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        if (dropped > 0) {
            player.sendMessage(ChatColor.YELLOW + "Full inventory: excess "
                + material.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " dropped at your feet.");
        }
        return new SupplyGrant(count, dropped);
    }

    private void spawnCylinder(ActiveBoss a) {
        for (int row = 0; row < CYLINDER_ROWS; row++) for (int column = 0; column < CYLINDER_COLUMNS; column++) {
            double angle = 2 * Math.PI * column / CYLINDER_COLUMNS;
            Location at = a.arenaSpawn.clone().add(Math.cos(angle) * 6, -5 + row * 4, Math.sin(angle) * 6);
            at.setYaw((float) Math.toDegrees(angle) + 90);
            ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
                entity.setItemStack(new ItemStack(Material.WITHER_SKELETON_SKULL));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(5.5f, 5.5f, 5.5f), new Quaternionf()));
                entity.setTeleportDuration(TICK_STEP);
                entity.setPersistent(false);
                entity.setGravity(false);
            });
            a.cylinderIds.add(display.getUniqueId());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (a.encounterPlayers == null || !a.encounterPlayers.contains(player.getUniqueId()))
                    player.hideEntity(plugin, display);
            }
        }
        plugin.getLogger().info("[CombatMaster] Cutscene cylinder boss=" + a.boss.getUniqueId()
            + " item_displays=" + a.cylinderIds.size() + " radius=6 height=24");
    }

    private void stageDenseCylinder(ActiveBoss a, int batch) {
        long started = System.nanoTime();
        int startRow = batch * DENSE_BATCH_ROWS;
        ItemStack blackSkull = new ItemStack(Material.WITHER_SKELETON_SKULL);
        ItemStack blueSkull = new ItemStack(Material.WITHER_SKELETON_SKULL);
        ItemMeta blueMeta = blueSkull.getItemMeta();
        blueMeta.setItemModel(BLUE_SKULL_MODEL);
        blueSkull.setItemMeta(blueMeta);
        for (int row = startRow; row < startRow + DENSE_BATCH_ROWS; row++) {
            for (int column = 0; column < DENSE_COLUMNS; column++) {
                boolean blue = isBlueSkull(row, column);
                Location at = denseSkullLocation(a.arenaSpawn, row, column, blue);
                ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
                    entity.setItemStack(blue ? blueSkull : blackSkull);
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    entity.setTransformation(new Transformation(new Vector3f(0, DENSE_START_Y, 0),
                        new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
                    entity.setInterpolationDelay(0);
                    entity.setInterpolationDuration(0);
                    entity.setDisplayWidth(1.0f);
                    // The render bounds must include both the -24 staging offset and +24 reveal travel.
                    entity.setDisplayHeight(50.0f);
                    entity.setVisibleByDefault(false);
                    entity.setPersistent(false);
                    entity.setGravity(false);
                });
                if (blue) a.blueSkullCount++;
                a.cylinderIds.add(display.getUniqueId());
                for (Player player : viewers(a)) player.showEntity(plugin, display);
            }
        }
        a.cylinderBuildNanos += System.nanoTime() - started;
        plugin.getLogger().info("[CombatMaster] Dense cylinder batch boss=" + a.boss.getUniqueId()
            + " batch=" + (batch + 1) + "/4 displays=" + a.cylinderIds.size()
            + " build_ms=" + ((System.nanoTime() - started) / 1_000_000L));
    }

    static boolean isBlueSkull(int row, int column) {
        return (column * 5 + row * 3) % 9 == 0;
    }

    static Location denseSkullLocation(Location center, int row, int column) {
        double angle = 2 * Math.PI * (column + (row % 2) * 0.5) / DENSE_COLUMNS;
        Location at = center.clone().add(Math.cos(angle) * CYLINDER_RADIUS,
            row * DENSE_ROW_SPACING, Math.sin(angle) * CYLINDER_RADIUS);
        // Bukkit yaw: east=-90, south=0. The skull front points away from the center.
        at.setYaw((float) Math.toDegrees(angle) - 90.0f);
        return at;
    }

    static Location denseSkullLocation(Location center, int row, int column, boolean blue) {
        Location at = denseSkullLocation(center, row, column);
        // The blue cube's textured north face is opposite the special vanilla skull
        // renderer's apparent front after their respective FIXED item transforms.
        // Use the same world position but reverse only the blue display's yaw.
        if (blue) at.setYaw(at.getYaw() + 180.0f);
        return at;
    }

    private void riseDenseCylinder(ActiveBoss a) {
        transformDenseCylinder(a, 0.0f, DENSE_EMERGE_TICKS);
        plugin.getLogger().info("[CombatMaster] Dense cylinder rising boss=" + a.boss.getUniqueId()
            + " item_displays=" + a.cylinderIds.size() + " scale=1 radius=" + CYLINDER_RADIUS
            + " rows=" + DENSE_ROWS + " columns=" + DENSE_COLUMNS + " height=24"
            + " blue=" + a.blueSkullCount);
    }

    private void continueDenseCylinderRise(ActiveBoss a) {
        transformDenseCylinder(a, DENSE_EXTRA_RISE, DENSE_ASCEND_TICKS);
    }

    private void dissipateDenseCylinder(ActiveBoss a, int tick) {
        long started = System.nanoTime();
        int targetRows = Math.min(DENSE_ROWS, (tick - DENSE_DISSIPATE_START + TICK_STEP)
            * DENSE_ROWS / (DENSE_DISSIPATE_END - DENSE_DISSIPATE_START + TICK_STEP));
        List<Player> audience = viewers(a);
        double traveled = DENSE_EXTRA_RISE * (tick - 145.0) / DENSE_ASCEND_TICKS;
        for (int row = a.dissolvedRows; row < targetRows; row++) {
            for (int column = 0; column < DENSE_COLUMNS; column++) {
                UUID id = a.cylinderIds.get(row * DENSE_COLUMNS + column);
                Entity display = Bukkit.getEntity(id);
                if (display != null) display.remove();
                if (column % 8 == 0) {
                    Location at = denseSkullLocation(a.arenaSpawn, row, column).add(0, traveled, 0);
                    for (Player player : audience) player.spawnParticle(Particle.SOUL, at, 1, 0, 0, 0, 0);
                }
            }
        }
        a.dissolvedRows = targetRows;
        a.cylinderDissipateNanos += System.nanoTime() - started;
        if (tick == DENSE_DISSIPATE_END) plugin.getLogger().info("[CombatMaster] Dense cylinder dissolved boss="
            + a.boss.getUniqueId() + " rows=" + a.dissolvedRows + " displays_remaining=0"
            + " dissolve_ms=" + (a.cylinderDissipateNanos / 1_000_000L));
    }

    private void transformDenseCylinder(ActiveBoss a, float y, int duration) {
        long started = System.nanoTime();
        for (UUID id : a.cylinderIds) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof ItemDisplay display)) continue;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(new Transformation(new Vector3f(0, y, 0),
                new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
        }
        a.cylinderUpdateNanos += System.nanoTime() - started;
    }

    private void startEmergenceEffects(ActiveBoss a) {
        for (Player player : viewers(a)) {
            if (!isNearbyLivingParticipant(a, player)) continue;
            if (!player.hasPotionEffect(PotionEffectType.DARKNESS)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 55, 0, false, false, false));
                a.darknessRecipients.add(player.getUniqueId());
            }
        }
    }

    private boolean isNearbyLivingParticipant(ActiveBoss a, Player player) {
        return !player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
            && player.getLocation().distanceSquared(a.arenaSpawn) <= 64.0 * 64.0;
    }

    private void clearEmergenceEffects(ActiveBoss a) {
        for (UUID id : a.darknessRecipients) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            PotionEffect darkness = player.getPotionEffect(PotionEffectType.DARKNESS);
            if (darkness != null && darkness.getAmplifier() == 0 && darkness.getDuration() <= 55)
                player.removePotionEffect(PotionEffectType.DARKNESS);
        }
        a.darknessRecipients.clear();
    }

    private void moveCylinder(ActiveBoss a, int tick) {
        double rise = tick < 160 ? (tick - 100) / 60.0 * 5 : tick < 185 ? 5 : Math.max(0, 5 - (tick - 185) / 20.0 * 5);
        int index = 0;
        for (UUID id : a.cylinderIds) {
            Entity display = Bukkit.getEntity(id);
            int row = index / CYLINDER_COLUMNS, column = index % CYLINDER_COLUMNS;
            double angle = 2 * Math.PI * column / CYLINDER_COLUMNS;
            if (display != null) {
                Location at = a.arenaSpawn.clone().add(Math.cos(angle) * 6, -5 + row * 4 + rise, Math.sin(angle) * 6);
                at.setYaw((float) Math.toDegrees(angle) + 90);
                display.teleport(at);
            }
            index++;
        }
    }

    private void clearCylinder(ActiveBoss a) {
        long started = System.nanoTime();
        for (UUID id : a.cylinderIds) {
            Entity display = Bukkit.getEntity(id);
            if (display != null) display.remove();
        }
        a.cylinderIds.clear();
        a.cylinderCleanupNanos += System.nanoTime() - started;
        clearEmergenceEffects(a);
    }

    private void finishTransition(ActiveBoss a) {
        if (a.transitionTicks < 0) return;
        int elapsedTicks = a.transitionTicks;
        clearCylinder(a);
        a.phase = a.transitionTo;
        applyPhase(a, false);
        resetCooldowns(a);
        summonPhaseMinions(a, a.phase);
        a.pauseTicks = TRANSITION_RECOVERY_TICKS;
        a.boss.setInvulnerable(false);
        a.transitionTicks = -1;
        for (Player player : viewers(a)) {
            player.playSound(a.arenaSpawn, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
            player.playSound(a.arenaSpawn, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.8f);
        }
        announceFormTransition(a, a.phase);
        plugin.getLogger().info("[CombatMaster] Cutscene complete boss=" + a.boss.getUniqueId()
            + " phase=" + a.phase + " duration_ticks=" + elapsedTicks
            + " elapsed_ms=" + ((System.nanoTime() - a.transitionStartedNanos) / 1_000_000L)
            + " max_effect_tick_ms=" + (a.transitionMaxTickNanos / 1_000_000L));
        if (a.denseCylinder) plugin.getLogger().info("[CombatMaster] Dense cylinder timing boss="
            + a.boss.getUniqueId() + " build_ms=" + (a.cylinderBuildNanos / 1_000_000L)
            + " update_ms=" + (a.cylinderUpdateNanos / 1_000_000L)
            + " dissolve_ms=" + (a.cylinderDissipateNanos / 1_000_000L)
            + " cleanup_ms=" + (a.cylinderCleanupNanos / 1_000_000L));
    }

    private void applyPhase(ActiveBoss a, boolean intro) {
        switch (a.phase) {
            case BALLISTIC -> { a.bar.setColor(BarColor.BLUE); a.boss.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW)); setStats(a.boss, 0.31, 10.0, 0.8); }
            case GUARDIAN -> { a.bar.setColor(BarColor.YELLOW); a.boss.getEquipment().setItemInMainHand(new ItemStack(Material.SHIELD)); setStats(a.boss, 0.29, 12.0, 0.9); }
            case MONK -> { a.bar.setColor(BarColor.GREEN); a.boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD)); setStats(a.boss, 0.38, 9.0, 0.8); }
            case MASTERY -> { a.bar.setColor(BarColor.PURPLE); a.boss.getEquipment().setItemInMainHand(new ItemStack(Material.MACE)); setStats(a.boss, 0.35, 11.0, 0.9); }
        }
        a.bar.setTitle(BOSS_NAME + ChatColor.GRAY + " - " + a.phase.display);
        if (!intro) a.boss.getWorld().playSound(a.boss.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.1f);
    }

    private void announceIntro(ActiveBoss a) {
        speakToNearby(a, ChatColor.GRAY + "Show me your skill.");
    }

    private void announceFormTransition(ActiveBoss a, Phase to) {
        String subtitle = switch (to) {
            case BALLISTIC -> "Artillery Form";
            case GUARDIAN -> "Bulwark Form";
            case MONK -> "Discipline Form";
            case MASTERY -> "Final Lesson";
        };
        String line = switch (to) {
            case BALLISTIC -> "Survive the barrage.";
            case GUARDIAN -> "Break your enemy, or be broken.";
            case MONK -> "Precision defeats panic.";
            case MASTERY -> "Your final lesson begins now.";
        };
        for (Player player : a.encounterPlayers == null
            ? playersNearBossForAnnouncement(a.boss.getLocation()) : viewers(a)) {
            player.sendTitle(ChatColor.AQUA + "COMBAT MASTER", ChatColor.YELLOW + subtitle, 5, 45, 10);
            player.sendMessage(ChatColor.GRAY + line);
        }
    }

    private void speakToNearby(ActiveBoss a, String line) {
        for (Player player : a.encounterPlayers == null
            ? playersNearBossForAnnouncement(a.boss.getLocation()) : viewers(a)) {
            player.sendMessage(ChatColor.AQUA + line);
        }
    }

    private List<Player> playersNearBossForAnnouncement(Location location) {
        List<Player> result = new ArrayList<>();
        World world = location.getWorld();
        if (world == null) {
            return result;
        }
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            if (Math.abs(loc.getX() - location.getX()) <= FORM_ANNOUNCE_RADIUS_XZ
                && Math.abs(loc.getZ() - location.getZ()) <= FORM_ANNOUNCE_RADIUS_XZ) {
                result.add(player);
            }
        }
        return result;
    }

    private void resetCooldowns(ActiveBoss a) {
        a.cdA = 30; a.cdB = 70; a.cdC = 110; a.cdD = 90; a.cdE = 130; a.cdSentinel = 0; a.guardianCycle = 0;
        a.omegaCooldown = Math.max(a.omegaCooldown, 120);
    }

    private void dec(ActiveBoss a) {
        a.cdA = Math.max(0, a.cdA - TICK_STEP); a.cdB = Math.max(0, a.cdB - TICK_STEP); a.cdC = Math.max(0, a.cdC - TICK_STEP);
        a.cdD = Math.max(0, a.cdD - TICK_STEP); a.cdE = Math.max(0, a.cdE - TICK_STEP); a.cdSentinel = Math.max(0, a.cdSentinel - TICK_STEP);
        a.guardianCycle = Math.max(0, a.guardianCycle - TICK_STEP); a.bulwarkTicks = Math.max(0, a.bulwarkTicks - TICK_STEP);
        a.omegaCooldown = Math.max(0, a.omegaCooldown - TICK_STEP);
        if (a.meditationActive) {
            int before = a.meditatingTicks;
            a.meditatingTicks = Math.max(0, a.meditatingTicks - TICK_STEP);
            if (before > 0 && a.meditatingTicks == 0) resolveMeditation(a);
        }
        if (a.omegaWindupTicks > 0) {
            int before = a.omegaWindupTicks;
            a.omegaWindupTicks = Math.max(0, a.omegaWindupTicks - TICK_STEP);
            if (before > 0 && a.omegaWindupTicks == 0) executeOmegaSlam(a);
        }
    }

    private void shootVolley(ActiveBoss a, Player t) {
        // Only the opt-in model test delays projectiles; normal V1 keeps its original timing.
        if (animateAttack(a)) {
            scheduleVolley(a, t, MODEL_VOLLEY_RELEASE_TICKS);
            return;
        }
        fireVolley(a, t);
    }

    private void scheduleVolley(ActiveBoss a, Player t, long delayTicks) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                a.pendingVolleyTasks.remove(this);
                if (canReleaseVolley(a, t)) fireVolley(a, t);
            }
        };
        a.pendingVolleyTasks.add(task);
        task.runTaskLater(plugin, delayTicks);
    }

    private boolean canReleaseVolley(ActiveBoss a, Player target) {
        return !a.cleanedUp && a.transitionTicks < 0 && bosses.get(a.boss.getUniqueId()) == a
            && a.task != null && !a.task.isCancelled()
            && a.boss.isValid() && !a.boss.isDead()
            && target != null && target.isOnline() && !target.isDead()
            && target.getWorld() == a.boss.getWorld();
    }

    private void fireVolley(ActiveBoss a, Player t) {
        Location eye = a.boss.getEyeLocation();
        Vector base = t.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        for (double offset : new double[] {-0.22, -0.10, 0.0, 0.10, 0.22}) {
            Vector shot = base.clone().add(new Vector(offset, 0.02, offset)).normalize();
            WitherSkull skull = a.boss.launchProjectile(WitherSkull.class, shot.multiply(1.3));
            skull.setCharged(false);
            skull.setYield(1.0f);
            skull.setIsIncendiary(false);
            a.projectileIds.add(skull.getUniqueId());
        }
        a.boss.getWorld().playSound(eye, Sound.ENTITY_SKELETON_SHOOT, 1.2f, 0.9f);
    }

    private void placeAnchor(ActiveBoss a, Location target) {
        Location loc = target.getBlock().getLocation().add(0.5, 0.1, 0.5);
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> { as.setInvisible(true); as.setMarker(true); as.setSmall(true); as.setGravity(false); });
        a.anchorIds.add(stand.getUniqueId());
        stand.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.5, 0.2, 0.5, 0.01);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!stand.isValid()) { a.anchorIds.remove(stand.getUniqueId()); return; }
            if (!playersInRange(stand.getLocation(), 3.2).isEmpty()) explodeAnchor(a, stand.getLocation(), 2.6f);
            stand.remove(); a.anchorIds.remove(stand.getUniqueId());
        }, 60L);
    }

    private void detonateAnchors(ActiveBoss a) {
        for (UUID id : new ArrayList<>(a.anchorIds)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof ArmorStand stand && stand.isValid()) { explodeAnchor(a, stand.getLocation(), 2.8f); stand.remove(); }
        }
        a.anchorIds.clear();
    }

    private void explodeAnchor(ActiveBoss active, Location location, float power) {
        // Explosion damage events are synchronous. Keep source identity scoped to this detonation,
        // so boss skulls and every other explosion retain their normal damage behavior.
        AnchorExplosionContext previous = activeAnchorExplosion;
        activeAnchorExplosion = new AnchorExplosionContext(active.boss.getUniqueId(), active.boss.getWorld(),
            active.encounterPlayers == null ? Set.of() : Set.copyOf(active.encounterPlayers));
        try {
            location.getWorld().createExplosion(location, power, false, true, active.boss);
        } finally {
            activeAnchorExplosion = previous;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnchorExplosionDamage(EntityDamageEvent event) {
        AnchorExplosionContext context = activeAnchorExplosion;
        if (context != null && context.matches(event)) capAnchorFinalDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void traceSuspiciousParticipantDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0.0D) return;
        for (ActiveBoss active : bosses.values()) {
            if (active.encounterPlayers == null || !active.encounterPlayers.contains(player.getUniqueId())
                || player.getWorld() != active.boss.getWorld()) continue;
            Entity direct = event.getDamageSource().getDirectEntity();
            Entity causing = event.getDamageSource().getCausingEntity();
            String source = direct == null ? "none" : direct.getType() + ":" + direct.getUniqueId();
            String attacker = causing == null ? "none" : causing.getType() + ":" + causing.getUniqueId();
            String held = causing instanceof LivingEntity living && living.getEquipment() != null
                ? living.getEquipment().getItemInMainHand().getType().name() : "none";
            String attack = activeAnchorExplosion != null && activeAnchorExplosion.matches(event) ? "anchor"
                : direct != null && active.projectileIds.contains(direct.getUniqueId()) ? "tracked_skull"
                : direct != null && direct.getUniqueId().equals(active.boss.getUniqueId()) ? "boss_direct"
                : direct != null && active.sentinelIds.contains(direct.getUniqueId()) ? "sentinel"
                : "other";
            String hit = "time=" + System.currentTimeMillis() + " tick=" + Bukkit.getCurrentTick()
                + " victim=" + player.getUniqueId()
                + " phase=" + active.phase + " attack=" + attack
                + " cause=" + event.getCause() + " type=" + event.getDamageSource().getDamageType()
                + " direct=" + source + " attacker=" + attacker + " held=" + held
                + " raw=" + event.getDamage() + " final=" + event.getFinalDamage()
                + " healthBefore=" + player.getHealth() + " bossFall=" + active.boss.getFallDistance();
            active.recentParticipantDamage.addLast(hit);
            if (active.recentParticipantDamage.size() > 8) active.recentParticipantDamage.removeFirst();
            if (isSuspiciousParticipantHit(event.getFinalDamage(), player.getHealth())
                && active.suspiciousDamageReports++ < MAX_SUSPICIOUS_DAMAGE_REPORTS_PER_BOSS) {
                plugin.getLogger().warning("[CombatMasterDamage] boss=" + active.boss.getUniqueId()
                    + " recent=" + String.join(" | ", active.recentParticipantDamage));
            }
            return;
        }
    }

    static boolean isSuspiciousParticipantHit(double finalDamage, double healthBefore) {
        return finalDamage >= 16.0D || finalDamage >= healthBefore;
    }

    static void capAnchorFinalDamage(EntityDamageEvent event) {
        if (event.getFinalDamage() <= MAX_ANCHOR_PARTICIPANT_DAMAGE) return;
        double upper = event.getDamage();
        event.setDamage(0.0D);
        if (event.getFinalDamage() > MAX_ANCHOR_PARTICIPANT_DAMAGE) {
            event.setCancelled(true);
            return;
        }
        double lower = 0.0D;
        for (int i = 0; i < 32; i++) {
            double middle = (lower + upper) / 2.0D;
            event.setDamage(middle);
            if (event.getFinalDamage() > MAX_ANCHOR_PARTICIPANT_DAMAGE) upper = middle;
            else lower = middle;
        }
        event.setDamage(lower);
    }

    record AnchorExplosionContext(UUID bossId, World world, Set<UUID> participantIds) {
        boolean matches(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player player) || player.getWorld() != world
                || !participantIds.contains(player.getUniqueId())) return false;
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return false;
            Entity direct = event.getDamageSource().getDirectEntity();
            return direct != null && direct.getUniqueId().equals(bossId);
        }
    }

    private void concussiveBurst(ActiveBoss a) {
        Location c = a.boss.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.9f, 1.2f);
        for (Player p : playersInRange(c, 4.5)) { p.damage(6.0, a.boss); p.setVelocity(p.getVelocity().add(p.getLocation().toVector().subtract(c.toVector()).normalize().multiply(1.05).setY(0.45))); }
    }

    private void summonSentinels(ActiveBoss a, int count) {
        World w = a.boss.getWorld(); Location c = a.boss.getLocation();
        for (int i = 0; i < count; i++) {
            Location s = c.clone().add(i % 2 == 0 ? 2.0 : -2.0, 0.0, 0.0);
            Skeleton sk = w.spawn(s, Skeleton.class, mob -> { mob.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE)); mob.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD)); });
            if (sk.getAttribute(Attribute.MAX_HEALTH) != null) sk.getAttribute(Attribute.MAX_HEALTH).setBaseValue(18.0);
            sk.setHealth(18.0); a.sentinelIds.add(sk.getUniqueId());
            a.minionIds.add(sk.getUniqueId());
        }
    }

    private void summonPhaseMinions(ActiveBoss a, Phase phase) {
        if (!a.allWaveMinions || !a.boss.isValid()) {
            return;
        }
        switch (phase) {
            case BALLISTIC -> summonArtilleryMinions(a, 2);
            case GUARDIAN -> {
                if (a.sentinelIds.isEmpty()) {
                    summonSentinels(a, 2);
                }
            }
            case MONK -> summonDisciplineMinions(a, 2);
            case MASTERY -> {
                summonArtilleryMinions(a, 1);
                summonSentinels(a, 1);
                summonDisciplineMinions(a, 1);
            }
        }
    }

    private void summonArtilleryMinions(ActiveBoss a, int count) {
        for (int i = 0; i < count; i++) {
            Location at = minionSpawn(a, i, count, 4.0D);
            Pillager minion = at.getWorld().spawn(at, Pillager.class, mob -> {
                mob.setCustomName(ChatColor.BLUE + "Artillery Disciple");
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
            });
            a.minionIds.add(minion.getUniqueId());
        }
    }

    private void summonDisciplineMinions(ActiveBoss a, int count) {
        for (int i = 0; i < count; i++) {
            Location at = minionSpawn(a, i, count, 3.5D);
            Vindicator minion = at.getWorld().spawn(at, Vindicator.class, mob -> {
                mob.setCustomName(ChatColor.GREEN + "Discipline Disciple");
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
                mob.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            });
            a.minionIds.add(minion.getUniqueId());
        }
    }

    private Location minionSpawn(ActiveBoss a, int index, int count, double radius) {
        double angle = (Math.PI * 2.0D * index / Math.max(1, count)) + (a.phase.ordinal() * 0.75D);
        return a.boss.getLocation().clone().add(Math.cos(angle) * radius, 0.25D, Math.sin(angle) * radius);
    }

    private void shieldBash(ActiveBoss a, Player t) {
        Vector dir = t.getLocation().toVector().subtract(a.boss.getLocation().toVector()).normalize();
        a.boss.setVelocity(dir.multiply(1.05).setY(0.25));
        if (t.getLocation().distanceSquared(a.boss.getLocation()) <= 9.0) { t.damage(7.0, a.boss); t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, false, true, true)); }
    }

    private void phantomStep(ActiveBoss a, Player t) {
        Vector dir = t.getLocation().toVector().subtract(a.boss.getLocation().toVector()).setY(0.0);
        if (dir.lengthSquared() < 0.0001) return;
        dir.normalize(); a.boss.teleport(t.getLocation().clone().add(dir.multiply(1.3)));
        t.damage(6.0, a.boss); t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, true, true));
        a.boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, t.getLocation().add(0.0, 1.0, 0.0), 8, 0.4, 0.2, 0.4, 0.01);
    }

    private void palmBarrage(ActiveBoss a, Player t) {
        damageMeleeTarget(a, t, 3.0D);
        Bukkit.getScheduler().runTaskLater(plugin, () -> damageMeleeTarget(a, t, 3.0D), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> damageMeleeTarget(a, t, 4.0D), 20L);
    }

    private void damageMeleeTarget(ActiveBoss active, Player target, double damage) {
        if (isValidMeleeTarget(active, target)) {
            target.damage(damage, active.boss);
        }
    }

    private boolean isValidMeleeTarget(ActiveBoss active, Player target) {
        return active != null
            && target != null
            && target.isOnline()
            && !target.isDead()
            && active.boss.isValid() && !active.cleanedUp && active.transitionTicks < 0
            && target.getWorld() == active.boss.getWorld()
            && target.getLocation().distanceSquared(active.boss.getLocation())
                <= MELEE_ABILITY_RANGE * MELEE_ABILITY_RANGE;
    }

    private void startMeditation(ActiveBoss a) {
        a.meditationActive = true; a.meditationBroken = false; a.meditationDamage = 0; a.meditatingTicks = 100;
        a.boss.getWorld().playSound(a.boss.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.7f);
    }

    private void resolveMeditation(ActiveBoss a) {
        if (!a.meditationActive) return;
        a.meditationActive = false; a.meditatingTicks = 0;
        if (!a.meditationBroken) { heal(a.boss, MEDITATION_HEAL); a.boss.getWorld().playSound(a.boss.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.8f); }
        else a.boss.getWorld().playSound(a.boss.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.4f);
    }

    private void chiDiscs(ActiveBoss a, Player t) {
        Location eye = a.boss.getEyeLocation();
        Vector base = t.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        for (double offset : new double[] {-0.25, 0.0, 0.25}) {
            Vector side = new Vector(-base.getZ(), 0.0, base.getX()).normalize().multiply(offset);
            WitherSkull skull = a.boss.launchProjectile(WitherSkull.class, base.clone().add(side).normalize().multiply(1.15));
            skull.setCharged(false);
            skull.setYield(0.8f);
            skull.setIsIncendiary(false);
            a.projectileIds.add(skull.getUniqueId());
        }
        BukkitRunnable followUp = new BukkitRunnable() {
            @Override
            public void run() {
                a.pendingVolleyTasks.remove(this);
                if (canReleaseVolley(a, t)) shootVolley(a, t);
            }
        };
        a.pendingVolleyTasks.add(followUp);
        followUp.runTaskLater(plugin, 12L);
    }

    private void venomArc(ActiveBoss a) {
        for (Player p : playersInRange(a.boss.getLocation(), 4.5)) {
            p.damage(6.0, a.boss); p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 90, 0, false, true, true));
            poisonCloud(a, p.getLocation(), 90, 2.2f);
        }
    }

    private void startOmegaSlam(ActiveBoss a) {
        a.omegaWindupTicks = 40;
        Location c = a.boss.getLocation();
        c.getWorld().playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);
        c.getWorld().playSound(c, Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.8f);
        c.getWorld().spawnParticle(Particle.TRIAL_OMEN, c.add(0.0, 1.0, 0.0), 40, 1.0, 0.5, 1.0, 0.03);
        for (Player p : playersInRange(a.boss.getLocation(), 40.0)) p.sendMessage(ChatColor.YELLOW + "Discipline Surge: jump!");
    }

    private void executeOmegaSlam(ActiveBoss a) {
        Location c = a.boss.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.8f);
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
        for (Player p : playersInRange(c, 28.0)) {
            if (isPlayerGrounded(p)) { p.damage(12.0, a.boss); p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, false, true, true)); p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1, false, true, true)); }
            else p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0.0, 0.1, 0.0), 8, 0.2, 0.1, 0.2, 0.02);
        }
    }

    private boolean isPlayerGrounded(Player player) {
        if (Math.abs(player.getVelocity().getY()) > 0.08D) {
            return false;
        }
        Location below = player.getLocation().clone().subtract(0.0, 0.2, 0.0);
        return below.getBlock().getType().isSolid();
    }

    private void poisonCloud(ActiveBoss a, Location at, int duration, float radius) {
        AreaEffectCloud cloud = at.getWorld().spawn(at, AreaEffectCloud.class, e -> {
            e.setRadius(radius); e.setDuration(duration); e.setRadiusPerTick(0.0f); e.setColor(org.bukkit.Color.fromRGB(0, 199, 179));
            e.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true, true), true);
        });
        a.cloudIds.add(cloud.getUniqueId());
        cloud.getWorld().spawnParticle(Particle.ENTITY_EFFECT, cloud.getLocation().add(0.0, 0.2, 0.0),
            20, 0.5, 0.1, 0.5, 0.0, org.bukkit.Color.fromRGB(0, 199, 179));
    }

    private void igniteFloor(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            Location loc = center.clone().add(x, 0.0, z);
            if (loc.getBlock().getType().isAir() && loc.clone().add(0.0, -1.0, 0.0).getBlock().getType().isSolid()) loc.getBlock().setType(Material.FIRE, false);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                Location loc = center.clone().add(x, 0.0, z);
                if (loc.getBlock().getType() == Material.FIRE) loc.getBlock().setType(Material.AIR, false);
            }
        }, 50L);
    }

    private void setStats(WitherSkeleton boss, double speed, double armor, double kb) {
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HP);
        if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null) boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        if (boss.getAttribute(Attribute.FOLLOW_RANGE) != null) boss.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(BOSS_TARGET_RANGE);
        if (boss.getAttribute(Attribute.ARMOR) != null) boss.getAttribute(Attribute.ARMOR).setBaseValue(armor);
        if (boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(kb);
    }

    private ItemStack litBulbHelmet() {
        ItemStack stack = new ItemStack(Material.COPPER_BULB);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.YELLOW + "Lit Copper Bulb");
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private void heal(LivingEntity entity, double amount) { entity.setHealth(Math.min(maxHealth(entity), entity.getHealth() + amount)); }

    private double maxHealth(LivingEntity entity) {
        return entity.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private void updateBar(ActiveBoss a) {
        double progress = Math.max(0.0, Math.min(1.0, a.boss.getHealth() / maxHealth(a.boss)));
        a.bar.setProgress(progress);
        if (a.transitionTicks < 0) a.bar.setTitle(BOSS_NAME + ChatColor.GRAY + " - " + a.phase.display);
        for (Player player : viewers(a)) if (!a.bar.getPlayers().contains(player)) a.bar.addPlayer(player);
        for (Player player : new ArrayList<>(a.bar.getPlayers())) {
            if (!player.isOnline() || player.getWorld() != a.boss.getWorld()
                || (a.encounterPlayers != null && !a.encounterPlayers.contains(player.getUniqueId()))) {
                a.bar.removePlayer(player);
            }
        }
        a.bar.setVisible(true);
    }

    private ActiveBoss nearest(Location from, double maxDistance) {
        double maxSq = maxDistance * maxDistance;
        return bosses.values().stream().filter(a -> a.boss.isValid() && a.boss.getWorld() == from.getWorld())
            .filter(a -> a.boss.getLocation().distanceSquared(from) <= maxSq)
            .min(Comparator.comparingDouble(a -> a.boss.getLocation().distanceSquared(from))).orElse(null);
    }

    private Player nearestPlayer(Location from, double maxDistance) {
        return playersInRange(from, maxDistance).stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(from))).orElse(null);
    }

    private List<Player> playersInRange(Location from, double maxDistance) {
        List<Player> out = new ArrayList<>(); World world = from.getWorld(); if (world == null) return out;
        double maxSq = maxDistance * maxDistance;
        for (Player p : world.getPlayers()) {
            if (p.isOnline() && !p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                && !p.isInvulnerable() && p.getLocation().distanceSquared(from) <= maxSq) {
                out.add(p);
            }
        }
        return out;
    }

    private List<Player> viewers(ActiveBoss a) {
        if (a.encounterPlayers == null) return new ArrayList<>(a.boss.getWorld().getPlayers());
        List<Player> result = new ArrayList<>();
        for (UUID id : a.encounterPlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline() && player.getWorld() == a.boss.getWorld()) result.add(player);
        }
        return result;
    }

    private Phase phaseForHealth(double hp) {
        if (hp > 150.0) return Phase.BALLISTIC;
        if (hp > 100.0) return Phase.GUARDIAN;
        if (hp > 50.0) return Phase.MONK;
        return Phase.MASTERY;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        handleBossDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOtherEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        handleBossDamage(event);
    }

    private void handleBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton boss)) return;
        ActiveBoss a = bosses.get(boss.getUniqueId());
        if (a == null) return;

        if (a.introTicks > 0 || a.pauseTicks > 0 || a.transitionTicks >= 0) {
            traceDeniedBossDamage(a, event, a.transitionTicks >= 0 ? "transition"
                : a.pauseTicks > 0 ? "recovery_pause" : "intro");
            event.setCancelled(true);
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntityEvent)) {
            enforcePhaseBoundary(event, a);
            return;
        }
        Entity damager = byEntityEvent.getDamager();
        if ((a.phase == Phase.GUARDIAN || a.phase == Phase.MASTERY) && a.bulwarkTicks > 0 && fromFront(a.boss, damager)) {
            traceDeniedBossDamage(a, event, "front_bulwark");
            event.setCancelled(true); a.boss.getWorld().playSound(a.boss.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.9f); return;
        }
        if ((a.phase == Phase.GUARDIAN || a.phase == Phase.MASTERY) && damager instanceof Projectile projectile && !a.sentinelIds.isEmpty()) {
            traceDeniedBossDamage(a, event, "sentinel_projectile_guard");
            event.setCancelled(true); projectile.remove(); consumeSentinel(a); return;
        }
        if (a.phase == Phase.MONK && a.meditationActive) {
            traceDeniedBossDamage(a, event, "meditation");
            event.setCancelled(true);
            a.meditationDamage += Math.max(1, (int) Math.ceil(event.getFinalDamage()));
            if (a.meditationDamage >= MEDITATION_THRESHOLD) { a.meditationBroken = true; resolveMeditation(a); }
            return;
        }
        enforcePhaseBoundary(event, a);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        for (ActiveBoss active : bosses.values()) {
            active.bar.removePlayer(event.getPlayer());
            removeEmergenceEffect(active, event.getPlayer());
        }
        hideUnauthorizedCutsceneDisplays(event.getPlayer());
        showAuthorizedCutsceneDisplays(event.getPlayer());
        refreshBossBarsFor(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        hideUnauthorizedCutsceneDisplays(event.getPlayer());
        showAuthorizedCutsceneDisplays(event.getPlayer());
        refreshBossBarsFor(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (ActiveBoss active : bosses.values()) {
            active.bar.removePlayer(event.getPlayer());
            removeEmergenceEffect(active, event.getPlayer());
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (ActiveBoss active : new ArrayList<>(bosses.values())) {
            if (active.boss.getWorld() == event.getWorld()) removeCombatMaster(active.boss.getUniqueId());
        }
        for (var entry : new ArrayList<>(pendingRewards.entrySet())) {
            if (entry.getValue().at().getWorld() == event.getWorld()) releaseRewards(entry.getKey());
        }
    }

    private void refreshBossBarsFor(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (ActiveBoss active : bosses.values()) {
                active.bar.removePlayer(player);
                if (active.boss.isValid() && active.boss.getWorld() == player.getWorld()
                    && (active.encounterPlayers == null || active.encounterPlayers.contains(player.getUniqueId()))) {
                    active.bar.addPlayer(player);
                    active.bar.setVisible(true);
                }
            }
        }, 2L);
    }

    private void hideUnauthorizedCutsceneDisplays(Player player) {
        for (ActiveBoss active : bosses.values()) {
            if (active.encounterPlayers != null && active.encounterPlayers.contains(player.getUniqueId())) continue;
            for (UUID id : active.cylinderIds) {
                Entity display = Bukkit.getEntity(id);
                if (display != null) player.hideEntity(plugin, display);
            }
        }
    }

    private void showAuthorizedCutsceneDisplays(Player player) {
        for (ActiveBoss active : bosses.values()) {
            if (!active.denseCylinder || active.encounterPlayers == null
                || !active.encounterPlayers.contains(player.getUniqueId())
                || active.boss.getWorld() != player.getWorld()) continue;
            for (UUID id : active.cylinderIds) {
                Entity display = Bukkit.getEntity(id);
                if (display != null) player.showEntity(plugin, display);
            }
        }
    }

    private void removeEmergenceEffect(ActiveBoss active, Player player) {
        if (!active.darknessRecipients.remove(player.getUniqueId())) return;
        PotionEffect darkness = player.getPotionEffect(PotionEffectType.DARKNESS);
        if (darkness != null && darkness.getAmplifier() == 0 && darkness.getDuration() <= 55)
            player.removePotionEffect(PotionEffectType.DARKNESS);
    }

    private void scheduleBossBarRefresh(ActiveBoss active, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.cleanedUp || !active.boss.isValid()) return;
            updateBar(active);
        }, delayTicks);
    }

    private void enforcePhaseBoundary(EntityDamageEvent event, ActiveBoss active) {
        double threshold = nextPhaseThreshold(active.phase);
        if (threshold <= 0.0D || active.boss.getHealth() - event.getFinalDamage() > threshold) return;

        traceDeniedBossDamage(active, event, "phase_threshold_" + threshold);
        event.setCancelled(true);
        active.phaseGateDamage = Math.max(0.0D, active.boss.getHealth() - threshold);
        active.boss.setHealth(threshold);
        plugin.getLogger().info("[CombatMaster] Damage gate boss=" + active.boss.getUniqueId()
            + " phase=" + active.phase.name() + " damage="
            + String.format(Locale.US, "%.1f", event.getFinalDamage()) + " threshold=" + threshold);
        advanceOnePhaseIfNeeded(active);
    }

    private void traceDeniedBossDamage(ActiveBoss active, EntityDamageEvent event, String reason) {
        if (!isRelevantPhaseDiagnosis(active.phase == Phase.MONK, active.boss.getHealth())
            || active.deniedDamageReports++ >= MAX_DENIED_DAMAGE_REPORTS_PER_BOSS) return;
        Entity direct = event.getDamageSource() == null ? null : event.getDamageSource().getDirectEntity();
        Entity causing = event.getDamageSource() == null ? null : event.getDamageSource().getCausingEntity();
        plugin.getLogger().info("[CombatMasterDenied] boss=" + active.boss.getUniqueId()
            + " tick=" + Bukkit.getCurrentTick() + " reason=" + reason + " phase=" + active.phase
            + " hp=" + active.boss.getHealth() + "/" + maxHealth(active.boss)
            + " transitionTicks=" + active.transitionTicks + " pauseTicks=" + active.pauseTicks
            + " meditation=" + active.meditationActive + " meditationDamage=" + active.meditationDamage
            + " gateDamage=" + active.phaseGateDamage + " raw=" + event.getDamage()
            + " final=" + event.getFinalDamage() + " cause=" + event.getCause()
            + " attacker=" + (causing == null ? "none" : causing.getType() + ":" + causing.getUniqueId())
            + " direct=" + (direct == null ? "none" : direct.getType() + ":" + direct.getUniqueId()));
    }

    static boolean isRelevantPhaseDiagnosis(boolean monkPhase, double health) {
        return monkPhase || (health >= 135.0D && health <= 160.0D);
    }

    private double nextPhaseThreshold(Phase phase) {
        return switch (phase) {
            case BALLISTIC -> 150.0D;
            case GUARDIAN -> 100.0D;
            case MONK -> 50.0D;
            case MASTERY -> 0.0D;
        };
    }

    private void advanceOnePhaseIfNeeded(ActiveBoss active) {
        Phase wanted = phaseForHealth(active.boss.getHealth());
        if (active.transitionTicks >= 0 || wanted.ordinal() <= active.phase.ordinal()) return;
        Phase previous = active.phase;
        Phase next = Phase.values()[previous.ordinal() + 1];
        onTransition(active, previous, next);
        if (active.encounterPlayers == null) {
            active.phase = next;
            applyPhase(active, false);
            resetCooldowns(active);
            summonPhaseMinions(active, next);
        }
        updateBar(active);
        plugin.getLogger().info("[CombatMaster] Phase transition boss=" + active.boss.getUniqueId()
            + " from=" + previous.name() + " to=" + next.name()
            + " health=" + String.format(Locale.US, "%.1f", active.boss.getHealth()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton boss)) return;
        ActiveBoss a = bosses.get(boss.getUniqueId());
        if (a == null) return;
        event.getDrops().clear();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        event.getDrops().add(namedItem(Material.HEAVY_CORE, ChatColor.AQUA + "Heavy Core", "Shattered hammer head"));
        event.getDrops().add(namedItem(Material.AMETHYST_SHARD, ChatColor.GOLD + "Combat Master's Seal", "Cosmetic trophy"));

        List<ItemStack> pool = currentRewardPool();
        int count = rng.nextInt(2, 4);
        for (int i = 0; i < count; i++) event.getDrops().add(pool.get(rng.nextInt(pool.size())).clone());

        int visualDeathTicks = 0;
        if (a.visualAttached) {
            CombatMasterVisualBridge bridge = visualBridge();
            if (bridge != null) {
                try {
                    visualDeathTicks = bridge.death(boss, boss.getLocation().clone());
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[CombatMaster] Visual death failed boss=" + boss.getUniqueId() + ": " + ex);
                }
            }
        }

        if (visualDeathTicks > 0) {
            Location at = boss.getLocation().clone();
            List<ItemStack> rewards = event.getDrops().stream().map(ItemStack::clone).toList();
            event.getDrops().clear();
            UUID bossId = boss.getUniqueId();
            int delay = visualDeathTicks;
            BukkitTask release = Bukkit.getScheduler().runTaskLater(plugin,
                () -> releaseRewards(bossId), delay);
            pendingRewards.put(bossId, new PendingRewards(at, rewards, release));
            plugin.getLogger().info("[CombatMaster] Deferred visual rewards boss=" + bossId
                + " count=" + rewards.size() + " ticks=" + delay);
        }

        cleanup(a, Completion.DEFEATED);
    }

    private void releaseRewards(UUID bossId) {
        PendingRewards pending = pendingRewards.remove(bossId);
        if (pending == null) return;
        pending.task().cancel();
        World world = pending.at().getWorld();
        if (world == null) return;
        for (ItemStack reward : pending.items()) world.dropItemNaturally(pending.at(), reward.clone());
        plugin.getLogger().info("[CombatMaster] Released visual rewards boss=" + bossId
            + " count=" + pending.items().size());
    }

    private List<ItemStack> currentRewardPool() {
        List<ItemStack> pool = new ArrayList<>();
        pool.add(Items.royalRoulette());
        pool.add(Items.lifeElixir(1));
        ItemStack hearts = Items.heartItem();
        hearts.setAmount(8);
        pool.add(hearts);
        pool.add(namedItem(new ItemStack(Material.TRIAL_KEY), ChatColor.YELLOW + "Trial Key", "Opens advanced encounter rewards"));
        pool.add(namedItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 32), ChatColor.AQUA + "Combat Master's Insight", "Training experience"));
        pool.add(namedItem(new ItemStack(Material.GOLDEN_APPLE, 4), ChatColor.GOLD + "Field Rations", "Emergency sustain"));
        return pool;
    }

    private ItemStack namedItem(Material material, String name, String loreLine) { return namedItem(new ItemStack(material), name, loreLine); }
    private ItemStack namedItem(ItemStack stack, String name, String... loreLines) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(name);
        if (loreLines != null && loreLines.length > 0) {
            List<String> lore = new ArrayList<>(loreLines.length);
            for (String line : loreLines) lore.add(ChatColor.DARK_GRAY + line);
            meta.setLore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean fromFront(WitherSkeleton boss, Entity attacker) {
        Vector facing = boss.getLocation().getDirection().setY(0.0);
        Vector attack = attacker.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0.0);
        if (facing.lengthSquared() < 0.0001 || attack.lengthSquared() < 0.0001) return false;
        return facing.normalize().dot(attack.normalize()) > 0.35;
    }

    private void consumeSentinel(ActiveBoss a) {
        for (UUID id : new ArrayList<>(a.sentinelIds)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Skeleton sk && sk.isValid() && !sk.isDead()) { sk.remove(); a.sentinelIds.remove(id); return; }
        }
    }

    private void clearSentinels(ActiveBoss a) {
        for (UUID id : new ArrayList<>(a.sentinelIds)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) e.remove();
        }
        a.sentinelIds.clear();
    }

    private void clearMinions(ActiveBoss a) {
        for (UUID id : new ArrayList<>(a.minionIds)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        a.minionIds.clear();
        a.sentinelIds.clear();
    }

    private void cleanup(ActiveBoss a, Completion completion) {
        if (a.cleanedUp) {
            return;
        }
        a.cleanedUp = true;
        a.transitionTicks = -1;
        clearCylinder(a);
        for (BukkitRunnable volley : new ArrayList<>(a.pendingVolleyTasks)) volley.cancel();
        a.pendingVolleyTasks.clear();
        for (UUID id : a.projectileIds) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        a.projectileIds.clear();
        for (UUID id : a.cloudIds) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        a.cloudIds.clear();
        if (a.visualAttached) {
            CombatMasterVisualBridge bridge = visualBridge();
            if (bridge != null) {
                try {
                    bridge.detach(a.boss.getUniqueId());
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[CombatMaster] Visual detach failed boss="
                        + a.boss.getUniqueId() + ": " + ex);
                }
            }
            a.visualAttached = false;
        }
        if (a.task != null) a.task.cancel();
        clearMinions(a);
        for (UUID id : new ArrayList<>(a.anchorIds)) { Entity e = Bukkit.getEntity(id); if (e != null && e.isValid()) e.remove(); }
        a.anchorIds.clear();
        a.bar.removeAll(); a.bar.setVisible(false);
        bosses.remove(a.boss.getUniqueId());
        if (completion == Completion.DEFEATED && a.boss.getWorld() != null) {
            Location loc = a.boss.getLocation();
            a.boss.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.3f, 1.0f);
            a.boss.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0.0, 1.1, 0.0), 28, 0.7, 0.7, 0.7, 0.08);
        }
        try {
            a.completion.accept(completion);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[CombatMaster] Completion callback failed: " + ex.getMessage());
        }
    }

    public enum Completion {
        DEFEATED,
        CANCELED
    }

    private record PendingRewards(Location at, List<ItemStack> items, BukkitTask task) {}
    private record SupplyGrant(int total, int dropped) {}

    private enum Phase {
        BALLISTIC("Artillery Form"), GUARDIAN("Bulwark Form"), MONK("Discipline Form"), MASTERY("Final Lesson");
        private final String display;
        Phase(String display) { this.display = display; }
        private static Phase fromKey(String raw) {
            String key = raw.toLowerCase(Locale.ROOT);
            return switch (key) {
                case "ballistic", "artillery", "phase1", "1" -> BALLISTIC;
                case "guardian", "bulwark", "phase2", "2" -> GUARDIAN;
                case "monk", "discipline", "phase3", "3" -> MONK;
                case "mastery", "final", "final_lesson", "venom", "phase4", "4" -> MASTERY;
                default -> null;
            };
        }
    }

    private static final class ActiveBoss {
        private final WitherSkeleton boss;
        private final BossBar bar;
        private final Location arenaSpawn;
        private Set<UUID> encounterPlayers;
        private int transitionTicks = -1;
        private long transitionStartedNanos;
        private long transitionMaxTickNanos;
        private Phase transitionFrom;
        private Phase transitionTo;
        private boolean denseCylinder;
        private long cylinderBuildNanos;
        private long cylinderUpdateNanos;
        private long cylinderCleanupNanos;
        private long cylinderDissipateNanos;
        private int dissolvedRows;
        private int blueSkullCount;
        private final List<UUID> cylinderIds = new ArrayList<>();
        private final Set<UUID> darknessRecipients = new HashSet<>();
        private final Set<UUID> projectileIds = new HashSet<>();
        private final ArrayDeque<String> recentParticipantDamage = new ArrayDeque<>();
        private int suspiciousDamageReports;
        private int deniedDamageReports;
        private final Set<UUID> cloudIds = new HashSet<>();
        private BukkitTask task;
        private Phase phase;
        private int introTicks;
        private int pauseTicks;
        private int cdA, cdB, cdC, cdD, cdE, cdSentinel;
        private int guardianCycle;
        private double phaseGateDamage;
        private int bulwarkTicks;
        private int omegaCooldown;
        private int omegaWindupTicks;
        private boolean meditationActive;
        private boolean meditationBroken;
        private int meditatingTicks;
        private int meditationDamage;
        private final List<UUID> sentinelIds = new ArrayList<>();
        private final List<UUID> anchorIds = new ArrayList<>();
        private final List<UUID> minionIds = new ArrayList<>();
        private boolean allWaveMinions;
        private boolean cleanedUp;
        private boolean visualAttached;
        private final Set<BukkitRunnable> pendingVolleyTasks = new HashSet<>();
        private long controllerTicks;
        private long lastMaintainedControllerTicks;
        private int controllerFailures;
        private Consumer<Completion> completion = ignored -> {};

        private ActiveBoss(WitherSkeleton boss, BossBar bar, Location arenaSpawn) {
            this.boss = boss;
            this.bar = bar;
            this.arenaSpawn = arenaSpawn;
        }
    }
}
