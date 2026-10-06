package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class SpleefInvisOrbVisibilityTest {
    private ServerMock server;
    private SpleefService spleef;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        spleef = new SpleefService(plugin);
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    @Test
    void aliveOpponentAndSpectatorCannotSeeEitherHeldItemUntilReveal() throws Exception {
        PlayerMock invisible = server.addPlayer("Invisible");
        PlayerMock opponent = server.addPlayer("Opponent");
        PlayerMock spectator = server.addPlayer("Spectator");
        invisible.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SHOVEL));
        invisible.getInventory().setItemInOffHand(new ItemStack(Material.SNOWBALL));
        setField("activeWorldName", invisible.getWorld().getName());
        players("alivePlayers").add(invisible.getUniqueId());
        players("alivePlayers").add(opponent.getUniqueId());
        players("spectators").add(spectator.getUniqueId());
        Map<UUID, BukkitTask> reveals = revealTasks();
        reveals.put(invisible.getUniqueId(), mock(BukkitTask.class));

        sync();
        assertFalse(opponent.canSee(invisible));
        assertFalse(spectator.canSee(invisible));
        assertTrue(invisible.canSee(invisible));

        reveals.remove(invisible.getUniqueId());
        sync();
        assertTrue(opponent.canSee(invisible));
        assertTrue(spectator.canSee(invisible));
        assertTrue(invisible.getInventory().getItemInMainHand().getType() == Material.DIAMOND_SHOVEL);
        assertTrue(invisible.getInventory().getItemInOffHand().getType() == Material.SNOWBALL);
    }

    private void sync() throws Exception {
        Method method = SpleefService.class.getDeclaredMethod("syncSpectatorVisibility");
        method.setAccessible(true);
        method.invoke(spleef);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> players(String name) throws Exception {
        Field field = SpleefService.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Set<UUID>) field.get(spleef);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, BukkitTask> revealTasks() throws Exception {
        Field field = SpleefService.class.getDeclaredField("invisRevealTasks");
        field.setAccessible(true);
        return (Map<UUID, BukkitTask>) field.get(spleef);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = SpleefService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(spleef, value);
    }
}
