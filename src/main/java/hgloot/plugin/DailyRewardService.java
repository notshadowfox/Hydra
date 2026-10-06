package hgloot.plugin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class DailyRewardService {
    static final ZoneId RESET_ZONE = ZoneId.of("America/New_York");
    static final int MAX_REWARD_AMOUNT = 1_000_000;

    private final JavaPlugin plugin;
    private final ProgressionService progressionService;
    private final NamespacedKey globalClaimKey;
    private final Clock clock;

    public DailyRewardService(JavaPlugin plugin, ProgressionService progressionService) {
        this(plugin, progressionService, Clock.system(RESET_ZONE));
    }

    DailyRewardService(JavaPlugin plugin, ProgressionService progressionService, Clock clock) {
        this.plugin = plugin;
        this.progressionService = progressionService;
        this.clock = clock;
        this.globalClaimKey = new NamespacedKey(plugin, "npc_daily_global_claim");
    }

    public RewardBundle parse(String payload) {
        if (payload == null || payload.isBlank()) return RewardBundle.empty();
        String normalized = payload.trim();
        if (normalized.regionMatches(true, 0, "daily:", 0, "daily:".length())) {
            normalized = normalized.substring("daily:".length()).trim();
        }
        EnumMap<RewardType, Integer> rewards = new EnumMap<>(RewardType.class);
        if (!normalized.contains("=")) {
            String[] legacy = normalized.split(":", 2);
            if (legacy.length != 2) throw new IllegalArgumentException("Invalid daily reward payload");
            putReward(rewards, legacy[0], parseAmount(legacy[1]));
            return new RewardBundle(rewards);
        }
        for (String entry : normalized.split("[,;]")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2) throw new IllegalArgumentException("Invalid daily reward entry: " + entry);
            putReward(rewards, pair[0], parseAmount(pair[1]));
        }
        return new RewardBundle(rewards);
    }

    public RewardBundle addOrReplace(RewardBundle bundle, String rawType, int amount) {
        validateAmount(amount);
        RewardType type = RewardType.from(rawType);
        if (type == null) throw new IllegalArgumentException("Unknown daily reward type");
        return (bundle == null ? RewardBundle.empty() : bundle).with(type, amount);
    }

    public RewardBundle remove(RewardBundle bundle, String rawType) {
        RewardType type = RewardType.from(rawType);
        if (type == null) throw new IllegalArgumentException("Unknown daily reward type");
        return (bundle == null ? RewardBundle.empty() : bundle).without(type);
    }

    public boolean claim(Player player, String sourceId, String payload) {
        if (player == null || progressionService == null) return false;
        RewardBundle bundle;
        try {
            bundle = parse(payload);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[DailyReward] Invalid reward source=" + sourceId + ": " + payload);
            player.sendMessage(ChatColor.RED + "This daily reward is misconfigured.");
            return false;
        }
        if (bundle.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This daily reward is unavailable right now.");
            return false;
        }

        LocalDate today = LocalDate.now(clock.withZone(RESET_ZONE));
        if (claimedOn(player.getPersistentDataContainer(), today)) {
            player.sendMessage(ChatColor.YELLOW + "You already claimed today's Daily Reward. Resets in "
                + formatUntilReset(clock.instant()) + ".");
            return false;
        }

        UUID playerId = player.getUniqueId();
        String reason = "NPC_DAILY:" + (sourceId == null ? "unknown" : sourceId);
        for (Map.Entry<RewardType, Integer> reward : bundle.rewards().entrySet()) {
            switch (reward.getKey()) {
                case SHARDS -> progressionService.grantShards(playerId, reward.getValue(), reason);
                case FRAGMENTS -> progressionService.grantShardFragments(playerId, reward.getValue(), reason);
                case XP -> progressionService.grantPrestigeXp(playerId, reward.getValue(), reason);
                case COINS -> progressionService.queueSilver(playerId, reward.getValue(), reason);
            }
        }
        player.getPersistentDataContainer().set(globalClaimKey, PersistentDataType.STRING, today.toString());
        plugin.getLogger().info("[DailyReward] Claimed source=" + sourceId + " player=" + playerId
            + " rewards=" + bundle.encode());
        player.sendMessage(ChatColor.GREEN + "Daily Reward claimed: " + ChatColor.GRAY + bundle.describe() + ChatColor.GREEN + ".");
        return true;
    }

    boolean claimedOn(PersistentDataContainer data, LocalDate date) {
        String expected = date.toString();
        String namespace = globalClaimKey.getNamespace();
        for (NamespacedKey key : data.getKeys()) {
            if (!namespace.equals(key.getNamespace()) || !key.getKey().startsWith("npc_daily_")) continue;
            if (expected.equals(data.get(key, PersistentDataType.STRING))) {
                data.set(globalClaimKey, PersistentDataType.STRING, expected);
                return true;
            }
        }
        return false;
    }

    String formatUntilReset(Instant now) {
        ZonedDateTime current = now.atZone(RESET_ZONE);
        ZonedDateTime reset = current.toLocalDate().plusDays(1).atStartOfDay(RESET_ZONE);
        long seconds = Math.max(1L, Duration.between(current, reset).getSeconds());
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m";
        return seconds + "s";
    }

    private void putReward(EnumMap<RewardType, Integer> rewards, String rawType, int amount) {
        validateAmount(amount);
        RewardType type = RewardType.from(rawType);
        if (type == null) throw new IllegalArgumentException("Unknown daily reward type: " + rawType);
        rewards.put(type, amount);
    }

    private int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Daily reward amount must be a whole number", exception);
        }
    }

    private void validateAmount(int amount) {
        if (amount <= 0 || amount > MAX_REWARD_AMOUNT) {
            throw new IllegalArgumentException("Daily reward amount is outside the allowed range");
        }
    }

    public enum RewardType {
        SHARDS("shards", "Shards"),
        FRAGMENTS("fragments", "Fragments"),
        XP("xp", "XP"),
        COINS("coins", "Coins");

        private final String key;
        private final String display;

        RewardType(String key, String display) {
            this.key = key;
            this.display = display;
        }

        static RewardType from(String raw) {
            if (raw == null) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "shard", "shards" -> SHARDS;
                case "fragment", "fragments", "shard-fragment", "shard-fragments" -> FRAGMENTS;
                case "xp", "experience", "level-xp" -> XP;
                case "coin", "coins", "gold", "silver", "money" -> COINS;
                default -> null;
            };
        }
    }

    public record RewardBundle(Map<RewardType, Integer> rewards) {
        public RewardBundle {
            EnumMap<RewardType, Integer> copy = new EnumMap<>(RewardType.class);
            if (rewards != null) copy.putAll(rewards);
            rewards = Collections.unmodifiableMap(copy);
        }

        public static RewardBundle empty() {
            return new RewardBundle(Map.of());
        }

        public RewardBundle with(RewardType type, int amount) {
            EnumMap<RewardType, Integer> copy = new EnumMap<>(RewardType.class);
            copy.putAll(rewards);
            copy.put(type, amount);
            return new RewardBundle(copy);
        }

        public RewardBundle without(RewardType type) {
            EnumMap<RewardType, Integer> copy = new EnumMap<>(RewardType.class);
            copy.putAll(rewards);
            copy.remove(type);
            return new RewardBundle(copy);
        }

        public boolean isEmpty() {
            return rewards.isEmpty();
        }

        public String encode() {
            return java.util.Arrays.stream(RewardType.values())
                .filter(rewards::containsKey)
                .map(type -> type.key + "=" + rewards.get(type))
                .collect(java.util.stream.Collectors.joining(","));
        }

        public String describe() {
            return java.util.Arrays.stream(RewardType.values())
                .filter(rewards::containsKey)
                .map(type -> rewards.get(type) + " " + type.display)
                .collect(java.util.stream.Collectors.joining(", "));
        }
    }
}
