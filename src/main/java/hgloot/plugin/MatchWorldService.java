package hgloot.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

//handles the disk and unload side of match worlds
final class MatchWorldService {
    private final Map<QueueService.QueueType, LinkedHashSet<String>> activeWorlds =
        new EnumMap<>(QueueService.QueueType.class);
    private final Map<String, String> netherByRoot = new HashMap<>();
    private final Map<String, String> rootByNether = new HashMap<>();
    private static final long FOLDER_DELETE_DELAY_TICKS = 20L * 30L;
    private static final long UNLOAD_RETRY_DELAY_TICKS = 20L * 5L;
    private static final int UNLOAD_MAX_RETRIES = 12;

    interface CleanupHooks {
        void returnPlayerToHub(Player player);
        void retryCleanup(String worldName);
        boolean isReserved(String worldName);
        boolean hasGenerationFlight(String worldName);
        void onFolderDeleted(String worldName);
        void onCleanupFinished(String worldName);
    }

    private final JavaPlugin plugin;
    private final CleanupHooks hooks;
    private final Map<String, Integer> unloadAttempts = new HashMap<>();
    private final Set<String> cleanupInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet();

    MatchWorldService(JavaPlugin plugin, CleanupHooks hooks) {
        this.plugin = plugin;
        this.hooks = hooks;
    }

    boolean isCleaning(String worldName) {
        return cleanupInProgress.contains(worldName);
    }

    void beginCleanup(String worldName) {
        cleanupInProgress.add(worldName);
    }

    void clearOwnership() {
        activeWorlds.clear();
        netherByRoot.clear();
        rootByNether.clear();
    }

    void addActive(QueueService.QueueType type, String worldName) {
        if (type == null || worldName == null || worldName.isBlank()) return;
        activeWorlds.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(worldName);
    }

    void removeActive(QueueService.QueueType type, String worldName) {
        if (type == null || worldName == null) return;
        LinkedHashSet<String> worlds = activeWorlds.get(type);
        if (worlds == null) return;
        worlds.remove(worldName);
        if (worlds.isEmpty()) activeWorlds.remove(type);
    }

    void removeActive(String worldName) {
        if (worldName == null) return;
        activeWorlds.values().forEach(worlds -> worlds.remove(worldName));
        activeWorlds.values().removeIf(LinkedHashSet::isEmpty);
    }

    String primaryActive(QueueService.QueueType type) {
        LinkedHashSet<String> worlds = type == null ? null : activeWorlds.get(type);
        return worlds == null || worlds.isEmpty() ? null : worlds.iterator().next();
    }

    List<String> activeFor(QueueService.QueueType type) {
        LinkedHashSet<String> worlds = type == null ? null : activeWorlds.get(type);
        return worlds == null ? List.of() : new ArrayList<>(worlds);
    }

    boolean hasActive(QueueService.QueueType type) {
        LinkedHashSet<String> worlds = type == null ? null : activeWorlds.get(type);
        return worlds != null && !worlds.isEmpty();
    }

    boolean isActive(String worldName) {
        if (worldName == null) return false;
        for (LinkedHashSet<String> worlds : activeWorlds.values()) {
            if (worlds.contains(worldName)) return true;
        }
        return false;
    }

    List<String> allActive() {
        List<String> all = new ArrayList<>();
        activeWorlds.values().forEach(all::addAll);
        return all;
    }

    List<Entry<QueueService.QueueType, String>> activeEntries() {
        List<Entry<QueueService.QueueType, String>> entries = new ArrayList<>();
        for (Entry<QueueService.QueueType, LinkedHashSet<String>> entry : activeWorlds.entrySet()) {
            for (String worldName : entry.getValue()) entries.add(Map.entry(entry.getKey(), worldName));
        }
        return entries;
    }

    QueueService.QueueType activeTypeFor(String worldName) {
        if (worldName == null) return null;
        for (Entry<QueueService.QueueType, LinkedHashSet<String>> entry : activeWorlds.entrySet()) {
            if (entry.getValue().contains(worldName)) return entry.getKey();
        }
        return null;
    }

    void registerLinkedNether(String root, String nether) {
        if (root == null || root.isBlank() || nether == null || nether.isBlank()) return;
        netherByRoot.put(root, nether);
        rootByNether.put(nether, root);
    }

    String linkedRoot(String worldName) {
        return rootByNether.get(worldName);
    }

    String mappedNether(String root) {
        return netherByRoot.get(root);
    }

    void unregisterLinkedNether(String root, String worldName, boolean linkedWorld) {
        if (worldName == null || worldName.isBlank()) return;
        String linked = netherByRoot.remove(root);
        if (linked != null) rootByNether.remove(linked);
        if (linkedWorld) rootByNether.remove(worldName);
    }

    //only unload after the match owner has cleared its own state
    void dispose(String worldName, boolean shuttingDown) {
        plugin.getLogger().info("[Queue] cleanup world: " + worldName);
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player player : world.getPlayers()) {
                hooks.returnPlayerToHub(player);
            }
            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded || Bukkit.getWorld(worldName) != null) {
                int attempt = unloadAttempts.merge(worldName, 1, Integer::sum);
                plugin.getLogger().warning("[Queue][WorldCleanup] event=unload_not_confirmed world="
                    + worldName + " attempt=" + attempt + " action=folder_delete_blocked");
                if (!shuttingDown && plugin.isEnabled() && attempt < UNLOAD_MAX_RETRIES) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> hooks.retryCleanup(worldName),
                        UNLOAD_RETRY_DELAY_TICKS);
                }
                return;
            }
            unloadAttempts.remove(worldName);
            plugin.getLogger().info("[Queue][WorldCleanup] event=unload_confirmed world=" + worldName);
        }
        unloadAttempts.remove(worldName);

        Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.exists(folder)) {
            finishCleanup(worldName);
            return;
        }
        if (world == null) {
            plugin.getLogger().info("[Queue][WorldCleanup] event=unload_confirmed world="
                + worldName + " state=not_loaded");
        }
        if (shuttingDown || !plugin.isEnabled()) {
            plugin.getLogger().info("[Queue] immediate shutdown folder delete for " + worldName + ".");
            deleteFolder(worldName, folder);
            finishCleanup(worldName);
            return;
        }
        scheduleFolderDelete(worldName, folder);
    }

    Set<String> staleRoots(Path container, Predicate<String> generatedRoot,
                           Predicate<String> liveOrReserved, java.util.function.Function<String, String> rootName,
                           java.util.function.Function<String, String> linkedNetherName) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        if (!Files.isDirectory(container)) {
            return roots;
        }
        try (Stream<Path> paths = Files.list(container)) {
            for (Path path : paths.toList()) {
                if (!Files.isDirectory(path)) continue;
                String root = rootName.apply(path.getFileName().toString());
                if (!generatedRoot.test(root)) continue;
                if (liveOrReserved.test(root) || liveOrReserved.test(linkedNetherName.apply(root))) continue;
                roots.add(root);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[Queue] Failed scanning stale match worlds: " + ex.getMessage());
        }
        return roots;
    }

    private void scheduleFolderDelete(String worldName, Path folder) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isSafeFolder(worldName, folder, Bukkit.getWorldContainer().toPath())) {
                plugin.getLogger().severe("[Queue] Refusing unsafe match world folder delete for "
                    + worldName + ": " + folder);
                cleanupInProgress.remove(worldName);
                return;
            }
            if (Bukkit.getWorld(worldName) != null || hooks.isReserved(worldName)) {
                plugin.getLogger().warning("[Queue][WorldCleanup] event=folder_delete_canceled world="
                    + worldName + " reason=world_became_live_or_reserved");
                cleanupInProgress.remove(worldName);
                return;
            }
            if (hooks.hasGenerationFlight(worldName)) {
                plugin.getLogger().warning("[Queue][WorldCleanup] event=folder_delete_canceled world="
                    + worldName + " reason=generation_flight_registered");
                cleanupInProgress.remove(worldName);
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                deleteFolder(worldName, folder);
                Bukkit.getScheduler().runTask(plugin, () -> finishCleanup(worldName));
            });
        }, FOLDER_DELETE_DELAY_TICKS);
    }

    static boolean isSafeFolder(String worldName, Path folder, Path worldContainer) {
        if (worldName == null || worldName.isBlank() || folder == null || worldContainer == null) return false;
        Path container = worldContainer.toAbsolutePath().normalize();
        Path normalized = folder.toAbsolutePath().normalize();
        return container.equals(normalized.getParent())
            && normalized.getFileName() != null
            && worldName.equals(normalized.getFileName().toString());
    }

    private void deleteFolder(String worldName, Path folder) {
        if (folder == null || !Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Failed deleting world path: " + path + " (" + ex.getMessage() + ")");
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed cleaning match world " + worldName + ": " + ex.getMessage());
        }
        if (!Files.exists(folder)) {
            plugin.getLogger().info("[Queue] deleted match world folder: " + worldName);
            hooks.onFolderDeleted(worldName);
        }
    }

    private void finishCleanup(String worldName) {
        hooks.onCleanupFinished(worldName);
        cleanupInProgress.remove(worldName);
    }
}
