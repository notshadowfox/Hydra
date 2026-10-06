package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AutoRequeueSourceTest {
    @Test
    void eachTopLevelSessionRetainsItsOwnQueueCommand() {
        assertEquals("hg join solos", PlayerSettingsService.AutoQueue.HUNGER_GAMES_SOLO.playerCommand());
        assertEquals("hg join duos", PlayerSettingsService.AutoQueue.HUNGER_GAMES_DUOS.playerCommand());
        assertEquals("roulette join", PlayerSettingsService.AutoQueue.MINIGAME_ROULETTE.playerCommand());
        assertEquals("bridge join", PlayerSettingsService.AutoQueue.BRIDGE.playerCommand());
        assertEquals("spleef join", PlayerSettingsService.AutoQueue.SPLEEF.playerCommand());
        assertEquals("voidroulette join", PlayerSettingsService.AutoQueue.VOID_ROULETTE.playerCommand());
        assertEquals("admin bossrush join", PlayerSettingsService.AutoQueue.BOSS_RUSH.playerCommand());
    }

    @Test
    void managedRoundsCannotQueueTheirStandaloneModes() throws Exception {
        for (String mode : new String[] {"BridgeService", "SpleefService", "VoidRouletteService", "BossRushService"}) {
            String source = Files.readString(Path.of("src/main/java/hgloot/plugin/" + mode + ".java"));
            assertTrue(source.contains("if (request == null) {"), mode);
            assertTrue(source.contains("requeueAfterCompletedSession("), mode);
        }
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        assertTrue(roulette.contains("\"Tournament complete.\".equals(reason)"));
        assertTrue(roulette.contains("AutoQueue.MINIGAME_ROULETTE"));
    }
}
