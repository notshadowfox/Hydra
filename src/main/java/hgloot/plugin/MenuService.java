package hgloot.plugin;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.ultraroyales.UltraRoyales;
import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookRegistry;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Egg;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import hgloot.plugin.util.HydraSounds;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.loot.LootCategory;
import hgloot.plugin.loot.LootTableService;
import hgloot.plugin.loot.ManagedLootService;
import hgloot.plugin.gui.GuiTitleFactory;
import hgloot.plugin.gui.MenuHolder;

@SuppressWarnings({"deprecation", "unused"})
public final class MenuService {
    private static final String MAIN_TITLE = "Hydra Admin";
    private static final String PLAYER_MENU_TITLE = "Navigator";
    private static final String LOOT_TITLE = "Hydra Loot System";
    private static final String AUDIT_TITLE = "Audit Books";
    private static final int NAVIGATOR_SLOT = 0;
    private static final int ECONOMY_SLOT = 1;
    private static final int SETTINGS_SLOT = 2;
    private static final int BATTLE_PASS_SLOT = 3;
    private static final int QUEUE_CONTROL_SLOT = 4;
    private static final int SHARD_CLAIM_SLOT = 6;
    private static final int COSMETICS_SLOT = 7;
    private static final int SOCIAL_SLOT = 8;
    private static final String HUB_ITEM_NAVIGATOR = "navigator";
    private static final String HUB_ITEM_ECONOMY = "economy";
    private static final String HUB_ITEM_RECIPES = "recipes"; // Legacy Hub item, removed on refresh.
    private static final String HUB_ITEM_UTILITY_BOOK = "utility_book";
    private static final String HUB_ITEM_SETTINGS = "settings";
    private static final String HUB_ITEM_BATTLE_PASS = "battle_pass";
    private static final String HUB_ITEM_LEAVE_GAMEHUB = "leave_gamehub";
    private static final String HUB_ITEM_LEAVE_ROULETTE = "leave_minigame_roulette";
    private static final String HUB_ITEM_LEAVE_HUNGER_GAMES = "leave_hunger_games";
    private static final String HUB_ITEM_CLAIM_SHARD = "claim_shard";
    private static final String HUB_ITEM_COSMETICS = "cosmetics";
    private static final String HUB_ITEM_SOCIAL = "social";
    private static final int HUB_ITEM_COSMETICS_CMD = 9201;
    private static final String COSMETIC_CONFIG_ROOT = "cosmetics.head";
    private static final String COSMETIC_KILL_MESSAGE_CONFIG_ROOT = "cosmetics.kill_message";
    private static final String COSMETIC_LEGENDARY_CONFIG_ROOT = "cosmetics.legendary";
    private static final String COSMETIC_RANK_STYLE_CONFIG_ROOT = "cosmetics.rank_style";
    private static final String COSMETIC_SYSTEM_CONFIG_ROOT = "cosmetics.system";
    // Keep the legacy path so existing hat favorites survive the all-category expansion.
    private static final String COSMETIC_FAVORITES_CONFIG_ROOT = "cosmetics.favorites.hats";
    private static final String COSMETIC_OWNED_CONFIG_ROOT = "cosmetics.owned.head";
    private static final String COSMETIC_REGISTRY_FILE = "cosmetics.yml";
    private static final String COSMETIC_REGISTRY_ROOT = "head_cosmetics";
    private static final String SPINNING_TOP_HAT_ID = "spinning_tophat";
    private static final int SPINNING_TOP_HAT_PERIOD_TICKS = 16;
    private static final int SPINNING_TOP_HAT_CUSTOM_MODEL_DATA = 1010;
    private static final String DEFAULT_RANK_STYLE_ID = "rank_style_classic";
    private static final String DEFAULT_KILL_MESSAGE_ID = "kill_message_default";
    private static final String COSMETIC_SELECT_ACTION_PREFIX = "player_cosmetic_head_select:";
    private static final String COSMETIC_TAB_ACTION_PREFIX = "player_cosmetics_tab:";
    private static final String COSMETIC_DOMAIN_ACTION_PREFIX = "player_cosmetics_domain:";
    private static final String COSMETIC_CATEGORY_ACTION_PREFIX = "player_cosmetics_category:";
    private static final String COSMETIC_PAGE_ACTION_PREFIX = "player_cosmetics_page:";
    private static final String COSMETIC_FAVORITES_PAGE_ACTION_PREFIX = "player_cosmetics_favorites_page:";
    private static final String ADMIN_COSMETICS_PLAYER_ACTION_PREFIX = "open_admin_cosmetics_player:";
    private static final String ADMIN_COSMETICS_CATEGORY_ACTION_PREFIX = "admin_cosmetics_category:";
    private static final String ADMIN_COSMETICS_TOGGLE_ACTION_PREFIX = "admin_toggle_cosmetic:";
    private static final String ADMIN_RANK_PLAYER_ACTION_PREFIX = "open_admin_rank_player:";
    private static final String ADMIN_RANK_SET_ACTION_PREFIX = "admin_rank_set:";
    private static final int PLAYER_COSMETICS_MENU_SIZE = 54;
    private static final int[] PLAYER_COSMETICS_CATEGORY_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] PLAYER_COSMETICS_CONTENT_SLOTS = {
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int PLAYER_COSMETICS_PREVIOUS_SLOT = 45;
    private static final int PLAYER_COSMETICS_NEXT_SLOT = 53;
    private static final int ADMIN_COSMETICS_MENU_SIZE = 54;
    private static final int[] ADMIN_COSMETICS_CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int ADMIN_COSMETICS_TARGET_SLOT = 4;
    private static final String RANK_PERMISSION_HYDRA = "rank.hydra";
    private static final String RANK_PERMISSION_HYDRA_PLUS = "rank.hydra_plus";
    private static final String RANK_PERMISSION_HYDRA_PLUS_TWO = "rank.hydra_plus.2";
    private static final String RANK_PERMISSION_HYDRA_PLUS_THREE = "rank.hydra_plus.3";
    private static final int LEGENDARY_SKIN_REFRESH_INTERVAL_TICKS = 20;
    private static final String HYDRA_WEAPON_GIVE_ACTION_PREFIX = "admin_hydra_weapon_give:";
    private static final String HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX = "admin_hg_hydra_weapon_give:";
    private static final String HG_UTILITY_GIVE_ACTION_PREFIX = "admin_hg_utility_give:";
    private static final String BETA_LEGENDARY_GIVE_ACTION_PREFIX = "admin_beta_legendary_give:";
    private static final ChatColor MAIN_TITLE_COLOR = ChatColor.AQUA;
    private static final ChatColor CATEGORY_TITLE_COLOR = ChatColor.AQUA;
    private static final ChatColor SECONDARY_TEXT_COLOR = ChatColor.GRAY;
    private static final ChatColor WARNING_COLOR = ChatColor.YELLOW;
    private static final ChatColor MATCH_COLOR = ChatColor.GREEN;
    private static final ChatColor LOOT_COLOR = ChatColor.GOLD;
    private static final ChatColor STRUCTURE_COLOR = ChatColor.AQUA;
    private static final ChatColor COMBAT_COLOR = ChatColor.RED;
    private static final ChatColor ECONOMY_COLOR = ChatColor.YELLOW;
    private static final ChatColor DEV_COLOR = ChatColor.LIGHT_PURPLE;
    private static final String JOIN_CHAT_TITLE_TEXT = ChatColor.AQUA + "" + ChatColor.BOLD + "HYDRA NETWORK";
    private static final String JOIN_CHAT_SUBTITLE_TEXT = ChatColor.GREEN + "Welcome to Open Beta.";
    private static final long JOIN_CHAT_DELAY_TICKS = 12L;
    private static final int CHAT_CENTER_PX = 154;
    private static final int CHAT_SPACE_PX = 4;
    private static final String GUI_MODEL_BOOK = "hydra:gui/book_orange";
    private static final String GUI_MODEL_BOOK_AQUA = "hydra:gui/book_aqua";
    private static final String GUI_MODEL_ARROW_DOWN = "hydra:gui/arrow_down";
    private static final String GUI_MODEL_CLOSE = "hydra:gui/cross_button";
    private static final String GUI_MODEL_CRAFTING = "hydra:gui/crafting_table";
    private static final String GUI_MODEL_DISPENSER = "hydra:gui/dispenser";
    private static final String GUI_MODEL_CLICK_TARGET = "hydra:gui/negative_space";
    private static final String GUI_MODEL_FRIENDS = "hydra:gui/friends";
    private static final String GUI_MODEL_HOPPER = "hydra:gui/hopper";
    private static final String GUI_MODEL_PAGE_NEXT = "hydra:gui/page_next";
    private static final String GUI_MODEL_PAGE_PREVIOUS = "hydra:gui/page_previous";
    private static final String GUI_MODEL_SCROLLER = "hydra:gui/scroller";
    private static final String GUI_MODEL_ARROW_LEFT = "hydra:gui/settings_arrow_left";
    private static final String GUI_MODEL_ARROW_RIGHT = "hydra:gui/settings_arrow_right";
    private static final String GUI_MODEL_SHULKER = "hydra:gui/shulker_box";
    private static final String GUI_MODEL_PARTY = "hydra:gui/party";
    private static final String GUI_MODEL_TAB_SEARCH = "hydra:gui/tab_item_search";
    private static final String GUI_MODEL_TAB_ITEMS = "hydra:gui/tab_items";
    private static final String GUI_MODEL_TOGGLE_OFF = "hydra:gui/toggle_switch_off";
    private static final String GUI_MODEL_TOGGLE_ON = "hydra:gui/toggle_switch_on";
    private static final String GUI_MODEL_NAVIGATOR_OVERLAY = "hydra:gui/navigator_overlay";
    private static final String GUI_MODEL_NAVIGATOR_EXIT = "hydra:gui/navigator_exit";
    private static final String GUI_MODEL_NAVIGATOR_INFO = "hydra:gui/navigator_info";
    private static final Material BACK_BUTTON_MATERIAL = Material.BARRIER;
    private static final int VOIDBREAKER_RUPTURE_COOLDOWN_MS = 11000;
    private static final double VOIDBREAKER_RUPTURE_RANGE = 13.5D;
    private static final double VOIDBREAKER_RUPTURE_STEP = 1.0D;
    private static final double VOIDBREAKER_RUPTURE_BASE_HALF_WIDTH = 0.75D;
    private static final double VOIDBREAKER_RUPTURE_MAX_HALF_WIDTH = 2.0D;
    private static final double VOIDBREAKER_RUPTURE_DAMAGE = 8.0D;
    private static final double VOIDBREAKER_MELEE_CHARGE = 0.22D;
    private static final double VOIDBREAKER_PLUNGE_CHARGE = 0.38D;
    private static final double VOIDBREAKER_PROTECTED_CENTER_RADIUS = 90.0D;
    private static final double LAST_BREATH_PENETRATION_POWER = 48.0D;
    private static final double LAST_BREATH_RECOIL = 0.24D;

    private final JavaPlugin plugin;
    private final LootTableService lootTableService;
    private final FriendService friendService;
    private final PartyService partyService;
    private ManagedLootService managedLootService;
    private final NamespacedKey actionKey;
    private final NamespacedKey lootTableKey;
    private final NamespacedKey shotModeKey;
    private final NamespacedKey sonicCrossbowKey;
    private final NamespacedKey railgunCrossbowKey;
    private final NamespacedKey railgunModeKey;
    private final NamespacedKey voidbreakerMaceKey;
    private final NamespacedKey sonicShotKey;
    private final NamespacedKey venomZombieKey;
    private final NamespacedKey playerMenuCompassKey;
    private final NamespacedKey playerHubItemKey;
    private final NamespacedKey settingsParticlesKey;
    private final NamespacedKey settingsEffectsLevelKey;
    private final NamespacedKey cosmeticArmorTrimKey;
    private final NamespacedKey spinningTopHatKey;
    private final Map<UUID, ShotMode> armedShots;
    private final Map<UUID, Long> railgunBlastImmunityUntilMs;
    private final Map<UUID, Long> sonicCooldownUntilMs;
    private final Map<UUID, Long> lastBreathWarmupUntilMs;
    private final Map<UUID, Float> voidWarpCharge;
    private final Map<UUID, Long> voidWarpCooldownUntilMs;
    private final Set<UUID> voidWarpHudPlayers;
    private final Set<UUID> hubHotbarDisabledPlayers;
    private final Set<String> conditionalHotbarWarnings = new HashSet<>();
    private final Set<UUID> pendingArmorTrimRefreshes;
    private final Set<UUID> temporarilyHiddenHeadCosmetics;
    private final CosmeticStateStore cosmeticStateStore;
    private final CosmeticService cosmeticService;
    private final Map<String, HeadCosmetic> headCosmeticRegistry;
    private final List<HeadCosmetic> headCosmeticMenuOrder;
    private FileConfiguration cosmeticRegistryConfig;
    private BukkitTask headCosmeticTask;
    private BukkitTask voidWarpHudTask;
    private BukkitTask tabListTask;
    private int legendarySkinRefreshTick;
    private long spinningTopHatTick;
    private SpleefService spleefService;
    private BridgeService bridgeService;
    private KitEditorService kitEditorService;
    private MinigameRouletteService minigameRouletteService;
    private Supplier<RecipeBookRegistry> hungerGamesUtilityRegistry = () -> null;

    public void setHungerGamesUtilityRegistry(Supplier<RecipeBookRegistry> registry) {
        hungerGamesUtilityRegistry = Objects.requireNonNull(registry);
    }

    public MenuService(JavaPlugin plugin, LootTableService lootTableService, FriendService friendService, PartyService partyService) {
        this.plugin = plugin;
        this.lootTableService = lootTableService;
        this.friendService = friendService;
        this.partyService = partyService;
        this.actionKey = new NamespacedKey(plugin, "menu_action");
        this.lootTableKey = new NamespacedKey(plugin, "loot_table_id");
        this.shotModeKey = new NamespacedKey(plugin, "shot_mode");
        this.sonicCrossbowKey = new NamespacedKey(plugin, "sonic_crossbow");
        this.railgunCrossbowKey = new NamespacedKey(plugin, "railgun_crossbow");
        this.railgunModeKey = new NamespacedKey(plugin, "railgun_mode");
        this.voidbreakerMaceKey = new NamespacedKey(plugin, "voidbreaker_mace");
        this.sonicShotKey = new NamespacedKey(plugin, "sonic_shot");
        this.venomZombieKey = new NamespacedKey(plugin, "venom_zombie");
        this.playerMenuCompassKey = new NamespacedKey(plugin, "player_menu_compass");
        this.playerHubItemKey = new NamespacedKey(plugin, "player_hub_item");
        this.settingsParticlesKey = new NamespacedKey(plugin, "settings_particles_enabled");
        this.settingsEffectsLevelKey = new NamespacedKey(plugin, "settings_effects_level");
        this.cosmeticArmorTrimKey = new NamespacedKey(plugin, "cosmetic_armor_trim");
        this.spinningTopHatKey = new NamespacedKey(plugin, SPINNING_TOP_HAT_ID);
        this.armedShots = new HashMap<>();
        this.railgunBlastImmunityUntilMs = new HashMap<>();
        this.sonicCooldownUntilMs = new HashMap<>();
        this.lastBreathWarmupUntilMs = new HashMap<>();
        this.voidWarpCharge = new HashMap<>();
        this.voidWarpCooldownUntilMs = new HashMap<>();
        this.voidWarpHudPlayers = new HashSet<>();
        this.hubHotbarDisabledPlayers = new HashSet<>();
        this.pendingArmorTrimRefreshes = new HashSet<>();
        this.temporarilyHiddenHeadCosmetics = new HashSet<>();
        loadHubHotbarSettings();
        this.headCosmeticRegistry = new HashMap<>();
        this.headCosmeticMenuOrder = new ArrayList<>();
        loadHeadCosmeticRegistry();
        this.cosmeticStateStore = new CosmeticStateStore(
            plugin,
            COSMETIC_OWNED_CONFIG_ROOT,
            COSMETIC_CONFIG_ROOT,
            COSMETIC_KILL_MESSAGE_CONFIG_ROOT,
            COSMETIC_LEGENDARY_CONFIG_ROOT,
            COSMETIC_RANK_STYLE_CONFIG_ROOT,
            COSMETIC_SYSTEM_CONFIG_ROOT,
            COSMETIC_FAVORITES_CONFIG_ROOT
        );
        this.cosmeticService = new CosmeticService(
            cosmeticStateStore,
            this::allOwnedCosmeticIds,
            this::normalizeLegacyCosmeticId,
            this::isOwnedCosmeticId,
            (legendaryKey, cosmeticId) -> {
                HeadCosmetic cosmetic = findHeadCosmetic(cosmeticId);
                return cosmetic.isLegendarySkin() && Objects.equals(cosmetic.legendaryKey, legendaryKey);
            },
            styleId -> normalizeRankStyleId(styleId) != null,
            HeadCosmetic.NONE.id,
            DEFAULT_RANK_STYLE_ID
        );
        cosmeticService.load();
        startHeadCosmeticTask();
        startVoidWarpHudTask();
    }

    public void setManagedLootService(ManagedLootService managedLootService) {
        this.managedLootService = managedLootService;
    }

    public void setSpleefService(SpleefService spleefService) {
        this.spleefService = spleefService;
    }

    public void setBridgeService(BridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    public void setKitEditorService(KitEditorService kitEditorService) {
        this.kitEditorService = kitEditorService;
    }

    public void setMinigameRouletteService(MinigameRouletteService minigameRouletteService) {
        this.minigameRouletteService = minigameRouletteService;
    }

    public void openMainMenu(Player player) {
        Inventory inv = menuInventory(36, MAIN_TITLE_COLOR + MAIN_TITLE);
        frame(inv);

        if (player.hasPermission("hgloot.admin")) {
            inv.setItem(10, button(Material.TOTEM_OF_UNDYING, MATCH_COLOR + "BR Admin", "open_admin_br_tools"));
            inv.setItem(11, button(Material.IRON_SWORD, MATCH_COLOR + "HG Admin", "open_admin_hg_tools"));
            inv.setItem(12, button(Material.SNOW_BLOCK, MAIN_TITLE_COLOR + "Minigames", "open_admin_minigames_tools"));
            inv.setItem(13, button(Material.NETHERITE_SWORD, COMBAT_COLOR + "Weapons", "open_admin_combat_tools"));
            inv.setItem(15, button(Material.NETHER_STAR, COMBAT_COLOR + "Bosses", "open_admin_boss_tools"));
            inv.setItem(16, button(Material.TRIAL_SPAWNER, COMBAT_COLOR + "Boss Rush", "open_admin_bossrush_tools"));
            inv.setItem(22, button(Material.REDSTONE, DEV_COLOR + "Debug Tools", "open_admin_dev_tools"));
        }
        if (player.hasPermission("hgloot.moderation.menu") || player.hasPermission("hgloot.moderation.warn")
            || player.hasPermission("hgloot.moderation.reports")) {
            inv.setItem(14, button(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Warnings", "open_admin_warnings_tools"));
        }

        inv.setItem(31, guiButton(GUI_MODEL_CLOSE, ChatColor.RED + "Exit", "close"));
        player.openInventory(inv);
    }

    public void openPlayerMenu(Player player) {
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + PLAYER_MENU_TITLE, "navigator");
        QueueService queueService = queueServiceOrNull();
        int hgPlaying = queueService == null ? 0 : queueService.hungerGamesPlayingCount();
        int minigamePlaying = minigameRouletteService == null ? 0 : minigameRouletteService.playingCount();
        boolean rouletteQueued = minigameRouletteService != null && minigameRouletteService.isQueued(player.getUniqueId());

        fillNavigatorCard(inv, new int[] {9, 10, 11, 12, 18, 19, 20, 21}, ChatColor.AQUA + "BATTLE ROYALE",
            "noop",
            "Battle Royale is coming soon.",
            "",
            ChatColor.RED + "Currently Unavailable");
        fillNavigatorCard(inv, new int[] {14, 15, 16, 17, 23, 24, 25, 26}, ChatColor.AQUA + "HUNGER GAMES",
            "player_open_hungergames",
            "Search the map for loot, craft powerful",
            "items, and eliminate every opponent.",
            "",
            "Features custom maps, unique crafts,",
            "KOTH events, and legendary weapons.",
            "",
            hgPlaying + " playing now",
            "", "Click to Choose Solo or Duos");
        fillNavigatorCard(inv, new int[] {38, 39, 40, 41, 42, 47, 48, 49, 50, 51}, ChatColor.AQUA + "MINIGAME ROULETTE",
            "player_minigame_roulette_join",
            "Compete through a random rotation of",
            "Hydra minigames.",
            "",
            "Earn points based on your placement",
            "and help your team finish in first.",
            "",
            minigamePlaying + " playing now",
            "", rouletteQueued ? "Click to Leave Queue" : "Click to Join");
        inv.setItem(45, button(Material.IRON_SWORD, ChatColor.AQUA + "Kit Editor", "open_player_kit_editor"));

        player.openInventory(inv);
    }

    private void fillNavigatorCard(Inventory inventory, int[] slots, String title, String action, String... lore) {
        ItemStack target = iconLoreTarget(GUI_MODEL_CLICK_TARGET, title, action, lore);
        for (int slot : slots) {
            inventory.setItem(slot, target.clone());
        }
    }

    public void openPlayerPlayMenu(Player player) {
        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Battle Royale");
        frame(inv);
        inv.setItem(11, button(Material.GOLDEN_HELMET, ChatColor.GOLD + "Solo", "player_queue_join_solo"));
        inv.setItem(15, button(Material.CHAINMAIL_CHESTPLATE, ChatColor.AQUA + "Team", "player_queue_join_duo"));
        inv.setItem(22, guiButton(GUI_MODEL_ARROW_DOWN, WARNING_COLOR + "Return", "back_player_menu"));
        player.openInventory(inv);
    }

    public void openPlayerHungerGamesMenu(Player player) {
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Hunger Games", "navigator_hunger_games");
        fillNavigatorCard(inv, new int[] {18, 19, 20, 21, 27, 28, 29, 30}, ChatColor.AQUA + "HUNGER GAMES SOLOS",
            "player_queue_join_hungergames_solo",
            "Scavenge, craft, and outlast every opponent.",
            "",
            "Click to Join");
        fillNavigatorCard(inv, new int[] {23, 24, 25, 26, 32, 33, 34, 35}, ChatColor.AQUA + "HUNGER GAMES DUOS",
            "player_queue_join_hungergames_duos",
            "Scavenge and fight together until one duo remains.",
            "Bring a partner or find one when you queue.",
            "",
            "Click to Join");
        player.openInventory(inv);
    }

    public void openPlayerMinigamesMenu(Player player) {
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Minigames", "navigator_minigames");
        inv.setItem(0, iconClickTarget(GUI_MODEL_NAVIGATOR_EXIT, "back_player_menu"));
        player.openInventory(inv);
    }

    public void openPlayerMinigameRouletteMenu(Player player) {
        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Minigame Roulette");
        frame(inv);
        if (!player.hasPermission("hgloot.admin")) {
            inv.setItem(13, button(Material.COMPASS, ChatColor.AQUA + "Minigame Roulette",
                "player_minigame_roulette_join", ChatColor.GRAY + "Click to Join Roulette"));
            inv.setItem(22, guiButton(GUI_MODEL_ARROW_DOWN, WARNING_COLOR + "Return", "open_player_minigames_menu"));
            player.openInventory(inv);
            return;
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Queue for Spleef.");
        if (spleefService != null) {
            lore.add(ChatColor.GRAY + "Queued: " + ChatColor.WHITE + spleefService.queuedCount() + "/" + spleefService.maxPlayersAllowed());
            lore.add(ChatColor.GRAY + "Needed: " + ChatColor.WHITE + spleefService.minPlayersRequired());
            lore.add(ChatColor.GRAY + "Status: " + ChatColor.YELLOW + spleefService.statusLabel());
            lore.add(ChatColor.GRAY + "Map: " + ChatColor.AQUA + spleefService.selectedMapDisplayName());
        }
        inv.setItem(11, buttonWithModel(
            Material.IRON_SHOVEL,
            "hydra:spleef_shovel",
            ChatColor.AQUA + "Spleef",
            "player_spleef_join",
            lore.toArray(new String[0])
        ));
        inv.setItem(15, button(
            Material.MACE,
            ChatColor.DARK_PURPLE + "Void Roulette",
            "player_voidroulette_join",
            ChatColor.GRAY + "Floating pillars over the void.",
            ChatColor.GRAY + "Every cycle curses one Voidbreaker.",
            ChatColor.GRAY + "Queue: 4-16 players"
        ));
        inv.setItem(22, guiButton(GUI_MODEL_ARROW_DOWN, WARNING_COLOR + "Return", "open_player_minigames_menu"));
        player.openInventory(inv);
    }

    public void openPlayerMarketMenu(Player player) {
        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Economy");
        frame(inv);
        inv.setItem(13, button(Material.GOLD_NUGGET, ChatColor.GREEN + "Auction House", "player_market_casino"));
        inv.setItem(22, guiButton(GUI_MODEL_ARROW_DOWN, WARNING_COLOR + "Return", "back_player_menu"));
        player.openInventory(inv);
    }

    private static final PlayerSettingsService.Toggle[] SETTINGS_PAGE_ONE_TOGGLES = {
        PlayerSettingsService.Toggle.NIGHT_VISION,
        PlayerSettingsService.Toggle.TEAM_GLOW,
        PlayerSettingsService.Toggle.SPECTATOR_VISIBILITY,
        PlayerSettingsService.Toggle.ROTATING_TIPS,
        PlayerSettingsService.Toggle.AUTO_REQUEUE,
        PlayerSettingsService.Toggle.REJOIN_REMINDER,
        PlayerSettingsService.Toggle.PROXY_CHAT,
        PlayerSettingsService.Toggle.FRIEND_NOTIFICATIONS,
        PlayerSettingsService.Toggle.FRIEND_REQUESTS,
        PlayerSettingsService.Toggle.PRIVATE_MESSAGES,
        PlayerSettingsService.Toggle.PARTY_INVITES,
        PlayerSettingsService.Toggle.LOBBY_FLIGHT
    };
    private static final int[] SETTINGS_PAGE_ONE_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
    private static final PlayerSettingsService.Toggle[] SETTINGS_PAGE_TWO_TOGGLES = {};
    private static final int[] SETTINGS_PAGE_TWO_SLOTS = {};

    public void openPlayerSettingsMenu(Player player) {
        openPlayerSettingsMenu(player, 1);
    }

    private void openPlayerSettingsMenu(Player player, int page) {
        int selectedPage = page == 2 ? 2 : 1;
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Settings",
            selectedPage == 1 ? "settings_page_1" : "settings_page_2");
        PlayerSettingsService settings = ((HGLootPlugin) plugin).playerSettingsService();
        PlayerSettingsService.Toggle[] visible = selectedPage == 1
            ? SETTINGS_PAGE_ONE_TOGGLES : SETTINGS_PAGE_TWO_TOGGLES;
        int[] settingSlots = selectedPage == 1 ? SETTINGS_PAGE_ONE_SLOTS : SETTINGS_PAGE_TWO_SLOTS;
        for (int i = 0; i < visible.length; i++) {
            PlayerSettingsService.Toggle setting = visible[i];
            boolean selected = setting == PlayerSettingsService.Toggle.LOBBY_FLIGHT
                ? settings.flightEnabled(player) : settings.enabled(player.getUniqueId(), setting);
            boolean locked = setting == PlayerSettingsService.Toggle.LOBBY_FLIGHT
                && !settings.canUseFlightToggle(player);
            int slot = settingSlots[i];
            inv.setItem(slot, guiButton(!locked && selected ? GUI_MODEL_TOGGLE_ON : GUI_MODEL_TOGGLE_OFF,
                ChatColor.AQUA + setting.title(), "player_settings_toggle:" + setting.key(),
                ChatColor.GRAY + setting.description(),
                locked ? ChatColor.RED + "Hydra+ Required" : (selected ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                locked ? ChatColor.RED + "Unavailable" : ChatColor.GRAY + "Click to Toggle"));
        }
        if (selectedPage == 2) {
            PlayerSettingsService.LobbyTime time = settings.lobbyTime(player.getUniqueId());
            Material timeIcon = switch (time) {
                case SERVER_DEFAULT -> Material.GRAY_CONCRETE;
                case DAY -> Material.YELLOW_CONCRETE;
                case SUNSET -> Material.ORANGE_CONCRETE;
                case NIGHT -> Material.BLUE_CONCRETE;
                case SUNRISE -> Material.RED_CONCRETE;
            };
            inv.setItem(22, button(timeIcon, ChatColor.AQUA + "Lobby Time", "player_settings_lobby_time",
                ChatColor.GRAY + "Changes your time of day in lobbies.",
                ChatColor.WHITE + time.label(), ChatColor.GRAY + "Click to Cycle"));
        }
        player.openInventory(inv);
    }

    private void refreshPlayerSettingsMenu(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
            && "settings_page_2".equalsIgnoreCase(holder.theme())) {
            openPlayerSettingsMenu(player, 2);
        } else {
            openPlayerSettingsMenu(player, 1);
        }
    }

    public void openPlayerKitEditorMenu(Player player) {
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Kit Editor", "kit_editor");
        inv.setItem(19, button(Material.IRON_SWORD, ChatColor.AQUA + "Bridge Kit", "player_edit_bridge_kit",
            ChatColor.GRAY + "Edit your Bridge item layout."));
        inv.setItem(21, button(Material.DIAMOND_SWORD, ChatColor.AQUA + "Boss Rush Kit", "player_edit_boss_rush_kit",
            ChatColor.GRAY + "Rearrange your Boss Rush starting gear."));
        for (int slot : new int[] {23, 25, 37, 39, 41, 43}) {
            inv.setItem(slot, button(Material.BARRIER, ChatColor.RED + "Coming Soon!", "noop"));
        }
        player.openInventory(inv);
    }

    public void openPlayerCosmeticsMenu(Player player) {
        openPlayerCosmeticsRootMenu(player);
    }

    public void openPlayerLobbyCosmeticsMenu(Player player) {
        openPlayerCosmeticsRootMenu(player);
    }

    private void openPlayerCosmeticsRootMenu(Player player) {
        openPlayerCosmeticCategoryMenu(player, CosmeticCategory.HATS, 0);
    }

    private ItemStack cosmeticCategoryButton(CosmeticCategory category) {
        ItemStack stack = category == CosmeticCategory.LEGENDARY_SKINS
            ? Items.skeletalBarber()
            : new ItemStack(category.iconMaterial);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(CATEGORY_TITLE_COLOR + category.displayName);
        meta.setLore(null);
        meta.getPersistentDataContainer().set(
            actionKey,
            PersistentDataType.STRING,
            (COSMETIC_CATEGORY_ACTION_PREFIX + category.id).toLowerCase(Locale.ROOT)
        );
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
        return stack;
    }

    private void openPlayerCosmeticsMenu(Player player, CosmeticMenuTab tab) {
        openPlayerCosmeticCategoryMenu(player, tab == null ? CosmeticCategory.PROJECTILE_TRAILS : tab.category);
    }

    private void openPlayerCosmeticDomainMenu(Player player, CosmeticDomain domain) {
        if (domain == null) {
            openPlayerCosmeticsRootMenu(player);
            return;
        }
        List<CosmeticCategory> categories = playerCosmeticCategories(player, domain);
        openPlayerCosmeticCategoryMenu(player,
            categories.isEmpty() ? CosmeticCategory.HATS : categories.get(0), 0);
    }

    private void openPlayerCosmeticCategoryMenu(Player player, CosmeticCategory category) {
        openPlayerCosmeticCategoryMenu(player, category, 0);
    }

    private void openPlayerCosmeticCategoryMenu(Player player, CosmeticCategory category, int requestedPage) {
        if (category == null) {
            openPlayerCosmeticsRootMenu(player);
            return;
        }
        List<CosmeticDefinition> cosmetics = playerCosmeticDefinitions(player, category);
        boolean includeDefaultOption = category.equipGroup != null && !cosmetics.isEmpty();
        int entryCount = cosmetics.size() + (includeDefaultOption ? 1 : 0);
        int pageSize = PLAYER_COSMETICS_CONTENT_SLOTS.length;
        int maxPage = Math.max(0, (entryCount - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inv = themedMenuInventory(
            PLAYER_COSMETICS_MENU_SIZE,
            MAIN_TITLE_COLOR + category.displayName,
            cosmeticCategoryTheme(category)
        );
        placePlayerCosmeticCategoryTabs(inv, category, false);

        UUID playerId = player.getUniqueId();
        int logicalIndex = page * pageSize;
        int slotIndex = 0;

        if (includeDefaultOption && logicalIndex == 0) {
            boolean selected = selectedCosmeticId(playerId, category) == null;
            inv.setItem(
                PLAYER_COSMETICS_CONTENT_SLOTS[slotIndex++],
                button(
                    Material.BARRIER,
                    (selected ? ChatColor.GREEN : ChatColor.GRAY) + (category == CosmeticCategory.KILL_MESSAGES ? "Default" : "None"),
                    COSMETIC_SELECT_ACTION_PREFIX + "none@" + category.id
                )
            );
            logicalIndex++;
        }

        int cosmeticIndex = logicalIndex - (includeDefaultOption ? 1 : 0);
        while (cosmeticIndex >= 0 && cosmeticIndex < cosmetics.size()
            && slotIndex < PLAYER_COSMETICS_CONTENT_SLOTS.length) {
            CosmeticDefinition cosmetic = cosmetics.get(cosmeticIndex++);
            boolean owned = cosmeticOwnedBy(playerId, player, cosmetic);
            boolean selected = isCosmeticSelected(playerId, player, cosmetic);
            String itemName = cosmeticDisplayName(cosmetic, owned, selected);
            String action = cosmetic.equipSupported ? COSMETIC_SELECT_ACTION_PREFIX + cosmetic.id : "noop";
            ItemStack item = cosmeticButton(cosmetic, itemName, action, owned, selected);
            applyCosmeticClickLore(item, cosmeticService.isFavoriteCosmetic(playerId, cosmetic.id));
            inv.setItem(PLAYER_COSMETICS_CONTENT_SLOTS[slotIndex++], item);
        }

        if (cosmetics.isEmpty()) {
            inv.setItem(31, emptyCosmeticCategoryItem(category));
        }
        if (page > 0) {
            inv.setItem(
                PLAYER_COSMETICS_PREVIOUS_SLOT,
                iconLoreTarget(
                    GUI_MODEL_CLICK_TARGET,
                    ChatColor.YELLOW + "Previous Page",
                    COSMETIC_PAGE_ACTION_PREFIX + category.id + ":" + (page - 1)
                )
            );
        }
        if (page < maxPage) {
            inv.setItem(
                PLAYER_COSMETICS_NEXT_SLOT,
                iconLoreTarget(
                    GUI_MODEL_CLICK_TARGET,
                    ChatColor.YELLOW + "Next Page",
                    COSMETIC_PAGE_ACTION_PREFIX + category.id + ":" + (page + 1)
                )
            );
        }
        player.openInventory(inv);
    }

    private void openPlayerFavoritesMenu(Player player, int requestedPage) {
        UUID playerId = player.getUniqueId();
        Set<String> favoriteIds = cosmeticService.favoriteCosmeticIds(playerId);
        List<CosmeticDefinition> favorites = new ArrayList<>();
        for (CosmeticCategory category : displayedCosmeticCategories()) {
            playerCosmeticDefinitions(player, category).stream()
                .filter(definition -> favoriteIds.contains(definition.id))
                .forEach(favorites::add);
        }
        int pageSize = PLAYER_COSMETICS_CONTENT_SLOTS.length;
        int maxPage = Math.max(0, (favorites.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inv = themedMenuInventory(
            PLAYER_COSMETICS_MENU_SIZE,
            MAIN_TITLE_COLOR + "Favorites",
            "cosmetics_favorites"
        );
        placePlayerCosmeticCategoryTabs(inv, null, true);

        int cosmeticIndex = page * pageSize;
        int slotIndex = 0;
        while (cosmeticIndex < favorites.size() && slotIndex < PLAYER_COSMETICS_CONTENT_SLOTS.length) {
            CosmeticDefinition cosmetic = favorites.get(cosmeticIndex++);
            boolean selected = isCosmeticSelected(playerId, player, cosmetic);
            ItemStack item = cosmeticButton(
                cosmetic,
                cosmeticDisplayName(cosmetic, true, selected),
                COSMETIC_SELECT_ACTION_PREFIX + cosmetic.id,
                true,
                selected
            );
            applyCosmeticClickLore(item, true);
            inv.setItem(PLAYER_COSMETICS_CONTENT_SLOTS[slotIndex++], item);
        }
        if (favorites.isEmpty()) {
            inv.setItem(31, button(
                Material.BARRIER,
                WARNING_COLOR + "No Favorites",
                "noop",
                "Right-click a cosmetic to favorite it."
            ));
        }
        if (page > 0) {
            inv.setItem(
                PLAYER_COSMETICS_PREVIOUS_SLOT,
                iconLoreTarget(
                    GUI_MODEL_CLICK_TARGET,
                    ChatColor.YELLOW + "Previous Page",
                    COSMETIC_FAVORITES_PAGE_ACTION_PREFIX + (page - 1)
                )
            );
        }
        if (page < maxPage) {
            inv.setItem(
                PLAYER_COSMETICS_NEXT_SLOT,
                iconLoreTarget(
                    GUI_MODEL_CLICK_TARGET,
                    ChatColor.YELLOW + "Next Page",
                    COSMETIC_FAVORITES_PAGE_ACTION_PREFIX + (page + 1)
                )
            );
        }
        player.openInventory(inv);
    }

    private void placePlayerCosmeticCategoryTabs(Inventory inv, CosmeticCategory selectedCategory, boolean favoritesSelected) {
        List<CosmeticCategory> categories = displayedCosmeticCategories();
        for (int i = 0; i < categories.size(); i++) {
            CosmeticCategory category = categories.get(i);
            String color = category == selectedCategory ? ChatColor.GREEN.toString() : ChatColor.AQUA.toString();
            inv.setItem(
                PLAYER_COSMETICS_CATEGORY_SLOTS[i],
                iconLoreTarget(
                    GUI_MODEL_CLICK_TARGET,
                    color + category.displayName,
                    COSMETIC_CATEGORY_ACTION_PREFIX + category.id
                )
            );
        }
        inv.setItem(
            PLAYER_COSMETICS_CATEGORY_SLOTS[8],
            iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                (favoritesSelected ? ChatColor.GREEN : ChatColor.LIGHT_PURPLE) + "Favorites",
                "player_cosmetics_favorites",
                ChatColor.GRAY + "View your favorite cosmetics."
            )
        );
    }

    private List<CosmeticCategory> displayedCosmeticCategories() {
        return List.of(
            CosmeticCategory.HATS,
            CosmeticCategory.LEGENDARY_SKINS,
            CosmeticCategory.KILL_MESSAGES,
            CosmeticCategory.KILL_EFFECTS,
            CosmeticCategory.PROJECTILE_TRAILS,
            CosmeticCategory.ARMOR_TRIMS,
            CosmeticCategory.RANK_STYLES
        );
    }

    private void applyCosmeticClickLore(ItemStack item, boolean favorite) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null
            ? new ArrayList<>(meta.getLore())
            : new ArrayList<>();
        lore.removeIf(line -> {
            String plain = ChatColor.stripColor(line);
            return plain != null && (plain.equals("Favorite")
                || plain.equals("Not favorited")
                || plain.startsWith("Left-click to ")
                || plain.startsWith("Right-click to "));
        });
        while (!lore.isEmpty() && lore.getLast().isBlank()) {
            lore.removeLast();
        }
        if (!lore.isEmpty()) {
            lore.add("");
        }
        lore.add(favorite
            ? ChatColor.LIGHT_PURPLE + "Favorite"
            : ChatColor.GRAY + "Not favorited");
        lore.add(ChatColor.AQUA + "Left-click to equip.");
        lore.add(ChatColor.YELLOW + "Right-click to " + (favorite ? "unfavorite." : "favorite."));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack emptyCosmeticCategoryItem(CosmeticCategory category) {
        String categoryName = category == null ? "Cosmetics" : category.displayName;
        return button(
            Material.BARRIER,
            WARNING_COLOR + "No " + categoryName + " Owned",
            "noop",
            "You can unlock cosmetics from Shards."
        );
    }

    public List<String> getOwnedHeadCosmeticIds(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        Set<String> owned = ensureOwnedHeadCosmetics(playerId);
        List<String> ordered = new ArrayList<>();
        for (HeadCosmetic cosmetic : headCosmeticMenuOrder) {
            if (owned.contains(cosmetic.id)) {
                ordered.add(cosmetic.id);
            }
        }
        return ordered;
    }

    public List<String> getAuctionSellableHeadCosmeticIds(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        Set<String> owned = ensureOwnedHeadCosmetics(playerId);
        List<String> ordered = new ArrayList<>();
        for (HeadCosmetic cosmetic : headCosmeticMenuOrder) {
            if (owned.contains(cosmetic.id) && isAuctionSellableHeadCosmetic(cosmetic.id)) {
                ordered.add(cosmetic.id);
            }
        }
        return ordered;
    }

    public List<String> getAuctionSellableCosmeticIds(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>();
        for (CosmeticCategory category : List.of(
            CosmeticCategory.HATS,
            CosmeticCategory.LEGENDARY_SKINS,
            CosmeticCategory.PROJECTILE_TRAILS,
            CosmeticCategory.ARMOR_TRIMS,
            CosmeticCategory.KILL_EFFECTS,
            CosmeticCategory.KILL_MESSAGES,
            CosmeticCategory.TITLES
        )) {
            for (CosmeticDefinition definition : cosmeticDefinitions(category)) {
                if (isAuctionSellableCosmetic(definition.id) && ownsCosmetic(playerId, definition.id)) {
                    ordered.add(definition.id);
                }
            }
        }
        return ordered;
    }

    private boolean hasCosmeticsInDomain(CosmeticDomain domain) {
        return !cosmeticCategories(domain).isEmpty();
    }

    private List<CosmeticCategory> adminCosmeticCategories() {
        List<CosmeticCategory> categories = new ArrayList<>();
        for (CosmeticCategory category : List.of(
            CosmeticCategory.HATS,
            CosmeticCategory.LEGENDARY_SKINS,
            CosmeticCategory.PROJECTILE_TRAILS,
            CosmeticCategory.ARMOR_TRIMS,
            CosmeticCategory.KILL_EFFECTS,
            CosmeticCategory.KILL_MESSAGES
        )) {
            if (!cosmeticDefinitions(category).isEmpty()) {
                categories.add(category);
            }
        }
        return categories;
    }

    private List<CosmeticCategory> cosmeticCategories(CosmeticDomain domain) {
        List<CosmeticCategory> categories = new ArrayList<>();
        if (domain == null) {
            return categories;
        }
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (category.domain != domain) {
                continue;
            }
            if (!cosmeticDefinitions(category).isEmpty()) {
                categories.add(category);
            }
        }
        return categories;
    }

    private List<CosmeticCategory> playerCosmeticCategories(Player player, CosmeticDomain domain) {
        List<CosmeticCategory> categories = new ArrayList<>();
        if (player == null || domain == null) {
            return categories;
        }
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (category.domain == domain && !cosmeticDefinitions(category).isEmpty()) {
                categories.add(category);
            }
        }
        return categories;
    }

    private List<CosmeticDefinition> cosmeticDefinitions(CosmeticCategory category) {
        List<CosmeticDefinition> definitions = new ArrayList<>();
        if (category == null) {
            return definitions;
        }
        for (HeadCosmetic cosmetic : headCosmeticMenuOrder) {
            if (category == CosmeticCategory.HATS && !hasCustomHatModel(cosmetic)) {
                continue;
            }
            CosmeticDefinition definition = CosmeticDefinition.fromHeadCosmetic(cosmetic);
            if (definition != null && definition.category == category) {
                definitions.add(definition);
            }
        }
        if (category == CosmeticCategory.KILL_MESSAGES) {
            definitions.addAll(killMessageDefinitions());
        }
        if (category == CosmeticCategory.PROJECTILE_TRAILS
            || category == CosmeticCategory.ARMOR_TRIMS
            || category == CosmeticCategory.KILL_EFFECTS) {
            definitions.addAll(systemCosmeticDefinitions(category));
        }
        if (category == CosmeticCategory.RANK_STYLES) {
            definitions.addAll(rankCosmeticDefinitions());
        }
        definitions.sort(
            Comparator.comparingInt((CosmeticDefinition definition) -> definition.sortOrder)
                .thenComparing(definition -> definition.displayName, String.CASE_INSENSITIVE_ORDER)
        );
        return definitions;
    }

    private List<CosmeticDefinition> playerCosmeticDefinitions(Player player, CosmeticCategory category) {
        if (player == null || category == null) {
            return List.of();
        }
        UUID playerId = player.getUniqueId();
        List<CosmeticDefinition> visible = new ArrayList<>();
        for (CosmeticDefinition definition : cosmeticDefinitions(category)) {
            if (cosmeticOwnedBy(playerId, player, definition)) {
                visible.add(definition);
            }
        }
        return visible;
    }

    private boolean hasCustomHatModel(HeadCosmetic cosmetic) {
        return cosmetic != null
            && cosmetic.isHat()
            && ((cosmetic.itemModel != null && !cosmetic.itemModel.isBlank()) || cosmetic.customModelData > 0);
    }

    private List<CosmeticDefinition> rankCosmeticDefinitions() {
        return List.of(
            CosmeticDefinition.rank("rank_style_classic", "Hydra+ Classic", "Default Hydra+ gradient",
                Material.PRISMARINE_CRYSTALS, "premium", 10, null),
            CosmeticDefinition.rank("rank_style_mint", "Hydra+ Mint", "Fresh green-to-cyan Hydra+ gradient",
                Material.PRISMARINE_SHARD, "premium", 20, null),
            CosmeticDefinition.rank("rank_style_solar", "Hydra+ Solar", "Warm gold-to-orange Hydra+ gradient",
                Material.NETHER_STAR, "premium", 30, null)
        );
    }

    private List<CosmeticDefinition> killMessageDefinitions() {
        return List.of(
            CosmeticDefinition.killMessage("kill_message_shattered", "Shattered",
                "{killer} shattered {victim}.", Material.IRON_SWORD, "default", "common", 10,
                "{killer} shattered {victim}."),
            CosmeticDefinition.killMessage("kill_message_abyss", "Into the Abyss",
                "{killer} cast {victim} into the abyss.", Material.ENDER_PEARL, "default", "common", 20,
                "{killer} cast {victim} into the abyss."),
            CosmeticDefinition.killMessage("kill_message_beneath", "Beneath",
                "{victim} fell beneath {killer}.", Material.DEEPSLATE, "default", "common", 30,
                "{victim} fell beneath {killer}."),
            CosmeticDefinition.killMessage("kill_message_silenced", "Silenced",
                "{killer} silenced {victim}.", Material.ECHO_SHARD, "default", "common", 40,
                "{killer} silenced {victim}."),
            CosmeticDefinition.killMessage("kill_message_deep_burial", "Deep Burial",
                "{killer} buried {victim} in the deep.", Material.MUD, "default", "common", 50,
                "{killer} buried {victim} in the deep."),
            CosmeticDefinition.killMessage("kill_message_claimed", "Claimed",
                "{victim} was claimed by {killer}.", Material.PRISMARINE_SHARD, "default", "rare", 60,
                "{victim} was claimed by {killer}."),
            CosmeticDefinition.killMessage("kill_message_broken", "Broken",
                "{killer} broke {victim}.", Material.ANVIL, "default", "rare", 70,
                "{killer} broke {victim}."),
            CosmeticDefinition.killMessage("kill_message_vanished", "Vanished",
                "{victim} vanished before {killer}.", Material.ENDER_EYE, "default", "epic", 80,
                "{victim} vanished before {killer}.")
        );
    }

    private List<CosmeticDefinition> systemCosmeticDefinitions(CosmeticCategory category) {
        if (category == CosmeticCategory.PROJECTILE_TRAILS) {
            return List.of(
                CosmeticDefinition.system("trail_blackstatic", category, "Blackstatic", "Black-purple static leaks from fired projectiles.", Material.BLACK_DYE, "default", "epic", 10),
                CosmeticDefinition.system("trail_rift_ash", category, "Rift Ash", "Ashy void residue follows fired projectiles.", Material.GUNPOWDER, "default", "rare", 20),
                CosmeticDefinition.system("trail_deep_current", category, "Deep Current", "A calm blue current traces the projectile path.", Material.PRISMARINE_CRYSTALS, "default", "common", 30),
                CosmeticDefinition.system("trail_nullwake", category, "Nullwake", "A muted End wake trails behind projectiles.", Material.ENDER_PEARL, "default", "rare", 40),
                CosmeticDefinition.system("trail_frost_mist", category, "Frost Mist", "Cold mist drifts behind projectiles.", Material.SNOWBALL, "default", "common", 50),
                CosmeticDefinition.system("trail_tidewake", category, "Tidewake", "Light water spray follows projectiles.", Material.HEART_OF_THE_SEA, "default", "common", 60),
                CosmeticDefinition.system("trail_eclipse", category, "Eclipse Trail", "Limited eclipse sparks flicker behind projectiles.", Material.NETHER_STAR, "event", "limited", 70),
                CosmeticDefinition.system("trail_abyss_wake", category, "Abyss Wake", "A heavy abyssal wake marks projectile flight.", Material.ECHO_SHARD, "prestige", "legendary", 80)
            );
        }
        if (category == CosmeticCategory.ARMOR_TRIMS) {
            return List.of(
                CosmeticDefinition.system("trim_ascendant", category, "Ascendant Trim", "Auto-applies a bright vanilla armor trim while equipped.", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "prestige", "legendary", 10),
                CosmeticDefinition.system("trim_hydra_crown", category, "Hydra Crown Trim", "Auto-applies a royal vanilla armor trim while equipped.", Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, "event", "limited", 20),
                CosmeticDefinition.system("trim_kingsfall", category, "Kingsfall Trim", "Auto-applies a gold prestige armor trim while equipped.", Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, "prestige", "epic", 30),
                CosmeticDefinition.system("trim_depthscarred", category, "Depthscarred Trim", "Auto-applies a chamber-worn armor trim while equipped.", Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, "chamber", "rare", 40),
                CosmeticDefinition.system("trim_venomwalker", category, "Venomwalker Trim", "Auto-applies a venom-green armor trim while equipped.", Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, "chamber", "rare", 50),
                CosmeticDefinition.system("trim_voidglass", category, "Voidglass Trim", "Auto-applies a dark void armor trim while equipped.", Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "prestige", "epic", 60)
            );
        }
        if (category == CosmeticCategory.KILL_EFFECTS) {
            return List.of(
                CosmeticDefinition.system("kill_effect_shatterfall", category, "Shatterfall", "A quick glassy burst marks your kill.", Material.GLASS, "default", "common", 10),
                CosmeticDefinition.system("kill_effect_permafrost", category, "Permafrost", "Cold particles bloom at the victim.", Material.BLUE_ICE, "default", "rare", 20),
                CosmeticDefinition.system("kill_effect_glacial_bloom", category, "Glacial Bloom", "A short frosted bloom expands from the victim.", Material.PACKED_ICE, "default", "rare", 30),
                CosmeticDefinition.system("kill_effect_kingsfall", category, "Kingsfall", "Gold sparks collapse around the victim.", Material.GOLD_INGOT, "prestige", "epic", 40),
                CosmeticDefinition.system("kill_effect_golden_collapse", category, "Golden Collapse", "A heavy gold pulse marks a kill.", Material.GOLD_BLOCK, "prestige", "legendary", 50),
                CosmeticDefinition.system("kill_effect_crownflare", category, "Crownflare", "Limited crown flare on final impact.", Material.NETHER_STAR, "event", "limited", 60)
            );
        }
        return List.of();
    }

    private boolean cosmeticOwnedBy(UUID playerId, Player viewer, CosmeticDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.category == CosmeticCategory.RANK_STYLES) {
            return hasHydraPlusAccess(viewer);
        }
        return cosmeticService.owns(playerId, definition.id);
    }

    private boolean isCosmeticSelected(UUID playerId, Player viewer, CosmeticDefinition definition) {
        if (definition == null) {
            return false;
        }
        return switch (definition.category) {
            case HATS -> definition.id.equals(selectedHatCosmeticId(playerId));
            case LEGENDARY_SKINS -> definition.id.equals(getSelectedLegendarySkinId(playerId, definition.legendaryKey));
            case KILL_MESSAGES -> definition.id.equals(selectedKillMessageId(playerId));
            case PROJECTILE_TRAILS, ARMOR_TRIMS, KILL_EFFECTS, TITLES -> definition.id.equals(selectedSystemCosmeticId(playerId, definition.category));
            case RANK_STYLES -> definition.id.equals(activeHydraRankCosmeticId(viewer));
        };
    }

    private String selectedHatCosmeticId(UUID playerId) {
        if (playerId == null) {
            return HeadCosmetic.NONE.id;
        }
        return cosmeticService.selectedHatCosmeticId(playerId);
    }

    private String activeHydraRankCosmeticId(Player player) {
        if (!hasHydraPlusAccess(player)) {
            return null;
        }
        return cosmeticService.selectedRankStyleId(player.getUniqueId());
    }

    public String selectedHydraRankStyleId(UUID playerId) {
        return playerId == null ? DEFAULT_RANK_STYLE_ID : cosmeticService.selectedRankStyleId(playerId);
    }

    public String selectedKillMessageId(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return cosmeticService.selectedKillMessageId(playerId);
    }

    private String selectedCosmeticId(UUID playerId, CosmeticCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case HATS -> HeadCosmetic.NONE.id.equals(selectedHatCosmeticId(playerId)) ? null : selectedHatCosmeticId(playerId);
            case KILL_MESSAGES -> selectedKillMessageId(playerId);
            case PROJECTILE_TRAILS, ARMOR_TRIMS, KILL_EFFECTS, TITLES -> selectedSystemCosmeticId(playerId, category);
            case LEGENDARY_SKINS, RANK_STYLES -> null;
        };
    }

    private String selectedSystemCosmeticId(UUID playerId, CosmeticCategory category) {
        if (playerId == null || category == null || category.equipGroup == null) {
            return null;
        }
        return cosmeticService.selectedSystemCosmeticId(playerId, category.equipGroup);
    }

    public String killMessageSuffix(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CosmeticDefinition definition = findKillMessageDefinition(selectedKillMessageId(playerId));
        return definition == null ? null : definition.killMessageTemplate;
    }

    public String customKillMessage(UUID playerId, String killerName, String victimName) {
        if (playerId == null || killerName == null || victimName == null) {
            return null;
        }
        CosmeticDefinition definition = findKillMessageDefinition(selectedKillMessageId(playerId));
        if (definition == null || definition.killMessageTemplate == null || definition.killMessageTemplate.isBlank()) {
            return null;
        }
        return definition.killMessageTemplate
            .replace("{killer}", ChatColor.WHITE + killerName + ChatColor.GRAY)
            .replace("{victim}", ChatColor.RED + victimName + ChatColor.GRAY);
    }

    private boolean hasHydraPlusAccess(Player player) {
        if (player == null) {
            return false;
        }
        if (plugin instanceof HGLootPlugin hydra) {
            return hydra.hasHydraPlusRank(player.getUniqueId());
        }
        return player.hasPermission(RANK_PERMISSION_HYDRA)
            || player.hasPermission(RANK_PERMISSION_HYDRA_PLUS)
            || player.hasPermission(RANK_PERMISSION_HYDRA_PLUS_TWO)
            || player.hasPermission(RANK_PERMISSION_HYDRA_PLUS_THREE);
    }

    private String selectedRankStyleId(UUID playerId) {
        return playerId == null ? DEFAULT_RANK_STYLE_ID : cosmeticService.selectedRankStyleId(playerId);
    }

    private Collection<String> allOwnedCosmeticIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(headCosmeticRegistry.keySet());
        for (CosmeticDefinition definition : killMessageDefinitions()) {
            ids.add(definition.id);
        }
        for (CosmeticDefinition definition : rankCosmeticDefinitions()) {
            ids.add(definition.id);
        }
        for (CosmeticCategory category : List.of(
            CosmeticCategory.PROJECTILE_TRAILS,
            CosmeticCategory.ARMOR_TRIMS,
            CosmeticCategory.KILL_EFFECTS
        )) {
            for (CosmeticDefinition definition : systemCosmeticDefinitions(category)) {
                ids.add(definition.id);
            }
        }
        return ids;
    }

    private boolean isOwnedCosmeticId(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return false;
        }
        return !HeadCosmetic.NONE.id.equals(cosmeticId)
            && (headCosmeticRegistry.containsKey(cosmeticId)
                || findKillMessageDefinition(cosmeticId) != null
                || findSystemCosmeticDefinition(cosmeticId) != null);
    }

    private CosmeticDefinition findRankStyleDefinition(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return null;
        }
        for (CosmeticDefinition definition : rankCosmeticDefinitions()) {
            if (definition.id.equalsIgnoreCase(cosmeticId)) {
                return definition;
            }
        }
        return null;
    }

    private CosmeticDefinition findSystemCosmeticDefinition(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return null;
        }
        for (CosmeticCategory category : List.of(
            CosmeticCategory.PROJECTILE_TRAILS,
            CosmeticCategory.ARMOR_TRIMS,
            CosmeticCategory.KILL_EFFECTS
        )) {
            for (CosmeticDefinition definition : systemCosmeticDefinitions(category)) {
                if (definition.id.equalsIgnoreCase(cosmeticId)) {
                    return definition;
                }
            }
        }
        return null;
    }

    private String normalizeRankStyleId(String cosmeticId) {
        CosmeticDefinition definition = findRankStyleDefinition(cosmeticId);
        return definition == null ? null : definition.id;
    }

    private CosmeticDefinition findKillMessageDefinition(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return null;
        }
        for (CosmeticDefinition definition : killMessageDefinitions()) {
            if (definition.id.equalsIgnoreCase(cosmeticId)) {
                return definition;
            }
        }
        return null;
    }

    private CosmeticDefinition findCosmeticDefinitionById(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return null;
        }
        CosmeticDefinition rankStyle = findRankStyleDefinition(cosmeticId);
        if (rankStyle != null) {
            return rankStyle;
        }
        CosmeticDefinition killMessage = findKillMessageDefinition(cosmeticId);
        if (killMessage != null) {
            return killMessage;
        }
        CosmeticDefinition system = findSystemCosmeticDefinition(cosmeticId);
        if (system != null) {
            return system;
        }
        HeadCosmetic headCosmetic = findHeadCosmetic(cosmeticId);
        return headCosmetic.isNone() ? null : CosmeticDefinition.fromHeadCosmetic(headCosmetic);
    }

    private void equipRankStyle(Player player, CosmeticDefinition rankStyle) {
        if (player == null || rankStyle == null || rankStyle.category != CosmeticCategory.RANK_STYLES) {
            return;
        }
        if (!hasHydraPlusAccess(player)) {
            player.sendMessage(ChatColor.RED + "You do not have access to Hydra+ styles.");
            return;
        }
        String styleId = normalizeRankStyleId(rankStyle.id);
        if (styleId == null) {
            return;
        }
        cosmeticService.setSelectedRankStyleId(player.getUniqueId(), styleId);
        if (plugin instanceof HGLootPlugin hydra && hydra.hydraPlusService() != null) {
            hydra.hydraPlusService().refreshNow();
        }
        player.sendMessage(ChatColor.GRAY + "Equipped Hydra+ gradient: "
            + ChatColor.YELLOW + rankStyle.displayName + ChatColor.GRAY + ".");
    }

    private void equipKillMessage(Player player, CosmeticDefinition definition) {
        if (player == null || definition == null || definition.category != CosmeticCategory.KILL_MESSAGES) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!cosmeticService.owns(playerId, definition.id)) {
            player.sendMessage(ChatColor.RED + "You do not own that cosmetic.");
            return;
        }
        cosmeticService.setSelectedKillMessageId(playerId, definition.id);
        player.sendMessage(ChatColor.GRAY + "Equipped kill message: "
            + ChatColor.YELLOW + definition.displayName + ChatColor.GRAY + ".");
    }

    private void clearKillMessage(Player player) {
        if (player == null) {
            return;
        }
        cosmeticService.setSelectedKillMessageId(player.getUniqueId(), null);
        player.sendMessage(ChatColor.GRAY + "Kill message reset.");
    }

    private void equipSystemCosmetic(Player player, CosmeticDefinition definition) {
        if (player == null || definition == null || definition.category.equipGroup == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!cosmeticService.owns(playerId, definition.id)) {
            player.sendMessage(ChatColor.RED + "You do not own that cosmetic.");
            return;
        }
        cosmeticService.setSelectedSystemCosmeticId(playerId, definition.category.equipGroup, definition.id);
        if (definition.category == CosmeticCategory.ARMOR_TRIMS) {
            refreshArmorTrimCosmetic(player);
        }
        player.sendMessage(ChatColor.GRAY + "Equipped " + definition.category.displayName + ": "
            + ChatColor.YELLOW + definition.displayName + ChatColor.GRAY + ".");
    }

    private void clearSystemCosmetic(Player player, CosmeticCategory category) {
        if (player == null || category == null || category.equipGroup == null) {
            return;
        }
        cosmeticService.setSelectedSystemCosmeticId(player.getUniqueId(), category.equipGroup, null);
        if (category == CosmeticCategory.ARMOR_TRIMS) {
            refreshArmorTrimCosmetic(player);
        }
        player.sendMessage(ChatColor.GRAY + category.displayName + " reset.");
    }

    private boolean ownsCosmetic(UUID playerId, String cosmeticId) {
        if (playerId == null) {
            return false;
        }
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        if (definition == null || definition.category == CosmeticCategory.RANK_STYLES) {
            return false;
        }
        return cosmeticService.owns(playerId, definition.id);
    }

    public boolean addCosmeticOwnership(UUID playerId, String cosmeticId) {
        if (playerId == null) {
            return false;
        }
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        if (definition == null || definition.category == CosmeticCategory.RANK_STYLES) {
            return false;
        }
        if (cosmeticService.owns(playerId, definition.id)) {
            return true;
        }
        if (!cosmeticService.grantOwnership(playerId, definition.id)) {
            return false;
        }
        refreshCosmeticVisuals(playerId, definition);
        return true;
    }

    public List<String> getShardCrateRewardCosmeticIds(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        List<String> rewardIds = new ArrayList<>();
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (category == CosmeticCategory.RANK_STYLES || category == CosmeticCategory.TITLES) {
                continue;
            }
            for (CosmeticDefinition definition : cosmeticDefinitions(category)) {
                if (definition == null || definition.hidden || definition.category == CosmeticCategory.RANK_STYLES) {
                    continue;
                }
                if (!isAuctionSellableCosmetic(definition.id)) {
                    continue;
                }
                if (!cosmeticService.owns(playerId, definition.id)) {
                    rewardIds.add(definition.id);
                }
            }
        }
        rewardIds.sort(String.CASE_INSENSITIVE_ORDER);
        return rewardIds;
    }

    public int resetAllCosmeticOwnership() {
        int affected = 0;
        ConfigurationSection ownedSection = plugin.getConfig().getConfigurationSection(COSMETIC_OWNED_CONFIG_ROOT);
        if (ownedSection != null) {
            affected = ownedSection.getKeys(false).size();
        }
        plugin.getConfig().set(COSMETIC_OWNED_CONFIG_ROOT, null);
        plugin.getConfig().set(COSMETIC_CONFIG_ROOT, null);
        plugin.getConfig().set(COSMETIC_KILL_MESSAGE_CONFIG_ROOT, null);
        plugin.getConfig().set(COSMETIC_LEGENDARY_CONFIG_ROOT, null);
        plugin.saveConfig();
        cosmeticService.load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshHeadCosmetic(player);
            refreshLegendarySkins(player);
            refreshArmorTrimCosmetic(player);
        }
        return affected;
    }

    private boolean removeCosmeticOwnership(UUID playerId, String cosmeticId) {
        if (playerId == null) {
            return false;
        }
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        if (definition == null || definition.category == CosmeticCategory.RANK_STYLES) {
            return false;
        }
        if (!cosmeticService.revokeOwnership(playerId, definition.id)) {
            return false;
        }
        refreshCosmeticVisuals(playerId, definition);
        return true;
    }

    private void refreshCosmeticVisuals(UUID playerId, CosmeticDefinition definition) {
        if (playerId == null || definition == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (definition.category == CosmeticCategory.HATS) {
            refreshHeadCosmetic(player);
            return;
        }
        if (definition.category == CosmeticCategory.LEGENDARY_SKINS) {
            refreshLegendarySkins(player);
        }
        if (definition.category == CosmeticCategory.ARMOR_TRIMS) {
            refreshArmorTrimCosmetic(player);
        }
    }

    public String getHeadCosmeticDisplayName(String cosmeticId) {
        HeadCosmetic cosmetic = findHeadCosmetic(cosmeticId);
        return cosmetic.isNone() ? null : cosmetic.displayName;
    }

    public boolean isAuctionSellableHeadCosmetic(String cosmeticId) {
        HeadCosmetic cosmetic = findHeadCosmetic(cosmeticId);
        if (cosmetic.isNone() || isManualGrantOnlyHeadCosmetic(cosmetic.id)) {
            return false;
        }
        return cosmetic.isLegendarySkin() || hasCustomHatModel(cosmetic);
    }

    public boolean isManualGrantOnlyHeadCosmetic(String cosmeticId) {
        return SPINNING_TOP_HAT_ID.equals(normalizeLegacyCosmeticId(cosmeticId));
    }

    public boolean isAuctionSellableCosmetic(String cosmeticId) {
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        if (definition == null || definition.category == CosmeticCategory.RANK_STYLES) {
            return false;
        }
        if (definition.category == CosmeticCategory.HATS) {
            return isAuctionSellableHeadCosmetic(cosmeticId);
        }
        return !definition.hidden;
    }

    public String getCosmeticDisplayName(String cosmeticId) {
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        return definition == null ? null : definition.displayName;
    }

    public String getCosmeticAuctionCategoryId(String cosmeticId) {
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        return definition == null || definition.category == null ? null : definition.category.id;
    }

    public String getCosmeticRarity(String cosmeticId) {
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        return definition == null ? null : definition.rarity;
    }

    public ItemStack createInvisibleGuiTarget(String name, String... lore) {
        return iconLoreTarget(GUI_MODEL_CLICK_TARGET, name, "noop", lore);
    }

    public List<String> getAllHeadCosmeticIds() {
        List<String> ids = new ArrayList<>();
        for (HeadCosmetic cosmetic : headCosmeticMenuOrder) {
            ids.add(cosmetic.id);
        }
        return ids;
    }

    public String resolveHeadCosmeticId(String cosmeticId) {
        String normalized = normalizeLegacyCosmeticId(cosmeticId);
        if (HeadCosmetic.NONE.id.equals(normalized) || !headCosmeticRegistry.containsKey(normalized)) {
            return null;
        }
        return normalized;
    }

    public ItemStack createHeadCosmeticAuctionIcon(String cosmeticId) {
        HeadCosmetic cosmetic = findHeadCosmetic(cosmeticId);
        if (cosmetic.isNone()) {
            return null;
        }
        ItemStack stack = new ItemStack(cosmetic.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.GOLD + cosmetic.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Hydra Cosmetic");
        lore.add(ChatColor.DARK_GRAY + "Category: " + cosmetic.category);
        lore.add(ChatColor.DARK_GRAY + "Rarity: " + cosmetic.rarity);
        meta.setLore(lore);
        NamespacedKey itemModelKey = parseItemModelKey(cosmetic.itemModel);
        if (itemModelKey != null) {
            meta.setItemModel(itemModelKey);
        }
        if (itemModelKey == null && cosmetic.customModelData > 0) {
            meta.setCustomModelData(cosmetic.customModelData);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createCosmeticAuctionIcon(String cosmeticId) {
        CosmeticDefinition definition = findCosmeticDefinitionById(cosmeticId);
        if (definition == null || definition.category == CosmeticCategory.RANK_STYLES) {
            return null;
        }
        ItemStack stack = new ItemStack(definition.material == null ? Material.PRISMARINE_CRYSTALS : definition.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(rarityColor(definition.rarity) + definition.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Hydra Cosmetic");
        lore.add(ChatColor.DARK_GRAY + "Category: " + definition.category.displayName);
        lore.add(ChatColor.DARK_GRAY + "Rarity: " + rarityDisplayName(definition.rarity));
        if (definition.description != null && !definition.description.isBlank()) {
            lore.add("");
            lore.add(ChatColor.GRAY + definition.description);
        }
        meta.setLore(lore);
        NamespacedKey itemModelKey = parseItemModelKey(definition.itemModel);
        if (itemModelKey != null) {
            meta.setItemModel(itemModelKey);
        }
        if (itemModelKey == null && definition.customModelData > 0) {
            meta.setCustomModelData(definition.customModelData);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean removeHeadCosmeticOwnership(UUID playerId, String cosmeticId) {
        if (playerId == null) {
            return false;
        }
        String normalized = normalizeLegacyCosmeticId(cosmeticId);
        if (HeadCosmetic.NONE.id.equals(normalized)) {
            return false;
        }
        if (!headCosmeticRegistry.containsKey(normalized)) {
            return false;
        }

        if (!cosmeticService.revokeOwnership(playerId, normalized)) {
            return false;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            refreshHeadCosmetic(player);
            refreshLegendarySkins(player);
        }
        return true;
    }

    public boolean addHeadCosmeticOwnership(UUID playerId, String cosmeticId) {
        if (playerId == null) {
            return false;
        }
        String normalized = normalizeLegacyCosmeticId(cosmeticId);
        if (HeadCosmetic.NONE.id.equals(normalized) || !headCosmeticRegistry.containsKey(normalized)) {
            return false;
        }
        if (ownsHeadCosmetic(playerId, normalized)) {
            return true;
        }
        if (!cosmeticService.grantOwnership(playerId, normalized)) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            refreshHeadCosmetic(player);
        }
        return true;
    }

    public boolean removeCosmeticOwnershipForAuction(UUID playerId, String cosmeticId) {
        return removeCosmeticOwnership(playerId, cosmeticId);
    }

    public void openPlayerSocialMenu(Player player) {
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Social", "socials");
        player.openInventory(inv);
    }

    public void openPlayerPartyMenu(Player player) {
        UUID playerId = player.getUniqueId();
        boolean inParty = partyService.isInParty(playerId);
        boolean leader = partyService.isLeader(playerId);
        UUID leaderId = inParty ? partyService.getLeader(playerId) : null;
        String theme = inParty ? "party_leader" : "party_member";
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Party", theme);

        if (inParty) {
            List<UUID> members = sortedPartyMembers(playerId);
            int[] memberSlots = {28, 30, 32, 34};
            for (int i = 0; i < members.size() && i < memberSlots.length; i++) {
                UUID memberId = members.get(i);
                boolean memberLeader = memberId.equals(leaderId);
                boolean self = memberId.equals(playerId);
                String status = isOnline(memberId) ? ChatColor.GREEN + "Online" : ChatColor.GRAY + "Offline";
                String roleLine = memberLeader ? ChatColor.GOLD + "* Leader" : ChatColor.GRAY + "Member";
                String clickLine = leader && !self
                    ? ChatColor.YELLOW + "Click to manage"
                    : ChatColor.GRAY + "Click to view";
                inv.setItem(memberSlots[i], playerHeadButton(
                    memberId,
                    (memberLeader ? ChatColor.GOLD : MAIN_TITLE_COLOR) + partyService.displayName(memberId),
                    "open_player_party_member_actions:" + memberId,
                    status,
                    roleLine,
                    self ? ChatColor.AQUA + "You" : clickLine
                ));
            }
        }
        PartyService.PartyVisibility visibility = partyService.getVisibility(playerId);
        String visibilityName = (visibility == PartyService.PartyVisibility.PUBLIC ? ChatColor.GREEN : ChatColor.RED)
            + (visibility == PartyService.PartyVisibility.PUBLIC ? "Public Party" : "Private Party");
        String visibilityDescription = visibility == PartyService.PartyVisibility.PUBLIC
            ? ChatColor.GRAY + "Players can join without an invite."
            : ChatColor.GRAY + "Players must be invited to join.";
        if (!inParty) {
            inv.setItem(48, iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                ChatColor.GREEN + "Create Party",
                "player_party_create",
                ChatColor.GRAY + "Create a new private party."
            ));
            inv.setItem(49, iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                ChatColor.GRAY + "No Active Party",
                "noop",
                ChatColor.DARK_GRAY + "Create a party or invite a friend."
            ));
            inv.setItem(50, iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                ChatColor.AQUA + "Invite Friends",
                "open_player_party_invite_friends",
                ChatColor.GRAY + "Choose an online friend to invite.",
                ChatColor.DARK_GRAY + "A party is created when you send the invite."
            ));
        } else {
            inv.setItem(48, iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                leader ? ChatColor.RED + "Disband Party" : ChatColor.RED + "Leave Party",
                leader ? "player_party_disband" : "player_party_leave",
                leader ? ChatColor.GRAY + "Disband this party." : ChatColor.GRAY + "Leave this party."
            ));
            inv.setItem(49, iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                visibilityName,
                leader ? "player_party_visibility_toggle" : "noop",
                visibilityDescription,
                leader
                    ? ChatColor.YELLOW + "Click to make "
                        + (visibility == PartyService.PartyVisibility.PUBLIC ? "Private." : "Public.")
                    : ChatColor.DARK_GRAY + "Only the party leader can change this."
            ));
            inv.setItem(50, iconLoreTarget(
                GUI_MODEL_CLICK_TARGET,
                ChatColor.AQUA + "Invite Friends",
                leader ? "open_player_party_invite_friends" : "noop",
                leader ? ChatColor.GRAY + "Choose an online friend to invite."
                    : ChatColor.DARK_GRAY + "Only the party leader can send invites."
            ));
        }
        player.openInventory(inv);
    }

    public void openPlayerPartyInviteFriendsMenu(Player player) {
        UUID playerId = player.getUniqueId();
        if (partyService.isInParty(playerId) && !partyService.isLeader(playerId)) {
            Inventory lockedInv = menuInventory(27, MAIN_TITLE_COLOR + "Party: Invite");
            frame(lockedInv);
            lockedInv.setItem(13, button(
                Material.BARRIER,
                ChatColor.GRAY + "Leader Only",
                "noop",
                ChatColor.DARK_GRAY + "Only the party leader can send invites."
            ));
            lockedInv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_player_party"));
            player.openInventory(lockedInv);
            return;
        }

        List<UUID> ids = friendService.getFriends(player.getUniqueId());
        ids.sort(Comparator.comparing(friendService::displayName, String.CASE_INSENSITIVE_ORDER));

        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Friends", "friends_all");
        inv.setItem(10, iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.AQUA + "All Friends", "noop"));
        inv.setItem(11, iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.GREEN + "Friend Requests", "friends_page:requests:0"));
        inv.setItem(12, iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.RED + "Blocked Players", "friends_page:blocked:0"));

        int[] slots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int placed = 0;
        for (UUID id : ids) {
            if (placed >= slots.length) {
                break;
            }
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            if (partyService.isInParty(playerId) && partyService.getPartyMembers(playerId).contains(id)) {
                continue;
            }
            inv.setItem(slots[placed], playerHeadButton(
                id,
                MAIN_TITLE_COLOR + target.getName(),
                "player_party_invite:" + id,
                ChatColor.GREEN + "Click to invite"
            ));
            placed++;
        }

        if (placed == 0) {
            inv.setItem(31, button(
                Material.BARRIER,
                ChatColor.GRAY + "No friends available",
                "noop",
                ChatColor.DARK_GRAY + "Only online friends can be invited."
            ));
        }
        player.openInventory(inv);
    }

    public void openPlayerPartyRequestsMenu(Player player) {
        List<UUID> ids = partyService.getIncomingInvites(player.getUniqueId());
        ids.sort(Comparator.comparing(partyService::displayName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(ids.size());
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Party Invites");
        frame(inv);

        if (ids.isEmpty()) {
            inv.setItem(size == 27 ? 13 : 22, button(
                Material.GRAY_DYE,
                ChatColor.GRAY + "No pending invites",
                "noop",
                ChatColor.DARK_GRAY + "New party invites will appear here."
            ));
        } else {
            List<Integer> slots = actionSlots(size);
            for (int i = 0; i < ids.size() && i < slots.size(); i++) {
                UUID inviterId = ids.get(i);
                inv.setItem(slots.get(i), playerHeadButton(
                    inviterId,
                    ChatColor.GOLD + partyService.displayName(inviterId),
                    "open_player_party_request_actions:" + inviterId,
                    ChatColor.GRAY + "Click to respond"
                ));
            }
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_player_party"));
        player.openInventory(inv);
    }

    public void openPlayerPartyRequestActionsMenu(Player player, UUID inviterId) {
        String inviterName = partyService.displayName(inviterId);
        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Party Invite");
        frame(inv);
        inv.setItem(11, button(Material.LIME_DYE, ChatColor.GREEN + "Accept " + inviterName, "player_party_accept:" + inviterId));
        inv.setItem(15, button(Material.RED_DYE, ChatColor.RED + "Deny " + inviterName, "player_party_deny:" + inviterId));
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_player_party_requests"));
        player.openInventory(inv);
    }

    public void openPlayerPartyMembersMenu(Player player) {
        openPlayerPartyMenu(player);
    }

    public void openPlayerPartyMemberActionsMenu(Player player, UUID memberId) {
        UUID playerId = player.getUniqueId();
        List<UUID> members = partyService.getPartyMembers(playerId);
        if (!members.contains(memberId)) {
            openPlayerPartyMenu(player);
            return;
        }

        boolean leader = partyService.isLeader(playerId);
        UUID leaderId = partyService.getLeader(playerId);
        boolean self = playerId.equals(memberId);
        boolean memberLeader = memberId.equals(leaderId);

        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Party Member");
        frame(inv);
        inv.setItem(4, playerHeadButton(
            memberId,
            (memberLeader ? ChatColor.GOLD : MAIN_TITLE_COLOR) + partyService.displayName(memberId),
            "noop",
            isOnline(memberId) ? ChatColor.GREEN + "Online" : ChatColor.GRAY + "Offline",
            memberLeader ? ChatColor.GOLD + "* Leader" : ChatColor.GRAY + "Member",
            self ? ChatColor.AQUA + "This is you." : ChatColor.GRAY + "Party member"
        ));

        if (leader && !self) {
            inv.setItem(11, button(
                Material.GOLD_NUGGET,
                ChatColor.GOLD + "Promote to Leader",
                "player_party_promote:" + memberId,
                ChatColor.GRAY + "Transfer party leadership."
            ));
            inv.setItem(15, button(
                Material.RED_DYE,
                ChatColor.RED + "Kick From Party",
                "player_party_kick:" + memberId,
                ChatColor.GRAY + "Remove this player from the party."
            ));
        } else {
            String message = self
                ? (memberLeader ? ChatColor.GOLD + "You lead this party." : ChatColor.GRAY + "You are a party member.")
                : ChatColor.GRAY + "Only the leader can manage members.";
            inv.setItem(13, button(Material.NAME_TAG, ChatColor.YELLOW + "Member Info", "noop", message));
        }

        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_player_party_menu"));
        player.openInventory(inv);
    }

    public void openPlayerPartyDetailsMenu(Player player) {
        UUID playerId = player.getUniqueId();
        boolean inParty = partyService.isInParty(playerId);
        boolean leader = partyService.isLeader(playerId);
        UUID leaderId = inParty ? partyService.getLeader(playerId) : null;

        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Party Details");
        frame(inv);
        inv.setItem(4, button(
            Material.COMPASS,
            ChatColor.YELLOW + "Party Details",
            "noop",
            ChatColor.GRAY + "Quick status and queue notes."
        ));

        if (!inParty) {
            inv.setItem(13, button(
                Material.BARRIER,
                ChatColor.GRAY + "No Active Party",
                "noop",
                ChatColor.DARK_GRAY + "Create a party or accept an invite first."
            ));
        } else {
            inv.setItem(10, button(
                Material.NAME_TAG,
                ChatColor.GOLD + "Party Size",
                "noop",
                ChatColor.GRAY + "Members: " + partyService.getPartySize(playerId)
                    + "/" + partyService.getMaxPartySize()
            ));
            inv.setItem(12, button(
                Material.GOLD_NUGGET,
                ChatColor.GOLD + "Your Role",
                "noop",
                leader ? ChatColor.GOLD + "Leader" : ChatColor.GRAY + "Member"
            ));
            inv.setItem(14, playerHeadButton(
                leaderId,
                ChatColor.GOLD + "Leader",
                "noop",
                ChatColor.WHITE + partyService.displayName(leaderId)
            ));
            inv.setItem(16, button(
                Material.COMPASS,
                ChatColor.AQUA + "Queue Sync",
                "noop",
                ChatColor.GRAY + "Duo queue follows the party leader",
                ChatColor.GRAY + "and uses both party members."
            ));
        }

        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_player_party_menu"));
        player.openInventory(inv);
    }

    public void openPlayerFriendsMenu(Player player) {
        openPlayerFriendsPage(player, FriendsView.ALL, 0);
    }

    private void openPlayerFriendsPage(Player player, FriendsView view, int requestedPage) {
        List<UUID> ids = switch (view) {
            case ALL -> friendService.getFriends(player.getUniqueId());
            case REQUESTS -> friendService.getIncomingRequests(player.getUniqueId());
            case BLOCKED -> friendService.getBlockedPlayers(player.getUniqueId());
        };
        ids.sort(Comparator.comparing(friendService::displayName, String.CASE_INSENSITIVE_ORDER));
        int pageSize = 21;
        int maxPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        String theme = switch (view) {
            case ALL -> "friends_all";
            case REQUESTS -> "friends_requests";
            case BLOCKED -> "friends_blocked";
        };
        Inventory inv = themedMenuInventory(54, MAIN_TITLE_COLOR + "Friends", theme);
        inv.setItem(10, iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.AQUA + "All Friends", "friends_page:all:0"));
        inv.setItem(11, iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.GREEN + "Friend Requests", "friends_page:requests:0"));
        inv.setItem(12, iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.RED + "Blocked Players", "friends_page:blocked:0"));
        if (page > 0) {
            ItemStack previous = iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.YELLOW + "Previous Page",
                "friends_page:" + view.id + ":" + (page - 1));
            inv.setItem(18, previous);
            inv.setItem(27, previous.clone());
        }
        if (page < maxPage) {
            ItemStack next = iconLoreTarget(GUI_MODEL_CLICK_TARGET, ChatColor.YELLOW + "Next Page",
                "friends_page:" + view.id + ":" + (page + 1));
            inv.setItem(26, next);
            inv.setItem(35, next.clone());
        }
        int[] slots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int start = page * pageSize;
        for (int index = start; index < ids.size() && index < start + pageSize; index++) {
            UUID id = ids.get(index);
            String action = switch (view) {
                case ALL -> "player_friends_view:" + id;
                case REQUESTS -> "open_player_friend_request_actions:" + id;
                case BLOCKED -> "player_friends_unblock:" + id;
            };
            String status = view == FriendsView.BLOCKED ? ChatColor.RED + "Click to unblock"
                : Bukkit.getPlayer(id) != null ? ChatColor.GREEN + "Online" : ChatColor.GRAY + "Offline";
            inv.setItem(slots[index - start], playerHeadButton(id, MAIN_TITLE_COLOR + friendService.displayName(id), action, status));
        }
        player.openInventory(inv);
    }

    public void openPlayerFriendsListMenu(Player player) {
        openPlayerFriendsPage(player, FriendsView.ALL, 0);
    }

    private void openLegacyPlayerFriendsListMenu(Player player) {
        List<UUID> ids = friendService.getFriends(player.getUniqueId());
        ids.sort(Comparator.comparing(friendService::displayName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(ids.size());
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Friends: My List");
        frame(inv);

        if (ids.isEmpty()) {
            inv.setItem(size == 27 ? 13 : 22, button(Material.PAPER, ChatColor.GRAY + "No friends yet", "noop"));
        } else {
            List<Integer> slots = actionSlots(size);
            for (int i = 0; i < ids.size() && i < slots.size(); i++) {
                UUID id = ids.get(i);
                Player online = Bukkit.getPlayer(id);
                String status = online != null && online.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.GRAY + "Offline";
                inv.setItem(slots.get(i), playerHeadButton(
                    id,
                    MAIN_TITLE_COLOR + friendService.displayName(id),
                    "player_friends_view:" + id,
                    status
                ));
            }
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_player_friends"));
        player.openInventory(inv);
    }

    public void openPlayerFriendRequestsMenu(Player player) {
        openPlayerFriendsPage(player, FriendsView.REQUESTS, 0);
    }

    private void openLegacyPlayerFriendRequestsMenu(Player player) {
        List<UUID> ids = friendService.getIncomingRequests(player.getUniqueId());
        ids.sort(Comparator.comparing(friendService::displayName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(ids.size());
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Friends: Requests");
        frame(inv);

        if (ids.isEmpty()) {
            inv.setItem(size == 27 ? 13 : 22, button(Material.GRAY_DYE, ChatColor.GRAY + "No pending requests", "noop"));
        } else {
            List<Integer> slots = actionSlots(size);
            for (int i = 0; i < ids.size() && i < slots.size(); i++) {
                UUID id = ids.get(i);
                String name = friendService.displayName(id);
                inv.setItem(slots.get(i), playerHeadButton(
                    id,
                    ChatColor.GOLD + name,
                    "open_player_friend_request_actions:" + id,
                    "Open request actions"
                ));
            }
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_player_friends"));
        player.openInventory(inv);
    }

    public void openPlayerFriendRequestActionsMenu(Player player, UUID requesterId) {
        String name = friendService.displayName(requesterId);
        Inventory inv = menuInventory(27, MAIN_TITLE_COLOR + "Request: " + name);
        frame(inv);
        inv.setItem(11, button(Material.LIME_DYE, ChatColor.GREEN + "Accept " + name, "player_friends_accept:" + requesterId));
        inv.setItem(15, button(Material.RED_DYE, ChatColor.RED + "Deny " + name, "player_friends_deny:" + requesterId));
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_player_friends_requests"));
        player.openInventory(inv);
    }

    public void openPlayerAddFriendMenu(Player player) {
        List<Player> online = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (friendService.areFriends(player.getUniqueId(), target.getUniqueId())) {
                continue;
            }
            online.add(target);
        }
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(online.size());
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Friends: Add");
        frame(inv);

        if (online.isEmpty()) {
            inv.setItem(size == 27 ? 13 : 22, button(Material.GRAY_DYE, ChatColor.GRAY + "No players available", "noop"));
        } else {
            List<Integer> slots = actionSlots(size);
            for (int i = 0; i < online.size() && i < slots.size(); i++) {
                Player target = online.get(i);
                inv.setItem(slots.get(i), playerHeadButton(
                    target.getUniqueId(),
                    MAIN_TITLE_COLOR + target.getName(),
                    "player_friends_send:" + target.getUniqueId(),
                    ChatColor.GREEN + "Click to send friend request"
                ));
            }
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_player_friends"));
        player.openInventory(inv);
    }

    public void openPlayerRemoveFriendMenu(Player player) {
        List<UUID> ids = friendService.getFriends(player.getUniqueId());
        ids.sort(Comparator.comparing(friendService::displayName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(ids.size());
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Friends: Remove");
        frame(inv);

        if (ids.isEmpty()) {
            inv.setItem(size == 27 ? 13 : 22, button(Material.GRAY_DYE, ChatColor.GRAY + "No friends to remove", "noop"));
        } else {
            List<Integer> slots = actionSlots(size);
            for (int i = 0; i < ids.size() && i < slots.size(); i++) {
                UUID id = ids.get(i);
                inv.setItem(slots.get(i), playerHeadButton(
                    id,
                    ChatColor.RED + friendService.displayName(id),
                    "player_friends_remove:" + id,
                    ChatColor.YELLOW + "Click to remove friend"
                ));
            }
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_player_friends"));
        player.openInventory(inv);
    }

    public void openLootMenu(Player player) {
        Inventory inv = menuInventory(36, CATEGORY_TITLE_COLOR + LOOT_TITLE);
        frame(inv);

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        LootCategory[] categories = LootCategory.values();
        for (int i = 0; i < categories.length && i < slots.length; i++) {
            LootCategory category = categories[i];
            String action = "open_category:" + category.key();
            inv.setItem(slots[i], button(
                category.icon(),
                MAIN_TITLE_COLOR + category.displayName(),
                action,
                "Open " + category.displayName() + " loot menu"
            ));
        }

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_loot_tools"));
        player.openInventory(inv);
    }

    public void openAuditMenu(Player player) {
        Inventory inv = menuInventory(36, CATEGORY_TITLE_COLOR + AUDIT_TITLE);
        frame(inv);

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        LootCategory[] categories = LootCategory.values();
        for (int i = 0; i < categories.length && i < slots.length; i++) {
            LootCategory category = categories[i];
            String action = "open_audit_category:" + category.key();
            inv.setItem(slots[i], button(
                Material.WRITTEN_BOOK,
                MAIN_TITLE_COLOR + category.displayName(),
                action,
                "Open " + category.displayName() + " audit books"
            ));
        }

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_loot_tools"));
        player.openInventory(inv);
    }

    public void openAuditCategoryMenu(Player player, LootCategory category) {
        int size = category.chestCount() <= 7 ? 36 : 54;
        Inventory inv = menuInventory(size, CATEGORY_TITLE_COLOR + category.displayName() + " Audits");
        frame(inv);

        List<Integer> slots = actionSlots(size);
        for (int i = 1; i <= category.chestCount() && i <= slots.size(); i++) {
            String action = "audit:" + category.key() + ":" + i;
            inv.setItem(slots.get(i - 1), button(
                Material.WRITTEN_BOOK,
                category.color() + category.displayName() + " " + i,
                action,
                "Get audit book for chest " + i
            ));
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_audit"));
        player.openInventory(inv);
    }

    public void openAdminMenu(Player player) {
        openMainMenu(player);
    }

    public void openAdminMatchToolsMenu(Player player) {
        Inventory inv = menuInventory(36, MATCH_COLOR + "Hydra Match Tools");
        frame(inv);
        boolean autoWinEnabled = isAutoWinTriggerEnabled();
        inv.setItem(10, button(Material.TOTEM_OF_UNDYING, WARNING_COLOR + "Trigger Win", "admin_trigger_win_check"));
        inv.setItem(11, button(Material.COMPASS, STRUCTURE_COLOR + "Preview Match Layout", "struct_preview"));
        inv.setItem(12, button(Material.COMPASS, MAIN_TITLE_COLOR + "Give Hub Hotbar", "admin_give_player_compass"));
        inv.setItem(13, button(Material.CLOCK, MAIN_TITLE_COLOR + "Refill Nearby Loot", "admin_refill:20"));
        inv.setItem(14, button(Material.PAPER, MAIN_TITLE_COLOR + "Queue Status", "admin_queue_status"));
        inv.setItem(15, button(Material.LIME_WOOL, MATCH_COLOR + "Force Start BR Solo", "admin_force_start_br_solo"));
        inv.setItem(16, button(Material.CYAN_WOOL, MATCH_COLOR + "Force Start BR Duo", "admin_force_start_br_duo"));
        inv.setItem(17, button(Material.GOLD_BLOCK, MATCH_COLOR + "Force Start HG", "admin_force_start_hg"));
        inv.setItem(25, button(Material.BARRIER, WARNING_COLOR + "Clear Queue", "admin_clear_queue"));
        inv.setItem(
            19,
            guiButton(
                autoWinEnabled ? GUI_MODEL_TOGGLE_ON : GUI_MODEL_TOGGLE_OFF,
                (autoWinEnabled ? ChatColor.GREEN : ChatColor.RED) + "Auto Win Trigger",
                "admin_auto_win_toggle",
                ChatColor.GRAY + "Click to " + (autoWinEnabled ? ChatColor.RED + "disable" : ChatColor.GREEN + "enable") + ChatColor.GRAY + "."
            )
        );
        inv.setItem(20, button(Material.COMPARATOR, ChatColor.YELLOW + "Settings", "open_player_settings_menu"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminBattleRoyaleToolsMenu(Player player) {
        Inventory inv = menuInventory(36, MATCH_COLOR + "Battle Royale Admin");
        frame(inv);
        boolean autoWinEnabled = isAutoWinTriggerEnabled();
        inv.setItem(10, button(Material.PAPER, MAIN_TITLE_COLOR + "Queue Status", "admin_queue_status_br"));
        inv.setItem(11, button(Material.LIME_WOOL, MATCH_COLOR + "Force Start Solo", "admin_force_start_br_solo"));
        inv.setItem(12, button(Material.CYAN_WOOL, MATCH_COLOR + "Force Start Duo", "admin_force_start_br_duo"));
        inv.setItem(13, button(Material.BARRIER, WARNING_COLOR + "Clear BR Queues", "admin_clear_br_queue"));
        inv.setItem(
            14,
            guiButton(
                autoWinEnabled ? GUI_MODEL_TOGGLE_ON : GUI_MODEL_TOGGLE_OFF,
                (autoWinEnabled ? ChatColor.GREEN : ChatColor.RED) + "Auto Win Trigger",
                "admin_auto_win_toggle_br",
                ChatColor.GRAY + "Click to " + (autoWinEnabled ? ChatColor.RED + "disable" : ChatColor.GREEN + "enable") + ChatColor.GRAY + "."
            )
        );
        inv.setItem(15, button(Material.COMPASS, STRUCTURE_COLOR + "Preview Match Layout", "struct_preview"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminHungerGamesToolsMenu(Player player) {
        Inventory inv = menuInventory(36, MATCH_COLOR + "Hunger Games Admin");
        frame(inv);
        inv.setItem(10, button(Material.PAPER, MAIN_TITLE_COLOR + "Queue Status", "admin_queue_status_hg"));
        inv.setItem(11, button(Material.GOLD_BLOCK, MATCH_COLOR + "Force Start HG", "admin_force_start_hg"));
        inv.setItem(12, button(Material.BARRIER, WARNING_COLOR + "Clear HG Queue", "admin_clear_hg_queue"));
        inv.setItem(13, button(Material.CLOCK, MAIN_TITLE_COLOR + "Refill Nearby Loot", "admin_refill:20"));
        inv.setItem(14, button(Material.COMPASS, STRUCTURE_COLOR + "Preview Match Layout", "struct_preview"));
        inv.setItem(15, button(Material.COMPARATOR, ChatColor.YELLOW + "Settings", "open_player_settings_menu"));
        inv.setItem(16, button(Material.CRAFTING_TABLE, MATCH_COLOR + "HG Utilities", "open_admin_hg_utilities"));
        boolean outcoveActive = isHungerGamesMapActive("outcove");
        boolean wildWestActive = isHungerGamesMapActive("wild_west");
        boolean aztecJungleActive = isHungerGamesMapActive("aztec_jungle");
        boolean randomActive = isHungerGamesMapActive("random");
        inv.setItem(19, button(
            outcoveActive ? Material.LIME_CONCRETE : Material.TUFF_BRICKS,
            (outcoveActive ? ChatColor.GREEN : MATCH_COLOR) + "Outcove",
            "admin_hg_map_outcove"
        ));
        inv.setItem(20, button(
            wildWestActive ? Material.LIME_CONCRETE : Material.DEAD_BUSH,
            (wildWestActive ? ChatColor.GREEN : MATCH_COLOR) + "Wild West",
            "admin_hg_map_wild_west"
        ));
        inv.setItem(21, button(
            aztecJungleActive ? Material.LIME_CONCRETE : Material.JUNGLE_SAPLING,
            (aztecJungleActive ? ChatColor.GREEN : MATCH_COLOR) + "Aztec Jungle",
            "admin_hg_map_aztec_jungle"
        ));
        inv.setItem(22, button(
            randomActive ? Material.LIME_CONCRETE : Material.AMETHYST_CLUSTER,
            (randomActive ? ChatColor.GREEN : MATCH_COLOR) + "Random",
            "admin_hg_map_random"
        ));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminHungerGamesUtilitiesMenu(Player player) {
        if (!player.hasPermission("hgloot.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use HG admin tools.");
            return;
        }
        RecipeBookRegistry registry = hungerGamesUtilityRegistry.get();
        if (registry == null) {
            player.sendMessage(ChatColor.RED + "HG utilities are currently unavailable.");
            return;
        }
        Inventory inv = menuInventory(54, MATCH_COLOR + "HG Utilities");
        frame(inv);
        int index = 0;
        for (RecipeBookRegistry.VisibleRecipe recipe : registry.visibleRecipes(RecipeMode.HUNGER_GAMES, RecipeCategory.BASIC)) {
            if (!(recipe.view() instanceof AvailableView view)) continue;
            int slot = 10 + (index / 7) * 9 + index % 7;
            if (slot >= 44) break;
            ItemStack display = view.result().createItem();
            ItemMeta meta = display.getItemMeta();
            meta.displayName(view.presentation().title());
            List<Component> lore = new ArrayList<>(view.presentation().notes());
            lore.add(Component.text("Left-click to receive.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                HG_UTILITY_GIVE_ACTION_PREFIX + recipe.id().getKey());
            display.setItemMeta(meta);
            inv.setItem(slot, display);
            index++;
        }
        inv.setItem(49, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_admin_hg_tools"));
        player.openInventory(inv);
    }

    public void openAdminHungerGamesStartMenu(Player player) {
        if (!player.hasPermission("hgloot.admin")) return;
        QueueService queues = queueServiceOrNull();
        if (queues == null) return;
        Inventory inv = menuInventory(27, MATCH_COLOR + "Start Local HG");
        frame(inv);
        for (boolean duos : new boolean[]{false, true}) {
            List<String> ids = queues.hungerGamesLocalLobbyIds(duos);
            String mode = duos ? "Duos" : "Solos";
            String id = ids.isEmpty() ? null : ids.getFirst();
            inv.setItem(duos ? 15 : 11, button(duos ? Material.DIAMOND_SWORD : Material.IRON_SWORD,
                MATCH_COLOR + "HG " + mode,
                id == null ? "noop" : "admin_hg_start_local:" + (duos ? "duos:" : "solos:") + id,
                ChatColor.GRAY + (id == null ? "Queue empty" : "Local lobby: " + id)));
        }
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_admin_hg_tools"));
        player.openInventory(inv);
    }

    public void openAdminBossRushToolsMenu(Player player) {
        if (!player.hasPermission("hgloot.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use Boss Rush admin tools.");
            return;
        }
        Inventory inv = menuInventory(27, COMBAT_COLOR + "Boss Rush Admin");
        frame(inv);
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    private void grantHungerGamesUtility(Player player, String recipeId) {
        if (!player.hasPermission("hgloot.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use HG admin tools.");
            return;
        }
        RecipeBookRegistry registry = hungerGamesUtilityRegistry.get();
        if (registry == null) return;
        registry.visibleRecipes(RecipeMode.HUNGER_GAMES, RecipeCategory.BASIC).stream()
            .filter(recipe -> recipe.id().getKey().equals(recipeId))
            .filter(recipe -> recipe.view() instanceof AvailableView)
            .findFirst().ifPresent(recipe -> {
                AvailableView view = (AvailableView) recipe.view();
                ItemStack item = QueueService.hungerGamesUtilityCraftResult(view.result().createItem(), recipeId);
                for (ItemStack overflow : player.getInventory().addItem(item).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
                player.sendMessage(Component.text("Given: ", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .append(view.presentation().title()));
            });
    }

    public void openAdminMinigamesToolsMenu(Player player) {
        if (!canUseSpleefAdminMenus(player)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use minigame admin tools.");
            return;
        }
        Inventory inv = menuInventory(45, MAIN_TITLE_COLOR + "Minigames Admin");
        frame(inv);
        inv.setItem(11, button(Material.SNOW_BLOCK, ChatColor.AQUA + "Spleef", "open_admin_spleef_tools"));
        inv.setItem(13, button(Material.MACE, ChatColor.LIGHT_PURPLE + "Void Roulette", "open_admin_voidroulette_tools"));
        inv.setItem(15, button(Material.LIGHT_BLUE_TERRACOTTA, ChatColor.AQUA + "Bridge", "open_admin_bridge_tools"));
        inv.setItem(22, button(Material.TRIAL_SPAWNER, COMBAT_COLOR + "Boss Rush", "open_admin_bossrush_tools"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminVoidRouletteToolsMenu(Player player) {
        if (!canUseSpleefAdminMenus(player)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use minigame admin tools.");
            return;
        }
        Inventory inv = menuInventory(27, ChatColor.LIGHT_PURPLE + "Void Roulette Admin");
        frame(inv);
        inv.setItem(10, button(Material.PAPER, MAIN_TITLE_COLOR + "Status", "admin_voidroulette_status"));
        inv.setItem(11, button(Material.LIME_WOOL, MATCH_COLOR + "Force Start", "admin_voidroulette_forcestart"));
        inv.setItem(12, button(Material.BARRIER, WARNING_COLOR + "Stop Match", "admin_voidroulette_stop"));
        boolean testMode = plugin instanceof HGLootPlugin hydra
            && hydra.voidRouletteService() != null
            && hydra.voidRouletteService().isTestModeEnabled();
        inv.setItem(13, button(Material.COMPARATOR,
            (testMode ? ChatColor.GREEN : ChatColor.YELLOW) + "Test Mode " + (testMode ? "ON" : "OFF"),
            "open_admin_voidroulette_test",
            ChatColor.GRAY + "Experimental events and endgame controls."));
        inv.setItem(14, button(Material.SPONGE, MAIN_TITLE_COLOR + "Cleanup Status", "admin_voidroulette_cleanup_status"));
        inv.setItem(15, button(Material.LIME_DYE, ChatColor.GREEN + "Auto Cleanup ON", "admin_voidroulette_cleanup_on"));
        inv.setItem(16, button(Material.GRAY_DYE, ChatColor.RED + "Auto Cleanup OFF", "admin_voidroulette_cleanup_off"));
        inv.setItem(21, button(Material.TNT, WARNING_COLOR + "Purge Worlds", "admin_voidroulette_cleanup_purge",
            ChatColor.GRAY + "Delete leftover Void Roulette worlds."));
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_minigames"));
        player.openInventory(inv);
    }

    public void openAdminBridgeToolsMenu(Player player) {
        if (!canUseSpleefAdminMenus(player)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use minigame admin tools.");
            return;
        }
        boolean randomActive = bridgeService != null && bridgeService.isMapSelected("random");
        boolean bridgeActive = bridgeService != null && bridgeService.isMapSelected("bridge");
        boolean oceanActive = bridgeService != null && bridgeService.isMapSelected("bridge_ocean");
        boolean outerEndActive = bridgeService != null && bridgeService.isMapSelected("outer_end");
        Inventory inv = menuInventory(36, ChatColor.AQUA + "Bridge Admin");
        frame(inv);
        inv.setItem(10, button(Material.PAPER, MAIN_TITLE_COLOR + "Status", "admin_bridge_status",
            ChatColor.GRAY + "Shows queue, match, and auto-win state."));
        inv.setItem(11, button(Material.LIME_WOOL, MATCH_COLOR + "Force Start", "admin_bridge_forcestart"));
        inv.setItem(12, button(Material.BARRIER, WARNING_COLOR + "Stop Match", "admin_bridge_stop"));
        inv.setItem(14, button(Material.COMPARATOR, ChatColor.YELLOW + "Toggle Auto Win", "admin_bridge_autowin_toggle",
            ChatColor.GRAY + "Current state shows in Status."));
        inv.setItem(15, button(Material.LIME_DYE, ChatColor.GREEN + "Auto Cleanup ON", "admin_bridge_cleanup_on"));
        inv.setItem(16, button(Material.GRAY_DYE, ChatColor.RED + "Auto Cleanup OFF", "admin_bridge_cleanup_off"));
        inv.setItem(19, button(randomActive ? Material.LIME_CONCRETE : Material.AMETHYST_CLUSTER, (randomActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Random Map", "admin_bridge_map_random"));
        inv.setItem(20, button(bridgeActive ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_TERRACOTTA, (bridgeActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Bridge", "admin_bridge_map_bridge"));
        inv.setItem(21, button(oceanActive ? Material.LIME_CONCRETE : Material.PRISMARINE, (oceanActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Bridge Ocean", "admin_bridge_map_bridge_ocean"));
        inv.setItem(22, button(outerEndActive ? Material.LIME_CONCRETE : Material.END_STONE,
            (outerEndActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Outer End", "admin_bridge_map_outer_end"));
        inv.setItem(29, button(Material.TNT, WARNING_COLOR + "Purge Worlds", "admin_bridge_cleanup_purge",
            ChatColor.GRAY + "Delete leftover Bridge worlds."));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_minigames"));
        player.openInventory(inv);
    }

    public void openAdminVoidRouletteTestMenu(Player player) {
        if (!canUseSpleefAdminMenus(player)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use minigame admin tools.");
            return;
        }
        boolean enabled = plugin instanceof HGLootPlugin hydra
            && hydra.voidRouletteService() != null
            && hydra.voidRouletteService().isTestModeEnabled();
        boolean infiniteLives = plugin instanceof HGLootPlugin hydra
            && hydra.voidRouletteService() != null
            && hydra.voidRouletteService().isInfiniteLivesEnabled();
        Inventory inv = menuInventory(36, ChatColor.LIGHT_PURPLE + "Void Roulette Test Mode");
        frame(inv);
        inv.setItem(10, button(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (enabled ? ChatColor.GREEN : ChatColor.RED) + "Test Mode " + (enabled ? "ON" : "OFF"),
            "admin_voidroulette_test_toggle",
            ChatColor.GRAY + "Experimental rules reset to OFF after a restart."));
        inv.setItem(11, button(infiniteLives ? Material.TOTEM_OF_UNDYING : Material.SKELETON_SKULL,
            (infiniteLives ? ChatColor.GREEN : ChatColor.YELLOW) + "Infinite Lives "
                + (infiniteLives ? "ON" : "OFF"),
            "admin_voidroulette_test_infinitelives",
            ChatColor.GRAY + "Respawn without consuming lives while testing."));
        inv.setItem(12, button(Material.FEATHER, ChatColor.AQUA + "Crosswind",
            "admin_voidroulette_test_crosswind", ChatColor.GRAY + "Pushes everyone in one direction."));
        inv.setItem(13, button(Material.PHANTOM_MEMBRANE, ChatColor.WHITE + "Low Gravity",
            "admin_voidroulette_test_lowgravity", ChatColor.GRAY + "Higher jumps and slower falling."));
        inv.setItem(14, button(Material.CHEST, ChatColor.GOLD + "Supply Surge",
            "admin_voidroulette_test_supply", ChatColor.GRAY + "Gives 3-10 rapid vanilla drops per player."));
        inv.setItem(15, button(Material.ENDER_PEARL, ChatColor.AQUA + "Swap",
            "admin_voidroulette_test_swap", ChatColor.GRAY + "Shuffles player positions once per match."));
        inv.setItem(21, button(Material.CLOCK, ChatColor.RED + "Sudden Death",
            "admin_voidroulette_test_suddendeath",
            ChatColor.GRAY + "Permanent death, rising void, and a 1x1 border."));
        inv.setItem(24, button(Material.BARRIER, WARNING_COLOR + "Stop Current Event",
            "admin_voidroulette_test_stop"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_voidroulette"));
        player.openInventory(inv);
    }

    public void openAdminSpleefToolsMenu(Player player) {
        if (!canUseSpleefAdminMenus(player)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use Spleef admin tools.");
            return;
        }
        Inventory inv = menuInventory(45, ChatColor.AQUA + "Spleef Admin");
        frame(inv);
        boolean randomActive = spleefService != null && spleefService.isMapSelected("random");
        boolean baseActive = spleefService != null && spleefService.isMapSelected("base");
        boolean ringsActive = spleefService != null && spleefService.isMapSelected("rings");
        boolean fourCornersActive = spleefService != null && spleefService.isMapSelected("fourcorners");
        boolean starsActive = spleefService != null && spleefService.isMapSelected("stars");
        boolean desertClassicActive = spleefService != null && spleefService.isMapSelected("desertclassic");
        boolean desertRingsActive = spleefService != null && spleefService.isMapSelected("desertrings");
        boolean desertFourCornersActive = spleefService != null && spleefService.isMapSelected("desertfourcorners");
        boolean desertStarsActive = spleefService != null && spleefService.isMapSelected("desertstars");
        boolean desertLayersActive = spleefService != null && spleefService.isMapSelected("desertlayers");
        boolean snowyLayersActive = spleefService != null && spleefService.isMapSelected("snowylayers");
        inv.setItem(10, button(Material.PAPER, MAIN_TITLE_COLOR + "Status", "admin_spleef_status"));
        inv.setItem(11, button(Material.LIME_WOOL, MATCH_COLOR + "Force Start", "admin_spleef_forcestart"));
        inv.setItem(12, button(Material.BARRIER, WARNING_COLOR + "Stop Match", "admin_spleef_stop"));
        inv.setItem(14, button(Material.SNOWBALL, MAIN_TITLE_COLOR + "Test Items", "admin_spleef_items"));
        inv.setItem(15, button(Material.HOPPER, WARNING_COLOR + "Clear Queue", "admin_spleef_clearqueue"));
        boolean floorDecay = spleefService == null || spleefService.isFloorDecayEnabled();
        inv.setItem(16, button(
            floorDecay ? Material.TNT : Material.GUNPOWDER,
            (floorDecay ? ChatColor.GREEN : ChatColor.RED) + "Floor Decay: " + (floorDecay ? "ON" : "OFF"),
            "admin_spleef_floordecay",
            ChatColor.GRAY + "Toggle whether the floor decays",
            ChatColor.GRAY + "under players automatically."));
        inv.setItem(19, button(randomActive ? Material.LIME_CONCRETE : Material.AMETHYST_CLUSTER, (randomActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Random", "admin_spleef_map_random"));
        inv.setItem(20, button(baseActive ? Material.LIME_CONCRETE : Material.SNOW_BLOCK, (baseActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Base Spleef", "admin_spleef_map_base"));
        inv.setItem(21, button(ringsActive ? Material.LIME_CONCRETE : Material.PACKED_ICE, (ringsActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Spleef Rings", "admin_spleef_map_rings"));
        inv.setItem(22, button(fourCornersActive ? Material.LIME_CONCRETE : Material.QUARTZ_BLOCK, (fourCornersActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Four Corners", "admin_spleef_map_fourcorners"));
        inv.setItem(23, button(starsActive ? Material.LIME_CONCRETE : Material.NETHER_STAR, (starsActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Stars", "admin_spleef_map_stars"));
        inv.setItem(24, button(desertLayersActive ? Material.LIME_CONCRETE : Material.SANDSTONE, (desertLayersActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Desert Layers", "admin_spleef_map_desertlayers"));
        inv.setItem(28, button(desertClassicActive ? Material.LIME_CONCRETE : Material.SAND, (desertClassicActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Desert Classic", "admin_spleef_map_desertclassic"));
        inv.setItem(29, button(desertRingsActive ? Material.LIME_CONCRETE : Material.SANDSTONE, (desertRingsActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Desert Rings", "admin_spleef_map_desertrings"));
        inv.setItem(30, button(snowyLayersActive ? Material.LIME_CONCRETE : Material.PACKED_ICE, (snowyLayersActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Snowy Layers", "admin_spleef_map_snowylayers"));
        inv.setItem(31, button(desertFourCornersActive ? Material.LIME_CONCRETE : Material.CUT_SANDSTONE, (desertFourCornersActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Desert Four Corners", "admin_spleef_map_desertfourcorners"));
        inv.setItem(32, button(desertStarsActive ? Material.LIME_CONCRETE : Material.NETHER_STAR, (desertStarsActive ? ChatColor.GREEN : MAIN_TITLE_COLOR) + "Desert Stars", "admin_spleef_map_desertstars"));
        inv.setItem(40, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_minigames"));
        player.openInventory(inv);
    }

    public void openAdminWarningsMenu(Player player) {
        if (!player.hasPermission("hgloot.moderation.menu") && !player.hasPermission("hgloot.moderation.warn")
            && !player.hasPermission("hgloot.moderation.reports")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use moderation tools.");
            return;
        }
        Inventory inv = menuInventory(36, ChatColor.YELLOW + "Warnings");
        frame(inv);
        inv.setItem(10, button(Material.PAPER, ChatColor.YELLOW + "View Reports", "admin_reports_list",
            "/reports list"));
        inv.setItem(11, button(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Warn Player", "admin_warn_help",
            "/warn <player> <reason>"));
        inv.setItem(12, button(Material.INK_SAC, ChatColor.YELLOW + "View Warnings", "admin_warnings_help",
            "/warnings <player>"));
        inv.setItem(13, button(Material.BARRIER, ChatColor.RED + "Mute Player", "admin_mute_help",
            "/mute <player> <10m|1h|1d|perm> <reason>"));
        inv.setItem(14, button(Material.MILK_BUCKET, ChatColor.GREEN + "Unmute Player", "admin_unmute_help",
            "/unmute <player>"));
        inv.setItem(15, button(Material.IRON_BOOTS, ChatColor.RED + "Kick Player", "admin_kick_help",
            "/kick <player> [reason]"));
        if (player.hasPermission("hgloot.moderation.reports.clear")) {
            inv.setItem(16, button(Material.LAVA_BUCKET, ChatColor.RED + "Clear Reports", "admin_reports_clear",
                "/reports clear"));
        }
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    private boolean canUseSpleefAdminMenus(Player player) {
        return player != null
            && (player.hasPermission("hgloot.admin")
                || player.hasPermission("hgloot.admin.minigames")
                || player.hasPermission("hgloot.admin.spleef"));
    }

    public void openAdminLootToolsMenu(Player player) {
        Inventory inv = menuInventory(36, LOOT_COLOR + "Hydra Loot Tools");
        frame(inv);
        inv.setItem(10, button(Material.CHEST, LOOT_COLOR + "Loot Categories", "open_loot"));
        inv.setItem(11, button(Material.CHEST, LOOT_COLOR + "Spawn Line", "admin_line:standard"));
        inv.setItem(12, button(Material.CHEST_MINECART, LOOT_COLOR + "Spawn Grid", "admin_grid:standard:1:6"));
        inv.setItem(13, button(Material.CHEST, LOOT_COLOR + "Spawn Radius", "admin_radius:standard:1:20"));
        inv.setItem(14, button(Material.BARREL, LOOT_COLOR + "Refill Nearby Loot", "open_radius_fill_category"));
        inv.setItem(15, button(Material.LIGHT_BLUE_WOOL, MAIN_TITLE_COLOR + "Loot Simulation", "admin_sim:standard:1:100"));
        inv.setItem(16, button(Material.WRITTEN_BOOK, MAIN_TITLE_COLOR + "Audit Books", "open_audit"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openStructureToolsMenu(Player player) {
        Inventory inv = menuInventory(36, STRUCTURE_COLOR + "Hydra Structure Tools");
        frame(inv);
        inv.setItem(10, button(Material.BOOK, MAIN_TITLE_COLOR + "List Structure IDs", "struct_list"));
        inv.setItem(11, button(Material.STRUCTURE_BLOCK, MAIN_TITLE_COLOR + "Spawn Random", "struct_spawn_random"));
        inv.setItem(12, button(Material.CRAFTING_TABLE, MAIN_TITLE_COLOR + "Spawn Grid", "struct_grid"));
        inv.setItem(13, button(Material.AMETHYST_CLUSTER, MAIN_TITLE_COLOR + "Spawn Bundle", "struct_bundle"));
        inv.setItem(14, button(Material.COMPASS, MAIN_TITLE_COLOR + "Preview Match Layout", "struct_preview"));
        inv.setItem(15, button(Material.PAPER, MAIN_TITLE_COLOR + "Validate Markers", "struct_validate"));
        inv.setItem(16, button(Material.BARRIER, WARNING_COLOR + "Clear Markers", "struct_clear"));
        inv.setItem(22, button(Material.OBSERVER, MAIN_TITLE_COLOR + "Structure Debug", "struct_debug"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminCombatToolsMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Weapons");
        frame(inv);
        inv.setItem(11, button(Material.NETHERITE_SWORD, COMBAT_COLOR + "Hydra Legendaries", "open_hydra_weapons_menu"));
        inv.setItem(13, button(Material.BOW, COMBAT_COLOR + "BR Legendaries", "open_br_weapons_menu"));
        inv.setItem(15, button(Material.TOTEM_OF_UNDYING, DEV_COLOR + "Beta Legendaries", "open_beta_legendary_menu"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminBossToolsMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Hydra Boss Tools");
        frame(inv);
        inv.setItem(11, button(Material.MACE, COMBAT_COLOR + "Combat Master", "open_combat_master_menu",
            ChatColor.GRAY + "Open direct summon and controller tests."));
        inv.setItem(13, button(Material.BOOK, MAIN_TITLE_COLOR + "Registered Bosses", "admin_bosses_list",
            ChatColor.GRAY + "Show every currently summonable boss."));
        inv.setItem(15, button(Material.TRIAL_SPAWNER, COMBAT_COLOR + "Summon Combat Master", "admin_spawn_combat_master",
            ChatColor.GRAY + "Spawns the boss directly at your location.",
            ChatColor.DARK_GRAY + "Does not start Boss Rush."));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openBetaLegendaryMenu(Player player) {
        Inventory inv = menuInventory(45, DEV_COLOR + "Beta Legendaries");
        frame(inv);
        inv.setItem(10, betaLegendaryButton("goblin_mask", Items.goblinMask()));
        inv.setItem(11, betaLegendaryButton("hooked_grappler", Items.hookedGrappler()));
        inv.setItem(12, betaLegendaryButton("diamond_totem", Items.diamondTotem()));
        inv.setItem(13, betaLegendaryButton("wonder_pickaxe", Items.wonderPickaxe()));
        inv.setItem(14, betaLegendaryButton("loki_scepter", Items.lokiScepter()));
        inv.setItem(15, betaLegendaryButton("druids_daggers", Items.druidsDaggers()));
        inv.setItem(19, betaLegendaryButton("memory_breaker", Items.memoryBreaker()));
        inv.setItem(20, betaLegendaryButton("sonic_wrench", Items.sonicWrench()));
        inv.setItem(21, betaLegendaryButton("aegis", Items.aegis()));
        inv.setItem(22, betaLegendaryButton("frozen_chestpiece", Items.frozenChestpiece()));
        inv.setItem(23, betaLegendaryButton("melted_scythe", Items.meltedScythe()));
        inv.setItem(24, betaLegendaryButton("architects_drill", Items.architectsDrill()));
        inv.setItem(29, betaLegendaryButton("plasma_distorter", Items.enderious()));
        inv.setItem(30, betaLegendaryButton("scarabs_wrath", Items.scarabsWrath()));
        inv.setItem(31, betaLegendaryButton("monarch_wings", Items.monarchWings()));
        inv.setItem(32, betaLegendaryButton("hoglin_horn", Items.hoglinHorn()));
        inv.setItem(33, betaLegendaryButton("voidbreaker", voidbreakerMaceItem()));
        inv.setItem(34, betaLegendaryButton("last_breath", railgunCrossbowItem()));
        inv.setItem(25, betaLegendaryButton("ghost_knife", Items.ghostKnife()));
        inv.setItem(40, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_combat"));
        player.openInventory(inv);
    }

    public void openHydraWeaponsMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Hydra Legendaries");
        frame(inv);

        inv.setItem(10, hydraWeaponButton("knights_saddle", Items.knightsSaddle()));
        inv.setItem(11, hydraWeaponButton("dwarven_bow", Items.dwarvenBow()));
        inv.setItem(12, hydraWeaponButton("ghlochester", Items.ghlochester()));
        inv.setItem(13, hydraWeaponButton("spider_staff", Items.spiderStaff()));
        inv.setItem(14, hydraWeaponButton("sponge_saber", Items.spongeSaber()));
        inv.setItem(15, hydraWeaponButton("skeletal_barber", Items.skeletalBarber()));
        inv.setItem(16, hydraWeaponButton("electric_conch", Items.electricConch()));
        inv.setItem(19, hydraWeaponButton("heart_bundle", createHeartBundle()));
        inv.setItem(20, hydraWeaponButton("life_elixir", Items.lifeElixir(1)));
        inv.setItem(21, hydraWeaponButton("royal_roulette", Items.royalRoulette()));
        inv.setItem(22, hydraWeaponButton("book_of_welding", Items.bookOfWelding()));

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_combat"));
        player.openInventory(inv);
    }

    public void openBrWeaponsMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "BR Legendaries");
        frame(inv);

        inv.setItem(10, hydraWeaponButton("knights_saddle", Items.knightsSaddle()));
        inv.setItem(11, hydraWeaponButton("dwarven_bow", Items.dwarvenBow()));
        inv.setItem(12, hydraWeaponButton("ghlochester", Items.ghlochester()));
        inv.setItem(13, hydraWeaponButton("spider_staff", Items.spiderStaff()));
        inv.setItem(14, hydraWeaponButton("sponge_saber", Items.spongeSaber()));
        inv.setItem(15, hydraWeaponButton("skeletal_barber", Items.skeletalBarber()));
        inv.setItem(16, hydraWeaponButton("electric_conch", Items.electricConch()));
        inv.setItem(19, hydraWeaponButton("heart_bundle", createHeartBundle()));
        inv.setItem(20, hydraWeaponButton("life_elixir", Items.lifeElixir(1)));
        inv.setItem(21, hydraWeaponButton("royal_roulette", Items.royalRoulette()));
        inv.setItem(22, hydraWeaponButton("book_of_welding", Items.bookOfWelding()));

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_combat"));
        player.openInventory(inv);
    }

    public void openHydraHungerGamesWeaponsMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "HG Weapon Variants");
        frame(inv);

        inv.setItem(10, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "knights_saddle", Items.knightsSaddle()));
        inv.setItem(11, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "dwarven_bow", Items.dwarvenBow()));
        inv.setItem(12, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "ghlochester", Items.ghlochester()));
        inv.setItem(13, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "spider_staff", Items.spiderStaff()));
        inv.setItem(14, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "sponge_saber", Items.spongeSaber()));
        inv.setItem(15, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "skeletal_barber", Items.skeletalBarber()));
        inv.setItem(16, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "electric_conch", Items.electricConch()));
        inv.setItem(19, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "heart_bundle", createHeartBundle()));
        inv.setItem(20, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "life_elixir", Items.lifeElixir(1)));
        inv.setItem(21, hydraWeaponButton(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX, "royal_roulette", Items.royalRoulette()));

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_combat"));
        player.openInventory(inv);
    }

    public void openCombatMasterMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Combat Master");
        frame(inv);
        inv.setItem(10, button(Material.MACE, COMBAT_COLOR + "Spawn Combat Master", "admin_spawn_combat_master"));
        inv.setItem(11, button(Material.REDSTONE_TORCH, COMBAT_COLOR + "Force Artillery Form", "combatmaster_phase:artillery"));
        inv.setItem(12, button(Material.SHIELD, COMBAT_COLOR + "Force Bulwark Form", "combatmaster_phase:bulwark"));
        inv.setItem(13, button(Material.BAMBOO, COMBAT_COLOR + "Force Discipline Form", "combatmaster_phase:discipline"));
        inv.setItem(14, button(Material.NETHER_STAR, COMBAT_COLOR + "Force Final Lesson", "combatmaster_phase:final"));
        inv.setItem(15, button(Material.OBSERVER, MAIN_TITLE_COLOR + "Combat Master Debug", "admin_combat_master_debug"));
        inv.setItem(16, button(Material.BARRIER, WARNING_COLOR + "Reset Combat Master", "admin_combat_master_reset"));
        inv.setItem(22, button(Material.TRIAL_SPAWNER, WARNING_COLOR + "Legacy Waves Deprecated", "legacy_wave_deprecated",
            "Old wave spawning/passive system is disabled",
            "New boss wave system will live here later"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_bosses"));
        player.openInventory(inv);
    }

    public void openWitheredWardenMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Withered Warden");
        frame(inv);
        inv.setItem(10, button(Material.REINFORCED_DEEPSLATE, MAIN_TITLE_COLOR + "Set Altar Here", "warden_set_altar"));
        inv.setItem(11, button(Material.TRIAL_KEY, MAIN_TITLE_COLOR + "Give Withered Key", "warden_give_key_self"));
        inv.setItem(12, button(Material.PAPER, MAIN_TITLE_COLOR + "Warden Status", "warden_status"));
        inv.setItem(13, button(Material.END_CRYSTAL, WARNING_COLOR + "Spawn Boss", "warden_spawn"));
        inv.setItem(14, button(Material.BARRIER, WARNING_COLOR + "Despawn Boss", "warden_despawn"));
        inv.setItem(15, button(Material.STRUCTURE_VOID, WARNING_COLOR + "Reset Keyholes", "warden_reset_keys"));

        inv.setItem(19, button(Material.BLUE_WOOL, COMBAT_COLOR + "Insert North", "warden_insert:north"));
        inv.setItem(20, button(Material.ORANGE_WOOL, COMBAT_COLOR + "Insert East", "warden_insert:east"));
        inv.setItem(21, button(Material.RED_WOOL, COMBAT_COLOR + "Insert South", "warden_insert:south"));
        inv.setItem(22, button(Material.LIME_WOOL, COMBAT_COLOR + "Insert West", "warden_insert:west"));
        inv.setItem(23, button(Material.OBSERVER, MAIN_TITLE_COLOR + "Debug", "warden_debug"));

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_combat"));
        player.openInventory(inv);
    }

    public void openWitheredVaultMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Hydra Vault");
        frame(inv);
        inv.setItem(10, button(Material.REINFORCED_DEEPSLATE, MAIN_TITLE_COLOR + "Set Vault Door Here", "vault_set_door"));
        inv.setItem(11, button(Material.BRICKS, MAIN_TITLE_COLOR + "Build Vault Interior", "vault_build_interior"));
        inv.setItem(12, button(Material.TRIAL_KEY, MAIN_TITLE_COLOR + "Give Vault Key", "vault_give_key"));
        inv.setItem(13, button(Material.PAPER, MAIN_TITLE_COLOR + "Vault Status", "vault_status"));
        inv.setItem(14, button(Material.STRUCTURE_VOID, WARNING_COLOR + "Reset 5 Keyholes", "vault_reset_keys"));
        inv.setItem(15, button(Material.IRON_DOOR, WARNING_COLOR + "Open Vault Door", "vault_open"));
        inv.setItem(16, button(Material.ZOMBIE_HEAD, MAIN_TITLE_COLOR + "Spawn Guards", "vault_spawn_guards"));
        inv.setItem(25, button(Material.BARRIER, WARNING_COLOR + "Clear Guards", "vault_clear_guards"));

        inv.setItem(19, button(Material.BLUE_WOOL, COMBAT_COLOR + "Insert Slot A", "vault_insert:a"));
        inv.setItem(20, button(Material.ORANGE_WOOL, COMBAT_COLOR + "Insert Slot B", "vault_insert:b"));
        inv.setItem(21, button(Material.RED_WOOL, COMBAT_COLOR + "Insert Slot C", "vault_insert:c"));
        inv.setItem(22, button(Material.LIME_WOOL, COMBAT_COLOR + "Insert Slot D", "vault_insert:d"));
        inv.setItem(23, button(Material.PURPLE_WOOL, COMBAT_COLOR + "Insert Slot E", "vault_insert:e"));

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_withered_warden_menu"));
        player.openInventory(inv);
    }

    public void openAdminEconomyToolsMenu(Player player) {
        Inventory inv = menuInventory(36, ECONOMY_COLOR + "Hydra Economy Admin");
        frame(inv);
        inv.setItem(10, button(Material.GOLD_INGOT, ECONOMY_COLOR + "Give Coins (Self +1000)", "admin_econ_addsilver_self_large"));
        inv.setItem(11, button(Material.IRON_INGOT, ECONOMY_COLOR + "Give Coins (Self +250)", "admin_econ_addsilver_self"));
        inv.setItem(12, button(Material.PAPER, MAIN_TITLE_COLOR + "Economy Log", "admin_econ_log"));
        inv.setItem(13, button(Material.BOOK, MAIN_TITLE_COLOR + "Auction House Audit", "admin_econ_audit_self"));
        inv.setItem(14, button(Material.BARRIER, WARNING_COLOR + "Refund Listing", "admin_econ_refund_placeholder"));
        inv.setItem(15, button(Material.AMETHYST_SHARD, MAIN_TITLE_COLOR + "Progression Admin", "open_admin_wallet_tools",
            "Manage Level XP, Coins, Shards, Kills, and Wins",
            "Command path: /admin wallet"));
        inv.setItem(16, button(Material.AMETHYST_SHARD, MAIN_TITLE_COLOR + "Open Shard Claim", "admin_econ_shard_claim_self"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminWalletToolsMenu(Player player) {
        Inventory inv = menuInventory(36, ECONOMY_COLOR + "Hydra Progression Admin");
        frame(inv);
        inv.setItem(10, button(Material.EXPERIENCE_BOTTLE, ECONOMY_COLOR + "Add 500 Level XP", "admin_wallet_add_xp_self_500",
            "/admin wallet add " + player.getName() + " levelxp 500"));
        inv.setItem(11, button(Material.AMETHYST_SHARD, MAIN_TITLE_COLOR + "Add 1 Shard", "admin_wallet_add_shard_self_1",
            "/admin wallet add " + player.getName() + " shards 1"));
        inv.setItem(12, button(Material.PRISMARINE_CRYSTALS, MAIN_TITLE_COLOR + "Add 1 Fragment", "admin_wallet_add_fragment_self_1",
            "/admin wallet add " + player.getName() + " fragments 1"));
        inv.setItem(14, button(Material.AMETHYST_SHARD, ECONOMY_COLOR + "Open Shard Claim", "admin_econ_shard_claim_self"));
        inv.setItem(16, button(Material.WRITABLE_BOOK, MAIN_TITLE_COLOR + "Command Help", "admin_wallet_help",
            "/admin set <money|kills|wins|shards|level> [player] <amount>",
            "/wins reset <player>"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_admin_economy_tools"));
        player.openInventory(inv);
    }

    public void openAdminCosmeticsToolsMenu(Player player) {
        Inventory inv = menuInventory(36, MAIN_TITLE_COLOR + "Hydra Cosmetics Admin");
        frame(inv);
        inv.setItem(10, button(Material.PLAYER_HEAD, MAIN_TITLE_COLOR + "Manage Online Players", "open_admin_cosmetics_player_list"));
        inv.setItem(12, button(Material.CARVED_PUMPKIN, CATEGORY_TITLE_COLOR + "Manage Self", ADMIN_COSMETICS_PLAYER_ACTION_PREFIX + player.getUniqueId()));
        inv.setItem(14, button(Material.NAME_TAG, CATEGORY_TITLE_COLOR + "Rank Online Players", "open_admin_rank_player_list",
            "Grant player-facing and staff ranks",
            "Hydra+ tiers do not grant staff access"));
        inv.setItem(16, button(Material.NETHER_STAR, CATEGORY_TITLE_COLOR + "Rank Self",
            ADMIN_RANK_PLAYER_ACTION_PREFIX + player.getUniqueId(),
            "Open your own rank tools",
            "Hydra+ and staff ranks are separate"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminCosmeticsPlayerListMenu(Player player) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(Math.max(onlinePlayers.size(), 1));
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Cosmetics Players");
        frame(inv);

        List<Integer> slots = actionSlots(size);
        int slotIndex = 0;
        for (Player online : onlinePlayers) {
            if (slotIndex >= slots.size()) {
                break;
            }
            inv.setItem(
                slots.get(slotIndex++),
                playerHeadButton(
                    online.getUniqueId(),
                    CATEGORY_TITLE_COLOR + online.getName(),
                    ADMIN_COSMETICS_PLAYER_ACTION_PREFIX + online.getUniqueId(),
                    "Manage unlocked cosmetics"
                )
            );
        }

        if (onlinePlayers.isEmpty()) {
            inv.setItem(13, button(Material.BARRIER, WARNING_COLOR + "No Players Online", "noop"));
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_cosmetics_tools"));
        player.openInventory(inv);
    }

    public void openAdminRankPlayerListMenu(Player player) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int size = friendsMenuSize(Math.max(onlinePlayers.size(), 1));
        Inventory inv = menuInventory(size, MAIN_TITLE_COLOR + "Hydra Rank Players");
        frame(inv);

        List<Integer> slots = actionSlots(size);
        int slotIndex = 0;
        for (Player online : onlinePlayers) {
            if (slotIndex >= slots.size()) {
                break;
            }
            inv.setItem(
                slots.get(slotIndex++),
                playerHeadButton(
                    online.getUniqueId(),
                    CATEGORY_TITLE_COLOR + online.getName(),
                    ADMIN_RANK_PLAYER_ACTION_PREFIX + online.getUniqueId(),
                    ChatColor.GRAY + "Current: " + ChatColor.WHITE + hydraRankLabel(online),
                    ChatColor.GREEN + "Click to manage rank"
                )
            );
        }

        if (onlinePlayers.isEmpty()) {
            inv.setItem(13, button(Material.BARRIER, WARNING_COLOR + "No Players Online", "noop"));
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_cosmetics_tools"));
        player.openInventory(inv);
    }

    public void openAdminRankPlayerMenu(Player admin, UUID targetId) {
        String targetName = cosmeticTargetDisplayName(targetId);
        Inventory inv = menuInventory(36, MAIN_TITLE_COLOR + "Rank: " + targetName);
        frame(inv);

        inv.setItem(4, playerHeadButton(
            targetId,
            MAIN_TITLE_COLOR + targetName,
            "noop",
            ChatColor.GRAY + "Current Rank: " + ChatColor.WHITE + hydraRankLabel(targetId)
        ));
        inv.setItem(10, button(Material.NAME_TAG, CATEGORY_TITLE_COLOR + "Hydra",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":base",
            "Player-facing tier"));
        inv.setItem(11, button(Material.PRISMARINE_CRYSTALS, CATEGORY_TITLE_COLOR + "Hydra+",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":tier1",
            "Player-facing support tier"));
        inv.setItem(12, button(Material.PRISMARINE_SHARD, CATEGORY_TITLE_COLOR + "Hydra++",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":tier2",
            "Player-facing support tier"));
        inv.setItem(13, button(Material.NETHER_STAR, CATEGORY_TITLE_COLOR + "Hydra+++",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":tier3",
            "Player-facing support tier"));
        inv.setItem(18, button(Material.FEATHER, ChatColor.YELLOW + "Trial Staff",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":trialstaff",
            "Limited moderation and spectator access"));
        inv.setItem(19, button(Material.EMERALD, ChatColor.GREEN + "Staff",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":staff",
            "Staff access"));
        inv.setItem(20, button(Material.LIME_DYE, ChatColor.GREEN + "Mod",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":mod",
            "Moderation Access"));
        inv.setItem(21, button(Material.GOLD_INGOT, ChatColor.GOLD + "Admin",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":admin",
            "Administrative Access"));
        inv.setItem(22, button(Material.AMETHYST_SHARD, DEV_COLOR + "Dev",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":dev",
            "Development Access"));
        inv.setItem(23, button(Material.REDSTONE_BLOCK, ChatColor.DARK_RED + "Owner",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":owner",
            "Full Network Access"));
        inv.setItem(27, button(Material.PAPER, ChatColor.AQUA + "Helper",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":helper",
            "Helper display rank"));
        inv.setItem(28, button(Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "Media",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":media",
            "Media display rank"));
        inv.setItem(29, button(Material.PAINTING, ChatColor.LIGHT_PURPLE + "Creator",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":creator",
            "Creator display rank"));
        inv.setItem(31, button(Material.BARRIER, WARNING_COLOR + "Remove Rank",
            ADMIN_RANK_SET_ACTION_PREFIX + targetId + ":none",
            "Unset player tier and staff rank permissions"));
        inv.setItem(35, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_admin_rank_player_list"));
        admin.openInventory(inv);
    }

    public void openAdminCosmeticPlayerMenu(Player admin, UUID targetId) {
        String targetName = cosmeticTargetDisplayName(targetId);
        Inventory inv = menuInventory(ADMIN_COSMETICS_MENU_SIZE, MAIN_TITLE_COLOR + "Cosmetics: " + targetName);
        frame(inv);

        inv.setItem(
            ADMIN_COSMETICS_TARGET_SLOT,
            playerHeadButton(
                targetId,
                MAIN_TITLE_COLOR + targetName,
                "noop",
                "Choose a cosmetic category"
            )
        );

        List<CosmeticCategory> categories = adminCosmeticCategories();
        List<Integer> slots = new ArrayList<>(actionSlots(ADMIN_COSMETICS_MENU_SIZE));
        slots.removeIf(slot -> slot == ADMIN_COSMETICS_TARGET_SLOT);
        for (int i = 0; i < categories.size() && i < slots.size(); i++) {
            CosmeticCategory category = categories.get(i);
            inv.setItem(
                slots.get(i),
                button(
                    category.iconMaterial,
                    CATEGORY_TITLE_COLOR + category.displayName,
                    ADMIN_COSMETICS_CATEGORY_ACTION_PREFIX + targetId + ":" + category.id,
                    ChatColor.GRAY + String.valueOf(cosmeticDefinitions(category).size()) + ChatColor.GRAY + " cosmetics"
                )
            );
        }

        inv.setItem(backSlot(ADMIN_COSMETICS_MENU_SIZE), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_cosmetics_player_list"));
        admin.openInventory(inv);
    }

    private void openAdminCosmeticCategoryMenu(Player admin, UUID targetId, CosmeticCategory category) {
        if (category == null || category.domain == CosmeticDomain.RANK) {
            category = CosmeticCategory.PROJECTILE_TRAILS;
        }
        String targetName = cosmeticTargetDisplayName(targetId);
        Inventory inv = menuInventory(ADMIN_COSMETICS_MENU_SIZE, MAIN_TITLE_COLOR + category.displayName);
        frame(inv);
        String selectedHatId = selectedHatCosmeticId(targetId);
        String selectedKillMessageId = selectedKillMessageId(targetId);
        String selectedSystemId = selectedSystemCosmeticId(targetId, category);

        inv.setItem(
            ADMIN_COSMETICS_TARGET_SLOT,
            playerHeadButton(
                targetId,
                MAIN_TITLE_COLOR + targetName,
                "noop",
                "Click a cosmetic below to unlock or lock it"
            )
        );

        List<Integer> slots = new ArrayList<>(actionSlots(ADMIN_COSMETICS_MENU_SIZE));
        slots.removeIf(slot -> slot == ADMIN_COSMETICS_TARGET_SLOT);
        int slotIndex = 0;
        for (CosmeticDefinition cosmetic : cosmeticDefinitions(category)) {
            if (slotIndex >= slots.size()) {
                break;
            }

            boolean owned = cosmeticOwnedBy(targetId, admin, cosmetic);
            String action = ADMIN_COSMETICS_TOGGLE_ACTION_PREFIX + targetId + ":" + cosmetic.id + ":" + category.id;
            boolean selected;
            if (category == CosmeticCategory.LEGENDARY_SKINS) {
                String selectedLegendaryId = getSelectedLegendarySkinId(targetId, cosmetic.legendaryKey);
                selected = cosmetic.id.equals(selectedLegendaryId);
            } else if (category == CosmeticCategory.KILL_MESSAGES) {
                selected = cosmetic.id.equals(selectedKillMessageId);
            } else if (category == CosmeticCategory.PROJECTILE_TRAILS
                || category == CosmeticCategory.ARMOR_TRIMS
                || category == CosmeticCategory.KILL_EFFECTS
                || category == CosmeticCategory.TITLES) {
                selected = cosmetic.id.equals(selectedSystemId);
            } else {
                selected = cosmetic.id.equals(selectedHatId);
            }
            String itemName = cosmeticDisplayName(cosmetic, owned, selected);

            inv.setItem(slots.get(slotIndex++), cosmeticButton(cosmetic, itemName, action, owned, selected));
        }

        inv.setItem(backSlot(ADMIN_COSMETICS_MENU_SIZE), button(
            BACK_BUTTON_MATERIAL,
            WARNING_COLOR + "Back",
            ADMIN_COSMETICS_PLAYER_ACTION_PREFIX + targetId
        ));
        admin.openInventory(inv);
    }

    public void openAdminDevToolsMenu(Player player) {
        Inventory inv = menuInventory(36, DEV_COLOR + "Debug Tools");
        frame(inv);
        inv.setItem(10, button(Material.CHEST, LOOT_COLOR + "Loot Tools", "open_admin_loot_tools"));
        inv.setItem(12, button(Material.STRUCTURE_BLOCK, STRUCTURE_COLOR + "Structure Tools", "open_structure_tools"));
        inv.setItem(14, button(Material.EMERALD, ECONOMY_COLOR + "Economy Admin", "open_admin_economy_tools"));
        inv.setItem(16, button(Material.ENDER_CHEST, MAIN_TITLE_COLOR + "Cosmetic Items", "open_admin_cosmetics_tools"));
        inv.setItem(22, button(Material.OAK_SIGN, MAIN_TITLE_COLOR + "Leaderboards", "open_admin_leaderboard_tools"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_main"));
        player.openInventory(inv);
    }

    public void openAdminLeaderboardToolsMenu(Player player) {
        openLeaderboardToolsMenu(player, "back_admin_dev");
    }

    public void openLeaderboardToolsMenu(Player player) {
        openLeaderboardToolsMenu(player, "close_menu");
    }

    private void openLeaderboardToolsMenu(Player player, String backAction) {
        Inventory inv = menuInventory(27, DEV_COLOR + "Leaderboards");
        frame(inv);
        inv.setItem(10, button(Material.OAK_SIGN, MAIN_TITLE_COLOR + "Total Wins Board", "admin_debug_summon_board:wins_total"));
        inv.setItem(11, button(Material.GOLDEN_SWORD, ChatColor.GOLD + "HG Wins Board", "admin_debug_summon_board:wins_hg"));
        inv.setItem(12, button(Material.CROSSBOW, ChatColor.GOLD + "BR Wins Board", "admin_debug_summon_board:wins_br"));
        inv.setItem(13, button(Material.IRON_SWORD, ChatColor.RED + "Kills Board", "admin_debug_summon_board:kills"));
        inv.setItem(14, button(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Level Board", "admin_debug_summon_board:level"));
        inv.setItem(15, button(Material.EMERALD, ChatColor.GREEN + "Coins Board", "admin_debug_summon_board:money"));
        inv.setItem(16, button(Material.BARRIER, WARNING_COLOR + "Clear Nearby", "leaderboard_clear_nearby"));
        inv.setItem(19, button(Material.CLOCK, ChatColor.AQUA + "Refresh Leaderboards", "leaderboard_refresh"));
        inv.setItem(20, button(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Debug State", "leaderboard_debug_state"));
        inv.setItem(21, button(Material.COMPASS, ChatColor.GRAY + "/leaderboard switch <type>", "noop"));
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + ("close_menu".equals(backAction) ? "Close" : "Back"), backAction));
        player.openInventory(inv);
    }

    // Legacy wrappers kept so old actions still open the new system menus.
    public void openCustomMenu(Player player) {
        openAdminCombatToolsMenu(player);
    }

    public void openFunStuffMenu(Player player) {
        openAdminDevToolsMenu(player);
    }

    public void openToolsMenu(Player player) {
        openAdminMatchToolsMenu(player);
    }

    public void openWaveMenu(Player player) {
        Inventory inv = menuInventory(36, COMBAT_COLOR + "Wave System");
        frame(inv);

        inv.setItem(13, button(Material.TRIAL_SPAWNER, WARNING_COLOR + "Deprecated", "legacy_wave_deprecated",
            "Old wave controls were removed",
            "Use Combat Chamber tools for the current test slice"));
        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_bosses"));
        player.openInventory(inv);
    }

    public void openRadiusFillCategoryMenu(Player player) {
        Inventory inv = menuInventory(36, CATEGORY_TITLE_COLOR + "Radius Chest Fill");
        frame(inv);

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        LootCategory[] categories = LootCategory.values();
        for (int i = 0; i < categories.length && i < slots.length; i++) {
            LootCategory category = categories[i];
            String action = "open_radius_fill_table:" + category.key();
            inv.setItem(slots[i], button(
                category.icon(),
                MAIN_TITLE_COLOR + category.displayName(),
                action
            ));
        }

        inv.setItem(31, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_admin_loot_tools"));
        player.openInventory(inv);
    }

    public void openRadiusFillTableMenu(Player player, LootCategory category) {
        int size = category.chestCount() <= 7 ? 36 : 54;
        Inventory inv = menuInventory(size, CATEGORY_TITLE_COLOR + "Radius Fill: " + category.displayName());
        frame(inv);

        List<Integer> slots = actionSlots(size);
        for (int i = 1; i <= category.chestCount() && i <= slots.size(); i++) {
            String action = "open_radius_fill_radius:" + category.key() + ":" + i;
            inv.setItem(slots.get(i - 1), button(
                Material.CHEST,
                category.color() + category.displayName() + " " + i,
                action
            ));
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "open_radius_fill_category"));
        player.openInventory(inv);
    }

    public void openRadiusFillRadiusMenu(Player player, LootCategory category, int index) {
        Inventory inv = menuInventory(27, CATEGORY_TITLE_COLOR + "Radius: " + category.displayName() + " " + index);
        frame(inv);

        inv.setItem(11, button(Material.YELLOW_WOOL, ChatColor.YELLOW + "Radius 8",
            "apply_radius_fill:" + category.key() + ":" + index + ":8"));
        inv.setItem(13, button(Material.ORANGE_WOOL, ChatColor.GOLD + "Radius 12",
            "apply_radius_fill:" + category.key() + ":" + index + ":12"));
        inv.setItem(15, button(Material.RED_WOOL, WARNING_COLOR + "Radius 20",
            "apply_radius_fill:" + category.key() + ":" + index + ":20"));
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_radius_fill_table:" + category.key()));
        player.openInventory(inv);
    }

    public void openCategoryMenu(Player player, LootCategory category) {
        int size = category.chestCount() <= 7 ? 36 : 54;
        Inventory inv = menuInventory(size, CATEGORY_TITLE_COLOR + category.displayName() + " Chests");
        frame(inv);

        List<Integer> slots = actionSlots(size);
        for (int i = 1; i <= category.chestCount() && i <= slots.size(); i++) {
            String action = "open_action:" + category.key() + ":" + i;
            inv.setItem(slots.get(i - 1), button(
                Material.CHEST,
                category.color() + category.displayName() + " " + i,
                action,
                "Open actions for chest " + i
            ));
        }

        inv.setItem(backSlot(size), button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_loot"));
        player.openInventory(inv);
    }

    public void openActionMenu(Player player, LootCategory category, int index) {
        Inventory inv = menuInventory(27, CATEGORY_TITLE_COLOR + category.displayName() + " " + index + " Actions");
        frame(inv);

        inv.setItem(11, button(Material.CHEST, ChatColor.GREEN + "Give", "give:" + category.key() + ":" + index, "Give loot chest item"));
        inv.setItem(13, button(Material.CHEST, WARNING_COLOR + "Spawn", "spawn:" + category.key() + ":" + index, "Place chest nearby"));
        inv.setItem(15, button(Material.WRITTEN_BOOK, MAIN_TITLE_COLOR + "Audit", "audit:" + category.key() + ":" + index, "Get audit book"));
        inv.setItem(22, button(BACK_BUTTON_MATERIAL, WARNING_COLOR + "Back", "back_category:" + category.key()));
        player.openInventory(inv);
    }

    public boolean isOurMenu(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof MenuHolder;
    }

    public void handlePlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyWorldInventoryMode(player), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyWorldInventoryMode(player), 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyWorldInventoryMode(player), 100L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshArmorTrimCosmetic(player), 25L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendJoinIntroMessages(player), JOIN_CHAT_DELAY_TICKS);
    }

    public void handlePlayerRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyWorldInventoryMode(event.getPlayer()), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshArmorTrimCosmetic(event.getPlayer()), 3L);
    }

    public void handlePlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyWorldInventoryMode(event.getPlayer()), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshArmorTrimCosmetic(event.getPlayer()), 8L);
    }

    public void refreshPlayerMenuCompass(Player player) {
        ensurePlayerMenuCompass(player);
    }

    public void refreshHubInterface(Player player) {
        ensurePlayerHubHotbar(player);
        if (player != null && player.isOnline()) {
            player.updateInventory();
        }
    }

    public void preparePlayerForMatch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.setLevel(0);
        player.setExp(0.0F);
        clearHubMenuItems(player);
        player.updateInventory();
    }

    public void preparePlayerForHub(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0D);
            player.setHealth(20.0D);
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] {null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        ensurePlayerHubHotbar(player);
        refreshHeadCosmetic(player);
        player.updateInventory();
    }

    public boolean isHubHotbarEnabled(UUID playerId) {
        return playerId != null && !hubHotbarDisabledPlayers.contains(playerId);
    }

    public boolean toggleHubHotbar(Player target) {
        if (target == null) {
            return true;
        }
        UUID playerId = target.getUniqueId();
        boolean nowEnabled;
        if (hubHotbarDisabledPlayers.remove(playerId)) {
            nowEnabled = true;
        } else {
            hubHotbarDisabledPlayers.add(playerId);
            nowEnabled = false;
        }
        saveHubHotbarSettings();
        if (nowEnabled) {
            ensurePlayerHubHotbar(target);
        } else {
            clearHubMenuItems(target);
        }
        target.updateInventory();
        return nowEnabled;
    }

    public void clearHubMenuItems(Player player) {
        if (player == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (readHubItemAction(contents[i]) != null) {
                player.getInventory().setItem(i, null);
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (readHubItemAction(offhand) != null) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public void shutdown() {
        conditionalHotbarWarnings.clear();
        if (headCosmeticTask != null) {
            headCosmeticTask.cancel();
            headCosmeticTask = null;
        }
        if (voidWarpHudTask != null) {
            voidWarpHudTask.cancel();
            voidWarpHudTask = null;
        }
        if (tabListTask != null) {
            tabListTask.cancel();
            tabListTask = null;
        }
    }

    public void handlePlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        Player player = event.getPlayer();
        if (tryToggleRailgunMode(event, player, event.getItem())) {
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        startLastBreathWarmup(player, event.getItem());
        if (tryUseVoidRupture(event, player, event.getItem())) {
            return;
        }

        String hubAction = readHubItemAction(event.getItem());
        if (hubAction == null) {
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission("hgloot.playermenu")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this.");
            return;
        }
        switch (hubAction) {
            case HUB_ITEM_NAVIGATOR -> openPlayerMenu(player);
            case HUB_ITEM_ECONOMY -> Bukkit.dispatchCommand(player, "ah");
            case HUB_ITEM_RECIPES, HUB_ITEM_UTILITY_BOOK -> Bukkit.dispatchCommand(player, "recipes");
            case HUB_ITEM_SETTINGS -> {
                openPlayerSettingsMenu(player);
                HydraSounds.playUi(player, HydraSounds.UiCue.OPEN);
            }
            case HUB_ITEM_BATTLE_PASS -> {
                Bukkit.dispatchCommand(player, "battlepass");
                HydraSounds.playUi(player, HydraSounds.UiCue.OPEN);
            }
            case HUB_ITEM_LEAVE_GAMEHUB -> Bukkit.dispatchCommand(player, "hub");
            case HUB_ITEM_COSMETICS -> openPlayerLobbyCosmeticsMenu(player);
            case HUB_ITEM_SOCIAL -> openPlayerSocialMenu(player);
            case HUB_ITEM_LEAVE_ROULETTE -> {
                if (minigameRouletteService != null) {
                    minigameRouletteService.leaveQueue(player);
                }
            }
            case HUB_ITEM_LEAVE_HUNGER_GAMES -> {
                QueueService queueService = queueServiceOrNull();
                if (queueService != null) {
                    queueService.leaveQueueOrMatch(player);
                }
            }
            case HUB_ITEM_CLAIM_SHARD -> Bukkit.dispatchCommand(player, "shard open");
            default -> {
            }
        }
    }

    private void startLastBreathWarmup(Player player, ItemStack usedItem) {
        if (!isRailgunCrossbowItem(usedItem) || !isRailgunCrossbowItem(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (CooldownRegistry.getCooldown(player, UltraRoyales.lastBreathCooldown) > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        if (lastBreathWarmupUntilMs.getOrDefault(playerId, 0L) > now) {
            return;
        }
        lastBreathWarmupUntilMs.put(playerId, now + 1250L);

        Particle.DustOptions voidDust = new Particle.DustOptions(Color.fromRGB(45, 0, 70), 1.25f);
        Particle.DustOptions whiteCore = new Particle.DustOptions(Color.fromRGB(240, 240, 255), 1.55f);
        for (int pulse = 0; pulse < 5; pulse++) {
            final int step = pulse;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead() || !isRailgunCrossbowItem(player.getInventory().getItemInMainHand())) {
                    return;
                }
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Location muzzle = eye.clone().add(dir.clone().multiply(0.75D));
                double radius = Math.max(0.22D, 1.08D - (step * 0.19D));
                int ringPoints = 18 + (step * 3);
                for (int i = 0; i < ringPoints; i++) {
                    double angle = (Math.PI * 2.0D / ringPoints) * i;
                    Location point = muzzle.clone().add(Math.cos(angle) * radius, Math.sin(angle * 1.7D) * 0.2D, Math.sin(angle) * radius);
                    Vector inward = muzzle.toVector().subtract(point.toVector()).normalize().multiply(0.04D + (step * 0.01D));
                    player.getWorld().spawnParticle(Particle.DUST, point, 1, inward.getX(), inward.getY(), inward.getZ(), 0.0D, voidDust, true);
                }
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, muzzle, 12 + (step * 6), 0.22D, 0.22D, 0.22D, 0.08D + (step * 0.015D));
                player.getWorld().spawnParticle(Particle.DUST, muzzle, 4 + step, 0.05D, 0.05D, 0.05D, 0.0D, whiteCore, true);
                if (step >= 2) {
                    player.getWorld().spawnParticle(Particle.END_ROD, muzzle, 3 + step, 0.08D, 0.08D, 0.08D, 0.012D);
                }
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45f + (step * 0.08f), 0.45f + (step * 0.11f));
            }, pulse * 5L);
        }
    }

    public void handleInventoryClickProtection(Player player, InventoryClickEvent event) {
        boolean movingHeadCosmetic = isHeadCosmeticItem(event.getCurrentItem()) || isHeadCosmeticItem(event.getCursor());
        if (!movingHeadCosmetic) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 8) {
                movingHeadCosmetic = isHeadCosmeticItem(player.getInventory().getItem(hotbarButton));
            }
        }
        if (movingHeadCosmetic) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> refreshCosmeticEquipment(player));
            return;
        }

        scheduleArmorTrimRefresh(player, 1L);

        if (isPlayerMenuCompass(event.getCurrentItem()) || isPlayerMenuCompass(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && hotbarButton <= 8) {
            ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
            if (isCriticalHubControl(hotbarItem) || (!player.isOp() && isPlayerMenuCompass(hotbarItem))) {
                event.setCancelled(true);
            }
        }
    }

    public void handleInventoryDragProtection(Player player, InventoryDragEvent event) {
        if (player == null) {
            return;
        }
        if (isHeadCosmeticItem(event.getOldCursor())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> refreshCosmeticEquipment(player));
            return;
        }
        if (isCriticalHubControl(event.getOldCursor()) || (!player.isOp() && isPlayerMenuCompass(event.getOldCursor()))) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensurePlayerMenuCompass(player));
        }
        scheduleArmorTrimRefresh(player, 1L);
    }

    public void handleEntityEquipmentChanged(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        boolean armorChanged = event.getEquipmentChanges().keySet().stream().anyMatch(slot ->
            (slot == EquipmentSlot.HEAD && !isSpinningTopHatItem(player.getInventory().getHelmet()))
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET
        );
        if (armorChanged) {
            scheduleArmorTrimRefresh(player, 1L);
        }
    }

    public void handlePlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isHeadCosmeticItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> refreshCosmeticEquipment(player));
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (!isCriticalHubControl(dropped) && (player.isOp() || !isPlayerMenuCompass(dropped))) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> ensurePlayerMenuCompass(player));
    }

    public void handlePlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isHeadCosmeticItem(event.getMainHandItem()) || isHeadCosmeticItem(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> refreshCosmeticEquipment(player));
            return;
        }
        boolean critical = isCriticalHubControl(event.getMainHandItem()) || isCriticalHubControl(event.getOffHandItem());
        if (!critical && (player.isOp()
            || (!isPlayerMenuCompass(event.getMainHandItem()) && !isPlayerMenuCompass(event.getOffHandItem())))) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> ensurePlayerMenuCompass(player));
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            String regionAction = themedEmptySlotAction(holder.theme(), event.getRawSlot());
            if (regionAction != null) {
                playMenuCue(player, regionAction, event.getRawSlot());
                executeAction(player, regionAction);
            }
            return;
        }

        String action = readAction(clicked);
        if (action == null || action.isBlank()) {
            return;
        }
        if (action.startsWith(HG_UTILITY_GIVE_ACTION_PREFIX) && event.getClick() != ClickType.LEFT) {
            return;
        }
        if (action.startsWith(COSMETIC_SELECT_ACTION_PREFIX)
            && (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            String cosmeticId = action.substring(COSMETIC_SELECT_ACTION_PREFIX.length());
            CosmeticDefinition cosmetic = findCosmeticDefinitionById(cosmeticId);
            if (cosmetic != null && cosmeticOwnedBy(player.getUniqueId(), player, cosmetic)) {
                boolean favorite = cosmeticService.toggleFavoriteCosmetic(player.getUniqueId(), cosmetic.id, true);
                HydraSounds.playUi(player, favorite ? HydraSounds.UiCue.FAVORITE_ON : HydraSounds.UiCue.FAVORITE_OFF);
                player.sendMessage((favorite ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY)
                    + cosmetic.displayName + (favorite ? " added to favorites." : " removed from favorites."));
                if ("cosmetics_favorites".equalsIgnoreCase(holder.theme())) {
                    openPlayerFavoritesMenu(player, 0);
                } else {
                    applyCosmeticClickLore(clicked, favorite);
                    event.getView().getTopInventory().setItem(event.getRawSlot(), clicked);
                }
                return;
            }
        }
        playMenuCue(player, action, event.getRawSlot());
        executeAction(player, action);
    }

    private void playMenuCue(Player player, String action, int rawSlot) {
        HydraSounds.UiCue cue = uiCueForAction(action, rawSlot);
        if (action.startsWith("player_settings_toggle:")) {
            PlayerSettingsService.Toggle setting = PlayerSettingsService.Toggle.byKey(
                action.substring("player_settings_toggle:".length()));
            if (setting == null) return;
            HGLootPlugin hydra = (HGLootPlugin) plugin;
            if (setting == PlayerSettingsService.Toggle.LOBBY_FLIGHT
                && (!hydra.playerSettingsService().canUseFlightToggle(player)
                    || (!hydra.playerSettingsService().flightEnabled(player)
                        && !hydra.playerSettingsService().canEnableFlight(player)))) {
                cue = HydraSounds.UiCue.UNAVAILABLE;
            } else {
                cue = (setting == PlayerSettingsService.Toggle.LOBBY_FLIGHT
                    ? hydra.playerSettingsService().flightEnabled(player)
                    : hydra.playerSettingsService().enabled(player.getUniqueId(), setting))
                    ? HydraSounds.UiCue.TOGGLE_OFF : HydraSounds.UiCue.TOGGLE_ON;
            }
        } else if (action.startsWith(COSMETIC_SELECT_ACTION_PREFIX)) {
            String id = action.substring(COSMETIC_SELECT_ACTION_PREFIX.length());
            if (!id.startsWith("none@")) {
                CosmeticDefinition cosmetic = findCosmeticDefinitionById(id);
                if (cosmetic != null && !cosmeticOwnedBy(player.getUniqueId(), player, cosmetic)) {
                    cue = HydraSounds.UiCue.UNAVAILABLE;
                }
            }
        }
        HydraSounds.playUi(player, cue);
    }

    static HydraSounds.UiCue uiCueForAction(String action, int rawSlot) {
        if (action == null || action.isBlank() || action.equals("noop")) return null;
        if (action.equals("player_settings_page_1")) return HydraSounds.UiCue.PAGE_BACK;
        if (action.equals("player_settings_page_2")) return HydraSounds.UiCue.PAGE_FORWARD;
        if (action.startsWith("friends_page:")) {
            if (rawSlot == 18 || rawSlot == 27) return HydraSounds.UiCue.PAGE_BACK;
            if (rawSlot == 26 || rawSlot == 35) return HydraSounds.UiCue.PAGE_FORWARD;
            return HydraSounds.UiCue.CATEGORY;
        }
        if (rawSlot == 22 && (action.equals("open_player_friends_requests")
            || action.equals("open_player_party_requests")
            || action.equals("open_player_party_menu"))) return HydraSounds.UiCue.PAGE_BACK;
        if (action.equals("open_player_friends_menu")
            || action.equals("open_player_party_menu")
            || action.equals("open_player_friends_list")
            || action.equals("open_player_friends_requests")
            || action.equals("open_player_friends_add")
            || action.equals("open_player_friends_remove")
            || action.equals("open_player_party_invite_friends")
            || action.equals("open_player_party_requests")
            || action.equals("open_player_party_members")
            || action.equals("open_player_party_details")) return HydraSounds.UiCue.CATEGORY;
        if (action.startsWith(COSMETIC_PAGE_ACTION_PREFIX)
            || action.startsWith(COSMETIC_FAVORITES_PAGE_ACTION_PREFIX)) {
            return rawSlot == PLAYER_COSMETICS_PREVIOUS_SLOT
                ? HydraSounds.UiCue.PAGE_BACK : HydraSounds.UiCue.PAGE_FORWARD;
        }
        if (action.startsWith(COSMETIC_CATEGORY_ACTION_PREFIX)
            || action.startsWith(COSMETIC_DOMAIN_ACTION_PREFIX)
            || action.startsWith(COSMETIC_TAB_ACTION_PREFIX)
            || action.equals("player_cosmetics_favorites")) return HydraSounds.UiCue.CATEGORY;
        if (action.startsWith(COSMETIC_SELECT_ACTION_PREFIX)
            || action.equals("player_settings_lobby_time")
            || action.equals("player_edit_bridge_kit")
            || action.equals("player_edit_boss_rush_kit")) return HydraSounds.UiCue.SELECT;
        if (action.startsWith("back_")) return HydraSounds.UiCue.PAGE_BACK;
        if (action.startsWith("open_player_") || action.startsWith("player_open_")) return HydraSounds.UiCue.OPEN;
        return null;
    }

    private String themedEmptySlotAction(String theme, int rawSlot) {
        if (theme == null || rawSlot < 0) {
            return null;
        }
        if (theme.equalsIgnoreCase("settings_page_1") && rawSlot == 51) {
            return "player_settings_page_2";
        }
        if (theme.equalsIgnoreCase("settings_page_2") && rawSlot == 47) {
            return "player_settings_page_1";
        }
        if (theme.equalsIgnoreCase("navigator")) {
            if (containsSlot(rawSlot, 9, 10, 11, 12, 18, 19, 20, 21)) {
                return "noop";
            }
            if (containsSlot(rawSlot, 14, 15, 16, 17, 23, 24, 25, 26)) {
                return "player_open_hungergames";
            }
            if (containsSlot(rawSlot, 38, 39, 40, 41, 42, 47, 48, 49, 50, 51)) {
                return "player_minigame_roulette_join";
            }
            return null;
        }
        if (theme.equalsIgnoreCase("navigator_hunger_games")) {
            if (containsSlot(rawSlot, 18, 19, 20, 21, 27, 28, 29, 30)) {
                return "player_queue_join_hungergames_solo";
            }
            if (containsSlot(rawSlot, 23, 24, 25, 26, 32, 33, 34, 35)) {
                return "player_queue_join_hungergames_duos";
            }
            return null;
        }
        if (theme.equalsIgnoreCase("navigator_minigames")) {
            if (containsSlot(rawSlot, 42, 43, 44, 51, 52, 53)) {
                return "player_elytra_coming_soon";
            }
        }
        if (theme.equalsIgnoreCase("socials")) {
            if (containsSlot(rawSlot, 18, 19, 20, 27, 28, 29, 36, 37, 38)) {
                return "open_player_friends_menu";
            }
            if (containsSlot(rawSlot, 24, 25, 26, 33, 34, 35, 42, 43, 44)) {
                return "open_player_party_menu";
            }
            return null;
        }
        if (theme.equalsIgnoreCase("party_leader") && containsSlot(rawSlot, 28, 30, 32, 34)) {
            return "open_player_party_invite_friends";
        }
        return null;
    }

    private boolean containsSlot(int rawSlot, int... slots) {
        for (int slot : slots) {
            if (rawSlot == slot) {
                return true;
            }
        }
        return false;
    }

    public void handleBlockPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (inHand == null || inHand.getType() != Material.CHEST || !inHand.hasItemMeta()) {
            return;
        }
        ItemMeta meta = inHand.getItemMeta();
        String tableId = meta.getPersistentDataContainer().get(lootTableKey, PersistentDataType.STRING);
        if (tableId == null || tableId.isBlank()) {
            return;
        }

        Block placed = event.getBlockPlaced();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(placed.getState() instanceof Container container)) {
                return;
            }
            int generated = managedLootService == null
                ? 0
                : managedLootService.populateManagedContainer(placed, tableId, ManagedLootService.SOURCE_MANUAL, null);
            Inventory inventory = writableInventory(container);
            event.getPlayer().sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Generated "
                + generated + " item stacks for " + tableId);
            plugin.getLogger().info("Place event loot fill: player=" + event.getPlayer().getName()
                + ", table=" + tableId
                + ", generatedStacks=" + generated
                + ", invSize=" + inventory.getSize()
                + ", block=" + placed.getX() + "," + placed.getY() + "," + placed.getZ());
        });
    }

    private void executeAction(Player player, String action) {
        if (action.startsWith("admin_hg_start_local:")) {
            if (!player.hasPermission("hgloot.admin")) return;
            String[] target = action.substring("admin_hg_start_local:".length()).split(":", 2);
            QueueService queues = queueServiceOrNull();
            if (queues != null && target.length == 2 && (target[0].equals("duos") || target[0].equals("solos"))) {
                queues.forceStartHungerGames(target[0].equals("duos"), target[1], player);
            }
            return;
        }
        if (action.equals("open_admin_hg_utilities")) {
            openAdminHungerGamesUtilitiesMenu(player);
            return;
        }
        if (action.equals("open_admin_bossrush_tools")) {
            openAdminBossRushToolsMenu(player);
            return;
        }
        if (action.startsWith(HG_UTILITY_GIVE_ACTION_PREFIX)) {
            grantHungerGamesUtility(player, action.substring(HG_UTILITY_GIVE_ACTION_PREFIX.length()));
            return;
        }
        if (action.equals("open_loot")) {
            openLootMenu(player);
            return;
        }
        if (action.equals("open_player_menu")) {
            openPlayerMenu(player);
            return;
        }
        if (action.equals("player_queue_home")) {
            openPlayerMenu(player);
            return;
        }
        if (action.equals("player_open_play")) {
            openPlayerPlayMenu(player);
            return;
        }
        if (action.equals("player_open_hungergames")) {
            openPlayerHungerGamesMenu(player);
            return;
        }
        if (action.equals("player_open_market")) {
            openPlayerMarketMenu(player);
            return;
        }
        if (action.equals("open_player_settings_menu")) {
            openPlayerSettingsMenu(player);
            return;
        }
        if (action.equals("player_settings_page_1")) {
            openPlayerSettingsMenu(player, 1);
            return;
        }
        if (action.equals("player_settings_page_2")) {
            openPlayerSettingsMenu(player, 2);
            return;
        }
        if (action.equals("open_player_kit_editor")) {
            openPlayerKitEditorMenu(player);
            return;
        }
        if (action.equals("player_edit_bridge_kit")) {
            if (kitEditorService != null) kitEditorService.beginWithTransition(player, BridgeKitDefinition.ID);
            return;
        }
        if (action.equals("player_edit_boss_rush_kit")) {
            if (kitEditorService != null) kitEditorService.beginWithTransition(player, BossRushKitDefinition.ID);
            return;
        }
        if (action.equals("player_open_cosmetics")) {
            openPlayerLobbyCosmeticsMenu(player);
            return;
        }
        if (action.equals("player_open_social")) {
            openPlayerSocialMenu(player);
            return;
        }
        if (action.equals("open_player_party_menu")) {
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("player_stats")) {
            Bukkit.dispatchCommand(player, "queue status");
            player.sendMessage(ChatColor.GRAY + "Stats coming soon.");
            return;
        }
        if (action.equals("player_help")) {
            player.sendMessage(ChatColor.GRAY + "Use Play to queue.");
            return;
        }
        if (action.equals("open_player_minigames_menu")) {
            openPlayerMinigamesMenu(player);
            return;
        }
        if (action.equals("open_player_minigame_roulette")) {
            openPlayerMinigameRouletteMenu(player);
            return;
        }
        if (action.equals("player_minigame_roulette_join")) {
            if (minigameRouletteService == null) {
                player.sendMessage(ChatColor.RED + "Minigame Roulette is currently unavailable.");
            } else {
                if (minigameRouletteService.isQueued(player.getUniqueId())) {
                    minigameRouletteService.leaveQueue(player);
                } else {
                    minigameRouletteService.joinFromNavigator(player);
                }
            }
            player.closeInventory();
            return;
        }
        if (action.equals("player_spleef_join")) {
            if (!player.hasPermission("hgloot.admin")) {
                return;
            }
            Bukkit.dispatchCommand(player, "spleef join");
            player.closeInventory();
            return;
        }
        if (action.equals("player_voidroulette_join")) {
            if (!player.hasPermission("hgloot.admin")) {
                return;
            }
            Bukkit.dispatchCommand(player, "voidroulette join");
            player.closeInventory();
            return;
        }
        if (action.equals("player_market_casino")) {
            Bukkit.dispatchCommand(player, "ah");
            return;
        }
        if (action.equals("player_queue_join_solo")) {
            Bukkit.dispatchCommand(player, "br join solo");
            return;
        }
        if (action.equals("player_queue_join_duo")) {
            Bukkit.dispatchCommand(player, "br join duo");
            return;
        }
        if (action.equals("player_queue_join_hungergames")
            || action.equals("player_queue_join_hungergames_wild_west")) {
            Bukkit.dispatchCommand(player, "hg join");
            return;
        }
        if (action.equals("player_queue_join_hungergames_solo")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService == null) {
                player.sendMessage(ChatColor.RED + "Hunger Games is currently unavailable.");
            } else {
                queueService.joinHungerGamesSolo(player);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("player_queue_join_hungergames_duos")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService == null) {
                player.sendMessage(ChatColor.RED + "Hunger Games is currently unavailable.");
            } else {
                queueService.joinHungerGamesDuos(player);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("admin_hg_map_outcove")) {
            selectHungerGamesMap(player, "outcove");
            openAdminHungerGamesToolsMenu(player);
            return;
        }
        if (action.equals("admin_hg_map_wild_west")) {
            selectHungerGamesMap(player, "wild_west");
            openAdminHungerGamesToolsMenu(player);
            return;
        }
        if (action.equals("admin_hg_map_aztec_jungle")) {
            selectHungerGamesMap(player, "aztec_jungle");
            openAdminHungerGamesToolsMenu(player);
            return;
        }
        if (action.equals("admin_hg_map_random")) {
            selectHungerGamesMap(player, "random");
            openAdminHungerGamesToolsMenu(player);
            return;
        }
        if (action.equals("player_queue_leave")) {
            Bukkit.dispatchCommand(player, "queue leave");
            return;
        }
        if (action.equals("player_queue_status")) {
            Bukkit.dispatchCommand(player, "queue status");
            return;
        }
        if (action.equals("player_queue_help")) {
            Bukkit.dispatchCommand(player, "queue");
            return;
        }
        if (action.equals("player_hub")) {
            Bukkit.dispatchCommand(player, "hub");
            return;
        }
        if (action.equals("player_cosmetics_lobby")) {
            openPlayerCosmeticsRootMenu(player);
            return;
        }
        if (action.startsWith(COSMETIC_DOMAIN_ACTION_PREFIX)) {
            CosmeticDomain domain = CosmeticDomain.fromId(action.substring(COSMETIC_DOMAIN_ACTION_PREFIX.length()));
            openPlayerCosmeticDomainMenu(player, domain);
            return;
        }
        if (action.startsWith(COSMETIC_CATEGORY_ACTION_PREFIX)) {
            CosmeticCategory category = CosmeticCategory.fromId(action.substring(COSMETIC_CATEGORY_ACTION_PREFIX.length()));
            openPlayerCosmeticCategoryMenu(player, category);
            return;
        }
        if (action.startsWith(COSMETIC_PAGE_ACTION_PREFIX)) {
            String[] parts = action.substring(COSMETIC_PAGE_ACTION_PREFIX.length()).split(":", 2);
            CosmeticCategory category = CosmeticCategory.fromId(parts[0]);
            int page = 0;
            if (parts.length == 2) {
                try {
                    page = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    page = 0;
                }
            }
            openPlayerCosmeticCategoryMenu(player, category, page);
            return;
        }
        if (action.equals("player_cosmetics_favorites")) {
            openPlayerFavoritesMenu(player, 0);
            return;
        }
        if (action.startsWith(COSMETIC_FAVORITES_PAGE_ACTION_PREFIX)) {
            int page = 0;
            try {
                page = Integer.parseInt(action.substring(COSMETIC_FAVORITES_PAGE_ACTION_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                page = 0;
            }
            openPlayerFavoritesMenu(player, page);
            return;
        }
        if (action.startsWith(COSMETIC_TAB_ACTION_PREFIX)) {
            CosmeticMenuTab tab = CosmeticMenuTab.fromId(action.substring(COSMETIC_TAB_ACTION_PREFIX.length()));
            openPlayerCosmeticsMenu(player, tab == null ? CosmeticMenuTab.PROJECTILE_TRAILS : tab);
            return;
        }
        if (action.startsWith(COSMETIC_SELECT_ACTION_PREFIX)) {
            String cosmeticId = action.substring(COSMETIC_SELECT_ACTION_PREFIX.length());
            if (cosmeticId.startsWith("none@")) {
                CosmeticCategory category = CosmeticCategory.fromId(cosmeticId.substring("none@".length()));
                if (category == CosmeticCategory.HATS) {
                    equipHeadCosmetic(player, HeadCosmetic.NONE);
                } else if (category == CosmeticCategory.KILL_MESSAGES) {
                    clearKillMessage(player);
                } else {
                    clearSystemCosmetic(player, category);
                }
                openPlayerCosmeticCategoryMenu(player, category == null ? CosmeticCategory.PROJECTILE_TRAILS : category);
                return;
            }
            CosmeticDefinition rankStyle = findRankStyleDefinition(cosmeticId);
            if (rankStyle != null) {
                equipRankStyle(player, rankStyle);
                openPlayerCosmeticCategoryMenu(player, CosmeticCategory.RANK_STYLES);
                return;
            }
            CosmeticDefinition killMessage = findKillMessageDefinition(cosmeticId);
            if (killMessage != null) {
                equipKillMessage(player, killMessage);
                openPlayerCosmeticCategoryMenu(player, CosmeticCategory.KILL_MESSAGES);
                return;
            }
            CosmeticDefinition systemCosmetic = findSystemCosmeticDefinition(cosmeticId);
            if (systemCosmetic != null) {
                equipSystemCosmetic(player, systemCosmetic);
                openPlayerCosmeticCategoryMenu(player, systemCosmetic.category);
                return;
            }
            HeadCosmetic cosmetic = findHeadCosmetic(cosmeticId);
            if (cosmetic.isLegendarySkin()) {
                equipLegendarySkin(player, cosmetic);
                openPlayerCosmeticCategoryMenu(player, CosmeticCategory.LEGENDARY_SKINS);
            } else {
                equipHeadCosmetic(player, cosmetic);
                openPlayerCosmeticCategoryMenu(player, CosmeticCategory.HATS);
            }
            return;
        }
        // Legacy actions kept for compatibility with stale client-side menu items.
        if (action.equals("player_cosmetic_head_none")) {
            equipHeadCosmetic(player, HeadCosmetic.NONE);
            openPlayerCosmeticCategoryMenu(player, CosmeticCategory.HATS);
            return;
        }
        if (action.equals("player_cosmetic_head_coal")) {
            equipHeadCosmetic(player, findHeadCosmetic("coal"));
            openPlayerCosmeticCategoryMenu(player, CosmeticCategory.HATS);
            return;
        }
        if (action.equals("player_cosmetic_head_halo")) {
            equipHeadCosmetic(player, findHeadCosmetic("halo"));
            openPlayerCosmeticCategoryMenu(player, CosmeticCategory.HATS);
            return;
        }
        if (action.equals("player_cosmetic_head_glasses")) {
            equipHeadCosmetic(player, findHeadCosmetic("glasses"));
            openPlayerCosmeticCategoryMenu(player, CosmeticCategory.HATS);
            return;
        }
        if (action.equals("back_player_cosmetics")) {
            openPlayerCosmeticsRootMenu(player);
            return;
        }
        if (action.equals("player_party")) {
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("open_player_party_invite_friends")) {
            openPlayerPartyInviteFriendsMenu(player);
            return;
        }
        if (action.equals("open_player_party_requests")) {
            openPlayerPartyRequestsMenu(player);
            return;
        }
        if (action.equals("open_player_party_members")) {
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("open_player_party_details")) {
            openPlayerPartyDetailsMenu(player);
            return;
        }
        if (action.equals("back_player_social")) {
            openPlayerSocialMenu(player);
            return;
        }
        if (action.equals("back_player_party")) {
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("player_party_create")) {
            Bukkit.dispatchCommand(player, "party create");
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("player_party_leave")) {
            Bukkit.dispatchCommand(player, "party leave");
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("player_party_disband")) {
            Bukkit.dispatchCommand(player, "party disband");
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("player_party_visibility_toggle")) {
            PartyService.VisibilityResult result = partyService.toggleVisibility(player.getUniqueId());
            switch (result) {
                case MADE_PUBLIC -> player.sendMessage(ChatColor.GREEN + "[Party] Your party is now public.");
                case MADE_PRIVATE -> player.sendMessage(ChatColor.YELLOW + "[Party] Your party is now private.");
                case NOT_LEADER -> player.sendMessage(ChatColor.RED + "[Party] Only the leader can change party visibility.");
                case NOT_IN_PARTY -> player.sendMessage(ChatColor.RED + "[Party] Create a party first.");
                case LOCKED -> player.sendMessage(ChatColor.RED + "[Party] Party settings cannot change while queued.");
            }
            openPlayerPartyMenu(player);
            return;
        }
        if (action.equals("open_player_friends_menu")) {
            openPlayerFriendsMenu(player);
            return;
        }
        if (action.equals("open_player_friends_list")) {
            openPlayerFriendsListMenu(player);
            return;
        }
        if (action.equals("open_player_friends_requests")) {
            openPlayerFriendRequestsMenu(player);
            return;
        }
        if (action.equals("open_player_friends_add")) {
            openPlayerAddFriendMenu(player);
            return;
        }
        if (action.equals("open_player_friends_remove")) {
            openPlayerRemoveFriendMenu(player);
            return;
        }
        if (action.equals("back_player_menu")) {
            player.closeInventory();
            return;
        }
        if (action.equals("back_player_friends")) {
            openPlayerFriendsMenu(player);
            return;
        }
        if (action.equals("open_audit")) {
            openAuditMenu(player);
            return;
        }
        if (action.equals("open_admin")) {
            openMainMenu(player);
            return;
        }
        if (action.equals("open_admin_br_tools")) {
            openAdminBattleRoyaleToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_hg_tools")) {
            openAdminHungerGamesToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_minigames_tools")) {
            openAdminMinigamesToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_voidroulette_tools")) {
            openAdminVoidRouletteToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_voidroulette_test")) {
            openAdminVoidRouletteTestMenu(player);
            return;
        }
        if (action.equals("back_admin_voidroulette")) {
            openAdminVoidRouletteToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_bridge_tools")) {
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_voidroulette_status")) {
            Bukkit.dispatchCommand(player, "admin voidroulette status");
            return;
        }
        if (action.equals("admin_voidroulette_forcestart")) {
            Bukkit.dispatchCommand(player, "admin voidroulette forcestart");
            player.closeInventory();
            return;
        }
        if (action.equals("admin_voidroulette_stop")) {
            Bukkit.dispatchCommand(player, "admin voidroulette stop");
            return;
        }
        if (action.equals("admin_voidroulette_cleanup_status")) {
            Bukkit.dispatchCommand(player, "admin voidroulette cleanup status");
            return;
        }
        if (action.equals("admin_voidroulette_cleanup_on")) {
            Bukkit.dispatchCommand(player, "admin voidroulette cleanup on");
            return;
        }
        if (action.equals("admin_voidroulette_cleanup_off")) {
            Bukkit.dispatchCommand(player, "admin voidroulette cleanup off");
            return;
        }
        if (action.equals("admin_voidroulette_cleanup_purge")) {
            Bukkit.dispatchCommand(player, "admin voidroulette cleanup purge");
            return;
        }
        if (action.startsWith("admin_voidroulette_test_")) {
            String testAction = action.substring("admin_voidroulette_test_".length());
            Bukkit.dispatchCommand(player, "admin voidroulette test " + testAction);
            openAdminVoidRouletteTestMenu(player);
            return;
        }
        if (action.equals("admin_bridge_status")) {
            Bukkit.dispatchCommand(player, "admin bridge status");
            return;
        }
        if (action.equals("admin_bridge_forcestart")) {
            Bukkit.dispatchCommand(player, "admin bridge forcestart");
            player.closeInventory();
            return;
        }
        if (action.equals("admin_bridge_stop")) {
            Bukkit.dispatchCommand(player, "admin bridge stop");
            return;
        }
        if (action.equals("admin_bridge_autowin_toggle")) {
            Bukkit.dispatchCommand(player, "admin bridge autowin toggle");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_map_random")) {
            Bukkit.dispatchCommand(player, "admin bridge map random");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_map_bridge")) {
            Bukkit.dispatchCommand(player, "admin bridge map bridge");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_map_bridge_ocean")) {
            Bukkit.dispatchCommand(player, "admin bridge map bridge_ocean");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_map_outer_end")) {
            Bukkit.dispatchCommand(player, "admin bridge map outer_end");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_cleanup_on")) {
            Bukkit.dispatchCommand(player, "admin bridge cleanup on");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_cleanup_off")) {
            Bukkit.dispatchCommand(player, "admin bridge cleanup off");
            openAdminBridgeToolsMenu(player);
            return;
        }
        if (action.equals("admin_bridge_cleanup_purge")) {
            Bukkit.dispatchCommand(player, "admin bridge cleanup purge");
            return;
        }
        if (action.equals("open_admin_spleef_tools")) {
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_warnings_tools")) {
            openAdminWarningsMenu(player);
            return;
        }
        if (action.equals("admin_reports_list")) {
            Bukkit.dispatchCommand(player, "reports list");
            return;
        }
        if (action.equals("admin_reports_clear")) {
            Bukkit.dispatchCommand(player, "reports clear");
            openAdminWarningsMenu(player);
            return;
        }
        if (action.equals("admin_warn_help")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /warn <player> <reason>");
            return;
        }
        if (action.equals("admin_warnings_help")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /warnings <player> [clear]");
            return;
        }
        if (action.equals("admin_mute_help")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /mute <player> <10m|1h|1d|perm> <reason>");
            return;
        }
        if (action.equals("admin_unmute_help")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /unmute <player>");
            return;
        }
        if (action.equals("admin_kick_help")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /kick <player> [reason]");
            return;
        }
        if (action.equals("admin_spleef_status")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef status");
            return;
        }
        if (action.equals("admin_spleef_forcestart")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef forcestart");
            player.closeInventory();
            return;
        }
        if (action.equals("admin_spleef_stop")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef stop");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_clearqueue")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef clearqueue");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_floordecay")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef floordecay");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_items")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef items");
            return;
        }
        if (action.equals("admin_spleef_map_random")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map random");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_base")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map base");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_rings")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map rings");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_fourcorners")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map fourcorners");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_stars")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map stars");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_desertclassic")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map desertclassic");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_desertrings")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map desertrings");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_desertfourcorners")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map desertfourcorners");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_desertstars")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map desertstars");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_desertlayers")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map desertlayers");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("admin_spleef_map_snowylayers")) {
            Bukkit.dispatchCommand(player, "admin minigames spleef map snowylayers");
            openAdminSpleefToolsMenu(player);
            return;
        }
        if (action.equals("player_elytra_coming_soon")) {
            player.sendMessage(ChatColor.YELLOW + "Elytra Racing is coming soon.");
            return;
        }
        if (action.equals("open_admin_match_tools")) {
            openAdminMatchToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_boss_tools")) {
            openAdminBossToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_loot_tools")) {
            openAdminLootToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_combat_tools")) {
            openAdminCombatToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_economy_tools")) {
            openAdminEconomyToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_wallet_tools")) {
            openAdminWalletToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_cosmetics_tools")) {
            openAdminCosmeticsToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_cosmetics_player_list")) {
            openAdminCosmeticsPlayerListMenu(player);
            return;
        }
        if (action.equals("open_admin_rank_player_list")) {
            openAdminRankPlayerListMenu(player);
            return;
        }
        if (action.equals("open_admin_dev_tools")) {
            openAdminDevToolsMenu(player);
            return;
        }
        if (action.equals("open_admin_leaderboard_tools")) {
            openAdminLeaderboardToolsMenu(player);
            return;
        }
        if (action.equals("close_menu")) {
            player.closeInventory();
            return;
        }
        if (action.equals("leaderboard_remove_viewed")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService != null) {
                queueService.removeViewedDebugLeaderboard(player);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("leaderboard_clear_nearby")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService != null) {
                queueService.clearNearbyDebugLeaderboards(player);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("leaderboard_refresh")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService != null) {
                queueService.refreshLeaderboards(player);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("leaderboard_debug_state")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService != null) {
                queueService.sendLeaderboardDebug(player);
            }
            player.closeInventory();
            return;
        }
        if (action.startsWith("admin_debug_summon_board:")) {
            QueueService queueService = queueServiceOrNull();
            String rawType = action.substring("admin_debug_summon_board:".length());
            QueueService.DebugLeaderboardType type = QueueService.DebugLeaderboardType.fromKey(rawType);
            if (queueService != null && type != null) {
                queueService.summonDebugLeaderboard(player, type);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("admin_debug_clear_kills_board")) {
            QueueService queueService = queueServiceOrNull();
            if (queueService != null) {
                queueService.clearNearbyDebugLeaderboards(player);
            }
            player.closeInventory();
            return;
        }
        if (action.equals("open_admin_legendary_tools")) {
            openAdminCombatToolsMenu(player);
            return;
        }
        if (action.equals("open_beta_legendary_menu")) {
            openBetaLegendaryMenu(player);
            return;
        }
        if (action.startsWith(ADMIN_COSMETICS_PLAYER_ACTION_PREFIX)) {
            UUID targetId = parseUuid(action.substring(ADMIN_COSMETICS_PLAYER_ACTION_PREFIX.length()));
            if (targetId != null) {
                openAdminCosmeticPlayerMenu(player, targetId);
            }
            return;
        }
        if (action.startsWith(ADMIN_RANK_PLAYER_ACTION_PREFIX)) {
            UUID targetId = parseUuid(action.substring(ADMIN_RANK_PLAYER_ACTION_PREFIX.length()));
            if (targetId != null) {
                openAdminRankPlayerMenu(player, targetId);
            }
            return;
        }
        if (action.startsWith(ADMIN_COSMETICS_CATEGORY_ACTION_PREFIX)) {
            String raw = action.substring(ADMIN_COSMETICS_CATEGORY_ACTION_PREFIX.length());
            int separator = raw.indexOf(':');
            if (separator >= 0) {
                UUID targetId = parseUuid(raw.substring(0, separator));
                CosmeticCategory category = CosmeticCategory.fromId(raw.substring(separator + 1));
                if (targetId != null) {
                    openAdminCosmeticCategoryMenu(player, targetId, category);
                }
            }
            return;
        }
        if (action.startsWith(ADMIN_RANK_SET_ACTION_PREFIX)) {
            String raw = action.substring(ADMIN_RANK_SET_ACTION_PREFIX.length());
            int separator = raw.indexOf(':');
            if (separator >= 0) {
                UUID targetId = parseUuid(raw.substring(0, separator));
                String mode = raw.substring(separator + 1);
                if (targetId != null && applyRank(player, targetId, mode)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            openAdminRankPlayerMenu(player, targetId);
                        }
                    }, 4L);
                }
            }
            return;
        }
        if (action.startsWith(ADMIN_COSMETICS_TOGGLE_ACTION_PREFIX)) {
            String raw = action.substring(ADMIN_COSMETICS_TOGGLE_ACTION_PREFIX.length());
            String[] parts = raw.split(":", 3);
            if (parts.length >= 2) {
                UUID targetId = parseUuid(parts[0]);
                CosmeticDefinition cosmetic = findCosmeticDefinitionById(parts[1]);
                CosmeticCategory category = parts.length >= 3 ? CosmeticCategory.fromId(parts[2]) : CosmeticCategory.PROJECTILE_TRAILS;
                if (targetId != null && cosmetic != null && cosmetic.category != CosmeticCategory.RANK_STYLES) {
                    boolean owned = ownsCosmetic(targetId, cosmetic.id);
                    boolean changed = owned
                        ? removeCosmeticOwnership(targetId, cosmetic.id)
                        : addCosmeticOwnership(targetId, cosmetic.id);
                    if (changed) {
                        player.sendMessage(
                            HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
                                + (owned ? "Locked " : "Unlocked ")
                                + ChatColor.YELLOW + cosmetic.displayName
                                + ChatColor.GRAY + " for " + ChatColor.YELLOW + cosmeticTargetDisplayName(targetId) + ChatColor.GRAY + "."
                        );
                    }
                    openAdminCosmeticCategoryMenu(player, targetId, category);
                }
            }
            return;
        }
        if (action.equals("open_combat_master_menu")) {
            openCombatMasterMenu(player);
            return;
        }
        if (action.equals("open_withered_warden_menu")
            || action.equals("open_withered_vault_menu") || action.equals("legacy_boss_system_removed")) {
            player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Legacy boss systems were removed.");
            return;
        }
        if (action.equals("open_hydra_weapons_menu")) {
            openHydraWeaponsMenu(player);
            return;
        }
        if (action.equals("open_br_weapons_menu")) {
            openBrWeaponsMenu(player);
            return;
        }
        if (action.equals("open_hg_hydra_weapons_menu")) {
            openHydraHungerGamesWeaponsMenu(player);
            return;
        }
        if (action.startsWith(HYDRA_WEAPON_GIVE_ACTION_PREFIX)) {
            String weaponId = action.substring(HYDRA_WEAPON_GIVE_ACTION_PREFIX.length());
            grantHydraWeapon(player, weaponId, false);
            return;
        }
        if (action.startsWith(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX)) {
            String weaponId = action.substring(HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX.length());
            grantHydraWeapon(player, weaponId, true);
            return;
        }
        if (action.startsWith(BETA_LEGENDARY_GIVE_ACTION_PREFIX)) {
            String weaponId = action.substring(BETA_LEGENDARY_GIVE_ACTION_PREFIX.length());
            grantBetaLegendary(player, weaponId);
            return;
        }
        if (action.equals("open_tools")) {
            openToolsMenu(player);
            return;
        }
        if (action.equals("open_custom") || action.equals("open_hydra")) {
            openCustomMenu(player);
            return;
        }
        if (action.equals("open_fun_stuff")) {
            openFunStuffMenu(player);
            return;
        }
        if (action.equals("open_radius_fill_category")) {
            openRadiusFillCategoryMenu(player);
            return;
        }
        if (action.equals("open_wave_menu")) {
            openWaveMenu(player);
            return;
        }
        if (action.equals("open_structure_tools")) {
            openStructureToolsMenu(player);
            return;
        }
        if (action.equals("back_main")) {
            openMainMenu(player);
            return;
        }
        if (action.equals("back_admin_main")) {
            openMainMenu(player);
            return;
        }
        if (action.equals("back_admin_dev")) {
            openAdminDevToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_match")) {
            openAdminMatchToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_br")) {
            openAdminBattleRoyaleToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_hg")) {
            openAdminHungerGamesToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_minigames")) {
            openAdminMinigamesToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_cosmetics_tools")) {
            openAdminCosmeticsToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_cosmetics_player_list")) {
            openAdminCosmeticsPlayerListMenu(player);
            return;
        }
        if (action.equals("back_admin_loot_tools")) {
            openAdminLootToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_combat")) {
            openAdminCombatToolsMenu(player);
            return;
        }
        if (action.equals("back_admin_bosses")) {
            openAdminBossToolsMenu(player);
            return;
        }
        if (action.equals("back_admin")) {
            openMainMenu(player);
            return;
        }
        if (action.equals("back_custom")) {
            openAdminCombatToolsMenu(player);
            return;
        }
        if (action.equals("back_loot")) {
            openLootMenu(player);
            return;
        }
        if (action.equals("back_audit")) {
            openAuditMenu(player);
            return;
        }
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.equals("noop")) {
            return;
        }
        if (action.equals("admin_reset")) {
            player.getInventory().clear();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Player reset complete!");
            return;
        }
        if (action.equals("admin_reload_tables")) {
            lootTableService.loadAllTables();
            player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Reloaded loot tables ("
                + lootTableService.loadedTableCount() + ")");
            player.sendMessage(ChatColor.GRAY + "External: " + lootTableService.loadedFromExternalCount()
                + ", Embedded: " + lootTableService.loadedFromEmbeddedCount()
                + ", Missing: " + lootTableService.missingTableCount());
            return;
        }
        if (action.equals("admin_table_count")) {
            player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.AQUA + "Loaded tables: "
                + lootTableService.loadedTableCount() + ChatColor.GRAY + " (External: "
                + lootTableService.loadedFromExternalCount() + ", Embedded: "
                + lootTableService.loadedFromEmbeddedCount() + ", Missing: "
                + lootTableService.missingTableCount() + ")");
            player.sendMessage(ChatColor.DARK_GRAY + "Folder: " + lootTableService.externalLootFolderPath());
            return;
        }
        if (action.equals("admin_trigger_win_check")) {
            Bukkit.dispatchCommand(player, "admin br forcewin");
            return;
        }
        if (action.equals("admin_auto_win_on")) {
            Bukkit.dispatchCommand(player, "admin br autowin on");
            openAdminMatchToolsMenu(player);
            return;
        }
        if (action.equals("admin_auto_win_off")) {
            Bukkit.dispatchCommand(player, "admin br autowin off");
            openAdminMatchToolsMenu(player);
            return;
        }
        if (action.equals("admin_auto_win_toggle")) {
            Bukkit.dispatchCommand(player, "admin br autowin toggle");
            openAdminMatchToolsMenu(player);
            return;
        }
        if (action.equals("admin_auto_win_toggle_br")) {
            Bukkit.dispatchCommand(player, "admin br autowin toggle");
            openAdminBattleRoyaleToolsMenu(player);
            return;
        }
        if (action.startsWith("player_settings_toggle:")) {
            PlayerSettingsService.Toggle setting = PlayerSettingsService.Toggle.byKey(
                action.substring("player_settings_toggle:".length()));
            if (setting == null) return;
            HGLootPlugin hydra = (HGLootPlugin) plugin;
            if (setting == PlayerSettingsService.Toggle.LOBBY_FLIGHT
                && !hydra.playerSettingsService().canUseFlightToggle(player)) {
                refreshPlayerSettingsMenu(player);
                return;
            }
            PlayerSettingsService settings = hydra.playerSettingsService();
            if (setting == PlayerSettingsService.Toggle.LOBBY_FLIGHT) {
                if (!settings.setFlightEnabled(player, !settings.flightEnabled(player))) {
                    player.sendMessage(ChatColor.RED + "Flight is unavailable here or your chest slot is occupied.");
                    refreshPlayerSettingsMenu(player);
                    return;
                }
            } else {
                settings.set(player, setting, !settings.enabled(player.getUniqueId(), setting));
            }
            if (setting == PlayerSettingsService.Toggle.PROXY_CHAT && hydra.hydraVoiceChatService() != null) {
                hydra.hydraVoiceChatService().onProxyPreferenceChanged(player);
            }
            if (setting == PlayerSettingsService.Toggle.TEAM_GLOW) {
                if (hydra.bridgeService() != null) hydra.bridgeService().refreshTeamGlowPreference();
                if (hydra.queueService() != null) hydra.queueService().refreshTeamGlowPreference();
            }
            if (setting == PlayerSettingsService.Toggle.SPECTATOR_VISIBILITY) {
                if (hydra.queueService() != null) hydra.queueService().resyncSpectatorVisibility(player.getWorld());
                if (hydra.bridgeService() != null) hydra.bridgeService().refreshSpectatorVisibilityPreference();
                if (hydra.spleefService() != null) hydra.spleefService().refreshSpectatorVisibilityPreference(player.getUniqueId());
                if (hydra.voidRouletteService() != null) hydra.voidRouletteService().refreshSpectatorVisibilityPreference();
                if (hydra.bossRushService() != null) hydra.bossRushService().refreshSpectatorVisibilityPreference();
            }
            refreshPlayerSettingsMenu(player);
            return;
        }
        if (action.equals("player_settings_lobby_time")) {
            HGLootPlugin hydra = (HGLootPlugin) plugin;
            PlayerSettingsService settings = hydra.playerSettingsService();
            settings.setLobbyTime(player, settings.lobbyTime(player.getUniqueId()).next());
            refreshPlayerSettingsMenu(player);
            return;
        }
        if (action.equals("admin_queue_status_br")) {
            Bukkit.dispatchCommand(player, "br status");
            return;
        }
        if (action.equals("admin_queue_status_hg")) {
            Bukkit.dispatchCommand(player, "hg status");
            return;
        }
        if (action.equals("admin_queue_status")) {
            Bukkit.dispatchCommand(player, "br status");
            Bukkit.dispatchCommand(player, "hg status");
            return;
        }
        if (action.equals("admin_force_start_br_solo")) {
            Bukkit.dispatchCommand(player, "admin br forcestart");
            return;
        }
        if (action.equals("admin_force_start_br_duo")) {
            Bukkit.dispatchCommand(player, "admin br forcestart duo");
            return;
        }
        if (action.equals("admin_force_start_hg")) {
            openAdminHungerGamesStartMenu(player);
            return;
        }
        if (action.equals("admin_clear_br_queue")) {
            Bukkit.dispatchCommand(player, "admin br clear all");
            return;
        }
        if (action.equals("admin_clear_hg_queue")) {
            Bukkit.dispatchCommand(player, "admin hg clear");
            return;
        }
        if (action.equals("admin_force_start_match")) {
            Bukkit.dispatchCommand(player, "admin br forcestart");
            return;
        }
        if (action.equals("admin_clear_queue")) {
            Bukkit.dispatchCommand(player, "admin br clear all");
            Bukkit.dispatchCommand(player, "admin hg clear");
            return;
        }
        if (action.equals("admin_give_player_compass")) {
            if (!player.hasPermission("hgloot.playermenu")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use player menu tools.");
                return;
            }
            boolean success = ensurePlayerMenuCompass(player);
            if (success) {
                player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Hub hotbar items granted.");
            } else {
                player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Failed to grant hub hotbar items.");
            }
            return;
        }
        if (action.equals("admin_arm_nuke")) {
            return;
        }
        if (action.equals("admin_arm_stab")) {
            return;
        }
        if (action.equals("admin_disarm_shot")) {
            return;
        }
        if (action.equals("admin_spawn_combat_master")) {
            Bukkit.dispatchCommand(player, "admin bosses summon combat_master");
            return;
        }
        if (action.equals("admin_force_spawn_combat_master")) {
            Bukkit.dispatchCommand(player, "admin bosses summon combat_master");
            return;
        }
        if (action.equals("admin_combat_master_debug")) {
            Bukkit.dispatchCommand(player, "admin bosses debug");
            return;
        }
        if (action.equals("admin_combat_master_reset")) {
            Bukkit.dispatchCommand(player, "admin bosses reset");
            return;
        }
        if (action.equals("admin_bosses_list")) {
            Bukkit.dispatchCommand(player, "admin bosses list");
            return;
        }
        if (action.equals("combatmaster_phase:artillery")) {
            Bukkit.dispatchCommand(player, "admin bosses phase combat_master artillery");
            return;
        }
        if (action.equals("combatmaster_phase:bulwark")) {
            Bukkit.dispatchCommand(player, "admin bosses phase combat_master bulwark");
            return;
        }
        if (action.equals("combatmaster_phase:discipline")) {
            Bukkit.dispatchCommand(player, "admin bosses phase combat_master discipline");
            return;
        }
        if (action.equals("combatmaster_phase:final")) {
            Bukkit.dispatchCommand(player, "admin bosses phase combat_master final");
            return;
        }
        if (action.equals("warden_set_altar")) {
            Bukkit.dispatchCommand(player, "witheredwarden setaltar");
            return;
        }
        if (action.equals("warden_give_key_self")) {
            Bukkit.dispatchCommand(player, "witheredwarden givekey");
            return;
        }
        if (action.equals("warden_status")) {
            Bukkit.dispatchCommand(player, "witheredwarden status");
            return;
        }
        if (action.equals("warden_spawn")) {
            Bukkit.dispatchCommand(player, "witheredwarden spawn");
            return;
        }
        if (action.equals("warden_despawn")) {
            Bukkit.dispatchCommand(player, "witheredwarden despawn");
            return;
        }
        if (action.equals("warden_reset_keys")) {
            Bukkit.dispatchCommand(player, "witheredwarden resetkeys");
            return;
        }
        if (action.equals("warden_debug")) {
            Bukkit.dispatchCommand(player, "witheredwarden debug");
            return;
        }
        if (action.startsWith("warden_insert:")) {
            String keyhole = action.substring("warden_insert:".length());
            if (!keyhole.isBlank()) {
                Bukkit.dispatchCommand(player, "witheredwarden insert " + keyhole);
            }
            return;
        }
        if (action.equals("vault_set_door")) {
            Bukkit.dispatchCommand(player, "witheredwarden setvaultdoor");
            return;
        }
        if (action.equals("vault_build_interior")) {
            Bukkit.dispatchCommand(player, "witheredwarden buildvault");
            return;
        }
        if (action.equals("vault_give_key")) {
            Bukkit.dispatchCommand(player, "witheredwarden givevaultkey");
            return;
        }
        if (action.equals("vault_status")) {
            Bukkit.dispatchCommand(player, "witheredwarden vaultstatus");
            return;
        }
        if (action.equals("vault_reset_keys")) {
            Bukkit.dispatchCommand(player, "witheredwarden resetvaultkeys");
            return;
        }
        if (action.equals("vault_open")) {
            Bukkit.dispatchCommand(player, "witheredwarden openvault");
            return;
        }
        if (action.equals("vault_spawn_guards")) {
            Bukkit.dispatchCommand(player, "witheredwarden guardspawn");
            return;
        }
        if (action.equals("vault_clear_guards")) {
            Bukkit.dispatchCommand(player, "witheredwarden guardclear");
            return;
        }
        if (action.startsWith("vault_insert:")) {
            String slot = action.substring("vault_insert:".length());
            if (!slot.isBlank()) {
                Bukkit.dispatchCommand(player, "witheredwarden insertvault " + slot);
            }
            return;
        }
        if (action.equals("admin_give_sonic_crossbow")) {
            return;
        }
        if (action.equals("admin_give_railgun_crossbow")) {
            if (!hasFunAdminPermission(player)) {
                denyFunAccess(player);
                return;
            }
            giveRailgunCrossbow(player);
            return;
        }
        if (action.equals("admin_give_voidbreaker")) {
            if (!hasFunAdminPermission(player)) {
                denyFunAccess(player);
                return;
            }
            giveVoidbreaker(player);
            return;
        }
        if (action.equals("hydra_give_sonic_crossbow")) {
            return;
        }
        if (action.equals("hydra_give_railgun_crossbow")) {
            if (!hasFunAdminPermission(player)) {
                denyFunAccess(player);
                return;
            }
            giveRailgunCrossbow(player);
            return;
        }
        if (action.equals("hydra_give_voidbreaker")) {
            if (!hasFunAdminPermission(player)) {
                denyFunAccess(player);
                return;
            }
            giveVoidbreaker(player);
            return;
        }
        if (action.equals("hydra_spawn_venom_zombie") || action.equals("custom_spawn_venom_zombie")) {
            spawnVenomZombie(player);
            return;
        }
        if (action.equals("hydra_info")) {
            player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.AQUA + "" + ChatColor.BOLD
                + "Hydra Network");
            return;
        }
        if (action.equals("legacy_wave_deprecated")) {
            player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "The old Combat Master wave system is deprecated.");
            player.sendMessage(ChatColor.GRAY + "Combat Master and its future waves will be rebuilt as one boss flow.");
            return;
        }
        if (action.equals("wave_status")) {
            Bukkit.dispatchCommand(player, "hgwave status");
            return;
        }
        if (action.equals("wave_ready")) {
            Bukkit.dispatchCommand(player, "hgwave ready");
            return;
        }
        if (action.equals("wave_reset")) {
            Bukkit.dispatchCommand(player, "hgwave reset");
            return;
        }
        if (action.equals("struct_list")) {
            Bukkit.dispatchCommand(player, "admin br structure list");
            return;
        }
        if (action.equals("struct_spawn_random")) {
            Bukkit.dispatchCommand(player, "admin br structure random");
            return;
        }
        if (action.equals("struct_grid")) {
            Bukkit.dispatchCommand(player, "admin br structure grid 9");
            return;
        }
        if (action.equals("struct_bundle")) {
            Bukkit.dispatchCommand(player, "admin br structure bundle");
            return;
        }
        if (action.equals("struct_preview")) {
            Bukkit.dispatchCommand(player, "admin br structure preview");
            return;
        }
        if (action.equals("struct_validate")) {
            Bukkit.dispatchCommand(player, "admin br structure validate 64");
            return;
        }
        if (action.equals("struct_clear")) {
            Bukkit.dispatchCommand(player, "admin br structure clear 64");
            return;
        }
        if (action.equals("struct_debug")) {
            Bukkit.dispatchCommand(player, "admin br structure help");
            return;
        }
        if (action.equals("admin_econ_addsilver_self_large")) {
            Bukkit.dispatchCommand(player, "casino addcoins " + player.getName() + " 1000");
            return;
        }
        if (action.equals("admin_econ_addsilver_self")) {
            Bukkit.dispatchCommand(player, "casino addcoins " + player.getName() + " 250");
            return;
        }
        if (action.equals("admin_econ_log")) {
            Bukkit.dispatchCommand(player, "economylog recent");
            return;
        }
        if (action.equals("admin_econ_audit_self")) {
            Bukkit.dispatchCommand(player, "casino audit " + player.getName());
            return;
        }
        if (action.equals("admin_econ_shard_claim_self")) {
            Bukkit.dispatchCommand(player, "shard open");
            return;
        }
        if (action.equals("admin_wallet_add_xp_self_500")) {
            Bukkit.dispatchCommand(player, "admin wallet add " + player.getName() + " levelxp 500");
            return;
        }
        if (action.equals("admin_wallet_add_shard_self_1")) {
            Bukkit.dispatchCommand(player, "admin wallet add " + player.getName() + " shards 1");
            return;
        }
        if (action.equals("admin_wallet_add_fragment_self_1")) {
            Bukkit.dispatchCommand(player, "admin wallet add " + player.getName() + " fragments 1");
            return;
        }
        if (action.equals("admin_wallet_help")) {
            player.sendMessage(ChatColor.AQUA + "[Progression] " + ChatColor.GRAY + "/admin set <money|kills|wins|shards|level> [player] <amount>");
            player.sendMessage(ChatColor.AQUA + "[Progression] " + ChatColor.GRAY + "/admin cosmetics reset all");
            player.sendMessage(ChatColor.AQUA + "[Progression] " + ChatColor.GRAY + "/shard open");
            return;
        }
        if (action.equals("admin_econ_refund_placeholder")) {
            player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Listing refund UI is not implemented yet.");
            return;
        }

        String[] parts = action.split(":");
        if (parts.length < 2) {
            return;
        }

        switch (parts[0]) {
            case "open_category" -> handleOpenCategory(player, parts);
            case "open_audit_category" -> handleOpenAuditCategory(player, parts);
            case "open_action" -> handleOpenAction(player, parts);
            case "open_player_friend_request_actions" -> handleOpenPlayerFriendRequestActions(player, parts);
            case "open_player_party_member_actions" -> handleOpenPlayerPartyMemberActions(player, parts);
            case "open_player_party_request_actions" -> handleOpenPlayerPartyRequestActions(player, parts);
            case "player_friends_send" -> handlePlayerFriendsSend(player, parts);
            case "player_friends_accept" -> handlePlayerFriendsAccept(player, parts);
            case "player_friends_deny" -> handlePlayerFriendsDeny(player, parts);
            case "player_friends_remove" -> handlePlayerFriendsRemove(player, parts);
            case "player_friends_view" -> handlePlayerFriendsView(player, parts);
            case "player_friends_unblock" -> handlePlayerFriendsUnblock(player, parts);
            case "friends_page" -> handleFriendsPage(player, parts);
            case "player_party_invite" -> handlePlayerPartyInvite(player, parts);
            case "player_party_accept" -> handlePlayerPartyAccept(player, parts);
            case "player_party_deny" -> handlePlayerPartyDeny(player, parts);
            case "player_party_kick" -> handlePlayerPartyKick(player, parts);
            case "player_party_promote" -> handlePlayerPartyPromote(player, parts);
            case "back_category" -> handleBackCategory(player, parts);
            case "admin_line" -> handleAdminLine(player, parts);
            case "admin_grid" -> handleAdminGrid(player, parts);
            case "admin_radius" -> handleAdminRadius(player, parts);
            case "admin_refill" -> handleAdminRefill(player, parts);
            case "admin_sim" -> handleAdminSim(player, parts);
            case "wave_start" -> handleWaveStart(player, parts);
            case "open_radius_fill_table" -> handleOpenRadiusFillTable(player, parts);
            case "open_radius_fill_radius" -> handleOpenRadiusFillRadius(player, parts);
            case "back_radius_fill_table" -> handleBackRadiusFillTable(player, parts);
            case "apply_radius_fill" -> handleApplyRadiusFill(player, parts);
            case "give" -> handleGive(player, parts);
            case "spawn" -> handleSpawn(player, parts);
            case "audit" -> handleAudit(player, parts);
            default -> {
            }
        }
    }

    private void handleOpenCategory(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        categoryOpt.ifPresent(category -> openCategoryMenu(player, category));
    }

    private void handleOpenAuditCategory(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        categoryOpt.ifPresent(category -> openAuditCategoryMenu(player, category));
    }

    private void handleOpenAction(Player player, String[] parts) {
        if (parts.length != 3) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        if (categoryOpt.isEmpty()) {
            return;
        }
        int index = parsePositive(parts[2]);
        if (index <= 0 || index > categoryOpt.get().chestCount()) {
            return;
        }
        openActionMenu(player, categoryOpt.get(), index);
    }

    private void handleOpenPlayerFriendRequestActions(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID requesterId = parseUuid(parts[1]);
        if (requesterId == null) {
            return;
        }
        if (!friendService.getIncomingRequests(player.getUniqueId()).contains(requesterId)) {
            player.sendMessage(ChatColor.RED + "[Friends] That request no longer exists.");
            openPlayerFriendRequestsMenu(player);
            return;
        }
        openPlayerFriendRequestActionsMenu(player, requesterId);
    }

    private void handleOpenPlayerPartyRequestActions(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID inviterId = parseUuid(parts[1]);
        if (inviterId == null) {
            return;
        }
        if (!partyService.getIncomingInvites(player.getUniqueId()).contains(inviterId)) {
            player.sendMessage(ChatColor.RED + "[Party] That invite no longer exists.");
            openPlayerPartyRequestsMenu(player);
            return;
        }
        openPlayerPartyRequestActionsMenu(player, inviterId);
    }

    private void handleOpenPlayerPartyMemberActions(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID memberId = parseUuid(parts[1]);
        if (memberId == null) {
            return;
        }
        if (!partyService.getPartyMembers(player.getUniqueId()).contains(memberId)) {
            player.sendMessage(ChatColor.RED + "[Party] That player is not in your party.");
            openPlayerPartyMenu(player);
            return;
        }
        openPlayerPartyMemberActionsMenu(player, memberId);
    }

    private void handlePlayerFriendsSend(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID targetId = parseUuid(parts[1]);
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "[Friends] Player is not online.");
            openPlayerAddFriendMenu(player);
            return;
        }
        FriendService.SendRequestResult result = friendService.sendRequest(player.getUniqueId(), targetId);
        switch (result) {
            case SELF -> player.sendMessage(ChatColor.RED + "[Friends] You cannot add yourself.");
            case ALREADY_FRIENDS -> player.sendMessage(ChatColor.YELLOW + "[Friends] You are already friends with " + target.getName() + ".");
            case ALREADY_SENT -> player.sendMessage(ChatColor.YELLOW + "[Friends] You already sent a request to " + target.getName() + ".");
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
        openPlayerAddFriendMenu(player);
    }

    private void handlePlayerFriendsAccept(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID requesterId = parseUuid(parts[1]);
        if (requesterId == null) {
            return;
        }
        FriendService.RequestActionResult result =
            friendService.acceptRequest(player.getUniqueId(), requesterId);
        if (result != FriendService.RequestActionResult.OK) {
            player.sendMessage(ChatColor.RED + "[Friends] Request no longer exists.");
            openPlayerFriendRequestsMenu(player);
            return;
        }
        String requesterName = friendService.displayName(requesterId);
        player.sendMessage(ChatColor.GREEN + "[Friends] You are now friends with " + requesterName + ".");
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.GREEN + "[Friends] " + player.getName() + " accepted your friend request.");
        }
        openPlayerFriendRequestsMenu(player);
    }

    private void handlePlayerFriendsDeny(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID requesterId = parseUuid(parts[1]);
        if (requesterId == null) {
            return;
        }
        FriendService.RequestActionResult result =
            friendService.denyRequest(player.getUniqueId(), requesterId);
        if (result != FriendService.RequestActionResult.OK) {
            player.sendMessage(ChatColor.RED + "[Friends] Request no longer exists.");
            openPlayerFriendRequestsMenu(player);
            return;
        }
        String requesterName = friendService.displayName(requesterId);
        player.sendMessage(ChatColor.YELLOW + "[Friends] Denied request from " + requesterName + ".");
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.YELLOW + "[Friends] " + player.getName() + " denied your friend request.");
        }
        openPlayerFriendRequestsMenu(player);
    }

    private void handlePlayerFriendsRemove(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID friendId = parseUuid(parts[1]);
        if (friendId == null) {
            return;
        }
        FriendService.RemoveFriendResult result =
            friendService.removeFriend(player.getUniqueId(), friendId);
        if (result != FriendService.RemoveFriendResult.OK) {
            player.sendMessage(ChatColor.RED + "[Friends] That player is not in your friend list.");
            openPlayerRemoveFriendMenu(player);
            return;
        }
        String friendName = friendService.displayName(friendId);
        player.sendMessage(ChatColor.YELLOW + "[Friends] Removed " + friendName + " from your friends list.");
        Player other = Bukkit.getPlayer(friendId);
        if (other != null && other.isOnline()) {
            other.sendMessage(ChatColor.YELLOW + "[Friends] " + player.getName() + " removed you from friends.");
        }
        openPlayerRemoveFriendMenu(player);
    }

    private void handlePlayerFriendsView(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID friendId = parseUuid(parts[1]);
        if (friendId == null) {
            return;
        }
        String name = friendService.displayName(friendId);
        Player online = Bukkit.getPlayer(friendId);
        player.sendMessage(ChatColor.AQUA + "[Friends] " + ChatColor.WHITE + name + ChatColor.GRAY + " is "
            + (online != null && online.isOnline() ? ChatColor.GREEN + "online" : ChatColor.GRAY + "offline"));
    }

    private void handleFriendsPage(Player player, String[] parts) {
        if (parts.length != 3) return;
        FriendsView view = FriendsView.fromId(parts[1]);
        if (view == null) return;
        try {
            openPlayerFriendsPage(player, view, Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            openPlayerFriendsPage(player, view, 0);
        }
    }

    private void handlePlayerFriendsUnblock(Player player, String[] parts) {
        if (parts.length != 2) return;
        UUID targetId = parseUuid(parts[1]);
        if (targetId == null) return;
        FriendService.BlockResult result = friendService.unblock(player.getUniqueId(), targetId);
        player.sendMessage(result == FriendService.BlockResult.UNBLOCKED
            ? ChatColor.GREEN + "[Friends] Unblocked " + friendService.displayName(targetId) + "."
            : ChatColor.RED + "[Friends] That player is no longer blocked.");
        openPlayerFriendsPage(player, FriendsView.BLOCKED, 0);
    }

    private void handlePlayerPartyInvite(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID targetId = parseUuid(parts[1]);
        if (targetId == null) {
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "[Party] That friend is not online.");
            openPlayerPartyInviteFriendsMenu(player);
            return;
        }

        if (!partyService.isInParty(player.getUniqueId())) {
            Bukkit.dispatchCommand(player, "party create");
            if (!partyService.isInParty(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "[Party] A party could not be created right now.");
                openPlayerPartyMenu(player);
                return;
            }
        }
        Bukkit.dispatchCommand(player, "party invite " + target.getName());
        openPlayerPartyInviteFriendsMenu(player);
    }

    private void handlePlayerPartyAccept(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID inviterId = parseUuid(parts[1]);
        if (inviterId == null) {
            return;
        }

        Bukkit.dispatchCommand(player, "party accept " + partyService.displayName(inviterId));
        openPlayerPartyRequestsMenu(player);
    }

    private void handlePlayerPartyDeny(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID inviterId = parseUuid(parts[1]);
        if (inviterId == null) {
            return;
        }

        Bukkit.dispatchCommand(player, "party deny " + partyService.displayName(inviterId));
        openPlayerPartyRequestsMenu(player);
    }

    private void handlePlayerPartyKick(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID memberId = parseUuid(parts[1]);
        if (memberId == null) {
            return;
        }
        if (!partyService.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Party] Only the party leader can kick members.");
            openPlayerPartyMenu(player);
            return;
        }

        Bukkit.dispatchCommand(player, "party kick " + partyService.displayName(memberId));
        openPlayerPartyMenu(player);
    }

    private void handlePlayerPartyPromote(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        UUID memberId = parseUuid(parts[1]);
        if (memberId == null) {
            return;
        }
        if (!partyService.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Party] Only the party leader can transfer leadership.");
            openPlayerPartyMenu(player);
            return;
        }

        Bukkit.dispatchCommand(player, "party promote " + partyService.displayName(memberId));
        openPlayerPartyMenu(player);
    }

    private void handleBackCategory(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        LootCategory.fromKey(parts[1]).ifPresent(category -> openCategoryMenu(player, category));
    }

    private void handleAdminLine(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        LootCategory.fromKey(parts[1]).ifPresentOrElse(
            category -> spawnLine(player, category),
            () -> player.sendMessage(ChatColor.RED + "Unknown category: " + parts[1])
        );
    }

    private void handleAdminGrid(Player player, String[] parts) {
        if (parts.length != 4) {
            return;
        }
        LootCategory category = LootCategory.fromKey(parts[1]).orElse(null);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown category: " + parts[1]);
            return;
        }
        int index = parsePositive(parts[2]);
        int size = parsePositive(parts[3]);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return;
        }
        if (size < 1 || size > 12) {
            player.sendMessage(ChatColor.RED + "Size must be between 1 and 12.");
            return;
        }
        spawnGrid(player, category, index, size);
    }

    private void handleAdminRadius(Player player, String[] parts) {
        if (parts.length != 4) {
            return;
        }
        LootCategory category = LootCategory.fromKey(parts[1]).orElse(null);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown category: " + parts[1]);
            return;
        }
        int index = parsePositive(parts[2]);
        int radius = parsePositive(parts[3]);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return;
        }
        if (radius < 1 || radius > 20) {
            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 20.");
            return;
        }
        spawnRadius(player, category, index, radius);
    }

    private void handleAdminRefill(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        int radius = parsePositive(parts[1]);
        if (radius < 1 || radius > 64) {
            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 64.");
            return;
        }
        refillPlaced(player, radius);
    }

    private void handleOpenRadiusFillTable(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        LootCategory.fromKey(parts[1]).ifPresent(category -> openRadiusFillTableMenu(player, category));
    }

    private void handleOpenRadiusFillRadius(Player player, String[] parts) {
        if (parts.length != 3) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        if (categoryOpt.isEmpty()) {
            return;
        }
        int index = parsePositive(parts[2]);
        LootCategory category = categoryOpt.get();
        if (index < 1 || index > category.chestCount()) {
            return;
        }
        openRadiusFillRadiusMenu(player, category, index);
    }

    private void handleBackRadiusFillTable(Player player, String[] parts) {
        if (parts.length != 2) {
            return;
        }
        LootCategory.fromKey(parts[1]).ifPresent(category -> openRadiusFillTableMenu(player, category));
    }

    private void handleApplyRadiusFill(Player player, String[] parts) {
        if (parts.length != 4) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        if (categoryOpt.isEmpty()) {
            return;
        }
        LootCategory category = categoryOpt.get();
        int index = parsePositive(parts[2]);
        int radius = parsePositive(parts[3]);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return;
        }
        if (radius < 1 || radius > 32) {
            player.sendMessage(ChatColor.RED + "Radius must be between 1 and 32.");
            return;
        }
        fillNearbyChests(player, category, index, radius);
    }

    private void handleWaveStart(Player player, String[] parts) {
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "The old Combat Master wave system is deprecated.");
        player.sendMessage(ChatColor.GRAY + "Use Combat Master boss controls while the combined system is rebuilt.");
    }

    private void handleAdminSim(Player player, String[] parts) {
        if (parts.length != 4) {
            return;
        }
        LootCategory category = LootCategory.fromKey(parts[1]).orElse(null);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown category: " + parts[1]);
            return;
        }
        int index = parsePositive(parts[2]);
        int iterations = parsePositive(parts[3]);
        if (index < 1 || index > category.chestCount()) {
            player.sendMessage(ChatColor.RED + "Index must be between 1 and " + category.chestCount());
            return;
        }
        if (iterations < 1 || iterations > 10000) {
            player.sendMessage(ChatColor.RED + "Iterations must be between 1 and 10000.");
            return;
        }
        simulateLoot(player, category, index, iterations);
    }

    private void handleGive(Player player, String[] parts) {
        if (parts.length != 3) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        if (categoryOpt.isEmpty()) {
            return;
        }
        int index = parsePositive(parts[2]);
        LootCategory category = categoryOpt.get();
        if (index <= 0 || index > category.chestCount()) {
            return;
        }
        String tableId = resolveMenuTableId(category, index);
        if (!lootTableService.hasTable(tableId)) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Missing loot table: " + tableId);
            return;
        }
        ItemStack chest = lootChestItem(category, index, tableId);
        player.getInventory().addItem(chest);
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Given " + category.displayName() + " " + index + " chest item");
        plugin.getLogger().info("Menu give: player=" + player.getName()
            + ", category=" + category.key()
            + ", index=" + index
            + ", table=" + tableId);
    }

    private void handleSpawn(Player player, String[] parts) {
        if (parts.length != 3) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        if (categoryOpt.isEmpty()) {
            return;
        }
        int index = parsePositive(parts[2]);
        LootCategory category = categoryOpt.get();
        if (index <= 0 || index > category.chestCount()) {
            return;
        }
        String tableId = resolveMenuTableId(category, index);
        if (!lootTableService.hasTable(tableId)) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Missing loot table: " + tableId);
            return;
        }

        Block target = relativeForward(player, 1, 0);
        if (!target.getType().isAir() && !target.isPassable()) {
            player.sendMessage(ChatColor.RED + "Target block is not empty.");
            return;
        }
        int generated = placeChestWithLoot(target, tableId);
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Placed " + category.displayName()
            + " Loot Chest " + index + ChatColor.GRAY + " (" + generated + " item stacks)");
        plugin.getLogger().info("Menu spawn: player=" + player.getName()
            + ", category=" + category.key()
            + ", index=" + index
            + ", table=" + tableId
            + ", generatedStacks=" + generated
            + ", block=" + target.getX() + "," + target.getY() + "," + target.getZ());
    }

    private void handleAudit(Player player, String[] parts) {
        if (parts.length != 3) {
            return;
        }
        Optional<LootCategory> categoryOpt = LootCategory.fromKey(parts[1]);
        if (categoryOpt.isEmpty()) {
            return;
        }
        int index = parsePositive(parts[2]);
        LootCategory category = categoryOpt.get();
        if (index <= 0 || index > category.chestCount()) {
            return;
        }
        String tableId = category.tableId(index);
        player.getInventory().addItem(lootTableService.createAuditBook(tableId));
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.AQUA + "Audit book created for " + tableId);
    }

    private ItemStack lootChestItem(LootCategory category, int index, String tableId) {
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        if (meta == null) {
            return chest;
        }
        meta.setDisplayName(category.color() + category.displayName() + " Loot Chest " + index);
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Place to generate loot",
            ChatColor.DARK_GRAY + "Table: " + tableId
        ));
        meta.getPersistentDataContainer().set(lootTableKey, PersistentDataType.STRING, tableId);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        chest.setItemMeta(meta);
        return chest;
    }

    public void spawnLine(Player player, LootCategory category) {
        int placed = 0;
        int generatedTotal = 0;
        for (int i = 1; i <= category.chestCount(); i++) {
            Block target = relativeForward(player, i, 0);
            if (!target.getType().isAir() && !target.isPassable()) {
                continue;
            }
            generatedTotal += placeChestWithLoot(target, category.tableId(i));
            placed++;
        }
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Spawned line for " + category.displayName()
            + " (" + placed + "/" + category.chestCount() + ")" + ChatColor.GRAY + ", stacks: " + generatedTotal);
    }

    public void spawnGrid(Player player, LootCategory category, int index, int size) {
        String tableId = category.tableId(index);
        int placed = 0;
        int generatedTotal = 0;
        int half = size / 2;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int sideOffset = x - half;
                int forwardOffset = z + 1;
                Block target = relativeForward(player, forwardOffset, sideOffset);
                if (!target.getType().isAir() && !target.isPassable()) {
                    continue;
                }
                generatedTotal += placeChestWithLoot(target, tableId);
                placed++;
            }
        }
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Spawned " + placed + " chests for "
            + tableId + " (" + size + "x" + size + ")" + ChatColor.GRAY + ", stacks: " + generatedTotal);
    }

    public void spawnRadius(Player player, LootCategory category, int index, int radius) {
        String tableId = category.tableId(index);
        if (!lootTableService.hasTable(tableId)) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Missing loot table: " + tableId);
            return;
        }

        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int placed = 0;
        int generatedTotal = 0;
        int y = origin.getBlockY();
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();

        // Step by 2 so chests do not merge into double chests.
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block target = world.getBlockAt(ox + dx, y, oz + dz);
                if (!target.getType().isAir() && !target.isPassable()) {
                    continue;
                }
                generatedTotal += placeChestWithLoot(target, tableId);
                placed++;
            }
        }
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Spawned " + placed + " chests for "
            + tableId + ChatColor.GRAY + " in radius " + radius + ", stacks: " + generatedTotal);
    }

    public void refillPlaced(Player player, int radius) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int yMin = Math.max(world.getMinHeight(), origin.getBlockY() - 8);
        int yMax = Math.min(world.getMaxHeight() - 1, origin.getBlockY() + 8);
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();

        int checked = 0;
        int refilled = 0;
        int generatedTotal = 0;
        int missingTables = 0;
        Set<String> seen = new HashSet<>();

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int z = oz - radius; z <= oz + radius; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!(block.getState() instanceof Container container)) {
                        continue;
                    }
                    String key = world.getName() + ":" + x + ":" + y + ":" + z;
                    if (!seen.add(key)) {
                        continue;
                    }
                    checked++;
                    String tableId = readPlacedTableId(container);
                    if (tableId == null || tableId.isBlank()) {
                        continue;
                    }
                    if (!lootTableService.hasTable(tableId)) {
                        missingTables++;
                        continue;
                    }
                    int generated = managedLootService == null
                        ? 0
                        : managedLootService.populateManagedContainer(block, tableId, ManagedLootService.SOURCE_MANUAL, null);
                    if (generated > 0) {
                        generatedTotal += generated;
                        refilled++;
                    }
                }
            }
        }

        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Refilled " + refilled
            + ChatColor.GRAY + " container(s) in radius " + radius + ". Checked: " + checked
            + ", stacks: " + generatedTotal
            + (missingTables > 0 ? ", missing tables: " + missingTables : ""));
    }

    public void clearPlaced(Player player, int radius) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int yMin = Math.max(world.getMinHeight(), origin.getBlockY() - 8);
        int yMax = Math.min(world.getMaxHeight() - 1, origin.getBlockY() + 8);
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();

        int checked = 0;
        int cleared = 0;
        Set<String> seen = new HashSet<>();

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int z = oz - radius; z <= oz + radius; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!(block.getState() instanceof Container container)) {
                        continue;
                    }
                    String key = world.getName() + ":" + x + ":" + y + ":" + z;
                    if (!seen.add(key)) {
                        continue;
                    }
                    checked++;
                    String tableId = readPlacedTableId(container);
                    if (tableId == null || tableId.isBlank()) {
                        continue;
                    }
                    if (managedLootService != null) {
                        managedLootService.clearManagedContainer(block);
                    }
                    cleared++;
                }
            }
        }

        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Cleared " + cleared
            + ChatColor.GRAY + " plugin-marked container(s) in radius " + radius
            + ". Checked: " + checked);
    }

    public void fillNearbyChests(Player player, LootCategory category, int index, int radius) {
        String tableId = category.tableId(index);
        if (!lootTableService.hasTable(tableId)) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Missing loot table: " + tableId);
            return;
        }

        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int yMin = Math.max(world.getMinHeight(), origin.getBlockY() - 8);
        int yMax = Math.min(world.getMaxHeight() - 1, origin.getBlockY() + 8);
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();

        int filled = 0;
        int generatedTotal = 0;
        int checked = 0;
        Set<String> seen = new HashSet<>();

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int z = oz - radius; z <= oz + radius; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
                        continue;
                    }
                    if (!(block.getState() instanceof Container container)) {
                        continue;
                    }

                    String key = world.getName() + ":" + x + ":" + y + ":" + z;
                    if (!seen.add(key)) {
                        continue;
                    }
                    checked++;

                    int generated = managedLootService == null
                        ? 0
                        : managedLootService.populateManagedContainer(block, tableId, ManagedLootService.SOURCE_MANUAL, null);
                    if (generated > 0) {
                        generatedTotal += generated;
                        filled++;
                    }
                }
            }
        }

        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Filled " + filled
            + ChatColor.GRAY + " chest/barrel container(s)"
            + ChatColor.GRAY + " using " + tableId
            + ChatColor.GRAY + " in radius " + radius
            + ", checked: " + checked
            + ", stacks: " + generatedTotal);
    }

    public void simulateLoot(Player player, LootCategory category, int index, int iterations) {
        String tableId = category.tableId(index);
        Map<String, Integer> counts = lootTableService.simulateCounts(tableId, iterations);
        if (counts.isEmpty()) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No loot generated for " + tableId);
            return;
        }

        int totalItems = counts.values().stream().mapToInt(Integer::intValue).sum();
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.AQUA + tableId
            + ChatColor.GRAY + " x" + iterations + " rolls, total items: " + totalItems);

        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(12)
            .forEach(entry -> player.sendMessage(ChatColor.GRAY + "- " + entry.getKey() + ChatColor.WHITE + ": " + entry.getValue()));
    }

    public void clearArmedShot(Player player) {
        UUID playerId = player.getUniqueId();
        armedShots.remove(playerId);
        railgunBlastImmunityUntilMs.remove(playerId);
        sonicCooldownUntilMs.remove(playerId);
        lastBreathWarmupUntilMs.remove(playerId);
        voidWarpCharge.remove(playerId);
        voidWarpCooldownUntilMs.remove(playerId);
        if (voidWarpHudPlayers.remove(playerId)) {
            player.setExp(0.0f);
            player.setLevel(0);
        }
    }

    public void handleProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        startSelectedProjectileTrail(player, projectile);
        if (!(projectile instanceof Snowball) && !(projectile instanceof Egg)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        ShotMode mode = armedShots.get(playerId);
        if (mode == null) {
            return;
        }
        // Keep Stab armed until explicit disarm; keep Nuke as one-shot.
        if (mode != ShotMode.STAB) {
            armedShots.remove(playerId);
        }
        if (!hasFunAdminPermission(player)) {
            denyFunAccess(player);
            return;
        }

        projectile.getPersistentDataContainer().set(shotModeKey, PersistentDataType.STRING, mode.id);
        projectile.setGlowing(true);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.9f);
    }

    public void handleEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        ItemStack bow = event.getBow();
        if (isRailgunCrossbowItem(bow)) {
            event.setCancelled(true); // no physical arrow/no projectile dropoff
            int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.lastBreathCooldown);
            if (cooldown > 0) {
                player.sendMessage(HydraMessages.cooldown("Last Breath", cooldown));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
                return;
            }
            triggerRailgunShot(player, arrow, RailgunMode.NORMAL);
            CooldownRegistry.setCooldown(player, UltraRoyales.lastBreathCooldown, 11, false);
            return;
        }
        if (!isSonicCrossbowItem(bow)) {
            startSelectedProjectileTrail(player, arrow);
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = sonicCooldownUntilMs.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            event.setCancelled(true);
            int seconds = (int) Math.ceil((readyAt - now) / 1000.0D);
            player.sendMessage(ChatColor.GOLD + "[Sonic] " + ChatColor.GRAY + "Cooling down: " + ChatColor.AQUA + seconds + "s");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }

        arrow.getPersistentDataContainer().set(sonicShotKey, PersistentDataType.BYTE, (byte) 1);
        startSonicTrail(arrow);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        sonicCooldownUntilMs.put(player.getUniqueId(), now + 20000L);
    }

    private void startSelectedProjectileTrail(Player player, Projectile projectile) {
        if (player == null || !isTrailEligibleProjectile(projectile) || projectile.isDead()) {
            return;
        }
        String cosmeticId = selectedSystemCosmeticId(player.getUniqueId(), CosmeticCategory.PROJECTILE_TRAILS);
        CosmeticDefinition definition = findSystemCosmeticDefinition(cosmeticId);
        if (definition == null || definition.category != CosmeticCategory.PROJECTILE_TRAILS) {
            return;
        }
        final int[] ticks = {0};
        final int[] groundedTicks = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (projectile.isDead() || !projectile.isValid() || ticks[0]++ > 220) {
                if (task[0] != null) {
                    task[0].cancel();
                }
                return;
            }
            boolean grounded = projectile.isOnGround()
                || (projectile instanceof AbstractArrow arrow && arrow.isInBlock());
            if (grounded && groundedTicks[0]++ > 0) {
                if (task[0] != null) {
                    task[0].cancel();
                }
                return;
            }
            Location loc = projectile.getLocation();
            spawnProjectileTrailParticle(definition.id, loc);
        }, 1L, 2L);
    }

    private void spawnProjectileTrailParticle(String cosmeticId, Location loc) {
        if (loc == null || loc.getWorld() == null || cosmeticId == null) {
            return;
        }
        World world = loc.getWorld();
        switch (cosmeticId) {
            case "trail_blackstatic" -> {
                world.spawnParticle(Particle.REVERSE_PORTAL, loc, 2, 0.04D, 0.04D, 0.04D, 0.01D);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0.025D, 0.025D, 0.025D, 0.0D);
            }
            case "trail_rift_ash" -> {
                Particle.DustOptions darkAsh = new Particle.DustOptions(Color.fromRGB(18, 16, 22), 0.85f);
                Location drift = loc.clone().add(
                    ThreadLocalRandom.current().nextDouble(-0.035D, 0.035D),
                    ThreadLocalRandom.current().nextDouble(0.015D, 0.08D),
                    ThreadLocalRandom.current().nextDouble(-0.035D, 0.035D)
                );
                world.spawnParticle(Particle.DUST, drift, 1, 0.035D, 0.035D, 0.035D, 0.0D, darkAsh);
                world.spawnParticle(Particle.ASH, loc, 3, 0.08D, 0.07D, 0.08D, 0.004D);
                world.spawnParticle(Particle.SMOKE, drift, 1, 0.055D, 0.055D, 0.055D, 0.004D);
                world.spawnParticle(Particle.SOUL, drift, 1, 0.04D, 0.04D, 0.04D, 0.002D);
            }
            case "trail_deep_current" -> {
                world.spawnParticle(Particle.DRIPPING_WATER, loc, 2, 0.04D, 0.04D, 0.04D, 0.0D);
                world.spawnParticle(Particle.SPLASH, loc, 1, 0.025D, 0.025D, 0.025D, 0.0D);
            }
            case "trail_nullwake" -> {
                world.spawnParticle(Particle.PORTAL, loc, 2, 0.04D, 0.04D, 0.04D, 0.012D);
            }
            case "trail_frost_mist" -> {
                world.spawnParticle(Particle.SNOWFLAKE, loc, 2, 0.05D, 0.05D, 0.05D, 0.006D);
                world.spawnParticle(Particle.CLOUD, loc, 1, 0.03D, 0.03D, 0.03D, 0.003D);
            }
            case "trail_tidewake" -> {
                world.spawnParticle(Particle.SPLASH, loc, 2, 0.045D, 0.045D, 0.045D, 0.0D);
                world.spawnParticle(Particle.BUBBLE, loc, 1, 0.035D, 0.035D, 0.035D, 0.0D);
            }
            case "trail_eclipse" -> {
                world.spawnParticle(Particle.END_ROD, loc, 1, 0.025D, 0.025D, 0.025D, 0.002D);
                world.spawnParticle(Particle.REVERSE_PORTAL, loc, 2, 0.04D, 0.04D, 0.04D, 0.008D);
            }
            case "trail_abyss_wake" -> {
                world.spawnParticle(Particle.SCULK_SOUL, loc, 1, 0.035D, 0.035D, 0.035D, 0.006D);
                world.spawnParticle(Particle.REVERSE_PORTAL, loc, 3, 0.05D, 0.05D, 0.05D, 0.012D);
            }
            default -> world.spawnParticle(Particle.CRIT, loc, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    private void spawnFlashParticle(World world, Location location, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (world == null || location == null) {
            return;
        }
        try {
            world.spawnParticle(Particle.FLASH, location, count, offsetX, offsetY, offsetZ, speed, Color.WHITE);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().fine("[Cosmetics] FLASH particle rejected Color data: " + ex.getMessage());
            try {
                world.spawnParticle(Particle.END_ROD, location, Math.max(2, count * 4), offsetX, offsetY, offsetZ, Math.max(0.01D, speed));
            } catch (IllegalArgumentException ignored) {
                // Cosmetic-only fallback; do not let a particle API mismatch break death/interact handlers.
            }
        }
    }

    public void handlePlayerDeath(PlayerDeathEvent event) {
        if (event == null) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        CosmeticDefinition effect = findSystemCosmeticDefinition(selectedSystemCosmeticId(killer.getUniqueId(), CosmeticCategory.KILL_EFFECTS));
        if (effect != null && effect.category == CosmeticCategory.KILL_EFFECTS) {
            playKillEffect(effect.id, victim.getLocation().add(0.0D, 0.8D, 0.0D));
        }
    }

    private void playKillEffect(String cosmeticId, Location loc) {
        if (loc == null || loc.getWorld() == null || cosmeticId == null) {
            return;
        }
        World world = loc.getWorld();
        switch (cosmeticId) {
            case "kill_effect_shatterfall" -> {
                world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 24, 0.45D, 0.5D, 0.45D, 0.03D, Material.GLASS.createBlockData());
                world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.85f);
            }
            case "kill_effect_permafrost" -> {
                world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 36, 0.42D, 0.42D, 0.42D, 0.035D, Material.BLUE_ICE.createBlockData());
                world.spawnParticle(Particle.SNOWFLAKE, loc, 28, 0.36D, 0.5D, 0.36D, 0.035D);
                world.spawnParticle(Particle.CRIT, loc, 12, 0.25D, 0.35D, 0.25D, 0.02D);
                world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.85f, 1.6f);
                world.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.45f, 1.35f);
            }
            case "kill_effect_glacial_bloom" -> {
                world.spawnParticle(Particle.SNOWFLAKE, loc, 24, 0.85D, 0.2D, 0.85D, 0.012D);
                world.spawnParticle(Particle.CLOUD, loc, 24, 0.9D, 0.28D, 0.9D, 0.012D);
                for (int i = 1; i <= 3; i++) {
                    int pulse = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (loc.getWorld() == null) {
                            return;
                        }
                        double radius = 0.35D + (pulse * 0.22D);
                        for (int point = 0; point < 10; point++) {
                            double angle = (Math.PI * 2.0D * point / 10.0D) + (pulse * 0.55D);
                            Location petal = loc.clone().add(Math.cos(angle) * radius, 0.12D + (pulse * 0.05D), Math.sin(angle) * radius);
                            loc.getWorld().spawnParticle(Particle.SNOWFLAKE, petal, 2, 0.035D, 0.035D, 0.035D, 0.006D);
                        }
                    }, pulse * 5L);
                }
                world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.75f);
            }
            case "kill_effect_kingsfall" -> {
                Particle.DustOptions royalGold = new Particle.DustOptions(Color.fromRGB(255, 214, 74), 1.2f);
                spawnFlashParticle(world, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                for (int i = 0; i < 7; i++) {
                    Location column = loc.clone().add(0.0D, i * 0.28D, 0.0D);
                    world.spawnParticle(Particle.DUST, column, 3, 0.08D, 0.04D, 0.08D, 0.0D, royalGold);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, column, 2, 0.12D, 0.06D, 0.12D, 0.015D);
                }
                world.spawnParticle(Particle.FIREWORK, loc.clone().add(0.0D, 1.0D, 0.0D), 18, 0.3D, 0.7D, 0.3D, 0.025D);
                world.playSound(loc, Sound.ITEM_TOTEM_USE, 0.5f, 1.35f);
                world.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.55f, 1.75f);
            }
            case "kill_effect_golden_collapse" -> {
                Particle.DustOptions collapseGold = new Particle.DustOptions(Color.fromRGB(255, 178, 38), 1.35f);
                world.spawnParticle(Particle.DUST, loc, 32, 0.85D, 0.6D, 0.85D, 0.0D, collapseGold);
                world.spawnParticle(Particle.FIREWORK, loc, 18, 0.45D, 0.4D, 0.45D, 0.02D);
                world.spawnParticle(Particle.CRIT, loc, 26, 0.35D, 0.3D, 0.35D, 0.025D);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (loc.getWorld() == null) {
                        return;
                    }
                    spawnFlashParticle(loc.getWorld(), loc, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 28, 0.28D, 0.22D, 0.28D, 0.035D);
                }, 5L);
                world.playSound(loc, Sound.BLOCK_BELL_RESONATE, 0.65f, 0.85f);
            }
            case "kill_effect_crownflare" -> {
                spawnFlashParticle(world, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.FIREWORK, loc, 42, 0.85D, 0.65D, 0.85D, 0.035D);
                Particle.DustOptions flareGold = new Particle.DustOptions(Color.fromRGB(255, 225, 110), 1.05f);
                for (int i = 1; i <= 5; i++) {
                    int pulse = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (loc.getWorld() == null) {
                            return;
                        }
                        double width = 0.25D + pulse * 0.12D;
                        loc.getWorld().spawnParticle(Particle.DUST, loc, 10, width, 0.22D, width, 0.0D, flareGold);
                        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.0D, 0.25D, 0.0D), 4, width, 0.2D, width, 0.012D);
                    }, pulse * 6L);
                }
                world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.25f);
            }
            default -> world.spawnParticle(Particle.CRIT, loc, 18, 0.45D, 0.45D, 0.45D, 0.02D);
        }
    }

    public void handleEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity target) {
            handleVoidbreakerHit(attacker, target, event);
        }

        if (!(event.getDamager() instanceof Zombie zombie)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        Byte tagged = zombie.getPersistentDataContainer().get(venomZombieKey, PersistentDataType.BYTE);
        if (tagged == null || tagged != (byte) 1) {
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, true, true));
    }

    public void handleEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            long now = System.currentTimeMillis();
            long immuneUntil = railgunBlastImmunityUntilMs.getOrDefault(playerId, 0L);
            if (immuneUntil > now) {
                event.setCancelled(true);
                return;
            }
            if (immuneUntil > 0L) {
                railgunBlastImmunityUntilMs.remove(playerId);
            }
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

    }

    private void handleVoidbreakerHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!isVoidbreakerMace(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        if (attacker.getGameMode() == GameMode.SPECTATOR || attacker.isDead()) {
            return;
        }

        float fall = Math.max(0.0f, attacker.getFallDistance());
        double chargeGain = fall >= 1.8f ? VOIDBREAKER_PLUNGE_CHARGE : VOIDBREAKER_MELEE_CHARGE;
        if (fall >= 1.8f) {
            event.setDamage(event.getDamage() + Math.min(3.0D, fall * 0.28D));
        }
        addVoidbreakerCharge(attacker, chargeGain);
    }

    private boolean tryToggleRailgunMode(PlayerInteractEvent event, Player player, ItemStack usedItem) {
        if (usedItem == null || !player.isSneaking()) {
            return false;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return false;
        }
        if (!isRailgunCrossbowItem(usedItem) || !isRailgunCrossbowItem(player.getInventory().getItemInMainHand())) {
            return false;
        }

        event.setCancelled(true);
        player.sendActionBar(Component.text("Last Breath: Railgun", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 0.9f);
        return true;
    }

    private boolean tryUseVoidRupture(PlayerInteractEvent event, Player player, ItemStack usedItem) {
        if (!isVoidbreakerMace(usedItem) || !isVoidbreakerMace(player.getInventory().getItemInMainHand())) {
            return false;
        }

        long now = System.currentTimeMillis();
        long readyAt = voidWarpCooldownUntilMs.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            event.setCancelled(true);
            int seconds = (int) Math.ceil((readyAt - now) / 1000.0D);
            player.sendMessage(ChatColor.DARK_PURPLE + "[Voidbreaker] "
                + ChatColor.LIGHT_PURPLE + "Void Rupture cooling down: " + ChatColor.AQUA + seconds + "s");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return true;
        }

        float charge = Math.max(0.0f, Math.min(1.0f, voidWarpCharge.getOrDefault(player.getUniqueId(), 0.0f)));
        if (charge < 1.0f) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Void Charge: " + Math.round(charge * 100.0f) + "%", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.65f);
            return true;
        }

        event.setCancelled(true);
        setVoidWarpCharge(player, 0.0f);
        voidWarpCooldownUntilMs.put(player.getUniqueId(), now + VOIDBREAKER_RUPTURE_COOLDOWN_MS);
        triggerVoidRupture(player);
        return true;
    }

    private void addVoidbreakerCharge(Player player, double amount) {
        if (player == null || amount <= 0.0D) {
            return;
        }
        float current = Math.max(0.0f, Math.min(1.0f, voidWarpCharge.getOrDefault(player.getUniqueId(), 0.0f)));
        float next = Math.max(0.0f, Math.min(1.0f, current + (float) amount));
        setVoidWarpCharge(player, next);
        if (current < 1.0f && next >= 1.0f) {
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.6f);
            player.sendActionBar(Component.text("Void Rupture Ready", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
        }
    }

    private void triggerVoidRupture(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation().clone();
        Vector forward = origin.getDirection().clone().setY(0.0D);
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        forward.normalize();
        final Vector ruptureForward = forward.clone();
        final Vector ruptureSide = new Vector(-ruptureForward.getZ(), 0.0D, ruptureForward.getX()).normalize();
        Set<UUID> hitTargets = new HashSet<>();

        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.25f, 0.55f);
        world.playSound(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.1f, 0.65f);
        world.spawnParticle(Particle.REVERSE_PORTAL, origin.clone().add(0.0D, 1.0D, 0.0D), 65, 0.55D, 0.55D, 0.55D, 0.08D);

        int steps = (int) Math.ceil(VOIDBREAKER_RUPTURE_RANGE / VOIDBREAKER_RUPTURE_STEP);
        for (int step = 1; step <= steps; step++) {
            final int currentStep = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double traveled = currentStep * VOIDBREAKER_RUPTURE_STEP;
                double widthT = Math.min(1.0D, traveled / VOIDBREAKER_RUPTURE_RANGE);
                double halfWidth = VOIDBREAKER_RUPTURE_BASE_HALF_WIDTH
                    + ((VOIDBREAKER_RUPTURE_MAX_HALF_WIDTH - VOIDBREAKER_RUPTURE_BASE_HALF_WIDTH) * widthT);
                Location center = origin.clone().add(ruptureForward.clone().multiply(traveled));
                fractureVoidSurface(world, center, ruptureForward, ruptureSide, halfWidth);
                damageVoidRuptureTargets(player, center, ruptureForward, halfWidth, hitTargets);
                world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0.0D, 0.35D, 0.0D), 14, halfWidth * 0.45D, 0.18D, halfWidth * 0.45D, 0.015D);
                world.spawnParticle(Particle.PORTAL, center.clone().add(0.0D, 0.35D, 0.0D), 8, halfWidth * 0.35D, 0.12D, halfWidth * 0.35D, 0.02D);
                world.spawnParticle(Particle.BLOCK_CRUMBLE, center.clone().add(0.0D, 0.1D, 0.0D), 10, halfWidth * 0.35D, 0.1D, halfWidth * 0.35D, 0.02D, Material.DEEPSLATE.createBlockData());
                if (currentStep % 3 == 0) {
                    world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 0.65f, 0.55f);
                }
            }, Math.max(0L, step - 1L));
        }
    }

    private void damageVoidRuptureTargets(Player source, Location center, Vector forward, double halfWidth, Set<UUID> hitTargets) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(center, halfWidth + 0.75D, 2.2D, halfWidth + 0.75D)) {
            if (!(entity instanceof LivingEntity living) || living.equals(source) || living.isDead()) {
                continue;
            }
            if (TeamProtectionService.blocks(source, living)) continue;
            if (!hitTargets.add(living.getUniqueId())) {
                continue;
            }
            double damage = Math.min(VOIDBREAKER_RUPTURE_DAMAGE, Math.max(0.0D, living.getHealth() - 1.0D));
            if (damage > 0.0D) {
                living.damage(damage, source);
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 55, 0, false, true, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0, false, true, true));
            Vector knock = forward.clone().multiply(0.42D).setY(0.42D);
            living.setVelocity(living.getVelocity().add(knock));
            living.getWorld().spawnParticle(Particle.PORTAL, living.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.4D, 0.35D, 0.05D);
        }
    }

    private void fractureVoidSurface(World world, Location center, Vector forward, Vector side, double halfWidth) {
        int samples = Math.max(1, (int) Math.ceil(halfWidth * 2.0D));
        for (int i = -samples; i <= samples; i++) {
            double lateral = i * 0.7D;
            if (Math.abs(lateral) > halfWidth) {
                continue;
            }
            Location sample = center.clone().add(side.clone().multiply(lateral));
            Block surface = findVoidRuptureSurfaceBlock(world, sample);
            if (surface == null || isVoidbreakerProtectedBlock(surface)) {
                continue;
            }
            disturbVoidSurfaceBlock(surface);
        }
    }

    private Block findVoidRuptureSurfaceBlock(World world, Location sample) {
        int x = sample.getBlockX();
        int z = sample.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 2, sample.getBlockY() + 3);
        int minY = Math.max(world.getMinHeight(), sample.getBlockY() - 4);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir() && block.getType().isSolid()) {
                return block;
            }
        }
        return null;
    }

    private boolean isVoidbreakerProtectedBlock(Block block) {
        if (block == null || block.getState() instanceof Container) {
            return true;
        }
        Material type = block.getType();
        if (!isVoidbreakerSoftSurface(type)) {
            return true;
        }
        Location loc = block.getLocation();
        String worldName = block.getWorld().getName();
        if ((worldName.startsWith("BR-World") || worldName.toLowerCase(Locale.ROOT).contains("br"))
            && Math.hypot(loc.getX(), loc.getZ()) <= VOIDBREAKER_PROTECTED_CENTER_RADIUS) {
            return true;
        }
        return false;
    }

    private boolean isVoidbreakerSoftSurface(Material type) {
        return type == Material.GRASS_BLOCK
            || type == Material.DIRT
            || type == Material.COARSE_DIRT
            || type == Material.ROOTED_DIRT
            || type == Material.PODZOL
            || type == Material.MYCELIUM
            || type == Material.SAND
            || type == Material.RED_SAND
            || type == Material.GRAVEL
            || type == Material.MOSS_BLOCK
            || type == Material.MUD;
    }

    private void disturbVoidSurfaceBlock(Block block) {
        Material[] scars = new Material[] {
            Material.COBBLESTONE,
            Material.COBBLED_DEEPSLATE,
            Material.BLACKSTONE,
            Material.BASALT,
            Material.CRYING_OBSIDIAN
        };
        Material scar = scars[ThreadLocalRandom.current().nextInt(scars.length)];
        block.setType(scar, false);
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() == Material.SHORT_GRASS
            || above.getType() == Material.TALL_GRASS
            || above.getType() == Material.FERN
            || above.getType() == Material.LARGE_FERN
            || above.getType().name().endsWith("_FLOWER")
            || above.getType().name().endsWith("_SAPLING")) {
            above.setType(Material.AIR, false);
        }
    }

    private void setVoidWarpCharge(Player player, float charge) {
        if (player == null) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, charge));
        voidWarpCharge.put(player.getUniqueId(), clamped);
        updateVoidWarpHudForPlayer(player);
    }

    public void handleProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Byte sonic = projectile.getPersistentDataContainer().get(sonicShotKey, PersistentDataType.BYTE);
        if (sonic != null && sonic == (byte) 1 && projectile instanceof AbstractArrow arrow) {
            triggerSonicHit(arrow);
            projectile.getPersistentDataContainer().remove(sonicShotKey);
            projectile.remove();
            return;
        }

        String modeId = projectile.getPersistentDataContainer().get(shotModeKey, PersistentDataType.STRING);
        if (modeId == null || modeId.isBlank()) {
            return;
        }

        ShotMode mode = ShotMode.fromId(modeId);
        if (mode == null) {
            return;
        }

        Player shooter = projectile.getShooter() instanceof Player player ? player : null;
        if (shooter == null) {
            return;
        }
        if (!hasFunAdminPermission(shooter)) {
            denyFunAccess(shooter);
            return;
        }

        Location impact;
        if (event.getHitEntity() != null) {
            impact = event.getHitEntity().getLocation().add(0.0, 0.5, 0.0);
        } else if (event.getHitBlock() != null) {
            impact = event.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        } else {
            impact = projectile.getLocation().clone();
        }

        switch (mode) {
            case NUKE -> triggerNukeShot(shooter, impact);
            case STAB -> triggerStabShot(shooter, impact);
        }
    }

    public int placeManagedLootChest(Block target, String tableId) {
        if (target == null || tableId == null || tableId.isBlank()) {
            return 0;
        }
        if (!lootTableService.hasTable(tableId)) {
            plugin.getLogger().warning("placeManagedLootChest: missing table=" + tableId);
            return 0;
        }
        return placeChestWithLoot(target, tableId);
    }

    public int populateManagedLootContainer(Block target, String tableId) {
        if (managedLootService == null) {
            return 0;
        }
        return managedLootService.populateManagedContainer(target, tableId, ManagedLootService.SOURCE_MANUAL, null);
    }

    private int placeChestWithLoot(Block target, String tableId) {
        target.setType(Material.CHEST, false);
        if (target.getState() instanceof Container container) {
            int generated = managedLootService == null
                ? 0
                : managedLootService.populateManagedContainer(target, tableId, ManagedLootService.SOURCE_MANUAL, null);
            Inventory inventory = writableInventory(container);
            plugin.getLogger().info("placeChestWithLoot: table=" + tableId
                + ", generatedStacks=" + generated
                + ", invSize=" + inventory.getSize()
                + ", block=" + target.getX() + "," + target.getY() + "," + target.getZ());
            logChestStacks("immediate", target, tableId);
            Bukkit.getScheduler().runTaskLater(plugin, () -> logChestStacks("after_5t", target, tableId), 5L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> logChestStacks("after_40t", target, tableId), 40L);
            return generated;
        }
        return 0;
    }

    private void ensureChestHasLoot(Block block, String tableId) {
        if (!(block.getState() instanceof Container container)) {
            plugin.getLogger().warning("Chest verify after_1t: missing container at "
                + block.getX() + "," + block.getY() + "," + block.getZ() + " table=" + tableId);
            return;
        }

        Inventory inventory = writableInventory(container);
        int stacks = countStacks(inventory.getContents());
        if (stacks > 0) {
            plugin.getLogger().info("Chest verify after_1t: table=" + tableId
                + ", stacks=" + stacks
                + ", invSize=" + inventory.getSize()
                + ", block=" + block.getX() + "," + block.getY() + "," + block.getZ());
            return;
        }

        int regenerated = lootTableService.fillInventory(inventory, tableId);
        flushContainer(container);
        int stacksAfter = countStacks(inventory.getContents());
        plugin.getLogger().warning("Chest refill after_1t: table=" + tableId
            + ", generatedStacks=" + regenerated
            + ", finalStacks=" + stacksAfter
            + ", invSize=" + inventory.getSize()
            + ", block=" + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    private void logChestStacks(String phase, Block block, String tableId) {
        if (!(block.getState() instanceof Container container)) {
            plugin.getLogger().warning("Chest verify " + phase + ": missing container at "
                + block.getX() + "," + block.getY() + "," + block.getZ() + " table=" + tableId);
            return;
        }
        Inventory inventory = writableInventory(container);
        int stacks = countStacks(inventory.getContents());
        plugin.getLogger().info("Chest verify " + phase + ": table=" + tableId
            + ", stacks=" + stacks
            + ", invSize=" + inventory.getSize()
            + ", block=" + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    private Inventory writableInventory(Container container) {
        if (container instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        return container.getInventory();
    }

    private void flushContainer(Container container) {
        // For placed chests, getBlockInventory() writes to live block state directly.
        // Updating the original snapshot state can overwrite live contents back to empty.
        if (container instanceof Chest) {
            return;
        }
        container.update(true, false);
    }

    private String readPlacedTableId(Container container) {
        return managedLootService == null ? null : managedLootService.readManagedTableId(container);
    }

    private int countStacks(ItemStack[] contents) {
        int stacks = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                stacks++;
            }
        }
        return stacks;
    }

    private String resolveMenuTableId(LootCategory category, int index) {
        return category.tableId(index);
    }

    private void armShot(Player player, ShotMode mode) {
        if (!hasFunAdminPermission(player)) {
            denyFunAccess(player);
            return;
        }
        armedShots.put(player.getUniqueId(), mode);
        player.closeInventory();
        player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + ChatColor.GOLD + mode.display + ChatColor.GRAY + " armed.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    private void disarmShot(Player player) {
        if (armedShots.remove(player.getUniqueId()) != null) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + ChatColor.GRAY + "Shot mode disarmed.");
        } else {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + ChatColor.GRAY + "No shot mode was armed.");
        }
    }

    private void triggerNukeShot(Player shooter, Location impact) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int tntCount = 54;
        double spread = 7.5;
        double baseHeight = 22.0;

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.7f);
        world.playSound(impact, Sound.ENTITY_WITHER_SPAWN, 1.8f, 1.5f);
        spawnFlashParticle(world, impact, 4, 0.5D, 0.5D, 0.5D, 0.02D);

        for (int i = 0; i < tntCount; i++) {
            double angle = rng.nextDouble(Math.PI * 2.0);
            double radius = Math.sqrt(rng.nextDouble()) * spread;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double offsetY = baseHeight + rng.nextDouble(0.0, 8.0);
            Location spawn = impact.clone().add(offsetX, offsetY, offsetZ);

            TNTPrimed tnt = world.spawn(spawn, TNTPrimed.class, entity -> {
                entity.setSource(shooter);
                entity.setYield(4.0f);
                entity.setFuseTicks(28 + rng.nextInt(0, 16));
            });
            tnt.setVelocity(new Vector(rng.nextDouble(-0.08, 0.08), -0.45 - rng.nextDouble(0.0, 0.25), rng.nextDouble(-0.08, 0.08)));
        }

        shooter.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + ChatColor.GOLD + "Nuke Shot deployed.");
    }

    private void triggerStabShot(Player shooter, Location impact) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int topY = Math.min(world.getMaxHeight() - 2, impact.getBlockY() + 12);
        int minY = world.getMinHeight() + 3;
        int step = 5;
        int layers = 0;
        int charges = 0;

        for (int y = topY; y >= minY; y -= step) {
            double progress = (topY == minY) ? 1.0D : (double) (topY - y) / (double) (topY - minY);
            double radius = 5.8D - (4.3D * progress); // funnel tighter as it goes down
            double spin = layers * 0.72D;
            int fuse = Math.min(100, 10 + (layers * 2));

            // Core charge
            Location core = new Location(world, impact.getBlockX() + 0.5D, y, impact.getBlockZ() + 0.5D);
            spawnStabDrillCharge(shooter, core, 6.0f, fuse);
            charges++;

            // Swirling ring charges to carve a vortex look.
            for (int i = 0; i < 6; i++) {
                double angle = spin + (Math.PI * 2.0D * i / 6.0D);
                double x = impact.getBlockX() + 0.5D + Math.cos(angle) * radius;
                double z = impact.getBlockZ() + 0.5D + Math.sin(angle) * radius;
                Location blast = new Location(world, x, y + rng.nextDouble(-0.15D, 0.15D), z);
                spawnStabDrillCharge(shooter, blast, 5.2f, fuse + rng.nextInt(0, 4));
                charges++;
            }
            layers++;
        }

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.2f, 0.55f);
        world.playSound(impact, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.7f, 0.7f);
        world.spawnParticle(Particle.SMOKE, impact, 70, 1.0, 0.7, 1.0, 0.03);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticle(Particle.ASH, impact, 40, 0.9, 0.8, 0.9, 0.01);

        long decorateDelayTicks = Math.min(140L, 40L + (layers * 2L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> decorateStabShaft(world, impact), decorateDelayTicks);
        shooter.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "" + ChatColor.GOLD
            + "Stab Shot drill armed (" + charges + " TNT charges to bedrock).");
    }

    private void spawnStabDrillCharge(Player shooter, Location location, float yield, int fuseTicks) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawn(location, TNTPrimed.class, tnt -> {
            tnt.setSource(shooter);
            tnt.setYield(yield);
            tnt.setFuseTicks(Math.max(1, fuseTicks));
            tnt.setGravity(false);
            tnt.setVelocity(new Vector(0.0, 0.0, 0.0));
        });
    }

    private void decorateStabShaft(World world, Location impact) {
        if (world == null || impact == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int cx = impact.getBlockX();
        int cz = impact.getBlockZ();
        int topY = Math.min(world.getMaxHeight() - 2, impact.getBlockY() + 14);
        int minY = world.getMinHeight() + 2;
        int depth = Math.max(1, topY - minY);

        // Carve and skin a jagged spiral funnel.
        for (int y = topY; y >= minY; y -= 2) {
            double progress = (double) (topY - y) / (double) depth;
            double baseRadius = 12.0D - (8.5D * progress); // wider at top, tight at bottom
            double swirlAngle = progress * Math.PI * 9.0D;
            double sx = Math.cos(swirlAngle) * 1.6D;
            double sz = Math.sin(swirlAngle) * 1.6D;
            int scan = (int) Math.ceil(baseRadius + 3.0D);

            for (int x = cx - scan; x <= cx + scan; x++) {
                for (int z = cz - scan; z <= cz + scan; z++) {
                    double dx = x - (cx + sx);
                    double dz = z - (cz + sz);
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    double jag = Math.sin((x * 0.37D) + (z * 0.22D) + (y * 0.19D)) * 0.9D
                        + Math.cos((x * 0.17D) - (z * 0.41D) + (y * 0.13D)) * 0.45D;
                    double cutRadius = baseRadius + jag;

                    Block block = world.getBlockAt(x, y, z);
                    Material current = block.getType();
                    if (current == Material.BEDROCK) {
                        continue;
                    }

                    if (dist <= cutRadius - 0.35D) {
                        if (rng.nextDouble() < 0.93D) {
                            block.setType(Material.AIR, false);
                        } else {
                            block.setType(progress > 0.60D ? Material.DEEPSLATE : Material.STONE, false);
                        }
                        continue;
                    }

                    if (dist <= cutRadius + 1.45D) {
                        double roll = rng.nextDouble();
                        if (roll < 0.48D) {
                            block.setType(Material.SCULK, false);
                        } else if (roll < 0.62D) {
                            block.setType(Material.SCULK_VEIN, false);
                        } else if (roll < 0.82D) {
                            block.setType(progress > 0.55D ? Material.DEEPSLATE : Material.STONE, false);
                        } else {
                            block.setType(Material.COBBLESTONE, false);
                        }
                    }
                }
            }
        }

        // Add jagged vertical pillars/fragments around the shaft wall.
        int pillarCount = 14;
        int pillarTopBase = topY - 4;
        for (int i = 0; i < pillarCount; i++) {
            double angle = (Math.PI * 2.0D * i / pillarCount) + rng.nextDouble(-0.25D, 0.25D);
            double radius = rng.nextDouble(4.0D, 10.5D);
            int pxBase = cx + (int) Math.round(Math.cos(angle) * radius);
            int pzBase = cz + (int) Math.round(Math.sin(angle) * radius);
            int pillarTop = pillarTopBase - rng.nextInt(0, 12);
            int pillarBottom = minY + rng.nextInt(0, 10);

            for (int y = pillarTop; y >= pillarBottom; y--) {
                double drift = Math.sin((y * 0.18D) + angle) * 0.9D;
                int px = pxBase + (int) Math.round(Math.cos(angle + 1.2D) * drift);
                int pz = pzBase + (int) Math.round(Math.sin(angle + 1.2D) * drift);

                Block core = world.getBlockAt(px, y, pz);
                if (core.getType() != Material.BEDROCK) {
                    double roll = rng.nextDouble();
                    if (roll < 0.38D) {
                        core.setType(Material.SCULK, false);
                    } else if (roll < 0.75D) {
                        core.setType(Material.STONE, false);
                    } else {
                        core.setType(Material.DEEPSLATE, false);
                    }
                }

                if (rng.nextDouble() < 0.28D) {
                    int ox = rng.nextInt(-1, 2);
                    int oz = rng.nextInt(-1, 2);
                    Block side = world.getBlockAt(px + ox, y, pz + oz);
                    if (side.getType() != Material.BEDROCK && side.getType() != Material.AIR) {
                        side.setType(rng.nextDouble() < 0.5D ? Material.COBBLESTONE : Material.SCULK, false);
                    }
                }
            }
        }

        // Dark, sculk-heavy basin near the bottom.
        int floorY = minY + 1;
        int floorRadius = 7;
        for (int x = cx - floorRadius; x <= cx + floorRadius; x++) {
            for (int z = cz - floorRadius; z <= cz + floorRadius; z++) {
                double dist = Math.hypot(x - cx, z - cz);
                if (dist > floorRadius) {
                    continue;
                }
                Block floor = world.getBlockAt(x, floorY, z);
                if (floor.getType() == Material.BEDROCK) {
                    continue;
                }
                if (dist <= 2.2D) {
                    floor.setType(Material.SCULK, false);
                } else {
                    double roll = rng.nextDouble();
                    if (roll < 0.55D) {
                        floor.setType(Material.SCULK, false);
                    } else if (roll < 0.80D) {
                        floor.setType(Material.DEEPSLATE, false);
                    } else {
                        floor.setType(Material.STONE, false);
                    }
                }
            }
        }

        Location fx = impact.clone().add(0.5D, 1.0D, 0.5D);
        world.playSound(fx, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.7f, 0.7f);
        world.playSound(fx, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.5f, 0.6f);
        world.spawnParticle(Particle.SMOKE, fx, 80, 1.2, 0.9, 1.2, 0.03);
        world.spawnParticle(Particle.ASH, fx, 60, 1.0, 0.7, 1.0, 0.01);
    }

    private void giveSonicCrossbow(Player player) {
        if (!hasFunAdminPermission(player)) {
            denyFunAccess(player);
            return;
        }
        ItemStack crossbow = sonicCrossbowItem();
        player.getInventory().addItem(crossbow);
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.AQUA + "Sonic Crossbow granted.");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
    }

    private void giveRailgunCrossbow(Player player) {
        if (!hasFunAdminPermission(player)) {
            denyFunAccess(player);
            return;
        }
        ItemStack crossbow = railgunCrossbowItem();
        player.getInventory().addItem(crossbow);
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.LIGHT_PURPLE + "Last Breath granted.");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.35f);
    }

    private void giveVoidbreaker(Player player) {
        if (!hasFunAdminPermission(player)) {
            denyFunAccess(player);
            return;
        }
        ItemStack mace = voidbreakerMaceItem();
        player.getInventory().addItem(mace);
        voidWarpCharge.put(player.getUniqueId(), 0.0f);
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.DARK_PURPLE + "Voidbreaker granted.");
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.8f);
    }

    private boolean hasFunAdminPermission(Player player) {
        return player.hasPermission("hgloot.fun.admin") || player.hasPermission("hgloot.debug");
    }

    private void denyFunAccess(Player player) {
        player.sendMessage(ChatColor.RED + "You do not have permission to use Fun Stuff tools.");
    }

    private void spawnVenomZombie(Player player) {
        if (!player.hasPermission("hgloot.debug")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this.");
            return;
        }

        Location spawn = relativeForward(player, 2, 0).getLocation().add(0.5, 0.0, 0.5);
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }

        Zombie zombie = world.spawn(spawn, Zombie.class, entity -> {
            entity.getPersistentDataContainer().set(venomZombieKey, PersistentDataType.BYTE, (byte) 1);
        });
        zombie.setAdult();
        zombie.setCanBreakDoors(false);

        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.GREEN + "Spawned Venom Zombie.");
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.9f);
    }

    private ItemStack sonicCrossbowItem() {
        ItemStack stack = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.GOLD + "Sonic Crossbow");
        meta.getPersistentDataContainer().set(sonicCrossbowKey, PersistentDataType.BYTE, (byte) 1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack railgunCrossbowItem() {
        ItemStack stack = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Last Breath");
        meta.setLore(null);
        meta.getPersistentDataContainer().set(railgunCrossbowKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(railgunModeKey, PersistentDataType.STRING, RailgunMode.NORMAL.id);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:last_breath"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack voidbreakerMaceItem() {
        ItemStack stack = new ItemStack(Material.MACE);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Voidbreaker");
        meta.setLore(null);
        meta.getPersistentDataContainer().set(voidbreakerMaceKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.DENSITY, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isSonicCrossbowItem(ItemStack bow) {
        if (bow == null || bow.getType() != Material.CROSSBOW || !bow.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = bow.getItemMeta();
        Byte tagged = meta.getPersistentDataContainer().get(sonicCrossbowKey, PersistentDataType.BYTE);
        if (tagged != null && tagged == (byte) 1) {
            return true;
        }
        String name = meta.getDisplayName();
        return ChatColor.stripColor(name == null ? "" : name).equalsIgnoreCase("Sonic Crossbow");
    }

    private boolean isRailgunCrossbowItem(ItemStack bow) {
        if (bow == null || bow.getType() != Material.CROSSBOW || !bow.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = bow.getItemMeta();
        Byte tagged = meta.getPersistentDataContainer().get(railgunCrossbowKey, PersistentDataType.BYTE);
        if (tagged != null && tagged == (byte) 1) {
            return true;
        }
        String name = meta.getDisplayName();
        String plain = ChatColor.stripColor(name == null ? "" : name);
        return plain.equalsIgnoreCase("Last Breath") || plain.equalsIgnoreCase("Railgun Crossbow");
    }

    private RailgunMode getRailgunMode(ItemStack crossbow) {
        if (crossbow == null || crossbow.getType() != Material.CROSSBOW || !crossbow.hasItemMeta()) {
            return RailgunMode.NORMAL;
        }
        ItemMeta meta = crossbow.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(railgunModeKey, PersistentDataType.STRING);
        RailgunMode parsed = RailgunMode.fromId(stored);
        return RailgunMode.NORMAL;
    }

    private void setRailgunMode(ItemStack crossbow, RailgunMode mode) {
        if (crossbow == null || mode == null || crossbow.getType() != Material.CROSSBOW || !crossbow.hasItemMeta()) {
            return;
        }
        ItemMeta meta = crossbow.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(railgunModeKey, PersistentDataType.STRING, mode.id);
        crossbow.setItemMeta(meta);
    }

    private boolean isVoidbreakerMace(ItemStack stack) {
        if (stack == null || stack.getType() != Material.MACE || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        Byte tagged = meta.getPersistentDataContainer().get(voidbreakerMaceKey, PersistentDataType.BYTE);
        if (tagged != null && tagged == (byte) 1) {
            return true;
        }
        String name = meta.getDisplayName();
        return ChatColor.stripColor(name == null ? "" : name).equalsIgnoreCase("Voidbreaker");
    }

    private void triggerRailgunShot(Player shooter, AbstractArrow projectile, RailgunMode mode) {
        World world = shooter.getWorld();
        Location start = shooter.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        double maxRange = 180.0D;
        spawnLastBreathReleaseCompression(shooter, start, direction);

        RailgunTarget target = findFirstRailgunTarget(shooter, start, direction, maxRange);
        RailgunPath path = carveLastBreathFracturePath(world, start, direction, target == null ? maxRange : target.distance());
        boolean hitTarget = target != null && target.distance() <= path.length() + 0.75D;
        if (hitTarget && target != null) {
            path = new RailgunPath(target.impact(), Math.max(1.0D, target.distance()));
            applyRailgunEntityImpact(shooter, target.target(), target.impact(), direction, target.quality());
        } else {
            applyRailgunDamageAlongPath(shooter, start, direction, path.length());
        }
        spawnRailgunBeamParticles(world, start, direction, path.length());
        if (!hitTarget) {
            spawnLastBreathTerrainVoidImpact(world, path.impact());
        }
        applyLastBreathRecoil(shooter, direction);
        if (!hitTarget) {
            scorchLastBreathImpact(world, path.impact());
        }

        if (projectile != null && projectile.isValid()) {
            projectile.remove();
        }

        playLastBreathReleaseSound(world, start);

        // No self-damage from Last Breath blast.
        railgunBlastImmunityUntilMs.put(shooter.getUniqueId(), System.currentTimeMillis() + 2500L);
    }

    private void spawnLastBreathReleaseCompression(Player shooter, Location start, Vector direction) {
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        Vector dir = direction.clone().normalize();
        Location muzzle = start.clone().add(dir.clone().multiply(0.75D));
        for (int i = 0; i < 26; i++) {
            double angle = (Math.PI * 2.0D / 26.0D) * i;
            double radius = 0.9D + ((i % 3) * 0.18D);
            Location point = muzzle.clone().add(Math.cos(angle) * radius, ((i % 5) - 2) * 0.08D, Math.sin(angle) * radius);
            Vector inward = muzzle.toVector().subtract(point.toVector()).normalize().multiply(0.055D);
            spawnLastBreathFxParticle(world, Particle.PORTAL, point, 1, inward.getX(), inward.getY(), inward.getZ(), 0.02D);
        }
        spawnLastBreathFxParticle(world, Particle.REVERSE_PORTAL, muzzle, 28, 0.3D, 0.3D, 0.3D, 0.11D);
        spawnLastBreathFxParticle(world, Particle.PORTAL, muzzle, 18, 0.22D, 0.22D, 0.22D, 0.06D);
        spawnLastBreathFxParticle(world, Particle.DRAGON_BREATH, muzzle, 12, 0.18D, 0.18D, 0.18D, 0.012D);
        spawnLastBreathFxParticle(world, Particle.SMOKE, muzzle, 10, 0.16D, 0.16D, 0.16D, 0.018D);
        spawnLastBreathFxParticle(world, Particle.ENCHANTED_HIT, muzzle, 12, 0.18D, 0.18D, 0.18D, 0.02D);
        spawnLastBreathFxParticle(world, Particle.ELECTRIC_SPARK, muzzle, 3, 0.12D, 0.12D, 0.12D, 0.01D);
        shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.48f);
        shooter.playSound(shooter.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.55f);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.48f, 0.62f);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.16f, 0.52f);
        shooter.playSound(shooter.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.28f, 0.72f);
    }

    private void spawnLastBreathFxParticle(World world, Particle particle, Location location, int count,
                                           double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException firstFailure) {
            String message = firstFailure.getMessage();
            try {
                if (message != null && message.contains("java.lang.Float")) {
                    world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, Float.valueOf(1.0f), true);
                    return;
                }
                if (message != null && message.contains("org.bukkit.Color")) {
                    world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, Color.WHITE, true);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Keep Last Breath functional even when a Paper particle needs version-specific data.
            }
        }
    }

    private void playLastBreathReleaseSound(World world, Location start) {
        world.playSound(start, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.45f, 0.38f);
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.45f, 0.44f);
        world.playSound(start, Sound.ENTITY_GENERIC_EXPLODE, 0.58f, 1.62f);
        world.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.48f, 1.32f);
        world.playSound(start, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.42f, 1.45f);
    }

    private RailgunTarget findFirstRailgunTarget(Player shooter, Location start, Vector direction, double maxRange) {
        World world = start.getWorld();
        if (world == null) {
            return null;
        }
        Vector origin = start.toVector();
        Vector dir = direction.clone().normalize();
        RailgunTarget best = null;

        for (Entity entity : world.getNearbyEntities(start, maxRange + 2.0D, maxRange + 2.0D, maxRange + 2.0D)) {
            if (!(entity instanceof LivingEntity living) || living.equals(shooter) || living.isDead()) {
                continue;
            }

            Location centerLoc = living.getLocation().add(0.0D, Math.max(0.3D, living.getHeight() * 0.5D), 0.0D);
            Vector center = centerLoc.toVector();
            Vector toCenter = center.clone().subtract(origin);
            double along = toCenter.dot(dir);
            if (along < 0.0D || along > maxRange) {
                continue;
            }

            Vector closest = origin.clone().add(dir.clone().multiply(along));
            double radial = center.distance(closest);
            double beamRadius = Math.max(1.1D, Math.min(2.2D, living.getWidth() + 0.8D));
            if (radial > beamRadius) {
                continue;
            }

            double quality = railgunShotQuality(radial, beamRadius, along);
            Location impact = closest.toLocation(world);
            if (best == null || along < best.distance()) {
                best = new RailgunTarget(living, impact, along, quality);
            }
        }
        return best;
    }

    private double railgunShotQuality(double radial, double beamRadius, double distance) {
        double directHitQuality = Math.max(0.0D, 1.0D - (radial / Math.max(0.01D, beamRadius)));
        double distanceFactor = Math.max(0.0D, Math.min(1.0D, distance / 70.0D));
        return Math.max(0.0D, Math.min(1.0D, (directHitQuality * 0.78D) + (distanceFactor * 0.22D)));
    }

    private RailgunPath carveLastBreathFracturePath(World world, Location start, Vector direction, double maxRange) {
        Vector dir = direction.clone().normalize();
        Set<String> touchedBlocks = new HashSet<>();
        double penetration = LAST_BREATH_PENETRATION_POWER;
        Location impact = start.clone().add(dir.clone().multiply(maxRange));
        double length = maxRange;

        for (double distance = 0.0D; distance <= maxRange; distance += 0.35D) {
            Location point = start.clone().add(dir.clone().multiply(distance));
            Block block = point.getBlock();
            Material type = block.getType();
            if (type.isAir() || !type.isSolid()) {
                continue;
            }
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!touchedBlocks.add(key)) {
                continue;
            }
            if (!canLegendaryDamageTerrain(block)) {
                impact = point;
                length = distance;
                break;
            }
            if (type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN) {
                fractureLastBreathBlock(block, dir, penetration);
                impact = point;
                length = distance;
                break;
            }
            double cost = lastBreathPenetrationCost(type);
            if (cost > penetration) {
                impact = point;
                length = distance;
                break;
            }
            penetration -= cost;
            if (!fractureLastBreathBlock(block, dir, penetration)) {
                impact = point;
                length = distance;
                break;
            }
            impact = point;
            length = distance;
            if (penetration <= 0.0D) {
                break;
            }
        }
        return new RailgunPath(impact, Math.max(1.0D, length));
    }

    private double lastBreathPenetrationCost(Material type) {
        float hardness = Math.max(0.0F, type.getHardness());
        float resistance = Math.max(0.0F, type.getBlastResistance());
        double cost = 0.65D + (hardness * 1.9D) + (Math.sqrt(resistance) * 0.55D);
        if (isLastBreathSoftSurface(type)) {
            cost *= 0.42D;
        } else if (isLastBreathLeafyOrPlant(type)) {
            cost *= 0.25D;
        }
        return Math.max(0.35D, cost);
    }

    private void applyLastBreathRecoil(Player shooter, Vector direction) {
        if (shooter == null || direction == null || direction.lengthSquared() <= 0.0001D) {
            return;
        }
        Vector recoil = direction.clone().normalize().multiply(-LAST_BREATH_RECOIL);
        recoil.setY(Math.min(0.24D, Math.max(-0.05D, shooter.getVelocity().getY() + 0.08D)));
        shooter.setVelocity(shooter.getVelocity().multiply(0.35D).add(recoil));
        shooter.getWorld().spawnParticle(Particle.SMOKE, shooter.getLocation().add(0.0D, 0.9D, 0.0D), 22, 0.35D, 0.28D, 0.35D, 0.025D);
    }

    private boolean fractureLastBreathBlock(Block block, Vector direction, double remainingPenetration) {
        if (block == null || !canLegendaryDamageTerrain(block)) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.OBSIDIAN) {
            transformObsidianResistance(block);
            return false;
        }
        if (type == Material.CRYING_OBSIDIAN) {
            breakLastBreathBlock(block, Material.CRYING_OBSIDIAN);
            return true;
        }
        if (!isLastBreathFracturable(type)) {
            return false;
        }
        breakLastBreathBlock(block, type);
        if (isLastBreathSoftSurface(type) || isLastBreathLeafyOrPlant(type)) {
            widenLastBreathSoftFracture(block, direction, remainingPenetration);
        }
        return true;
    }

    private void widenLastBreathSoftFracture(Block origin, Vector direction, double remainingPenetration) {
        if (remainingPenetration <= 1.0D) {
            return;
        }
        World world = origin.getWorld();
        int maxExtra = remainingPenetration > 28.0D ? 5 : 3;
        int removed = 0;
        long seed = Objects.hash(world.getName(), origin.getX(), origin.getY(), origin.getZ(), world.getGameTime());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    double distance = Math.sqrt((dx * dx) + (dy * dy * 1.35D) + (dz * dz));
                    if (distance > 1.75D) {
                        continue;
                    }
                    Block candidate = world.getBlockAt(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    Material type = candidate.getType();
                    if ((!isLastBreathSoftSurface(type) && !isLastBreathLeafyOrPlant(type)) || !canLegendaryDamageTerrain(candidate)) {
                        continue;
                    }
                    double forwardBias = direction == null ? 0.0D : new Vector(dx, dy, dz).dot(direction.clone().normalize()) * 0.08D;
                    double chance = 0.58D - (distance * 0.18D) + forwardBias;
                    if (crackedPatternNoise(seed, dx, dy, dz) > chance) {
                        continue;
                    }
                    breakLastBreathBlock(candidate, type);
                    removed++;
                    if (removed >= maxExtra) {
                        return;
                    }
                }
            }
        }
    }

    private void transformObsidianResistance(Block block) {
        if (block == null || block.getType() != Material.OBSIDIAN) {
            return;
        }
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5D, 0.5D, 0.5D);
        block.setType(Material.CRYING_OBSIDIAN, false);
        world.spawnParticle(Particle.PORTAL, loc, 20, 0.25D, 0.25D, 0.25D, 0.03D);
        world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 18, 0.35D, 0.35D, 0.35D, 0.025D, Material.OBSIDIAN.createBlockData());
        world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.25f);
        spreadLastBreathObsidianCracks(block);
    }

    private void spreadLastBreathObsidianCracks(Block origin) {
        World world = origin.getWorld();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        long seed = Objects.hash(world.getName(), ox, oy, oz, world.getGameTime());
        int converted = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    double distance = Math.sqrt((dx * dx) + (dy * dy * 1.8D) + (dz * dz));
                    if (distance > 2.25D) {
                        continue;
                    }
                    Block candidate = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (candidate.getType() != Material.OBSIDIAN || !canLegendaryDamageTerrain(candidate)) {
                        continue;
                    }
                    double chance = Math.max(0.08D, 0.58D - (distance * 0.22D));
                    double noise = crackedPatternNoise(seed, dx, dy, dz);
                    boolean axialCrack = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 2 && noise < 0.72D;
                    if (!axialCrack && noise > chance) {
                        continue;
                    }
                    candidate.setType(Material.CRYING_OBSIDIAN, false);
                    Location loc = candidate.getLocation().add(0.5D, 0.5D, 0.5D);
                    world.spawnParticle(Particle.PORTAL, loc, 8, 0.2D, 0.2D, 0.2D, 0.025D);
                    converted++;
                    if (converted >= 6) {
                        return;
                    }
                }
            }
        }
    }

    private double crackedPatternNoise(long seed, int dx, int dy, int dz) {
        long value = seed;
        value ^= (long) (dx + 31) * 0x9E3779B97F4A7C15L;
        value ^= (long) (dy + 17) * 0xBF58476D1CE4E5B9L;
        value ^= (long) (dz + 43) * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private void breakLastBreathBlock(Block block, Material originalType) {
        World world = block.getWorld();
        Location breakLoc = block.getLocation().add(0.5D, 0.5D, 0.5D);
        block.setType(Material.AIR, false);
        world.spawnParticle(Particle.BLOCK, breakLoc, 18, 0.28D, 0.28D, 0.28D, 0.02D, originalType.createBlockData());
        world.spawnParticle(Particle.ASH, breakLoc, 10, 0.22D, 0.22D, 0.22D, 0.02D);
        world.playSound(breakLoc, Sound.BLOCK_DEEPSLATE_BREAK, 0.85f, 0.72f);
    }

    private void applyRailgunEntityImpact(Player shooter, LivingEntity target, Location impact, Vector direction, double quality) {
        if (target == null || target.isDead()) {
            return;
        }
        if (TeamProtectionService.blocks(shooter, target)) return;
        World world = target.getWorld();
        Location center = impact == null ? target.getLocation().add(0.0D, target.getHeight() * 0.5D, 0.0D) : impact;
        double clampedQuality = Math.max(0.0D, Math.min(1.0D, quality));
        double directTrueDamage = 8.0D + (clampedQuality * 8.0D); // 4-8 hearts, armor/protection ignored.

        target.setNoDamageTicks(0);
        target.damage(0.01D, shooter);
        if (!target.isDead()) {
            target.setHealth(Math.max(0.0D, target.getHealth() - directTrueDamage));
        }
        Vector knockback = direction.clone().normalize().multiply(0.34D).setY(0.14D);
        target.setVelocity(target.getVelocity().multiply(0.35D).add(knockback));

        spawnLastBreathFxParticle(world, Particle.FLASH, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        spawnLastBreathFxParticle(world, Particle.EXPLOSION_EMITTER, center, 1, 0.08D, 0.08D, 0.08D, 0.0D);
        spawnLastBreathFxParticle(world, Particle.DRAGON_BREATH, center, 10, 0.2D, 0.16D, 0.2D, 0.014D);
        spawnLastBreathFxParticle(world, Particle.REVERSE_PORTAL, center, 24, 0.28D, 0.28D, 0.28D, 0.08D);
        spawnLastBreathFxParticle(world, Particle.ASH, center, 14, 0.34D, 0.24D, 0.34D, 0.016D);
        spawnLastBreathFxParticle(world, Particle.LARGE_SMOKE, center, 8, 0.34D, 0.2D, 0.34D, 0.022D);
        spawnLastBreathImpactShockwave(world, center);
        spawnLastBreathAftermath(world, center);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.92f, 1.4f);
        world.playSound(center, Sound.ENTITY_WARDEN_HURT, 1.05f, 0.58f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.18f, 0.58f);
        world.playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.18f, 0.55f);
        world.playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.72f, 0.72f);
        playLastBreathPressureForNearbyPlayers(world, center);
        applyLastBreathImpactAoe(shooter, target, center, direction);
    }

    private void spawnLastBreathAftermath(World world, Location center) {
        for (int pulse = 1; pulse <= 3; pulse++) {
            final int step = pulse;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnLastBreathFxParticle(world, Particle.SMOKE, center, Math.max(2, 8 - step * 2), 0.38D, 0.15D, 0.38D, 0.014D);
                spawnLastBreathFxParticle(world, Particle.ASH, center, Math.max(2, 7 - step), 0.36D, 0.15D, 0.36D, 0.014D);
                spawnLastBreathFxParticle(world, Particle.ELECTRIC_SPARK, center, Math.max(1, 4 - step), 0.24D, 0.14D, 0.24D, 0.01D);
                spawnLastBreathFxParticle(world, Particle.REVERSE_PORTAL, center, 3, 0.26D, 0.2D, 0.26D, 0.025D);
            }, step * 5L);
        }
    }

    private void spawnLastBreathImpactShockwave(World world, Location center) {
        for (int i = 0; i < 14; i++) {
            double angle = (Math.PI * 2.0D / 14.0D) * i;
            Location point = center.clone().add(Math.cos(angle) * 1.55D, 0.05D, Math.sin(angle) * 1.55D);
            spawnLastBreathFxParticle(world, Particle.ELECTRIC_SPARK, point, 1, 0.02D, 0.02D, 0.02D, 0.004D);
        }
        spawnLastBreathFxParticle(world, Particle.SONIC_BOOM, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void playLastBreathPressureForNearbyPlayers(World world, Location center) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) > 100.0D) {
                continue;
            }
            player.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.45f, 0.45f);
            player.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 0.48f);
        }
    }

    private void applyLastBreathImpactAoe(Player shooter, LivingEntity directTarget, Location center, Vector direction) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        for (Entity entity : world.getNearbyEntities(center, 3.2D, 2.2D, 3.2D)) {
            if (!(entity instanceof LivingEntity living)
                || living.equals(shooter)
                || living.equals(directTarget)
                || living.isDead()) {
                continue;
            }
            if (TeamProtectionService.blocks(shooter, living)) continue;
            living.setNoDamageTicks(0);
            living.damage(2.0D, shooter);
            Vector knockback = living.getLocation().toVector().subtract(center.toVector());
            if (knockback.lengthSquared() <= 0.0001D) {
                knockback = direction.clone();
            }
            living.setVelocity(knockback.normalize().multiply(0.55D).setY(0.22D));
        }
        world.spawnParticle(Particle.ASH, center, 6, 0.65D, 0.18D, 0.65D, 0.02D);
    }

    private int applyRailgunDamageAlongPath(Player shooter, Location start, Vector direction, double beamLength) {
        World world = start.getWorld();
        Vector origin = start.toVector();
        Vector dir = direction.clone().normalize();
        int affected = 0;
        Set<UUID> hit = new HashSet<>();

        for (Entity entity : world.getNearbyEntities(start, beamLength + 2.0D, beamLength + 2.0D, beamLength + 2.0D)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(shooter) || living.isDead()) {
                continue;
            }
            if (!hit.add(living.getUniqueId())) {
                continue;
            }

            Location centerLoc = living.getLocation().add(0.0D, Math.max(0.3D, living.getHeight() * 0.5D), 0.0D);
            Vector center = centerLoc.toVector();
            Vector toCenter = center.clone().subtract(origin);
            double along = toCenter.dot(dir);
            if (along < 0.0D || along > beamLength) {
                continue;
            }

            Vector closest = origin.clone().add(dir.clone().multiply(along));
            double radial = center.distance(closest);
            double beamRadius = Math.max(1.1D, Math.min(2.2D, living.getWidth() + 0.8D));
            if (radial > beamRadius) {
                continue;
            }

            double quality = railgunShotQuality(radial, beamRadius, along);
            applyRailgunEntityImpact(shooter, living, closest.toLocation(world), direction, quality);
            affected++;
        }
        return affected;
    }

    private void spawnLastBreathTerrainVoidImpact(World world, Location impact) {
        if (world == null || impact == null) {
            return;
        }
        spawnLastBreathFxParticle(world, Particle.FLASH, impact, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        spawnLastBreathFxParticle(world, Particle.EXPLOSION_EMITTER, impact, 1, 0.06D, 0.06D, 0.06D, 0.0D);
        spawnLastBreathFxParticle(world, Particle.REVERSE_PORTAL, impact, 18, 0.32D, 0.24D, 0.32D, 0.08D);
        spawnLastBreathFxParticle(world, Particle.DRAGON_BREATH, impact, 7, 0.24D, 0.16D, 0.24D, 0.012D);
        spawnLastBreathFxParticle(world, Particle.ASH, impact, 12, 0.34D, 0.18D, 0.34D, 0.014D);
        spawnLastBreathFxParticle(world, Particle.LARGE_SMOKE, impact, 8, 0.36D, 0.16D, 0.36D, 0.022D);
        spawnLastBreathImpactShockwave(world, impact);
        spawnLastBreathAftermath(world, impact);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.82f, 1.42f);
        world.playSound(impact, Sound.ENTITY_WARDEN_HURT, 0.92f, 0.62f);
        world.playSound(impact, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.05f, 0.6f);
        world.playSound(impact, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.14f, 0.55f);
        world.playSound(impact, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.58f, 0.78f);
    }

    private void scorchLastBreathImpact(World world, Location impact) {
        if (world == null || impact == null) {
            return;
        }
        spawnLastBreathFxParticle(world, Particle.ASH, impact, 24, 0.55D, 0.25D, 0.55D, 0.035D);
        spawnLastBreathFxParticle(world, Particle.CAMPFIRE_COSY_SMOKE, impact, 12, 0.45D, 0.25D, 0.45D, 0.012D);
        Block surface = findLastBreathSurfaceBlock(world, impact);
        if (surface == null || !canLegendaryDamageTerrain(surface) || !isLastBreathSoftSurface(surface.getType())) {
            return;
        }
        Material scar = ThreadLocalRandom.current().nextBoolean() ? Material.BLACKSTONE : Material.COBBLED_DEEPSLATE;
        surface.setType(scar, false);
        Block above = surface.getRelative(0, 1, 0);
        if (above.getType() == Material.SHORT_GRASS
            || above.getType() == Material.TALL_GRASS
            || above.getType() == Material.FERN
            || above.getType() == Material.LARGE_FERN
            || above.getType().name().endsWith("_FLOWER")
            || above.getType().name().endsWith("_SAPLING")) {
            above.setType(Material.AIR, false);
        }
    }

    private Block findLastBreathSurfaceBlock(World world, Location impact) {
        int x = impact.getBlockX();
        int z = impact.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 2, impact.getBlockY() + 2);
        int minY = Math.max(world.getMinHeight(), impact.getBlockY() - 3);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir() && block.getType().isSolid()) {
                return block;
            }
        }
        return null;
    }

    private boolean canLegendaryDamageTerrain(Block block) {
        if (block == null || block.getState() instanceof Container) {
            return false;
        }
        if (plugin instanceof HGLootPlugin hydra && hydra.queueService() != null) {
            return !hydra.queueService().isLegendaryTerrainDamageProtected(block);
        }
        return true;
    }

    private boolean isLastBreathFracturable(Material type) {
        if (type == null || type.isAir()) {
            return false;
        }
        if (type == Material.BEDROCK
            || type == Material.END_PORTAL_FRAME
            || type == Material.END_PORTAL
            || type == Material.NETHER_PORTAL
            || type == Material.REINFORCED_DEEPSLATE) {
            return false;
        }
        return type.isBlock() && type.getHardness() >= 0.0F && type.getBlastResistance() < 1200.0F;
    }

    private boolean isLastBreathSoftSurface(Material type) {
        return type == Material.GRASS_BLOCK
            || type == Material.DIRT
            || type == Material.COARSE_DIRT
            || type == Material.ROOTED_DIRT
            || type == Material.PODZOL
            || type == Material.MYCELIUM
            || type == Material.SAND
            || type == Material.RED_SAND
            || type == Material.GRAVEL
            || type == Material.MOSS_BLOCK
            || type == Material.MUD;
    }

    private boolean isLastBreathLeafyOrPlant(Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_LEAVES")
            || name.endsWith("_SAPLING")
            || name.endsWith("_FLOWER")
            || name.endsWith("_MUSHROOM")
            || name.endsWith("_ROOTS")
            || name.endsWith("_VINES")
            || type == Material.SHORT_GRASS
            || type == Material.TALL_GRASS
            || type == Material.FERN
            || type == Material.LARGE_FERN
            || type == Material.DEAD_BUSH
            || type == Material.VINE
            || type == Material.BAMBOO
            || type == Material.SUGAR_CANE;
    }

    private void spawnRailgunBeamParticles(World world, Location start, Vector direction, double beamLength) {
        Particle.DustTransition core = new Particle.DustTransition(
            Color.fromRGB(125, 22, 220),
            Color.fromRGB(248, 248, 255),
            2.15f
        );
        Vector dir = direction.clone().normalize();

        kickLastBreathTravelReaction(world, start, dir, beamLength);

        for (int pulse = 0; pulse < 4; pulse++) {
            final int step = pulse;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double spacing = step == 0 ? 0.48D : 0.95D;
                int coreCount = step == 0 ? 3 : 1;
                int glowCount = step <= 1 ? 2 : 1;
                for (double d = 0.0D; d <= beamLength; d += spacing) {
                    Location point = start.clone().add(dir.clone().multiply(d));
                    spawnLastBreathFxParticle(world, Particle.END_ROD, point, coreCount, 0.014D, 0.014D, 0.014D, 0.003D);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 1, 0.025D, 0.025D, 0.025D, 0.0D, core, true);
                    if (step == 0 && ((int) (d * 10.0D)) % 10 == 0) {
                        spawnLastBreathFxParticle(world, Particle.ELECTRIC_SPARK, point, 1, 0.02D, 0.02D, 0.02D, 0.004D);
                    }
                    if (step <= 2 && ((int) (d * 10.0D + step)) % 11 == 0) {
                        spawnLastBreathFxParticle(world, Particle.CRIT, point, glowCount, 0.1D, 0.1D, 0.1D, 0.016D);
                    }
                    if (step <= 1 && ((int) (d * 2.0D + step)) % 7 == 0) {
                        Location arc = point.clone().add(Math.sin(d * 1.7D + step) * 0.28D, Math.cos(d * 1.1D) * 0.16D, Math.cos(d * 1.4D + step) * 0.28D);
                        spawnLastBreathFxParticle(world, Particle.ELECTRIC_SPARK, arc, 1, 0.04D, 0.04D, 0.04D, 0.008D);
                        spawnLastBreathFxParticle(world, Particle.REVERSE_PORTAL, arc, 1, 0.08D, 0.08D, 0.08D, 0.025D);
                    }
                    if (step == 0 && ((int) d) % 16 == 0) {
                        spawnLastBreathFxParticle(world, Particle.SONIC_BOOM, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    }
                }
            }, pulse * 4L);
        }
    }

    private void kickLastBreathTravelReaction(World world, Location start, Vector direction, double beamLength) {
        Vector dir = direction.clone().normalize();
        for (double d = 1.0D; d <= beamLength; d += 1.75D) {
            Location point = start.clone().add(dir.clone().multiply(d));
            Block base = point.getBlock();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) {
                            continue;
                        }
                        Block candidate = base.getRelative(dx, dy, dz);
                        Material type = candidate.getType();
                        if (isLastBreathLeafyOrPlant(type) && canLegendaryDamageTerrain(candidate)) {
                            breakLastBreathBlock(candidate, type);
                            continue;
                        }
                        if (candidate.getType().isSolid() && crackedPatternNoise(world.getGameTime(), candidate.getX(), candidate.getY(), candidate.getZ()) < 0.12D) {
                            Location dust = candidate.getLocation().add(0.5D, 0.65D, 0.5D);
                            world.spawnParticle(Particle.BLOCK, dust, 3, 0.22D, 0.07D, 0.22D, 0.01D, candidate.getBlockData());
                            spawnLastBreathFxParticle(world, Particle.ASH, dust, 1, 0.16D, 0.05D, 0.16D, 0.008D);
                            if (crackedPatternNoise(world.getGameTime() + 17L, candidate.getX(), candidate.getY(), candidate.getZ()) < 0.16D) {
                                spawnLastBreathFxParticle(world, Particle.CLOUD, dust, 1, 0.12D, 0.04D, 0.12D, 0.006D);
                                spawnLastBreathFxParticle(world, Particle.CAMPFIRE_COSY_SMOKE, dust, 1, 0.1D, 0.04D, 0.1D, 0.004D);
                            }
                            if (crackedPatternNoise(world.getGameTime() + 41L, candidate.getX(), candidate.getY(), candidate.getZ()) < 0.04D) {
                                spawnLastBreathFxParticle(world, Particle.EXPLOSION, dust, 1, 0.02D, 0.02D, 0.02D, 0.0D);
                            }
                        }
                    }
                }
            }
        }
    }

    private void startSonicTrail(AbstractArrow arrow) {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                task.cancel();
                return;
            }
            Location location = arrow.getLocation().add(0.0, 0.4, 0.0);
            World world = location.getWorld();
            if (world == null) {
                task.cancel();
                return;
            }
            world.spawnParticle(Particle.SONIC_BOOM, location, 1, 0.0, 0.0, 0.0, 0.0);
            world.playSound(location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        }, 1L, 2L);
    }

    private void triggerSonicHit(AbstractArrow arrow) {
        Location impact = arrow.getLocation().clone();
        World world = impact.getWorld();
        if (world == null) {
            return;
        }

        Player shooter = arrow.getShooter() instanceof Player p ? p : null;
        world.spawnParticle(Particle.EXPLOSION, impact, 8, 0.35D, 0.35D, 0.35D, 0.01D);
        world.spawnParticle(Particle.SONIC_BOOM, impact.clone().add(0.0D, 0.4D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.playSound(impact, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.95f);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);

        int radius = 10;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int cx = impact.getBlockX();
        int cy = impact.getBlockY();
        int cz = impact.getBlockZ();
        final double trueDamage = 4.0D; // 2 hearts true damage

        for (Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (shooter != null && living.getUniqueId().equals(shooter.getUniqueId())) {
                continue;
            }

            // True-damage style: direct health reduction ignores armor scaling.
            double newHealth = Math.max(0.0D, living.getHealth() - trueDamage);
            living.setNoDamageTicks(0);
            living.setLastDamage(trueDamage);
            living.setHealth(newHealth);
        }

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    double dx = x - cx;
                    double dy = y - cy;
                    double dz = z - cz;
                    if ((dx * dx + dy * dy + dz * dz) > (radius * radius)) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == Material.AIR || type == Material.WATER) {
                        continue;
                    }
                    if (random.nextDouble() <= 0.35D) {
                        block.setType(Material.SCULK, false);
                    }
                }
            }
        }
    }

    private Block relativeForward(Player player, int forward, int side) {
        double x = player.getLocation().getDirection().getX();
        double z = player.getLocation().getDirection().getZ();
        int dx;
        int dz;
        if (Math.abs(x) >= Math.abs(z)) {
            dx = x >= 0 ? 1 : -1;
            dz = 0;
        } else {
            dx = 0;
            dz = z >= 0 ? 1 : -1;
        }
        // side vector is perpendicular in XZ plane
        int sx = -dz;
        int sz = dx;
        return player.getLocation().getBlock().getRelative(dx * forward + sx * side, 0, dz * forward + sz * side);
    }

    private Inventory menuInventory(int size, String title) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, GuiTitleFactory.menuTitle(size, title));
        holder.setInventory(inv);
        return inv;
    }

    private Inventory themedMenuInventory(int size, String title, String theme) {
        MenuHolder holder = new MenuHolder(theme);
        Component inventoryTitle;
        if ("gamemode_switcher".equalsIgnoreCase(theme)) {
            inventoryTitle = GuiTitleFactory.gamemodeSwitcherTitle(title);
        } else if ("navigator".equalsIgnoreCase(theme)) {
            inventoryTitle = GuiTitleFactory.navigatorTitle(title);
        } else if (isCustomGuiTheme(theme)) {
            inventoryTitle = GuiTitleFactory.themedTitle(theme, title);
        } else {
            inventoryTitle = GuiTitleFactory.menuTitle(size, title);
        }
        Inventory inv = Bukkit.createInventory(holder, size, inventoryTitle);
        holder.setInventory(inv);
        return inv;
    }

    private boolean ensurePlayerMenuCompass(Player player) {
        return ensurePlayerHubHotbar(player);
    }

    private static boolean isCustomGuiTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return false;
        }
        return switch (theme.toLowerCase(Locale.ROOT)) {
            case "cosmetics", "hats", "cosmetics_favorites", "hydra_styles", "rank_styles", "kill_effects",
                "kill_messages", "recipes", "socials", "social", "weapon_skins",
                "legendary_skins", "trails", "projectile_trails", "trims", "armor_trims",
                "navigator_minigames", "navigator_hunger_games", "minigames", "friends_all", "friends_requests",
                "friends_blocked", "party_leader", "party_member", "kit_editor",
                "settings_page_1", "settings_page_2" -> true;
            default -> false;
        };
    }

    private static String cosmeticDomainTheme(CosmeticDomain domain) {
        if (domain == null) {
            return "cosmetics";
        }
        return switch (domain) {
            case GAME -> "cosmetics";
            case LOBBY -> "hats";
            case RANK -> "hydra_styles";
        };
    }

    private static String cosmeticCategoryTheme(CosmeticCategory category) {
        if (category == null) {
            return "cosmetics";
        }
        return switch (category) {
            case LEGENDARY_SKINS -> "weapon_skins";
            case PROJECTILE_TRAILS -> "trails";
            case ARMOR_TRIMS -> "trims";
            case KILL_EFFECTS -> "kill_effects";
            case KILL_MESSAGES -> "kill_messages";
            case HATS -> "hats";
            case RANK_STYLES -> "hydra_styles";
            case TITLES -> "cosmetics";
        };
    }

    private void applyWorldInventoryMode(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin instanceof HGLootPlugin hydra && hydra.kitEditorService() != null
            && (hydra.kitEditorService().isEditing(player.getUniqueId())
                || hydra.kitEditorService().isRecovering(player.getUniqueId()))) {
            return;
        }
        if (plugin instanceof HGLootPlugin hydra
            && hydra.gameHubService() != null
            && hydra.gameHubService().isFightPit(player.getLocation())) {
            return;
        }
        if (isMatchWorld(player.getWorld())) {
            clearHubMenuItems(player);
            refreshHeadCosmetic(player);
            return;
        }
        ensurePlayerHubHotbar(player);
        refreshHeadCosmetic(player);
    }

    private boolean ensurePlayerHubHotbar(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (isMatchWorld(player.getWorld())) {
            clearHubMenuItems(player);
            return true;
        }
        boolean gameHub = plugin instanceof HGLootPlugin hydra && hydra.gameHubService() != null
            && hydra.gameHubService().isGameHub(player.getWorld());
        if (!gameHub && !isHubHotbarEnabled(player.getUniqueId())) {
            clearHubMenuItems(player);
            return true;
        }
        if (!player.hasPermission("hgloot.playermenu")) {
            return false;
        }

        stripExtraPlayerMenuCompasses(player, gameHub);
        if (gameHub) {
            ensureHubSlot(player, NAVIGATOR_SLOT, createUtilityBookHubItem());
            ensureHubSlot(player, QUEUE_CONTROL_SLOT, createGameHubLeaveItem());
            return HUB_ITEM_UTILITY_BOOK.equals(readHubItemAction(player.getInventory().getItem(NAVIGATOR_SLOT)))
                && HUB_ITEM_LEAVE_GAMEHUB.equals(readHubItemAction(player.getInventory().getItem(QUEUE_CONTROL_SLOT)));
        }
        ensureHubSlot(player, NAVIGATOR_SLOT, createNavigatorHubItem());
        ensureHubSlot(player, ECONOMY_SLOT, createEconomyHubItem());
        ensureHubSlot(player, SETTINGS_SLOT, createSettingsHubItem());
        ensureHubSlot(player, BATTLE_PASS_SLOT, createBattlePassHubItem());
        boolean queuedForRoulette = minigameRouletteService != null
            && minigameRouletteService.isQueued(player.getUniqueId());
        QueueService queueService = queueServiceOrNull();
        boolean queuedForHungerGames = queueService != null
            && (queueService.isPlayerQueuedFor(player.getUniqueId(), QueueService.QueueType.HUNGERGAMES)
                || queueService.isPlayerQueuedFor(player.getUniqueId(), QueueService.QueueType.HUNGERGAMES_DUOS));
        clearHubSlotIfAction(player, QUEUE_CONTROL_SLOT,
            queuedForRoulette ? HUB_ITEM_LEAVE_HUNGER_GAMES : HUB_ITEM_LEAVE_ROULETTE);
        syncConditionalHubSlot(player, QUEUE_CONTROL_SLOT,
            queuedForRoulette ? HUB_ITEM_LEAVE_ROULETTE : HUB_ITEM_LEAVE_HUNGER_GAMES,
            queuedForRoulette || queuedForHungerGames,
            queuedForRoulette ? createLeaveRouletteItem() : createLeaveHungerGamesItem());
        ProgressionService progression = progressionServiceOrNull();
        syncConditionalHubSlot(player, SHARD_CLAIM_SLOT, HUB_ITEM_CLAIM_SHARD,
            progression != null && progression.shards(player.getUniqueId()) > 0,
            createClaimShardItem());
        ensureHubSlot(player, COSMETICS_SLOT, createCosmeticsHubItem(player));
        ensureHubSlot(player, SOCIAL_SLOT, createSocialHubItem(player));

        return HUB_ITEM_NAVIGATOR.equals(readHubItemAction(player.getInventory().getItem(NAVIGATOR_SLOT)))
            && HUB_ITEM_ECONOMY.equals(readHubItemAction(player.getInventory().getItem(ECONOMY_SLOT)))
            && HUB_ITEM_SETTINGS.equals(readHubItemAction(player.getInventory().getItem(SETTINGS_SLOT)))
            && HUB_ITEM_BATTLE_PASS.equals(readHubItemAction(player.getInventory().getItem(BATTLE_PASS_SLOT)))
            && HUB_ITEM_COSMETICS.equals(readHubItemAction(player.getInventory().getItem(COSMETICS_SLOT)))
            && HUB_ITEM_SOCIAL.equals(readHubItemAction(player.getInventory().getItem(SOCIAL_SLOT)));
    }

    private void clearHubSlotIfAction(Player player, int slot, String action) {
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && action.equals(readHubItemAction(existing))) {
            player.getInventory().setItem(slot, null);
        }
    }

    private void loadHubHotbarSettings() {
        hubHotbarDisabledPlayers.clear();
        List<String> ids = plugin.getConfig().getStringList("hub.hotbar.disabled_players");
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                hubHotbarDisabledPlayers.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveHubHotbarSettings() {
        List<String> ids = hubHotbarDisabledPlayers.stream()
            .map(UUID::toString)
            .sorted()
            .toList();
        plugin.getConfig().set("hub.hotbar.disabled_players", ids);
        plugin.saveConfig();
    }

    private void stripExtraPlayerMenuCompasses(Player player, boolean gameHub) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (!isPlayerMenuCompass(contents[i])) {
                continue;
            }
            String action = readHubItemAction(contents[i]);
            if (expectedHubSlot(action, gameHub) != i) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void ensureHubSlot(Player player, int slot, ItemStack expectedItem) {
        ItemStack current = player.getInventory().getItem(slot);
        String expectedAction = readHubItemAction(expectedItem);
        if (isExpectedHubItem(current, expectedItem, expectedAction)) {
            return;
        }
        if (current != null && current.getType() != Material.AIR && !isPlayerMenuCompass(current)) {
            moveItemOutOfNavigatorSlot(player, current);
        }
        player.getInventory().setItem(slot, expectedItem);
    }

    private boolean isExpectedHubItem(ItemStack current, ItemStack expectedItem, String expectedAction) {
        if (current == null || expectedItem == null || expectedAction == null) {
            return false;
        }
        if (current.getType() != expectedItem.getType()) {
            return false;
        }
        if (!expectedAction.equals(readHubItemAction(current))) {
            return false;
        }
        if (!current.hasItemMeta() || !expectedItem.hasItemMeta()) {
            return false;
        }

        ItemMeta currentMeta = current.getItemMeta();
        ItemMeta expectedMeta = expectedItem.getItemMeta();
        if (currentMeta == null || expectedMeta == null) {
            return false;
        }
        if (!Objects.equals(currentMeta.getDisplayName(), expectedMeta.getDisplayName())) {
            return false;
        }

        boolean currentHasCmd = currentMeta.hasCustomModelData();
        boolean expectedHasCmd = expectedMeta.hasCustomModelData();
        if (currentHasCmd != expectedHasCmd) {
            return false;
        }
        boolean currentHasItemModel = currentMeta.hasItemModel();
        boolean expectedHasItemModel = expectedMeta.hasItemModel();
        if (currentHasItemModel != expectedHasItemModel) {
            return false;
        }
        if (currentHasItemModel && !Objects.equals(currentMeta.getItemModel(), expectedMeta.getItemModel())) {
            return false;
        }
        if (currentHasCmd && currentMeta.getCustomModelData() != expectedMeta.getCustomModelData()) {
            return false;
        }
        return true;
    }

    private int expectedHubSlot(String action, boolean gameHub) {
        if (action == null) {
            return -1;
        }
        if (gameHub) {
            return switch (action) {
                case HUB_ITEM_UTILITY_BOOK -> NAVIGATOR_SLOT;
                case HUB_ITEM_LEAVE_GAMEHUB -> QUEUE_CONTROL_SLOT;
                default -> -1;
            };
        }
        return switch (action) {
            case HUB_ITEM_NAVIGATOR -> NAVIGATOR_SLOT;
            case HUB_ITEM_ECONOMY -> ECONOMY_SLOT;
            case HUB_ITEM_SETTINGS -> SETTINGS_SLOT;
            case HUB_ITEM_BATTLE_PASS -> BATTLE_PASS_SLOT;
            case HUB_ITEM_LEAVE_ROULETTE, HUB_ITEM_LEAVE_HUNGER_GAMES -> QUEUE_CONTROL_SLOT;
            case HUB_ITEM_CLAIM_SHARD -> SHARD_CLAIM_SLOT;
            case HUB_ITEM_COSMETICS -> COSMETICS_SLOT;
            case HUB_ITEM_SOCIAL -> SOCIAL_SLOT;
            default -> -1;
        };
    }

    private void moveItemOutOfNavigatorSlot(Player player, ItemStack slotItem) {
        if (slotItem == null || slotItem.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(slotItem.clone());
        if (overflow.isEmpty()) {
            return;
        }
        for (ItemStack item : overflow.values()) {
            if (item != null && item.getType() != Material.AIR) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private ItemStack createNavigatorHubItem() {
        return createHubItem(Material.COMPASS, MAIN_TITLE_COLOR + "Navigator " + SECONDARY_TEXT_COLOR + "(Right Click)", HUB_ITEM_NAVIGATOR, true);
    }

    private ItemStack createEconomyHubItem() {
        return createHubItemWithModel(Material.GOLD_NUGGET, MAIN_TITLE_COLOR + "Auction House", HUB_ITEM_ECONOMY, false, "hydra:ah");
    }

    private ItemStack createSettingsHubItem() {
        return createHubItem(Material.COMPARATOR, MAIN_TITLE_COLOR + "Settings", HUB_ITEM_SETTINGS,
            false, null, "hydra:settings_hub_icon", ChatColor.GRAY + "Right Click to open settings.");
    }

    static boolean isTrailEligibleProjectile(Projectile projectile) {
        // A fishing hook can remain attached to another player's head. A repeating
        // combat trail there becomes a first-person particle flood, not a shot trail.
        return projectile != null && !(projectile instanceof FishHook);
    }

    private ItemStack createBattlePassHubItem() {
        return createHubItem(Material.NETHER_STAR, MAIN_TITLE_COLOR + "Battle Pass", HUB_ITEM_BATTLE_PASS,
            false, null, "hydra:battle_pass_hub_icon", ChatColor.GRAY + "Right Click to view your progress.");
    }

    private ItemStack createUtilityBookHubItem() {
        return createHubItemWithModel(Material.BOOK, MAIN_TITLE_COLOR + "Utility Book", HUB_ITEM_UTILITY_BOOK,
            false, "hydra:recipe_book");
    }

    private ItemStack createGameHubLeaveItem() {
        return createHubItem(Material.PAPER, ChatColor.RED + "Leave", HUB_ITEM_LEAVE_GAMEHUB,
            false, null, "hydra:backspace", ChatColor.GRAY + "Right Click to return to Hub.");
    }

    private ItemStack createLeaveRouletteItem() {
        return createHubItem(Material.PAPER, ChatColor.RED + "" + ChatColor.BOLD + "Leave Queue",
            HUB_ITEM_LEAVE_ROULETTE, false, null, "hydra:backspace",
            ChatColor.GRAY + "Right Click to leave",
            ChatColor.GRAY + "Minigame Roulette.");
    }

    private ItemStack createLeaveHungerGamesItem() {
        return createHubItem(Material.PAPER, ChatColor.RED + "" + ChatColor.BOLD + "Leave Queue",
            HUB_ITEM_LEAVE_HUNGER_GAMES, false, null, "hydra:backspace",
            ChatColor.GRAY + "Right Click to leave",
            ChatColor.GRAY + "Hunger Games.");
    }

    private ItemStack createClaimShardItem() {
        ProgressionService progressionService = progressionServiceOrNull();
        int fragmentsPerShard = progressionService == null
            ? ProgressionService.FRAGMENTS_PER_SHARD
            : progressionService.fragmentsPerShard();
        return createHubItem(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Claim Shard",
            HUB_ITEM_CLAIM_SHARD, false, null, "hydra:shard",
            ChatColor.GRAY + "You have " + fragmentsPerShard + " / " + fragmentsPerShard + " Fragments.",
            "",
            ChatColor.YELLOW + "Right Click to open the",
            ChatColor.YELLOW + "Shard Claim menu.");
    }

    private void syncConditionalHubSlot(Player player, int slot, String action, boolean enabled, ItemStack item) {
        String warningKey = player.getUniqueId() + ":" + action;
        clearHubSlotIfAction(player, slot, action);
        if (!enabled) {
            conditionalHotbarWarnings.remove(warningKey);
            return;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType() == Material.AIR || isPlayerMenuCompass(current)) {
            player.getInventory().setItem(slot, item);
            conditionalHotbarWarnings.remove(warningKey);
            return;
        }
        int safeSlot = player.getInventory().firstEmpty();
        if (safeSlot < 0) {
            if (conditionalHotbarWarnings.add(warningKey)) {
                String fallback = HUB_ITEM_CLAIM_SHARD.equals(action)
                    ? "Your inventory is full. Use /shard claim to claim your ready Shard."
                    : "Your inventory is full. Use /leave to leave Minigame Roulette.";
                player.sendMessage(ChatColor.YELLOW + fallback);
            }
            return;
        }
        player.getInventory().setItem(safeSlot, current.clone());
        player.getInventory().setItem(slot, item);
        conditionalHotbarWarnings.remove(warningKey);
    }

    private ItemStack createCosmeticsHubItem(Player player) {
        return createHubItemWithModel(Material.ENDER_CHEST, MAIN_TITLE_COLOR + "Cosmetics", HUB_ITEM_COSMETICS, false, "hydra:cosmetics");
    }

    private ItemStack createSocialHubItem(Player player) {
        ItemStack stack = createHubItem(Material.PLAYER_HEAD, MAIN_TITLE_COLOR + "Social", HUB_ITEM_SOCIAL);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    private String colorSelected(String cosmeticId, String selectedId) {
        if (Objects.equals(cosmeticId, selectedId)) {
            return ChatColor.GREEN + "" + ChatColor.BOLD;
        }
        return ChatColor.YELLOW.toString();
    }

    private String cosmeticDisplayName(CosmeticDefinition cosmetic, boolean owned, boolean selected) {
        String displayName = cosmetic == null || cosmetic.displayName == null || cosmetic.displayName.isBlank()
            ? "Unknown Cosmetic"
            : cosmetic.displayName;
        ChatColor rarityColor = rarityColor(cosmetic == null ? null : cosmetic.rarity);
        if (selected) {
            return ChatColor.GREEN + displayName;
        }
        if (owned) {
            return rarityColor + displayName;
        }
        return ChatColor.DARK_GRAY + displayName;
    }

    private ChatColor rarityColor(String rarity) {
        if (rarity == null || rarity.isBlank()) {
            return ChatColor.GRAY;
        }
        return switch (rarity.trim().toLowerCase(Locale.ROOT)) {
            case "legendary" -> ChatColor.GOLD;
            case "epic" -> ChatColor.LIGHT_PURPLE;
            case "rare" -> ChatColor.BLUE;
            case "uncommon" -> ChatColor.GREEN;
            default -> ChatColor.GRAY;
        };
    }

    private String rarityDisplayName(String rarity) {
        if (rarity == null || rarity.isBlank()) {
            return "Common";
        }
        String normalized = rarity.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Arrays.stream(normalized.split(" "))
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .reduce((left, right) -> left + " " + right)
            .orElse("Common");
    }

    public String selectedKillMessageDisplayName(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CosmeticDefinition definition = findKillMessageDefinition(selectedKillMessageId(playerId));
        return definition == null ? null : definition.displayName;
    }

    public String selectedKillMessageRarity(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CosmeticDefinition definition = findKillMessageDefinition(selectedKillMessageId(playerId));
        return definition == null ? null : definition.rarity;
    }

    private void loadHeadCosmeticRegistry() {
        headCosmeticRegistry.clear();
        headCosmeticMenuOrder.clear();

        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not create plugin data folder: " + dataFolder);
        }

        Path registryPath = dataFolder.resolve(COSMETIC_REGISTRY_FILE);
        if (Files.notExists(registryPath)) {
            try {
                plugin.saveResource(COSMETIC_REGISTRY_FILE, false);
            } catch (IllegalArgumentException ignored) {
                // No bundled resource; registry can still be driven by an external file.
            }
        }

        cosmeticRegistryConfig = YamlConfiguration.loadConfiguration(registryPath.toFile());
        ConfigurationSection section = cosmeticRegistryConfig.getConfigurationSection(COSMETIC_REGISTRY_ROOT);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                HeadCosmetic cosmetic = HeadCosmetic.fromConfig(key, item);
                if (cosmetic == null || cosmetic.isNone()) {
                    continue;
                }
                headCosmeticRegistry.put(cosmetic.id, cosmetic);
            }
        }

        if (headCosmeticRegistry.isEmpty()) {
            headCosmeticRegistry.put("tophat", new HeadCosmetic(
                "tophat", "Top Hat", Material.COAL, "hydra:tophat", 1000, true, false, 0, "head", "default", "common",
                CosmeticType.HAT, null
            ));
            headCosmeticRegistry.put("halo", new HeadCosmetic(
                "halo", "Halo", Material.COAL, "hydra:halo", 1001, true, false, 1, "head", "default", "common",
                CosmeticType.HAT, null
            ));
            headCosmeticRegistry.put("glasses", new HeadCosmetic(
                "glasses", "Glasses", Material.COAL, "hydra:glasses", 1002, true, false, 2, "head", "default", "common",
                CosmeticType.HAT, null
            ));
            headCosmeticRegistry.put("moneyglasses", new HeadCosmetic(
                "moneyglasses", "Money Glasses", Material.COAL, "hydra:moneyglasses", 1003, true, false, 3, "head", "default", "rare",
                CosmeticType.HAT, null
            ));
            headCosmeticRegistry.put("magnet_helmet", new HeadCosmetic(
                "magnet_helmet", "Magnet Helmet", Material.COAL, "hydra:magnet_helmet", 1004, true, false, 4, "head", "default", "rare",
                CosmeticType.HAT, null
            ));
            headCosmeticRegistry.put("tie", new HeadCosmetic(
                "tie", "Tie", Material.COAL, "hydra:tie", 1005, true, false, 5, "head", "default", "legendary",
                CosmeticType.HAT, null
            ));
            plugin.getLogger().warning("No cosmetics registry entries found; using built-in fallback cosmetics.");
        }

        ensureBuiltInHatCosmetic("blue_bandanna", "Blue Bandanna", "hydra:blue_bandanna", 1006, 70, "default", "rare");
        ensureBuiltInHatCosmetic("trick_or_slime_hat", "Trick or Slime Hat", "hydra:trick_or_slime_hat", 1007, 80, "event", "epic");
        ensureBuiltInHatCosmetic("cardboard_box_hat", "Cardboard Box Hat", "hydra:cardboard_box_hat", 1008, 90, "default", "common");
        ensureBuiltInHatCosmetic("tnt_hat", "TNT Hat", "hydra:tnt_hat", 1009, 100, "default", "rare");
        // Production cosmetics.yml may predate this entry or contain the old display-based model assignment.
        headCosmeticRegistry.put(SPINNING_TOP_HAT_ID, new HeadCosmetic(
            SPINNING_TOP_HAT_ID, "Spinning Top Hat", Material.COAL, "hydra:spinning_tophat_00",
            SPINNING_TOP_HAT_CUSTOM_MODEL_DATA, true, false, 11, "hat", "dev_only", "limited", CosmeticType.HAT, null
        ));

        headCosmeticMenuOrder.addAll(headCosmeticRegistry.values());
        headCosmeticMenuOrder.sort(
            Comparator.comparingInt((HeadCosmetic cosmetic) -> cosmetic.menuOrder)
                .thenComparing(cosmetic -> cosmetic.displayName, String.CASE_INSENSITIVE_ORDER)
        );
    }

    private void ensureBuiltInHatCosmetic(
        String id,
        String displayName,
        String itemModel,
        int customModelData,
        int menuOrder,
        String unlockType,
        String rarity
    ) {
        if (headCosmeticRegistry.containsKey(id)) {
            return;
        }
        headCosmeticRegistry.put(id, new HeadCosmetic(
            id, displayName, Material.COAL, itemModel, customModelData, true, false, menuOrder, "hat", unlockType, rarity,
            CosmeticType.HAT, null
        ));
    }

    private void loadOwnedHeadCosmetics() {
        cosmeticService.load();
    }

    private void saveOwnedHeadCosmetics(UUID playerId) {
        // Ownership persistence is handled by CosmeticStateStore.
    }

    private Set<String> ensureOwnedHeadCosmetics(UUID playerId) {
        return cosmeticService.ensureOwnedCosmetics(playerId);
    }

    public boolean ownsHeadCosmetic(UUID playerId, String cosmeticId) {
        if (playerId == null) {
            return false;
        }
        return cosmeticService.owns(playerId, cosmeticId);
    }

    private void loadSelectedHeadCosmetics() {
        cosmeticService.load();
    }

    private void loadSelectedLegendarySkins() {
        cosmeticService.load();
    }

    private void saveSelectedHeadCosmetic(UUID playerId, String cosmeticId) {
        cosmeticService.setSelectedHatCosmeticId(playerId, cosmeticId);
    }

    private void saveSelectedLegendarySkins(UUID playerId) {
        // Legendary selection persistence is handled by CosmeticStateStore.
    }

    private void loadSelectedRankStyles() {
        cosmeticService.load();
    }

    private void saveSelectedRankStyle(UUID playerId) {
        // Rank style persistence is handled by CosmeticStateStore.
    }

    private void startHeadCosmeticTask() {
        if (headCosmeticTask != null) {
            headCosmeticTask.cancel();
        }
        headCosmeticTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeadCosmetics, 1L, 1L);
    }

    private void tickHeadCosmetics() {
        spinningTopHatTick++;
        for (UUID playerId : cosmeticService.playerIdsWithSelectedHats()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            refreshHeadCosmetic(player);
        }
        legendarySkinRefreshTick = (legendarySkinRefreshTick + 1) % LEGENDARY_SKIN_REFRESH_INTERVAL_TICKS;
        if (legendarySkinRefreshTick != 0) {
            return;
        }
        for (UUID playerId : cosmeticService.playerIdsWithLegendarySelections()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            refreshLegendarySkins(player);
        }
    }

    private void startVoidWarpHudTask() {
        if (voidWarpHudTask != null) {
            voidWarpHudTask.cancel();
        }
        voidWarpHudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickVoidWarpHud, 1L, 2L);
    }

    private void startTabListTask() {
        if (tabListTask != null) {
            tabListTask.cancel();
        }
        tabListTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTabList, 1L, 40L);
    }

    private void tickVoidWarpHud() {
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineIds.add(player.getUniqueId());
            updateVoidWarpHudForPlayer(player);
        }

        voidWarpCharge.keySet().removeIf(id -> !onlineIds.contains(id));
        voidWarpHudPlayers.removeIf(id -> !onlineIds.contains(id));
    }

    private void tickTabList() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabListForPlayer(player);
        }
    }

    private void updateTabListForPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        String modeLine = "";
        if (plugin instanceof HGLootPlugin hydra && hydra.queueService() != null) {
            String mode = hydra.queueService().activeMatchModeLabel(player);
            if (mode != null && !mode.isBlank()) {
                modeLine = "\n" + ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode;
            }
        }
        String header = ChatColor.AQUA + "Hydra Network"
            + "\n"
            + ChatColor.GRAY + "Online: " + ChatColor.WHITE + online
            + "\n"
            + ChatColor.GRAY + "Your Ping: " + ChatColor.WHITE + Math.max(0, player.getPing()) + "ms"
            + modeLine
            + "\n\n"
            + ChatColor.GRAY + "Store: " + ChatColor.WHITE + "Coming Soon"
            + "\n"
            + ChatColor.GRAY + "Discord: " + ChatColor.WHITE + "discord.gg/Mgfhay8wRx";
        player.setPlayerListHeader(header);
        player.setPlayerListFooter("");
    }

    private void sendJoinIntroMessages(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage("");
        player.sendMessage(centerChatLine(JOIN_CHAT_TITLE_TEXT));
        player.sendMessage(centerChatLine(JOIN_CHAT_SUBTITLE_TEXT));
        HydraSounds.playJoin(player);
    }

    private String centerChatLine(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        int messagePx = estimateChatPixelWidth(line);
        int paddingPx = Math.max(0, CHAT_CENTER_PX - (messagePx / 2));
        int spaces = Math.max(0, paddingPx / CHAT_SPACE_PX);
        return " ".repeat(spaces) + line;
    }

    private int estimateChatPixelWidth(String line) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == ChatColor.COLOR_CHAR && i + 1 < line.length()) {
                char code = Character.toLowerCase(line.charAt(++i));
                if (code == 'l') {
                    bold = true;
                } else if ((code >= '0' && code <= '9')
                    || (code >= 'a' && code <= 'f')
                    || code == 'r') {
                    bold = false;
                }
                continue;
            }
            int charWidth = switch (current) {
                case ' ', 'i', '!', '.', ',', ':', ';', '|', '\'' -> current == ' ' ? 4 : 2;
                case 'l', '`' -> 3;
                case 'I', '[', ']', 't', '(', ')', '{', '}' -> 4;
                case '"', '*', '<', '>', 'f', 'k' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
            width += bold && current != ' ' ? charWidth + 1 : charWidth;
        }
        return width;
    }

    private void updateVoidWarpHudForPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (isVoidbreakerMace(player.getInventory().getItemInMainHand())) {
            float charge = Math.max(0.0f, Math.min(1.0f, voidWarpCharge.getOrDefault(playerId, 0.0f)));
            player.setExp(charge);
            player.setLevel(0);
            voidWarpHudPlayers.add(playerId);
            return;
        }

        if (voidWarpHudPlayers.remove(playerId)) {
            player.setExp(0.0f);
            player.setLevel(0);
        }
    }

    private void equipHeadCosmetic(Player player, HeadCosmetic cosmetic) {
        if (cosmetic == null) {
            cosmetic = HeadCosmetic.NONE;
        }
        if (cosmetic.isLegendarySkin()) {
            equipLegendarySkin(player, cosmetic);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!cosmetic.isNone() && !ownsHeadCosmetic(playerId, cosmetic.id)) {
            player.sendMessage(ChatColor.RED + "You do not own that cosmetic.");
            return;
        }
        cosmeticService.setSelectedHatCosmeticId(playerId, cosmetic.id);
        refreshHeadCosmetic(player);

        if (cosmetic.isNone()) {
            player.sendMessage(ChatColor.GRAY + "Head cosmetic cleared.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Equipped head cosmetic: "
                + ChatColor.YELLOW + cosmetic.displayName + ChatColor.GRAY + ".");
        }
    }

    private void equipLegendarySkin(Player player, HeadCosmetic cosmetic) {
        if (cosmetic == null || !cosmetic.isLegendarySkin() || cosmetic.legendaryKey == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!ownsHeadCosmetic(playerId, cosmetic.id)) {
            player.sendMessage(ChatColor.RED + "You do not own that cosmetic.");
            return;
        }
        String currentlySelected = cosmeticService.selectedLegendarySkinId(playerId, cosmetic.legendaryKey);
        if (Objects.equals(currentlySelected, cosmetic.id)) {
            cosmeticService.setSelectedLegendarySkinId(playerId, cosmetic.legendaryKey, null);
            refreshLegendarySkins(player);
            player.sendMessage(ChatColor.GRAY
                + "Reset " + ChatColor.YELLOW + cosmetic.legendaryKeyDisplay()
                + ChatColor.GRAY + " to its default skin.");
            return;
        }
        cosmeticService.setSelectedLegendarySkinId(playerId, cosmetic.legendaryKey, cosmetic.id);
        refreshLegendarySkins(player);
        player.sendMessage(ChatColor.GRAY + "Equipped legendary skin: "
            + ChatColor.YELLOW + cosmetic.displayName + ChatColor.GRAY + ".");
    }

    private void refreshHeadCosmetic(Player player) {
        if (plugin instanceof HGLootPlugin hydra && hydra.kitEditorService() != null
            && (hydra.kitEditorService().isEditing(player.getUniqueId())
                || hydra.kitEditorService().isRecovering(player.getUniqueId()))) return;
        if (plugin instanceof HGLootPlugin hydra
            && hydra.gameHubService() != null
            && hydra.gameHubService().isFightPit(player.getLocation())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        HeadCosmetic cosmetic = findHeadCosmetic(selectedHatCosmeticId(playerId));
        if (shouldHideHeadCosmetic(player, cosmetic)) {
            clearCosmeticFromPlayerHelmetSlot(player);
            return;
        }

        if (SPINNING_TOP_HAT_ID.equals(cosmetic.id)) {
            refreshSpinningTopHatHelmet(player, cosmetic);
            return;
        }

        ItemStack currentHelmet = player.getInventory().getHelmet();
        boolean bossRushArmor = plugin instanceof HGLootPlugin hydra
            && hydra.bossRushService() != null
            && hydra.bossRushService().isInActiveMatch(playerId);
        Material helmetMaterial = bossRushArmor ? Material.DIAMOND_HELMET : cosmetic.material;
        if (isHeadCosmeticItem(currentHelmet, cosmetic, helmetMaterial)) {
            return;
        }
        player.getInventory().setHelmet(createHeadCosmeticItem(cosmetic, helmetMaterial));
    }

    private void refreshSpinningTopHatHelmet(Player player, HeadCosmetic cosmetic) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!isSpinningTopHatItem(helmet)) {
            helmet = createHeadCosmeticItem(cosmetic);
        }
        ItemMeta meta = helmet.getItemMeta();
        if (meta == null) {
            return;
        }
        NamespacedKey frameModel = spinningTopHatFrameModel(spinningTopHatTick);
        if (frameModel.equals(meta.getItemModel()) && isSpinningTopHatItem(player.getInventory().getHelmet())) {
            return;
        }
        meta.setItemModel(frameModel);
        helmet.setItemMeta(meta);
        player.getInventory().setHelmet(helmet);
    }

    static NamespacedKey spinningTopHatFrameModel(long tick) {
        int frame = (int) Math.floorMod(tick, SPINNING_TOP_HAT_PERIOD_TICKS);
        return new NamespacedKey("hydra", "spinning_tophat_" + String.format(Locale.ROOT, "%02d", frame));
    }

    private boolean isSpinningTopHatItem(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
            && stack.getItemMeta().getPersistentDataContainer().has(spinningTopHatKey, PersistentDataType.BYTE);
    }

    private void refreshCosmeticEquipment(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        refreshHeadCosmetic(player);
        refreshArmorTrimCosmetic(player);
    }

    public void setHeadCosmeticTemporarilyHidden(Player player, boolean hidden) {
        if (player == null) {
            return;
        }
        if (hidden) {
            temporarilyHiddenHeadCosmetics.add(player.getUniqueId());
        } else {
            temporarilyHiddenHeadCosmetics.remove(player.getUniqueId());
        }
        if (player.isOnline()) {
            refreshHeadCosmetic(player);
            player.updateInventory();
        }
    }

    private void scheduleArmorTrimRefresh(Player player, long delayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingArmorTrimRefreshes.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingArmorTrimRefreshes.remove(playerId);
            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                refreshArmorTrimCosmetic(onlinePlayer);
            }
        }, Math.max(1L, delayTicks));
    }

    private void refreshArmorTrimCosmetic(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin instanceof HGLootPlugin hydra && hydra.kitEditorService() != null
            && (hydra.kitEditorService().isEditing(player.getUniqueId())
                || hydra.kitEditorService().isRecovering(player.getUniqueId()))) return;
        if (plugin instanceof HGLootPlugin hydra
            && hydra.gameHubService() != null
            && hydra.gameHubService().isFightPit(player.getLocation())) {
            return;
        }
        String selectedId = selectedSystemCosmeticId(player.getUniqueId(), CosmeticCategory.ARMOR_TRIMS);
        ArmorTrim selectedTrim = armorTrimForCosmetic(selectedId);
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack updated = applyArmorTrimCosmetic(armor[i], selectedId, selectedTrim);
            if (updated != armor[i]) {
                armor[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
            player.updateInventory();
        }
    }

    private ItemStack applyArmorTrimCosmetic(ItemStack stack, String selectedId, ArmorTrim selectedTrim) {
        if (stack == null || stack.getType() == Material.AIR || !(stack.getItemMeta() instanceof ArmorMeta armorMeta)) {
            return stack;
        }
        PersistentDataContainer pdc = armorMeta.getPersistentDataContainer();
        String appliedId = pdc.get(cosmeticArmorTrimKey, PersistentDataType.STRING);
        if (selectedTrim == null) {
            if (appliedId == null) {
                return stack;
            }
            ItemStack updated = stack.clone();
            armorMeta = (ArmorMeta) updated.getItemMeta();
            pdc = armorMeta.getPersistentDataContainer();
            armorMeta.setTrim(null);
            pdc.remove(cosmeticArmorTrimKey);
            updated.setItemMeta(armorMeta);
            return updated;
        }
        if (selectedId != null && selectedId.equals(appliedId) && selectedTrim.equals(armorMeta.getTrim())) {
            return stack;
        }
        ItemStack updated = stack.clone();
        armorMeta = (ArmorMeta) updated.getItemMeta();
        pdc = armorMeta.getPersistentDataContainer();
        armorMeta.setTrim(selectedTrim);
        pdc.set(cosmeticArmorTrimKey, PersistentDataType.STRING, selectedId == null ? "" : selectedId);
        updated.setItemMeta(armorMeta);
        return updated;
    }

    private ArmorTrim armorTrimForCosmetic(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return null;
        }
        return switch (cosmeticId.toLowerCase(Locale.ROOT)) {
            case "trim_ascendant" -> new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.SPIRE);
            case "trim_hydra_crown" -> new ArmorTrim(TrimMaterial.GOLD, TrimPattern.SPIRE);
            case "trim_kingsfall" -> new ArmorTrim(TrimMaterial.GOLD, TrimPattern.SNOUT);
            case "trim_depthscarred" -> new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.WILD);
            case "trim_venomwalker" -> new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.RIB);
            case "trim_voidglass" -> new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.SILENCE);
            default -> null;
        };
    }

    private void refreshLegendarySkins(Player player) {
        UUID playerId = player.getUniqueId();
        for (ItemStack stack : player.getInventory().getContents()) {
            applySelectedLegendarySkin(playerId, stack);
        }
    }

    private String getSelectedLegendarySkinId(UUID playerId, String legendaryKey) {
        return cosmeticService.selectedLegendarySkinId(playerId, legendaryKey);
    }

    private void sanitizeSelectedLegendarySkins(UUID playerId) {
        // Legendary selection validation now happens through CosmeticService lookups.
    }

    private void applySelectedLegendarySkin(UUID playerId, ItemStack stack) {
        if (playerId == null || stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        LegendarySkinTarget target = detectLegendarySkinTarget(meta);
        if (target == null) {
            return;
        }
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        List<String> currentModels = new ArrayList<>(customModelData.getStrings());
        String desiredPrimaryModel = target.baseModel();
        String selectedSkinId = getSelectedLegendarySkinId(playerId, target.legendaryKey());
        if (selectedSkinId != null) {
            HeadCosmetic selectedSkin = findHeadCosmetic(selectedSkinId);
            if (selectedSkin.isLegendarySkin() && selectedSkin.itemModel != null && !selectedSkin.itemModel.isBlank()) {
                desiredPrimaryModel = selectedLegendaryModelForTarget(selectedSkin, target);
            }
        }
        List<String> desiredModels = desiredPrimaryModel.equals(target.baseModel())
            ? List.of(target.baseModel())
            : List.of(desiredPrimaryModel, target.baseModel());
        if (currentModels.equals(desiredModels)) {
            return;
        }
        customModelData.setStrings(desiredModels);
        meta.setCustomModelDataComponent(customModelData);
        stack.setItemMeta(meta);
    }

    private LegendarySkinTarget detectLegendarySkinTarget(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        List<String> modelStrings = meta.getCustomModelDataComponent().getStrings();
        if (modelStrings.contains("ultraroyales:ghlochester")) {
            return new LegendarySkinTarget("ghlochester", "Ghlochester", "ultraroyales:ghlochester");
        }
        if (modelStrings.contains("ultraroyales:dwarvenbow")) {
            return new LegendarySkinTarget("dwarven_bow", "Dwarf Bow", "ultraroyales:dwarvenbow");
        }
        if (modelStrings.contains("ultraroyales:skeletalbarber")) {
            return new LegendarySkinTarget("skeletal_barber", "Skeletal Barber", "ultraroyales:skeletalbarber");
        }
        if (modelStrings.contains("ultraroyales:electricconch")) {
            return new LegendarySkinTarget("electric_conch", "Electric Conch", "ultraroyales:electricconch");
        }
        if (modelStrings.contains("ultraroyales:wet_sponge_saber")) {
            return new LegendarySkinTarget("sponge_saber", "Sponge Saber", "ultraroyales:wet_sponge_saber");
        }
        if (modelStrings.contains("ultraroyales:sponge_saber")) {
            return new LegendarySkinTarget("sponge_saber", "Sponge Saber", "ultraroyales:sponge_saber");
        }
        return null;
    }

    private String selectedLegendaryModelForTarget(HeadCosmetic selectedSkin, LegendarySkinTarget target) {
        if (selectedSkin == null || target == null || selectedSkin.itemModel == null || selectedSkin.itemModel.isBlank()) {
            return target == null ? "" : target.baseModel();
        }
        if ("sponge_saber".equals(target.legendaryKey())
            && "ultraroyales:wet_sponge_saber".equals(target.baseModel())
            && "ultraroyales:sponge_saber_samurai".equals(selectedSkin.itemModel)) {
            return "ultraroyales:wet_sponge_saber_samurai";
        }
        return selectedSkin.itemModel;
    }

    private boolean shouldHideHeadCosmetic(Player player, HeadCosmetic cosmetic) {
        if (cosmetic == null || cosmetic.isNone()) {
            return true;
        }
        if (!player.isOnline() || player.isDead()) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        if (temporarilyHiddenHeadCosmetics.contains(playerId)) {
            return true;
        }
        boolean cosmeticMinigame = (plugin instanceof HGLootPlugin hydra)
            && ((hydra.spleefService() != null && hydra.spleefService().isInSpleef(playerId))
                || (hydra.voidRouletteService() != null && hydra.voidRouletteService().isInVoidRoulette(playerId)));
        if (cosmeticMinigame) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return true;
        if (plugin instanceof HGLootPlugin hydra && hydra.bridgeService() != null
            && hydra.bridgeService().isInBridge(playerId)) return true;
        boolean inMatch = isMatchWorld(player.getWorld());
        if (inMatch) {
            return !cosmetic.visibleInMatch;
        }
        return !cosmetic.visibleInLobby;
    }

    private ItemStack createHeadCosmeticItem(HeadCosmetic cosmetic) {
        return createHeadCosmeticItem(cosmetic, cosmetic.material);
    }

    private ItemStack createHeadCosmeticItem(HeadCosmetic cosmetic, Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.YELLOW + cosmetic.displayName);
        applyCosmeticVisual(meta, cosmetic);
        if (SPINNING_TOP_HAT_ID.equals(cosmetic.id)) {
            meta.getPersistentDataContainer().set(spinningTopHatKey, PersistentDataType.BYTE, (byte) 1);
        }
        EquippableComponent equippable = meta.getEquippable();
        if (equippable != null) {
            equippable.setSlot(EquipmentSlot.HEAD);
            meta.setEquippable(equippable);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // Only clear plugin-managed cosmetic helmets. Do not remove real armor.
    private void clearCosmeticFromPlayerHelmetSlot(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!isHeadCosmeticItem(helmet)) {
            return;
        }
        player.getInventory().setHelmet(null);
    }

    private boolean isHeadCosmeticItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        if (isSpinningTopHatItem(stack)) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        Material material = stack.getType();
        Integer cmd = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        NamespacedKey modelKey = meta.hasItemModel() ? meta.getItemModel() : null;
        for (HeadCosmetic cosmetic : headCosmeticRegistry.values()) {
            if (!cosmetic.isHat()) {
                continue;
            }
            if (cosmetic.material != material) {
                continue;
            }
            NamespacedKey cosmeticModelKey = parseItemModelKey(cosmetic.itemModel);
            if (modelKey != null && cosmeticModelKey != null && cosmeticModelKey.equals(modelKey)) {
                return true;
            }
            if (cmd != null && cosmetic.customModelData > 0 && cosmetic.customModelData == cmd) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeadCosmeticItem(ItemStack stack, HeadCosmetic cosmetic) {
        return isHeadCosmeticItem(stack, cosmetic, cosmetic == null ? null : cosmetic.material);
    }

    private boolean isHeadCosmeticItem(ItemStack stack, HeadCosmetic cosmetic, Material material) {
        if (stack == null || cosmetic == null || cosmetic.isNone() || !cosmetic.isHat() || !stack.hasItemMeta()) {
            return false;
        }
        if (material == null || stack.getType() != material) {
            return false;
        }
        if (SPINNING_TOP_HAT_ID.equals(cosmetic.id) && isSpinningTopHatItem(stack)) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey cosmeticModelKey = parseItemModelKey(cosmetic.itemModel);
        if (cosmeticModelKey != null && meta.hasItemModel() && cosmeticModelKey.equals(meta.getItemModel())) {
            return true;
        }
        return cosmetic.customModelData > 0
            && meta.hasCustomModelData()
            && meta.getCustomModelData() == cosmetic.customModelData;
    }

    private HeadCosmetic findHeadCosmetic(String cosmeticId) {
        String normalized = normalizeLegacyCosmeticId(cosmeticId);
        if (HeadCosmetic.NONE.id.equals(normalized)) {
            return HeadCosmetic.NONE;
        }
        return headCosmeticRegistry.getOrDefault(normalized, HeadCosmetic.NONE);
    }

    private String normalizeLegacyCosmeticId(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return HeadCosmetic.NONE.id;
        }
        String normalized = cosmeticId.toLowerCase(Locale.ROOT);
        if (normalized.equals("tophat") && headCosmeticRegistry.containsKey("tophat")) {
            return normalized;
        }
        if (normalized.equals("coal") && !headCosmeticRegistry.containsKey("coal")
            && headCosmeticRegistry.containsKey("tophat")) {
            return "tophat";
        }
        return normalized;
    }

    private void applyCosmeticVisual(ItemMeta meta, HeadCosmetic cosmetic) {
        if (meta == null || cosmetic == null || cosmetic.isNone()) {
            return;
        }
        NamespacedKey itemModelKey = parseItemModelKey(cosmetic.itemModel);
        if (itemModelKey != null) {
            meta.setItemModel(itemModelKey);
        }
        if (cosmetic.customModelData > 0) {
            meta.setCustomModelData(cosmetic.customModelData);
        }
    }

    private void applyCosmeticVisual(ItemMeta meta, CosmeticDefinition cosmetic) {
        if (meta == null || cosmetic == null) {
            return;
        }
        NamespacedKey itemModelKey = parseItemModelKey(cosmetic.itemModel);
        if (itemModelKey != null) {
            meta.setItemModel(itemModelKey);
            return;
        }
        if (cosmetic.customModelData > 0) {
            meta.setCustomModelData(cosmetic.customModelData);
        }
    }

    private NamespacedKey parseItemModelKey(String itemModel) {
        if (itemModel == null || itemModel.isBlank()) {
            return null;
        }
        try {
            return NamespacedKey.fromString(itemModel.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ItemStack createHubItem(Material material, String name, String action) {
        return createHubItem(material, name, action, false, null);
    }

    private ItemStack createHubItem(Material material, String name, String action, boolean enchanted) {
        return createHubItem(material, name, action, enchanted, null);
    }

    private ItemStack createHubItem(Material material, String name, String action, boolean enchanted, Integer customModelData) {
        return createHubItem(material, name, action, enchanted, customModelData, null);
    }

    private ItemStack createHubItemWithModel(Material material, String name, String action, boolean enchanted, String itemModel) {
        return createHubItem(material, name, action, enchanted, null, itemModel);
    }

    private ItemStack createHubItem(Material material, String name, String action, boolean enchanted, Integer customModelData, String itemModel,
                                    String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        meta.getPersistentDataContainer().set(playerHubItemKey, PersistentDataType.STRING, action);
        if (customModelData != null && customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        NamespacedKey itemModelKey = parseItemModelKey(itemModel);
        if (itemModelKey != null) {
            meta.setItemModel(itemModelKey);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (enchanted) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isMatchWorld(World world) {
        if (world == null) {
            return false;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        return worldName.startsWith("hg_match_")
            || worldName.startsWith("hg-world-")
            || worldName.startsWith("br-world-")
            || worldName.startsWith("spleef-world-")
            || worldName.startsWith("voidroulette-world-")
            || worldName.startsWith("bridge-world-")
            || worldName.startsWith("bossrush-world-");
    }

    private String readHubItemAction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(playerHubItemKey, PersistentDataType.STRING);
    }

    private boolean isCriticalHubControl(ItemStack stack) {
        String action = readHubItemAction(stack);
        return HUB_ITEM_LEAVE_ROULETTE.equals(action)
            || HUB_ITEM_LEAVE_HUNGER_GAMES.equals(action)
            || HUB_ITEM_LEAVE_GAMEHUB.equals(action)
            || HUB_ITEM_CLAIM_SHARD.equals(action);
    }

    private ProgressionService progressionServiceOrNull() {
        return plugin instanceof HGLootPlugin hydra ? hydra.progressionService() : null;
    }

    public boolean isHubItem(ItemStack stack) {
        return readHubItemAction(stack) != null;
    }

    public void clearHubHotbarItems(Player player) {
        clearHubMenuItems(player);
    }

    private boolean isPlayerMenuCompass(ItemStack stack) {
        if (readHubItemAction(stack) != null) {
            return true;
        }
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte tagged = stack.getItemMeta().getPersistentDataContainer().get(playerMenuCompassKey, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private String readAction(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.get(actionKey, PersistentDataType.STRING);
    }

    private ItemStack cosmeticButton(HeadCosmetic cosmetic, String name, String action, boolean owned) {
        ItemStack stack = new ItemStack(cosmetic.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name);
        meta.setLore(null);
        applyCosmeticVisual(meta, cosmetic);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.toLowerCase(Locale.ROOT));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack cosmeticButton(CosmeticDefinition cosmetic, String name, String action, boolean owned, boolean selected) {
        ItemStack stack = new ItemStack(cosmetic.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name);
        meta.setLore(null);
        applyCosmeticVisual(meta, cosmetic);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.toLowerCase(Locale.ROOT));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    private List<UUID> sortedPartyMembers(UUID playerId) {
        UUID leaderId = partyService.getLeader(playerId);
        List<UUID> members = new ArrayList<>(partyService.getPartyMembers(playerId));
        members.sort(
            Comparator.<UUID, Integer>comparing(id -> id.equals(leaderId) ? 0 : 1)
                .thenComparing(partyService::displayName, String.CASE_INSENSITIVE_ORDER)
        );
        return members;
    }

    private QueueService queueServiceOrNull() {
        if (plugin instanceof HGLootPlugin hydraPlugin) {
            return hydraPlugin.queueService();
        }
        return null;
    }

    private boolean isHungerGamesMapActive(String mapKey) {
        QueueService queueService = queueServiceOrNull();
        return queueService != null && queueService.isHungerGamesMapActive(mapKey);
    }

    private boolean selectHungerGamesMap(Player player, String mapKey) {
        QueueService queueService = queueServiceOrNull();
        return queueService == null || queueService.selectHungerGamesMap(mapKey, player);
    }

    private ItemStack button(Material material, String name, String action, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name);
        if (lore.length > 0) {
            List<String> loreLines = new ArrayList<>(lore.length);
            for (String line : lore) {
                loreLines.add(SECONDARY_TEXT_COLOR + line);
            }
            meta.setLore(loreLines);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.toLowerCase(Locale.ROOT));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buttonWithModel(Material material, String itemModel, String name, String action, String... lore) {
        ItemStack stack = button(material, name, action, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        NamespacedKey itemModelKey = parseItemModelKey(itemModel);
        if (itemModelKey != null) {
            meta.setItemModel(itemModelKey);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack hydraWeaponButton(String weaponId, ItemStack displayItem) {
        return hydraWeaponButton(HYDRA_WEAPON_GIVE_ACTION_PREFIX, weaponId, displayItem);
    }

    private ItemStack betaLegendaryButton(String weaponId, ItemStack displayItem) {
        ItemStack stack = displayItem.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(
            actionKey,
            PersistentDataType.STRING,
            (BETA_LEGENDARY_GIVE_ACTION_PREFIX + weaponId).toLowerCase(Locale.ROOT)
        );
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack hydraWeaponButton(String actionPrefix, String weaponId, ItemStack displayItem) {
        boolean hungerGamesVariant = HYDRA_HG_WEAPON_GIVE_ACTION_PREFIX.equals(actionPrefix);
        ItemStack stack = displayItem.clone();
        if (hungerGamesVariant) {
            stack = Items.withHungerGamesVariant(weaponId, stack);
        }
        stack = Items.withModeLabel(stack, hungerGamesVariant);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(
            actionKey,
            PersistentDataType.STRING,
            (actionPrefix + weaponId).toLowerCase(Locale.ROOT)
        );
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createHeartBundle() {
        ItemStack heart = Items.heartItem();
        heart.setAmount(64);
        return heart;
    }

    private ItemStack hydraWeaponById(String weaponId) {
        return switch (weaponId == null ? "" : weaponId.toLowerCase(Locale.ROOT)) {
            case "knights_saddle" -> Items.knightsSaddle();
            case "dwarven_bow" -> Items.dwarvenBow();
            case "ghlochester" -> Items.ghlochester();
            case "spider_staff" -> Items.spiderStaff();
            case "sponge_saber" -> Items.spongeSaber();
            case "skeletal_barber" -> Items.skeletalBarber();
            case "electric_conch" -> Items.electricConch();
            case "heart_bundle" -> createHeartBundle();
            case "life_elixir" -> Items.lifeElixir(1);
            case "memory_breaker" -> Items.memoryBreaker();
            case "sonic_wrench" -> Items.sonicWrench();
            case "aegis" -> Items.aegis();
            case "frozen_chestpiece" -> Items.frozenChestpiece();
            case "melted_scythe" -> Items.meltedScythe();
            case "royal_roulette" -> Items.royalRoulette();
            case "book_of_welding" -> Items.bookOfWelding();
            default -> null;
        };
    }

    private ItemStack betaLegendaryById(String weaponId) {
        return switch (weaponId == null ? "" : weaponId.toLowerCase(Locale.ROOT)) {
            case "goblin_mask" -> Items.goblinMask();
            case "hooked_grappler" -> Items.hookedGrappler();
            case "diamond_totem" -> Items.diamondTotem();
            case "wonder_pickaxe" -> Items.wonderPickaxe();
            case "loki_scepter" -> Items.lokiScepter();
            case "druids_daggers" -> Items.druidsDaggers();
            case "memory_breaker" -> Items.memoryBreaker();
            case "sonic_wrench" -> Items.sonicWrench();
            case "aegis" -> Items.aegis();
            case "frozen_chestpiece" -> Items.frozenChestpiece();
            case "melted_scythe" -> Items.meltedScythe();
            case "architects_drill" -> Items.architectsDrill();
            case "enderious", "plasma_distorter" -> Items.enderious();
            case "scarabs_wrath" -> Items.scarabsWrath();
            case "monarch_wings" -> Items.monarchWings();
            case "hoglin_horn" -> Items.hoglinHorn();
            case "voidbreaker" -> voidbreakerMaceItem();
            case "last_breath" -> railgunCrossbowItem();
            case "ghost_knife" -> Items.ghostKnife();
            default -> null;
        };
    }

    private void grantHydraWeapon(Player player, String weaponId, boolean hungerGamesVariant) {
        ItemStack item = hydraWeaponById(weaponId);
        if (hungerGamesVariant) {
            item = Items.withHungerGamesVariant(weaponId, item);
        }
        item = Items.withModeLabel(item, hungerGamesVariant);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown Hydra weapon selection.");
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
        }
        player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Given: " + ChatColor.AQUA + item.getI18NDisplayName());
    }

    private void grantBetaLegendary(Player player, String weaponId) {
        ItemStack item = betaLegendaryById(weaponId);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown beta legendary selection.");
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
        }
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? item.getItemMeta().getDisplayName()
            : item.getType().name();
        player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Given: " + ChatColor.AQUA + name);
    }

    private ItemStack playerHeadButton(UUID playerId, String name, String action, String... lore) {
        ItemStack stack = button(Material.PLAYER_HEAD, name, action, lore);
        ItemMeta itemMeta = stack.getItemMeta();
        if (!(itemMeta instanceof SkullMeta skullMeta)) {
            return stack;
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            skullMeta.setOwningPlayer(online);
            stack.setItemMeta(skullMeta);
            return stack;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(playerId);
        if (owner.getName() != null && !owner.getName().isBlank()) {
            skullMeta.setOwningPlayer(owner);
        }
        stack.setItemMeta(skullMeta);
        return stack;
    }

    private String cosmeticTargetDisplayName(UUID targetId) {
        Player online = Bukkit.getPlayer(targetId);
        if (online != null && online.isOnline()) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return targetId.toString();
    }

    private static List<Integer> actionSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        int rows = size / 9;
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private static boolean containsSlot(int[] slots, int targetSlot) {
        for (int slot : slots) {
            if (slot == targetSlot) {
                return true;
            }
        }
        return false;
    }

    private static int backSlot(int size) {
        return size - 5;
    }

    private static int friendsMenuSize(int count) {
        if (count <= 7) {
            return 27;
        }
        if (count <= 21) {
            return 45;
        }
        return 54;
    }

    private static int cosmeticsMenuSize(int count) {
        if (count <= 7) {
            return 27;
        }
        if (count <= 14) {
            return 36;
        }
        if (count <= 21) {
            return 45;
        }
        return 54;
    }

    private static int parsePositive(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void frame(Inventory inv) {
        // Intentionally blank for now. The old edge frame was just visual noise,
        // and we can layer a proper border back in once the icon set is settled.
    }

    private static ItemStack plainItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack guiButton(String modelId, String name, String action, String... lore) {
        ItemStack stack = button(Material.PAPER, name, action, lore);
        return applyGuiModel(stack, modelId);
    }

    private ItemStack iconClickTarget(String modelId, String action) {
        ItemStack stack = guiButton(modelId, " ", action);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setHideTooltip(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack iconLoreTarget(String modelId, String name, String action, String... lore) {
        ItemStack stack = guiButton(modelId, name, action, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack backgroundOverlay(String modelId) {
        ItemStack stack = guiButton(modelId, " ", "noop");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setHideTooltip(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack guiBackButton(String action) {
        return guiButton(GUI_MODEL_ARROW_LEFT, WARNING_COLOR + "Back", action);
    }

    private static ItemStack guiPlainItem(Material material, String modelId, String name) {
        ItemStack stack = plainItem(material, name);
        return applyGuiModel(stack, modelId);
    }

    private static ItemStack applyGuiModel(ItemStack stack, String modelId) {
        if (stack == null || modelId == null || modelId.isBlank() || !stack.hasItemMeta()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of(modelId));
        meta.setCustomModelDataComponent(customModelData);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean hydraParticlesEnabled(Player player) {
        return hydraEffectsLevel(player) > 0;
    }

    public int hydraEffectsLevel(Player player) {
        if (player == null) {
            return 2;
        }
        Byte stored = player.getPersistentDataContainer().get(settingsEffectsLevelKey, PersistentDataType.BYTE);
        if (stored != null) {
            return Math.max(0, Math.min(2, stored.intValue()));
        }
        Byte legacy = player.getPersistentDataContainer().get(settingsParticlesKey, PersistentDataType.BYTE);
        return legacy == null || legacy.byteValue() != 0 ? 2 : 0;
    }

    public boolean setHydraEffectsLevel(Player player, String levelName) {
        if (player == null || levelName == null) {
            return false;
        }
        int level = switch (levelName.toLowerCase(Locale.ROOT)) {
            case "full" -> 2;
            case "reduced" -> 1;
            case "minimal" -> 0;
            default -> -1;
        };
        if (level < 0) {
            return false;
        }
        setHydraEffectsLevel(player, level);
        return true;
    }

    private void setHydraEffectsLevel(Player player, int level) {
        int safeLevel = Math.max(0, Math.min(2, level));
        player.getPersistentDataContainer().set(settingsEffectsLevelKey, PersistentDataType.BYTE, (byte) safeLevel);
        player.getPersistentDataContainer().set(settingsParticlesKey, PersistentDataType.BYTE,
            (byte) (safeLevel > 0 ? 1 : 0));
        player.sendMessage(
            HydraIcons.EXCLAMATION + " " + ChatColor.GRAY + "Hydra effects set to "
                + ChatColor.WHITE + hydraEffectsLevelName(safeLevel) + ChatColor.GRAY + "."
        );
    }

    private String hydraEffectsLevelName(int level) {
        return level >= 2 ? "Full" : level == 1 ? "Reduced" : "Minimal";
    }

    private String hydraRankLabel(UUID targetId) {
        if (plugin instanceof HGLootPlugin hydra) {
            String staff = switch (hydra.staffRankModeFor(targetId)) {
                case "owner" -> "Owner";
                case "dev" -> "Dev";
                case "admin" -> "Admin";
                case "mod" -> "Mod";
                case "staff" -> "Staff";
                case "trialstaff" -> "Trial Staff";
                case "helper" -> "Helper";
                case "media" -> "Media";
                case "creator" -> "Creator";
                default -> null;
            };
            String support = switch (hydra.hydraRankModeFor(targetId)) {
                case "tier3" -> "Hydra+++";
                case "tier2" -> "Hydra++";
                case "tier1" -> "Hydra+";
                case "base" -> "Hydra";
                default -> null;
            };
            if (staff != null) {
                return staff;
            }
            return support == null ? "Player" : support;
        }
        Player target = Bukkit.getPlayer(targetId);
        return hydraRankLabel(target);
    }

    private String hydraRankLabel(Player target) {
        if (target == null) {
            return "None";
        }
        if (target.hasPermission(RANK_PERMISSION_HYDRA_PLUS_THREE)) {
            return "Hydra+++";
        }
        if (target.hasPermission(RANK_PERMISSION_HYDRA_PLUS_TWO)) {
            return "Hydra++";
        }
        if (target.hasPermission(RANK_PERMISSION_HYDRA_PLUS)) {
            return "Hydra+";
        }
        if (target.hasPermission(RANK_PERMISSION_HYDRA)) {
            return "Hydra";
        }
        return "Player";
    }

    private boolean applyRank(Player admin, UUID targetId, String mode) {
        if (mode == null) {
            return false;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("trialstaff") || normalized.equals("trial_staff") || normalized.equals("trial-staff")
            || normalized.equals("trialmod") || normalized.equals("trial_mod") || normalized.equals("trial-mod")
            || normalized.equals("trial") || normalized.equals("staff") || normalized.equals("helper")
            || normalized.equals("media") || normalized.equals("creator") || normalized.equals("mod") || normalized.equals("admin")
            || normalized.equals("dev") || normalized.equals("owner")) {
            return applyStaffRank(admin, targetId, normalized);
        }
        if (normalized.equals("none") || normalized.equals("remove")) {
            boolean hydraRemoved = applyHydraRank(admin, targetId, "none");
            boolean staffRemoved = applyStaffRank(admin, targetId, "none");
            return hydraRemoved || staffRemoved;
        }
        return applyHydraRank(admin, targetId, mode);
    }

    private boolean applyHydraRank(Player admin, UUID targetId, String mode) {
        if (!(plugin instanceof HGLootPlugin hydra)) {
            admin.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Rank tools are unavailable right now.");
            return false;
        }
        return hydra.applyHydraRank(admin, targetId, cosmeticTargetDisplayName(targetId), mode);
    }

    private boolean applyStaffRank(Player admin, UUID targetId, String mode) {
        if (!(plugin instanceof HGLootPlugin hydra)) {
            admin.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Rank tools are unavailable right now.");
            return false;
        }
        return hydra.applyStaffRank(admin, targetId, cosmeticTargetDisplayName(targetId), mode);
    }

    private boolean isAutoWinTriggerEnabled() {
        if (plugin instanceof HGLootPlugin hydra && hydra.queueService() != null) {
            return hydra.queueService().isAutoWinTriggerEnabled();
        }
        return false;
    }

    private enum FriendsView {
        ALL("all"),
        REQUESTS("requests"),
        BLOCKED("blocked");

        private final String id;

        FriendsView(String id) {
            this.id = id;
        }

        private static FriendsView fromId(String id) {
            for (FriendsView value : values()) {
                if (value.id.equalsIgnoreCase(id)) return value;
            }
            return null;
        }
    }

    private enum CosmeticDomain {
        GAME("game", "Game Cosmetics", "Match visuals and skins", Material.NETHERITE_SWORD),
        LOBBY("lobby", "Lobby Cosmetics", "Hub-only vanity cosmetics", Material.ENDER_CHEST),
        RANK("rank", "Rank Color", "Hydra+ presentation styles", Material.NAME_TAG);

        private final String id;
        private final String displayName;
        private final String description;
        private final Material iconMaterial;

        CosmeticDomain(String id, String displayName, String description, Material iconMaterial) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.iconMaterial = iconMaterial;
        }

        private static CosmeticDomain fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (CosmeticDomain domain : values()) {
                if (domain.id.equalsIgnoreCase(id)) {
                    return domain;
                }
            }
            return null;
        }
    }

    private enum CosmeticCategory {
        LEGENDARY_SKINS("legendary_skins", CosmeticDomain.GAME, "Weapon Skins", "Apply skins to legendary items", Material.NETHERITE_SWORD),
        PROJECTILE_TRAILS("projectile_trails", CosmeticDomain.GAME, "Trails", "Visual trails for arrows and projectiles", Material.ARROW, "projectile_trail"),
        ARMOR_TRIMS("armor_trims", CosmeticDomain.GAME, "Trims", "Stored vanilla trim presets", Material.SMITHING_TABLE, "armor_trim"),
        KILL_EFFECTS("kill_effects", CosmeticDomain.GAME, "Kill Effects", "Short visual effects on kills", Material.FIREWORK_STAR, "kill_effect"),
        KILL_MESSAGES("kill_messages", CosmeticDomain.GAME, "Kill Messages", "Customize your player kill feed line", Material.WRITABLE_BOOK, "kill_message"),
        TITLES("titles", CosmeticDomain.LOBBY, "Titles", "Profile and lobby identity titles", Material.NAME_TAG, "title"),
        HATS("hats", CosmeticDomain.LOBBY, "Hats", "Wear cosmetics in the hub", Material.CARVED_PUMPKIN, "hat"),
        RANK_STYLES("rank_styles", CosmeticDomain.RANK, "Hydra+ Styles", "Choose your Hydra+ gradient", Material.NAME_TAG);

        private final String id;
        private final CosmeticDomain domain;
        private final String displayName;
        private final String description;
        private final Material iconMaterial;
        private final String equipGroup;

        CosmeticCategory(String id, CosmeticDomain domain, String displayName, String description, Material iconMaterial) {
            this(id, domain, displayName, description, iconMaterial, null);
        }

        CosmeticCategory(String id, CosmeticDomain domain, String displayName, String description, Material iconMaterial, String equipGroup) {
            this.id = id;
            this.domain = domain;
            this.displayName = displayName;
            this.description = description;
            this.iconMaterial = iconMaterial;
            this.equipGroup = equipGroup;
        }

        private static CosmeticCategory fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (CosmeticCategory category : values()) {
                if (category.id.equalsIgnoreCase(id)) {
                    return category;
                }
            }
            return null;
        }
    }

    private record CosmeticDefinition(
        String id,
        CosmeticDomain domain,
        CosmeticCategory category,
        String displayName,
        String description,
        Material material,
        String itemModel,
        int customModelData,
        String unlockType,
        String rarity,
        boolean previewSupported,
        boolean equipSupported,
        boolean hidden,
        int sortOrder,
        String headCosmeticId,
        String legendaryKey,
        String rankMode,
        String killMessageTemplate
    ) {
        private static CosmeticDefinition fromHeadCosmetic(HeadCosmetic cosmetic) {
            if (cosmetic == null || cosmetic.isNone()) {
                return null;
            }
            CosmeticCategory category = cosmetic.isLegendarySkin() ? CosmeticCategory.LEGENDARY_SKINS : CosmeticCategory.HATS;
            String description = cosmetic.isLegendarySkin()
                ? "Skin for " + cosmetic.legendaryKeyDisplay()
                : "Hub hat cosmetic";
            return new CosmeticDefinition(
                cosmetic.id,
                category.domain,
                category,
                cosmetic.displayName,
                description,
                cosmetic.material,
                cosmetic.itemModel,
                cosmetic.customModelData,
                cosmetic.unlockType,
                cosmetic.rarity,
                true,
                true,
                false,
                cosmetic.menuOrder,
                cosmetic.id,
                cosmetic.legendaryKey,
                null,
                null
            );
        }

        private static CosmeticDefinition rank(
            String id,
            String displayName,
            String description,
            Material material,
            String rarity,
            int sortOrder,
            String rankMode
        ) {
            return new CosmeticDefinition(
                id,
                CosmeticDomain.RANK,
                CosmeticCategory.RANK_STYLES,
                displayName,
                description,
                material,
                null,
                -1,
                "rank",
                rarity,
                true,
                true,
                false,
                sortOrder,
                null,
                null,
                rankMode,
                null
            );
        }

        private static CosmeticDefinition killMessage(
            String id,
            String displayName,
            String description,
            Material material,
            String unlockType,
            String rarity,
            int sortOrder,
            String killMessageTemplate
        ) {
            return new CosmeticDefinition(
                id,
                CosmeticDomain.GAME,
                CosmeticCategory.KILL_MESSAGES,
                displayName,
                description,
                material,
                null,
                -1,
                unlockType,
                rarity,
                true,
                true,
                false,
                sortOrder,
                null,
                null,
                null,
                killMessageTemplate
            );
        }

        private static CosmeticDefinition system(
            String id,
            CosmeticCategory category,
            String displayName,
            String description,
            Material material,
            String unlockType,
            String rarity,
            int sortOrder
        ) {
            return new CosmeticDefinition(
                id,
                category.domain,
                category,
                displayName,
                description,
                material,
                null,
                -1,
                unlockType,
                rarity,
                true,
                true,
                false,
                sortOrder,
                null,
                null,
                null,
                null
            );
        }
    }

    private static final class HeadCosmetic {
        private static final HeadCosmetic NONE = new HeadCosmetic(
            "none", "None", Material.AIR, null, -1, false, false, Integer.MIN_VALUE, "head", "none", "common",
            CosmeticType.HAT, null
        );

        private final String id;
        private final String displayName;
        private final Material material;
        private final String itemModel;
        private final int customModelData;
        private final boolean visibleInLobby;
        private final boolean visibleInMatch;
        private final int menuOrder;
        private final String category;
        private final String unlockType;
        private final String rarity;
        private final CosmeticType cosmeticType;
        private final String legendaryKey;

        private HeadCosmetic(
            String id,
            String displayName,
            Material material,
            String itemModel,
            int customModelData,
            boolean visibleInLobby,
            boolean visibleInMatch,
            int menuOrder,
            String category,
            String unlockType,
            String rarity,
            CosmeticType cosmeticType,
            String legendaryKey
        ) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.itemModel = itemModel;
            this.customModelData = customModelData;
            this.visibleInLobby = visibleInLobby;
            this.visibleInMatch = visibleInMatch;
            this.menuOrder = menuOrder;
            this.category = category;
            this.unlockType = unlockType;
            this.rarity = rarity;
            this.cosmeticType = cosmeticType == null ? CosmeticType.HAT : cosmeticType;
            this.legendaryKey = legendaryKey == null || legendaryKey.isBlank()
                ? null
                : legendaryKey.trim().toLowerCase(Locale.ROOT);
        }

        private boolean isNone() {
            return this == NONE || id.equals(NONE.id);
        }

        private boolean isHat() {
            return !isNone() && cosmeticType == CosmeticType.HAT;
        }

        private boolean isLegendarySkin() {
            return !isNone() && cosmeticType == CosmeticType.LEGENDARY;
        }

        private String legendaryKeyDisplay() {
            if (legendaryKey == null || legendaryKey.isBlank()) {
                return "Legendary";
            }
            return switch (legendaryKey) {
                case "ghlochester" -> "Ghlochester";
                case "skeletal_barber" -> "Skeletal Barber";
                case "electric_conch" -> "Electric Conch";
                case "sponge_saber" -> "Sponge Saber";
                default -> legendaryKey.replace('_', ' ');
            };
        }

        private static HeadCosmetic fromConfig(String key, ConfigurationSection section) {
            String id = key == null ? null : key.trim().toLowerCase(Locale.ROOT);
            if (id == null || id.isBlank() || id.equals(NONE.id)) {
                return null;
            }

            String displayName = section.getString("name", id);
            String materialName = section.getString("material", Material.COAL.name());
            Material material = Material.matchMaterial(materialName == null ? "" : materialName);
            if (material == null || material == Material.AIR) {
                material = Material.COAL;
            }

            String itemModel = section.getString("item_model", "").trim().toLowerCase(Locale.ROOT);
            CosmeticType cosmeticType = CosmeticType.fromId(section.getString("type", "hat"));
            if (itemModel.isBlank()) {
                itemModel = defaultItemModelForId(id);
            }
            int customModelData = section.getInt("custom_model_data", -1);
            if (cosmeticType == CosmeticType.LEGENDARY && !itemModel.isBlank()) {
                customModelData = -1;
            }
            if (cosmeticType == CosmeticType.LEGENDARY && itemModel.isBlank() && customModelData <= 0) {
                return null;
            }

            boolean visibleInLobby = section.getBoolean("visible_in_lobby", true);
            boolean visibleInMatch = section.getBoolean("visible_in_match", false);
            int menuOrder = section.getInt("menu_order", Integer.MAX_VALUE);
            String category = section.getString("category", "head");
            String unlockType = section.getString("unlock_type", "default");
            String rarity = section.getString("rarity", "common");
            String legendaryKey = section.getString("legendary_key", "");
            if (cosmeticType == CosmeticType.LEGENDARY && legendaryKey != null) {
                legendaryKey = legendaryKey.trim().toLowerCase(Locale.ROOT);
            }

            return new HeadCosmetic(
                id,
                displayName,
                material,
                itemModel.isBlank() ? null : itemModel,
                customModelData,
                visibleInLobby,
                visibleInMatch,
                menuOrder,
                category,
                unlockType,
                rarity,
                cosmeticType,
                cosmeticType == CosmeticType.LEGENDARY ? legendaryKey : null
            );
        }

        private static String defaultItemModelForId(String id) {
            return switch (id) {
                case "coal", "tophat" -> "hydra:tophat";
                case "halo" -> "hydra:halo";
                case "glasses" -> "hydra:glasses";
                case "moneyglasses" -> "hydra:moneyglasses";
                case "magnet_helmet" -> "hydra:magnet_helmet";
                case "tie" -> "hydra:tie";
                default -> "";
            };
        }
    }

    private enum CosmeticType {
        HAT("hat"),
        LEGENDARY("legendary");

        private final String id;

        CosmeticType(String id) {
            this.id = id;
        }

        private static CosmeticType fromId(String id) {
            if (id == null || id.isBlank()) {
                return HAT;
            }
            for (CosmeticType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return HAT;
        }
    }

    private enum CosmeticMenuTab {
        HATS("hats", CosmeticCategory.HATS),
        LEGENDARY("legendary", CosmeticCategory.LEGENDARY_SKINS),
        PROJECTILE_TRAILS("projectile_trails", CosmeticCategory.PROJECTILE_TRAILS),
        ARMOR_TRIMS("armor_trims", CosmeticCategory.ARMOR_TRIMS),
        KILL_EFFECTS("kill_effects", CosmeticCategory.KILL_EFFECTS),
        KILL_MESSAGES("kill_messages", CosmeticCategory.KILL_MESSAGES),
        TITLES("titles", CosmeticCategory.TITLES);

        private final String id;
        private final CosmeticCategory category;

        CosmeticMenuTab(String id, CosmeticCategory category) {
            this.id = id;
            this.category = category;
        }

        private static CosmeticMenuTab fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (CosmeticMenuTab tab : values()) {
                if (tab.id.equalsIgnoreCase(id)) {
                    return tab;
                }
            }
            return null;
        }
    }

    private record LegendarySkinTarget(String legendaryKey, String displayName, String baseModel) {
    }

    private enum RailgunMode {
        NORMAL("normal");

        private final String id;

        RailgunMode(String id) {
            this.id = id;
        }

        private static RailgunMode fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (RailgunMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private record RailgunPath(Location impact, double length) {
    }

    private record RailgunTarget(LivingEntity target, Location impact, double distance, double quality) {
    }

    private enum ShotMode {
        NUKE("nuke", "Nuke Shot"),
        STAB("stab", "Stab Shot");

        private final String id;
        private final String display;

        ShotMode(String id, String display) {
            this.id = id;
            this.display = display;
        }

        private static ShotMode fromId(String id) {
            for (ShotMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return null;
        }
    }
}


