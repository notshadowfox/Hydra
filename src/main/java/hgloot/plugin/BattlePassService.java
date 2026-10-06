package hgloot.plugin;

import hgloot.plugin.gui.BattlePassMenuHolder;
import hgloot.plugin.gui.GuiTitleFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class BattlePassService implements Listener {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_COUNT = 8;
    private static final int CLOSE_SLOT = 0;
    private static final int INFO_SLOT = 8;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int[] PAGE_START_TIERS = {1, 9, 18, 27, 36, 45, 54, 63};
    private static final int[] PAGE_END_TIERS = {8, 17, 26, 35, 44, 53, 62, 70};

    private final JavaPlugin plugin;
    private final AuctionHouseService economy;
    private final MenuService menus;
    private final File configFile;
    private final File dataFile;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<String> claimsInProgress = new HashSet<>();
    private YamlConfiguration config;
    private String seasonId;
    private String seasonName;
    private int tierCount;
    private int xpPerTier;

    public BattlePassService(
        JavaPlugin plugin,
        AuctionHouseService economy,
        MenuService menus
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.menus = menus;
        this.configFile = new File(plugin.getDataFolder(), "battlepass.yml");
        this.dataFile = new File(plugin.getDataFolder(), "battlepass-data.yml");
        reload();
    }

    public void shutdown() {
        saveData();
        claimsInProgress.clear();
    }

    public boolean reload() {
        if (!configFile.isFile()) {
            plugin.saveResource("battlepass.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configFile);
        String nextSeason = normalizedId(loaded.getString("active-season", "beta"));
        ConfigurationSection season = loaded.getConfigurationSection("seasons." + nextSeason);
        if (season == null) {
            plugin.getLogger().severe("[BattlePass] Active season is missing: " + nextSeason);
            return false;
        }
        int nextTiers = Math.max(1, season.getInt("tiers", 70));
        int nextXpPerTier = Math.max(1, season.getInt("xp-per-tier", 3000));
        this.config = loaded;
        this.seasonId = nextSeason;
        this.seasonName = season.getString("display-name", nextSeason);
        this.tierCount = nextTiers;
        this.xpPerTier = nextXpPerTier;
        plugin.getLogger().info("[BattlePass] Loaded season=" + seasonId + " tiers=" + tierCount
            + " xp-per-tier=" + xpPerTier + ".");
        loadData();
        return true;
    }

    public boolean handlePlayerCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the Battle Pass.");
            return true;
        }
        open(player, pageForTier(tier(player.getUniqueId())));
        return true;
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage the Battle Pass.");
            return true;
        }
        if (args.length == 0) {
            adminUsage(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(reload()
                ? ChatColor.GREEN + "Battle Pass configuration reloaded."
                : ChatColor.RED + "Battle Pass reload failed. Check the server log.");
            return true;
        }
        if (args.length < 3) {
            adminUsage(sender);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("premium")) {
            boolean grant = args[2].equalsIgnoreCase("grant");
            boolean revoke = args[2].equalsIgnoreCase("revoke");
            if (!grant && !revoke) {
                sender.sendMessage(ChatColor.RED + "Usage: /admin battlepass premium <player> <grant|revoke>");
                return true;
            }
            state(targetId).premium = grant;
            saveData();
            sender.sendMessage(ChatColor.GREEN + (grant ? "Granted" : "Revoked")
                + " Premium Battle Pass for " + displayName(target) + ".");
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Amount/tier must be a whole number.");
            return true;
        }
        switch (action) {
            case "addxp" -> addXp(targetId, Math.max(0, value), "ADMIN");
            case "setxp" -> setXp(targetId, Math.max(0, value));
            case "settier" -> setXp(targetId,
                (Math.max(1, Math.min(tierCount, value)) - 1) * xpPerTier);
            default -> {
                adminUsage(sender);
                return true;
            }
        }
        sender.sendMessage(ChatColor.GREEN + "Updated " + displayName(target) + ": Tier " + tier(targetId)
            + " (" + xp(targetId) + " BP XP).");
        return true;
    }

    public List<String> completeAdmin(String[] args) {
        if (args.length <= 1) return filtered(args, 1, List.of("addxp", "setxp", "settier", "premium", "reload"));
        if (args[0].equalsIgnoreCase("reload")) return List.of();
        if (args.length == 2) return filtered(args, 2, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (args.length == 3 && args[0].equalsIgnoreCase("premium")) return filtered(args, 3, List.of("grant", "revoke"));
        if (args.length == 3) return filtered(args, 3, List.of("100", "300", "1000", "3000"));
        return List.of();
    }

    public int xp(UUID playerId) {
        return state(playerId).xp;
    }

    public int tier(UUID playerId) {
        return Math.min(tierCount, (xp(playerId) / xpPerTier) + 1);
    }

    public boolean premium(UUID playerId) {
        return state(playerId).premium;
    }

    public void addXp(UUID playerId, int amount, String source) {
        if (playerId == null || amount <= 0) return;
        int oldTier = tier(playerId);
        PlayerState state = state(playerId);
        state.xp = clampAdd(state.xp, amount);
        saveData();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GOLD + "+" + amount + " Battle Pass XP"
                + ChatColor.DARK_GRAY + " (" + source + ")");
            int newTier = tier(playerId);
            if (newTier > oldTier) {
                player.sendMessage(ChatColor.GOLD + "Battle Pass Tier " + newTier + " reached!");
            }
        }
    }

    public void recordHgElimination(UUID playerId, String matchId) {
        if (playerId == null || matchId == null) return;
        PlayerState state = state(playerId);
        String key = seasonId + ":" + matchId;
        int earned = state.hgEliminationXp.getOrDefault(key, 0);
        int each = configInt("gameplay.hunger-games.elimination", 50);
        int cap = configInt("gameplay.hunger-games.elimination-cap", 250);
        int award = Math.min(each, Math.max(0, cap - earned));
        if (award <= 0) return;
        state.hgEliminationXp.put(key, earned + award);
        addXp(playerId, award, "HG elimination");
    }

    public void completeHgMatch(UUID playerId, String matchId, int placement) {
        if (playerId == null || matchId == null) return;
        String completion = seasonId + ":hg:" + matchId;
        PlayerState state = state(playerId);
        if (!state.completedEvents.add(completion)) return;
        int award = configInt("gameplay.hunger-games.complete", 300) + hgPlacementAward(placement);
        addXp(playerId, award, "HG completion (" + placement + ")");
        state.hgEliminationXp.remove(seasonId + ":" + matchId);
        saveData();
    }

    public void completeRouletteTournament(UUID playerId, String tournamentId, int placement) {
        if (playerId == null || tournamentId == null) return;
        String completion = seasonId + ":mr:" + tournamentId;
        PlayerState state = state(playerId);
        if (!state.completedEvents.add(completion)) return;
        int award = configInt("gameplay.minigame-roulette.complete", 400)
            + configInt("gameplay.minigame-roulette.placement." + ordinalKey(placement), 0);
        addXp(playerId, award, "Roulette completion (" + placement + ")");
        saveData();
    }

    public void open(Player player, int page) {
        int safePage = Math.max(1, Math.min(PAGE_COUNT, page));
        BattlePassMenuHolder holder = new BattlePassMenuHolder(safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, GuiTitleFactory.battlePassTitle(safePage));
        holder.setInventory(inventory);
        inventory.setItem(CLOSE_SLOT, target(ChatColor.RED + "Close"));
        inventory.setItem(INFO_SLOT, information(player));
        if (safePage > 1) inventory.setItem(PREVIOUS_SLOT, target(ChatColor.YELLOW + "Previous Page"));
        if (safePage < PAGE_COUNT) inventory.setItem(NEXT_SLOT, target(ChatColor.YELLOW + "Next Page"));
        int firstTier = PAGE_START_TIERS[safePage - 1];
        int lastTier = PAGE_END_TIERS[safePage - 1];
        int freeStart = safePage == 1 ? 19 : 18;
        int premiumStart = safePage == 1 ? 28 : 27;
        for (int tier = firstTier; tier <= lastTier; tier++) {
            int column = tier - firstTier;
            inventory.setItem(freeStart + column, rewardIcon(player, tier, Track.FREE));
            inventory.setItem(premiumStart + column, rewardIcon(player, tier, Track.PREMIUM));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BattlePassMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.page() > 1) {
            open(player, holder.page() - 1);
            return;
        }
        if (slot == NEXT_SLOT && holder.page() < PAGE_COUNT) {
            open(player, holder.page() + 1);
            return;
        }
        RewardSlot rewardSlot = rewardAt(holder.page(), slot);
        if (rewardSlot != null) claim(player, rewardSlot.tier, rewardSlot.track, holder.page());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BattlePassMenuHolder) event.setCancelled(true);
    }

    private void claim(Player player, int tier, Track track, int page) {
        UUID playerId = player.getUniqueId();
        String claimKey = claimKey(tier, track);
        PlayerState state = state(playerId);
        if (tier(playerId) < tier) {
            player.sendMessage(ChatColor.RED + "Reach Battle Pass Tier " + tier + " first.");
            return;
        }
        if (track == Track.PREMIUM && !state.premium) {
            player.sendMessage(ChatColor.RED + "This reward requires the Premium Battle Pass.");
            return;
        }
        if (state.claimed.contains(claimKey)) {
            player.sendMessage(ChatColor.GRAY + "That reward is already claimed.");
            return;
        }
        String guard = playerId + ":" + seasonId + ":" + claimKey;
        if (!claimsInProgress.add(guard)) return;
        try {
            Reward reward = reward(tier, track);
            String transactionId = "battlepass:" + seasonId + ":" + playerId + ":" + claimKey;
            boolean granted = switch (reward.type) {
                case COINS -> economy.grantCoinsOnce(playerId, reward.amount, transactionId, "BATTLE_PASS");
                case COSMETIC -> reward.cosmeticId != null && menus.addCosmeticOwnership(playerId, reward.cosmeticId);
            };
            if (!granted) {
                player.sendMessage(ChatColor.RED + "That reward could not be granted. Nothing was consumed.");
                return;
            }
            state.claimed.add(claimKey);
            saveData();
            player.sendMessage(ChatColor.GREEN + "Claimed " + reward.display() + " from Tier " + tier + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.25F);
            open(player, page);
        } finally {
            claimsInProgress.remove(guard);
        }
    }

    private ItemStack information(Player player) {
        int currentTier = tier(player.getUniqueId());
        int progress = currentTier >= tierCount ? xpPerTier : xp(player.getUniqueId()) % xpPerTier;
        return target(ChatColor.AQUA + "Battle Pass Information",
            ChatColor.GRAY + "Battle Pass Level: " + ChatColor.WHITE + currentTier,
            ChatColor.GRAY + "XP: " + ChatColor.WHITE + progress + " / " + xpPerTier,
            ChatColor.GRAY + "Season: " + ChatColor.WHITE + seasonName,
            ChatColor.GRAY + "Premium: " + (premium(player.getUniqueId()) ? ChatColor.GOLD + "Owned" : ChatColor.RED + "Not Owned"));
    }

    private ItemStack rewardIcon(Player player, int tier, Track track) {
        Reward reward = reward(tier, track);
        ItemStack stack = reward.type == RewardType.COSMETIC && reward.cosmeticId != null
            ? menus.createCosmeticAuctionIcon(reward.cosmeticId)
            : new ItemStack(Material.GOLD_NUGGET);
        if (stack == null) stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            RewardState state = rewardState(player.getUniqueId(), tier, track);
            meta.setDisplayName((track == Track.PREMIUM ? ChatColor.GOLD : ChatColor.GRAY)
                + "Tier " + tier + " " + track.displayName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Reward: " + ChatColor.WHITE + reward.display());
            lore.add(ChatColor.GRAY + "Track: " + (track == Track.PREMIUM ? ChatColor.GOLD : ChatColor.WHITE) + track.displayName);
            lore.add(ChatColor.GRAY + "State: " + state.color + state.displayName);
            if (state == RewardState.CLAIMABLE) lore.add(ChatColor.GREEN + "Click to claim.");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private RewardState rewardState(UUID playerId, int tier, Track track) {
        PlayerState state = state(playerId);
        if (state.claimed.contains(claimKey(tier, track))) return RewardState.CLAIMED;
        if (tier(playerId) < tier || (track == Track.PREMIUM && !state.premium)) return RewardState.LOCKED;
        return RewardState.CLAIMABLE;
    }

    private Reward reward(int tier, Track track) {
        String base = "seasons." + seasonId + ".rewards.tiers." + tier + "." + track.key;
        String fallback = "seasons." + seasonId + ".rewards.default-" + track.key;
        String typeName = config.getString(base + ".type", config.getString(fallback + ".type", "COINS"));
        RewardType type;
        try {
            type = RewardType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            type = RewardType.COINS;
        }
        int amount = Math.max(0, config.getInt(base + ".amount", config.getInt(fallback + ".amount", 0)));
        String cosmeticId = normalizedNullable(config.getString(base + ".cosmetic-id",
            config.getString(fallback + ".cosmetic-id")));
        return new Reward(type, amount, cosmeticId);
    }

    private RewardSlot rewardAt(int page, int slot) {
        int first = PAGE_START_TIERS[page - 1];
        int count = PAGE_END_TIERS[page - 1] - first + 1;
        int freeStart = page == 1 ? 19 : 18;
        int premiumStart = page == 1 ? 28 : 27;
        if (slot >= freeStart && slot < freeStart + count) return new RewardSlot(first + slot - freeStart, Track.FREE);
        if (slot >= premiumStart && slot < premiumStart + count) return new RewardSlot(first + slot - premiumStart, Track.PREMIUM);
        return null;
    }

    private int hgPlacementAward(int placement) {
        String key = placement == 1 ? "first" : placement == 2 ? "second" : placement == 3 ? "third"
            : placement <= 5 ? "top-five" : placement <= 10 ? "top-ten" : null;
        return key == null ? 0 : configInt("gameplay.hunger-games.placement." + key, 0);
    }

    private int configInt(String path, int fallback) {
        return Math.max(0, config.getInt("seasons." + seasonId + "." + path, fallback));
    }

    private void setXp(UUID playerId, int value) {
        state(playerId).xp = Math.max(0, value);
        saveData();
    }

    private PlayerState state(UUID playerId) {
        return states.computeIfAbsent(playerId, ignored -> new PlayerState());
    }

    private void loadData() {
        states.clear();
        if (!dataFile.isFile()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        for (String rawId : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawId);
                ConfigurationSection section = players.getConfigurationSection(rawId + ".seasons." + seasonId);
                if (section == null) continue;
                PlayerState state = new PlayerState();
                state.xp = Math.max(0, section.getInt("xp", 0));
                state.premium = section.getBoolean("premium", false);
                state.claimed.addAll(section.getStringList("claimed"));
                state.completedEvents.addAll(section.getStringList("completed-events"));
                ConfigurationSection kills = section.getConfigurationSection("hg-elimination-xp");
                if (kills != null) for (String key : kills.getKeys(false)) state.hgEliminationXp.put(key, kills.getInt(key));
                states.put(playerId, state);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveData() {
        YamlConfiguration data = dataFile.isFile()
            ? YamlConfiguration.loadConfiguration(dataFile)
            : new YamlConfiguration();
        data.set("schema-version", 3);
        data.set("active-season", seasonId);
        for (Map.Entry<UUID, PlayerState> entry : states.entrySet()) {
            String base = "players." + entry.getKey() + ".seasons." + seasonId;
            PlayerState state = entry.getValue();
            data.set(base + ".xp", state.xp);
            data.set(base + ".xp-migrated", null);
            data.set(base + ".premium", state.premium);
            data.set(base + ".claimed", new ArrayList<>(state.claimed));
            data.set(base + ".completed-events", new ArrayList<>(state.completedEvents));
            data.set(base + ".hg-elimination-xp", new LinkedHashMap<>(state.hgEliminationXp));
        }
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "[BattlePass] Failed to persist battlepass-data.yml", ex);
        }
    }

    private ItemStack target(String name, String... lore) {
        return menus.createInvisibleGuiTarget(name, lore);
    }

    private int pageForTier(int tier) {
        for (int i = 0; i < PAGE_END_TIERS.length; i++) if (tier <= PAGE_END_TIERS[i]) return i + 1;
        return PAGE_COUNT;
    }

    private String claimKey(int tier, Track track) {
        return track.key + ":" + tier;
    }

    private void adminUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/admin battlepass addxp <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "/admin battlepass setxp <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "/admin battlepass settier <player> <tier>");
        sender.sendMessage(ChatColor.YELLOW + "/admin battlepass premium <player> <grant|revoke>");
        sender.sendMessage(ChatColor.YELLOW + "/admin battlepass reload");
    }

    private static List<String> filtered(String[] args, int position, List<String> values) {
        String prefix = args.length >= position ? args[position - 1].toLowerCase(Locale.ROOT) : "";
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static String normalizedId(String value) {
        return value == null || value.isBlank() ? "beta" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static String ordinalKey(int placement) {
        return switch (placement) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            default -> "none";
        };
    }

    private static int clampAdd(int current, int amount) {
        long value = (long) Math.max(0, current) + Math.max(0, amount);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private enum Track {
        FREE("free", "Free"), PREMIUM("premium", "Premium");
        private final String key;
        private final String displayName;
        Track(String key, String displayName) { this.key = key; this.displayName = displayName; }
    }

    private enum RewardType { COINS, COSMETIC }

    private enum RewardState {
        LOCKED("Locked", ChatColor.RED), CLAIMABLE("Claimable", ChatColor.GREEN), CLAIMED("Claimed", ChatColor.GRAY);
        private final String displayName;
        private final ChatColor color;
        RewardState(String displayName, ChatColor color) { this.displayName = displayName; this.color = color; }
    }

    private record Reward(RewardType type, int amount, String cosmeticId) {
        private String display() {
            return type == RewardType.COINS ? amount + " Coins" : cosmeticId;
        }
    }

    private record RewardSlot(int tier, Track track) {}

    private static final class PlayerState {
        private int xp;
        private boolean premium;
        private final Set<String> claimed = new HashSet<>();
        private final Set<String> completedEvents = new HashSet<>();
        private final Map<String, Integer> hgEliminationXp = new HashMap<>();
    }
}
