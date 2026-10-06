package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HungerGamesDeathRoutingTest {
    @TempDir
    Path worldContainer;
    private ServerMock server;
    private World hub;
    private World match;
    private PlayerMock player;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock(new ServerMock() {
            @Override public File getWorldContainer() { return worldContainer.toFile(); }
        });
        hub = server.addSimpleWorld("Hub");
        match = server.addSimpleWorld("HG-World-Death-Test");
        player = new PlayerMock(server, "Fallen") {
            private boolean canPickupItems = true;

            @Override public void setCanPickupItems(boolean value) { canPickupItems = value; }
            @Override public boolean getCanPickupItems() { return canPickupItems; }
        };
        server.addPlayer(player);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void expiredSoloQueueFadeCannotMoveAnActiveOrDeadHgPlayerToGameHub() throws Exception {
        for (String phase : new String[] {"active", "pending", "spectating"}) {
            TransferFixture fixture = transferFixture(QueueService.QueueType.HUNGERGAMES);
            UUID id = player.getUniqueId();
            map(fixture.queues, "queuedPlayers").remove(id);
            if (phase.equals("active")) set(fixture.queues, "inMatchPlayers", id);
            if (phase.equals("pending")) map(fixture.queues, "pendingSpectatorRespawnWorld").put(id, match.getName());
            if (phase.equals("spectating")) map(fixture.queues, "spectatingMatchWorlds").put(id, match.getName());
            fixture.midpoint.run();
            verify(fixture.gameHub, never()).enter(player);
            assertEquals(hub, player.getWorld());
        }
    }

    @Test
    void expiredDuosQueueFadeCannotMoveADeadTeammateToGameHub() throws Exception {
        TransferFixture fixture = transferFixture(QueueService.QueueType.HUNGERGAMES_DUOS);
        UUID id = player.getUniqueId();
        map(fixture.queues, "queuedPlayers").remove(id);
        map(fixture.queues, "pendingSpectatorRespawnWorld").put(id, match.getName());
        fixture.midpoint.run();
        verify(fixture.gameHub, never()).enter(player);
    }

    @Test
    void validQueueFadeAndUnownedGameHubEntryRemainAvailable() throws Exception {
        TransferFixture fixture = transferFixture(QueueService.QueueType.HUNGERGAMES);
        fixture.midpoint.run();
        verify(fixture.gameHub).enter(player);
    }

    @Test
    void hgDeathKeepsMatchOwnershipUntilSpectatorStateIsApplied() throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        UUID id = player.getUniqueId();
        player.teleport(new Location(match, 4, 80, 4));
        set(queues, "inMatchPlayers", id);
        map(queues, "inMatchWorlds").put(id, match.getName());
        map(queues, "inMatchTypes").put(id, QueueService.QueueType.HUNGERGAMES);
        map(queues, "managedMatchWorldTypes").put(match.getName(), QueueService.QueueType.HUNGERGAMES);
        field(queues, "autoWinTriggerEnabled").setBoolean(queues, false);

        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        queues.onPlayerDeath(death);
        assertFalse(queues.isPlayerInMatch(id));
        assertEquals(match.getName(), map(queues, "pendingSpectatorRespawnWorld").get(id));

        PlayerRespawnEvent respawn = new PlayerRespawnEvent(player, hub.getSpawnLocation(), false);
        queues.onPlayerRespawn(respawn);
        assertEquals(match, respawn.getRespawnLocation().getWorld());
        assertEquals(match.getName(), map(queues, "pendingSpectatorRespawnWorld").get(id));
        player.teleport(respawn.getRespawnLocation());
        server.getScheduler().performTicks(1);
        assertEquals(match.getName(), map(queues, "spectatingMatchWorlds").get(id));
        assertFalse(map(queues, "pendingSpectatorRespawnWorld").containsKey(id));
        assertEquals(GameMode.ADVENTURE, player.getGameMode());
        assertNotNull(player.getInventory().getItem(0));
        assertEquals(Material.COMPASS, player.getInventory().getItem(0).getType());
        assertNotNull(player.getInventory().getItem(8));
        assertFalse(queues.isPlayerQueued(id));
    }

    @Test
    void duosDeathWithLivingTeammateDoesNotRemoveTheirMatchMembership() throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        PlayerMock teammate = server.addPlayer("Teammate");
        player.teleport(new Location(match, 4, 80, 4));
        teammate.teleport(new Location(match, 5, 80, 5));
        for (PlayerMock member : new PlayerMock[] {player, teammate}) {
            set(queues, "inMatchPlayers", member.getUniqueId());
            map(queues, "inMatchWorlds").put(member.getUniqueId(), match.getName());
            map(queues, "inMatchTypes").put(member.getUniqueId(), QueueService.QueueType.HUNGERGAMES_DUOS);
        }
        map(queues, "managedMatchWorldTypes").put(match.getName(), QueueService.QueueType.HUNGERGAMES_DUOS);
        field(queues, "autoWinTriggerEnabled").setBoolean(queues, false);

        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        queues.onPlayerDeath(death);
        queues.onPlayerDeath(death);
        assertEquals(match.getName(), map(queues, "pendingSpectatorRespawnWorld").get(player.getUniqueId()));
        assertTrue(queues.isPlayerInMatch(teammate.getUniqueId()));
        assertFalse(queues.isPlayerQueued(player.getUniqueId()));
    }

    @Test
    void finalSoloDeathWithAnOpponentAliveStartsResultWithoutEvictingTheDeadPlayer() throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        PlayerMock opponent = server.addPlayer("Opponent");
        player.teleport(new Location(match, 4, 80, 4));
        opponent.teleport(new Location(match, 5, 80, 5));
        for (PlayerMock participant : new PlayerMock[] {player, opponent}) {
            set(queues, "inMatchPlayers", participant.getUniqueId());
            map(queues, "inMatchWorlds").put(participant.getUniqueId(), match.getName());
            map(queues, "inMatchTypes").put(participant.getUniqueId(), QueueService.QueueType.HUNGERGAMES);
        }
        map(queues, "managedMatchWorldTypes").put(match.getName(), QueueService.QueueType.HUNGERGAMES);
        field(queues, "autoWinTriggerEnabled").setBoolean(queues, true);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        queues.onPlayerDeath(death);
        assertEquals(match.getName(), map(queues, "pendingSpectatorRespawnWorld").get(player.getUniqueId()));
        assertTrue(queues.isPlayerInMatch(opponent.getUniqueId()));
        @SuppressWarnings("unchecked")
        Set<String> ending = (Set<String>) field(queues, "endingMatchWorlds").get(queues);
        assertTrue(ending.contains(match.getName()));
        assertNotNull(server.getWorld(match.getName()));
    }

    @Test
    void intentionalHubExitCancelsPendingSpectatorTask() throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        UUID id = player.getUniqueId();
        player.teleport(new Location(match, 4, 80, 4));
        map(queues, "pendingSpectatorRespawnWorld").put(id, match.getName());
        PlayerRespawnEvent respawn = new PlayerRespawnEvent(player, hub.getSpawnLocation(), false);
        queues.onPlayerRespawn(respawn);
        queues.sendPlayerToHub(player);
        server.getScheduler().performTicks(1);
        assertEquals(hub, player.getWorld());
        assertFalse(map(queues, "pendingSpectatorRespawnWorld").containsKey(id));
        assertFalse(map(queues, "spectatingMatchWorlds").containsKey(id));
    }

    @Test
    void deathNearMatchEndCannotReplaceTheWinnerReturnWithImmediateCleanup() throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        @SuppressWarnings("unchecked")
        Set<String> ending = (Set<String>) field(queues, "endingMatchWorlds").get(queues);
        ending.add(match.getName());
        Method evaluate = QueueService.class.getDeclaredMethod("evaluateMatchState",
            QueueService.QueueType.class, String.class);
        evaluate.setAccessible(true);
        evaluate.invoke(queues, QueueService.QueueType.HUNGERGAMES, match.getName());
        assertTrue(ending.contains(match.getName()));
        assertNotNull(server.getWorld(match.getName()));
    }

    @Test
    void pendingSoloDeathSurvivesLogoutAndRejoinsTheSameMatchAsSpectator() throws Exception {
        assertHungerGamesSpectatorRejoin(QueueService.QueueType.HUNGERGAMES, true);
    }

    @Test
    void establishedDuosSpectatorSurvivesLogoutAndRejoinsTheSameMatch() throws Exception {
        assertHungerGamesSpectatorRejoin(QueueService.QueueType.HUNGERGAMES_DUOS, false);
    }

    @Test
    void normalDeathWithMultipleSurvivorsCannotAutoRequeueBeforeCompletion() throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        player.teleport(new Location(match, 4, 80, 4));
        for (PlayerMock participant : new PlayerMock[] {player, server.addPlayer("SurvivorA"), server.addPlayer("SurvivorB")}) {
            participant.teleport(new Location(match, 5, 80, 5));
            set(queues, "inMatchPlayers", participant.getUniqueId());
            map(queues, "inMatchWorlds").put(participant.getUniqueId(), match.getName());
            map(queues, "inMatchTypes").put(participant.getUniqueId(), QueueService.QueueType.HUNGERGAMES);
        }
        map(queues, "managedMatchWorldTypes").put(match.getName(), QueueService.QueueType.HUNGERGAMES);
        field(queues, "autoWinTriggerEnabled").setBoolean(queues, true);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        queues.onPlayerDeath(death);
        server.getScheduler().performTicks(3);
        assertEquals(match.getName(), map(queues, "pendingSpectatorRespawnWorld").get(player.getUniqueId()));
        assertFalse(queues.isPlayerQueued(player.getUniqueId()));
        assertTrue(((Set<?>) field(queues, "endingMatchWorlds").get(queues)).isEmpty());
    }

    private void assertHungerGamesSpectatorRejoin(QueueService.QueueType type, boolean pending) throws Exception {
        QueueService queues = new QueueService(MockBukkit.createMockPlugin(), null);
        UUID id = player.getUniqueId();
        player.teleport(new Location(match, 4, 80, 4));
        map(queues, "managedMatchWorldTypes").put(match.getName(), type);
        ((MatchWorldService) field(queues, "matchWorldService").get(queues)).addActive(type, match.getName());
        map(queues, pending ? "pendingSpectatorRespawnWorld" : "spectatingMatchWorlds")
            .put(id, match.getName());
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        queues.onPlayerQuit(quit);
        assertFalse(map(queues, "pendingSpectatorRespawnWorld").containsKey(id));
        assertEquals(match.getName(), map(queues, "spectatingMatchWorlds").get(id));
        assertTrue(map(queues, "brRejoinSnapshots").containsKey(id));

        player.teleport(hub.getSpawnLocation());
        assertTrue(queues.rejoinBattleRoyale(player, true));
        assertEquals(match, player.getWorld());
        assertEquals(match.getSpawnLocation().getBlockX(), player.getLocation().getBlockX());
        assertEquals(match.getName(), map(queues, "spectatingMatchWorlds").get(id));
        assertEquals(Material.COMPASS, player.getInventory().getItem(0).getType());
        assertFalse(queues.isPlayerQueued(id));
    }

    private TransferFixture transferFixture(QueueService.QueueType type) throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        GameHubService gameHub = mock(GameHubService.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDataFolder()).thenReturn(worldContainer.resolve("plugin-data").toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HgDeathRoutingTest"));
        when(plugin.gameHubService()).thenReturn(gameHub);
        when(gameHub.isReadyForEntry()).thenReturn(true);
        when(gameHub.enter(player)).thenReturn(true);
        AtomicReference<Runnable> midpoint = new AtomicReference<>();
        when(plugin.transitionTravel(eq(player), any())).thenAnswer(call -> {
            midpoint.set(call.getArgument(1));
            return true;
        });
        QueueService queues = new QueueService(plugin, null);
        map(queues, "queuedPlayers").put(player.getUniqueId(), type);
        map(queues, "managedMatchWorldTypes").put(match.getName(), type);
        assertTrue(queues.sendPlayerToGameHub(player));
        assertNotNull(midpoint.get());
        return new TransferFixture(queues, gameHub, midpoint.get());
    }

    private record TransferFixture(QueueService queues, GameHubService gameHub, Runnable midpoint) {}

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(QueueService queues, String name) throws Exception {
        return (Map<K, V>) field(queues, name).get(queues);
    }

    @SuppressWarnings("unchecked")
    private static void set(QueueService queues, String name, UUID id) throws Exception {
        ((Set<UUID>) field(queues, name).get(queues)).add(id);
    }

    private static Field field(QueueService queues, String name) throws Exception {
        Field field = queues.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
