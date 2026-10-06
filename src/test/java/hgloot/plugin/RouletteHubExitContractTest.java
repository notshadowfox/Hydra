package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RouletteHubExitContractTest {
    @Test
    void commandWordingsAndExitSemanticsStayDistinct() throws Exception {
        String plugin = source("HGLootPlugin");
        String roulette = source("MinigameRouletteService");
        assertTrue(plugin.contains("Are you sure? Run /hub again to return to Hub for this round. "));
        assertTrue(plugin.contains("Use /leave to exit Minigame Roulette entirely."));
        assertTrue(plugin.contains("Are you sure? Run /hub again to leave this match."));
        assertTrue(plugin.contains("minigameRouletteService.returnCurrentRoundPlayerToHub(player)"));
        assertTrue(plugin.contains("minigameRouletteService.leaveTournament(player)"));
        assertFalse(plugin.contains("minigameRouletteService.blockTournamentExit(player)"));
        assertTrue(roulette.contains("roster.remove(playerId)"));
        assertTrue(roulette.contains("roundHubPlayers.clear()"));
    }

    @Test
    void everyLivingForfeitRemovesItsActiveSlotBeforeHubCleanup() throws Exception {
        String bridge = source("BridgeService");
        String spleef = source("SpleefService");
        String voidRoulette = source("VoidRouletteService");
        String bossRush = source("BossRushService");
        assertTrue(bridge.contains("isBridgeAlive(player.getUniqueId())")
            && bridge.contains("&& leaveToHub(player)"));
        assertTrue(bridge.contains("playerTeams.remove(playerId)"));
        assertTrue(spleef.contains("eliminate(player, null, \"left the round\");"));
        assertTrue(voidRoulette.contains("eliminate(player, null, \"left the round\");"));
        int forfeit = bossRush.indexOf("public boolean forfeitTournamentPlayerToHub");
        int eliminate = bossRush.indexOf("eliminate(player);", forfeit);
        int protect = bossRush.indexOf("player.setInvulnerable(true);", forfeit);
        int returnToHub = bossRush.indexOf("completeTournamentReturnToHub(player);", forfeit);
        assertTrue(forfeit >= 0 && eliminate > forfeit && protect > eliminate
            && returnToHub > protect);
        assertTrue(bossRush.contains("if (spectatorInHub.contains(playerId)) continue;"));
    }

    private static String source(String className) throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/" + className + ".java"));
    }
}
