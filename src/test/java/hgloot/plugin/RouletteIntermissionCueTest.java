package hgloot.plugin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class RouletteIntermissionCueTest {
    @Test
    @SuppressWarnings("unchecked")
    void resultCuePlaysOncePerPlayerPerIntermissionAndCanResetForNextRound() throws Exception {
        MinigameRouletteService service = new MinigameRouletteService(
            mock(HGLootPlugin.class), mock(PartyService.class));
        Player first = player();
        Player second = player();
        Player outsider = player();

        service.playIntermissionCueOnce(first);
        service.playIntermissionCueOnce(first);
        service.playIntermissionCueOnce(second);

        verify(first, times(1)).playSound(first.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);
        verify(second, times(1)).playSound(second.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);
        verify(outsider, times(0)).playSound(outsider.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);

        Field played = MinigameRouletteService.class.getDeclaredField("intermissionCuePlayed");
        played.setAccessible(true);
        ((Set<UUID>) played.get(service)).clear();
        service.playIntermissionCueOnce(first);
        verify(first, times(2)).playSound(first.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);
    }

    @Test
    void intermissionCueIsResetAtRoundStartAndTournamentCleanup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        String start = source.substring(source.indexOf("private void startIntermission()"),
            source.indexOf("private void finishTournament()"));
        String cleanup = source.substring(source.indexOf("private void cleanupTournament("),
            source.indexOf("private void removeIntermissionUi()"));
        org.junit.jupiter.api.Assertions.assertTrue(start.contains("intermissionCuePlayed.clear()"));
        org.junit.jupiter.api.Assertions.assertTrue(cleanup.contains("intermissionCuePlayed.clear()"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("player.showDialog(RouletteIntermissionDialog.create"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("playIntermissionCueOnce(player)"));
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        return player;
    }
}
