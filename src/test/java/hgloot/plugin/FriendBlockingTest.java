package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class FriendBlockingTest {
    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void blockingRemovesFriendshipRejectsRequestsAndPersists() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FriendService friends = new FriendService(plugin);

        assertEquals(FriendService.SendRequestResult.SENT, friends.sendRequest(first, second));
        assertEquals(FriendService.SendRequestResult.AUTO_ACCEPTED, friends.sendRequest(second, first));
        assertTrue(friends.areFriends(first, second));

        assertEquals(FriendService.BlockResult.BLOCKED, friends.block(first, second));
        assertFalse(friends.areFriends(first, second));
        assertEquals(FriendService.SendRequestResult.BLOCKED, friends.sendRequest(second, first));
        friends.shutdown();

        FriendService reloaded = new FriendService(plugin);
        assertTrue(reloaded.isBlocked(first, second));
        assertEquals(FriendService.BlockResult.UNBLOCKED, reloaded.unblock(first, second));
        reloaded.shutdown();
    }
}
