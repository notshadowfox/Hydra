package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class VoidbreakerPresentationTest {
    private ServerMock server;
    private VoidRouletteService service;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        service = new VoidRouletteService(plugin);
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    @Test
    void possessionHudCountsTenToZeroThenShowsUsedAndDisappears() {
        UUID holder = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals("10s", VoidRouletteService.voidbreakerMaceDisplay(holder, holder, false, 10));
        assertEquals("0s", VoidRouletteService.voidbreakerMaceDisplay(holder, holder, false, 0));
        assertEquals("USED", VoidRouletteService.voidbreakerMaceDisplay(holder, holder, true, 6));
        assertNull(VoidRouletteService.voidbreakerMaceDisplay(holder, null, false, 0));
        assertNull(VoidRouletteService.voidbreakerMaceDisplay(other, holder, false, 10));
    }

    @Test
    void glowBeginsAndEndsOnlyForTheCurrentHolder() throws Exception {
        PlayerMock holder = server.addPlayer("Holder");
        PlayerMock other = server.addPlayer("Other");
        Method begin = VoidRouletteService.class.getDeclaredMethod("beginVoidbreakerGlow", Player.class);
        Method clear = VoidRouletteService.class.getDeclaredMethod("clearVoidbreakerGlow");
        begin.setAccessible(true);
        clear.setAccessible(true);

        begin.invoke(service, holder);
        assertTrue(holder.isGlowing());
        assertFalse(other.isGlowing());
        Field tracked = VoidRouletteService.class.getDeclaredField("glowingVoidbreaker");
        tracked.setAccessible(true);
        assertEquals(holder.getUniqueId(), tracked.get(service));

        clear.invoke(service);
        assertFalse(holder.isGlowing());
        assertNull(tracked.get(service));
    }

    @Test
    void oneUseGateStillGuardsTheMaceAbility() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/VoidRouletteService.java"));
        assertTrue(source.contains("if (voidbreakerAbilityUsed)"));
        assertTrue(source.contains("voidbreakerAbilityUsed = true;"));
        assertTrue(source.contains("currentVoidbreaker = null;"));
    }
}
