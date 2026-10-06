package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

final class TeamVoiceGroupTest {
    private ServerMock server;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void teamKeyIsStableRegardlessOfMemberOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(HydraVoiceChatService.teamKey(Set.of(first, second)),
            HydraVoiceChatService.teamKey(Set.of(second, first)));
    }

    @Test
    void automaticTeamGroupIsPasswordLockedAndCleanedWhenTeamEnds() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        TeamProtectionService teams = mock(TeamProtectionService.class);
        when(plugin.teamProtectionService()).thenReturn(teams);
        Player first = server.addPlayer("First");
        Player second = server.addPlayer("Second");
        Set<UUID> duo = Set.of(first.getUniqueId(), second.getUniqueId());
        when(teams.currentTeamMembers(eq(first), org.mockito.ArgumentMatchers.anyCollection())).thenReturn(duo);
        when(teams.currentTeamMembers(eq(second), org.mockito.ArgumentMatchers.anyCollection())).thenReturn(duo);

        VoicechatServerApi api = mock(VoicechatServerApi.class);
        Group group = mock(Group.class);
        UUID groupId = UUID.randomUUID();
        when(group.getId()).thenReturn(groupId);
        VoicechatConnection firstConnection = mock(VoicechatConnection.class);
        VoicechatConnection secondConnection = mock(VoicechatConnection.class);
        when(api.getConnectionOf(first.getUniqueId())).thenReturn(firstConnection);
        when(api.getConnectionOf(second.getUniqueId())).thenReturn(secondConnection);
        when(api.createGroup(eq("Hydra Team"), argThat(password -> password != null && !password.isBlank())))
            .thenReturn(group);

        HydraVoiceChatService voice = new HydraVoiceChatService(plugin);
        Field apiField = HydraVoiceChatService.class.getDeclaredField("voicechatApi");
        apiField.setAccessible(true);
        apiField.set(voice, api);
        Method sync = HydraVoiceChatService.class.getDeclaredMethod("syncTeamGroups");
        sync.setAccessible(true);
        sync.invoke(voice);
        verify(firstConnection).setGroup(group);
        verify(secondConnection).setGroup(group);

        when(firstConnection.getGroup()).thenReturn(group);
        when(secondConnection.getGroup()).thenReturn(group);
        when(teams.currentTeamMembers(eq(first), org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Set.of());
        when(teams.currentTeamMembers(eq(second), org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Set.of());
        sync.invoke(voice);
        verify(firstConnection).setGroup(null);
        verify(secondConnection).setGroup(null);
    }
}
