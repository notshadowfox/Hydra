package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the mode-specific audience and phase boundaries of the presentation pass. */
final class MatchLifecyclePresentationRoutingTest {
    @Test
    void countdownsShareCuesWithoutAddingTicksOrChangingModeTitles() throws Exception {
        for (String service : new String[] {"SpleefService", "BridgeService", "VoidRouletteService",
            "BossRushService", "QueueService"}) {
            assertTrue(source(service).contains("HydraSounds.playCountdownTick("), service);
            assertTrue(source(service).contains("HydraSounds.playGo("), service);
        }
        assertTrue(source("BridgeService").contains("START_FREEZE_SECONDS = 5"));
        assertTrue(source("VoidRouletteService").contains("START_FREEZE_SECONDS = 5"));
        assertTrue(source("SpleefService").contains("GRACE_SECONDS = 5"));
    }

    @Test
    void bridgeNonfinalGoalFeedbackIsScopedToScoringAndScoredOnTeams() throws Exception {
        String score = between(source("BridgeService"), "private void handleScore(",
            "private void resetInvalidGoal(");
        assertTrue(score.contains("if (score < WIN_SCORE)"));
        assertTrue(score.contains("if (team == scorerTeam)"));
        assertTrue(score.contains("else if (team == scoredOn)"));
        assertTrue(score.contains("HydraSounds.playRoundResult(participant)"));
        assertTrue(score.contains("HydraSounds.playRoundLoss(participant)"));
        assertFalse(score.contains("HydraSounds.playGameWin(participant)"));
    }

    @Test
    void bridgeFinalCreditsStayUnchangedButAllWinningTeammatesSeeVictory() throws Exception {
        String result = between(source("BridgeService"), "private void finishMatch(",
            "private void cleanupPlayerForHub(");
        assertTrue(result.contains("recordBridgeWin(winnerId)"));
        assertTrue(result.contains("if (playerTeams.get(player.getUniqueId()) == winner)" )
            || result.contains("boolean won = playerTeams.get(player.getUniqueId()) == winner;"));
        assertTrue(result.contains("if (request != null)"));
        assertTrue(result.contains("ROUND COMPLETE"));
        assertTrue(result.contains("HydraSounds.playGameWin(player)"));
        assertTrue(source("BridgeService").contains("HydraSounds.playSpectatorEnter(eliminatedPlayer)"));
        assertTrue(source("BridgeService").contains("HydraSounds.playSpectatorEnter(player)"));
    }

    @Test
    void bossRushChildResultAndStandaloneFinalRemainDistinctAndParticipantOnly() throws Exception {
        String result = between(source("BossRushService"), "private void finishMatch(",
            "private List<RouletteTeam> activeTeamsByDamage(");
        assertTrue(result.contains("for (UUID playerId : participants)"));
        assertTrue(result.contains("if (managed)"));
        assertTrue(result.contains("ROUND COMPLETE"));
        assertTrue(result.contains("VICTORY"));
        assertTrue(result.contains("DEFEAT"));
        assertTrue(result.contains("HydraSounds.playRoundResult(player)"));
        assertTrue(result.contains("HydraSounds.playGameWin(player)"));
        assertTrue(result.contains("HydraSounds.playGameLoss(player)"));
        String respawn = between(source("BossRushService"), "public void onRespawn(",
            "public void onQuit(");
        assertTrue(respawn.contains("prepareSpectator(player)"));
        assertTrue(respawn.contains("state == State.ACTIVE && !alivePlayers.contains"));
        assertTrue(respawn.contains("HydraSounds.playSpectatorEnter(player)"));
    }

    @Test
    void rouletteChildResultRemainsOncePerPlayerAndRecipeEntryKeepsBookCue() throws Exception {
        String mr = source("MinigameRouletteService");
        assertTrue(mr.contains("intermissionCuePlayed.add(player.getUniqueId())"));
        assertTrue(mr.contains("HydraSounds.playRoundResult(player)"));
        assertTrue(source("gui/LegendaryRecipeBookService").contains(
            "case OpenRecipe ignored -> HydraSounds.UiCue.RECIPE_SELECT"));
    }

    @Test
    void hgAndBattleRoyaleDeathRespawnAnnouncesSpectatingWithoutChangingExternalSpec() throws Exception {
        String queue = source("QueueService");
        String respawn = between(queue, "public void onPlayerRespawn(", "public void onPlayerPortal(");
        assertTrue(respawn.contains("setCustomSpectatorState(player, worldName, true)"));
        assertTrue(respawn.contains("HydraSounds.playSpectatorEnter(player)"));
        String externalSpec = between(queue, "public boolean spectateTargetMatch(",
            "private boolean transitionTravel(");
        assertFalse(externalSpec.contains("HydraSounds.playSpectatorEnter"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from, "Expected method boundaries");
        return source.substring(from, to);
    }

    private static String source(String service) throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/" + service + ".java"));
    }
}
