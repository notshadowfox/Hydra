package hgloot.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.util.HydraSounds;

public final class BridgeService implements Listener {
    private static final long KILL_CREDIT_MS = 8_000L;
    private static final String PREFIX = HydraIcons.EXCLAMATION + " " + ChatColor.GRAY;
    private static final String SIDEBAR_OBJECTIVE = "hydra_hud";
    private static final int MIN_PLAYERS = 4;
    private static final int FORCE_START_MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 4;
    private static final int WIN_SCORE = 3;
    private static final int QUEUE_COUNTDOWN_SECONDS = 20;
    private static final int START_FREEZE_SECONDS = 5;
    private static final int PASTE_Y = 80;
    private static final int CASTLE_MAX_BUILD_Y = 101;
    private static final int OCEAN_MAX_BUILD_Y = 117;
    private static final int CASTLE_NO_BUILD_RADIUS = 10;
    private static final long ARROW_RECHARGE_TICKS = 20L * 5L;
    private static final long ARROW_FALLBACK_RECHARGE_TICKS = 20L * 10L;
    private static final int DEATHMATCH_DELAY_SECONDS = 15 * 60;
    private static final int DEATHMATCH_BORDER_CLOSE_SECONDS = 2 * 60;
    private static final double DEATHMATCH_FINAL_BORDER_SIZE = 16.0D;
    private static final long SCORE_GRACE_MS = 2500L;
    private static final String SPECTATOR_MENU_TITLE = ChatColor.DARK_AQUA + "Bridge Players";
    private static final String SPECTATOR_ACTION_TELEPORT = "teleport";
    private static final String SPECTATOR_ACTION_HUB = "hub";
    private static final String SPECTATOR_ACTION_CLOSE = "close";
    private static final String SPECTATOR_ACTION_TARGET_PREFIX = "target:";

    private final HGLootPlugin plugin;
    private KitEditorService kitEditorService;
    private final ViewerGlowService glowService;
    private final NamespacedKey spectatorActionKey;
    private final LinkedHashSet<UUID> queuedPlayers = new LinkedHashSet<>();
    private final Map<UUID, BridgeTeam> playerTeams = new HashMap<>();
    private final EnumMap<BridgeTeam, LinkedHashSet<UUID>> teamPlayers = new EnumMap<>(BridgeTeam.class);
    private final EnumMap<BridgeTeam, Integer> scoresAgainst = new EnumMap<>(BridgeTeam.class);
    private final EnumMap<BridgeTeam, Location> teamSpawns = new EnumMap<>(BridgeTeam.class);
    private final Map<UUID, Location> playerSpawns = new HashMap<>();
    private final EnumMap<BridgeTeam, GoalCluster> teamGoals = new EnumMap<>(BridgeTeam.class);
    private final Set<BridgeTeam> participatingTeams = EnumSet.noneOf(BridgeTeam.class);
    private final Set<BridgeTeam> eliminatedTeams = EnumSet.noneOf(BridgeTeam.class);
    private final List<BridgeTeam> eliminationOrder = new ArrayList<>();
    private final Map<UUID, RecentAttacker> recentAttackers = new HashMap<>();
    private final EnumMap<BridgeTeam, Integer> managedTeamKills = new EnumMap<>(BridgeTeam.class);
    private final Set<BlockPos> placedBlocks = new HashSet<>();
    private final Set<BlockPos> protectedMapBlocks = new HashSet<>();
    private final Set<UUID> scoringLocked = new HashSet<>();
    private final Set<UUID> respawningPlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> externalSpectators = new HashSet<>();
    private final Set<UUID> tournamentHubSpectators = new HashSet<>();
    private final Map<UUID, String> sidebarSignatures = new HashMap<>();
    private final Map<UUID, BukkitTask> arrowRechargeTasks = new HashMap<>();
    private final Map<UUID, UUID> firedArrowShooters = new HashMap<>();
    private final Map<UUID, BukkitTask> disconnectExpiryTasks = new HashMap<>();
    private final Set<UUID> disconnectedPlayers = new HashSet<>();
    private final Set<UUID> disconnectedRoundSpectators = new HashSet<>();
    private final List<BridgeMap> maps = List.of(
        new BridgeMap("bridge", "Bridge", "Structure nbt/minigames/bridge/Bridge.nbt"),
        new BridgeMap("bridge_ocean", "Bridge Ocean", "Structure nbt/minigames/bridge/Bridge ocean.nbt"),
        new BridgeMap("outer_end", "Outer End", "Structure nbt/minigames/bridge/outerendbridge.nbt")
    );

    private BukkitTask queueCountdownTask;
    private BukkitTask startFreezeTask;
    private BukkitTask sidebarTask;
    private BukkitTask glowTask;
    private BukkitTask deathmatchCountdownTask;
    private BukkitTask roundResetTask;
    private World activeWorld;
    private String activeWorldName;
    private String activeMatchTeamId;
    private BridgeMap activeMap;
    private Bounds bounds;
    private boolean matchStarting;
    private boolean matchActive;
    private boolean startFreezeActive;
    private boolean scoreResetActive;
    private boolean deathmatchActive;
    private boolean autoWinEnabled;
    private boolean autoCleanupEnabled = true;
    private String selectedMapKey = "random";
    private int countdownSeconds;
    private int nextWorldNumber = 1;
    private int deathmatchSecondsRemaining;
    private double initialWorldBorderSize;
    private ManagedMinigameRound.Request managedRound;
    private boolean managedCallbackSent;

    public BridgeService(HGLootPlugin plugin) {
        this.plugin = plugin;
        this.glowService = new ViewerGlowService(plugin);
        this.spectatorActionKey = new NamespacedKey(plugin, "bridge_spectator_action");
        for (BridgeTeam team : BridgeTeam.values()) {
            scoresAgainst.put(team, 0);
        }
    }

    public void setKitEditorService(KitEditorService kitEditorService) {
        this.kitEditorService = kitEditorService;
    }

    public void start() {
        ensureBundledMaps();
        autoCleanupEnabled = plugin.getConfig().getBoolean("bridge.auto_cleanup", true);
        String configuredMap = normalizeMapKey(plugin.getConfig().getString("bridge.map", "random"));
        selectedMapKey = configuredMap == null ? "random" : configuredMap;
        sidebarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSidebars, 20L, 20L);
        glowTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPrivateTeamGlow, 10L, 10L);
    }

    public void shutdown() {
        if (sidebarTask != null) {
            sidebarTask.cancel();
            sidebarTask = null;
        }
        if (glowTask != null) {
            glowTask.cancel();
            glowTask = null;
        }
        stopMatch(Bukkit.getConsoleSender());
    }

    public boolean startManagedRound(ManagedMinigameRound.Request request) {
        if (request == null || request.game() != ManagedMinigameRound.Game.BRIDGE
            || matchActive || matchStarting || managedRound != null) {
            return false;
        }
        List<UUID> participants = request.roster().stream()
            .filter(id -> request.teams().containsKey(id))
            .filter(id -> {
                Player player = Bukkit.getPlayer(id);
                return player != null && player.isOnline();
            })
            .toList();
        if (participants.isEmpty()) {
            return false;
        }
        cancelQueueCountdown();
        managedRound = request;
        managedCallbackSent = false;
        boolean started = startMatch(participants, true);
        if (!started) {
            managedRound = null;
            managedCallbackSent = false;
        }
        return started;
    }

    public void stopManagedRound(String reason) {
        if (managedRound == null) {
            return;
        }
        ManagedMinigameRound.Request request = managedRound;
        stopMatchInternal();
        dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, reason));
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!MinigameForceJoin.requireAdmin(sender)) return true;
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "join" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    join(player);
                }
                yield true;
            }
            case "leave" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    leaveToHub(player);
                }
                yield true;
            }
            case "status" -> {
                sendStatus(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /bridge <join|leave|status>");
                yield true;
            }
        };
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        String sub = args.length <= 1 ? "status" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> sendStatus(sender);
            case "forcestart" -> forceStart(sender);
            case "forcejoin" -> MinigameForceJoin.execute(sender, args, 2, "bridge", this::join,
                target -> queuedPlayers.contains(target.getUniqueId()));
            case "stop", "forceend" -> stopMatch(sender);
            case "autowin" -> handleAutoWin(sender, args.length >= 3 ? args[2] : "status");
            case "cleanup" -> handleCleanupAdmin(sender, args);
            case "map" -> handleMapAdmin(sender, args.length >= 3 ? args[2] : "status");
            default -> sender.sendMessage(ChatColor.RED + "Usage: /admin minigames bridge <status|forcejoin|forcestart|stop|forceend|autowin|cleanup|map>");
        }
        return true;
    }

    public List<String> completeCommand(String[] args) {
        if (args.length == 1) {
            return completeFrom(args[0], List.of("join", "leave", "status"));
        }
        return List.of();
    }

    public List<String> completeAdmin(String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("forcejoin")) {
            return MinigameForceJoin.complete(args[2]);
        }
        if (args.length <= 1) {
            return List.of();
        }
        if (args.length == 2) {
            return completeFrom(args[1], List.of("status", "forcejoin", "forcestart", "stop", "forceend", "autowin", "cleanup", "map"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("autowin")) {
            return completeFrom(args[2], List.of("on", "off", "toggle", "status"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("cleanup")) {
            return completeFrom(args[2], List.of("status", "purge", "on", "off"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("map")) {
            return completeFrom(args[2], bridgeMapKeys());
        }
        return List.of();
    }

    public boolean isInBridge(UUID playerId) {
        return playerId != null
            && (queuedPlayers.contains(playerId) || playerTeams.containsKey(playerId) || spectators.contains(playerId)
                || disconnectedRoundSpectators.contains(playerId));
    }

    public boolean isQueuedForSpectatorCommand(UUID playerId) {
        return playerId != null && queuedPlayers.contains(playerId);
    }

    public boolean isInActiveBridgeMatch(UUID playerId) {
        return playerId != null && matchActive
            && (playerTeams.containsKey(playerId) || spectators.contains(playerId)
                || disconnectedRoundSpectators.contains(playerId));
    }

    public boolean isBridgeAlive(UUID playerId) {
        return playerId != null && matchActive && playerTeams.containsKey(playerId);
    }

    public boolean rejoin(Player player) {
        if (player == null || !player.isOnline() || !matchActive) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        boolean activeReturn = disconnectedPlayers.remove(playerId);
        boolean spectatorReturn = disconnectedRoundSpectators.remove(playerId);
        if (!activeReturn && !spectatorReturn) {
            return false;
        }
        BukkitTask expiry = disconnectExpiryTasks.remove(playerId);
        if (expiry != null) {
            expiry.cancel();
        }
        if (spectatorReturn || !playerTeams.containsKey(playerId)) {
            spectators.add(playerId);
            Location destination = spectatorReturnLocation(playerId);
            if (destination == null && activeWorld != null) destination = activeWorld.getSpawnLocation();
            if (destination != null) player.teleport(destination);
            setSpectatorState(player);
            updateMatchSidebar(player);
            syncSpectatorVisibility();
            player.sendMessage(PREFIX + ChatColor.YELLOW
                + "Your Bridge life expired. You rejoined as spectator for this round.");
            return true;
        }
        BridgeTeam team = assignedMatchTeam(playerId);
        Location spawn = playerSpawns.get(playerId);
        plugin.menuService().preparePlayerForMatch(player);
        preparePlayer(player, team == null ? BridgeTeam.RED : team);
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Rejoined your Bridge match as "
            + (team == null ? ChatColor.WHITE + "a player" : team.chatColor + team.displayName) + ChatColor.GRAY + ".");
        updateMatchSidebar(player);
        syncSpectatorVisibility();
        refreshPrivateTeamGlow();
        return true;
    }

    public boolean returnTournamentSpectatorToHub(Player player) {
        if (player == null || managedRound == null || !spectators.contains(player.getUniqueId())) return false;
        return plugin.transitionTravel(player, () -> {
            if (managedRound == null || !spectators.remove(player.getUniqueId())) return;
            tournamentHubSpectators.add(player.getUniqueId());
            cleanupPlayerForHub(player);
            syncSpectatorVisibility();
        });
    }

    public boolean forfeitTournamentPlayerToHub(Player player) {
        return player != null && managedRound != null && isBridgeAlive(player.getUniqueId())
            && player.getWorld() == activeWorld && leaveToHub(player);
    }

    public boolean rejoinTournamentSpectator(Player player) {
        if (player == null || !player.isOnline() || !matchActive
            || !tournamentHubSpectators.contains(player.getUniqueId())) return false;
        return plugin.transitionTravel(player, () -> {
            if (!matchActive || !tournamentHubSpectators.remove(player.getUniqueId())) return;
            spectators.add(player.getUniqueId());
            Location destination = spectatorReturnLocation(player.getUniqueId());
            if (destination == null && activeWorld != null) destination = activeWorld.getSpawnLocation();
            if (destination != null) player.teleport(destination);
            setSpectatorState(player);
            updateMatchSidebar(player);
            syncSpectatorVisibility();
            player.sendMessage(PREFIX + ChatColor.GREEN + "Rejoined Bridge as spectator.");
        });
    }

    public String playerAudienceKey(Player player) {
        if (player == null || !matchActive || activeWorldName == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        if (!playerTeams.containsKey(playerId) && !spectators.contains(playerId)) {
            return null;
        }
        return "bridge:" + activeWorldName;
    }

    public boolean isAudienceSpectator(UUID playerId) {
        return playerId != null && spectators.contains(playerId);
    }

    public int tabActivityCount() {
        return queuedPlayers.size() + playerTeams.size() + spectators.size();
    }

    public int queuedCount() {
        return queuedPlayers.size();
    }

    public int maxPlayersAllowed() {
        return MAX_PLAYERS;
    }

    public int minPlayersRequired() {
        return MIN_PLAYERS;
    }

    public boolean isMapSelected(String key) {
        String normalized = normalizeMapKey(key);
        return normalized != null && normalized.equals(selectedMapKey);
    }

    public String statusLabel() {
        if (matchActive) {
            return "In Game";
        }
        if (matchStarting) {
            return "Starting";
        }
        return "Waiting";
    }

    public boolean spectateTargetMatch(Player spectator, Player target) {
        return spectateTargetMatch(spectator, target, spectatableMatchId(target));
    }

    public String spectatableMatchId(Player target) {
        if (target == null || !target.isOnline() || target.getWorld() == null) return null;
        UUID id = target.getUniqueId();
        return matchActive && activeWorldName != null && target.getWorld() == activeWorld
            && (playerTeams.containsKey(id) || spectators.contains(id)) ? "bridge:" + activeWorldName : null;
    }

    public boolean spectateTargetMatch(Player spectator, Player target, String expectedMatchId) {
        if (spectator == null || target == null || !spectator.isOnline() || !target.isOnline()) {
            return false;
        }
        if (expectedMatchId == null || !expectedMatchId.equals(spectatableMatchId(target))) return false;
        if (!matchActive || activeWorldName == null) {
            return false;
        }
        UUID spectatorId = spectator.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (!playerTeams.containsKey(targetId) && !spectators.contains(targetId)) {
            return false;
        }
        if (playerTeams.containsKey(spectatorId) || spectators.contains(spectatorId)) {
            spectator.sendMessage(ChatColor.RED + "You are already in that Bridge match.");
            return true;
        }
        return plugin.transitionTravel(spectator, () -> {
            if (!matchActive || !target.isOnline() || (!playerTeams.containsKey(targetId) && !spectators.contains(targetId))
                || !expectedMatchId.equals(spectatableMatchId(target))
                || target.getWorld() != activeWorld || playerTeams.containsKey(spectatorId)
                || spectators.contains(spectatorId)) {
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
            spectator.sendMessage(PREFIX + ChatColor.GRAY + "Now spectating Bridge. Use " + ChatColor.WHITE + "/leave"
                + ChatColor.GRAY + " to return to the hub.");
            SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, true);
        });
    }

    public boolean leaveToHub(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        boolean queued = queuedPlayers.remove(playerId);
        boolean active = playerTeams.containsKey(playerId);
        boolean spectating = spectators.remove(playerId);
        externalSpectators.remove(playerId);
        sidebarSignatures.remove(playerId);
        if (!queued && !active && !spectating) {
            player.sendMessage(PREFIX + ChatColor.RED + "You are not in Bridge.");
            return false;
        }
        if (spectating) {
            cleanupPlayerForHub(player);
            return true;
        }
        if (active) {
            BridgeTeam team = playerTeams.remove(playerId);
            if (team != null) {
                LinkedHashSet<UUID> members = teamPlayers.get(team);
                if (members != null) {
                    members.remove(playerId);
                    if (members.isEmpty()) {
                        eliminateAbandonedTeam(team);
                    }
                }
            }
            broadcastMatch(player.getName() + " left Bridge.");
            checkWin();
        }
        cleanupPlayerForHub(player);
        if (queued) {
            broadcastQueue(player.getName() + " left Bridge.");
            if (queuedPlayers.size() < MIN_PLAYERS) {
                cancelQueueCountdown();
            }
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ChatColor.RED + "Only players can use this command.");
        return null;
    }

    private void join(Player player) {
        if (plugin.bossRushService() != null && plugin.bossRushService().ownsPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already committed to Boss Rush.");
            return;
        }
        if (plugin.minigameRouletteService() != null
            && plugin.minigameRouletteService().blockOtherModeEntry(player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (plugin.isModeLocked("bridge")) {
            player.sendMessage(PREFIX + ChatColor.RED + "Bridge is currently locked.");
            return;
        }
        if (plugin.queueService() != null && (plugin.queueService().isPlayerQueued(playerId) || plugin.queueService().isPlayerInHydraMatchOrSpectating(playerId))) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave your current queue or match before joining Bridge.");
            return;
        }
        if (plugin.spleefService() != null && plugin.spleefService().isInSpleef(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave Spleef before joining Bridge.");
            return;
        }
        if (plugin.voidRouletteService() != null && plugin.voidRouletteService().isInVoidRoulette(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave Void Roulette before joining Bridge.");
            return;
        }
        if (matchActive || matchStarting && !queuedPlayers.contains(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "A Bridge match is already starting or running.");
            return;
        }
        if (!queuedPlayers.add(playerId)) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "You are already queued for Bridge.");
            return;
        }
        if (!MinigameForceJoin.stageInGameHub(plugin, player)) {
            queuedPlayers.remove(playerId);
            return;
        }
        sidebarSignatures.remove(playerId);
        plugin.queueService().clearSidebarCache(playerId);
        broadcastQueueRaw(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.WHITE + player.getName()
            + ChatColor.GRAY + " Joined " + ChatColor.WHITE + "Bridge"
            + ChatColor.GRAY + " (" + ChatColor.WHITE + queuedPlayers.size() + "/" + MIN_PLAYERS
            + ChatColor.GRAY + ") Needed");
        updateQueuedSidebars();
        if (queuedPlayers.size() >= MIN_PLAYERS) {
            startQueueCountdown();
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Status: " + ChatColor.WHITE + statusLabel());
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Queue: " + ChatColor.WHITE + queuedPlayers.size() + "/" + MAX_PLAYERS);
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Map: " + ChatColor.WHITE + (activeMap == null ? "none" : activeMap.displayName()));
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Selected Map: " + ChatColor.WHITE + selectedMapDisplayName());
        sender.sendMessage(PREFIX + ChatColor.AQUA + "AutoWin: " + (autoWinEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Auto Cleanup: " + (autoCleanupEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        if (!teamGoals.isEmpty()) {
            for (BridgeTeam team : BridgeTeam.values()) {
                GoalCluster goal = teamGoals.get(team);
                Location spawn = teamSpawns.get(team);
                sender.sendMessage(PREFIX + team.chatColor + team.displayName + ChatColor.GRAY
                    + " goal=" + formatGoal(goal) + " spawn=" + formatLocation(spawn));
            }
        }
    }

    private void forceStart(CommandSender sender) {
        if (matchActive || matchStarting) {
            sender.sendMessage(PREFIX + ChatColor.RED + "A Bridge match is already running.");
            return;
        }
        if (queuedPlayers.isEmpty() && sender instanceof Player player) {
            join(player);
        }
        if (queuedPlayers.size() < FORCE_START_MIN_PLAYERS) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Need at least one queued player to force start Bridge.");
            return;
        }
        cancelQueueCountdown();
        startMatch(true);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Force started Bridge.");
    }

    private void handleAutoWin(CommandSender sender, String mode) {
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "on" -> autoWinEnabled = true;
            case "off" -> autoWinEnabled = false;
            case "toggle" -> autoWinEnabled = !autoWinEnabled;
            case "status" -> {
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /admin minigames bridge autowin <on|off|toggle|status>");
                return;
            }
        }
        sender.sendMessage(PREFIX + "AutoWin is " + (autoWinEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF")
            + ChatColor.GRAY + ". It only affects single-player forced test matches.");
    }

    private void handleMapAdmin(CommandSender sender, String key) {
        if (matchActive || matchStarting) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Bridge map cannot be changed while a match is active or starting.");
            return;
        }
        String normalized = normalizeMapKey(key);
        if (normalized == null) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin minigames bridge map <random|bridge|bridge_ocean>");
            return;
        }
        selectedMapKey = normalized;
        plugin.getConfig().set("bridge.map", selectedMapKey);
        plugin.saveConfig();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Bridge map set to " + ChatColor.WHITE + selectedMapDisplayName() + ChatColor.GREEN + ".");
    }

    private void stopMatch(CommandSender sender) {
        if (!matchActive && !matchStarting && queuedPlayers.isEmpty()) {
            autoWinEnabled = false;
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No active Bridge match found.");
            return;
        }
        if (managedRound != null) {
            ManagedMinigameRound.Request request = managedRound;
            stopMatchInternal();
            dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, "Bridge was force-stopped."));
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Bridge stopped.");
            return;
        }
        stopMatchInternal();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Bridge stopped.");
    }

    private void stopMatchInternal() {
        clearPrivateTeamGlow();
        clearRoundEntities();
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Bridge queue stopped.");
                queuedPlayers.remove(id);
                if (managedRound == null) plugin.queueService().returnPlayerToHydraHub(player);
                resetSidebarToHub(player);
            }
        }
        for (UUID id : new ArrayList<>(playerTeams.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                cleanupPlayerForHub(player);
            }
        }
        forSpectators(this::cleanupPlayerForHub);
        String worldName = activeWorldName;
        cancelTasks();
        cleanupState(false);
        if (worldName != null) {
            cleanupWorld(worldName);
        }
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
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Bridge auto-cleanup: "
                + (autoCleanupEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Stale folders: "
                + ChatColor.WHITE + staleFolders.size() + ChatColor.GRAY + " total, "
                + ChatColor.WHITE + purgeable + ChatColor.GRAY + " purgeable, "
                + ChatColor.WHITE + loadedStale + ChatColor.GRAY + " loaded/skipped.");
            return;
        }
        if (!action.equals("purge")) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin minigames bridge cleanup <status|purge|on|off>");
            return;
        }
        if (staleFolders.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No stale Bridge world folders to purge.");
            return;
        }
        int purged = 0;
        for (Path folder : staleFolders) {
            String worldName = folder.getFileName().toString();
            cleanupWorld(worldName);
            purged++;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Purged " + purged + " stale Bridge world folder(s).");
    }

    private void setAutoCleanup(CommandSender sender, boolean enabled) {
        if (autoCleanupEnabled == enabled) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Bridge auto-cleanup is already "
                + (enabled ? "ON" : "OFF") + ".");
            return;
        }
        autoCleanupEnabled = enabled;
        plugin.getConfig().set("bridge.auto_cleanup", enabled);
        plugin.saveConfig();
        sender.sendMessage(PREFIX + (enabled
            ? ChatColor.GREEN + "Bridge auto-cleanup ENABLED."
            : ChatColor.RED + "Bridge auto-cleanup DISABLED. Match worlds stay loaded for inspection."));
        String actor = sender instanceof Player actorPlayer ? actorPlayer.getName() : "console";
        plugin.getLogger().info("[Bridge] Auto-cleanup " + (enabled ? "ENABLED" : "DISABLED") + " by " + actor);
    }

    private List<Path> staleWorldFolders() {
        Path worldContainer = Bukkit.getWorldContainer().toPath();
        List<Path> folders = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.list(worldContainer)) {
            walk.filter(Files::isDirectory)
                .filter(path -> path.getFileName() != null)
                .filter(path -> path.getFileName().toString().startsWith("Bridge-World-"))
                .filter(path -> !Objects.equals(path.getFileName().toString(), activeWorldName))
                .forEach(folders::add);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Bridge] Failed listing world folder cleanup candidates: " + ex.getMessage());
        }
        return folders;
    }

    private void startQueueCountdown() {
        if (queueCountdownTask != null) {
            return;
        }
        matchStarting = true;
        countdownSeconds = QUEUE_COUNTDOWN_SECONDS;
        queueCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queuedPlayers.size() < MIN_PLAYERS) {
                broadcastQueue("Bridge countdown cancelled. Need " + MIN_PLAYERS + " players.");
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
                broadcastQueue("Bridge starts in " + countdownSeconds + "s.");
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
        List<UUID> participants = new ArrayList<>();
        Iterator<UUID> iterator = queuedPlayers.iterator();
        while (iterator.hasNext() && participants.size() < MAX_PLAYERS) {
            UUID id = iterator.next();
            iterator.remove();
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                participants.add(id);
            }
        }
        startMatch(participants, forced);
    }

    private boolean startMatch(List<UUID> participants, boolean forced) {
        int floor = managedRound != null ? 1 : (forced ? FORCE_START_MIN_PLAYERS : MIN_PLAYERS);
        if (participants.size() < floor) {
            matchStarting = false;
            updateQueuedSidebars();
            return false;
        }
        matchStarting = true;
        BridgeMap map = selectedMap();
        Path mapPath = plugin.getDataFolder().toPath().resolve(map.resourcePath()).normalize();
        if (!Files.isRegularFile(mapPath)) {
            broadcastParticipants(participants, ChatColor.RED + "Bridge map is not available: " + map.displayName());
            restoreParticipantsToHub(participants);
            matchStarting = false;
            return false;
        }
        World world = createMatchWorld();
        if (world == null) {
            broadcastParticipants(participants, ChatColor.RED + "Failed to create Bridge world.");
            restoreParticipantsToHub(participants);
            matchStarting = false;
            return false;
        }
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(mapPath.toFile());
        } catch (Exception ex) {
            plugin.getLogger().warning("[Bridge] Failed loading map " + mapPath + ": " + ex.getMessage());
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            matchStarting = false;
            return false;
        }
        if (structure == null || !placeStructureCentered(world, structure)) {
            plugin.getLogger().warning("[Bridge] Failed placing map " + map.displayName() + ".");
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            matchStarting = false;
            return false;
        }
        activeWorld = world;
        activeWorldName = world.getName();
        activeMap = map;
        scanProtectedMapBlocks(world);
        List<GoalCluster> goals = discoverGoalClusters(world);
        if (goals.size() < BridgeTeam.values().length) {
            broadcastParticipants(participants, ChatColor.RED + "Bridge map needs 4 end portal goal clusters. Found " + goals.size() + ".");
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            cleanupState(false);
            return false;
        }
        assignGoalsAndSpawns(goals);
        configureMatchBorder();
        participatingTeams.clear();
        eliminatedTeams.clear();
        eliminationOrder.clear();
        playerSpawns.clear();
        teamPlayers.clear();
        for (BridgeTeam team : BridgeTeam.values()) {
            scoresAgainst.put(team, 0);
        }
        int[] expectedArrivals = {0};
        int[] arrivals = {0};
        boolean[] begun = {false};
        Runnable beginAfterArrival = () -> {
            if (begun[0] || !matchStarting || activeWorld != world) return;
            begun[0] = true;
            matchActive = true;
            matchStarting = false;
            startStartFreezeCountdown();
            refreshPrivateTeamGlow();
            broadcastMatch("Bridge started on " + map.displayName() + ".");
        };
        for (int i = 0; i < participants.size(); i++) {
            UUID id = participants.get(i);
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            BridgeTeam team = managedRound == null
                ? BridgeTeam.values()[i]
                : bridgeTeam(managedRound.teams().get(id));
            if (team == null) {
                continue;
            }
            playerTeams.put(id, team);
            LinkedHashSet<UUID> members = teamPlayers.computeIfAbsent(team, ignored -> new LinkedHashSet<>());
            members.add(id);
            participatingTeams.add(team);
            if (plugin.queueService() != null) {
                plugin.queueService().resetMatchRewardStats(id);
            }
            Location spawn = memberSpawn(team, members.size() - 1);
            Location destination = spawn == null ? world.getSpawnLocation() : spawn;
            playerSpawns.put(id, destination.clone());
            if (!plugin.transitionTravel(player, () -> {
                if (!matchStarting || activeWorld != world || !player.isOnline()) return;
                plugin.menuService().preparePlayerForMatch(player);
                preparePlayer(player, team);
                player.teleport(destination);
                player.sendTitle(team.chatColor + "" + ChatColor.BOLD + team.displayName, ChatColor.GRAY + "Score in enemy portals.", 10, 45, 10);
                updateMatchSidebar(player);
                if (++arrivals[0] >= expectedArrivals[0]) beginAfterArrival.run();
            })) {
                plugin.getLogger().warning("[Bridge] Entry fade already active for " + player.getName());
                restoreParticipantsToHub(participants);
                cleanupWorld(world.getName());
                cleanupState(false);
                return false;
            }
            expectedArrivals[0]++;
        }
        if (!registerImmutableMatchTeams()) {
            plugin.getLogger().warning("[Bridge] Failed registering immutable match teams for " + activeWorldName + ".");
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            cleanupState(false);
            return false;
        }
        if (expectedArrivals[0] == 0) beginAfterArrival.run();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (matchStarting && activeWorld == world) beginAfterArrival.run();
        }, 20L);
        return true;
    }

    private BridgeTeam bridgeTeam(RouletteTeam team) {
        return team == null ? null : BridgeTeam.valueOf(team.name());
    }

    private RouletteTeam rouletteTeam(BridgeTeam team) {
        return RouletteTeam.valueOf(team.name());
    }

    private boolean registerImmutableMatchTeams() {
        if (activeWorldName == null || plugin.matchTeamService() == null) {
            return false;
        }
        activeMatchTeamId = "bridge:" + activeWorldName;
        List<MatchTeamService.TeamDefinition> definitions = new ArrayList<>();
        for (BridgeTeam team : participatingTeams) {
            Collection<UUID> members = teamPlayers.get(team);
            if (members != null && !members.isEmpty()) {
                definitions.add(new MatchTeamService.TeamDefinition(
                    team.name(), team.displayName, team.chatColor, members));
            }
        }
        return plugin.matchTeamService().registerMatch(activeMatchTeamId, definitions);
    }

    private BridgeTeam assignedMatchTeam(UUID playerId) {
        if (activeMatchTeamId != null && plugin.matchTeamService() != null) {
            MatchTeamService.MatchTeam team = plugin.matchTeamService().teamOf(activeMatchTeamId, playerId);
            if (team != null) {
                try {
                    return BridgeTeam.valueOf(team.id());
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("[Bridge] Unknown immutable team id: " + team.id());
                }
            }
        }
        return playerTeams.get(playerId);
    }

    private Location memberSpawn(BridgeTeam team, int memberIndex) {
        Location base = teamSpawns.get(team);
        if (base == null || memberIndex == 0) {
            return base == null ? null : base.clone();
        }
        double[][] offsets = {{0.6D, 0.0D}, {-0.6D, 0.0D}, {0.0D, 0.6D}};
        double[] offset = offsets[Math.min(memberIndex - 1, offsets.length - 1)];
        return base.clone().add(offset[0], 0.0D, offset[1]);
    }

    private void ensureBundledMaps() {
        for (BridgeMap map : maps) {
            try {
                Path target = plugin.getDataFolder().toPath().resolve(map.resourcePath()).normalize();
                boolean copyResource = !Files.isRegularFile(target);
                if (!copyResource) {
                    try (InputStream resource = plugin.getResource(map.resourcePath())) {
                        if (resource == null) {
                            plugin.getLogger().warning("[Bridge] Bundled map missing: " + map.resourcePath());
                            continue;
                        }
                        Path temp = Files.createTempFile("hydra-bridge-map-", ".nbt");
                        try {
                            Files.copy(resource, temp, StandardCopyOption.REPLACE_EXISTING);
                            copyResource = Files.mismatch(target, temp) != -1L;
                        } finally {
                            Files.deleteIfExists(temp);
                        }
                    }
                }
                if (copyResource) {
                    Files.createDirectories(target.getParent());
                    try (InputStream resource = plugin.getResource(map.resourcePath())) {
                        if (resource == null) {
                            plugin.getLogger().warning("[Bridge] Bundled map missing: " + map.resourcePath());
                            continue;
                        }
                        Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[Bridge] Bundled map missing: " + map.resourcePath());
            } catch (IOException ex) {
                plugin.getLogger().warning("[Bridge] Could not sync bundled map " + map.resourcePath() + ": " + ex.getMessage());
            }
        }
    }

    private void restoreParticipantsToHub(List<UUID> participants) {
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                cleanupPlayerForHub(player);
            }
        }
    }

    private World createMatchWorld() {
        String worldName = "Bridge-World-" + nextWorldNumber++;
        while (Bukkit.getWorld(worldName) != null || Files.exists(Bukkit.getWorldContainer().toPath().resolve(worldName))) {
            worldName = "Bridge-World-" + nextWorldNumber++;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new EmptyBridgeChunkGenerator(PASTE_Y + 8));
        creator.generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            return null;
        }
        world.setAutoSave(false);
        world.setPVP(true);
        world.setTime(6000L);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        return world;
    }

    private boolean placeStructureCentered(World world, Structure structure) {
        BlockVector size = structure.getSize();
        int pasteX = -Math.floorDiv(Math.max(1, size.getBlockX()), 2);
        int pasteZ = -Math.floorDiv(Math.max(1, size.getBlockZ()), 2);
        bounds = new Bounds(pasteX, PASTE_Y, pasteZ, pasteX + size.getBlockX() - 1, PASTE_Y + size.getBlockY() - 1, pasteZ + size.getBlockZ() - 1);
        try {
            structure.place(new Location(world, pasteX, PASTE_Y, pasteZ), true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
            world.setSpawnLocation(0, PASTE_Y + 8, 0);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("[Bridge] Structure placement failed: " + ex.getMessage());
            return false;
        }
    }

    private void scanProtectedMapBlocks(World world) {
        protectedMapBlocks.clear();
        if (bounds == null) {
            return;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) {
                        protectedMapBlocks.add(BlockPos.of(block));
                    }
                }
            }
        }
    }

    private List<GoalCluster> discoverGoalClusters(World world) {
        List<BlockPos> portals = new ArrayList<>();
        if (bounds == null) {
            return List.of();
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.END_PORTAL) {
                        portals.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        Set<BlockPos> unvisited = new HashSet<>(portals);
        List<GoalCluster> clusters = new ArrayList<>();
        while (!unvisited.isEmpty()) {
            BlockPos seed = unvisited.iterator().next();
            List<BlockPos> cluster = new ArrayList<>();
            List<BlockPos> queue = new ArrayList<>();
            queue.add(seed);
            unvisited.remove(seed);
            for (int i = 0; i < queue.size(); i++) {
                BlockPos current = queue.get(i);
                cluster.add(current);
                for (BlockPos other : new ArrayList<>(unvisited)) {
                    if (Math.abs(other.x - current.x) <= 1 && Math.abs(other.y - current.y) <= 1 && Math.abs(other.z - current.z) <= 1) {
                        unvisited.remove(other);
                        queue.add(other);
                    }
                }
            }
            clusters.add(GoalCluster.from(cluster));
        }
        clusters.sort(Comparator.comparingDouble(c -> Math.atan2(c.centerZ(), c.centerX())));
        return clusters;
    }

    private void assignGoalsAndSpawns(List<GoalCluster> goals) {
        teamGoals.clear();
        teamSpawns.clear();
        List<GoalCluster> selected = new ArrayList<>(goals.subList(0, Math.min(goals.size(), BridgeTeam.values().length)));
        if (activeMap != null && activeMap.key().equals("bridge")) {
            assignCastleGoalsAndSpawns(selected);
            return;
        }
        assignOceanGoalsAndSpawns(selected);
    }

    private void assignOceanGoalsAndSpawns(List<GoalCluster> goals) {
        EnumMap<BridgeTeam, GoalCluster> assigned = new EnumMap<>(BridgeTeam.class);
        assigned.put(BridgeTeam.RED, goals.stream().max(Comparator.comparingDouble(GoalCluster::centerZ)).orElseThrow());
        assigned.put(BridgeTeam.BLUE, goals.stream().max(Comparator.comparingDouble(GoalCluster::centerX)).orElseThrow());
        assigned.put(BridgeTeam.GREEN, goals.stream().min(Comparator.comparingDouble(GoalCluster::centerX)).orElseThrow());
        assigned.put(BridgeTeam.YELLOW, goals.stream().min(Comparator.comparingDouble(GoalCluster::centerZ)).orElseThrow());
        teamGoals.putAll(assigned);

        List<Block> markers = discoverOceanSpawnMarkers();
        for (BridgeTeam team : BridgeTeam.values()) {
            GoalCluster goal = assigned.get(team);
            Block marker = markers.stream()
                .min(Comparator.comparingDouble(block -> horizontalDistanceSquared(block.getLocation(), goal)))
                .orElse(null);
            if (marker != null) {
                teamSpawns.put(team, new Location(activeWorld, marker.getX() + 0.5D, marker.getY() + 1.0D,
                    marker.getZ() + 0.5D, team.spawnYaw, 0.0F));
                markers.remove(marker);
            } else {
                teamSpawns.put(team, centeredSpawn(findSpawnNearGoal(activeWorld, goal, team.spawnYaw), team.spawnYaw));
            }
        }
    }

    private List<Block> discoverOceanSpawnMarkers() {
        List<Block> markers = new ArrayList<>();
        if (activeWorld == null || bounds == null) {
            return markers;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = activeWorld.getBlockAt(x, y, z);
                    if (block.getType() == Material.CREAKING_HEART) {
                        markers.add(block);
                    }
                }
            }
        }
        return markers;
    }

    private void assignCastleGoalsAndSpawns(List<GoalCluster> goals) {
        List<GoalCluster> remaining = new ArrayList<>(goals);
        for (BridgeTeam team : BridgeTeam.values()) {
            Location spawn = castleSpawn(team);
            teamSpawns.put(team, spawn);
            GoalCluster nearest = remaining.stream()
                .min(Comparator.comparingDouble(goal -> horizontalDistanceSquared(spawn, goal)))
                .orElse(null);
            if (nearest != null) {
                teamGoals.put(team, nearest);
                remaining.remove(nearest);
            }
        }
    }

    private Location castleSpawn(BridgeTeam team) {
        return switch (team) {
            case RED -> new Location(activeWorld, 0.70D, 100.00D, 39.56D, team.spawnYaw, 0.0F);
            case BLUE -> new Location(activeWorld, 40.07D, 100.00D, 0.51D, team.spawnYaw, 0.0F);
            case GREEN -> new Location(activeWorld, -39.29D, 100.00D, 0.47D, team.spawnYaw, 0.0F);
            case YELLOW -> new Location(activeWorld, 0.52D, 100.00D, -39.40D, team.spawnYaw, 0.0F);
        };
    }

    private Location centeredSpawn(Location spawn, float yaw) {
        if (spawn == null) {
            return null;
        }
        return new Location(spawn.getWorld(), spawn.getBlockX() + 0.5D, spawn.getY(),
            spawn.getBlockZ() + 0.5D, yaw, 0.0F);
    }

    private double horizontalDistanceSquared(Location spawn, GoalCluster goal) {
        double dx = spawn.getX() - goal.centerX();
        double dz = spawn.getZ() - goal.centerZ();
        return (dx * dx) + (dz * dz);
    }

    private float yawTowardCenter(GoalCluster goal) {
        return (float) Math.toDegrees(Math.atan2(-goal.centerX(), goal.centerZ()));
    }

    private Location findSpawnNearGoal(World world, GoalCluster goal, float yaw) {
        Location fallback = new Location(world, goal.centerX() + 0.5D, goal.maxY() + 2.0D, goal.centerZ() + 0.5D, yaw, 0.0F);
        double bestDistance = Double.MAX_VALUE;
        Location best = null;
        int cx = (int) Math.round(goal.centerX());
        int cz = (int) Math.round(goal.centerZ());
        for (int radius = 1; radius <= 12; radius++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    Location safe = highestSafeStandingLocation(world, x, z, yaw);
                    if (safe == null) {
                        continue;
                    }
                    double distance = safe.distanceSquared(fallback);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = safe;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return fallback;
    }

    private Location highestSafeStandingLocation(World world, int x, int z, float yaw) {
        if (world == null || bounds == null) {
            return null;
        }
        for (int y = bounds.maxY() + 4; y >= bounds.minY(); y--) {
            Material floor = world.getBlockAt(x, y, z).getType();
            Material body = world.getBlockAt(x, y + 1, z).getType();
            Material head = world.getBlockAt(x, y + 2, z).getType();
            if (floor.isSolid() && !body.isSolid() && !head.isSolid() && floor != Material.END_PORTAL_FRAME) {
                return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D, yaw, 0.0F);
            }
        }
        return null;
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
                broadcastMatch("Go!");
                forPlayers(player -> {
                    player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "GO!", "", 0, 18, 8);
                    HydraSounds.playGo(player);
                });
                scheduleDeathmatch();
                return;
            }
            int shown = seconds[0];
            forPlayers(player -> {
                Location spawn = playerSpawns.get(player.getUniqueId());
                if (spawn != null && player.getLocation().distanceSquared(spawn) > 0.01D) {
                    player.teleport(spawn);
                }
                player.sendTitle(ChatColor.AQUA + "" + ChatColor.BOLD + shown, ChatColor.GRAY + "Bridge begins soon", 0, 18, 4);
                HydraSounds.playCountdownTick(player, shown);
            });
            seconds[0]--;
        }, 0L, 20L);
    }

    private void clearStartFreeze() {
        startFreezeActive = false;
        if (startFreezeTask != null) {
            startFreezeTask.cancel();
            startFreezeTask = null;
        }
    }

    private void configureMatchBorder() {
        if (activeWorld == null || bounds == null || activeMap == null) {
            return;
        }
        double centerX = deathmatchCenterX();
        double centerZ = deathmatchCenterZ();
        double halfSpan = Math.max(
            Math.max(Math.abs(bounds.minX() - centerX), Math.abs(bounds.maxX() - centerX)),
            Math.max(Math.abs(bounds.minZ() - centerZ), Math.abs(bounds.maxZ() - centerZ))
        );
        initialWorldBorderSize = Math.max(32.0D, (halfSpan + 3.0D) * 2.0D);
        WorldBorder border = activeWorld.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setWarningDistance(8);
        border.setWarningTime(20);
        border.setSize(initialWorldBorderSize);
    }

    private double deathmatchCenterX() {
        if (activeMap == null) {
            return 0.0D;
        }
        return switch (activeMap.key()) {
            case "bridge" -> 0.0D;
            case "bridge_ocean" -> 0.0D;
            default -> 0.0D;
        };
    }

    private double deathmatchCenterZ() {
        if (activeMap == null) {
            return 0.0D;
        }
        return switch (activeMap.key()) {
            case "bridge" -> 0.0D;
            case "bridge_ocean" -> 0.0D;
            default -> 0.0D;
        };
    }

    private void scheduleDeathmatch() {
        cancelDeathmatchCountdown();
        deathmatchSecondsRemaining = DEATHMATCH_DELAY_SECONDS;
        deathmatchCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || deathmatchActive) {
                cancelDeathmatchCountdown();
                return;
            }
            if (deathmatchSecondsRemaining <= 0) {
                startDeathmatch();
                return;
            }
            if (deathmatchSecondsRemaining == 60 || deathmatchSecondsRemaining == 30
                || deathmatchSecondsRemaining == 10 || deathmatchSecondsRemaining <= 5) {
                broadcastMatch(ChatColor.RED + "Deathmatch begins in " + ChatColor.WHITE
                    + deathmatchSecondsRemaining + ChatColor.RED + "s.");
            }
            deathmatchSecondsRemaining--;
        }, 20L, 20L);
    }

    private void startDeathmatch() {
        if (!matchActive || deathmatchActive || activeWorld == null) {
            return;
        }
        cancelDeathmatchCountdown();
        cancelRoundReset();
        deathmatchActive = true;
        deathmatchSecondsRemaining = 0;
        scoreResetActive = false;
        scoringLocked.clear();
        clearRoundEntities();
        clearAllArrowState();
        forPlayers(this::giveSingleBridgeArrow);
        deactivateGoals();

        WorldBorder border = activeWorld.getWorldBorder();
        border.setCenter(deathmatchCenterX(), deathmatchCenterZ());
        border.setWarningDistance(8);
        border.setWarningTime(20);
        border.setSize(DEATHMATCH_FINAL_BORDER_SIZE, DEATHMATCH_BORDER_CLOSE_SECONDS);

        broadcastMatch(ChatColor.RED + "" + ChatColor.BOLD + "DEATHMATCH"
            + ChatColor.GRAY + " Goals are disabled. Death is permanent.");
        forPlayers(player -> {
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "DEATHMATCH",
                ChatColor.GRAY + "Last team standing wins", 10, 60, 15);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8F, 1.0F);
            updateMatchSidebar(player);
        });
        forSpectators(this::updateMatchSidebar);
    }

    private void deactivateGoals() {
        if (activeWorld == null) {
            return;
        }
        for (GoalCluster goal : teamGoals.values()) {
            for (BlockPos pos : goal.blocks()) {
                activeWorld.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
            }
        }
    }

    private void cancelDeathmatchCountdown() {
        if (deathmatchCountdownTask != null) {
            deathmatchCountdownTask.cancel();
            deathmatchCountdownTask = null;
        }
    }

    private void resetDeathmatchState() {
        cancelDeathmatchCountdown();
        if (activeWorld != null && initialWorldBorderSize > 0.0D) {
            WorldBorder border = activeWorld.getWorldBorder();
            border.setSize(border.getSize());
            border.setCenter(deathmatchCenterX(), deathmatchCenterZ());
            border.setSize(initialWorldBorderSize);
        }
        deathmatchActive = false;
        deathmatchSecondsRemaining = 0;
        initialWorldBorderSize = 0.0D;
    }

    private void preparePlayer(Player player, BridgeTeam team) {
        resetArrowState(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        clearPotionEffects(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
        }
        player.setHealth(Math.min(player.getMaxHealth(), 20.0D));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        if (kitEditorService == null) {
            KitLayout.place(player.getInventory(), BridgeKitDefinition.items(team.block),
                KitLayout.normalize(new BridgeKitDefinition(), null));
        } else {
            kitEditorService.equipBridgeKit(player.getInventory(), player.getUniqueId(), team.block);
        }
        player.getInventory().setArmorContents(BridgeKitDefinition.armor(team.armorColor));
        player.updateInventory();
    }

    private void clearPotionEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setAbsorptionAmount(0.0D);
    }

    private void scheduleArrowRecharge(UUID playerId, long delayTicks) {
        if (playerId == null) {
            return;
        }
        BukkitTask existing = arrowRechargeTasks.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            arrowRechargeTasks.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && matchActive && playerTeams.containsKey(playerId)) {
                giveSingleBridgeArrow(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.45F, 1.7F);
            }
        }, Math.max(1L, delayTicks));
        arrowRechargeTasks.put(playerId, task);
    }

    private void completeArrowRecharge(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BukkitTask task = arrowRechargeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && matchActive && playerTeams.containsKey(playerId)) {
            giveSingleBridgeArrow(player);
        }
    }

    private void giveSingleBridgeArrow(Player player) {
        if (player == null || !player.isOnline() || !playerTeams.containsKey(player.getUniqueId())) {
            return;
        }
        removeBridgeArrows(player);
        int preferredSlot = kitEditorService == null ? 8 : kitEditorService.bridgeArrowSlot(player.getUniqueId());
        if (player.getInventory().getItem(preferredSlot) == null
            || player.getInventory().getItem(preferredSlot).getType() == Material.AIR) {
            player.getInventory().setItem(preferredSlot, new ItemStack(Material.ARROW, 1));
        } else {
            player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        }
        player.updateInventory();
    }

    private void removeBridgeArrows(Player player) {
        if (player == null) {
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack != null && stack.getType() == Material.ARROW) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (player.getInventory().getItemInOffHand().getType() == Material.ARROW) {
            player.getInventory().setItemInOffHand(null);
        }
        if (player.getItemOnCursor().getType() == Material.ARROW) {
            player.setItemOnCursor(null);
        }
    }

    private void resetArrowState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BukkitTask task = arrowRechargeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        firedArrowShooters.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
    }

    private void clearAllArrowState() {
        for (BukkitTask task : arrowRechargeTasks.values()) {
            task.cancel();
        }
        arrowRechargeTasks.clear();
        firedArrowShooters.clear();
    }

    private void handleScore(Player player, BridgeTeam scoredOn) {
        if (!matchActive || deathmatchActive || scoreResetActive || player == null
            || scoringLocked.contains(player.getUniqueId())) {
            return;
        }
        BridgeTeam scorerTeam = playerTeams.get(player.getUniqueId());
        if (scorerTeam == null) {
            return;
        }
        if (scorerTeam == scoredOn) {
            resetInvalidGoal(player);
            return;
        }
        boolean singlePlayerAutoWin = managedRound == null && autoWinEnabled && playerTeams.size() == 1;
        if (!singlePlayerAutoWin && (!participatingTeams.contains(scoredOn)
            || eliminatedTeams.contains(scoredOn) || activeTeamMembers(scoredOn).isEmpty())) {
            resetInvalidGoal(player);
            return;
        }
        scoringLocked.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> scoringLocked.remove(player.getUniqueId()), Math.max(1L, SCORE_GRACE_MS / 50L));
        if (singlePlayerAutoWin) {
            finishMatch(scorerTeam, player.getName());
            return;
        }
        int score = scoresAgainst.merge(scoredOn, 1, Integer::sum);
        broadcastMatch(scorerTeam.chatColor + player.getName() + ChatColor.GRAY + " scored against "
            + scoredOn.chatColor + scoredOn.displayName + ChatColor.GRAY + ". "
            + scoredOn.chatColor + scoredOn.displayName + ChatColor.GRAY + " has " + ChatColor.WHITE + score + ChatColor.GRAY + "/" + WIN_SCORE + " against them.");
        if (score < WIN_SCORE) {
            forPlayers(participant -> {
                BridgeTeam team = playerTeams.get(participant.getUniqueId());
                if (team == scorerTeam) {
                    participant.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "GOAL!",
                        ChatColor.GRAY + "Scored against " + scoredOn.chatColor + scoredOn.displayName, 0, 18, 6);
                    HydraSounds.playRoundResult(participant);
                } else if (team == scoredOn) {
                    participant.sendTitle(ChatColor.RED + "SCORED ON",
                        ChatColor.GRAY + scorerTeam.displayName + " scored", 0, 18, 6);
                    HydraSounds.playRoundLoss(participant);
                }
            });
        }
        forPlayers(this::updateMatchSidebar);
        forSpectators(this::updateMatchSidebar);
        if (score >= WIN_SCORE) {
            eliminateTeam(scoredOn, player.getName());
            return;
        }
        resetRound();
    }

    private void resetInvalidGoal(Player player) {
        if (player == null || !matchActive || !playerTeams.containsKey(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!scoringLocked.add(playerId)) {
            return;
        }
        player.sendActionBar(Component.text(ChatColor.RED + "That goal is inactive."));
        respawnPlayer(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> scoringLocked.remove(playerId),
            Math.max(1L, SCORE_GRACE_MS / 50L));
    }

    private void eliminateTeam(BridgeTeam eliminatedTeam, String scorerName) {
        LinkedHashSet<UUID> eliminatedIds = new LinkedHashSet<>(activeTeamMembers(eliminatedTeam));
        clearPrivateTeamGlow();
        teamPlayers.remove(eliminatedTeam);
        eliminatedTeams.add(eliminatedTeam);
        eliminationOrder.add(eliminatedTeam);
        for (UUID eliminatedId : eliminatedIds) {
            playerTeams.remove(eliminatedId);
            scoringLocked.remove(eliminatedId);
            Player eliminatedPlayer = Bukkit.getPlayer(eliminatedId);
            if (eliminatedPlayer != null && eliminatedPlayer.isOnline()) {
                Location spawn = teamSpawns.get(eliminatedTeam);
                if (spawn != null) {
                    eliminatedPlayer.teleport(spawn);
                }
                spectators.add(eliminatedId);
                setSpectatorState(eliminatedPlayer);
                eliminatedPlayer.sendTitle(ChatColor.RED + "ELIMINATED", ChatColor.GRAY + eliminatedTeam.displayName + " reached " + WIN_SCORE + "/" + WIN_SCORE + ".", 10, 50, 15);
                HydraSounds.playSpectatorEnter(eliminatedPlayer);
                updateMatchSidebar(eliminatedPlayer);
            }
        }
        broadcastMatch(eliminatedTeam.chatColor + eliminatedTeam.displayName + ChatColor.GRAY + " has been eliminated.");
        syncSpectatorVisibility();
        refreshPrivateTeamGlow();
        if (activeTeams().isEmpty()) {
            stopMatch(Bukkit.getConsoleSender());
            return;
        }
        if (activeTeams().size() == 1) {
            BridgeTeam winner = activeTeams().iterator().next();
            finishMatch(winner, scorerName);
            return;
        }
        resetRound();
    }

    private void resetRound() {
        cancelRoundReset();
        scoreResetActive = true;
        clearRoundEntities();
        clearAllArrowState();
        clearPlacedBlocks();
        roundResetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            roundResetTask = null;
            if (!matchActive || deathmatchActive) {
                scoreResetActive = false;
                return;
            }
            forPlayers(player -> {
                BridgeTeam team = playerTeams.get(player.getUniqueId());
                Location spawn = playerSpawns.get(player.getUniqueId());
                preparePlayer(player, team == null ? BridgeTeam.RED : team);
                if (spawn != null) {
                    player.teleport(spawn);
                }
            });
            scoreResetActive = false;
            refreshPrivateTeamGlow();
        }, 30L);
    }

    private void cancelRoundReset() {
        if (roundResetTask != null) {
            roundResetTask.cancel();
            roundResetTask = null;
        }
        scoreResetActive = false;
    }

    private void finishMatch(BridgeTeam winner, String scorerName) {
        LinkedHashSet<UUID> requeueCandidates = new LinkedHashSet<>(playerTeams.keySet());
        requeueCandidates.addAll(spectators);
        requeueCandidates.removeAll(externalSpectators);
        String resultDetail = deathmatchActive
            ? ChatColor.GRAY + " wins the Bridge deathmatch."
            : ChatColor.GRAY + " wins Bridge. Last score: " + ChatColor.WHITE + scorerName;
        broadcastMatch(winner.chatColor + winner.displayName + resultDetail);
        matchActive = false;
        cancelTasks();
        clearPrivateTeamGlow();
        clearRoundEntities();
        ManagedMinigameRound.Request request = managedRound;
        List<RouletteTeam> placements = request == null ? List.of() : managedTeamPlacements(winner);
        Map<RouletteTeam, Integer> teamEliminations = request == null
            ? Map.of() : managedTeamEliminations();
        UUID winnerId = activeTeamMembers(winner).stream().findFirst().orElse(null);
        Player winnerPlayer = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        if (request == null && winnerPlayer != null && winnerPlayer.isOnline()) {
            int silver = plugin.queueService() == null ? 0 : plugin.queueService().recordBridgeWin(winnerId);
            if (plugin.progressionService() != null) {
                plugin.progressionService().grantShardFragments(winnerId, 1, "BRIDGE_WIN");
            }
            if (silver > 0) {
                winnerPlayer.sendMessage(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + silver + " Coins");
            }
        }
        forPlayers(player -> {
            boolean won = playerTeams.get(player.getUniqueId()) == winner;
            if (request != null) {
                player.sendTitle(ChatColor.AQUA + "" + ChatColor.BOLD + "ROUND COMPLETE",
                    winner.chatColor + winner.displayName + ChatColor.GRAY + " won Bridge", 5, 30, 10);
                HydraSounds.playRoundResult(player);
            } else if (won) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY",
                    ChatColor.GRAY + "Your team won Bridge.", 10, 60, 20);
                HydraSounds.playGameWin(player);
            } else {
                player.sendTitle(winner.chatColor + "" + ChatColor.BOLD + winner.displayName + " WINS", ChatColor.GRAY + "Bridge complete.", 10, 50, 10);
                HydraSounds.playGameLoss(player);
            }
        });
        forSpectators(player -> player.sendTitle(
            request == null ? ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER"
                : ChatColor.AQUA + "" + ChatColor.BOLD + "ROUND COMPLETE",
            winner.chatColor + winner.displayName + ChatColor.GRAY + " won Bridge", 5, 35, 10));
        LinkedHashSet<UUID> returningIds = new LinkedHashSet<>(playerTeams.keySet());
        returningIds.addAll(spectators);
        List<Player> returning = returningIds.stream().map(Bukkit::getPlayer)
            .filter(Objects::nonNull).filter(Player::isOnline).toList();
        plugin.transitionTravelGroup(returning, this::cleanupPlayerForHub, () -> {
            String worldName = activeWorldName;
            cleanupState(false);
            if (request == null) {
                plugin.playerSettingsService().requeueAfterCompletedSession(
                    requeueCandidates, PlayerSettingsService.AutoQueue.BRIDGE);
            }
            if (worldName != null && autoCleanupEnabled) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupWorld(worldName), 20L * 30L);
            } else if (worldName != null) {
                plugin.getLogger().info("[Bridge] Auto-cleanup is OFF; leaving world " + worldName + " loaded for inspection.");
            }
            if (request != null) {
                dispatchManagedResult(request,
                    ManagedMinigameRound.Result.teams(request, placements, teamEliminations));
            }
        });
    }

    private void checkWin() {
        if (!matchActive) {
            return;
        }
        if (activeTeams().size() <= 1) {
            if (activeTeams().isEmpty()) {
                stopMatch(Bukkit.getConsoleSender());
                return;
            }
            BridgeTeam winner = activeTeams().iterator().next();
            UUID winnerId = activeTeamMembers(winner).stream().findFirst().orElse(null);
            Player player = Bukkit.getPlayer(winnerId);
            finishMatch(winner, player == null ? "A player" : player.getName());
        }
    }

    private void cleanupPlayerForHub(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        sidebarSignatures.remove(player.getUniqueId());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        clearPotionEffects(player);
        restorePlayerVisibility(player);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
        }
        player.setHealth(Math.min(20.0D, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        if (plugin.queueService() != null) {
            plugin.queueService().sendPlayerToHub(player);
            plugin.queueService().clearSidebarCache(player.getUniqueId());
            plugin.queueService().refreshSidebar(player);
        }
    }

    private void resetSidebarToHub(Player player) {
        sidebarSignatures.remove(player.getUniqueId());
        if (plugin.queueService() != null) {
            plugin.queueService().clearSidebarCache(player.getUniqueId());
            plugin.queueService().refreshSidebar(player);
        }
    }

    private void cleanupState(boolean keepQueue) {
        if (!keepQueue) {
            queuedPlayers.clear();
        }
        tournamentHubSpectators.clear();
        playerTeams.clear();
        teamPlayers.clear();
        teamGoals.clear();
        teamSpawns.clear();
        playerSpawns.clear();
        participatingTeams.clear();
        eliminatedTeams.clear();
        eliminationOrder.clear();
        recentAttackers.clear();
        managedTeamKills.clear();
        placedBlocks.clear();
        protectedMapBlocks.clear();
        scoringLocked.clear();
        respawningPlayers.clear();
        spectators.clear();
        externalSpectators.clear();
        sidebarSignatures.clear();
        for (BukkitTask task : disconnectExpiryTasks.values()) {
            task.cancel();
        }
        disconnectExpiryTasks.clear();
        disconnectedPlayers.clear();
        disconnectedRoundSpectators.clear();
        clearAllArrowState();
        if (activeMatchTeamId != null && plugin.matchTeamService() != null) {
            plugin.matchTeamService().removeMatch(activeMatchTeamId);
        }
        activeMatchTeamId = null;
        activeWorld = null;
        activeWorldName = null;
        activeMap = null;
        bounds = null;
        matchActive = false;
        matchStarting = false;
        startFreezeActive = false;
        scoreResetActive = false;
        deathmatchActive = false;
        deathmatchSecondsRemaining = 0;
        initialWorldBorderSize = 0.0D;
        autoWinEnabled = false;
        managedRound = null;
        for (BridgeTeam team : BridgeTeam.values()) {
            scoresAgainst.put(team, 0);
        }
    }

    private Set<BridgeTeam> activeTeams() {
        EnumSet<BridgeTeam> active = EnumSet.noneOf(BridgeTeam.class);
        for (BridgeTeam team : participatingTeams) {
            if (!eliminatedTeams.contains(team) && !activeTeamMembers(team).isEmpty()) {
                active.add(team);
            }
        }
        return active;
    }

    private Set<UUID> activeTeamMembers(BridgeTeam team) {
        return teamPlayers.getOrDefault(team, new LinkedHashSet<>());
    }

    private void eliminateAbandonedTeam(BridgeTeam team) {
        if (!eliminatedTeams.add(team)) {
            return;
        }
        eliminationOrder.add(team);
        teamPlayers.remove(team);
    }

    private List<RouletteTeam> managedTeamPlacements(BridgeTeam winner) {
        LinkedHashSet<RouletteTeam> placements = new LinkedHashSet<>();
        placements.add(rouletteTeam(winner));
        for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
            placements.add(rouletteTeam(eliminationOrder.get(i)));
        }
        for (BridgeTeam team : participatingTeams) {
            placements.add(rouletteTeam(team));
        }
        return new ArrayList<>(placements);
    }

    private Map<RouletteTeam, Integer> managedTeamEliminations() {
        EnumMap<RouletteTeam, Integer> eliminations = new EnumMap<>(RouletteTeam.class);
        managedTeamKills.forEach((team, kills) -> eliminations.put(rouletteTeam(team), kills));
        return Map.copyOf(eliminations);
    }

    private void dispatchManagedResult(ManagedMinigameRound.Request request, ManagedMinigameRound.Result result) {
        if (request == null || managedCallbackSent) {
            return;
        }
        managedCallbackSent = true;
        Bukkit.getScheduler().runTask(plugin, () -> request.callback().accept(result));
    }

    private void cancelTasks() {
        cancelQueueCountdown();
        clearStartFreeze();
        cancelRoundReset();
        resetDeathmatchState();
        clearAllArrowState();
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
            Bukkit.unloadWorld(world, false);
        }
        Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName).normalize();
        try {
            if (Files.exists(folder)) {
                try (java.util.stream.Stream<Path> stream = Files.walk(folder)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("[Bridge] Failed deleting " + path + ": " + ex.getMessage());
                        }
                    });
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Bridge] Failed cleaning world " + worldName + ": " + ex.getMessage());
        }
    }

    private void clearPlacedBlocks() {
        if (activeWorld == null) {
            placedBlocks.clear();
            return;
        }
        for (BlockPos pos : new ArrayList<>(placedBlocks)) {
            activeWorld.getBlockAt(pos.x, pos.y, pos.z).setType(Material.AIR, false);
        }
        placedBlocks.clear();
    }

    private void clearRoundEntities() {
        if (activeWorld == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : new ArrayList<>(activeWorld.getEntities())) {
            if (entity instanceof Projectile || entity instanceof org.bukkit.entity.Item
                || entity instanceof org.bukkit.entity.ExperienceOrb
                || entity instanceof org.bukkit.entity.AreaEffectCloud) {
                entity.remove();
            }
        }
    }

    private void tickSidebars() {
        updateQueuedSidebars();
        if (matchActive) {
            forPlayers(this::updateMatchSidebar);
            forSpectators(this::updateMatchSidebar);
        }
    }

    private void updateQueuedSidebars() {
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            lines.add(sidebarLine("Mode", "Bridge", ChatColor.WHITE));
            lines.add(sidebarLine("Queue", "Bridge", ChatColor.WHITE));
            lines.add(sidebarLine("Players", queuedPlayers.size() + "/" + MAX_PLAYERS, ChatColor.YELLOW));
            lines.add(sidebarLine("Needed", String.valueOf(MIN_PLAYERS), ChatColor.WHITE));
            lines.add(separator(0));
            lines.add(sidebarLine("Status", queueCountdownTask == null ? "Waiting" : "Starting " + countdownSeconds + "s", ChatColor.YELLOW));
            lines.add(ChatColor.AQUA + "HydraMC.net");
            applySidebar(player, "BRIDGE", lines);
        }
    }

    private void updateMatchSidebar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BridgeTeam team = assignedMatchTeam(player.getUniqueId());
        List<String> lines = new ArrayList<>();
        RouletteTeam tournamentTeam = plugin.minigameRouletteService() == null
            ? null
            : plugin.minigameRouletteService().teamOf(player.getUniqueId());
        String teamName = tournamentTeam == null
            ? (team == null ? "Spectator" : team.displayName)
            : tournamentTeam.displayName();
        ChatColor teamColor = tournamentTeam == null
            ? (team == null ? ChatColor.GRAY : team.chatColor)
            : tournamentTeam.color();
        lines.add(sidebarLine(tournamentTeam == null ? "Team" : "Your Team", teamName, teamColor));
        lines.add(sidebarLine("Phase", deathmatchActive ? "Deathmatch" : "Goals", deathmatchActive ? ChatColor.RED : ChatColor.WHITE));
        lines.add(sidebarLine("Out At", WIN_SCORE + "/" + WIN_SCORE, ChatColor.WHITE));
        lines.add(separator(0));
        for (BridgeTeam bridgeTeam : BridgeTeam.values()) {
            if (!participatingTeams.contains(bridgeTeam)) {
                continue;
            }
            int score = scoresAgainst.getOrDefault(bridgeTeam, 0);
            String state = score + "/" + WIN_SCORE + (eliminatedTeams.contains(bridgeTeam) ? ChatColor.RED + " OUT" : "");
            lines.add(bridgeTeam.chatColor + bridgeTeam.displayName + " " + ChatColor.WHITE + state);
        }
        lines.add(ChatColor.AQUA + "HydraMC.net");
        applySidebar(player, "BRIDGE", lines);
    }

    private void applySidebar(Player player, String title, List<String> lines) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        String signature = title + "\n" + String.join("\n", lines);
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
            Component.text(ChatColor.AQUA + "" + ChatColor.BOLD + title)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = lines.size();
        int duplicateVariant = 20;
        Set<String> used = new HashSet<>();
        for (String rawLine : lines) {
            String entry = rawLine == null ? blank(duplicateVariant++) : rawLine;
            while (!used.add(entry)) {
                entry = entry + uniqueSuffix(duplicateVariant++);
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

    private String sidebarLine(String label, String value, ChatColor valueColor) {
        return ChatColor.GRAY + label + " " + ChatColor.DARK_GRAY + "• " + valueColor + value;
    }

    private String separator(int variant) {
        return ChatColor.DARK_GRAY + "------------" + uniqueSuffix(variant);
    }

    private String blank(int variant) {
        return ChatColor.values()[Math.floorMod(variant, ChatColor.values().length)] + "";
    }

    private String uniqueSuffix(int variant) {
        return ChatColor.values()[Math.floorMod(variant, ChatColor.values().length)].toString();
    }

    private void forPlayers(Consumer<Player> consumer) {
        for (UUID id : new ArrayList<>(playerTeams.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                consumer.accept(player);
            }
        }
    }

    private void forSpectators(Consumer<Player> consumer) {
        for (UUID id : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                consumer.accept(player);
            }
        }
    }

    private void setSpectatorState(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        player.getInventory().setItem(0, spectatorItem(Material.COMPASS, ChatColor.AQUA + "Teleport Compass",
            SPECTATOR_ACTION_TELEPORT));
        player.getInventory().setItem(8, spectatorItem(Material.PAPER, ChatColor.RED + "Return to Hub",
            SPECTATOR_ACTION_HUB));
        player.updateInventory();
    }

    private void openSpectatorTeleportMenu(Player spectator) {
        if (!spectators.contains(spectator.getUniqueId()) || !matchActive) return;
        Inventory inventory = Bukkit.createInventory(null, 54, SPECTATOR_MENU_TITLE);
        int slot = 0;
        for (UUID playerId : new ArrayList<>(playerTeams.keySet())) {
            Player target = Bukkit.getPlayer(playerId);
            if (target == null || !target.isOnline() || target.getWorld() != activeWorld || slot >= 45) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) continue;
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GREEN + target.getName());
            meta.getPersistentDataContainer().set(spectatorActionKey, PersistentDataType.STRING,
                SPECTATOR_ACTION_TARGET_PREFIX + target.getUniqueId());
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }
        inventory.setItem(49, spectatorItem(Material.BARRIER, ChatColor.RED + "Close", SPECTATOR_ACTION_CLOSE));
        spectator.openInventory(inventory);
    }

    private void teleportSpectatorToTarget(Player spectator, String rawTargetId) {
        if (!spectators.contains(spectator.getUniqueId()) || !matchActive) return;
        try {
            UUID targetId = UUID.fromString(rawTargetId);
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline() || !playerTeams.containsKey(targetId)
                || target.getWorld() != activeWorld) {
                spectator.sendMessage(PREFIX + ChatColor.YELLOW + "That player is no longer active.");
                openSpectatorTeleportMenu(spectator);
                return;
            }
            spectator.closeInventory();
            UUID spectatorId = spectator.getUniqueId();
            plugin.transitionTravel(spectator, () -> {
                if (spectators.contains(spectatorId) && matchActive && target.isOnline()
                    && playerTeams.containsKey(targetId) && target.getWorld() == activeWorld) {
                    spectator.teleport(target.getLocation());
                }
            });
        } catch (IllegalArgumentException ex) {
            spectator.sendMessage(PREFIX + ChatColor.RED + "Invalid spectator target.");
        }
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
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(spectatorActionKey, PersistentDataType.STRING);
    }

    private void syncSpectatorVisibility() {
        forPlayers(alive -> forSpectators(spectator -> alive.hidePlayer(plugin, spectator)));
        forSpectators(spectator -> {
            forPlayers(alive -> spectator.showPlayer(plugin, alive));
            forSpectators(other -> PlayerSettingsService.syncSpectatorPairOrDefault(plugin, spectator, other));
        });
    }

    public ChatColor privateTeamColor(UUID viewerId, UUID targetId) {
        if (!matchActive || viewerId == null || targetId == null || viewerId.equals(targetId)) {
            return null;
        }
        BridgeTeam viewerTeam = assignedMatchTeam(viewerId);
        return viewerTeam != null && activeMatchTeamId != null
            && plugin.matchTeamService().sameTeam(activeMatchTeamId, viewerId, targetId)
            ? viewerTeam.chatColor : null;
    }

    private void refreshPrivateTeamGlow() {
        if (!matchActive) {
            return;
        }
        forPlayers(viewer -> forPlayers(target -> {
            if (viewer.equals(target)) {
                return;
            }
            boolean teammate = privateTeamColor(viewer.getUniqueId(), target.getUniqueId()) != null
                && PlayerSettingsService.enabledOrDefault(plugin, viewer.getUniqueId(), PlayerSettingsService.Toggle.TEAM_GLOW);
            glowService.setGlowing(viewer, target, teammate || target.isGlowing());
        }));
    }

    public boolean isLobbyTimeContext(UUID playerId) {
        return queuedPlayers.contains(playerId) || (startFreezeActive && playerTeams.containsKey(playerId));
    }

    public void refreshSpectatorVisibilityPreference() {
        syncSpectatorVisibility();
    }

    public void refreshTeamGlowPreference() {
        refreshPrivateTeamGlow();
    }

    private void clearPrivateTeamGlow() {
        LinkedHashSet<UUID> audience = new LinkedHashSet<>(playerTeams.keySet());
        audience.addAll(spectators);
        for (UUID viewerId : audience) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            for (UUID targetId : audience) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline() && !viewer.equals(target)) {
                    glowService.setGlowing(viewer, target, target.isGlowing());
                }
            }
        }
    }

    private void restorePlayerVisibility(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
            player.showPlayer(plugin, other);
        }
    }

    private BridgeMap selectedMap() {
        if ("random".equals(selectedMapKey)) {
            return maps.get(ThreadLocalRandom.current().nextInt(maps.size()));
        }
        return maps.stream()
            .filter(map -> map.key().equals(selectedMapKey))
            .findFirst()
            .orElseGet(() -> maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
    }

    private String selectedMapDisplayName() {
        if ("random".equals(selectedMapKey)) {
            return "Random";
        }
        return maps.stream()
            .filter(map -> map.key().equals(selectedMapKey))
            .map(BridgeMap::displayName)
            .findFirst()
            .orElse(selectedMapKey);
    }

    private String normalizeMapKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "random";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        if (key.equals("random")) {
            return key;
        }
        for (BridgeMap map : maps) {
            if (map.key().equals(key)) {
                return key;
            }
        }
        return null;
    }

    private List<String> bridgeMapKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("random");
        for (BridgeMap map : maps) {
            keys.add(map.key());
        }
        return keys;
    }

    private void broadcastQueue(String message) {
        broadcastQueueRaw(PREFIX + message);
    }

    private void broadcastQueueRaw(String message) {
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void broadcastMatch(String message) {
        forPlayers(player -> player.sendMessage(PREFIX + message));
        forSpectators(player -> player.sendMessage(PREFIX + message));
    }

    private void broadcastParticipants(List<UUID> participants, String message) {
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + message);
            }
        }
    }

    private String formatGoal(GoalCluster goal) {
        if (goal == null) {
            return "none";
        }
        return Math.round(goal.centerX()) + "," + goal.maxY() + "," + Math.round(goal.centerZ());
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "none";
        }
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private List<String> completeFrom(String prefix, List<String> values) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle))
            .toList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (!matchActive || activeWorldName == null || event.getFrom().getWorld() == null
            || !activeWorldName.equals(event.getFrom().getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!playerTeams.containsKey(playerId) && !spectators.contains(playerId)) {
            return;
        }
        event.setCancelled(true);
        if (playerTeams.containsKey(playerId) && !startFreezeActive && !scoreResetActive) {
            handleGoalEntry(player, event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!matchActive || event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (spectators.contains(playerId)) {
            if (event.getTo().getY() < (bounds == null ? 0 : bounds.minY() - 8)) {
                Location safe = spectatorReturnLocation(playerId);
                if (safe != null) {
                    player.teleport(safe);
                    player.setVelocity(new org.bukkit.util.Vector());
                    player.setFallDistance(0.0F);
                }
            }
            return;
        }
        if (!playerTeams.containsKey(playerId)) {
            return;
        }
        if (startFreezeActive) {
            Location spawn = playerSpawns.get(player.getUniqueId());
            if (spawn != null && event.getTo().distanceSquared(spawn) > 0.01D) {
                event.setTo(spawn);
            }
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() == null || !Objects.equals(activeWorldName, to.getWorld().getName())) {
            return;
        }
        if (to.getY() < (bounds == null ? 0 : bounds.minY() - 16)) {
            if (deathmatchActive) {
                eliminateDeathmatchPlayer(player, "fell into the void");
            } else {
                respawnPlayer(player);
            }
            return;
        }
        handleGoalEntry(player, to);
    }

    private Location spectatorReturnLocation(UUID playerId) {
        Location assigned = playerSpawns.get(playerId);
        if (assigned != null && assigned.getWorld() != null) {
            return assigned.clone().add(0.0D, 4.0D, 0.0D);
        }
        if (activeWorld == null) {
            return null;
        }
        Location spawn = activeWorld.getSpawnLocation().clone().add(0.5D, 4.0D, 0.5D);
        spawn.setPitch(0.0F);
        return spawn;
    }

    private void handleGoalEntry(Player player, Location location) {
        if (deathmatchActive) {
            return;
        }
        for (Map.Entry<BridgeTeam, GoalCluster> entry : teamGoals.entrySet()) {
            if (entry.getValue().contains(location)) {
                handleScore(player, entry.getKey());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter) || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        UUID shooterId = shooter.getUniqueId();
        if (!matchActive || !playerTeams.containsKey(shooterId)) {
            return;
        }
        firedArrowShooters.put(arrow.getUniqueId(), shooterId);
        scheduleArrowRecharge(shooterId, ARROW_FALLBACK_RECHARGE_TICKS);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (arrowRechargeTasks.containsKey(shooterId)) {
                removeBridgeArrows(shooter);
                shooter.updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID shooterId = firedArrowShooters.remove(event.getEntity().getUniqueId());
        if (shooterId == null) {
            return;
        }
        if (event.getHitEntity() instanceof Player hitPlayer
            && !hitPlayer.getUniqueId().equals(shooterId)
            && playerTeams.containsKey(hitPlayer.getUniqueId())) {
            completeArrowRecharge(shooterId);
            return;
        }
        scheduleArrowRecharge(shooterId, ARROW_RECHARGE_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!matchActive || !playerTeams.containsKey(player.getUniqueId())) {
            return;
        }
        if (startFreezeActive || scoreResetActive) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlockPlaced().getY() > activeMapBuildLimit()
            || isSpawnNoBuildZone(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(ChatColor.RED + "You cannot build here."));
            return;
        }
        placedBlocks.add(BlockPos.of(event.getBlockPlaced()));
        BridgeTeam team = playerTeams.get(player.getUniqueId());
        EquipmentSlot hand = event.getHand();
        Bukkit.getScheduler().runTask(plugin, () -> restockTeamBlocks(player, team, hand));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!matchActive || !playerTeams.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        if (isSpawnNoBuildZone(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        BlockPos pos = BlockPos.of(event.getBlock());
        if (placedBlocks.remove(pos)) {
            return;
        }
        if (protectedMapBlocks.contains(pos)) {
            event.setCancelled(true);
        }
    }

    private boolean isSpawnNoBuildZone(Block block) {
        if (block == null || activeMap == null) {
            return false;
        }
        for (Location spawn : teamSpawns.values()) {
            if (spawn != null && Math.abs(block.getX() - spawn.getBlockX()) <= CASTLE_NO_BUILD_RADIUS
                && Math.abs(block.getZ() - spawn.getBlockZ()) <= CASTLE_NO_BUILD_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private boolean isCastleMap() {
        return activeMap != null && "bridge".equals(activeMap.key());
    }

    private int activeMapBuildLimit() {
        return isCastleMap() ? CASTLE_MAX_BUILD_Y : OCEAN_MAX_BUILD_Y;
    }

    private void restockTeamBlocks(Player player, BridgeTeam team, EquipmentSlot hand) {
        if (player == null || team == null || !player.isOnline() || !matchActive
            || !playerTeams.containsKey(player.getUniqueId())) {
            return;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack != null && isBridgeTeamBlock(stack.getType())
                && (hand == EquipmentSlot.OFF_HAND || slot != heldSlot)) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand.getType() == Material.AIR || offHand.getType() == team.block) {
                player.getInventory().setItemInOffHand(new ItemStack(team.block, 64));
            }
        } else {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (isBridgeTeamBlock(offHand.getType())) {
                player.getInventory().setItemInOffHand(null);
            }
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType() == Material.AIR || mainHand.getType() == team.block) {
                player.getInventory().setItemInMainHand(new ItemStack(team.block, 64));
            }
        }
        player.updateInventory();
    }

    private boolean isBridgeTeamBlock(Material material) {
        if (material == null) {
            return false;
        }
        for (BridgeTeam team : BridgeTeam.values()) {
            if (team.block == material) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (isBridgeAlive(playerId) || spectators.contains(playerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (isBridgeAlive(player.getUniqueId()) && event.getItem().getItemStack().getType() == Material.ARROW) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!spectators.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        String action = spectatorAction(event.getItem());
        if (SPECTATOR_ACTION_TELEPORT.equals(action)) {
            openSpectatorTeleportMenu(player);
        } else if (SPECTATOR_ACTION_HUB.equals(action)) {
            if (managedRound != null) returnTournamentSpectatorToHub(player);
            else leaveToHub(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInteractEntity(PlayerInteractEntityEvent event) {
        if (spectators.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (SPECTATOR_MENU_TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
            String action = spectatorAction(event.getCurrentItem());
            if (SPECTATOR_ACTION_CLOSE.equals(action)) {
                player.closeInventory();
            } else if (action != null && action.startsWith(SPECTATOR_ACTION_TARGET_PREFIX)) {
                teleportSpectatorToTarget(player, action.substring(SPECTATOR_ACTION_TARGET_PREFIX.length()));
            }
            return;
        }
        if (spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!isBridgeAlive(player.getUniqueId())) {
            return;
        }
        int hotbarButton = event.getHotbarButton();
        ItemStack hotbarItem = hotbarButton >= 0 && hotbarButton < 9
            ? player.getInventory().getItem(hotbarButton)
            : null;
        if (isArrowStack(event.getCurrentItem()) || isArrowStack(event.getCursor()) || isArrowStack(hotbarItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (spectators.contains(player.getUniqueId())
            || (isBridgeAlive(player.getUniqueId()) && isArrowStack(event.getOldCursor()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isBridgeAlive(event.getPlayer().getUniqueId())
            && (isArrowStack(event.getMainHandItem()) || isArrowStack(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    private boolean isArrowStack(ItemStack stack) {
        return stack != null && stack.getType() == Material.ARROW;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isSpectatorDamageSource(event.getDamager())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player target) || !isBridgeAlive(target.getUniqueId())) {
            return;
        }
        Player attacker = damagingPlayer(event.getDamager());
        if (attacker != null && isBridgeAlive(attacker.getUniqueId()) && activeMatchTeamId != null
            && plugin.matchTeamService().sameTeam(activeMatchTeamId, attacker.getUniqueId(), target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private Player damagingPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isSpectatorDamageSource(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return spectators.contains(player.getUniqueId());
        }
        return damager instanceof Projectile projectile
            && projectile.getShooter() instanceof Player player
            && spectators.contains(player.getUniqueId());
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
        if (!isBridgeAlive(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (respawningPlayers.contains(playerId)) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            player.setFallDistance(0.0F);
            return;
        }
        if (startFreezeActive || scoreResetActive) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = damagingPlayer(byEntity.getDamager());
            if (attacker != null && (spectators.contains(attacker.getUniqueId())
                || !validBridgeAttacker(attacker.getUniqueId(), playerId))) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.isCancelled()) {
            return;
        }
        UUID creditedKiller = creditedBridgeKiller(player, event);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            creditManagedBridgeKill(playerId, creditedKiller);
            if (deathmatchActive) {
                eliminateDeathmatchPlayer(player, "fell into the void");
            } else {
                respawnPlayer(player);
            }
            return;
        }
        if (event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            creditManagedBridgeKill(playerId, creditedKiller);
            if (deathmatchActive) {
                eliminateDeathmatchPlayer(player, "was eliminated");
                return;
            }
            respawningPlayers.add(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (player.isOnline() && matchActive && playerTeams.containsKey(playerId)) {
                        respawnPlayer(player);
                    }
                } finally {
                    respawningPlayers.remove(playerId);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        respawningPlayers.remove(playerId);
        if (queuedPlayers.remove(playerId)) {
            broadcastQueue(event.getPlayer().getName() + " left Bridge.");
            resetSidebarToHub(event.getPlayer());
            return;
        }
        if (spectators.remove(playerId)) {
            externalSpectators.remove(playerId);
            sidebarSignatures.remove(playerId);
            return;
        }
        if (playerTeams.containsKey(playerId)) {
            if (deathmatchActive) {
                creditManagedBridgeKill(playerId, recentBridgeKiller(playerId));
                eliminateDeathmatchPlayer(event.getPlayer(), "disconnected");
            } else {
                creditManagedBridgeKill(playerId, recentBridgeKiller(playerId));
                markDisconnected(playerId, event.getPlayer().getName());
            }
        }
    }

    private void markDisconnected(UUID playerId, String playerName) {
        if (!disconnectedPlayers.add(playerId)) {
            return;
        }
        BukkitTask previous = disconnectExpiryTasks.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }
        broadcastMatch(playerName + " disconnected and has 60 seconds to /rejoin.");
        disconnectExpiryTasks.put(playerId, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            disconnectExpiryTasks.remove(playerId);
            if (!matchActive || !disconnectedPlayers.remove(playerId)) {
                return;
            }
            BridgeTeam team = playerTeams.remove(playerId);
            if (team == null) {
                return;
            }
            LinkedHashSet<UUID> members = teamPlayers.get(team);
            if (members != null) {
                members.remove(playerId);
                if (members.isEmpty()) {
                    eliminateAbandonedTeam(team);
                }
            }
            if (managedRound != null && managedRound.roster().contains(playerId)) {
                disconnectedRoundSpectators.add(playerId);
            }
            broadcastMatch(playerName + " did not rejoin and was removed from the round.");
            checkWin();
        }, 20L * 60L));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isInBridge(event.getPlayer().getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshPrivateTeamGlow, 2L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (isInBridge(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, this::refreshPrivateTeamGlow, 2L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (isInBridge(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, this::refreshPrivateTeamGlow, 2L);
        }
    }

    private void respawnPlayer(Player player) {
        if (deathmatchActive) {
            eliminateDeathmatchPlayer(player, "was eliminated");
            return;
        }
        BridgeTeam team = playerTeams.get(player.getUniqueId());
        recentAttackers.remove(player.getUniqueId());
        Location spawn = playerSpawns.get(player.getUniqueId());
        if (spawn != null) {
            player.teleport(spawn);
        }
        preparePlayer(player, team == null ? BridgeTeam.RED : team);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 0.9F);
    }

    private void eliminateDeathmatchPlayer(Player player, String reason) {
        if (player == null || !matchActive || !deathmatchActive) {
            return;
        }
        UUID playerId = player.getUniqueId();
        recentAttackers.remove(playerId);
        BridgeTeam team = playerTeams.remove(playerId);
        if (team == null) {
            return;
        }
        resetArrowState(playerId);
        respawningPlayers.remove(playerId);
        scoringLocked.remove(playerId);
        LinkedHashSet<UUID> members = teamPlayers.get(team);
        if (members != null) {
            members.remove(playerId);
        }
        spectators.add(playerId);
        if (player.isOnline()) {
            Location safe = spectatorReturnLocation(playerId);
            if (safe != null) {
                player.teleport(safe);
            }
            setSpectatorState(player);
            player.sendTitle(ChatColor.RED + "ELIMINATED", ChatColor.GRAY + "Deathmatch death is permanent.", 10, 50, 15);
            HydraSounds.playSpectatorEnter(player);
            updateMatchSidebar(player);
        }
        broadcastMatch(team.chatColor + player.getName() + ChatColor.GRAY + " " + reason + " in Deathmatch.");
        if (members == null || members.isEmpty()) {
            if (eliminatedTeams.add(team)) {
                eliminationOrder.add(team);
                broadcastMatch(team.chatColor + team.displayName + ChatColor.GRAY + " has been eliminated.");
            }
            teamPlayers.remove(team);
        }
        syncSpectatorVisibility();
        refreshPrivateTeamGlow();
        Set<BridgeTeam> remaining = activeTeams();
        if (remaining.isEmpty()) {
            stopMatch(Bukkit.getConsoleSender());
            return;
        }
        if (remaining.size() == 1) {
            finishMatch(remaining.iterator().next(), "Deathmatch");
            return;
        }
        forPlayers(this::updateMatchSidebar);
        forSpectators(this::updateMatchSidebar);
    }

    private UUID creditedBridgeKiller(Player victim, EntityDamageEvent event) {
        UUID victimId = victim.getUniqueId();
        if (!event.isCancelled() && event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = damagingPlayer(byEntity.getDamager());
            if (attacker != null && validBridgeAttacker(attacker.getUniqueId(), victimId)) {
                UUID attackerId = attacker.getUniqueId();
                recentAttackers.put(victimId, new RecentAttacker(attackerId, System.currentTimeMillis()));
                return attackerId;
            }
        }
        return recentBridgeKiller(victimId);
    }

    private UUID recentBridgeKiller(UUID victimId) {
        RecentAttacker recent = recentAttackers.get(victimId);
        return recent != null && System.currentTimeMillis() - recent.timeMs() <= KILL_CREDIT_MS
            && validBridgeAttacker(recent.attackerId(), victimId) ? recent.attackerId() : null;
    }

    private boolean validBridgeAttacker(UUID attackerId, UUID victimId) {
        if (attackerId == null || victimId == null || attackerId.equals(victimId)) {
            return false;
        }
        BridgeTeam attackerTeam = playerTeams.get(attackerId);
        BridgeTeam victimTeam = playerTeams.get(victimId);
        return attackerTeam != null && victimTeam != null && attackerTeam != victimTeam;
    }

    private void creditManagedBridgeKill(UUID victimId, UUID attackerId) {
        if (managedRound == null || attackerId == null || !managedRound.roster().contains(attackerId)
            || !managedRound.roster().contains(victimId) || !validBridgeAttacker(attackerId, victimId)) {
            return;
        }
        BridgeTeam attackerTeam = playerTeams.get(attackerId);
        managedTeamKills.merge(attackerTeam, 1, Integer::sum);
        recentAttackers.remove(victimId);
    }

    private record RecentAttacker(UUID attackerId, long timeMs) {
    }

    private enum BridgeTeam {
        RED("Red", ChatColor.RED, Color.RED, Material.RED_TERRACOTTA, 180.0F),
        BLUE("Blue", ChatColor.BLUE, Color.BLUE, Material.BLUE_TERRACOTTA, 90.0F),
        GREEN("Green", ChatColor.GREEN, Color.GREEN, Material.GREEN_TERRACOTTA, -90.0F),
        YELLOW("Yellow", ChatColor.YELLOW, Color.YELLOW, Material.YELLOW_TERRACOTTA, 0.0F);

        private final String displayName;
        private final ChatColor chatColor;
        private final Color armorColor;
        private final Material block;
        private final float spawnYaw;

        BridgeTeam(String displayName, ChatColor chatColor, Color armorColor, Material block, float spawnYaw) {
            this.displayName = displayName;
            this.chatColor = chatColor;
            this.armorColor = armorColor;
            this.block = block;
            this.spawnYaw = spawnYaw;
        }
    }

    private record BridgeMap(String key, String displayName, String resourcePath) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private record BlockPos(int x, int y, int z) {
        private static BlockPos of(Block block) {
            return new BlockPos(block.getX(), block.getY(), block.getZ());
        }
    }

    private record GoalCluster(List<BlockPos> blocks, double centerX, double centerY, double centerZ, int maxY) {
        private static GoalCluster from(List<BlockPos> blocks) {
            double sx = 0.0D;
            double sy = 0.0D;
            double sz = 0.0D;
            int maxY = Integer.MIN_VALUE;
            for (BlockPos pos : blocks) {
                sx += pos.x;
                sy += pos.y;
                sz += pos.z;
                maxY = Math.max(maxY, pos.y);
            }
            int size = Math.max(1, blocks.size());
            return new GoalCluster(List.copyOf(blocks), sx / size, sy / size, sz / size, maxY);
        }

        private boolean contains(Location location) {
            if (location == null) {
                return false;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            for (BlockPos pos : blocks) {
                if (Math.abs(pos.x - x) <= 1 && Math.abs(pos.z - z) <= 1 && Math.abs(pos.y - y) <= 2) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class EmptyBridgeChunkGenerator extends ChunkGenerator {
        private final int spawnY;

        private EmptyBridgeChunkGenerator(int spawnY) {
            this.spawnY = spawnY;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures(@NotNull WorldInfo worldInfo, @NotNull java.util.Random random, int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return world == null ? null : new Location(world, 0.5D, spawnY, 0.5D);
        }

        @Override
        public boolean isParallelCapable() {
            return true;
        }
    }
}
