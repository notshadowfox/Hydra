package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoulettePodiumDialogTest {
    @Test
    void snapshotDefensivelyCopiesStandingsAndWinners() {
        List<RoulettePodiumDialog.Standing> standings = new ArrayList<>();
        standings.add(new RoulettePodiumDialog.Standing(1, RouletteTeam.BLUE, 34, 2));
        List<RouletteTeam> winners = new ArrayList<>(List.of(RouletteTeam.BLUE));

        RoulettePodiumDialog.Snapshot snapshot = new RoulettePodiumDialog.Snapshot(
            standings, winners, false);
        standings.clear();
        winners.clear();

        assertEquals(1, snapshot.standings().size());
        assertEquals(List.of(RouletteTeam.BLUE), snapshot.winners());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.winners().add(RouletteTeam.RED));
    }

    @Test
    void sharedVictoryCanUseTiedPodiumPlacements() {
        RoulettePodiumDialog.Snapshot snapshot = new RoulettePodiumDialog.Snapshot(List.of(
            new RoulettePodiumDialog.Standing(1, RouletteTeam.RED, 30, 1),
            new RoulettePodiumDialog.Standing(1, RouletteTeam.BLUE, 30, 1),
            new RoulettePodiumDialog.Standing(3, RouletteTeam.GREEN, 22, 0),
            new RoulettePodiumDialog.Standing(4, RouletteTeam.YELLOW, 18, 0)
        ), List.of(RouletteTeam.RED, RouletteTeam.BLUE), false);

        assertEquals(2, snapshot.winners().size());
        assertEquals(1, snapshot.standings().get(1).placement());
        assertEquals(3, snapshot.standings().get(2).placement());
    }
}
