package hgloot.plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import hgloot.plugin.ultraroyales.Items;

/** UUID-keyed preferences stored with Hydra's existing config-backed player data. */
public final class PlayerSettingsService implements Listener {
    private static final String ROOT = "player-settings.";

    public enum Toggle {
        NIGHT_VISION("night-vision", "Night Vision", "Toggles night vision in games.", true),
        TEAM_GLOW("team-glow", "Team Glow", "Toggles teammate outlines.", true),
        SPECTATOR_VISIBILITY("spectator-visibility", "Spectator Visibility", "Toggles seeing other spectators.", true),
        ROTATING_TIPS("rotating-tips", "Rotating Tips", "Toggles helpful chat tips.", true),
        PROXY_CHAT("proxy-chat", "Proxy Chat", "Toggles proximity voice chat.", true),
        FRIEND_NOTIFICATIONS("friend-notifications", "Friend Notifications", "Toggles friend join and leave notices.", true),
        FRIEND_REQUESTS("friend-requests", "Friend Requests", "Toggles incoming friend requests.", true),
        PRIVATE_MESSAGES("private-messages", "Private Messages", "Toggles incoming player messages.", true),
        PARTY_INVITES("party-invites", "Party Invites", "Toggles incoming party invites.", true),
        AUTO_REQUEUE("auto-requeue", "Auto Requeue", "Requeues after your session ends.", false),
        REJOIN_REMINDER("rejoin-reminder", "Rejoin Reminder", "Toggles optional rejoin prompts.", true),
        LOBBY_FLIGHT("lobby-flight", "Lobby Flight", "Toggles Hydra+ lobby flight.", false);

        private final String key;
        private final String title;
        private final String description;
        private final boolean defaultValue;

        Toggle(String key, String title, String description, boolean defaultValue) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        public String key() { return key; }
        public String title() { return title; }
        public String description() { return description; }
        public boolean defaultValue() { return defaultValue; }

        public static Toggle byKey(String key) {
            for (Toggle value : values()) if (value.key.equals(key)) return value;
            return null;
        }
    }

    public enum LobbyTime {
        SERVER_DEFAULT("Default", -1L),
        DAY("Day", 1000L),
        SUNSET("Sunset", 12000L),
        NIGHT("Night", 18000L),
        SUNRISE("Sunrise", 23000L);

        private final String label;
        private final long ticks;

        LobbyTime(String label, long ticks) {
            this.label = label;
            this.ticks = ticks;
        }

        public String label() { return label; }
        public long ticks() { return ticks; }
        public LobbyTime next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum AutoQueue {
        HUNGER_GAMES_SOLO("hg join solos"),
        HUNGER_GAMES_DUOS("hg join duos"),
        MINIGAME_ROULETTE("roulette join"),
        BRIDGE("bridge join"),
        SPLEEF("spleef join"),
        VOID_ROULETTE("voidroulette join"),
        BOSS_RUSH("admin bossrush join");

        private final String playerCommand;
        AutoQueue(String playerCommand) { this.playerCommand = playerCommand; }
        public String playerCommand() { return playerCommand; }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, EnumMap<Toggle, Boolean>> voiceSafeCache = new ConcurrentHashMap<>();
    private final Set<UUID> hydraNightVision = ConcurrentHashMap.newKeySet();
    private final NamespacedKey flightWingsKey;
    private final Map<UUID, LobbyTime> appliedLobbyTimes = new ConcurrentHashMap<>();
    private BukkitTask contextTask;

    public PlayerSettingsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.flightWingsKey = NamespacedKey.fromString("hydra:hydraplus_flight_wings");
    }

    public void start() {
        if (contextTask != null) contextTask.cancel();
        Bukkit.getOnlinePlayers().forEach(player -> loadVoiceSnapshot(player.getUniqueId()));
        contextTask = Bukkit.getScheduler().runTaskTimer(plugin,
            () -> Bukkit.getOnlinePlayers().forEach(this::refreshContext), 1L, 20L);
    }

    public void shutdown() {
        if (contextTask != null) contextTask.cancel();
        contextTask = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearHydraNightVision(player);
            if (appliedLobbyTimes.remove(player.getUniqueId()) != null) player.resetPlayerTime();
            clearHydraFlight(player);
        }
        voiceSafeCache.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadVoiceSnapshot(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshContext(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshContext(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearHydraNightVision(player);
        appliedLobbyTimes.remove(player.getUniqueId());
        clearHydraFlight(player);
        unloadVoiceSnapshot(player.getUniqueId());
    }

    public boolean enabled(UUID playerId, Toggle setting) {
        if (playerId == null || setting == null) return false;
        String path = path(playerId, setting);
        FileConfiguration config = plugin.getConfig();
        return config.contains(path) ? config.getBoolean(path) : setting.defaultValue();
    }

    public static boolean enabledOrDefault(HGLootPlugin hydra, UUID playerId, Toggle setting) {
        return hydra == null || hydra.playerSettingsService() == null
            ? setting.defaultValue() : hydra.playerSettingsService().enabled(playerId, setting);
    }

    /** Voice packet callbacks run off the main thread; never read Bukkit config there. */
    public boolean enabledFromVoiceThread(UUID playerId, Toggle setting) {
        EnumMap<Toggle, Boolean> snapshot = voiceSafeCache.get(playerId);
        return snapshot == null ? setting.defaultValue() : snapshot.getOrDefault(setting, setting.defaultValue());
    }

    public void loadVoiceSnapshot(UUID playerId) {
        if (playerId == null) return;
        EnumMap<Toggle, Boolean> snapshot = new EnumMap<>(Toggle.class);
        for (Toggle setting : Toggle.values()) snapshot.put(setting, enabled(playerId, setting));
        voiceSafeCache.put(playerId, snapshot);
    }

    public void unloadVoiceSnapshot(UUID playerId) {
        voiceSafeCache.remove(playerId);
    }

    public void set(Player player, Toggle setting, boolean enabled) {
        if (setting == Toggle.LOBBY_FLIGHT) {
            setFlightEnabled(player, enabled);
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.getConfig().set(path(playerId, setting), enabled);
        plugin.saveConfig();
        loadVoiceSnapshot(playerId);
        if (setting == Toggle.NIGHT_VISION && !enabled) clearHydraNightVision(player);
    }

    /** The persisted Flight toggle is the sole /fly and Settings state. */
    public boolean flightEnabled(Player player) {
        return player != null && enabled(player.getUniqueId(), Toggle.LOBBY_FLIGHT);
    }

    public boolean canUseFlightToggle(Player player) {
        return player != null && plugin instanceof HGLootPlugin hydra
            && (hydra.hasHydraPlusTierRank(player.getUniqueId()) || player.hasPermission("hgloot.fly"));
    }

    public boolean canEnableFlight(Player player) {
        if (!flightContextAllowed(player)) return false;
        ItemStack chest = player.getInventory().getChestplate();
        return chest == null || chest.getType() == Material.AIR || isGrantedFlightWings(chest);
    }

    private boolean flightContextAllowed(Player player) {
        if (!canUseFlightToggle(player) || !(plugin instanceof HGLootPlugin hydra)) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        if (!player.hasPermission("hgloot.fly") && !lobbyFlightContext(hydra, player)) return false;
        return !hydra.isInAnyHydraQueueOrMatch(player) || hydra.hasTrialStaffOrHigher(player);
    }

    /** Never replaces an occupied chest slot; the caller can report a refused enable. */
    public boolean setFlightEnabled(Player player, boolean enable) {
        if (player == null || (enable && !canEnableFlight(player))) return false;
        UUID id = player.getUniqueId();
        if (enable && !equipFlightWings(player)) return false;
        plugin.getConfig().set(path(id, Toggle.LOBBY_FLIGHT), enable);
        plugin.saveConfig();
        loadVoiceSnapshot(id);
        if (enable && plugin instanceof HGLootPlugin hydra) {
            clearLegacyLobbyFlight(hydra, player);
        } else if (!enable) {
            clearHydraFlight(player);
        }
        return true;
    }

    public void applyHydraNightVision(Player player) {
        if (player == null || !enabled(player.getUniqueId(), Toggle.NIGHT_VISION)
            || player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
            Integer.MAX_VALUE, 0, true, false, false));
        hydraNightVision.add(player.getUniqueId());
    }

    public void clearHydraNightVision(Player player) {
        if (player == null || !hydraNightVision.remove(player.getUniqueId())) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current != null && current.getDuration() > 1_000_000_000
            && current.getAmplifier() == 0 && current.isAmbient()
            && !current.hasParticles() && !current.hasIcon()) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    public void syncSpectatorPair(Player viewer, Player other) {
        if (viewer == null || other == null || viewer.equals(other)) return;
        if (enabled(viewer.getUniqueId(), Toggle.SPECTATOR_VISIBILITY)) viewer.showPlayer(plugin, other);
        else viewer.hidePlayer(plugin, other);
    }

    public static void syncSpectatorPairOrDefault(HGLootPlugin hydra, Player viewer, Player other) {
        if (viewer == null || other == null || viewer.equals(other)) return;
        if (hydra == null) return;
        if (hydra.playerSettingsService() == null) viewer.showPlayer(hydra, other);
        else hydra.playerSettingsService().syncSpectatorPair(viewer, other);
    }

    public LobbyTime lobbyTime(UUID playerId) {
        if (playerId == null) return LobbyTime.SERVER_DEFAULT;
        String value = plugin.getConfig().getString(ROOT + playerId + ".lobby-time", "SERVER_DEFAULT");
        if (value == null) return LobbyTime.SERVER_DEFAULT;
        try {
            return LobbyTime.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return LobbyTime.SERVER_DEFAULT;
        }
    }

    public void setLobbyTime(Player player, LobbyTime time) {
        plugin.getConfig().set(ROOT + player.getUniqueId() + ".lobby-time", time.name());
        plugin.saveConfig();
        refreshContext(player);
    }

    public void refreshContext(Player player) {
        if (player == null || !player.isOnline() || !(plugin instanceof HGLootPlugin hydra)) return;
        UUID id = player.getUniqueId();
        if (flightEnabled(player) && !canUseFlightToggle(player)) {
            plugin.getConfig().set(path(id, Toggle.LOBBY_FLIGHT), false);
            plugin.saveConfig();
            loadVoiceSnapshot(id);
        }
        LobbyTime desired = lobbyTimeContext(hydra, player) ? lobbyTime(id) : LobbyTime.SERVER_DEFAULT;
        LobbyTime applied = appliedLobbyTimes.get(id);
        if (desired == LobbyTime.SERVER_DEFAULT) {
            if (applied != null) {
                player.resetPlayerTime();
                appliedLobbyTimes.remove(id);
            }
        } else if (desired != applied) {
            player.setPlayerTime(desired.ticks(), false);
            appliedLobbyTimes.put(id, desired);
        }

        boolean mayFly = flightEnabled(player) && flightContextAllowed(player);
        if (mayFly) {
            if (equipFlightWings(player)) {
                // The Legendary wings own movement; Hydra+ never grants Bukkit flight.
                clearLegacyLobbyFlight(hydra, player);
            } else {
                clearHydraFlight(player);
            }
        } else if (inActiveMatchContext(hydra, id)) {
            // The match or its spectator controller owns flight now. Do not revoke it here.
            removeGrantedFlightWings(player);
        } else {
            clearHydraFlight(player);
        }
    }

    public void requeueAfterCompletedSession(Collection<UUID> participants, AutoQueue originalQueue) {
        if (participants == null || participants.isEmpty() || originalQueue == null) return;
        List<UUID> candidates = List.copyOf(new LinkedHashSet<>(participants));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(plugin instanceof HGLootPlugin hydra)) return;
            for (UUID id : candidates) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !player.isOnline() || !enabled(id, Toggle.AUTO_REQUEUE)
                    || busyForRequeue(hydra, id)) continue;
                if (hydra.partyService() != null && hydra.partyService().isInParty(id)
                    && !hydra.partyService().isLeader(id)) continue;
                // Dispatching as the player preserves command permissions and each mode's normal
                // party, queue, and admission checks. Managed MR rounds never call this method.
                Bukkit.dispatchCommand(player, originalQueue.playerCommand());
            }
        }, 2L);
    }

    private boolean busyForRequeue(HGLootPlugin hydra, UUID id) {
        return (hydra.queueService() != null && (hydra.queueService().isPlayerQueued(id)
                || hydra.queueService().isPlayerCommittedToMatchStart(id)
                || hydra.queueService().isPlayerInHydraMatchOrSpectating(id)))
            || (hydra.minigameRouletteService() != null && hydra.minigameRouletteService().isTournamentMember(id))
            || (hydra.bridgeService() != null && hydra.bridgeService().isInBridge(id))
            || (hydra.spleefService() != null && hydra.spleefService().isInSpleef(id))
            || (hydra.voidRouletteService() != null && hydra.voidRouletteService().isInVoidRoulette(id))
            || (hydra.bossRushService() != null && hydra.bossRushService().ownsPlayer(id));
    }

    private boolean lobbyTimeContext(HGLootPlugin hydra, Player player) {
        UUID id = player.getUniqueId();
        if (hydra.minigameRouletteService() != null && hydra.minigameRouletteService().isLobbyTimeContext(id)) return true;
        if (hydra.bridgeService() != null && hydra.bridgeService().isLobbyTimeContext(id)) return true;
        if (hydra.spleefService() != null && hydra.spleefService().isLobbyTimeContext(id)) return true;
        if (hydra.voidRouletteService() != null && hydra.voidRouletteService().isLobbyTimeContext(id)) return true;
        if (hydra.bossRushService() != null && hydra.bossRushService().isLobbyTimeContext(id)) return true;
        if (hydra.kitEditorService() != null && hydra.kitEditorService().isEditing(id)) return true;
        if (hydra.queueService() != null && hydra.queueService().isPlayerInHydraMatchOrSpectating(id)) return false;
        if (hydra.minigameRouletteService() != null && hydra.minigameRouletteService().isInActiveTournament(id)) return false;
        if (hydra.queueService() != null && hydra.queueService().isLobbyWorld(player)) return true;
        return hydra.gameHubService() != null && hydra.gameHubService().isGameHub(player.getWorld());
    }

    private boolean lobbyFlightContext(HGLootPlugin hydra, Player player) {
        UUID id = player.getUniqueId();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (inActiveMatchContext(hydra, id)) return false;
        return (hydra.queueService() != null && hydra.queueService().isLobbyWorld(player))
            || (hydra.gameHubService() != null && hydra.gameHubService().isGameHub(player.getWorld()));
    }

    private boolean inActiveMatchContext(HGLootPlugin hydra, UUID id) {
        return (hydra.queueService() != null && hydra.queueService().isPlayerInHydraMatchOrSpectating(id))
            || (hydra.bridgeService() != null && hydra.bridgeService().isInActiveBridgeMatch(id))
            || (hydra.spleefService() != null && hydra.spleefService().isInActiveSpleefMatch(id))
            || (hydra.voidRouletteService() != null && hydra.voidRouletteService().isInActiveVoidRouletteMatch(id))
            || (hydra.bossRushService() != null && hydra.bossRushService().isInActiveMatch(id))
            || (hydra.minigameRouletteService() != null && hydra.minigameRouletteService().isInActiveTournament(id));
    }

    private void clearHydraFlight(Player player) {
        removeGrantedFlightWings(player);
        if (plugin instanceof HGLootPlugin hydra) clearLegacyLobbyFlight(hydra, player);
    }

    private void clearLegacyLobbyFlight(HGLootPlugin hydra, Player player) {
        // Strip a still-connected 2.0.221 Hydra+ grant only in its old lobby scope.
        // Staff, creative/spectator, and active matches own their own flight state.
        if (hydra.hasTrialStaffOrHigher(player) || !lobbyFlightContext(hydra, player)) return;
        if (player.isFlying()) player.setFlying(false);
        if (player.getAllowFlight()) player.setAllowFlight(false);
    }

    private boolean equipFlightWings(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (isGrantedFlightWings(chest)) return true;
        if (chest != null && chest.getType() != Material.AIR) return false;
        removeGrantedFlightWings(player);
        ItemStack wings = Items.monarchWings();
        ItemMeta meta = wings.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.getPersistentDataContainer().set(flightWingsKey, PersistentDataType.BYTE, (byte) 1);
        wings.setItemMeta(meta);
        player.getInventory().setChestplate(wings);
        return true;
    }

    private boolean isGrantedFlightWings(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
            .has(flightWingsKey, PersistentDataType.BYTE);
    }

    private void removeGrantedFlightWings(Player player) {
        if (isGrantedFlightWings(player.getInventory().getChestplate())) {
            player.getInventory().setChestplate(null);
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (isGrantedFlightWings(storage[slot])) player.getInventory().setItem(slot, null);
        }
        if (isGrantedFlightWings(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    static String path(UUID playerId, Toggle setting) {
        return ROOT + playerId + "." + setting.key();
    }
}
