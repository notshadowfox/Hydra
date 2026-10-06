package hgloot.plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One confirmation per player, bound to the current MR round when applicable. */
final class HubExitConfirmation {
    private static final long WINDOW_MS = 10_000L;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    boolean confirm(UUID playerId, UUID rouletteRoundId, boolean liveMatch, long now) {
        if (rouletteRoundId == null && !liveMatch) {
            clear(playerId);
            return true;
        }
        Pending previous = pending.remove(playerId);
        if (previous != null && previous.expiresAt() >= now
            && Objects.equals(previous.rouletteRoundId(), rouletteRoundId)) {
            return true;
        }
        pending.put(playerId, new Pending(now + WINDOW_MS, rouletteRoundId));
        return false;
    }

    void clear(UUID playerId) {
        pending.remove(playerId);
    }

    void clearAll() {
        pending.clear();
    }

    private record Pending(long expiresAt, UUID rouletteRoundId) {
    }
}
