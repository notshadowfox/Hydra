package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hgloot.plugin.ManagedMinigameRound.Game;
import hgloot.plugin.ManagedMinigameRound.Request;
import hgloot.plugin.ManagedMinigameRound.Result;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagedMinigameRoundTest {
    @Test
    void successfulResultDefensivelyCopiesEliminations() {
        UUID playerId = UUID.randomUUID();
        Request request = request(Game.SPLEEF, playerId);
        Map<UUID, Integer> eliminations = new HashMap<>();
        eliminations.put(playerId, 4);

        Result result = Result.players(request, List.of(playerId), eliminations);
        eliminations.put(playerId, 99);

        assertEquals(4, result.playerEliminations().get(playerId));
        assertThrows(UnsupportedOperationException.class,
            () -> result.playerEliminations().put(UUID.randomUUID(), 1));
    }

    @Test
    void failedResultCannotCarryPartialScoring() {
        UUID playerId = UUID.randomUUID();
        Result result = Result.failed(request(Game.VOID_ROULETTE, playerId), "failed");

        assertEquals(Map.of(), result.playerEliminations());
        assertEquals(Map.of(), result.teamEliminations());
        assertEquals(Map.of(), result.teamObjectiveScores());
    }

    @Test
    void negativeEliminationsAreRejected() {
        UUID playerId = UUID.randomUUID();
        Request request = request(Game.SPLEEF, playerId);

        assertThrows(IllegalArgumentException.class,
            () -> Result.players(request, List.of(playerId), Map.of(playerId, -1)));
    }

    private Request request(Game game, UUID playerId) {
        return new Request(UUID.randomUUID(), UUID.randomUUID(), game, List.of(playerId),
            Map.of(playerId, RouletteTeam.RED), ignored -> { });
    }
}
