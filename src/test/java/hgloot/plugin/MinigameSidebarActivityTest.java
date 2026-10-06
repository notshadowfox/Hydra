package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MinigameSidebarActivityTest {
    @Test
    void hubSidebarUsesTheAuthoritativeRouletteRosterAsItsActivityFloor() throws Exception {
        String queueSource = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        String rouletteSource = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));

        assertTrue(queueSource.contains("minigameRouletteService().sidebarActivityCount()"));
        assertTrue(queueSource.contains("Math.max(directMinigameCount, rouletteCount)"));
        assertTrue(rouletteSource.contains("return rosterLocked ? playingCount() : queuedCount()"));
    }
}
