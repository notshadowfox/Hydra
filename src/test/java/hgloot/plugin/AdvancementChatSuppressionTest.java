package hgloot.plugin;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.junit.jupiter.api.Test;

class AdvancementChatSuppressionTest {
    @Test
    void suppressesTheAdvancementBroadcastWithoutTouchingToastOrProgress() {
        HGLootPlugin plugin = mock(HGLootPlugin.class, CALLS_REAL_METHODS);
        PlayerAdvancementDoneEvent event = mock(PlayerAdvancementDoneEvent.class);
        plugin.onPlayerAdvancementDone(event);
        verify(event).message(null);
    }
}
