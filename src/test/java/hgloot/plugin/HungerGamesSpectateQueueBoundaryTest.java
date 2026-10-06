package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HungerGamesSpectateQueueBoundaryTest {
    @RegisterExtension
    static final TestExecutionExceptionHandler unsupportedOperationsFail = (context, failure) -> {
        if (failure instanceof org.mockbukkit.mockbukkit.exception.UnimplementedOperationException) {
            throw new AssertionError("Spectate regression tests must not skip unsupported fixture operations", failure);
        }
        throw failure;
    };

    @TempDir
    Path worldContainer;
    private ServerMock server;
    private QueueService queues;
    private PlayerMock spectator;
    private PlayerMock target;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock(new ServerMock() {
            @Override
            public File getWorldContainer() { return worldContainer.toFile(); }
        });
        var hub = server.addSimpleWorld("Hub");
        var match = server.addSimpleWorld("HG-World-Spectate-Test");
        queues = new QueueService(MockBukkit.createMockPlugin(), null);
        spectator = player("Spectator");
        target = player("Target");
        spectator.teleport(new Location(hub, 0, 80, 0));
        target.teleport(new Location(match, 5, 80, 5));
        map("inMatchWorlds").put(target.getUniqueId(), match.getName());
        map("managedMatchWorldTypes").put(match.getName(), QueueService.QueueType.HUNGERGAMES);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void unqueuedHubPlayerEntersActiveHgUsingHydraSpectatorState() throws Exception {
        assertTrue(queues.spectateTargetMatch(spectator, target));
        assertEquals(target.getWorld(), spectator.getWorld());
        assertEquals(GameMode.ADVENTURE, spectator.getGameMode());
        assertEquals(target.getWorld().getName(), map("spectatingMatchWorlds").get(spectator.getUniqueId()));
        assertFalse(queues.isPlayerQueued(spectator.getUniqueId()));
    }

    @Test
    void staleQueueMapWithoutQueueOwnershipDoesNotRejectEligibleViewer() throws Exception {
        UUID id = spectator.getUniqueId();
        map("queuedPlayers").put(id, QueueService.QueueType.HUNGERGAMES);
        assertFalse(queues.isPlayerActivelyQueuedOrBound(id));
        assertTrue(queues.spectateTargetMatch(spectator, target));
        assertEquals(target.getWorld(), spectator.getWorld());
        assertEquals(target.getWorld().getName(), map("spectatingMatchWorlds").get(id));
    }

    @Test
    void eliminatedTargetStillResolvesExactHgMatch() throws Exception {
        map("inMatchWorlds").remove(target.getUniqueId());
        map("spectatingMatchWorlds").put(target.getUniqueId(), target.getWorld().getName());
        assertEquals("queue:hungergames:HG-World-Spectate-Test", queues.spectatableMatchId(target));
        assertTrue(queues.spectateTargetMatch(spectator, target));
        assertEquals(target.getWorld(), spectator.getWorld());
    }

    @Test
    void duosTargetResolvesItsExactMatchWorld() throws Exception {
        map("managedMatchWorldTypes").put(target.getWorld().getName(), QueueService.QueueType.HUNGERGAMES_DUOS);
        assertEquals("queue:hungergames_duos:HG-World-Spectate-Test", queues.spectatableMatchId(target));
        assertTrue(queues.spectateTargetMatch(spectator, target));
        assertEquals(target.getWorld(), spectator.getWorld());
    }

    @Test
    void pendingLaunchCommitmentIsRejectedWithoutChangingOwnership() throws Exception {
        UUID id = spectator.getUniqueId();
        map("pendingMatchLaunches").put(id, null);
        assertTrue(queues.isPlayerActivelyQueuedOrBound(id));
        assertTrue(queues.spectateTargetMatch(spectator, target));
        assertEquals("Hub", spectator.getWorld().getName());
        assertTrue(map("pendingMatchLaunches").containsKey(id));
        assertNull(map("spectatingMatchWorlds").get(id));
    }

    @Test
    void queuedPlayerIsRejectedWithoutQueueOrTeamMutation() throws Exception {
        UUID id = spectator.getUniqueId();
        QueueService.QueueType type = QueueService.QueueType.HUNGERGAMES_DUOS;
        map("queuedPlayers").put(id, type);
        queueMap().get(type).add(id);
        UUID duo = UUID.randomUUID();
        map("duoTeamByPlayer").put(id, duo);
        assertTrue(queues.spectateTargetMatch(spectator, target));
        assertEquals("Hub", spectator.getWorld().getName());
        assertEquals(type, map("queuedPlayers").get(id));
        assertTrue(queueMap().get(type).contains(id));
        assertEquals(duo, map("duoTeamByPlayer").get(id));
        assertNull(map("spectatingMatchWorlds").get(id));
        assertTrue(spectator.nextMessage().contains("Leave your queue"));
    }

    @Test
    void missingQueueCollectionAndWrongOwnershipAreNoOps() throws Exception {
        UUID id = spectator.getUniqueId();
        QueueService.QueueType type = QueueService.QueueType.HUNGERGAMES;
        map("queuedPlayers").put(id, type);
        UUID duo = UUID.randomUUID();
        map("duoTeamByPlayer").put(id, duo);
        LinkedHashSet<UUID> queue = queueMap().remove(type);
        removeFromQueue(id, type);
        assertEquals(type, map("queuedPlayers").get(id));
        assertEquals(duo, map("duoTeamByPlayer").get(id));
        queueMap().put(type, queue);
        removeFromQueue(id, null);
        removeFromQueue(id, QueueService.QueueType.DUO);
        removeFromQueue(id, type);
        assertEquals(type, map("queuedPlayers").get(id));
        assertEquals(duo, map("duoTeamByPlayer").get(id));
        assertFalse(queue.contains(id));
    }

    @Test
    void staleMembershipInAnotherQueueCannotRemoveActualCommitment() throws Exception {
        UUID id = spectator.getUniqueId();
        map("queuedPlayers").put(id, QueueService.QueueType.DUO);
        queueMap().get(QueueService.QueueType.DUO).add(id);
        queueMap().get(QueueService.QueueType.HUNGERGAMES).add(id);
        removeFromQueue(id, QueueService.QueueType.HUNGERGAMES);
        assertEquals(QueueService.QueueType.DUO, map("queuedPlayers").get(id));
        assertTrue(queueMap().get(QueueService.QueueType.DUO).contains(id));
    }

    private PlayerMock player(String name) {
        PlayerMock player = new PlayerMock(server, name) {
            private boolean canPickupItems = true;

            @Override
            public void setCanPickupItems(boolean value) { canPickupItems = value; }

            @Override
            public boolean getCanPickupItems() { return canPickupItems; }
        };
        server.addPlayer(player);
        return player;
    }

    private void removeFromQueue(UUID id, QueueService.QueueType type) throws Exception {
        Method method = QueueService.class.getDeclaredMethod("removeFromQueue", UUID.class, QueueService.QueueType.class);
        method.setAccessible(true);
        method.invoke(queues, id, type);
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> map(String name) throws Exception {
        Field field = QueueService.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<K, V>) field.get(queues);
    }

    @SuppressWarnings("unchecked")
    private Map<QueueService.QueueType, LinkedHashSet<UUID>> queueMap() throws Exception {
        return (Map<QueueService.QueueType, LinkedHashSet<UUID>>) (Map<?, ?>) map("queues");
    }
}
