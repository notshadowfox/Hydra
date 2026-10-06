package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouletteRoundRulesTest {
    @Test
    void twoSurvivorsOnSameTeamEndRound() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ManagedMinigameRound.Request round = request(List.of(first, second),
            Map.of(first, RouletteTeam.BLUE, second, RouletteTeam.BLUE));

        assertTrue(RouletteRoundRules.onlyOneTeamRemains(List.of(first, second), round));
    }

    @Test
    void survivorsOnDifferentTeamsKeepPlaying() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ManagedMinigameRound.Request round = request(List.of(first, second),
            Map.of(first, RouletteTeam.BLUE, second, RouletteTeam.RED));

        assertFalse(RouletteRoundRules.onlyOneTeamRemains(List.of(first, second), round));
    }

    @Test
    void soloAndUnmanagedRoundsUseNormalWinnerRules() {
        UUID player = UUID.randomUUID();
        ManagedMinigameRound.Request round = request(List.of(player), Map.of(player, RouletteTeam.BLUE));

        assertFalse(RouletteRoundRules.onlyOneTeamRemains(List.of(player), round));
        assertFalse(RouletteRoundRules.onlyOneTeamRemains(List.of(player, UUID.randomUUID()), null));
    }

    private ManagedMinigameRound.Request request(List<UUID> roster, Map<UUID, RouletteTeam> teams) {
        return new ManagedMinigameRound.Request(UUID.randomUUID(), UUID.randomUUID(),
            ManagedMinigameRound.Game.SPLEEF, roster, teams, ignored -> { });
    }
}
