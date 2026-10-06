package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

final class HydraTabMatchSnapshotTest {
    @TempDir Path worldContainer;
    private QueueService queues;
    private ServerMock server;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock(new ServerMock() {
            @Override public File getWorldContainer() { return worldContainer.toFile(); }
        });
        queues = new QueueService(MockBukkit.createMockPlugin(), null);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void soloAndDuosUseTheirOwnAliveCountsAndOnlyScheduledFutureBorder() throws Exception {
        var soloWorld = server.addSimpleWorld("HG-World-Solo-Tab");
        var duoWorld = server.addSimpleWorld("HG-World-Duo-Tab");
        Player solo = server.addPlayer("SoloTab");
        Player duo = server.addPlayer("DuoTab");
        solo.teleport(new Location(soloWorld, 0, 80, 0));
        duo.teleport(new Location(duoWorld, 0, 80, 0));
        own(solo, soloWorld.getName(), QueueService.QueueType.HUNGERGAMES);
        own(duo, duoWorld.getName(), QueueService.QueueType.HUNGERGAMES_DUOS);
        assertEquals(new QueueService.TabMatchSnapshot("HG SOLOS", 1, null), queues.tabMatchSnapshot(solo));
        assertEquals(new QueueService.TabMatchSnapshot("HG DUOS", 1, null), queues.tabMatchSnapshot(duo));

        ((MatchBorderService) field("matchBorderService")).schedule(soloWorld.getName(),
            System.currentTimeMillis() + 120_000L);
        QueueService.TabMatchSnapshot scheduled = queues.tabMatchSnapshot(solo);
        assertNotNull(scheduled.borderSeconds());
        assertTrue(scheduled.borderSeconds() >= 119 && scheduled.borderSeconds() <= 120);
        assertNull(queues.tabMatchSnapshot(duo).borderSeconds(), "other match must not inherit timer");
        ((MatchBorderService) field("matchBorderService")).schedule(soloWorld.getName(),
            System.currentTimeMillis() - 1L);
        assertNull(queues.tabMatchSnapshot(solo).borderSeconds(), "expired timer is not future shrink");
    }

    @Test
    void spectatorRetainsExactMatchButLeavingWorldClearsContext() throws Exception {
        var world = server.addSimpleWorld("HG-World-Spectator-Tab");
        var hub = server.addSimpleWorld("Hub");
        Player spectator = server.addPlayer("SpectatorTab");
        spectator.teleport(new Location(world, 0, 80, 0));
        ((MatchWorldService) field("matchWorldService")).addActive(QueueService.QueueType.HUNGERGAMES,
            world.getName());
        map("spectatingMatchWorlds").put(spectator.getUniqueId(), world.getName());
        assertEquals(new QueueService.TabMatchSnapshot("HG SOLOS", 0, null), queues.tabMatchSnapshot(spectator));
        spectator.teleport(new Location(hub, 0, 80, 0));
        assertNull(queues.tabMatchSnapshot(spectator), "stale membership may not label Hub as HG");
    }

    private void own(Player player, String world, QueueService.QueueType type) throws Exception {
        ((MatchWorldService) field("matchWorldService")).addActive(type, world);
        map("inMatchWorlds").put(player.getUniqueId(), world);
        map("inMatchTypes").put(player.getUniqueId(), type);
        set("inMatchPlayers").add(player.getUniqueId());
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> map(String name) throws Exception {
        return (Map<K, V>) field(name);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> set(String name) throws Exception {
        return (Set<UUID>) field(name);
    }

    private Object field(String name) throws Exception {
        Field field = QueueService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(queues);
    }
}
