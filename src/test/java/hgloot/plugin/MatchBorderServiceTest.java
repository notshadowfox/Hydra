package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.Test;

final class MatchBorderServiceTest {
    private final MatchBorderService borders = new MatchBorderService();

    @Test
    void timersArePerWorldAndClearWithMatch() {
        borders.schedule("HG-World-1", 120_000L);
        borders.schedule("HG-World-2", 90_000L);
        assertEquals(120, borders.timerRemaining("HG-World-1", 60, 0L));
        assertEquals(90, borders.timerRemaining("HG-World-2", 60, 0L));
        assertEquals(121, borders.adjustTimer("HG-World-1", 60, 1, 0L));
        borders.clear("HG-World-1");
        assertNull(borders.scheduledAt("HG-World-1"));
        assertEquals(90_000L, borders.scheduledAt("HG-World-2"));
    }

    @Test
    void genericShrinkRunsOnceAtScheduledTimeAndSyncsLinkedWorld() {
        World root = world("BR-World-1", 400.0D);
        World linked = world("BR-World-1_nether", 400.0D);
        borders.schedule(root.getName(), 1000L);
        var settings = new MatchBorderService.Settings(60, 100.0D, 50.0D, 0, 400.0D);
        borders.tick(root, () -> linked, false, 0L, settings, 999L);
        verify(root.getWorldBorder(), never()).setSize(anyDouble());
        borders.tick(root, () -> linked, false, 0L, settings, 1000L);
        verify(root.getWorldBorder()).setSize(350.0D);
        verify(linked.getWorldBorder()).setSize(350.0D);
        assertEquals(61_000L, borders.scheduledAt(root.getName()));
    }

    @Test
    void hgPhasesUseElapsedMatchTimeAndDoNotRepeat() {
        World world = world("HG-World-1", 1000.0D);
        var settings = new MatchBorderService.Settings(60, 100.0D, 0.0D, 60, 1000.0D);
        borders.tick(world, () -> null, true, 1000L, settings, 330_999L);
        verify(world.getWorldBorder(), never()).changeSize(anyDouble(), anyLong());
        borders.tick(world, () -> null, true, 1000L, settings, 331_000L);
        verify(world.getWorldBorder()).changeSize(700.0D, 1200L);
        borders.tick(world, () -> null, true, 1000L, settings, 331_100L);
        verify(world.getWorldBorder(), times(1)).changeSize(700.0D, 1200L);
        assertEquals("Shrinking", borders.hungerGamesSidebar(world.getName(), 1000L, 331_100L));
    }

    @Test
    void speedCapAndSnapshotsKeepExistingMeaning() {
        assertEquals(100, MatchBorderService.hungerGamesShrinkSeconds(1000.0D, 100.0D));
        assertEquals(60, MatchBorderService.hungerGamesShrinkSeconds(400.0D, 280.0D));
        World world = world("HG-World-1", 400.0D);
        var settings = new MatchBorderService.Settings(60, 100.0D, 10.0D, 60, 400.0D);
        assertNull(borders.tabCountdown(world, world.getName(), settings, 1000L));
        borders.schedule(world.getName(), 61_000L);
        assertEquals(60, borders.tabCountdown(world, world.getName(), settings, 1000L));
        assertEquals("05:30", MatchBorderService.hungerGamesStatus(0L, 0, 0L));
        assertEquals("Final", MatchBorderService.hungerGamesStatus(0L, 4, 631_000L));
    }

    @Test
    void initialBorderUsesResolvedMapCenterAndSize() {
        World world = world("HG-World-Map", 400.0D);
        borders.applyInitial(world, 125.0D, -75.0D, 900.0D);
        verify(world.getWorldBorder()).setCenter(125.0D, -75.0D);
        verify(world.getWorldBorder()).setSize(900.0D);
    }

    private static World world(String name, double size) {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getName()).thenReturn(name);
        when(world.getWorldBorder()).thenReturn(border);
        when(world.getPlayers()).thenReturn(List.of());
        when(border.getSize()).thenReturn(size);
        return world;
    }
}
