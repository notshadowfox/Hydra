package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockito.MockedConstruction;

class VoidRouletteSwapTest {
    private ServerMock server;
    private VoidRouletteService vr;
    private HGLootPlugin plugin;
    private World world;
    private Object swap;
    private final List<PlayerMock> roster = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("vr_swap");
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        vr = new VoidRouletteService(plugin);
        activateMatch();
        swap = event("SWAP");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void twoPlayersExchangeSnapshotXyzButKeepTheirOwnAimAndLoseAllMomentum() throws Exception {
        addPlayers(2);
        Map<UUID, Location> before = snapshot();
        assertTrue(fire());
        assertPermutation(before);
        assertNull(get("activeExperimentalEvent"));
        assertNull(get("activeExperimentalTask"));
        assertTrue((boolean) get("swapUsedThisMatch"));
    }

    @Test
    void everyRosterSizeIsABijectionWithoutSelfDestinations() throws Exception {
        for (int size = 2; size <= 16; size++) {
            if (size == 2) addPlayers(2);
            else addPlayers(1);
            for (int attempt = 0; attempt < 10; attempt++) {
                set("swapUsedThisMatch", false);
                Map<UUID, Location> before = snapshot();
                assertTrue(fire(), "size=" + size);
                assertPermutation(before);
            }
        }
    }

    @Test
    void excludesSpectatorsEliminatedOfflineDeadZeroHealthWrongWorldAndMissingPlayers() throws Exception {
        addPlayers(9);
        players("spectators").add(roster.get(2).getUniqueId());
        roster.get(3).setGameMode(GameMode.SPECTATOR);
        players("alivePlayers").remove(roster.get(4).getUniqueId());
        doReturn(false).when(roster.get(5)).isOnline();
        doReturn(true).when(roster.get(6)).isDead();
        doReturn(0.0D).when(roster.get(7)).getHealth();
        roster.get(8).teleport(server.addSimpleWorld("elsewhere").getSpawnLocation());
        players("alivePlayers").add(UUID.randomUUID());
        roster.forEach(org.mockito.Mockito::clearInvocations);
        Location first = roster.get(0).getLocation();
        Location second = roster.get(1).getLocation();
        assertTrue(fire());
        assertEquals(second.toVector(), roster.get(0).getLocation().toVector());
        assertEquals(first.toVector(), roster.get(1).getLocation().toVector());
        for (int i = 2; i < roster.size(); i++) {
            verify(roster.get(i), never()).teleport(any(Location.class));
            verify(roster.get(i), never()).setVelocity(any(Vector.class));
            verify(roster.get(i), never()).setFallDistance(anyFloat());
        }
    }

    @Test
    void zeroOrOneValidPlayerIsANoOpThatStillConsumesTheActivation() throws Exception {
        assertFalse(fire());
        assertTrue((boolean) get("swapUsedThisMatch"));
        set("swapUsedThisMatch", false);
        addPlayers(1);
        assertFalse(fire());
        verify(roster.getFirst(), never()).teleport(any(Location.class));
        assertTrue((boolean) get("swapUsedThisMatch"));
        assertNull(get("activeExperimentalEvent"));
    }

    @Test
    void sharedCoordinatesSkipRatherThanCreateDuplicateDestinations() throws Exception {
        addPlayers(2);
        Location duplicate = roster.getFirst().getLocation();
        duplicate.setYaw(70);
        roster.getLast().teleport(duplicate);
        roster.forEach(org.mockito.Mockito::clearInvocations);
        assertFalse(fire());
        for (Player player : roster) verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void inactiveMissingWorldAndSuddenDeathRejectSwapWithoutConsumingIt() throws Exception {
        addPlayers(2);
        set("matchActive", false);
        assertFalse(queue(swap));
        assertFalse(fire());
        set("matchActive", true);
        set("activeWorld", null);
        assertFalse(queue(swap));
        assertFalse(fire());
        set("activeWorld", world);
        set("suddenDeathActive", true);
        assertFalse(queue(swap));
        assertFalse(fire());
        assertFalse((boolean) get("swapUsedThisMatch"));
    }

    @Test
    void warningUsesExistingSchedulerAndSnapshotsAtExecutionNotQueueTime() throws Exception {
        addPlayers(2);
        assertTrue(queue(swap));
        assertEquals(swap, get("pendingExperimentalEvent"));
        assertFalse((boolean) get("swapUsedThisMatch"));
        server.getScheduler().performTicks(199);
        for (Player player : roster) verify(player, never()).teleport(any(Location.class));
        roster.getFirst().teleport(new Location(world, 14.125, 91.875, -17.25, 65, -25));
        Map<UUID, Location> before = snapshot();
        server.getScheduler().performTicks(1);
        assertPermutation(before);
        assertNull(get("pendingExperimentalEvent"));
        assertNull(get("experimentalWarningTask"));
        assertNull(get("activeExperimentalTask"));
    }

    @Test
    void stoppedWarningCannotFireAndCanBeQueuedAgain() throws Exception {
        addPlayers(2);
        assertTrue(queue(swap));
        invoke("stopActiveExperiment", false);
        server.getScheduler().performTicks(220);
        assertFalse((boolean) get("swapUsedThisMatch"));
        for (Player player : roster) verify(player, never()).teleport(any(Location.class));
        assertTrue(queue(swap));
        server.getScheduler().performTicks(200);
        assertTrue((boolean) get("swapUsedThisMatch"));
    }

    @Test
    void lostEligibilityDuringWarningIsRechecked() throws Exception {
        addPlayers(2);
        assertTrue(queue(swap));
        players("spectators").add(roster.getLast().getUniqueId());
        server.getScheduler().performTicks(200);
        for (Player player : roster) verify(player, never()).teleport(any(Location.class));
        assertNull(get("activeExperimentalEvent"));
    }

    @Test
    void cleanupCancelsPendingSwapAndResetsOncePerMatch() throws Exception {
        addPlayers(2);
        assertTrue(queue(swap));
        invoke("stopExperimentalSystems", false);
        invoke("cleanupState", false);
        server.getScheduler().performTicks(220);
        for (Player player : roster) verify(player, never()).teleport(any(Location.class));
        activateMatch();
        for (Player player : roster) players("alivePlayers").add(player.getUniqueId());
        assertTrue(fire());
        invoke("cleanupState", false);
        assertFalse((boolean) get("swapUsedThisMatch"));
        activateMatch();
        for (Player player : roster) players("alivePlayers").add(player.getUniqueId());
        assertTrue(fire());
    }

    @Test
    void onceUsedCannotRepeatOrReplaceAnotherWarningEvenViaAdmin() throws Exception {
        addPlayers(2);
        assertTrue(fire());
        assertFalse(fire());
        Object crosswind = event("CROSSWIND");
        assertTrue(queue(crosswind));
        assertFalse(queue(swap));
        assertEquals(crosswind, get("pendingExperimentalEvent"));
        set("testModeEnabled", true);
        vr.handleAdminCommand(roster.getFirst(), new String[] {"vr", "test", "swap"});
        assertEquals(crosswind, get("pendingExperimentalEvent"));
    }

    @Test
    void randomBagIncludesSwapBeforeUseAndNeverRefillsItAfterUse() throws Exception {
        invoke("startRandomChaosEvent");
        List<Object> bag = bag();
        assertTrue(bag.contains(swap) || get("pendingExperimentalEvent") == swap);
        invoke("cancelExperimentalWarning");
        addPlayers(2);
        assertTrue(fire());
        assertFalse(bag.contains(swap));
        for (int i = 0; i < 20; i++) {
            invoke("startRandomChaosEvent");
            assertNotEquals(swap, get("pendingExperimentalEvent"));
            assertFalse(bag.contains(swap));
            invoke("cancelExperimentalWarning");
        }
    }

    @Test
    void adminSwapUsesExistingTestModeGateWarningAndCompletion() throws Exception {
        addPlayers(2);
        assertEquals(List.of("swap"), vr.completeAdmin(new String[] {"vr", "test", "SW"}));
        vr.handleAdminCommand(roster.getFirst(), new String[] {"vr", "test", "swap"});
        assertNull(get("pendingExperimentalEvent"));
        set("testModeEnabled", true);
        vr.handleAdminCommand(roster.getFirst(), new String[] {"vr", "test", "swap"});
        assertEquals(swap, get("pendingExperimentalEvent"));
        assertEquals(10, get("experimentalWarningSecondsRemaining"));
    }

    @Test
    void cancelledTeleportAbortsAndRollsBackEarlierMovesWithoutRetryingSwap() throws Exception {
        addPlayers(4);
        Map<UUID, Location> before = snapshot();
        Map<UUID, Location> pillarAssignments = locations("playerSpawns");
        before.forEach((id, location) -> pillarAssignments.put(id, location.clone()));
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> calls.incrementAndGet() == 2 ? false : invocation.callRealMethod())
                .when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertEquals(3, calls.get());
        assertOrigins(before);
        assertEquals(before, locations("playerSpawns"));
        assertFalse(queue(swap));
        assertNull(get("activeExperimentalEvent"));
    }

    @Test
    void thrownTeleportAbortsAndRollsBackEarlierMoves() throws Exception {
        addPlayers(3);
        Map<UUID, Location> before = snapshot();
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> {
                if (calls.incrementAndGet() == 2) throw new IllegalStateException("test failure");
                return invocation.callRealMethod();
            }).when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertOrigins(before);
        verify(plugin.getLogger()).warning(contains("Swap teleport failed"));
    }

    @Test
    void redirectedTeleportIsNotAcceptedAsASuccessfulPermutation() throws Exception {
        addPlayers(3);
        Map<UUID, Location> before = snapshot();
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> {
                if (calls.incrementAndGet() == 2) ((Location) invocation.getArgument(0)).add(1, 0, 0);
                return invocation.callRealMethod();
            }).when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertOrigins(before);
    }

    @Test
    void cancellationInsideTeleportStopsRemainingTeleportsAndMotionWrites() throws Exception {
        addPlayers(3);
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> {
                calls.incrementAndGet();
                Object result = invocation.callRealMethod();
                invoke("stopActiveExperiment", false);
                return result;
            }).when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertEquals(1, calls.get());
        for (Player player : roster) verify(player, never()).setVelocity(any(Vector.class));
        assertNull(get("activeExperimentalEvent"));
    }

    @Test
    void eliminationDuringTeleportDoesNotMoveEliminatedPlayerOrContinuePermutation() throws Exception {
        addPlayers(3);
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> {
                calls.incrementAndGet();
                Object result = invocation.callRealMethod();
                players("alivePlayers").remove(player.getUniqueId());
                return result;
            }).when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertEquals(1, calls.get());
        for (Player player : roster) verify(player, never()).setVelocity(any(Vector.class));
    }

    @Test
    void rollbackCancellationIsLoggedWithoutLeakingEventOrAllowingRepeat() throws Exception {
        addPlayers(3);
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> calls.incrementAndGet() == 1 && (boolean) invocation.callRealMethod())
                .when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        verify(plugin.getLogger()).warning(contains("Swap rollback failed"));
        assertNull(get("activeExperimentalEvent"));
        assertFalse(queue(swap));
    }

    @Test
    void laterTeleportCannotLeaveEarlierPlayersWithMomentum() throws Exception {
        addPlayers(3);
        Map<UUID, Location> before = snapshot();
        for (Player player : roster) {
            doAnswer(invocation -> {
                Object result = invocation.callRealMethod();
                for (Player other : roster) {
                    other.setVelocity(new Vector(5, -9, 2));
                    other.setFallDistance(30);
                }
                return result;
            }).when(player).teleport(any(Location.class));
        }
        assertTrue(fire());
        assertPermutation(before);
    }

    @Test
    void reentrantSwapCannotTriggerASecondActivation() throws Exception {
        addPlayers(2);
        Map<UUID, Location> before = snapshot();
        for (Player player : roster) {
            doAnswer(invocation -> {
                assertFalse(queue(swap));
                assertFalse(fire());
                return invocation.callRealMethod();
            }).when(player).teleport(any(Location.class));
        }
        assertTrue(fire());
        assertPermutation(before);
        for (Player player : roster) verify(player, times(1)).teleport(any(Location.class));
    }

    @Test
    void matchWorldChangeInsideTeleportStopsTheOldSwap() throws Exception {
        addPlayers(3);
        World nextWorld = server.addSimpleWorld("next_vr");
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> {
                calls.incrementAndGet();
                Object result = invocation.callRealMethod();
                set("activeWorld", nextWorld);
                return result;
            }).when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertEquals(1, calls.get());
        for (Player player : roster) verify(player, never()).setVelocity(any(Vector.class));
    }

    @Test
    void matchEndDuringWarningPreventsTeleport() throws Exception {
        addPlayers(2);
        assertTrue(queue(swap));
        set("matchActive", false);
        server.getScheduler().performTicks(220);
        for (Player player : roster) verify(player, never()).teleport(any(Location.class));
        assertFalse((boolean) get("swapUsedThisMatch"));
        assertNull(get("experimentalWarningTask"));
        assertNull(get("pendingExperimentalEvent"));
    }

    @Test
    void laterTeleportMovingAnEarlierPlayerBreaksPermutationAndTriggersRollback() throws Exception {
        addPlayers(3);
        Map<UUID, Location> before = snapshot();
        List<PlayerMock> moved = new ArrayList<>();
        for (PlayerMock player : roster) {
            doAnswer(invocation -> {
                Object result = invocation.callRealMethod();
                moved.add(player);
                if (moved.size() == 3) moved.getFirst().setLocation(new Location(world, 30, 93, 30));
                return result;
            }).when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertOrigins(before);
    }

    @Test
    void startFreezeFollowsSwapAndFailedSwapRestoresRespawnFreezeAnchors() throws Exception {
        addPlayers(2);
        set("startFreezeActive", true);
        for (Player player : roster) locations("frozenSpawnLocations").put(player.getUniqueId(), player.getLocation());
        assertTrue(fire());
        for (Player player : roster) {
            Location destination = player.getLocation();
            PlayerMoveEvent move = new PlayerMoveEvent(player, destination, destination.clone().add(1, 0, 0));
            vr.onPlayerMove(move);
            assertEquals(destination, move.getTo());
            locations("respawnFreezeLocations").put(player.getUniqueId(), destination.clone());
        }
        Map<UUID, Location> before = snapshot();
        set("swapUsedThisMatch", false);
        AtomicInteger calls = new AtomicInteger();
        for (Player player : roster) {
            doAnswer(invocation -> calls.incrementAndGet() == 2 ? false : invocation.callRealMethod())
                .when(player).teleport(any(Location.class));
        }
        assertFalse(fire());
        assertOrigins(before);
        assertEquals(before, locations("respawnFreezeLocations"));
        assertEquals(before, locations("frozenSpawnLocations"));
    }

    @Test
    void respawnFreezeFollowsSwapAndTransfersAssignedPillarWithoutChangingGrace() throws Exception {
        addPlayers(2);
        Player player = roster.getFirst();
        Location original = player.getLocation();
        Location otherPillar = roster.getLast().getLocation();
        locations("respawnFreezeLocations").put(player.getUniqueId(), original.clone());
        locations("playerSpawns").put(player.getUniqueId(), original.clone());
        locations("playerSpawns").put(roster.getLast().getUniqueId(), otherPillar.clone());
        players("lifeLossInProgress").add(player.getUniqueId());
        assertTrue(fire());
        Location destination = player.getLocation();
        PlayerMoveEvent move = new PlayerMoveEvent(player, destination, destination.clone().add(1, 0, 0));
        vr.onPlayerMove(move);
        assertEquals(destination, move.getTo());
        assertEquals(otherPillar, locations("playerSpawns").get(player.getUniqueId()));
        assertEquals(original, locations("playerSpawns").get(roster.getLast().getUniqueId()));
        assertTrue(players("lifeLossInProgress").contains(player.getUniqueId()));
    }

    @Test
    void swappingDuringRespawnGraceDoesNotStrandPlayerInvulnerable() throws Exception {
        addPlayers(2);
        PlayerMock respawning = roster.getFirst();
        locations("playerSpawns").put(respawning.getUniqueId(), respawning.getLocation().clone());
        VoxelShape emptyShape = mock(VoxelShape.class);
        when(emptyShape.getBoundingBoxes()).thenReturn(List.of());
        try (MockedConstruction<BlockMock> ignored = mockConstruction(BlockMock.class,
            (block, context) -> when(block.getCollisionShape()).thenReturn(emptyShape))) {
            invoke("loseLifeAndRespawn", respawning, null, "fell into the void");
        }
        Location graceLock = locations("respawnFreezeLocations").get(respawning.getUniqueId());
        assertNotNull(graceLock);
        assertTrue(respawning.isInvulnerable());

        assertTrue(fire());
        assertSame(graceLock, locations("respawnFreezeLocations").get(respawning.getUniqueId()));
        server.getScheduler().performTicks(40);
        assertFalse(respawning.isInvulnerable());
        assertFalse(players("lifeLossInProgress").contains(respawning.getUniqueId()));
        assertFalse(locations("respawnFreezeLocations").containsKey(respawning.getUniqueId()));
    }

    private void addPlayers(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            int index = roster.size();
            PlayerMock player = spy(new PlayerMock(server, "Swap" + index));
            server.addPlayer(player);
            player.teleport(new Location(world, index * 3.125 + 0.25, 80.5 + index * 0.25,
                -index * 2.75 - 0.375, index * 13.5F - 90, index * 2.75F - 30));
            player.setVelocity(new Vector(0.4 + index, -0.9, 0.7));
            player.setFallDistance(19.5F);
            players("alivePlayers").add(player.getUniqueId());
            roster.add(player);
            clearInvocations(player);
        }
    }

    private void activateMatch() throws Exception {
        set("matchActive", true);
        set("activeWorld", world);
        set("activeWorldName", world.getName());
    }

    private Map<UUID, Location> snapshot() {
        Map<UUID, Location> result = new HashMap<>();
        for (Player player : roster) result.put(player.getUniqueId(), player.getLocation().clone());
        return result;
    }

    private void assertOrigins(Map<UUID, Location> before) {
        for (Player player : roster) assertEquals(before.get(player.getUniqueId()), player.getLocation());
    }

    private void assertPermutation(Map<UUID, Location> before) {
        Set<Vector> expected = new HashSet<>();
        before.values().forEach(location -> expected.add(location.toVector()));
        Set<Vector> actual = new HashSet<>();
        for (Player player : roster) {
            Location origin = before.get(player.getUniqueId());
            Location after = player.getLocation();
            assertNotEquals(origin.toVector(), after.toVector());
            assertTrue(actual.add(after.toVector()), "duplicate destination");
            assertEquals(origin.getYaw(), after.getYaw());
            assertEquals(origin.getPitch(), after.getPitch());
            assertEquals(new Vector(), player.getVelocity());
            assertEquals(0.0F, player.getFallDistance());
        }
        assertEquals(expected, actual);
    }

    private boolean fire() throws Exception { return (boolean) invoke("startExperimentalEvent", swap); }
    private boolean queue(Object event) throws Exception { return (boolean) invoke("queueExperimentalEvent", event, true); }

    private Object event(String name) throws Exception {
        for (Object value : field("activeExperimentalEvent").getType().getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) return value;
        }
        throw new AssertionError(name);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> players(String name) throws Exception { return (Set<UUID>) get(name); }
    @SuppressWarnings("unchecked")
    private Map<UUID, Location> locations(String name) throws Exception { return (Map<UUID, Location>) get(name); }
    @SuppressWarnings("unchecked")
    private List<Object> bag() throws Exception { return (List<Object>) get("chaosEventBag"); }
    private Object get(String name) throws Exception { return field(name).get(vr); }
    private void set(String name, Object value) throws Exception { field(name).set(vr, value); }

    private Field field(String name) throws Exception {
        Field field = VoidRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private Object invoke(String name, Object... args) throws Exception {
        for (Method method : VoidRouletteService.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(vr, args);
            }
        }
        throw new NoSuchMethodException(name);
    }
}
