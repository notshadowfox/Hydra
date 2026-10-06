package hgloot.plugin;

import net.kyori.adventure.text.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;
import org.jetbrains.annotations.NotNull;
import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.util.HydraSounds;

/** Owns both the fourth Roulette round and the force-start-only Boss Rush test lifecycle. */
public final class BossRushService implements Listener {
    private static final int ARENA_WIDTH = 264;
    private static final int ARENA_HEIGHT = 68;
    private static final int ARENA_DEPTH = 273;
    private static final int PASTE_Y = 80;
    private static final int MOB_SPAWN_Y = 87;
    private static final int PEDESTAL_SPAWN_Y = MOB_SPAWN_Y;
    private static final int BOSS_START_FREEZE_TICKS = 10 * 20;
    private static final int WORLD_BORDER_SIZE = 800;
    private static final int TEAM_ARENA_OFFSET = 240;
    private static final int ROUND_SECONDS = 300;
    private static final int BARREL_REFILL_TICKS = 30 * 20;
    private static final int FIRST_EVENT_TICKS = 35 * 20;
    private static final int FIRST_PEDESTAL_TICKS = 45 * 20;
    private static final String SPECTATOR_MENU_TITLE = ChatColor.DARK_GREEN + "Boss Rush Spectate";
    private static final String SPECTATOR_ACTION_TELEPORT = "teleport";
    private static final String SPECTATOR_ACTION_HUB = "hub";
    private static final String SPECTATOR_ACTION_CLOSE = "close";
    private static final String SPECTATOR_ACTION_TARGET_PREFIX = "target:";
    private static final int[][] PLAYER_LODESTONE_MARKERS = {
        {97, 136},
        {136, 98},
        {136, 174},
        {174, 136}
    };
    private static final int MARKER_SCAN_HEIGHT = 16;
    private static final int BARREL_SCAN_RADIUS = 8;
    private static final int EXPECTED_REFILL_BARRELS = 32;
    private static final double HEALER_HEAL = 1.0D;
    private static final int HEALER_INTERVAL_TICKS = 10 * 20;
    private static final List<String> LEGENDARY_PEDESTAL_IDS = RecipeBookCatalog.approvedPublicLegendaryRecipeIds().stream()
        .map(id -> "hydramain:" + id)
        .toList();

    private final HGLootPlugin plugin;
    private final CombatMasterService combatMasterService;
    private final NamespacedKey spectatorActionKey;
    private final LinkedHashSet<UUID> queuedPlayers = new LinkedHashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Map<UUID, RouletteTeam> externalSpectators = new HashMap<>();
    private final Map<UUID, RouletteTeam> spectatorViewingTeams = new HashMap<>();
    private final Set<UUID> spectatorInHub = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, String> sidebarSignatures = new HashMap<>();
    private final Map<UUID, Long> damageActionBarHeldUntil = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> pendingRestores = new HashMap<>();
    private final Map<UUID, RouletteTeam> playerTeams = new HashMap<>();
    private final Map<UUID, Integer> playerSpawnSlots = new HashMap<>();
    private final Map<UUID, Location> playerStartingSpawns = new HashMap<>();
    private final Map<UUID, Location> frozenSpawnLocations = new HashMap<>();
    private final EnumMap<RouletteTeam, Double> teamDamage = new EnumMap<>(RouletteTeam.class);
    private final EnumMap<RouletteTeam, ArenaInstance> arenas = new EnumMap<>(RouletteTeam.class);
    private BossRushTemplateWorlds templateWorlds;
    private final Map<UUID, RouletteTeam> bossTeams = new HashMap<>();
    private final Set<UUID> eventMobs = new HashSet<>();
    private final Map<UUID, RouletteTeam> healerTeams = new HashMap<>();
    private State state = State.IDLE;
    private World activeWorld;
    private String activeWorldName;
    private BukkitTask startupTask;
    private BukkitTask matchTask;
    private int elapsedTicks;
    private int nextWaveTick;
    private int nextPedestalTick;
    private int pedestalCount;
    private long preparationStartedNanos;
    private long markerNanos;
    private long chunkReadyNanos;
    private long playerReadyNanos;
    private long bossSetupNanos;
    private long modelSetupNanos;
    private long barrelSetupNanos;
    private int readyChunks;
    private ManagedMinigameRound.Request managedRound;
    private boolean managedCallbackSent;

    public BossRushService(HGLootPlugin plugin, CombatMasterService combatMasterService) {
        this.plugin = plugin;
        this.combatMasterService = combatMasterService;
        this.spectatorActionKey = new NamespacedKey(plugin, "bossrush_spectator_action");
    }

    public void start() {
        try {
            templateWorlds = new BossRushTemplateWorlds(plugin, new EmptyArenaGenerator(PASTE_Y + 8),
                this::configureTemplateWorld, this::validatePreparedTemplate);
            templateWorlds.recoverStaleClones();
            plugin.getLogger().info("[BossRush] Four immutable arena templates configured; no runtime tile placement.");
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRush] Template manager failed to initialize.", ex);
        }
    }

    public boolean handleAdminCommand(Player player, String[] args) {
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "join" -> join(player);
            case "forcejoin" -> MinigameForceJoin.execute(player, args, 1, "bossrush", this::join,
                target -> queuedPlayers.contains(target.getUniqueId()));
            case "leave" -> leave(player);
            case "forcestart" -> forceStart(player);
            case "status" -> sendStatus(player);
            case "transition" -> previewTransition(player);
            case "stop", "forceend" -> stop(player);
            default -> sendUsage(player);
        }
        return true;
    }

    public boolean ownsPlayer(UUID playerId) {
        return playerId != null && (queuedPlayers.contains(playerId) || participants.contains(playerId)
            || externalSpectators.containsKey(playerId));
    }

    public boolean isQueuedForSpectatorCommand(UUID playerId) {
        return playerId != null && queuedPlayers.contains(playerId);
    }

    public boolean isExternalSpectator(UUID playerId) {
        return playerId != null && externalSpectators.containsKey(playerId);
    }

    /** Standalone Boss Rush activity only; MR contestants are counted under MR. */
    public int tabActivityCount() {
        if (managedRound != null) return 0;
        Set<UUID> active = new HashSet<>(queuedPlayers);
        active.addAll(participants);
        return (int) active.stream().filter(id -> {
            Player player = Bukkit.getPlayer(id);
            return player != null && player.isOnline();
        }).count();
    }

    /** Never exposes another arena's team or result to this viewer. */
    public RouletteTeam tabArenaTeam(Player viewer) {
        if (viewer == null || playerAudienceKey(viewer) == null) return null;
        UUID id = viewer.getUniqueId();
        return spectatorAudienceTeam(id);
    }

    public String spectatableMatchId(Player target) {
        if (target == null || !target.isOnline() || state != State.ACTIVE || activeWorld == null
            || target.getWorld() != activeWorld || !participants.contains(target.getUniqueId())) return null;
        RouletteTeam team = playerTeams.get(target.getUniqueId());
        return team == null || arenas.get(team) == null ? null
            : "bossrush:" + activeWorldName + ":" + team.name();
    }

    public boolean spectateTargetMatch(Player spectator, Player target, String expectedMatchId) {
        if (spectator == null || target == null || !spectator.isOnline() || !target.isOnline()
            || expectedMatchId == null || !expectedMatchId.equals(spectatableMatchId(target))) return false;
        UUID spectatorId = spectator.getUniqueId();
        if (ownsPlayer(spectatorId) || plugin.queueService().isPlayerActivelyQueuedOrBound(spectatorId)) return false;
        return plugin.transitionTravel(spectator, () -> {
            if (!spectator.isOnline() || !expectedMatchId.equals(spectatableMatchId(target))
                || ownsPlayer(spectatorId) || plugin.queueService().isPlayerActivelyQueuedOrBound(spectatorId)) {
                if (spectator.isOnline()) spectator.sendMessage(ChatColor.RED + "That match is no longer available to spectate.");
                SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, false);
                return;
            }
            Location spawn = playerStartingSpawn(target.getUniqueId());
            RouletteTeam team = playerTeams.get(target.getUniqueId());
            if (spawn == null || team == null || arenas.get(team) == null) {
                spectator.sendMessage(ChatColor.RED + "That match is no longer available to spectate.");
                SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, false);
                return;
            }
            registerExternalSpectator(spectator, team, spawn);
            try {
                prepareSpectator(spectator);
                spectator.sendMessage(ChatColor.GRAY + "Now spectating Boss Rush.");
                SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, true);
            } catch (RuntimeException ex) {
                externalSpectators.remove(spectatorId);
                spectatorViewingTeams.remove(spectatorId);
                playerStartingSpawns.remove(spectatorId);
                PlayerSnapshot snapshot = snapshots.remove(spectatorId);
                if (snapshot != null) snapshot.restore(spectator);
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[Spectate] Boss Rush external spectator setup failed for " + spectatorId, ex);
                spectator.sendMessage(ChatColor.RED + "That match is no longer available to spectate.");
                SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, false);
            }
        });
    }

    private void registerExternalSpectator(Player spectator, RouletteTeam team, Location spawn) {
        UUID id = spectator.getUniqueId();
        spectatorViewingTeams.remove(id);
        snapshots.put(id, PlayerSnapshot.capture(spectator));
        externalSpectators.put(id, team);
        playerStartingSpawns.put(id, spawn.clone());
    }

    public boolean isInActiveMatch(UUID playerId) {
        return playerId != null
            && participants.contains(playerId)
            && (state == State.PLAYER_FREEZE || state == State.ACTIVE);
    }

    public boolean ownsSidebar(Player player) {
        return player != null && (participants.contains(player.getUniqueId())
            || externalSpectators.containsKey(player.getUniqueId()))
            && player.getWorld() == activeWorld
            && (state == State.PLAYER_FREEZE || state == State.ACTIVE || state == State.ENDING);
    }

    public void updateBossRushSidebar(Player player) {
        if (!ownsSidebar(player)) return;
        RouletteTeam team = playerTeams.getOrDefault(player.getUniqueId(), externalSpectators.get(player.getUniqueId()));
        if (team == null) return;
        ArenaInstance arena = arenas.get(team);
        int teamAlive = (int) alivePlayers.stream().filter(id -> playerTeams.get(id) == team).count();
        String phase = state == State.PLAYER_FREEZE ? "Preparing"
            : state == State.ENDING ? "Finished"
            : arenaFinished(arena) ? "Complete" : "Fighting";
        int seconds = Math.max(0, ROUND_SECONDS - elapsedTicks / 20);
        List<String> lines = List.of(
            ChatColor.GRAY + "Team " + team.color() + team.displayName(),
            ChatColor.GRAY + "Status " + ChatColor.WHITE + phase,
            ChatColor.GRAY + "Alive " + ChatColor.WHITE + teamAlive,
            ChatColor.GRAY + "Damage " + ChatColor.GOLD + roundedDamage(team),
            ChatColor.GRAY + "Time " + ChatColor.WHITE + (seconds / 60) + ":" + String.format("%02d", seconds % 60),
            " ", ChatColor.AQUA + "HydraMC.net"
        );
        String signature = String.join("|", lines);
        Objective current = player.getScoreboard() == null ? null : player.getScoreboard().getObjective("bossrush");
        if (signature.equals(sidebarSignatures.get(player.getUniqueId()))
            && current != null && current.getDisplaySlot() == DisplaySlot.SIDEBAR) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = player.getScoreboard();
        if (board == null || board == manager.getMainScoreboard()) {
            board = manager.getNewScoreboard();
        }
        Objective previous = board.getObjective("bossrush");
        if (previous != null) previous.unregister();
        Objective objective = board.registerNewObjective("bossrush", Criteria.DUMMY,
            ChatColor.GOLD + "" + ChatColor.BOLD + "BOSS RUSH");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Set<String> used = new HashSet<>();
        int score = lines.size();
        for (String raw : lines) {
            String line = raw;
            while (!used.add(line)) line += ChatColor.RESET;
            objective.getScore(line).setScore(score--);
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
            if (plugin.hydraPlusService() != null) {
                plugin.hydraPlusService().applyToViewer(player);
            }
        }
        sidebarSignatures.put(player.getUniqueId(), signature);
    }

    public String playerAudienceKey(Player player) {
        if (player == null || !player.isOnline() || player.getWorld() != activeWorld
            || (!participants.contains(player.getUniqueId())
                && !externalSpectators.containsKey(player.getUniqueId()))) return null;
        RouletteTeam team = spectatorAudienceTeam(player.getUniqueId());
        return team == null ? null : "bossrush:" + activeWorld.getName() + ":" + team.name();
    }

    private RouletteTeam spectatorAudienceTeam(UUID playerId) {
        RouletteTeam home = playerTeams.getOrDefault(playerId, externalSpectators.get(playerId));
        return home != null && !alivePlayers.contains(playerId)
            ? spectatorViewingTeams.getOrDefault(playerId, home) : home;
    }

    public boolean isAudienceSpectator(UUID playerId) {
        return playerId != null && (externalSpectators.containsKey(playerId)
            || (participants.contains(playerId) && !alivePlayers.contains(playerId)));
    }

    public boolean startManagedRound(ManagedMinigameRound.Request request) {
        return startManagedRound(request, System.nanoTime());
    }

    boolean startManagedRound(ManagedMinigameRound.Request request, long rouletteSelectedNanos) {
        if (request == null || request.game() != ManagedMinigameRound.Game.BOSS_RUSH
            || state != State.IDLE || managedRound != null) {
            return false;
        }
        List<Player> online = request.roster().stream()
            .map(Bukkit::getPlayer)
            .filter(player -> player != null && player.isOnline())
            .toList();
        if (online.isEmpty()) {
            return false;
        }
        managedRound = request;
        managedCallbackSent = false;
        playerTeams.clear();
        online.forEach(player -> playerTeams.put(player.getUniqueId(),
            request.teams().getOrDefault(player.getUniqueId(), RouletteTeam.RED)));
        state = State.PREPARING;
        try {
            startMatch(online, rouletteSelectedNanos);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRush] Managed start failed.", ex);
            state = State.ENDING;
            restoreAllPlayers();
            cleanupWorld();
            ManagedMinigameRound.Request failed = managedRound;
            resetState();
            dispatchManagedResult(failed, ManagedMinigameRound.Result.failed(failed,
                "Boss Rush failed to start."));
            return true;
        }
    }

    public void stopManagedRound(String reason) {
        ManagedMinigameRound.Request request = managedRound;
        if (request == null) {
            return;
        }
        state = State.ENDING;
        removeAllBosses();
        restoreAllPlayers();
        cleanupWorld();
        resetState();
        dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, reason));
    }

    public boolean areTeammates(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)
            || (state != State.PREPARING && state != State.PLAYER_FREEZE && state != State.ACTIVE)
            || !participants.contains(first) || !participants.contains(second)) {
            return false;
        }
        RouletteTeam firstTeam = playerTeams.get(first);
        return firstTeam != null && firstTeam == playerTeams.get(second);
    }

    public boolean rejoin(Player player) {
        if (player == null || !participants.contains(player.getUniqueId()) || activeWorld == null || state != State.ACTIVE) {
            return false;
        }
        return plugin.transitionTravel(player, () -> {
            if (!participants.contains(player.getUniqueId()) || activeWorld == null || state != State.ACTIVE) return;
            spectatorInHub.remove(player.getUniqueId());
            if (alivePlayers.contains(player.getUniqueId())) {
                preparePlayer(player, playerStartingSpawn(player.getUniqueId()));
            } else {
                prepareSpectator(player);
            }
        });
    }

    public boolean returnTournamentSpectatorToHub(Player player) {
        if (!isBossRushSpectator(player)) {
            return false;
        }
        boolean started = plugin.transitionTravel(player, () -> {
            if (!isBossRushSpectator(player)) return;
            if (externalSpectators.containsKey(player.getUniqueId())) leaveExternalSpectator(player);
            else completeTournamentReturnToHub(player);
        });
        if (!started && externalSpectators.containsKey(player.getUniqueId())) {
            leaveExternalSpectator(player);
            return true;
        }
        return started;
    }

    private void leaveExternalSpectator(Player player) {
        UUID id = player.getUniqueId();
        spectatorViewingTeams.remove(id);
        restoreSpectatorPairVisibility(player);
        clearCustomSpectatorState(player);
        externalSpectators.remove(id);
        snapshots.remove(id);
        playerStartingSpawns.remove(id);
        sidebarSignatures.remove(id);
        plugin.queueService().sendPlayerToHub(player);
        plugin.queueService().refreshSidebar(player);
    }

    public boolean isAliveParticipant(UUID playerId) {
        return playerId != null && state == State.ACTIVE && alivePlayers.contains(playerId);
    }

    public void shutdown() {
        state = State.ENDING;
        cancelMatchTask();
        removeAllBosses();
        restoreAllPlayers();
        cleanupWorld();
        queuedPlayers.clear();
        participants.clear();
        alivePlayers.clear();
        externalSpectators.clear();
        spectatorViewingTeams.clear();
        snapshots.clear();
        pendingRestores.clear();
        state = State.IDLE;
    }

    private void join(Player player) {
        UUID playerId = player.getUniqueId();
        if (state != State.IDLE) {
            player.sendMessage(ChatColor.RED + "Boss Rush is already preparing or active.");
            return;
        }
        if (ownsOtherGame(playerId)) {
            player.sendMessage(ChatColor.RED + "Leave your current queue or match before joining Boss Rush.");
            return;
        }
        if (!queuedPlayers.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "You are already queued for Boss Rush.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Joined Boss Rush. An admin must use /admin bossrush forcestart.");
        prepareForTeamCount(1);
        broadcastQueue(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " joined Boss Rush "
            + ChatColor.WHITE + "(" + queuedPlayers.size() + " queued)" + ChatColor.GRAY + ".");
    }

    private void leave(Player player) {
        if (participants.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Use /admin bossrush stop to end an active Boss Rush test.");
            return;
        }
        if (!queuedPlayers.remove(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are not queued for Boss Rush.");
            return;
        }
        if (queuedPlayers.isEmpty()) discardUnclaimedArena();
        player.sendMessage(ChatColor.GREEN + "Left the Boss Rush queue.");
    }

    private void forceStart(Player admin) {
        if (state != State.IDLE) {
            admin.sendMessage(ChatColor.RED + "Boss Rush is already " + state.name().toLowerCase(java.util.Locale.ROOT) + ".");
            return;
        }
        List<Player> online = queuedPlayers.stream().map(Bukkit::getPlayer)
            .filter(player -> player != null && player.isOnline()).toList();
        if (online.isEmpty()) {
            admin.sendMessage(ChatColor.RED + "Boss Rush needs at least one online queued player.");
            return;
        }
        state = State.PREPARING;
        managedRound = null;
        managedCallbackSent = false;
        playerTeams.clear();
        online.forEach(player -> playerTeams.put(player.getUniqueId(), RouletteTeam.RED));
        admin.sendMessage(ChatColor.YELLOW + "Preparing The Verdant Sanctum for " + online.size() + " player(s)...");
        try {
            startMatch(online, System.nanoTime());
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRush] Force-start failed.", ex);
            broadcast(online, ChatColor.RED + "Boss Rush failed to start. The arena was cleaned up.");
            state = State.ENDING;
            restoreAllPlayers();
            cleanupWorld();
            resetState();
        }
    }

    private void previewTransition(Player admin) {
        if (state != State.ACTIVE || !participants.contains(admin.getUniqueId())) {
            admin.sendMessage(ChatColor.RED + "Join an active Boss Rush encounter to preview its transition.");
            return;
        }
        ArenaInstance arena = arenas.get(playerTeams.get(admin.getUniqueId()));
        if (arena == null || arena.bossId() == null
            || !combatMasterService.previewNextPhase(arena.bossId())) {
            admin.sendMessage(ChatColor.RED + "No next Combat Master phase is available right now.");
            return;
        }
        admin.sendMessage(ChatColor.YELLOW + "Combat Master phase transition started for your encounter.");
    }

    private void startMatch(List<Player> players, long selectedNanos) {
        preparationStartedNanos = selectedNanos;
        if (templateWorlds == null) throw new IllegalStateException("Template manager unavailable");
        int count = (int) players.stream()
            .map(player -> playerTeams.getOrDefault(player.getUniqueId(), RouletteTeam.RED)).distinct().count();
        configureArenaInstances(count);
        broadcast(players, ChatColor.GRAY + "Preparing the Boss Rush arena...");
        templateWorlds.claim(count, world -> {
            if (state != State.PREPARING) return;
            activeWorld = world;
            activeWorldName = world.getName();
            finishStart(players);
        }, ex -> failPreparation("Boss Rush template preparation failed: " + ex.getMessage(), ex));
    }

    public boolean forfeitTournamentPlayerToHub(Player player) {
        if (player == null || managedRound == null || state != State.ACTIVE
            || !alivePlayers.contains(player.getUniqueId()) || player.getWorld() != activeWorld) return false;
        eliminate(player);
        player.setInvulnerable(true);
        if (!plugin.transitionTravel(player, () -> {
            completeTournamentReturnToHub(player);
        })) {
            completeTournamentReturnToHub(player);
        }
        return true;
    }

    public void forgetTournamentLeaver(UUID playerId) {
        participants.remove(playerId);
        alivePlayers.remove(playerId);
        playerTeams.remove(playerId);
        playerStartingSpawns.remove(playerId);
        playerSpawnSlots.remove(playerId);
        snapshots.remove(playerId);
        pendingRestores.remove(playerId);
        frozenSpawnLocations.remove(playerId);
        damageActionBarHeldUntil.remove(playerId);
        sidebarSignatures.remove(playerId);
    }

    private void completeTournamentReturnToHub(Player player) {
        spectatorInHub.add(player.getUniqueId());
        spectatorViewingTeams.remove(player.getUniqueId());
        restoreSpectatorPairVisibility(player);
        clearCustomSpectatorState(player);
        plugin.queueService().sendPlayerToHub(player);
        sidebarSignatures.remove(player.getUniqueId());
        if (managedRound != null && plugin.minigameRouletteService() != null) {
            plugin.minigameRouletteService().refreshTournamentSidebar(player);
        } else {
            plugin.queueService().refreshSidebar(player);
        }
    }

    /** Queue/roster phase may warm exactly the required prefix before launch. */
    public void prepareForTeamCount(int count) {
        if (templateWorlds != null && state == State.IDLE) {
            configureArenaInstances(count);
            templateWorlds.prepare(count);
        }
    }

    /** Release a queue or tournament prewarm that was never claimed by a match. */
    public void discardUnclaimedArena() {
        if (templateWorlds != null && state == State.IDLE
            && templateWorlds.state() != BossRushTemplateWorlds.State.CLAIMED) {
            templateWorlds.dispose();
            arenas.clear();
        }
    }

    private void configureArenaInstances(int count) {
        arenas.clear();
        RouletteTeam[] order = {RouletteTeam.RED, RouletteTeam.BLUE, RouletteTeam.GREEN, RouletteTeam.YELLOW};
        for (int index = 0; index < count; index++) {
            RouletteTeam team = order[index];
            int[] center = arenaCenter(team);
            int pasteX = center[0] - Math.floorDiv(ARENA_WIDTH, 2);
            int pasteZ = center[1] - Math.floorDiv(ARENA_DEPTH, 2);
            arenas.put(team, new ArenaInstance(team, center[0], center[1], pasteX, pasteZ,
                new Bounds(pasteX, PASTE_Y, pasteZ, pasteX + ARENA_WIDTH - 1,
                    PASTE_Y + ARENA_HEIGHT - 1, pasteZ + ARENA_DEPTH - 1)));
        }
    }

    private boolean validatePreparedTemplate(World world) {
        World previous = activeWorld;
        activeWorld = world;
        try {
            discoverArenaMarkers();
            for (ArenaInstance arena : arenas.values()) {
                for (Location location : arena.barrels()) {
                    if (!(world.getBlockAt(location).getState() instanceof org.bukkit.block.Barrel barrel)
                        || !barrel.getInventory().isEmpty()) return false;
                }
            }
            return world.getEntities().isEmpty();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRush] Template marker validation failed", ex);
            return false;
        } finally {
            activeWorld = previous;
        }
    }

    private void configureTemplateWorld(World world) {
        world.setTime(18000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setPVP(true);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.getWorldBorder().setCenter(0.0D, 0.0D);
        world.getWorldBorder().setSize(WORLD_BORDER_SIZE);
    }

    private void finishStart(List<Player> requestedPlayers) {
        if (state != State.PREPARING || activeWorld == null) {
            return;
        }
        List<Player> players = requestedPlayers.stream().filter(Player::isOnline).toList();
        if (players.isEmpty()) {
            failPreparation("No queued players remained online after arena preparation.", null);
            return;
        }

        long stageStarted = System.nanoTime();
        try {
            discoverArenaMarkers();
        } catch (RuntimeException exception) {
            failPreparation("Arena marker validation failed: " + exception.getMessage(), exception);
            return;
        }
        markerNanos = System.nanoTime() - stageStarted;

        stageStarted = System.nanoTime();
        queuedPlayers.clear();
        for (Player player : players) {
            snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
            participants.add(player.getUniqueId());
            alivePlayers.add(player.getUniqueId());
        }
        assignSpawnSlots(players);
        playerStartingSpawns.clear();
        for (Player player : players) {
            RouletteTeam team = playerTeams.getOrDefault(player.getUniqueId(), RouletteTeam.RED);
            playerStartingSpawns.put(player.getUniqueId(), teamSpawnFor(player.getUniqueId(), team));
        }
        long chunkStarted = System.nanoTime();
        Set<Long> checkedChunks = new HashSet<>();
        for (ArenaInstance arena : arenas.values()) {
            ensureReadyChunk(arena.bossSpawn(), checkedChunks);
        }
        for (Player player : players) {
            ensureReadyChunk(playerStartingSpawn(player.getUniqueId()), checkedChunks);
        }
        readyChunks = checkedChunks.size();
        chunkReadyNanos = System.nanoTime() - chunkStarted;
        long playerPreparationStarted = stageStarted;
        state = State.PLAYER_FREEZE;
        for (Player player : players) {
            Location spawn = playerStartingSpawn(player.getUniqueId());
            if (!plugin.transitionTravel(player, () -> {
                if (state != State.PLAYER_FREEZE || activeWorld != spawn.getWorld()
                    || !participants.contains(player.getUniqueId())) return;
                try {
                    preparePlayer(player, spawn);
                    freezePlayerForStartup(player, spawn);
                    playerReadyNanos = Math.max(playerReadyNanos, System.nanoTime() - playerPreparationStarted);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[BossRush] Player transfer failed during fade for " + player.getName(), exception);
                    failFrozenStart("A player could not enter the Boss Rush arena.");
                }
            })) {
                failFrozenStart("A player was already in a screen transition during Boss Rush startup.");
                return;
            }
        }
        players.forEach(this::updateBossRushSidebar);
        plugin.getLogger().info("[BossRush] Player fades scheduled after arena placement players=" + players.size()
            + " world=" + activeWorldName + " boss_spawn_pending_ticks=20");
        startupTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> spawnBossesAndReleasePlayers(players), 20L);
    }

    private void spawnBossesAndReleasePlayers(List<Player> requestedPlayers) {
        startupTask = null;
        if (state != State.PLAYER_FREEZE || activeWorld == null) {
            return;
        }
        List<Player> players = requestedPlayers.stream()
            .filter(Player::isOnline)
            .filter(player -> player.getWorld() == activeWorld)
            .toList();
        if (players.isEmpty()) {
            failFrozenStart("No players remained in the arena for boss startup.");
            return;
        }

        teamDamage.clear();
        for (RouletteTeam team : RouletteTeam.values()) teamDamage.put(team, 0.0D);
        long bossStarted = System.nanoTime();
        for (ArenaInstance arena : arenas.values()) {
            List<Player> teamPlayers = players.stream()
                .filter(player -> playerTeams.get(player.getUniqueId()) == arena.team())
                .toList();
            UUID bossId = combatMasterService.spawnCombatMasterEncounter(arena.bossSpawn(),
                completion -> onBossComplete(arena.team(), completion));
            if (bossId == null || !combatMasterService.bindEncounter(bossId, teamPlayers)) {
                if (bossId != null) combatMasterService.removeCombatMaster(bossId);
                failFrozenStart("Combat Master failed readiness binding for " + arena.team().displayName() + " Team.");
                return;
            }
            if (plugin.getConfig().getBoolean("combat-master.bossrush-visual-test", false)) {
                long modelStarted = System.nanoTime();
                boolean attached = combatMasterService.attachEncounterVisual(bossId);
                modelSetupNanos += System.nanoTime() - modelStarted;
                if (!attached) {
                    plugin.getLogger().warning("[BossRush] Optional Combat Master visual unavailable for "
                        + arena.team().displayName() + " Team; continuing with V1 appearance.");
                }
            }
            arena.bossId(bossId);
            bossTeams.put(bossId, arena.team());
        }
        bossSetupNanos = System.nanoTime() - bossStarted - modelSetupNanos;
        elapsedTicks = 0;
        nextWaveTick = FIRST_EVENT_TICKS + ThreadLocalRandom.current().nextInt(0, 20 * 25);
        nextPedestalTick = FIRST_PEDESTAL_TICKS + ThreadLocalRandom.current().nextInt(0, 20 * 30);
        long barrelStarted = System.nanoTime();
        refillBarrels();
        barrelSetupNanos = System.nanoTime() - barrelStarted;
        for (ArenaInstance arena : arenas.values()) {
            if (!combatMasterService.freezeCombatMaster(arena.bossId(), BOSS_START_FREEZE_TICKS)) {
                failFrozenStart("Combat Master could not enter its encounter freeze for "
                    + arena.team().displayName() + " Team.");
                return;
            }
        }
        startEncounterCountdown(players);
        plugin.getLogger().info("[BossRush] Started world=" + activeWorldName + ", players=" + players.size()
            + ", arenas=" + arenas.size() + ", bosses=" + bossTeams.size() + ", source=validated_template_clone");
    }

    private void freezePlayerForStartup(Player player, Location spawn) {
        frozenSpawnLocations.put(player.getUniqueId(), spawn.clone());
        player.setInvulnerable(true);
        player.sendTitle(ChatColor.GOLD + "BOSS RUSH", ChatColor.YELLOW + "Prepare for battle...", 5, 30, 5);
    }

    private void releaseStartupFreeze(List<Player> players) {
        for (Player player : players) {
            frozenSpawnLocations.remove(player.getUniqueId());
            player.setInvulnerable(false);
        }
    }

    private void startEncounterCountdown(List<Player> players) {
        final int[] seconds = {5};
        startupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.PLAYER_FREEZE || activeWorld == null) {
                if (startupTask != null) startupTask.cancel();
                startupTask = null;
                return;
            }
            if (seconds[0] > 0) {
                players.stream().filter(Player::isOnline).forEach(player -> {
                    player.sendTitle(ChatColor.GOLD + Integer.toString(seconds[0]), "", 0, 22, 0);
                    HydraSounds.playCountdownTick(player, seconds[0]);
                });
                seconds[0]--;
                return;
            }
            players.stream().filter(Player::isOnline).forEach(player -> {
                player.sendTitle(ChatColor.GREEN + "GO", "", 0, 20, 5);
                HydraSounds.playGo(player);
            });
            if (startupTask != null) startupTask.cancel();
            startupTask = null;
            state = State.ACTIVE;
            releaseStartupFreeze(players);
            logPreparationProfile();
            startMatchTicker();
            broadcastPlayers(ChatColor.AQUA + "BOSS RUSH" + ChatColor.GRAY
                + " - Deal the most damage to Combat Master.");
        }, 0L, 20L);
    }

    private void failFrozenStart(String reason) {
        plugin.getLogger().severe("[BossRush] " + reason);
        broadcastPlayers(ChatColor.RED + "Boss Rush failed during boss startup. The arena is being cleaned up.");
        state = State.ENDING;
        removeAllBosses();
        restoreAllPlayers();
        cleanupWorld();
        ManagedMinigameRound.Request request = managedRound;
        resetState();
        if (request != null) {
            dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, reason));
        }
    }

    private void preparePlayer(Player player, Location spawn) {
        plugin.menuService().preparePlayerForMatch(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
        }
        player.setHealth(20.0D);
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        grantStandardKit(player.getInventory(), player.getUniqueId(), plugin.kitEditorService());
        player.teleport(spawn);
        updateBossRushSidebar(player);
        player.updateInventory();
        player.sendTitle(ChatColor.GOLD + "BOSS RUSH", ChatColor.AQUA + "Combat Master", 10, 50, 10);
    }

    static void grantStandardKit(PlayerInventory inventory, UUID playerId, KitEditorService editor) {
        inventory.setArmorContents(standardKitArmor());
        if (editor != null) {
            editor.equipBossRushKit(inventory, playerId);
        } else {
            KitDefinition kit = new BossRushKitDefinition();
            KitLayout.place(inventory, standardKitItems(), KitLayout.normalize(kit, null));
        }
    }

    private static ItemStack enchantedItem(Material material, Enchantment enchantment) {
        ItemStack item = new ItemStack(material);
        item.addEnchantment(enchantment, 1);
        return item;
    }

    private static ItemStack bossArmor(Material material) {
        ItemStack item = enchantedItem(material, Enchantment.PROTECTION);
        item.addEnchantment(Enchantment.BINDING_CURSE, 1);
        item.addEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    private void onBossComplete(RouletteTeam team, CombatMasterService.Completion completion) {
        ArenaInstance arena = arenas.get(team);
        if (arena == null) return;
        if (arena.bossId() != null) {
            bossTeams.remove(arena.bossId());
            arena.bossId(null);
        }
        if (state != State.ACTIVE || !arena.resolve(completion)) return;
        clearArenaMobs(arena);
        for (UUID playerId : participants) {
            if (playerTeams.get(playerId) != team) continue;
            alivePlayers.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && !spectatorInHub.contains(playerId)) prepareSpectator(player);
        }
        broadcastTeam(team, completion == CombatMasterService.Completion.DEFEATED
            ? team.color() + team.displayName() + " Team defeated its Combat Master!"
            : team.color() + team.displayName() + " Team's Combat Master became unavailable.");
        if (allTeamsFinished()) finishMatch("All team arenas finished", false);
    }

    private void discoverArenaMarkers() {
        for (ArenaInstance arena : arenas.values()) {
            arena.barrels().clear();
            arena.playerSpawns().clear();
            for (int[] marker : PLAYER_LODESTONE_MARKERS) {
                int worldX = arena.pasteX() + marker[0];
                int worldZ = arena.pasteZ() + marker[1];
                int highestLodestone = Integer.MIN_VALUE;
                for (int y = PASTE_Y; y < PASTE_Y + MARKER_SCAN_HEIGHT; y++) {
                    if (activeWorld.getBlockAt(worldX, y, worldZ).getType() == Material.LODESTONE) {
                        highestLodestone = y;
                    }
                }
                if (highestLodestone == Integer.MIN_VALUE) {
                    throw new IllegalStateException("Missing player lodestone for " + arena.team().displayName()
                        + " Team at local " + marker[0] + "," + marker[1]);
                }
                arena.playerSpawns().add(new Location(activeWorld,
                    worldX + 0.5D, highestLodestone + 1.0D, worldZ + 0.5D));
            }
            arena.bossSpawn(arenaMobSpawn(arena, 4, 0));
            for (int[] zone : PLAYER_LODESTONE_MARKERS) {
                int zoneX = arena.pasteX() + zone[0];
                int zoneZ = arena.pasteZ() + zone[1];
                for (int x = zoneX - BARREL_SCAN_RADIUS; x <= zoneX + BARREL_SCAN_RADIUS; x++) {
                    for (int z = zoneZ - BARREL_SCAN_RADIUS; z <= zoneZ + BARREL_SCAN_RADIUS; z++) {
                        for (int y = PASTE_Y; y < PASTE_Y + MARKER_SCAN_HEIGHT; y++) {
                            if (activeWorld.getBlockAt(x, y, z).getType() == Material.BARREL) {
                                Location location = new Location(activeWorld, x, y, z);
                                if (!arena.barrels().contains(location)) arena.barrels().add(location);
                            }
                        }
                    }
                }
            }
            if (arena.barrels().size() != EXPECTED_REFILL_BARRELS) {
                throw new IllegalStateException("Expected " + EXPECTED_REFILL_BARRELS + " refill barrels for "
                    + arena.team().displayName() + " Team but found " + arena.barrels().size());
            }
            plugin.getLogger().info("[BossRush] Arena markers team=" + arena.team().displayName()
                + " player_spawns=" + arena.playerSpawns().size() + " barrels=" + arena.barrels().size());
        }
        plugin.getLogger().info("[BossRush] Team arena markers verified arenas=" + arenas.size());
    }

    private Location teamSpawnFor(UUID playerId, RouletteTeam team) {
        ArenaInstance arena = arenas.get(team);
        if (arena == null || arena.playerSpawns().isEmpty()) throw new IllegalStateException("Missing arena for " + team);
        int teammateIndex = (int) playerSpawnSlots.entrySet().stream()
            .filter(entry -> playerTeams.get(entry.getKey()) == team)
            .filter(entry -> entry.getValue() < playerSpawnSlots.getOrDefault(playerId, 0))
            .count();
        return faceBoss(arena.playerSpawns().get(teammateIndex % arena.playerSpawns().size()), arena.bossSpawn());
    }

    static Map<String, ItemStack> standardKitItems() {
        Map<String, ItemStack> items = new LinkedHashMap<>();
        items.put("sword", enchantedItem(Material.DIAMOND_SWORD, Enchantment.SHARPNESS));
        items.put("axe", enchantedItem(Material.DIAMOND_AXE, Enchantment.SHARPNESS));
        items.put("bow", enchantedItem(Material.BOW, Enchantment.POWER));
        items.put("food", new ItemStack(Material.COOKED_BEEF, 5));
        items.put("golden_apples", new ItemStack(Material.GOLDEN_APPLE, 3));
        items.put("arrows", new ItemStack(Material.ARROW, 10));
        items.put("shield", new ItemStack(Material.SHIELD));
        return items;
    }

    static ItemStack[] standardKitArmor() {
        return new ItemStack[] {
            bossArmor(Material.DIAMOND_BOOTS), bossArmor(Material.DIAMOND_LEGGINGS),
            bossArmor(Material.DIAMOND_CHESTPLATE), bossArmor(Material.DIAMOND_HELMET)
        };
    }

    static Location faceBoss(Location assignedSpawn, Location bossSpawn) {
        Location spawn = assignedSpawn.clone();
        if (bossSpawn == null || spawn.getWorld() != bossSpawn.getWorld()) return spawn;
        double dx = bossSpawn.getX() - spawn.getX();
        double dz = bossSpawn.getZ() - spawn.getZ();
        if (dx != 0.0D || dz != 0.0D) {
            spawn.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        return spawn;
    }

    private Location playerStartingSpawn(UUID playerId) {
        Location spawn = playerStartingSpawns.get(playerId);
        return spawn == null ? null : spawn.clone();
    }

    private void assignSpawnSlots(List<Player> players) {
        playerSpawnSlots.clear();
        for (int index = 0; index < players.size(); index++) {
            playerSpawnSlots.put(players.get(index).getUniqueId(), index);
        }
    }

    private void startMatchTicker() {
        if (matchTask != null) matchTask.cancel();
        matchTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.ACTIVE) return;
            elapsedTicks += 20;
            for (ArenaInstance arena : arenas.values()) {
                if (arena.completed() || arena.eliminated()) continue;
                if (arena.bossId() == null) {
                    onBossComplete(arena.team(), CombatMasterService.Completion.CANCELED);
                    if (state != State.ACTIVE) return;
                    continue;
                }
                if (!combatMasterService.maintainCombatMaster(arena.bossId())) {
                    if (state != State.ACTIVE) return;
                    onBossComplete(arena.team(), CombatMasterService.Completion.CANCELED);
                    if (state != State.ACTIVE) return;
                }
            }
            if (allTeamsFinished()) { finishMatch("All team arenas finished", false); return; }
            if (elapsedTicks % BARREL_REFILL_TICKS == 0) refillBarrels();
            if (elapsedTicks >= nextWaveTick) {
                arenas.values().stream().filter(this::arenaIsActive).forEach(this::spawnRandomCombatWave);
                nextWaveTick = elapsedTicks + ThreadLocalRandom.current().nextInt(45 * 20, 76 * 20);
            }
            if (elapsedTicks >= nextPedestalTick) {
                arenas.values().stream().filter(this::arenaIsActive).forEach(this::spawnLegendaryPedestal);
                nextPedestalTick = elapsedTicks + ThreadLocalRandom.current().nextInt(60 * 20, 101 * 20);
            }
            tickHealers();
            sendDamageActionBars();
            List<UUID> viewers = new ArrayList<>(participants);
            viewers.addAll(externalSpectators.keySet());
            for (UUID playerId : viewers) {
                Player viewer = Bukkit.getPlayer(playerId);
                if (viewer != null && viewer.isOnline()) updateBossRushSidebar(viewer);
            }
            if (elapsedTicks >= ROUND_SECONDS * 20) finishMatch("Time expired", false);
        }, 20L, 20L);
    }

    private void refillBarrels() {
        for (Location location : arenas.values().stream().flatMap(arena -> arena.barrels().stream()).toList()) {
            if (!(location.getBlock().getState() instanceof Container container)) continue;
            container.getInventory().clear();
            container.getInventory().setItem(ThreadLocalRandom.current().nextInt(container.getInventory().getSize()),
                new ItemStack(Material.GOLDEN_APPLE, 1));
            addRandomBarrelItem(container, new ItemStack(Material.COOKED_BEEF, ThreadLocalRandom.current().nextInt(2, 6)));
            ItemStack utility = switch (ThreadLocalRandom.current().nextInt(5)) {
                case 0 -> new ItemStack(Material.ARROW, ThreadLocalRandom.current().nextInt(3, 9));
                case 1 -> new ItemStack(Material.SHIELD);
                case 2 -> new ItemStack(Material.EXPERIENCE_BOTTLE, ThreadLocalRandom.current().nextInt(2, 5));
                case 3 -> new ItemStack(Material.COOKED_PORKCHOP, ThreadLocalRandom.current().nextInt(2, 5));
                default -> new ItemStack(Material.GOLDEN_CARROT, ThreadLocalRandom.current().nextInt(2, 5));
            };
            addRandomBarrelItem(container, utility);
        }
        if (state == State.ACTIVE) broadcastPlayers(ChatColor.GOLD + "Arena barrels refilled.");
    }

    private void addRandomBarrelItem(Container container, ItemStack item) {
        int size = container.getInventory().getSize();
        for (int attempt = 0; attempt < size; attempt++) {
            int slot = ThreadLocalRandom.current().nextInt(size);
            if (container.getInventory().getItem(slot) == null) {
                container.getInventory().setItem(slot, item);
                return;
            }
        }
    }

    private void spawnRandomCombatWave(ArenaInstance arena) {
        if (activeWorld == null || !arenaIsActive(arena)) return;
        int wave = ThreadLocalRandom.current().nextInt(4);
        int count = switch (wave) { case 0 -> 5; case 1 -> 6; case 2 -> 7; default -> 6; };
        List<UUID> spawned = plugin.combatChamberService().spawnBossRushWave(arenaMobSpawn(arena, 0, 0), wave, count);
        eventMobs.addAll(spawned);
        arena.eventMobs().addAll(spawned);
        Location healerAt = arenaMobSpawn(arena, 11, 11);
        healerTeams.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            return entity == null || !entity.isValid() || entity.isDead();
        });
        boolean hasHealer = healerTeams.containsValue(arena.team());
        if (!hasHealer) {
            Witch healer = activeWorld.spawn(healerAt, Witch.class, mob -> {
                mob.setCustomName(ChatColor.LIGHT_PURPLE + "Boss Healer");
                mob.setCustomNameVisible(true);
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
            });
            eventMobs.add(healer.getUniqueId());
            arena.eventMobs().add(healer.getUniqueId());
            healerTeams.put(healer.getUniqueId(), arena.team());
        }
        broadcastTeam(arena.team(), ChatColor.DARK_GREEN + "A Combat Chamber wave has entered your Sanctum!");
    }

    private void tickHealers() {
        if (elapsedTicks % HEALER_INTERVAL_TICKS != 0) return;
        for (Map.Entry<UUID, RouletteTeam> entry : new ArrayList<>(healerTeams.entrySet())) {
            UUID healerId = entry.getKey();
            Entity entity = Bukkit.getEntity(healerId);
            if (!(entity instanceof Witch healer) || !healer.isValid() || healer.isDead()) {
                healerTeams.remove(healerId);
                eventMobs.remove(healerId);
                continue;
            }
            ArenaInstance arena = arenas.get(entry.getValue());
            double healed = arena == null || arena.bossId() == null ? 0.0D
                : combatMasterService.healBoss(arena.bossId(), HEALER_HEAL);
            if (healed > 0.0D) {
                healer.getWorld().spawnParticle(org.bukkit.Particle.HEART, healer.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }

    private void spawnLegendaryPedestal(ArenaInstance arena) {
        if (plugin.zxdzeroEventsCoreService() == null || activeWorld == null || !arenaIsActive(arena)) return;
        double angle = pedestalCount++ * 2.399963229728653D;
        Location at = arenaLocationAtY(arena, (int) Math.round(Math.cos(angle) * 12.0D),
            PEDESTAL_SPAWN_Y, (int) Math.round(Math.sin(angle) * 12.0D));
        String id = LEGENDARY_PEDESTAL_IDS.get(ThreadLocalRandom.current().nextInt(LEGENDARY_PEDESTAL_IDS.size()));
        if (plugin.zxdzeroEventsCoreService().placeRewardPedestal(at, id)) {
            broadcastTeam(arena.team(), ChatColor.LIGHT_PURPLE + "A legendary appeared on a pedestal!");
        }
    }

    private void sendDamageActionBars() {
        long now = System.nanoTime();
        for (UUID id : participants) {
            if (!alivePlayers.contains(id) || now < damageActionBarHeldUntil.getOrDefault(id, 0L)) continue;
            Player player = Bukkit.getPlayer(id);
            RouletteTeam team = playerTeams.get(id);
            ArenaInstance arena = arenas.get(team);
            if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR
                && team != null && (arena == null || arena.bossId() == null
                    || !combatMasterService.isTransitioning(arena.bossId()))) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    damageRaceLine(team, teamDamage, playerTeams.values())));
            }
        }
    }

    /** Briefly reserves the actionbar for a more important Boss Rush message. */
    public void holdDamageActionBar(Player player, long millis) {
        if (player == null || !participants.contains(player.getUniqueId())) return;
        damageActionBarHeldUntil.put(player.getUniqueId(), System.nanoTime() + Math.max(0L, millis) * 1_000_000L);
    }

    static String damageRaceLine(RouletteTeam own, Map<RouletteTeam, Double> damage,
                                 Collection<RouletteTeam> teams) {
        List<RouletteTeam> ranked = teams.stream().distinct()
            .sorted(Comparator.comparingLong((RouletteTeam team) -> Math.round(damage.getOrDefault(team, 0.0D)))
                .reversed().thenComparingInt(RouletteTeam::ordinal)).toList();
        long ownDamage = Math.round(damage.getOrDefault(own, 0.0D));
        String prefix = ChatColor.AQUA + "YOUR TEAM " + own.color() + own.displayName()
            + ChatColor.WHITE + " " + String.format(java.util.Locale.US, "%,d", ownDamage) + " DMG";
        if (ranked.isEmpty()) return prefix;
        RouletteTeam leader = ranked.get(0);
        long leadingDamage = Math.round(damage.getOrDefault(leader, 0.0D));
        if (ownDamage == leadingDamage) {
            long tied = ranked.stream().filter(team -> Math.round(damage.getOrDefault(team, 0.0D)) == leadingDamage).count();
            return prefix + ChatColor.GRAY + "  •  " + (tied == 1
                ? ChatColor.GREEN + "LEADING" : ChatColor.AQUA + "TIED FOR LEAD");
        }
        return prefix + ChatColor.GRAY + "  •  LEADER " + leader.color() + leader.displayName()
            + ChatColor.WHITE + " " + String.format(java.util.Locale.US, "%,d", leadingDamage);
    }

    private long roundedDamage(RouletteTeam team) {
        return Math.round(teamDamage.getOrDefault(team, 0.0D));
    }

    private void finishMatch(String reason, boolean bossDefeated) {
        if (state != State.ACTIVE) return;
        state = State.ENDING;
        if (matchTask != null) { matchTask.cancel(); matchTask = null; }
        removeAllBosses();
        List<RouletteTeam> placements = activeTeamsByDamage();
        RouletteTeam winner = placements.isEmpty() ? RouletteTeam.RED : placements.get(0);
        broadcastPlayers(winner.color() + winner.displayName() + " Team wins Boss Rush with "
            + roundedDamage(winner) + " damage. " + ChatColor.GRAY + reason + ".");
        boolean managed = managedRound != null;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            boolean won = playerTeams.get(playerId) == winner;
            if (managed) {
                player.sendTitle(ChatColor.AQUA + "" + ChatColor.BOLD + "ROUND COMPLETE",
                    winner.color() + winner.displayName() + ChatColor.GRAY + " Team led Boss Rush", 5, 40, 10);
                HydraSounds.playRoundResult(player);
            } else {
                player.sendTitle(won ? ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY"
                        : ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT",
                    winner.color() + winner.displayName() + ChatColor.GRAY + " Team led with "
                        + ChatColor.WHITE + roundedDamage(winner) + ChatColor.GRAY + " damage", 5, 45, 10);
                if (won) HydraSounds.playGameWin(player);
                else HydraSounds.playGameLoss(player);
            }
        }
        if (bossDefeated) {
            putAllParticipantsInSpectator();
        }
        ManagedMinigameRound.Request request = managedRound;
        List<UUID> requeueCandidates = List.copyOf(participants);
        Map<RouletteTeam, Integer> objectiveScores = new EnumMap<>(RouletteTeam.class);
        for (RouletteTeam team : RouletteTeam.values()) {
            objectiveScores.put(team, (int) Math.min(Integer.MAX_VALUE, roundedDamage(team)));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Player> returning = new ArrayList<>();
            List<UUID> returningIds = new ArrayList<>(participants);
            returningIds.addAll(externalSpectators.keySet());
            for (UUID playerId : returningIds) {
                if (spectatorInHub.contains(playerId)) continue;
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) returning.add(player);
                else restoreOnePlayer(playerId);
            }
            plugin.transitionTravelGroup(returning, player -> {
                restoreSpectatorPairVisibility(player);
                restoreOnePlayer(player.getUniqueId());
            }, () -> {
                cleanupWorld();
                resetState();
                if (request == null) {
                    plugin.playerSettingsService().requeueAfterCompletedSession(
                        requeueCandidates, PlayerSettingsService.AutoQueue.BOSS_RUSH);
                }
                if (request != null) {
                    dispatchManagedResult(request,
                        ManagedMinigameRound.Result.teamsByObjective(request, placements, objectiveScores));
                }
            });
        }, 60L - TransitionService.Preset.NORMAL.fadeOutTicks);
    }

    private List<RouletteTeam> activeTeamsByDamage() {
        LinkedHashSet<RouletteTeam> active = new LinkedHashSet<>(playerTeams.values());
        List<RouletteTeam> placements = new ArrayList<>(active);
        placements.sort(Comparator.comparingDouble((RouletteTeam team) -> teamDamage.getOrDefault(team, 0.0D)).reversed()
            .thenComparingInt(RouletteTeam::ordinal));
        return placements;
    }

    private void eliminate(Player player) {
        if (!alivePlayers.remove(player.getUniqueId())) {
            return;
        }
        RouletteTeam team = playerTeams.get(player.getUniqueId());
        broadcastTeam(team, ChatColor.RED + player.getName() + ChatColor.GRAY + " was eliminated.");
        if (team != null && alivePlayers.stream().noneMatch(id -> playerTeams.get(id) == team)) {
            ArenaInstance arena = arenas.get(team);
            if (arena != null && !arena.completed()) {
                arena.eliminated(true);
                removeBoss(arena);
                clearArenaMobs(arena);
                for (UUID playerId : participants) {
                    if (playerTeams.get(playerId) != team) continue;
                    if (playerId.equals(player.getUniqueId())) continue;
                    Player teammate = Bukkit.getPlayer(playerId);
                    if (teammate != null && teammate.isOnline() && !teammate.isDead()) prepareSpectator(teammate);
                }
            }
        }
        if (allTeamsFinished() && state == State.ACTIVE) finishMatch("All team arenas finished", false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        RouletteTeam bossTeam = bossTeams.get(event.getEntity().getUniqueId());
        if (state != State.ACTIVE || bossTeam == null) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || !participants.contains(attacker.getUniqueId())
            || !alivePlayers.contains(attacker.getUniqueId())
            || attacker.getWorld() != activeWorld || attacker.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        RouletteTeam team = playerTeams.getOrDefault(attacker.getUniqueId(), RouletteTeam.RED);
        if (team != bossTeam) {
            event.setCancelled(true);
            return;
        }
        ArenaInstance arena = arenas.get(team);
        if (arena == null || arena.bossId() == null) return;
        double credited = event.isCancelled()
            ? combatMasterService.consumePhaseGateDamage(arena.bossId())
            : Math.min(event.getFinalDamage(), Math.max(0.0D,
                ((org.bukkit.entity.LivingEntity) event.getEntity()).getHealth()));
        if (credited > 0.0D) teamDamage.merge(team, credited, Double::sum);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenEncounterDamage(EntityDamageByEntityEvent event) {
        if (state != State.PLAYER_FREEZE || activeWorld == null) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null && participants.contains(attacker.getUniqueId())
            && attacker.getWorld() == activeWorld) {
            event.setCancelled(true);
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private boolean arenaIsActive(ArenaInstance arena) {
        return arena != null && !arena.completed() && !arena.eliminated() && arena.bossId() != null;
    }

    private boolean arenaFinished(ArenaInstance arena) {
        return arena == null || arena.completed() || arena.eliminated();
    }

    private boolean allTeamsFinished() {
        return !arenas.isEmpty() && arenas.values().stream().allMatch(this::arenaFinished);
    }

    private void removeBoss(ArenaInstance arena) {
        if (arena == null || arena.bossId() == null) return;
        UUID bossId = arena.bossId();
        arena.bossId(null);
        bossTeams.remove(bossId);
        combatMasterService.removeCombatMaster(bossId);
    }

    private void removeAllBosses() {
        for (ArenaInstance arena : arenas.values()) removeBoss(arena);
        bossTeams.clear();
    }

    private void clearArenaMobs(ArenaInstance arena) {
        for (UUID id : new ArrayList<>(arena.eventMobs())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
            eventMobs.remove(id);
            healerTeams.remove(id);
        }
        arena.eventMobs().clear();
    }

    private void broadcastTeam(RouletteTeam team, String message) {
        for (UUID playerId : participants) {
            if (playerTeams.get(playerId) != team) continue;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) player.sendMessage(message);
        }
        for (Map.Entry<UUID, RouletteTeam> entry : externalSpectators.entrySet()) {
            if (entry.getValue() != team) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && player.getWorld() == activeWorld) player.sendMessage(message);
        }
    }

    /** Returns true when this world belongs to Boss Rush, even if the claimant is not eligible. */
    public boolean broadcastEncounterMessage(Player claimant, Component message) {
        if (claimant == null || activeWorld == null || claimant.getWorld() != activeWorld) return false;
        RouletteTeam team = playerTeams.get(claimant.getUniqueId());
        if (participants.contains(claimant.getUniqueId()) && team != null) {
            sendTeamMessage(team, message);
        }
        return true;
    }

    private void sendTeamMessage(RouletteTeam team, Component message) {
        if (team == null || message == null) return;
        for (UUID playerId : participants) {
            if (playerTeams.get(playerId) != team) continue;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) player.sendMessage(message);
        }
        for (Map.Entry<UUID, RouletteTeam> entry : externalSpectators.entrySet()) {
            if (entry.getValue() != team) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && player.getWorld() == activeWorld) player.sendMessage(message);
        }
    }

    private void logPreparationProfile() {
        long totalNanos = System.nanoTime() - preparationStartedNanos;
        plugin.getLogger().info("[BossRushPerf] teams=" + arenas.size()
            + " arena_source=validated_template_clone"
            + " total_ms=" + millis(totalNanos)
            + " markers_ms=" + millis(markerNanos)
            + " ready_chunks=" + readyChunks
            + " chunk_ready_ms=" + millis(chunkReadyNanos)
            + " players_ready_ms=" + millis(playerReadyNanos)
            + " boss_setup_ms=" + millis(bossSetupNanos)
            + " model_setup_ms=" + millis(modelSetupNanos)
            + " barrels_ms=" + millis(barrelSetupNanos));
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }

    private void ensureReadyChunk(Location location, Set<Long> checkedChunks) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        if (checkedChunks.add(key)) activeWorld.getChunkAt(chunkX, chunkZ);
    }

    private static int[] arenaCenter(RouletteTeam team) {
        return switch (team) {
            case RED -> new int[] {-TEAM_ARENA_OFFSET, -TEAM_ARENA_OFFSET};
            case BLUE -> new int[] {TEAM_ARENA_OFFSET, -TEAM_ARENA_OFFSET};
            case GREEN -> new int[] {-TEAM_ARENA_OFFSET, TEAM_ARENA_OFFSET};
            case YELLOW -> new int[] {TEAM_ARENA_OFFSET, TEAM_ARENA_OFFSET};
        };
    }

    private void failPreparation(String reason, Exception ex) {
        if (ex == null) {
            plugin.getLogger().warning("[BossRush] " + reason);
        } else {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRush] " + reason, ex);
        }
        broadcastQueue(ChatColor.RED + "Boss Rush preparation failed. The arena was cleaned up.");
        state = State.ENDING;
        cleanupWorld();
        queuedPlayers.clear();
        ManagedMinigameRound.Request request = managedRound;
        resetState();
        if (request != null) {
            dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, reason));
        }
    }

    private Location arenaMobSpawn(ArenaInstance arena, int localX, int localZ) {
        return arenaLocationAtY(arena, localX, MOB_SPAWN_Y, localZ);
    }

    private Location arenaLocationAtY(ArenaInstance arena, int localX, int y, int localZ) {
        return new Location(activeWorld, arena.centerX() + localX + 0.5D, y,
            arena.centerZ() + localZ + 0.5D);
    }

    private boolean ownsOtherGame(UUID playerId) {
        return plugin.queueService().isPlayerQueued(playerId)
            || plugin.queueService().isPlayerInHydraMatchOrSpectating(playerId)
            || plugin.spleefService().isInSpleef(playerId)
            || plugin.voidRouletteService().isInVoidRoulette(playerId)
            || plugin.bridgeService().isInBridge(playerId)
            || plugin.minigameRouletteService().isTournamentMember(playerId);
    }

    private void restoreAllPlayers() {
        List<UUID> restoring = new ArrayList<>(participants);
        restoring.addAll(externalSpectators.keySet());
        for (UUID playerId : restoring) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) restoreSpectatorPairVisibility(player);
        }
        for (UUID playerId : restoring) {
            restoreOnePlayer(playerId);
        }
    }

    private void restoreOnePlayer(UUID playerId) {
        spectatorViewingTeams.remove(playerId);
        if (externalSpectators.remove(playerId) != null) playerStartingSpawns.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            if (!alivePlayers.contains(playerId)) clearCustomSpectatorState(player);
            PlayerSnapshot snapshot = snapshots.get(playerId);
            if (snapshot != null && snapshot.restore(player)) {
                plugin.queueService().clearSidebarCache(playerId);
                plugin.queueService().refreshSidebar(player);
                return;
            }
            plugin.queueService().returnPlayerToHydraHub(player);
            plugin.queueService().clearSidebarCache(playerId);
            plugin.queueService().refreshSidebar(player);
        } else {
            PlayerSnapshot snapshot = snapshots.get(playerId);
            if (snapshot != null) pendingRestores.put(playerId, snapshot);
        }
    }

    private void cleanupWorld() {
        cancelMatchTask();
        cancelStartupTask();
        removeAllBosses();
        String worldName = activeWorldName;
        World world = activeWorld;
        if (world != null && plugin.zxdzeroEventsCoreService() != null) {
            plugin.zxdzeroEventsCoreService().clearPedestals(new Location(world, 0, PASTE_Y, 0), WORLD_BORDER_SIZE);
        }
        for (UUID id : new ArrayList<>(eventMobs)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        eventMobs.clear();
        healerTeams.clear();
        bossTeams.clear();
        activeWorld = null;
        activeWorldName = null;
        if (templateWorlds != null) {
            if (world != null) {
                for (Entity entity : new ArrayList<>(world.getEntities())) entity.remove();
            }
            templateWorlds.dispose();
            return;
        }
        if (worldName == null) {
            return;
        }
        if (world != null) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                entity.remove();
            }
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("[BossRush] Could not unload " + worldName + "; directory retained.");
                return;
            }
            plugin.getLogger().info("[BossRush] unload_confirmed world=" + worldName);
        }
        Path folder = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize().resolve(worldName).normalize();
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        if (!folder.startsWith(container) || folder.equals(container)) {
            plugin.getLogger().severe("[BossRush] Refusing unsafe world deletion: " + folder);
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    plugin.getLogger().warning("[BossRush] Failed deleting " + path + ": " + ex.getMessage());
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().warning("[BossRush] Failed cleaning world " + worldName + ": " + ex.getMessage());
        }
    }

    private void resetState() {
        preparationStartedNanos = 0L;
        markerNanos = chunkReadyNanos = playerReadyNanos = bossSetupNanos = modelSetupNanos = barrelSetupNanos = 0L;
        readyChunks = 0;
        participants.clear();
        alivePlayers.clear();
        externalSpectators.clear();
        spectatorViewingTeams.clear();
        spectatorInHub.clear();
        sidebarSignatures.clear();
        damageActionBarHeldUntil.clear();
        snapshots.clear();
        playerTeams.clear();
        arenas.clear();
        playerSpawnSlots.clear();
        playerStartingSpawns.clear();
        teamDamage.clear();
        frozenSpawnLocations.clear();
        bossTeams.clear();
        healerTeams.clear();
        elapsedTicks = 0;
        pedestalCount = 0;
        managedRound = null;
        state = State.IDLE;
    }

    private void dispatchManagedResult(ManagedMinigameRound.Request request,
                                       ManagedMinigameRound.Result result) {
        if (request == null || managedCallbackSent) {
            return;
        }
        managedCallbackSent = true;
        Bukkit.getScheduler().runTask(plugin, () -> request.callback().accept(result));
    }

    private void prepareSpectator(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || activeWorld == null) {
            return;
        }
        spectatorViewingTeams.remove(player.getUniqueId());
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setFallDistance(0.0F);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
            Integer.MAX_VALUE, 0, true, false, false));
        plugin.playerSettingsService().applyHydraNightVision(player);
        Location spawn = playerStartingSpawn(player.getUniqueId());
        if (spawn != null) player.teleport(spawn);
        player.getInventory().setItem(0, spectatorItem(Material.COMPASS,
            ChatColor.AQUA + "Teleport Compass", SPECTATOR_ACTION_TELEPORT));
        player.getInventory().setItem(8, spectatorItem(Material.PAPER,
            ChatColor.RED + "Return to Hub", SPECTATOR_ACTION_HUB));
        player.updateInventory();
        updateBossRushSidebar(player);
        refreshSpectatorVisibilityPreference();
    }

    public boolean isLobbyTimeContext(UUID playerId) {
        return queuedPlayers.contains(playerId) || (participants.contains(playerId)
            && (state == State.PREPARING || state == State.PLAYER_FREEZE));
    }

    public void refreshSpectatorVisibilityPreference() {
        if (activeWorld == null) return;
        List<UUID> viewers = new ArrayList<>(participants);
        viewers.addAll(externalSpectators.keySet());
        for (UUID viewerId : viewers) {
            if (alivePlayers.contains(viewerId)) continue;
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() != activeWorld) continue;
            for (UUID targetId : participants) {
                RouletteTeam viewerTeam = spectatorAudienceTeam(viewerId);
                if (alivePlayers.contains(targetId) || viewerId.equals(targetId)
                    || viewerTeam != playerTeams.get(targetId)) continue;
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline() && target.getWorld() == activeWorld) {
                    PlayerSettingsService.syncSpectatorPairOrDefault(plugin, viewer, target);
                }
            }
        }
    }

    private void restoreSpectatorPairVisibility(Player player) {
        List<UUID> viewers = new ArrayList<>(participants);
        viewers.addAll(externalSpectators.keySet());
        for (UUID otherId : viewers) {
            Player other = Bukkit.getPlayer(otherId);
            if (other != null && other.isOnline() && !other.equals(player)) {
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            }
        }
    }

    private void clearCustomSpectatorState(Player player) {
        player.closeInventory();
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        plugin.playerSettingsService().clearHydraNightVision(player);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private boolean isBossRushSpectator(Player player) {
        return player != null && (participants.contains(player.getUniqueId())
            || externalSpectators.containsKey(player.getUniqueId()))
            && !alivePlayers.contains(player.getUniqueId())
            && !spectatorInHub.contains(player.getUniqueId())
            && player.getWorld() == activeWorld && player.getGameMode() == GameMode.ADVENTURE;
    }

    private boolean canSpectateTarget(Player spectator, Player target) {
        if (!isBossRushSpectator(spectator) || target == null || !target.isOnline()
            || target.getWorld() != activeWorld || !alivePlayers.contains(target.getUniqueId())) return false;
        RouletteTeam team = playerTeams.getOrDefault(spectator.getUniqueId(),
            externalSpectators.get(spectator.getUniqueId()));
        ArenaInstance arena = arenas.get(team);
        return team != null && arena != null
            && (arenaFinished(arena) || team == playerTeams.get(target.getUniqueId()));
    }

    private ItemStack spectatorItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (material == Material.PAPER && SPECTATOR_ACTION_HUB.equals(action)) {
            meta.setItemModel(NamespacedKey.fromString("hydra:backspace"));
        }
        meta.getPersistentDataContainer().set(spectatorActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String spectatorAction(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
            : item.getItemMeta().getPersistentDataContainer().get(spectatorActionKey, PersistentDataType.STRING);
    }

    private void openSpectatorTeleportMenu(Player spectator) {
        if (!isBossRushSpectator(spectator)) return;
        Inventory inventory = Bukkit.createInventory(null, 54, SPECTATOR_MENU_TITLE);
        int slot = 0;
        for (UUID playerId : alivePlayers) {
            Player target = Bukkit.getPlayer(playerId);
            if (!canSpectateTarget(spectator, target) || slot >= 45) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) continue;
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GREEN + target.getName());
            meta.getPersistentDataContainer().set(spectatorActionKey, PersistentDataType.STRING,
                SPECTATOR_ACTION_TARGET_PREFIX + playerId);
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }
        inventory.setItem(49, spectatorItem(Material.BARRIER, ChatColor.RED + "Close", SPECTATOR_ACTION_CLOSE));
        spectator.openInventory(inventory);
    }

    private void teleportSpectatorToTarget(Player spectator, String rawTargetId) {
        if (!isBossRushSpectator(spectator)) return;
        try {
            Player target = Bukkit.getPlayer(UUID.fromString(rawTargetId));
            if (!canSpectateTarget(spectator, target)) {
                spectator.sendMessage(ChatColor.RED + "You may only spectate a living teammate until the boss is defeated.");
                openSpectatorTeleportMenu(spectator);
                return;
            }
            spectator.closeInventory();
            UUID targetId = target.getUniqueId();
            plugin.transitionTravel(spectator, () -> {
                Player current = Bukkit.getPlayer(targetId);
                if (isBossRushSpectator(spectator) && canSpectateTarget(spectator, current)) {
                    if (spectator.teleport(current.getLocation())) {
                        spectatorViewingTeams.put(spectator.getUniqueId(), playerTeams.get(targetId));
                        if (plugin.hydraPlusService() != null) plugin.hydraPlusService().applyToViewer(spectator);
                        refreshSpectatorVisibilityPreference();
                    }
                }
            });
        } catch (IllegalArgumentException exception) {
            spectator.sendMessage(ChatColor.RED + "Invalid spectator target.");
        }
    }

    private void putAllParticipantsInSpectator() {
        alivePlayers.clear();
        for (UUID playerId : participants) {
            if (spectatorInHub.contains(playerId)) continue;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) prepareSpectator(player);
        }
    }

    private void cancelMatchTask() {
        if (matchTask != null) {
            matchTask.cancel();
            matchTask = null;
        }
    }

    private void cancelStartupTask() {
        if (startupTask != null) {
            startupTask.cancel();
            startupTask = null;
        }
    }

    private void stop(Player admin) {
        if (state == State.IDLE && queuedPlayers.isEmpty()) {
            admin.sendMessage(ChatColor.YELLOW + "Boss Rush is already idle.");
            return;
        }
        state = State.ENDING;
        queuedPlayers.clear();
        removeAllBosses();
        restoreAllPlayers();
        cleanupWorld();
        resetState();
        admin.sendMessage(ChatColor.GREEN + "Boss Rush stopped and cleaned up.");
    }

    private void sendStatus(Player player) {
        player.sendMessage(ChatColor.GOLD + "Boss Rush Status");
        player.sendMessage(ChatColor.GRAY + "State: " + ChatColor.WHITE + state);
        player.sendMessage(ChatColor.GRAY + "Queued: " + ChatColor.WHITE + queuedPlayers.size());
        player.sendMessage(ChatColor.GRAY + "Alive: " + ChatColor.WHITE + alivePlayers.size() + "/" + participants.size());
        player.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + (activeWorldName == null ? "none" : activeWorldName));
        for (RouletteTeam team : RouletteTeam.values()) {
            player.sendMessage(team.color() + team.displayName() + ChatColor.GRAY + " damage: " + ChatColor.WHITE + roundedDamage(team));
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /admin bossrush <join|forcejoin|leave|forcestart|transition|status|stop>");
    }

    private void broadcastQueue(String message) {
        for (UUID playerId : queuedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void broadcastPlayers(String message) {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
        for (UUID playerId : externalSpectators.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && player.getWorld() == activeWorld) {
                player.sendMessage(message);
            }
        }
    }

    private static void broadcast(List<Player> players, String message) {
        players.forEach(player -> player.sendMessage(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (state != State.ACTIVE || !participants.contains(player.getUniqueId())) {
            return;
        }
        Component deathMessage = event.deathMessage();
        event.deathMessage(null);
        sendTeamMessage(playerTeams.get(player.getUniqueId()), deathMessage);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        eliminate(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = playerStartingSpawn(event.getPlayer().getUniqueId());
        if (participants.contains(event.getPlayer().getUniqueId()) && spawn != null) {
            event.setRespawnLocation(spawn);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                if (participants.contains(player.getUniqueId())
                    && !spectatorInHub.contains(player.getUniqueId())) {
                    prepareSpectator(player);
                    if (state == State.ACTIVE && !alivePlayers.contains(player.getUniqueId())) {
                        player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "ELIMINATED",
                            ChatColor.GRAY + "You are now spectating Boss Rush.", 5, 40, 10);
                        HydraSounds.playSpectatorEnter(player);
                    }
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        spectatorViewingTeams.remove(playerId);
        damageActionBarHeldUntil.remove(playerId);
        if (externalSpectators.remove(playerId) != null) {
            PlayerSnapshot snapshot = snapshots.remove(playerId);
            if (snapshot != null) pendingRestores.put(playerId, snapshot);
            playerStartingSpawns.remove(playerId);
            sidebarSignatures.remove(playerId);
        }
        if (state == State.IDLE) {
            queuedPlayers.remove(playerId);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (participants.contains(playerId) && state == State.ACTIVE) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (participants.contains(playerId) && state == State.ACTIVE) {
                    rejoin(event.getPlayer());
                }
            });
            return;
        }
        PlayerSnapshot snapshot = pendingRestores.remove(event.getPlayer().getUniqueId());
        if (snapshot != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                clearCustomSpectatorState(event.getPlayer());
                snapshot.restore(event.getPlayer());
                plugin.queueService().clearSidebarCache(event.getPlayer().getUniqueId());
                plugin.queueService().refreshSidebar(event.getPlayer());
            });
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (externalSpectators.containsKey(playerId) && event.getPlayer().getWorld() != activeWorld) {
            spectatorViewingTeams.remove(playerId);
            externalSpectators.remove(playerId);
            PlayerSnapshot snapshot = snapshots.remove(playerId);
            playerStartingSpawns.remove(playerId);
            sidebarSignatures.remove(playerId);
            clearCustomSpectatorState(event.getPlayer());
            if (snapshot != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (event.getPlayer().isOnline()) snapshot.restore(event.getPlayer());
                    plugin.queueService().refreshSidebar(event.getPlayer());
                });
            } else plugin.queueService().refreshSidebar(event.getPlayer());
        }
        if (state == State.ACTIVE && participants.contains(event.getPlayer().getUniqueId())
            && activeWorld != null && event.getPlayer().getWorld() != activeWorld) {
            eliminate(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpectatorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isBossRushSpectator(player)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        String action = spectatorAction(event.getItem());
        if (SPECTATOR_ACTION_TELEPORT.equals(action)) openSpectatorTeleportMenu(player);
        else if (SPECTATOR_ACTION_HUB.equals(action)) returnTournamentSpectatorToHub(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInteractEntity(PlayerInteractEntityEvent event) {
        if (isBossRushSpectator(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (SPECTATOR_MENU_TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
            if (!isBossRushSpectator(player)) return;
            String action = spectatorAction(event.getCurrentItem());
            if (SPECTATOR_ACTION_CLOSE.equals(action)) player.closeInventory();
            else if (action != null && action.startsWith(SPECTATOR_ACTION_TARGET_PREFIX)) {
                teleportSpectatorToTarget(player, action.substring(SPECTATOR_ACTION_TARGET_PREFIX.length()));
            }
        } else if (isBossRushSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isBossRushSpectator(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorDrop(PlayerDropItemEvent event) {
        if (isBossRushSpectator(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorAttack(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (isBossRushSpectator(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isBossRushSpectator(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!participants.contains(event.getPlayer().getUniqueId())
            && !externalSpectators.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        if (spectatorInHub.contains(event.getPlayer().getUniqueId())) return;
        if (state == State.ACTIVE) {
            Location to = event.getTo();
            UUID playerId = event.getPlayer().getUniqueId();
            RouletteTeam team = playerTeams.getOrDefault(playerId, externalSpectators.get(playerId));
            ArenaInstance arena = arenas.get(team);
            boolean contain = externalSpectators.containsKey(playerId)
                || alivePlayers.contains(playerId) || !arenaFinished(arena);
            if (contain && to != null && arena != null && !arena.bounds().containsXZ(to.getBlockX(), to.getBlockZ())) {
                Location destination = playerStartingSpawn(event.getPlayer().getUniqueId());
                if (destination != null) event.setTo(destination);
                event.getPlayer().sendMessage(ChatColor.RED + "Your team must remain inside its Boss Rush arena.");
            }
            return;
        }
        if (state != State.PLAYER_FREEZE) return;
        Location frozen = frozenSpawnLocations.get(event.getPlayer().getUniqueId());
        Location to = event.getTo();
        if (frozen == null || to == null || to.getWorld() != frozen.getWorld()) {
            return;
        }
        if (to.distanceSquared(frozen) <= 0.0001D) {
            return;
        }
        Location locked = frozen.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == activeWorld) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() == activeWorld) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == activeWorld) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation().getWorld() == activeWorld
            && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    private enum State {
        IDLE,
        PREPARING,
        PLAYER_FREEZE,
        ACTIVE,
        ENDING
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private boolean containsXZ(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static final class ArenaInstance {
        private final RouletteTeam team;
        private final int centerX;
        private final int centerZ;
        private final int pasteX;
        private final int pasteZ;
        private final Bounds bounds;
        private final List<Location> barrels = new ArrayList<>();
        private final Set<UUID> eventMobs = new HashSet<>();
        private final List<Location> playerSpawns = new ArrayList<>();
        private Location bossSpawn;
        private UUID bossId;
        private boolean completed;
        private boolean eliminated;

        private ArenaInstance(RouletteTeam team, int centerX, int centerZ, int pasteX, int pasteZ, Bounds bounds) {
            this.team = team;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.pasteX = pasteX;
            this.pasteZ = pasteZ;
            this.bounds = bounds;
        }

        private RouletteTeam team() { return team; }
        private int centerX() { return centerX; }
        private int centerZ() { return centerZ; }
        private int pasteX() { return pasteX; }
        private int pasteZ() { return pasteZ; }
        private Bounds bounds() { return bounds; }
        private List<Location> barrels() { return barrels; }
        private Set<UUID> eventMobs() { return eventMobs; }
        private List<Location> playerSpawns() { return playerSpawns; }
        private Location bossSpawn() { return bossSpawn; }
        private void bossSpawn(Location value) { bossSpawn = value; }
        private UUID bossId() { return bossId; }
        private void bossId(UUID value) { bossId = value; }
        private boolean completed() { return completed; }
        private void completed(boolean value) { completed = value; }
        private boolean eliminated() { return eliminated; }
        private void eliminated(boolean value) { eliminated = value; }
        private boolean resolve(CombatMasterService.Completion completion) {
            if (completed || eliminated) return false;
            if (completion == CombatMasterService.Completion.DEFEATED) completed = true;
            else eliminated = true;
            return true;
        }
    }

    private record PlayerSnapshot(Location location, GameMode gameMode, ItemStack[] contents, ItemStack[] armor,
                                  ItemStack offHand, double health, int food, float saturation, int level, float exp) {
        private static PlayerSnapshot capture(Player player) {
            return new PlayerSnapshot(player.getLocation().clone(), player.getGameMode(), clone(player.getInventory().getContents()),
                clone(player.getInventory().getArmorContents()), clone(player.getInventory().getItemInOffHand()),
                player.getHealth(), player.getFoodLevel(), player.getSaturation(), player.getLevel(), player.getExp());
        }

        private boolean restore(Player player) {
            if (location == null || location.getWorld() == null) {
                return false;
            }
            player.setGameMode(gameMode);
            player.setAllowFlight(gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
            player.setFlying(false);
            player.setInvulnerable(false);
            player.getActivePotionEffects().stream().map(PotionEffect::getType).forEach(player::removePotionEffect);
            player.teleport(location);
            player.getInventory().setContents(clone(contents));
            player.getInventory().setArmorContents(clone(armor));
            player.getInventory().setItemInOffHand(clone(offHand));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setLevel(level);
            player.setExp(exp);
            player.setHealth(Math.max(1.0D, Math.min(player.getMaxHealth(), health)));
            player.updateInventory();
            return true;
        }

        private static ItemStack[] clone(ItemStack[] source) {
            ItemStack[] result = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                result[i] = clone(source[i]);
            }
            return result;
        }

        private static ItemStack clone(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }

    private static final class EmptyArenaGenerator extends ChunkGenerator {
        private final int spawnY;

        private EmptyArenaGenerator(int spawnY) {
            this.spawnY = spawnY;
        }

        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public boolean shouldGenerateStructures(@NotNull WorldInfo info, @NotNull java.util.Random random, int chunkX, int chunkZ) { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return world == null ? null : new Location(world, 0.5D, spawnY, 0.5D);
        }
        @Override public boolean isParallelCapable() { return true; }
    }
}
