package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mockbukkit.mockbukkit.MockBukkit;

final class TransitionServiceTest {
    private TransitionService transitions;

    @BeforeEach void setup() {
        MockBukkit.mock();
        transitions = new TransitionService(MockBukkit.createMockPlugin(), player ->
            player.getWorld().isChunkLoaded(player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4));
    }

    @AfterEach void teardown() {
        transitions.shutdown();
        MockBukkit.unmock();
    }

    @Test void midpointRunsOnceAfterFadeOutAndStateClears() {
        var player = MockBukkit.getMock().addPlayer();
        player.getLocation().getChunk().load();
        AtomicInteger calls = new AtomicInteger();
        assertTrue(transitions.transition(player, TransitionService.Preset.NORMAL, calls::incrementAndGet));
        MockBukkit.getMock().getScheduler().performTicks(8);
        assertEquals(0, calls.get());
        assertTrue(transitions.isTransitioning(player.getUniqueId()));
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(1, calls.get());
        MockBukkit.getMock().getScheduler().performTicks(15);
        assertEquals(1, calls.get());
        assertFalse(transitions.isTransitioning(player.getUniqueId()));
    }

    @Test void concurrentPlayersIndependentAndDuplicateRejected() {
        var first = MockBukkit.getMock().addPlayer();
        var second = MockBukkit.getMock().addPlayer();
        first.getLocation().getChunk().load();
        second.getLocation().getChunk().load();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        assertTrue(transitions.transition(first, TransitionService.Preset.NORMAL, firstCalls::incrementAndGet));
        assertFalse(transitions.transition(first, TransitionService.Preset.NORMAL, firstCalls::incrementAndGet));
        assertTrue(transitions.transition(second, TransitionService.Preset.NORMAL, secondCalls::incrementAndGet));
        transitions.onQuit(new org.bukkit.event.player.PlayerQuitEvent(first, net.kyori.adventure.text.Component.empty()));
        MockBukkit.getMock().getScheduler().performTicks(24);
        assertEquals(0, firstCalls.get());
        assertEquals(1, secondCalls.get());
        assertFalse(transitions.isTransitioning(first.getUniqueId()));
        assertFalse(transitions.isTransitioning(second.getUniqueId()));
    }

    @Test void groupedExitWaitsForBothBlackMidpointsBeforeCleanup() {
        var first = MockBukkit.getMock().addPlayer();
        var second = MockBukkit.getMock().addPlayer();
        first.getLocation().getChunk().load();
        second.getLocation().getChunk().load();
        AtomicInteger arrivals = new AtomicInteger();
        AtomicInteger cleanup = new AtomicInteger();
        transitions.transitionGroup(List.of(first, second), ignored -> arrivals.incrementAndGet(), cleanup::incrementAndGet);
        MockBukkit.getMock().getScheduler().performTicks(8);
        assertEquals(0, arrivals.get());
        assertEquals(0, cleanup.get());
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(2, arrivals.get());
        assertEquals(1, cleanup.get());
        MockBukkit.getMock().getScheduler().performTicks(22);
        assertEquals(1, cleanup.get());
    }

    @Test void groupedExitCanFinishAfterOnePlayerDisconnects() {
        var first = MockBukkit.getMock().addPlayer();
        var second = MockBukkit.getMock().addPlayer();
        AtomicInteger arrivals = new AtomicInteger();
        AtomicInteger cleanup = new AtomicInteger();
        transitions.transitionGroup(List.of(first, second), ignored -> arrivals.incrementAndGet(), cleanup::incrementAndGet);
        transitions.onQuit(new org.bukkit.event.player.PlayerQuitEvent(second, net.kyori.adventure.text.Component.empty()));
        MockBukkit.getMock().getScheduler().performTicks(21);
        assertEquals(1, arrivals.get());
        assertEquals(1, cleanup.get());
    }

    @Test void shutdownClearsActiveTransitionsBeforeMidpoint() {
        var player = MockBukkit.getMock().addPlayer();
        AtomicInteger calls = new AtomicInteger();
        assertTrue(transitions.transition(player, TransitionService.Preset.NORMAL, calls::incrementAndGet));
        transitions.shutdown();
        MockBukkit.getMock().getScheduler().performTicks(24);
        assertEquals(0, calls.get());
        assertFalse(transitions.isTransitioning(player.getUniqueId()));
    }

    @Test void unloadedDestinationCannotLeaveBlackOverlayStuck() {
        var player = MockBukkit.getMock().addPlayer();
        AtomicInteger calls = new AtomicInteger();
        assertTrue(transitions.transition(player, TransitionService.Preset.NORMAL, calls::incrementAndGet));
        MockBukkit.getMock().getScheduler().performTicks(65);
        assertEquals(1, calls.get());
        assertFalse(transitions.isTransitioning(player.getUniqueId()));
    }

    @Test void failedMidpointStillRevealsAndClearsThePlayer() {
        var player = MockBukkit.getMock().addPlayer();
        player.getLocation().getChunk().load();
        AtomicInteger calls = new AtomicInteger();
        assertTrue(transitions.transition(player, TransitionService.Preset.NORMAL, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("test midpoint failure");
        }));
        MockBukkit.getMock().getScheduler().performTicks(65);
        assertEquals(1, calls.get());
        assertFalse(transitions.isTransitioning(player.getUniqueId()));
    }

    @Test void loadedChunkMustAlsoBeSentToPlayer() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        Location location = new Location(world, 9, 81, 45);
        when(player.getLocation()).thenReturn(location);
        when(world.isChunkLoaded(0, 2)).thenReturn(true);
        assertFalse(TransitionService.destinationReady(player));
        when(player.isChunkSent(Chunk.getChunkKey(0, 2))).thenReturn(true);
        assertTrue(TransitionService.destinationReady(player));
    }

    @Test void revealWaitsForStableDestinationButKeepsBoundedFallback() {
        transitions.shutdown();
        AtomicBoolean ready = new AtomicBoolean(false);
        transitions = new TransitionService(MockBukkit.createMockPlugin(), player -> ready.get());
        var player = MockBukkit.getMock().addPlayer();
        AtomicInteger midpoint = new AtomicInteger();
        assertTrue(transitions.transition(player, TransitionService.Preset.NORMAL, midpoint::incrementAndGet));
        MockBukkit.getMock().getScheduler().performTicks(12);
        assertEquals(1, midpoint.get());
        assertTrue(transitions.isTransitioning(player.getUniqueId()));
        ready.set(true);
        MockBukkit.getMock().getScheduler().performTicks(2);
        assertTrue(transitions.isTransitioning(player.getUniqueId()));
        MockBukkit.getMock().getScheduler().performTicks(11);
        assertFalse(transitions.isTransitioning(player.getUniqueId()));
    }

    @Test void packFramesHaveMonotonicAlphaAndUniqueFont() throws Exception {
        Path root = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra");
        BufferedImage image = ImageIO.read(root.resolve("textures/font/transition/fade.png").toFile());
        assertEquals(4, image.getWidth());
        assertEquals(8, image.getHeight());
        int previous = 0;
        for (int row = 0; row < 8; row++) {
            int alpha = image.getRGB(0, row) >>> 24;
            assertTrue(alpha > previous);
            for (int column = 0; column < 4; column++) {
                int argb = image.getRGB(column, row);
                assertEquals(alpha, argb >>> 24);
                assertEquals(0, argb & 0x00ffffff);
            }
            assertEquals(1, TransitionService.frameComponent(row + 1).content().length());
            previous = alpha;
        }
        assertEquals(255, previous);
        String font = Files.readString(root.resolve("font/transition.json"));
        assertTrue(font.contains("hydra:font/transition/fade.png"));
    }
}
