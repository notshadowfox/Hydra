package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class PlayerSettingsSocialTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private PlayerSettingsService settings;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = mock(HGLootPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        settings = new PlayerSettingsService(plugin);
        when(plugin.playerSettingsService()).thenReturn(settings);
        doCallRealMethod().when(plugin).onPlayerDirectMessage(org.mockito.ArgumentMatchers.any());
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void disabledFriendRequestsRejectNewRequestsWithoutChangingExistingFriends() {
        Player sender = server.addPlayer("FriendSender");
        Player target = server.addPlayer("FriendTarget");
        FriendService friends = new FriendService(plugin);
        settings.set(target, PlayerSettingsService.Toggle.FRIEND_REQUESTS, false);
        assertEquals(FriendService.SendRequestResult.REQUESTS_DISABLED,
            friends.sendRequest(sender.getUniqueId(), target.getUniqueId()));
        assertTrue(friends.getIncomingRequests(target.getUniqueId()).isEmpty());
        settings.set(target, PlayerSettingsService.Toggle.FRIEND_REQUESTS, true);
        assertEquals(FriendService.SendRequestResult.SENT,
            friends.sendRequest(sender.getUniqueId(), target.getUniqueId()));
        assertEquals(FriendService.SendRequestResult.AUTO_ACCEPTED,
            friends.sendRequest(target.getUniqueId(), sender.getUniqueId()));
        settings.set(target, PlayerSettingsService.Toggle.FRIEND_REQUESTS, false);
        assertTrue(friends.areFriends(sender.getUniqueId(), target.getUniqueId()));
    }

    @Test
    void disabledPartyInvitesRejectNormalInvitesButDoNotDissolveParty() {
        Player leader = server.addPlayer("PartyLeader");
        Player target = server.addPlayer("PartyTarget");
        PartyService parties = new PartyService();
        parties.setInviteAllowed(id -> settings.enabled(id, PlayerSettingsService.Toggle.PARTY_INVITES));
        parties.createParty(leader.getUniqueId());
        settings.set(target, PlayerSettingsService.Toggle.PARTY_INVITES, false);
        assertEquals(PartyService.InviteResult.INVITES_DISABLED,
            parties.invite(leader.getUniqueId(), target.getUniqueId()));
        settings.set(target, PlayerSettingsService.Toggle.PARTY_INVITES, true);
        assertEquals(PartyService.InviteResult.SENT,
            parties.invite(leader.getUniqueId(), target.getUniqueId()));
        assertEquals(PartyService.InviteResponse.ACCEPTED,
            parties.acceptInvite(target.getUniqueId(), leader.getUniqueId()));
        settings.set(target, PlayerSettingsService.Toggle.PARTY_INVITES, false);
        assertTrue(parties.isInParty(target.getUniqueId()));
    }

    @Test
    void privateMessageOptOutBlocksPlayerCommandsButNotStaffOrOtherCommands() throws Exception {
        java.lang.reflect.Field settingsField = HGLootPlugin.class.getDeclaredField("playerSettingsService");
        settingsField.setAccessible(true);
        settingsField.set(plugin, settings);
        PlayerMock sender = server.addPlayer("MessageSender");
        Player target = server.addPlayer("MessageTarget");
        settings.set(target, PlayerSettingsService.Toggle.PRIVATE_MESSAGES, false);
        assertEquals(target, org.bukkit.Bukkit.getPlayerExact("MessageTarget"));
        assertFalse(settings.enabled(target.getUniqueId(), PlayerSettingsService.Toggle.PRIVATE_MESSAGES));
        PlayerCommandPreprocessEvent blocked = new PlayerCommandPreprocessEvent(sender,
            "/minecraft:msg MessageTarget hello");
        assertTrue(blocked.getMessage().trim().split("\\s+", 3)[0].matches("(?i)/(?:minecraft:)?(?:msg|tell|w)"));
        assertFalse(plugin.hasStaffRank(sender.getUniqueId()));
        plugin.onPlayerDirectMessage(blocked);
        assertTrue(blocked.isCancelled());
        assertTrue(sender.nextMessage().contains("private messages disabled"));

        PlayerCommandPreprocessEvent unrelated = new PlayerCommandPreprocessEvent(sender,
            "/party invite MessageTarget");
        plugin.onPlayerDirectMessage(unrelated);
        assertFalse(unrelated.isCancelled());

        when(plugin.hasStaffRank(sender.getUniqueId())).thenReturn(true);
        PlayerCommandPreprocessEvent staff = new PlayerCommandPreprocessEvent(sender,
            "/tell MessageTarget moderation notice");
        plugin.onPlayerDirectMessage(staff);
        assertFalse(staff.isCancelled());
    }
}
