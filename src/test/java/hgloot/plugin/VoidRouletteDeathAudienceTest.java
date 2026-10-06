package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class VoidRouletteDeathAudienceTest {
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
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void standaloneRealDeathSuppressesGlobalMessageAndPreservesFallbackHandling() throws Exception {
        PlayerMock player = activePlayer();
        PlayerDeathEvent event = deathEvent(player);

        service.onPlayerDeath(event);

        verify(event).deathMessage((Component) null);
        verify(event).setKeepInventory(true);
        verify(event).setKeepLevel(true);
        verify(event).setDroppedExp(0);
        assertTrue(event.getDrops().isEmpty());
    }

    @Test
    void managedRouletteRealDeathUsesTheSameSuppression() throws Exception {
        PlayerMock player = activePlayer();
        setField("managedRound", new ManagedMinigameRound.Request(
            UUID.randomUUID(), UUID.randomUUID(), ManagedMinigameRound.Game.VOID_ROULETTE,
            List.of(player.getUniqueId()), Map.of(player.getUniqueId(), RouletteTeam.RED), result -> {}));
        PlayerDeathEvent event = deathEvent(player);

        service.onPlayerDeath(event);

        verify(event).deathMessage((Component) null);
        verify(event).setKeepInventory(true);
        assertTrue(event.getDrops().isEmpty());
    }

    @Test
    void unrelatedDeathMessageIsUntouched() {
        PlayerMock player = server.addPlayer("NotInVoidRoulette");
        PlayerDeathEvent event = deathEvent(player);

        service.onPlayerDeath(event);

        verify(event, never()).deathMessage((Component) null);
        verify(event, never()).getDrops();
    }

    @Test
    void queuedButNotActivePlayerRetainsPriorDeathMessageBehavior() throws Exception {
        PlayerMock player = server.addPlayer("QueuedOnly");
        uuids("queuedPlayers").add(player.getUniqueId());
        PlayerDeathEvent event = deathEvent(player);

        service.onPlayerDeath(event);

        verify(event, never()).deathMessage((Component) null);
        verify(event).setKeepInventory(true);
    }

    private PlayerMock activePlayer() throws Exception {
        PlayerMock player = server.addPlayer();
        setField("matchActive", true);
        setField("activeWorldName", player.getWorld().getName());
        uuids("alivePlayers").add(player.getUniqueId());
        return player;
    }

    private PlayerDeathEvent deathEvent(PlayerMock player) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(new ArrayList<ItemStack>());
        return event;
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> uuids(String name) throws Exception {
        Field field = VoidRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Set<UUID>) field.get(service);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = VoidRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
