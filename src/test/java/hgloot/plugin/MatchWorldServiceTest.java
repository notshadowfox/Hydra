package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

final class MatchWorldServiceTest {
    @TempDir Path worlds;
    private MatchWorldService service;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        service = new MatchWorldService(plugin, new MatchWorldService.CleanupHooks() {
            @Override public void returnPlayerToHub(Player player) {}
            @Override public void retryCleanup(String worldName) {}
            @Override public boolean isReserved(String worldName) { return false; }
            @Override public boolean hasGenerationFlight(String worldName) { return false; }
            @Override public void onFolderDeleted(String worldName) {}
            @Override public void onCleanupFinished(String worldName) {}
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onlyExactChildFoldersAreSafeToDelete() {
        assertTrue(MatchWorldService.isSafeFolder("HG-World-1", worlds.resolve("HG-World-1"), worlds));
        assertFalse(MatchWorldService.isSafeFolder("HG-World-1", worlds.resolve("other"), worlds));
        assertFalse(MatchWorldService.isSafeFolder("HG-World-1", worlds.resolve("nested/HG-World-1"), worlds));
        assertFalse(MatchWorldService.isSafeFolder("HG-World-1", worlds.resolve("../HG-World-1"), worlds));
    }

    @Test
    void staleScanSkipsReservedRootsAndIncludesLinkedNetherOnce() throws Exception {
        Files.createDirectory(worlds.resolve("HG-World-1"));
        Files.createDirectory(worlds.resolve("HG-World-1_nether"));
        Files.createDirectory(worlds.resolve("HG-World-2"));
        Files.createDirectory(worlds.resolve("normal-world"));
        Set<String> roots = service.staleRoots(worlds, name -> name.startsWith("HG-World-"),
            name -> "HG-World-2".equals(name),
            name -> name.endsWith("_nether") ? name.substring(0, name.length() - 7) : name,
            name -> name + "_nether");
        assertEquals(Set.of("HG-World-1"), roots);
    }

    @Test
    void cleanupStateIsOwnedByWorldService() {
        assertFalse(service.isCleaning("HG-World-1"));
        service.beginCleanup("HG-World-1");
        assertTrue(service.isCleaning("HG-World-1"));
    }

    @Test
    void activeWorldOwnershipKeepsInsertionOrderAndExactType() {
        service.addActive(QueueService.QueueType.HUNGERGAMES, "HG-World-1");
        service.addActive(QueueService.QueueType.HUNGERGAMES, "HG-World-2");
        service.addActive(QueueService.QueueType.HUNGERGAMES_DUOS, "HG-World-3");
        assertEquals("HG-World-1", service.primaryActive(QueueService.QueueType.HUNGERGAMES));
        assertEquals(QueueService.QueueType.HUNGERGAMES_DUOS, service.activeTypeFor("HG-World-3"));
        assertEquals(2, service.activeFor(QueueService.QueueType.HUNGERGAMES).size());
        service.removeActive(QueueService.QueueType.HUNGERGAMES, "HG-World-1");
        assertEquals("HG-World-2", service.primaryActive(QueueService.QueueType.HUNGERGAMES));
        service.removeActive("HG-World-3");
        assertFalse(service.isActive("HG-World-3"));
    }

    @Test
    void linkedNetherOwnershipClearsWithRoot() {
        service.registerLinkedNether("HG-World-1", "HG-World-1_nether");
        assertEquals("HG-World-1", service.linkedRoot("HG-World-1_nether"));
        assertEquals("HG-World-1_nether", service.mappedNether("HG-World-1"));
        service.unregisterLinkedNether("HG-World-1", "HG-World-1", false);
        assertNull(service.linkedRoot("HG-World-1_nether"));
        assertNull(service.mappedNether("HG-World-1"));
    }
}
