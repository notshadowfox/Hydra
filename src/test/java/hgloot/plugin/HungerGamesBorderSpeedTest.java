package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class HungerGamesBorderSpeedTest {
    @TempDir Path worldContainer;

    @Test
    void largeShrinkNeverMovesEitherEdgeFasterThanRunningCap() {
        int seconds = QueueService.hungerGamesBorderShrinkSeconds(1000.0D, 100.0D);
        assertEquals(100, seconds);
        assertTrue((1000.0D - 100.0D) / (2.0D * seconds) <= 4.5D);
    }

    @Test
    void ordinaryShrinkKeepsExistingMinimumDuration() {
        assertEquals(60, QueueService.hungerGamesBorderShrinkSeconds(400.0D, 280.0D));
        assertEquals(60, QueueService.hungerGamesBorderShrinkSeconds(280.0D, 280.0D));
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminCommandTargetsOnlyAnActiveHungerGamesWorld() throws Exception {
        ServerMock server = MockBukkit.mock(new ServerMock() {
            @Override public File getWorldContainer() { return worldContainer.toFile(); }
        });
        try {
            var activeWorld = server.addSimpleWorld("HG-World-Border-Test");
            var otherWorld = server.addSimpleWorld("Unrelated-World");
            var admin = server.addPlayer("Admin");
            QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
            Field field = QueueService.class.getDeclaredField("matchWorldService");
            field.setAccessible(true);
            ((MatchWorldService) field.get(queues)).addActive(QueueService.QueueType.HUNGERGAMES,
                activeWorld.getName());
            activeWorld.getWorldBorder().setSize(200.0D);
            double otherSize = otherWorld.getWorldBorder().getSize();

            queues.handleHungerGamesBorderAdmin(admin, new String[] {"set", "300", activeWorld.getName()});
            assertEquals(300.0D, activeWorld.getWorldBorder().getSize());
            queues.handleHungerGamesBorderAdmin(admin, new String[] {"set", "100", otherWorld.getName()});
            assertEquals(otherSize, otherWorld.getWorldBorder().getSize());
        } finally {
            MockBukkit.unmock();
        }
    }
}
