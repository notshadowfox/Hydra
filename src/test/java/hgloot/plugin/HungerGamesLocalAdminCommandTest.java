package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HungerGamesLocalAdminCommandTest {
    private HGLootPlugin plugin;
    private QueueService queues;
    private Player player;

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(HGLootPlugin.class);
        queues = mock(QueueService.class);
        player = mock(Player.class);
        when(player.hasPermission("hgloot.admin")).thenReturn(true);
        var field = HGLootPlugin.class.getDeclaredField("queueService");
        field.setAccessible(true);
        field.set(plugin, queues);
    }

    @Test
    void exactLocalDuoIdIsForwardedOnlyToLocalQueueService() throws Exception {
        call("forcestart", "duos", "hg-1");
        verify(queues).forceStartHungerGames(true, "hg-1", player);
        verifyNoMoreInteractions(queues);
    }

    @Test
    void soloSelectionWithoutIdMeansCurrentSoloQueue() throws Exception {
        call("forcestart", "solos");
        verify(queues).forceStartHungerGames(false, null, player);
        verifyNoMoreInteractions(queues);
    }

    @Test
    void missingModeAndMalformedArgumentsCannotStartAnything() throws Exception {
        call("forcestart");
        call("forcestart", "hg-1");
        call("forcestart", "duos", "hg-1", "unexpected");
        verify(queues, never()).forceStartHungerGames(anyBoolean(), any(), any());
    }

    @Test
    void permissionIsCheckedBeforeStartingOrListingLobbies() throws Exception {
        when(player.hasPermission("hgloot.admin")).thenReturn(false);
        call("forcestart", "duos", "hg-1");
        verifyNoInteractions(queues);
    }

    @Test
    void completionListsOnlyIdsForRequestedMode() throws Exception {
        when(queues.hungerGamesLocalLobbyIds(true)).thenReturn(List.of("hg-2", "hg-12"));
        Method method = HGLootPlugin.class.getDeclaredMethod("completeHungerGamesForceStart", String[].class);
        method.setAccessible(true);
        assertEquals(List.of("hg-12"), method.invoke(plugin, (Object) new String[]{"forcestart", "duos", "hg-1"}));
        verify(queues).hungerGamesLocalLobbyIds(true);
        verify(queues, never()).hungerGamesLocalLobbyIds(false);
    }

    @Test
    void forceEndCompletionUsesActiveLocalWorldsInsteadOfLegacyQueueKey() throws Exception {
        when(queues.activeHungerGamesWorldNames()).thenReturn(List.of("HG-World-1", "HG-World-2"));
        Method method = HGLootPlugin.class.getDeclaredMethod("completeAdminQueueAlias",
            org.bukkit.command.CommandSender.class, String[].class, QueueService.QueueType.class);
        method.setAccessible(true);
        assertEquals(List.of("HG-World-2"), method.invoke(plugin, player,
            new String[]{"hg", "forceend", "HG-World-2"}, QueueService.QueueType.HUNGERGAMES));
    }

    @Test
    void modeParserAcceptsSingularAndPluralButNeverBackendIds() {
        assertEquals(false, HGLootPlugin.hungerGamesDuosArgument("solo"));
        assertEquals(false, HGLootPlugin.hungerGamesDuosArgument("SOLOS"));
        assertEquals(true, HGLootPlugin.hungerGamesDuosArgument("duo"));
        assertEquals(true, HGLootPlugin.hungerGamesDuosArgument("DUOS"));
        assertNull(HGLootPlugin.hungerGamesDuosArgument("HG-1"));
    }

    private void call(String... args) throws Exception {
        Method method = HGLootPlugin.class.getDeclaredMethod("handleHungerGamesForceStart", Player.class, String[].class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(plugin, player, args));
    }
}
