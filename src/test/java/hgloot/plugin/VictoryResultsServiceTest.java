package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VictoryResultsServiceTest {
    @Test
    void interpolationIsBoundedAndFinishesAtTarget() {
        assertEquals(0, VictoryResultsService.interpolate(850, 17, 17, 37));
        assertEquals(850, VictoryResultsService.interpolate(850, 37, 17, 37));
        assertEquals(0, VictoryResultsService.interpolate(0, 25, 17, 37));

        int previous = 0;
        for (int tick = 17; tick <= 37; tick++) {
            int value = VictoryResultsService.interpolate(850, tick, 17, 37);
            assertTrue(value >= previous, "tally must never count backward");
            assertTrue(value <= 850, "tally must never exceed its granted reward");
            previous = value;
        }
    }

    @Test
    void presentationTimelineStaysInsideSixSeconds() {
        assertEquals(0, VictoryResultsService.VICTORY_TICK);
        assertTrue(VictoryResultsService.COMPLETE_TICK >= 100);
        assertTrue(VictoryResultsService.COMPLETE_TICK <= 120);
        assertTrue(VictoryResultsService.XP_END_TICK < VictoryResultsService.COINS_START_TICK);
        assertTrue(VictoryResultsService.COINS_END_TICK < VictoryResultsService.FRAGMENTS_START_TICK);
    }

    @Test
    void presentationServiceCannotGrantRewards() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/VictoryResultsService.java"));
        assertTrue(!source.contains("grantPrestigeXp("));
        assertTrue(!source.contains("grantShardFragments("));
        assertTrue(!source.contains("grantSilver("));
        assertTrue(!source.contains("queueSilver("));
        assertTrue(source.contains("labeledValue(\"EXP\""));
        assertTrue(source.contains("active.ordinal() >= Phase.COINS.ordinal()"));
        assertTrue(source.contains("active.ordinal() >= Phase.FRAGMENTS.ordinal()"));
    }

    @Test
    void rouletteUsesSingleRewardSnapshotForGrantAndPresentation() throws IOException {
        String roulette = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        assertTrue(roulette.contains("RewardSnapshot reward = plugin.queueService().recordMinigameRouletteWin(playerId)"));
        assertTrue(roulette.contains("showVictory(player, reward)"));
        assertTrue(!roulette.contains("grantShardFragments(playerId, 4, \"MINIGAME_ROULETTE_WIN\")"));
    }

    @Test
    void simulatedHgWinOnlyInvokesPresentation() throws IOException {
        String plugin = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/HGLootPlugin.java"));
        int commandStart = plugin.indexOf("if (sub.equals(\"swin\"))");
        int commandEnd = plugin.indexOf("if (sub.equals(\"addplayer\"))", commandStart);
        assertTrue(commandStart >= 0 && commandEnd > commandStart);
        String command = plugin.substring(commandStart, commandEnd);
        assertTrue(command.contains("victoryResultsService.showVictory("));
        assertTrue(command.contains("new VictoryResultsService.RewardSnapshot(850, 400, 3, null)"));
        assertTrue(!command.contains("grant"));
        assertTrue(!command.contains("recordLifetimeWin"));
        assertTrue(!command.contains("finishMatch"));
    }
}
