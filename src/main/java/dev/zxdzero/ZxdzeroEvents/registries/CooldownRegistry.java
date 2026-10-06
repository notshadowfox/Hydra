package dev.zxdzero.ZxdzeroEvents.registries;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A comprehensive cooldown registry system for Paper plugins using Persistent Data Containers.
 * Much more efficient than file-based storage as data is stored directly on players.
 */
public class CooldownRegistry implements Listener {

    private static JavaPlugin PLUGIN;
    private static final Map<NamespacedKey, CooldownType> registeredCooldowns = new ConcurrentHashMap<>();
    private static BukkitRunnable cleanupTask;

    /**
     * Represents a registered cooldown type
     */
    public static class CooldownType {
        private final NamespacedKey key;
        private final Material material;

        public CooldownType(NamespacedKey key, Material material) {
            this.key = key;
            this.material = material;
        }

        public NamespacedKey getKey() {
            return key;
        }

        public Material getMaterial() {
            return material;
        }

        public boolean hasMaterial() {
            return material != null;
        }
    }

    /**
     * Initializes the cooldown registry (call this in your plugin's onEnable)
     * @param plugin The plugin instance
     */
    public static void initialize(JavaPlugin plugin) {
        if (PLUGIN != null) {
            throw new IllegalStateException("CooldownRegistry is already initialized!");
        }

        PLUGIN = plugin;
        startCleanupTask();

        // Register event listener
        Bukkit.getPluginManager().registerEvents(new CooldownRegistry(), plugin);

        plugin.getLogger().info("CooldownRegistry initialized with PDC storage");
    }

    /**
     * Registers a new cooldown type
     * @param key The namespaced key for this cooldown
     * @param material Optional material for visual cooldown (can be null)
     */
    public static void registerCooldown(NamespacedKey key, Material material) {
        if (PLUGIN == null) {
            throw new IllegalStateException("CooldownRegistry not initialized! Call initialize() first.");
        }
        if (key == null) {
            throw new IllegalArgumentException("NamespacedKey cannot be null");
        }

        registeredCooldowns.put(key, new CooldownType(key, material));
        PLUGIN.getLogger().info("Registered cooldown: " + key +
                (material != null ? " with material: " + material : ""));
    }

    /**
     * Sets a cooldown for a specific player
     * @param player The player
     * @param key The cooldown key
     * @param seconds Duration in seconds
     * @param visualFeedback Show the cooldown visual
     * @return true if successful, false if cooldown type not registered
     */
    public static boolean setCooldown(Player player, NamespacedKey key, double seconds, boolean visualFeedback) {
        if (PLUGIN == null) {
            throw new IllegalStateException("CooldownRegistry not initialized!");
        }
        if (!registeredCooldowns.containsKey(key)) {
            return false;
        }

        long endTime = (long) (System.currentTimeMillis() + (seconds * 1000L));

        // Store in PDC
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey cooldownKey = createCooldownKey(key);
        pdc.set(cooldownKey, PersistentDataType.LONG, endTime);

        // Apply visual cooldown if material is specified
        CooldownType cooldownType = registeredCooldowns.get(key);
        if (cooldownType.hasMaterial() && visualFeedback) {
            applyVisualCooldown(player, cooldownType.getMaterial(), seconds);
        }

        return true;
    }

    /**
     * Sets a cooldown for a specific player
     * @param player The player
     * @param key The cooldown key
     * @param seconds Duration in seconds
     * @return true if successful, false if cooldown type not registered
     */
    public static boolean setCooldown(Player player, NamespacedKey key, double seconds) {
        return setCooldown(player, key, seconds, true);
    }

    /**
     * Sets a cooldown for a specific player using a string key
     * @param player The player
     * @param keyString The cooldown key as string (e.g., "yourplugin:teleport")
     * @param seconds Duration in seconds
     * @return true if successful, false if cooldown type not registered or invalid key
     */
    public static boolean setCooldown(Player player, String keyString, double seconds) {
        try {
            NamespacedKey key = NamespacedKey.fromString(keyString);
            if (key == null) {
                return false;
            }
            return setCooldown(player, key, seconds);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the remaining cooldown time for a player
     * @param player The player
     * @param key The cooldown key
     * @return Remaining seconds, or 0 if no cooldown
     */
    public static int getCooldown(Player player, NamespacedKey key) {
        if (PLUGIN == null) {
            throw new IllegalStateException("CooldownRegistry not initialized!");
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey cooldownKey = createCooldownKey(key);

        if (!pdc.has(cooldownKey, PersistentDataType.LONG)) {
            return 0;
        }

        long endTime = pdc.get(cooldownKey, PersistentDataType.LONG);
        long remaining = endTime - System.currentTimeMillis();

        if (remaining <= 0) {
            // Cooldown expired, clean up
            pdc.remove(cooldownKey);
            return 0;
        }

        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Gets the remaining cooldown time for a player using a string key
     * @param player The player
     * @param keyString The cooldown key as string (e.g., "yourplugin:teleport")
     * @return Remaining seconds, or 0 if no cooldown or invalid key
     */
    public static int getCooldown(Player player, String keyString) {
        try {
            NamespacedKey key = NamespacedKey.fromString(keyString);
            if (key == null) {
                return 0;
            }
            return getCooldown(player, key);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Checks if a player has an active cooldown
     * @param player The player
     * @param key The cooldown key
     * @return true if player has active cooldown
     */
    public static boolean hasCooldown(Player player, NamespacedKey key) {
        return getCooldown(player, key) > 0;
    }

    /**
     * Checks if a player has an active cooldown using a string key
     * @param player The player
     * @param keyString The cooldown key as string (e.g., "yourplugin:teleport")
     * @return true if player has active cooldown
     */
    public static boolean hasCooldown(Player player, String keyString) {
        return getCooldown(player, keyString) > 0;
    }

    /**
     * Resets a player's specific cooldown
     * @param player The player
     * @param key The cooldown key
     * @return true if cooldown was reset, false if no cooldown existed
     */
    public static boolean resetCooldown(Player player, NamespacedKey key) {
        if (PLUGIN == null) {
            throw new IllegalStateException("CooldownRegistry not initialized!");
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey cooldownKey = createCooldownKey(key);

        if (!pdc.has(cooldownKey, PersistentDataType.LONG)) {
            return false;
        }

        pdc.remove(cooldownKey);

        // Remove visual cooldown if material is specified
        CooldownType cooldownType = registeredCooldowns.get(key);
        if (cooldownType != null && cooldownType.hasMaterial()) {
            removeVisualCooldown(player, cooldownType.getMaterial());
        }

        return true;
    }

    /**
     * Resets a player's specific cooldown using a string key
     * @param player The player
     * @param keyString The cooldown key as string (e.g., "yourplugin:teleport")
     * @return true if cooldown was reset, false if no cooldown existed or invalid key
     */
    public static boolean resetCooldown(Player player, String keyString) {
        try {
            NamespacedKey key = NamespacedKey.fromString(keyString);
            if (key == null) {
                return false;
            }
            return resetCooldown(player, key);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resets all cooldowns for a player
     * @param player The player
     * @return Number of cooldowns reset
     */
    public static int resetAllCooldowns(Player player) {
        if (PLUGIN == null) {
            throw new IllegalStateException("CooldownRegistry not initialized!");
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int count = 0;

        // Find all cooldown keys for this plugin
        Set<NamespacedKey> keysToRemove = new HashSet<>();
        for (NamespacedKey registeredKey : registeredCooldowns.keySet()) {
            NamespacedKey cooldownKey = createCooldownKey(registeredKey);
            if (pdc.has(cooldownKey, PersistentDataType.LONG)) {
                keysToRemove.add(cooldownKey);

                // Remove visual cooldown if material is specified
                CooldownType cooldownType = registeredCooldowns.get(registeredKey);
                if (cooldownType.hasMaterial()) {
                    removeVisualCooldown(player, cooldownType.getMaterial());
                }
            }
        }

        // Remove all found cooldown keys
        for (NamespacedKey key : keysToRemove) {
            pdc.remove(key);
            count++;
        }

        return count;
    }

    /**
     * Gets all registered cooldown types
     * @return List of all registered cooldown keys as strings
     */
    public static List<String> getRegisteredCooldowns() {
        return registeredCooldowns.keySet().stream()
                .map(NamespacedKey::toString)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets all active cooldowns for a player
     * @param player The player
     * @return Map of cooldown keys to remaining seconds
     */
    public static Map<NamespacedKey, Integer> getPlayerCooldowns(Player player) {
        if (PLUGIN == null) {
            throw new IllegalStateException("CooldownRegistry not initialized!");
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Map<NamespacedKey, Integer> result = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        // Check all registered cooldowns
        for (NamespacedKey registeredKey : registeredCooldowns.keySet()) {
            NamespacedKey cooldownKey = createCooldownKey(registeredKey);

            if (pdc.has(cooldownKey, PersistentDataType.LONG)) {
                long endTime = pdc.get(cooldownKey, PersistentDataType.LONG);
                long remaining = endTime - currentTime;

                if (remaining <= 0) {
                    // Expired, remove it
                    pdc.remove(cooldownKey);
                } else {
                    result.put(registeredKey, (int) Math.ceil(remaining / 1000.0));
                }
            }
        }

        return result;
    }

    /**
     * Gets the number of active cooldowns for a player
     * @param player The player
     * @return Number of active cooldowns
     */
    public static int getActiveCooldownCount(Player player) {
        return getPlayerCooldowns(player).size();
    }

    /**
     * Creates a namespaced key for storing cooldown data in PDC
     * @param originalKey The original cooldown key
     * @return PDC storage key
     */
    private static NamespacedKey createCooldownKey(NamespacedKey originalKey) {
        return new NamespacedKey(PLUGIN, "cd_" + originalKey.getNamespace() + "_" + originalKey.getKey());
    }

    /**
     * Applies visual cooldown to items in player's inventory
     */
    private static void applyVisualCooldown(Player player, Material material, double seconds) {
        // Set cooldown on the material for the player
        player.setCooldown(material, (int) (seconds * 20)); // Convert seconds to ticks
    }

    /**
     * Removes visual cooldown from items in player's inventory
     */
    private static void removeVisualCooldown(Player player, Material material) {
        player.setCooldown(material, 0);
    }

    /**
     * Reapplies visual cooldowns when player joins
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Reapply visual cooldowns for all registered cooldowns with materials
        for (CooldownType cooldownType : registeredCooldowns.values()) {
            if (cooldownType.hasMaterial()) {
                int remaining = getCooldown(player, cooldownType.getKey());
                if (remaining > 0) {
                    applyVisualCooldown(player, cooldownType.getMaterial(), remaining);
                }
            }
        }
    }

    /**
     * Starts the cleanup task that removes expired cooldowns
     * This is optional since getCooldown() already cleans up expired cooldowns,
     * but helps keep PDCs clean for offline players
     */
    private static void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredCooldowns();
            }
        };

        // Run every 5 minutes (6000 ticks) to clean up offline players
        cleanupTask.runTaskTimer(PLUGIN, 6000L, 6000L);
    }

    /**
     * Cleans up expired cooldowns for online players
     * This is mainly for maintenance - expired cooldowns are cleaned up on access
     */
    private static void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        int cleaned = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PersistentDataContainer pdc = player.getPersistentDataContainer();

            for (NamespacedKey registeredKey : registeredCooldowns.keySet()) {
                NamespacedKey cooldownKey = createCooldownKey(registeredKey);

                if (pdc.has(cooldownKey, PersistentDataType.LONG)) {
                    long endTime = pdc.get(cooldownKey, PersistentDataType.LONG);

                    if (endTime <= currentTime) {
                        pdc.remove(cooldownKey);
                        cleaned++;
                    }
                }
            }
        }

        if (cleaned > 0) {
            PLUGIN.getLogger().info("Cleaned up " + cleaned + " expired cooldowns");
        }
    }

    /**
     * Cleans up all cooldown data for a specific player
     * Useful when a player leaves permanently or for administrative cleanup
     * @param player The player to clean up
     * @return Number of cooldowns removed
     */
    public static int cleanupPlayerData(Player player) {
        return resetAllCooldowns(player);
    }

    /**
     * Gets debug information about cooldown storage
     * @param player The player to check
     * @return Debug information string
     */
    public static String getDebugInfo(Player player) {
        Map<NamespacedKey, Integer> cooldowns = getPlayerCooldowns(player);

        StringBuilder info = new StringBuilder();
        info.append("Player: ").append(player.getName()).append("\n");
        info.append("Active cooldowns: ").append(cooldowns.size()).append("\n");
        info.append("Registered cooldown types: ").append(registeredCooldowns.size()).append("\n");

        if (!cooldowns.isEmpty()) {
            info.append("Details:\n");
            for (Map.Entry<NamespacedKey, Integer> entry : cooldowns.entrySet()) {
                info.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("s\n");
            }
        }

        return info.toString();
    }

    /**
     * Shuts down the cooldown registry
     */
    public static void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        // Final cleanup for online players
        cleanupExpiredCooldowns();

        registeredCooldowns.clear();

        if (PLUGIN != null) {
            PLUGIN.getLogger().info("CooldownRegistry shut down");
            PLUGIN = null;
        }
    }
}