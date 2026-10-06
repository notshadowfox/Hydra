package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@SuppressWarnings("deprecation")
final class PartyTeamChatTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private PartyService parties;
    private TeamProtectionService teams;
    private HydraPlusService chat;
    private PlayerMock first;
    private PlayerMock second;
    private PlayerMock outsider;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = mock(HGLootPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        parties = new PartyService();
        teams = mock(TeamProtectionService.class);
        when(plugin.partyService()).thenReturn(parties);
        when(plugin.teamProtectionService()).thenReturn(teams);
        first = server.addPlayer("First");
        second = server.addPlayer("Second");
        outsider = server.addPlayer("Outsider");
        chat = new HydraPlusService(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void partyChatRoutesOutgoingOnlyAndKeepsIncomingPublicVisible() {
        createParty();

        chat.togglePartyChat(first);
        assertEquals("Seeing messages from party", plain(first.nextMessage()));

        AsyncPlayerChatEvent privateEvent = chatEvent(first, "Hello Bob");
        chat.onAsyncPlayerChat(privateEvent);
        assertTrue(privateEvent.isCancelled());
        assertPrivateMessage(first.nextMessage(), "[Party]", "First", "Hello Bob");
        assertPrivateMessage(second.nextMessage(), "[Party]", "First", "Hello Bob");
        assertNull(outsider.nextMessage());

        AsyncPlayerChatEvent publicEvent = chatEvent(outsider, "Hello everyone");
        chat.onAsyncPlayerChat(publicEvent);
        assertFalse(publicEvent.isCancelled());
        assertEquals(3, publicEvent.getRecipients().size());
        assertTrue(publicEvent.getRecipients().contains(first));
    }

    @Test
    void incomingPartyMessagesDoNotRequirePartyMode() {
        createParty();
        chat.togglePartyChat(first);
        first.nextMessage();

        AsyncPlayerChatEvent event = chatEvent(first, "private");
        chat.onAsyncPlayerChat(event);

        assertPrivateMessage(second.nextMessage(), "[Party]", "First", "private");
        assertNull(outsider.nextMessage());
    }

    @Test
    void asynchronousPrivateChatIsDeliveredOnTheServerThread() {
        createParty();
        chat.togglePartyChat(first);
        first.nextMessage();
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
            true, first, "scheduled", new HashSet<>(server.getOnlinePlayers()));

        chat.onAsyncPlayerChat(event);
        assertTrue(event.isCancelled());
        assertNull(first.nextMessage());
        assertNull(second.nextMessage());

        server.getScheduler().performOneTick();
        assertPrivateMessage(first.nextMessage(), "[Party]", "First", "scheduled");
        assertPrivateMessage(second.nextMessage(), "[Party]", "First", "scheduled");
    }

    @Test
    void teamChatUsesAuthoritativeRecipientsAndReplacesPartyMode() {
        createParty();
        Set<UUID> duo = Set.of(first.getUniqueId(), second.getUniqueId());
        when(teams.currentTeamMembers(eq(first), anyCollection())).thenReturn(duo);
        when(teams.currentTeamMembers(eq(second), anyCollection())).thenReturn(duo);

        chat.togglePartyChat(first);
        assertEquals("Seeing messages from party", plain(first.nextMessage()));
        chat.toggleTeamChat(first);
        assertEquals("Seeing messages from team", plain(first.nextMessage()));

        AsyncPlayerChatEvent event = chatEvent(first, "team only");
        chat.onAsyncPlayerChat(event);
        assertTrue(event.isCancelled());
        assertPrivateMessage(first.nextMessage(), "[Team]", "First", "team only");
        assertPrivateMessage(second.nextMessage(), "[Team]", "First", "team only");
        assertNull(outsider.nextMessage());

        chat.toggleTeamChat(first);
        assertEquals("Seeing messages from public chat", plain(first.nextMessage()));
        AsyncPlayerChatEvent publicEvent = chatEvent(first, "public again");
        chat.onAsyncPlayerChat(publicEvent);
        assertFalse(publicEvent.isCancelled());
        assertEquals(3, publicEvent.getRecipients().size());
    }

    @Test
    void partyDisbandDropsMessageAndReturnsModeToPublic() {
        createParty();
        chat.togglePartyChat(first);
        first.nextMessage();
        assertEquals(PartyService.DisbandResult.DISBANDED, parties.disband(first.getUniqueId()));

        AsyncPlayerChatEvent event = chatEvent(first, "must not leak");
        chat.onAsyncPlayerChat(event);

        assertTrue(event.isCancelled());
        assertEquals("[!] Party chat is no longer available. Your message was not sent.", plain(first.nextMessage()));
        assertNull(second.nextMessage());
        assertNull(outsider.nextMessage());

        AsyncPlayerChatEvent next = chatEvent(first, "public next time");
        chat.onAsyncPlayerChat(next);
        assertFalse(next.isCancelled());
    }

    @Test
    void teamChangeDropsMessageWithoutGlobalFallback() {
        Set<UUID> duo = Set.of(first.getUniqueId(), second.getUniqueId());
        when(teams.currentTeamMembers(eq(first), anyCollection())).thenReturn(duo);
        chat.toggleTeamChat(first);
        first.nextMessage();
        when(teams.currentTeamMembers(eq(first), anyCollection())).thenReturn(Set.of());

        AsyncPlayerChatEvent event = chatEvent(first, "must not leak");
        chat.onAsyncPlayerChat(event);

        assertTrue(event.isCancelled());
        assertEquals("[!] Team chat is no longer available. Your message was not sent.", plain(first.nextMessage()));
        assertNull(second.nextMessage());
        assertNull(outsider.nextMessage());
    }

    @Test
    void disconnectClearsPrivateOutgoingMode() {
        createParty();
        chat.togglePartyChat(first);
        first.nextMessage();

        chat.onPlayerQuit(new PlayerQuitEvent(first, net.kyori.adventure.text.Component.empty()));
        chat.refreshNow();
        AsyncPlayerChatEvent event = chatEvent(first, "public after reset");
        chat.onAsyncPlayerChat(event);

        assertFalse(event.isCancelled());
        assertEquals(3, event.getRecipients().size());
    }

    @Test
    void privateMessagesAreDeliveredExactlyOnce() {
        createParty();
        chat.togglePartyChat(first);
        first.nextMessage();

        chat.onAsyncPlayerChat(chatEvent(first, "once"));

        assertPrivateMessage(first.nextMessage(), "[Party]", "First", "once");
        assertNull(first.nextMessage());
        assertPrivateMessage(second.nextMessage(), "[Party]", "First", "once");
        assertNull(second.nextMessage());
    }

    @Test
    void invalidPrivateChannelsDoNotActivate() {
        when(teams.currentTeamMembers(eq(first), anyCollection())).thenReturn(Set.of());
        chat.togglePartyChat(first);
        assertEquals("You are not in a party.", plain(first.nextMessage()));
        chat.toggleTeamChat(first);
        assertEquals("You are not on a team.", plain(first.nextMessage()));
    }

    @Test
    void publicSpectatorRestrictionsRemainIntact() {
        QueueService queues = mock(QueueService.class);
        when(plugin.queueService()).thenReturn(queues);
        when(queues.playerAudienceKey(any(Player.class))).thenReturn("match:test");
        when(queues.isMatchAudienceSpectator(first.getUniqueId())).thenReturn(false);
        when(queues.isMatchAudienceSpectator(second.getUniqueId())).thenReturn(false);
        when(queues.isMatchAudienceSpectator(outsider.getUniqueId())).thenReturn(true);
        chat.refreshNow();

        AsyncPlayerChatEvent event = chatEvent(first, "alive chat");
        chat.onAsyncPlayerChat(event);

        assertTrue(event.getRecipients().contains(first));
        assertTrue(event.getRecipients().contains(second));
        assertFalse(event.getRecipients().contains(outsider));
    }

    private void createParty() {
        assertEquals(PartyService.CreateResult.CREATED, parties.createParty(first.getUniqueId()));
        assertEquals(PartyService.InviteResult.SENT, parties.invite(first.getUniqueId(), second.getUniqueId()));
        assertEquals(PartyService.InviteResponse.ACCEPTED,
            parties.acceptInvite(second.getUniqueId(), first.getUniqueId()));
    }

    private AsyncPlayerChatEvent chatEvent(Player speaker, String message) {
        return new AsyncPlayerChatEvent(false, speaker, message, new HashSet<>(server.getOnlinePlayers()));
    }

    private static String plain(String message) {
        return org.bukkit.ChatColor.stripColor(message == null ? "" : message)
            .replaceAll("[\\uE000-\\uF8FF]", "")
            .trim();
    }

    private static void assertPrivateMessage(String actual, String prefix, String sender, String message) {
        String plain = plain(actual);
        assertTrue(plain.contains(prefix), plain);
        assertTrue(plain.contains(sender + ": " + message), plain);
    }
}
