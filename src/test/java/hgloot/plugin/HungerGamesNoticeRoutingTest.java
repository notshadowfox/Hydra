package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HungerGamesNoticeRoutingTest {
    @TempDir Path worldContainer;
    private ServerMock server;
    private QueueService queues;
    private PlayerMock admin;
    private PlayerMock teammate;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock(new ServerMock() {
            @Override public File getWorldContainer() { return worldContainer.toFile(); }
        });
        queues = new QueueService(MockBukkit.createMockPlugin(), null);
        admin = server.addPlayer("Admin");
        teammate = server.addPlayer("Teammate");
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @EnumSource(value = QueueService.QueueType.class, names = {"HUNGERGAMES", "HUNGERGAMES_DUOS"})
    void repeatedPreparationRequestSendsExactlyOneNoticeToEachQueuedPlayer(QueueService.QueueType type)
        throws Exception {
        queue(type, admin);
        queue(type, teammate);
        set("prewarmInProgress", type);
        Method request = QueueService.class.getDeclaredMethod("requestMatchWorldAsyncForStart",
            QueueService.QueueType.class, boolean.class, CommandSender.class);
        request.setAccessible(true);
        request.invoke(queues, type, true, admin);
        request.invoke(queues, type, true, admin);
        assertTrue(admin.nextMessage().contains("Preparing " + type.displayName() + " world"));
        assertTrue(teammate.nextMessage().contains("Preparing " + type.displayName() + " world"));
        assertNull(admin.nextMessage(), "queued admin must not also receive the queue broadcast");
        assertNull(teammate.nextMessage(), "second request must reuse the preparation notice");
    }

    @Test
    void readyAndForceStartNoticeRoutingExcludesOnlyTheAlreadyNotifiedAdmin() throws Exception {
        QueueService.QueueType type = QueueService.QueueType.HUNGERGAMES;
        queue(type, admin);
        queue(type, teammate);
        Method notify = QueueService.class.getDeclaredMethod("notifyQueue",
            QueueService.QueueType.class, String.class, CommandSender.class);
        notify.setAccessible(true);
        String ready = "Hunger Games world ready. Starting match now.";
        admin.sendMessage(ready);
        notify.invoke(queues, type, ready, admin);
        assertEquals(ready, admin.nextMessage());
        assertNull(admin.nextMessage());
        assertTrue(teammate.nextMessage().contains(ready));
        assertNull(teammate.nextMessage());

        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        assertTrue(source.contains("notifyQueue(type, ready, pending.sender())"));
        assertTrue(source.contains("notifyQueue(type, preparing, triggerSender)"));
        assertNotNull(queues);
    }

    @SuppressWarnings("unchecked")
    private void queue(QueueService.QueueType type, PlayerMock player) throws Exception {
        Map<QueueService.QueueType, LinkedHashSet<UUID>> collections =
            (Map<QueueService.QueueType, LinkedHashSet<UUID>>) field("queues").get(queues);
        collections.get(type).add(player.getUniqueId());
        ((Map<UUID, QueueService.QueueType>) field("queuedPlayers").get(queues)).put(player.getUniqueId(), type);
    }

    @SuppressWarnings("unchecked")
    private void set(String name, QueueService.QueueType type) throws Exception {
        ((Set<QueueService.QueueType>) field(name).get(queues)).add(type);
    }

    private Field field(String name) throws Exception {
        Field field = QueueService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
