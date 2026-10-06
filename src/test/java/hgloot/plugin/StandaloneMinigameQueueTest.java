package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class StandaloneMinigameQueueTest {
    enum Game { SPLEEF, VR, BRIDGE }

    @RegisterExtension
    static final TestExecutionExceptionHandler unsupportedOperationsFail = (context, failure) -> {
        if (failure instanceof org.mockbukkit.mockbukkit.exception.UnimplementedOperationException) {
            throw new AssertionError("Admission tests must not skip unsupported fixture operations", failure);
        }
        throw failure;
    };

    private HGLootPlugin plugin;
    private QueueService queues;
    private PlayerMock admin;
    private PlayerMock player;
    private SpleefService spleef;
    private VoidRouletteService vr;
    private BridgeService bridge;
    private PluginMock fixture;

    @BeforeEach
    void setup() {
        ServerMock server = MockBukkit.mock();
        fixture = MockBukkit.createMockPlugin();
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MinigameQueueTest"));
        queues = mock(QueueService.class);
        when(plugin.queueService()).thenReturn(queues);
        when(queues.sendPlayerToGameHub(any())).thenReturn(true);
        spleef = new SpleefService(plugin);
        vr = new VoidRouletteService(plugin);
        bridge = new BridgeService(plugin);
        when(plugin.spleefService()).thenReturn(spleef);
        when(plugin.voidRouletteService()).thenReturn(vr);
        when(plugin.bridgeService()).thenReturn(bridge);
        admin = server.addPlayer("Admin");
        admin.addAttachment(fixture, "hgloot.admin", true);
        player = new PlayerMock(server, "Player") {
            private PlayerInventoryMock inventory;
            private boolean canPickupItems = true;

            @Override
            public PlayerInventoryMock getInventory() {
                if (inventory == null) {
                    inventory = spy((PlayerInventoryMock) super.getInventory());
                    // Paper accepts null to clear armor; MockBukkit currently rejects it.
                    doAnswer(call -> {
                        if (call.getArgument(0) == null) {
                            inventory.setArmorContents(new ItemStack[4]);
                            return null;
                        }
                        return call.callRealMethod();
                    }).when(inventory).setArmorContents(nullable(ItemStack[].class));
                }
                return inventory;
            }

            @Override
            public void setCanPickupItems(boolean value) { canPickupItems = value; }

            @Override
            public boolean getCanPickupItems() { return canPickupItems; }
        };
        server.addPlayer(player);
        player.addAttachment(fixture, "hgloot.admin", false);
        player.addAttachment(fixture, "hgloot.spleef", false);
        player.addAttachment(fixture, "hgloot.voidroulette", false);
        player.addAttachment(fixture, "hgloot.bridge", false);
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest
    @EnumSource(Game.class)
    void publicJoinAndDefaultCommandCannotQueueEvenWithLegacyPermissions(Game game) throws Exception {
        player.addAttachment(fixture, "hgloot.spleef", true);
        player.addAttachment(fixture, "hgloot.voidroulette", true);
        player.addAttachment(fixture, "hgloot.bridge", true);
        assertTrue(player.hasPermission("hgloot." + switch (game) {
            case SPLEEF -> "spleef";
            case VR -> "voidroulette";
            case BRIDGE -> "bridge";
        }));
        command(game, player, new String[]{"join"});
        command(game, player, new String[]{});
        assertFalse(queued(game));
        verify(queues, never()).sendPlayerToGameHub(any());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void adminForceJoinStagesNormalPlayerOnceAndRetainsNormalOwnershipChecks(Game game) throws Exception {
        assertFalse(player.hasPermission("hgloot.spleef"));
        assertFalse(player.hasPermission("hgloot.voidroulette"));
        assertFalse(player.hasPermission("hgloot.bridge"));
        forceJoin(game, admin);
        assertTrue(queued(game));
        verify(queues).sendPlayerToGameHub(player);
        forceJoin(game, admin);
        verify(queues, times(1)).sendPlayerToGameHub(player);
        Game other = game == Game.SPLEEF ? Game.VR : Game.SPLEEF;
        forceJoin(other, admin);
        assertFalse(queued(other), "forcejoin must not bypass existing mode ownership");
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void normalPlayerCannotUseAdminForcejoin(Game game) throws Exception {
        forceJoin(game, player);
        assertFalse(queued(game));
        verify(queues, never()).sendPlayerToGameHub(any());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void unavailableGameHubRollsBackAdmission(Game game) throws Exception {
        when(queues.sendPlayerToGameHub(player)).thenReturn(false);
        forceJoin(game, admin);
        assertFalse(queued(game));
        verify(queues).sendPlayerToGameHub(player);
        Object service = service(game);
        Field task = service.getClass().getDeclaredField("queueCountdownTask");
        task.setAccessible(true);
        assertNull(task.get(service), "a failed handoff must not start a countdown");
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void rouletteManagedCleanupDoesNotUseStandaloneHubReturn(Game game) throws Exception {
        forceJoin(game, admin);
        Object service = service(game);
        Field round = service.getClass().getDeclaredField("managedRound");
        round.setAccessible(true);
        round.set(service, mock(ManagedMinigameRound.Request.class));
        if (game == Game.BRIDGE) {
            var cleanup = service.getClass().getDeclaredMethod("stopMatchInternal");
            cleanup.setAccessible(true);
            cleanup.invoke(service);
        } else {
            var cleanup = service.getClass().getDeclaredMethod("releaseQueuedPlayer", org.bukkit.entity.Player.class);
            cleanup.setAccessible(true);
            cleanup.invoke(service, player);
        }
        assertFalse(queued(game));
        verify(queues, never()).returnPlayerToHydraHub(any());
        verify(queues, never()).sendPlayerToHub(any());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void stoppingWaitingQueueReturnsPlayerToHub(Game game) throws Exception {
        forceJoin(game, admin);
        switch (game) {
            case SPLEEF -> spleef.stopMatch(admin);
            case VR -> vr.stopMatch(admin);
            case BRIDGE -> bridge.handleAdminCommand(admin, new String[]{"bridge", "stop"});
        }
        assertFalse(queued(game));
        verify(queues).returnPlayerToHydraHub(player);
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void explicitLeaveStillReturnsNormalPlayerToHub(Game game) throws Exception {
        forceJoin(game, admin);
        switch (game) {
            case SPLEEF -> assertTrue(spleef.leaveToHub(player));
            case VR -> assertTrue(vr.leaveToHub(player));
            case BRIDGE -> assertTrue(bridge.leaveToHub(player));
        }
        assertFalse(queued(game));
        if (game == Game.BRIDGE) verify(queues).sendPlayerToHub(player);
        else verify(queues).returnPlayerToHydraHub(player);
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void disconnectRemovesWaitingOwnershipWithoutTeleporting(Game game) throws Exception {
        forceJoin(game, admin);
        PlayerQuitEvent event = new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty());
        switch (game) {
            case SPLEEF -> spleef.onQuit(event);
            case VR -> vr.onQuit(event);
            case BRIDGE -> bridge.onQuit(event);
        }
        assertFalse(queued(game));
        verify(queues, never()).returnPlayerToHydraHub(any());
        verify(queues, never()).sendPlayerToHub(any());
    }

    @Test
    void publicServiceEntryCannotBypassCommands() throws Exception {
        spleef.join(player);
        vr.join(player);
        assertFalse(queued(Game.SPLEEF));
        assertFalse(queued(Game.VR));
        verify(queues, never()).sendPlayerToGameHub(any());
    }

    @Test
    void allLegacyAliasesArePermissionHiddenButRouletteStaysPublic() throws Exception {
        var yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/plugin.yml").toFile());
        for (String name : Set.of("spleef", "voidroulette", "maceroulette", "bridge")) {
            assertEquals("hgloot.admin", yaml.getString("commands." + name + ".permission"));
        }
        assertTrue(yaml.getStringList("commands.voidroulette.aliases").contains("vr"));
        assertNull(yaml.getString("commands.roulette.permission"));
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        for (String command : Set.of("spleef", "voidroulette", "maceroulette", "bridge")) {
            assertTrue(source.contains("tabRoutes.put(\"" + command
                + "\", (sender, args) -> !sender.hasPermission(\"hgloot.admin\")"));
        }
    }

    private void command(Game game, PlayerMock actor, String[] args) {
        switch (game) {
            case SPLEEF -> spleef.handleCommand(actor, args);
            case VR -> vr.handleCommand(actor, args);
            case BRIDGE -> bridge.handleCommand(actor, args);
        }
    }

    private void forceJoin(Game game, PlayerMock actor) {
        String[] args = {game.name().toLowerCase(), "forcejoin", player.getName()};
        switch (game) {
            case SPLEEF -> spleef.handleAdminCommand(actor, args);
            case VR -> vr.handleAdminCommand(actor, args);
            case BRIDGE -> bridge.handleAdminCommand(actor, args);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean queued(Game game) throws Exception {
        Object service = service(game);
        Field field = service.getClass().getDeclaredField("queuedPlayers");
        field.setAccessible(true);
        return ((Set<UUID>) field.get(service)).contains(player.getUniqueId());
    }

    private Object service(Game game) {
        return switch (game) { case SPLEEF -> spleef; case VR -> vr; case BRIDGE -> bridge; };
    }
}
