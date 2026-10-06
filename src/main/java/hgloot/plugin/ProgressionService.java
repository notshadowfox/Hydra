package hgloot.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.scheduler.BukkitTask;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.gui.CasinoMenuHolder;
import hgloot.plugin.gui.GuiTitleFactory;

public final class ProgressionService implements Listener {
    private static final String CONFIG_ROOT = "progression.players";
    private static final String LEVEL_XP_RECOVERY_FILE = "level-xp-recovery-v2.0.149.yml";
    private static final int BASE_PRESTIGE_LEVEL_XP = 150;
    private static final int SEPARATE_PROGRESSION_SCHEMA = 3;
    static final int FRAGMENTS_PER_SHARD = 25;
    private static final String SHARD_OPEN_VIEW = "shard_open";
    private static final String SHARD_OPENING_VIEW = "shard_opening";
    private static final int SHARD_CRATE_SIZE = 54;
    private static final int SHARD_CRATE_CENTER_SLOT = 22;
    private static final int SHARD_ODDS_SLOT = 37;
    private static final int SHARD_CART_SLOT = 43;
    private static final int[] SHARD_DISPLAY_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] SHARD_OPEN_SLOTS = {39, 40, 41};
    private static final int SHARD_CRATE_TICKS = 34;
    private static final long SHARD_REVEAL_TICKS = 20L;
    private static final List<String> SHARD_RARITY_ORDER = List.of(
        "common", "rare", "epic", "legendary", "limited"
    );
    private static final long SAVE_DEBOUNCE_TICKS = 20L;

    private final JavaPlugin plugin;
    private final AuctionHouseService auctionHouseService;
    private final Map<UUID, ProgressionState> states = new HashMap<>();
    private final Map<UUID, BukkitTask> activeShardCrates = new HashMap<>();
    private final Random random = new Random();
    private MenuService menuService;
    private BukkitTask pendingSaveTask;
    private boolean saveDirty;

    public ProgressionService(JavaPlugin plugin, AuctionHouseService auctionHouseService) {
        this.plugin = plugin;
        this.auctionHouseService = auctionHouseService;
        load();
    }

    public void shutdown() {
        for (BukkitTask task : activeShardCrates.values()) {
            task.cancel();
        }
        activeShardCrates.clear();
        saveNow();
    }

    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }

    public boolean handleCommand(Player player, String[] args) {
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            openShardMenu(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("open") && !args[0].equalsIgnoreCase("claim")) {
            player.sendMessage(ChatColor.RED + "Usage: /shard open");
            return true;
        }
        openShardMenu(player);
        return true;
    }

    public int prestigeXp(UUID playerId) {
        return state(playerId).prestigeXp;
    }

    public int prestigeLevel(UUID playerId) {
        int xp = prestigeXp(playerId);
        int level = 1;
        while (level < 3999) {
            int required = prestigeLevelSize(level);
            if (xp < required) {
                break;
            }
            xp -= required;
            level++;
        }
        return level;
    }

    public int level(UUID playerId) {
        return Math.max(0, prestigeLevel(playerId) - 1);
    }

    public Map<UUID, Integer> levelSnapshot() {
        Map<UUID, Integer> snapshot = new HashMap<>();
        for (UUID playerId : states.keySet()) {
            int level = level(playerId);
            if (level > 0) {
                snapshot.put(playerId, level);
            }
        }
        return snapshot;
    }

    public int prestigeProgress(UUID playerId) {
        int xp = prestigeXp(playerId);
        int level = 1;
        while (level < 3999) {
            int required = prestigeLevelSize(level);
            if (xp < required) {
                return xp;
            }
            xp -= required;
            level++;
        }
        return Math.max(0, xp);
    }

    public int prestigeLevelSize() {
        return BASE_PRESTIGE_LEVEL_XP;
    }

    public int prestigeLevelSize(UUID playerId) {
        return prestigeLevelSize(prestigeLevel(playerId));
    }

    private int prestigeLevelSize(int level) {
        return legacyPrestigeLevelSize(level);
    }

    private static int legacyPrestigeLevelSize(int level) {
        int safeLevel = Math.max(1, level);
        long index = safeLevel - 1L;
        long required = BASE_PRESTIGE_LEVEL_XP + (index * 35L) + (index * index * 6L);
        return required > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
    }

    private static int clampLinearXp(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    static int restoreLegacyLevelXp(int linearXp, int xpPerTier) {
        int safeXpPerTier = Math.max(1, xpPerTier);
        int safeLinearXp = Math.max(0, linearXp);
        int visibleLevel = Math.min(3998, (safeLinearXp / safeXpPerTier) + 1);
        int linearProgress = safeLinearXp % safeXpPerTier;
        long restored = 0L;
        for (int internalLevel = 1; internalLevel <= visibleLevel; internalLevel++) {
            restored += legacyPrestigeLevelSize(internalLevel);
            if (restored >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        int nextRequirement = legacyPrestigeLevelSize(visibleLevel + 1);
        restored += Math.round((double) linearProgress * nextRequirement / safeXpPerTier);
        return clampLinearXp(restored);
    }

    public int shards(UUID playerId) {
        return state(playerId).shards;
    }

    public int shardFragments(UUID playerId) {
        return state(playerId).shardFragments;
    }

    public int fragmentsPerShard() {
        return FRAGMENTS_PER_SHARD;
    }

    public String prestigeRoman(UUID playerId) {
        return roman(level(playerId));
    }

    public void grantPrestigeXp(UUID playerId, int amount, String reason) {
        if (playerId == null || amount <= 0) {
            return;
        }
        ProgressionState state = state(playerId);
        state.prestigeXp = clampAdd(state.prestigeXp, amount);
        save();
        notifyOnline(playerId, ChatColor.YELLOW + "+" + amount + " Level XP");
        applyHubLevelBar(Bukkit.getPlayer(playerId));
    }

    public void setPrestigeXp(UUID playerId, int amount, String reason) {
        if (playerId == null) {
            return;
        }
        ProgressionState state = state(playerId);
        state.prestigeXp = Math.max(0, amount);
        save();
        notifyOnline(playerId, ChatColor.YELLOW + "Level XP set to " + formatNumber(state.prestigeXp));
        applyHubLevelBar(Bukkit.getPlayer(playerId));
    }

    public void setPrestigeLevel(UUID playerId, int level, String reason) {
        setLevel(playerId, level, reason);
    }

    public void setLevel(UUID playerId, int level, String reason) {
        if (playerId == null) {
            return;
        }
        int visibleLevel = Math.max(0, Math.min(3998, level));
        int targetLevel = visibleLevel + 1;
        long requiredTotal = 0L;
        for (int currentLevel = 1; currentLevel < targetLevel; currentLevel++) {
            requiredTotal += prestigeLevelSize(currentLevel);
            if (requiredTotal >= Integer.MAX_VALUE) {
                requiredTotal = Integer.MAX_VALUE;
                break;
            }
        }
        ProgressionState state = state(playerId);
        state.prestigeXp = (int) requiredTotal;
        save();
        notifyOnline(playerId, ChatColor.YELLOW + "Level set to " + visibleLevel);
        applyHubLevelBar(Bukkit.getPlayer(playerId));
    }

    public void resetLevelProgress(UUID playerId, String reason) {
        if (playerId == null) {
            return;
        }
        ProgressionState state = state(playerId);
        state.prestigeXp = 0;
        state.pendingPrestigeXp = 0;
        save();
        notifyOnline(playerId, ChatColor.YELLOW + "Level progress reset");
    }

    public int resetAllLevelProgress(String reason) {
        int count = states.size();
        for (ProgressionState state : states.values()) {
            state.prestigeXp = 0;
            state.pendingPrestigeXp = 0;
        }
        save();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(HydraIcons.EXCLAMATION + " " + ChatColor.YELLOW + "Level progress reset");
            applyHubLevelBar(player);
        }
        return count;
    }

    public void applyHubLevelBar(Player player) {
        if (player == null || !player.isOnline() || !isHubWorld(player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int required = Math.max(1, prestigeLevelSize(playerId));
        int progress = Math.max(0, Math.min(required, prestigeProgress(playerId)));
        player.setLevel(level(playerId));
        player.setExp(Math.max(0.0F, Math.min(1.0F, (float) progress / (float) required)));
    }

    public void clearLevelBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setLevel(0);
        player.setExp(0.0F);
    }

    private boolean isHubWorld(Player player) {
        return player.getWorld() != null && "Hub".equalsIgnoreCase(player.getWorld().getName());
    }

    public void grantShards(UUID playerId, int amount, String reason) {
        if (playerId == null || amount <= 0) {
            return;
        }
        ProgressionState state = state(playerId);
        state.shards = clampAdd(state.shards, amount);
        save();
        notifyOnline(playerId, ChatColor.LIGHT_PURPLE + "+" + amount + " Shard" + (amount == 1 ? "" : "s"));
        refreshHubControls(playerId);
    }

    public void setShards(UUID playerId, int amount, String reason) {
        if (playerId == null) {
            return;
        }
        ProgressionState state = state(playerId);
        state.shards = Math.max(0, amount);
        save();
        notifyOnline(playerId, ChatColor.LIGHT_PURPLE + "Shards set to " + formatNumber(state.shards));
        refreshHubControls(playerId);
    }

    public void grantShardFragments(UUID playerId, int amount, String reason) {
        if (playerId == null || amount <= 0) {
            return;
        }
        ProgressionState state = state(playerId);
        state.shardFragments = clampAdd(state.shardFragments, amount);
        normalizeFragments(state);
        save();
        notifyOnline(playerId, ChatColor.DARK_PURPLE + "+" + amount + " Shard Fragment" + (amount == 1 ? "" : "s"));
        refreshHubControls(playerId);
    }

    public void setShardFragments(UUID playerId, int amount, String reason) {
        if (playerId == null) {
            return;
        }
        ProgressionState state = state(playerId);
        state.shardFragments = Math.max(0, amount);
        normalizeFragments(state);
        save();
        notifyOnline(playerId, ChatColor.DARK_PURPLE + "Shard Fragments set to " + formatNumber(state.shardFragments));
        refreshHubControls(playerId);
    }

    public void queueSilver(UUID playerId, int amount, String reason) {
        if (playerId == null || amount <= 0) {
            return;
        }
        if (auctionHouseService != null) {
            auctionHouseService.grantSilver(playerId, amount, reason == null ? "REWARD" : reason);
        }
    }

    public void queuePrestigeXp(UUID playerId, int amount, String reason) {
        grantPrestigeXp(playerId, amount, reason);
    }

    public void queueShards(UUID playerId, int amount, String reason) {
        grantShards(playerId, amount, reason);
    }

    public void queueShardFragments(UUID playerId, int amount, String reason) {
        grantShardFragments(playerId, amount, reason);
    }

    private void claimStoredRewards(Player player) {
        UUID playerId = player.getUniqueId();
        ProgressionState state = state(playerId);
        int silver = state.pendingSilver;
        int xp = state.pendingPrestigeXp;
        int shards = state.pendingShards;
        int fragments = state.pendingShardFragments;
        if (silver <= 0 && xp <= 0 && shards <= 0 && fragments <= 0) {
            player.sendMessage(ChatColor.YELLOW + "[Wallet] No unclaimed rewards.");
            return;
        }
        state.pendingSilver = 0;
        state.pendingPrestigeXp = 0;
        state.pendingShards = 0;
        state.pendingShardFragments = 0;
        if (silver > 0 && auctionHouseService != null) {
            auctionHouseService.grantSilver(playerId, silver, "WALLET_CLAIM");
        }
        if (xp > 0) {
            state.prestigeXp = clampAdd(state.prestigeXp, xp);
        }
        if (shards > 0) {
            state.shards = clampAdd(state.shards, shards);
        }
        if (fragments > 0) {
            state.shardFragments = clampAdd(state.shardFragments, fragments);
            normalizeFragments(state);
        }
        save();
        player.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN + "Claimed stored rewards.");
        refreshHubControls(playerId);
    }

    private void openShardMenu(Player player) {
        UUID playerId = player.getUniqueId();
        if (activeShardCrates.containsKey(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "[Shard] Your crate is already opening.");
            return;
        }
        ProgressionState state = state(playerId);
        if (state.pendingSilver > 0 || state.pendingPrestigeXp > 0 || state.pendingShards > 0 || state.pendingShardFragments > 0) {
            claimStoredRewards(player);
            state = state(playerId);
        }
        if (menuService == null) {
            player.sendMessage(ChatColor.RED + "[Shard] Cosmetic rewards are unavailable right now.");
            return;
        }
        List<String> rewards = eligibleShardRewards(menuService.getShardCrateRewardCosmeticIds(playerId));
        Inventory inventory = shardInventory(SHARD_OPEN_VIEW);
        inventory.setItem(SHARD_CRATE_CENTER_SLOT, shardDisplay(state.shards));
        inventory.setItem(SHARD_ODDS_SLOT, menuService.createInvisibleGuiTarget(
            ChatColor.LIGHT_PURPLE + "Shard Rarity Odds",
            shardOddsLore(rewards)
        ));
        ItemStack openTarget = menuService.createInvisibleGuiTarget(
            ChatColor.GREEN + "Open Shard",
            state.shards > 0 ? ChatColor.GRAY + "Click to open one Shard." : ChatColor.RED + "No Shards available."
        );
        for (int slot : SHARD_OPEN_SLOTS) {
            inventory.setItem(slot, openTarget.clone());
        }
        inventory.setItem(SHARD_CART_SLOT, menuService.createInvisibleGuiTarget(
            ChatColor.YELLOW + "Coming Soon!",
            ChatColor.GRAY + "The Shard shop is not available yet."
        ));
        player.openInventory(inventory);
    }

    private void beginShardOpening(Player player) {
        UUID playerId = player.getUniqueId();
        if (activeShardCrates.containsKey(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "[Shard] Your crate is already opening.");
            return;
        }
        ProgressionState state = state(playerId);
        if (state.shards <= 0) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "[Shard] " + ChatColor.GRAY + "You have no Shards to open.");
            openShardMenu(player);
            return;
        }
        if (menuService == null) {
            player.sendMessage(ChatColor.RED + "[Shard] Cosmetic rewards are unavailable right now.");
            return;
        }
        List<String> rewardIds = eligibleShardRewards(menuService.getShardCrateRewardCosmeticIds(playerId));
        if (rewardIds.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "[Shard] You already own every available shard cosmetic.");
            return;
        }

        state.shards--;
        save();
        refreshHubControls(playerId);

        Inventory inventory = shardInventory(SHARD_OPENING_VIEW);
        for (int slot : SHARD_DISPLAY_SLOTS) {
            inventory.setItem(slot, shardFiller(slot));
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick;

            @Override
            public void run() {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null || !online.isOnline()) {
                    finish(null);
                    return;
                }
                if (!(online.getOpenInventory().getTopInventory().getHolder() instanceof CasinoMenuHolder holder)
                    || !SHARD_OPENING_VIEW.equals(holder.view())) {
                    online.openInventory(inventory);
                }
                spin(inventory, rewardIds);
                online.playSound(online.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.4f + Math.min(0.5f, tick * 0.015f));
                tick++;
                if (tick >= SHARD_CRATE_TICKS) {
                    String rewardId = selectShardReward(rewardIds);
                    if (rewardId == null) {
                        online.sendMessage(ChatColor.RED + "[Shard] No eligible reward rarity was available.");
                        finish(playerId);
                        return;
                    }
                    ItemStack rewardIcon = menuService.createCosmeticAuctionIcon(rewardId);
                    if (rewardIcon != null) {
                        inventory.setItem(SHARD_CRATE_CENTER_SLOT, rewardIcon);
                    }
                    if (menuService.addCosmeticOwnership(playerId, rewardId)) {
                        String displayName = menuService.getCosmeticDisplayName(rewardId);
                        online.sendMessage(ChatColor.LIGHT_PURPLE + "[Shard] " + ChatColor.GREEN + "Unlocked "
                            + ChatColor.YELLOW + (displayName == null ? rewardId : displayName) + ChatColor.GREEN + ".");
                    } else {
                        online.sendMessage(ChatColor.YELLOW + "[Shard] Reward was already owned; no duplicate was added.");
                    }
                    online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
                    finish(playerId);
                    scheduleShardMenuReturn(playerId);
                }
            }

            private void finish(UUID id) {
                BukkitTask task = activeShardCrates.remove(id == null ? playerId : id);
                if (task != null) {
                    task.cancel();
                }
            }
        }, 0L, 2L);
        activeShardCrates.put(playerId, task);
    }

    private Inventory shardInventory(String view) {
        CasinoMenuHolder holder = new CasinoMenuHolder(view, 0);
        Inventory inventory = Bukkit.createInventory(holder, SHARD_CRATE_SIZE,
            GuiTitleFactory.themedTitle(view, "Shard Claim"));
        holder.setInventory(inventory);
        return inventory;
    }

    private void spin(Inventory inventory, List<String> rewardIds) {
        for (int slot : SHARD_DISPLAY_SLOTS) {
            String rewardId = rewardIds.get(random.nextInt(rewardIds.size()));
            ItemStack icon = menuService.createCosmeticAuctionIcon(rewardId);
            inventory.setItem(slot, icon == null ? shardFiller(slot) : icon);
        }
    }

    private void scheduleShardMenuReturn(UUID playerId) {
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeShardCrates.remove(playerId, holder[0]);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline()) {
                openShardMenu(online);
            }
        }, SHARD_REVEAL_TICKS);
        activeShardCrates.put(playerId, holder[0]);
    }

    private List<String> eligibleShardRewards(List<String> rewardIds) {
        if (rewardIds == null || rewardIds.isEmpty()) {
            return List.of();
        }
        return rewardIds.stream()
            .filter(id -> shardRarityWeights().containsKey(normalizedRarity(id)))
            .toList();
    }

    private String selectShardReward(List<String> rewardIds) {
        Map<String, List<String>> byRarity = rewardsByRarity(rewardIds);
        Map<String, Double> weights = shardRarityWeights();
        double totalWeight = SHARD_RARITY_ORDER.stream()
            .filter(byRarity::containsKey)
            .mapToDouble(weights::get)
            .sum();
        if (totalWeight <= 0.0D) {
            return null;
        }
        double roll = random.nextDouble() * totalWeight;
        for (String rarity : SHARD_RARITY_ORDER) {
            List<String> candidates = byRarity.get(rarity);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            roll -= weights.get(rarity);
            if (roll < 0.0D) {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }
        for (int index = SHARD_RARITY_ORDER.size() - 1; index >= 0; index--) {
            List<String> fallback = byRarity.get(SHARD_RARITY_ORDER.get(index));
            if (fallback != null && !fallback.isEmpty()) {
                return fallback.get(random.nextInt(fallback.size()));
            }
        }
        return null;
    }

    private Map<String, List<String>> rewardsByRarity(List<String> rewardIds) {
        Map<String, List<String>> byRarity = new LinkedHashMap<>();
        if (rewardIds == null) {
            return byRarity;
        }
        for (String rewardId : rewardIds) {
            String rarity = normalizedRarity(rewardId);
            if (shardRarityWeights().containsKey(rarity)) {
                byRarity.computeIfAbsent(rarity, ignored -> new ArrayList<>()).add(rewardId);
            }
        }
        return byRarity;
    }

    private String normalizedRarity(String rewardId) {
        String rarity = menuService.getCosmeticRarity(rewardId);
        return rarity == null ? "" : rarity.trim().toLowerCase(Locale.ROOT);
    }

    private ItemStack shardFiller(int slot) {
        ItemStack stack = new ItemStack(slot == SHARD_CRATE_CENTER_SLOT ? Material.AMETHYST_SHARD : Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(slot == SHARD_CRATE_CENTER_SLOT
                ? ChatColor.LIGHT_PURPLE + "Shard Reward"
                : ChatColor.DARK_PURPLE + "Rolling...");
            if (slot == SHARD_CRATE_CENTER_SLOT) {
                meta.setItemModel(new NamespacedKey("hydra", "shard"));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack shardDisplay(int count) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, count)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Shard");
            meta.setItemModel(new NamespacedKey("hydra", "shard"));
            meta.setLore(List.of(
                ChatColor.GRAY + "Available: " + ChatColor.WHITE + Math.max(0, count),
                count > 0 ? ChatColor.GREEN + "Press Open to claim a reward."
                    : ChatColor.RED + "Earn " + FRAGMENTS_PER_SHARD + " Fragments to create a Shard."
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String[] shardOddsLore(List<String> rewardIds) {
        if (rewardIds == null || rewardIds.isEmpty()) {
            return new String[] {ChatColor.GRAY + "No eligible rewards remain."};
        }
        Map<String, List<String>> byRarity = rewardsByRarity(rewardIds);
        Map<String, Double> weights = shardRarityWeights();
        double totalWeight = SHARD_RARITY_ORDER.stream()
            .filter(byRarity::containsKey)
            .mapToDouble(weights::get)
            .sum();
        return SHARD_RARITY_ORDER.stream()
            .filter(byRarity::containsKey)
            .map(rarity -> ChatColor.GRAY + titleCase(rarity) + " - " + ChatColor.WHITE
                + formatChance((weights.get(rarity) * 100.0D) / totalWeight) + "%")
            .toArray(String[]::new);
    }

    private Map<String, Double> shardRarityWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("common", positiveWeight("common", 50.0D));
        weights.put("rare", positiveWeight("rare", 25.0D));
        weights.put("epic", positiveWeight("epic", 15.0D));
        weights.put("legendary", positiveWeight("legendary", 8.0D));
        weights.put("limited", positiveWeight("limited", 2.0D));
        return weights;
    }

    private double positiveWeight(String rarity, double fallback) {
        return Math.max(0.0D, plugin.getConfig().getDouble("shards.rarity_weights." + rarity, fallback));
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String formatChance(double chance) {
        double rounded = Math.rint(chance);
        return Math.abs(chance - rounded) < 0.05D
            ? String.format(Locale.US, "%.0f", rounded)
            : String.format(Locale.US, "%.1f", chance);
    }

    @EventHandler
    public void onShardCrateClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder holder)
            || (!SHARD_OPEN_VIEW.equals(holder.view()) && !SHARD_OPENING_VIEW.equals(holder.view()))) {
            return;
        }
        event.setCancelled(true);
        if (!SHARD_OPEN_VIEW.equals(holder.view())
            || event.getClickedInventory() != event.getView().getTopInventory()
            || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == SHARD_CART_SLOT) {
            player.sendMessage(ChatColor.YELLOW + "[Shard] Coming Soon!");
            return;
        }
        for (int openSlot : SHARD_OPEN_SLOTS) {
            if (slot == openSlot) {
                beginShardOpening(player);
                return;
            }
        }
    }

    @EventHandler
    public void onShardCrateDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder holder
            && (SHARD_OPEN_VIEW.equals(holder.view()) || SHARD_OPENING_VIEW.equals(holder.view()))) {
            event.setCancelled(true);
        }
    }

    private ProgressionState state(UUID playerId) {
        return states.computeIfAbsent(playerId, ignored -> {
            ProgressionState created = new ProgressionState();
            created.xpSchema = SEPARATE_PROGRESSION_SCHEMA;
            return created;
        });
    }

    private void notifyWallet(UUID playerId, String rewardLabel) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.AQUA + "[Wallet] " + ChatColor.GRAY + "Stored "
                + rewardLabel + ChatColor.GRAY + ".");
        }
    }

    private void notifyOnline(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendActionBar(HydraIcons.EXCLAMATION + " " + message);
        }
    }

    private void normalizeFragments(ProgressionState state) {
        if (state.shardFragments < FRAGMENTS_PER_SHARD) {
            return;
        }
        int created = state.shardFragments / FRAGMENTS_PER_SHARD;
        state.shardFragments %= FRAGMENTS_PER_SHARD;
        state.shards = clampAdd(state.shards, created);
    }

    private void refreshHubControls(UUID playerId) {
        if (menuService == null || playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            menuService.refreshHubInterface(player);
        }
    }

    private void load() {
        states.clear();
        Map<UUID, Integer> exactLevelXpRecovery = loadExactLevelXpRecovery();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_ROOT);
        if (section == null) {
            return;
        }
        boolean migratedPendingRewards = false;
        int restoredLevelPlayers = 0;
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                ConfigurationSection stateSection = section.getConfigurationSection(key);
                if (stateSection == null) {
                    continue;
                }
                ProgressionState state = new ProgressionState();
                state.prestigeXp = Math.max(0, stateSection.getInt("prestige_xp", 0));
                state.shards = Math.max(0, stateSection.getInt("shards", 0));
                state.shardFragments = Math.max(0, stateSection.getInt("shard_fragments", 0));
                state.pendingSilver = Math.max(0, stateSection.getInt("wallet.silver", 0));
                state.pendingPrestigeXp = Math.max(0, stateSection.getInt("wallet.prestige_xp", 0));
                state.pendingShards = Math.max(0, stateSection.getInt("wallet.shards", 0));
                state.pendingShardFragments = Math.max(0, stateSection.getInt("wallet.shard_fragments", 0));
                state.xpSchema = Math.max(1, stateSection.getInt("xp_schema", 1));
                state.xpPerTier = Math.max(0, stateSection.getInt("xp_per_tier", 0));
                if (state.xpSchema == 2) {
                    state.prestigeXp = exactLevelXpRecovery.getOrDefault(playerId,
                        restoreLegacyLevelXp(state.prestigeXp,
                            state.xpPerTier > 0 ? state.xpPerTier : 3000));
                    state.xpSchema = SEPARATE_PROGRESSION_SCHEMA;
                    state.xpPerTier = 0;
                    restoredLevelPlayers++;
                }
                normalizeFragments(state);
                migratedPendingRewards |= migratePendingRewards(state, playerId);
                states.put(playerId, state);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (migratedPendingRewards || restoredLevelPlayers > 0) {
            saveDirty = true;
            saveNow();
        }
        if (restoredLevelPlayers > 0) {
            plugin.getLogger().info("[Progression] Restored separate Level progression for "
                + restoredLevelPlayers + " player(s) previously migrated by schema 2.");
        }
    }

    private Map<UUID, Integer> loadExactLevelXpRecovery() {
        java.io.File recoveryFile = new java.io.File(plugin.getDataFolder(), LEVEL_XP_RECOVERY_FILE);
        if (!recoveryFile.isFile()) return Map.of();
        YamlConfiguration recovery = YamlConfiguration.loadConfiguration(recoveryFile);
        ConfigurationSection players = recovery.getConfigurationSection("players");
        if (players == null) return Map.of();
        Map<UUID, Integer> result = new HashMap<>();
        for (String rawId : players.getKeys(false)) {
            try {
                result.put(UUID.fromString(rawId), Math.max(0, players.getInt(rawId)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("[Progression] Loaded " + result.size()
            + " exact Level XP recovery record(s) for schema 2 rollback.");
        return Map.copyOf(result);
    }

    private boolean migratePendingRewards(ProgressionState state, UUID playerId) {
        boolean changed = state.pendingSilver > 0
            || state.pendingPrestigeXp > 0
            || state.pendingShards > 0
            || state.pendingShardFragments > 0;
        if (state.pendingSilver > 0 && auctionHouseService != null) {
            auctionHouseService.grantSilver(playerId, state.pendingSilver, "WALLET_MIGRATION");
        }
        if (state.pendingPrestigeXp > 0) {
            state.prestigeXp = clampAdd(state.prestigeXp, state.pendingPrestigeXp);
        }
        if (state.pendingShards > 0) {
            state.shards = clampAdd(state.shards, state.pendingShards);
        }
        if (state.pendingShardFragments > 0) {
            state.shardFragments = clampAdd(state.shardFragments, state.pendingShardFragments);
            normalizeFragments(state);
        }
        state.pendingSilver = 0;
        state.pendingPrestigeXp = 0;
        state.pendingShards = 0;
        state.pendingShardFragments = 0;
        return changed;
    }

    private void save() {
        saveDirty = true;
        if (pendingSaveTask != null) {
            return;
        }
        pendingSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSaveTask = null;
            saveNow();
        }, SAVE_DEBOUNCE_TICKS);
    }

    private void saveNow() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
        if (!saveDirty) {
            return;
        }
        saveDirty = false;
        plugin.getConfig().set(CONFIG_ROOT, null);
        for (Map.Entry<UUID, ProgressionState> entry : states.entrySet()) {
            String base = CONFIG_ROOT + "." + entry.getKey();
            ProgressionState state = entry.getValue();
            plugin.getConfig().set(base + ".prestige_xp", state.prestigeXp);
            plugin.getConfig().set(base + ".shards", state.shards);
            plugin.getConfig().set(base + ".shard_fragments", state.shardFragments);
            plugin.getConfig().set(base + ".wallet.silver", state.pendingSilver);
            plugin.getConfig().set(base + ".wallet.prestige_xp", state.pendingPrestigeXp);
            plugin.getConfig().set(base + ".wallet.shards", state.pendingShards);
            plugin.getConfig().set(base + ".wallet.shard_fragments", state.pendingShardFragments);
            plugin.getConfig().set(base + ".xp_schema", state.xpSchema);
        }
        plugin.saveConfig();
    }

    private static int clampAdd(int current, int amount) {
        if (amount <= 0) {
            return current;
        }
        long value = (long) Math.max(0, current) + amount;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int parsePositive(String raw, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatNumber(int value) {
        return String.format(Locale.US, "%,d", Math.max(0, value));
    }

    private static String roman(int value) {
        int safe = Math.max(1, Math.min(3999, value));
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (safe >= values[i]) {
                safe -= values[i];
                out.append(symbols[i]);
            }
        }
        return out.toString();
    }

    private static final class ProgressionState {
        private int prestigeXp;
        private int shards;
        private int shardFragments;
        private int pendingSilver;
        private int pendingPrestigeXp;
        private int pendingShards;
        private int pendingShardFragments;
        private int xpSchema = SEPARATE_PROGRESSION_SCHEMA;
        private int xpPerTier;
    }
}
