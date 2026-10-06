package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouletteScoringTest {
    @Test
    void fourTeamPlacementsAreEightSixFourTwoForEveryMode() {
        int[] expected = {8, 6, 4, 2, 0};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], RouletteScoring.teamPlacementPoints(i));
            assertEquals(expected[i], RouletteScoring.placementOnlyAward(i, 12).totalPoints());
            assertEquals(expected[i], RouletteScoring.bridgeAward(i, 0).totalPoints());
            assertEquals(expected[i], RouletteScoring.eliminationAward(i, 0).totalPoints());
        }
    }

    @Test
    void spleefAndVoidEliminationsAreUncappedTeamPoints() {
        UUID blueFirst = UUID.randomUUID();
        UUID blueSecond = UUID.randomUUID();
        UUID red = UUID.randomUUID();
        Map<UUID, RouletteTeam> teams = Map.of(
            blueFirst, RouletteTeam.BLUE, blueSecond, RouletteTeam.BLUE, red, RouletteTeam.RED);

        assertEquals(List.of(RouletteTeam.BLUE, RouletteTeam.RED, RouletteTeam.GREEN, RouletteTeam.YELLOW),
            RouletteScoring.teamPlacementsFromPlayers(List.of(blueFirst, blueSecond, red), teams));
        assertEquals(Map.of(RouletteTeam.BLUE, 13, RouletteTeam.RED, 1),
            RouletteScoring.teamEliminations(Map.of(blueFirst, 9, blueSecond, 4, red, 1), teams));
        assertEquals(21, RouletteScoring.eliminationAward(0, 13).totalPoints());
        assertEquals(13, RouletteScoring.eliminationAward(0, 13).bonusPoints());
        assertEquals(8, RouletteScoring.eliminationAward(0, 13).placementPoints());
    }

    @Test
    void bridgeBonusUsesFourKillBucketsAndCapsAtTwoWithoutMultiplyingByMembers() {
        assertEquals(0, RouletteScoring.bridgeKillBonus(3));
        assertEquals(1, RouletteScoring.bridgeKillBonus(4));
        assertEquals(1, RouletteScoring.bridgeKillBonus(7));
        assertEquals(2, RouletteScoring.bridgeKillBonus(8));
        assertEquals(2, RouletteScoring.bridgeKillBonus(20));
        assertEquals(10, RouletteScoring.bridgeAward(0, 20).totalPoints());
    }
}
