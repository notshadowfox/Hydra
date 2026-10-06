package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level contract for deliberate handoffs; visual coverage still needs a client. */
final class PresentationTravelCoverageTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/" + name + ".java"));
    }

    @Test void gameHubAndMainHubHandoffsUseTheSharedMidpoint() throws Exception {
        String queue = source("QueueService");
        String gameHub = queue.substring(queue.indexOf("public boolean sendPlayerToGameHub(Player player, Runnable onArrival)"),
            queue.indexOf("private void requestPrewarmAfterPlayerStaging"));
        assertTrue(gameHub.contains("transitionTravel(player, arrival)"));
        assertTrue(gameHub.contains("hydra.gameHubService().enter(player)"));
        assertTrue(gameHub.contains("if (entered && onArrival != null) onArrival.run()"));
        String hub = queue.substring(queue.indexOf("private void returnToHubPresented("),
            queue.indexOf("private void returnToHub(Player player", queue.indexOf("private void returnToHubPresented(")));
        assertTrue(hub.contains("transitionTravel(player, () -> returnToHub(player, resetGamemode))"));
    }

    @Test void matchEntriesWaitForBlackMidpointsBeforeStartingCountdowns() throws Exception {
        String spleef = source("SpleefService");
        String bridge = source("BridgeService");
        String roulette = source("VoidRouletteService");
        String hungerGames = source("QueueService");
        for (String mode : new String[] {spleef, bridge, roulette}) {
            assertTrue(mode.contains("plugin.transitionTravel(player, () ->"));
            assertTrue(mode.contains("beginAfterArrival.run()"));
            assertTrue(mode.contains("plugin.transitionTravelGroup(returning, this::cleanupPlayerForHub"));
        }
        assertTrue(spleef.contains("startGraceCountdown();"));
        assertTrue(bridge.contains("startStartFreezeCountdown();"));
        assertTrue(roulette.contains("startStartFreezeCountdown();"));
        assertTrue(hungerGames.contains("finishAfterArrival[0].run()"));
        assertTrue(hungerGames.contains("transitionTravel(player, arrival)"));
        assertTrue(hungerGames.contains("hydra.transitionTravelGroup(returning, player -> returnToHub(player, true), finish)"));
        assertTrue(source("BossRushService").contains("plugin.transitionTravel(player, () ->"));
        assertTrue(source("BossRushService").contains("plugin.transitionTravelGroup(returning, player ->"));
    }

    @Test void presentedExitsAndKitEditorRemainConnected() throws Exception {
        String plugin = source("HGLootPlugin");
        String roulette = source("MinigameRouletteService");
        String editor = source("KitEditorService");
        assertTrue(plugin.contains("transitionTravel(player, () -> spleefService.leaveToHub(player))"));
        assertTrue(plugin.contains("transitionTravel(player, () -> bridgeService.leaveToHub(player))"));
        assertTrue(plugin.contains("transitionTravel(player, () -> voidRouletteService.leaveToHub(player))"));
        assertTrue(roulette.contains("returnPlayerToHydraHubPresented(player)"));
        assertTrue(roulette.contains("sendPlayerToGameHub(player, () ->"));
        assertTrue(editor.contains("transitions.transition(player, TransitionService.Preset.NORMAL"));
        assertTrue(source("SpleefService").contains("if (rejoinSnapshots.get(playerId) == snapshot && snapshot.isRestorable())"));
        assertTrue(source("VoidRouletteService").contains("if (rejoinSnapshots.get(playerId) == snapshot && snapshot.isRestorable(this))"));
    }
}
