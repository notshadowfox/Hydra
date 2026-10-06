package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class HungerGamesDeathAudienceTest {
    private ServerMock server;
    private QueueService queues;
    @TempDir
    Path worldContainer;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock(new ServerMock() {
            @Override
            public File getWorldContainer() {
                return worldContainer.toFile();
            }
        });
        var plugin = MockBukkit.createMockPlugin();
        plugin.getConfig().set("queue.match_world.enabled", false);
        queues = new QueueService(plugin, null);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void deathMessageReachesOnlyOriginatingMatchAndItsSpectators() throws Exception {
        var firstWorld = server.addSimpleWorld("hg_first");
        var secondWorld = server.addSimpleWorld("hg_second");
        var hubWorld = server.addSimpleWorld("hub");
        PlayerMock victim = server.addPlayer("Victim");
        PlayerMock teammate = server.addPlayer("Teammate");
        PlayerMock spectator = server.addPlayer("Spectator");
        PlayerMock otherMatch = server.addPlayer("OtherMatch");
        PlayerMock hub = server.addPlayer("HubPlayer");
        victim.teleport(new Location(firstWorld, 0, 65, 0));
        teammate.teleport(new Location(firstWorld, 0, 65, 0));
        spectator.teleport(new Location(firstWorld, 0, 65, 0));
        otherMatch.teleport(new Location(secondWorld, 0, 65, 0));
        hub.teleport(new Location(hubWorld, 0, 65, 0));

        matchPlayer(victim, "hg_first");
        matchPlayer(teammate, "hg_first");
        matchPlayer(otherMatch, "hg_second");
        managedTypes().put("hg_first", QueueService.QueueType.HUNGERGAMES);
        managedTypes().put("hg_second", QueueService.QueueType.HUNGERGAMES);
        spectators().put(spectator.getUniqueId(), "hg_first");

        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        Component message = Component.text("Victim was slain");
        when(event.getEntity()).thenReturn(victim);
        when(event.deathMessage()).thenReturn(message);
        Method send = QueueService.class.getDeclaredMethod("sendHungerGamesDeathMessage", PlayerDeathEvent.class);
        send.setAccessible(true);
        send.invoke(queues, event);

        verify(event).deathMessage(null);
        assertEquals("Victim was slain", teammate.nextMessage());
        assertEquals("Victim was slain", spectator.nextMessage());
        assertNull(otherMatch.nextMessage());
        assertNull(hub.nextMessage());
    }

    private void matchPlayer(PlayerMock player, String world) throws Exception {
        players().add(player.getUniqueId());
        matchWorlds().put(player.getUniqueId(), world);
        matchTypes().put(player.getUniqueId(), QueueService.QueueType.HUNGERGAMES);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> players() throws Exception {
        return (Set<UUID>) field("inMatchPlayers");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> matchWorlds() throws Exception {
        return (Map<UUID, String>) field("inMatchWorlds");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, QueueService.QueueType> matchTypes() throws Exception {
        return (Map<UUID, QueueService.QueueType>) field("inMatchTypes");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> spectators() throws Exception {
        return (Map<UUID, String>) field("spectatingMatchWorlds");
    }

    @SuppressWarnings("unchecked")
    private Map<String, QueueService.QueueType> managedTypes() throws Exception {
        return (Map<String, QueueService.QueueType>) field("managedMatchWorldTypes");
    }

    private Object field(String name) throws Exception {
        Field field = QueueService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(queues);
    }
}
