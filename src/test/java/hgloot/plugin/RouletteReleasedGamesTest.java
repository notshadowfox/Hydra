package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RouletteReleasedGamesTest {
    @Test
    void rouletteOrderSelectsBossRush() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/MinigameRouletteService.java"));

        String releasedOrder = source.substring(
            source.indexOf("List<Game> order ="),
            source.indexOf("Collections.shuffle(order)"));
        assertTrue(releasedOrder.contains("Game.BOSS_RUSH"));
    }

    @Test
    void managedGameContractContainsAllReleasedRouletteGames() {
        assertEquals(4, ManagedMinigameRound.Game.values().length);
    }
}
