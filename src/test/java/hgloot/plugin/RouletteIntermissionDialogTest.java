package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hgloot.plugin.ManagedMinigameRound.Game;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouletteIntermissionDialogTest {
    @Test
    void ordinalHandlesCommonAndTeenPlacements() {
        assertEquals("1st", RouletteIntermissionDialog.ordinal(1));
        assertEquals("2nd", RouletteIntermissionDialog.ordinal(2));
        assertEquals("3rd", RouletteIntermissionDialog.ordinal(3));
        assertEquals("4th", RouletteIntermissionDialog.ordinal(4));
        assertEquals("11th", RouletteIntermissionDialog.ordinal(11));
        assertEquals("12th", RouletteIntermissionDialog.ordinal(12));
        assertEquals("13th", RouletteIntermissionDialog.ordinal(13));
    }

    @Test
    void snapshotDefensivelyCopiesStandingsAndResults() {
        EnumMap<RouletteTeam, Integer> points = new EnumMap<>(RouletteTeam.class);
        points.put(RouletteTeam.BLUE, 19);
        RouletteIntermissionDialog.ResultLine result = new RouletteIntermissionDialog.ResultLine(
            1, "Player", RouletteTeam.BLUE, 11, 8, 3, 3, false);
        RouletteIntermissionDialog.Snapshot snapshot = new RouletteIntermissionDialog.Snapshot(
            Game.SPLEEF, "Bridge", 2, points, List.of(result), "");

        points.put(RouletteTeam.BLUE, 99);

        assertEquals(19, snapshot.teamPoints().get(RouletteTeam.BLUE));
        assertEquals(List.of(result), snapshot.results());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.teamPoints().put(RouletteTeam.RED, 1));
    }

    @Test
    void scoreBreakdownShowsSoloAndBridgeBonusesWithoutFractions() {
        RouletteIntermissionDialog.ResultLine solo = new RouletteIntermissionDialog.ResultLine(
            1, "Player", RouletteTeam.BLUE, 11, 8, 3, 3, false);
        RouletteIntermissionDialog.ResultLine bridge = new RouletteIntermissionDialog.ResultLine(
            1, "Blue Team", RouletteTeam.BLUE, 34, 8, 12, 2, true);

        assertEquals("1st Place +8 | 3 Kills +3", RouletteIntermissionDialog.scoreBreakdown(solo));
        assertEquals("1st Place +8 | 12 Kills | Kill Bonus +2",
            RouletteIntermissionDialog.scoreBreakdown(bridge));
    }

}
