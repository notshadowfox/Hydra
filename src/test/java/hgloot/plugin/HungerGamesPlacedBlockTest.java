package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HungerGamesPlacedBlockTest {
    @TempDir Path worldContainer;
    private ServerMock server;
    private QueueService queues;
    private PlayerMock builder;
    private PlayerMock breaker;
    private Block block;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock(new ServerMock() {
            @Override public File getWorldContainer() { return worldContainer.toFile(); }
        });
        var world = server.addSimpleWorld("HG-World-Block-Test");
        builder = server.addPlayer("Builder");
        breaker = server.addPlayer("Breaker");
        builder.teleport(world.getSpawnLocation());
        breaker.teleport(world.getSpawnLocation());
        queues = new QueueService(MockBukkit.createMockPlugin(), null);
        matchTypes().put(builder.getUniqueId(), QueueService.QueueType.HUNGERGAMES);
        matchTypes().put(breaker.getUniqueId(), QueueService.QueueType.HUNGERGAMES);
        block = world.getBlockAt(32, 90, 32);
        block.setType(Material.OAK_PLANKS);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void anotherContestantMayBreakOnlyTrackedPlacementWithoutStaleDecay() throws Exception {
        schedule();
        BlockBreakEvent breaking = new BlockBreakEvent(block, breaker);
        queues.onSpectatorBlockBreak(breaking);
        assertFalse(breaking.isCancelled());
        queues.clearBrokenHungerGamesPlacement(breaking);
        block.setType(Material.STONE); // map-authored replacement at same coordinate
        server.getScheduler().performTicks(200);
        assertEquals(Material.STONE, block.getType());
        assertTrue(placements().isEmpty());

        BlockBreakEvent authored = new BlockBreakEvent(block, breaker);
        queues.onSpectatorBlockBreak(authored);
        assertTrue(authored.isCancelled());
    }

    @Test
    void unbrokenPlacementDecaysAtTenSecondsWithoutDrops() throws Exception {
        schedule();
        server.getScheduler().performTicks(199);
        assertEquals(Material.OAK_PLANKS, block.getType());
        server.getScheduler().performTicks(1);
        assertEquals(Material.AIR, block.getType());
        assertTrue(placements().isEmpty());
    }

    private void schedule() throws Exception {
        Method method = QueueService.class.getDeclaredMethod("scheduleHungerGamesPlacedBlockDecay",
            org.bukkit.entity.Player.class, Block.class, BlockData.class);
        method.setAccessible(true);
        method.invoke(queues, builder, block, Material.AIR.createBlockData());
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, QueueService.QueueType> matchTypes() throws Exception {
        Field field = QueueService.class.getDeclaredField("inMatchTypes");
        field.setAccessible(true);
        return (Map<UUID, QueueService.QueueType>) field.get(queues);
    }

    private Map<?, ?> placements() throws Exception {
        Field field = QueueService.class.getDeclaredField("hungerGamesPlacedBlocks");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(queues);
    }
}
