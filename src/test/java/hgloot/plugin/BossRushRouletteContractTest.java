package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BossRushRouletteContractTest {
    @Test
    void deathDoesNotRestoreBossRushLife() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/BossRushService.java"));
        String respawn = source.substring(source.indexOf("public void onRespawn"),
            source.indexOf("public void onQuit"));

        assertTrue(respawn.contains("prepareSpectator"));
        assertFalse(respawn.contains("alivePlayers.add"));
        assertFalse(respawn.contains("preparePlayer("));
    }

    @Test
    void rouletteOwnsBossRushLifecycle() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/MinigameRouletteService.java"));

        assertTrue(source.contains("Game.BOSS_RUSH"));
        assertTrue(source.contains("plugin.bossRushService().startManagedRound(request, selectedNanos)"));
        assertTrue(source.contains("long selectedNanos = game == Game.BOSS_RUSH ? System.nanoTime() : 0L"));
        assertTrue(source.contains("plugin.bossRushService().stopManagedRound(reason)"));
    }

    @Test
    void staffSkipInvalidatesRoundBeforeStoppingChildGame() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        String method = source.substring(source.indexOf("private void skipActiveRound"),
            source.indexOf("public void joinFromNavigator"));

        assertTrue(method.indexOf("activeRoundId = null")
            < method.indexOf("stopManagedRound(\"Round skipped by staff.\")"));
    }
}
