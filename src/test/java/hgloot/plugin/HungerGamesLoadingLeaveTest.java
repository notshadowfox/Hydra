package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HungerGamesLoadingLeaveTest {
    private static final Path QUEUE_SERVICE = Path.of("src/main/java/hgloot/plugin/QueueService.java");

    @Test
    void selectedPlayersRemainCancellableUntilTheDelayedLaunchClaimsThem() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);

        assertTrue(source.contains("reservePlayersForMatchLaunch(selected, type, matchWorldName);"));
        assertTrue(source.contains("PendingMatchLaunch pendingLaunch = pendingMatchLaunches.remove(playerId);"));
        assertTrue(source.contains("if (!claimPendingMatchLaunch(id, type, matchWorldName))"));
        assertTrue(source.contains("releasePendingMatchLaunches(selected, type, matchWorldName);"));
    }
}
