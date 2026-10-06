package hgloot.plugin;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.module.HydraModuleManager;
import hgloot.plugin.network.BackendRole;
import hgloot.plugin.network.NetworkBridgeService;
import hgloot.plugin.network.NetworkNodeConfig;
import hgloot.plugin.registry.HydraRegistryHub;
import hgloot.plugin.ultraroyales.UltraRoyales;
import hgloot.plugin.zxdzeroevents.ZxdzeroEventsCoreService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRules;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.util.HydraSounds;
import hgloot.plugin.announcement.AnnouncementService;
import hgloot.plugin.announcement.RotatingAnnouncementService;
import hgloot.plugin.announcement.SituationalAnnouncementService;
import hgloot.plugin.loot.LootCategory;
import hgloot.plugin.loot.LootTableService;
import hgloot.plugin.loot.ManagedLootService;
import hgloot.plugin.gui.LegendaryRecipeBookService;
import hgloot.plugin.hunger.HungerGamesUtilityService;
import hgloot.plugin.gui.MenuListener;

@SuppressWarnings("deprecation")
public final class HGLootPlugin extends JavaPlugin implements Listener {
    private static final List<String> QUEUE_BASE_SUBCOMMANDS = List.of("join", "leave", "status");
    private static final List<String> QUEUE_ADMIN_SUBCOMMANDS = List.of(
        "list", "clear", "forcejoin", "forcestart", "forcewin", "forceend",
        "autowin", "cleanup", "setlobby", "setarena", "removearena", "arenas", "structure", "cave", "preload", "map", "switch"
    );
    private static final List<String> FRIEND_SUBCOMMANDS = List.of("add", "accept", "deny", "remove", "block", "unblock", "list", "requests");
    private static final List<String> PARTY_SUBCOMMANDS = List.of("create", "join", "invite", "accept", "deny", "leave", "disband", "kick", "promote", "list", "requests");
    private static final List<String> COMBAT_MASTER_PHASES = List.of("artillery", "bulwark", "discipline", "final");
    private static final List<String> KOTH_SUBCOMMANDS = List.of(
        "setpos1", "setpos2", "start", "stop", "status", "countdown", "capture",
        "earlystart", "addtime", "removetime", "instantstart"
    );
    private static final List<String> CASINO_SUBCOMMANDS = List.of("claim", "balance", "audit");
    private static final List<String> CASINO_ADMIN_SUBCOMMANDS = List.of("addcoins");
    private static final List<String> COSMETICS_ADMIN_SUBCOMMANDS = List.of("menu", "unlock", "lock", "list", "reset");
    private static final List<String> BORDER_SUBCOMMANDS = List.of("info", "set", "add", "addseconds", "removeseconds", "center", "centerhere");
    private static final List<String> COMMON_AMOUNT_SUGGESTIONS = List.of("100", "250", "500", "1000", "2500", "5000");
    private static final List<String> RADIUS_SUGGESTIONS = List.of("16", "32", "64", "96", "128", "256");
    private static final List<String> LEADERBOARD_SUBCOMMANDS = List.of(
        "wins", "kills", "level", "coins", "money", "refresh", "reload", "clear", "forceupdate", "debug", "place", "switch", "remove"
    );
    private static final List<String> LEADERBOARD_STAT_SUGGESTIONS = List.of("wins", "kills", "level", "coins");
    private static final List<String> LEADERBOARD_SCOPE_SUGGESTIONS = List.of("total", "hg", "br");
    private static final List<String> RANK_SUBCOMMANDS = List.of("menu", "give", "set", "remove");
    private static final List<String> MAINTENANCE_SUBCOMMANDS = List.of("on", "off", "status", "add", "remove");
    private static final List<String> RANK_MODEL_SUGGESTIONS = List.of("hydra", "hydra+", "hydra++", "hydra+++", "trialstaff", "staff", "helper", "media", "creator", "mod", "admin", "dev", "owner");
    private static final List<String> RANK_TIER_SUGGESTIONS = List.of("Hydra", "Hydra+", "Hydra++", "Hydra+++");
    private static final String RANK_PERMISSION_HYDRA = "rank.hydra";
    private static final String RANK_PERMISSION_HYDRA_PLUS = "rank.hydra_plus";
    private static final String RANK_PERMISSION_HYDRA_PLUS_TWO = "rank.hydra_plus.2";
    private static final String RANK_PERMISSION_HYDRA_PLUS_THREE = "rank.hydra_plus.3";
    private static final String RANK_PERMISSION_STAFF_TRIAL_STAFF = "rank.staff.trial_staff";
    private static final String RANK_PERMISSION_STAFF_TRIAL_MOD = "rank.staff.trial_mod";
    private static final String RANK_PERMISSION_STAFF_STAFF = "rank.staff.staff";
    private static final String RANK_PERMISSION_STAFF_HELPER = "rank.staff.helper";
    private static final String RANK_PERMISSION_STAFF_MEDIA = "rank.staff.media";
    private static final String RANK_PERMISSION_STAFF_CREATOR = "rank.staff.creator";
    private static final String RANK_PERMISSION_STAFF_MOD = "rank.staff.mod";
    private static final String RANK_PERMISSION_STAFF_ADMIN = "rank.staff.admin";
    private static final String RANK_PERMISSION_STAFF_DEV = "rank.staff.dev";
    private static final String RANK_PERMISSION_STAFF_OWNER = "rank.staff.owner";
    private static final String HYDRA_RANK_CONFIG_ROOT = "ranks.hydra_plus";
    private static final String STAFF_RANK_CONFIG_ROOT = "ranks.staff";
    private static final String MAINTENANCE_CONFIG_ROOT = "maintenance";
    private static final String MODERATION_CONFIG_ROOT = "moderation";
    private static final String MAINTENANCE_COMMAND_PERMISSION = "hgloot.maintenance";
    private static final String DEFAULT_MAINTENANCE_BYPASS_PERMISSION = "hydra.maintenance.bypass";
    private static final String STAGED_VERSION_FILE = "staged-version.txt";
    private static final String LIVE_VERSION_FILE = "live-version.txt";
    private static final long STAGED_VERSION_CHECK_PERIOD_TICKS = 60L * 20L;
    private static final long STATS_RESET_ALL_CONFIRMATION_TTL_MS = 60_000L;
    private static final long REPORT_COOLDOWN_MS = 30_000L;
    private static final int REPORT_REASON_MAX_LENGTH = 256;
    private static final int MAX_STORED_REPORTS = 500;
    private static final String STATS_RESET_ALL_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @FunctionalInterface
    private interface CommandRoute {
        boolean handle(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    private interface TabRoute {
        List<String> complete(CommandSender sender, String[] args);
    }

    private MenuService menuService;
    private PlayerSettingsService playerSettingsService;
    private final Set<String> lockedModes = new HashSet<>();
    private LootTableService lootTableService;
    private ManagedLootService managedLootService;
    private CombatMasterService combatMasterService;

    /** Used only by the optional, admin-gated Combat Master model test companion. */
    public UUID spawnCombatMasterVisualTest(Location spawn) {
        return combatMasterService == null ? null : combatMasterService.spawnVisualTest(spawn);
    }

    public boolean removeCombatMasterVisualTest(UUID bossId) {
        return combatMasterService != null && combatMasterService.removeCombatMaster(bossId);
    }
    private BossRushService bossRushService;
    private VenomSpikeService venomSpikeService;
    private CombatChamberService combatChamberService;
    private KothService kothService;
    private AuctionHouseService auctionHouseService;
    private ProgressionService progressionService;
    private DailyRewardService dailyRewardService;
    private BattlePassService battlePassService;
    private QueueService queueService;
    private HungerGamesUtilityService hungerGamesUtilityService;
    private GameHubService gameHubService;
    private SpleefService spleefService;
    private VoidRouletteService voidRouletteService;
    private BridgeService bridgeService;
    private KitEditorService kitEditorService;
    private TransitionService transitionService;
    private MinigameRouletteService minigameRouletteService;
    private VictoryResultsService victoryResultsService;
    private FriendService friendService;
    private PartyService partyService;
    private MatchTeamService matchTeamService;
    private TeamProtectionService teamProtectionService;
    private LegendaryRecipeBookService legendaryRecipeBookService;
    private ZxdzeroEventsCoreService zxdzeroEventsCoreService;
    private HydraModuleManager moduleManager;
    private BukkitTask stagedVersionCheckTask;
    private String lastAnnouncedStagedVersion = "";
    private HydraRegistryHub registryHub;
    private AnnouncementService announcementService;
    private RotatingAnnouncementService rotatingAnnouncementService;
    private SituationalAnnouncementService situationalAnnouncementService;
    private HydraPlusService hydraPlusService;
    private HydraVoiceChatService hydraVoiceChatService;
    private NpcDialogueService npcDialogueService;
    private NpcService npcService;
    private GuideService guideService;
    private HubProtectionService hubProtectionService;
    private StoreFulfillmentService storeFulfillmentService;
    private NetworkNodeConfig networkNodeConfig;
    private NetworkBridgeService networkBridgeService;
    private Handler debugFileHandler;
    private final Map<String, CommandRoute> commandRoutes = new LinkedHashMap<>();
    private final Map<String, TabRoute> tabRoutes = new LinkedHashMap<>();
    private final Map<UUID, Location> backLocations = new HashMap<>();
    private final Set<UUID> backTrackingSuppression = new HashSet<>();
    private final Set<UUID> godModePlayers = new HashSet<>();
    private final Set<UUID> commandExitLocks = ConcurrentHashMap.newKeySet();
    private final HubExitConfirmation hubExitConfirmation = new HubExitConfirmation();
    private final Map<UUID, String> hydraRankAssignments = new HashMap<>();
    private final Map<UUID, String> staffRankAssignments = new HashMap<>();
    private final Map<UUID, Long> mutedUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, String> muteReasons = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reportCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<String, StatsResetAllConfirmation> statsResetAllConfirmations = new ConcurrentHashMap<>();

    private record StatsResetAllConfirmation(String token, long expiresAtMs) {
    }

    private static List<Map<String, Object>> defaultHungerGamesKothStrongPoints() {
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(strongPoint("Center", 0, 0));
        points.add(strongPoint("North", 0, -56));
        points.add(strongPoint("South", 0, 56));
        points.add(strongPoint("East", 56, 0));
        points.add(strongPoint("West", -56, 0));
        points.add(strongPoint("North-East", 56, -56));
        points.add(strongPoint("North-West", -56, -56));
        points.add(strongPoint("South-East", 56, 56));
        points.add(strongPoint("South-West", -56, 56));
        return points;
    }

    private static Map<String, Object> strongPoint(String name, int x, int z) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", name);
        point.put("x", x);
        point.put("z", z);
        return point;
    }

    static void migrateHungerGamesCountdown(org.bukkit.configuration.ConfigurationSection config) {
        String migration = "queue.hunger_games.migrations.separate_local_queues_countdown";
        if (!config.getBoolean(migration, false)) {
            if (config.getInt("queue.hunger_games.countdown_seconds", 10) == 10) {
                config.set("queue.hunger_games.countdown_seconds", 30);
            }
            config.set(migration, true);
        }
    }

    @Override
    public void onEnable() {
        initializeDebugLogFile();
        getConfig().addDefault("network.enabled", false);
        getConfig().addDefault("network.server_role", "STANDALONE");
        getConfig().addDefault("network.node_name", "standalone");
        getConfig().addDefault("network.coordinator_url", "http://127.0.0.1:8765");
        getConfig().addDefault("network.shared_secret", "CHANGE_ME");
        getConfig().addDefault("network.heartbeat_seconds", 2);
        getConfig().addDefault("network.request_timeout_millis", 3000);
        getConfig().addDefault("modules.ultraroyales.enabled", true);
        getConfig().addDefault("koth.countdown_seconds", 30);
        getConfig().addDefault("koth.capture_seconds", 20);
        getConfig().addDefault("queue.hunger_games.enabled", true);
        getConfig().addDefault("queue.hunger_games.map_selection_key", "outcove");
        getConfig().addDefault("queue.hunger_games.countdown_seconds", 30);
        getConfig().addDefault("queue.hunger_games.spawn_radius", 11);
        getConfig().addDefault("queue.hunger_games.border_size", 423);
        getConfig().addDefault("queue.hunger_games.border_min_size", 32);
        getConfig().addDefault("queue.hunger_games.koth_delay_seconds", 300);
        getConfig().addDefault("queue.hunger_games.koth_capture_seconds", 10);
        getConfig().addDefault("queue.hunger_games.koth_random_radius", 82);
        getConfig().addDefault("queue.hunger_games.koth_strong_points", defaultHungerGamesKothStrongPoints());
        getConfig().addDefault("queue.hunger_games.fill_chests", true);
        getConfig().addDefault("queue.hunger_games.keep_ready_arena", false);
        getConfig().addDefault("queue.hunger_games.start_freeze_seconds", 10);
        getConfig().addDefault("queue.hunger_games.combat_grace_seconds", 30);
        getConfig().addDefault("queue.hunger_games.placed_block_decay_seconds", 7);
        getConfig().addDefault(
            "queue.hunger_games.initial_loot_table_ids",
            List.of("hg_single")
        );
        getConfig().addDefault("queue.hunger_games.chest_refill_delay_seconds", 300);
        getConfig().addDefault("queue.hunger_games.chest_refill_table_id", "hg_refill");
        getConfig().addDefault(
            "queue.hunger_games.refill_loot_table_ids",
            List.of("hg_refill")
        );
        getConfig().addDefault("queue.hunger_games.chest_refill_fill_ratio", 1.0D);
        getConfig().addDefault("queue.hunger_games.disable_mob_spawns", true);
        getConfig().addDefault("queue.hunger_games.border_shrink_interval_seconds", 60);
        getConfig().addDefault("queue.hunger_games.border_shrink_step", 12.0D);
        getConfig().addDefault("queue.hunger_games.border_shrink_duration_seconds", 40);
        getConfig().addDefault("queue.rewards.win_silver.br", 250);
        getConfig().addDefault("queue.rewards.win_silver.hg", 250);
        getConfig().addDefault("queue.rewards.voice_multiplier", 1.20D);
        getConfig().addDefault("queue.fake_pcs.enabled", false);
        getConfig().addDefault("queue.fake_pcs.testing.stationary_plane", false);
        getConfig().addDefault("queue.fake_pcs.dive.animation_mode", "rig");
        getConfig().addDefault("queue.fake_pcs.dive.frame_ticks", 2);
        getConfig().addDefault("queue.fake_pcs.dive.frame_pose_times_seconds", List.of(0.08D, 0.33D));
        getConfig().addDefault("queue.fake_pcs.dive.frame_loop_seconds", 0.54D);
        getConfig().addDefault("queue.fake_pcs.dive.offset.x", 0.0D);
        getConfig().addDefault("queue.fake_pcs.dive.offset.y", 12.25D / 16.0D);
        getConfig().addDefault("queue.fake_pcs.dive.offset.z", 0.0D);
        getConfig().addDefault("queue.fake_pcs.dive.scale.x", 1.0D);
        getConfig().addDefault("queue.fake_pcs.dive.scale.y", 1.0D);
        getConfig().addDefault("queue.fake_pcs.dive.scale.z", 1.0D);
        getConfig().addDefault("queue.fake_pcs.dive.rotation.x", -90.0D);
        getConfig().addDefault("queue.fake_pcs.dive.rotation.y", 0.0D);
        getConfig().addDefault("queue.fake_pcs.dive.rotation.z", 0.0D);
        getConfig().addDefault("queue.fake_pcs.dive.follow_player_pitch", false);
        getConfig().addDefault("queue.fake_pcs.dive.pitch_bias_degrees", 0.0D);
        getConfig().addDefault("queue.fake_pcs.dive.yaw_bias_degrees", 180.0D);
        getConfig().addDefault("queue.fake_pcs.dive.gltf.path", "animations/plane_dive/Alternative.gltf");
        getConfig().addDefault("queue.fake_pcs.dive.gltf.loop_seconds", 0.54D);
        getConfig().addDefault("queue.match_world.generation_mode", "datapack");
        getConfig().addDefault("queue.match_world.datapack_source", "datapacks/hydra_worldgen.zip");
        getConfig().addDefault("queue.match_world.testing.force_plains_biomes", false);
        getConfig().addDefault("queue.match_world.br_terrain.profile", "hydra_wilds");
        getConfig().addDefault("queue.match_world.br_terrain.force_origin_spawn", true);
        getConfig().addDefault("queue.match_world.structure_zip.radius", 980);
        getConfig().addDefault("queue.match_world.structure_zip.center_exclusion_radius", 0);
        getConfig().addDefault("queue.match_world.structure_zip.place_all_ids", true);
        getConfig().addDefault("queue.match_world.cornucopia.dedicated_system", true);
        getConfig().addDefault("queue.match_world.cornucopia.spawn_structure", false);
        getConfig().addDefault("queue.match_world.cornucopia.forced_center", true);
        getConfig().addDefault("queue.match_world.cornucopia.flat_zone", true);
        getConfig().addDefault("queue.match_world.next_br_world_number", 1L);
        getConfig().addDefault("queue.match_world.next_hg_world_number", 1L);
        getConfig().addDefault("maintenance.enabled", false);
        getConfig().addDefault("maintenance.bypass_permission", DEFAULT_MAINTENANCE_BYPASS_PERMISSION);
        getConfig().addDefault(
            "maintenance.kick_message",
            List.of(
                "§8┃ §bHYDRA §8┃",
                "",
                "§7Maintenance mode active.",
                "§7Please check back soon."
            )
        );
        getConfig().addDefault("maintenance.allowed_players", new LinkedHashMap<String, Object>());
        getConfig().addDefault("store.pending_purchases_file", getDataFolder().toPath().resolve("pending-purchases.json").toString());
        getConfig().addDefault("announcements.rotating.enabled", true);
        getConfig().addDefault("announcements.rotating.interval-seconds", 300);
        getConfig().addDefault("announcements.rotating.messages", RotatingAnnouncementService.DEFAULT_MESSAGES);
        getConfig().addDefault("announcements.situational.recipe_assist.enabled", true);
        getConfig().addDefault("announcements.situational.recipe_assist.cooldown-seconds", 180);
        getConfig().addDefault("shards.rarity_weights.common", 50.0D);
        getConfig().addDefault("shards.rarity_weights.rare", 25.0D);
        getConfig().addDefault("shards.rarity_weights.epic", 15.0D);
        getConfig().addDefault("shards.rarity_weights.legendary", 8.0D);
        getConfig().addDefault("shards.rarity_weights.limited", 2.0D);
        getConfig().addDefault("economy.cosmetic_values.common", 500);
        getConfig().addDefault("economy.cosmetic_values.rare", 1000);
        getConfig().addDefault("economy.cosmetic_values.epic", 2500);
        getConfig().addDefault("economy.cosmetic_values.legendary", 5000);
        getConfig().addDefault("economy.cosmetic_values.limited", 10000);
        getConfig().addDefault("economy.cosmetic_values.limited_purchasable", false);
        getConfig().addDefault("results.victory.sounds.victory_primary.key", "minecraft:ui.toast.challenge_complete");
        getConfig().addDefault("results.victory.sounds.victory_primary.volume", 0.75D);
        getConfig().addDefault("results.victory.sounds.victory_primary.pitch", 1.0D);
        getConfig().addDefault("results.victory.sounds.victory_accent.key", "minecraft:entity.firework_rocket.large_blast");
        getConfig().addDefault("results.victory.sounds.victory_accent.volume", 0.28D);
        getConfig().addDefault("results.victory.sounds.victory_accent.pitch", 1.0D);
        getConfig().addDefault("results.victory.sounds.xp_tick.key", "minecraft:entity.experience_orb.pickup");
        getConfig().addDefault("results.victory.sounds.xp_tick.volume", 0.35D);
        getConfig().addDefault("results.victory.sounds.xp_tick.pitch", 0.85D);
        getConfig().addDefault("results.victory.sounds.xp_complete.key", "minecraft:entity.player.levelup");
        getConfig().addDefault("results.victory.sounds.xp_complete.volume", 0.20D);
        getConfig().addDefault("results.victory.sounds.xp_complete.pitch", 1.35D);
        getConfig().addDefault("results.victory.sounds.coins_tick.key", "minecraft:block.note_block.pling");
        getConfig().addDefault("results.victory.sounds.coins_tick.volume", 0.42D);
        getConfig().addDefault("results.victory.sounds.coins_tick.pitch", 0.90D);
        getConfig().addDefault("results.victory.sounds.fragments_tick.key", "minecraft:block.amethyst_block.chime");
        getConfig().addDefault("results.victory.sounds.fragments_tick.volume", 0.55D);
        getConfig().addDefault("results.victory.sounds.fragments_tick.pitch", 1.0D);
        migrateHungerGamesCountdown(getConfig());
        RotatingAnnouncementService.migrateLegacyDefaults(getConfig());
        getConfig().options().copyDefaults(true);
        saveConfig();
        this.networkNodeConfig = NetworkNodeConfig.load(getConfig());
        loadModeLocks();
        loadHydraRankAssignments();
        loadStaffRankAssignments();
        loadModerationState();
        this.lootTableService = new LootTableService(this);
        this.lootTableService.loadAllTables();
        this.managedLootService = new ManagedLootService(this, lootTableService);
        this.friendService = new FriendService(this);
        this.playerSettingsService = new PlayerSettingsService(this);
        this.partyService = new PartyService();
        this.partyService.setInviteAllowed(id -> playerSettingsService.enabled(id, PlayerSettingsService.Toggle.PARTY_INVITES));
        this.matchTeamService = new MatchTeamService();
        this.teamProtectionService = new TeamProtectionService(this);
        this.menuService = new MenuService(this, lootTableService, friendService, partyService);
        this.gameHubService = new GameHubService(this);
        this.combatMasterService = new CombatMasterService(this);
        this.bossRushService = new BossRushService(this, combatMasterService);
        this.venomSpikeService = new VenomSpikeService(this);
        this.combatChamberService = new CombatChamberService(this, venomSpikeService);
        this.kothService = new KothService(this);
        if (networkNodeConfig.role().hungerGamesServices()) {
            this.kothService.start();
        }
        this.auctionHouseService = new AuctionHouseService(this);
        this.auctionHouseService.start();
        this.progressionService = new ProgressionService(this, auctionHouseService);
        this.progressionService.setMenuService(menuService);
        this.dailyRewardService = new DailyRewardService(this, progressionService);
        this.battlePassService = new BattlePassService(this, auctionHouseService, menuService);
        this.victoryResultsService = new VictoryResultsService(this);
        this.queueService = new QueueService(this, auctionHouseService);
        this.hungerGamesUtilityService = new HungerGamesUtilityService(this);
        this.networkBridgeService = new NetworkBridgeService(this, networkNodeConfig);
        this.spleefService = new SpleefService(this);
        this.voidRouletteService = new VoidRouletteService(this);
        this.bridgeService = new BridgeService(this);
        this.kitEditorService = new KitEditorService(this);
        this.transitionService = new TransitionService(this);
        this.kitEditorService.setTransitionService(transitionService);
        this.bridgeService.setKitEditorService(kitEditorService);
        this.menuService.setKitEditorService(kitEditorService);
        this.minigameRouletteService = new MinigameRouletteService(this, partyService);
        this.hydraPlusService = new HydraPlusService(this);
        this.hydraVoiceChatService = new HydraVoiceChatService(this);
        this.npcDialogueService = new NpcDialogueService(this);
        this.npcService = new NpcService(this, npcDialogueService, dailyRewardService);
        this.guideService = new GuideService(npcDialogueService);
        this.hubProtectionService = new HubProtectionService(this, menuService, queueService, npcService);
        this.storeFulfillmentService = new StoreFulfillmentService(this);
        this.menuService.setManagedLootService(managedLootService);
        this.queueService.setManagedLootService(managedLootService);
        this.legendaryRecipeBookService = new LegendaryRecipeBookService(this);
        this.menuService.setHungerGamesUtilityRegistry(legendaryRecipeBookService::recipeRegistry);
        this.announcementService = new AnnouncementService(this);
        this.queueService.setMenuService(menuService);
        this.queueService.setPartyService(partyService);
        this.queueService.setKothService(kothService);
        this.queueService.setProgressionService(progressionService);
        this.combatChamberService.setProgressionService(progressionService);
        this.kothService.setQueueService(queueService);
        this.rotatingAnnouncementService = new RotatingAnnouncementService(this);
        this.situationalAnnouncementService = new SituationalAnnouncementService(
            this,
            legendaryRecipeBookService::recipeRegistry
        );
        this.partyService.start(this, this::isPartyMutationLocked);
        if (networkNodeConfig.role().gameHubServices()) {
            this.gameHubService.start();
        }
        this.queueService.start();
        this.hungerGamesUtilityService.start();
        this.networkBridgeService.start();
        if (networkNodeConfig.role().legacyMinigameServices()) {
            this.bossRushService.start();
            this.spleefService.start();
            this.voidRouletteService.start();
            this.bridgeService.start();
            this.minigameRouletteService.start();
        }
        this.auctionHouseService.setQueueService(queueService);
        this.auctionHouseService.setMenuService(menuService);
        this.menuService.setSpleefService(spleefService);
        this.menuService.setBridgeService(bridgeService);
        this.menuService.setMinigameRouletteService(minigameRouletteService);
        this.zxdzeroEventsCoreService = new ZxdzeroEventsCoreService(this);
        this.zxdzeroEventsCoreService.start();
        this.queueService.setZxdzeroEventsCoreService(zxdzeroEventsCoreService);
        this.playerSettingsService.start();
        this.hydraPlusService.start();
        applyVoiceChatPlayerPermissionDefaults();
        this.hydraVoiceChatService.start();
        if (networkNodeConfig.role().hubServices()) {
            this.npcService.start();
            this.rotatingAnnouncementService.start();
            this.situationalAnnouncementService.start();
        }
        applyGlobalWorldRules();

        if (networkNodeConfig.role().hubServices() || networkNodeConfig.role().gameHubServices()) {
            getServer().getPluginManager().registerEvents(new MenuListener(menuService), this);
            getServer().getPluginManager().registerEvents(kitEditorService, this);
        }
        getServer().getPluginManager().registerEvents(transitionService, this);
        if (networkNodeConfig.role().legacyMinigameServices()) {
            getServer().getPluginManager().registerEvents(combatMasterService, this);
            getServer().getPluginManager().registerEvents(bossRushService, this);
            getServer().getPluginManager().registerEvents(combatChamberService, this);
        }
        if (networkNodeConfig.role().hungerGamesServices()) {
            getServer().getPluginManager().registerEvents(kothService, this);
        }
        getServer().getPluginManager().registerEvents(auctionHouseService, this);
        getServer().getPluginManager().registerEvents(progressionService, this);
        getServer().getPluginManager().registerEvents(battlePassService, this);
        getServer().getPluginManager().registerEvents(victoryResultsService, this);
        getServer().getPluginManager().registerEvents(queueService, this);
        getServer().getPluginManager().registerEvents(hungerGamesUtilityService, this);
        getServer().getPluginManager().registerEvents(networkBridgeService, this);
        if (networkNodeConfig.role().gameHubServices()) {
            getServer().getPluginManager().registerEvents(gameHubService, this);
        }
        if (networkNodeConfig.role().legacyMinigameServices()) {
            getServer().getPluginManager().registerEvents(spleefService, this);
            getServer().getPluginManager().registerEvents(voidRouletteService, this);
            getServer().getPluginManager().registerEvents(bridgeService, this);
            getServer().getPluginManager().registerEvents(minigameRouletteService, this);
        }
        getServer().getPluginManager().registerEvents(teamProtectionService, this);
        getServer().getPluginManager().registerEvents(playerSettingsService, this);
        getServer().getPluginManager().registerEvents(hydraPlusService, this);
        getServer().getPluginManager().registerEvents(hydraVoiceChatService, this);
        if (networkNodeConfig.role().hubServices()) {
            getServer().getPluginManager().registerEvents(npcService, this);
        }
        getServer().getPluginManager().registerEvents(hubProtectionService, this);
        if (networkNodeConfig.role().hubServices()) {
            getServer().getPluginManager().registerEvents(legendaryRecipeBookService, this);
            getServer().getPluginManager().registerEvents(situationalAnnouncementService, this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        initializeRoutes();
        registerCommands(commandRoutes.keySet().toArray(String[]::new));

        this.registryHub = new HydraRegistryHub();
        this.moduleManager = new HydraModuleManager(getLogger());
        if (getConfig().getBoolean("modules.ultraroyales.enabled", true)) {
            try {
                this.moduleManager.register(new UltraRoyales(this, registryHub));
            } catch (Throwable throwable) {
                getLogger().log(Level.SEVERE, "[HydraModules] UltraRoyales module registration failed.", throwable);
            }
        }
        this.moduleManager.enableAll();
        removeVanillaShieldRecipe();
        if (networkNodeConfig.role().hubServices()) {
            this.legendaryRecipeBookService.start();
        }

        getLogger().info("HydraMain enabled. Loaded " + lootTableService.loadedTableCount()
            + " loot tables (external=" + lootTableService.loadedFromExternalCount()
            + ", embedded=" + lootTableService.loadedFromEmbeddedCount()
            + ", missing=" + lootTableService.missingTableCount() + ").");
        announcementService.admin("System", "HydraMain v" + getDescription().getVersion() + " is running.");
        startStagedVersionMonitor();
    }

    private void removeVanillaShieldRecipe() {
        Bukkit.removeRecipe(NamespacedKey.minecraft("shield"));
    }

    private void applyVoiceChatPlayerPermissionDefaults() {
        setPermissionDefault("voicechat.listen", PermissionDefault.TRUE);
        setPermissionDefault("voicechat.speak", PermissionDefault.TRUE);
        setPermissionDefault("voicechat.groups", PermissionDefault.TRUE);
        getLogger().info("[VoiceChat] Normal-player defaults enabled for listen, speak, and groups.");
    }

    private void setPermissionDefault(String node, PermissionDefault permissionDefault) {
        Permission permission = getServer().getPluginManager().getPermission(node);
        if (permission == null) {
            permission = new Permission(node);
            getServer().getPluginManager().addPermission(permission);
        }
        permission.setDefault(permissionDefault);
        getServer().getPluginManager().recalculatePermissionDefaults(permission);
    }

    @Override
    public void onDisable() {
        if (playerSettingsService != null) {
            playerSettingsService.shutdown();
        }
        if (kitEditorService != null) {
            kitEditorService.shutdown();
        }
        if (transitionService != null) {
            transitionService.shutdown();
        }
        if (networkBridgeService != null) {
            networkBridgeService.shutdown();
        }
        if (stagedVersionCheckTask != null) {
            stagedVersionCheckTask.cancel();
            stagedVersionCheckTask = null;
        }
        if (rotatingAnnouncementService != null) {
            rotatingAnnouncementService.shutdown();
        }
        if (situationalAnnouncementService != null) {
            situationalAnnouncementService.shutdown();
        }
        if (legendaryRecipeBookService != null) {
            legendaryRecipeBookService.shutdown();
        }
        hubExitConfirmation.clearAll();
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.hasGravity()) {
                player.setGravity(true);
            }
            player.setFallDistance(0.0F);
        }
        if (menuService != null) {
            menuService.shutdown();
        }
        if (hydraPlusService != null) {
            hydraPlusService.shutdown();
        }
        if (hydraVoiceChatService != null) {
            hydraVoiceChatService.shutdown();
        }
        if (npcService != null) {
            npcService.shutdown();
        }
        if (venomSpikeService != null) {
            venomSpikeService.shutdown();
        }
        if (combatChamberService != null) {
            combatChamberService.shutdown();
        }
        if (bossRushService != null) {
            bossRushService.shutdown();
        }
        if (combatMasterService != null) {
            combatMasterService.shutdown();
        }
        if (kothService != null) {
            kothService.shutdown();
        }
        if (auctionHouseService != null) {
            auctionHouseService.shutdown();
        }
        if (progressionService != null) {
            progressionService.shutdown();
        }
        if (battlePassService != null) {
            battlePassService.shutdown();
        }
        if (victoryResultsService != null) {
            victoryResultsService.shutdown();
        }
        if (minigameRouletteService != null) {
            minigameRouletteService.shutdown();
        }
        if (spleefService != null) {
            spleefService.shutdown();
        }
        if (voidRouletteService != null) {
            voidRouletteService.shutdown();
        }
        if (bridgeService != null) {
            bridgeService.shutdown();
        }
        if (queueService != null) {
            queueService.shutdown();
        }
        if (hungerGamesUtilityService != null) {
            hungerGamesUtilityService.shutdown();
        }
        if (gameHubService != null) {
            gameHubService.shutdown();
        }
        if (friendService != null) {
            friendService.shutdown();
        }
        if (partyService != null) {
            partyService.shutdown();
        }
        if (matchTeamService != null) {
            matchTeamService.shutdown();
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (zxdzeroEventsCoreService != null) {
            zxdzeroEventsCoreService.shutdown();
        }
        shutdownDebugLogFile();
        getLogger().info("HydraMain disabled.");
    }

    private void initializeDebugLogFile() {
        try {
            Path logsDir = getDataFolder().toPath().resolve("logs");
            Files.createDirectories(logsDir);
            Path pattern = logsDir.resolve("hydramain-debug-%g.log");
            FileHandler handler = new FileHandler(pattern.toString(), 2 * 1024 * 1024, 5, true);
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            getLogger().addHandler(handler);
            debugFileHandler = handler;
            getLogger().info("HydraMain debug logs will also be written to " + logsDir + ".");
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Failed to initialize HydraMain debug log file.", ex);
        }
    }

    private void shutdownDebugLogFile() {
        if (debugFileHandler == null) {
            return;
        }
        getLogger().removeHandler(debugFileHandler);
        debugFileHandler.close();
        debugFileHandler = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (sender instanceof Player player && kitEditorService != null
            && kitEditorService.beforeCommand(player, name)) {
            return true;
        }
        if (sender instanceof Player player && minigameRouletteService != null) {
            if (Set.of("br", "hg", "spleef", "voidroulette", "maceroulette", "bridge")
                .contains(name) && minigameRouletteService.blockOtherModeEntry(player)) {
                return true;
            }
        }
        CommandRoute route = commandRoutes.get(name);
        return route != null && route.handle(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        TabRoute route = tabRoutes.get(name);
        return route == null ? List.of() : route.complete(sender, args);
    }

    private void initializeRoutes() {
        commandRoutes.clear();
        commandRoutes.put("menu", (sender, args) -> handleMenu(sender));
        commandRoutes.put("combatchamber", this::handleCombatChamber);
        commandRoutes.put("koth", this::handleKoth);
        commandRoutes.put("ah", this::handleAuctionHouse);
        commandRoutes.put("sell", this::handleSellShortcut);
        commandRoutes.put("pay", this::handlePay);
        commandRoutes.put("shard", this::handleShard);
        commandRoutes.put("battlepass", (sender, args) -> battlePassService.handlePlayerCommand(sender));
        commandRoutes.put("spectate", this::handleSpectate);
        commandRoutes.put("wins", this::handleWinsAdminShortcut);
        commandRoutes.put("border", this::handleBorder);
        commandRoutes.put("admin", this::handleAdmin);
        commandRoutes.put("br", this::handleBattleRoyale);
        commandRoutes.put("hg", this::handleHungerGames);
        commandRoutes.put("recipes", this::handleLegendaryRecipes);
        commandRoutes.put("spleef", this::handleSpleef);
        commandRoutes.put("voidroulette", this::handleVoidRoulette);
        commandRoutes.put("maceroulette", this::handleVoidRoulette);
        commandRoutes.put("bridge", this::handleBridge);
        commandRoutes.put("roulette", (sender, args) -> minigameRouletteService != null
            && minigameRouletteService.handlePlayerCommand(sender, args));
        commandRoutes.put("leave", this::handleLeave);
        commandRoutes.put("hub", (sender, args) -> handleHub(sender));
        commandRoutes.put("world", this::handleWorld);
        commandRoutes.put("resetlegendarycooldowns", this::handleResetLegendaryCooldowns);
        commandRoutes.put("pedestal", this::handlePedestal);
        commandRoutes.put("cosmetics", (sender, args) -> handleCosmetic(sender));
        commandRoutes.put("friend", this::handleFriend);
        commandRoutes.put("party", this::handleParty);
        commandRoutes.put("social", (sender, args) -> handleSocial(sender));
        commandRoutes.put("pc", (sender, args) -> handleChatToggle(sender, true));
        commandRoutes.put("tc", (sender, args) -> handleChatToggle(sender, false));
        commandRoutes.put("partychat", (sender, args) -> handleChatToggle(sender, true));
        commandRoutes.put("teamchat", (sender, args) -> handleChatToggle(sender, false));
        commandRoutes.put("team", this::handleMatchTeam);
        commandRoutes.put("settings", this::handleSettings);
        commandRoutes.put("pversion", (sender, args) -> handlePluginVersion(sender));
        commandRoutes.put("back", this::handleBack);
        commandRoutes.put("gm", this::handleGm);
        commandRoutes.put("feed", this::handleFeed);
        commandRoutes.put("heal", this::handleHeal);
        commandRoutes.put("fly", this::handleFly);
        commandRoutes.put("god", this::handleGod);
        commandRoutes.put("announce", this::handleAnnounce);
        commandRoutes.put("revive", this::handleRevive);
        commandRoutes.put("rejoin", this::handleRejoin);
        commandRoutes.put("npc", this::handleNpc);
        commandRoutes.put("guide", (sender, args) -> guideService.handleCommand(sender, args));
        commandRoutes.put("leaderboard", this::handleLeaderboard);
        commandRoutes.put("rank", this::handleRank);
        commandRoutes.put("maintenance", this::handleMaintenance);
        commandRoutes.put("warn", this::handleWarn);
        commandRoutes.put("warnings", this::handleWarnings);
        commandRoutes.put("mute", this::handleMute);
        commandRoutes.put("unmute", this::handleUnmute);
        commandRoutes.put("tempban", this::handleTempBan);
        commandRoutes.put("unban", this::handleUnban);
        commandRoutes.put("kick", this::handleKick);
        commandRoutes.put("report", this::handleReport);
        commandRoutes.put("reports", this::handleReports);
        commandRoutes.put("stats", this::handleStats);

        tabRoutes.clear();
        tabRoutes.put("admin", this::completeAdmin);
        tabRoutes.put("br", this::completeBattleRoyale);
        tabRoutes.put("hg", this::completeHungerGames);
        tabRoutes.put("spleef", (sender, args) -> !sender.hasPermission("hgloot.admin") || spleefService == null ? List.of() : spleefService.completeCommand(args));
        tabRoutes.put("voidroulette", (sender, args) -> !sender.hasPermission("hgloot.admin") || voidRouletteService == null ? List.of() : voidRouletteService.completeCommand(args));
        tabRoutes.put("maceroulette", (sender, args) -> !sender.hasPermission("hgloot.admin") || voidRouletteService == null ? List.of() : voidRouletteService.completeCommand(args));
        tabRoutes.put("bridge", (sender, args) -> !sender.hasPermission("hgloot.admin") || bridgeService == null ? List.of() : bridgeService.completeCommand(args));
        tabRoutes.put("roulette", (sender, args) -> minigameRouletteService == null
            ? List.of()
            : minigameRouletteService.completePlayerCommand(args, sender.hasPermission("hgloot.admin.roulette")));
        tabRoutes.put("leave", (sender, args) -> List.of());
        tabRoutes.put("friend", this::completeFriend);
        tabRoutes.put("party", this::completeParty);
        tabRoutes.put("pc", (sender, args) -> List.of());
        tabRoutes.put("tc", (sender, args) -> List.of());
        tabRoutes.put("partychat", (sender, args) -> List.of());
        tabRoutes.put("teamchat", (sender, args) -> List.of());
        tabRoutes.put("team", (sender, args) -> List.of());
        tabRoutes.put("settings", (sender, args) -> {
            if (args.length == 1) {
                return completeFrom(args, 1, List.of("effects"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("effects")) {
                return completeFrom(args, 2, List.of("full", "reduced", "minimal"));
            }
            return List.of();
        });
        tabRoutes.put("combatchamber", (sender, args) -> completeCombatChamber(args));
        tabRoutes.put("koth", (sender, args) -> completeKoth(args));
        tabRoutes.put("ah", this::completeCasino);
        tabRoutes.put("sell", this::completeSellShortcut);
        tabRoutes.put("pay", (sender, args) -> completePlayerAndAmount(args, sender instanceof Player player ? player.getUniqueId() : null));
        tabRoutes.put("shard", this::completeShard);
        tabRoutes.put("battlepass", (sender, args) -> List.of());
        tabRoutes.put("spectate", this::completeSpectate);
        tabRoutes.put("wins", this::completeWinsAdminShortcut);
        tabRoutes.put("border", this::completeBorder);
        tabRoutes.put("world", (sender, args) -> completeWorld(args));
        tabRoutes.put("resetlegendarycooldowns", (sender, args) -> completeFrom(args, 1,
            sender instanceof Player player ? onlinePlayerNamesExcluding(player.getUniqueId()) : onlinePlayerNames()));
        tabRoutes.put("pedestal", (sender, args) -> zxdzeroEventsCoreService == null ? List.of() : zxdzeroEventsCoreService.completePedestal(args));
        tabRoutes.put("revive", (sender, args) -> completeFrom(args, 1, onlinePlayerNames()));
        tabRoutes.put("npc", (sender, args) -> npcService == null ? List.of() : npcService.complete(sender, args));
        tabRoutes.put("leaderboard", this::completeLeaderboard);
        tabRoutes.put("rank", this::completeRank);
        tabRoutes.put("maintenance", (sender, args) -> completeMaintenance(args));
        tabRoutes.put("warn", this::completeWarn);
        tabRoutes.put("warnings", this::completeWarnings);
        tabRoutes.put("mute", this::completeMute);
        tabRoutes.put("unmute", this::completeUnmute);
        tabRoutes.put("tempban", this::completeTempBan);
        tabRoutes.put("unban", this::completeUnban);
        tabRoutes.put("kick", this::completeKick);
        tabRoutes.put("report", this::completeReport);
        tabRoutes.put("reports", this::completeReports);
        tabRoutes.put("stats", this::completeStats);
    }

    private boolean handleNpc(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.npc", "You do not have permission to use this.")) {
            return true;
        }
        if (npcService == null) {
            sender.sendMessage(ChatColor.RED + "NPC service is unavailable right now.");
            return true;
        }
        return npcService.handleCommand(sender, args);
    }

    private Player requirePlayer(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ChatColor.RED + message);
        return null;
    }

    private boolean requirePermission(CommandSender sender, String permission, String message) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + message);
        return false;
    }

    private boolean requireAnyPermission(CommandSender sender, String message, String... permissions) {
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + message);
        return false;
    }

    private void startStagedVersionMonitor() {
        writeLiveVersionMarker();
        checkStagedVersionAndNotifyAdmins(false);
        stagedVersionCheckTask = Bukkit.getScheduler().runTaskTimer(
            this,
            () -> checkStagedVersionAndNotifyAdmins(false),
            STAGED_VERSION_CHECK_PERIOD_TICKS,
            STAGED_VERSION_CHECK_PERIOD_TICKS
        );
    }

    private void writeLiveVersionMarker() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve(LIVE_VERSION_FILE), runningPluginVersion());
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not write HydraMain live version marker.", exception);
        }
    }

    private void checkStagedVersionAndNotifyAdmins(boolean force) {
        String stagedVersion = stagedPluginVersion();
        if (!isUpdateStaged(stagedVersion)) {
            return;
        }
        if (!force && stagedVersion.equals(lastAnnouncedStagedVersion)) {
            return;
        }
        lastAnnouncedStagedVersion = stagedVersion;
        String message = stagedUpdateMessage(stagedVersion);
        getLogger().warning(ChatColor.stripColor(message));
        for (Player player : getServer().getOnlinePlayers()) {
            if (shouldReceiveStagedVersionWarning(player)) {
                player.sendMessage(message);
            }
        }
    }

    private void notifyPlayerIfUpdateStaged(Player player) {
        if (player == null) {
            return;
        }
        if (!shouldReceiveStagedVersionWarning(player)) {
            return;
        }
        String stagedVersion = stagedPluginVersion();
        if (isUpdateStaged(stagedVersion)) {
            player.sendMessage(stagedUpdateMessage(stagedVersion));
        }
    }

    private boolean shouldReceiveStagedVersionWarning(Player player) {
        return player != null && hasStaffServerManagementAccess(player.getUniqueId());
    }

    private String stagedPluginVersion() {
        Path path = getDataFolder().toPath().resolve(STAGED_VERSION_FILE);
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path).trim();
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not read HydraMain staged version marker.", exception);
            return "";
        }
    }

    private boolean isUpdateStaged(String stagedVersion) {
        return stagedVersion != null
            && !stagedVersion.isBlank()
            && comparePluginVersions(stagedVersion, runningPluginVersion()) > 0;
    }

    private String runningPluginVersion() {
        return getDescription().getVersion();
    }

    private String stagedUpdateMessage(String stagedVersion) {
        return HydraIcons.EXCLAMATION + " "
            + ChatColor.YELLOW + "HydraMain update ready: "
            + ChatColor.GRAY + runningPluginVersion()
            + ChatColor.DARK_GRAY + " -> "
            + ChatColor.GREEN + stagedVersion
            + ChatColor.RED + " restart required.";
    }

    private static int comparePluginVersions(String left, String right) {
        int[] leftParts = parsePluginVersionParts(left);
        int[] rightParts = parsePluginVersionParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.length ? leftParts[i] : 0;
            int rightPart = i < rightParts.length ? rightParts[i] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int[] parsePluginVersionParts(String version) {
        if (version == null || version.isBlank()) {
            return new int[0];
        }
        String[] rawParts = version.trim().split("\\.");
        int[] parts = new int[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            String digits = rawParts[i].replaceAll("[^0-9].*$", "");
            if (digits.isBlank()) {
                parts[i] = 0;
                continue;
            }
            try {
                parts[i] = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    private boolean openNavigator(Player player) {
        menuService.refreshPlayerMenuCompass(player);
        menuService.openPlayerMenu(player);
        return true;
    }

    private boolean handlePluginVersion(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "HydraMain " + ChatColor.GRAY + "version "
            + ChatColor.GOLD + runningPluginVersion());
        String stagedVersion = stagedPluginVersion();
        if (isUpdateStaged(stagedVersion)) {
            sender.sendMessage(stagedUpdateMessage(stagedVersion));
        } else if (!stagedVersion.isBlank()) {
            sender.sendMessage(ChatColor.GRAY + "Staged version marker: " + ChatColor.GREEN + stagedVersion
                + ChatColor.GRAY + " (no restart needed).");
        }
        return true;
    }

    private void sendFriendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "[Friends] Commands");
        player.sendMessage(ChatColor.GRAY + "/friend add <player>");
        player.sendMessage(ChatColor.GRAY + "/friend accept <player>");
        player.sendMessage(ChatColor.GRAY + "/friend deny <player>");
        player.sendMessage(ChatColor.GRAY + "/friend remove <player>");
        player.sendMessage(ChatColor.GRAY + "/friend block <player>, /friend unblock <player>");
        player.sendMessage(ChatColor.GRAY + "/friend list");
        player.sendMessage(ChatColor.GRAY + "/friend requests");
    }

    private void sendPartyHelp(Player player) {
        player.sendMessage(ChatColor.GRAY + "/party create, /party join <leader>, /party invite <player>, /party accept <player>, /party deny <player>");
        player.sendMessage(ChatColor.GRAY + "/party list, /party leave, /party disband, /party kick <player>, /party promote <player>, /party requests");
    }

    private void sendPartyLocked(Player player) {
        player.sendMessage(HydraIcons.LOCKED + " " + ChatColor.RED
            + "Party changes are locked while any member is in an active match.");
    }

    private void sendBorderHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage:");
        player.sendMessage(ChatColor.GRAY + "/border info");
        player.sendMessage(ChatColor.GRAY + "/border set <size> [seconds]");
        player.sendMessage(ChatColor.GRAY + "/border add <delta> [seconds]");
        player.sendMessage(ChatColor.GRAY + "/border addseconds <seconds>");
        player.sendMessage(ChatColor.GRAY + "/border removeseconds <seconds>");
        player.sendMessage(ChatColor.GRAY + "/border center <x> <z>");
        player.sendMessage(ChatColor.GRAY + "/border centerhere");
    }

    private boolean handleMenu(CommandSender sender) {
        Player player = requirePlayer(sender, "Only players can open the menu.");
        if (player == null) {
            return true;
        }
        if (!requireAnyPermission(player, "You do not have permission to use this.", "hgloot.playermenu", "hgloot.menu")) {
            return true;
        }
        return openNavigator(player);
    }

    private boolean handleSocial(CommandSender sender) {
        Player player = requirePlayer(sender, "Only players can open the social menu.");
        if (player == null) return true;
        if (!requirePermission(player, "hgloot.playermenu", "You do not have permission to use this.")) return true;
        menuService.openPlayerSocialMenu(player);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("maintenance")) {
            return handleMaintenance(sender, trimLeadingArg(args));
        }
        if (sender instanceof ConsoleCommandSender
            && args.length >= 3
            && args[0].equalsIgnoreCase("br")
            && args[1].equalsIgnoreCase("switch")) {
            queueService.selectBattleRoyaleTerrainProfile(args[2], sender);
            return true;
        }
        Player player = requirePlayer(sender, "Only players can open the admin menu.");
        if (player == null) {
            return true;
        }
        boolean fullAdmin = player.hasPermission("hgloot.admin");
        boolean moderationAdmin = player.hasPermission("hgloot.moderation.menu");
        boolean bossAdmin = player.hasPermission("hgloot.boss");
        if (!fullAdmin && !moderationAdmin && !bossAdmin) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this.");
            return true;
        }
        if (args.length == 0) {
            if (!fullAdmin && bossAdmin) {
                menuService.openAdminBossToolsMenu(player);
                return true;
            }
            menuService.openAdminMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (!fullAdmin && bossAdmin) {
                    menuService.openAdminBossToolsMenu(player);
                    return true;
                }
                menuService.openAdminMenu(player);
                return true;
            }
            case "transition" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to test transitions.");
                    return true;
                }
                if (args.length != 2 || !args[1].equalsIgnoreCase("test")) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin transition test");
                    return true;
                }
                if (!transitionService.transition(player, TransitionService.Preset.NORMAL, () -> {})) {
                    player.sendMessage(ChatColor.RED + "A screen transition is already running.");
                }
                return true;
            }
            case "warnings", "moderation", "reports" -> {
                menuService.openAdminWarningsMenu(player);
                return true;
            }
            case "set" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use admin stat controls.");
                    return true;
                }
                return handleAdminSetShortcut(player, trimLeadingArg(args));
            }
            case "gift" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to gift shards.");
                    return true;
                }
                return handleAdminGift(player, trimLeadingArg(args));
            }
            case "battlepass" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to manage the Battle Pass.");
                    return true;
                }
                return battlePassService.handleAdminCommand(player, trimLeadingArg(args));
            }
            case "br" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use match admin tools.");
                    return true;
                }
                if (args.length == 1) {
                    menuService.openAdminBattleRoyaleToolsMenu(player);
                    return true;
                }
                return handleQueueAdminAlias(player, QueueService.QueueType.SOLO, trimLeadingArg(args));
            }
            case "hg" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use match admin tools.");
                    return true;
                }
                if (args.length == 1) {
                    menuService.openAdminHungerGamesToolsMenu(player);
                    return true;
                }
                return handleQueueAdminAlias(player, QueueService.QueueType.HUNGERGAMES, trimLeadingArg(args));
            }
            case "koth" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use KOTH admin tools.");
                    return true;
                }
                return handleAdminKoth(player, trimLeadingArg(args));
            }
            case "bossrush" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use Boss Rush admin tools.");
                    return true;
                }
                if (bossRushService == null) {
                    player.sendMessage(ChatColor.RED + "Boss Rush is unavailable right now.");
                    return true;
                }
                return bossRushService.handleAdminCommand(player, trimLeadingArg(args));
            }
            case "roulette" -> {
                if (!player.hasPermission("hgloot.admin.roulette")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use Roulette admin tools.");
                    return true;
                }
                return minigameRouletteService.handleAdminCommand(player, trimLeadingArg(args));
            }
            case "minigame", "minigames" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use minigame admin tools.");
                    return true;
                }
                if (args.length == 1) {
                    menuService.openAdminMinigamesToolsMenu(player);
                    return true;
                }
                String minigame = args[1].toLowerCase(Locale.ROOT);
                if (minigame.equals("smenu")) {
                    if (minigameRouletteService == null) {
                        player.sendMessage(ChatColor.RED + "Minigame Roulette is unavailable right now.");
                        return true;
                    }
                    minigameRouletteService.openIntermissionPreview(player);
                    return true;
                }
                if (minigame.equals("podium")) {
                    if (minigameRouletteService == null) {
                        player.sendMessage(ChatColor.RED + "Minigame Roulette is unavailable right now.");
                        return true;
                    }
                    minigameRouletteService.openPodiumPreview(player);
                    return true;
                }
                if (minigame.equals("skip") || minigame.equals("ignorepr")) {
                    if (minigameRouletteService == null) {
                        player.sendMessage(ChatColor.RED + "Minigame Roulette is unavailable right now.");
                        return true;
                    }
                    return minigameRouletteService.handleAdminCommand(player,
                        new String[] {minigame});
                }
                if (minigame.equals("voidroulette") || minigame.equals("maceroulette")) {
                    if (voidRouletteService == null) {
                        player.sendMessage(ChatColor.RED + "Void Roulette is unavailable right now.");
                        return true;
                    }
                    return voidRouletteService.handleAdminCommand(player, trimLeadingArg(args));
                }
                if (minigame.equals("bridge")) {
                    if (bridgeService == null) {
                        player.sendMessage(ChatColor.RED + "Bridge is unavailable right now.");
                        return true;
                    }
                    return bridgeService.handleAdminCommand(player, trimLeadingArg(args));
                }
                if (spleefService == null) {
                    player.sendMessage(ChatColor.RED + "Spleef is unavailable right now.");
                    return true;
                }
                return spleefService.handleAdminCommand(player, trimLeadingArg(args));
            }
            case "spleef" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use Spleef admin tools.");
                    return true;
                }
                if (spleefService == null) {
                    player.sendMessage(ChatColor.RED + "Spleef is unavailable right now.");
                    return true;
                }
                return spleefService.handleAdminCommand(player, args);
            }
            case "vr", "voidroulette", "maceroulette" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use Void Roulette admin tools.");
                    return true;
                }
                if (voidRouletteService == null) {
                    player.sendMessage(ChatColor.RED + "Void Roulette is unavailable right now.");
                    return true;
                }
                return voidRouletteService.handleAdminCommand(player, args);
            }
            case "bridge" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use Bridge admin tools.");
                    return true;
                }
                if (bridgeService == null) {
                    player.sendMessage(ChatColor.RED + "Bridge is unavailable right now.");
                    return true;
                }
                return bridgeService.handleAdminCommand(player, args);
            }
            case "lock" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to lock game modes.");
                    return true;
                }
                return handleModeLock(player, trimLeadingArg(args), true);
            }
            case "unlock" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to unlock game modes.");
                    return true;
                }
                return handleModeLock(player, trimLeadingArg(args), false);
            }
            case "locks" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to view mode locks.");
                    return true;
                }
                return handleModeLockList(player);
            }
            case "bosses" -> {
                if (!fullAdmin && !bossAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use boss admin tools.");
                    return true;
                }
                return handleAdminBosses(player, trimLeadingArg(args));
            }
            case "weapons" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use weapon admin tools.");
                    return true;
                }
                menuService.openAdminCombatToolsMenu(player);
                return true;
            }
            case "waves" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use wave admin tools.");
                    return true;
                }
                player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Legacy wave controls were removed. Use /combatchamber for the current test slice.");
                return true;
            }
            case "structures" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use structure admin tools.");
                    return true;
                }
                menuService.openStructureToolsMenu(player);
                return true;
            }
            case "cosmetics" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use cosmetic admin tools.");
                    return true;
                }
                if (args.length == 1 || args[1].equalsIgnoreCase("menu")) {
                    menuService.openAdminCosmeticsPlayerListMenu(player);
                    return true;
                }
                return handleCosmeticsAdmin(player, trimLeadingArg(args));
            }
            case "economy" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use economy admin tools.");
                    return true;
                }
                menuService.openAdminEconomyToolsMenu(player);
                return true;
            }
            case "wallet" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use wallet admin tools.");
                    return true;
                }
                if (args.length == 1 || args[1].equalsIgnoreCase("menu")) {
                    menuService.openAdminWalletToolsMenu(player);
                    return true;
                }
                return handleWalletAdmin(player, trimLeadingArg(args), "/admin wallet");
            }
            case "ah" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use Auction House admin tools.");
                    return true;
                }
                return auctionHouseService.handleAdminCommand(player, trimLeadingArg(args));
            }
            case "store" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use store admin tools.");
                    return true;
                }
                return storeFulfillmentService.handleAdmin(player, trimLeadingArg(args));
            }
            case "hotbar" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use hotbar admin tools.");
                    return true;
                }
                return handleAdminHotbar(player, trimLeadingArg(args));
            }
            case "sethub" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to set the hub.");
                    return true;
                }
                queueService.setLobby(player.getLocation());
                player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Hub location saved.");
                return true;
            }
            case "economylog" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to view economy logs.");
                    return true;
                }
                return handleEconomyLog(player, trimLeadingArg(args));
            }
            case "debug" -> {
                if (!fullAdmin) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use debug tools.");
                    return true;
                }
                menuService.openAdminDevToolsMenu(player);
                return true;
            }
            default -> {
                announcementService.direct(player, "Admin", "Usage: /admin <set|br|hg|bossrush|minigames|spleef|vr|voidroulette|bridge|lock|unlock|locks|bosses|weapons|waves|structures|cosmetics|economy|wallet|warnings|ah|store|hotbar|sethub|economylog|debug|maintenance>");
                return true;
            }
        }
    }

    private boolean handleAdminHotbar(Player sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("toggle")) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin hotbar toggle [player]");
            return true;
        }
        Player target = sender;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found online: " + args[1]);
                return true;
            }
        }
        boolean enabled = menuService.toggleHubHotbar(target);
        String state = enabled ? "ON" : "OFF";
        sender.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Hub hotbar is now "
            + ChatColor.WHITE + state + ChatColor.GREEN + " for " + ChatColor.WHITE + target.getName() + ChatColor.GREEN + ".");
        if (!target.equals(sender)) {
            target.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Your hub hotbar was toggled "
                + ChatColor.WHITE + state + ChatColor.GRAY + ".");
        }
        return true;
    }

    private boolean handleAdminSetShortcut(Player sender, String[] args) {
        if (!requirePermission(sender, "hgloot.wallet.admin", "You do not have permission to manage stats.")) {
            return true;
        }
        if (progressionService == null) {
            sender.sendMessage(ChatColor.RED + "Progression is unavailable right now.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin set <money|kills|wins|shards|level> [hg|br] [player] <amount>");
            return true;
        }
        String stat = args[0].toLowerCase(Locale.ROOT);
        OfflinePlayer target = sender;
        String mode = null;
        String rawAmount;
        int cursor = 1;
        if (stat.equals("wins") || stat.equals("win")) {
            String possibleMode = args.length > cursor ? args[cursor].toLowerCase(Locale.ROOT) : "";
            if (possibleMode.equals("hg") || possibleMode.equals("br") || possibleMode.equals("all") || possibleMode.equals("total")) {
                mode = possibleMode;
                cursor++;
            }
        }
        if (args.length - cursor >= 2) {
            target = Bukkit.getOfflinePlayer(args[cursor]);
            rawAmount = args[cursor + 1];
        } else {
            rawAmount = args[cursor];
        }
        int amount;
        try {
            amount = Integer.parseInt(rawAmount);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
            return true;
        }
        if (!applyAdminStatSet(sender, target, stat, mode, amount)) {
            return true;
        }
        sender.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Set "
            + ChatColor.WHITE + stat + (mode == null ? "" : " " + mode) + ChatColor.GREEN + " to " + ChatColor.WHITE + amount
            + ChatColor.GREEN + " for " + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleWinsAdminShortcut(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.wallet.admin", "You do not have permission to manage wins.")) {
            return true;
        }
        if (args.length < 2 || args.length > 3 || !args[0].equalsIgnoreCase("reset")) {
            sender.sendMessage(ChatColor.RED + "Usage: /wins reset [all|hg|br] <player>");
            return true;
        }
        if (queueService == null) {
            sender.sendMessage(ChatColor.RED + "Queue stats are unavailable right now.");
            return true;
        }
        String mode = "all";
        String targetName = args[1];
        if (args.length == 3) {
            mode = args[1];
            targetName = args[2];
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        queueService.resetLifetimeWins(target.getUniqueId(), mode);
        sender.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Reset wins for "
            + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + " (" + mode.toLowerCase(Locale.ROOT) + ").");
        return true;
    }

    private boolean handleWarn(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.warn", "You do not have permission to warn players.")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /warn <player> <reason>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String reason = joinArgs(args, 1);
        if (reason.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Warning reason cannot be blank.");
            return true;
        }
        String entry = System.currentTimeMillis() + "|" + sender.getName() + "|" + reason;
        String path = MODERATION_CONFIG_ROOT + ".warnings." + target.getUniqueId() + ".entries";
        List<String> entries = new ArrayList<>(getConfig().getStringList(path));
        entries.add(entry);
        getConfig().set(path, entries);
        getConfig().set(MODERATION_CONFIG_ROOT + ".warnings." + target.getUniqueId() + ".name", safeOfflineName(target));
        saveConfig();
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(ChatColor.RED + "[Warning] " + ChatColor.YELLOW + reason);
        }
        sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Warned "
            + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + ".");
        broadcastToModerators(ChatColor.YELLOW + "[Moderation] " + ChatColor.WHITE + sender.getName()
            + ChatColor.GRAY + " warned " + ChatColor.WHITE + safeOfflineName(target)
            + ChatColor.GRAY + ": " + ChatColor.YELLOW + reason);
        return true;
    }

    private boolean handleWarnings(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.warn", "You do not have permission to view warnings.")) {
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /warnings <player> [clear]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String base = MODERATION_CONFIG_ROOT + ".warnings." + target.getUniqueId();
        if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
            getConfig().set(base, null);
            saveConfig();
            sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Cleared warnings for "
                + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + ".");
            return true;
        }
        List<String> entries = getConfig().getStringList(base + ".entries");
        sender.sendMessage(ChatColor.AQUA + "[Warnings] " + ChatColor.WHITE + safeOfflineName(target)
            + ChatColor.GRAY + " has " + ChatColor.YELLOW + entries.size() + ChatColor.GRAY + " warning(s).");
        int start = Math.max(0, entries.size() - 10);
        for (int i = start; i < entries.size(); i++) {
            sender.sendMessage(ChatColor.DARK_GRAY + "#" + (i + 1) + " " + ChatColor.GRAY + formatModerationEntry(entries.get(i)));
        }
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.mute", "You do not have permission to mute players.")) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <player> <duration|perm> <reason>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        long durationMs = parseDurationMillis(args[1]);
        if (durationMs == Long.MIN_VALUE) {
            sender.sendMessage(ChatColor.RED + "Duration must be like 10m, 2h, 1d, or perm.");
            return true;
        }
        String reason = joinArgs(args, 2);
        if (reason.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Mute reason cannot be blank.");
            return true;
        }
        long until = durationMs < 0L ? -1L : System.currentTimeMillis() + durationMs;
        mutedUntilMs.put(target.getUniqueId(), until);
        muteReasons.put(target.getUniqueId(), reason);
        saveModerationState();
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(ChatColor.RED + "[Mute] " + ChatColor.GRAY + "You are muted "
                + muteDurationLabel(until) + ChatColor.GRAY + ". Reason: " + ChatColor.YELLOW + reason);
        }
        sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Muted "
            + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + " " + muteDurationLabel(until)
            + ChatColor.GREEN + ".");
        broadcastToModerators(ChatColor.YELLOW + "[Moderation] " + ChatColor.WHITE + sender.getName()
            + ChatColor.GRAY + " muted " + ChatColor.WHITE + safeOfflineName(target)
            + ChatColor.GRAY + " " + muteDurationLabel(until) + ChatColor.GRAY + ": " + ChatColor.YELLOW + reason);
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.mute", "You do not have permission to unmute players.")) {
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        mutedUntilMs.remove(target.getUniqueId());
        muteReasons.remove(target.getUniqueId());
        saveModerationState();
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(ChatColor.GREEN + "[Mute] You have been unmuted.");
        }
        sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Unmuted "
            + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.kick", "You do not have permission to kick players.")) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /kick <player> [reason]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found online: " + args[0]);
            return true;
        }
        String reason = args.length >= 2 ? joinArgs(args, 1) : "Kicked by staff.";
        target.kickPlayer(ChatColor.RED + "Kicked from Hydra\n" + ChatColor.GRAY + reason);
        sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Kicked "
            + ChatColor.WHITE + target.getName() + ChatColor.GREEN + ".");
        broadcastToModerators(ChatColor.YELLOW + "[Moderation] " + ChatColor.WHITE + sender.getName()
            + ChatColor.GRAY + " kicked " + ChatColor.WHITE + target.getName()
            + ChatColor.GRAY + ": " + ChatColor.YELLOW + reason);
        return true;
    }

    private boolean handleReport(CommandSender sender, String[] args) {
        Player reporter = requirePlayer(sender, "Only players can submit reports.");
        if (reporter == null) {
            return true;
        }
        if (!requirePermission(reporter, "hgloot.report", "You do not have permission to submit reports.")) {
            return true;
        }
        if (args.length < 2) {
            reporter.sendMessage(ChatColor.RED + "Usage: /report <player> <reason>");
            return true;
        }
        UUID reporterId = reporter.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownUntil = reportCooldownUntilMs.getOrDefault(reporterId, 0L);
        if (cooldownUntil > now) {
            long seconds = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            reporter.sendMessage(ChatColor.RED + "Wait " + seconds + "s before submitting another report.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String reason = joinArgs(args, 1);
        if (reason.isBlank()) {
            reporter.sendMessage(ChatColor.RED + "Report reason cannot be blank.");
            return true;
        }
        if (reason.length() > REPORT_REASON_MAX_LENGTH) {
            reporter.sendMessage(ChatColor.RED + "Report reasons are limited to " + REPORT_REASON_MAX_LENGTH + " characters.");
            return true;
        }
        long expiresAt = now + REPORT_COOLDOWN_MS;
        reportCooldownUntilMs.put(reporterId, expiresAt);
        Bukkit.getScheduler().runTaskLater(this, () ->
            reportCooldownUntilMs.remove(reporterId, expiresAt), REPORT_COOLDOWN_MS / 50L);
        List<String> reports = new ArrayList<>(getConfig().getStringList(MODERATION_CONFIG_ROOT + ".reports.entries"));
        reports.add(System.currentTimeMillis() + "|" + reporter.getName() + "|" + safeOfflineName(target) + "|" + reason);
        if (reports.size() > MAX_STORED_REPORTS) {
            reports = new ArrayList<>(reports.subList(reports.size() - MAX_STORED_REPORTS, reports.size()));
        }
        getConfig().set(MODERATION_CONFIG_ROOT + ".reports.entries", reports);
        saveConfig();
        reporter.sendMessage(ChatColor.AQUA + "[Reports] " + ChatColor.GREEN + "Report submitted.");
        broadcastToModerators(ChatColor.RED + "[Report] " + ChatColor.WHITE + reporter.getName()
            + ChatColor.GRAY + " reported " + ChatColor.WHITE + safeOfflineName(target)
            + ChatColor.GRAY + ": " + ChatColor.YELLOW + reason);
        return true;
    }

    private boolean handleReports(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.reports", "You do not have permission to view reports.")) {
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("clear")) {
            if (!requirePermission(sender, "hgloot.moderation.reports.clear", "You do not have permission to clear reports.")) {
                return true;
            }
            getConfig().set(MODERATION_CONFIG_ROOT + ".reports.entries", new ArrayList<String>());
            saveConfig();
            sender.sendMessage(ChatColor.AQUA + "[Reports] " + ChatColor.GREEN + "Cleared reports.");
            return true;
        }
        if (!sub.equals("list")) {
            sender.sendMessage(ChatColor.RED + "Usage: /reports [list|clear]");
            return true;
        }
        List<String> reports = getConfig().getStringList(MODERATION_CONFIG_ROOT + ".reports.entries");
        sender.sendMessage(ChatColor.AQUA + "[Reports] " + ChatColor.GRAY + "Stored reports: "
            + ChatColor.YELLOW + reports.size());
        int start = Math.max(0, reports.size() - 10);
        for (int i = start; i < reports.size(); i++) {
            sender.sendMessage(ChatColor.DARK_GRAY + "#" + (i + 1) + " " + ChatColor.GRAY + formatReportEntry(reports.get(i)));
        }
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (args.length >= 2 && args.length <= 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("all")) {
            if (!requirePermission(sender, "hgloot.stats.reset", "You do not have permission to reset player stats.")) {
                return true;
            }
            return handleStatsResetAll(sender, args.length == 3 ? args[2] : null);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            if (!requirePermission(sender, "hgloot.stats.reset", "You do not have permission to reset player stats.")) {
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            UUID targetId = target.getUniqueId();
            if (queueService != null) {
                queueService.resetLifetimeStats(targetId);
            }
            if (progressionService != null) {
                progressionService.resetLevelProgress(targetId, "ADMIN_STATS_RESET");
            }
            sender.sendMessage(ChatColor.AQUA + "[Stats] " + ChatColor.GREEN + "Reset wins, kills, HG/BR splits, damage stats, Level XP, and level for "
                + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + ".");
            Player online = target.getPlayer();
            if (online != null && online.isOnline()) {
                online.sendMessage(ChatColor.YELLOW + "[Stats] Your Hydra match stats and level progress were reset by staff.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /stats reset <player|all> [confirmation]");
        return true;
    }

    private boolean handleStatsResetAll(CommandSender sender, String suppliedToken) {
        String confirmationKey = statsResetConfirmationKey(sender);
        long now = System.currentTimeMillis();
        StatsResetAllConfirmation pending = statsResetAllConfirmations.get(confirmationKey);
        if (suppliedToken == null || suppliedToken.isBlank()) {
            String token = generateStatsResetToken();
            statsResetAllConfirmations.put(
                confirmationKey,
                new StatsResetAllConfirmation(token, now + STATS_RESET_ALL_CONFIRMATION_TTL_MS)
            );
            sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "[Stats] DANGEROUS GLOBAL RESET");
            sender.sendMessage(ChatColor.YELLOW + "This will reset all player wins, kills, HG/BR splits, damage stats, Level XP, and levels.");
            sender.sendMessage(ChatColor.YELLOW + "It will not reset Coins, shards, cosmetics, ranks, warnings, or reports.");
            sender.sendMessage(ChatColor.GRAY + "Confirm within 60 seconds with:");
            sender.sendMessage(ChatColor.WHITE + "/stats reset all " + token);
            return true;
        }
        if (pending == null || pending.expiresAtMs() < now) {
            statsResetAllConfirmations.remove(confirmationKey);
            sender.sendMessage(ChatColor.RED + "[Stats] No active reset-all confirmation. Run /stats reset all again.");
            return true;
        }
        if (!pending.token().equalsIgnoreCase(suppliedToken.trim())) {
            sender.sendMessage(ChatColor.RED + "[Stats] Incorrect confirmation token. Run /stats reset all again if needed.");
            return true;
        }
        statsResetAllConfirmations.remove(confirmationKey);
        if (queueService != null) {
            queueService.resetAllLifetimeStats();
        }
        int progressionCount = 0;
        if (progressionService != null) {
            progressionCount = progressionService.resetAllLevelProgress("ADMIN_STATS_RESET_ALL");
        }
        sender.sendMessage(ChatColor.AQUA + "[Stats] " + ChatColor.GREEN
            + "Reset all player match stats and level progress. Progression records touched: "
            + ChatColor.WHITE + progressionCount + ChatColor.GREEN + ".");
        broadcastToModerators(ChatColor.RED + "[Stats] " + ChatColor.WHITE + sender.getName()
            + ChatColor.GRAY + " reset all player match stats and level progress.");
        return true;
    }

    private boolean applyAdminStatSet(CommandSender sender, OfflinePlayer target, String stat, int amount) {
        return applyAdminStatSet(sender, target, stat, null, amount);
    }

    private boolean applyAdminStatSet(CommandSender sender, OfflinePlayer target, String stat, String mode, int amount) {
        UUID targetId = target.getUniqueId();
        switch (stat) {
            case "level", "lvl", "prestigelevel", "prestigelvl", "prestige_level", "prestige" -> {
                progressionService.setLevel(targetId, amount, "ADMIN");
                return true;
            }
            case "money", "coins", "coin", "gold", "silver" -> {
                if (auctionHouseService == null) {
                    sender.sendMessage(ChatColor.RED + "Economy is unavailable right now.");
                    return false;
                }
                return auctionHouseService.setSilver(targetId, amount, "ADMIN");
            }
            case "shards", "shard" -> {
                progressionService.setShards(targetId, amount, "ADMIN");
                return true;
            }
            case "kills", "kill" -> {
                if (queueService == null) {
                    sender.sendMessage(ChatColor.RED + "Queue stats are unavailable right now.");
                    return false;
                }
                queueService.setLifetimeKills(targetId, amount);
                return true;
            }
            case "wins", "win" -> {
                if (queueService == null) {
                    sender.sendMessage(ChatColor.RED + "Queue stats are unavailable right now.");
                    return false;
                }
                if (mode == null) {
                    queueService.setLifetimeWins(targetId, amount);
                } else {
                    queueService.setLifetimeWins(targetId, mode, amount);
                }
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Stat must be money, kills, wins, shards, or level.");
                return false;
            }
        }
    }

    private String safeOfflineName(OfflinePlayer player) {
        if (player == null) {
            return "Unknown";
        }
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    private boolean handleMaintenance(CommandSender sender, String[] args) {
        if (!requirePermission(sender, MAINTENANCE_COMMAND_PERMISSION, "You do not have permission to use maintenance mode.")) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Maintenance mode is "
                + (isMaintenanceEnabled()
                ? ChatColor.RED + "enabled"
                : ChatColor.GREEN + "disabled")
                + ChatColor.GRAY + ".");
            sender.sendMessage(ChatColor.GRAY + "Bypass permission: " + ChatColor.AQUA + maintenanceBypassPermission());
            sender.sendMessage(ChatColor.GRAY + "Allowlisted players: " + ChatColor.AQUA + maintenanceAllowedPlayerCount());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on" -> {
                setMaintenanceEnabled(true);
                sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Maintenance mode "
                    + ChatColor.RED + "enabled" + ChatColor.GRAY + ".");
                return true;
            }
            case "off" -> {
                setMaintenanceEnabled(false);
                sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Maintenance mode "
                    + ChatColor.GREEN + "disabled" + ChatColor.GRAY + ".");
                return true;
            }
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance add <player>");
                    return true;
                }
                OfflinePlayer target = resolveKnownPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown player: " + args[1]);
                    return true;
                }
                if (!addMaintenanceAllowedPlayer(target)) {
                    sender.sendMessage(ChatColor.YELLOW + offlinePlayerName(target) + " is already allowlisted for maintenance mode.");
                    return true;
                }
                sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Allowlisted "
                    + ChatColor.YELLOW + offlinePlayerName(target) + ChatColor.GRAY + " for maintenance mode.");
                return true;
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance remove <player>");
                    return true;
                }
                OfflinePlayer target = resolveKnownPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown player: " + args[1]);
                    return true;
                }
                if (!removeMaintenanceAllowedPlayer(target)) {
                    sender.sendMessage(ChatColor.YELLOW + offlinePlayerName(target) + " is not on the maintenance allowlist.");
                    return true;
                }
                sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Removed "
                    + ChatColor.YELLOW + offlinePlayerName(target) + ChatColor.GRAY + " from the maintenance allowlist.");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /maintenance <on|off|status|add|remove>");
                return true;
            }
        }
    }

    /** Canonical mode keys: br, hg, spleef, voidroulette, bridge. Returns null for unknown input. */
    public static String normalizeModeKey(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "br", "battleroyale", "battle", "royale", "battle_royale", "battleroyal", "battle_royal" -> "br";
            case "hg", "hungergames", "hunger", "hunger_games" -> "hg";
            case "spleef" -> "spleef";
            case "voidroulette", "void", "mace", "maceroulette", "void_roulette" -> "voidroulette";
            case "bridge" -> "bridge";
            default -> null;
        };
    }

    public static String modeDisplayName(String key) {
        return switch (key == null ? "" : key) {
            case "br" -> "Battle Royale";
            case "hg" -> "Hunger Games";
            case "spleef" -> "Spleef";
            case "voidroulette" -> "Void Roulette";
            case "bridge" -> "Bridge";
            default -> key;
        };
    }

    public boolean isModeLocked(String modeKey) {
        return modeKey != null && lockedModes.contains(modeKey);
    }

    private void loadModeLocks() {
        lockedModes.clear();
        for (String raw : getConfig().getStringList("locked_modes")) {
            String key = normalizeModeKey(raw);
            if (key != null) {
                lockedModes.add(key);
            }
        }
    }

    private void saveModeLocks() {
        getConfig().set("locked_modes", new ArrayList<>(lockedModes));
        saveConfig();
    }

    private boolean handleModeLock(Player player, String[] args, boolean lock) {
        String verb = lock ? "lock" : "unlock";
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /admin " + verb + " <br|hg|spleef|voidroulette|bridge>");
            return true;
        }
        String key = normalizeModeKey(args[0]);
        if (key == null) {
            player.sendMessage(ChatColor.RED + "Unknown mode '" + args[0] + "'. Use br, hg, spleef, voidroulette, or bridge.");
            return true;
        }
        boolean changed = lock ? lockedModes.add(key) : lockedModes.remove(key);
        if (!changed) {
            player.sendMessage(ChatColor.YELLOW + modeDisplayName(key) + " is already " + (lock ? "locked." : "unlocked."));
            return true;
        }
        saveModeLocks();
        player.sendMessage(HydraIcons.LOCKED + " " + (lock ? ChatColor.RED : ChatColor.GREEN)
            + modeDisplayName(key) + " is now " + (lock ? "LOCKED" : "UNLOCKED") + ChatColor.GRAY
            + (lock ? " — players can no longer queue it." : " — players can queue it again."));
        return true;
    }

    private boolean handleModeLockList(Player player) {
        if (lockedModes.isEmpty()) {
            player.sendMessage(HydraIcons.LOCKED + " " + ChatColor.GREEN + "No game modes are locked.");
            return true;
        }
        String list = lockedModes.stream().map(HGLootPlugin::modeDisplayName).sorted().collect(java.util.stream.Collectors.joining(", "));
        player.sendMessage(HydraIcons.LOCKED + " " + ChatColor.GRAY + "Locked modes: " + ChatColor.RED + list);
        return true;
    }

    private boolean handleBattleRoyale(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.queue", "You do not have permission to use Battle Royale commands.")) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("info")) {
            sendBattleRoyaleHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                QueueService.QueueType type = args.length >= 2 ? QueueService.QueueType.fromKey(args[1]) : QueueService.QueueType.SOLO;
                if (type != QueueService.QueueType.SOLO && type != QueueService.QueueType.DUO) {
                    player.sendMessage(ChatColor.RED + "Usage: /br join <solo|duo>");
                    return true;
                }
                queueService.joinQueue(player, type);
                return true;
            }
            case "leave" -> {
                queueService.leaveQueueOrMatch(player);
                return true;
            }
            case "status" -> {
                QueueService.QueueType type = args.length >= 2 ? QueueService.QueueType.fromKey(args[1]) : null;
                if (type != null && type != QueueService.QueueType.SOLO && type != QueueService.QueueType.DUO) {
                    player.sendMessage(ChatColor.RED + "Usage: /br status [solo|duo]");
                    return true;
                }
                queueService.sendStatus(player, type);
                return true;
            }
            case "forcestart" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to force start Battle Royale queues.");
                    return true;
                }
                QueueService.QueueType type = args.length >= 2 ? QueueService.QueueType.fromKey(args[1]) : QueueService.QueueType.SOLO;
                if (type != QueueService.QueueType.SOLO && type != QueueService.QueueType.DUO) {
                    player.sendMessage(ChatColor.RED + "Usage: /br forcestart [solo|duo]");
                    return true;
                }
                return handleQueueAdmin(player, new String[] {"forcestart", type.key()});
            }
            case "cleanup" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to control match world cleanup.");
                    return true;
                }
                return handleQueueCleanupAdmin(player, trimLeadingArg(args), QueueService.QueueType.SOLO);
            }
            case "win" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to force a Battle Royale win check.");
                    return true;
                }
                QueueService.QueueType type = args.length >= 2 ? QueueService.QueueType.fromKey(args[1]) : QueueService.QueueType.SOLO;
                if (type != QueueService.QueueType.SOLO && type != QueueService.QueueType.DUO) {
                    player.sendMessage(ChatColor.RED + "Usage: /br win [solo|duo]");
                    return true;
                }
                queueService.triggerWinCheck(type, player);
                return true;
            }
            case "forceend" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to force-end Battle Royale matches.");
                    return true;
                }
                QueueService.QueueType type = QueueService.QueueType.SOLO;
                String worldName = null;
                if (args.length >= 2) {
                    QueueService.QueueType parsed = QueueService.QueueType.fromKey(args[1]);
                    if (parsed == QueueService.QueueType.SOLO || parsed == QueueService.QueueType.DUO) {
                        type = parsed;
                        worldName = args.length >= 3 ? args[2] : null;
                    } else {
                        player.sendMessage(ChatColor.RED + "Usage: /br forceend [solo|duo] [world]");
                        return true;
                    }
                }
                queueService.forceEndMatch(type, worldName, player);
                return true;
            }
            case "revive" -> {
                if (!requirePermission(player, "hgloot.br.revive", "You do not have permission to revive Battle Royale players.")) {
                    return true;
                }
                Player target = resolveTargetPlayer(player, args, 1);
                if (target == null) {
                    return true;
                }
                queueService.reviveBattleRoyale(player, target);
                return true;
            }
            case "rejoin" -> {
                if (!requirePermission(player, "hgloot.br.rejoin", "You do not have permission to rejoin Hydra matches.")) {
                    return true;
                }
                queueService.rejoinBattleRoyale(player, false);
                return true;
            }
            default -> {
                sendBattleRoyaleHelp(player);
                return true;
            }
        }
    }

    private boolean handleHungerGames(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.queue", "You do not have permission to use Hunger Games commands.")) {
            return true;
        }
        if (args.length == 0) {
            menuService.openPlayerHungerGamesMenu(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendHungerGamesHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                sendHungerGamesInfo(player);
                return true;
            }
            case "join" -> {
                if (args.length > 2 || (args.length == 2 && hungerGamesDuosArgument(args[1]) == null)) {
                    player.sendMessage(ChatColor.RED + "Usage: /hg join [solos|duos]");
                } else if (args.length == 2 && Boolean.TRUE.equals(hungerGamesDuosArgument(args[1]))) {
                    queueService.joinHungerGamesDuos(player);
                } else {
                    queueService.joinHungerGamesSolo(player);
                }
                return true;
            }
            case "leave" -> {
                queueService.leaveQueueOrMatch(player);
                return true;
            }
            case "status" -> {
                queueService.sendStatus(player, queueService.isPlayerQueuedFor(player.getUniqueId(), QueueService.QueueType.HUNGERGAMES_DUOS)
                    ? QueueService.QueueType.HUNGERGAMES_DUOS : QueueService.QueueType.HUNGERGAMES);
                if (player.hasPermission("hgloot.queue.admin")) {
                    queueService.sendHungerGamesAdminStatus(player);
                }
                return true;
            }
            case "rejoin" -> {
                if (!requirePermission(player, "hgloot.br.rejoin", "You do not have permission to rejoin Hydra matches.")) {
                    return true;
                }
                queueService.rejoinBattleRoyale(player, false);
                return true;
            }
            case "forcestart" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to force start Hunger Games.");
                    return true;
                }
                return handleHungerGamesForceStart(player, args);
            }
            case "addplayer" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to add players to a Hunger Games match.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /hg addplayer <player> [world]");
                    return true;
                }
                Player target = resolveTargetPlayer(player, args, 1);
                if (target == null) {
                    return true;
                }
                queueService.adminAddPlayerToMatch(player, target, args.length >= 3 ? args[2] : null);
                return true;
            }
            case "cleanup" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to control match world cleanup.");
                    return true;
                }
                return handleQueueCleanupAdmin(player, trimLeadingArg(args), QueueService.QueueType.HUNGERGAMES);
            }
            case "win" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to force a Hunger Games win check.");
                    return true;
                }
                QueueService.QueueType type = QueueService.QueueType.HUNGERGAMES;
                if (args.length >= 2) {
                    type = QueueService.QueueType.fromKey(args[1]);
                }
                if (!QueueService.isHungerGames(type)) {
                    player.sendMessage(ChatColor.RED + "Usage: /hg win [hungergames]");
                    return true;
                }
                if (args.length == 1) queueService.triggerHungerGamesWinCheck(player);
                else queueService.triggerWinCheck(type, player);
                return true;
            }
            case "forceend" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to force-end Hunger Games matches.");
                    return true;
                }
                queueService.forceEndHungerGames(args.length >= 2 ? args[1] : null, player);
                return true;
            }
            case "revive" -> {
                if (!requirePermission(player, "hgloot.br.revive", "You do not have permission to revive Hunger Games players.")) {
                    return true;
                }
                Player target = resolveTargetPlayer(player, args, 1);
                if (target == null) {
                    return true;
                }
                queueService.reviveHungerGames(player, target);
                return true;
            }
            default -> {
                sendHungerGamesHelp(player);
                return true;
            }
        }
    }

    private boolean handleSpleef(CommandSender sender, String[] args) {
        if (!MinigameForceJoin.requireAdmin(sender)) {
            return true;
        }
        if (spleefService == null) {
            sender.sendMessage(ChatColor.RED + "Spleef is unavailable right now.");
            return true;
        }
        return spleefService.handleCommand(sender, args);
    }

    private boolean handleLegendaryRecipes(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.recipes", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("craft")) {
            if (situationalAnnouncementService != null && situationalAnnouncementService.openCraftingAssist(player, args[1])) {
                return true;
            }
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown recipe assist.");
            return true;
        }
        if (spleefService != null && spleefService.openRecipeGuide(player)) {
            return true;
        }
        legendaryRecipeBookService.openIndex(player);
        return true;
    }

    private boolean handleVoidRoulette(CommandSender sender, String[] args) {
        if (!MinigameForceJoin.requireAdmin(sender)) {
            return true;
        }
        if (voidRouletteService == null) {
            sender.sendMessage(ChatColor.RED + "Void Roulette is unavailable right now.");
            return true;
        }
        return voidRouletteService.handleCommand(sender, args);
    }

    private boolean handleBridge(CommandSender sender, String[] args) {
        if (!MinigameForceJoin.requireAdmin(sender)) {
            return true;
        }
        if (bridgeService == null) {
            sender.sendMessage(ChatColor.RED + "Bridge is unavailable right now.");
            return true;
        }
        return bridgeService.handleCommand(sender, args);
    }

    private void sendBattleRoyaleHelp(Player player) {
        announcementService.direct(player, "BR", "/br join <solo|duo>");
        player.sendMessage(ChatColor.GRAY + "/br leave");
        player.sendMessage(ChatColor.GRAY + "/br status [solo|duo]");
        if (player.hasPermission("hgloot.br.rejoin")) {
            player.sendMessage(ChatColor.GRAY + "/br rejoin");
        }
        if (player.hasPermission("hgloot.queue.admin")) {
            player.sendMessage(ChatColor.GRAY + "/br forcestart [solo|duo]");
            player.sendMessage(ChatColor.GRAY + "/br win [solo|duo]");
            player.sendMessage(ChatColor.GRAY + "/br forceend [solo|duo] [world]");
            player.sendMessage(ChatColor.GRAY + "/br cleanup <on|off|status>");
        }
        if (player.hasPermission("hgloot.br.revive")) {
            player.sendMessage(ChatColor.GRAY + "/br revive [player]");
        }
    }

    private void sendHungerGamesHelp(Player player) {
        announcementService.direct(player, "HG", "/hg join");
        player.sendMessage(ChatColor.GRAY + "/hg info");
        player.sendMessage(ChatColor.GRAY + "/hg leave");
        player.sendMessage(ChatColor.GRAY + "/hg status");
        if (player.hasPermission("hgloot.br.rejoin")) {
            player.sendMessage(ChatColor.GRAY + "/hg rejoin");
        }
        if (player.hasPermission("hgloot.queue.admin")) {
            player.sendMessage(ChatColor.GRAY + "/admin hg forcestart <solos|duos> [local-lobby-id]");
            player.sendMessage(ChatColor.GRAY + "/hg addplayer <player> [world]");
            player.sendMessage(ChatColor.GRAY + "/hg win [hungergames]");
            player.sendMessage(ChatColor.GRAY + "/hg forceend [world]");
            player.sendMessage(ChatColor.GRAY + "/hg cleanup <on|off|status>");
        }
        if (player.hasPermission("hgloot.br.revive")) {
            player.sendMessage(ChatColor.GRAY + "/hg revive [player]");
        }
    }

    private void sendHungerGamesInfo(Player player) {
        player.sendMessage(HydraIcons.EXCLAMATION + " "
            + ChatColor.AQUA + "" + ChatColor.BOLD + "Hunger Games Info");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "Start Freeze: "
            + ChatColor.WHITE + "10s countdown");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "Grace: "
            + ChatColor.WHITE + "30s after GO");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "Blocks: "
            + ChatColor.WHITE + "breaking disabled, placed blocks decay");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "TNT: "
            + ChatColor.WHITE + "auto-lights and does not destroy terrain");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "Combat: "
            + ChatColor.WHITE + "normal saturation healing");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "Refill: "
            + ChatColor.WHITE + "chests restock mid-match");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + "KOTH: "
            + ChatColor.WHITE + "capture the hill for a random production legendary");
    }

    private String[] trimLeadingArg(String[] args) {
        if (args == null || args.length <= 1) {
            return new String[0];
        }
        String[] trimmed = new String[args.length - 1];
        System.arraycopy(args, 1, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private boolean handlePlayerMenu(CommandSender sender) {
        Player player = requirePlayer(sender, "Only players can open the player menu.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.playermenu", "You do not have permission to use this.")) {
            return true;
        }
        return openNavigator(player);
    }

    private boolean handleCosmetic(CommandSender sender) {
        Player player = requirePlayer(sender, "Only players can open cosmetics.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.cosmetic", "You do not have permission to use this.")) {
            return true;
        }
        menuService.openPlayerCosmeticsMenu(player);
        return true;
    }

    public QueueService queueService() {
        return queueService;
    }

    /** Schedule deliberate travel at the black midpoint; false means a fade is already active. */
    public boolean transitionTravel(Player player, Runnable midpoint) {
        if (transitionService == null) {
            midpoint.run();
            return true;
        }
        return transitionService.transition(player, TransitionService.Preset.NORMAL, midpoint);
    }

    /** A shared black-midpoint barrier for match teardown and arena unloading. */
    public void transitionTravelGroup(Collection<Player> players, Consumer<Player> midpoint, Runnable afterMidpoints) {
        if (transitionService == null) {
            players.stream().filter(Player::isOnline).forEach(midpoint);
            afterMidpoints.run();
            return;
        }
        transitionService.transitionGroup(players, midpoint, afterMidpoints);
    }

    public HungerGamesUtilityService hungerGamesUtilityService() {
        return hungerGamesUtilityService;
    }

    public NetworkBridgeService networkBridgeService() {
        return networkBridgeService;
    }

    public NetworkNodeConfig networkNodeConfig() {
        return networkNodeConfig;
    }

    public PartyService partyService() {
        return partyService;
    }

    public GameHubService gameHubService() {
        return gameHubService;
    }

    public LegendaryRecipeBookService utilityBookService() {
        return legendaryRecipeBookService;
    }

    public BattlePassService battlePassService() {
        return battlePassService;
    }

    public SpleefService spleefService() {
        return spleefService;
    }

    public VoidRouletteService voidRouletteService() {
        return voidRouletteService;
    }

    public BridgeService bridgeService() {
        return bridgeService;
    }

    public KitEditorService kitEditorService() {
        return kitEditorService;
    }

    public MinigameRouletteService minigameRouletteService() {
        return minigameRouletteService;
    }

    public VictoryResultsService victoryResultsService() {
        return victoryResultsService;
    }

    public MatchTeamService matchTeamService() {
        return matchTeamService;
    }

    public TeamProtectionService teamProtectionService() {
        return teamProtectionService;
    }

    private boolean isPartyMutationLocked(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return (queueService != null && queueService.isPlayerInHydraMatchOrSpectating(playerId))
            || (spleefService != null && spleefService.isInActiveSpleefMatch(playerId))
            || (voidRouletteService != null && voidRouletteService.isInActiveVoidRouletteMatch(playerId))
            || (bridgeService != null && bridgeService.isInActiveBridgeMatch(playerId))
            || (minigameRouletteService != null && minigameRouletteService.isInActiveTournament(playerId));
    }

    public MenuService menuService() {
        return menuService;
    }

    public PlayerSettingsService playerSettingsService() {
        return playerSettingsService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDirectMessage(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+", 3);
        if (parts.length < 3 || !parts[0].matches("(?i)/(?:minecraft:)?(?:msg|tell|w)")) return;
        Player sender = event.getPlayer();
        if (hasStaffRank(sender.getUniqueId())) return;
        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null || playerSettingsService == null
            || playerSettingsService.enabled(target.getUniqueId(), PlayerSettingsService.Toggle.PRIVATE_MESSAGES)) return;
        event.setCancelled(true);
        sender.sendMessage(ChatColor.RED + target.getName() + " has private messages disabled.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendJoinNotice(PlayerJoinEvent event) {
        sendFriendPresenceNotice(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendQuitNotice(PlayerQuitEvent event) {
        sendFriendPresenceNotice(event.getPlayer(), false);
    }

    private void sendFriendPresenceNotice(Player subject, boolean joined) {
        if (friendService == null || playerSettingsService == null) return;
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (receiver.equals(subject)
                || !friendService.areFriends(receiver.getUniqueId(), subject.getUniqueId())
                || !playerSettingsService.enabled(receiver.getUniqueId(), PlayerSettingsService.Toggle.FRIEND_NOTIFICATIONS)) continue;
            receiver.sendMessage(ChatColor.AQUA + "[Friends] " + ChatColor.WHITE + subject.getName()
                + (joined ? " joined." : " left."));
        }
    }

    public ProgressionService progressionService() {
        return progressionService;
    }

    public DailyRewardService dailyRewardService() {
        return dailyRewardService;
    }

    public CombatChamberService combatChamberService() {
        return combatChamberService;
    }

    public ZxdzeroEventsCoreService zxdzeroEventsCoreService() {
        return zxdzeroEventsCoreService;
    }

    public BossRushService bossRushService() {
        return bossRushService;
    }

    public HydraPlusService hydraPlusService() {
        return hydraPlusService;
    }

    public HydraVoiceChatService hydraVoiceChatService() {
        return hydraVoiceChatService;
    }

    public NpcService npcService() {
        return npcService;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyGlobalWorldRules(event.getWorld());
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        // Chat only: Paper does not expose a corresponding toast mutation here.
        event.message(null);
    }

    private void applyGlobalWorldRules() {
        for (World world : Bukkit.getWorlds()) {
            applyGlobalWorldRules(world);
        }
    }

    private void applyGlobalWorldRules(World world) {
        if (world == null) {
            return;
        }
        try {
            world.setGameRule(GameRules.LOCATOR_BAR, false);
        } catch (IllegalArgumentException ignored) {
            // Older/alternate API builds may not expose this gamerule.
        }
        try {
            world.setGameRule(GameRules.SPREAD_VINES, false);
        } catch (IllegalArgumentException ignored) {
            // Keep startup safe if the gamerule name changes in a future build.
        }
    }

    private boolean handleBack(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use /back.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.back", "You do not have permission to use /back.")) {
            return true;
        }
        Location previous = backLocations.get(player.getUniqueId());
        if (previous == null || previous.getWorld() == null) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No previous location found.");
            return true;
        }
        transitionTravel(player, () -> {
            backTrackingSuppression.add(player.getUniqueId());
            if (player.teleport(previous.clone())) {
                player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Returned to previous location.");
            } else {
                backTrackingSuppression.remove(player.getUniqueId());
                player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not return to previous location.");
            }
        });
        return true;
    }

    private boolean handleGm(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use /gm.");
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /gm <0|1|2|3|survival|creative|adventure|spectator>");
            return true;
        }
        GameMode mode = parseGameMode(args[0]);
        if (mode == null) {
            player.sendMessage(ChatColor.RED + "Unknown gamemode: " + args[0]);
            return true;
        }
        if (mode == GameMode.SPECTATOR) {
            if (!requireAnyPermission(player, "You do not have permission to use spectator mode.", "hgloot.gamemode", "hgloot.gamemode.spectator")) {
                return true;
            }
        } else if (!requirePermission(player, "hgloot.gamemode", "You do not have permission to use /gm.")) {
            return true;
        }
        player.setGameMode(mode);
        return true;
    }

    private boolean handleFeed(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.feed", "You do not have permission to use /feed.")) {
            return true;
        }
        Player target = resolveSelfOrNamedTarget(sender, args, "feed");
        if (target == null) {
            return true;
        }
        target.setFoodLevel(20);
        target.setSaturation(20.0F);
        target.setExhaustion(0.0F);
        return true;
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.heal", "You do not have permission to use /heal.")) {
            return true;
        }
        Player target = resolveSelfOrNamedTarget(sender, args, "heal");
        if (target == null) {
            return true;
        }
        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        return true;
    }

    private boolean handleFly(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use /fly.");
        if (player == null) {
            return true;
        }
        if (hasTrialStaffOrHigher(player) && player.hasPermission("hgloot.fly")) {
            // Administrative /fly remains separate from Hydra+ Monarch Wings.
            boolean enable = !player.getAllowFlight();
            player.setAllowFlight(enable);
            if (!enable) player.setFlying(false);
            player.sendMessage((enable ? ChatColor.GREEN : ChatColor.GRAY)
                + "Flight " + (enable ? "enabled" : "disabled") + ".");
            return true;
        }
        if (playerSettingsService == null || !playerSettingsService.canUseFlightToggle(player)) {
            player.sendMessage(ChatColor.RED + "You do not have access to flight.");
            return true;
        }
        boolean enable = !playerSettingsService.flightEnabled(player);
        if (!playerSettingsService.setFlightEnabled(player, enable)) {
            player.sendMessage(ChatColor.RED + "Flight is unavailable here or your chest slot is occupied.");
            return true;
        }
        player.sendMessage((enable ? ChatColor.GREEN : ChatColor.GRAY) + "Flight " + (enable ? "enabled" : "disabled") + ".");
        return true;
    }

    boolean isInAnyHydraQueueOrMatch(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (queueService != null
            && (queueService.isPlayerQueued(playerId)
            || queueService.isPlayerInMatch(playerId)
            || queueService.isCustomSpectator(playerId))) {
            return true;
        }
        if (spleefService != null && spleefService.isInSpleef(playerId)) {
            return true;
        }
        if (voidRouletteService != null && voidRouletteService.isInVoidRoulette(playerId)) {
            return true;
        }
        return bridgeService != null && bridgeService.isInBridge(playerId);
    }

    boolean hasTrialStaffOrHigher(Player player) {
        if (player == null) {
            return false;
        }
        return player.hasPermission(RANK_PERMISSION_STAFF_TRIAL_STAFF)
            || player.hasPermission(RANK_PERMISSION_STAFF_TRIAL_MOD)
            || player.hasPermission(RANK_PERMISSION_STAFF_STAFF)
            || player.hasPermission(RANK_PERMISSION_STAFF_MOD)
            || player.hasPermission(RANK_PERMISSION_STAFF_ADMIN)
            || player.hasPermission(RANK_PERMISSION_STAFF_DEV)
            || player.hasPermission(RANK_PERMISSION_STAFF_OWNER);
    }

    private boolean handleGod(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use /god.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.god", "You do not have permission to use /god.")) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        boolean enabled;
        if (godModePlayers.contains(playerId)) {
            godModePlayers.remove(playerId);
            enabled = false;
        } else {
            godModePlayers.add(playerId);
            enabled = true;
        }
        player.sendMessage((enabled ? ChatColor.GREEN : ChatColor.YELLOW) + "God mode " + (enabled ? "enabled" : "disabled") + ".");
        return true;
    }

    private boolean handleAnnounce(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.announce", "You do not have permission to use /announce.")) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /announce <message>");
            return true;
        }
        String message = ChatColor.translateAlternateColorCodes('&', String.join(" ", args).trim());
        if (message.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Announcement message cannot be empty.");
            return true;
        }
        announcementService.globalTitle(message);
        return true;
    }

    private boolean handleRevive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /revive.");
            return true;
        }
        if (!requirePermission(player, "hgloot.br.revive", "You do not have permission to revive match players.")) {
            return true;
        }
        Player target = resolveTargetPlayer(player, args, 0);
        if (target == null) {
            return true;
        }
        if (spleefService != null && spleefService.hasReviveSnapshot(target)) {
            spleefService.revive(player, target);
            return true;
        }
        if (voidRouletteService != null && voidRouletteService.hasReviveSnapshot(target)) {
            voidRouletteService.revive(player, target);
            return true;
        }
        queueService.reviveAnyMatch(player, target);
        return true;
    }

    private boolean handleRejoin(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use /rejoin.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.br.rejoin", "You do not have permission to rejoin Hydra matches.")) {
            return true;
        }
        if (minigameRouletteService != null && minigameRouletteService.rejoinTournamentPlayer(player)) {
            return true;
        }
        if (minigameRouletteService != null
            && minigameRouletteService.isTournamentLocked(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW
                + "Your Minigame Roulette tournament is active, but you cannot rejoin this round as a player.");
            return true;
        }
        if (bridgeService != null && bridgeService.rejoin(player)) {
            return true;
        }
        if (spleefService != null && spleefService.hasRejoinSnapshot(player)) {
            spleefService.rejoin(player, false);
            return true;
        }
        if (voidRouletteService != null && voidRouletteService.hasRejoinSnapshot(player)) {
            voidRouletteService.rejoin(player, false);
            return true;
        }
        queueService.rejoinBattleRoyale(player, false);
        return true;
    }

    private boolean handleLeave(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use /leave.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.queue", "You do not have permission to use queue commands.")) {
            return true;
        }
        hubExitConfirmation.clear(player.getUniqueId());
        if (minigameRouletteService != null
            && minigameRouletteService.isTournamentLocked(player.getUniqueId())) {
            minigameRouletteService.leaveTournament(player);
            return true;
        }
        return handleSessionExit(player, false);
    }

    private boolean handleLeaderboard(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use leaderboards.");
        if (player == null) {
            return true;
        }
        if (queueService == null) {
            player.sendMessage(ChatColor.RED + "Leaderboard tools are unavailable right now.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            sendLeaderboardHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wins" -> {
                String scope = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "total";
                QueueService.DebugLeaderboardType type = switch (scope) {
                    case "total", "all", "overall" -> QueueService.DebugLeaderboardType.WINS_TOTAL;
                    case "hg", "hungergames", "hunger_games" -> QueueService.DebugLeaderboardType.WINS_HG;
                    case "br", "battleroyale", "battle_royale" -> QueueService.DebugLeaderboardType.WINS_BR;
                    default -> null;
                };
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Usage: /leaderboard wins [total|hg|br]");
                    return true;
                }
                queueService.sendLeaderboard(player, type);
                return true;
            }
            case "kills" -> {
                queueService.sendLeaderboard(player, QueueService.DebugLeaderboardType.KILLS);
                return true;
            }
            case "level" -> {
                queueService.sendLeaderboard(player, QueueService.DebugLeaderboardType.LEVEL);
                return true;
            }
            case "money", "balance", "coins", "coin", "gold", "silver" -> {
                queueService.sendLeaderboard(player, QueueService.DebugLeaderboardType.MONEY);
                return true;
            }
            case "refresh", "forceupdate" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                queueService.refreshLeaderboards(player);
                return true;
            }
            case "reload" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                queueService.reloadLeaderboardData(player);
                return true;
            }
            case "debug" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                queueService.sendLeaderboardDebug(player);
                return true;
            }
            case "place" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                if (args.length < 5) {
                    player.sendMessage(ChatColor.RED + "Usage: /leaderboard place <wins|kills|level|coins> [total|hg|br] <x> <y> <z>");
                    return true;
                }
                int coordinateIndex = 2;
                String scope = "total";
                if (args.length >= 6 && isLeaderboardScope(args[2])) {
                    scope = args[2];
                    coordinateIndex = 3;
                }
                QueueService.DebugLeaderboardType type = resolveLeaderboardType(args[1], scope);
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Unknown leaderboard type. Use wins total, wins hg, wins br, kills, level, or Coins.");
                    return true;
                }
                if (args.length < coordinateIndex + 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /leaderboard place <wins|kills|level|coins> [total|hg|br] <x> <y> <z>");
                    return true;
                }
                Location anchor = parseLeaderboardLocation(player, args[coordinateIndex], args[coordinateIndex + 1], args[coordinateIndex + 2]);
                if (anchor == null) {
                    player.sendMessage(ChatColor.RED + "Invalid coordinates. Use numbers or relative values like ~ ~1 ~.");
                    return true;
                }
                queueService.summonDebugLeaderboard(player, type, anchor);
                return true;
            }
            case "switch", "set" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /leaderboard switch <wins|kills|level|coins> [total|hg|br]");
                    return true;
                }
                String scope = args.length >= 3 ? args[2] : "total";
                QueueService.DebugLeaderboardType type = resolveLeaderboardType(args[1], scope);
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Unknown leaderboard type. Use wins total, wins hg, wins br, kills, level, or Coins.");
                    return true;
                }
                queueService.switchViewedDebugLeaderboard(player, type);
                return true;
            }
            case "remove" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                queueService.removeViewedDebugLeaderboard(player);
                return true;
            }
            case "clear" -> {
                if (!requirePermission(player, "hgloot.leaderboard", "You do not have permission to manage leaderboards.")) {
                    return true;
                }
                Set<String> typeKeys = resolveLeaderboardClearTypes(args);
                if (typeKeys != null && typeKeys.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Usage: /leaderboard clear <wins|kills|level|coins|all> [total|hg|br]");
                    return true;
                }
                queueService.clearDebugLeaderboards(player, typeKeys);
                return true;
            }
            default -> {
                sendLeaderboardHelp(player);
                return true;
            }
        }
    }

    private void sendLeaderboardHelp(Player player) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "HYDRA LEADERBOARD");
        player.sendMessage(ChatColor.GRAY + "/leaderboard wins total");
        player.sendMessage(ChatColor.GRAY + "/leaderboard wins hg");
        player.sendMessage(ChatColor.GRAY + "/leaderboard wins br");
        player.sendMessage(ChatColor.GRAY + "/leaderboard kills");
        player.sendMessage(ChatColor.GRAY + "/leaderboard level");
        player.sendMessage(ChatColor.GRAY + "/leaderboard coins");
        if (player.hasPermission("hgloot.leaderboard")) {
            player.sendMessage(ChatColor.DARK_GRAY + "Admin: /leaderboard place <type> [total|hg|br] <x> <y> <z>");
            player.sendMessage(ChatColor.DARK_GRAY + "Admin: /leaderboard switch <type> [total|hg|br]");
            player.sendMessage(ChatColor.DARK_GRAY + "Admin: /leaderboard refresh, reload, clear <type|all>, forceupdate, debug");
        }
    }

    private boolean handleRank(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.rank", "You do not have permission to manage ranks.")) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            Player player = requirePlayer(sender, "Only players can open the rank menu.");
            if (player != null) {
                menuService.openAdminRankPlayerListMenu(player);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("remove")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /rank remove <username>");
                return true;
            }
            OfflinePlayer target = resolveKnownPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Unknown player: " + args[1]);
                return true;
            }
            applyHydraRank(sender, target.getUniqueId(), offlinePlayerName(target), "none");
            applyStaffRank(sender, target.getUniqueId(), offlinePlayerName(target), "none");
            return true;
        }

        if (!sub.equals("give") && !sub.equals("set")) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank <give|set|remove> ...");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank " + sub + " <username> <hydra|hydra+|hydra++|hydra+++|trialstaff|staff|helper|media|creator|mod|admin|dev|owner> [tier]");
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Unknown player: " + args[1]);
            return true;
        }
        String rankFamily = args[2].trim().toLowerCase(Locale.ROOT);
        if (isStaffRankMode(rankFamily)) {
            applyStaffRank(sender, target.getUniqueId(), offlinePlayerName(target), rankFamily);
            return true;
        }
        String directHydraMode = hydraRankMode(rankFamily);
        if (directHydraMode != null) {
            applyHydraRank(sender, target.getUniqueId(), offlinePlayerName(target), directHydraMode);
            return true;
        }
        if (!rankFamily.equals("hydra+") && !rankFamily.equals("hydraplus") && !rankFamily.equals("hydra")) {
            sender.sendMessage(ChatColor.RED + "Unknown rank. Use hydra, hydra+, hydra++, hydra+++, trialstaff, staff, helper, media, creator, mod, admin, dev, or owner.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank " + sub + " <username> hydra <Hydra|Hydra+|Hydra++|Hydra+++>");
            return true;
        }
        String mode = hydraRankMode(args[3]);
        if (mode == null) {
            sender.sendMessage(ChatColor.RED + "Unknown tier. Use Base, I, II, or III.");
            return true;
        }
        applyHydraRank(sender, target.getUniqueId(), offlinePlayerName(target), mode);
        return true;
    }

    public boolean applyHydraRank(CommandSender sender, UUID targetId, String targetName, String mode) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "LuckPerms is required for rank management.");
            return false;
        }
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        List<String> commands = switch (normalized) {
            case "base", "tier0", "0" -> List.of(
                "permission set " + RANK_PERMISSION_HYDRA + " true",
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS,
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_TWO,
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_THREE,
                "permission unset " + RANK_PERMISSION_STAFF_TRIAL_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_ADMIN,
                "permission unset " + RANK_PERMISSION_STAFF_DEV,
                "permission unset " + RANK_PERMISSION_STAFF_OWNER
            );
            case "tier1", "i", "1" -> List.of(
                "permission set " + RANK_PERMISSION_HYDRA + " true",
                "permission set " + RANK_PERMISSION_HYDRA_PLUS + " true",
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_TWO,
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_THREE,
                "permission unset " + RANK_PERMISSION_STAFF_TRIAL_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_ADMIN,
                "permission unset " + RANK_PERMISSION_STAFF_DEV,
                "permission unset " + RANK_PERMISSION_STAFF_OWNER
            );
            case "tier2", "ii", "2" -> List.of(
                "permission set " + RANK_PERMISSION_HYDRA + " true",
                "permission set " + RANK_PERMISSION_HYDRA_PLUS + " true",
                "permission set " + RANK_PERMISSION_HYDRA_PLUS_TWO + " true",
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_THREE,
                "permission unset " + RANK_PERMISSION_STAFF_TRIAL_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_ADMIN,
                "permission unset " + RANK_PERMISSION_STAFF_DEV,
                "permission unset " + RANK_PERMISSION_STAFF_OWNER
            );
            case "tier3", "iii", "3", "all" -> List.of(
                "permission set " + RANK_PERMISSION_HYDRA + " true",
                "permission set " + RANK_PERMISSION_HYDRA_PLUS + " true",
                "permission set " + RANK_PERMISSION_HYDRA_PLUS_TWO + " true",
                "permission set " + RANK_PERMISSION_HYDRA_PLUS_THREE + " true",
                "permission unset " + RANK_PERMISSION_STAFF_TRIAL_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_ADMIN,
                "permission unset " + RANK_PERMISSION_STAFF_DEV,
                "permission unset " + RANK_PERMISSION_STAFF_OWNER
            );
            case "none", "remove" -> List.of(
                "permission unset " + RANK_PERMISSION_HYDRA,
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS,
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_TWO,
                "permission unset " + RANK_PERMISSION_HYDRA_PLUS_THREE,
                "permission unset " + RANK_PERMISSION_STAFF_TRIAL_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_MOD,
                "permission unset " + RANK_PERMISSION_STAFF_ADMIN,
                "permission unset " + RANK_PERMISSION_STAFF_DEV,
                "permission unset " + RANK_PERMISSION_STAFF_OWNER
            );
            default -> List.of();
        };
        if (commands.isEmpty()) {
            sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown rank action.");
            return false;
        }
        String resolvedTargetName = targetName == null || targetName.isBlank() ? targetId.toString() : targetName;
        String storedMode = switch (normalized) {
            case "base", "tier0", "0" -> "base";
            case "tier1", "i", "1" -> "tier1";
            case "tier2", "ii", "2" -> "tier2";
            case "tier3", "iii", "3", "all" -> "tier3";
            case "none", "remove" -> "none";
            default -> "none";
        };
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetId + " " + command);
        }
        for (String permission : allStaffRankPermissions()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetId + " permission unset " + permission);
        }
        setHydraRankAssignment(targetId, storedMode);
        setStaffRankAssignment(targetId, "none");
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
            + "Updated rank for " + ChatColor.WHITE + resolvedTargetName + ChatColor.GRAY + ".");
        int[] refreshDelays = new int[] {0, 10, 40, 100};
        for (int delay : refreshDelays) {
            if (delay <= 0) {
                Bukkit.getScheduler().runTask(this, () -> refreshHydraRankDisplay(targetId));
            } else {
                Bukkit.getScheduler().runTaskLater(this, () -> refreshHydraRankDisplay(targetId), delay);
            }
        }
        return true;
    }

    public boolean applyStaffRank(CommandSender sender, UUID targetId, String targetName, String mode) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "LuckPerms is required for rank management.");
            return false;
        }
        String normalized = staffRankMode(mode);
        if (normalized == null) {
            sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown staff rank.");
            return false;
        }
        List<String> commands = new ArrayList<>();
        for (String permission : allStaffRankPermissions()) {
            commands.add("permission unset " + permission);
        }
        commands.add("permission unset " + RANK_PERMISSION_HYDRA);
        commands.add("permission unset " + RANK_PERMISSION_HYDRA_PLUS);
        commands.add("permission unset " + RANK_PERMISSION_HYDRA_PLUS_TWO);
        commands.add("permission unset " + RANK_PERMISSION_HYDRA_PLUS_THREE);
        switch (normalized) {
            case "trialstaff" -> commands.add("permission set " + RANK_PERMISSION_STAFF_TRIAL_STAFF + " true");
            case "helper" -> commands.add("permission set " + RANK_PERMISSION_STAFF_HELPER + " true");
            case "media" -> commands.add("permission set " + RANK_PERMISSION_STAFF_MEDIA + " true");
            case "creator" -> commands.add("permission set " + RANK_PERMISSION_STAFF_CREATOR + " true");
            case "staff" -> {
                commands.add("permission set " + RANK_PERMISSION_STAFF_TRIAL_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_STAFF + " true");
            }
            case "mod" -> {
                commands.add("permission set " + RANK_PERMISSION_STAFF_TRIAL_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_MOD + " true");
            }
            case "admin" -> {
                commands.add("permission set " + RANK_PERMISSION_STAFF_TRIAL_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_MOD + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_ADMIN + " true");
            }
            case "dev" -> {
                commands.add("permission set " + RANK_PERMISSION_STAFF_TRIAL_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_MOD + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_ADMIN + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_DEV + " true");
            }
            case "owner" -> {
                commands.add("permission set " + RANK_PERMISSION_STAFF_TRIAL_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_STAFF + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_MOD + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_ADMIN + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_DEV + " true");
                commands.add("permission set " + RANK_PERMISSION_STAFF_OWNER + " true");
            }
            case "none" -> {
            }
            default -> {
                sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown staff rank.");
                return false;
            }
        }
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetId + " " + command);
        }
        setStaffRankAssignment(targetId, normalized);
        setHydraRankAssignment(targetId, "none");
        String resolvedTargetName = targetName == null || targetName.isBlank() ? targetId.toString() : targetName;
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
            + "Updated staff rank for " + ChatColor.WHITE + resolvedTargetName + ChatColor.GRAY + ".");
        int[] refreshDelays = new int[] {0, 10, 40, 100};
        for (int delay : refreshDelays) {
            if (delay <= 0) {
                Bukkit.getScheduler().runTask(this, () -> refreshHydraRankDisplay(targetId));
            } else {
                Bukkit.getScheduler().runTaskLater(this, () -> refreshHydraRankDisplay(targetId), delay);
            }
        }
        return true;
    }

    private Player resolveTargetPlayer(Player sender, String[] args, int targetIndex) {
        if (args.length <= targetIndex) {
            return sender;
        }
        Player target = Bukkit.getPlayerExact(args[targetIndex]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return null;
        }
        return target;
    }

    private Player resolveSelfOrNamedTarget(CommandSender sender, String[] args, String commandName) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                return player;
            }
            sender.sendMessage(ChatColor.RED + "Usage: /" + commandName + " <player>");
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return null;
        }
        return target;
    }

    private String joinArgs(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = Math.max(0, startIndex); i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private String statsResetConfirmationKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return "player:" + player.getUniqueId();
        }
        return "sender:" + sender.getName().toLowerCase(Locale.ROOT);
    }

    private String generateStatsResetToken() {
        StringBuilder builder = new StringBuilder("RESET-");
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                builder.append('-');
            }
            builder.append(STATS_RESET_ALL_TOKEN_ALPHABET.charAt(SECURE_RANDOM.nextInt(STATS_RESET_ALL_TOKEN_ALPHABET.length())));
        }
        return builder.toString();
    }

    private long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return Long.MIN_VALUE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("perm") || normalized.equals("permanent") || normalized.equals("forever")) {
            return -1L;
        }
        char unit = normalized.charAt(normalized.length() - 1);
        String numberPart = Character.isLetter(unit) ? normalized.substring(0, normalized.length() - 1) : normalized;
        long multiplier = switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> Character.isLetter(unit) ? -1L : 60_000L;
        };
        if (multiplier <= 0L) {
            return Long.MIN_VALUE;
        }
        try {
            long value = Long.parseLong(numberPart);
            if (value <= 0L) {
                return Long.MIN_VALUE;
            }
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException | NumberFormatException ex) {
            return Long.MIN_VALUE;
        }
    }

    private String muteDurationLabel(long untilMs) {
        if (untilMs < 0L) {
            return "permanently";
        }
        long remaining = Math.max(0L, untilMs - System.currentTimeMillis());
        return "for " + formatDuration(remaining);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private String formatModerationEntry(String entry) {
        String[] parts = entry == null ? new String[0] : entry.split("\\|", 3);
        if (parts.length < 3) {
            return entry == null ? "" : entry;
        }
        return ChatColor.WHITE + parts[1] + ChatColor.GRAY + ": " + ChatColor.YELLOW + parts[2];
    }

    private String formatReportEntry(String entry) {
        String[] parts = entry == null ? new String[0] : entry.split("\\|", 4);
        if (parts.length < 4) {
            return entry == null ? "" : entry;
        }
        return ChatColor.WHITE + parts[1] + ChatColor.GRAY + " -> " + ChatColor.WHITE + parts[2]
            + ChatColor.GRAY + ": " + ChatColor.YELLOW + parts[3];
    }

    private void broadcastToModerators(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("hgloot.moderation.reports") || online.hasPermission("hgloot.moderation.warn")
                || online.hasPermission("hgloot.admin")) {
                online.sendMessage(message);
            }
        }
        getLogger().info(ChatColor.stripColor(message));
    }

    private boolean isMuted(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Long until = mutedUntilMs.get(playerId);
        if (until == null) {
            return false;
        }
        if (until.longValue() < 0L) {
            return true;
        }
        if (System.currentTimeMillis() <= until.longValue()) {
            return true;
        }
        mutedUntilMs.remove(playerId);
        muteReasons.remove(playerId);
        if (Bukkit.isPrimaryThread()) {
            saveModerationState();
        } else {
            Bukkit.getScheduler().runTask(this, this::saveModerationState);
        }
        return false;
    }

    private void loadModerationState() {
        mutedUntilMs.clear();
        muteReasons.clear();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection(MODERATION_CONFIG_ROOT + ".mutes");
        if (section == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                long until = section.getLong(key + ".until", 0L);
                if (until == 0L || (until > 0L && until < now)) {
                    continue;
                }
                mutedUntilMs.put(playerId, until);
                muteReasons.put(playerId, section.getString(key + ".reason", "Muted by staff."));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveModerationState() {
        getConfig().set(MODERATION_CONFIG_ROOT + ".mutes", null);
        for (Map.Entry<UUID, Long> entry : mutedUntilMs.entrySet()) {
            String base = MODERATION_CONFIG_ROOT + ".mutes." + entry.getKey();
            getConfig().set(base + ".until", entry.getValue());
            getConfig().set(base + ".reason", muteReasons.getOrDefault(entry.getKey(), "Muted by staff."));
        }
        saveConfig();
    }

    private GameMode parseGameMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "survival", "s" -> GameMode.SURVIVAL;
            case "1", "creative", "c" -> GameMode.CREATIVE;
            case "2", "adventure", "a" -> GameMode.ADVENTURE;
            case "3", "spectator", "sp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMutedPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isMuted(player)) {
            return;
        }
        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        long until = mutedUntilMs.getOrDefault(playerId, -1L);
        String reason = muteReasons.getOrDefault(playerId, "Muted by staff.");
        Bukkit.getScheduler().runTask(this, () -> player.sendMessage(ChatColor.RED + "[Mute] " + ChatColor.GRAY + "You are muted "
            + muteDurationLabel(until) + ChatColor.GRAY + ". Reason: " + ChatColor.YELLOW + reason));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMutedPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isMuted(player)) {
            return;
        }
        long until = mutedUntilMs.getOrDefault(player.getUniqueId(), -1L);
        String reason = muteReasons.getOrDefault(player.getUniqueId(), "Muted by staff.");
        player.sendMessage(ChatColor.RED + "[Mute] " + ChatColor.GRAY + "You are muted "
            + muteDurationLabel(until) + ChatColor.GRAY + ". Reason: " + ChatColor.YELLOW + reason);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrackedTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (backTrackingSuppression.remove(playerId)) {
            return;
        }
        if (event.getFrom() == null || event.getTo() == null) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != null
            && to.getWorld() != null
            && from.getWorld().equals(to.getWorld())
            && from.distanceSquared(to) < 0.25D) {
            return;
        }
        backLocations.put(playerId, from.clone());
    }

    boolean isModerationMuted(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long until = mutedUntilMs.get(playerId);
        if (until == null) {
            return false;
        }
        if (until.longValue() < 0L || System.currentTimeMillis() <= until.longValue()) {
            return true;
        }
        mutedUntilMs.remove(playerId);
        muteReasons.remove(playerId);
        Bukkit.getScheduler().runTask(this, this::saveModerationState);
        return false;
    }

    @EventHandler
    public void onSessionQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        HydraSounds.forgetUi(event.getPlayer());
        if (partyService != null) {
            partyService.markOffline(playerId);
        }
        backLocations.remove(playerId);
        backTrackingSuppression.remove(playerId);
        godModePlayers.remove(playerId);
        commandExitLocks.remove(playerId);
        hubExitConfirmation.clear(playerId);
        reportCooldownUntilMs.remove(playerId);
        statsResetAllConfirmations.remove("player:" + playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMaintenanceLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        if (!isMaintenanceEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(maintenanceBypassPermission()) || isMaintenanceAllowedPlayer(player.getUniqueId())) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, maintenanceKickMessage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onStagedVersionAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (partyService != null) {
            partyService.markOnline(player.getUniqueId());
        }
        if (player.isOp() || player.hasPermission("hgloot.admin")) {
            notifyPlayerIfUpdateStaged(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGodDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!godModePlayers.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    private boolean handleHub(CommandSender sender) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.hub", "You do not have permission to use this.")) {
            return true;
        }
        if (!confirmHubExitIfNeeded(player)) {
            return true;
        }
        if (minigameRouletteService != null
            && minigameRouletteService.isActiveRoundMember(player.getUniqueId())) {
            if (!minigameRouletteService.returnCurrentRoundPlayerToHub(player)) {
                player.sendMessage(ChatColor.RED + "Could not return to Hub right now. Please try again.");
            }
            return true;
        }
        return handleSessionExit(player, true);
    }

    private boolean confirmHubExitIfNeeded(Player player) {
        UUID playerId = player.getUniqueId();
        UUID rouletteRoundId = minigameRouletteService == null ? null
            : minigameRouletteService.activeRoundIdFor(playerId);
        if (hubExitConfirmation.confirm(playerId, rouletteRoundId, isLiveMatchExit(player),
            System.currentTimeMillis())) return true;
        if (rouletteRoundId != null) {
            player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW
                + "Are you sure? Run /hub again to return to Hub for this round. "
                + "Use /leave to exit Minigame Roulette entirely.");
            return false;
        }
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW
            + "Are you sure? Run /hub again to leave this match.");
        return false;
    }

    private boolean isLiveMatchExit(Player player) {
        UUID playerId = player.getUniqueId();
        if (queueService != null && queueService.isPlayerInMatch(playerId)) {
            return true;
        }
        if (spleefService != null && spleefService.isAliveSpleefPlayer(playerId)) {
            return true;
        }
        if (voidRouletteService != null && voidRouletteService.isAudienceAlive(playerId)) {
            return true;
        }
        return bridgeService != null && bridgeService.isBridgeAlive(playerId);
    }

    private boolean handleSessionExit(Player player, boolean forceHubWhenIdle) {
        UUID playerId = player.getUniqueId();
        if (!commandExitLocks.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "Already returning you safely.");
            return true;
        }
        try {
            boolean handled = false;
            if (networkBridgeService != null) {
                handled = networkBridgeService.leaveNetworkSession(player);
            }
            if (!handled && spleefService != null && spleefService.isInSpleef(playerId)) {
                handled = transitionTravel(player, () -> spleefService.leaveToHub(player));
                if (!handled) handled = spleefService.leaveToHub(player);
            }
            if (!handled && voidRouletteService != null && voidRouletteService.isInVoidRoulette(playerId)) {
                handled = transitionTravel(player, () -> voidRouletteService.leaveToHub(player));
                if (!handled) handled = voidRouletteService.leaveToHub(player);
            }
            if (!handled && bridgeService != null && bridgeService.isInBridge(playerId)) {
                handled = transitionTravel(player, () -> bridgeService.leaveToHub(player));
                if (!handled) handled = bridgeService.leaveToHub(player);
            }
            if (!handled && bossRushService != null && bossRushService.isExternalSpectator(playerId)) {
                handled = bossRushService.returnTournamentSpectatorToHub(player);
            }
            if (!handled && minigameRouletteService != null && minigameRouletteService.isQueued(playerId)) {
                handled = minigameRouletteService.leaveQueue(player);
            }
            if (!handled && queueService != null) {
                if (forceHubWhenIdle) {
                    if (!transitionTravel(player, () -> queueService.sendPlayerToHub(player))) {
                        queueService.sendPlayerToHub(player);
                    }
                    player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Sent to hub.");
                } else {
                    if (queueService.isPlayerInHydraMatchOrSpectating(playerId)) {
                        if (!transitionTravel(player, () -> queueService.leaveQueueOrMatch(player))) {
                            queueService.leaveQueueOrMatch(player);
                        }
                    } else {
                        queueService.leaveQueueOrMatch(player);
                    }
                }
            }
            return true;
        } finally {
            Bukkit.getScheduler().runTaskLater(this, () -> commandExitLocks.remove(playerId), 20L * 10L);
        }
    }

    private boolean handleWorld(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.world", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /world <name>");
            List<String> available = availableWorldNames();
            if (!available.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Available: " + ChatColor.AQUA + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, available));
            }
            return true;
        }

        String requested = args[0].trim();
        if (requested.isBlank()) {
            player.sendMessage(ChatColor.RED + "Usage: /world <name>");
            return true;
        }

        World world = resolveWorldByName(requested);
        if (world == null) {
            File worldFolder = resolveWorldFolder(requested);
            if (!worldFolder.isDirectory()) {
                player.sendMessage(ChatColor.RED + "World not found: " + requested);
                return true;
            }
            WorldCreator creator = new WorldCreator(worldFolder.getName());
            creator.environment(inferWorldEnvironment(worldFolder.getName(), worldFolder));
            world = creator.createWorld();
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Failed to load world: " + worldFolder.getName());
                return true;
            }
        }

        Location target = world.getSpawnLocation();
        String destinationName = world.getName();
        transitionTravel(player, () -> {
            if (player.teleport(target)) {
                player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Teleported to world "
                    + ChatColor.GOLD + destinationName + ChatColor.GRAY + ".");
            }
        });
        return true;
    }

    private boolean handleSettings(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.playermenu", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length > 0) {
            if (args.length == 2 && args[0].equalsIgnoreCase("effects")
                && menuService.setHydraEffectsLevel(player, args[1])) {
                return true;
            }
            player.sendMessage(ChatColor.RED + "Usage: /settings effects <full|reduced|minimal>");
            return true;
        }
        menuService.openPlayerSettingsMenu(player);
        return true;
    }

    private boolean handleAeos(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.aeos", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "Usage: /aeos [worldName]");
            return true;
        }

        String requestedName = args.length == 0 ? "aeos_void" : args[0].trim();
        String worldName = requestedName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        if (worldName.isBlank()) {
            worldName = "aeos_void";
        }

        World world = queueService.createOrLoadAeosPreviewWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Failed to create the Aeos preview world.");
            return true;
        }

        Location spawn = queueService.aeosPreviewSpawn(world);
        if (spawn == null) {
            player.sendMessage(ChatColor.RED + "Failed to find a safe Aeos preview spawn.");
            return true;
        }

        String destinationName = world.getName();
        transitionTravel(player, () -> {
            if (player.teleport(spawn)) {
                player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
                    + "Teleported to Aeos preview world '" + destinationName + "'.");
            }
        });
        return true;
    }

    private boolean handlePlane(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can use this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.queue.admin", "You do not have permission to use this.")) {
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Plane testing is disabled.");
        return true;
    }

    private boolean handleResetLegendaryCooldowns(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.queue.admin", "You do not have permission to reset legendary cooldowns.")) {
            return true;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found online: " + args[0]);
                return true;
            }
        } else {
            target = requirePlayer(sender, "Usage: /resetlegendarycooldowns <player>");
            if (target == null) {
                return true;
            }
        }

        int resetCount = CooldownRegistry.resetAllCooldowns(target);
        target.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Your legendary cooldowns were reset.");
        if (!target.getUniqueId().equals(sender instanceof Player player ? player.getUniqueId() : null)) {
            sender.sendMessage(ChatColor.GREEN + "Reset " + resetCount + " legendary cooldown(s) for " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Reset " + resetCount + " legendary cooldown(s).");
        }
        return true;
    }


    private boolean handleCosmeticsAdmin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.cosmetics.admin", "You do not have permission to manage cosmetics.")) {
            return true;
        }

        if (sender instanceof Player player) {
            announcementService.direct(player, "Admin", "Use /admin cosmetics for the staff cosmetics workflow.");
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            Player player = requirePlayer(sender, "Only players can open the cosmetics admin menu.");
            if (player == null) {
                return true;
            }
            menuService.openAdminCosmeticsPlayerListMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reset")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("all")) {
                int affected = menuService.resetAllCosmeticOwnership();
                sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN
                    + "Reset cosmetic ownership and selections for " + affected + " player(s).");
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Usage: /admin cosmetics reset all");
            return true;
        }
        if (sub.equals("list")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /cosmetics list <player>");
                return true;
            }
            OfflinePlayer target = resolvePlayer(args[1]);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline())) {
                sender.sendMessage(ChatColor.RED + "Unknown player: " + args[1]);
                return true;
            }
            List<String> ownedIds = menuService.getOwnedHeadCosmeticIds(target.getUniqueId());
            String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
            sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Owned cosmetics for " + ChatColor.YELLOW + targetName + ChatColor.GRAY + ":");
            if (ownedIds.isEmpty()) {
                sender.sendMessage(ChatColor.DARK_GRAY + "- none -");
                return true;
            }
            for (String cosmeticId : ownedIds) {
                String displayName = menuService.getHeadCosmeticDisplayName(cosmeticId);
                sender.sendMessage(ChatColor.GRAY + "- " + cosmeticId + (displayName == null ? "" : ChatColor.DARK_GRAY + " (" + displayName + ")"));
            }
            return true;
        }

        if (!sub.equals("unlock") && !sub.equals("lock")) {
            sender.sendMessage(ChatColor.RED + "Usage: /cosmetics <menu|unlock|lock|list> ...");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /cosmetics " + sub + " <player> <cosmeticId|all>");
            return true;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Unknown player: " + args[1]);
            return true;
        }

        boolean unlock = sub.equals("unlock");
        String rawCosmeticId = args[2];
        List<String> changed = new ArrayList<>();
        if (rawCosmeticId.equalsIgnoreCase("all")) {
            for (String cosmeticId : menuService.getAllHeadCosmeticIds()) {
                if (unlock && menuService.isManualGrantOnlyHeadCosmetic(cosmeticId)) {
                    continue;
                }
                boolean result = unlock
                    ? menuService.addHeadCosmeticOwnership(target.getUniqueId(), cosmeticId)
                    : menuService.removeHeadCosmeticOwnership(target.getUniqueId(), cosmeticId);
                if (result) {
                    changed.add(cosmeticId);
                }
            }
        } else {
            String cosmeticId = menuService.resolveHeadCosmeticId(rawCosmeticId);
            if (cosmeticId == null) {
                sender.sendMessage(ChatColor.RED + "Unknown cosmetic id: " + rawCosmeticId);
                return true;
            }
            boolean result = unlock
                ? menuService.addHeadCosmeticOwnership(target.getUniqueId(), cosmeticId)
                : menuService.removeHeadCosmeticOwnership(target.getUniqueId(), cosmeticId);
            if (result) {
                changed.add(cosmeticId);
            }
        }

        String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        if (changed.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No cosmetic ownership changed for " + targetName + ".");
            return true;
        }
        sender.sendMessage(
            HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
                + (unlock ? "Unlocked " : "Locked ")
                + ChatColor.YELLOW + changed.size() + ChatColor.GRAY + " cosmetic(s) for "
                + ChatColor.YELLOW + targetName + ChatColor.GRAY + "."
        );
        return true;
    }

    private boolean handlePedestal(CommandSender sender, String[] args) {
        if (zxdzeroEventsCoreService == null) {
            sender.sendMessage(ChatColor.RED + "Pedestal system is not available.");
            return true;
        }
        return zxdzeroEventsCoreService.handlePedestal(sender, args);
    }

    private boolean handleQueue(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.queue", "You do not have permission to use queue commands.")) {
            return true;
        }

        if (args.length == 0) {
            queueService.sendQueueHelp(player, player.hasPermission("hgloot.queue.admin"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                QueueService.QueueType type = args.length >= 2
                    ? QueueService.QueueType.fromKey(args[1])
                    : QueueService.QueueType.SOLO;
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue join <solo|duo|hungergames>");
                    return true;
                }
                queueService.joinQueue(player, type);
                return true;
            }
            case "leave" -> {
                queueService.leaveQueueOrMatch(player);
                return true;
            }
            case "status" -> {
                QueueService.QueueType type = args.length >= 2
                    ? QueueService.QueueType.fromKey(args[1])
                    : null;
                queueService.sendStatus(player, type);
                return true;
            }
            case "list", "clear", "forcestart", "forcejoin", "forcewin", "forceend", "autowin", "cleanup", "setlobby", "setarena", "removearena", "arenas", "structure", "cave", "preload", "map", "switch", "valous", "test" -> {
                if (!player.hasPermission("hgloot.queue.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use admin queue commands.");
                    return true;
                }
                return handleQueueAdmin(player, args);
            }
            default -> {
                queueService.sendQueueHelp(player, player.hasPermission("hgloot.queue.admin"));
                return true;
            }
        }
    }

    static Boolean hungerGamesDuosArgument(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "solo", "solos" -> false;
            case "duo", "duos" -> true;
            default -> null;
        };
    }

    private boolean handleHungerGamesForceStart(Player player, String[] args) {
        if (!player.hasPermission("hgloot.admin") && !player.hasPermission("hgloot.queue.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to force start Hunger Games.");
            return true;
        }
        Boolean duos = args.length >= 2 ? hungerGamesDuosArgument(args[1]) : null;
        if (duos == null || args.length > 3) {
            player.sendMessage(ChatColor.RED + "Usage: /admin hg forcestart <solos|duos> [local-lobby-id]");
            player.sendMessage(ChatColor.GRAY + "Local Solo lobbies: " + queueService.hungerGamesLocalLobbyIds(false)
                + "; local Duo lobbies: " + queueService.hungerGamesLocalLobbyIds(true));
            return true;
        }
        queueService.forceStartHungerGames(duos, args.length == 3 ? args[2] : null, player);
        return true;
    }

    private boolean handleQueueAdminAlias(Player player, QueueService.QueueType defaultType, String[] args) {
        if (args.length == 0) {
            if (defaultType == QueueService.QueueType.HUNGERGAMES) {
                menuService.openAdminHungerGamesToolsMenu(player);
            } else {
                menuService.openAdminBattleRoyaleToolsMenu(player);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (defaultType == QueueService.QueueType.HUNGERGAMES) {
            if (sub.equals("border")) {
                queueService.handleHungerGamesBorderAdmin(player, java.util.Arrays.copyOfRange(args, 1, args.length));
                return true;
            }
            if (sub.equals("forcestart")) {
                return handleHungerGamesForceStart(player, args);
            }
            if (sub.equals("forceend")) {
                queueService.forceEndHungerGames(args.length >= 2 ? args[1] : null, player);
                return true;
            }
            if (sub.equals("forcewin") && args.length == 1) {
                queueService.triggerHungerGamesWinCheck(player);
                return true;
            }
            if (sub.equals("clear")) {
                Boolean duos = args.length == 2 ? hungerGamesDuosArgument(args[1]) : null;
                if (args.length > 2 || (args.length == 2 && duos == null)) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin hg clear [solos|duos]");
                    return true;
                }
                if (duos == null || !duos) queueService.clearQueue(QueueService.QueueType.HUNGERGAMES);
                if (duos == null || duos) queueService.clearQueue(QueueService.QueueType.HUNGERGAMES_DUOS);
                player.sendMessage(ChatColor.GREEN + "HG queue cleared.");
                return true;
            }
            if (sub.equals("swin")) {
                if (args.length != 1) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin hg swin");
                    return true;
                }
                victoryResultsService.showVictory(
                    player,
                    new VictoryResultsService.RewardSnapshot(850, 400, 3, null)
                );
                player.sendMessage(ChatColor.GRAY + "Showing a simulated HG victory. No rewards or stats were changed.");
                return true;
            }
            if (sub.equals("addplayer")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin hg addplayer <player> [world]");
                    return true;
                }
                Player target = resolveTargetPlayer(player, args, 1);
                if (target == null) {
                    return true;
                }
                queueService.adminAddPlayerToMatch(player, target, args.length >= 3 ? args[2] : null);
                return true;
            }
            if (sub.equals("status")) {
                queueService.sendHungerGamesAdminStatus(player);
                return true;
            }
        }
        if (sub.equals("forcestart") || sub.equals("forcewin") || sub.equals("clear")) {
            if (args.length == 1) {
                return handleQueueAdmin(player, new String[] {sub, defaultType.key()});
            }
        }
        if (sub.equals("forceend")) {
            queueService.forceEndMatch(defaultType, args.length >= 2 ? args[1] : null, player);
            return true;
        }
        if (sub.equals("forcejoin")) {
            if (args.length == 2) {
                return handleQueueAdmin(player, new String[] {sub, args[1], defaultType.key()});
            }
        }
        if (sub.equals("switch") && defaultType == QueueService.QueueType.SOLO) {
            return handleQueueAdmin(player, args);
        }
        if (sub.equals("valous") && defaultType == QueueService.QueueType.SOLO) {
            return handleQueueAdmin(player, args);
        }
        if (sub.equals("test") && defaultType == QueueService.QueueType.SOLO) {
            return handleQueueAdmin(player, args);
        }
        return handleQueueAdmin(player, args);
    }

    private boolean handleFriend(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.friends", "You do not have permission to use friends.")) {
            return true;
        }
        if (args.length == 0) {
            menuService.openPlayerFriendsMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<UUID> ids = friendService.getFriends(player.getUniqueId());
                if (ids.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "[Friends] You have no friends yet.");
                    return true;
                }
                player.sendMessage(ChatColor.GOLD + "[Friends] Your Friends (" + ids.size() + ")");
                ids.stream()
                    .map(friendService::displayName)
                    .sorted(String::compareToIgnoreCase)
                    .forEach(name -> player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + name));
                return true;
            }
            case "requests" -> {
                List<UUID> ids = friendService.getIncomingRequests(player.getUniqueId());
                if (ids.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "[Friends] No pending requests.");
                    return true;
                }
                player.sendMessage(ChatColor.GOLD + "[Friends] Pending Requests (" + ids.size() + ")");
                ids.stream()
                    .map(friendService::displayName)
                    .sorted(String::compareToIgnoreCase)
                    .forEach(name -> player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + name));
                return true;
            }
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend add <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player must be online: " + args[1]);
                    return true;
                }
                FriendService.SendRequestResult result =
                    friendService.sendRequest(player.getUniqueId(), target.getUniqueId());
                switch (result) {
                    case SELF -> player.sendMessage(ChatColor.RED + "[Friends] You cannot add yourself.");
                    case ALREADY_FRIENDS -> player.sendMessage(ChatColor.YELLOW + "[Friends] You are already friends with " + target.getName() + ".");
                    case ALREADY_SENT -> player.sendMessage(ChatColor.YELLOW + "[Friends] You already sent a request to " + target.getName() + ".");
                    case RATE_LIMITED -> player.sendMessage(ChatColor.YELLOW + "[Friends] Wait "
                        + friendService.requestCooldownSeconds(player.getUniqueId()) + "s before sending another request.");
                    case FRIEND_LIMIT_REACHED -> player.sendMessage(ChatColor.RED
                        + "[Friends] One of you has reached the 100-friend limit.");
                    case OUTGOING_LIMIT_REACHED -> player.sendMessage(ChatColor.RED
                        + "[Friends] You already have 20 outgoing requests pending.");
                    case TARGET_REQUEST_LIMIT_REACHED -> player.sendMessage(ChatColor.RED
                        + "[Friends] That player has too many pending requests.");
                    case BLOCKED -> player.sendMessage(ChatColor.RED + "[Friends] A block prevents this request.");
                    case REQUESTS_DISABLED -> player.sendMessage(ChatColor.RED + "[Friends] " + target.getName() + " has friend requests disabled.");
                    case AUTO_ACCEPTED -> {
                        player.sendMessage(ChatColor.GREEN + "[Friends] You and " + target.getName() + " are now friends.");
                        target.sendMessage(ChatColor.GREEN + "[Friends] You and " + player.getName() + " are now friends.");
                    }
                    case SENT -> {
                        player.sendMessage(ChatColor.GREEN + "[Friends] Friend request sent to " + target.getName() + ".");
                        target.sendMessage(ChatColor.AQUA + "[Friends] " + player.getName()
                            + " sent you a friend request. Use /friend accept " + player.getName() + ".");
                    }
                }
                return true;
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend accept <player>");
                    return true;
                }
                UUID requesterId = resolveByName(friendService.getIncomingRequests(player.getUniqueId()), args[1]);
                if (requesterId == null) {
                    player.sendMessage(ChatColor.RED + "[Friends] No pending request from " + args[1] + ".");
                    return true;
                }
                FriendService.RequestActionResult result =
                    friendService.acceptRequest(player.getUniqueId(), requesterId);
                if (result != FriendService.RequestActionResult.OK) {
                    player.sendMessage(ChatColor.RED + (result == FriendService.RequestActionResult.FRIEND_LIMIT_REACHED
                        ? "[Friends] One of you has reached the 100-friend limit."
                        : "[Friends] Could not accept that request."));
                    return true;
                }
                String requesterName = friendService.displayName(requesterId);
                player.sendMessage(ChatColor.GREEN + "[Friends] You are now friends with " + requesterName + ".");
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage(ChatColor.GREEN + "[Friends] " + player.getName() + " accepted your friend request.");
                }
                return true;
            }
            case "deny" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend deny <player>");
                    return true;
                }
                UUID requesterId = resolveByName(friendService.getIncomingRequests(player.getUniqueId()), args[1]);
                if (requesterId == null) {
                    player.sendMessage(ChatColor.RED + "[Friends] No pending request from " + args[1] + ".");
                    return true;
                }
                FriendService.RequestActionResult result =
                    friendService.denyRequest(player.getUniqueId(), requesterId);
                if (result != FriendService.RequestActionResult.OK) {
                    player.sendMessage(ChatColor.RED + "[Friends] Could not deny that request.");
                    return true;
                }
                String requesterName = friendService.displayName(requesterId);
                player.sendMessage(ChatColor.YELLOW + "[Friends] Denied request from " + requesterName + ".");
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage(ChatColor.YELLOW + "[Friends] " + player.getName() + " denied your friend request.");
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend remove <player>");
                    return true;
                }
                UUID friendId = resolveByName(friendService.getFriends(player.getUniqueId()), args[1]);
                if (friendId == null) {
                    player.sendMessage(ChatColor.RED + "[Friends] " + args[1] + " is not in your friend list.");
                    return true;
                }
                FriendService.RemoveFriendResult result =
                    friendService.removeFriend(player.getUniqueId(), friendId);
                if (result != FriendService.RemoveFriendResult.OK) {
                    player.sendMessage(ChatColor.RED + "[Friends] Could not remove friend.");
                    return true;
                }
                String friendName = friendService.displayName(friendId);
                player.sendMessage(ChatColor.YELLOW + "[Friends] Removed " + friendName + " from your friends list.");
                Player other = Bukkit.getPlayer(friendId);
                if (other != null && other.isOnline()) {
                    other.sendMessage(ChatColor.YELLOW + "[Friends] " + player.getName() + " removed you from friends.");
                }
                return true;
            }
            case "block" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend block <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                UUID targetId = target == null
                    ? resolveByName(friendService.getFriends(player.getUniqueId()), args[1])
                    : target.getUniqueId();
                if (targetId == null) {
                    player.sendMessage(ChatColor.RED + "[Friends] Player must be online or in your friends list.");
                    return true;
                }
                FriendService.BlockResult result = friendService.block(player.getUniqueId(), targetId);
                player.sendMessage(result == FriendService.BlockResult.BLOCKED
                    ? ChatColor.YELLOW + "[Friends] Blocked " + friendService.displayName(targetId) + "."
                    : ChatColor.RED + "[Friends] Could not block that player.");
                return true;
            }
            case "unblock" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend unblock <player>");
                    return true;
                }
                UUID targetId = resolveByName(friendService.getBlockedPlayers(player.getUniqueId()), args[1]);
                if (targetId == null) {
                    player.sendMessage(ChatColor.RED + "[Friends] That player is not blocked.");
                    return true;
                }
                friendService.unblock(player.getUniqueId(), targetId);
                player.sendMessage(ChatColor.GREEN + "[Friends] Unblocked " + friendService.displayName(targetId) + ".");
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /friend");
                return true;
            }
        }
    }

    private boolean handleMatchTeam(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        List<MatchTeamService.MatchTeam> teams = matchTeamService.teamsFor(player.getUniqueId());
        if (teams.isEmpty()) {
            player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW
                + "You do not have an assigned match team.");
            return true;
        }
        for (MatchTeamService.MatchTeam team : teams) {
            player.sendMessage(HydraIcons.EXCLAMATION + " " + team.color() + team.displayName()
                + ChatColor.GRAY + " team");
            for (UUID memberId : team.members()) {
                player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + partyService.displayName(memberId));
            }
        }
        return true;
    }

    private boolean handleTempBan(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.tempban", "You do not have permission to temporarily ban players.")) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempban <player> <duration> <reason>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot temporarily ban yourself.");
            return true;
        }
        if ((target.isOp() || hasStaffRank(target.getUniqueId()))
            && !sender.hasPermission("hgloot.moderation.tempban.override")) {
            sender.sendMessage(ChatColor.RED + "You cannot temporarily ban staff members.");
            return true;
        }
        long durationMs = parseDurationMillis(args[1]);
        if (durationMs <= 0L) {
            sender.sendMessage(ChatColor.RED + "Duration must be temporary, such as 10m, 2h, or 7d.");
            return true;
        }
        String reason = joinArgs(args, 2);
        if (reason.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Ban reason cannot be blank.");
            return true;
        }
        long expiresAtMs;
        try {
            expiresAtMs = Math.addExact(System.currentTimeMillis(), durationMs);
        } catch (ArithmeticException exception) {
            sender.sendMessage(ChatColor.RED + "That ban duration is too large.");
            return true;
        }
        target.ban(reason, Instant.ofEpochMilli(expiresAtMs), sender.getName());
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.kickPlayer(ChatColor.RED + "Temporarily banned from Hydra\n"
                + ChatColor.GRAY + "Duration: " + formatDuration(durationMs) + "\n"
                + ChatColor.GRAY + "Reason: " + ChatColor.YELLOW + reason);
        }
        sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Temporarily banned "
            + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + " for " + formatDuration(durationMs) + ".");
        broadcastToModerators(ChatColor.YELLOW + "[Moderation] " + ChatColor.WHITE + sender.getName()
            + ChatColor.GRAY + " temporarily banned " + ChatColor.WHITE + safeOfflineName(target)
            + ChatColor.GRAY + " for " + formatDuration(durationMs) + ChatColor.GRAY + ": " + ChatColor.YELLOW + reason);
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.moderation.tempban", "You do not have permission to unban players.")) {
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
        if (!bans.isBanned(target.getPlayerProfile())) {
            sender.sendMessage(ChatColor.YELLOW + safeOfflineName(target) + " is not profile-banned.");
            return true;
        }
        bans.pardon(target.getPlayerProfile());
        sender.sendMessage(ChatColor.AQUA + "[Moderation] " + ChatColor.GREEN + "Unbanned "
            + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GREEN + ".");
        broadcastToModerators(ChatColor.YELLOW + "[Moderation] " + ChatColor.WHITE + sender.getName()
            + ChatColor.GRAY + " unbanned " + ChatColor.WHITE + safeOfflineName(target) + ChatColor.GRAY + ".");
        return true;
    }

    private boolean handleChatToggle(CommandSender sender, boolean partyChat) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) return true;
        if (partyChat) {
            hydraPlusService.togglePartyChat(player);
        } else {
            hydraPlusService.toggleTeamChat(player);
        }
        return true;
    }

    private boolean handleParty(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.party", "You do not have permission to use party commands.")) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            menuService.openPlayerPartyMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                PartyService.CreateResult result = partyService.createParty(playerId);
                if (result == PartyService.CreateResult.ALREADY_IN_PARTY) {
                    player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You are already in a party.");
                } else if (result == PartyService.CreateResult.LOCKED) {
                    sendPartyLocked(player);
                } else {
                    player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Party created. You are the leader.");
                }
                return true;
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party join <leader>");
                    return true;
                }
                Player leader = Bukkit.getPlayerExact(args[1]);
                if (leader == null) {
                    player.sendMessage(ChatColor.RED + "Party leader must be online.");
                    return true;
                }
                PartyService.PublicJoinResult result = partyService.joinPublic(playerId, leader.getUniqueId());
                switch (result) {
                    case JOINED -> player.sendMessage(ChatColor.GREEN + "Joined " + leader.getName() + "'s public party.");
                    case PRIVATE -> player.sendMessage(ChatColor.RED + "That party is private.");
                    case PARTY_FULL -> player.sendMessage(ChatColor.RED + "That party is full.");
                    case ALREADY_IN_PARTY -> player.sendMessage(ChatColor.YELLOW + "Leave your current party first.");
                    case LOCKED -> sendPartyLocked(player);
                    case SELF, UNAVAILABLE -> player.sendMessage(ChatColor.RED + "That public party is unavailable.");
                }
                return true;
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player must be online: " + args[1]);
                    return true;
                }
                if (!partyService.isInParty(playerId)) {
                    PartyService.CreateResult created = partyService.createParty(playerId);
                    if (created == PartyService.CreateResult.LOCKED) {
                        sendPartyLocked(player);
                        return true;
                    }
                    player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Party created automatically. You are the leader.");
                }

                PartyService.InviteResult result = partyService.invite(playerId, target.getUniqueId());
                switch (result) {
                    case SELF -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "You cannot invite yourself.");
                    case NOT_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Create a party first with /party create.");
                    case NOT_LEADER -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Only the party leader can invite players.");
                    case PARTY_FULL -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Your party is full.");
                    case TARGET_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + target.getName() + " is already in another party.");
                    case SAME_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "" + target.getName() + " is already in your party.");
                    case ALREADY_INVITED -> player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "" + target.getName() + " already has your invite.");
                    case INVITES_DISABLED -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + target.getName() + " has party invites disabled.");
                    case LOCKED -> sendPartyLocked(player);
                    case SENT -> {
                        player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Invite sent to " + target.getName() + ".");
                        Component invite = Component.text(HydraIcons.EXCLAMATION_CHAR + " ", NamedTextColor.AQUA)
                            .append(Component.text(player.getName() + " invited you to their party ", NamedTextColor.AQUA))
                            .append(Component.text("[Click here to Join]", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/party accept " + player.getName()))
                                .hoverEvent(HoverEvent.showText(Component.text("Join " + player.getName() + "'s party", NamedTextColor.GREEN))));
                        target.sendMessage(invite);
                    }
                }
                return true;
            }
            case "accept" -> {
                List<UUID> invites = partyService.getIncomingInvites(playerId);
                if (invites.isEmpty()) {
                    player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You have no pending party invites.");
                    return true;
                }
                UUID inviterId;
                if (args.length >= 2) {
                    inviterId = resolveByName(invites, args[1], partyService::displayName);
                    if (inviterId == null) {
                        player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No invite from " + args[1] + ".");
                        return true;
                    }
                } else if (invites.size() == 1) {
                    inviterId = invites.get(0);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /party accept <player>");
                    return true;
                }

                PartyService.InviteResponse response = partyService.acceptInvite(playerId, inviterId);
                switch (response) {
                    case ACCEPTED -> {
                        String inviterName = partyService.displayName(inviterId);
                        player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Joined " + inviterName + "'s party.");
                        List<UUID> members = partyService.getPartyMembers(playerId);
                        for (UUID memberId : members) {
                            if (memberId.equals(playerId)) {
                                continue;
                            }
                            Player member = Bukkit.getPlayer(memberId);
                            if (member != null && member.isOnline()) {
                                member.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.AQUA + "" + player.getName() + " joined the party.");
                            }
                        }
                    }
                    case NO_INVITE -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "That invite no longer exists.");
                    case INVITER_UNAVAILABLE -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "The inviter is no longer available.");
                    case TARGET_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Leave your current party first.");
                    case PARTY_FULL -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "That party is full.");
                    case LOCKED -> sendPartyLocked(player);
                    case DENIED -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not accept that invite.");
                }
                return true;
            }
            case "deny" -> {
                List<UUID> invites = partyService.getIncomingInvites(playerId);
                if (invites.isEmpty()) {
                    player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You have no pending party invites.");
                    return true;
                }
                UUID inviterId;
                if (args.length >= 2) {
                    inviterId = resolveByName(invites, args[1], partyService::displayName);
                    if (inviterId == null) {
                        player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No invite from " + args[1] + ".");
                        return true;
                    }
                } else if (invites.size() == 1) {
                    inviterId = invites.get(0);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /party deny <player>");
                    return true;
                }

                PartyService.InviteResponse response = partyService.denyInvite(playerId, inviterId);
                if (response != PartyService.InviteResponse.DENIED) {
                    player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not deny that invite.");
                    return true;
                }
                String inviterName = partyService.displayName(inviterId);
                player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Denied invite from " + inviterName + ".");
                Player inviter = Bukkit.getPlayer(inviterId);
                if (inviter != null && inviter.isOnline()) {
                    inviter.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "" + player.getName() + " denied your invite.");
                }
                return true;
            }
            case "leave" -> {
                List<UUID> oldMembers = partyService.getPartyMembers(playerId);
                PartyService.LeaveResult result = partyService.leave(playerId);
                switch (result) {
                    case NOT_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You are not in a party.");
                    case LOCKED -> sendPartyLocked(player);
                    case LEFT -> {
                        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You left the party.");
                        for (UUID memberId : oldMembers) {
                            if (memberId.equals(playerId)) {
                                continue;
                            }
                            Player member = Bukkit.getPlayer(memberId);
                            if (member != null && member.isOnline()) {
                                member.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.AQUA + "" + player.getName() + " left the party.");
                            }
                        }
                    }
                    case LEADER_DISBANDED -> {
                        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Party disbanded.");
                        for (UUID memberId : oldMembers) {
                            if (memberId.equals(playerId)) {
                                continue;
                            }
                            Player member = Bukkit.getPlayer(memberId);
                            if (member != null && member.isOnline()) {
                                member.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Party disbanded by the leader.");
                            }
                        }
                    }
                }
                return true;
            }
            case "disband" -> {
                List<UUID> members = partyService.getPartyMembers(playerId);
                PartyService.DisbandResult result = partyService.disband(playerId);
                switch (result) {
                    case NOT_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You are not in a party.");
                    case NOT_LEADER -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Only the leader can disband the party.");
                    case LOCKED -> sendPartyLocked(player);
                    case DISBANDED -> {
                        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Party disbanded.");
                        for (UUID memberId : members) {
                            if (memberId.equals(playerId)) {
                                continue;
                            }
                            Player member = Bukkit.getPlayer(memberId);
                            if (member != null && member.isOnline()) {
                                member.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Party disbanded by the leader.");
                            }
                        }
                    }
                }
                return true;
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party kick <player>");
                    return true;
                }
                List<UUID> members = partyService.getPartyMembers(playerId);
                UUID memberId = resolveByName(members, args[1], partyService::displayName);
                if (memberId == null) {
                    player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + args[1] + " is not in your party.");
                    return true;
                }
                PartyService.KickResult result = partyService.kick(playerId, memberId);
                switch (result) {
                    case NOT_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You are not in a party.");
                    case NOT_LEADER -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Only the leader can kick members.");
                    case CANNOT_KICK_SELF -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Use /party leave or /party disband.");
                    case NOT_IN_YOUR_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "That player is not in your party.");
                    case LOCKED -> sendPartyLocked(player);
                    case KICKED -> {
                        String kickedName = partyService.displayName(memberId);
                        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Kicked " + kickedName + ".");
                        Player kicked = Bukkit.getPlayer(memberId);
                        if (kicked != null && kicked.isOnline()) {
                            kicked.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "You were kicked from the party.");
                        }
                    }
                }
                return true;
            }
            case "promote" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party promote <player>");
                    return true;
                }
                List<UUID> members = partyService.getPartyMembers(playerId);
                UUID memberId = resolveByName(members, args[1], partyService::displayName);
                if (memberId == null) {
                    player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + args[1] + " is not in your party.");
                    return true;
                }
                PartyService.PromoteResult result = partyService.promote(playerId, memberId);
                switch (result) {
                    case NOT_IN_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You are not in a party.");
                    case NOT_LEADER -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Only the leader can transfer leadership.");
                    case CANNOT_PROMOTE_SELF -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "You are already the leader.");
                    case NOT_IN_YOUR_PARTY -> player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "That player is not in your party.");
                    case LOCKED -> sendPartyLocked(player);
                    case PROMOTED -> {
                        String promotedName = partyService.displayName(memberId);
                        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GOLD + "" + promotedName + " is now the party leader.");
                        for (UUID partyMemberId : members) {
                            if (partyMemberId.equals(playerId) || partyMemberId.equals(memberId)) {
                                continue;
                            }
                            Player member = Bukkit.getPlayer(partyMemberId);
                            if (member != null && member.isOnline()) {
                                member.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GOLD + "" + promotedName + " is now the party leader.");
                            }
                        }
                        Player promoted = Bukkit.getPlayer(memberId);
                        if (promoted != null && promoted.isOnline()) {
                            promoted.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GOLD + "You are now the party leader.");
                        }
                    }
                }
                return true;
            }
            case "list" -> {
                if (!partyService.isInParty(playerId)) {
                    player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "You are not in a party.");
                    return true;
                }
                UUID leaderId = partyService.getLeader(playerId);
                List<UUID> members = partyService.getPartyMembers(playerId);
                player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GOLD + "Members (" + members.size() + "/" + partyService.getMaxPartySize() + ")");
                for (UUID id : members) {
                    Player online = Bukkit.getPlayer(id);
                    String status = online != null && online.isOnline() ? ChatColor.GREEN + "online" : ChatColor.GRAY + "offline";
                    String marker = id.equals(leaderId) ? ChatColor.GOLD + "*" : ChatColor.GRAY + "-";
                    player.sendMessage(marker + " " + ChatColor.WHITE + partyService.displayName(id)
                        + ChatColor.DARK_GRAY + " (" + status + ChatColor.DARK_GRAY + ")");
                }
                return true;
            }
            case "requests" -> {
                List<UUID> invites = partyService.getIncomingInvites(playerId);
                if (invites.isEmpty()) {
                    player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "No pending invites.");
                    return true;
                }
                player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GOLD + "pending invites (" + invites.size() + ")");
                invites.stream()
                    .map(partyService::displayName)
                    .sorted(String::compareToIgnoreCase)
                    .forEach(name -> player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + name));
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /party");
                return true;
            }
        }
    }

    private boolean handleQueueAdmin(Player player, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                queueService.sendAdminList(player);
                return true;
            }
            case "clear" -> {
                QueueService.QueueType type = null;
                if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
                    type = QueueService.QueueType.fromKey(args[1]);
                    if (type == null) {
                        player.sendMessage(ChatColor.RED + "Usage: /queue clear [solo|duo|hungergames|all]");
                        return true;
                    }
                }
                queueService.clearQueue(type);
                player.sendMessage(ChatColor.GREEN + "Queue cleared.");
                return true;
            }
            case "forcestart" -> {
                QueueService.QueueType type = args.length >= 2
                    ? QueueService.QueueType.fromKey(args[1])
                    : QueueService.QueueType.SOLO;
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue forcestart [solo|duo|hungergames]");
                    return true;
                }
                queueService.forceStart(type, player);
                return true;
            }
            case "forcejoin" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue forcejoin <player> [solo|duo|hungergames]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found online: " + args[1]);
                    return true;
                }
                QueueService.QueueType type = args.length >= 3
                    ? QueueService.QueueType.fromKey(args[2])
                    : QueueService.QueueType.SOLO;
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue forcejoin <player> [solo|duo|hungergames]");
                    return true;
                }
                queueService.forceJoinQueue(target, type);
                player.sendMessage(ChatColor.GREEN + "Force-joined " + target.getName() + " to " + type.displayName() + ".");
                return true;
            }
            case "forcewin" -> {
                QueueService.QueueType type = null;
                if (args.length >= 2) {
                    type = QueueService.QueueType.fromKey(args[1]);
                    if (type == null) {
                        player.sendMessage(ChatColor.RED + "Usage: /queue forcewin [solo|duo|hungergames]");
                        return true;
                    }
                }
                queueService.triggerWinCheck(type, player);
                return true;
            }
            case "forceend" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue forceend <solo|duo|hungergames> [world]");
                    return true;
                }
                QueueService.QueueType type = QueueService.QueueType.fromKey(args[1]);
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue forceend <solo|duo|hungergames> [world]");
                    return true;
                }
                queueService.forceEndMatch(type, args.length >= 3 ? args[2] : null, player);
                return true;
            }
            case "autowin" -> {
                if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
                    player.sendMessage(ChatColor.GRAY + "Auto win trigger: "
                        + (queueService.isAutoWinTriggerEnabled()
                        ? ChatColor.GREEN + "ENABLED"
                        : ChatColor.RED + "DISABLED"));
                    player.sendMessage(ChatColor.GRAY + "Usage: /queue autowin <on|off|toggle|status>");
                    return true;
                }

                String mode = args[1].toLowerCase(Locale.ROOT);
                switch (mode) {
                    case "on", "enable", "enabled", "true", "1" ->
                        queueService.setAutoWinTriggerEnabled(true, player);
                    case "off", "disable", "disabled", "false", "0" ->
                        queueService.setAutoWinTriggerEnabled(false, player);
                    case "toggle" ->
                        queueService.setAutoWinTriggerEnabled(!queueService.isAutoWinTriggerEnabled(), player);
                    default -> player.sendMessage(ChatColor.RED + "Usage: /queue autowin <on|off|toggle|status>");
                }
                return true;
            }
            case "cleanup" -> {
                return handleQueueCleanupAdmin(player, trimLeadingArg(args), null);
            }
            case "map" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin hg map <outcove|wildwest|wild_west|aztec|aztec_jungle|random>");
                    return true;
                }
                String key = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "outcove", "city", "ruinedcity", "ruined_city" -> "outcove";
                    case "wildwest", "wild_west", "west" -> "wild_west";
                    case "aztec", "aztecjungle", "aztec_jungle", "jungle", "forest" -> "aztec_jungle";
                    case "random", "rand" -> "random";
                    default -> "";
                };
                if (key.isBlank()) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin hg map <outcove|wildwest|wild_west|aztec|aztec_jungle|random>");
                    return true;
                }
                queueService.selectHungerGamesMap(key, player);
                return true;
            }
            case "switch" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /admin br switch <"
                        + String.join("|", queueService.availableBattleRoyaleTerrainProfiles(player)) + ">");
                    return true;
                }
                queueService.selectBattleRoyaleTerrainProfile(args[1], player);
                return true;
            }
            case "refill" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("early") && args[2].equalsIgnoreCase("start")) {
                    queueService.startHungerGamesRefillEarly(player);
                    return true;
                }
                player.sendMessage(ChatColor.RED + "Usage: /admin hg refill early start");
                return true;
            }
            case "valous" -> {
                queueService.handleValousGenerationAdmin(player, trimLeadingArg(args));
                return true;
            }
            case "test" -> {
                queueService.handleBrTestAdmin(player, trimLeadingArg(args));
                return true;
            }
            case "setlobby" -> {
                queueService.setLobby(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Lobby spawn saved.");
                return true;
            }
            case "setarena" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue setarena <index>");
                    return true;
                }
                int index = parsePositive(args[1], -1);
                if (index < 1 || index > 200) {
                    player.sendMessage(ChatColor.RED + "Index must be between 1 and 200.");
                    return true;
                }
                queueService.setArenaSpawn(index, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Arena spawn #" + index + " saved.");
                return true;
            }
            case "removearena" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /queue removearena <index>");
                    return true;
                }
                int index = parsePositive(args[1], -1);
                if (index < 1 || !queueService.removeArenaSpawn(index)) {
                    player.sendMessage(ChatColor.RED + "Invalid arena index.");
                    return true;
                }
                player.sendMessage(ChatColor.GREEN + "Arena spawn #" + index + " removed.");
                return true;
            }
            case "arenas" -> {
                queueService.sendArenaList(player);
                return true;
            }
            case "structure" -> {
                if (args.length < 2) {
                    queueService.sendStructureHelp(player);
                    return true;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                switch (mode) {
                    case "help" -> {
                        queueService.sendStructureHelp(player);
                        return true;
                    }
                    case "list" -> {
                        queueService.sendStructureIdList(player);
                        return true;
                    }
                    case "random" -> {
                        queueService.placeRandomStructureAtPlayer(player);
                        return true;
                    }
                    case "bundle" -> {
                        String id = args.length >= 3 ? args[2] : null;
                        queueService.placeBundledStructureAtPlayer(player, id);
                        return true;
                    }
                    case "spawn" -> {
                        if (args.length < 3) {
                            player.sendMessage(ChatColor.RED + "Usage: /queue structure spawn <id>");
                            return true;
                        }
                        queueService.placeStructureByIdAtPlayer(player, args[2]);
                        return true;
                    }
                    case "grid" -> {
                        int count = args.length >= 3 ? parsePositive(args[2], -1) : 9;
                        if (count < 1 || count > 64) {
                            player.sendMessage(ChatColor.RED + "Count must be between 1 and 64.");
                            return true;
                        }
                        queueService.placeStructureGridAtPlayer(player, count);
                        return true;
                    }
                    case "preview" -> {
                        queueService.previewMatchStructurePlacement(player);
                        return true;
                    }
                    case "locate" -> {
                        String structureId = null;
                        int radius = 256;
                        if (args.length >= 3) {
                            int parsedRadius = parsePositive(args[2], -1);
                            if (parsedRadius > 0) {
                                radius = parsedRadius;
                            } else {
                                structureId = args[2];
                                WorldBorder border = player.getWorld().getWorldBorder();
                                radius = Math.max(256, (int) Math.ceil(border.getSize() / 2.0D) + 32);
                                if (args.length >= 4) {
                                    radius = parsePositive(args[3], -1);
                                }
                            }
                        }
                        if (radius < 1 || radius > 8192) {
                            player.sendMessage(ChatColor.RED + "Usage: /queue structure locate [id] [radius]");
                            return true;
                        }
                        queueService.locateStructureMarkers(player, structureId, radius);
                        return true;
                    }
                    case "spawns", "where" -> {
                        int radius = args.length >= 3 ? parsePositive(args[2], -1) : 4096;
                        if (radius < 1 || radius > 8192) {
                            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 8192.");
                            return true;
                        }
                        queueService.sendStructureSpawnReport(player, radius);
                        return true;
                    }
                    case "rejects" -> {
                        queueService.sendStructureRejectSummary(player);
                        return true;
                    }
                    case "validate" -> {
                        int radius = args.length >= 3 ? parsePositive(args[2], -1) : 64;
                        if (radius < 1 || radius > 512) {
                            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 512.");
                            return true;
                        }
                        queueService.validateStructureMarkers(player, radius);
                        return true;
                    }
                    case "clear" -> {
                        int radius = args.length >= 3 ? parsePositive(args[2], -1) : 64;
                        if (radius < 1 || radius > 512) {
                            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 512.");
                            return true;
                        }
                        queueService.clearStructureMarkers(player, radius);
                        return true;
                    }
                    default -> {
                        queueService.sendStructureHelp(player);
                        return true;
                    }
                }
            }
            case "cave" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
                    queueService.sendCaveDebug(player);
                    return true;
                }
                player.sendMessage(ChatColor.RED + "Usage: /queue cave debug");
                return true;
            }
            case "preload" -> {
                World targetWorld = player.getWorld();
                if (args.length >= 2 && !args[1].equalsIgnoreCase("here")) {
                    World named = Bukkit.getWorld(args[1]);
                    if (named == null) {
                        player.sendMessage(ChatColor.RED + "World not found: " + args[1]);
                        return true;
                    }
                    targetWorld = named;
                }
                queueService.startFullWorldChunkPreload(targetWorld, player);
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown queue admin command.");
                return true;
            }
        }
    }

    private boolean handleQueueCleanupAdmin(Player player, String[] args) {
        return handleQueueCleanupAdmin(player, args, null);
    }

    private boolean handleQueueCleanupAdmin(Player player, String[] args, QueueService.QueueType cleanupType) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            player.sendMessage(ChatColor.GRAY + "Match world cleanup: "
                + (queueService.isMatchWorldAutoCleanupEnabled()
                ? ChatColor.GREEN + "ENABLED"
                : ChatColor.RED + "DISABLED"));
            player.sendMessage(ChatColor.GRAY + "Usage: /queue cleanup <on|off|status|purge>");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "on", "enable", "enabled", "true", "1" ->
                queueService.setMatchWorldAutoCleanupEnabled(true, player);
            case "off", "disable", "disabled", "false", "0" ->
                queueService.setMatchWorldAutoCleanupEnabled(false, player);
            case "purge", "stale", "cleanstale" ->
                queueService.cleanupStaleMatchWorldFolders(player, cleanupType);
            default ->
                player.sendMessage(ChatColor.RED + "Usage: /queue cleanup <on|off|status|purge>");
        }
        return true;
    }

    private boolean handleLootLine(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /lootline <category>");
            return true;
        }
        LootCategory.fromKey(args[0]).ifPresentOrElse(
            category -> menuService.spawnLine(player, category),
            () -> player.sendMessage(ChatColor.RED + "Unknown category: " + args[0])
        );
        return true;
    }

    private boolean handleLootGrid(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            player.sendMessage(ChatColor.RED + "Usage: /lootgrid <category> <index> [size]");
            return true;
        }
        LootCategory category = LootCategory.fromKey(args[0]).orElse(null);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown category: " + args[0]);
            return true;
        }
        int index = parsePositive(args[1], -1);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return true;
        }
        int size = args.length == 3 ? parsePositive(args[2], -1) : 6;
        if (size < 1 || size > 12) {
            player.sendMessage(ChatColor.RED + "Size must be between 1 and 12.");
            return true;
        }
        menuService.spawnGrid(player, category, index, size);
        return true;
    }

    private boolean handleLootSim(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            player.sendMessage(ChatColor.RED + "Usage: /lootsim <category> <index> [iterations]");
            return true;
        }
        LootCategory category = LootCategory.fromKey(args[0]).orElse(null);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown category: " + args[0]);
            return true;
        }
        int index = parsePositive(args[1], -1);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return true;
        }
        int iterations = args.length == 3 ? parsePositive(args[2], -1) : 100;
        if (iterations < 1 || iterations > 10000) {
            player.sendMessage(ChatColor.RED + "Iterations must be between 1 and 10000.");
            return true;
        }
        menuService.simulateLoot(player, category, index, iterations);
        return true;
    }

    private boolean handleLootRadius(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            player.sendMessage(ChatColor.RED + "Usage: /lootradius <category> <index> [radius]");
            return true;
        }
        LootCategory category = LootCategory.fromKey(args[0]).orElse(null);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown category: " + args[0]);
            return true;
        }
        int index = parsePositive(args[1], -1);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return true;
        }
        int radius = args.length == 3 ? parsePositive(args[2], -1) : 20;
        if (radius < 1 || radius > 20) {
            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 20.");
            return true;
        }
        menuService.spawnRadius(player, category, index, radius);
        return true;
    }

    private boolean handleLootRefill(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "Usage: /lootrefill [radius]");
            return true;
        }
        int radius = args.length == 1 ? parsePositive(args[0], -1) : 20;
        if (radius < 1 || radius > 64) {
            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 64.");
            return true;
        }
        menuService.refillPlaced(player, radius);
        return true;
    }

    private boolean handleLootClear(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "Usage: /lootclear [radius]");
            return true;
        }
        int radius = args.length == 1 ? parsePositive(args[0], -1) : 20;
        if (radius < 1 || radius > 64) {
            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 64.");
            return true;
        }
        menuService.clearPlaced(player, radius);
        return true;
    }

    private boolean handleCombatMaster(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.boss", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("spawn")) {
            combatMasterService.spawnCombatMaster(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("force")) {
            combatMasterService.spawnCombatMasterForce(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            boolean ok = combatMasterService.resetNearestCombatMaster(player);
            if (ok) {
                player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Nearest Combat Master reset.");
            } else {
                player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No Combat Master found nearby.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("phase")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /combatmaster phase <artillery|bulwark|discipline|final>");
                return true;
            }
            boolean ok = combatMasterService.forceNearestPhase(player, args[1]);
            if (ok) {
                player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Forced nearest Combat Master phase to "
                    + args[1].toLowerCase(Locale.ROOT) + ".");
            } else {
                player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not force phase. Check phase name and nearby boss.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            List<String> lines = combatMasterService.debugNearest(player);
            if (lines.isEmpty()) {
                player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No Combat Master found nearby.");
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "[Combat Master Debug]");
            for (String line : lines) {
                player.sendMessage(ChatColor.GRAY + "- " + line);
            }
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /combatmaster [spawn|force|reset|phase <artillery|bulwark|discipline|final>|debug]");
        return true;
    }

    private boolean handleAdminBosses(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            menuService.openAdminBossToolsMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            player.sendMessage(ChatColor.GOLD + "[Hydra Bosses]");
            player.sendMessage(ChatColor.GREEN + "- combat_master " + ChatColor.GRAY + "(implemented and summonable)");
            player.sendMessage(ChatColor.DARK_GRAY + "No other custom boss controller is currently registered.");
            return true;
        }
        if (sub.equals("summon") || sub.equals("spawn")) {
            if (args.length < 2 || !isCombatMasterBossId(args[1])) {
                player.sendMessage(ChatColor.RED + "Usage: /admin bosses summon combat_master");
                return true;
            }
            combatMasterService.spawnCombatMasterForce(player);
            return true;
        }
        if (sub.equals("reset")) {
            boolean ok = combatMasterService.resetNearestCombatMaster(player);
            player.sendMessage(ok
                ? HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Nearest Combat Master reset."
                : HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No Combat Master found nearby.");
            return true;
        }
        if (sub.equals("phase")) {
            if (args.length < 3 || !isCombatMasterBossId(args[1])) {
                player.sendMessage(ChatColor.RED + "Usage: /admin bosses phase combat_master <artillery|bulwark|discipline|final>");
                return true;
            }
            boolean ok = combatMasterService.forceNearestPhase(player, args[2]);
            player.sendMessage(ok
                ? HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Forced nearest Combat Master phase to "
                    + args[2].toLowerCase(Locale.ROOT) + "."
                : HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not force that phase on a nearby Combat Master.");
            return true;
        }
        if (sub.equals("debug")) {
            List<String> lines = combatMasterService.debugNearest(player);
            if (lines.isEmpty()) {
                player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No Combat Master found nearby.");
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "[Combat Master Debug]");
            for (String line : lines) {
                player.sendMessage(ChatColor.GRAY + "- " + line);
            }
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /admin bosses <menu|list|summon combat_master|reset|phase combat_master <phase>|debug>");
        return true;
    }

    private static boolean isCombatMasterBossId(String value) {
        return value != null && (value.equalsIgnoreCase("combat_master") || value.equalsIgnoreCase("combatmaster"));
    }

    private boolean handleCombatChamber(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.debug", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length == 1) {
            if (combatChamberService == null) {
                player.sendMessage(ChatColor.RED + "[Combat Chamber] Service is unavailable.");
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "testvenom" -> {
                    combatChamberService.testVenom(player);
                    return true;
                }
                case "finish" -> {
                    combatChamberService.clearVenom(player);
                    return true;
                }
                case "yes", "escape" -> {
                    combatChamberService.chooseVenomReinforcement(player, false);
                    return true;
                }
                case "no" -> {
                    combatChamberService.chooseVenomReinforcement(player, true);
                    return true;
                }
                case "stay" -> {
                    combatChamberService.chooseFinalRingTransition(player, true);
                    return true;
                }
                case "leave" -> {
                    combatChamberService.chooseFinalRingTransition(player, false);
                    return true;
                }
                default -> {
                }
            }
        }
        if (args.length == 4
            && args[0].equalsIgnoreCase("venom")
            && args[1].equalsIgnoreCase("wave")
            && args[2].equalsIgnoreCase("set")) {
            if (combatChamberService == null) {
                player.sendMessage(ChatColor.RED + "[Combat Chamber] Service is unavailable.");
                return true;
            }
            try {
                combatChamberService.setVenomWave(player, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "[Combat Chamber] Use wave 1-4.");
            }
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /combatchamber <testvenom|finish|yes|no|stay|leave|venom wave set <1-4>>");
        return true;
    }

    private boolean handleAdminKoth(Player player, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("test")) {
            player.sendMessage(ChatColor.RED + "Usage: /admin koth test [count]");
            return true;
        }
        if (queueService == null) {
            player.sendMessage(ChatColor.RED + "Queue service is unavailable right now.");
            return true;
        }
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Count must be a whole number. Usage: /admin koth test [count]");
                return true;
            }
            if (count < 1) {
                player.sendMessage(ChatColor.RED + "Count must be at least 1.");
                return true;
            }
        }
        queueService.runKothSelectionTest(player, count);
        return true;
    }

    private boolean handleKoth(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.koth", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage:");
            sender.sendMessage(ChatColor.GRAY + "/koth setpos1");
            sender.sendMessage(ChatColor.GRAY + "/koth setpos2");
            sender.sendMessage(ChatColor.GRAY + "/koth start [delaySeconds]");
            sender.sendMessage(ChatColor.GRAY + "/koth stop");
            sender.sendMessage(ChatColor.GRAY + "/koth status");
            sender.sendMessage(ChatColor.GRAY + "/koth countdown <seconds>");
            sender.sendMessage(ChatColor.GRAY + "/koth capture <seconds>");
            sender.sendMessage(ChatColor.GRAY + "/koth earlystart <seconds|now> <matchWorld>");
            sender.sendMessage(ChatColor.GRAY + "/koth addtime <seconds> <matchWorld>");
            sender.sendMessage(ChatColor.GRAY + "/koth removetime <seconds> <matchWorld>");
            sender.sendMessage(ChatColor.GRAY + "/koth instantstart <matchWorld>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setpos1" -> {
                Player player = requirePlayer(sender, "Only players can run /koth setpos1.");
                if (player == null) {
                    return true;
                }
                boolean ok = kothService.setPos1(player.getLocation());
                player.sendMessage(ok
                    ? ChatColor.GREEN + "Saved KOTH pos1."
                    : ChatColor.RED + "Could not save KOTH pos1.");
                return true;
            }
            case "setpos2" -> {
                Player player = requirePlayer(sender, "Only players can run /koth setpos2.");
                if (player == null) {
                    return true;
                }
                KothService.SetPos2Result result = kothService.setPos2(player.getLocation());
                switch (result) {
                    case OK -> player.sendMessage(ChatColor.GREEN + "Saved KOTH zone.");
                    case MISSING_POS1 -> player.sendMessage(ChatColor.RED + "Set pos1 first.");
                    case WORLD_MISMATCH -> player.sendMessage(ChatColor.RED + "pos1 and pos2 must be in the same world.");
                    case INVALID_LOCATION -> player.sendMessage(ChatColor.RED + "Invalid location.");
                }
                return true;
            }
            case "start" -> {
                Integer overrideDelay = null;
                if (args.length >= 2) {
                    int seconds = parsePositive(args[1], -1);
                    if (seconds < 0) {
                        sender.sendMessage(ChatColor.RED + "Delay must be >= 0.");
                        return true;
                    }
                    overrideDelay = seconds;
                }
                KothService.StartResult result = kothService.startEvent(overrideDelay);
                switch (result) {
                    case STARTED -> sender.sendMessage(ChatColor.GREEN + "KOTH started.");
                    case NO_ZONE -> sender.sendMessage(ChatColor.RED + "KOTH zone is not set. Use /koth setpos1 and /koth setpos2.");
                    case INVALID_WORLD -> sender.sendMessage(ChatColor.RED + "KOTH world is not loaded.");
                    case ALREADY_RUNNING -> sender.sendMessage(ChatColor.RED + "KOTH is already running.");
                }
                return true;
            }
            case "stop" -> {
                boolean stopped;
                if (args.length >= 2) {
                    String worldName = queueService == null ? args[1] : queueService.resolveManagedMatchWorldName(args[1]);
                    if (worldName == null) {
                        sender.sendMessage(ChatColor.RED + "Unknown match world: " + args[1]);
                        return true;
                    }
                    stopped = kothService.stopEventInWorld(worldName);
                } else {
                    stopped = kothService.stopEvent();
                }
                sender.sendMessage(stopped
                    ? ChatColor.GREEN + "KOTH stopped."
                    : ChatColor.RED + "KOTH is not running.");
                return true;
            }
            case "status" -> {
                if (args.length >= 2) {
                    if (queueService == null) {
                        sender.sendMessage(ChatColor.RED + "Queue service is not available.");
                        return true;
                    }
                    String worldName = queueService.resolveManagedMatchWorldName(args[1]);
                    if (worldName == null) {
                        sender.sendMessage(ChatColor.RED + "Unknown match world: " + args[1]);
                        return true;
                    }
                    sender.sendMessage(ChatColor.GOLD + "[KOTH] Status for " + worldName);
                    int scheduled = queueService.scheduledHungerGamesKothRemainingSeconds(worldName);
                    if (scheduled >= 0) {
                        sender.sendMessage(ChatColor.GRAY + "- Scheduled Start: " + formatClock(scheduled));
                    }
                    if (kothService.isActiveInWorld(worldName)) {
                        for (String line : kothService.statusLines(worldName)) {
                            sender.sendMessage(ChatColor.GRAY + "- " + line);
                        }
                    } else if (scheduled < 0) {
                        sender.sendMessage(ChatColor.GRAY + "- No scheduled or active KOTH for this match.");
                    }
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "[KOTH] Status");
                for (String line : kothService.statusLines()) {
                    sender.sendMessage(ChatColor.GRAY + "- " + line);
                }
                return true;
            }
            case "countdown" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /koth countdown <seconds>");
                    return true;
                }
                int seconds = parsePositive(args[1], -1);
                if (!kothService.setCountdownSeconds(seconds)) {
                    sender.sendMessage(ChatColor.RED + "Countdown must be at least 5 seconds.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "KOTH countdown set to " + seconds + "s.");
                return true;
            }
            case "capture" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /koth capture <seconds>");
                    return true;
                }
                int seconds = parsePositive(args[1], -1);
                if (!kothService.setCaptureSeconds(seconds)) {
                    sender.sendMessage(ChatColor.RED + "Capture time must be at least 5 seconds.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "KOTH capture time set to " + seconds + "s.");
                return true;
            }
            case "earlystart", "addtime", "removetime", "instantstart" -> {
                if (queueService == null) {
                    sender.sendMessage(ChatColor.RED + "Queue service is not available.");
                    return true;
                }
                boolean instant = sub.equals("instantstart");
                if ((!instant && args.length < 3) || (instant && args.length < 2)) {
                    sender.sendMessage(ChatColor.RED + switch (sub) {
                        case "earlystart" -> "Usage: /koth earlystart <seconds|now> <matchWorld>";
                        case "addtime" -> "Usage: /koth addtime <seconds> <matchWorld>";
                        case "removetime" -> "Usage: /koth removetime <seconds> <matchWorld>";
                        default -> "Usage: /koth instantstart <matchWorld>";
                    });
                    return true;
                }
                String worldArgument = instant ? args[1] : args[2];
                String worldName = queueService.resolveManagedMatchWorldName(worldArgument);
                if (worldName == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown match world: " + worldArgument);
                    return true;
                }

                if (instant || args[1].equalsIgnoreCase("now") || args[1].equalsIgnoreCase("instant")) {
                    QueueService.MatchKothScheduleResult scheduleResult = queueService.startScheduledMatchKothNow(worldName);
                    if (scheduleResult == QueueService.MatchKothScheduleResult.STARTED_NOW) {
                        sender.sendMessage(ChatColor.GREEN + "Started scheduled KOTH now for " + worldName + ".");
                        return true;
                    }
                    KothService.CountdownAdjustResult activeResult = kothService.startNowInWorld(worldName);
                    if (activeResult == KothService.CountdownAdjustResult.STARTED_NOW) {
                        sender.sendMessage(ChatColor.GREEN + "Started active KOTH now for " + worldName + ".");
                        return true;
                    }
                    sender.sendMessage(ChatColor.RED + "No scheduled or starting KOTH found for " + worldName + ".");
                    return true;
                }

                int seconds = parseDurationSeconds(args[1], -1);
                if (seconds < 0) {
                    sender.sendMessage(ChatColor.RED + "Time must be a valid value like 10, 30s, or 2m.");
                    return true;
                }

                int deltaSeconds = switch (sub) {
                    case "earlystart", "removetime" -> -seconds;
                    case "addtime" -> seconds;
                    default -> 0;
                };
                QueueService.MatchKothScheduleResult scheduleResult = queueService.adjustScheduledMatchKoth(worldName, deltaSeconds);
                if (scheduleResult == QueueService.MatchKothScheduleResult.UPDATED) {
                    sender.sendMessage(ChatColor.GREEN + switch (sub) {
                        case "earlystart" -> "Moved scheduled KOTH " + seconds + "s earlier for " + worldName + ".";
                        case "removetime" -> "Removed " + seconds + "s from scheduled KOTH for " + worldName + ".";
                        default -> "Added " + seconds + "s to scheduled KOTH for " + worldName + ".";
                    });
                    return true;
                }
                if (scheduleResult == QueueService.MatchKothScheduleResult.STARTED_NOW) {
                    sender.sendMessage(ChatColor.GREEN + "Started scheduled KOTH now for " + worldName + ".");
                    return true;
                }

                KothService.CountdownAdjustResult activeResult = kothService.adjustCountdownInWorld(worldName, deltaSeconds);
                if (activeResult == KothService.CountdownAdjustResult.UPDATED) {
                    sender.sendMessage(ChatColor.GREEN + switch (sub) {
                        case "earlystart" -> "Moved active KOTH " + seconds + "s earlier for " + worldName + ".";
                        case "removetime" -> "Removed " + seconds + "s from active KOTH for " + worldName + ".";
                        default -> "Added " + seconds + "s to active KOTH for " + worldName + ".";
                    });
                    return true;
                }
                if (activeResult == KothService.CountdownAdjustResult.STARTED_NOW) {
                    sender.sendMessage(ChatColor.GREEN + "Started active KOTH now for " + worldName + ".");
                    return true;
                }
                sender.sendMessage(ChatColor.RED + "No scheduled or starting KOTH found for " + worldName + ".");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /koth");
                return true;
            }
        }
    }

    private boolean handleAuctionHouse(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.casino", "You do not have permission to use the Auction House.")) {
            return true;
        }
        return auctionHouseService.handleCommand(player, args);
    }

    private boolean handleSellShortcut(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.casino", "You do not have permission to use the Auction House.")) {
            return true;
        }
        String[] casinoArgs = new String[args.length + 1];
        casinoArgs[0] = "sell";
        System.arraycopy(args, 0, casinoArgs, 1, args.length);
        return auctionHouseService.handleCommand(player, casinoArgs);
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.casino.pay", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found online: " + args[0]);
            return true;
        }
        int amount = parsePositive(args[1], -1);
        AuctionHouseService.PayResult result = auctionHouseService.paySilver(player, target, amount);
        switch (result) {
            case OK -> {
                int payerBalance = auctionHouseService.getSilverBalance(player.getUniqueId());
                int targetBalance = auctionHouseService.getSilverBalance(target.getUniqueId());
                player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "Paid " + amount + " Coins to "
                    + target.getName() + ChatColor.GRAY + ". Balance: " + payerBalance);
                target.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + player.getName()
                    + " paid you " + amount + " Coins." + ChatColor.GRAY + " Balance: " + targetBalance);
            }
            case SELF -> player.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            case INVALID_AMOUNT -> player.sendMessage(ChatColor.RED + "Amount must be > 0.");
            case INSUFFICIENT -> player.sendMessage(ChatColor.RED + "Not enough Coins.");
            case INVALID_TARGET -> player.sendMessage(ChatColor.RED + "Invalid target.");
        }
        return true;
    }

    private boolean handleWallet(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            sender.sendMessage(ChatColor.YELLOW + "[Wallet] Use /admin wallet <add|set|reset> <player> <levelxp|level|money|shards|fragments|kills|wins> [amount].");
            return handleWalletAdmin(sender, trimLeadingArg(args), "/admin wallet");
        }
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (progressionService == null) {
            player.sendMessage(ChatColor.RED + "Wallet is unavailable right now.");
            return true;
        }
        return progressionService.handleCommand(player, args);
    }

    private boolean handleShard(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can claim Shards.");
        if (player == null) {
            return true;
        }
        if (progressionService == null) {
            player.sendMessage(ChatColor.RED + "Shard claiming is unavailable right now.");
            return true;
        }
        return progressionService.handleCommand(player, args);
    }

    private boolean handleSpectate(CommandSender sender, String[] args) {
        return SpectatorCommandRouter.handle(this, sender, args);
    }

    private boolean handleAdminGift(Player sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin gift <player|@a>");
            return true;
        }
        if (progressionService == null) {
            sender.sendMessage(ChatColor.RED + "Progression is unavailable right now.");
            return true;
        }
        List<Player> targets = new ArrayList<>();
        if (args[0].equalsIgnoreCase("@a")) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
            targets.add(target);
        }
        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No online players matched that target.");
            return true;
        }
        for (Player target : targets) {
            progressionService.grantShards(target.getUniqueId(), 1, "ADMIN_GIFT");
            target.sendMessage(ChatColor.LIGHT_PURPLE + "You were gifted Shard x1 by " + sender.getName());
        }
        sender.sendMessage(ChatColor.GREEN + "Gifted Shard x1 to " + targets.size() + " player(s).");
        return true;
    }

    private boolean handleWalletAdmin(CommandSender sender, String[] args, String label) {
        if (!requirePermission(sender, "hgloot.wallet.admin", "You do not have permission to manage wallets.")) {
            return true;
        }
        if (progressionService == null) {
            sender.sendMessage(ChatColor.RED + "Wallet is unavailable right now.");
            return true;
        }
        if (args.length < 3 || (!args[0].equalsIgnoreCase("reset") && args.length < 4)) {
            sender.sendMessage(ChatColor.RED + "Usage: " + label + " <add|set|reset> <player> <levelxp|level|money|shards|fragments|kills|wins> [amount]");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!action.equals("add") && !action.equals("set") && !action.equals("reset")) {
            sender.sendMessage(ChatColor.RED + "Use add, set, or reset.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();
        String stat = args[2].toLowerCase(Locale.ROOT);
        int amount = 0;
        if (!action.equals("reset")) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                return true;
            }
            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
                return true;
            }
        }

        switch (stat) {
            case "levelxp", "level_xp", "prestigexp", "prestige_xp", "xp" -> {
                if (action.equals("reset")) {
                    progressionService.setPrestigeXp(targetId, 0, "ADMIN");
                    break;
                }
                if (action.equals("add")) {
                    progressionService.grantPrestigeXp(targetId, amount, "ADMIN");
                } else {
                    progressionService.setPrestigeXp(targetId, amount, "ADMIN");
                }
            }
            case "level", "lvl", "prestigelevel", "prestigelvl", "prestige_level", "prestige" -> {
                if (!action.equals("set")) {
                    sender.sendMessage(ChatColor.RED + "Level supports set only.");
                    return true;
                }
                if (!applyAdminStatSet(sender, target, stat, amount)) {
                    return true;
                }
            }
            case "money", "coins", "coin", "gold", "silver" -> {
                if (action.equals("reset")) {
                    applyAdminStatSet(sender, target, stat, 0);
                } else if (action.equals("add")) {
                    if (auctionHouseService == null) {
                        sender.sendMessage(ChatColor.RED + "Economy is unavailable right now.");
                        return true;
                    }
                    auctionHouseService.grantSilver(targetId, amount, "ADMIN");
                } else {
                    applyAdminStatSet(sender, target, stat, amount);
                }
            }
            case "shards", "shard" -> {
                if (action.equals("reset")) {
                    progressionService.setShards(targetId, 0, "ADMIN");
                    break;
                }
                if (action.equals("add")) {
                    progressionService.grantShards(targetId, amount, "ADMIN");
                } else {
                    progressionService.setShards(targetId, amount, "ADMIN");
                }
            }
            case "fragments", "fragment", "shardfragments", "shard_fragments" -> {
                if (action.equals("reset")) {
                    progressionService.setShardFragments(targetId, 0, "ADMIN");
                    break;
                }
                if (action.equals("add")) {
                    progressionService.grantShardFragments(targetId, amount, "ADMIN");
                } else {
                    progressionService.setShardFragments(targetId, amount, "ADMIN");
                }
            }
            case "kills", "kill" -> {
                if (queueService == null) {
                    sender.sendMessage(ChatColor.RED + "Queue stats are unavailable right now.");
                    return true;
                }
                if (!action.equals("set") && !action.equals("reset")) {
                    sender.sendMessage(ChatColor.RED + "Kills supports set or reset.");
                    return true;
                }
                applyAdminStatSet(sender, target, stat, action.equals("reset") ? 0 : amount);
            }
            case "wins", "win" -> {
                if (queueService == null) {
                    sender.sendMessage(ChatColor.RED + "Queue stats are unavailable right now.");
                    return true;
                }
                if (action.equals("reset")) {
                    queueService.resetLifetimeWins(targetId);
                } else if (action.equals("set")) {
                    applyAdminStatSet(sender, target, stat, null, amount);
                } else {
                    sender.sendMessage(ChatColor.RED + "Wins supports set or reset.");
                    return true;
                }
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Stat must be levelxp, level, money, shards, fragments, kills, or wins.");
                return true;
            }
        }
        sender.sendMessage(ChatColor.AQUA + "[Wallet] " + ChatColor.GREEN + action + " "
            + (action.equals("reset") ? "" : ChatColor.WHITE + String.valueOf(amount) + ChatColor.GREEN + " ")
            + stat + " for " + ChatColor.WHITE + (target.getName() == null ? args[1] : target.getName()) + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleEconomyLog(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "hgloot.casino.admin", "You do not have permission to use this.")) {
            return true;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("recent"))) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin economylog recent");
            return true;
        }
        List<String> lines = auctionHouseService.recentEconomyLogs(25);
        if (lines.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[Auction House] No economy log entries yet.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "[Auction House Economy Log]");
        for (String line : lines) {
            sender.sendMessage(ChatColor.GRAY + line);
        }
        return true;
    }

    private boolean handleBorder(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "Only players can run this.");
        if (player == null) {
            return true;
        }
        if (!requirePermission(player, "hgloot.border", "You do not have permission to use this.")) {
            return true;
        }

        if (args.length == 0) {
            sendBorderHelp(player);
            return true;
        }

        WorldBorder border = player.getWorld().getWorldBorder();
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                player.sendMessage(ChatColor.GOLD + "[Master Border]");
                player.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + player.getWorld().getName());
                player.sendMessage(ChatColor.GRAY + "Size: " + ChatColor.WHITE + String.format(Locale.US, "%.2f", border.getSize()));
                player.sendMessage(ChatColor.GRAY + "Center: " + ChatColor.WHITE
                    + String.format(Locale.US, "%.2f, %.2f", border.getCenter().getX(), border.getCenter().getZ()));
                int timer = queueService.borderShrinkTimerRemaining(player.getWorld());
                if (timer >= 0) {
                    player.sendMessage(ChatColor.GRAY + "Next Shrink: " + ChatColor.WHITE
                        + String.format(Locale.US, "%02d:%02d", timer / 60, timer % 60));
                }
                return true;
            }
            case "set" -> {
                if (args.length < 2 || args.length > 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /border set <size> [seconds]");
                    return true;
                }
                double size = parseDouble(args[1], -1.0);
                if (size < 1.0 || size > 60000000.0) {
                    player.sendMessage(ChatColor.RED + "Size must be between 1 and 60000000.");
                    return true;
                }
                int seconds = args.length == 3 ? parsePositive(args[2], -1) : 0;
                if (seconds < 0) {
                    player.sendMessage(ChatColor.RED + "Seconds must be >= 0.");
                    return true;
                }
                if (seconds == 0) {
                    border.setSize(size);
                } else {
                    border.changeSize(size, seconds);
                }
                player.sendMessage(ChatColor.AQUA + "[Master] " + ChatColor.GREEN + "World border set to "
                    + String.format(Locale.US, "%.2f", size)
                    + (seconds > 0 ? ChatColor.GRAY + " over " + seconds + "s." : ChatColor.GRAY + "."));
                return true;
            }
            case "add" -> {
                if (args.length < 2 || args.length > 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /border add <delta> [seconds]");
                    return true;
                }
                double delta = parseDouble(args[1], Double.NaN);
                if (Double.isNaN(delta)) {
                    player.sendMessage(ChatColor.RED + "Delta must be a number.");
                    return true;
                }
                double target = Math.max(1.0, Math.min(60000000.0, border.getSize() + delta));
                int seconds = args.length == 3 ? parsePositive(args[2], -1) : 0;
                if (seconds < 0) {
                    player.sendMessage(ChatColor.RED + "Seconds must be >= 0.");
                    return true;
                }
                if (seconds == 0) {
                    border.setSize(target);
                } else {
                    border.changeSize(target, seconds);
                }
                player.sendMessage(ChatColor.AQUA + "[Master] " + ChatColor.GREEN + "World border changed to "
                    + String.format(Locale.US, "%.2f", target)
                    + (seconds > 0 ? ChatColor.GRAY + " over " + seconds + "s." : ChatColor.GRAY + "."));
                return true;
            }
            case "addseconds" -> {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /border addseconds <seconds>");
                    return true;
                }
                int seconds = parsePositive(args[1], -1);
                if (seconds < 0) {
                    player.sendMessage(ChatColor.RED + "Seconds must be >= 0.");
                    return true;
                }
                int remaining = queueService.adjustBorderShrinkTimer(player.getWorld(), seconds);
                if (remaining < 0) {
                    player.sendMessage(ChatColor.RED + "No active match border timer in this world.");
                    return true;
                }
                player.sendMessage(ChatColor.AQUA + "[Master] " + ChatColor.GREEN + "Added "
                    + seconds + "s to border shrink timer. Next shrink in "
                    + String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60) + ".");
                return true;
            }
            case "removeseconds" -> {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /border removeseconds <seconds>");
                    return true;
                }
                int seconds = parsePositive(args[1], -1);
                if (seconds < 0) {
                    player.sendMessage(ChatColor.RED + "Seconds must be >= 0.");
                    return true;
                }
                int remaining = queueService.adjustBorderShrinkTimer(player.getWorld(), -seconds);
                if (remaining < 0) {
                    player.sendMessage(ChatColor.RED + "No active match border timer in this world.");
                    return true;
                }
                player.sendMessage(ChatColor.AQUA + "[Master] " + ChatColor.GREEN + "Removed "
                    + seconds + "s from border shrink timer. Next shrink in "
                    + String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60) + ".");
                return true;
            }
            case "center" -> {
                if (args.length != 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /border center <x> <z>");
                    return true;
                }
                double x = parseDouble(args[1], Double.NaN);
                double z = parseDouble(args[2], Double.NaN);
                if (Double.isNaN(x) || Double.isNaN(z)) {
                    player.sendMessage(ChatColor.RED + "X and Z must be numbers.");
                    return true;
                }
                border.setCenter(x, z);
                player.sendMessage(ChatColor.AQUA + "[Master] " + ChatColor.GREEN + "World border center set to "
                    + String.format(Locale.US, "%.2f, %.2f", x, z));
                return true;
            }
            case "centerhere" -> {
                border.setCenter(player.getLocation().getX(), player.getLocation().getZ());
                player.sendMessage(ChatColor.AQUA + "[Master] " + ChatColor.GREEN + "World border centered on your location.");
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Usage: /border <info|set|add|addseconds|removeseconds|center|centerhere> ...");
                return true;
            }
        }
    }

    private List<String> completeQueue(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission("hgloot.queue.admin");
        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>(QUEUE_BASE_SUBCOMMANDS);
            if (admin) {
                suggestions.addAll(QUEUE_ADMIN_SUBCOMMANDS);
            }
            return completeFrom(args, 1, suggestions);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!admin && !QUEUE_BASE_SUBCOMMANDS.contains(sub)) {
            return List.of();
        }
        return switch (sub) {
            case "join", "status" -> completeFrom(args, 2, queueTypeKeys());
            case "forcestart", "forcewin", "forceend" -> admin ? completeFrom(args, 2, queueTypeKeys()) : List.of();
            case "clear" -> {
                if (!admin) {
                    yield List.of();
                }
                List<String> values = new ArrayList<>(queueTypeKeys());
                values.add("all");
                yield completeFrom(args, 2, values);
            }
            case "forcejoin" -> {
                if (!admin) {
                    yield List.of();
                }
                if (args.length == 2) {
                    yield completeFrom(args, 2, onlinePlayerNames());
                }
                yield completeFrom(args, 3, queueTypeKeys());
            }
            case "autowin" -> admin ? completeFrom(args, 2, List.of("on", "off", "toggle", "status")) : List.of();
            case "cleanup" -> admin ? completeFrom(args, 2, List.of("on", "off", "status", "purge")) : List.of();
            case "setarena", "removearena" -> admin ? completeFrom(args, 2, arenaIndexSuggestions(sub)) : List.of();
            case "structure" -> admin ? completeQueueStructure(args) : List.of();
            case "cave" -> admin ? completeFrom(args, 2, List.of("debug")) : List.of();
            case "preload" -> {
                List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).toList();
                List<String> base = new ArrayList<>();
                base.add("here");
                base.addAll(worlds);
                yield completeFrom(args, 2, base);
            }
            case "map" -> completeFrom(args, 2, List.of("outcove", "wildwest", "wild_west", "aztec", "aztec_jungle", "random"));
            default -> List.of();
        };
    }

    private List<String> completeAdmin(CommandSender sender, String[] args) {
        boolean fullAdmin = sender.hasPermission("hgloot.admin");
        boolean moderationAdmin = sender.hasPermission("hgloot.moderation.menu");
        boolean bossAdmin = sender.hasPermission("hgloot.boss");
        if (!fullAdmin && !moderationAdmin && !bossAdmin) {
            return List.of();
        }
        if (args.length <= 1) {
            if (!fullAdmin && bossAdmin) {
                return completeFrom(args, 1, List.of("bosses"));
            }
            if (!fullAdmin && moderationAdmin) {
                return completeFrom(args, 1, List.of("menu", "warnings", "reports"));
            }
            return completeFrom(args, 1, List.of("set", "gift", "battlepass", "br", "hg", "bossrush", "koth", "roulette", "minigame", "minigames", "spleef", "vr", "voidroulette", "bridge", "lock", "unlock", "locks", "bosses", "weapons", "waves", "structures", "cosmetics", "economy", "wallet", "warnings", "ah", "store", "hotbar", "sethub", "economylog", "debug", "maintenance", "transition", "menu"));
        }
        if (!fullAdmin && bossAdmin && args[0].equalsIgnoreCase("bosses")) {
            return completeAdminBosses(args);
        }
        if (!fullAdmin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("warnings") || sub.equals("moderation") || sub.equals("reports") || sub.equals("menu")) {
                return List.of();
            }
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "transition" -> completeFrom(args, 2, List.of("test"));
            case "set" -> completeAdminSetShortcut(sender, trimLeadingArg(args));
            case "gift" -> args.length == 2 ? completeFrom(args, 2, onlinePlayerNamesWithAllTarget()) : List.of();
            case "battlepass" -> battlePassService.completeAdmin(trimLeadingArg(args));
            case "br" -> completeAdminQueueAlias(sender, args, QueueService.QueueType.SOLO);
            case "hg" -> completeAdminQueueAlias(sender, args, QueueService.QueueType.HUNGERGAMES);
            case "koth" -> completeFrom(args, 2, List.of("test"));
            case "bossrush" -> args.length == 3 && args[1].equalsIgnoreCase("forcejoin")
                ? MinigameForceJoin.complete(args[2])
                : completeFrom(args, 2, List.of("join", "forcejoin", "leave", "forcestart", "transition", "status", "stop"));
            case "bosses" -> completeAdminBosses(args);
            case "roulette" -> minigameRouletteService == null
                ? List.of()
                : minigameRouletteService.completeAdmin(trimLeadingArg(args));
            case "lock", "unlock" -> completeFrom(args, 2, List.of("br", "hg", "spleef", "voidroulette", "bridge"));
            case "minigame", "minigames" -> {
                String[] minigameArgs = trimLeadingArg(args);
                if (minigameArgs.length <= 1) {
                    yield completeFrom(args, 2,
                        List.of("smenu", "podium", "ignorepr", "skip", "spleef", "voidroulette", "bridge"));
                }
                String mg = minigameArgs[0].toLowerCase(Locale.ROOT);
                if (mg.equals("voidroulette") || mg.equals("maceroulette")) {
                    yield voidRouletteService == null ? List.of() : voidRouletteService.completeAdmin(minigameArgs);
                }
                if (mg.equals("bridge")) {
                    yield bridgeService == null ? List.of() : bridgeService.completeAdmin(minigameArgs);
                }
                yield spleefService == null ? List.of() : spleefService.completeAdmin(minigameArgs);
            }
            case "spleef" -> spleefService == null ? List.of() : spleefService.completeAdmin(args);
            case "vr", "voidroulette", "maceroulette" -> voidRouletteService == null ? List.of() : voidRouletteService.completeAdmin(args);
            case "bridge" -> bridgeService == null ? List.of() : bridgeService.completeAdmin(args);
            case "cosmetics" -> completeCosmetics(sender, trimLeadingArg(args));
            case "wallet" -> completeAdminWallet(sender, trimLeadingArg(args));
            case "warnings", "moderation", "reports" -> List.of();
            case "ah" -> completeAdminAuctionHouse(args);
            case "store" -> completeAdminStore(args);
            case "hotbar" -> completeAdminHotbar(args);
            case "economylog" -> completeFrom(args, 1, List.of("recent"));
            case "maintenance" -> completeMaintenance(trimLeadingArg(args));
            default -> List.of();
        };
    }

    private List<String> completeAdminBosses(String[] args) {
        if (args.length <= 2) {
            return completeFrom(args, 2, List.of("menu", "list", "summon", "reset", "phase", "debug"));
        }
        if ((args[1].equalsIgnoreCase("summon") || args[1].equalsIgnoreCase("spawn")) && args.length == 3) {
            return completeFrom(args, 3, List.of("combat_master"));
        }
        if (args[1].equalsIgnoreCase("phase")) {
            if (args.length == 3) {
                return completeFrom(args, 3, List.of("combat_master"));
            }
            if (args.length == 4 && isCombatMasterBossId(args[2])) {
                return completeFrom(args, 4, COMBAT_MASTER_PHASES);
            }
        }
        return List.of();
    }

    private List<String> completeAdminSetShortcut(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.wallet.admin")) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("money", "kills", "wins", "shards", "level"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("wins") || args[0].equalsIgnoreCase("win"))) {
            List<String> values = new ArrayList<>(List.of("hg", "br", "all"));
            values.addAll(COMMON_AMOUNT_SUGGESTIONS);
            values.addAll(onlinePlayerNames());
            return completeFrom(args, 2, values);
        }
        if (args.length == 2) {
            List<String> values = new ArrayList<>(COMMON_AMOUNT_SUGGESTIONS);
            values.addAll(onlinePlayerNames());
            return completeFrom(args, 2, values);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("wins") || args[0].equalsIgnoreCase("win"))
            && List.of("hg", "br", "all", "total").contains(args[1].toLowerCase(Locale.ROOT))) {
            List<String> values = new ArrayList<>(COMMON_AMOUNT_SUGGESTIONS);
            values.addAll(onlinePlayerNames());
            return completeFrom(args, 3, values);
        }
        if (args.length == 3) {
            return completeFrom(args, 3, COMMON_AMOUNT_SUGGESTIONS);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("wins") || args[0].equalsIgnoreCase("win"))) {
            return completeFrom(args, 4, COMMON_AMOUNT_SUGGESTIONS);
        }
        return List.of();
    }

    private List<String> completeWinsAdminShortcut(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.wallet.admin")) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("reset"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> values = new ArrayList<>(List.of("all", "hg", "br"));
            values.addAll(onlinePlayerNames());
            return completeFrom(args, 2, values);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return completeFrom(args, 3, onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> completeAdminHotbar(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("toggle"));
        }
        if (args[0].equalsIgnoreCase("toggle") && args.length == 2) {
            return completeFrom(args, 2, onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> completeWarn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.warn")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> completeWarnings(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.warn")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, onlinePlayerNames());
        }
        if (args.length == 2) {
            return completeFrom(args, 2, List.of("clear"));
        }
        return List.of();
    }

    private List<String> completeMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.mute")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, onlinePlayerNames());
        }
        if (args.length == 2) {
            return completeFrom(args, 2, List.of("10m", "30m", "1h", "6h", "1d", "perm"));
        }
        return List.of();
    }

    private List<String> completeUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.mute")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> completeTempBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.tempban")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, onlinePlayerNames());
        }
        if (args.length == 2) {
            return completeFrom(args, 2, List.of("10m", "30m", "1h", "6h", "1d", "7d"));
        }
        return List.of();
    }

    private List<String> completeUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.tempban")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, Bukkit.getBannedPlayers().stream()
                .map(this::safeOfflineName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
        }
        return List.of();
    }

    private List<String> completeKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.kick")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> completeReport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.report")) {
            return List.of();
        }
        if (args.length == 1) {
            UUID excluded = sender instanceof Player player ? player.getUniqueId() : null;
            return completeFrom(args, 1, onlinePlayerNamesExcluding(excluded));
        }
        return List.of();
    }

    private List<String> completeReports(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.moderation.reports")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list"));
            if (sender.hasPermission("hgloot.moderation.reports.clear")) {
                options.add("clear");
            }
            return completeFrom(args, 1, options);
        }
        return List.of();
    }

    private List<String> completeStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.stats.reset")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeFrom(args, 1, List.of("reset"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> values = new ArrayList<>(List.of("all"));
            values.addAll(onlinePlayerNames());
            return completeFrom(args, 2, values);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("all")) {
            return List.of();
        }
        return List.of();
    }

    private List<String> completeAdminAuctionHouse(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("simulate", "clearfake", "refreshmarket"));
        }
        if (args[0].equalsIgnoreCase("simulate")) {
            if (args.length == 2) {
                return completeFrom(args, 2, List.of("cosmetics", "weapon_skins", "effects", "titles", "trails", "shards", "rare", "limited", "mixed"));
            }
            if (args.length == 3) {
                return completeFrom(args, 3, List.of("10", "15", "30", "50"));
            }
        }
        return List.of();
    }

    private List<String> completeAdminStore(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("pending", "fulfill", "fulfillall"));
        }
        return List.of();
    }

    private List<String> completeAdminWallet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.wallet.admin")) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("add", "set", "reset", "menu"));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("menu")) {
            return List.of();
        }
        if (!sub.equals("add") && !sub.equals("set") && !sub.equals("reset")) {
            return List.of();
        }
        if (args.length == 2) {
            return completeFrom(args, 2, onlinePlayerNames());
        }
        if (args.length == 3) {
            return completeFrom(args, 3, List.of("levelxp", "level", "money", "shards", "fragments", "kills", "wins"));
        }
        if (args.length == 4) {
            return completeFrom(args, 4, COMMON_AMOUNT_SUGGESTIONS);
        }
        return List.of();
    }

    private List<String> completeShard(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("open", "claim"));
        }
        return List.of();
    }

    private List<String> completeSpectate(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return completeFrom(args, 1, sender instanceof Player player
                ? onlinePlayerNamesExcluding(player.getUniqueId())
                : onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> completeBattleRoyale(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>(List.of("join", "leave", "status", "help", "info"));
            if (sender.hasPermission("hgloot.br.rejoin")) {
                suggestions.add("rejoin");
            }
            if (sender.hasPermission("hgloot.queue.admin")) {
                suggestions.add("forcestart");
                suggestions.add("win");
                suggestions.add("forceend");
                suggestions.add("cleanup");
            }
            if (sender.hasPermission("hgloot.br.revive")) {
                suggestions.add("revive");
            }
            return completeFrom(args, 1, suggestions);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean queueAdmin = sender.hasPermission("hgloot.queue.admin");
        return switch (sub) {
            case "join", "status" -> completeFrom(args, 2, List.of("solo", "duo"));
            case "forcestart", "win", "forceend" -> queueAdmin ? completeFrom(args, 2, List.of("solo", "duo")) : List.of();
            case "cleanup" -> queueAdmin ? completeFrom(args, 2, List.of("on", "off", "status", "purge")) : List.of();
            case "revive" -> sender.hasPermission("hgloot.br.revive") ? completeFrom(args, 2, onlinePlayerNames()) : List.of();
            default -> List.of();
        };
    }

    private List<String> completeHungerGames(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>(List.of("join", "leave", "status", "help", "info"));
            if (sender.hasPermission("hgloot.br.rejoin")) {
                suggestions.add("rejoin");
            }
            if (sender.hasPermission("hgloot.queue.admin")) {
                suggestions.add("forcestart");
                suggestions.add("addplayer");
                suggestions.add("win");
                suggestions.add("forceend");
                suggestions.add("cleanup");
            }
            if (sender.hasPermission("hgloot.br.revive")) {
                suggestions.add("revive");
            }
            return completeFrom(args, 1, suggestions);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean queueAdmin = sender.hasPermission("hgloot.queue.admin");
        return switch (sub) {
            case "join" -> completeFrom(args, 2, List.of("solos", "duos"));
            case "forcestart" -> queueAdmin ? completeHungerGamesForceStart(args) : List.of();
            case "win" -> queueAdmin ? completeFrom(args, 2, List.of("hungergames")) : List.of();
            case "forceend" -> queueAdmin ? completeFrom(args, 2, List.of()) : List.of();
            case "cleanup" -> queueAdmin ? completeFrom(args, 2, List.of("on", "off", "status", "purge")) : List.of();
            case "revive" -> sender.hasPermission("hgloot.br.revive") ? completeFrom(args, 2, onlinePlayerNames()) : List.of();
            case "addplayer" -> queueAdmin && args.length == 2 ? completeFrom(args, 2, onlinePlayerNames()) : List.of();
            default -> List.of();
        };
    }

    private List<String> completeHungerGamesForceStart(String[] args) {
        if (args.length == 2) return completeFrom(args, 2, List.of("solos", "duos"));
        Boolean duos = args.length >= 2 ? hungerGamesDuosArgument(args[1]) : null;
        if (args.length == 3 && duos != null) {
            return completeFrom(args, 3, queueService.hungerGamesLocalLobbyIds(duos));
        }
        return List.of();
    }

    private List<String> completeAdminQueueAlias(
        CommandSender sender,
        String[] args,
        QueueService.QueueType defaultType
    ) {
        String[] trimmed = trimLeadingArg(args);
        List<String> base = new ArrayList<>(List.of("forcestart", "forcewin", "forceend", "clear", "forcejoin", "autowin", "cleanup", "setlobby", "setarena", "removearena", "arenas", "structure", "cave", "preload", "map"));
        if (defaultType == QueueService.QueueType.HUNGERGAMES) {
            base.add("addplayer");
            base.add("status");
            base.add("swin");
            base.add("refill");
            base.add("border");
        } else {
            base.add("switch");
            if (sender.hasPermission("hgloot.debug")) {
                base.add("valous");
                base.add("test");
            }
        }
        if (trimmed.length == 0) {
            return base;
        }
        if (trimmed.length == 1) {
            return completeFrom(trimmed, 1, base);
        }
        if (defaultType == QueueService.QueueType.HUNGERGAMES
            && trimmed[0].equalsIgnoreCase("forcestart")) {
            return completeHungerGamesForceStart(trimmed);
        }
        if (defaultType == QueueService.QueueType.HUNGERGAMES && trimmed[0].equalsIgnoreCase("border")) {
            if (trimmed.length == 2) return completeFrom(trimmed, 2, List.of("info", "set", "add", "remove"));
            if (trimmed.length == 3 && trimmed[1].equalsIgnoreCase("info")) {
                return completeFrom(trimmed, 3, queueService.activeHungerGamesWorldNames());
            }
            if (trimmed.length == 4) return completeFrom(trimmed, 4, queueService.activeHungerGamesWorldNames());
            return List.of();
        }
        if (defaultType == QueueService.QueueType.HUNGERGAMES && trimmed[0].equalsIgnoreCase("clear")) {
            return completeFrom(trimmed, 2, List.of("solos", "duos"));
        }
        if (defaultType == QueueService.QueueType.HUNGERGAMES && trimmed[0].equalsIgnoreCase("forceend")) {
            return completeFrom(trimmed, 2, queueService.activeHungerGamesWorldNames());
        }
        if (defaultType == QueueService.QueueType.HUNGERGAMES
            && trimmed[0].equalsIgnoreCase("addplayer") && trimmed.length == 2) {
            return completeFrom(trimmed, 2, onlinePlayerNames());
        }
        if ((trimmed[0].equalsIgnoreCase("forcestart") || trimmed[0].equalsIgnoreCase("forcewin") || trimmed[0].equalsIgnoreCase("forceend") || trimmed[0].equalsIgnoreCase("clear")) && trimmed.length == 2) {
            return List.of(defaultType.key());
        }
        if (trimmed[0].equalsIgnoreCase("cleanup") && trimmed.length == 2) {
            return List.of("on", "off", "status", "purge");
        }
        if (trimmed[0].equalsIgnoreCase("map") && trimmed.length == 2) {
            return List.of("outcove", "wildwest", "wild_west", "aztec", "aztec_jungle", "random");
        }
        if (trimmed[0].equalsIgnoreCase("refill") && defaultType == QueueService.QueueType.HUNGERGAMES) {
            if (trimmed.length == 2) return List.of("early");
            if (trimmed.length == 3 && trimmed[1].equalsIgnoreCase("early")) return List.of("start");
        }
        if (trimmed[0].equalsIgnoreCase("switch") && defaultType == QueueService.QueueType.SOLO && trimmed.length == 2) {
            return queueService.availableBattleRoyaleTerrainProfiles(sender);
        }
        if (trimmed[0].equalsIgnoreCase("valous") && defaultType == QueueService.QueueType.SOLO) {
            if (!sender.hasPermission("hgloot.debug")) {
                return List.of();
            }
            if (trimmed.length == 2) {
                return List.of("status", "prepare", "stop", "size", "export");
            }
            if (trimmed.length == 3 && trimmed[1].equalsIgnoreCase("size")) {
                return List.of("quick", "medium", "full");
            }
            if (trimmed.length == 3 && trimmed[1].equalsIgnoreCase("export")) {
                return List.of("current", "ready", "review", "route", "status", "cancel", "open");
            }
            if (trimmed.length == 4 && trimmed[1].equalsIgnoreCase("export")) {
                return List.of("review", "full", "route");
            }
        }
        if (trimmed[0].equalsIgnoreCase("test") && defaultType == QueueService.QueueType.SOLO) {
            if (!sender.hasPermission("hgloot.debug")) {
                return List.of();
            }
            if (trimmed.length == 2) {
                return List.of("create", "addbot", "removebot", "start", "status", "join", "spectate", "stop");
            }
            if (trimmed.length == 3 && (trimmed[1].equalsIgnoreCase("create")
                || trimmed[1].equalsIgnoreCase("addbot") || trimmed[1].equalsIgnoreCase("removebot"))) {
                return List.of("1", "2", "4", "8", "16");
            }
            if (trimmed.length == 3 && (trimmed[1].equalsIgnoreCase("join")
                || trimmed[1].equalsIgnoreCase("spectate") || trimmed[1].equalsIgnoreCase("stop"))) {
                return queueService.brDevelopmentTestWorldNames();
            }
        }
        if (trimmed[0].equalsIgnoreCase("forcejoin")) {
            if (trimmed.length == 2) {
                return onlinePlayerNames();
            }
            if (trimmed.length == 3) {
                return List.of(defaultType.key());
            }
        }
        return completeQueue(Bukkit.getConsoleSender(), trimmed);
    }

    private List<String> completeQueueStructure(String[] args) {
        if (args.length <= 2) {
            return completeFrom(args, 2, List.of("help", "list", "random", "bundle", "spawn", "grid", "preview", "locate", "spawns", "where", "rejects", "validate", "clear"));
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "bundle", "spawn" -> completeFrom(args, 3, queueService.availableStructureIds());
            case "grid" -> completeFrom(args, 3, List.of("4", "9", "16", "25", "36"));
            case "locate" -> {
                if (args.length == 3) {
                    List<String> options = new ArrayList<>(queueService.availableStructureIds());
                    options.addAll(RADIUS_SUGGESTIONS);
                    yield completeFrom(args, 3, options);
                }
                if (args.length == 4) {
                    yield completeFrom(args, 4, RADIUS_SUGGESTIONS);
                }
                yield List.of();
            }
            case "spawns", "where", "validate", "clear" -> completeFrom(args, 3, RADIUS_SUGGESTIONS);
            default -> List.of();
        };
    }

    private List<String> completeFriend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, FRIEND_SUBCOMMANDS);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> completeFrom(args, 2, onlinePlayerNamesExcluding(player.getUniqueId()));
            case "accept", "deny" -> completeFrom(args, 2, displayNames(friendService.getIncomingRequests(player.getUniqueId()), friendService::displayName));
            case "remove", "block" -> completeFrom(args, 2, displayNames(friendService.getFriends(player.getUniqueId()), friendService::displayName));
            case "unblock" -> completeFrom(args, 2, displayNames(friendService.getBlockedPlayers(player.getUniqueId()), friendService::displayName));
            default -> List.of();
        };
    }

    private List<String> completeParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, PARTY_SUBCOMMANDS);
        }
        UUID playerId = player.getUniqueId();
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite", "join" -> completeFrom(args, 2, onlinePlayerNamesExcluding(playerId));
            case "accept", "deny" -> completeFrom(args, 2, displayNames(partyService.getIncomingInvites(playerId), partyService::displayName));
            case "kick", "promote" -> completeFrom(args, 2, partyMemberNames(playerId));
            default -> List.of();
        };
    }

    private List<String> completeLootCommand(String[] args) {
        if (args.length == 1) {
            return completeLootCategory(args, 1);
        }
        if (args.length == 2) {
            LootCategory category = LootCategory.fromKey(args[0]).orElse(null);
            if (category == null) {
                return List.of();
            }
            return completeFrom(args, 2, numericRange(category.chestCount()));
        }
        int slot = args.length;
        if (slot == 3) {
            return completeFrom(args, 3, List.of("5", "8", "16", "25", "50", "100"));
        }
        return List.of();
    }

    private List<String> completeCombatMaster(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("spawn", "force", "reset", "phase", "debug"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("phase")) {
            return completeFrom(args, 2, COMBAT_MASTER_PHASES);
        }
        return List.of();
    }

    private List<String> completeCombatChamber(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("testvenom", "finish", "yes", "no", "stay", "leave", "venom"));
        }
        if (args[0].equalsIgnoreCase("venom")) {
            if (args.length == 2) {
                return completeFrom(args, 2, List.of("wave"));
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("wave")) {
                return completeFrom(args, 3, List.of("set"));
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("wave") && args[2].equalsIgnoreCase("set")) {
                return completeFrom(args, 4, List.of("1", "2", "3", "4"));
            }
        }
        return List.of();
    }

    private List<String> completeKoth(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, KOTH_SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "start" -> completeFrom(args, 2, List.of("10", "15", "20", "30", "45", "60"));
            case "countdown", "capture" -> completeFrom(args, 2, List.of("5", "10", "15", "20", "30", "45", "60"));
            case "earlystart" -> {
                if (args.length == 2) {
                    yield completeFrom(args, 2, List.of("10s", "30s", "50s", "1m", "now"));
                }
                yield completeFrom(args, 3, queueService == null ? List.of() : queueService.managedMatchWorldCommandTargets());
            }
            case "addtime", "removetime" -> {
                if (args.length == 2) {
                    yield completeFrom(args, 2, List.of("10s", "30s", "50s", "1m", "2m"));
                }
                yield completeFrom(args, 3, queueService == null ? List.of() : queueService.managedMatchWorldCommandTargets());
            }
            case "instantstart", "stop", "status" -> completeFrom(
                args,
                2,
                queueService == null ? List.of() : queueService.managedMatchWorldCommandTargets()
            );
            default -> List.of();
        };
    }

    private List<String> completeCasino(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>(CASINO_SUBCOMMANDS);
            if (sender.hasPermission("hgloot.casino.admin")) {
                suggestions.addAll(CASINO_ADMIN_SUBCOMMANDS);
            }
            return completeFrom(args, 1, suggestions);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "balance" -> sender.hasPermission("hgloot.casino.admin")
                ? completeFrom(args, 2, onlinePlayerNames())
                : List.of();
            case "addcoins", "addgold", "addsilver" -> {
                if (args.length == 2) {
                    yield completeFrom(args, 2, onlinePlayerNames());
                }
                yield completeFrom(args, 3, COMMON_AMOUNT_SUGGESTIONS);
            }
            case "sell" -> completeFrom(args, 2, COMMON_AMOUNT_SUGGESTIONS);
            default -> List.of();
        };
    }

    private List<String> completeSellShortcut(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, List.of("cosmetics"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cosmetics")) {
            return completeFrom(args, 2, COMMON_AMOUNT_SUGGESTIONS);
        }
        return List.of();
    }

    private List<String> completeCosmetics(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.cosmetics.admin")) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, COSMETICS_ADMIN_SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reset" -> args.length == 2 ? completeFrom(args, 2, List.of("all")) : List.of();
            case "unlock", "lock", "list" -> {
                if (args.length == 2) {
                    yield completeFrom(args, 2, onlinePlayerNames());
                }
                if (args.length == 3 && !sub.equals("list")) {
                    List<String> suggestions = new ArrayList<>(menuService.getAllHeadCosmeticIds());
                    suggestions.add("all");
                    yield completeFrom(args, 3, suggestions);
                }
                yield List.of();
            }
            default -> List.of();
        };
    }

    private List<String> completeLeaderboard(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, LEADERBOARD_SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("wins") && args.length == 2) {
            return completeFrom(args, 2, LEADERBOARD_SCOPE_SUGGESTIONS);
        }
        if (!sender.hasPermission("hgloot.leaderboard")) {
            return List.of();
        }
        if (sub.equals("clear") && args.length == 2) {
            return completeFrom(args, 2, LEADERBOARD_STAT_SUGGESTIONS);
        }
        if (sub.equals("clear") && args.length == 3 && args[1].equalsIgnoreCase("wins")) {
            return completeFrom(args, 3, LEADERBOARD_SCOPE_SUGGESTIONS);
        }
        if ((sub.equals("switch") || sub.equals("set")) && args.length == 2) {
            return completeFrom(args, 2, LEADERBOARD_STAT_SUGGESTIONS);
        }
        if ((sub.equals("switch") || sub.equals("set")) && args.length == 3 && args[1].equalsIgnoreCase("wins")) {
            return completeFrom(args, 3, LEADERBOARD_SCOPE_SUGGESTIONS);
        }
        if (!sub.equals("place")) {
            return List.of();
        }
        if (args.length == 2) {
            return completeFrom(args, 2, LEADERBOARD_STAT_SUGGESTIONS);
        }
        if (args.length == 3) {
            List<String> suggestions = new ArrayList<>(LEADERBOARD_SCOPE_SUGGESTIONS);
            suggestions.addAll(leaderboardCoordinateSuggestions(sender, 0));
            return completeFrom(args, 3, suggestions);
        }
        if (args.length == 4) {
            return completeFrom(args, 4, leaderboardCoordinateSuggestions(sender, isLeaderboardScope(args[2]) ? 0 : 1));
        }
        if (args.length == 5) {
            return completeFrom(args, 5, leaderboardCoordinateSuggestions(sender, isLeaderboardScope(args[2]) ? 1 : 2));
        }
        if (args.length == 6 && isLeaderboardScope(args[2])) {
            return completeFrom(args, 6, leaderboardCoordinateSuggestions(sender, 2));
        }
        return List.of();
    }

    private List<String> completeRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.rank")) {
            return List.of();
        }
        if (args.length <= 1) {
            return completeFrom(args, 1, RANK_SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "give", "set" -> {
                if (args.length == 2) {
                    yield completeFrom(args, 2, onlinePlayerNames());
                }
                if (args.length == 3) {
                    yield completeFrom(args, 3, RANK_MODEL_SUGGESTIONS);
                }
                if (args.length == 4) {
                    String rankFamily = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                    yield rankFamily.equals("hydra") || rankFamily.equals("hydra+") || rankFamily.equals("hydraplus")
                        ? completeFrom(args, 4, RANK_TIER_SUGGESTIONS)
                        : List.of();
                }
                yield List.of();
            }
            case "remove" -> args.length == 2 ? completeFrom(args, 2, onlinePlayerNames()) : List.of();
            default -> List.of();
        };
    }

    private List<String> completePlayerAndAmount(String[] args, UUID excludedPlayerId) {
        if (args.length == 1) {
            return completeFrom(args, 1, excludedPlayerId == null
                ? onlinePlayerNames()
                : onlinePlayerNamesExcluding(excludedPlayerId));
        }
        if (args.length == 2) {
            return completeFrom(args, 2, COMMON_AMOUNT_SUGGESTIONS);
        }
        return List.of();
    }

    private List<String> completeBorder(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, BORDER_SUBCOMMANDS);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> args.length == 2
                ? completeFrom(args, 2, List.of("100", "250", "500", "1000", "2000"))
                : completeFrom(args, 3, List.of("5", "10", "30", "60", "120"));
            case "add" -> args.length == 2
                ? completeFrom(args, 2, List.of("50", "100", "250", "-50", "-100", "-250"))
                : completeFrom(args, 3, List.of("5", "10", "30", "60", "120"));
            case "addseconds", "removeseconds" -> completeFrom(args, 2, List.of("5", "10", "30", "60", "120"));
            case "center" -> completeBorderCenter(sender, args);
            default -> List.of();
        };
    }

    private List<String> completeBorderCenter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String x = Integer.toString(player.getLocation().getBlockX());
        String z = Integer.toString(player.getLocation().getBlockZ());
        if (args.length == 2) {
            return completeFrom(args, 2, List.of(x));
        }
        if (args.length == 3) {
            return completeFrom(args, 3, List.of(z));
        }
        return List.of();
    }

    private List<String> completeWorld(String[] args) {
        return completeFrom(args, 1, availableWorldNames());
    }

    private void registerCommand(String commandName) {
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            getLogger().warning("Command '" + commandName + "' is not defined in plugin.yml.");
        }
    }

    private void registerCommands(String... commandNames) {
        for (String commandName : commandNames) {
            registerCommand(commandName);
        }
    }

    private boolean isMaintenanceEnabled() {
        return getConfig().getBoolean(MAINTENANCE_CONFIG_ROOT + ".enabled", false);
    }

    private void setMaintenanceEnabled(boolean enabled) {
        getConfig().set(MAINTENANCE_CONFIG_ROOT + ".enabled", enabled);
        saveConfig();
    }

    private String maintenanceBypassPermission() {
        String configured = getConfig().getString(MAINTENANCE_CONFIG_ROOT + ".bypass_permission");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAINTENANCE_BYPASS_PERMISSION;
        }
        return configured.trim();
    }

    private String maintenanceKickMessage() {
        List<String> lines = getConfig().getStringList(MAINTENANCE_CONFIG_ROOT + ".kick_message");
        if (lines.isEmpty()) {
            lines = List.of(
                "§8┃ §bHYDRA §8┃",
                "",
                "§7Maintenance mode active.",
                "§7Please check back soon."
            );
        }
        List<String> formatted = new ArrayList<>();
        for (String line : lines) {
            formatted.add(ChatColor.translateAlternateColorCodes('&', line == null ? "" : line));
        }
        return String.join("\n", formatted);
    }

    private int maintenanceAllowedPlayerCount() {
        var section = getConfig().getConfigurationSection(MAINTENANCE_CONFIG_ROOT + ".allowed_players");
        return section == null ? 0 : section.getKeys(false).size();
    }

    private boolean isMaintenanceAllowedPlayer(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return getConfig().contains(MAINTENANCE_CONFIG_ROOT + ".allowed_players." + playerId);
    }

    private boolean addMaintenanceAllowedPlayer(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return false;
        }
        String path = MAINTENANCE_CONFIG_ROOT + ".allowed_players." + player.getUniqueId();
        if (getConfig().contains(path)) {
            return false;
        }
        getConfig().set(path, offlinePlayerName(player));
        saveConfig();
        return true;
    }

    private boolean removeMaintenanceAllowedPlayer(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return false;
        }
        String path = MAINTENANCE_CONFIG_ROOT + ".allowed_players." + player.getUniqueId();
        if (!getConfig().contains(path)) {
            return false;
        }
        getConfig().set(path, null);
        saveConfig();
        return true;
    }

    private List<String> completeMaintenance(String[] args) {
        if (args.length <= 1) {
            return completeFrom(args, 1, MAINTENANCE_SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "add", "remove" -> completeFrom(args, 2, knownPlayerNames());
            default -> List.of();
        };
    }

    private List<String> completeLootCategory(String[] args, int index) {
        List<String> categories = new ArrayList<>();
        for (LootCategory category : LootCategory.values()) {
            categories.add(category.key());
        }
        return completeFrom(args, index, categories);
    }

    private List<String> completeFrom(String[] args, int index, List<String> candidates) {
        if (args.length != index || candidates.isEmpty()) {
            return List.of();
        }
        String token = args[index - 1].toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.toLowerCase(Locale.ROOT).startsWith(token)) {
                matches.add(candidate);
            }
        }
        return new ArrayList<>(matches);
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> onlinePlayerNamesWithAllTarget() {
        List<String> names = new ArrayList<>();
        names.add("@a");
        names.addAll(onlinePlayerNames());
        return names;
    }

    private List<String> onlinePlayerNamesExcluding(UUID excludedId) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(excludedId)) {
                continue;
            }
            names.add(online.getName());
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> availableWorldNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        File[] entries = getServer().getWorldContainer().listFiles(File::isDirectory);
        if (entries != null) {
            for (File entry : entries) {
                if (!new File(entry, "level.dat").isFile()) {
                    continue;
                }
                names.add(entry.getName());
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareToIgnoreCase);
        return sorted;
    }

    private World.Environment inferWorldEnvironment(String worldName, File worldFolder) {
        String normalized = worldName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("_nether") || normalized.endsWith("-nether")) {
            return World.Environment.NETHER;
        }
        if (normalized.endsWith("_the_end") || normalized.endsWith("-the_end")
            || normalized.endsWith("_end") || normalized.endsWith("-end")) {
            return World.Environment.THE_END;
        }
        if (new File(worldFolder, "DIM-1").isDirectory()) {
            return World.Environment.NORMAL;
        }
        if (new File(worldFolder, "DIM1").isDirectory()) {
            return World.Environment.NORMAL;
        }
        return World.Environment.NORMAL;
    }

    private World resolveWorldByName(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        World exact = Bukkit.getWorld(requested);
        if (exact != null) {
            return exact;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(requested)) {
                return world;
            }
        }
        return null;
    }

    private List<String> knownPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && !offline.getName().isBlank()) {
                names.add(offline.getName());
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareToIgnoreCase);
        return sorted;
    }

    private File resolveWorldFolder(String requested) {
        if (requested == null || requested.isBlank()) {
            return new File(getServer().getWorldContainer(), "");
        }
        File exact = new File(getServer().getWorldContainer(), requested);
        if (exact.isDirectory()) {
            return exact;
        }
        File[] entries = getServer().getWorldContainer().listFiles(File::isDirectory);
        if (entries != null) {
            for (File entry : entries) {
                if (entry.getName().equalsIgnoreCase(requested)) {
                    return entry;
                }
            }
        }
        return exact;
    }

    private List<String> displayNames(List<UUID> ids, Function<UUID, String> resolver) {
        List<String> names = new ArrayList<>();
        for (UUID id : ids) {
            String name = resolver.apply(id);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> partyMemberNames(UUID playerId) {
        List<String> names = new ArrayList<>();
        for (UUID memberId : partyService.getPartyMembers(playerId)) {
            if (memberId.equals(playerId)) {
                continue;
            }
            String name = partyService.displayName(memberId);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> queueTypeKeys() {
        List<String> keys = new ArrayList<>();
        for (QueueService.QueueType type : QueueService.QueueType.values()) {
            keys.add(type.key());
        }
        return keys;
    }

    private List<String> numericRange(int max) {
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= max; i++) {
            values.add(Integer.toString(i));
        }
        return values;
    }

    private List<String> arenaIndexSuggestions(String subcommand) {
        int currentCount = Math.max(1, queueService.arenaSpawnCount());
        int max = subcommand.equals("setarena") ? Math.min(12, currentCount + 1) : Math.min(12, currentCount);
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= max; i++) {
            values.add(Integer.toString(i));
        }
        return values;
    }

    private OfflinePlayer resolvePlayer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(raw);
        if (exact != null) {
            return exact;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(raw)) {
                return online;
            }
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(raw)) {
                return offline;
            }
        }
        return Bukkit.getOfflinePlayer(raw);
    }

    private OfflinePlayer resolveKnownPlayer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(raw);
        if (exact != null) {
            return exact;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(raw)) {
                return online;
            }
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(raw)) {
                return offline;
            }
        }
        return null;
    }

    private String offlinePlayerName(OfflinePlayer player) {
        if (player == null) {
            return "Unknown";
        }
        return player.getName() == null || player.getName().isBlank()
            ? player.getUniqueId().toString()
            : player.getName();
    }

    public String hydraRankModeFor(UUID targetId) {
        if (targetId == null) {
            return "none";
        }
        String assigned = hydraRankAssignments.get(targetId);
        if (assigned != null && !assigned.isBlank()) {
            return assigned;
        }
        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget == null) {
            return "none";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_HYDRA_PLUS_THREE)) {
            return "tier3";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_HYDRA_PLUS_TWO)) {
            return "tier2";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_HYDRA_PLUS)) {
            return "tier1";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_HYDRA)) {
            return "base";
        }
        return "none";
    }

    public boolean hasHydraPlusRank(UUID targetId) {
        return !"none".equalsIgnoreCase(hydraRankModeFor(targetId));
    }

    /** Strict Hydra+ tiers for features that must not include the base Hydra rank. */
    public boolean hasHydraPlusTierRank(UUID targetId) {
        return switch (hydraRankModeFor(targetId)) {
            case "tier1", "tier2", "tier3" -> true;
            default -> false;
        };
    }

    public boolean hasHydraPlusThreeRank(UUID targetId) {
        return "tier3".equalsIgnoreCase(hydraRankModeFor(targetId));
    }

    public String staffRankModeFor(UUID targetId) {
        if (targetId == null) {
            return "none";
        }
        String assigned = staffRankAssignments.get(targetId);
        if (assigned != null && !assigned.isBlank()) {
            return assigned;
        }
        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget == null) {
            return "none";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_OWNER)) {
            return "owner";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_DEV)) {
            return "dev";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_ADMIN)) {
            return "admin";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_MOD)) {
            return "mod";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_STAFF)) {
            return "staff";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_CREATOR)) {
            return "creator";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_MEDIA)) {
            return "media";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_HELPER)) {
            return "helper";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_TRIAL_STAFF)) {
            return "trialstaff";
        }
        if (onlineTarget.hasPermission(RANK_PERMISSION_STAFF_TRIAL_MOD)) {
            return "trialstaff";
        }
        return "none";
    }

    public boolean hasStaffRank(UUID targetId) {
        return !"none".equalsIgnoreCase(staffRankModeFor(targetId));
    }

    public boolean hasStaffServerManagementAccess(UUID targetId) {
        String mode = staffRankModeFor(targetId);
        return mode.equals("admin") || mode.equals("dev") || mode.equals("owner");
    }

    private String hydraRankMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "base", "tier0", "0", "hydra" -> "base";
            case "i", "1", "tier1", "hydra+", "hydraplus" -> "tier1";
            case "ii", "2", "tier2", "hydra++", "hydraplusplus" -> "tier2";
            case "iii", "3", "tier3", "all", "hydra+++", "hydraplusplusplus" -> "tier3";
            case "none", "remove" -> "none";
            default -> null;
        };
    }

    private List<String> allStaffRankPermissions() {
        return List.of(
            RANK_PERMISSION_STAFF_TRIAL_STAFF,
            RANK_PERMISSION_STAFF_TRIAL_MOD,
            RANK_PERMISSION_STAFF_STAFF,
            RANK_PERMISSION_STAFF_HELPER,
            RANK_PERMISSION_STAFF_MEDIA,
            RANK_PERMISSION_STAFF_CREATOR,
            RANK_PERMISSION_STAFF_MOD,
            RANK_PERMISSION_STAFF_ADMIN,
            RANK_PERMISSION_STAFF_DEV,
            RANK_PERMISSION_STAFF_OWNER
        );
    }

    private boolean isStaffRankMode(String raw) {
        String mode = staffRankMode(raw);
        return mode != null && !"none".equalsIgnoreCase(mode);
    }

    private String staffRankMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "trialstaff", "trial_staff", "trial-staff", "trialmod", "trial_mod", "trial-mod", "trial", "trialmoderator", "trial_moderator", "trial-moderator" -> "trialstaff";
            case "staff" -> "staff";
            case "helper" -> "helper";
            case "media" -> "media";
            case "creator" -> "creator";
            case "mod", "moderator" -> "mod";
            case "admin", "administrator" -> "admin";
            case "dev", "developer" -> "dev";
            case "owner" -> "owner";
            case "none", "remove" -> "none";
            default -> null;
        };
    }

    private void refreshHydraRankDisplay(UUID targetId) {
        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.recalculatePermissions();
        }
        if (hydraPlusService != null) {
            hydraPlusService.refreshNow();
        }
    }

    private void loadHydraRankAssignments() {
        hydraRankAssignments.clear();
        var section = getConfig().getConfigurationSection(HYDRA_RANK_CONFIG_ROOT);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String mode = hydraRankMode(section.getString(key));
                if (mode != null && !"none".equalsIgnoreCase(mode)) {
                    hydraRankAssignments.put(playerId, mode);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void loadStaffRankAssignments() {
        staffRankAssignments.clear();
        var section = getConfig().getConfigurationSection(STAFF_RANK_CONFIG_ROOT);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String mode = staffRankMode(section.getString(key));
                if (mode != null && !"none".equalsIgnoreCase(mode)) {
                    staffRankAssignments.put(playerId, mode);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void setHydraRankAssignment(UUID targetId, String mode) {
        if (targetId == null) {
            return;
        }
        String normalized = hydraRankMode(mode);
        String path = HYDRA_RANK_CONFIG_ROOT + "." + targetId;
        if (normalized == null || "none".equalsIgnoreCase(normalized)) {
            hydraRankAssignments.remove(targetId);
            getConfig().set(path, null);
        } else {
            hydraRankAssignments.put(targetId, normalized);
            getConfig().set(path, normalized);
        }
        saveConfig();
    }

    private void setStaffRankAssignment(UUID targetId, String mode) {
        if (targetId == null) {
            return;
        }
        String normalized = staffRankMode(mode);
        String path = STAFF_RANK_CONFIG_ROOT + "." + targetId;
        if (normalized == null || "none".equalsIgnoreCase(normalized)) {
            staffRankAssignments.remove(targetId);
            getConfig().set(path, null);
        } else {
            staffRankAssignments.put(targetId, normalized);
            getConfig().set(path, normalized);
        }
        saveConfig();
    }

    private boolean isLeaderboardScope(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("br") || normalized.equals("battleroyale") || normalized.equals("battle_royale")
            || normalized.equals("hg") || normalized.equals("hungergames") || normalized.equals("hunger_games")
            || normalized.equals("total") || normalized.equals("all") || normalized.equals("overall");
    }

    private Location parseLeaderboardLocation(Player player, String rawX, String rawY, String rawZ) {
        if (player == null) {
            return null;
        }
        Location origin = player.getLocation();
        Double x = parseCoordinate(rawX, origin.getX());
        Double y = parseCoordinate(rawY, origin.getY());
        Double z = parseCoordinate(rawZ, origin.getZ());
        if (x == null || y == null || z == null) {
            return null;
        }
        return new Location(player.getWorld(), x, y, z);
    }

    private Double parseCoordinate(String raw, double base) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.equals("~")) {
            return base;
        }
        if (normalized.startsWith("~")) {
            Double offset = tryParseDouble(normalized.substring(1));
            return offset == null ? null : base + offset;
        }
        return tryParseDouble(normalized);
    }

    private Double tryParseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> leaderboardCoordinateSuggestions(CommandSender sender, int axis) {
        if (!(sender instanceof Player player)) {
            return List.of("~");
        }
        Location location = player.getLocation();
        String absolute = switch (axis) {
            case 0 -> Integer.toString(location.getBlockX());
            case 1 -> Integer.toString(location.getBlockY());
            case 2 -> Integer.toString(location.getBlockZ());
            default -> "~";
        };
        return List.of("~", absolute);
    }

    private QueueService.DebugLeaderboardType resolveLeaderboardType(String statRaw, String scopeRaw) {
        String stat = statRaw == null ? "" : statRaw.trim().toLowerCase(Locale.ROOT);
        String scope = scopeRaw == null ? "all" : scopeRaw.trim().toLowerCase(Locale.ROOT);
        return switch (stat) {
            case "kills", "kill" -> QueueService.DebugLeaderboardType.KILLS;
            case "level", "levels", "xp" -> QueueService.DebugLeaderboardType.LEVEL;
            case "money", "coins", "coin", "gold", "silver", "balance" -> QueueService.DebugLeaderboardType.MONEY;
            case "wins", "win" -> switch (scope) {
                case "hg", "hungergames", "hunger_games" -> QueueService.DebugLeaderboardType.WINS_HG;
                case "br", "battleroyale", "battle_royale" -> QueueService.DebugLeaderboardType.WINS_BR;
                case "total", "all", "overall" -> QueueService.DebugLeaderboardType.WINS_TOTAL;
                default -> null;
            };
            default -> null;
        };
    }

    private Set<String> resolveLeaderboardClearTypes(String[] args) {
        if (args.length < 2) {
            return null;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 1; i < args.length; i++) {
            String token = args[i] == null ? "" : args[i].trim().toLowerCase(Locale.ROOT);
            if (token.isBlank() || token.equals("-") || token.equals(",") || token.equals("and")) {
                continue;
            }
            if (token.equals("all") || token.equals("*")) {
                return null;
            }
            if (token.equals("wins") || token.equals("win")) {
                String next = i + 1 < args.length ? args[i + 1] : "";
                if (isLeaderboardScope(next)) {
                    QueueService.DebugLeaderboardType scopedType = resolveLeaderboardType(token, next);
                    if (scopedType == null) {
                        return Set.of();
                    }
                    keys.add(scopedType.key());
                    i++;
                } else {
                    keys.add(QueueService.DebugLeaderboardType.WINS_TOTAL.key());
                    keys.add(QueueService.DebugLeaderboardType.WINS_BR.key());
                    keys.add(QueueService.DebugLeaderboardType.WINS_HG.key());
                }
                continue;
            }
            QueueService.DebugLeaderboardType type = resolveLeaderboardType(token, "total");
            if (type == null || type.isWinsBoard()) {
                return Set.of();
            }
            keys.add(type.key());
        }
        return keys;
    }

    private int parsePositive(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseDurationSeconds(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return fallback;
        }
        int multiplier = 1;
        if (normalized.endsWith("seconds")) {
            normalized = normalized.substring(0, normalized.length() - "seconds".length()).trim();
        } else if (normalized.endsWith("second")) {
            normalized = normalized.substring(0, normalized.length() - "second".length()).trim();
        } else if (normalized.endsWith("secs")) {
            normalized = normalized.substring(0, normalized.length() - "secs".length()).trim();
        } else if (normalized.endsWith("sec")) {
            normalized = normalized.substring(0, normalized.length() - "sec".length()).trim();
        } else if (normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        } else if (normalized.endsWith("minutes")) {
            normalized = normalized.substring(0, normalized.length() - "minutes".length()).trim();
            multiplier = 60;
        } else if (normalized.endsWith("minute")) {
            normalized = normalized.substring(0, normalized.length() - "minute".length()).trim();
            multiplier = 60;
        } else if (normalized.endsWith("mins")) {
            normalized = normalized.substring(0, normalized.length() - "mins".length()).trim();
            multiplier = 60;
        } else if (normalized.endsWith("min")) {
            normalized = normalized.substring(0, normalized.length() - "min".length()).trim();
            multiplier = 60;
        } else if (normalized.endsWith("m")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
            multiplier = 60;
        }
        int base = parsePositive(normalized, fallback);
        if (base < 0) {
            return fallback;
        }
        return base * multiplier;
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatClock(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60);
    }

    private UUID resolveByName(List<UUID> candidates, String name) {
        return resolveByName(candidates, name, friendService::displayName);
    }

    private UUID resolveByName(List<UUID> candidates, String name, Function<UUID, String> nameResolver) {
        for (UUID id : candidates) {
            String candidateName = nameResolver.apply(id);
            if (candidateName.equalsIgnoreCase(name)) {
                return id;
            }
        }
        return null;
    }

    public HydraRegistryHub registries() {
        return registryHub;
    }

}
