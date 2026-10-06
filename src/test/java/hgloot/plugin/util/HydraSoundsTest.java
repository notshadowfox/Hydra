package hgloot.plugin.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class HydraSoundsTest {
    @Test
    void rapidRepeatUiCuesAreThrottledPerPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long started = 1_000_000_000L;
        assertTrue(HydraSounds.allowUiCue(first, started));
        assertFalse(HydraSounds.allowUiCue(first, started + 69_000_000L));
        assertTrue(HydraSounds.allowUiCue(second, started + 1L));
        assertTrue(HydraSounds.allowUiCue(first, started + 70_000_000L));
    }

    @Test
    void uiCueIsOnlySentToTheInteractingPlayer() {
        Player actor = mock(Player.class);
        Player bystander = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(actor.isOnline()).thenReturn(true);
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
        when(actor.getLocation()).thenReturn(location);

        HydraSounds.playUi(actor, HydraSounds.UiCue.PAGE_FORWARD);

        verify(actor, times(1)).playSound(location,
            HydraSounds.UiCue.PAGE_FORWARD.sound, SoundCategory.MASTER,
            HydraSounds.UiCue.PAGE_FORWARD.volume, HydraSounds.UiCue.PAGE_FORWARD.pitch);
        org.mockito.Mockito.verifyNoInteractions(bystander);
    }

    @Test
    void queueJoinLeaveAndRejectionHaveDistinctPlayerOnlySounds() {
        Player actor = mock(Player.class);
        Player bystander = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(actor.isOnline()).thenReturn(true);
        when(actor.getLocation()).thenReturn(location);

        HydraSounds.playQueueJoin(actor);
        HydraSounds.playQueueLeave(actor);
        HydraSounds.playError(actor);

        verify(actor).playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85F, 1.2F);
        verify(actor).playSound(location, Sound.BLOCK_AMETHYST_CLUSTER_HIT,
            SoundCategory.MASTER, 0.75F, 0.85F);
        verify(actor).playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.75F);
        org.mockito.Mockito.verifyNoInteractions(bystander);
    }

    @Test
    void recipeEntrySharesTheBookPageSoundFamily() {
        assertEquals(Sound.ITEM_BOOK_PAGE_TURN, HydraSounds.UiCue.RECIPE_OPEN.sound);
        assertEquals(Sound.ITEM_BOOK_PAGE_TURN, HydraSounds.UiCue.RECIPE_SELECT.sound);
        assertEquals(Sound.ITEM_BOOK_PAGE_TURN, HydraSounds.UiCue.PAGE_BACK.sound);
        assertFalse(HydraSounds.UiCue.RECIPE_SELECT.sound == HydraSounds.UiCue.CONFIRM.sound);
    }

    @Test
    void roundResultIsQuietAndPlayerOnly() {
        Player actor = mock(Player.class);
        Player outsider = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(actor.isOnline()).thenReturn(true);
        when(actor.getLocation()).thenReturn(location);

        HydraSounds.playRoundResult(actor);

        verify(actor).playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);
        org.mockito.Mockito.verifyNoInteractions(outsider);
    }

    @Test
    void lifecycleCuesStayPlayerOnlyAndDistinguishRoundFromFinal() {
        Player actor = mock(Player.class);
        Player outsider = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(actor.isOnline()).thenReturn(true);
        when(actor.getLocation()).thenReturn(location);

        HydraSounds.playCountdownTick(actor, 3);
        HydraSounds.playGo(actor);
        HydraSounds.playRoundResult(actor);
        HydraSounds.playRoundLoss(actor);
        HydraSounds.playGameWin(actor);
        HydraSounds.playGameLoss(actor);
        HydraSounds.playSpectatorEnter(actor);

        verify(actor).playSound(location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.95F, 1.285F);
        verify(actor).playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.05F, 1.2F);
        verify(actor).playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);
        verify(actor).playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.58F, 0.92F);
        verify(actor).playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
        verify(actor).playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.68F, 0.88F);
        verify(actor).playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.62F, 1.12F);
        org.mockito.Mockito.verifyNoInteractions(outsider);
    }
}
