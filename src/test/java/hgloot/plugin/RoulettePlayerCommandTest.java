package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoulettePlayerCommandTest {
    @Test
    void publicRouletteCommandUsesTheNavigatorJoinPath() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        String descriptor = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(plugin.contains("commandRoutes.put(\"roulette\""));
        assertTrue(plugin.contains("completePlayerCommand(args, sender.hasPermission(\"hgloot.admin.roulette\"))"));
        assertTrue(roulette.contains("public boolean handlePlayerCommand"));
        assertTrue(roulette.contains("joinFromNavigator(player)"));
        assertTrue(roulette.contains("leaveQueue(player)"));
        assertTrue(roulette.contains("case \"ignorepr\""));
        assertTrue(roulette.contains("startTournament(sender, 1)"));
        assertTrue(!roulette.contains("case \"solo\""));
        assertTrue(descriptor.contains("description: Minigame Roulette queue commands"));
    }

    @Test
    void ignorePlayerRequirementsStartsTheWholeExistingQueue() throws Exception {
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        int start = roulette.indexOf("private void startIgnoringPlayerRequirements");
        int end = roulette.indexOf("private void skipActiveRound", start);
        String method = roulette.substring(start, end);
        assertTrue(method.contains("startTournament(sender, 1)"));
        assertTrue(!method.contains("requires an empty Roulette queue"));
        assertTrue(!method.contains("joinQueue(player"));
    }

    @Test
    void stoppingAQueuedRouletteReturnsGameLobbyPlayersToHub() throws Exception {
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        int start = roulette.indexOf("private void stopTournament");
        int end = roulette.indexOf("private boolean isBusyElsewhere", start);
        String method = roulette.substring(start, end);
        assertTrue(method.contains("plugin.queueService().sendPlayerToHub(player)"));
    }
}
