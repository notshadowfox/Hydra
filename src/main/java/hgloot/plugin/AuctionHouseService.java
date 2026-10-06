package hgloot.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import hgloot.plugin.util.HydraSounds;
import hgloot.plugin.gui.CasinoMenuHolder;
import hgloot.plugin.gui.GuiTitleFactory;

@SuppressWarnings("deprecation")
public final class AuctionHouseService implements Listener {
    private static final String MAIN_TITLE = ChatColor.AQUA + "" + ChatColor.BOLD + "Auction House";
    private static final String CONFIG_COINS = "casino.coins";
    private static final String CONFIG_SILVER = "casino.silver";
    private static final String CONFIG_PENDING = "casino.pending";
    private static final String CONFIG_REWARD_TRANSACTIONS = "casino.reward_transactions";

    private static final int DEFAULT_SILVER = 1250;

    private static final int BID_STEP_COINS = 200;
    private static final int DEFAULT_START_BID = 25;
    private static final int LEGACY_COINS_PER_SILVER = 4;
    private static final long AUCTION_DURATION_MS = 30L * 60L * 1000L;
    private static final int ECON_LOG_LIMIT = 250;
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey listingKey;
    private QueueService queueService;
    private MenuService menuService;

    private final Map<UUID, Integer> silver = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingClaims = new HashMap<>();
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, BrowseSort> browseSorts = new HashMap<>();
    private final Map<UUID, BrowseCategory> browseCategories = new HashMap<>();
    private final Map<UUID, Integer> pendingSellStartBid = new HashMap<>();
    private final List<String> economyLog = new ArrayList<>();
    private final Set<String> processedRewardTransactions = new java.util.HashSet<>();

    private BukkitTask tickTask;

    public AuctionHouseService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "casino_action");
        this.listingKey = new NamespacedKey(plugin, "casino_listing");
        loadState();
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAuctions, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (AuctionListing listing : new ArrayList<>(listings.values())) {
            if (!listing.simulated) {
                returnListingToSeller(listing);
            }
        }
        listings.clear();
        saveState();
    }

    public int getCoinsBalance(UUID playerId) {
        return getSilver(playerId);
    }

    public int getSilverBalance(UUID playerId) {
        return getSilver(playerId);
    }

    public Map<UUID, Integer> silverBalancesSnapshot() {
        return new HashMap<>(silver);
    }

    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }

    public boolean adminGiveCoins(Player target, int amount) {
        if (target == null || amount <= 0) {
            return false;
        }
        return adminGiveSilver(target, amount);
    }

    public boolean adminGiveSilver(Player target, int amount) {
        if (target == null || amount <= 0) {
            return false;
        }
        addSilver(target.getUniqueId(), amount);
        saveState();
        return true;
    }

    public boolean grantSilver(UUID playerId, int amount, String reason) {
        if (playerId == null || amount <= 0) {
            return false;
        }
        addSilver(playerId, amount);
        saveState();
        String source = (reason == null || reason.isBlank()) ? "REWARD" : reason;
        logEconomy(source, resolvePlayerName(playerId, playerId.toString()) + " +" + amount + " Coins");
        return true;
    }

    public boolean grantCoinsOnce(UUID playerId, int amount, String transactionId, String reason) {
        if (playerId == null || amount <= 0 || transactionId == null || transactionId.isBlank()) {
            return false;
        }
        if (processedRewardTransactions.contains(transactionId)) {
            return true;
        }
        addSilver(playerId, amount);
        processedRewardTransactions.add(transactionId);
        saveState();
        String source = reason == null || reason.isBlank() ? "REWARD" : reason;
        logEconomy(source, resolvePlayerName(playerId, playerId.toString()) + " +" + amount
            + " Coins transaction=" + transactionId);
        return true;
    }

    public boolean setSilver(UUID playerId, int amount, String reason) {
        if (playerId == null || amount < 0) {
            return false;
        }
        silver.put(playerId, amount);
        refreshWalletSidebar(playerId);
        saveState();
        String source = (reason == null || reason.isBlank()) ? "ADMIN" : reason;
        logEconomy(source, resolvePlayerName(playerId, playerId.toString()) + " Coins set to " + amount);
        return true;
    }

    public int silverBalance(UUID playerId) {
        return playerId == null ? 0 : getSilver(playerId);
    }

    public PayResult payCoins(Player from, Player to, int amount) {
        return paySilver(from, to, amount);
    }

    public PayResult paySilver(Player from, Player to, int amount) {
        if (from == null || to == null) {
            return PayResult.INVALID_TARGET;
        }
        if (from.getUniqueId().equals(to.getUniqueId())) {
            return PayResult.SELF;
        }
        if (amount <= 0) {
            return PayResult.INVALID_AMOUNT;
        }
        if (!takeSilver(from.getUniqueId(), amount)) {
            return PayResult.INSUFFICIENT;
        }
        addSilver(to.getUniqueId(), amount);
        saveState();
        logEconomy("PAY", from.getName() + " -> " + to.getName() + " amount=" + amount + " Coins");
        return PayResult.OK;
    }

    public List<String> recentEconomyLogs(int limit) {
        if (economyLog.isEmpty()) {
            return List.of();
        }
        int max = Math.max(1, limit);
        int start = Math.max(0, economyLog.size() - max);
        return new ArrayList<>(economyLog.subList(start, economyLog.size()));
    }

    public int simulateMarket(String rawCategory, int amount) {
        BrowseCategory requested = BrowseCategory.fromCommand(rawCategory);
        if (requested == null || requested == BrowseCategory.ENDING_SOON || requested == BrowseCategory.NEWEST) {
            requested = BrowseCategory.MIXED;
        }
        int count = Math.max(1, Math.min(250, amount));
        for (int i = 0; i < count; i++) {
            SimulatedMarketItem item = createSimulatedMarketItem(requested);
            AuctionListing listing = new AuctionListing(
                UUID.randomUUID(),
                fakeSellerId(item.sellerName),
                item.sellerName,
                item.stack,
                item.cosmeticId,
                item.price,
                null,
                null,
                0,
                System.currentTimeMillis() + item.durationMs,
                true,
                item.category,
                item.rarity
            );
            listings.put(listing.id, listing);
        }
        saveState();
        logEconomy("AH_SIMULATE", "generated " + count + " fake listing(s) category=" + requested.commandName);
        refreshOpenBrowseMenus();
        return count;
    }

    public int clearSimulatedListings() {
        List<UUID> fakeIds = listings.values().stream()
            .filter(listing -> listing.simulated)
            .map(listing -> listing.id)
            .toList();
        for (UUID id : fakeIds) {
            listings.remove(id);
        }
        saveState();
        logEconomy("AH_CLEAR_FAKE", "removed " + fakeIds.size() + " fake listing(s)");
        refreshOpenBrowseMenus();
        return fakeIds.size();
    }

    public int refreshSimulatedMarket() {
        clearSimulatedListings();
        return simulateMarket("mixed", 50);
    }

    public boolean handleCommand(Player player, String[] args) {
        if (args.length == 0) {
            openBrowseMenu(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("sell")) {
            int amountIndex = args.length >= 2 && args[1].equalsIgnoreCase("cosmetics") ? 2 : 1;
            int startBid = args.length > amountIndex ? parsePositive(args[amountIndex], -1) : DEFAULT_START_BID;
            if (startBid < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /sell [startBidCoins]");
                return true;
            }
            openSellCosmeticsMenu(player, startBid);
            return true;
        }
        if (sub.equals("claim")) {
            claimItems(player);
            return true;
        }
        if (sub.equals("balance")) {
            if (args.length == 1) {
                player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.YELLOW + "Coins: " + getSilver(player.getUniqueId()));
                return true;
            }
            if (!player.hasPermission("hgloot.casino.admin")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to view other balances.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found online: " + args[1]);
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.AQUA + target.getName() + " -> "
                + ChatColor.YELLOW + "Coins: " + getSilver(target.getUniqueId()));
            return true;
        }
        if (sub.equals("addcoins") || sub.equals("addgold") || sub.equals("addsilver")) {
            if (!player.hasPermission("hgloot.casino.admin")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to add Coins.");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /ah addcoins <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found online: " + args[1]);
                return true;
            }
            int amount = parsePositive(args[2], -1);
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "Amount must be > 0.");
                return true;
            }
            addSilver(target.getUniqueId(), amount);
            saveState();
            logEconomy("ADMIN_ADD_SILVER", player.getName() + " -> " + target.getName() + " amount=" + amount + " Coins");
            player.sendMessage(ChatColor.GREEN + "Added " + amount + " Coins to " + target.getName() + ".");
            target.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "You received " + amount + " Coins.");
            return true;
        }
        if (sub.equals("audit")) {
            if (!player.hasPermission("hgloot.casino.admin")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to audit Auction House state.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /ah audit <player>");
                return true;
            }
            UUID targetId = resolvePlayerId(args[1]);
            if (targetId == null) {
                player.sendMessage(ChatColor.RED + "Could not resolve player: " + args[1]);
                return true;
            }
            String targetName = resolvePlayerName(targetId, args[1]);
            long activeSelling = listings.values().stream().filter(l -> l.sellerId.equals(targetId)).count();
            long topBids = listings.values().stream().filter(l -> targetId.equals(l.topBidderId)).count();
            int pendingItems = pendingClaims.getOrDefault(targetId, List.of()).size();
            player.sendMessage(ChatColor.GOLD + "[Auction House Audit] " + ChatColor.AQUA + targetName);
            player.sendMessage(ChatColor.GRAY + "Coins: " + ChatColor.WHITE + getSilver(targetId));
            player.sendMessage(ChatColor.GRAY + "Pending claims: " + ChatColor.WHITE + pendingItems);
            player.sendMessage(ChatColor.GRAY + "Active listings (seller): " + ChatColor.WHITE + activeSelling);
            player.sendMessage(ChatColor.GRAY + "Active top bids: " + ChatColor.WHITE + topBids);
            logEconomy("AUDIT", player.getName() + " audited " + targetName + " (" + targetId + ")");
            return true;
        }
        if (sub.equals("open")) {
            openBrowseMenu(player, 0);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage:");
        player.sendMessage(ChatColor.GRAY + "/ah");
        player.sendMessage(ChatColor.GRAY + "/sell [startBidCoins] - list an owned cosmetic");
        player.sendMessage(ChatColor.GRAY + "/ah claim");
        player.sendMessage(ChatColor.GRAY + "/ah balance [player]");
        player.sendMessage(ChatColor.GRAY + "/ah addcoins <player> <amount>");
        player.sendMessage(ChatColor.GRAY + "/ah audit <player>");
        return true;
    }

    public boolean handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("hgloot.casino.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage Auction House simulation.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /admin ah <simulate|clearfake|refreshmarket>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("simulate")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /admin ah simulate <category> <amount>");
                return true;
            }
            int amount = parsePositive(args[2], -1);
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "[Auction House] Amount must be > 0.");
                return true;
            }
            int generated = simulateMarket(args[1], amount);
            player.sendMessage(ChatColor.AQUA + "[Auction House] " + ChatColor.GREEN
                + "Generated " + generated + " simulated listing(s).");
            return true;
        }
        if (sub.equals("clearfake")) {
            int removed = clearSimulatedListings();
            player.sendMessage(ChatColor.AQUA + "[Auction House] " + ChatColor.YELLOW
                + "Cleared " + removed + " simulated listing(s).");
            return true;
        }
        if (sub.equals("refreshmarket")) {
            int generated = refreshSimulatedMarket();
            player.sendMessage(ChatColor.AQUA + "[Auction House] " + ChatColor.GREEN
                + "Refreshed simulated market with " + generated + " listing(s).");
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /admin ah <simulate|clearfake|refreshmarket>");
        return true;
    }

    public void openMainMenu(Player player) {
        openBrowseMenu(player, 0);
    }

    private void openSellCosmeticsMenu(Player player, int startBidCoins) {
        int safeStartBid = Math.max(1, startBidCoins);
        pendingSellStartBid.put(player.getUniqueId(), safeStartBid);

        CasinoMenuHolder holder = new CasinoMenuHolder("sell_cosmetics", 0);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.GOLD + "Sell Cosmetic");
        holder.setInventory(inv);
        frame(inv);

        if (menuService == null) {
            inv.setItem(22, plain(Material.BARRIER, ChatColor.RED + "Cosmetics service unavailable"));
            inv.setItem(49, button(Material.ARROW, ChatColor.RED + "Back", "casino_browse"));
            player.openInventory(inv);
            return;
        }

        List<String> ownedCosmetics = menuService.getAuctionSellableCosmeticIds(player.getUniqueId());
        List<Integer> slots = listingSlots();
        int placed = 0;
        for (String cosmeticId : ownedCosmetics) {
            if (placed >= slots.size()) {
                break;
            }
            ItemStack icon = menuService.createCosmeticAuctionIcon(cosmeticId);
            if (icon == null || icon.getType() == Material.AIR) {
                continue;
            }
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Start Bid");
                lore.add(ChatColor.GOLD + String.valueOf(safeStartBid) + ChatColor.GRAY + " Coins");
                lore.add("");
                lore.add(ChatColor.GREEN + "Click to list");
                meta.setLore(lightGrayLore(lore));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "casino_sell_cosmetic:" + cosmeticId);
                icon.setItemMeta(meta);
            }
            inv.setItem(slots.get(placed++), icon);
        }

        if (placed == 0) {
            inv.setItem(22, plain(Material.GRAY_DYE, ChatColor.GRAY + "You have no cosmetics to sell"));
        }

        inv.setItem(49, button(Material.ARROW, ChatColor.RED + "Back", "casino_browse"));
        inv.setItem(4, plain(Material.NAME_TAG, ChatColor.AQUA + "Start Bid: " + ChatColor.WHITE + safeStartBid + " Coins"));
        player.openInventory(inv);
    }

    private void openBrowseMenu(Player player, int page) {
        BrowseCategory category = browseCategories.getOrDefault(player.getUniqueId(), BrowseCategory.ALL);
        long listingCount = listings.values().stream().filter(category::matches).count();
        int pageSize = listingSlots().size();
        int lastPage = Math.max(0, (int) ((listingCount - 1L) / pageSize));
        int safePage = Math.max(0, Math.min(page, lastPage));
        boolean hasPreviousPage = safePage > 0;
        boolean hasNextPage = safePage < lastPage;
        CasinoMenuHolder holder = new CasinoMenuHolder("browse", safePage);
        Inventory inv = Bukkit.createInventory(holder, 54,
            GuiTitleFactory.auctionHouseTitle(hasPreviousPage, hasNextPage));
        holder.setInventory(inv);
        populateBrowseInventory(player, inv, safePage);
        player.openInventory(inv);
    }

    private void populateBrowseInventory(Player player, Inventory inv, int page) {
        BrowseSort sort = browseSorts.getOrDefault(player.getUniqueId(), BrowseSort.ENDING_SOON);
        BrowseCategory category = browseCategories.getOrDefault(player.getUniqueId(), BrowseCategory.ALL);
        Comparator<AuctionListing> comparator = switch (sort) {
            case HIGHEST_PRICE -> Comparator.comparingInt((AuctionListing a) -> a.currentBidCoinsEq).reversed()
                .thenComparingLong(a -> a.endsAtMs);
            case LOWEST_PRICE -> Comparator.comparingInt((AuctionListing a) -> a.currentBidCoinsEq)
                .thenComparingLong(a -> a.endsAtMs);
            case ENDING_SOON -> Comparator.comparingLong((AuctionListing a) -> a.endsAtMs)
                .thenComparing(Comparator.comparingInt((AuctionListing a) -> a.currentBidCoinsEq).reversed());
            case ALPHABETICAL -> Comparator.comparing(this::listingSortName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(a -> a.endsAtMs);
        };
        List<AuctionListing> active = listings.values().stream()
            .filter(category::matches)
            .sorted(comparator)
            .toList();
        int safePage = Math.max(0, page);

        inv.clear();

        List<Integer> slots = listingSlots();
        int start = safePage * slots.size();
        int end = Math.min(active.size(), start + slots.size());
        long now = System.currentTimeMillis();

        for (int i = start; i < end; i++) {
            AuctionListing listing = active.get(i);
            int slot = slots.get(i - start);
            long secLeft = Math.max(0L, (listing.endsAtMs - now) / 1000L);
            int nextCoinsBid = listing.currentBidCoinsEq + BID_STEP_COINS;

            ItemStack display = listing.item.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(listing.rarity.color + listing.rarity.displayName + ChatColor.GRAY + " " + listing.category.displayName);
                lore.add("");
                lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + listing.sellerName);
                if (listing.simulated) {
                    lore.add(ChatColor.DARK_GRAY + "Market listing");
                }
                lore.add("");
                lore.add(ChatColor.GRAY + "Current Bid: " + ChatColor.GOLD + listing.currentBidCoinsEq + ChatColor.GRAY + " Coins");
                lore.add(ChatColor.GRAY + "Next Bid: " + nextCoinsBid + " Coins");
                lore.add(ChatColor.GRAY + "Expires In: " + ChatColor.YELLOW + formatSeconds(secLeft));
                lore.add(ChatColor.GRAY + "Top Bidder:");
                if (listing.topBidderName == null) {
                    lore.add(ChatColor.DARK_GRAY + "None");
                } else {
                    lore.add(ChatColor.AQUA + listing.topBidderName + ChatColor.GRAY
                        + " (" + listing.topBidAmount + " " + listing.topBidCurrency.label + ")");
                }
                lore.add("");
                lore.add(ChatColor.YELLOW + "Click to inspect/bid");
                meta.setLore(lightGrayLore(lore));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "casino_bid");
                meta.getPersistentDataContainer().set(listingKey, PersistentDataType.STRING, listing.id.toString());
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
        }

        if (safePage > 0) {
            inv.setItem(45, invisiblePageButton(ChatColor.YELLOW + "Previous Page", "casino_page:" + (safePage - 1)));
        }
        if (end < active.size()) {
            inv.setItem(53, invisiblePageButton(ChatColor.YELLOW + "Next Page", "casino_page:" + (safePage + 1)));
        }
        inv.setItem(49, button(Material.BARRIER, ChatColor.RED + "Close", "casino_close"));
        inv.setItem(46, sortButton(sort));
        inv.setItem(47, categoryButton(category));
        inv.setItem(50, button(Material.SPYGLASS, ChatColor.AQUA + "Refresh", "casino_refresh"));
        inv.setItem(52, button(Material.KNOWLEDGE_BOOK, ChatColor.AQUA + "Auction House Help", "casino_help",
            ChatColor.DARK_GRAY + "━━━━━━━━",
            ChatColor.GRAY + "/shard open " + ChatColor.WHITE + "Open a cosmetic Shard reward.",
            ChatColor.GRAY + "/sell " + ChatColor.WHITE + "List owned cosmetics.",
            ChatColor.GRAY + "/ah claim " + ChatColor.WHITE + "Claim won or expired items."));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) {
            return;
        }

        if (action.equals("casino_browse")) {
            HydraSounds.playClick(player);
            openBrowseMenu(player, 0);
            return;
        }
        if (action.equals("casino_sell")) {
            HydraSounds.playClick(player);
            openSellCosmeticsMenu(player, pendingSellStartBid.getOrDefault(player.getUniqueId(), DEFAULT_START_BID));
            return;
        }
        if (action.equals("casino_claim")) {
            claimItems(player);
            openBrowseMenu(player, 0);
            return;
        }
        if (action.equals("casino_back_main")) {
            HydraSounds.playClick(player);
            openBrowseMenu(player, 0);
            return;
        }
        if (action.equals("casino_close")) {
            HydraSounds.playClick(player);
            player.closeInventory();
            return;
        }
        if (action.equals("casino_help")) {
            HydraSounds.playClick(player);
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Auction House Help");
            player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━");
            player.sendMessage(ChatColor.GRAY + "/shard open " + ChatColor.WHITE + "Open a cosmetic Shard reward.");
            player.sendMessage(ChatColor.GRAY + "/sell " + ChatColor.WHITE + "List owned cosmetics.");
            player.sendMessage(ChatColor.GRAY + "/ah claim " + ChatColor.WHITE + "Claim won or expired items.");
            return;
        }
        if (action.equals("casino_refresh")) {
            HydraSounds.playClick(player);
            openBrowseMenu(player, holder.page());
            return;
        }
        if (action.equals("casino_sort_cycle")) {
            BrowseSort current = browseSorts.getOrDefault(player.getUniqueId(), BrowseSort.ENDING_SOON);
            BrowseSort next = event.isRightClick() ? current.previous() : current.next();
            browseSorts.put(player.getUniqueId(), next);
            HydraSounds.playClick(player);
            openBrowseMenu(player, holder.page());
            return;
        }
        if (action.equals("casino_category_cycle")) {
            BrowseCategory current = browseCategories.getOrDefault(player.getUniqueId(), BrowseCategory.ALL);
            BrowseCategory next = event.isRightClick() ? current.previous() : current.next();
            browseCategories.put(player.getUniqueId(), next);
            HydraSounds.playClick(player);
            openBrowseMenu(player, 0);
            return;
        }
        if (action.startsWith("casino_sort:")) {
            BrowseSort selected = BrowseSort.fromAction(action.substring("casino_sort:".length()));
            if (selected != null) {
                browseSorts.put(player.getUniqueId(), selected);
                HydraSounds.playClick(player);
                openBrowseMenu(player, 0);
            }
            return;
        }
        if (action.startsWith("casino_page:")) {
            int page = parsePositive(action.substring("casino_page:".length()), 0);
            HydraSounds.playClick(player);
            openBrowseMenu(player, Math.max(0, page));
            return;
        }
        if (action.startsWith("casino_sell_cosmetic:")) {
            int startBid = pendingSellStartBid.getOrDefault(player.getUniqueId(), DEFAULT_START_BID);
            String cosmeticId = action.substring("casino_sell_cosmetic:".length()).trim().toLowerCase(Locale.ROOT);
            listCosmetic(player, cosmeticId, startBid);
            openBrowseMenu(player, 0);
            return;
        }
        if (action.equals("casino_bid")) {
            String rawId = pdc.get(listingKey, PersistentDataType.STRING);
            if (rawId == null) {
                return;
            }
            try {
                placeBid(player, UUID.fromString(rawId));
                openBrowseMenu(player, holder.page());
            } catch (IllegalArgumentException ignored) {
                player.sendMessage(ChatColor.RED + "[Auction House] Invalid listing ID.");
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getSilver(player.getUniqueId());
        updateWalletSidebar(player);

        List<ItemStack> pending = pendingClaims.get(player.getUniqueId());
        if (pending != null && !pending.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.YELLOW
                + "You have " + pending.size() + " item(s) to claim. Use /ah claim.");
        }
    }

    public boolean isCasinoMenu(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof CasinoMenuHolder;
    }

    private void listCosmetic(Player player, String cosmeticId, int startBidSilver) {
        if (startBidSilver < 1) {
            player.sendMessage(ChatColor.RED + "[Auction House] Start bid must be > 0.");
            HydraSounds.playError(player);
            return;
        }
        if (menuService == null) {
            player.sendMessage(ChatColor.RED + "[Auction House] Cosmetics service unavailable.");
            HydraSounds.playError(player);
            return;
        }

        String displayName = menuService.getCosmeticDisplayName(cosmeticId);
        if (displayName == null) {
            player.sendMessage(ChatColor.RED + "[Auction House] Invalid cosmetic selection.");
            HydraSounds.playError(player);
            return;
        }
        if (!menuService.isAuctionSellableCosmetic(cosmeticId)) {
            player.sendMessage(ChatColor.RED + "[Auction House] That cosmetic cannot be listed.");
            HydraSounds.playError(player);
            return;
        }
        if (!menuService.removeCosmeticOwnershipForAuction(player.getUniqueId(), cosmeticId)) {
            player.sendMessage(ChatColor.RED + "[Auction House] You do not own that cosmetic.");
            HydraSounds.playError(player);
            return;
        }

        ItemStack listingItem = menuService.createCosmeticAuctionIcon(cosmeticId);
        if (listingItem == null || listingItem.getType() == Material.AIR) {
            menuService.addCosmeticOwnership(player.getUniqueId(), cosmeticId);
            player.sendMessage(ChatColor.RED + "[Auction House] Could not create listing icon for that cosmetic.");
            HydraSounds.playError(player);
            return;
        }
        BrowseCategory category = browseCategoryForCosmetic(cosmeticId);
        MarketRarity rarity = marketRarityForCosmetic(cosmeticId);

        AuctionListing listing = new AuctionListing(
            UUID.randomUUID(),
            player.getUniqueId(),
            player.getName(),
            listingItem,
            cosmeticId,
            startBidSilver,
            null,
            null,
            0,
            System.currentTimeMillis() + AUCTION_DURATION_MS,
            false,
            category,
            rarity
        );
        listings.put(listing.id, listing);
        pendingSellStartBid.remove(player.getUniqueId());
        logEconomy("LISTING_CREATE", player.getName() + " listed cosmetic=" + cosmeticId
            + " id=" + listing.id + " start=" + startBidSilver + " Coins");
        player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "Listed " + displayName + " for "
            + startBidSilver + " Coins. Auction ends in 30m.");
        HydraSounds.playSuccess(player);
    }

    private BrowseCategory browseCategoryForCosmetic(String cosmeticId) {
        String categoryId = menuService == null ? null : menuService.getCosmeticAuctionCategoryId(cosmeticId);
        if (categoryId == null) {
            return BrowseCategory.COSMETICS;
        }
        return switch (categoryId.toLowerCase(Locale.ROOT)) {
            case "legendary_skins" -> BrowseCategory.WEAPON_SKINS;
            case "projectile_trails" -> BrowseCategory.TRAILS;
            case "kill_effects", "kill_messages", "armor_trims", "titles" -> BrowseCategory.EFFECTS;
            case "hats" -> BrowseCategory.COSMETICS;
            default -> BrowseCategory.COSMETICS;
        };
    }

    private MarketRarity marketRarityForCosmetic(String cosmeticId) {
        String rarity = menuService == null ? null : menuService.getCosmeticRarity(cosmeticId);
        if (rarity == null || rarity.isBlank()) {
            return MarketRarity.RARE;
        }
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common", "default" -> MarketRarity.COMMON;
            case "rare" -> MarketRarity.RARE;
            case "epic" -> MarketRarity.EPIC;
            case "legendary", "prestige" -> MarketRarity.LEGENDARY;
            case "limited", "event" -> MarketRarity.LIMITED;
            default -> MarketRarity.RARE;
        };
    }

    private void placeBid(Player player, UUID listingId) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null) {
            player.sendMessage(ChatColor.RED + "[Auction House] Listing no longer exists.");
            HydraSounds.playError(player);
            return;
        }
        if (listing.sellerId.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Auction House] You cannot bid on your own listing.");
            HydraSounds.playError(player);
            return;
        }

        int nextBidCoins = listing.currentBidCoinsEq + BID_STEP_COINS;
        int cost = nextBidCoins;
        boolean paid = takeSilver(player.getUniqueId(), cost);
        if (!paid) {
            player.sendMessage(ChatColor.RED + "[Auction House] Not enough Coins. Need " + cost + " Coins.");
            HydraSounds.playError(player);
            return;
        }

        if (listing.topBidderId != null && listing.topBidCurrency != null) {
            int refundAmount = listing.topBidAmount;
            addSilver(listing.topBidderId, refundAmount);
            logEconomy("BID_REFUND", "listing=" + listing.id + " refunded " + refundAmount + " "
                + listing.topBidCurrency.label.toLowerCase(Locale.ROOT)
                + " to " + listing.topBidderName);
            Player previous = Bukkit.getPlayer(listing.topBidderId);
            if (previous != null && previous.isOnline()) {
                previous.sendMessage(ChatColor.YELLOW + "[Auction House] You were outbid. Refunded "
                    + refundAmount + " " + listing.topBidCurrency.label.toLowerCase(Locale.ROOT) + ".");
            }
        }

        listing.topBidderId = player.getUniqueId();
        listing.topBidderName = player.getName();
        listing.topBidCurrency = BidCurrency.SILVER;
        listing.topBidAmount = cost;
        listing.currentBidCoinsEq = nextBidCoins;

        long now = System.currentTimeMillis();
        if ((listing.endsAtMs - now) < 30_000L) {
            listing.endsAtMs = now + 30_000L;
        }
        saveState();
        logEconomy("BID_PLACE", player.getName() + " bid " + cost + " Coins"
            + " on listing=" + listing.id + " next=" + nextBidCoins + " Coins");

        player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "Bid placed: "
            + cost + " Coins.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.15F);
    }

    private void claimItems(Player player) {
        List<ItemStack> claim = pendingClaims.get(player.getUniqueId());
        if (claim == null || claim.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "[Auction House] No pending items to claim.");
            HydraSounds.playError(player);
            return;
        }

        if (!canFitAllClaims(player, claim)) {
            player.sendMessage(ChatColor.RED + "[Auction House] Inventory full. Clear at least one slot before claiming.");
            logEconomy("CLAIM_BLOCKED", player.getName() + " blocked, pending=" + claim.size() + " (inventory full)");
            HydraSounds.playError(player);
            return;
        }

        int delivered = 0;
        for (ItemStack item : claim) {
            player.getInventory().addItem(item);
            delivered++;
        }
        pendingClaims.remove(player.getUniqueId());
        saveState();
        logEconomy("CLAIM_SUCCESS", player.getName() + " claimed " + delivered + " item(s)");
        player.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "Claimed " + delivered + " item(s).");
        HydraSounds.playSuccess(player);
    }

    private void tickAuctions() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        for (AuctionListing listing : listings.values()) {
            if (now >= listing.endsAtMs) {
                expired.add(listing.id);
            }
        }
        for (UUID id : expired) {
            AuctionListing listing = listings.remove(id);
            if (listing != null) {
                finalizeListing(listing);
            }
        }
        refreshOpenBrowseMenus();
    }

    private void refreshOpenBrowseMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                continue;
            }
            if (!(view.getTopInventory().getHolder() instanceof CasinoMenuHolder holder)) {
                continue;
            }
            if (!"browse".equalsIgnoreCase(holder.view())) {
                continue;
            }
            populateBrowseInventory(player, view.getTopInventory(), holder.page());
            player.updateInventory();
        }
    }

    private void finalizeListing(AuctionListing listing) {
        if (listing.topBidderId == null || listing.topBidCurrency == null) {
            if (!listing.simulated) {
                returnListingToSeller(listing);
            }
            logEconomy("LISTING_EXPIRE", "listing=" + listing.id + " no bids, returned to " + listing.sellerName);
            Player seller = listing.simulated ? null : Bukkit.getPlayer(listing.sellerId);
            if (seller != null && seller.isOnline()) {
                String returnedType = listing.cosmeticId == null ? "item" : "cosmetic";
                seller.sendMessage(ChatColor.YELLOW + "[Auction House] No bids. Your " + returnedType + " was returned.");
            }
            saveState();
            return;
        }

        if (!listing.simulated) {
            addSilver(listing.sellerId, listing.topBidAmount);
        }
        if (listing.cosmeticId != null) {
            boolean granted = menuService != null && menuService.addCosmeticOwnership(listing.topBidderId, listing.cosmeticId);
            if (!granted) {
                queueClaim(listing.topBidderId, listing.item);
            }
        } else {
            queueClaim(listing.topBidderId, listing.item);
        }
        logEconomy("LISTING_SOLD", "listing=" + listing.id + " seller=" + listing.sellerName
            + " winner=" + listing.topBidderName + " amount=" + listing.topBidAmount + " "
            + listing.topBidCurrency.label.toLowerCase(Locale.ROOT));

        Player winner = Bukkit.getPlayer(listing.topBidderId);
        if (winner != null && winner.isOnline()) {
            if (listing.cosmeticId != null) {
                String cosmeticName = menuService == null ? listing.cosmeticId : menuService.getCosmeticDisplayName(listing.cosmeticId);
                if (cosmeticName == null) {
                    cosmeticName = listing.cosmeticId;
                }
                winner.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "You won cosmetic "
                    + cosmeticName + " for " + listing.topBidAmount + " "
                    + listing.topBidCurrency.label.toLowerCase(Locale.ROOT) + ChatColor.GRAY + ".");
            } else {
                winner.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "You won an auction for "
                    + listing.topBidAmount + " " + listing.topBidCurrency.label.toLowerCase(Locale.ROOT)
                    + ChatColor.GRAY + ". Use /ah claim.");
            }
        }
        Player seller = listing.simulated ? null : Bukkit.getPlayer(listing.sellerId);
        if (seller != null && seller.isOnline()) {
            String soldType = listing.cosmeticId == null ? "item" : "cosmetic";
            seller.sendMessage(ChatColor.GOLD + "[Auction House] " + ChatColor.GREEN + "Your " + soldType + " sold for "
                + listing.topBidAmount + " " + listing.topBidCurrency.label.toLowerCase(Locale.ROOT) + ".");
        }
        saveState();
    }

    private void returnListingToSeller(AuctionListing listing) {
        if (listing.cosmeticId != null && menuService != null) {
            if (menuService.addCosmeticOwnership(listing.sellerId, listing.cosmeticId)) {
                return;
            }
        }
        queueClaim(listing.sellerId, listing.item);
    }

    private void queueClaim(UUID playerId, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        pendingClaims.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(item.clone());
    }

    private int getSilver(UUID playerId) {
        return silver.computeIfAbsent(playerId, ignored -> DEFAULT_SILVER);
    }

    private void addSilver(UUID playerId, int amount) {
        if (amount <= 0) {
            return;
        }
        silver.put(playerId, clampSilver(getSilver(playerId), amount));
        refreshWalletSidebar(playerId);
    }

    private UUID resolvePlayerId(String rawName) {
        Player online = Bukkit.getPlayerExact(rawName);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(rawName)) {
                return player.getUniqueId();
            }
        }
        for (org.bukkit.OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(rawName)) {
                return offline.getUniqueId();
            }
        }
        return null;
    }

    private String resolvePlayerName(UUID playerId, String fallback) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        if (offline != null && offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return fallback;
    }

    private void logEconomy(String type, String message) {
        String line = "[" + LocalDateTime.now().format(LOG_TIME_FORMAT) + "] " + type + " | " + message;
        economyLog.add(line);
        while (economyLog.size() > ECON_LOG_LIMIT) {
            economyLog.remove(0);
        }
        plugin.getLogger().info("[CasinoLog] " + line);
    }

    private boolean takeSilver(UUID playerId, int amount) {
        if (amount <= 0) {
            return true;
        }
        int current = getSilver(playerId);
        if (current < amount) {
            return false;
        }
        silver.put(playerId, current - amount);
        refreshWalletSidebar(playerId);
        return true;
    }

    private void loadState() {
        Map<UUID, Integer> legacyCoins = new HashMap<>();
        ConfigurationSection coinsSection = plugin.getConfig().getConfigurationSection(CONFIG_COINS);
        if (coinsSection != null) {
            for (String key : coinsSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int value = Math.max(0, coinsSection.getInt(key, 0));
                    if (value > 0) {
                        legacyCoins.put(playerId, value);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection silverSection = plugin.getConfig().getConfigurationSection(CONFIG_SILVER);
        if (silverSection != null) {
            for (String key : silverSection.getKeys(false)) {
                try {
                    silver.put(UUID.fromString(key), Math.max(0, silverSection.getInt(key, DEFAULT_SILVER)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // One-time migration path: legacy coin wallets are converted to silver and coins are removed.
        for (Map.Entry<UUID, Integer> entry : legacyCoins.entrySet()) {
            int migrated = coinsToSilver(entry.getValue());
            if (migrated <= 0) {
                continue;
            }
            UUID playerId = entry.getKey();
            silver.put(playerId, clampSilver(getSilver(playerId), migrated));
        }

        ConfigurationSection pendingSection = plugin.getConfig().getConfigurationSection(CONFIG_PENDING);
        if (pendingSection != null) {
            for (String key : pendingSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<?> raw = plugin.getConfig().getList(CONFIG_PENDING + "." + key);
                    if (raw == null || raw.isEmpty()) {
                        continue;
                    }
                    List<ItemStack> items = new ArrayList<>();
                    for (Object obj : raw) {
                        if (obj instanceof ItemStack stack && stack.getType() != Material.AIR) {
                            items.add(stack);
                        }
                    }
                    if (!items.isEmpty()) {
                        pendingClaims.put(id, items);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        processedRewardTransactions.addAll(plugin.getConfig().getStringList(CONFIG_REWARD_TRANSACTIONS));
    }

    private void saveState() {
        plugin.getConfig().set(CONFIG_COINS, null);

        plugin.getConfig().set(CONFIG_SILVER, null);
        for (Map.Entry<UUID, Integer> entry : silver.entrySet()) {
            plugin.getConfig().set(CONFIG_SILVER + "." + entry.getKey(), entry.getValue());
        }

        plugin.getConfig().set(CONFIG_PENDING, null);
        for (Map.Entry<UUID, List<ItemStack>> entry : pendingClaims.entrySet()) {
            plugin.getConfig().set(CONFIG_PENDING + "." + entry.getKey(), entry.getValue());
        }
        plugin.getConfig().set(CONFIG_REWARD_TRANSACTIONS, new ArrayList<>(processedRewardTransactions));
        plugin.saveConfig();
    }

    private int clampSilver(int current, int delta) {
        long total = (long) Math.max(0, current) + Math.max(0L, (long) delta);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private void refreshWalletSidebar(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            updateWalletSidebar(player);
        }
    }

    private void updateWalletSidebar(Player player) {
        if (queueService != null) {
            queueService.refreshSidebar(player);
            return;
        }
        // The Hydra HUD owns sidebar rendering. Avoid creating a second auction
        // objective here, because that can fight queue/lobby/chamber scoreboards.
    }

    private void reapplyNpcNameHiding(Scoreboard board) {
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.npcService() == null) {
            return;
        }
        hydra.npcService().applyHiddenNameTeam(board);
    }

    private List<String> lightGrayLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) return List.of();
        List<String> normalized = new ArrayList<>(lore.size());
        for (String line : lore) {
            String text = ChatColor.stripColor(line == null ? "" : line);
            normalized.add(text == null || text.isEmpty() ? "" : ChatColor.GRAY + text);
        }
        return normalized;
    }

    private ItemStack button(Material material, String name, String action, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            List<String> lines = new ArrayList<>(lore.length);
            for (String line : lore) {
                lines.add(ChatColor.GRAY + line);
            }
            meta.setLore(lightGrayLore(lines));
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack plain(Material material, String name) {
        return plain(material, name, new String[0]);
    }

    private ItemStack plain(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>(lore.length);
                for (String line : lore) {
                    lines.add(ChatColor.GRAY + line);
                }
                meta.setLore(lightGrayLore(lines));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack sortButton(BrowseSort active) {
        ItemStack stack = new ItemStack(Material.HOPPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName(ChatColor.GREEN + "Sort");
        List<String> lore = new ArrayList<>();
        for (BrowseSort sort : BrowseSort.values()) {
            String line = (sort == active ? ChatColor.AQUA + "> " : ChatColor.GRAY.toString()) + sort.displayName;
            lore.add(line);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Right-Click to go backwards!");
        lore.add(ChatColor.GRAY + "Click to switch sort!");
        meta.setLore(lightGrayLore(lore));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "casino_sort_cycle");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack categoryButton(BrowseCategory active) {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName(ChatColor.AQUA + "Category");
        List<String> lore = new ArrayList<>();
        for (BrowseCategory category : BrowseCategory.browsableValues()) {
            String line = (category == active ? ChatColor.AQUA + "> " : ChatColor.GRAY.toString()) + category.displayName;
            lore.add(line);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Right-Click to go backwards!");
        lore.add(ChatColor.GRAY + "Click to switch category!");
        meta.setLore(lightGrayLore(lore));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "casino_category_cycle");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private void frame(Inventory inv) {
        ItemStack filler = plain(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + " ");
        int size = inv.getSize();
        int rows = size / 9;

        for (int col = 0; col < 9; col++) {
            inv.setItem(col, filler);
            inv.setItem((rows - 1) * 9 + col, filler);
        }
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }

    private ItemStack invisiblePageButton(String name, String action) {
        ItemStack stack = button(Material.PAPER, name, action);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("hydra", "gui/negative_space"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<Integer> listingSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private String formatSeconds(long seconds) {
        long minutes = seconds / 60L;
        long remain = seconds % 60L;
        return minutes + "m " + remain + "s";
    }

    private int parsePositive(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int coinsToSilver(int coins) {
        if (coins <= 0) {
            return 0;
        }
        return Math.max(1, (coins + LEGACY_COINS_PER_SILVER - 1) / LEGACY_COINS_PER_SILVER);
    }

    private String listingSortName(AuctionListing listing) {
        if (listing == null || listing.item == null || listing.item.getType() == Material.AIR) {
            return "";
        }
        ItemMeta meta = listing.item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && !stripped.isBlank()) {
                return stripped;
            }
        }
        return listing.item.getType().name();
    }

    private SimulatedMarketItem createSimulatedMarketItem(BrowseCategory requested) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<MarketSeed> pool = simulatedMarketSeeds(requested);
        MarketSeed seed = randomFrom(pool);
        BrowseCategory category = seed.category;
        MarketRarity rarity = seed.rarity;
        int price = rollPrice(category, rarity);
        long durationMs = rollDurationMs();
        String seller = randomFrom(List.of(
            "Ravik", "Kairo", "Nyro", "Vanta", "Aetherial", "Solace", "Stonee", "Crypte", "Veyr",
            "Sable", "Ardent", "Myra", "Kael", "Vire", "Noct", "Seren", "Auri", "Vale"
        ));
        ItemStack stack = createSimulatedAuctionItem(seed, price, durationMs, seller);
        return new SimulatedMarketItem(stack, seed.cosmeticId, category, rarity, seller, price, durationMs);
    }

    private ItemStack createSimulatedAuctionItem(MarketSeed seed, int price, long durationMs, String seller) {
        ItemStack stack = null;
        if (seed.cosmeticId != null && menuService != null) {
            stack = menuService.createCosmeticAuctionIcon(seed.cosmeticId);
        }
        if (stack == null) {
            stack = new ItemStack(seed.material);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String display = seed.displayName;
            if (seed.cosmeticId != null && menuService != null) {
                String resolved = menuService.getCosmeticDisplayName(seed.cosmeticId);
                if (resolved != null && !resolved.isBlank()) {
                    display = resolved;
                }
            }
            if (seed.category == BrowseCategory.SHARDS && "Prestige Shard".equals(seed.displayName)) {
                meta.setItemModel(new NamespacedKey("hydra", "shard"));
                CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
                customModelData.setStrings(List.of("hydra:shard"));
                meta.setCustomModelDataComponent(customModelData);
            }
            meta.setDisplayName(seed.rarity.color + display);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + seed.rarity.displayName + " " + seed.category.displayName);
            lore.add("");
            lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + seller);
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GREEN + price + " Coins");
            lore.add(ChatColor.GRAY + "Expires In: " + ChatColor.YELLOW + formatSeconds(durationMs / 1000L));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to inspect");
            meta.setLore(lightGrayLore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<MarketSeed> simulatedMarketSeeds(BrowseCategory requested) {
        List<MarketSeed> seeds = new ArrayList<>(List.of(
            seed("trail_blackstatic", "Blackstatic", BrowseCategory.TRAILS, MarketRarity.EPIC, Material.BLACK_DYE),
            seed("trail_rift_ash", "Rift Ash", BrowseCategory.TRAILS, MarketRarity.RARE, Material.GUNPOWDER),
            seed("trail_deep_current", "Deep Current", BrowseCategory.TRAILS, MarketRarity.COMMON, Material.PRISMARINE_CRYSTALS),
            seed("trail_nullwake", "Nullwake", BrowseCategory.TRAILS, MarketRarity.RARE, Material.ENDER_PEARL),
            seed("trail_frost_mist", "Frost Mist", BrowseCategory.TRAILS, MarketRarity.COMMON, Material.SNOWBALL),
            seed("trail_tidewake", "Tidewake", BrowseCategory.TRAILS, MarketRarity.COMMON, Material.HEART_OF_THE_SEA),
            seed("trail_eclipse", "Eclipse Trail", BrowseCategory.LIMITED, MarketRarity.LIMITED, Material.NETHER_STAR),
            seed("trail_abyss_wake", "Abyss Wake", BrowseCategory.RARE, MarketRarity.LEGENDARY, Material.ECHO_SHARD),
            seed("trim_ascendant", "Ascendant Trim", BrowseCategory.RARE, MarketRarity.LEGENDARY, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
            seed("trim_hydra_crown", "Hydra Crown Trim", BrowseCategory.LIMITED, MarketRarity.LIMITED, Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
            seed("trim_kingsfall", "Kingsfall Trim", BrowseCategory.RARE, MarketRarity.EPIC, Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
            seed("trim_depthscarred", "Depthscarred Trim", BrowseCategory.COSMETICS, MarketRarity.RARE, Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE),
            seed("trim_venomwalker", "Venomwalker Trim", BrowseCategory.COSMETICS, MarketRarity.RARE, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE),
            seed("trim_voidglass", "Voidglass Trim", BrowseCategory.RARE, MarketRarity.EPIC, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
            seed("kill_effect_shatterfall", "Shatterfall", BrowseCategory.EFFECTS, MarketRarity.COMMON, Material.GLASS),
            seed("kill_effect_permafrost", "Permafrost", BrowseCategory.EFFECTS, MarketRarity.RARE, Material.BLUE_ICE),
            seed("kill_effect_glacial_bloom", "Glacial Bloom", BrowseCategory.EFFECTS, MarketRarity.RARE, Material.PACKED_ICE),
            seed("kill_effect_kingsfall", "Kingsfall", BrowseCategory.EFFECTS, MarketRarity.EPIC, Material.GOLD_INGOT),
            seed("kill_effect_golden_collapse", "Golden Collapse", BrowseCategory.EFFECTS, MarketRarity.LEGENDARY, Material.GOLD_BLOCK),
            seed("kill_effect_crownflare", "Crownflare", BrowseCategory.LIMITED, MarketRarity.LIMITED, Material.NETHER_STAR),
            seed("kill_message_shattered", "Shattered", BrowseCategory.COSMETICS, MarketRarity.COMMON, Material.IRON_SWORD),
            seed("kill_message_abyss", "Into the Abyss", BrowseCategory.COSMETICS, MarketRarity.COMMON, Material.ENDER_PEARL),
            seed("kill_message_beneath", "Beneath", BrowseCategory.COSMETICS, MarketRarity.COMMON, Material.DEEPSLATE),
            seed("kill_message_silenced", "Silenced", BrowseCategory.COSMETICS, MarketRarity.COMMON, Material.ECHO_SHARD),
            seed("kill_message_deep_burial", "Deep Burial", BrowseCategory.COSMETICS, MarketRarity.COMMON, Material.MUD),
            seed("kill_message_claimed", "Claimed", BrowseCategory.COSMETICS, MarketRarity.RARE, Material.PRISMARINE_SHARD),
            seed("kill_message_broken", "Broken", BrowseCategory.COSMETICS, MarketRarity.RARE, Material.ANVIL),
            seed("kill_message_vanished", "Vanished", BrowseCategory.RARE, MarketRarity.EPIC, Material.ENDER_EYE),
            seed("ghlochester_anime", "Ghlochester Anime", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.FISHING_ROD),
            seed("skeletal_barber_piglin", "Skeletal Barber Piglin", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.NETHERITE_SWORD),
            seed("frozen_barber", "Frozen Barber", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.NETHERITE_SWORD),
            seed("dwarven_bow_ocean", "Dwarf Bow Ocean", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.BOW),
            seed("sponge_saber_samurai", "Sponge Saber Samurai", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.DIAMOND_SWORD),
            seed("electric_conch_futuristic", "Electric Conch Futuristic", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.TRIDENT),
            seed("electric_conch_obsidian", "Electric Conch Obsidian", BrowseCategory.WEAPON_SKINS, MarketRarity.LEGENDARY, Material.TRIDENT),
            new MarketSeed(null, "Prestige Shard", BrowseCategory.SHARDS, MarketRarity.LEGENDARY, Material.AMETHYST_SHARD)
        ));
        if (!plugin.getConfig().getBoolean("economy.cosmetic_values.limited_purchasable", false)) {
            seeds.removeIf(seed -> seed.rarity == MarketRarity.LIMITED || seed.category == BrowseCategory.LIMITED);
        }
        BrowseCategory filter = requested == null ? BrowseCategory.MIXED : requested;
        if (filter == BrowseCategory.MIXED || filter == BrowseCategory.ALL || filter == BrowseCategory.NEWEST || filter == BrowseCategory.ENDING_SOON) {
            return seeds;
        }
        List<MarketSeed> filtered = seeds.stream()
            .filter(seed -> filter == BrowseCategory.RARE
                ? seed.rarity == MarketRarity.RARE || seed.rarity == MarketRarity.EPIC || seed.rarity == MarketRarity.LEGENDARY || seed.rarity == MarketRarity.LIMITED
                : seed.category == filter)
            .toList();
        return filtered.isEmpty() ? seeds : filtered;
    }

    private MarketSeed seed(String cosmeticId, String displayName, BrowseCategory category, MarketRarity rarity, Material material) {
        return new MarketSeed(cosmeticId, displayName, category, rarity, material);
    }

    private MarketRarity rollRarity(BrowseCategory category) {
        if (category == BrowseCategory.LIMITED) {
            return MarketRarity.LIMITED;
        }
        if (category == BrowseCategory.SHARDS) {
            return MarketRarity.LEGENDARY;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int roll = random.nextInt(100);
        if (roll < 48) {
            return MarketRarity.COMMON;
        }
        if (roll < 76) {
            return MarketRarity.RARE;
        }
        if (roll < 93) {
            return MarketRarity.EPIC;
        }
        return MarketRarity.LEGENDARY;
    }

    private int rollPrice(BrowseCategory category, MarketRarity rarity) {
        if (category == BrowseCategory.SHARDS) {
            return ThreadLocalRandom.current().nextInt(45_000, 120_001);
        }
        String key = rarity.name().toLowerCase(Locale.ROOT);
        int fallback = switch (rarity) {
            case COMMON -> 500;
            case RARE -> 1_000;
            case EPIC -> 2_500;
            case LEGENDARY -> 5_000;
            case LIMITED -> 10_000;
        };
        return Math.max(1, plugin.getConfig().getInt("economy.cosmetic_values." + key, fallback));
    }

    private long rollDurationMs() {
        return AUCTION_DURATION_MS;
    }

    private UUID fakeSellerId(String sellerName) {
        return UUID.nameUUIDFromBytes(("hydra-ah-fake:" + sellerName.toLowerCase(Locale.ROOT)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static <T> T randomFrom(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private boolean canFitAllClaims(Player player, List<ItemStack> items) {
        ItemStack[] current = player.getInventory().getStorageContents();
        ItemStack[] simulated = new ItemStack[current.length];
        for (int i = 0; i < current.length; i++) {
            simulated[i] = current[i] == null ? null : current[i].clone();
        }
        int inventoryMax = player.getInventory().getMaxStackSize();

        for (ItemStack raw : items) {
            if (raw == null || raw.getType() == Material.AIR || raw.getAmount() <= 0) {
                continue;
            }
            ItemStack item = raw.clone();
            int remaining = item.getAmount();

            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                ItemStack existing = simulated[slot];
                if (existing == null || existing.getType() == Material.AIR || !existing.isSimilar(item)) {
                    continue;
                }
                int maxStack = Math.min(existing.getMaxStackSize(), inventoryMax);
                int space = maxStack - existing.getAmount();
                if (space <= 0) {
                    continue;
                }
                int moved = Math.min(space, remaining);
                existing.setAmount(existing.getAmount() + moved);
                remaining -= moved;
            }

            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                ItemStack existing = simulated[slot];
                if (existing != null && existing.getType() != Material.AIR) {
                    continue;
                }
                int maxStack = Math.min(item.getMaxStackSize(), inventoryMax);
                int moved = Math.min(maxStack, remaining);
                ItemStack placed = item.clone();
                placed.setAmount(moved);
                simulated[slot] = placed;
                remaining -= moved;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private enum BrowseSort {
        HIGHEST_PRICE("Highest Price", "highest"),
        LOWEST_PRICE("Lowest Price", "lowest"),
        ENDING_SOON("Ending Soon", "ending"),
        ALPHABETICAL("Alphabetical", "alphabetical");

        private final String displayName;
        private final String actionId;

        BrowseSort(String displayName, String actionId) {
            this.displayName = displayName;
            this.actionId = actionId;
        }

        private static BrowseSort fromAction(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            if (raw.equalsIgnoreCase("shortest")) {
                return ENDING_SOON;
            }
            for (BrowseSort value : values()) {
                if (value.actionId.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return null;
        }

        private BrowseSort next() {
            BrowseSort[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        private BrowseSort previous() {
            BrowseSort[] all = values();
            return all[(ordinal() - 1 + all.length) % all.length];
        }
    }

    private enum BrowseCategory {
        ALL("All", "all"),
        COSMETICS("Cosmetics", "cosmetics"),
        WEAPON_SKINS("Weapon Skins", "weapon_skins"),
        EFFECTS("Effects", "effects"),
        TITLES("Titles", "titles"),
        TRAILS("Trails", "trails"),
        SHARDS("Shards", "shards"),
        RARE("Rare", "rare"),
        LIMITED("Limited", "limited"),
        NEWEST("Newest", "newest"),
        ENDING_SOON("Ending Soon", "ending_soon"),
        MIXED("Mixed", "mixed");

        private final String displayName;
        private final String commandName;

        BrowseCategory(String displayName, String commandName) {
            this.displayName = displayName;
            this.commandName = commandName;
        }

        private boolean matches(AuctionListing listing) {
            if (this == ALL || this == MIXED) {
                return true;
            }
            if (this == NEWEST) {
                return true;
            }
            if (this == ENDING_SOON) {
                return true;
            }
            if (this == RARE) {
                return listing.rarity == MarketRarity.RARE
                    || listing.rarity == MarketRarity.EPIC
                    || listing.rarity == MarketRarity.LEGENDARY
                    || listing.rarity == MarketRarity.LIMITED
                    || listing.category == RARE;
            }
            return listing.category == this;
        }

        private static BrowseCategory fromCommand(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.toLowerCase(Locale.ROOT).replace("-", "_");
            for (BrowseCategory value : values()) {
                if (value.commandName.equals(normalized) || value.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return value;
                }
            }
            if (normalized.equals("weaponskins")) {
                return WEAPON_SKINS;
            }
            if (normalized.equals("ending")) {
                return ENDING_SOON;
            }
            return null;
        }

        private static List<BrowseCategory> browsableValues() {
            return List.of(ALL, COSMETICS, WEAPON_SKINS, EFFECTS, TRAILS, SHARDS, RARE, LIMITED, NEWEST, ENDING_SOON);
        }

        private BrowseCategory next() {
            List<BrowseCategory> all = browsableValues();
            return all.get((all.indexOf(this) + 1) % all.size());
        }

        private BrowseCategory previous() {
            List<BrowseCategory> all = browsableValues();
            return all.get((all.indexOf(this) - 1 + all.size()) % all.size());
        }
    }

    private enum MarketRarity {
        COMMON("Common", ChatColor.WHITE),
        RARE("Rare", ChatColor.AQUA),
        EPIC("Epic", ChatColor.LIGHT_PURPLE),
        LEGENDARY("Legendary", ChatColor.GOLD),
        LIMITED("Limited", ChatColor.DARK_PURPLE);

        private final String displayName;
        private final ChatColor color;

        MarketRarity(String displayName, ChatColor color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    private enum BidCurrency {
        SILVER("Coins");

        private final String label;

        BidCurrency(String label) {
            this.label = label;
        }
    }

    public enum PayResult {
        OK,
        INVALID_TARGET,
        INVALID_AMOUNT,
        INSUFFICIENT,
        SELF
    }

    private record SimulatedMarketItem(
        ItemStack stack,
        String cosmeticId,
        BrowseCategory category,
        MarketRarity rarity,
        String sellerName,
        int price,
        long durationMs
    ) {
    }

    private record MarketSeed(
        String cosmeticId,
        String displayName,
        BrowseCategory category,
        MarketRarity rarity,
        Material material
    ) {
    }

    private static final class AuctionListing {
        private final UUID id;
        private final UUID sellerId;
        private final String sellerName;
        private final ItemStack item;
        private final String cosmeticId;
        private final boolean simulated;
        private final BrowseCategory category;
        private final MarketRarity rarity;
        private int currentBidCoinsEq;
        private UUID topBidderId;
        private String topBidderName;
        private BidCurrency topBidCurrency;
        private int topBidAmount;
        private long endsAtMs;

        private AuctionListing(
            UUID id,
            UUID sellerId,
            String sellerName,
            ItemStack item,
            String cosmeticId,
            int currentBidCoinsEq,
            UUID topBidderId,
            String topBidderName,
            int topBidAmount,
            long endsAtMs,
            boolean simulated,
            BrowseCategory category,
            MarketRarity rarity
        ) {
            this.id = id;
            this.sellerId = sellerId;
            this.sellerName = sellerName;
            this.item = item;
            this.cosmeticId = cosmeticId;
            this.simulated = simulated;
            this.category = category == null ? BrowseCategory.COSMETICS : category;
            this.rarity = rarity == null ? MarketRarity.COMMON : rarity;
            this.currentBidCoinsEq = currentBidCoinsEq;
            this.topBidderId = topBidderId;
            this.topBidderName = topBidderName;
            this.topBidCurrency = null;
            this.topBidAmount = topBidAmount;
            this.endsAtMs = endsAtMs;
        }
    }
}

