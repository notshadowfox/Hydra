package hgloot.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FriendService {
    private static final int MAX_FRIENDS = 100;
    private static final int MAX_PENDING_REQUESTS = 20;
    private static final long REQUEST_COOLDOWN_MS = 10_000L;
    private static final long SAVE_DEBOUNCE_TICKS = 20L;

    private final JavaPlugin plugin;
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    private final Map<UUID, Set<UUID>> incomingRequests = new HashMap<>();
    private final Map<UUID, Set<UUID>> blockedPlayers = new HashMap<>();
    private final Map<UUID, Long> lastRequestAtMs = new HashMap<>();
    private BukkitTask pendingSaveTask;
    private boolean saveDirty;

    public FriendService(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public synchronized void shutdown() {
        saveNow();
        lastRequestAtMs.clear();
    }

    public synchronized List<UUID> getFriends(UUID playerId) {
        Set<UUID> set = friends.getOrDefault(playerId, Set.of());
        return new ArrayList<>(set);
    }

    public synchronized List<UUID> getIncomingRequests(UUID playerId) {
        Set<UUID> set = incomingRequests.getOrDefault(playerId, Set.of());
        return new ArrayList<>(set);
    }

    public synchronized List<UUID> getBlockedPlayers(UUID playerId) {
        return new ArrayList<>(blockedPlayers.getOrDefault(playerId, Set.of()));
    }

    public synchronized boolean isBlocked(UUID ownerId, UUID targetId) {
        return ownerId != null && targetId != null
            && blockedPlayers.getOrDefault(ownerId, Set.of()).contains(targetId);
    }

    public synchronized boolean areFriends(UUID a, UUID b) {
        return friends.getOrDefault(a, Set.of()).contains(b)
            && friends.getOrDefault(b, Set.of()).contains(a);
    }

    public synchronized SendRequestResult sendRequest(UUID from, UUID to) {
        if (from.equals(to)) {
            return SendRequestResult.SELF;
        }
        if (areFriends(from, to)) {
            return SendRequestResult.ALREADY_FRIENDS;
        }
        if (isBlocked(from, to) || isBlocked(to, from)) {
            return SendRequestResult.BLOCKED;
        }
        if (plugin instanceof HGLootPlugin hydra && hydra.playerSettingsService() != null
            && !hydra.playerSettingsService().enabled(to, PlayerSettingsService.Toggle.FRIEND_REQUESTS)) {
            return SendRequestResult.REQUESTS_DISABLED;
        }
        if (incomingRequests.getOrDefault(to, Set.of()).contains(from)) {
            return SendRequestResult.ALREADY_SENT;
        }
        if (incomingRequests.getOrDefault(from, Set.of()).contains(to)) {
            // Mutual request: auto accept.
            if (friendLimitReached(from) || friendLimitReached(to)) {
                return SendRequestResult.FRIEND_LIMIT_REACHED;
            }
            incomingRequests.get(from).remove(to);
            cleanup(incomingRequests, from);
            addFriendship(from, to);
            save();
            return SendRequestResult.AUTO_ACCEPTED;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequestAtMs.getOrDefault(from, 0L) < REQUEST_COOLDOWN_MS) {
            return SendRequestResult.RATE_LIMITED;
        }
        if (friendLimitReached(from) || friendLimitReached(to)) {
            return SendRequestResult.FRIEND_LIMIT_REACHED;
        }
        if (outgoingRequestCount(from) >= MAX_PENDING_REQUESTS) {
            return SendRequestResult.OUTGOING_LIMIT_REACHED;
        }
        if (incomingRequests.getOrDefault(to, Set.of()).size() >= MAX_PENDING_REQUESTS) {
            return SendRequestResult.TARGET_REQUEST_LIMIT_REACHED;
        }

        incomingRequests.computeIfAbsent(to, ignored -> new HashSet<>()).add(from);
        lastRequestAtMs.put(from, now);
        save();
        return SendRequestResult.SENT;
    }

    public synchronized long requestCooldownSeconds(UUID playerId) {
        long remainingMs = REQUEST_COOLDOWN_MS
            - (System.currentTimeMillis() - lastRequestAtMs.getOrDefault(playerId, 0L));
        return Math.max(0L, (remainingMs + 999L) / 1000L);
    }

    public synchronized RequestActionResult acceptRequest(UUID target, UUID requester) {
        Set<UUID> incoming = incomingRequests.get(target);
        if (incoming == null || !incoming.contains(requester)) {
            return RequestActionResult.NOT_FOUND;
        }
        if (isBlocked(target, requester) || isBlocked(requester, target)) {
            return RequestActionResult.BLOCKED;
        }
        if (friendLimitReached(target) || friendLimitReached(requester)) {
            return RequestActionResult.FRIEND_LIMIT_REACHED;
        }
        incoming.remove(requester);
        cleanup(incomingRequests, target);
        addFriendship(target, requester);
        save();
        return RequestActionResult.OK;
    }

    public synchronized RequestActionResult denyRequest(UUID target, UUID requester) {
        Set<UUID> incoming = incomingRequests.get(target);
        if (incoming == null || !incoming.remove(requester)) {
            return RequestActionResult.NOT_FOUND;
        }
        cleanup(incomingRequests, target);
        save();
        return RequestActionResult.OK;
    }

    public synchronized RemoveFriendResult removeFriend(UUID a, UUID b) {
        boolean removed = false;
        Set<UUID> aSet = friends.get(a);
        if (aSet != null) {
            removed |= aSet.remove(b);
            cleanup(friends, a);
        }
        Set<UUID> bSet = friends.get(b);
        if (bSet != null) {
            removed |= bSet.remove(a);
            cleanup(friends, b);
        }
        if (!removed) {
            return RemoveFriendResult.NOT_FRIENDS;
        }
        save();
        return RemoveFriendResult.OK;
    }

    public synchronized BlockResult block(UUID ownerId, UUID targetId) {
        if (ownerId == null || targetId == null || ownerId.equals(targetId)) {
            return BlockResult.SELF;
        }
        Set<UUID> blocked = blockedPlayers.computeIfAbsent(ownerId, ignored -> new HashSet<>());
        if (!blocked.add(targetId)) {
            return BlockResult.ALREADY_BLOCKED;
        }
        removeFriendshipInternal(ownerId, targetId);
        removeRequestInternal(ownerId, targetId);
        removeRequestInternal(targetId, ownerId);
        save();
        return BlockResult.BLOCKED;
    }

    public synchronized BlockResult unblock(UUID ownerId, UUID targetId) {
        Set<UUID> blocked = blockedPlayers.get(ownerId);
        if (blocked == null || !blocked.remove(targetId)) {
            return BlockResult.NOT_BLOCKED;
        }
        cleanup(blockedPlayers, ownerId);
        save();
        return BlockResult.UNBLOCKED;
    }

    public String displayName(UUID playerId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null && !name.isBlank() ? name : playerId.toString().substring(0, 8);
    }

    private void addFriendship(UUID a, UUID b) {
        friends.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
        friends.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
    }

    private void removeFriendshipInternal(UUID a, UUID b) {
        Set<UUID> aFriends = friends.get(a);
        if (aFriends != null) {
            aFriends.remove(b);
            cleanup(friends, a);
        }
        Set<UUID> bFriends = friends.get(b);
        if (bFriends != null) {
            bFriends.remove(a);
            cleanup(friends, b);
        }
    }

    private void removeRequestInternal(UUID targetId, UUID requesterId) {
        Set<UUID> requests = incomingRequests.get(targetId);
        if (requests != null) {
            requests.remove(requesterId);
            cleanup(incomingRequests, targetId);
        }
    }

    private boolean friendLimitReached(UUID playerId) {
        return friends.getOrDefault(playerId, Set.of()).size() >= MAX_FRIENDS;
    }

    private int outgoingRequestCount(UUID playerId) {
        int count = 0;
        for (Set<UUID> incoming : incomingRequests.values()) {
            if (incoming.contains(playerId)) {
                count++;
            }
        }
        return count;
    }

    private void load() {
        friends.clear();
        incomingRequests.clear();
        blockedPlayers.clear();

        ConfigurationSection friendsSection = plugin.getConfig().getConfigurationSection("friends.data");
        if (friendsSection != null) {
            for (String key : friendsSection.getKeys(false)) {
                UUID playerId = parseUuid(key);
                if (playerId == null) {
                    continue;
                }
                List<String> rawList = friendsSection.getStringList(key);
                Set<UUID> set = new HashSet<>();
                for (String raw : rawList) {
                    UUID friendId = parseUuid(raw);
                    if (friendId != null && !friendId.equals(playerId)) {
                        set.add(friendId);
                    }
                }
                if (!set.isEmpty()) {
                    friends.put(playerId, set);
                }
            }
        }

        ConfigurationSection requestsSection = plugin.getConfig().getConfigurationSection("friends.requests");
        if (requestsSection != null) {
            for (String key : requestsSection.getKeys(false)) {
                UUID playerId = parseUuid(key);
                if (playerId == null) {
                    continue;
                }
                List<String> rawList = requestsSection.getStringList(key);
                Set<UUID> set = new HashSet<>();
                for (String raw : rawList) {
                    UUID requesterId = parseUuid(raw);
                    if (requesterId != null && !requesterId.equals(playerId)) {
                        set.add(requesterId);
                    }
                }
                if (!set.isEmpty()) {
                    incomingRequests.put(playerId, set);
                }
            }
        }

        ConfigurationSection blockedSection = plugin.getConfig().getConfigurationSection("friends.blocked");
        if (blockedSection != null) {
            for (String key : blockedSection.getKeys(false)) {
                UUID playerId = parseUuid(key);
                if (playerId == null) {
                    continue;
                }
                Set<UUID> set = new HashSet<>();
                for (String raw : blockedSection.getStringList(key)) {
                    UUID blockedId = parseUuid(raw);
                    if (blockedId != null && !blockedId.equals(playerId)) {
                        set.add(blockedId);
                    }
                }
                if (!set.isEmpty()) {
                    blockedPlayers.put(playerId, set);
                }
            }
        }
    }

    private synchronized void save() {
        saveDirty = true;
        if (pendingSaveTask != null) {
            return;
        }
        pendingSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (FriendService.this) {
                pendingSaveTask = null;
                saveNow();
            }
        }, SAVE_DEBOUNCE_TICKS);
    }

    private synchronized void saveNow() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
        if (!saveDirty) {
            return;
        }
        saveDirty = false;
        plugin.getConfig().set("friends.data", null);
        plugin.getConfig().set("friends.requests", null);
        plugin.getConfig().set("friends.blocked", null);

        for (Map.Entry<UUID, Set<UUID>> entry : friends.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> raw = new ArrayList<>();
            for (UUID id : entry.getValue()) {
                raw.add(id.toString());
            }
            plugin.getConfig().set("friends.data." + entry.getKey(), raw);
        }

        for (Map.Entry<UUID, Set<UUID>> entry : incomingRequests.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> raw = new ArrayList<>();
            for (UUID id : entry.getValue()) {
                raw.add(id.toString());
            }
            plugin.getConfig().set("friends.requests." + entry.getKey(), raw);
        }

        for (Map.Entry<UUID, Set<UUID>> entry : blockedPlayers.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            plugin.getConfig().set("friends.blocked." + entry.getKey(), entry.getValue().stream()
                .map(UUID::toString)
                .toList());
        }

        plugin.saveConfig();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void cleanup(Map<UUID, Set<UUID>> map, UUID key) {
        Set<UUID> set = map.get(key);
        if (set != null && set.isEmpty()) {
            map.remove(key);
        }
    }

    public enum SendRequestResult {
        SENT,
        AUTO_ACCEPTED,
        SELF,
        ALREADY_FRIENDS,
        ALREADY_SENT,
        RATE_LIMITED,
        FRIEND_LIMIT_REACHED,
        OUTGOING_LIMIT_REACHED,
        TARGET_REQUEST_LIMIT_REACHED,
        REQUESTS_DISABLED,
        BLOCKED
    }

    public enum RequestActionResult {
        OK,
        NOT_FOUND,
        FRIEND_LIMIT_REACHED,
        BLOCKED
    }

    public enum RemoveFriendResult {
        OK,
        NOT_FRIENDS
    }

    public enum BlockResult {
        BLOCKED,
        UNBLOCKED,
        SELF,
        ALREADY_BLOCKED,
        NOT_BLOCKED
    }
}
