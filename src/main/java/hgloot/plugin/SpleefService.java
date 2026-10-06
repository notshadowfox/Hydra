package hgloot.plugin;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import hgloot.plugin.util.HydraSounds;
import hgloot.plugin.util.HydraIcons;

public final class SpleefService implements Listener {
    private static final String HYDRA_PREFIX = HydraIcons.EXCLAMATION + " " + ChatColor.GRAY;
    private static final List<String> MAP_KEYS = List.of(
        "base", "rings", "fourcorners", "stars",
        "desertclassic", "desertrings", "desertfourcorners",
        "desertstars", "desertlayers", "snowylayers",
        "random"
    );
    private static final String SIDEBAR_OBJECTIVE = "hydra_hud";
    private static final int MIN_PLAYERS = 8;
    private static final int MAX_PLAYERS = 16;
    private static final int ARENA_CENTER_HALF_SIZE = 20;
    private static final int BASE_RINGS_COLLAPSE_RADIUS = 32;
    private static final int BOULDER_COLLAPSE_RADIUS = 64;
    private static final int BOULDER_COLLAPSE_BAND_HEIGHT = 5;
    private static final int PLAYER_SPAWN_Y = 130;
    private static final int QUEUE_COUNTDOWN_SECONDS = 15;
    private static final int GRACE_SECONDS = 5;
    private static final int SHOVEL_UPGRADE_BREAKS = 15;
    private static final int SNOWBALL_EVERY_BREAKS = 10;
    private static final int MAX_HELD_POWERUPS = 2;
    private static final int POWERUP_GRANT_SECONDS = 20;
    private static final int INVIS_ORB_TICKS = 100;
    private static final int LEVITATION_ORB_TICKS = 60; // 3 seconds
    private static final int LEVITATION_ORB_AMPLIFIER = 4; // Levitation V ("power 5")
    // Eight forward blocks / twenty ticks = 0.4 forward blocks per tick.
    private static final int BOOMERANG_OUTBOUND_TICKS = 20;
    private static final int BOOMERANG_RETURN_TICKS = 20;
    private static final double BOOMERANG_FORWARD_BLOCKS = 8.0D;
    private static final double BOOMERANG_SIDE_BLOCKS = 4.0D;
    private static final double BOOMERANG_SAMPLE_STEP = 0.20D;
    private static final double BOOMERANG_OWNER_SAFETY_RADIUS = 2.0D;
    private static final int STOP_WATCH_TICKS = 100;
    private static final double LASSO_RANGE_BLOCKS = 8.0D;
    private static final int LASSO_ARC_TICKS = 10;
    private static final double LASSO_ARC_LIFT_BLOCKS = 1.75D;
    private static final double GALE_MACE_WIELDER_LAUNCH_VELOCITY = 2.78D;
    private static final double GALE_MACE_TARGET_LAUNCH_VELOCITY = 1.185D;
    private static boolean floorDecayEnabled = true;
    private static boolean autoCleanupEnabled = true;
    private static final int LAYER_COLLAPSE_FIRST_DELAY_SECONDS = 45;
    private static final int LAYER_COLLAPSE_SECOND_HALF_DELAY_SECONDS = 10;
    private static final int LAYER_COLLAPSE_NEXT_LAYER_SECONDS = 40;
    private static final long LAYER_COLLAPSE_BREAK_TICKS = 100L;
    private static final int BORDER_SHRINK_DELAY_SECONDS = 150;
    private static final long BORDER_SHRINK_DURATION_SECONDS = 120L;
    private static final double BORDER_FINAL_SIZE = 18.0D;
    private static final long OUTSIDE_BORDER_GRACE_MS = 4_000L;
    private static final long KILL_CREDIT_MS = 8_000L;
    private static final long WIN_RETURN_TICKS = 20L * 10L;
    private static final long RECONNECT_BODY_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final String SPECTATOR_MENU_TITLE = ChatColor.DARK_AQUA + "Spleef Spectate";
    private static final String SPECTATOR_ACTION_TELEPORT_COMPASS = "spectator_teleport_compass";
    private static final String SPECTATOR_ACTION_HUB_PAPER = "spectator_hub_paper";
    private static final String SPECTATOR_ACTION_TARGET_PREFIX = "spectator_target:";
    private static final String SPECTATOR_ACTION_CLOSE = "spectator_close";
    private static final String TUTORIAL_MENU_TITLE = ChatColor.DARK_AQUA + "Spleef Items";
    private static final Set<Material> BASE_TILE_TYPES = EnumSet.of(Material.SNOW_BLOCK, Material.SNOW, Material.POWDER_SNOW);
    private static final Set<Material> STAGE_TILE_TYPES = EnumSet.of(Material.NOTE_BLOCK);
    private static final Set<Material> TILE_TYPES = EnumSet.copyOf(BASE_TILE_TYPES);

    static {
        TILE_TYPES.addAll(STAGE_TILE_TYPES);
    }

    private final HGLootPlugin plugin;
    private final SpleefService manager;
    private final boolean matchInstance;
    private final NamespacedKey itemKey;
    private final NamespacedKey projectileKey;
    private final LinkedHashSet<UUID> queuedPlayers = new LinkedHashSet<>();
    private final Set<SpleefService> activeMatches = new LinkedHashSet<>();
    private final Map<UUID, SpleefService> activeMatchesByPlayer = new HashMap<>();
    private final Map<String, SpleefService> activeMatchesByWorld = new HashMap<>();
    private final Map<UUID, Integer> shovelBreaks = new HashMap<>();
    private final Map<UUID, Integer> totalBreaks = new HashMap<>();
    private final Map<UUID, Integer> heldPowerups = new HashMap<>();
    private final Map<UUID, BukkitTask> invisRevealTasks = new HashMap<>();
    private final Map<UUID, Long> outsideBorderSinceMs = new HashMap<>();
    private final List<BukkitTask> collapseTasks = new ArrayList<>();
    private final List<LogicalFloor> collapseFloors = new ArrayList<>();
    private final Map<BoulderQuarter, Integer> boulderQuarterNextTopY = new EnumMap<>(BoulderQuarter.class);
    private int collapseLayerIndex;
    private SpleefCollapsePattern activeCollapsePattern = SpleefCollapsePattern.OUTSIDE_IN;
    private final Map<UUID, Long> lastLassoUseMs = new HashMap<>();
    private final Map<UUID, BukkitTask> activeLassoPulls = new HashMap<>();
    private final Map<UUID, Cause> recentPlayerCauses = new HashMap<>();
    private final Map<BlockPos, Cause> recentTileCauses = new HashMap<>();
    private final Map<UUID, Location> frozenSpawnLocations = new HashMap<>();
    private final Map<BlockPos, DecayState> decayingTiles = new HashMap<>();
    private final Set<BlockPos> originalSnowTiles = new HashSet<>();
    private final Set<BlockPos> activeTiles = new HashSet<>();
    private final Set<UUID> alivePlayers = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> externalSpectators = new HashSet<>();
    private final List<UUID> managedEliminationOrder = new ArrayList<>();
    private final Map<UUID, Integer> managedEliminationCredits = new HashMap<>();
    private final Set<UUID> doubleJumpReady = new HashSet<>();
    private final Set<UUID> activePowerupEntities = new HashSet<>();
    private final Set<UUID> tutorialViewers = new HashSet<>();
    private final Map<UUID, BukkitTask> powerupParticleTasks = new HashMap<>();
    private final Map<UUID, Boolean> powerupLanded = new HashMap<>();
    private final Map<UUID, BukkitTask> activeBoomerangs = new HashMap<>();
    private final Map<UUID, Integer> boomerangActivationTicks = new HashMap<>();
    private final Map<UUID, Location> stopWatchAnchors = new HashMap<>();
    private final Map<UUID, BukkitTask> stopWatchTasks = new HashMap<>();
    private final Map<UUID, String> sidebarSignatures = new HashMap<>();
    private final Map<UUID, SpleefPlayerSnapshot> rejoinSnapshots = new HashMap<>();
    private final Map<UUID, SpleefPlayerSnapshot> reviveSnapshots = new HashMap<>();
    private final Map<UUID, UUID> reconnectZombieByPlayer = new HashMap<>();
    private final Map<UUID, UUID> reconnectPlayerByZombie = new HashMap<>();
    private final Map<UUID, Long> reconnectZombieExpiresAtMs = new HashMap<>();
    private final Map<SpleefMap, Path> mapFiles = new HashMap<>();
    private SpleefMap selectedMap = SpleefMap.RANDOM;
    private SpleefMap activeMap;
    private BukkitTask queueCountdownTask;
    private BukkitTask graceTask;
    private BukkitTask powerupTask;
    private BukkitTask powerupGrantTask;
    private BukkitTask borderShrinkTask;
    private BukkitTask cleanupTask;
    private BukkitTask matchDecayTask;
    private BukkitTask statusTask;
    private int matchTickCounter;
    private World activeWorld;
    private String activeWorldName;
    private boolean matchStarting;
    private boolean matchActive;
    private boolean graceActive;
    private int countdownSeconds;
    private int graceSeconds;
    private int nextWorldNumber = 1;
    private int matchPlayerCount;
    private int lowestPlayableTileY;
    private Bounds bounds;
    private int eliminationY;
    private ManagedMinigameRound.Request managedRound;
    private boolean managedCallbackSent;
    private ManagedStartFailure lastManagedStartFailure;
    private String startupFailureReason;

    public SpleefService(HGLootPlugin plugin) {
        this(plugin, null);
    }

    private SpleefService(HGLootPlugin plugin, SpleefService manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.matchInstance = manager != null;
        this.itemKey = new NamespacedKey(plugin, "spleef_item");
        this.projectileKey = new NamespacedKey(plugin, "spleef_projectile");
    }

    public void start() {
        if (matchInstance) {
            return;
        }
        floorDecayEnabled = plugin.getConfig().getBoolean("spleef.floor_decay_enabled", true);
        autoCleanupEnabled = plugin.getConfig().getBoolean("spleef.auto_cleanup", true);
        ensureBundledMaps();
        reloadMapFiles();
        statusTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpleefSidebars, 20L, 20L);
    }

    public void shutdown() {
        cancelTasks();
        if (!matchInstance) {
            for (SpleefService match : new ArrayList<>(activeMatches)) {
                match.stopMatch(Bukkit.getConsoleSender());
            }
            activeMatches.clear();
            activeMatchesByPlayer.clear();
            activeMatchesByWorld.clear();
            cleanupAllReconnectBodies();
            rejoinSnapshots.clear();
            reviveSnapshots.clear();
        }
        if (activeWorldName != null) {
            cleanupWorld(activeWorldName);
        }
        queuedPlayers.clear();
        cleanupState(false);
    }

    public boolean startTournamentRound(ManagedMinigameRound.Request request) {
        return startTournamentRound(request, Set.of());
    }

    public boolean startTournamentRound(ManagedMinigameRound.Request request, Set<String> excludedMapKeys) {
        lastManagedStartFailure = null;
        if (matchInstance || request == null || request.game() != ManagedMinigameRound.Game.SPLEEF) {
            return false;
        }
        List<UUID> participants = request.roster().stream()
            .filter(id -> !activeMatchesByPlayer.containsKey(id))
            .filter(id -> {
                Player player = Bukkit.getPlayer(id);
                return player != null && player.isOnline();
            })
            .limit(MAX_PLAYERS)
            .toList();
        if (participants.isEmpty()) {
            lastManagedStartFailure = new ManagedStartFailure("", "", "No online tournament participants were available.");
            return false;
        }
        SpleefService match = new SpleefService(plugin, this);
        match.managedRound = request;
        match.managedCallbackSent = false;
        SpleefMap tournamentMap = selectTournamentMap(excludedMapKeys);
        if (tournamentMap == null) {
            lastManagedStartFailure = new ManagedStartFailure("", "", "No untried Spleef map is available.");
            return false;
        }
        match.selectedMap = tournamentMap;
        match.mapFiles.putAll(mapFiles);
        match.queuedPlayers.addAll(participants);
        for (UUID id : participants) {
            activeMatchesByPlayer.put(id, match);
        }
        match.startMatch(true);
        if (!match.matchActive && !match.matchStarting) {
            for (UUID id : participants) {
                activeMatchesByPlayer.remove(id, match);
            }
            lastManagedStartFailure = new ManagedStartFailure(tournamentMap.key, tournamentMap.displayName(),
                match.startupFailureReason == null ? "Spleef failed before activation." : match.startupFailureReason);
            match.managedRound = null;
            return false;
        }
        registerMatch(match);
        return true;
    }

    public ManagedStartFailure consumeManagedStartFailure() {
        ManagedStartFailure failure = lastManagedStartFailure;
        lastManagedStartFailure = null;
        return failure;
    }

    private SpleefMap selectTournamentMap(Set<String> excludedMapKeys) {
        Set<String> excluded = excludedMapKeys == null ? Set.of() : excludedMapKeys;
        List<SpleefMap> candidates = SpleefMap.playable().stream()
            .filter(map -> !excluded.contains(map.key))
            .filter(map -> {
                Path path = mapFiles.get(map);
                return path != null && Files.isRegularFile(path);
            })
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public void stopTournamentRound(String reason) {
        if (matchInstance) {
            return;
        }
        for (SpleefService match : new ArrayList<>(activeMatches)) {
            if (match.managedRound != null) {
                match.stopManagedRound(reason);
            }
        }
    }

    public boolean hasRejoinSnapshot(Player player) {
        return player != null && snapshotOwner().rejoinSnapshots.containsKey(player.getUniqueId());
    }

    public boolean hasReviveSnapshot(Player player) {
        return player != null && snapshotOwner().reviveSnapshots.containsKey(player.getUniqueId());
    }

    public boolean rejoin(Player player, boolean automatic) {
        if (matchInstance && manager != null) {
            return manager.rejoin(player, automatic);
        }
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        SpleefService active = activeMatchesByPlayer.get(playerId);
        if (active != null && (active.alivePlayers.contains(playerId) || active.spectators.contains(playerId))) {
            if (!automatic) {
                player.sendMessage(ChatColor.YELLOW + "Already in a Spleef match.");
            }
            return false;
        }
        SpleefPlayerSnapshot snapshot = rejoinSnapshots.get(playerId);
        if (snapshot == null) {
            if (!automatic) {
                player.sendMessage(ChatColor.RED + "No Spleef match to rejoin.");
            }
            return false;
        }
        if (!snapshot.isRestorable()) {
            clearSnapshot(playerId);
            if (!automatic) {
                player.sendMessage(ChatColor.RED + "Spleef match no longer active.");
            }
            return false;
        }
        if (!automatic) {
            return plugin.transitionTravel(player, () -> {
                if (rejoinSnapshots.get(playerId) == snapshot && snapshot.isRestorable()) {
                    completeRejoin(player, snapshot, false);
                }
            });
        }
        return completeRejoin(player, snapshot, true);
    }

    private boolean completeRejoin(Player player, SpleefPlayerSnapshot snapshot, boolean automatic) {
        UUID playerId = player.getUniqueId();
        boolean restored = snapshot.spectator
            ? snapshot.match.restoreSpectatorSnapshot(player, snapshot)
            : snapshot.match.restoreLiveSnapshot(player, snapshot, false);
        if (!restored) {
            if (!automatic) {
                player.sendMessage(ChatColor.RED + "Spleef rejoin failed.");
            }
            return false;
        }
        rejoinSnapshots.remove(playerId);
        removeReconnectZombie(playerId);
        player.sendMessage(ChatColor.GREEN + (snapshot.spectator ? "Rejoined Spleef as spectator." : "Rejoined Spleef."));
        return true;
    }

    public boolean revive(CommandSender sender, Player target) {
        if (matchInstance && manager != null) {
            return manager.revive(sender, target);
        }
        if (target == null || !target.isOnline()) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
            }
            return false;
        }
        UUID targetId = target.getUniqueId();
        SpleefService active = activeMatchesByPlayer.get(targetId);
        if (active != null && active.alivePlayers.contains(targetId)) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + target.getName() + " is already alive in Spleef.");
            }
            return false;
        }
        SpleefPlayerSnapshot snapshot = reviveSnapshots.get(targetId);
        if (snapshot == null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "No Spleef revive snapshot is available for " + target.getName() + ".");
            }
            return false;
        }
        if (!snapshot.isRestorable()) {
            clearSnapshot(targetId);
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "That Spleef match is no longer active.");
            }
            return false;
        }
        if (!snapshot.match.restoreLiveSnapshot(target, snapshot, true)) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Could not revive " + target.getName() + " in Spleef.");
            }
            return false;
        }
        clearSnapshot(targetId);
        if (sender != null) {
            sender.sendMessage(ChatColor.GREEN + "Revived " + target.getName() + " in Spleef.");
        }
        return true;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!MinigameForceJoin.requireAdmin(sender)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("join")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can join Spleef.");
                return true;
            }
            join(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can leave Spleef.");
                    return true;
                }
                leave(player);
                return true;
            }
            case "status" -> {
                sendStatus(sender);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /spleef <join|leave|status>");
                return true;
            }
        }
    }

    public boolean handleAdminCommand(Player player, String[] args) {
        if (!canUseSpleefAdmin(player)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use Spleef admin tools.");
            return true;
        }
        if (args.length == 0) {
            plugin.menuService().openAdminSpleefToolsMenu(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("spleef")) {
            player.sendMessage(ChatColor.RED + "Usage: /admin minigames spleef <status|forcejoin|forcestart|stop|map|items>");
            return true;
        }
        if (args.length == 1) {
            plugin.menuService().openAdminSpleefToolsMenu(player);
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> sendStatus(player);
            case "forcestart" -> forceStart(player);
            case "forcejoin" -> MinigameForceJoin.execute(player, args, 2, "spleef", this::joinForAdmin,
                target -> queuedPlayers.contains(target.getUniqueId()));
            case "stop", "forceend" -> stopMatch(player);
            case "items" -> giveAdminTestItems(player);
            case "clearqueue" -> clearQueue(player);
            case "floordecay" -> toggleFloorDecay(player);
            case "cleanup" -> handleCleanupAdmin(player, args);
            case "map" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin minigames spleef map <base|rings|fourcorners|stars|desertclassic|desertrings|desertfourcorners|desertstars|desertlayers|snowylayers|random>");
                    return true;
                }
                selectMap(player, args[2]);
            }
            default -> player.sendMessage(ChatColor.RED + "Usage: /admin minigames spleef <status|forcejoin|forcestart|stop|forceend|map|items|clearqueue|floordecay|cleanup>");
        }
        return true;
    }

    public List<String> completeAdmin(String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("forcejoin")) {
            return MinigameForceJoin.complete(args[2]);
        }
        if (args.length <= 1) {
            return List.of("spleef");
        }
        if (!args[0].equalsIgnoreCase("spleef")) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(args[1], List.of("status", "forcejoin", "forcestart", "stop", "forceend", "map", "items", "clearqueue", "floordecay", "cleanup"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("map")) {
            return filter(args[2], MAP_KEYS);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("cleanup")) {
            return filter(args[2], List.of("on", "off", "status"));
        }
        return List.of();
    }

    private void handleCleanupAdmin(Player player, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "on" -> setAutoCleanup(player, true);
            case "off" -> setAutoCleanup(player, false);
            case "status" -> player.sendMessage(HYDRA_PREFIX + ChatColor.GRAY + "Spleef auto-cleanup: "
                + (autoCleanupEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            default -> player.sendMessage(ChatColor.RED + "Usage: /admin minigames spleef cleanup <on|off|status>");
        }
    }

    private void setAutoCleanup(Player player, boolean enabled) {
        if (autoCleanupEnabled == enabled) {
            player.sendMessage(HYDRA_PREFIX + ChatColor.YELLOW + "Spleef auto-cleanup is already "
                + (enabled ? "ON" : "OFF") + ".");
            return;
        }
        autoCleanupEnabled = enabled;
        plugin.getConfig().set("spleef.auto_cleanup", enabled);
        plugin.saveConfig();
        player.sendMessage(HYDRA_PREFIX + (enabled
            ? ChatColor.GREEN + "Spleef auto-cleanup ENABLED."
            : ChatColor.RED + "Spleef auto-cleanup DISABLED. Match worlds stay loaded for inspection."));
        plugin.getLogger().info("[Spleef] Auto-cleanup " + (enabled ? "ENABLED" : "DISABLED") + " by " + player.getName());
    }

    private boolean canUseSpleefAdmin(Player player) {
        return player != null
            && (player.hasPermission("hgloot.admin")
                || player.hasPermission("hgloot.admin.minigames")
                || player.hasPermission("hgloot.admin.spleef"));
    }

    public List<String> completeCommand(String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("join", "leave", "status"));
        }
        return List.of();
    }

    public void join(Player player) {
        if (!MinigameForceJoin.requireAdmin(player)) return;
        joinForAdmin(player);
    }

    private void joinForAdmin(Player player) {
        if (plugin.bossRushService() != null && plugin.bossRushService().ownsPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already committed to Boss Rush.");
            return;
        }
        if (plugin.minigameRouletteService() != null
            && plugin.minigameRouletteService().blockOtherModeEntry(player)) {
            return;
        }
        if (plugin.isModeLocked("spleef")) {
            player.sendMessage(HydraIcons.LOCKED + " " + ChatColor.RED + "Spleef is currently locked.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (plugin.queueService() != null
            && (plugin.queueService().isPlayerQueued(playerId) || plugin.queueService().isPlayerInMatch(playerId))) {
            player.sendMessage(HYDRA_PREFIX + ChatColor.RED + "Leave your current queue or match before joining Spleef.");
            return;
        }
        if (plugin.voidRouletteService() != null && plugin.voidRouletteService().isInVoidRoulette(playerId)) {
            player.sendMessage(HYDRA_PREFIX + ChatColor.RED + "Leave Void Roulette before joining Spleef.");
            return;
        }
        if (plugin.bridgeService() != null && plugin.bridgeService().isInBridge(playerId)) {
            player.sendMessage(HYDRA_PREFIX + ChatColor.RED + "Leave Bridge before joining Spleef.");
            return;
        }
        if (isInSpleef(playerId)) {
            player.sendMessage(HYDRA_PREFIX + ChatColor.YELLOW + "Already in Spleef.");
            return;
        }
        if (queuedPlayers.size() >= MAX_PLAYERS) {
            player.sendMessage(HYDRA_PREFIX + ChatColor.RED + "Spleef queue is full.");
            return;
        }
        queuedPlayers.add(playerId);
        if (!MinigameForceJoin.stageInGameHub(plugin, player)) {
            queuedPlayers.remove(playerId);
            return;
        }
        broadcastQueue(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.WHITE + player.getName()
            + ChatColor.GRAY + " Joined " + ChatColor.WHITE + "Spleef"
            + ChatColor.GRAY + " (" + ChatColor.WHITE + queuedPlayers.size() + "/" + MIN_PLAYERS
            + ChatColor.GRAY + ") Needed");
        HydraSounds.playSuccess(player);
        updateSpleefSidebar(player);
        if (queuedPlayers.size() >= MIN_PLAYERS) {
            startQueueCountdown(false);
        }
    }

    public boolean leave(Player player) {
        UUID playerId = player.getUniqueId();
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            if (match != null) {
                return match.leave(player);
            }
        }
        if (queuedPlayers.remove(playerId)) {
            player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "You left the queue.");
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
            player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "You left the match.");
            return true;
        }
        return false;
    }

    public boolean leaveToHub(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            if (match != null) {
                return match.leaveToHub(player);
            }
        }
        if (queuedPlayers.remove(playerId)) {
            player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "You left the queue.");
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
        cleanupPlayerForHub(player);
        player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Returned to hub.");
        if (wasAlive) {
            broadcastMatch(ChatColor.WHITE + player.getName() + ChatColor.GRAY + " left the match.");
            evaluateWin();
        }
        return true;
    }

    public boolean isInSpleef(UUID playerId) {
        if (!matchInstance) {
            return queuedPlayers.contains(playerId) || activeMatchesByPlayer.containsKey(playerId);
        }
        return queuedPlayers.contains(playerId) || alivePlayers.contains(playerId) || spectators.contains(playerId);
    }

    public boolean isQueuedForSpectatorCommand(UUID playerId) {
        return playerId != null && queuedPlayers.contains(playerId);
    }

    public boolean isInActiveSpleefMatch(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (!matchInstance) {
            return activeMatchesByPlayer.containsKey(playerId);
        }
        return alivePlayers.contains(playerId) || spectators.contains(playerId);
    }

    public boolean openRecipeGuide(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            if (match != null) {
                plugin.utilityBookService().openIndex(player);
                return true;
            }
            if (queuedPlayers.contains(playerId)) {
                plugin.utilityBookService().openIndex(player);
                return true;
            }
            return false;
        }
        if (!alivePlayers.contains(playerId) && !spectators.contains(playerId) && !queuedPlayers.contains(playerId)) {
            return false;
        }
        plugin.utilityBookService().openIndex(player);
        return true;
    }

    /** Preview the authoritative Spleef powerup catalog without granting items. */
    public List<ItemStack> utilityBookItems() {
        return Arrays.stream(Powerup.values()).map(this::createPowerupItem).toList();
    }

    public boolean isAliveSpleefPlayer(UUID playerId) {
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            return match != null && match.alivePlayers.contains(playerId);
        }
        return alivePlayers.contains(playerId);
    }

    public String playerAudienceKey(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            return match == null ? null : match.playerAudienceKey(player);
        }
        if (!alivePlayers.contains(playerId) && !spectators.contains(playerId)) {
            return null;
        }
        if (activeWorldName == null || player.getWorld() == null
            || !Objects.equals(activeWorldName, player.getWorld().getName())) {
            return null;
        }
        return "spleef:" + activeWorldName;
    }

    public boolean isAudienceSpectator(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            return match != null && match.spectators.contains(playerId);
        }
        return spectators.contains(playerId);
    }

    public boolean isAudienceAlive(UUID playerId) {
        return isAliveSpleefPlayer(playerId);
    }

    public boolean isSpleefInvisHidden(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(playerId);
            return match != null && match.invisRevealTasks.containsKey(playerId);
        }
        return invisRevealTasks.containsKey(playerId);
    }

    public boolean isMapSelected(String key) {
        SpleefMap map = SpleefMap.fromKey(key);
        return map != null && selectedMap == map;
    }

    public boolean spectateTargetMatch(Player spectator, Player target) {
        return spectateTargetMatch(spectator, target, spectatableMatchId(target));
    }

    public String spectatableMatchId(Player target) {
        if (target == null || !target.isOnline() || target.getWorld() == null) return null;
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(target.getUniqueId());
            return match == null ? null : match.spectatableMatchId(target);
        }
        UUID id = target.getUniqueId();
        return matchActive && activeWorldName != null && activeWorldName.equals(target.getWorld().getName())
            && (alivePlayers.contains(id) || spectators.contains(id)) ? "spleef:" + activeWorldName : null;
    }

    public boolean spectateTargetMatch(Player spectator, Player target, String expectedMatchId) {
        if (spectator == null || target == null || !spectator.isOnline() || !target.isOnline()) {
            return false;
        }
        if (expectedMatchId == null || !expectedMatchId.equals(spectatableMatchId(target))) return false;
        UUID spectatorId = spectator.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (!matchInstance) {
            SpleefService match = activeMatchesByPlayer.get(targetId);
            return match != null && match.spectateTargetMatch(spectator, target, expectedMatchId);
        }
        if (!matchActive || activeWorldName == null) {
            return false;
        }
        if (!alivePlayers.contains(targetId) && !spectators.contains(targetId)) {
            return false;
        }
        if (alivePlayers.contains(spectatorId) || spectators.contains(spectatorId)) {
            spectator.sendMessage(ChatColor.RED + "You are already in that Spleef match.");
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
            if (manager != null) manager.activeMatchesByPlayer.put(spectatorId, this);
            spectator.teleport(target.getLocation());
            configureSpectatorInventory(spectator);
            syncSpectatorVisibility();
            updateSpleefSidebar(spectator);
            spectator.sendMessage(ChatColor.GRAY + "Now spectating Spleef.");
            SpectatorRouteTrace.midpoint(plugin, spectator, target, expectedMatchId, true);
        });
    }

    public int queuedCount() {
        return queuedPlayers.size();
    }

    public int tabActivityCount() {
        if (matchInstance) {
            LinkedHashSet<UUID> players = new LinkedHashSet<>();
            players.addAll(queuedPlayers);
            players.addAll(alivePlayers);
            players.addAll(spectators);
            return players.size();
        }
        LinkedHashSet<UUID> players = new LinkedHashSet<>(queuedPlayers);
        players.addAll(activeMatchesByPlayer.keySet());
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
            return "In Progress";
        }
        if (!matchInstance && !activeMatches.isEmpty()) {
            return activeMatches.size() + " Active";
        }
        if (matchStarting || queueCountdownTask != null) {
            return "Starting";
        }
        return "Waiting";
    }

    public String selectedMapDisplayName() {
        return selectedMap.displayName();
    }

    public void clearQueue(CommandSender sender) {
        if (queuedPlayers.isEmpty()) {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.YELLOW + "Spleef queue is already empty.");
            return;
        }
        int cleared = queuedPlayers.size();
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(HYDRA_PREFIX + ChatColor.YELLOW + "The Spleef queue was cleared by an admin.");
                releaseQueuedPlayer(player);
            } else {
                queuedPlayers.remove(id);
            }
        }
        cancelQueueCountdown();
        sender.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Cleared " + cleared + " queued player(s).");
    }

    public void forceStart(CommandSender sender) {
        if (matchInstance && (matchActive || matchStarting)) {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.RED + "A Spleef match is already running.");
            return;
        }
        if (queuedPlayers.isEmpty() && sender instanceof Player player) {
            joinForAdmin(player);
        }
        if (queuedPlayers.isEmpty()) {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.RED + "No players are queued.");
            return;
        }
        startMatch(true);
        sender.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Force started Spleef.");
    }

    public void stopMatch(CommandSender sender) {
        if (matchInstance && managedRound != null) {
            stopManagedRound("Spleef was force-stopped.");
            sender.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Spleef stopped.");
            return;
        }
        if (!matchInstance) {
            if (activeMatches.isEmpty() && queuedPlayers.isEmpty()) {
                sender.sendMessage(HYDRA_PREFIX + ChatColor.YELLOW + "No active Spleef match found.");
                return;
            }
            for (SpleefService match : new ArrayList<>(activeMatches)) {
                match.stopMatch(sender);
            }
            for (UUID id : new ArrayList<>(queuedPlayers)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.sendMessage(ChatColor.YELLOW + "Spleef queue stopped.");
                    releaseQueuedPlayer(player);
                }
            }
            cancelQueueCountdown();
            queuedPlayers.clear();
            sender.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Spleef stopped.");
            return;
        }
        if (!matchActive && !matchStarting && queuedPlayers.isEmpty()) {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.YELLOW + "No active Spleef match found.");
            return;
        }
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(ChatColor.YELLOW + "Spleef queue stopped.");
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
        sender.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Spleef stopped.");
    }

    public void selectMap(CommandSender sender, String raw) {
        SpleefMap map = SpleefMap.fromKey(raw);
        if (map == null) {
            sender.sendMessage(ChatColor.RED + "Use map base, rings, fourcorners, stars, desertclassic, desertrings, desertfourcorners, desertstars, desertlayers, snowylayers, or random.");
            return;
        }
        selectedMap = map;
        sender.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Spleef map set to "
            + ChatColor.WHITE + map.displayName() + ChatColor.GREEN + ".");
        updateQueuedSidebars();
    }

    public void sendStatus(CommandSender sender) {
        sender.sendMessage(HYDRA_PREFIX + ChatColor.GRAY + "Spleef queue: "
            + ChatColor.WHITE + queuedPlayers.size() + "/" + MAX_PLAYERS);
        sender.sendMessage(HYDRA_PREFIX + ChatColor.GRAY + "Selected map: "
            + ChatColor.WHITE + selectedMap.displayName());
        if (matchActive || matchStarting) {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.GRAY + "Match: "
                + ChatColor.WHITE + activeMap.displayName() + ChatColor.DARK_GRAY + " - "
                + ChatColor.GRAY + "Alive: " + ChatColor.WHITE + alivePlayers.size());
        } else if (!matchInstance && !activeMatches.isEmpty()) {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.GRAY + "Active matches: "
                + ChatColor.WHITE + activeMatches.size());
            for (SpleefService match : activeMatches) {
                sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + match.activeWorldName
                    + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE
                    + (match.activeMap == null ? "Spleef" : match.activeMap.displayName())
                    + ChatColor.DARK_GRAY + " / " + ChatColor.GRAY + "Alive: "
                    + ChatColor.WHITE + match.alivePlayers.size());
            }
        } else {
            sender.sendMessage(HYDRA_PREFIX + ChatColor.GRAY + "Match: "
                + ChatColor.WHITE + "Idle");
        }
    }

    private void startQueueCountdown(boolean forced) {
        if (queueCountdownTask != null || matchActive || matchStarting) {
            return;
        }
        countdownSeconds = forced ? 3 : QUEUE_COUNTDOWN_SECONDS;
        queueCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queuedPlayers.size() < MIN_PLAYERS && !forced) {
                int neededPlayers = MIN_PLAYERS - queuedPlayers.size();
                broadcastQueue(ChatColor.YELLOW + "Need " + neededPlayers + " more "
                        + (neededPlayers == 1 ? "player" : "players") + ".");
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
                broadcastQueue(ChatColor.AQUA + "Spleef starts in " + ChatColor.WHITE + countdownSeconds + ChatColor.GRAY + "...");
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
        countdownSeconds = 0;
    }

    private void startMatch(boolean forced) {
        if (!matchInstance) {
            startManagedMatch(forced);
            return;
        }
        if (matchActive || matchStarting) {
            return;
        }
        if (queuedPlayers.isEmpty()) {
            startupFailureReason = "No queued tournament participants remained.";
            return;
        }
        matchStarting = true;
        List<UUID> participants = new ArrayList<>();
        Iterator<UUID> iterator = queuedPlayers.iterator();
        while (iterator.hasNext() && participants.size() < MAX_PLAYERS) {
            UUID id = iterator.next();
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                participants.add(id);
            }
            iterator.remove();
        }
        if (participants.isEmpty()) {
            startupFailureReason = "No online tournament participants remained.";
            matchStarting = false;
            return;
        }

        SpleefMap map = selectedMap == SpleefMap.RANDOM ? SpleefMap.randomPlayable() : selectedMap;
        Path mapPath = mapFiles.get(map);
        if (mapPath == null || !Files.isRegularFile(mapPath)) {
            startupFailureReason = "Map file is missing for " + map.displayName() + ".";
            broadcastParticipants(participants, ChatColor.RED + "Spleef map is not available: " + map.displayName());
            restoreParticipantsToHub(participants);
            matchStarting = false;
            return;
        }

        World world = createMatchWorld();
        if (world == null) {
            startupFailureReason = "Failed to create the Spleef world.";
            broadcastParticipants(participants, ChatColor.RED + "Failed to create Spleef world.");
            restoreParticipantsToHub(participants);
            matchStarting = false;
            return;
        }
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(mapPath.toFile());
        } catch (Exception ex) {
            startupFailureReason = "Failed loading " + map.displayName() + ": " + ex.getMessage();
            plugin.getLogger().warning("[Spleef] Failed loading map " + mapPath + ": " + ex.getMessage());
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            matchStarting = false;
            return;
        }
        if (structure == null || !placeStructureCentered(world, structure)) {
            startupFailureReason = "Failed placing " + map.displayName() + ".";
            plugin.getLogger().warning("[Spleef] Failed placing map " + map.displayName() + ".");
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            matchStarting = false;
            return;
        }

        activeWorld = world;
        activeWorldName = world.getName();
        activeMap = map;
        scanTiles(world);
        setupMatchBorder(world);
        List<BlockPos> spawnTiles = topSpawnTiles();
        if (spawnTiles.size() < participants.size()) {
            startupFailureReason = "Map has " + spawnTiles.size() + " safe center spawns for "
                + participants.size() + " participants.";
            broadcastParticipants(participants, ChatColor.RED + "Spleef map does not have enough safe center snow spawns.");
            restoreParticipantsToHub(participants);
            cleanupWorld(world.getName());
            cleanupState(false);
            return;
        }

        alivePlayers.addAll(participants);
        matchPlayerCount = participants.size();
        List<Location> spawnLocations = chooseSpawnLocations(spawnTiles, participants.size());
        int[] arrivals = {0};
        int expectedArrivals = (int) participants.stream()
            .map(Bukkit::getPlayer).filter(player -> player != null && player.isOnline()).count();
        boolean[] begun = {false};
        Runnable beginAfterArrival = () -> {
            if (begun[0] || !matchStarting || activeWorld != world) return;
            begun[0] = true;
            matchActive = true;
            graceActive = true;
            matchStarting = false;
            startMatchDecayTicker();
            startGraceCountdown();
            if (managedRound != null) {
                // Show the real contextual book once at the old instructional point.
                // Closing it is the player's choice; inventory refreshes never reopen it.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!matchActive || !graceActive || activeWorld != world || managedRound == null) return;
                    for (UUID id : participants) {
                        Player viewer = Bukkit.getPlayer(id);
                        if (viewer != null && viewer.isOnline() && alivePlayers.contains(id)) {
                            plugin.utilityBookService().openIndex(viewer);
                        }
                    }
                }, 1L);
            }
        };
        for (int i = 0; i < participants.size(); i++) {
            Player player = Bukkit.getPlayer(participants.get(i));
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (plugin.queueService() != null) {
                plugin.queueService().resetMatchRewardStats(player.getUniqueId());
            }
            Location spawn = spawnLocations.get(Math.min(i, spawnLocations.size() - 1));
            frozenSpawnLocations.put(player.getUniqueId(), spawn.clone());
            if (!plugin.transitionTravel(player, () -> {
                if (!matchStarting || activeWorld != world || !player.isOnline()) return;
                preparePlayer(player);
                player.teleport(spawn);
                player.sendMessage(ChatColor.AQUA + "Spleef map: " + ChatColor.WHITE + map.displayName());
                if (++arrivals[0] >= expectedArrivals) beginAfterArrival.run();
            })) {
                plugin.getLogger().warning("[Spleef] Entry fade already active for " + player.getName());
                restoreParticipantsToHub(participants);
                cleanupWorld(world.getName());
                cleanupState(false);
                return;
            }
        }
        if (expectedArrivals == 0) beginAfterArrival.run();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (matchStarting && activeWorld == world) beginAfterArrival.run();
        }, 20L);
    }

    private void startManagedMatch(boolean forced) {
        if (matchStarting || queuedPlayers.isEmpty()) {
            return;
        }
        matchStarting = true;
        List<UUID> participants = new ArrayList<>();
        Iterator<UUID> iterator = queuedPlayers.iterator();
        while (iterator.hasNext() && participants.size() < MAX_PLAYERS) {
            UUID id = iterator.next();
            Player player = Bukkit.getPlayer(id);
            iterator.remove();
            if (player != null && player.isOnline() && !activeMatchesByPlayer.containsKey(id)) {
                participants.add(id);
            }
        }
        if (participants.isEmpty()) {
            matchStarting = false;
            updateQueuedSidebars();
            return;
        }

        SpleefMap map = selectedMap == SpleefMap.RANDOM ? SpleefMap.randomPlayable() : selectedMap;
        SpleefService match = new SpleefService(plugin, this);
        match.selectedMap = map;
        match.mapFiles.putAll(mapFiles);
        match.queuedPlayers.addAll(participants);
        for (UUID id : participants) {
            activeMatchesByPlayer.put(id, match);
        }
        plugin.getLogger().info("[Spleef] Starting managed match map=" + map.displayName()
            + ", players=" + participants.size() + ", reserved=" + participants);
        match.startMatch(forced);
        if (match.matchActive || match.matchStarting) {
            registerMatch(match);
            for (UUID id : participants) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    updateSpleefSidebar(player);
                }
            }
        } else {
            for (UUID id : participants) {
                activeMatchesByPlayer.remove(id, match);
            }
            plugin.getLogger().warning("[Spleef] Managed match failed before activation map=" + map.displayName()
                + ", players=" + participants.size());
        }
        matchStarting = false;
        if (queuedPlayers.size() >= MIN_PLAYERS) {
            startQueueCountdown(false);
        } else {
            updateQueuedSidebars();
        }
    }

    private void restoreParticipantsToHub(List<UUID> participants) {
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                releaseQueuedPlayer(player);
            }
        }
    }

    private void releaseQueuedPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        queuedPlayers.remove(player.getUniqueId());
        sidebarSignatures.remove(player.getUniqueId());
        if (managedRound == null) plugin.queueService().returnPlayerToHydraHub(player);
        plugin.queueService().clearSidebarCache(player.getUniqueId());
        plugin.queueService().refreshSidebar(player);
    }

    private World createMatchWorld() {
        String worldName = "Spleef-World-" + allocateWorldNumber();
        while (Bukkit.getWorld(worldName) != null || Files.exists(Bukkit.getWorldContainer().toPath().resolve(worldName))) {
            worldName = "Spleef-World-" + allocateWorldNumber();
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new EmptySpleefChunkGenerator(96));
        creator.generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            return null;
        }
        world.setAutoSave(false);
        world.setTime(6000L);
        world.setPVP(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        return world;
    }

    private int allocateWorldNumber() {
        if (matchInstance && manager != null) {
            return manager.nextWorldNumber++;
        }
        return nextWorldNumber++;
    }

    private void registerMatch(SpleefService match) {
        if (match == null || match.activeWorldName == null) {
            return;
        }
        activeMatches.add(match);
        activeMatchesByWorld.put(match.activeWorldName, match);
        for (UUID id : match.alivePlayers) {
            activeMatchesByPlayer.put(id, match);
        }
        for (UUID id : match.spectators) {
            activeMatchesByPlayer.put(id, match);
        }
        plugin.getLogger().info("[Spleef] Registered match world=" + match.activeWorldName
            + ", map=" + (match.activeMap == null ? "unknown" : match.activeMap.displayName())
            + ", alive=" + match.alivePlayers.size() + ", spectators=" + match.spectators.size());
    }

    private void unregisterMatch(SpleefService match) {
        if (match == null) {
            return;
        }
        activeMatches.remove(match);
        if (match.activeWorldName != null) {
            activeMatchesByWorld.remove(match.activeWorldName, match);
        }
        for (UUID id : new ArrayList<>(activeMatchesByPlayer.keySet())) {
            activeMatchesByPlayer.remove(id, match);
        }
        plugin.getLogger().info("[Spleef] Unregistered match world="
            + (match.activeWorldName == null ? "unknown" : match.activeWorldName));
    }

    private SpleefService matchFor(Player player) {
        return player == null ? null : activeMatchesByPlayer.get(player.getUniqueId());
    }

    private SpleefService matchFor(World world) {
        return world == null ? null : activeMatchesByWorld.get(world.getName());
    }

    private boolean placeStructureCentered(World world, Structure structure) {
        BlockVector size = structure.getSize();
        int pasteX = -Math.floorDiv(Math.max(1, size.getBlockX()), 2);
        int pasteY = 80;
        int pasteZ = -Math.floorDiv(Math.max(1, size.getBlockZ()), 2);
        bounds = new Bounds(pasteX, pasteY, pasteZ, pasteX + size.getBlockX() - 1, pasteY + size.getBlockY() - 1, pasteZ + size.getBlockZ() - 1);
        try {
            structure.place(new Location(world, pasteX, pasteY, pasteZ), true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("[Spleef] Structure placement failed: " + ex.getMessage());
            return false;
        }
    }

    private void scanTiles(World world) {
        originalSnowTiles.clear();
        activeTiles.clear();
        boulderQuarterNextTopY.clear();
        int lowest = Integer.MAX_VALUE;
        if (bounds == null) {
            eliminationY = world.getMinHeight();
            lowestPlayableTileY = world.getMinHeight();
            return;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (BASE_TILE_TYPES.contains(block.getType())) {
                        BlockPos pos = BlockPos.of(block);
                        originalSnowTiles.add(pos);
                        activeTiles.add(pos);
                        lowest = Math.min(lowest, y);
                    }
                }
            }
        }
        if (isFixedLayerMap(activeMap)) {
            List<LogicalFloor> logicalFloors = activeMap.logicalFloors(activeCollapsePattern);
            LogicalFloor finalFloor = logicalFloors.getLast();
            lowestPlayableTileY = finalFloor.minY();
            eliminationY = lowestPlayableTileY - 8;
        } else {
            eliminationY = lowest == Integer.MAX_VALUE ? bounds.minY() - 8 : lowest - 8;
            lowestPlayableTileY = lowest == Integer.MAX_VALUE ? bounds.minY() : lowest;
        }
        collapseFloors.clear();
        collapseLayerIndex = 0;
        activeCollapsePattern = world == null
            ? SpleefCollapsePattern.OUTSIDE_IN
            : SpleefCollapsePattern.selectFor(world.getSeed() ^ world.getName().hashCode());
        if (isFixedLayerMap(activeMap)) {
            List<LogicalFloor> logicalFloors = activeMap.logicalFloors(activeCollapsePattern);
            collapseFloors.addAll(logicalFloors.subList(0, logicalFloors.size() - 1));
        } else if (isBoulderMap(activeMap)) {
            for (BlockPos pos : originalSnowTiles) {
                if (!isInsideCollapseRadius(pos)) {
                    continue;
                }
                BoulderQuarter quarter = BoulderQuarter.from(pos);
                boulderQuarterNextTopY.merge(quarter, pos.y(), Math::max);
            }
        } else {
            Set<Integer> layerSet = new HashSet<>();
            for (BlockPos pos : originalSnowTiles) {
                layerSet.add(pos.y());
            }
            List<Integer> layerYs = new ArrayList<>(layerSet);
            layerYs.sort(Collections.reverseOrder());
            for (int index = 0; index < Math.max(0, layerYs.size() - 1); index++) {
                collapseFloors.add(new LogicalFloor(List.of(layerYs.get(index)), SpleefCollapsePattern.OUTSIDE_IN));
            }
        }
    }

    private void setupMatchBorder(World world) {
        if (world == null || bounds == null) {
            return;
        }
        org.bukkit.WorldBorder border = world.getWorldBorder();
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0D + 0.5D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0D + 0.5D;
        double span = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) + 8.0D;
        border.setCenter(centerX, centerZ);
        border.setSize(span);
        border.setDamageAmount(0.0D);
        border.setWarningDistance(4);
    }

    private List<BlockPos> topSpawnTiles() {
        Map<String, BlockPos> highestByColumn = new HashMap<>();
        for (BlockPos pos : activeTiles) {
            Block block = blockAt(pos);
            if (isFixedLayerMap(activeMap) && !activeMap.logicalFloors(activeCollapsePattern).getFirst().containsY(pos.y())) {
                continue;
            }
            if (block == null || !originalSnowTiles.contains(pos) || !isInsideCenterArena(pos) || !isSafeSpawnTile(block)) {
                continue;
            }
            String column = pos.x() + ":" + pos.z();
            BlockPos existing = highestByColumn.get(column);
            if (existing == null || pos.y() > existing.y()) {
                highestByColumn.put(column, pos);
            }
        }
        List<BlockPos> tiles = new ArrayList<>(highestByColumn.values());
        Collections.shuffle(tiles);
        return tiles;
    }

    private boolean isInsideCenterArena(BlockPos pos) {
        if (pos == null || bounds == null) {
            return false;
        }
        int centerX = Math.floorDiv(bounds.minX() + bounds.maxX(), 2);
        int centerZ = Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2);
        return Math.abs(pos.x() - centerX) <= ARENA_CENTER_HALF_SIZE
            && Math.abs(pos.z() - centerZ) <= ARENA_CENTER_HALF_SIZE;
    }

    private boolean isFixedLayerMap(SpleefMap map) {
        return map == SpleefMap.BASE || map == SpleefMap.RINGS
            || map == SpleefMap.FOUR_CORNERS || map == SpleefMap.STARS
            || map == SpleefMap.DESERT_CLASSIC || map == SpleefMap.DESERT_RINGS
            || map == SpleefMap.DESERT_FOUR_CORNERS || map == SpleefMap.DESERT_STARS
            || map == SpleefMap.DESERT_LAYERS || map == SpleefMap.SNOWY_LAYERS;
    }

    private boolean isBoulderMap(SpleefMap map) {
        return map == SpleefMap.BOULDER || map == SpleefMap.DESERT_BOULDER;
    }

    private boolean isInsideCollapseRadius(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        int radius = isBoulderMap(activeMap) ? BOULDER_COLLAPSE_RADIUS : BASE_RINGS_COLLAPSE_RADIUS;
        return Math.abs(pos.x()) <= radius && Math.abs(pos.z()) <= radius;
    }

    private boolean isSafeSpawnTile(Block block) {
        if (block == null || !TILE_TYPES.contains(block.getType())) {
            return false;
        }
        Block feet = block.getRelative(0, 1, 0);
        Block head = block.getRelative(0, 2, 0);
        return (feet.getType().isAir() || feet.isPassable()) && (head.getType().isAir() || head.isPassable());
    }

    private List<Location> chooseSpawnLocations(List<BlockPos> candidates, int count) {
        List<BlockPos> chosen = new ArrayList<>();
        List<BlockPos> remaining = new ArrayList<>(candidates);
        while (!remaining.isEmpty() && chosen.size() < count) {
            BlockPos best;
            if (chosen.isEmpty()) {
                best = remaining.remove(0);
            } else {
                best = remaining.stream()
                    .max(java.util.Comparator.comparingDouble(candidate -> minDistanceSquared(candidate, chosen)))
                    .orElse(remaining.get(0));
                remaining.remove(best);
            }
            chosen.add(best);
        }
        if (chosen.isEmpty()) {
            chosen.add(candidates.get(0));
        }
        List<Location> locations = new ArrayList<>();
        for (BlockPos pos : chosen) {
            double spawnY = pos.y() + 1.05D;
            locations.add(new Location(activeWorld, pos.x() + 0.5D, spawnY, pos.z() + 0.5D));
        }
        return locations;
    }

    private double minDistanceSquared(BlockPos candidate, List<BlockPos> chosen) {
        double min = Double.MAX_VALUE;
        for (BlockPos pos : chosen) {
            double dx = candidate.x() - pos.x();
            double dz = candidate.z() - pos.z();
            min = Math.min(min, (dx * dx) + (dz * dz));
        }
        return min;
    }

    private void preparePlayer(Player player) {
        plugin.menuService().preparePlayerForMatch(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setHealth(Math.min(player.getMaxHealth(), 20.0D));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.closeInventory();
        player.setItemOnCursor(null);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[] {null, null, null, null});
        inventory.setItemInOffHand(null);
        inventory.setItem(0, createShovel(Material.STONE_SHOVEL));
        Powerup startingPowerup = Powerup.random();
        inventory.setItem(1, createPowerupItem(startingPowerup));
        heldPowerups.put(player.getUniqueId(), 1);
        player.sendMessage(ChatColor.GRAY + "Starting powerup: " + startingPowerup.color() + startingPowerup.displayName());
        player.updateInventory();
    }

    private ItemStack createShovel(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Spleef Shovel");
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.setItemModel(new NamespacedKey("hydra", material == Material.DIAMOND_SHOVEL
            ? "spleef_advanced_shovel"
            : "spleef_shovel"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "shovel");
        stack.setItemMeta(meta);
        return stack;
    }

    private void openTutorialMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, TUTORIAL_MENU_TITLE);
        fillTutorialFrame(inventory);
        inventory.setItem(20, createTutorialItem(Powerup.MEGA_SNOWBALL));
        inventory.setItem(22, createTutorialItem(Powerup.SUPER_FEATHER));
        inventory.setItem(24, createTutorialItem(Powerup.LASSO));
        inventory.setItem(30, createTutorialItem(Powerup.INVIS_ORB));
        inventory.setItem(32, createTutorialItem(Powerup.LEVITATION_ORB));
        inventory.setItem(34, createTutorialItem(Powerup.BOOMERANG));
        inventory.setItem(40, createTutorialItem(Powerup.STOP_WATCH));
        inventory.setItem(42, createTutorialItem(Powerup.GALE_MACE));
        tutorialViewers.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    private void fillTutorialFrame(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + " ");
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack createTutorialItem(Powerup powerup) {
        return createPowerupItem(powerup);
    }

    private boolean isTutorialMenu(String title) {
        return TUTORIAL_MENU_TITLE.equals(title);
    }

    private void closeTutorialMenus() {
        Set<UUID> viewers = new HashSet<>(tutorialViewers);
        tutorialViewers.clear();
        for (UUID id : viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline() && isTutorialMenu(player.getOpenInventory().getTitle())) {
                player.closeInventory();
            }
        }
    }

    private void startGraceCountdown() {
        graceSeconds = GRACE_SECONDS;
        graceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (graceSeconds <= 0) {
                graceActive = false;
                closeTutorialMenus();
                forAlive(player -> {
                    player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "GO!", ChatColor.GRAY + "Break the floor.", 0, 18, 8);
                    HydraSounds.playGo(player);
                });
                if (graceTask != null) {
                    graceTask.cancel();
                    graceTask = null;
                }
                frozenSpawnLocations.clear();
                scheduleNextPowerup(30);
                startPowerupGrants();
                scheduleLayerCollapse();
                scheduleBorderShrink();
                return;
            }
            if (graceSeconds == GRACE_SECONDS || graceSeconds <= 5) {
                int shown = graceSeconds;
                forAlive(player -> {
                    player.sendTitle(ChatColor.AQUA + "" + ChatColor.BOLD + shown, ChatColor.GRAY + "Spleef begins soon", 0, 18, 4);
                    HydraSounds.playCountdownTick(player, shown);
                });
            }
            graceSeconds--;
        }, 0L, 20L);
    }

    private void startMatchDecayTicker() {
        if (matchDecayTask != null) {
            matchDecayTask.cancel();
        }
        matchTickCounter = 0;
        matchDecayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || graceActive) {
                return;
            }
            forAlive(this::triggerDecayUnderPlayer);
            if (++matchTickCounter % 20 == 0) {
                forAlive(this::checkOutsideBorder);
            }
        }, 1L, 1L);
    }

    private void checkOutsideBorder(Player player) {
        if (player == null || activeWorld == null || !Objects.equals(player.getWorld().getName(), activeWorldName)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (activeWorld.getWorldBorder().isInside(player.getLocation())) {
            outsideBorderSinceMs.remove(playerId);
            return;
        }
        long now = System.currentTimeMillis();
        Long since = outsideBorderSinceMs.putIfAbsent(playerId, now);
        if (since == null) {
            player.sendActionBar(Component.text(ChatColor.RED + "Return to the arena!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 0.6F);
            return;
        }
        if (now - since >= OUTSIDE_BORDER_GRACE_MS) {
            outsideBorderSinceMs.remove(playerId);
            eliminate(player, null, "was caught outside the border");
        } else {
            player.sendActionBar(Component.text(ChatColor.RED + "Return to the arena!"));
        }
    }

    private void startPowerupGrants() {
        if (powerupGrantTask != null) {
            powerupGrantTask.cancel();
        }
        powerupGrantTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive || graceActive) {
                return;
            }
            forAlive(this::grantPowerupTo);
        }, POWERUP_GRANT_SECONDS * 20L, POWERUP_GRANT_SECONDS * 20L);
    }

    private void grantPowerupTo(Player player) {
        UUID playerId = player.getUniqueId();
        int held = heldPowerups.getOrDefault(playerId, 0);
        if (held >= MAX_HELD_POWERUPS) {
            return;
        }
        Powerup powerup = Powerup.random();
        if (!player.getInventory().addItem(createPowerupItem(powerup)).isEmpty()) {
            return;
        }
        heldPowerups.put(playerId, held + 1);
        player.sendActionBar(Component.text(ChatColor.AQUA + "Powerup gained: " + powerup.color() + powerup.displayName()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
    }

    private void scheduleLayerCollapse() {
        if (!matchActive || (isBoulderMap(activeMap) ? boulderQuarterNextTopY.isEmpty() : collapseFloors.isEmpty())) {
            return;
        }
        collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin, this::collapseNextLayer, LAYER_COLLAPSE_FIRST_DELAY_SECONDS * 20L));
    }

    private void collapseNextLayer() {
        if (!matchActive || bounds == null) {
            return;
        }
        if (isBoulderMap(activeMap)) {
            if (collapseBoulderQuarterBand()) {
                collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin,
                    this::collapseNextLayer, LAYER_COLLAPSE_NEXT_LAYER_SECONDS * 20L));
            }
            return;
        }
        if (collapseLayerIndex >= collapseFloors.size()) {
            return;
        }
        LogicalFloor floor = collapseFloors.get(collapseLayerIndex++);
        collapseSection(floor, 0);
        collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin,
            () -> collapseSection(floor, 1), LAYER_COLLAPSE_SECOND_HALF_DELAY_SECONDS * 20L));
        if (collapseLayerIndex < collapseFloors.size()) {
            collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin,
                this::collapseNextLayer, LAYER_COLLAPSE_NEXT_LAYER_SECONDS * 20L));
        }
    }

    private void collapseSection(LogicalFloor floor, int section) {
        if (!matchActive || bounds == null) {
            return;
        }
        int centerX = Math.floorDiv(bounds.minX() + bounds.maxX(), 2);
        int centerZ = Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2);
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : activeTiles) {
            if (!originalSnowTiles.contains(pos) || !isInsideCollapseRadius(pos)) {
                continue;
            }
            if (!floor.containsY(pos.y())) {
                continue;
            }
            if (floor.pattern().section(pos.x(), pos.z(), centerX, centerZ) != section) {
                continue;
            }
            Block block = blockAt(pos);
            if (block != null && isActiveTile(block, pos)) {
                targets.add(pos);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        warnAndCollapseTargets(targets, ChatColor.RED + "A section of the floor is collapsing!");
    }

    private boolean collapseBoulderQuarterBand() {
        if (!matchActive || bounds == null || !isBoulderMap(activeMap)) {
            return false;
        }
        int attempts = 0;
        while (!boulderQuarterNextTopY.isEmpty() && attempts++ < 256) {
            List<BoulderQuarter> available = new ArrayList<>(boulderQuarterNextTopY.keySet());
            BoulderQuarter quarter = available.get(ThreadLocalRandom.current().nextInt(available.size()));
            Integer topYValue = boulderQuarterNextTopY.get(quarter);
            if (topYValue == null || topYValue < bounds.minY()) {
                boulderQuarterNextTopY.remove(quarter);
                continue;
            }
            int topY = topYValue;
            int bottomY = topY - BOULDER_COLLAPSE_BAND_HEIGHT + 1;
            boulderQuarterNextTopY.put(quarter, topY - BOULDER_COLLAPSE_BAND_HEIGHT);

            List<BlockPos> targets = new ArrayList<>();
            for (BlockPos pos : activeTiles) {
                if (!originalSnowTiles.contains(pos)
                    || !isInsideCollapseRadius(pos)
                    || BoulderQuarter.from(pos) != quarter
                    || pos.y() > topY
                    || pos.y() < bottomY) {
                    continue;
                }
                Block block = blockAt(pos);
                if (block != null && isActiveTile(block, pos)) {
                    targets.add(pos);
                }
            }
            if (!targets.isEmpty()) {
                warnAndCollapseTargets(targets, ChatColor.RED + "The " + quarter.displayName() + " Boulder section is collapsing!");
                return true;
            }
        }
        return false;
    }

    private void warnAndCollapseTargets(List<BlockPos> targets, String broadcastMessage) {
        if (targets.isEmpty()) {
            return;
        }
        broadcastMatch(broadcastMessage);
        forAlive(player -> {
            player.sendActionBar(Component.text(ChatColor.RED + "Floor collapse incoming!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.6F);
        });
        for (BlockPos pos : targets) {
            setStage(pos, DecayStage.STAGE_1);
        }
        collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!matchActive) {
                return;
            }
            for (BlockPos pos : targets) {
                setStage(pos, DecayStage.STAGE_2);
            }
        }, 60L));
        collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!matchActive) {
                return;
            }
            for (BlockPos pos : targets) {
                setStage(pos, DecayStage.STAGE_3);
            }
        }, 86L));
        collapseTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!matchActive) {
                return;
            }
            for (BlockPos pos : targets) {
                Block block = blockAt(pos);
                if (block != null && isActiveTile(block, pos)) {
                    breakTile(block, null, false);
                }
            }
            if (activeWorld != null && !targets.isEmpty()) {
                BlockPos sample = targets.get(targets.size() / 2);
                activeWorld.playSound(new Location(activeWorld, sample.x(), sample.y(), sample.z()),
                    Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 0.8F);
            }
        }, LAYER_COLLAPSE_BREAK_TICKS));
    }

    private void scheduleBorderShrink() {
        if (!matchActive || activeWorld == null) {
            return;
        }
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
        }
        borderShrinkTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!matchActive || activeWorld == null) {
                return;
            }
            broadcastMatch(ChatColor.RED + "The border is shrinking! Move toward the middle.");
            forAlive(player -> player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.7F));
            activeWorld.getWorldBorder().changeSize(BORDER_FINAL_SIZE, BORDER_SHRINK_DURATION_SECONDS * 20L);
        }, BORDER_SHRINK_DELAY_SECONDS * 20L);
    }

    private void applyInvisOrb(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask previous = invisRevealTasks.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVIS_ORB_TICKS, 0, false, false, false));
        plugin.menuService().setHeadCosmeticTemporarilyHidden(player, true);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.3D, 0.5D, 0.3D, 0.02D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9F, 1.2F);
        player.sendActionBar(Component.text(ChatColor.LIGHT_PURPLE + "You vanished for 3 seconds."));
        invisRevealTasks.put(playerId, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            invisRevealTasks.remove(playerId);
            plugin.menuService().setHeadCosmeticTemporarilyHidden(player, false);
            if (!player.isOnline() || !alivePlayers.contains(playerId)) {
                return;
            }
            syncSpectatorVisibility();
            player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.3D, 0.5D, 0.3D, 0.02D);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9F, 0.8F);
            player.sendActionBar(Component.text(ChatColor.GRAY + "You are visible again."));
        }, INVIS_ORB_TICKS));
        syncSpectatorVisibility();
    }

    private void applyLevitationOrb(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, LEVITATION_ORB_TICKS, LEVITATION_ORB_AMPLIFIER, false, false, false));
        player.setFallDistance(0.0F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 0.8D, 0.0D), 28, 0.35D, 0.55D, 0.35D, 0.02D);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 1.4F);
        player.sendActionBar(Component.text(ChatColor.LIGHT_PURPLE + "Levitation Orb!"));
    }

    private void throwBoomerang(Player player, double curveDirection) {
        Location start = boomerangAnchor(player);
        Vector forward = horizontalForward(player);
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        Location farPoint = start.clone().add(forward.clone().multiply(BOOMERANG_FORWARD_BLOCKS));

        ItemDisplay display = (ItemDisplay) activeWorld.spawnEntity(start, EntityType.ITEM_DISPLAY);
        display.setItemStack(createPowerupItem(Powerup.BOOMERANG));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        updateBoomerangRotation(display, 0);
        UUID displayId = display.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            private int tick;
            private Location previous = start.clone();

            @Override
            public void run() {
                if (!display.isValid() || !matchActive || !alivePlayers.contains(player.getUniqueId())
                    || !player.isOnline() || !Objects.equals(player.getWorld().getName(), activeWorldName)) {
                    finish();
                    return;
                }
                tick++;
                Location next;
                if (tick <= BOOMERANG_OUTBOUND_TICKS) {
                    Vector offset = boomerangOutgoingOffset(
                        tick / (double) BOOMERANG_OUTBOUND_TICKS, curveDirection);
                    next = start.clone()
                        .add(side.clone().multiply(offset.getX()))
                        .add(forward.clone().multiply(offset.getZ()));
                } else {
                    double progress = Math.min(1.0D,
                        (tick - BOOMERANG_OUTBOUND_TICKS) / (double) BOOMERANG_RETURN_TICKS);
                    Location target = boomerangAnchor(player);
                    Location returnControl = farPoint.clone()
                        .add(side.clone().multiply(BOOMERANG_SIDE_BLOCKS * 2.0D * curveDirection));
                    next = quadraticPoint(farPoint, returnControl, target, progress);
                }
                if (processBoomerangSegment(previous, next, player)) {
                    finish();
                    return;
                }
                display.teleport(next);
                updateBoomerangRotation(display, tick);
                previous = next;
                if (tick >= BOOMERANG_OUTBOUND_TICKS + BOOMERANG_RETURN_TICKS) finish();
            }

            private void finish() {
                cancel();
                activeBoomerangs.remove(displayId);
                if (display.isValid()) display.remove();
            }
        }.runTaskTimer(plugin, 1L, 1L);
        activeBoomerangs.put(displayId, task);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.7F, 1.25F);
    }

    private static Location boomerangAnchor(Player player) {
        return player.getLocation().clone().add(0.0D, 0.35D, 0.0D);
    }

    private Vector horizontalForward(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0.0D);
        if (forward.lengthSquared() < 0.0001D) {
            double yaw = Math.toRadians(player.getLocation().getYaw());
            forward.setX(-Math.sin(yaw)).setZ(Math.cos(yaw));
        }
        return forward.normalize();
    }

    static Vector boomerangOutgoingOffset(double progress, double curveDirection) {
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        double forward = BOOMERANG_FORWARD_BLOCKS * t;
        double side = 4.0D * BOOMERANG_SIDE_BLOCKS * t * (1.0D - t) * curveDirection;
        return new Vector(side, 0.0D, forward);
    }

    private static Location quadraticPoint(Location start, Location control, Location end, double progress) {
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        double inverse = 1.0D - t;
        return new Location(
            start.getWorld(),
            (inverse * inverse * start.getX()) + (2.0D * inverse * t * control.getX()) + (t * t * end.getX()),
            (inverse * inverse * start.getY()) + (2.0D * inverse * t * control.getY()) + (t * t * end.getY()),
            (inverse * inverse * start.getZ()) + (2.0D * inverse * t * control.getZ()) + (t * t * end.getZ())
        );
    }

    private boolean processBoomerangSegment(Location from, Location to, Player owner) {
        Vector delta = to.toVector().subtract(from.toVector());
        int samples = Math.max(1, (int) Math.ceil(delta.length() / BOOMERANG_SAMPLE_STEP));
        Set<BlockPos> visited = new HashSet<>();
        for (int index = 1; index <= samples; index++) {
            Location sample = from.clone().add(delta.clone().multiply(index / (double) samples));
            boolean ownerSafe = isInsideBoomerangSafetyRadius(sample, owner);
            Block occupied = sample.getBlock();
            Block support = occupied.getRelative(0, -1, 0);
            if (touchBoomerangBlock(support, owner.getUniqueId(), visited, ownerSafe)) return true;
            if (touchBoomerangBlock(occupied, owner.getUniqueId(), visited, ownerSafe)) return true;
        }
        return false;
    }

    static boolean isInsideBoomerangSafetyRadius(Location boomerang, Player owner) {
        if (boomerang == null || owner == null || owner.getWorld() == null
            || !Objects.equals(boomerang.getWorld(), owner.getWorld())) {
            return false;
        }
        return boomerang.distanceSquared(boomerangAnchor(owner))
            <= BOOMERANG_OWNER_SAFETY_RADIUS * BOOMERANG_OWNER_SAFETY_RADIUS;
    }

    private boolean touchBoomerangBlock(Block block, UUID causeId, Set<BlockPos> visited, boolean ownerSafe) {
        if (block == null || block.getType().isAir()) return false;
        BlockPos pos = BlockPos.of(block);
        if (!visited.add(pos)) return false;
        if (isActiveTile(block, pos)) {
            if (!ownerSafe) {
                breakTile(block, causeId, true);
            }
            return false;
        }
        return !block.isPassable();
    }

    private void updateBoomerangRotation(ItemDisplay display, int tick) {
        float angle = (float) Math.toRadians((tick * 54) % 360);
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf().rotateZ(angle),
            new Vector3f(0.85F, 0.85F, 0.85F),
            new Quaternionf()
        ));
    }

    private void activateStopWatch(Player player) {
        UUID playerId = player.getUniqueId();
        finishStopWatch(playerId, false);
        stopWatchAnchors.put(playerId, player.getLocation().clone());
        player.setGravity(false);
        player.setVelocity(new Vector());
        player.setFallDistance(0.0F);
        showStopWatchCountdown(player, 5);

        BukkitTask task = new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!matchActive || !alivePlayers.contains(playerId) || !player.isOnline()
                    || !Objects.equals(player.getWorld().getName(), activeWorldName)) {
                    finishStopWatch(playerId, false);
                    return;
                }
                elapsedTicks++;
                player.setVelocity(new Vector());
                player.setFallDistance(0.0F);
                if (elapsedTicks >= STOP_WATCH_TICKS) {
                    finishStopWatch(playerId, true);
                    return;
                }
                if (elapsedTicks % 20 == 0) {
                    showStopWatchCountdown(player, 5 - (elapsedTicks / 20));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        stopWatchTasks.put(playerId, task);
    }

    private void showStopWatchCountdown(Player player, int secondsRemaining) {
        player.sendActionBar(Component.text(ChatColor.GOLD + "Stop Watch " + ChatColor.WHITE + secondsRemaining + "s"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 0.9F + ((5 - secondsRemaining) * 0.05F));
    }

    private void finishStopWatch(UUID playerId, boolean completed) {
        stopWatchAnchors.remove(playerId);
        BukkitTask task = stopWatchTasks.remove(playerId);
        if (task != null) task.cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        player.setGravity(true);
        player.setVelocity(new Vector());
        player.setFallDistance(0.0F);
        if (completed && player.isOnline()) {
            player.sendActionBar(Component.text(ChatColor.GREEN + "Time resumes."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9F, 1.25F);
        }
    }

    private void cleanupStopWatches() {
        Set<UUID> frozenPlayers = new HashSet<>(stopWatchAnchors.keySet());
        frozenPlayers.addAll(stopWatchTasks.keySet());
        for (UUID playerId : frozenPlayers) {
            finishStopWatch(playerId, false);
        }
    }

    private void triggerDecayUnderPlayer(Player player) {
        if (player == null || !player.isOnline() || activeWorldName == null) {
            return;
        }
        Location location = player.getLocation();
        if (location.getWorld() == null || !Objects.equals(location.getWorld().getName(), activeWorldName)) {
            return;
        }
        for (Block block : footSupportBlocks(player)) {
            BlockPos pos = BlockPos.of(block);
            if (isActiveTile(block, pos)) {
                if (floorDecayEnabled) {
                    startDecay(block, player.getUniqueId());
                }
            }
        }
    }

    public boolean isFloorDecayEnabled() {
        return floorDecayEnabled;
    }

    public boolean toggleFloorDecay(CommandSender sender) {
        floorDecayEnabled = !floorDecayEnabled;
        plugin.getConfig().set("spleef.floor_decay_enabled", floorDecayEnabled);
        plugin.saveConfig();
        sender.sendMessage(HYDRA_PREFIX + (floorDecayEnabled ? ChatColor.GREEN : ChatColor.RED)
            + "Spleef floor decay " + (floorDecayEnabled ? "ENABLED" : "DISABLED") + ChatColor.GRAY
            + (floorDecayEnabled ? " — the floor decays under players again." : " — the floor only breaks from shovels/snowballs."));
        return floorDecayEnabled;
    }

    private List<Block> footSupportBlocks(Player player) {
        Location base = player.getLocation();
        double y = base.getY() - 0.2D;
        double[][] offsets = {
            {0.0D, 0.0D},
            {0.42D, 0.0D},
            {-0.42D, 0.0D},
            {0.0D, 0.42D},
            {0.0D, -0.42D}
        };
        List<Block> blocks = new ArrayList<>(offsets.length);
        Set<BlockPos> seen = new HashSet<>();
        for (double[] offset : offsets) {
            Location sample = new Location(base.getWorld(), base.getX() + offset[0], y, base.getZ() + offset[1]);
            Block block = supportTileForSample(sample.getBlock());
            if (block != null && seen.add(BlockPos.of(block))) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private Block supportTileForSample(Block sampled) {
        if (sampled == null) {
            return null;
        }
        BlockPos sampledPos = BlockPos.of(sampled);
        if (isActiveTile(sampled, sampledPos)) {
            return sampled;
        }
        Block below = sampled.getRelative(0, -1, 0);
        BlockPos belowPos = BlockPos.of(below);
        return isActiveTile(below, belowPos) ? below : sampled;
    }

    private void tickSpleefSidebars() {
        updateQueuedSidebars();
        if (!matchInstance) {
            for (SpleefService match : new ArrayList<>(activeMatches)) {
                match.tickSpleefSidebars();
            }
            return;
        }
        if (!matchActive && !matchStarting) {
            return;
        }
        forAlive(this::updateSpleefSidebar);
        for (UUID id : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                updateSpleefSidebar(player);
            }
        }
    }

    private void updateQueuedSidebars() {
        for (UUID id : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                updateSpleefSidebar(player);
            }
        }
    }

    private void updateSpleefSidebar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        List<String> lines = new ArrayList<>();
        if (queuedPlayers.contains(playerId) && !matchActive && !matchStarting) {
            lines.add(sidebarSystem("Players", queuedPlayers.size() + "/" + MAX_PLAYERS, ChatColor.YELLOW));
            lines.add(sidebarSystem("Needed", String.valueOf(MIN_PLAYERS), ChatColor.WHITE));
            lines.add(sidebarSeparator(0));
            lines.add(sidebarSystem("Map", selectedMap.displayName(), ChatColor.AQUA));
            lines.add(sidebarSystem("Status", queueCountdownTask == null ? "Waiting" : "Starting", queueCountdownTask == null ? ChatColor.GRAY : ChatColor.YELLOW));
            lines.add(ChatColor.AQUA + "HydraMC.net");
            applySpleefSidebar(player, lines);
            return;
        }
        if (!alivePlayers.contains(playerId) && !spectators.contains(playerId)) {
            return;
        }
        boolean alive = alivePlayers.contains(playerId);
        lines.add(sidebarSystem("Alive", alivePlayers.size() + "/" + Math.max(matchPlayerCount, 1), ChatColor.WHITE));
        lines.add(sidebarSystem("Status", !alive ? "Spectating" : graceActive ? "Preparing" : "Playing", ChatColor.WHITE));
        if (alive && graceActive) lines.add(sidebarSystem("Starts", Math.max(0, graceSeconds) + "s", ChatColor.AQUA));
        lines.add(sidebarSeparator(0));
        if (alive) {
            if (hasDiamondShovel(player)) {
                lines.add(sidebarSystem("Shovel", "Diamond", ChatColor.AQUA));
            } else {
                int breaks = Math.min(SHOVEL_UPGRADE_BREAKS, shovelBreaks.getOrDefault(playerId, 0));
                lines.add(sidebarSystem("Shovel", breaks + "/" + SHOVEL_UPGRADE_BREAKS, ChatColor.WHITE));
            }
            lines.add(sidebarSystem("Powerups", heldPowerups.getOrDefault(playerId, 0) + "/2", ChatColor.WHITE));
        }
        RouletteTeam tournamentTeam = plugin.minigameRouletteService() == null
            ? null
            : plugin.minigameRouletteService().teamOf(playerId);
        if (tournamentTeam != null) {
            lines.add(sidebarSystem("Your Team", tournamentTeam.displayName(), tournamentTeam.color()));
        }
        lines.add(ChatColor.AQUA + "HydraMC.net");
        applySpleefSidebar(player, lines);
    }

    private void applySpleefSidebar(Player player, List<String> lines) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        String signature = String.join("\n", lines);
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
            Component.text(ChatColor.AQUA + "" + ChatColor.BOLD + "SPLEEF")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
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
        ChatColor[] colors = ChatColor.values();
        ChatColor color = colors[Math.floorMod(variant, colors.length)];
        return color.toString() + ChatColor.RESET;
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            if (match != null) {
                match.onBlockDamage(event);
            }
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId()) || graceActive) {
            return;
        }
        if (stopWatchAnchors.containsKey(player.getUniqueId())
            && isSpleefShovel(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        BlockPos pos = BlockPos.of(block);
        if (isActiveTile(block, pos)
            && STAGE_TILE_TYPES.contains(block.getType())
            && isSpleefShovel(player.getInventory().getItemInMainHand())) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            if (match != null) {
                match.onBlockBreak(event);
            }
            return;
        }
        if (spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (stopWatchAnchors.containsKey(player.getUniqueId())
            && isSpleefShovel(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId())) {
            return;
        }
        event.setDropItems(false);
        if (graceActive) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
            return;
        }
        Block block = event.getBlock();
        BlockPos pos = BlockPos.of(block);
        if (!isActiveTile(block, pos)) {
            event.setCancelled(true);
            return;
        }
        if (!isSpleefShovel(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ChatColor.RED + "Use your Spleef Shovel."));
            return;
        }
        event.setCancelled(false);
        block.setType(Material.AIR, false);
        removeTileState(pos, player.getUniqueId());
        playTileBreakEffects(block);
        incrementShovelBreak(player);
        maybeGiveSnowball(player);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!matchInstance) {
            SpleefService match = matchFor(event.getPlayer());
            if (match != null || queuedPlayers.contains(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (isInSpleef(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            if (graceActive) {
                event.getPlayer().sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!matchInstance) {
            if (event.getWhoClicked() instanceof Player player && isInSpleef(player.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player && isInSpleef(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!matchInstance) {
            if (isInSpleef(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (isInSpleef(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!matchInstance) {
            SpleefService match = matchFor(event.getPlayer());
            if (match != null) {
                match.onSwapHands(event);
            }
            return;
        }
        if (spectators.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            if (match != null) {
                match.onMove(event);
            }
            return;
        }
        Location to = event.getTo();
        if (spectators.contains(playerId)) {
            if (to != null && to.getWorld() != null && Objects.equals(to.getWorld().getName(), activeWorldName)
                && to.getBlockY() <= eliminationY) {
                Location safe = spectatorReturnLocation();
                if (safe != null) {
                    event.setTo(safe);
                    player.setFallDistance(0.0F);
                }
            }
            return;
        }
        if (!alivePlayers.contains(playerId)) {
            return;
        }
        Location stopWatchAnchor = stopWatchAnchors.get(playerId);
        if (stopWatchAnchor != null) {
            if (to != null) {
                Location locked = stopWatchAnchor.clone();
                locked.setYaw(to.getYaw());
                locked.setPitch(to.getPitch());
                event.setTo(locked);
            } else {
                event.setCancelled(true);
            }
            player.setVelocity(new Vector());
            player.setFallDistance(0.0F);
            return;
        }
        if (to == null || to.getWorld() == null || !Objects.equals(to.getWorld().getName(), activeWorldName)) {
            return;
        }
        if (graceActive) {
            Location frozen = frozenSpawnLocations.get(playerId);
            if (frozen != null) {
                Location locked = frozen.clone();
                locked.setYaw(to.getYaw());
                locked.setPitch(to.getPitch());
                event.setTo(locked);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        if ((event.getFrom().getY() > to.getY() || player.getFallDistance() > 0.0F)
            && validCause(recentPlayerCauses.get(playerId)) == null) {
            Cause tileCause = resolveFallCause(to);
            if (tileCause != null && !tileCause.playerId().equals(playerId)) {
                recentPlayerCauses.put(playerId, tileCause);
            }
        }
        if (to.getBlockY() <= eliminationY) {
            eliminate(player, validCause(recentPlayerCauses.get(playerId)), "fell");
            return;
        }
        if (to.getBlock().getType() == Material.WATER || to.getBlock().getType() == Material.BUBBLE_COLUMN) {
            eliminate(player, null, "hit the water");
            return;
        }
        if (player.isOnGround() && player.getFallDistance() <= 0.0F) {
            recentPlayerCauses.remove(playerId);
        }
        if (!matchActive) {
            return;
        }
        triggerDecayUnderPlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            if (match != null) match.onVelocity(event);
            return;
        }
        if (stopWatchAnchors.containsKey(player.getUniqueId())) {
            event.setVelocity(new Vector());
        }
    }

    private Location spectatorReturnLocation() {
        for (UUID aliveId : alivePlayers) {
            Player alive = Bukkit.getPlayer(aliveId);
            if (alive != null && alive.isOnline() && alive.getWorld().equals(activeWorld)) {
                return alive.getLocation().clone().add(0.0D, 4.0D, 0.0D);
            }
        }
        if (activeWorld == null) {
            return null;
        }
        return new Location(activeWorld, 0.5D, Math.max(PLAYER_SPAWN_Y + 5.0D, eliminationY + 10.0D), 0.5D, 0.0F, 0.0F);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!matchInstance) {
            if (event.getEntity() instanceof Player player && isInSpleef(player.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getEntity() instanceof Player player && isInSpleef(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrePlayerAttack(PrePlayerAttackEntityEvent event) {
        Player attacker = event.getPlayer();
        if (!matchInstance) {
            SpleefService match = matchFor(attacker);
            if (match != null) {
                match.onPrePlayerAttack(event);
            }
            return;
        }
        if (event.isCancelled() || !event.willAttack() || !(event.getAttacked() instanceof Player target)) {
            return;
        }
        if (tryActivateGaleMace(attacker, target)) {
            // Activate before cancelling so zero effective damage cannot suppress the powerup.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!matchInstance) {
            SpleefService match = null;
            if (event.getEntity() instanceof Player victim) {
                match = matchFor(victim);
            }
            if (match == null && event.getDamager() instanceof Player attacker) {
                match = matchFor(attacker);
            }
            if (match != null) {
                match.onDamageByEntity(event);
            }
            return;
        }
        if (event.getEntity() instanceof Player victim && isInSpleef(victim.getUniqueId())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Snowball snowball && snowball.getShooter() instanceof Player shooter) {
                registerCause(victim.getUniqueId(), shooter.getUniqueId(), "snowball");
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!matchInstance) {
            SpleefService match = matchFor(event.getEntity().getWorld());
            if (match != null) {
                match.onProjectileHit(event);
            }
            return;
        }
        if (!(event.getEntity() instanceof Snowball snowball) || !(snowball.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!isInActiveWorld(snowball.getWorld()) || !alivePlayers.contains(shooter.getUniqueId()) || graceActive) {
            return;
        }
        String projectileType = snowball.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING);
        if (event.getHitEntity() instanceof Player target && alivePlayers.contains(target.getUniqueId())) {
            if ("mega_snowball".equals(projectileType)) {
                applyKnockback(shooter, target, 1.45D);
                registerCause(target.getUniqueId(), shooter.getUniqueId(), "mega snowball");
            }
            return;
        }
        Block hit = event.getHitBlock();
        if (hit == null) {
            return;
        }
        BlockPos pos = BlockPos.of(hit);
        if (!isActiveTile(hit, pos)) {
            return;
        }
        if ("mega_snowball".equals(projectileType)) {
            breakTileArea(hit, shooter.getUniqueId(), 1);
        } else {
            breakTile(hit, shooter.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            if (match != null) {
                match.onInteract(event);
            }
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId())) {
            if (spectators.contains(player.getUniqueId())) {
                handleSpectatorInteract(event);
            }
            return;
        }
        if (graceActive) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
            return;
        }
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR
            || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        hand = boomerangInteractionHand(hand, rightClick, player.getInventory().getItemInOffHand());
        if (hand == EquipmentSlot.OFF_HAND && event.getHand() == EquipmentSlot.HAND) {
            item = player.getInventory().getItemInOffHand();
        }
        String type = spleefItemType(item);
        boolean frozen = stopWatchAnchors.containsKey(player.getUniqueId());
        if (frozen && "shovel".equals(type)) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null
            && event.getAction() != Action.LEFT_CLICK_BLOCK
            && isActiveTile(clickedBlock, BlockPos.of(clickedBlock))) {
            event.setCancelled(true);
            if (frozen && item != null && item.getType() == Material.SNOWBALL && type == null) {
                // Keep the floor non-interactable without suppressing a vanilla snowball throw.
                event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
            }
        }
        if (type == null) {
            return;
        }
        switch (type) {
            case "lasso" -> {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                event.setCancelled(true);
                Player target = rayTraceLassoTarget(player);
                if (target == null || !pullWithLasso(player, target, item, hand)) {
                    player.sendActionBar(Component.text(ChatColor.RED + "Lasso failed."));
                }
            }
            case "mega_snowball" -> {
                event.setCancelled(true);
                // Mega Snowball uses Material.SNOWBALL, so vanilla would also throw/consume one
                // after this event resolves. Explicitly deny the vanilla item use so only our
                // tagged projectile is launched and consumeOne removes exactly one from the stack.
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                Snowball snowball = player.launchProjectile(Snowball.class);
                snowball.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, "mega_snowball");
                consumeOne(player, item, hand);
                decrementHeldPowerups(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.9F, 0.65F);
            }
            case "super_feather" -> {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                event.setCancelled(true);
                consumeOne(player, item, hand);
                decrementHeldPowerups(player.getUniqueId());
                // Launch immediately on right-click so it can't be stored as a held double-jump.
                Vector launch = player.getLocation().getDirection().setY(0.0D);
                if (launch.lengthSquared() > 0.0001D) {
                    launch.normalize().multiply(0.45D);
                }
                launch.setY(0.95D);
                player.setVelocity(launch);
                player.setFallDistance(0.0F);
                player.sendActionBar(Component.text(ChatColor.AQUA + "Super Feather!"));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9F, 1.45F);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 24, 0.4D, 0.2D, 0.4D, 0.03D);
            }
            case "invis_orb" -> {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                event.setCancelled(true);
                consumeOne(player, item, hand);
                decrementHeldPowerups(player.getUniqueId());
                applyInvisOrb(player);
            }
            case "levitation_orb" -> {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                event.setCancelled(true);
                consumeOne(player, item, hand);
                decrementHeldPowerups(player.getUniqueId());
                applyLevitationOrb(player);
            }
            case "boomerang" -> {
                boolean left = event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK;
                boolean right = event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK;
                if (!left && !right) return;
                event.setCancelled(true);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                if (!claimBoomerangActivation(player.getUniqueId(), player.getTicksLived())) return;
                consumeOne(player, item, hand);
                decrementHeldPowerups(player.getUniqueId());
                throwBoomerang(player, left ? -1.0D : 1.0D);
            }
            case "stop_watch" -> {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                event.setCancelled(true);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                consumeOne(player, item, hand);
                decrementHeldPowerups(player.getUniqueId());
                activateStopWatch(player);
            }
            default -> {
            }
        }
    }

    EquipmentSlot boomerangInteractionHand(EquipmentSlot eventHand, boolean rightClick, ItemStack offhand) {
        // Main-hand events arrive first; only an offhand Boomerang redirects the click.
        return eventHand == EquipmentSlot.HAND && rightClick && "boomerang".equals(spleefItemType(offhand))
            ? EquipmentSlot.OFF_HAND : eventHand;
    }

    boolean claimBoomerangActivation(UUID playerId, int playerTick) {
        if (boomerangActivationTicks.getOrDefault(playerId, -1) == playerTick) return false;
        boomerangActivationTicks.put(playerId, playerTick);
        return true;
    }

    private void handleSpectatorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        Player spectator = event.getPlayer();
        String action = spleefItemType(event.getItem());
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

    @EventHandler
    public void onSpectatorInventoryClick(InventoryClickEvent event) {
        if (isTutorialMenu(event.getView().getTitle())) {
            event.setCancelled(true);
            return;
        }
        if (!matchInstance) {
            if (event.getWhoClicked() instanceof Player player) {
                SpleefService match = matchFor(player);
                if (match != null) {
                    match.onSpectatorInventoryClick(event);
                }
            }
            return;
        }
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
        String action = spleefItemType(event.getCurrentItem());
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
        if (isTutorialMenu(event.getView().getTitle())) {
            event.setCancelled(true);
            return;
        }
        if (!matchInstance) {
            if (event.getWhoClicked() instanceof Player player) {
                SpleefService match = matchFor(player);
                if (match != null) {
                    match.onSpectatorInventoryDrag(event);
                }
            }
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!spectators.contains(player.getUniqueId()) && !isSpectatorTeleportMenu(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onTutorialInventoryClose(InventoryCloseEvent event) {
        if (!isTutorialMenu(event.getView().getTitle())) {
            return;
        }
        if (!matchInstance) {
            if (event.getPlayer() instanceof Player player) {
                SpleefService match = matchFor(player);
                if (match != null) {
                    match.onTutorialInventoryClose(event);
                    return;
                }
                tutorialViewers.remove(player.getUniqueId());
            }
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!tutorialViewers.contains(playerId) || !graceActive || !alivePlayers.contains(playerId)) {
            tutorialViewers.remove(playerId);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (graceActive && alivePlayers.contains(playerId) && player.isOnline()) {
                openTutorialMenu(player);
            }
        });
    }

    private boolean isSpectatorTeleportMenu(String title) {
        return SPECTATOR_MENU_TITLE.equals(title);
    }

    private void openSpectatorTeleportMenu(Player spectator) {
        if (spectator == null || !spectator.isOnline() || !spectators.contains(spectator.getUniqueId())) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, SPECTATOR_MENU_TITLE);
        int slot = 0;
        for (UUID id : alivePlayers) {
            Player target = Bukkit.getPlayer(id);
            if (target != null && target.isOnline() && Objects.equals(target.getWorld().getName(), activeWorldName)) {
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
                    SPECTATOR_ACTION_TARGET_PREFIX + id
                );
                head.setItemMeta(meta);
                inventory.setItem(slot++, head);
            }
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

    private void syncSpectatorVisibility() {
        if (activeWorldName == null) {
            return;
        }
        World world = Bukkit.getWorld(activeWorldName);
        if (world == null) {
            return;
        }
        List<Player> alive = new ArrayList<>();
        List<Player> spectating = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            UUID id = player.getUniqueId();
            if (alivePlayers.contains(id)) {
                alive.add(player);
            } else if (spectators.contains(id)) {
                spectating.add(player);
            }
        }
        for (Player alivePlayer : alive) {
            for (Player otherAlive : alive) {
                if (!alivePlayer.equals(otherAlive) && invisRevealTasks.containsKey(otherAlive.getUniqueId())) {
                    alivePlayer.hidePlayer(plugin, otherAlive);
                } else {
                    alivePlayer.showPlayer(plugin, otherAlive);
                }
            }
            for (Player spectator : spectating) {
                alivePlayer.hidePlayer(plugin, spectator);
                if (invisRevealTasks.containsKey(alivePlayer.getUniqueId())) {
                    spectator.hidePlayer(plugin, alivePlayer);
                } else {
                    spectator.showPlayer(plugin, alivePlayer);
                }
            }
        }
        for (Player spectator : spectating) {
            for (Player otherSpectator : spectating) {
                PlayerSettingsService.syncSpectatorPairOrDefault(plugin, spectator, otherSpectator);
            }
        }
    }

    public void refreshSpectatorVisibilityPreference(UUID playerId) {
        SpleefService match = matchInstance ? this : activeMatchesByPlayer.get(playerId);
        if (match != null) match.syncSpectatorVisibility();
    }

    public boolean isLobbyTimeContext(UUID playerId) {
        if (queuedPlayers.contains(playerId)) return true;
        SpleefService match = matchInstance ? this : activeMatchesByPlayer.get(playerId);
        return match != null && match.graceActive
            && (match.alivePlayers.contains(playerId) || match.spectators.contains(playerId));
    }

    private Player rayTraceLassoTarget(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            LASSO_RANGE_BLOCKS,
            0.35D,
            entity -> entity instanceof Player target
                && !target.getUniqueId().equals(player.getUniqueId())
                && alivePlayers.contains(target.getUniqueId())
                && Objects.equals(target.getWorld().getName(), activeWorldName)
        );
        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            return null;
        }
        if (!player.hasLineOfSight(target)) {
            return null;
        }
        return target;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!matchInstance) {
            SpleefService match = matchFor(event.getPlayer());
            if (match != null) {
                match.onInteractEntity(event);
            }
            return;
        }
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (!alivePlayers.contains(player.getUniqueId()) || !alivePlayers.contains(target.getUniqueId())) {
            return;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (!"lasso".equals(spleefItemType(item))) {
            return;
        }
        event.setCancelled(true);
        if (graceActive) {
            player.sendActionBar(Component.text(ChatColor.RED + "Wait for GO!"));
            return;
        }
        if (!pullWithLasso(player, target, item, hand)) {
            player.sendActionBar(Component.text(ChatColor.RED + "Lasso failed."));
        }
    }

    private boolean pullWithLasso(Player player, Player target, ItemStack item, EquipmentSlot hand) {
        if (player == null || target == null || item == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long lastUse = lastLassoUseMs.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < 250L) {
            return true;
        }
        if (!alivePlayers.contains(player.getUniqueId()) || !alivePlayers.contains(target.getUniqueId())) {
            return false;
        }
        if (plugin.teamProtectionService().areProtectedTeammates(player, target)) return false;
        if (!Objects.equals(player.getWorld().getName(), activeWorldName)
            || !Objects.equals(target.getWorld().getName(), activeWorldName)) {
            return false;
        }
        if (player.getLocation().distanceSquared(target.getLocation())
            > LASSO_RANGE_BLOCKS * LASSO_RANGE_BLOCKS || !player.hasLineOfSight(target)) {
            return false;
        }
        Location start = target.getLocation().clone();
        Location destination = player.getLocation().clone();
        if (start.distanceSquared(destination) <= 0.01D) {
            return false;
        }
        startLassoArc(player, target, start, destination);
        lastLassoUseMs.put(player.getUniqueId(), now);
        registerCause(target.getUniqueId(), player.getUniqueId(), "lasso");
        consumeOne(player, item, hand);
        decrementHeldPowerups(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8F, 1.1F);
        target.playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8F, 0.85F);
        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0D, 0.9D, 0.0D), 12, 0.25D, 0.35D, 0.25D, 0.01D);
        return true;
    }

    private void startLassoArc(Player user, Player target, Location start, Location destination) {
        BukkitTask previous = activeLassoPulls.remove(target.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        target.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        final int[] elapsed = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!matchActive
                || !user.isOnline()
                || !target.isOnline()
                || !alivePlayers.contains(user.getUniqueId())
                || !alivePlayers.contains(target.getUniqueId())
                || !Objects.equals(user.getWorld(), target.getWorld())
                || !Objects.equals(target.getWorld().getName(), activeWorldName)) {
                cancelLassoPull(target.getUniqueId());
                return;
            }
            elapsed[0]++;
            double progress = Math.min(1.0D, elapsed[0] / (double) LASSO_ARC_TICKS);
            Location next = lassoArcPoint(start, destination, progress);
            Vector error = next.toVector().subtract(target.getLocation().toVector());
            if (progress >= 1.0D && error.lengthSquared() <= 0.09D) {
                target.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                cancelLassoPull(target.getUniqueId());
                return;
            }
            Vector velocity = lassoPullVelocity(error, target.getVelocity());
            if (!isLassoMovementClear(target, velocity)) {
                target.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                cancelLassoPull(target.getUniqueId());
                return;
            }
            target.setVelocity(velocity);
            target.setFallDistance(0.0F);
            if (elapsed[0] >= LASSO_ARC_TICKS + 8) {
                target.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                cancelLassoPull(target.getUniqueId());
            }
        }, 0L, 1L);
        activeLassoPulls.put(target.getUniqueId(), task);
    }

    static Vector lassoPullVelocity(Vector error, Vector currentVelocity) {
        Vector velocity = error.clone().multiply(0.8D).add(currentVelocity.clone().multiply(0.2D));
        double speed = velocity.length();
        return speed > 1.2D ? velocity.multiply(1.2D / speed) : velocity;
    }

    private boolean isLassoMovementClear(Player target, Vector velocity) {
        int steps = Math.max(1, (int) Math.ceil(velocity.length() * 4.0D));
        org.bukkit.util.BoundingBox bounds = target.getBoundingBox();
        for (int step = 1; step <= steps; step++) {
            org.bukkit.util.BoundingBox moved = bounds.clone().shift(velocity.clone().multiply(step / (double) steps));
            for (int x = (int) Math.floor(moved.getMinX() + 0.001D); x <= Math.floor(moved.getMaxX() - 0.001D); x++) {
                for (int y = (int) Math.floor(moved.getMinY() + 0.001D); y <= Math.floor(moved.getMaxY() - 0.001D); y++) {
                    for (int z = (int) Math.floor(moved.getMinZ() + 0.001D); z <= Math.floor(moved.getMaxZ() - 0.001D); z++) {
                        if (target.getWorld().getBlockAt(x, y, z).getType().isSolid()) return false;
                    }
                }
            }
        }
        return true;
    }

    static Location lassoArcPoint(Location start, Location destination, double progress) {
        if (start == null || destination == null) {
            return null;
        }
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        double inverse = 1.0D - t;
        double controlX = (start.getX() + destination.getX()) * 0.5D;
        double controlY = Math.max(start.getY(), destination.getY()) + LASSO_ARC_LIFT_BLOCKS;
        double controlZ = (start.getZ() + destination.getZ()) * 0.5D;
        return new Location(start.getWorld(),
            (inverse * inverse * start.getX()) + (2.0D * inverse * t * controlX) + (t * t * destination.getX()),
            (inverse * inverse * start.getY()) + (2.0D * inverse * t * controlY) + (t * t * destination.getY()),
            (inverse * inverse * start.getZ()) + (2.0D * inverse * t * controlZ) + (t * t * destination.getZ()),
            start.getYaw(), start.getPitch());
    }

    private void cancelLassoPull(UUID targetId) {
        BukkitTask task = activeLassoPulls.remove(targetId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cleanupLassoPulls() {
        for (BukkitTask task : activeLassoPulls.values()) {
            task.cancel();
        }
        activeLassoPulls.clear();
    }

    private boolean tryActivateGaleMace(Player player, Player target) {
        if (!matchActive || graceActive || player == null || target == null
            || player.equals(target)
            || !alivePlayers.contains(player.getUniqueId())
            || !alivePlayers.contains(target.getUniqueId())
            || !Objects.equals(player.getWorld().getName(), activeWorldName)
            || !Objects.equals(target.getWorld().getName(), activeWorldName)
            || plugin.teamProtectionService().areProtectedTeammates(player, target)) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!"gale_mace".equals(spleefItemType(item))) {
            return false;
        }
        consumeOne(player, item, EquipmentSlot.HAND);
        decrementHeldPowerups(player.getUniqueId());
        player.setVelocity(new Vector(0.0D, GALE_MACE_WIELDER_LAUNCH_VELOCITY, 0.0D));
        target.setVelocity(new Vector(0.0D, GALE_MACE_TARGET_LAUNCH_VELOCITY, 0.0D));
        player.setFallDistance(0.0F);
        target.setFallDistance(0.0F);
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation().add(0.0D, 0.35D, 0.0D),
            10, 0.35D, 0.15D, 0.35D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.9F, 1.1F);
        player.sendActionBar(Component.text(ChatColor.AQUA + "Gale Mace!"));
        return true;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            if (match != null) {
                match.onToggleFlight(event);
            }
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!doubleJumpReady.remove(playerId)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        Vector velocity = player.getLocation().getDirection().setY(0.85D).normalize().multiply(0.85D);
        velocity.setY(0.82D);
        player.setVelocity(velocity);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 24, 0.4D, 0.2D, 0.4D, 0.03D);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9F, 1.45F);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!matchInstance) {
            if (event.getEntity() instanceof Player player) {
                SpleefService match = matchFor(player);
                if (match != null) {
                    match.onPickup(event);
                }
            }
            return;
        }
        if (event.getEntity() instanceof Player player && spectators.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !alivePlayers.contains(player.getUniqueId())) {
            return;
        }
        Item itemEntity = event.getItem();
        String type = itemEntity.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (type == null || !type.startsWith("powerup:")) {
            return;
        }
        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        int held = heldPowerups.getOrDefault(playerId, 0);
        if (held >= MAX_HELD_POWERUPS) {
            player.sendActionBar(Component.text(ChatColor.RED + "You can only hold " + MAX_HELD_POWERUPS + " powerups."));
            return;
        }
        ItemStack stack = itemEntity.getItemStack().clone();
        stack.setAmount(1);
        if (player.getInventory().addItem(stack).isEmpty()) {
            heldPowerups.put(playerId, held + 1);
            removePowerupEntity(itemEntity);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.35F);
            player.sendActionBar(Component.text(ChatColor.AQUA + cleanPowerupName(type.substring("powerup:".length()))));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (matchInstance) {
            return;
        }
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
        UUID playerId = player.getUniqueId();
        if (!matchInstance) {
            queuedPlayers.remove(playerId);
            resetSidebarToHub(player);
            SpleefService match = activeMatchesByPlayer.get(playerId);
            if (match != null) {
                match.onQuit(event);
            }
            return;
        }
        queuedPlayers.remove(playerId);
        finishStopWatch(playerId, false);
        if (alivePlayers.contains(playerId)) {
            SpleefPlayerSnapshot snapshot = captureSnapshot(player, false);
            if (snapshot != null) {
                snapshotOwner().rejoinSnapshots.put(playerId, snapshot);
                spawnReconnectZombie(player, snapshot);
                return;
            }
            eliminate(player, null, "left the match");
        }
        if (spectators.contains(playerId)) {
            SpleefPlayerSnapshot snapshot = captureSnapshot(player, true);
            if (snapshot != null) {
                snapshotOwner().rejoinSnapshots.put(playerId, snapshot);
            }
            spectators.remove(playerId);
            syncSpectatorVisibility();
        }
        if (matchInstance && manager != null) {
            manager.activeMatchesByPlayer.remove(playerId, this);
        }
        if (plugin.queueService() != null) {
            plugin.queueService().resetMatchRewardStats(playerId);
        }
        heldPowerups.remove(playerId);
        doubleJumpReady.remove(playerId);
        lastLassoUseMs.remove(playerId);
        cancelLassoPull(playerId);
        totalBreaks.remove(playerId);
        outsideBorderSinceMs.remove(playerId);
        BukkitTask invisTask = invisRevealTasks.remove(playerId);
        if (invisTask != null) {
            invisTask.cancel();
        }
        plugin.menuService().setHeadCosmeticTemporarilyHidden(player, false);
    }

    private void incrementShovelBreak(Player player) {
        UUID playerId = player.getUniqueId();
        int currentBreaks = Math.min(SHOVEL_UPGRADE_BREAKS, shovelBreaks.getOrDefault(playerId, 0));
        if (currentBreaks >= SHOVEL_UPGRADE_BREAKS) {
            shovelBreaks.put(playerId, SHOVEL_UPGRADE_BREAKS);
            ensureSingleUpgradedShovel(player);
            return;
        }
        int breaks = currentBreaks + 1;
        shovelBreaks.put(playerId, breaks);
        if (breaks == SHOVEL_UPGRADE_BREAKS) {
            replaceSpleefShovels(player, Material.DIAMOND_SHOVEL);
            player.sendTitle(ChatColor.AQUA + "" + ChatColor.BOLD + "SHOVEL UPGRADED", ChatColor.GRAY + "Diamond shovel unlocked", 5, 30, 8);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.2F);
        }
    }

    private void ensureSingleUpgradedShovel(Player player) {
        ItemStack slotZero = player.getInventory().getItem(0);
        if (countSpleefShovels(player) == 1
            && slotZero != null
            && slotZero.getType() == Material.DIAMOND_SHOVEL
            && isSpleefShovel(slotZero)) {
            return;
        }
        replaceSpleefShovels(player, Material.DIAMOND_SHOVEL);
    }

    private int countSpleefShovels(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSpleefShovel(item)) {
                count += item.getAmount();
            }
        }
        if (isSpleefShovel(player.getItemOnCursor())) {
            count += player.getItemOnCursor().getAmount();
        }
        return count;
    }

    private void replaceSpleefShovels(Player player, Material material) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isSpleefShovel(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        if (isSpleefShovel(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        inventory.setItem(0, createShovel(material));
        player.updateInventory();
    }

    private void maybeGiveSnowball(Player player) {
        int breaks = totalBreaks.merge(player.getUniqueId(), 1, Integer::sum);
        if (breaks % SNOWBALL_EVERY_BREAKS != 0) {
            return;
        }
        player.getInventory().addItem(new ItemStack(Material.SNOWBALL));
        player.sendActionBar(Component.text(ChatColor.AQUA + "Snowball!"));
        player.playSound(player.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.7F, 1.5F);
    }

    private boolean hasDiamondShovel(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND_SHOVEL && isSpleefShovel(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpleefShovel(ItemStack item) {
        return "shovel".equals(spleefItemType(item));
    }

    private String spleefItemType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private void startDecay(Block block, UUID causeId) {
        BlockPos pos = BlockPos.of(block);
        if (decayingTiles.containsKey(pos)) {
            return;
        }
        DecayState state = new DecayState(pos, block.getType(), causeId, new ArrayList<>());
        decayingTiles.put(pos, state);
        setStage(pos, DecayStage.STAGE_1);
        state.tasks().add(Bukkit.getScheduler().runTaskLater(plugin, () -> setStage(pos, DecayStage.STAGE_2), 60L));
        state.tasks().add(Bukkit.getScheduler().runTaskLater(plugin, () -> setStage(pos, DecayStage.STAGE_3), 86L));
        state.tasks().add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block current = blockAt(pos);
            if (current != null && isActiveTile(current, pos)) {
                breakTile(current, causeId, true);
            }
        }, 100L));
    }

    private void setStage(BlockPos pos, DecayStage stage) {
        Block block = blockAt(pos);
        if (block == null || !activeTiles.contains(pos)) {
            return;
        }
        if (TILE_TYPES.contains(block.getType())) {
            block.setBlockData(stage.blockData(), false);
        }
    }

    private boolean isActiveTile(Block block, BlockPos pos) {
        return block != null && activeTiles.contains(pos) && TILE_TYPES.contains(block.getType());
    }

    private void breakTile(Block block, UUID causeId, boolean effects) {
        BlockPos pos = BlockPos.of(block);
        if (!removeTileState(pos, causeId)) {
            return;
        }
        block.setType(Material.AIR, false);
        if (effects) {
            playTileBreakEffects(block);
        }
    }

    private boolean removeTileState(BlockPos pos, UUID causeId) {
        if (!activeTiles.remove(pos)) {
            return false;
        }
        DecayState state = decayingTiles.remove(pos);
        if (state != null) {
            for (BukkitTask task : state.tasks()) {
                task.cancel();
            }
        }
        if (causeId != null) {
            recentTileCauses.put(pos, new Cause(causeId, System.currentTimeMillis(), "tile"));
        }
        return true;
    }

    private void playTileBreakEffects(Block block) {
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.75F, 1.0F);
        block.getWorld().spawnParticle(Particle.SNOWFLAKE, block.getLocation().add(0.5D, 0.7D, 0.5D), 8, 0.25D, 0.12D, 0.25D, 0.01D);
    }

    private void breakTileArea(Block center, UUID causeId, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    BlockPos pos = BlockPos.of(block);
                    if (isActiveTile(block, pos)) {
                        breakTile(block, causeId, false);
                    }
                }
            }
        }
        center.getWorld().playSound(center.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.55F, 1.6F);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.getLocation().add(0.5D, 0.5D, 0.5D), 40, 1.2D, 0.2D, 1.2D, 0.03D);
    }

    private void scheduleNextPowerup(int delaySeconds) {
        if (!matchActive) {
            return;
        }
        if (powerupTask != null) {
            powerupTask.cancel();
        }
        powerupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnPowerup();
            int next = ThreadLocalRandom.current().nextInt(25, 41);
            scheduleNextPowerup(next);
        }, delaySeconds * 20L);
    }

    private void spawnPowerup() {
        if (!matchActive || activeWorld == null || activePowerupEntities.size() >= 5 || originalSnowTiles.isEmpty()) {
            return;
        }
        List<BlockPos> candidates = new ArrayList<>(originalSnowTiles);
        Collections.shuffle(candidates);
        for (BlockPos pos : candidates) {
            if (!activeTiles.contains(pos) || decayingTiles.containsKey(pos) || !isInsideCenterArena(pos)) {
                continue;
            }
            Block block = blockAt(pos);
            if (block == null || !BASE_TILE_TYPES.contains(block.getType()) || !isActiveTile(block, pos) || block.getRelative(0, 1, 0).isLiquid()) {
                continue;
            }
            Location drop = new Location(activeWorld, pos.x() + 0.5D, pos.y() + 8.0D, pos.z() + 0.5D);
            Location landedBeamBase = new Location(activeWorld, pos.x() + 0.5D, pos.y() + 1.1D, pos.z() + 0.5D);
            Powerup powerup = Powerup.random();
            Item entity = activeWorld.dropItem(drop, createPowerupItem(powerup));
            entity.setCanMobPickup(false);
            entity.setCanPlayerPickup(true);
            entity.setUnlimitedLifetime(false);
            entity.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "powerup:" + powerup.key());
            activePowerupEntities.add(entity.getUniqueId());
            powerupLanded.put(entity.getUniqueId(), false);
            BukkitTask particles = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!entity.isValid()) {
                    removePowerupEntity(entity);
                    return;
                }
                Location itemLocation = entity.getLocation();
                boolean landed = powerupLanded.getOrDefault(entity.getUniqueId(), false);
                if (!landed) {
                    entity.getWorld().spawnParticle(Particle.END_ROD, itemLocation.clone().add(0.0D, 0.3D, 0.0D), 4, 0.14D, 0.14D, 0.14D, 0.01D);
                    entity.getWorld().spawnParticle(Particle.SNOWFLAKE, itemLocation.clone().add(0.0D, 0.2D, 0.0D), 3, 0.12D, 0.12D, 0.12D, 0.01D);
                    if (entity.isOnGround() || itemLocation.getY() <= landedBeamBase.getY() + 0.35D) {
                        powerupLanded.put(entity.getUniqueId(), true);
                        entity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, landedBeamBase, 24, 0.35D, 0.15D, 0.35D, 0.02D);
                        entity.getWorld().spawnParticle(Particle.END_ROD, landedBeamBase, 18, 0.25D, 0.25D, 0.25D, 0.03D);
                        entity.getWorld().playSound(landedBeamBase, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9F, 1.35F);
                        forAlive(player -> player.sendActionBar(Component.text(ChatColor.AQUA + "Powerup landed!")));
                    }
                }
                drawPowerupBeam(landed ? landedBeamBase : itemLocation);
            }, 0L, 5L);
            powerupParticleTasks.put(entity.getUniqueId(), particles);
            forAlive(player -> player.sendActionBar(Component.text(ChatColor.AQUA + "Powerup incoming!")));
            activeWorld.playSound(drop, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 1.2F);
            return;
        }
    }

    private void drawPowerupBeam(Location base) {
        if (base == null || base.getWorld() == null) {
            return;
        }
        int height = bounds == null ? 12 : Math.max(8, Math.min(24, bounds.maxY() - base.getBlockY() + 8));
        for (int i = 0; i <= height; i += 2) {
            base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0.0D, i, 0.0D), 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
        base.getWorld().spawnParticle(Particle.SCRAPE, base.clone().add(0.0D, 0.05D, 0.0D), 2, 0.22D, 0.02D, 0.22D, 0.0D);
    }

    private ItemStack createPowerupItem(Powerup powerup) {
        ItemStack item = new ItemStack(powerup.material());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(powerup.color() + powerup.displayName());
        if (powerup.hasLore()) {
            meta.setLore(List.of(
                ChatColor.AQUA + powerup.category(),
                "",
                ChatColor.GRAY + powerup.description(),
                "",
                ChatColor.GRAY + powerup.ability()
            ));
        } else {
            meta.setLore(null);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (powerup == Powerup.GALE_MACE) {
            meta.addEnchant(Enchantment.WIND_BURST, 5, true);
        }
        if (powerup.customModel()) {
            meta.setItemModel(new NamespacedKey("hydra", "spleef_" + powerup.key()));
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, powerup.key());
        item.setItemMeta(meta);
        return item;
    }

    private void giveAdminTestItems(Player player) {
        if (player == null) {
            return;
        }
        player.getInventory().addItem(
            createShovel(Material.STONE_SHOVEL),
            createShovel(Material.DIAMOND_SHOVEL),
            createPowerupItem(Powerup.MEGA_SNOWBALL),
            createPowerupItem(Powerup.LASSO),
            createPowerupItem(Powerup.SUPER_FEATHER),
            createPowerupItem(Powerup.INVIS_ORB),
            createPowerupItem(Powerup.LEVITATION_ORB),
            createPowerupItem(Powerup.BOOMERANG),
            createPowerupItem(Powerup.STOP_WATCH),
            createPowerupItem(Powerup.GALE_MACE)
        );
        player.sendMessage(HYDRA_PREFIX + ChatColor.GREEN + "Spleef test items added.");
    }

    private void removePowerupEntity(Item entity) {
        if (entity == null) {
            return;
        }
        UUID id = entity.getUniqueId();
        activePowerupEntities.remove(id);
        powerupLanded.remove(id);
        BukkitTask task = powerupParticleTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        entity.remove();
    }

    private void decrementHeldPowerups(UUID playerId) {
        int held = Math.max(0, heldPowerups.getOrDefault(playerId, 0) - 1);
        if (held == 0) {
            heldPowerups.remove(playerId);
        } else {
            heldPowerups.put(playerId, held);
        }
    }

    private void consumeOne(Player player, ItemStack item, EquipmentSlot hand) {
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            ItemStack updated = item.clone();
            updated.setAmount(item.getAmount() - 1);
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(updated);
            } else {
                player.getInventory().setItemInMainHand(updated);
            }
        }
        player.updateInventory();
    }

    private void eliminate(Player player, Cause explicitCause, String reason) {
        UUID playerId = player.getUniqueId();
        if (!alivePlayers.remove(playerId)) {
            return;
        }
        if (managedRound != null) {
            managedEliminationOrder.add(playerId);
        }
        SpleefPlayerSnapshot snapshot = captureSnapshot(player, false);
        if (snapshot != null) {
            snapshotOwner().reviveSnapshots.put(playerId, snapshot);
        }
        Cause cause = explicitCause != null ? explicitCause : validCause(recentPlayerCauses.get(playerId));
        if (cause != null && !cause.playerId().equals(playerId)) {
            creditManagedElimination(playerId, cause);
            Player killer = Bukkit.getPlayer(cause.playerId());
            if (killer != null && killer.isOnline()) {
                plugin.queueService().recordSpleefKill(cause.playerId());
                killer.sendActionBar(Component.text(ChatColor.GREEN + "+1 Spleef Kill"));
                HydraSounds.playKill(killer);
            }
        }
        setSpectator(player);
        broadcastMatch(ChatColor.WHITE + player.getName() + ChatColor.GRAY + " " + reason + ".");
        evaluateWin();
    }

    private void creditManagedElimination(UUID victimId, Cause cause) {
        if (managedRound == null || cause == null || cause.playerId().equals(victimId)
            || !managedRound.roster().contains(cause.playerId())) {
            return;
        }
        managedEliminationCredits.merge(cause.playerId(), 1, Integer::sum);
    }

    private Cause resolveFallCause(Location location) {
        if (location == null || activeWorld == null) {
            return null;
        }
        Block base = location.getBlock();
        Cause best = null;
        for (int y = 0; y <= 5; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    Cause cause = validCause(recentTileCauses.get(BlockPos.of(base.getRelative(x, -y, z))));
                    if (cause != null && (best == null || cause.timeMs() > best.timeMs())) {
                        best = cause;
                    }
                }
            }
        }
        return best;
    }

    private void registerCause(UUID victimId, UUID causeId, String reason) {
        if (victimId == null || causeId == null) {
            return;
        }
        recentPlayerCauses.put(victimId, new Cause(causeId, System.currentTimeMillis(), reason));
    }

    private Cause validCause(Cause cause) {
        if (cause == null || System.currentTimeMillis() - cause.timeMs() > KILL_CREDIT_MS) {
            return null;
        }
        return cause;
    }

    private void setSpectator(Player player) {
        finishStopWatch(player.getUniqueId(), false);
        spectators.add(player.getUniqueId());
        frozenSpawnLocations.remove(player.getUniqueId());
        tutorialViewers.remove(player.getUniqueId());
        heldPowerups.remove(player.getUniqueId());
        doubleJumpReady.remove(player.getUniqueId());
        lastLassoUseMs.remove(player.getUniqueId());
        outsideBorderSinceMs.remove(player.getUniqueId());
        BukkitTask invisTask = invisRevealTasks.remove(player.getUniqueId());
        if (invisTask != null) {
            invisTask.cancel();
        }
        plugin.menuService().setHeadCosmeticTemporarilyHidden(player, false);
        configureSpectatorInventory(player);
        syncSpectatorVisibility();
        player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED", ChatColor.GRAY + "You are now spectating.", 5, 50, 15);
        HydraSounds.playSpectatorEnter(player);
        player.sendMessage(ChatColor.GRAY + "Use the compass to spectate players.");
    }

    private void evaluateWin() {
        if (!matchActive || (alivePlayers.size() > 1
            && !RouletteRoundRules.onlyOneTeamRemains(alivePlayers, managedRound))) {
            return;
        }
        UUID winnerId = alivePlayers.stream().findFirst().orElse(null);
        if (winnerId == null) {
            finishMatch(null);
            return;
        }
        Player winner = Bukkit.getPlayer(winnerId);
        if (managedRound == null && winner != null && winner.isOnline()) {
            int silver = plugin.queueService().recordSpleefWin(winnerId);
            if (plugin.progressionService() != null) {
                plugin.progressionService().grantShardFragments(winnerId, 1, "SPLEEF_WIN");
            }
            winner.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY", ChatColor.GRAY + "You won Spleef.", 10, 60, 20);
            if (silver > 0) {
                winner.sendMessage(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + silver + " Coins");
            }
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
        }
        broadcastMatch(ChatColor.GOLD + (winner == null ? "A player" : winner.getName()) + ChatColor.GREEN + " won Spleef.");
        finishMatch(winnerId);
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
        graceActive = false;
        frozenSpawnLocations.clear();
        closeTutorialMenus();
        if (powerupTask != null) {
            powerupTask.cancel();
            powerupTask = null;
        }
        if (powerupGrantTask != null) {
            powerupGrantTask.cancel();
            powerupGrantTask = null;
        }
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
            borderShrinkTask = null;
        }
        for (BukkitTask task : collapseTasks) {
            task.cancel();
        }
        collapseTasks.clear();
        if (matchDecayTask != null) {
            matchDecayTask.cancel();
            matchDecayTask = null;
        }
        for (UUID id : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                resetSidebarToHub(player);
            }
        }
        for (UUID id : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                resetSidebarToHub(player);
            }
        }
        cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LinkedHashSet<UUID> returningIds = new LinkedHashSet<>(alivePlayers);
            returningIds.addAll(spectators);
            List<Player> returning = returningIds.stream().map(Bukkit::getPlayer)
                .filter(Objects::nonNull).filter(Player::isOnline).toList();
            plugin.transitionTravelGroup(returning, this::cleanupPlayerForHub, () -> {
                String worldName = activeWorldName;
                cleanupState(false);
                if (request == null) {
                    plugin.playerSettingsService().requeueAfterCompletedSession(
                        requeueCandidates, PlayerSettingsService.AutoQueue.SPLEEF);
                }
                if (worldName != null) cleanupWorld(worldName);
                if (request != null) {
                    dispatchManagedResult(request,
                        ManagedMinigameRound.Result.players(request, placements, eliminationCredits));
                }
            });
        }, WIN_RETURN_TICKS - TransitionService.Preset.NORMAL.fadeOutTicks);
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

    private void stopManagedRound(String reason) {
        ManagedMinigameRound.Request request = managedRound;
        if (request == null) {
            return;
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
        cleanupState(false);
        if (worldName != null) {
            cleanupWorld(worldName);
        }
        dispatchManagedResult(request, ManagedMinigameRound.Result.failed(request, reason));
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
        finishStopWatch(playerId, false);
        queuedPlayers.remove(playerId);
        alivePlayers.remove(playerId);
        boolean wasSpectator = spectators.remove(playerId);
        externalSpectators.remove(playerId);
        frozenSpawnLocations.remove(playerId);
        tutorialViewers.remove(playerId);
        if (matchInstance && manager != null) {
            manager.activeMatchesByPlayer.remove(playerId, this);
        }
        heldPowerups.remove(playerId);
        doubleJumpReady.remove(playerId);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        plugin.playerSettingsService().clearHydraNightVision(player);
        player.setInvulnerable(false);
        player.setCanPickupItems(true);
        player.setCollidable(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        resetSidebarToHub(player);
        restorePlayerVisibility(player);
        if (wasSpectator) {
            syncSpectatorVisibility();
        }
        plugin.queueService().returnPlayerToHydraHub(player);
        resetSidebarToHub(player);
    }

    public boolean returnTournamentSpectatorToHub(Player player) {
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            return match != null && match.returnTournamentSpectatorToHub(player);
        }
        if (player == null || !spectators.contains(player.getUniqueId())) return false;
        return plugin.transitionTravel(player, () -> {
            if (!spectators.contains(player.getUniqueId())) return;
            SpleefPlayerSnapshot snapshot = captureSnapshot(player, true);
            if (snapshot == null) return;
            snapshotOwner().rejoinSnapshots.put(player.getUniqueId(), snapshot);
            cleanupPlayerForHub(player);
        });
    }

    public boolean forfeitTournamentPlayerToHub(Player player) {
        if (!matchInstance) {
            SpleefService match = matchFor(player);
            return match != null && match.forfeitTournamentPlayerToHub(player);
        }
        if (player == null || managedRound == null || !alivePlayers.contains(player.getUniqueId())) return false;
        eliminate(player, null, "left the round");
        clearSnapshot(player.getUniqueId());
        if (!plugin.transitionTravel(player, () -> cleanupPlayerForHub(player))) {
            cleanupPlayerForHub(player);
        }
        return true;
    }

    private void restorePlayerVisibility(Player target) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, target);
            target.showPlayer(plugin, other);
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

    private SpleefService snapshotOwner() {
        return matchInstance && manager != null ? manager : this;
    }

    private void clearSnapshot(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SpleefService owner = snapshotOwner();
        owner.removeReconnectZombie(playerId);
        owner.rejoinSnapshots.remove(playerId);
        owner.reviveSnapshots.remove(playerId);
    }

    private void clearSnapshotsForMatch(SpleefService match) {
        if (match == null) {
            return;
        }
        rejoinSnapshots.entrySet().removeIf(entry -> entry.getValue().match == match);
        reviveSnapshots.entrySet().removeIf(entry -> entry.getValue().match == match);
        for (UUID playerId : new ArrayList<>(reconnectZombieByPlayer.keySet())) {
            SpleefPlayerSnapshot snapshot = rejoinSnapshots.get(playerId);
            if (snapshot == null || snapshot.match == match) {
                removeReconnectZombie(playerId);
            }
        }
    }

    private SpleefPlayerSnapshot captureSnapshot(Player player, boolean spectator) {
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
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? 20.0D : Math.max(1.0D, maxHealthAttribute.getValue());
        double storedHealth = spectator ? maxHealth : Math.max(1.0D, Math.min(maxHealth, player.getHealth()));
        UUID playerId = player.getUniqueId();
        return new SpleefPlayerSnapshot(
            this,
            activeWorldName,
            spectator,
            location,
            cloneItemStacks(player.getInventory().getContents()),
            cloneItemStacks(player.getInventory().getArmorContents()),
            cloneItem(player.getInventory().getItemInOffHand()),
            storedHealth,
            player.getFoodLevel(),
            player.getSaturation(),
            shovelBreaks.getOrDefault(playerId, 0),
            totalBreaks.getOrDefault(playerId, 0),
            heldPowerups.getOrDefault(playerId, 0),
            doubleJumpReady.contains(playerId)
        );
    }

    private boolean restoreLiveSnapshot(Player player, SpleefPlayerSnapshot snapshot, boolean fullHeal) {
        if (player == null || snapshot == null || !snapshot.isRestorable()) {
            return false;
        }
        World world = Bukkit.getWorld(snapshot.worldName);
        if (world == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        spectators.remove(playerId);
        alivePlayers.add(playerId);
        frozenSpawnLocations.remove(playerId);
        if (matchInstance && manager != null) {
            manager.activeMatchesByPlayer.put(playerId, this);
        }
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
        player.getInventory().setContents(cloneItemStacks(snapshot.contents));
        player.getInventory().setArmorContents(cloneItemStacks(snapshot.armor));
        player.getInventory().setItemInOffHand(cloneItem(snapshot.offHand));
        player.updateInventory();
        shovelBreaks.put(playerId, Math.max(0, snapshot.shovelBreaks));
        if (snapshot.shovelBreaks >= SHOVEL_UPGRADE_BREAKS) {
            ensureSingleUpgradedShovel(player);
        }
        totalBreaks.put(playerId, Math.max(0, snapshot.totalBreaks));
        if (snapshot.heldPowerups > 0) {
            heldPowerups.put(playerId, snapshot.heldPowerups);
        } else {
            heldPowerups.remove(playerId);
        }
        if (snapshot.doubleJumpReady) {
            doubleJumpReady.add(playerId);
        } else {
            doubleJumpReady.remove(playerId);
        }
        if (fullHeal) {
            player.setHealth(Math.min(player.getMaxHealth(), 20.0D));
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        } else {
            player.setHealth(Math.max(1.0D, Math.min(player.getMaxHealth(), snapshot.health)));
            player.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel)));
            player.setSaturation(Math.max(0.0F, snapshot.saturation));
        }
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        syncSpectatorVisibility();
        updateSpleefSidebar(player);
        return true;
    }

    private boolean restoreSpectatorSnapshot(Player player, SpleefPlayerSnapshot snapshot) {
        if (player == null || snapshot == null || !snapshot.isRestorable()) {
            return false;
        }
        World world = Bukkit.getWorld(snapshot.worldName);
        if (world == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        alivePlayers.remove(playerId);
        spectators.add(playerId);
        if (matchInstance && manager != null) {
            manager.activeMatchesByPlayer.put(playerId, this);
        }
        Location targetLocation = snapshot.location.clone();
        if (targetLocation.getWorld() == null) {
            targetLocation.setWorld(world);
        }
        player.teleport(targetLocation);
        configureSpectatorInventory(player);
        syncSpectatorVisibility();
        updateSpleefSidebar(player);
        return true;
    }

    private void configureSpectatorInventory(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCanPickupItems(false);
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setItem(0, createSpectatorTeleportCompass());
        player.getInventory().setItem(8, createSpectatorHubPaper());
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, false));
        plugin.playerSettingsService().applyHydraNightVision(player);
        player.updateInventory();
    }

    private void spawnReconnectZombie(Player player, SpleefPlayerSnapshot snapshot) {
        SpleefService owner = snapshotOwner();
        if (player == null || snapshot == null || snapshot.spectator || snapshot.location == null) {
            return;
        }
        owner.removeReconnectZombie(player.getUniqueId());
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
            body.addScoreboardTag("hydra_spleef_reconnect");
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
        owner.reconnectZombieByPlayer.put(player.getUniqueId(), zombie.getUniqueId());
        owner.reconnectPlayerByZombie.put(zombie.getUniqueId(), player.getUniqueId());
        long expiresAtMs = System.currentTimeMillis() + RECONNECT_BODY_TIMEOUT_MS;
        owner.reconnectZombieExpiresAtMs.put(player.getUniqueId(), expiresAtMs);
        Bukkit.getScheduler().runTaskLater(plugin, () -> owner.cleanupExpiredReconnectBody(player.getUniqueId(), expiresAtMs), RECONNECT_BODY_TIMEOUT_MS / 50L);
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
        SpleefPlayerSnapshot snapshot = rejoinSnapshots.remove(playerId);
        removeReconnectZombie(playerId);
        reviveSnapshots.remove(playerId);
        if (snapshot != null && snapshot.isRestorable()) {
            if (snapshot.match.managedRound != null) {
                snapshot.match.managedEliminationOrder.add(playerId);
                snapshot.match.creditManagedElimination(playerId,
                    snapshot.match.validCause(snapshot.match.recentPlayerCauses.get(playerId)));
            }
            snapshot.match.alivePlayers.remove(playerId);
            if (snapshot.match.manager != null) {
                snapshot.match.manager.activeMatchesByPlayer.remove(playerId, snapshot.match);
            }
            snapshot.match.broadcastMatch(ChatColor.RED + displayName(playerId) + ChatColor.GRAY + " failed to reconnect.");
            snapshot.match.evaluateWin();
        }
    }

    private void cleanupAllReconnectBodies() {
        for (UUID playerId : new ArrayList<>(reconnectZombieByPlayer.keySet())) {
            removeReconnectZombie(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReconnectZombieDeath(EntityDeathEvent event) {
        SpleefService owner = snapshotOwner();
        UUID playerId = owner.reconnectPlayerByZombie.remove(event.getEntity().getUniqueId());
        if (playerId == null) {
            return;
        }
        owner.reconnectZombieByPlayer.remove(playerId);
        owner.reconnectZombieExpiresAtMs.remove(playerId);
        event.getDrops().clear();
        SpleefPlayerSnapshot snapshot = owner.rejoinSnapshots.remove(playerId);
        owner.reviveSnapshots.remove(playerId);
        if (snapshot != null && snapshot.isRestorable()) {
            if (snapshot.match.managedRound != null) {
                snapshot.match.managedEliminationOrder.add(playerId);
                snapshot.match.creditManagedElimination(playerId,
                    snapshot.match.validCause(snapshot.match.recentPlayerCauses.get(playerId)));
            }
            snapshot.match.alivePlayers.remove(playerId);
            if (snapshot.match.manager != null) {
                snapshot.match.manager.activeMatchesByPlayer.remove(playerId, snapshot.match);
            }
            snapshot.match.broadcastMatch(ChatColor.RED + snapshot.match.displayName(playerId) + ChatColor.GRAY + " failed to reconnect.");
            snapshot.match.evaluateWin();
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

    private void cleanupState(boolean keepQueue) {
        if (matchInstance && manager != null) {
            manager.clearSnapshotsForMatch(this);
            manager.unregisterMatch(this);
        }
        for (DecayState state : decayingTiles.values()) {
            for (BukkitTask task : state.tasks()) {
                task.cancel();
            }
            Block block = blockAt(state.pos());
            if (block != null && STAGE_TILE_TYPES.contains(block.getType())) {
                block.setType(state.original(), false);
            }
        }
        for (UUID id : new ArrayList<>(activePowerupEntities)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Item item) {
                removePowerupEntity(item);
            }
        }
        for (BukkitTask task : powerupParticleTasks.values()) {
            task.cancel();
        }
        cleanupBoomerangs();
        boomerangActivationTicks.clear();
        cleanupStopWatches();
        cleanupLassoPulls();
        closeTutorialMenus();
        for (BukkitTask task : collapseTasks) {
            task.cancel();
        }
        collapseTasks.clear();
        collapseFloors.clear();
        boulderQuarterNextTopY.clear();
        collapseLayerIndex = 0;
        activeCollapsePattern = SpleefCollapsePattern.OUTSIDE_IN;
        for (UUID playerId : new ArrayList<>(invisRevealTasks.keySet())) {
            Player hiddenPlayer = Bukkit.getPlayer(playerId);
            if (hiddenPlayer != null) {
                plugin.menuService().setHeadCosmeticTemporarilyHidden(hiddenPlayer, false);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!viewer.getUniqueId().equals(playerId)) {
                        viewer.showPlayer(plugin, hiddenPlayer);
                    }
                }
            }
        }
        for (BukkitTask task : invisRevealTasks.values()) {
            task.cancel();
        }
        invisRevealTasks.clear();
        outsideBorderSinceMs.clear();
        totalBreaks.clear();
        frozenSpawnLocations.clear();
        powerupParticleTasks.clear();
        powerupLanded.clear();
        activePowerupEntities.clear();
        tutorialViewers.clear();
        decayingTiles.clear();
        originalSnowTiles.clear();
        activeTiles.clear();
        alivePlayers.clear();
        spectators.clear();
        externalSpectators.clear();
        managedEliminationOrder.clear();
        managedEliminationCredits.clear();
        shovelBreaks.clear();
        heldPowerups.clear();
        doubleJumpReady.clear();
        lastLassoUseMs.clear();
        recentPlayerCauses.clear();
        recentTileCauses.clear();
        activeWorld = null;
        activeWorldName = null;
        activeMap = null;
        managedRound = null;
        bounds = null;
        eliminationY = 0;
        matchPlayerCount = 0;
        lowestPlayableTileY = 0;
        matchStarting = false;
        matchActive = false;
        graceActive = false;
        if (!keepQueue) {
            queuedPlayers.clear();
        }
    }

    private void cancelTasks() {
        cancelMatchTasks();
        if (statusTask != null) {
            statusTask.cancel();
            statusTask = null;
        }
    }

    private void cancelMatchTasks() {
        graceActive = false;
        frozenSpawnLocations.clear();
        closeTutorialMenus();
        if (queueCountdownTask != null) {
            queueCountdownTask.cancel();
            queueCountdownTask = null;
        }
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }
        if (powerupTask != null) {
            powerupTask.cancel();
            powerupTask = null;
        }
        if (powerupGrantTask != null) {
            powerupGrantTask.cancel();
            powerupGrantTask = null;
        }
        cleanupBoomerangs();
        cleanupStopWatches();
        cleanupLassoPulls();
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
            borderShrinkTask = null;
        }
        for (BukkitTask task : collapseTasks) {
            task.cancel();
        }
        collapseTasks.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (matchDecayTask != null) {
            matchDecayTask.cancel();
            matchDecayTask = null;
        }
    }

    private void cleanupBoomerangs() {
        for (Map.Entry<UUID, BukkitTask> entry : new HashMap<>(activeBoomerangs).entrySet()) {
            entry.getValue().cancel();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null) entity.remove();
        }
        activeBoomerangs.clear();
    }

    private void cleanupWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        int playersInWorld = world == null ? 0 : world.getPlayers().size();
        if (world != null) {
            for (Player player : new ArrayList<>(world.getPlayers())) {
                cleanupPlayerForHub(player);
            }
        }
        if (!autoCleanupEnabled) {
            plugin.getLogger().info("[Spleef] Auto-cleanup disabled; leaving match world=" + worldName
                + " loaded for inspection (players=" + playersInWorld + ").");
            return;
        }
        plugin.getLogger().info("[Spleef] Cleaning match world=" + worldName + ", players=" + playersInWorld);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName);
        Bukkit.getScheduler().runTaskLater(plugin, () -> deleteFolder(folder), 20L);
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
                    plugin.getLogger().warning("[Spleef] Failed deleting " + path + ": " + ex.getMessage());
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().warning("[Spleef] Failed walking world folder " + folder + ": " + ex.getMessage());
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

    private void broadcastParticipants(List<UUID> participants, String message) {
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void applyKnockback(Player shooter, Player target, double strength) {
        if (plugin.teamProtectionService().areProtectedTeammates(shooter, target)) return;
        Vector vector = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
        vector.setY(0.0D);
        if (vector.lengthSquared() <= 0.01D) {
            vector = shooter.getLocation().getDirection();
            vector.setY(0.0D);
        }
        target.setVelocity(vector.normalize().multiply(strength).setY(0.22D));
    }

    private boolean isInActiveWorld(World world) {
        return world != null && world.getName().equals(activeWorldName);
    }

    private Block blockAt(BlockPos pos) {
        if (activeWorld == null || pos == null || !activeWorld.getName().equals(pos.world())) {
            return null;
        }
        return activeWorld.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    private void ensureBundledMaps() {
        for (SpleefMap map : SpleefMap.playable()) {
            try {
                Path target = plugin.getDataFolder().toPath().resolve(map.resourcePath()).normalize();
                boolean copyResource = !Files.isRegularFile(target);
                if (!copyResource) {
                    try (InputStream resource = plugin.getResource(map.resourcePath())) {
                        if (resource == null) {
                            plugin.getLogger().warning("[Spleef] Bundled map missing: " + map.resourcePath());
                            continue;
                        }
                        Path temp = Files.createTempFile("hydra-spleef-map-", ".nbt");
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
                            plugin.getLogger().warning("[Spleef] Bundled map missing: " + map.resourcePath());
                            continue;
                        }
                        Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[Spleef] Bundled map missing: " + map.resourcePath());
            } catch (IOException ex) {
                plugin.getLogger().warning("[Spleef] Could not sync bundled map " + map.resourcePath() + ": " + ex.getMessage());
            }
        }
    }

    private void reloadMapFiles() {
        mapFiles.clear();
        for (SpleefMap map : SpleefMap.playable()) {
            Path path = plugin.getDataFolder().toPath().resolve(map.resourcePath()).normalize();
            if (Files.isRegularFile(path)) {
                mapFiles.put(map, path);
            }
        }
    }

    private ItemStack createNamedItem(Material material, String type, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private String cleanPowerupName(String key) {
        return switch (key) {
            case "mega_snowball" -> "Mega Snowball";
            case "lasso" -> "Lasso";
            case "super_feather" -> "Super Feather";
            case "invis_orb" -> "Invis Orb";
            case "levitation_orb" -> "Levitation Orb";
            case "boomerang" -> "Boomerang";
            case "stop_watch" -> "Stop Watch";
            case "gale_mace" -> "Gale Mace";
            default -> "Powerup";
        };
    }

    private String displayName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        return playerId == null ? "A player" : playerId.toString().substring(0, 8);
    }


    private static List<String> filter(String token, List<String> values) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    public record ManagedStartFailure(String mapKey, String mapName, String reason) {
        public ManagedStartFailure {
            mapKey = mapKey == null ? "" : mapKey;
            mapName = mapName == null ? "" : mapName;
            reason = reason == null ? "" : reason;
        }
    }

    private enum SpleefMap {
        RANDOM("random", "Random", ""),
        BASE("base", "Base Spleef", "Structure nbt/minigames/base_spleef.nbt", 1, 129, 119, 109, 99, 89),
        RINGS("rings", "Spleef Rings", "Structure nbt/minigames/spleef_rings.nbt", 1, 129, 119, 109, 99, 89),
        BOULDER("boulder", "Boulder Spleef", "Structure nbt/minigames/boulder_spleef.nbt"),
        FOUR_CORNERS("fourcorners", "Four Corners", "Structure nbt/minigames/four_corners.nbt", 1, 129, 119, 109, 99, 89),
        STARS("stars", "Stars", "Structure nbt/minigames/stars.nbt", 1, 129, 119, 109, 99, 89),
        DESERT_CLASSIC("desertclassic", "Desert Classic", "Structure nbt/minigames/desert_classic.nbt", 1, 129, 119, 109, 99, 89),
        DESERT_RINGS("desertrings", "Desert Rings", "Structure nbt/minigames/desert_rings.nbt", 1, 129, 119, 109, 99, 89),
        DESERT_BOULDER("desertboulder", "Desert Boulder", "Structure nbt/minigames/desert_boulder.nbt"),
        DESERT_FOUR_CORNERS("desertfourcorners", "Desert Four Corners", "Structure nbt/minigames/desert_four_corners.nbt", 1, 129, 119, 109, 99, 89),
        DESERT_STARS("desertstars", "Desert Stars", "Structure nbt/minigames/desert_stars.nbt", 1, 129, 119, 109, 99, 89),
        DESERT_LAYERS("desertlayers", "Desert Layers", "Structure nbt/minigames/desert_layers.nbt", 2, 129, 119, 109, 99, 89),
        SNOWY_LAYERS("snowylayers", "Snowy Layers", "Structure nbt/minigames/snowy_layers.nbt", 2, 129, 119, 109, 99, 89);

        private final String key;
        private final String displayName;
        private final String resourcePath;
        private final int floorThickness;
        private final List<Integer> floorBaseYs;

        SpleefMap(String key, String displayName, String resourcePath) {
            this(key, displayName, resourcePath, 0);
        }

        SpleefMap(String key, String displayName, String resourcePath, int floorThickness, int... floorBaseYs) {
            this.key = key;
            this.displayName = displayName;
            this.resourcePath = resourcePath;
            this.floorThickness = floorThickness;
            this.floorBaseYs = Arrays.stream(floorBaseYs).boxed().toList();
        }

        private List<LogicalFloor> logicalFloors(SpleefCollapsePattern pattern) {
            return buildLogicalFloors(floorThickness, pattern,
                floorBaseYs.stream().mapToInt(Integer::intValue).toArray());
        }

        private String displayName() {
            return displayName;
        }

        private String resourcePath() {
            return resourcePath;
        }

        private static SpleefMap fromKey(String raw) {
            if (raw == null) {
                return null;
            }
            String key = raw.toLowerCase(Locale.ROOT).trim();
            String compactKey = key.replace("_", "").replace("-", "").replace(" ", "");
            if (key.equals("base_spleef")) {
                key = "base";
            } else if (key.equals("spleef_rings")) {
                key = "rings";
            } else if (key.equals("boulder_spleef") || compactKey.equals("boulder")
                || compactKey.equals("desertboulder")) {
                return null;
            } else if (key.equals("four_corners") || key.equals("four-corners") || key.equals("four corners")) {
                key = "fourcorners";
            } else if (compactKey.equals("desertclassic") || compactKey.equals("desertbase")) {
                key = "desertclassic";
            } else if (compactKey.equals("desertrings")) {
                key = "desertrings";
            } else if (compactKey.equals("desertfourcorners")) {
                key = "desertfourcorners";
            } else if (compactKey.equals("desertstars") || compactKey.equals("desertstar")) {
                key = "desertstars";
            } else if (compactKey.equals("desertlayers") || compactKey.equals("desertlayer")) {
                key = "desertlayers";
            } else if (compactKey.equals("snowylayers") || compactKey.equals("snowylayer")) {
                key = "snowylayers";
            }
            for (SpleefMap map : values()) {
                if (map.key.equals(key) || map.key.equals(compactKey)) {
                    return map;
                }
            }
            return null;
        }

        private static List<SpleefMap> playable() {
            return List.of(BASE, RINGS, FOUR_CORNERS, STARS,
                DESERT_CLASSIC, DESERT_RINGS, DESERT_FOUR_CORNERS, DESERT_STARS,
                DESERT_LAYERS, SNOWY_LAYERS);
        }

        private static SpleefMap randomPlayable() {
            List<SpleefMap> maps = playable();
            return maps.get(ThreadLocalRandom.current().nextInt(maps.size()));
        }
    }

    static List<LogicalFloor> buildLogicalFloors(int thickness, SpleefCollapsePattern pattern, int... baseYs) {
        if (thickness <= 0 || baseYs == null || baseYs.length == 0) {
            return List.of();
        }
        List<LogicalFloor> floors = new ArrayList<>();
        for (int baseY : baseYs) {
            List<Integer> ys = new ArrayList<>();
            for (int offset = 0; offset < thickness; offset++) {
                ys.add(baseY + offset);
            }
            floors.add(new LogicalFloor(List.copyOf(ys), pattern));
        }
        return List.copyOf(floors);
    }

    record LogicalFloor(List<Integer> ys, SpleefCollapsePattern pattern) {
        boolean containsY(int y) {
            return ys.contains(y);
        }

        int minY() {
            return ys.stream().mapToInt(Integer::intValue).min().orElseThrow();
        }
    }

    private enum Powerup {
        MEGA_SNOWBALL(
            "mega_snowball",
            "Mega Snowball",
            Material.SNOWBALL,
            ChatColor.AQUA,
            true,
            "Destruction \u00B7 Ranged",
            "A snowball with absolutely no respect for property.",
            "RMB to throw, creating a 3\u00D73\u00D73 crater on impact or knocking nearby players back."
        ),
        LASSO(
            "lasso",
            "Lasso",
            Material.LEAD,
            ChatColor.YELLOW,
            true,
            "Displacement \u00B7 Single Use",
            "Your cowboy impression finally has a purpose.",
            "RMB while aiming at a player within 8 blocks to pull them toward you."
        ),
        SUPER_FEATHER(
            "super_feather",
            "Super Feather",
            Material.FEATHER,
            ChatColor.WHITE,
            true,
            "Mobility \u00B7 Escape",
            "A feather blessed by the divine. Gravity disagrees.",
            "RMB to launch yourself skyward."
        ),
        INVIS_ORB(
            "invis_orb",
            "Invis Orb",
            Material.ENDER_EYE,
            ChatColor.LIGHT_PURPLE,
            true,
            "Stealth \u00B7 Escape",
            "Now you see me. Now you really shouldn't.",
            "RMB to become invisible for 5 seconds."
        ),
        LEVITATION_ORB(
            "levitation_orb",
            "Levitation Orb",
            Material.PHANTOM_MEMBRANE,
            ChatColor.LIGHT_PURPLE,
            true,
            "Mobility \u00B7 Aerial",
            "Proving that gravity is merely a suggestion.",
            "RMB to levitate for 3 seconds."
        ),
        BOOMERANG(
            "boomerang",
            "Boomerang",
            Material.FLINT,
            ChatColor.GOLD,
            true,
            "Destruction \u00B7 Single Use",
            "What goes around comes around. So does the floor.",
            "LMB/RMB to throw left or right, instantly destroying blocks along its path."
        ),
        STOP_WATCH(
            "stop_watch",
            "Stop Watch",
            Material.CLOCK,
            ChatColor.GOLD,
            true,
            "Defensive \u00B7 Single Use",
            "Time waits for no one. Except you, apparently.",
            "Freeze yourself in place for 5 seconds."
        ),
        GALE_MACE(
            "gale_mace",
            "Gale Mace",
            Material.MACE,
            ChatColor.AQUA,
            true,
            "Mobility \u00B7 Single Use",
            "Gravity has favorites. You're not one of them.",
            "Hit a player to launch yourself. Deals no damage."
        );

        private final String key;
        private final String displayName;
        private final Material material;
        private final ChatColor color;
        private final boolean customModel;
        private final String category;
        private final String description;
        private final String ability;

        Powerup(
            String key,
            String displayName,
            Material material,
            ChatColor color,
            boolean customModel,
            String category,
            String description,
            String ability
        ) {
            this.key = key;
            this.displayName = displayName;
            this.material = material;
            this.color = color;
            this.customModel = customModel;
            this.category = category;
            this.description = description;
            this.ability = ability;
        }

        private boolean customModel() {
            return customModel;
        }

        private String category() {
            return category;
        }

        private String key() {
            return key;
        }

        private String displayName() {
            return displayName;
        }

        private Material material() {
            return material;
        }

        private ChatColor color() {
            return color;
        }

        private String description() {
            return description;
        }

        private String ability() {
            return ability;
        }

        private boolean hasLore() {
            return category != null && description != null && ability != null;
        }

        private static Powerup random() {
            Powerup[] values = values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }
    }

    private record BlockPos(String world, int x, int y, int z) {
        private static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record Cause(UUID playerId, long timeMs, String reason) {
    }

    private static final class SpleefPlayerSnapshot {
        private final SpleefService match;
        private final String worldName;
        private final boolean spectator;
        private final Location location;
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offHand;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final int shovelBreaks;
        private final int totalBreaks;
        private final int heldPowerups;
        private final boolean doubleJumpReady;

        private SpleefPlayerSnapshot(
            SpleefService match,
            String worldName,
            boolean spectator,
            Location location,
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offHand,
            double health,
            int foodLevel,
            float saturation,
            int shovelBreaks,
            int totalBreaks,
            int heldPowerups,
            boolean doubleJumpReady
        ) {
            this.match = match;
            this.worldName = worldName;
            this.spectator = spectator;
            this.location = location;
            this.contents = contents;
            this.armor = armor;
            this.offHand = offHand;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.shovelBreaks = shovelBreaks;
            this.totalBreaks = totalBreaks;
            this.heldPowerups = heldPowerups;
            this.doubleJumpReady = doubleJumpReady;
        }

        private boolean isRestorable() {
            return match != null
                && match.matchActive
                && worldName != null
                && match.activeWorldName != null
                && match.activeWorldName.equals(worldName)
                && Bukkit.getWorld(worldName) != null;
        }
    }

    private record DecayState(BlockPos pos, Material original, UUID causeId, List<BukkitTask> tasks) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private enum BoulderQuarter {
        NORTHEAST("northeast"),
        NORTHWEST("northwest"),
        SOUTHEAST("southeast"),
        SOUTHWEST("southwest");

        private final String displayName;

        BoulderQuarter(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private static BoulderQuarter from(BlockPos pos) {
            boolean east = pos.x() >= 0;
            boolean south = pos.z() >= 0;
            if (east) {
                return south ? SOUTHEAST : NORTHEAST;
            }
            return south ? SOUTHWEST : NORTHWEST;
        }
    }

    private enum DecayStage {
        STAGE_1(21),
        STAGE_2(22),
        STAGE_3(23);

        private final int noteId;

        DecayStage(int noteId) {
            this.noteId = noteId;
        }

        private BlockData blockData() {
            NoteBlock data = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
            data.setInstrument(Instrument.BANJO);
            data.setNote(new Note(noteId));
            data.setPowered(false);
            return data;
        }
    }

    private static final class EmptySpleefChunkGenerator extends ChunkGenerator {
        private final int spawnY;

        private EmptySpleefChunkGenerator(int spawnY) {
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
