package hgloot.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.util.HydraSounds;

public final class VoidRouletteService implements Listener {
    private static final String PREFIX = HydraIcons.EXCLAMATION + " " + ChatColor.GRAY;
    private static final String SIDEBAR_OBJECTIVE = "hydra_hud";
    private static final String SPECTATOR_MENU_TITLE = ChatColor.DARK_AQUA + "Void Roulette Spectate";
    private static final String SPECTATOR_ACTION_TELEPORT_COMPASS = "spectator_teleport_compass";
    private static final String SPECTATOR_ACTION_HUB_PAPER = "spectator_hub_paper";
    private static final String SPECTATOR_ACTION_TARGET_PREFIX = "spectator_target:";
    private static final String SPECTATOR_ACTION_CLOSE = "spectator_close";
    private static final int MIN_PLAYERS = 4;
    private static final int MIN_FORCE_START_PLAYERS = 2;
    private static final int MAX_PLAYERS = 16;
    private static final int QUEUE_COUNTDOWN_SECONDS = 15;
    private static final int ROULETTE_INTERVAL_SECONDS = 25;
    private static final int VOIDBREAKER_LIMIT_SECONDS = 10;
    private static final int VOIDBREAKER_REVEAL_SECONDS = 2;
    private static final long OPENING_DROP_INTERVAL_TICKS = 20L * 5L;
    private static final long MIDGAME_DROP_INTERVAL_TICKS = 20L * 4L;
    private static final long ENDGAME_DROP_INTERVAL_TICKS = 20L * 3L;
    private static final long MIDGAME_START_MS = 90_000L;
    private static final long ENDGAME_START_MS = 180_000L;
    private static final int START_FREEZE_SECONDS = 5;
    private static final int STARTING_LIVES = 3;
    private static final int ARENA_RADIUS = 44;
    private static final int PLATFORM_Y = 100;
    private static final int SPAWN_Y = PLATFORM_Y + 2;
    private static final int PILLAR_HEIGHT = 25;
    private static final int ELIMINATION_Y = 62;
    private static final int TEST_EVENT_WARNING_SECONDS = 10;
    private static final int TEST_EVENT_DURATION_SECONDS = 10;
    private static final int TEST_SUDDEN_DEATH_SECONDS = 8 * 60;
    private static final int TEST_SUDDEN_DEATH_CYCLE_SECONDS = 15;
    private static final int TEST_SUDDEN_DEATH_HUNT_SECONDS = 7;
    private static final int TEST_SUDDEN_DEATH_STAGE_SECONDS = 15;
    private static final double[] TEST_SUDDEN_DEATH_BORDER_STAGES = {60.0D, 40.0D, 25.0D, 12.0D, 5.0D, 1.0D};
    private static final long RECONNECT_BODY_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final Material[] RANDOM_ITEM_POOL = buildRandomItemPool();

    private final HGLootPlugin plugin;
    private boolean autoCleanupEnabled = true;
    private final NamespacedKey itemKey;
    private final LinkedHashSet<UUID> queuedPlayers = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> alivePlayers = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> externalSpectators = new HashSet<>();
    private final List<UUID> managedEliminationOrder = new ArrayList<>();
    private final Map<UUID, Integer> managedEliminationCredits = new HashMap<>();
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Location> playerSpawns = new HashMap<>();
    private final Map<UUID, UUID> recentKnockbackCauses = new HashMap<>();
    private final Map<UUID, Long> recentKnockbackTimes = new HashMap<>();
    private final Map<UUID, Location> frozenSpawnLocations = new HashMap<>();
    private final Map<UUID, Location> respawnFreezeLocations = new HashMap<>();
    private final Set<UUID> lifeLossInProgress = new HashSet<>();
    private final Map<UUID, String> sidebarSignatures = new HashMap<>();
    private final List<UUID> voidbreakerSelectionBag = new ArrayList<>();
    private final Map<UUID, VoidRoulettePlayerSnapshot> rejoinSnapshots = new HashMap<>();
    private final Map<UUID, VoidRoulettePlayerSnapshot> reviveSnapshots = new HashMap<>();
    private final Map<UUID, UUID> reconnectZombieByPlayer = new HashMap<>();
    private final Map<UUID, UUID> reconnectPlayerByZombie = new HashMap<>();
    private final Map<UUID, Long> reconnectZombieExpiresAtMs = new HashMap<>();
    private final List<Location> arenaPillars = new ArrayList<>();
    private final Set<BlockPosition> playerPlacedBlocks = new HashSet<>();
    private final Map<UUID, Long> risingVoidWarningCooldownUntilMs = new HashMap<>();
    private final List<ExperimentalEvent> chaosEventBag = new ArrayList<>();
    private BukkitTask queueCountdownTask;
    private BukkitTask rouletteTask;
    private BukkitTask voidbreakerDeadlineTask;
    private BukkitTask randomDropTask;
    private BukkitTask startFreezeTask;
    private BukkitTask sidebarTask;
    private BukkitTask activeExperimentalTask;
    private BukkitTask experimentalWarningTask;
    private BukkitTask suddenDeathScheduleTask;
    private BukkitTask suddenDeathTask;
    private BossBar experimentalBossBar;
    private World activeWorld;
    private String activeWorldName;
    private int nextWorldNumber = 1;
    private int countdownSeconds;
    private int cycleSecondsRemaining;
    private int matchPlayerCount;
    private UUID currentVoidbreaker;
    private UUID glowingVoidbreaker;
    private UUID lastVoidbreaker;
    private boolean voidbreakerSucceeded;
    private boolean voidbreakerAbilityUsed;
    private boolean startFreezeActive;
    private boolean matchStarting;
    private boolean matchActive;
    private boolean testModeEnabled;
    private boolean infiniteLivesEnabled;
    private boolean suddenDeathActive;
    private boolean swapUsedThisMatch;
    private ExperimentalEvent activeExperimentalEvent;
    private ExperimentalEvent pendingExperimentalEvent;
    private int experimentalWarningSecondsRemaining;
    private int suddenDeathStageIndex;
    private double risingVoidY = ELIMINATION_Y;
    private int lastRemovedVoidLayer = ELIMINATION_Y;
    private long matchStartedAtMs;
    private long nextRandomDropAtMs;
    private ArenaLayout activeArenaLayout = ArenaLayout.RING;
    private ManagedMinigameRound.Request managedRound;
    private boolean managedCallbackSent;

    public VoidRouletteService(HGLootPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "void_roulette_item");
    }

    public void start() {
        autoCleanupEnabled = plugin.getConfig().getBoolean("voidroulette.auto_cleanup", true);
        experimentalBossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        experimentalBossBar.setVisible(false);
        sidebarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSidebars, 20L, 20L);
    }

    public void shutdown() {
        cancelTasks();
        hideExperimentalBossBar();
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                releaseQueuedPlayer(player);
            }
        }
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cleanupPlayerForHub(player);
            }
        }
        for (UUID id : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cleanupPlayerForHub(player);
            }
        }
        String worldName = activeWorldName;
        cleanupState(false);
        cleanupAllReconnectBodies();
        rejoinSnapshots.clear();
        reviveSnapshots.clear();
        if (worldName != null) {
            cleanupWorld(worldName);
        }
    }

    public boolean startManagedRound(ManagedMinigameRound.Request request) {
        if (request == null || request.game() != ManagedMinigameRound.Game.VOID_ROULETTE
            || matchActive || matchStarting || managedRound != null) {
            return false;
        }
        List<UUID> participants = request.roster().stream()
            .filter(id -> {
                Player player = Bukkit.getPlayer(id);
                return player != null && player.isOnline();
            })
            .limit(MAX_PLAYERS)
            .toList();
        if (participants.isEmpty()) {
            return false;
        }
        cancelQueueCountdown();
        queuedPlayers.clear();
        queuedPlayers.addAll(participants);
        managedRound = request;
        managedCallbackSent = false;
        startMatch(true);
        if (!matchActive && !matchStarting) {
            queuedPlayers.removeAll(participants);
            managedRound = null;
            return false;
        }
        return true;
    }

    public void stopManagedRound(String reason) {
        if (managedRound == null) {
            return;
        }
        ManagedMinigameRound.Request request = managedRound;
        stopMatchInternal();
        dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, reason));
    }

    public boolean hasRejoinSnapshot(Player player) {
        return player != null && rejoinSnapshots.containsKey(player.getUniqueId());
    }

    public boolean hasReviveSnapshot(Player player) {
        return player != null && reviveSnapshots.containsKey(player.getUniqueId());
    }

    public boolean rejoin(Player player, boolean automatic) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (alivePlayers.contains(playerId) || spectators.contains(playerId)) {
            if (!automatic) {
                player.sendMessage(ChatColor.YELLOW + "Already in Void Roulette.");
            }
            return false;
        }
        VoidRoulettePlayerSnapshot snapshot = rejoinSnapshots.get(playerId);
        if (snapshot == null) {
            if (!automatic) {
                player.sendMessage(ChatColor.RED + "No Void Roulette match to rejoin.");
            }
            return false;
        }
        if (!snapshot.isRestorable(this)) {
            clearSnapshot(playerId);
            if (!automatic) {
                player.sendMessage(ChatColor.RED + "Void Roulette match no longer active.");
            }
            return false;
        }
        if (!automatic) {
            return plugin.transitionTravel(player, () -> {
                if (rejoinSnapshots.get(playerId) == snapshot && snapshot.isRestorable(this)) {
                    completeRejoin(player, snapshot, false);
                }
            });
        }
        return completeRejoin(player, snapshot, true);
    }

    private boolean completeRejoin(Player player, VoidRoulettePlayerSnapshot snapshot, boolean automatic) {
        UUID playerId = player.getUniqueId();
        boolean restored = snapshot.spectator
            ? restoreSpectatorSnapshot(player, snapshot)
            : restoreLiveSnapshot(player, snapshot, false);
        if (!restored) {
            if (!automatic) {
                player.sendMessage(ChatColor.RED + "Void Roulette rejoin failed.");
            }
            return false;
        }
        rejoinSnapshots.remove(playerId);
        removeReconnectZombie(playerId);
        player.sendMessage(ChatColor.GREEN + (snapshot.spectator ? "Rejoined Void Roulette as spectator." : "Rejoined Void Roulette."));
        return true;
    }

    public boolean revive(CommandSender sender, Player target) {
        if (target == null || !target.isOnline()) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
            }
            return false;
        }
        UUID targetId = target.getUniqueId();
        if (alivePlayers.contains(targetId)) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + target.getName() + " is already alive in Void Roulette.");
            }
            return false;
        }
        VoidRoulettePlayerSnapshot snapshot = reviveSnapshots.get(targetId);
        if (snapshot == null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "No Void Roulette revive snapshot is available for " + target.getName() + ".");
            }
            return false;
        }
        if (!snapshot.isRestorable(this)) {
            clearSnapshot(targetId);
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "That Void Roulette match is no longer active.");
            }
            return false;
        }
        if (!restoreLiveSnapshot(target, snapshot, true)) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Could not revive " + target.getName() + " in Void Roulette.");
            }
            return false;
        }
        clearSnapshot(targetId);
        if (sender != null) {
            sender.sendMessage(ChatColor.GREEN + "Revived " + target.getName() + " in Void Roulette.");
        }
        return true;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!MinigameForceJoin.requireAdmin(sender)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("join")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can join Void Roulette.");
                return true;
            }
            join(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can leave Void Roulette.");
                    return true;
                }
                if (!leave(player)) {
                    player.sendMessage(ChatColor.YELLOW + "You are not in Void Roulette.");
                }
                return true;
            }
            case "status" -> {
                sender.sendMessage(PREFIX + statusLabel() + ChatColor.GRAY + " | Queue "
                    + queuedPlayers.size() + "/" + MAX_PLAYERS + " | Alive " + alivePlayers.size());
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /voidroulette <join|leave|status>");
                return true;
            }
        }
    }

    public List<String> completeCommand(String[] args) {
        if (args.length <= 1) {
            return List.of("join", "leave", "status").stream()
                .filter(value -> value.startsWith(args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        return List.of();
    }

    public boolean join(Player player) {
        if (player == null || !MinigameForceJoin.requireAdmin(player)) return false;
        return joinForAdmin(player);
    }

    private boolean joinForAdmin(Player player) {
        if (plugin.bossRushService() != null && plugin.bossRushService().ownsPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already committed to Boss Rush.");
            return false;
        }
        if (player == null || !player.isOnline()) {
            return true;
        }
        if (plugin.minigameRouletteService() != null
            && plugin.minigameRouletteService().blockOtherModeEntry(player)) {
            return true;
        }
        if (plugin.isModeLocked("voidroulette")) {
            player.sendMessage(HydraIcons.LOCKED + " " + ChatColor.RED + "Void Roulette is currently locked.");
            return true;
        }
        UUID playerId = player.getUniqueId();
        if (plugin.queueService() != null
            && (plugin.queueService().isPlayerQueued(playerId) || plugin.queueService().isPlayerInMatch(playerId))) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave your current queue or match before joining Void Roulette.");
            return true;
        }
        if (plugin.spleefService() != null && plugin.spleefService().isInSpleef(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave Spleef before joining Void Roulette.");
            return true;
        }
        if (plugin.bridgeService() != null && plugin.bridgeService().isInBridge(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave Bridge before joining Void Roulette.");
            return true;
        }
        if (isInVoidRoulette(playerId)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "You are already in Void Roulette.");
            return true;
        }
        if (queuedPlayers.size() >= MAX_PLAYERS) {
            player.sendMessage(PREFIX + ChatColor.RED + "Void Roulette is full.");
            return true;
        }
        queuedPlayers.add(playerId);
        if (!MinigameForceJoin.stageInGameHub(plugin, player)) {
            queuedPlayers.remove(playerId);
            return false;
        }
        broadcastQueue(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.WHITE + player.getName()
            + ChatColor.GRAY + " Joined " + ChatColor.WHITE + "Void Roulette"
            + ChatColor.GRAY + " (" + ChatColor.WHITE + queuedPlayers.size() + "/" + MIN_PLAYERS
            + ChatColor.GRAY + ") Needed");
        updateQueuedSidebars();
        if (queuedPlayers.size() >= MIN_PLAYERS && queueCountdownTask == null) {
            startQueueCountdown();
        }
        return true;
    }

    public boolean leave(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (queuedPlayers.remove(playerId)) {
            player.sendMessage(PREFIX + ChatColor.GREEN + "You left the queue.");
            releaseQueuedPlayer(player);
            if (queuedPlayers.size() < MIN_PLAYERS) {
                cancelQueueCountdown();
            }
            updateQueuedSidebars();
            return true;
        }
        if (alivePlayers.contains(playerId)) {
            eliminate(player, null, "left the match");
            return true;
        }
        if (spectators.contains(playerId)) {
            cleanupPlayerForHub(player);
            player.sendMessage(PREFIX + ChatColor.GREEN + "You left the match.");
            return true;
        }
        return false;
    }

    public boolean leaveToHub(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (queuedPlayers.remove(playerId)) {
            player.sendMessage(PREFIX + ChatColor.GREEN + "You left the queue.");
            releaseQueuedPlayer(player);
            if (queuedPlayers.size() < MIN_PLAYERS) {
                cancelQueueCountdown();
            }
            updateQueuedSidebars();
            return true;
        }
        boolean wasAlive = alivePlayers.contains(playerId);
        boolean wasSpectator = spectators.contains(playerId);
        if (!wasAlive && !wasSpectator) {
            return false;
        }
        if (playerId.equals(currentVoidbreaker)) {
            clearVoidbreakerGlow();
            currentVoidbreaker = null;
            voidbreakerSucceeded = true;
        }
        cleanupPlayerForHub(player);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Returned to hub.");
        if (wasAlive) {
            broadcastMatch(ChatColor.RED + player.getName() + ChatColor.GRAY + " left Void Roulette.");
            checkWin();
        }
        return true;
    }

    public boolean isInVoidRoulette(UUID playerId) {
        return queuedPlayers.contains(playerId) || alivePlayers.contains(playerId) || spectators.contains(playerId);
    }

    public boolean isQueuedForSpectatorCommand(UUID playerId) {
        return playerId != null && queuedPlayers.contains(playerId);
    }

    public boolean isInActiveVoidRouletteMatch(UUID playerId) {
        return playerId != null && matchActive
            && (alivePlayers.contains(playerId) || spectators.contains(playerId));
    }

    public boolean spectateTargetMatch(Player spectator, Player target) {
        return spectateTargetMatch(spectator, target, spectatableMatchId(target));
    }

    public String spectatableMatchId(Player target) {
        if (target == null || !target.isOnline() || target.getWorld() == null) return null;
        UUID id = target.getUniqueId();
        return matchActive && activeWorldName != null && activeWorldName.equals(target.getWorld().getName())
            && (alivePlayers.contains(id) || spectators.contains(id)) ? "voidroulette:" + activeWorldName : null;
    }

    public boolean spectateTargetMatch(Player spectator, Player target, String expectedMatchId) {
        if (spectator == null || target == null || !spectator.isOnline() || !target.isOnline()) {
            return false;
        }
        if (expectedMatchId == null || !expectedMatchId.equals(spectatableMatchId(target))) return false;
        UUID spectatorId = spectator.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (!matchActive || activeWorldName == null) {
            return false;
        }
        if (!alivePlayers.contains(targetId) && !spectators.contains(targetId)) {
            return false;
        }
        if (alivePlayers.contains(spectatorId) || spectators.contains(spectatorId)) {
            spectator.sendMessage(ChatColor.RED + "You are already in that Void Roulette match.");
            return true;
        }
        return plugin.transitionTravel(spectator, () -> {
            if (!matchActive || !target.isOnline()
                || !expectedMatchId.equals(spectatableMatchId(target))
                || (!alivePlayers.contains(targetId) && !spectators.contains(targetId))
                || !Objects.equals(target.getWorld().getName(), activeWorldName)
                || alivePlayers.contains(spectatorId) || spectators.contains(spectatorId)) {
                if (spectator.isOnline()) spectator.sendMessage(ChatColor.RED + "That match is no longer available to spectate.");
                SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, false);
                return;
            }
            queuedPlayers.remove(spectatorId);
            spectators.add(spectatorId);
            externalSpectators.add(spectatorId);
            spectator.teleport(target.getLocation());
            setSpectatorState(spectator);
            syncSpectatorVisibility();
            updateMatchSidebar(spectator);
            SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, true);
        });
    }

    public String playerAudienceKey(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        if (!alivePlayers.contains(playerId) && !spectators.contains(playerId)) {
            return null;
        }
        if (activeWorldName == null || player.getWorld() == null
            || !Objects.equals(activeWorldName, player.getWorld().getName())) {
            return null;
        }
        return "voidroulette:" + activeWorldName;
    }

    public boolean isAudienceSpectator(UUID playerId) {
        return playerId != null && spectators.contains(playerId);
    }

    public boolean isAudienceAlive(UUID playerId) {
        return playerId != null && alivePlayers.contains(playerId);
    }

    public int queuedCount() {
        return queuedPlayers.size();
    }

    public int tabActivityCount() {
        LinkedHashSet<UUID> players = new LinkedHashSet<>();
        players.addAll(queuedPlayers);
        players.addAll(alivePlayers);
        players.addAll(spectators);
        return players.size();
    }

    public int minPlayersRequired() {
        return MIN_PLAYERS;
    }

    public int maxPlayersAllowed() {
        return MAX_PLAYERS;
    }

    public String statusLabel() {
        if (matchActive) {
            return "Active";
        }
        if (matchStarting) {
            return "Starting";
        }
        return queuedPlayers.size() >= MIN_PLAYERS ? "Ready" : "Waiting";
    }

    public boolean isTestModeEnabled() {
        return testModeEnabled;
    }

    public boolean isInfiniteLivesEnabled() {
        return testModeEnabled && infiniteLivesEnabled;
    }

    public boolean handleAdminCommand(Player player, String[] args) {
        // args[0] is "voidroulette" or "maceroulette" (the route token).
        if (args.length <= 1) {
            player.sendMessage(PREFIX + statusLabel() + ChatColor.GRAY + " | Queue "
                + queuedPlayers.size() + "/" + MAX_PLAYERS + " | Alive " + alivePlayers.size()
                + " | Test " + (testModeEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            player.sendMessage(ChatColor.RED + "Usage: /admin vr <status|forcejoin|forcestart|stop|forceend|cleanup|test>");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> player.sendMessage(PREFIX + statusLabel() + ChatColor.GRAY + " | Queue "
                + queuedPlayers.size() + "/" + MAX_PLAYERS + " | Alive " + alivePlayers.size()
                + " | Test " + (testModeEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            case "forcestart" -> forceStart(player);
            case "forcejoin" -> MinigameForceJoin.execute(player, args, 2, "vr", this::joinForAdmin,
                target -> queuedPlayers.contains(target.getUniqueId()));
            case "stop", "forceend" -> stopMatch(player);
            case "cleanup" -> handleCleanupAdmin(player, args);
            case "test" -> handleTestAdmin(player, args);
            default -> player.sendMessage(ChatColor.RED + "Usage: /admin vr <status|forcejoin|forcestart|stop|forceend|cleanup|test>");
        }
        return true;
    }

    public List<String> completeAdmin(String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("forcejoin")) {
            return MinigameForceJoin.complete(args[2]);
        }
        // args[0] is the route token ("voidroulette"/"maceroulette").
        if (args.length == 2) {
            String token = args[1].toLowerCase(Locale.ROOT);
            return List.of("status", "forcejoin", "forcestart", "stop", "forceend", "cleanup", "test").stream()
                .filter(value -> value.startsWith(token))
                .toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("cleanup")) {
            String token = args[2].toLowerCase(Locale.ROOT);
            return List.of("status", "purge", "on", "off").stream()
                .filter(value -> value.startsWith(token))
                .toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("test")) {
            String token = args[2].toLowerCase(Locale.ROOT);
            return List.of("status", "toggle", "on", "off", "infinitelives", "crosswind", "lowgravity",
                    "supply", "swap", "suddendeath", "stop")
                .stream()
                .filter(value -> value.startsWith(token))
                .toList();
        }
        return List.of();
    }

    private void handleTestAdmin(CommandSender sender, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("status")) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Void Roulette test mode: "
                + (testModeEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Active experiment: " + ChatColor.WHITE
                + (activeExperimentalEvent == null ? "None" : activeExperimentalEvent.displayName));
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Next experiment: " + ChatColor.WHITE
                + (pendingExperimentalEvent == null ? "None" : pendingExperimentalEvent.displayName
                    + " in " + experimentalWarningSecondsRemaining + "s"));
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Sudden death: "
                + (suddenDeathActive ? ChatColor.RED + "ACTIVE" : ChatColor.WHITE + "Inactive"));
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Infinite lives: "
                + (isInfiniteLivesEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            return;
        }
        if (action.equals("toggle") || action.equals("on") || action.equals("off")) {
            boolean enabled = action.equals("toggle") ? !testModeEnabled : action.equals("on");
            setTestMode(sender, enabled);
            return;
        }
        if (!testModeEnabled) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Enable Void Roulette test mode first.");
            return;
        }
        if (action.equals("infinitelives") || action.equals("lives")) {
            setInfiniteLives(sender, !infiniteLivesEnabled);
            return;
        }
        if (action.equals("stop")) {
            stopActiveExperiment(true);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Stopped the active Void Roulette experiment.");
            return;
        }
        if (!matchActive || activeWorld == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Start a Void Roulette match before triggering experiments.");
            return;
        }
        ExperimentalEvent event = switch (action) {
            case "crosswind" -> ExperimentalEvent.CROSSWIND;
            case "lowgravity" -> ExperimentalEvent.LOW_GRAVITY;
            case "supply" -> ExperimentalEvent.SUPPLY_SURGE;
            case "swap" -> ExperimentalEvent.SWAP;
            case "suddendeath" -> ExperimentalEvent.SUDDEN_DEATH;
            default -> null;
        };
        if (event == null) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin voidroulette test "
                + "<status|toggle|on|off|infinitelives|crosswind|lowgravity|supply|swap|suddendeath|stop>");
            return;
        }
        if (event == ExperimentalEvent.SWAP && swapUsedThisMatch) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Swap has already been used this match.");
            return;
        }
        if (!queueExperimentalEvent(event, true)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not queue " + event.displayName + ".");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Queued " + event.displayName + " with a 10-second warning.");
    }

    private void setTestMode(CommandSender sender, boolean enabled) {
        if (testModeEnabled == enabled) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Void Roulette test mode is already "
                + (enabled ? "ON" : "OFF") + ".");
            return;
        }
        testModeEnabled = enabled;
        if (!enabled) {
            infiniteLivesEnabled = false;
        }
        sender.sendMessage(PREFIX + (enabled
            ? ChatColor.GREEN + "Void Roulette test controls ENABLED. Infinite Lives and manual event controls are available."
            : ChatColor.RED + "Void Roulette test controls DISABLED. Infinite Lives is off; standard events remain active."));
        String actor = sender instanceof Player player ? player.getName() : "console";
        plugin.getLogger().info("[VoidRoulette] Test mode " + (enabled ? "ENABLED" : "DISABLED") + " by " + actor);
    }

    private void setInfiniteLives(CommandSender sender, boolean enabled) {
        infiniteLivesEnabled = enabled;
        if (enabled) {
            for (UUID playerId : new ArrayList<>(alivePlayers)) {
                lives.put(playerId, STARTING_LIVES);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    applyLifeHealth(player, STARTING_LIVES);
                    updateMatchSidebar(player);
                }
            }
        }
        sender.sendMessage(PREFIX + (enabled ? ChatColor.GREEN : ChatColor.RED)
            + "Infinite Lives " + (enabled ? "ENABLED" : "DISABLED") + ".");
        String actor = sender instanceof Player player ? player.getName() : "console";
        plugin.getLogger().info("[VoidRoulette] Infinite Lives " + (enabled ? "ENABLED" : "DISABLED")
            + " by " + actor);
    }

    private void handleCleanupAdmin(CommandSender sender, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        List<Path> staleFolders = staleWorldFolders();
        long loadedStale = staleFolders.stream()
            .filter(path -> Bukkit.getWorld(path.getFileName().toString()) != null)
            .count();
        long purgeable = staleFolders.size() - loadedStale;
        if (action.equals("on") || action.equals("off")) {
            setAutoCleanup(sender, action.equals("on"));
            return;
        }
        if (action.equals("status")) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Void Roulette auto-cleanup: "
                + (autoCleanupEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Stale folders: "
                + ChatColor.WHITE + staleFolders.size() + ChatColor.GRAY + " total, "
                + ChatColor.WHITE + purgeable + ChatColor.GRAY + " purgeable, "
                + ChatColor.WHITE + loadedStale + ChatColor.GRAY + " loaded/skipped.");
            return;
        }
        if (!action.equals("purge")) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin voidroulette cleanup <status|purge|on|off>");
            return;
        }
        if (purgeable <= 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No unloaded stale Void Roulette folders to purge.");
            return;
        }
        for (Path folder : staleFolders) {
            String worldName = folder.getFileName().toString();
            if (Bukkit.getWorld(worldName) != null) {
                continue;
            }
            deleteFolderAsync(folder);
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Scheduled purge for " + purgeable
            + " unloaded Void Roulette world folder(s). Loaded worlds were skipped.");
    }

    private void setAutoCleanup(CommandSender sender, boolean enabled) {
        if (autoCleanupEnabled == enabled) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Void Roulette auto-cleanup is already "
                + (enabled ? "ON" : "OFF") + ".");
            return;
        }
        autoCleanupEnabled = enabled;
        plugin.getConfig().set("voidroulette.auto_cleanup", enabled);
        plugin.saveConfig();
        sender.sendMessage(PREFIX + (enabled
            ? ChatColor.GREEN + "Void Roulette auto-cleanup ENABLED."
            : ChatColor.RED + "Void Roulette auto-cleanup DISABLED. Match worlds stay loaded for inspection."));
        String actor = sender instanceof Player actorPlayer ? actorPlayer.getName() : "console";
        plugin.getLogger().info("[VoidRoulette] Auto-cleanup " + (enabled ? "ENABLED" : "DISABLED") + " by " + actor);
    }

    public void forceStart(CommandSender sender) {
        if (matchActive || matchStarting) {
            sender.sendMessage(PREFIX + ChatColor.RED + "A Void Roulette match is already running.");
            return;
        }
        if (queuedPlayers.isEmpty() && sender instanceof Player player) {
            joinForAdmin(player);
        }
        if (queuedPlayers.size() < MIN_FORCE_START_PLAYERS) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Need at least " + MIN_FORCE_START_PLAYERS + " players to force start Void Roulette.");
            return;
        }
        cancelQueueCountdown();
        startMatch(true);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Force started Void Roulette.");
    }

    public void stopMatch(CommandSender sender) {
        if (!matchActive && !matchStarting && queuedPlayers.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No active Void Roulette match found.");
            return;
        }
        if (managedRound != null) {
            ManagedMinigameRound.Request request = managedRound;
            stopMatchInternal();
            dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, "Void Roulette was force-stopped."));
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Void Roulette stopped.");
            return;
        }
        stopMatchInternal();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Void Roulette stopped.");
    }

    private void stopMatchInternal() {
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Void Roulette queue stopped.");
                releaseQueuedPlayer(player);
            }
        }
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cleanupPlayerForHub(player);
            }
        }
        for (UUID id : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cleanupPlayerForHub(player);
            }
        }
        String worldName = activeWorldName;
        cancelMatchTasks();
        queuedPlayers.clear();
        cleanupState(false);
        if (worldName != null) {
            cleanupWorld(worldName);
        }
    }

    private void startQueueCountdown() {
        cancelQueueCountdown();
        matchStarting = true;
        countdownSeconds = QUEUE_COUNTDOWN_SECONDS;
        queueCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queuedPlayers.size() < MIN_PLAYERS) {
                broadcastQueue(ChatColor.YELLOW + "Void Roulette countdown cancelled. Need " + MIN_PLAYERS + " players.");
                cancelQueueCountdown();
                updateQueuedSidebars();
                return;
            }
            if (countdownSeconds <= 0) {
                cancelQueueCountdown();
                startMatch(false);
                return;
            }
            if (countdownSeconds <= 5 || countdownSeconds % 5 == 0) {
                broadcastQueue(ChatColor.LIGHT_PURPLE + "Void Roulette starts in " + countdownSeconds + "s.");
            }
            updateQueuedSidebars();
            countdownSeconds--;
        }, 0L, 20L);
    }

    private void cancelQueueCountdown() {
        if (queueCountdownTask != null) {
            queueCountdownTask.cancel();
            queueCountdownTask = null;
        }
        matchStarting = false;
        countdownSeconds = 0;
    }

    private void startMatch(boolean forced) {
        matchStarting = true;
        List<UUID> participants = new ArrayList<>(queuedPlayers);
        int floor = managedRound != null ? 1 : (forced ? MIN_FORCE_START_PLAYERS : MIN_PLAYERS);
        if (participants.size() < floor) {
            matchStarting = false;
            return;
        }
        if (participants.size() > MAX_PLAYERS) {
            participants = participants.subList(0, MAX_PLAYERS);
        }
        activeWorld = createArenaWorld();
        if (activeWorld == null) {
            broadcastQueue(ChatColor.RED + "Void Roulette failed to create an arena.");
            matchStarting = false;
            return;
        }
        int pillarCount = pillarCountForPlayers(participants.size());
        activeArenaLayout = ArenaLayout.random();
        List<Location> spawns = buildArena(activeWorld, pillarCount, activeArenaLayout);
        activeWorldName = activeWorld.getName();
        queuedPlayers.clear();
        alivePlayers.clear();
        spectators.clear();
        managedEliminationOrder.clear();
        lives.clear();
        playerSpawns.clear();
        frozenSpawnLocations.clear();
        respawnFreezeLocations.clear();
        lifeLossInProgress.clear();
        recentKnockbackCauses.clear();
        recentKnockbackTimes.clear();
        voidbreakerSelectionBag.clear();
        currentVoidbreaker = null;
        clearVoidbreakerGlow();
        lastVoidbreaker = null;
        voidbreakerSucceeded = false;
        matchStartedAtMs = 0L;
        nextRandomDropAtMs = 0L;
        suddenDeathActive = false;
        swapUsedThisMatch = false;
        chaosEventBag.clear();
        stopActiveExperiment(false);
        matchPlayerCount = participants.size();
        World entryWorld = activeWorld;
        int[] expectedArrivals = {0};
        int[] arrivals = {0};
        boolean[] begun = {false};
        Runnable beginAfterArrival = () -> {
            if (begun[0] || !matchStarting || activeWorld != entryWorld) return;
            begun[0] = true;
            matchActive = true;
            matchStarting = false;
            broadcastMatch(ChatColor.LIGHT_PURPLE + "Void Roulette started on the "
                + ChatColor.WHITE + activeArenaLayout.displayName + ChatColor.LIGHT_PURPLE + " layout. Hold steady.");
            startStartFreezeCountdown();
        };

        for (int i = 0; i < participants.size(); i++) {
            UUID id = participants.get(i);
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (plugin.queueService() != null) {
                plugin.queueService().resetMatchRewardStats(id);
            }
            alivePlayers.add(id);
            lives.put(id, STARTING_LIVES);
            Location spawn = spawns.get(i);
            playerSpawns.put(id, spawn);
            frozenSpawnLocations.put(id, spawn.clone());
            if (!plugin.transitionTravel(player, () -> {
                if (!matchStarting || activeWorld != entryWorld || !player.isOnline()) return;
                plugin.menuService().preparePlayerForMatch(player);
                clearCycleInventory(player);
                player.teleport(spawn);
                player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                player.setFallDistance(0.0F);
                player.setGameMode(GameMode.SURVIVAL);
                applyLifeHealth(player, STARTING_LIVES);
                player.setFoodLevel(20);
                player.setSaturation(0.0F);
                player.sendTitle(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "VOID ROULETTE", ChatColor.GRAY + "Do not fall.", 10, 50, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7F, 1.2F);
                updateMatchSidebar(player);
                if (++arrivals[0] >= expectedArrivals[0]) beginAfterArrival.run();
            })) {
                plugin.getLogger().warning("[VoidRoulette] Entry fade already active for " + player.getName());
                for (UUID participantId : participants) {
                    Player participant = Bukkit.getPlayer(participantId);
                    if (participant != null && participant.isOnline()) cleanupPlayerForHub(participant);
                }
                cleanupState(false);
                cleanupWorld(entryWorld.getName());
                return;
            }
            expectedArrivals[0]++;
        }
        if (expectedArrivals[0] == 0) beginAfterArrival.run();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (matchStarting && activeWorld == entryWorld) beginAfterArrival.run();
        }, 20L);
    }

    private void startStartFreezeCountdown() {
        startFreezeActive = true;
        if (startFreezeTask != null) {
            startFreezeTask.cancel();
        }
        final int[] seconds = {START_FREEZE_SECONDS};
        startFreezeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive) {
                clearStartFreeze();
                return;
            }
            if (seconds[0] <= 0) {
                clearStartFreeze();
                forAlive(player -> {
                    player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "GO!", ChatColor.GRAY + "Survive the roulette.", 0, 20, 8);
                    HydraSounds.playGo(player);
                });
                matchStartedAtMs = System.currentTimeMillis();
                scheduleNextRandomDrop();
                rouletteTask = Bukkit.getScheduler().runTaskLater(plugin, this::startRouletteCycleTuned, 20L * 5L);
                startExperimentalSchedulers();
                return;
            }
            int shown = seconds[0];
            forAlive(player -> {
                Location frozen = frozenSpawnLocations.get(player.getUniqueId());
                if (frozen != null && player.getLocation().distanceSquared(frozen) > 0.01D) {
                    player.teleport(frozen);
                }
                player.sendTitle(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + shown, ChatColor.GRAY + "Void Roulette begins soon", 0, 18, 4);
                HydraSounds.playCountdownTick(player, shown);
            });
            seconds[0]--;
        }, 0L, 20L);
    }

    private void clearStartFreeze() {
        startFreezeActive = false;
        frozenSpawnLocations.clear();
        if (startFreezeTask != null) {
            startFreezeTask.cancel();
            startFreezeTask = null;
        }
    }

    private World createArenaWorld() {
        String worldName = "VoidRoulette-World-" + nextWorldNumber++;
        while (Bukkit.getWorld(worldName) != null || Files.exists(Bukkit.getWorldContainer().toPath().resolve(worldName))) {
            worldName = "VoidRoulette-World-" + nextWorldNumber++;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new EmptyVoidChunkGenerator(SPAWN_Y));
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            return null;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setTime(18000L);
        world.setSpawnLocation(0, SPAWN_Y, 0);
        world.getWorldBorder().setCenter(0.0D, 0.0D);
        world.getWorldBorder().setSize(ARENA_RADIUS * 2.0D + 12.0D);
        world.getWorldBorder().setDamageAmount(0.0D);
        return world;
    }

    private List<Location> buildArena(World world, int pillarCount, ArenaLayout layout) {
        List<Location> plannedSpawns = pillarSpawns(world, pillarCount, layout);
        for (Location spawn : plannedSpawns) {
            int cx = spawn.getBlockX();
            int cz = spawn.getBlockZ();
            // Single 1-wide bedrock column, PILLAR_HEIGHT tall, top capped at PLATFORM_Y, over
            // the void. 1-wide forces players to build/clutch instead of sitting on a safe pad.
            for (int y = 0; y < PILLAR_HEIGHT; y++) {
                world.getBlockAt(cx, PLATFORM_Y - y, cz).setType(Material.BEDROCK, false);
            }
            // Invisible light source placed ABOVE head height so the top is lit while the
            // spawn/standing column (Y=PLATFORM_Y+1..+2) stays clear. LIGHT is non-solid and
            // never has a collision box, so it cannot block spawning or movement regardless.
            world.getBlockAt(cx, PLATFORM_Y + 3, cz).setType(Material.LIGHT, false);
        }
        List<Location> safeSpawns = new ArrayList<>(plannedSpawns.size());
        for (Location spawn : plannedSpawns) {
            safeSpawns.add(highestSafePillarSpawn(
                world,
                spawn.getBlockX(),
                spawn.getBlockZ(),
                spawn.getYaw()
            ));
        }
        arenaPillars.clear();
        for (Location safeSpawn : safeSpawns) {
            arenaPillars.add(safeSpawn.clone());
        }
        return safeSpawns;
    }

    private List<Location> pillarSpawns(World world, int pillarCount, ArenaLayout layout) {
        List<Location> spawns = new ArrayList<>(pillarCount);
        double radius = pillarRingRadius(pillarCount);
        switch (layout) {
            case DOUBLE_RING -> addDoubleRingSpawns(spawns, world, pillarCount, radius);
            case DIAMOND -> addDiamondSpawns(spawns, world, pillarCount, radius);
            case RING -> addRingSpawns(spawns, world, pillarCount, radius, 0.0D);
        }
        return spawns;
    }

    private void addRingSpawns(List<Location> spawns, World world, int count, double radius, double angleOffset) {
        for (int i = 0; i < count; i++) {
            double angle = angleOffset + ((Math.PI * 2.0D * i) / count);
            addPillarSpawn(spawns, world, Math.cos(angle) * radius, Math.sin(angle) * radius);
        }
    }

    private void addDoubleRingSpawns(List<Location> spawns, World world, int count, double radius) {
        int outerCount = (count + 1) / 2;
        int innerCount = count - outerCount;
        addRingSpawns(spawns, world, outerCount, radius, 0.0D);
        if (innerCount > 0) {
            addRingSpawns(spawns, world, innerCount, Math.max(8.5D, radius * 0.67D), Math.PI / innerCount);
        }
    }

    private void addDiamondSpawns(List<Location> spawns, World world, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double perimeterPosition = (4.0D * i) / count;
            int side = Math.min(3, (int) Math.floor(perimeterPosition));
            double t = perimeterPosition - side;
            double x;
            double z;
            switch (side) {
                case 0 -> {
                    x = radius * t;
                    z = radius * (1.0D - t);
                }
                case 1 -> {
                    x = radius * (1.0D - t);
                    z = -radius * t;
                }
                case 2 -> {
                    x = -radius * t;
                    z = -radius * (1.0D - t);
                }
                default -> {
                    x = -radius * (1.0D - t);
                    z = radius * t;
                }
            }
            addPillarSpawn(spawns, world, x, z);
        }
    }

    private void addPillarSpawn(List<Location> spawns, World world, double x, double z) {
        int blockX = (int) Math.round(x);
        int blockZ = (int) Math.round(z);
        float yaw = (float) Math.toDegrees(Math.atan2(x, -z));
        spawns.add(new Location(world, blockX + 0.5D, SPAWN_Y, blockZ + 0.5D, yaw, 0.0F));
    }

    private Location highestSafePillarSpawn(World world, int blockX, int blockZ, float yaw) {
        if (world == null) {
            return new Location(null, blockX + 0.5D, SPAWN_Y, blockZ + 0.5D, yaw, 0.0F);
        }
        int[][] columnOffsets = {
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] offset : columnOffsets) {
            Location safe = highestCollisionSafeSpawnAtColumn(
                world,
                blockX + offset[0],
                blockZ + offset[1],
                yaw
            );
            if (safe != null) {
                return safe;
            }
        }
        return new Location(world, blockX + 0.5D, SPAWN_Y, blockZ + 0.5D, yaw, 0.0F);
    }

    private Location highestCollisionSafeSpawnAtColumn(World world, int blockX, int blockZ, float yaw) {
        int maxY = Math.min(world.getMaxHeight() - 3, PLATFORM_Y + 16);
        int minY = Math.max(world.getMinHeight(), ELIMINATION_Y);
        double[][] standingOffsets = {
            {0.0D, 0.0D},
            {0.28D, 0.0D}, {-0.28D, 0.0D}, {0.0D, 0.28D}, {0.0D, -0.28D},
            {0.28D, 0.28D}, {0.28D, -0.28D}, {-0.28D, 0.28D}, {-0.28D, -0.28D}
        };
        for (int y = maxY; y >= minY; y--) {
            Block base = world.getBlockAt(blockX, y, blockZ);
            List<BoundingBox> collisionBoxes = worldCollisionBoxes(base);
            if (collisionBoxes.isEmpty()) {
                continue;
            }
            Set<Double> surfaces = new HashSet<>();
            for (BoundingBox box : collisionBoxes) {
                surfaces.add(box.getMaxY());
            }
            List<Double> orderedSurfaces = new ArrayList<>(surfaces);
            orderedSurfaces.sort(Collections.reverseOrder());
            for (double surfaceY : orderedSurfaces) {
                for (double[] offset : standingOffsets) {
                    double x = blockX + 0.5D + offset[0];
                    double z = blockZ + 0.5D + offset[1];
                    BoundingBox body = new BoundingBox(
                        x - 0.29D,
                        surfaceY + 0.002D,
                        z - 0.29D,
                        x + 0.29D,
                        surfaceY + 1.802D,
                        z + 0.29D
                    );
                    BoundingBox supportProbe = new BoundingBox(
                        x - 0.29D,
                        surfaceY - 0.045D,
                        z - 0.29D,
                        x + 0.29D,
                        surfaceY + 0.001D,
                        z + 0.29D
                    );
                    if (!hasBlockCollision(world, body) && hasBlockCollision(world, supportProbe)) {
                        return new Location(world, x, surfaceY + 0.002D, z, yaw, 0.0F);
                    }
                }
            }
        }
        return null;
    }

    private boolean hasBlockCollision(World world, BoundingBox box) {
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX() - 0.0001D);
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(box.getMinY()));
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(box.getMaxY() - 0.0001D));
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ() - 0.0001D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (BoundingBox collision : worldCollisionBoxes(world.getBlockAt(x, y, z))) {
                        if (collision.overlaps(box)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<BoundingBox> worldCollisionBoxes(Block block) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (BoundingBox local : block.getCollisionShape().getBoundingBoxes()) {
            boxes.add(new BoundingBox(
                block.getX() + local.getMinX(),
                block.getY() + local.getMinY(),
                block.getZ() + local.getMinZ(),
                block.getX() + local.getMaxX(),
                block.getY() + local.getMaxY(),
                block.getZ() + local.getMaxZ()
            ));
        }
        return boxes;
    }

    private int pillarCountForPlayers(int playerCount) {
        return Math.min(MAX_PLAYERS, Math.max(6, playerCount + 2));
    }

    private double pillarRingRadius(int pillarCount) {
        if (pillarCount <= 6) {
            return 13.0D;
        }
        if (pillarCount <= 10) {
            return 13.0D + ((pillarCount - 6) * (3.0D / 4.0D));
        }
        if (pillarCount >= 16) {
            return 20.0D;
        }
        return 16.0D + ((pillarCount - 10) * (4.0D / 6.0D));
    }

    private void startRouletteCycleTuned() {
        if (!matchActive || alivePlayers.size() <= 1) {
            checkWin();
            return;
        }
        cancelVoidbreakerDeadline();
        List<UUID> eligible = new ArrayList<>();
        for (UUID id : alivePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline() && Objects.equals(player.getWorld().getName(), activeWorldName)) {
                eligible.add(id);
            }
        }
        if (eligible.isEmpty()) {
            scheduleNextCycle();
            return;
        }
        UUID selected = nextVoidbreaker(eligible);
        currentVoidbreaker = null;
        clearVoidbreakerGlow();
        voidbreakerSucceeded = false;
        voidbreakerAbilityUsed = false;
        cycleSecondsRemaining = VOIDBREAKER_REVEAL_SECONDS;
        forAlive(player -> {
            player.sendTitle(
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ROULETTE",
                ChatColor.GRAY + "Choosing the Voidbreaker...",
                0,
                35,
                5
            );
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 0.8F);
            updateMatchSidebar(player);
        });
        rouletteTask = Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> activateVoidbreakerCycle(selected),
            20L * VOIDBREAKER_REVEAL_SECONDS
        );
    }

    private void activateVoidbreakerCycle(UUID selected) {
        if (!matchActive || selected == null || !alivePlayers.contains(selected)) {
            scheduleNextCycle();
            return;
        }
        Player selectedPlayer = Bukkit.getPlayer(selected);
        if (selectedPlayer == null || !selectedPlayer.isOnline()
            || !Objects.equals(selectedPlayer.getWorld().getName(), activeWorldName)) {
            scheduleNextCycle();
            return;
        }
        currentVoidbreaker = selected;
        voidbreakerSucceeded = false;
        voidbreakerAbilityUsed = false;
        cycleSecondsRemaining = currentVoidbreakerHuntSeconds();

        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (id.equals(currentVoidbreaker)) {
                giveItem(player, createVoidbreakerMace());
                beginVoidbreakerGlow(player);
                player.sendTitle(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "VOIDBREAKER", ChatColor.GRAY + "You are the Voidbreaker", 5, 35, 5);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6F, 1.4F);
            }
            updateMatchSidebar(player);
        }
        broadcastMatch(ChatColor.DARK_PURPLE + displayName(currentVoidbreaker) + ChatColor.GRAY + " is the Voidbreaker.");
        voidbreakerDeadlineTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || currentVoidbreaker == null || !alivePlayers.contains(currentVoidbreaker)) {
                scheduleNextCycle();
                return;
            }
            cycleSecondsRemaining--;
            forAlive(this::updateMatchSidebar);
            if (cycleSecondsRemaining > 0) {
                return;
            }
            UUID exVoidbreaker = currentVoidbreaker;
            Player exPlayer = Bukkit.getPlayer(exVoidbreaker);
            if (exPlayer != null && alivePlayers.contains(exVoidbreaker)) {
                removeVoidbreakerItems(exPlayer);
                if (voidbreakerSucceeded) {
                    exPlayer.sendActionBar(Component.text(ChatColor.LIGHT_PURPLE + "Mace expired."));
                } else {
                    exPlayer.sendTitle(ChatColor.GRAY + "Hunt ended", ChatColor.DARK_GRAY + "Mace gone.", 5, 25, 5);
                    exPlayer.playSound(exPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.8F);
                    broadcastMatch(ChatColor.GRAY + displayName(exVoidbreaker) + " did not score with the mace.");
                }
            }
            clearVoidbreakerGlow();
            scheduleNextCycle();
        }, 20L, 20L);
    }

    private UUID nextVoidbreaker(List<UUID> eligible) {
        voidbreakerSelectionBag.removeIf(id -> !eligible.contains(id));
        if (voidbreakerSelectionBag.isEmpty()) {
            voidbreakerSelectionBag.addAll(eligible);
            Collections.shuffle(voidbreakerSelectionBag);
            if (voidbreakerSelectionBag.size() > 1 && Objects.equals(voidbreakerSelectionBag.get(0), lastVoidbreaker)) {
                Collections.swap(voidbreakerSelectionBag, 0, 1);
            }
        }
        UUID selected = voidbreakerSelectionBag.remove(0);
        lastVoidbreaker = selected;
        return selected;
    }

    private void scheduleNextCycle() {
        cancelVoidbreakerDeadline();
        clearVoidbreakerGlow();
        if (rouletteTask != null) {
            rouletteTask.cancel();
            rouletteTask = null;
        }
        currentVoidbreaker = null;
        cycleSecondsRemaining = currentRouletteIntervalSeconds() - currentVoidbreakerHuntSeconds() - VOIDBREAKER_REVEAL_SECONDS;
        forAlive(this::updateMatchSidebar);
        if (!checkWin()) {
            if (!suddenDeathActive) {
                startRandomChaosEvent();
            }
            rouletteTask = Bukkit.getScheduler().runTaskLater(plugin, this::startRouletteCycleTuned, 20L * cycleSecondsRemaining);
        }
    }

    private int currentRouletteIntervalSeconds() {
        return suddenDeathActive ? TEST_SUDDEN_DEATH_CYCLE_SECONDS : ROULETTE_INTERVAL_SECONDS;
    }

    private int currentVoidbreakerHuntSeconds() {
        return suddenDeathActive ? TEST_SUDDEN_DEATH_HUNT_SECONDS : VOIDBREAKER_LIMIT_SECONDS;
    }

    private void cancelVoidbreakerDeadline() {
        if (voidbreakerDeadlineTask != null) {
            voidbreakerDeadlineTask.cancel();
            voidbreakerDeadlineTask = null;
        }
    }

    private void beginVoidbreakerGlow(Player player) {
        clearVoidbreakerGlow();
        if (player != null && player.isOnline()) {
            player.setGlowing(true);
            glowingVoidbreaker = player.getUniqueId();
        }
    }

    private void clearVoidbreakerGlow() {
        UUID playerId = glowingVoidbreaker;
        glowingVoidbreaker = null;
        if (playerId != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.setGlowing(false);
        }
    }

    static String voidbreakerMaceDisplay(UUID viewerId, UUID holderId, boolean abilityUsed, int secondsRemaining) {
        if (viewerId == null || !viewerId.equals(holderId)) return null;
        return abilityUsed ? "USED" : Math.max(0, secondsRemaining) + "s";
    }

    private void startExperimentalSchedulers() {
        cancelExperimentalSchedulers();
        if (!matchActive) {
            return;
        }
        long elapsedSeconds = matchStartedAtMs <= 0L
            ? 0L
            : Math.max(0L, (System.currentTimeMillis() - matchStartedAtMs) / 1000L);
        long delaySeconds = Math.max(1L, TEST_SUDDEN_DEATH_SECONDS - elapsedSeconds);
        suddenDeathScheduleTask = Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> {
                if (matchActive) {
                    queueExperimentalEvent(ExperimentalEvent.SUDDEN_DEATH, true);
                }
            },
            20L * delaySeconds
        );
    }

    private void cancelExperimentalSchedulers() {
        cancelExperimentalWarning();
        if (suddenDeathScheduleTask != null) {
            suddenDeathScheduleTask.cancel();
            suddenDeathScheduleTask = null;
        }
    }

    private void startRandomChaosEvent() {
        if (!matchActive || suddenDeathActive
            || activeExperimentalEvent != null || pendingExperimentalEvent != null) {
            return;
        }
        if (swapUsedThisMatch) {
            chaosEventBag.remove(ExperimentalEvent.SWAP);
        }
        if (chaosEventBag.isEmpty()) {
            chaosEventBag.add(ExperimentalEvent.CROSSWIND);
            chaosEventBag.add(ExperimentalEvent.LOW_GRAVITY);
            chaosEventBag.add(ExperimentalEvent.SUPPLY_SURGE);
            if (!swapUsedThisMatch) {
                chaosEventBag.add(ExperimentalEvent.SWAP);
            }
            Collections.shuffle(chaosEventBag);
        }
        queueExperimentalEvent(chaosEventBag.remove(0), false);
    }

    private boolean queueExperimentalEvent(ExperimentalEvent event, boolean replaceCurrent) {
        if (event == null || !matchActive || activeWorld == null || suddenDeathActive
            || (event == ExperimentalEvent.SWAP && swapUsedThisMatch)) {
            return false;
        }
        if (activeExperimentalEvent != null || pendingExperimentalEvent != null) {
            if (!replaceCurrent) {
                return false;
            }
            stopActiveExperiment(true);
        }
        startExperimentalWarning(event);
        return pendingExperimentalEvent == event;
    }

    private void startExperimentalWarning(ExperimentalEvent event) {
        if (event == null || !matchActive || suddenDeathActive) {
            return;
        }
        cancelExperimentalWarning();
        pendingExperimentalEvent = event;
        experimentalWarningSecondsRemaining = TEST_EVENT_WARNING_SECONDS;
        updateExperimentalWarningBossBar();
        experimentalWarningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || suddenDeathActive || pendingExperimentalEvent != event) {
                cancelExperimentalWarning();
                return;
            }
            experimentalWarningSecondsRemaining--;
            if (experimentalWarningSecondsRemaining <= 0) {
                experimentalWarningTask.cancel();
                experimentalWarningTask = null;
                pendingExperimentalEvent = null;
                hideExperimentalBossBar();
                startExperimentalEvent(event);
                return;
            }
            updateExperimentalWarningBossBar();
        }, 20L, 20L);
    }

    private void updateExperimentalWarningBossBar() {
        if (pendingExperimentalEvent == null) {
            return;
        }
        showExperimentalBossBar(
            ChatColor.LIGHT_PURPLE + "Next Event: " + ChatColor.WHITE + pendingExperimentalEvent.displayName
                + ChatColor.GRAY + " \u2022 " + experimentalWarningSecondsRemaining + "s",
            BarColor.PURPLE,
            experimentalWarningSecondsRemaining / (double) TEST_EVENT_WARNING_SECONDS
        );
    }

    private void cancelExperimentalWarning() {
        if (experimentalWarningTask != null) {
            experimentalWarningTask.cancel();
            experimentalWarningTask = null;
        }
        pendingExperimentalEvent = null;
        experimentalWarningSecondsRemaining = 0;
    }

    private boolean startExperimentalEvent(ExperimentalEvent event) {
        if (event == null || !matchActive || activeWorld == null
            || (event == ExperimentalEvent.SWAP && swapUsedThisMatch)) {
            return false;
        }
        if (suddenDeathActive && event != ExperimentalEvent.SUDDEN_DEATH) {
            return false;
        }
        if (activeExperimentalEvent != null) {
            return false;
        }
        cancelExperimentalWarning();
        if (event == ExperimentalEvent.SUDDEN_DEATH) {
            startSuddenDeath();
            return true;
        }

        activeExperimentalEvent = event;
        if (event == ExperimentalEvent.SWAP) {
            // Consume on activation, not warning: cancellation can requeue, execution cannot retry.
            swapUsedThisMatch = true;
            chaosEventBag.remove(ExperimentalEvent.SWAP);
            try {
                announceExperimentalEvent(event);
                return swapPlayers();
            } finally {
                finishExperimentalEvent(event);
            }
        }
        announceExperimentalEvent(event);
        switch (event) {
            case CROSSWIND -> startCrosswind();
            case LOW_GRAVITY -> startLowGravity();
            case SUPPLY_SURGE -> startSupplySurge();
            case SWAP, SUDDEN_DEATH -> {
            }
        }
        forAlive(this::updateMatchSidebar);
        return true;
    }

    private boolean swapPlayers() {
        World world = activeWorld;
        List<Player> players = new ArrayList<>();
        Map<UUID, Location> origins = new HashMap<>();
        Set<Vector> coordinates = new HashSet<>();
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (!isSwapParticipant(player, world)) {
                continue;
            }
            Location origin = player.getLocation().clone();
            if (!coordinates.add(origin.toVector())) {
                broadcastMatch(PREFIX + ChatColor.YELLOW + "Swap skipped: players share a position.");
                return false;
            }
            players.add(player);
            origins.put(id, origin);
        }
        if (players.size() < 2 || !isSwapRunning(world)) {
            return false;
        }

        // A shuffled cycle is a bijection without fixed points. Every XYZ is captured first.
        Collections.shuffle(players);
        Map<UUID, Location> originalPillars = new HashMap<>();
        for (Player player : players) {
            Location pillar = playerSpawns.get(player.getUniqueId());
            if (pillar != null) originalPillars.put(player.getUniqueId(), pillar.clone());
        }
        List<Location> destinations = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Location own = origins.get(players.get(i).getUniqueId());
            Location destination = origins.get(players.get((i + 1) % players.size()).getUniqueId()).clone();
            destination.setYaw(own.getYaw());
            destination.setPitch(own.getPitch());
            destinations.add(destination);
        }
        List<Player> attempted = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (!isSwapRunning(world) || players.stream().anyMatch(player -> !isSwapParticipant(player, world))) {
                rollbackSwap(attempted, origins, world);
                return false;
            }
            Player player = players.get(i);
            attempted.add(player);
            if (!teleportSwapPlayer(player, destinations.get(i), world)) {
                rollbackSwap(attempted, origins, world);
                return false;
            }
        }
        // Teleport listeners can also move an earlier participant during a later teleport.
        for (int i = 0; i < players.size(); i++) {
            if (!isSwapRunning(world) || !isSwapParticipant(players.get(i), world)
                || !players.get(i).getLocation().equals(destinations.get(i))) {
                rollbackSwap(attempted, origins, world);
                return false;
            }
        }
        for (Player player : players) {
            player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
            player.setFallDistance(0.0F);
        }
        // The destination owner's pillar follows the position transfer. Commit only after
        // every teleport succeeds so a cancelled swap leaves respawn/Voidbreaker anchors intact.
        for (int i = 0; i < players.size(); i++) {
            UUID recipient = players.get(i).getUniqueId();
            UUID previousOwner = players.get((i + 1) % players.size()).getUniqueId();
            Location pillar = originalPillars.get(previousOwner);
            if (pillar == null) playerSpawns.remove(recipient);
            else playerSpawns.put(recipient, pillar.clone());
        }
        return true;
    }

    private boolean isSwapRunning(World world) {
        return matchActive && world != null && Objects.equals(activeWorld, world)
            && activeExperimentalEvent == ExperimentalEvent.SWAP && !suddenDeathActive;
    }

    private boolean isSwapParticipant(Player player, World world) {
        return player != null && player.isOnline() && !player.isDead() && player.getHealth() > 0.0D
            && alivePlayers.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId())
            && player.getGameMode() != GameMode.SPECTATOR && world != null
            && Objects.equals(player.getWorld(), world);
    }

    private boolean teleportSwapPlayer(Player player, Location destination, World world) {
        try {
            if (!player.teleport(destination.clone()) || !isSwapRunning(world) || !isSwapParticipant(player, world)) {
                return false;
            }
            player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
            player.setFallDistance(0.0F);
            if (!player.getLocation().equals(destination)) {
                return false;
            }
            // Preserve existing freeze durations without snapping players back on their next move.
            // The grace-expiry task owns this exact Location object as its life token.
            // Replacing it during Swap would strand the player frozen and invulnerable.
            respawnFreezeLocations.computeIfPresent(player.getUniqueId(), (id, old) -> {
                old.setWorld(destination.getWorld());
                old.setX(destination.getX());
                old.setY(destination.getY());
                old.setZ(destination.getZ());
                old.setYaw(destination.getYaw());
                old.setPitch(destination.getPitch());
                return old;
            });
            frozenSpawnLocations.computeIfPresent(player.getUniqueId(), (id, old) -> destination.clone());
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[VoidRoulette] Swap teleport failed for " + player.getUniqueId()
                + ": " + exception.getMessage());
            return false;
        }
    }

    private void rollbackSwap(List<Player> attempted, Map<UUID, Location> origins, World world) {
        // Bukkit teleports are cancellable, so rollback is best-effort and never revives departed players.
        for (int i = attempted.size() - 1; i >= 0; i--) {
            Player player = attempted.get(i);
            if (!isSwapRunning(world)) {
                return;
            }
            if (!isSwapParticipant(player, world)) {
                continue;
            }
            Location origin = origins.get(player.getUniqueId());
            if (!player.getLocation().equals(origin) && !teleportSwapPlayer(player, origin, world)) {
                plugin.getLogger().warning("[VoidRoulette] Swap rollback failed for " + player.getUniqueId());
            }
        }
    }

    private void announceExperimentalEvent(ExperimentalEvent event) {
        broadcastMatch(ChatColor.DARK_PURPLE + "Roulette Event: " + ChatColor.WHITE + event.displayName);
        forAlive(player -> {
            player.sendTitle(
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + event.displayName.toUpperCase(Locale.ROOT),
                ChatColor.GRAY + event.description,
                5,
                30,
                5
            );
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.75F, 1.35F);
        });
    }

    private void startCrosswind() {
        Vector[] directions = {
            new Vector(1.0D, 0.0D, 0.0D),
            new Vector(-1.0D, 0.0D, 0.0D),
            new Vector(0.0D, 0.0D, 1.0D),
            new Vector(0.0D, 0.0D, -1.0D)
        };
        Vector direction = directions[ThreadLocalRandom.current().nextInt(directions.length)];
        startTimedExperimentalTask(ExperimentalEvent.CROSSWIND, 10L, 20L * TEST_EVENT_DURATION_SECONDS, () ->
            forAlive(player -> {
                Vector velocity = player.getVelocity().add(direction.clone().multiply(0.12D));
                double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
                if (horizontal > 1.1D) {
                    velocity.setX(velocity.getX() * (1.1D / horizontal));
                    velocity.setZ(velocity.getZ() * (1.1D / horizontal));
                }
                player.setVelocity(velocity);
                spawnPersonalEffectParticle(player, Particle.CLOUD,
                    player.getLocation().add(0.0D, 0.5D, 0.0D), 3, 1,
                    0.35D, 0.2D, 0.35D, 0.01D);
            })
        );
    }

    private void startLowGravity() {
        startTimedExperimentalTask(ExperimentalEvent.LOW_GRAVITY, 10L, 20L * TEST_EVENT_DURATION_SECONDS, () ->
            forAlive(player -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 15, 1, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 15, 0, false, true, true));
                spawnPersonalEffectParticle(player, Particle.END_ROD,
                    player.getLocation().add(0.0D, 0.2D, 0.0D), 2, 1,
                    0.25D, 0.1D, 0.25D, 0.01D);
            })
        );
    }

    private void startSupplySurge() {
        Map<UUID, Integer> dropsRemaining = new HashMap<>();
        for (UUID playerId : new ArrayList<>(alivePlayers)) {
            dropsRemaining.put(playerId, ThreadLocalRandom.current().nextInt(3, 11));
        }
        startTimedExperimentalTask(ExperimentalEvent.SUPPLY_SURGE, 5L, 20L * 3L, () -> {
            for (UUID playerId : new ArrayList<>(alivePlayers)) {
                int remaining = dropsRemaining.getOrDefault(playerId, 0);
                if (remaining <= 0) {
                    continue;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && Objects.equals(player.getWorld(), activeWorld)) {
                    giveItem(player, createRandomRewardItem());
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25F, 1.7F);
                    dropsRemaining.put(playerId, remaining - 1);
                }
            }
        });
    }

    private void startSuddenDeath() {
        stopActiveExperiment(false);
        cancelExperimentalWarning();
        if (suddenDeathScheduleTask != null) {
            suddenDeathScheduleTask.cancel();
            suddenDeathScheduleTask = null;
        }
        activeExperimentalEvent = ExperimentalEvent.SUDDEN_DEATH;
        suddenDeathActive = true;
        suddenDeathStageIndex = 0;
        risingVoidY = ELIMINATION_Y;
        lastRemovedVoidLayer = ELIMINATION_Y;
        risingVoidWarningCooldownUntilMs.clear();
        if (randomDropTask != null) {
            randomDropTask.cancel();
            randomDropTask = null;
        }
        if (activeWorld != null) {
            activeWorld.setTime(18000L);
            activeWorld.getWorldBorder().setCenter(0.0D, 0.0D);
            activeWorld.getWorldBorder().setDamageBuffer(0.0D);
            activeWorld.getWorldBorder().setDamageAmount(1.0D);
            activeWorld.getWorldBorder().setSize(
                TEST_SUDDEN_DEATH_BORDER_STAGES[0],
                TEST_SUDDEN_DEATH_STAGE_SECONDS
            );
        }
        announceExperimentalEvent(ExperimentalEvent.SUDDEN_DEATH);
        broadcastMatch(ChatColor.RED
            + "Random drops stopped. Death is permanent. The border and void are closing in.");
        startSuddenDeathTask();
        forAlive(this::updateMatchSidebar);
    }

    private void startSuddenDeathTask() {
        if (suddenDeathTask != null) {
            suddenDeathTask.cancel();
        }
        long stageTicks = 20L * TEST_SUDDEN_DEATH_STAGE_SECONDS;
        long totalTicks = stageTicks * TEST_SUDDEN_DEATH_BORDER_STAGES.length;
        final long[] elapsedTicks = {0L};
        suddenDeathTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || !suddenDeathActive || activeWorld == null) {
                if (suddenDeathTask != null) {
                    suddenDeathTask.cancel();
                    suddenDeathTask = null;
                }
                hideExperimentalBossBar();
                return;
            }
            elapsedTicks[0] = Math.min(totalTicks, elapsedTicks[0] + 10L);
            int nextStage = Math.min(
                TEST_SUDDEN_DEATH_BORDER_STAGES.length - 1,
                (int) (elapsedTicks[0] / stageTicks)
            );
            if (nextStage > suddenDeathStageIndex) {
                suddenDeathStageIndex = nextStage;
                activeWorld.getWorldBorder().setSize(
                    TEST_SUDDEN_DEATH_BORDER_STAGES[suddenDeathStageIndex],
                    TEST_SUDDEN_DEATH_STAGE_SECONDS
                );
                broadcastMatch(ChatColor.RED + "Sudden Death border: " + ChatColor.WHITE
                    + formatBorderSize(TEST_SUDDEN_DEATH_BORDER_STAGES[suddenDeathStageIndex]) + "x"
                    + formatBorderSize(TEST_SUDDEN_DEATH_BORDER_STAGES[suddenDeathStageIndex]));
            }
            double progress = elapsedTicks[0] / (double) totalTicks;
            risingVoidY = ELIMINATION_Y + ((PLATFORM_Y - ELIMINATION_Y) * progress);
            removeRisingVoidLayers();
            enforceRisingVoid();
            updateSuddenDeathBossBar(progress);
        }, 0L, 10L);
    }

    private String formatBorderSize(double size) {
        return String.valueOf((int) Math.round(size));
    }

    private void removeRisingVoidLayers() {
        if (activeWorld == null) {
            return;
        }
        int targetLayer = Math.min(PLATFORM_Y - 1, (int) Math.ceil(risingVoidY) - 1);
        if (targetLayer <= lastRemovedVoidLayer) {
            return;
        }
        for (int y = lastRemovedVoidLayer + 1; y <= targetLayer; y++) {
            for (Location pillar : arenaPillars) {
                Block block = activeWorld.getBlockAt(pillar.getBlockX(), y, pillar.getBlockZ());
                if (!block.getType().isAir()) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        int previousLayer = lastRemovedVoidLayer;
        playerPlacedBlocks.removeIf(position -> {
            if (position.y <= previousLayer || position.y > targetLayer) {
                return false;
            }
            Block block = activeWorld.getBlockAt(position.x, position.y, position.z);
            if (!block.getType().isAir()) {
                block.setType(Material.AIR, false);
            }
            return true;
        });
        lastRemovedVoidLayer = targetLayer;
    }

    private void enforceRisingVoid() {
        long now = System.currentTimeMillis();
        for (UUID playerId : new ArrayList<>(alivePlayers)) {
            if (!matchActive) {
                return;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || !Objects.equals(player.getWorld(), activeWorld)) {
                continue;
            }
            double blocksAboveVoid = player.getLocation().getY() - risingVoidY;
            if (blocksAboveVoid <= 0.0D) {
                loseLifeAndRespawn(player, recentCause(playerId), "was consumed by the rising void");
                continue;
            }
            showRisingVoidParticles(player);
            if (blocksAboveVoid > 6.0D || risingVoidWarningCooldownUntilMs.getOrDefault(playerId, 0L) > now) {
                continue;
            }
            int safeBlocks = Math.max(0, (int) Math.ceil(blocksAboveVoid));
            player.sendActionBar(Component.text(HydraIcons.EXCLAMATION + " " + ChatColor.RED
                + "THE VOID IS " + safeBlocks + " BLOCKS BELOW YOU"));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.65F,
                blocksAboveVoid <= 3.0D ? 0.75F : 1.0F);
            if (blocksAboveVoid <= 3.0D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false, false));
            }
            risingVoidWarningCooldownUntilMs.put(playerId, now + 1000L);
        }
    }

    private void showRisingVoidParticles(Player player) {
        int effectsLevel = hydraEffectsLevel(player);
        if (effectsLevel <= 0) {
            return;
        }
        int count = effectsLevel >= 2 ? 8 : 3;
        double radius = effectsLevel >= 2 ? 3.0D : 2.0D;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i) / count;
            Location particleLocation = new Location(
                player.getWorld(),
                player.getLocation().getX() + Math.cos(angle) * radius,
                risingVoidY + 0.15D,
                player.getLocation().getZ() + Math.sin(angle) * radius
            );
            player.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLocation, 1, 0.05D, 0.02D, 0.05D, 0.0D);
        }
    }

    private void updateSuddenDeathBossBar(double progress) {
        showExperimentalBossBar(
            ChatColor.RED + "VOID RISING " + ChatColor.GRAY + "\u2022 " + ChatColor.WHITE
                + "SAFE ABOVE Y " + (int) Math.ceil(risingVoidY),
            BarColor.RED,
            Math.max(0.0D, 1.0D - progress)
        );
    }

    private void startTimedExperimentalTask(
        ExperimentalEvent event,
        long intervalTicks,
        long durationTicks,
        Runnable tick
    ) {
        final long[] elapsed = {0L};
        final BukkitTask[] holder = new BukkitTask[1];
        int totalSeconds = Math.max(1, (int) Math.ceil(durationTicks / 20.0D));
        updateActiveEventBossBar(event, totalSeconds, totalSeconds);
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || activeExperimentalEvent != event) {
                holder[0].cancel();
                if (activeExperimentalTask == holder[0]) {
                    activeExperimentalTask = null;
                }
                hideExperimentalBossBar();
                return;
            }
            int secondsRemaining = Math.max(0, (int) Math.ceil((durationTicks - elapsed[0]) / 20.0D));
            updateActiveEventBossBar(event, secondsRemaining, totalSeconds);
            tick.run();
            elapsed[0] += intervalTicks;
            if (elapsed[0] >= durationTicks) {
                holder[0].cancel();
                if (activeExperimentalTask == holder[0]) {
                    activeExperimentalTask = null;
                }
                finishExperimentalEvent(event);
            }
        }, 0L, intervalTicks);
        activeExperimentalTask = holder[0];
    }

    private void finishExperimentalEvent(ExperimentalEvent event) {
        if (activeExperimentalEvent != event || event == ExperimentalEvent.SUDDEN_DEATH) {
            return;
        }
        activeExperimentalEvent = null;
        hideExperimentalBossBar();
        forAlive(this::updateMatchSidebar);
    }

    private void stopActiveExperiment(boolean restoreBorder) {
        cancelExperimentalWarning();
        if (activeExperimentalTask != null) {
            activeExperimentalTask.cancel();
            activeExperimentalTask = null;
        }
        if (suddenDeathTask != null) {
            suddenDeathTask.cancel();
            suddenDeathTask = null;
        }
        activeExperimentalEvent = null;
        if (suddenDeathActive) {
            suddenDeathActive = false;
        }
        risingVoidWarningCooldownUntilMs.clear();
        risingVoidY = ELIMINATION_Y;
        lastRemovedVoidLayer = ELIMINATION_Y;
        hideExperimentalBossBar();
        forAlive(player -> player.removePotionEffect(PotionEffectType.DARKNESS));
        if (restoreBorder && activeWorld != null) {
            restoreGeneratedPillars();
            activeWorld.getWorldBorder().setCenter(0.0D, 0.0D);
            activeWorld.getWorldBorder().setSize(ARENA_RADIUS * 2.0D + 12.0D);
            activeWorld.getWorldBorder().setDamageAmount(0.0D);
            activeWorld.getWorldBorder().setDamageBuffer(5.0D);
            if (matchActive && randomDropTask == null) {
                scheduleNextRandomDrop();
            }
        }
        forAlive(this::updateMatchSidebar);
    }

    private void stopExperimentalSystems(boolean restoreBorder) {
        cancelExperimentalSchedulers();
        stopActiveExperiment(restoreBorder);
    }

    private void restoreGeneratedPillars() {
        if (activeWorld == null) {
            return;
        }
        int bottomY = PLATFORM_Y - PILLAR_HEIGHT + 1;
        for (Location pillar : arenaPillars) {
            int x = pillar.getBlockX();
            int z = pillar.getBlockZ();
            for (int y = bottomY; y <= PLATFORM_Y; y++) {
                activeWorld.getBlockAt(x, y, z).setType(Material.BEDROCK, false);
            }
        }
    }

    private void updateActiveEventBossBar(ExperimentalEvent event, int secondsRemaining, int totalSeconds) {
        showExperimentalBossBar(
            ChatColor.LIGHT_PURPLE + event.displayName + " Active " + ChatColor.GRAY + "\u2022 "
                + ChatColor.WHITE + secondsRemaining + "s",
            event == ExperimentalEvent.SUPPLY_SURGE ? BarColor.YELLOW : BarColor.PURPLE,
            secondsRemaining / (double) Math.max(1, totalSeconds)
        );
    }

    private void showExperimentalBossBar(String title, BarColor color, double progress) {
        if (experimentalBossBar == null) {
            experimentalBossBar = Bukkit.createBossBar("", color, BarStyle.SEGMENTED_10);
        }
        experimentalBossBar.setTitle(title);
        experimentalBossBar.setColor(color);
        experimentalBossBar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));
        experimentalBossBar.setVisible(true);
        syncExperimentalBossBarPlayers();
    }

    private void syncExperimentalBossBarPlayers() {
        if (experimentalBossBar == null || !experimentalBossBar.isVisible()) {
            return;
        }
        Set<Player> desired = new HashSet<>();
        for (UUID playerId : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                desired.add(player);
            }
        }
        for (UUID playerId : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                desired.add(player);
            }
        }
        for (Player player : desired) {
            if (!experimentalBossBar.getPlayers().contains(player)) {
                experimentalBossBar.addPlayer(player);
            }
        }
        for (Player player : new ArrayList<>(experimentalBossBar.getPlayers())) {
            if (!desired.contains(player)) {
                experimentalBossBar.removePlayer(player);
            }
        }
    }

    private void hideExperimentalBossBar() {
        if (experimentalBossBar == null) {
            return;
        }
        experimentalBossBar.removeAll();
        experimentalBossBar.setVisible(false);
    }

    private int hydraEffectsLevel(Player player) {
        MenuService menu = plugin.menuService();
        return menu == null ? 2 : menu.hydraEffectsLevel(player);
    }

    private void spawnPersonalEffectParticle(
        Player player,
        Particle particle,
        Location location,
        int fullCount,
        int reducedCount,
        double offsetX,
        double offsetY,
        double offsetZ,
        double extra
    ) {
        int level = hydraEffectsLevel(player);
        int count = level >= 2 ? fullCount : level == 1 ? reducedCount : 0;
        if (count > 0) {
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    private void clearCycleInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[] {null, null, null, null});
        inventory.setItemInOffHand(null);
    }

    /** Ordinary rolled maces and wind charges are not part of the temporary kit. */
    private void removeVoidbreakerItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && isVoidbreakerMace(stack)) {
                inventory.setItem(i, null);
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && isVoidbreakerMace(offhand)) {
            inventory.setItemInOffHand(null);
        }
    }

    public ItemStack utilityBookVoidbreakerPreview() {
        return createVoidbreakerMace();
    }

    private ItemStack createVoidbreakerMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Voidbreaker Mace");
        meta.setLore(List.of(
            ChatColor.DARK_GRAY + "Voidbreaker",
            "",
            ChatColor.AQUA + "Description:",
            ChatColor.GRAY + "A mace forged from the relics of the past.",
            "",
            ChatColor.LIGHT_PURPLE + "Ability:",
            ChatColor.GRAY + "RMB to teleport 10 blocks in the direction you are looking.",
            ChatColor.GRAY + "This ability is single-use and can only be activated once."
        ));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("hydra:voidbreaker_mace"));
        meta.setCustomModelDataComponent(customModelData);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "voidbreaker_mace");
        item.setItemMeta(meta);
        return item;
    }

    private void scheduleNextRandomDrop() {
        if (randomDropTask != null) {
            randomDropTask.cancel();
            randomDropTask = null;
        }
        if (!matchActive) {
            return;
        }
        long delay = currentRandomDropIntervalTicks();
        nextRandomDropAtMs = System.currentTimeMillis() + (delay * 50L);
        randomDropTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            randomDropTask = null;
            if (!matchActive) {
                return;
            }
            grantRandomDrops();
            scheduleNextRandomDrop();
        }, delay);
    }

    private long currentRandomDropIntervalTicks() {
        long elapsed = matchStartedAtMs <= 0L ? 0L : System.currentTimeMillis() - matchStartedAtMs;
        if (elapsed >= ENDGAME_START_MS) {
            return ENDGAME_DROP_INTERVAL_TICKS;
        }
        if (elapsed >= MIDGAME_START_MS) {
            return MIDGAME_DROP_INTERVAL_TICKS;
        }
        return OPENING_DROP_INTERVAL_TICKS;
    }

    private void grantRandomDrops() {
        if (!matchActive || alivePlayers.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || spectators.contains(id)) {
                continue;
            }
            ItemStack reward = createRandomRewardItem();
            giveItem(player, reward);
            player.sendActionBar(Component.text(ChatColor.LIGHT_PURPLE + "Drop: " + displayItemName(reward)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.55F, 1.35F);
        }
    }

    private ItemStack createRandomRewardItem() {
        return new ItemStack(RANDOM_ITEM_POOL[ThreadLocalRandom.current().nextInt(RANDOM_ITEM_POOL.length)]);
    }

    static Material[] buildRandomItemPool() {
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && !material.isAir() && material.isItem()) {
                materials.add(material);
            }
        }
        return materials.toArray(new Material[0]);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            player.sendActionBar(Component.text(ChatColor.RED + "Inventory full."));
        }
    }

    private String displayItemName(ItemStack item) {
        if (item == null) {
            return "Nothing";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return ChatColor.WHITE + prettyMaterialName(item.getType());
    }

    private String prettyMaterialName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isActivePlayer(player.getUniqueId()) && !spectators.contains(player.getUniqueId())) {
            return;
        }
        if (startFreezeActive && alivePlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
            return;
        }
        if (spectators.contains(player.getUniqueId())) {
            handleSpectatorInteract(event);
            return;
        }
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        String type = itemType(item);
        if (type == null) {
            if (item != null && item.getType().name().endsWith("_SPAWN_EGG")) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && event.getClickedBlock() != null
                    && !isInsideArena(event.getClickedBlock().getRelative(event.getBlockFace()).getLocation())) {
                    event.setCancelled(true);
                }
                return;
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        switch (type) {
            case "voidbreaker_mace" -> {
                event.setCancelled(true);
                if (!player.getUniqueId().equals(currentVoidbreaker)) {
                    return;
                }
                if (voidbreakerAbilityUsed) {
                    player.sendActionBar(Component.text(ChatColor.RED + "Voidbreaker USED"));
                    return;
                }
                if (blink(player, 10.0D)) {
                    voidbreakerAbilityUsed = true;
                    updateMatchSidebar(player);
                }
            }
            default -> {
            }
        }
    }

    private boolean blink(Player player, double distance) {
        Location from = player.getLocation();
        Vector direction = from.getDirection().normalize();
        Location destination = findSafeBlinkDestination(from, direction, distance);
        if (destination == null) {
            player.sendActionBar(Component.text(ChatColor.RED + "Blink blocked."));
            return false;
        }
        player.teleport(destination);
        player.getWorld().spawnParticle(Particle.PORTAL, destination, 30, 0.35D, 0.6D, 0.35D, 0.03D);
        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.3F);
        return true;
    }

    private Location findSafeBlinkDestination(Location from, Vector direction, double maxDistance) {
        if (from == null || from.getWorld() == null || direction == null || direction.lengthSquared() <= 0.0001D) {
            return null;
        }
        Vector stepDirection = direction.clone().normalize();
        Location lastSafe = null;
        for (double traveled = 0.5D; traveled <= maxDistance + 0.0001D; traveled += 0.5D) {
            Location candidate = from.clone().add(stepDirection.clone().multiply(traveled));
            candidate.setY(Math.max(ELIMINATION_Y + 2, candidate.getY()));
            if (!isInsideArena(candidate)) {
                break;
            }
            if (!isSafe(candidate)) {
                break;
            }
            lastSafe = candidate;
        }
        if (lastSafe == null || lastSafe.distanceSquared(from) < 0.75D) {
            return null;
        }
        lastSafe.setYaw(from.getYaw());
        lastSafe.setPitch(from.getPitch());
        return lastSafe;
    }

    private boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return (feet.getType().isAir() || feet.isPassable()) && (head.getType().isAir() || head.isPassable());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location safe = spectatorReturnLocation(player.getUniqueId());
                if (safe != null) {
                    player.teleport(safe);
                    player.setFallDistance(0.0F);
                }
            }
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId())) {
            return;
        }
        if (matchStarting || startFreezeActive || !matchActive || player.getWorld() != activeWorld) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            loseLifeAndRespawn(player, recentCause(player.getUniqueId()), "fell into the void");
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            player.setHealth(Math.max(1.0D, player.getHealth()));
            loseLifeAndRespawn(player, recentCause(player.getUniqueId()), "was defeated");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isInVoidRoulette(player.getUniqueId())) {
            return;
        }
        if (isInActiveVoidRouletteMatch(player.getUniqueId())) {
            event.deathMessage(null);
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target) || !alivePlayers.contains(target.getUniqueId())) {
            return;
        }
        if (isSpectatorDamageSource(event.getDamager())) {
            event.setCancelled(true);
            return;
        }
        Player attacker = event.getDamager() instanceof Player direct ? direct
            : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                ? shooter : null;
        if (attacker == null || !alivePlayers.contains(attacker.getUniqueId())) {
            return;
        }
        if (!Objects.equals(attacker.getWorld().getName(), activeWorldName)) {
            return;
        }
        if (startFreezeActive) {
            event.setCancelled(true);
            return;
        }
        if (plugin.teamProtectionService().areProtectedTeammates(attacker, target)) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile instanceof Trident) event.setDamage(6.0D);
            registerCause(target.getUniqueId(), attacker.getUniqueId());
            return;
        }
        if (attacker.getUniqueId().equals(currentVoidbreaker) && isVoidbreakerMace(attacker.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            registerCause(target.getUniqueId(), attacker.getUniqueId());
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 0.9F, 1.2F);
            loseLifeAndRespawn(target, attacker.getUniqueId(), "was smashed by the Voidbreaker");
            return;
        }
        event.setDamage(attacker.getInventory().getItemInMainHand().getType() == Material.TRIDENT
            ? 6.0D : Math.min(event.getDamage(), 1.0D));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTridentKnockback(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
        if (!(event.getEntity() instanceof Player target) || startFreezeActive
            || !alivePlayers.contains(target.getUniqueId())) return;
        Entity source = event.getPushedBy();
        // Melee knockback names the attacker; projectile knockback can name either the projectile or shooter.
        if (target.getLastDamageCause() instanceof EntityDamageByEntityEvent damage
            && damage.getDamager() instanceof Projectile projectile && projectile.getShooter() == source) {
            source = projectile;
        }
        Player attacker = source instanceof Trident trident && trident.getShooter() instanceof Player shooter
            ? shooter : source instanceof Player player
                && player.getInventory().getItemInMainHand().getType() == Material.TRIDENT ? player : null;
        if (attacker == null || !alivePlayers.contains(attacker.getUniqueId())
            || !Objects.equals(target.getWorld().getName(), activeWorldName)
            || !attacker.getWorld().equals(target.getWorld())
            || plugin.teamProtectionService().areProtectedTeammates(attacker, target)) return;
        event.setKnockback(event.getKnockback().multiply(3.0D));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof AbstractArrow || projectile instanceof Trident
            || !(event.getHitEntity() instanceof Player target)
            || !(projectile.getShooter() instanceof Player attacker)) return;
        if (!alivePlayers.contains(attacker.getUniqueId()) || !alivePlayers.contains(target.getUniqueId())
            || plugin.teamProtectionService().areProtectedTeammates(attacker, target)) return;
        registerCause(target.getUniqueId(), attacker.getUniqueId());
        applyProjectileKnockback(projectile, target, 0.62D);
    }

    private void applyProjectileKnockback(Projectile projectile, Player target, double strength) {
        Vector direction = projectile.getVelocity().clone().setY(0.0D);
        if (direction.lengthSquared() < 0.001D) {
            direction = target.getLocation().toVector().subtract(projectile.getLocation().toVector()).setY(0.0D);
        }
        if (direction.lengthSquared() < 0.001D) return;
        Vector knockback = direction.normalize().multiply(strength).setY(0.24D);
        target.setVelocity(target.getVelocity().multiply(0.25D).add(knockback));
    }

    private boolean isSpectatorDamageSource(Entity damager) {
        if (damager instanceof Player player) {
            return spectators.contains(player.getUniqueId());
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return spectators.contains(player.getUniqueId());
        }
        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (spectators.contains(player.getUniqueId())) {
            if (to != null && to.getWorld() != null && Objects.equals(to.getWorld().getName(), activeWorldName)
                && to.getY() <= ELIMINATION_Y) {
                Location safe = spectatorReturnLocation(player.getUniqueId());
                if (safe != null) {
                    event.setTo(safe);
                    player.setFallDistance(0.0F);
                }
            }
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId()) || matchStarting || !matchActive
            || activeWorld == null || player.getWorld() != activeWorld
            || to == null || to.getWorld() != activeWorld) return;
        Location respawnFrozen = respawnFreezeLocations.get(player.getUniqueId());
        if (respawnFrozen != null && alivePlayers.contains(player.getUniqueId())) {
            if (to != null) {
                Location locked = respawnFrozen.clone();
                locked.setYaw(to.getYaw());
                locked.setPitch(to.getPitch());
                event.setTo(locked);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        if (startFreezeActive && alivePlayers.contains(player.getUniqueId())) {
            Location frozen = frozenSpawnLocations.get(player.getUniqueId());
            if (frozen != null && to != null) {
                Location locked = frozen.clone();
                locked.setYaw(to.getYaw());
                locked.setPitch(to.getPitch());
                event.setTo(locked);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        if (alivePlayers.contains(player.getUniqueId()) && to != null && to.getY() <= ELIMINATION_Y) {
            loseLifeAndRespawn(player, recentCause(player.getUniqueId()), "fell into the void");
        }
    }

    private Location spectatorReturnLocation(UUID playerId) {
        Location assigned = playerSpawns.get(playerId);
        if (assigned != null && assigned.getWorld() != null) {
            return assigned.clone().add(0.0D, 5.0D, 0.0D);
        }
        if (activeWorld == null) {
            return null;
        }
        Location spawn = activeWorld.getSpawnLocation().clone().add(0.5D, 5.0D, 0.5D);
        spawn.setPitch(0.0F);
        return spawn;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId())) {
            return;
        }
        if (startFreezeActive) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
            return;
        }
        if (!isInsideArena(event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlockPlaced();
        if (!isSafePlaceableRewardBlock(block.getType())) {
            event.setCancelled(true);
            return;
        }
        if (suddenDeathActive && block.getY() < (int) Math.ceil(risingVoidY)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ChatColor.RED + "The rising void consumed that height."));
            return;
        }
        playerPlacedBlocks.add(new BlockPosition(block.getX(), block.getY(), block.getZ()));
    }

    private boolean isSafePlaceableRewardBlock(Material material) {
        return material != null
            && material.isBlock()
            && !material.isAir();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (spectators.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (isInVoidRoulette(event.getPlayer().getUniqueId())) {
            if (startFreezeActive) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
                return;
            }
            event.setCancelled(false);
            Block block = event.getBlock();
            playerPlacedBlocks.remove(new BlockPosition(block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (spectators.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (isInVoidRoulette(event.getPlayer().getUniqueId()) && startFreezeActive) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (spectators.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && isInVoidRoulette(player.getUniqueId()) && startFreezeActive) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(0.0F);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline() && hasRejoinSnapshot(player)) {
                rejoin(player, true);
            }
        }, 4L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getUniqueId().equals(glowingVoidbreaker)) clearVoidbreakerGlow();
        if (queuedPlayers.remove(player.getUniqueId())) {
            resetSidebarToHub(player);
            return;
        }
        if (alivePlayers.contains(player.getUniqueId())) {
            VoidRoulettePlayerSnapshot snapshot = captureSnapshot(player, false);
            if (snapshot != null) {
                rejoinSnapshots.put(player.getUniqueId(), snapshot);
                spawnReconnectZombie(player, snapshot);
                return;
            }
            eliminate(player, null, "left the match");
            return;
        }
        if (spectators.contains(player.getUniqueId())) {
            VoidRoulettePlayerSnapshot snapshot = captureSnapshot(player, true);
            if (snapshot != null) {
                rejoinSnapshots.put(player.getUniqueId(), snapshot);
            }
            spectators.remove(player.getUniqueId());
            syncSpectatorVisibility();
        }
    }

    private void handleSpectatorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        Player spectator = event.getPlayer();
        String action = itemType(event.getItem());
        if (SPECTATOR_ACTION_TELEPORT_COMPASS.equals(action)) {
            openSpectatorTeleportMenu(spectator);
            return;
        }
        if (SPECTATOR_ACTION_HUB_PAPER.equals(action)) {
            if (plugin.minigameRouletteService() != null
                && plugin.minigameRouletteService().returnEliminatedPlayerToHub(spectator)) {
                spectator.sendMessage(ChatColor.GRAY + "Returned to hub. You remain in Minigame Roulette.");
                return;
            }
            cleanupPlayerForHub(spectator);
            spectator.sendMessage(ChatColor.GRAY + "Returned to hub.");
        }
    }

    private boolean isSpectatorTeleportMenu(String title) {
        return SPECTATOR_MENU_TITLE.equals(title);
    }

    private void openSpectatorTeleportMenu(Player spectator) {
        if (spectator == null || !spectators.contains(spectator.getUniqueId())) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, SPECTATOR_MENU_TITLE);
        int slot = 0;
        for (UUID targetId : new ArrayList<>(alivePlayers)) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                continue;
            }
            if (slot >= 45) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) {
                continue;
            }
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GREEN + target.getName());
            meta.getPersistentDataContainer().set(
                itemKey,
                PersistentDataType.STRING,
                SPECTATOR_ACTION_TARGET_PREFIX + targetId
            );
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }
        inventory.setItem(49, createNamedItem(Material.BARRIER, SPECTATOR_ACTION_CLOSE, ChatColor.RED + "Close"));
        spectator.openInventory(inventory);
    }

    private ItemStack createSpectatorTeleportCompass() {
        return createNamedItem(Material.COMPASS, SPECTATOR_ACTION_TELEPORT_COMPASS, ChatColor.AQUA + "Teleport Compass");
    }

    private ItemStack createSpectatorHubPaper() {
        ItemStack item = createNamedItem(Material.PAPER, SPECTATOR_ACTION_HUB_PAPER, ChatColor.RED + "Return to Hub");
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("hydra:backspace"));
        item.setItemMeta(meta);
        return item;
    }

    public void refreshSpectatorVisibilityPreference() {
        syncSpectatorVisibility();
    }

    public boolean isLobbyTimeContext(UUID playerId) {
        return queuedPlayers.contains(playerId)
            || (startFreezeActive && (alivePlayers.contains(playerId) || spectators.contains(playerId)));
    }

    private void syncSpectatorVisibility() {
        if (activeWorld == null) {
            return;
        }
        List<Player> alive = new ArrayList<>();
        List<Player> spectating = new ArrayList<>();
        for (Player player : activeWorld.getPlayers()) {
            UUID id = player.getUniqueId();
            if (alivePlayers.contains(id)) {
                alive.add(player);
            } else if (spectators.contains(id)) {
                spectating.add(player);
            }
        }
        for (Player alivePlayer : alive) {
            for (Player otherAlive : alive) {
                alivePlayer.showPlayer(plugin, otherAlive);
            }
            for (Player spectator : spectating) {
                alivePlayer.hidePlayer(plugin, spectator);
                spectator.showPlayer(plugin, alivePlayer);
            }
        }
        for (Player spectator : spectating) {
            for (Player otherSpectator : spectating) {
                PlayerSettingsService.syncSpectatorPairOrDefault(plugin, spectator, otherSpectator);
            }
        }
    }

    @EventHandler
    public void onSpectatorInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean spectator = spectators.contains(player.getUniqueId());
        boolean teleportMenu = isSpectatorTeleportMenu(event.getView().getTitle());
        if (!spectator && !teleportMenu) {
            return;
        }
        event.setCancelled(true);
        if (!teleportMenu || event.getClickedInventory() == null || event.getCurrentItem() == null) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String action = itemType(event.getCurrentItem());
        if (SPECTATOR_ACTION_CLOSE.equals(action)) {
            player.closeInventory();
            return;
        }
        if (action == null || !action.startsWith(SPECTATOR_ACTION_TARGET_PREFIX)) {
            return;
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(action.substring(SPECTATOR_ACTION_TARGET_PREFIX.length()));
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(ChatColor.RED + "Invalid target.");
            return;
        }
        if (!alivePlayers.contains(targetId)) {
            player.sendMessage(ChatColor.YELLOW + "Player not alive.");
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.YELLOW + "Target offline.");
            return;
        }
        if (activeWorldName != null && !Objects.equals(target.getWorld().getName(), activeWorldName)) {
            player.sendMessage(ChatColor.YELLOW + "Player not in match.");
            return;
        }
        plugin.transitionTravel(player, () -> {
            if (spectators.contains(player.getUniqueId()) && alivePlayers.contains(targetId)
                && target.isOnline() && Objects.equals(target.getWorld().getName(), activeWorldName)) {
                player.teleport(target.getLocation().clone().add(0.0D, 1.0D, 0.0D));
                player.sendMessage(ChatColor.GRAY + "Teleported to " + ChatColor.WHITE + target.getName());
            }
        });
    }

    @EventHandler
    public void onSpectatorInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!spectators.contains(player.getUniqueId()) && !isSpectatorTeleportMenu(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
    }

    private void loseLifeAndRespawn(Player player, UUID creditedKiller, String reason) {
        if (player == null || !matchActive || matchStarting || player.getWorld() != activeWorld
            || !alivePlayers.contains(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!lifeLossInProgress.add(playerId)) {
            return;
        }
        boolean infiniteLives = isInfiniteLivesEnabled();
        int remaining = infiniteLives
            ? STARTING_LIVES
            : lives.getOrDefault(playerId, STARTING_LIVES) - 1;
        lives.put(playerId, remaining);

        boolean creditedVoidbreaker = creditedKiller != null && creditedKiller.equals(currentVoidbreaker);
        if (creditedVoidbreaker) {
            voidbreakerSucceeded = true;
        }

        if (suddenDeathActive && !infiniteLives) {
            eliminate(player, creditedKiller, reason);
            lifeLossInProgress.remove(playerId);
            return;
        }

        if (!infiniteLives && remaining <= 0) {
            eliminate(player, creditedKiller, reason);
            lifeLossInProgress.remove(playerId);
            return;
        }

        // Respawn on the player's own bedrock pillar with a brief grace so they are not
        // instantly re-knocked. Inventory persists; only elimination/cleanup clears it.
        Location spawn = assignedPillarSpawn(playerId, player.getLocation());
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        player.teleport(spawn);
        Location respawnLock = spawn.clone();
        respawnFreezeLocations.put(playerId, respawnLock);
        applyLifeHealth(player, remaining);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 2, 4, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 2, 0, false, false, false));
        if (infiniteLives) {
            player.sendTitle(ChatColor.LIGHT_PURPLE + "TEST RESPAWN",
                ChatColor.GRAY + "Infinite Lives enabled", 5, 25, 5);
        } else {
            player.sendTitle(ChatColor.RED + "-1 LIFE", ChatColor.GRAY + "Lives left: " + remaining, 5, 25, 5);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 0.8F);
        if (infiniteLives) {
            broadcastMatch(ChatColor.LIGHT_PURPLE + displayName(playerId)
                + ChatColor.GRAY + " respawned with Infinite Lives.");
        } else {
            broadcastMatch(ChatColor.RED + displayName(playerId) + ChatColor.GRAY
                + " fell and lost a life (" + remaining + " left).");
        }
        forAlive(this::updateMatchSidebar);

        UUID graceId = playerId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (respawnFreezeLocations.get(graceId) != respawnLock) return;
            Player graced = Bukkit.getPlayer(graceId);
            respawnFreezeLocations.remove(graceId);
            lifeLossInProgress.remove(graceId);
            if (graced != null && alivePlayers.contains(graceId)) {
                graced.setInvulnerable(false);
            }
        }, 40L);
        if (creditedVoidbreaker) {
            completeVoidbreakerSuccess(creditedKiller);
        }
    }

    private Location assignedPillarSpawn(UUID playerId, Location fallback) {
        Location spawn = playerSpawns.get(playerId);
        if (spawn != null) {
            World world = spawn.getWorld();
            if (world != null) {
                return highestSafePillarSpawn(world, spawn.getBlockX(), spawn.getBlockZ(), spawn.getYaw());
            }
            return spawn.clone();
        }
        if (activeWorld != null) {
            return highestSafePillarSpawn(activeWorld, 0, 0, 0.0F);
        }
        return fallback == null ? null : fallback.clone();
    }

    private void returnVoidbreakerToAssignedPillar(UUID voidbreakerId) {
        if (voidbreakerId == null || !alivePlayers.contains(voidbreakerId)) {
            return;
        }
        Player voidbreaker = Bukkit.getPlayer(voidbreakerId);
        if (voidbreaker == null || !voidbreaker.isOnline()) {
            return;
        }
        Location spawn = assignedPillarSpawn(voidbreakerId, voidbreaker.getLocation());
        if (spawn == null) {
            return;
        }
        voidbreaker.setFallDistance(0.0F);
        voidbreaker.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        voidbreaker.teleport(spawn);
        voidbreaker.playSound(voidbreaker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.55F, 1.35F);
    }

    private void completeVoidbreakerSuccess(UUID voidbreakerId) {
        if (voidbreakerId == null || !voidbreakerId.equals(currentVoidbreaker)) {
            return;
        }
        voidbreakerSucceeded = true;
        voidbreakerAbilityUsed = true;
        Player voidbreaker = Bukkit.getPlayer(voidbreakerId);
        if (voidbreaker != null && voidbreaker.isOnline()) {
            removeVoidbreakerItems(voidbreaker);
            returnVoidbreakerToAssignedPillar(voidbreakerId);
            if (testModeEnabled) {
                giveItem(voidbreaker, createRandomRewardItem());
                giveItem(voidbreaker, createRandomRewardItem());
            }
            voidbreaker.sendActionBar(Component.text(ChatColor.LIGHT_PURPLE + "Voidbreaker USED · Kill secured."));
        }
        clearVoidbreakerGlow();
        currentVoidbreaker = null;
        cancelVoidbreakerDeadline();
        if (matchActive && alivePlayers.size() > 1) {
            scheduleNextCycle();
        }
    }

    private void applyLifeHealth(Player player, int remainingLives) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(20.0D);
        }
        if (!player.isDead()) {
            player.setHealth(Math.min(20.0D, player.getMaxHealth()));
        }
    }

    private void resetNormalHealth(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(20.0D);
        }
        if (!player.isDead()) {
            player.setHealth(Math.min(20.0D, player.getMaxHealth()));
        }
    }

    private void eliminate(Player player, UUID creditedKiller, String reason) {
        if (player == null || !alivePlayers.remove(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (managedRound != null) {
            managedEliminationOrder.add(playerId);
            creditManagedElimination(playerId, creditedKiller);
        }
        VoidRoulettePlayerSnapshot snapshot = captureSnapshot(player, false);
        if (snapshot != null) {
            reviveSnapshots.put(playerId, snapshot);
        }
        lives.put(playerId, 0);
        clearCycleInventory(player);
        resetNormalHealth(player);
        setSpectatorState(player);
        player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "ELIMINATED", ChatColor.GRAY + reason, 5, 40, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_DEATH, 0.8F, 0.8F);

        if (creditedKiller != null && creditedKiller.equals(currentVoidbreaker)) {
            voidbreakerSucceeded = true;
            if (plugin.queueService() != null) {
                plugin.queueService().recordVoidRouletteKill(creditedKiller);
            }
            completeVoidbreakerSuccess(creditedKiller);
        }
        if (playerId.equals(currentVoidbreaker)) {
            clearVoidbreakerGlow();
            currentVoidbreaker = null;
            voidbreakerSucceeded = true;
        }
        broadcastMatch(ChatColor.RED + player.getName() + ChatColor.GRAY + " was eliminated.");
        checkWin();
    }

    private void creditManagedElimination(UUID victimId, UUID creditedKiller) {
        if (managedRound == null || creditedKiller == null || creditedKiller.equals(victimId)
            || !managedRound.roster().contains(creditedKiller)) {
            return;
        }
        managedEliminationCredits.merge(creditedKiller, 1, Integer::sum);
    }

    private void setSpectatorState(Player player) {
        UUID playerId = player.getUniqueId();
        spectators.add(playerId);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.08F);
        player.setCanPickupItems(false);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setFallDistance(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(0.0F);
        player.closeInventory();
        player.setItemOnCursor(null);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, false));
        plugin.playerSettingsService().applyHydraNightVision(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setItem(0, createSpectatorTeleportCompass());
        player.getInventory().setItem(8, createSpectatorHubPaper());
        player.updateInventory();
        syncSpectatorVisibility();
        player.sendMessage(ChatColor.GRAY + "Now spectating.");
    }

    private boolean checkWin() {
        if (!matchActive || (alivePlayers.size() > 1
            && !RouletteRoundRules.onlyOneTeamRemains(alivePlayers, managedRound))) {
            return false;
        }
        if (alivePlayers.size() == 1 && matchPlayerCount < MIN_FORCE_START_PLAYERS) {
            finishMatch(null);
            return true;
        }
        UUID winnerId = alivePlayers.stream().findFirst().orElse(null);
        finishMatch(winnerId);
        return true;
    }

    private void finishMatch(UUID winnerId) {
        ManagedMinigameRound.Request request = managedRound;
        LinkedHashSet<UUID> requeueCandidates = new LinkedHashSet<>(alivePlayers);
        requeueCandidates.addAll(spectators);
        requeueCandidates.removeAll(externalSpectators);
        List<UUID> placements = request == null ? List.of() : managedPlayerPlacements(winnerId);
        Map<UUID, Integer> eliminationCredits = request == null
            ? Map.of() : Map.copyOf(managedEliminationCredits);
        matchActive = false;
        cancelMatchTasks();
        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        if (request == null && winner != null && winner.isOnline()) {
            int silver = plugin.queueService() == null ? 0 : plugin.queueService().recordVoidRouletteWin(winnerId);
            if (plugin.progressionService() != null) {
                plugin.progressionService().grantShardFragments(winnerId, 1, "VOID_ROULETTE_WIN");
            }
            winner.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY", ChatColor.GRAY + "You survived Void Roulette.", 10, 60, 20);
            if (silver > 0) {
                winner.sendMessage(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + silver + " Coins");
            }
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
        }
        broadcastMatch(ChatColor.GOLD + (winner == null ? "Nobody" : winner.getName()) + ChatColor.GREEN + " won Void Roulette.");
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                resetNormalHealth(player);
                resetSidebarToHub(player);
            }
        }
        for (UUID id : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                resetNormalHealth(player);
                resetSidebarToHub(player);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LinkedHashSet<UUID> returningIds = new LinkedHashSet<>(alivePlayers);
            returningIds.addAll(spectators);
            List<Player> returning = returningIds.stream().map(Bukkit::getPlayer)
                .filter(Objects::nonNull).filter(Player::isOnline).toList();
            plugin.transitionTravelGroup(returning, this::cleanupPlayerForHub, () -> {
                String worldName = activeWorldName;
                cleanupState(false);
                if (request == null) {
                    plugin.playerSettingsService().requeueAfterCompletedSession(
                        requeueCandidates, PlayerSettingsService.AutoQueue.VOID_ROULETTE);
                }
                if (worldName != null) cleanupWorld(worldName);
                if (request != null) {
                    dispatchManagedResult(request,
                        ManagedMinigameRound.Result.players(request, placements, eliminationCredits));
                }
            });
        }, 20L * 8L - TransitionService.Preset.NORMAL.fadeOutTicks);
    }

    private List<UUID> managedPlayerPlacements(UUID winnerId) {
        LinkedHashSet<UUID> placements = new LinkedHashSet<>();
        if (winnerId != null) {
            placements.add(winnerId);
        }
        placements.addAll(alivePlayers);
        for (int i = managedEliminationOrder.size() - 1; i >= 0; i--) {
            placements.add(managedEliminationOrder.get(i));
        }
        return new ArrayList<>(placements);
    }

    private void dispatchManagedResult(ManagedMinigameRound.Request request, ManagedMinigameRound.Result result) {
        if (request == null || managedCallbackSent) {
            return;
        }
        managedCallbackSent = true;
        Bukkit.getScheduler().runTask(plugin, () -> request.callback().accept(result));
    }

    private void cleanupPlayerForHub(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (playerId.equals(glowingVoidbreaker)) clearVoidbreakerGlow();
        queuedPlayers.remove(playerId);
        alivePlayers.remove(playerId);
        boolean wasSpectator = spectators.remove(playerId);
        externalSpectators.remove(playerId);
        frozenSpawnLocations.remove(playerId);
        respawnFreezeLocations.remove(playerId);
        lifeLossInProgress.remove(playerId);
        if (plugin.queueService() != null) {
            plugin.queueService().resetMatchRewardStats(playerId);
        }
        lives.remove(playerId);
        recentKnockbackCauses.remove(playerId);
        recentKnockbackTimes.remove(playerId);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        plugin.playerSettingsService().clearHydraNightVision(player);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.setInvulnerable(false);
        resetNormalHealth(player);
        player.setCanPickupItems(true);
        player.setCollidable(true);
        player.setFlySpeed(0.1F);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        restorePlayerVisibility(player);
        if (wasSpectator) {
            syncSpectatorVisibility();
        }
        plugin.queueService().returnPlayerToHydraHub(player);
        resetSidebarToHub(player);
    }

    public boolean returnTournamentSpectatorToHub(Player player) {
        if (player == null || !spectators.contains(player.getUniqueId()) || managedRound == null) return false;
        return plugin.transitionTravel(player, () -> {
            if (!spectators.contains(player.getUniqueId()) || managedRound == null) return;
            VoidRoulettePlayerSnapshot snapshot = captureSnapshot(player, true);
            if (snapshot == null) return;
            rejoinSnapshots.put(player.getUniqueId(), snapshot);
            cleanupPlayerForHub(player);
        });
    }

    public boolean forfeitTournamentPlayerToHub(Player player) {
        if (player == null || managedRound == null || !alivePlayers.contains(player.getUniqueId())) return false;
        eliminate(player, null, "left the round");
        clearSnapshot(player.getUniqueId());
        if (!plugin.transitionTravel(player, () -> cleanupPlayerForHub(player))) {
            cleanupPlayerForHub(player);
        }
        return true;
    }

    private void restorePlayerVisibility(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, target);
            target.showPlayer(plugin, viewer);
        }
    }

    private void resetSidebarToHub(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        sidebarSignatures.remove(player.getUniqueId());
        plugin.queueService().clearSidebarCache(player.getUniqueId());
        plugin.queueService().refreshSidebar(player);
    }

    private void releaseQueuedPlayer(Player player) {
        queuedPlayers.remove(player.getUniqueId());
        if (managedRound == null) plugin.queueService().returnPlayerToHydraHub(player);
        resetSidebarToHub(player);
    }

    private void clearSnapshot(UUID playerId) {
        if (playerId == null) {
            return;
        }
        removeReconnectZombie(playerId);
        rejoinSnapshots.remove(playerId);
        reviveSnapshots.remove(playerId);
    }

    private VoidRoulettePlayerSnapshot captureSnapshot(Player player, boolean spectator) {
        if (player == null || activeWorldName == null) {
            return null;
        }
        Location location = player.getLocation() == null ? null : player.getLocation().clone();
        if (location == null || location.getWorld() == null) {
            World world = Bukkit.getWorld(activeWorldName);
            location = world == null ? null : world.getSpawnLocation();
        }
        if (location == null || location.getWorld() == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? 20.0D : Math.max(1.0D, maxHealthAttribute.getValue());
        double storedHealth = spectator ? maxHealth : Math.max(1.0D, Math.min(maxHealth, player.getHealth()));
        return new VoidRoulettePlayerSnapshot(
            activeWorldName,
            spectator,
            location,
            cloneItemStacks(player.getInventory().getContents()),
            cloneItemStacks(player.getInventory().getArmorContents()),
            cloneItem(player.getInventory().getItemInOffHand()),
            storedHealth,
            player.getFoodLevel(),
            player.getSaturation(),
            Math.max(1, lives.getOrDefault(playerId, STARTING_LIVES)),
            playerSpawns.get(playerId) == null ? null : playerSpawns.get(playerId).clone()
        );
    }

    private boolean restoreLiveSnapshot(Player player, VoidRoulettePlayerSnapshot snapshot, boolean fullHeal) {
        if (player == null || snapshot == null || !snapshot.isRestorable(this)) {
            return false;
        }
        World world = Bukkit.getWorld(snapshot.worldName);
        if (world == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        spectators.remove(playerId);
        alivePlayers.add(playerId);
        lives.put(playerId, Math.max(1, Math.min(STARTING_LIVES, snapshot.lives)));
        if (snapshot.assignedSpawn != null) {
            playerSpawns.put(playerId, snapshot.assignedSpawn.clone());
        }
        respawnFreezeLocations.remove(playerId);
        lifeLossInProgress.remove(playerId);
        frozenSpawnLocations.remove(playerId);
        plugin.menuService().preparePlayerForMatch(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        Location targetLocation = snapshot.location.clone();
        if (targetLocation.getWorld() == null) {
            targetLocation.setWorld(world);
        }
        player.teleport(targetLocation);
        player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        player.getInventory().setContents(cloneItemStacks(snapshot.contents));
        player.getInventory().setArmorContents(cloneItemStacks(snapshot.armor));
        player.getInventory().setItemInOffHand(cloneItem(snapshot.offHand));
        player.updateInventory();
        if (playerId.equals(currentVoidbreaker)) beginVoidbreakerGlow(player);
        applyLifeHealth(player, lives.getOrDefault(playerId, STARTING_LIVES));
        if (fullHeal) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(0.0F);
        } else {
            player.setHealth(Math.max(1.0D, Math.min(player.getMaxHealth(), snapshot.health)));
            player.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel)));
            player.setSaturation(Math.max(0.0F, snapshot.saturation));
        }
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        syncSpectatorVisibility();
        updateMatchSidebar(player);
        return true;
    }

    private boolean restoreSpectatorSnapshot(Player player, VoidRoulettePlayerSnapshot snapshot) {
        if (player == null || snapshot == null || !snapshot.isRestorable(this)) {
            return false;
        }
        World world = Bukkit.getWorld(snapshot.worldName);
        if (world == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        alivePlayers.remove(playerId);
        spectators.add(playerId);
        lives.put(playerId, 0);
        Location targetLocation = snapshot.location.clone();
        if (targetLocation.getWorld() == null) {
            targetLocation.setWorld(world);
        }
        player.teleport(targetLocation);
        setSpectatorState(player);
        syncSpectatorVisibility();
        updateMatchSidebar(player);
        return true;
    }

    private void spawnReconnectZombie(Player player, VoidRoulettePlayerSnapshot snapshot) {
        if (player == null || snapshot == null || snapshot.spectator || snapshot.location == null) {
            return;
        }
        removeReconnectZombie(player.getUniqueId());
        World world = snapshot.location.getWorld();
        if (world == null) {
            world = Bukkit.getWorld(snapshot.worldName);
        }
        if (world == null) {
            return;
        }
        Location spawn = snapshot.location.clone();
        if (spawn.getWorld() == null) {
            spawn.setWorld(world);
        }
        Zombie zombie = world.spawn(spawn, Zombie.class, body -> {
            body.setAI(false);
            body.setSilent(true);
            body.setCanPickupItems(false);
            body.setRemoveWhenFarAway(false);
            body.setPersistent(true);
            body.setCustomName(ChatColor.RED + player.getName());
            body.setCustomNameVisible(true);
            body.setAdult();
            AttributeInstance maxHealth = body.getAttribute(Attribute.MAX_HEALTH);
            double maxHealthValue = maxHealth == null ? 20.0D : Math.max(1.0D, maxHealth.getValue());
            body.setHealth(Math.max(1.0D, Math.min(maxHealthValue, snapshot.health)));
            body.addScoreboardTag("hydra_reconnect_body");
            body.addScoreboardTag("hydra_voidroulette_reconnect");
            body.addScoreboardTag("hydra_reconnect_" + player.getUniqueId());
            EntityEquipment equipment = body.getEquipment();
            if (equipment != null) {
                equipment.setArmorContents(cloneItemStacks(snapshot.armor));
                equipment.setItemInMainHand(firstHeldItem(snapshot.contents));
                equipment.setItemInOffHand(cloneItem(snapshot.offHand));
                equipment.setHelmetDropChance(0.0F);
                equipment.setChestplateDropChance(0.0F);
                equipment.setLeggingsDropChance(0.0F);
                equipment.setBootsDropChance(0.0F);
                equipment.setItemInMainHandDropChance(0.0F);
                equipment.setItemInOffHandDropChance(0.0F);
            }
        });
        reconnectZombieByPlayer.put(player.getUniqueId(), zombie.getUniqueId());
        reconnectPlayerByZombie.put(zombie.getUniqueId(), player.getUniqueId());
        long expiresAtMs = System.currentTimeMillis() + RECONNECT_BODY_TIMEOUT_MS;
        reconnectZombieExpiresAtMs.put(player.getUniqueId(), expiresAtMs);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupExpiredReconnectBody(player.getUniqueId(), expiresAtMs), RECONNECT_BODY_TIMEOUT_MS / 50L);
    }

    private void removeReconnectZombie(UUID playerId) {
        if (playerId == null) {
            return;
        }
        UUID zombieId = reconnectZombieByPlayer.remove(playerId);
        reconnectZombieExpiresAtMs.remove(playerId);
        if (zombieId == null) {
            return;
        }
        reconnectPlayerByZombie.remove(zombieId);
        Entity entity = Bukkit.getEntity(zombieId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void cleanupExpiredReconnectBody(UUID playerId, long expectedExpiryMs) {
        Long currentExpiryMs = reconnectZombieExpiresAtMs.get(playerId);
        if (currentExpiryMs == null || currentExpiryMs.longValue() != expectedExpiryMs || System.currentTimeMillis() < currentExpiryMs) {
            return;
        }
        VoidRoulettePlayerSnapshot snapshot = rejoinSnapshots.remove(playerId);
        removeReconnectZombie(playerId);
        reviveSnapshots.remove(playerId);
        if (snapshot != null && snapshot.isRestorable(this)) {
            if (managedRound != null) {
                managedEliminationOrder.add(playerId);
                creditManagedElimination(playerId, recentCause(playerId));
            }
            alivePlayers.remove(playerId);
            lives.put(playerId, 0);
            if (playerId.equals(currentVoidbreaker)) {
                clearVoidbreakerGlow();
                currentVoidbreaker = null;
                voidbreakerSucceeded = true;
            }
            broadcastMatch(ChatColor.RED + displayName(playerId) + ChatColor.GRAY + " failed to reconnect.");
            checkWin();
        }
    }

    private void cleanupAllReconnectBodies() {
        for (UUID playerId : new ArrayList<>(reconnectZombieByPlayer.keySet())) {
            removeReconnectZombie(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReconnectZombieDeath(EntityDeathEvent event) {
        UUID playerId = reconnectPlayerByZombie.remove(event.getEntity().getUniqueId());
        if (playerId == null) {
            return;
        }
        reconnectZombieByPlayer.remove(playerId);
        reconnectZombieExpiresAtMs.remove(playerId);
        event.getDrops().clear();
        VoidRoulettePlayerSnapshot snapshot = rejoinSnapshots.remove(playerId);
        reviveSnapshots.remove(playerId);
        if (snapshot != null && snapshot.isRestorable(this)) {
            if (managedRound != null) {
                managedEliminationOrder.add(playerId);
                creditManagedElimination(playerId, recentCause(playerId));
            }
            alivePlayers.remove(playerId);
            lives.put(playerId, 0);
            if (playerId.equals(currentVoidbreaker)) {
                clearVoidbreakerGlow();
                currentVoidbreaker = null;
                voidbreakerSucceeded = true;
            }
            broadcastMatch(ChatColor.RED + displayName(playerId) + ChatColor.GRAY + " failed to reconnect.");
            checkWin();
        }
    }

    private ItemStack firstHeldItem(ItemStack[] contents) {
        if (contents == null) {
            return null;
        }
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) {
                return cloneItem(stack);
            }
        }
        return null;
    }

    private ItemStack[] cloneItemStacks(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack source) {
        return source == null ? null : source.clone();
    }

    private void cancelTasks() {
        cancelMatchTasks();
        if (sidebarTask != null) {
            sidebarTask.cancel();
            sidebarTask = null;
        }
    }

    private void cancelMatchTasks() {
        clearVoidbreakerGlow();
        cancelQueueCountdown();
        clearStartFreeze();
        cancelVoidbreakerDeadline();
        stopExperimentalSystems(true);
        if (rouletteTask != null) {
            rouletteTask.cancel();
            rouletteTask = null;
        }
        if (randomDropTask != null) {
            randomDropTask.cancel();
            randomDropTask = null;
        }
    }

    private void cleanupState(boolean keepQueue) {
        if (!keepQueue) {
            queuedPlayers.clear();
        }
        alivePlayers.clear();
        spectators.clear();
        externalSpectators.clear();
        managedEliminationOrder.clear();
        managedEliminationCredits.clear();
        lives.clear();
        recentKnockbackCauses.clear();
        recentKnockbackTimes.clear();
        frozenSpawnLocations.clear();
        respawnFreezeLocations.clear();
        lifeLossInProgress.clear();
        sidebarSignatures.clear();
        rejoinSnapshots.clear();
        reviveSnapshots.clear();
        cleanupAllReconnectBodies();
        playerSpawns.clear();
        arenaPillars.clear();
        playerPlacedBlocks.clear();
        risingVoidWarningCooldownUntilMs.clear();
        chaosEventBag.clear();
        voidbreakerSelectionBag.clear();
        clearVoidbreakerGlow();
        currentVoidbreaker = null;
        lastVoidbreaker = null;
        voidbreakerSucceeded = false;
        voidbreakerAbilityUsed = false;
        startFreezeActive = false;
        matchStarting = false;
        matchActive = false;
        activeWorld = null;
        activeWorldName = null;
        matchPlayerCount = 0;
        cycleSecondsRemaining = 0;
        matchStartedAtMs = 0L;
        nextRandomDropAtMs = 0L;
        activeArenaLayout = ArenaLayout.RING;
        activeExperimentalEvent = null;
        pendingExperimentalEvent = null;
        experimentalWarningSecondsRemaining = 0;
        suddenDeathActive = false;
        swapUsedThisMatch = false;
        suddenDeathStageIndex = 0;
        risingVoidY = ELIMINATION_Y;
        lastRemovedVoidLayer = ELIMINATION_Y;
        hideExperimentalBossBar();
        managedRound = null;
    }

    private void cleanupWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player player : new ArrayList<>(world.getPlayers())) {
                cleanupPlayerForHub(player);
            }
        }
        if (!autoCleanupEnabled) {
            plugin.getLogger().info("[VoidRoulette] Auto-cleanup disabled; leaving world " + worldName
                + " loaded for inspection.");
            return;
        }
        if (world != null) {
            if (!plugin.getServer().isStopping()) {
                world.setAutoSave(false);
                plugin.getLogger().warning("[VoidRoulette] Deferred live unload for " + worldName
                    + " to avoid Paper/Moonrise main-thread world-unload stalls. Restart or stale-world cleanup can remove it later.");
                return;
            }
            Bukkit.unloadWorld(world, false);
        }
        Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName);
        Bukkit.getScheduler().runTaskLater(plugin, () -> deleteFolderAsync(folder), 20L);
    }

    private List<Path> staleWorldFolders() {
        Path worldContainer = Bukkit.getWorldContainer().toPath();
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> walk = Files.list(worldContainer)) {
            walk.filter(Files::isDirectory)
                .filter(path -> path.getFileName() != null)
                .filter(path -> path.getFileName().toString().startsWith("VoidRoulette-World-"))
                .filter(path -> !Objects.equals(path.getFileName().toString(), activeWorldName))
                .forEach(folders::add);
        } catch (IOException ex) {
            plugin.getLogger().warning("[VoidRoulette] Failed listing world folder cleanup candidates: " + ex.getMessage());
        }
        return folders;
    }

    private void deleteFolderAsync(Path folder) {
        if (folder == null || !Files.exists(folder)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteFolder(folder));
    }

    private void deleteFolder(Path folder) {
        if (folder == null || !Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    plugin.getLogger().warning("[VoidRoulette] Failed deleting " + path + ": " + ex.getMessage());
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().warning("[VoidRoulette] Failed walking world folder " + folder + ": " + ex.getMessage());
        }
    }

    private void registerCause(UUID targetId, UUID attackerId) {
        if (targetId == null || attackerId == null) {
            return;
        }
        recentKnockbackCauses.put(targetId, attackerId);
        recentKnockbackTimes.put(targetId, System.currentTimeMillis());
    }

    private UUID recentCause(UUID targetId) {
        Long time = recentKnockbackTimes.get(targetId);
        if (time == null || System.currentTimeMillis() - time > 8_000L) {
            return null;
        }
        return recentKnockbackCauses.get(targetId);
    }

    private boolean isVoidbreakerMace(ItemStack item) {
        return "voidbreaker_mace".equals(itemType(item));
    }

    private String itemType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private boolean isActivePlayer(UUID playerId) {
        return matchActive && alivePlayers.contains(playerId);
    }

    private boolean isInsideArena(Location location) {
        return location != null
            && location.getWorld() != null
            && Objects.equals(location.getWorld().getName(), activeWorldName)
            && Math.abs(location.getX()) <= ARENA_RADIUS
            && Math.abs(location.getZ()) <= ARENA_RADIUS
            && location.getY() >= ELIMINATION_Y;
    }

    private ItemStack createNamedItem(Material material, String type, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private void broadcastQueue(String message) {
        for (UUID id : queuedPlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void broadcastMatch(String message) {
        for (UUID id : alivePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
        for (UUID id : spectators) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void forAlive(java.util.function.Consumer<Player> consumer) {
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                consumer.accept(player);
            }
        }
    }

    private String displayName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? "A player" : player.getName();
    }

    private void tickSidebars() {
        updateQueuedSidebars();
        if (matchActive) {
            syncExperimentalBossBarPlayers();
            forAlive(this::updateMatchSidebar);
            for (UUID id : new ArrayList<>(spectators)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    updateMatchSidebar(player);
                }
            }
        }
    }

    private void updateQueuedSidebars() {
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                List<String> lines = new ArrayList<>();
                lines.add(sidebarSystem("Players", queuedPlayers.size() + "/" + MAX_PLAYERS, ChatColor.YELLOW));
                lines.add(sidebarSystem("Needed", String.valueOf(MIN_PLAYERS), ChatColor.WHITE));
                lines.add(sidebarSeparator(0));
                lines.add(sidebarSystem("Status", queueCountdownTask == null ? "Waiting" : "Starting", ChatColor.YELLOW));
                lines.add(ChatColor.AQUA + "HydraMC.net");
                applySidebar(player, lines);
            }
        }
    }

    private void updateMatchSidebar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int level = plugin.progressionService() == null ? 0 : plugin.progressionService().level(playerId);
        int battlePassTier = plugin.battlePassService() == null ? 1 : plugin.battlePassService().tier(playerId);
        int prestigeProgress = plugin.progressionService() == null ? 0 : plugin.progressionService().prestigeProgress(playerId);
        int prestigeLevelSize = plugin.progressionService() == null ? 1000 : plugin.progressionService().prestigeLevelSize(playerId);
        List<String> lines = new ArrayList<>();
        if (alivePlayers.contains(playerId)) {
            String lifeDisplay = isInfiniteLivesEnabled()
                ? "Infinite"
                : String.valueOf(lives.getOrDefault(playerId, 0));
            lines.add(sidebarSystem("Lives", lifeDisplay,
                isInfiniteLivesEnabled() ? ChatColor.LIGHT_PURPLE : ChatColor.RED));
        } else {
            lines.add(sidebarSystem("Lives", "Spectator", ChatColor.GRAY));
        }
        lines.add(sidebarSystem("Players", alivePlayers.size() + "/" + Math.max(matchPlayerCount, 1), ChatColor.YELLOW));
        lines.add(sidebarSeparator(0));
        lines.add(sidebarSystem("Level", String.valueOf(level), ChatColor.YELLOW));
        lines.add(sidebarSystem("Tier", String.valueOf(battlePassTier), ChatColor.GOLD));
        lines.add(sidebarSystem("XP", formatNumber(prestigeProgress) + "/" + formatNumber(prestigeLevelSize), ChatColor.WHITE));
        lines.add(sidebarSeparator(1));
        if (alivePlayers.contains(playerId)) {
            String role = playerId.equals(currentVoidbreaker) ? "Voidbreaker" : "Survivor";
            ChatColor roleColor = playerId.equals(currentVoidbreaker) ? ChatColor.DARK_PURPLE : ChatColor.WHITE;
            lines.add(sidebarSystem("Role", role, roleColor));
            String maceDisplay = voidbreakerMaceDisplay(playerId, currentVoidbreaker,
                voidbreakerAbilityUsed, cycleSecondsRemaining);
            if (maceDisplay != null) {
                lines.add(sidebarSystem("Mace", maceDisplay, ChatColor.LIGHT_PURPLE));
            }
        } else {
            lines.add(sidebarSystem("Role", "Spectator", ChatColor.GRAY));
        }
        String timer = currentVoidbreaker == null ? "Next" : String.valueOf(Math.max(0, cycleSecondsRemaining)) + "s";
        lines.add(sidebarSystem("Roulette", timer, ChatColor.WHITE));
        if (testModeEnabled) {
            String eventName = activeExperimentalEvent != null
                ? activeExperimentalEvent.displayName
                : pendingExperimentalEvent != null ? "Next " + pendingExperimentalEvent.displayName : "Waiting";
            lines.add(sidebarSystem("Test Event", eventName,
                suddenDeathActive ? ChatColor.RED : ChatColor.LIGHT_PURPLE));
        }
        RouletteTeam tournamentTeam = plugin.minigameRouletteService() == null
            ? null
            : plugin.minigameRouletteService().teamOf(playerId);
        if (tournamentTeam != null) {
            lines.add(sidebarSystem("Your Team", tournamentTeam.displayName(), tournamentTeam.color()));
        }
        lines.add(ChatColor.AQUA + "HydraMC.net");
        applySidebar(player, lines);
    }

    private void applySidebar(Player player, List<String> lines) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        String signature = String.join("\n", lines) + "\n" + lifeTabSignature();
        Objective current = player.getScoreboard() == null ? null : player.getScoreboard().getObjective(SIDEBAR_OBJECTIVE);
        if (signature.equals(sidebarSignatures.get(player.getUniqueId())) && current != null) {
            return;
        }
        Scoreboard board = player.getScoreboard();
        if (board == null || board == manager.getMainScoreboard()) {
            board = manager.getNewScoreboard();
        }
        Objective existing = board.getObjective(SIDEBAR_OBJECTIVE);
        if (existing != null) {
            existing.unregister();
        }
        Objective objective = board.registerNewObjective(
            SIDEBAR_OBJECTIVE,
            Criteria.DUMMY,
            Component.text(ChatColor.AQUA + "" + ChatColor.BOLD + "VOID ROULETTE")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        ensureStatusObjectives(board);
        int score = lines.size();
        int duplicateVariant = 20;
        Set<String> usedEntries = new HashSet<>();
        for (String rawLine : lines) {
            String entry = rawLine == null ? sidebarBlank(duplicateVariant++) : rawLine;
            while (!usedEntries.add(entry)) {
                entry = entry + sidebarUniqueSuffix(duplicateVariant++);
            }
            objective.getScore(entry).setScore(score--);
        }
        if (plugin.npcService() != null) {
            plugin.npcService().applyHiddenNameTeam(board);
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        if (plugin.hydraPlusService() != null) {
            plugin.hydraPlusService().applyToViewer(player);
        }
        sidebarSignatures.put(player.getUniqueId(), signature);
    }

    private String lifeTabSignature() {
        return alivePlayers.stream()
            .sorted()
            .map(playerId -> playerId + ":" + lives.getOrDefault(playerId, 0))
            .collect(java.util.stream.Collectors.joining(","));
    }

    private void ensureStatusObjectives(Scoreboard board) {
        Objective legacyHealthTab = board.getObjective("hydra_hp_tab");
        if (legacyHealthTab != null) legacyHealthTab.unregister();
        Objective tab = board.getObjective("hydra_lives_tab");
        if (tab == null) tab = board.registerNewObjective("hydra_lives_tab", Criteria.DUMMY, Component.text(ChatColor.RED + "Lives"));
        tab.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        for (UUID playerId : alivePlayers) {
            tab.getScore(displayName(playerId)).setScore(Math.max(0, lives.getOrDefault(playerId, 0)));
        }
        Objective heads = board.getObjective("hydra_hp_head");
        if (heads == null) heads = board.registerNewObjective("hydra_hp_head", Criteria.HEALTH, Component.text(ChatColor.RED + "♥"));
        heads.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    private String sidebarSeparator(int variant) {
        return ChatColor.DARK_GRAY + "------------" + sidebarUniqueSuffix(variant);
    }

    private String sidebarBlank(int variant) {
        return sidebarUniqueSuffix(variant) + " ";
    }

    private String sidebarSystem(String label, String value, ChatColor valueColor) {
        return ChatColor.GRAY + label + ChatColor.DARK_GRAY + " • " + valueColor + value;
    }

    private String formatNumber(int value) {
        return String.format(Locale.US, "%,d", Math.max(0, value));
    }

    private String sidebarUniqueSuffix(int variant) {
        return ChatColor.COLOR_CHAR + Integer.toHexString(Math.floorMod(variant, 16));
    }

    private record BlockPosition(int x, int y, int z) {
    }

    private static final class VoidRoulettePlayerSnapshot {
        private final String worldName;
        private final boolean spectator;
        private final Location location;
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offHand;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final int lives;
        private final Location assignedSpawn;

        private VoidRoulettePlayerSnapshot(
            String worldName,
            boolean spectator,
            Location location,
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offHand,
            double health,
            int foodLevel,
            float saturation,
            int lives,
            Location assignedSpawn
        ) {
            this.worldName = worldName;
            this.spectator = spectator;
            this.location = location;
            this.contents = contents;
            this.armor = armor;
            this.offHand = offHand;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.lives = lives;
            this.assignedSpawn = assignedSpawn;
        }

        private boolean isRestorable(VoidRouletteService service) {
            return service != null
                && service.matchActive
                && worldName != null
                && service.activeWorldName != null
                && service.activeWorldName.equals(worldName)
                && Bukkit.getWorld(worldName) != null;
        }
    }

    private enum ExperimentalEvent {
        CROSSWIND("Crosswind", "A strong wind is crossing the arena."),
        LOW_GRAVITY("Low Gravity", "Jump higher and fall slower."),
        SUPPLY_SURGE("Supply Surge", "Three to ten rapid vanilla drops are incoming."),
        SWAP("Swap", "Everyone swaps positions. Keep your own aim."),
        SUDDEN_DEATH("Sudden Death", "Permanent death. The border and void are closing.");

        private final String displayName;
        private final String description;

        ExperimentalEvent(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    private enum ArenaLayout {
        RING("Ring"),
        DOUBLE_RING("Double Ring"),
        DIAMOND("Diamond");

        private static final ArenaLayout[] VALUES = values();
        private final String displayName;

        ArenaLayout(String displayName) {
            this.displayName = displayName;
        }

        private static ArenaLayout random() {
            return VALUES[ThreadLocalRandom.current().nextInt(VALUES.length)];
        }
    }

    private static final class EmptyVoidChunkGenerator extends ChunkGenerator {
        private final int spawnY;

        private EmptyVoidChunkGenerator(int spawnY) {
            this.spawnY = spawnY;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull java.util.Random random) {
            return new Location(world, 0.5D, spawnY, 0.5D);
        }
    }
}
