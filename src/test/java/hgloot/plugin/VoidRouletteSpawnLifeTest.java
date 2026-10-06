package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.VoxelShape;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedConstruction;

class VoidRouletteSpawnLifeTest {
    private ServerMock server;
    private VoidRouletteService service;
    private World arena;
    private PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("void_life_hub");
        arena = server.addSimpleWorld("void_life_test");
        player = server.addPlayer();
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        when(plugin.namespace()).thenReturn("hydramain");
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        service = new VoidRouletteService(plugin);
        set("activeWorld", arena);
        set("activeWorldName", arena.getName());
        uuids("alivePlayers").add(player.getUniqueId());
        lives().put(player.getUniqueId(), 3);
        spawns().put(player.getUniqueId(), pillar());
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void initialSpawnAndMrChildTransitionIgnoreOldWorldVoidSignals() throws Exception {
        set("matchStarting", true);
        lowMoveInCurrentWorld();
        EntityDamageEvent damage = voidDamage();
        service.onDamage(damage);
        assertTrue(damage.isCancelled());
        assertEquals(3, lives().get(player.getUniqueId()));

        set("managedRound", managedRequest());
        lowMoveInCurrentWorld();
        assertEquals(3, lives().get(player.getUniqueId()));
    }

    @Test
    void arrivalFallbackCannotChargeAPlayerStillInTheOldWorld() throws Exception {
        set("matchStarting", false);
        set("matchActive", true);
        lowMoveInCurrentWorld();
        EntityDamageEvent damage = voidDamage();
        service.onDamage(damage);
        assertTrue(damage.isCancelled());
        assertEquals(3, lives().get(player.getUniqueId()));
    }

    @Test
    void freezeAfterSpawnAndSpectatorToActivePromotionDoNotConsumeLife() throws Exception {
        player.teleport(pillar());
        set("matchActive", true);
        set("startFreezeActive", true);
        spawns().put(player.getUniqueId(), pillar());
        @SuppressWarnings("unchecked")
        Map<UUID, Location> frozen = (Map<UUID, Location>) field("frozenSpawnLocations").get(service);
        frozen.put(player.getUniqueId(), pillar());
        PlayerMoveEvent move = lowMoveInCurrentWorld();
        assertEquals(pillar().getBlockY(), move.getTo().getBlockY());
        EntityDamageEvent damage = voidDamage();
        service.onDamage(damage);
        assertTrue(damage.isCancelled());
        assertEquals(3, lives().get(player.getUniqueId()));

        uuids("spectators").add(player.getUniqueId());
        uuids("alivePlayers").remove(player.getUniqueId());
        uuids("spectators").remove(player.getUniqueId());
        uuids("alivePlayers").add(player.getUniqueId());
        set("matchStarting", true);
        lowMoveInCurrentWorld();
        assertEquals(3, lives().get(player.getUniqueId()));
    }

    @Test
    void legitimateFallAndDuplicateVoidDamageCostExactlyOneLife() throws Exception {
        activate();
        lowMoveInCurrentWorld();
        assertEquals(2, lives().get(player.getUniqueId()));
        service.onDamage(voidDamage());
        assertEquals(2, lives().get(player.getUniqueId()));
        assertTrue(uuids("lifeLossInProgress").contains(player.getUniqueId()));
    }

    @Test
    void nextLifeRespawnsOnAssignedPillarIncludingSwapTransferredOwnership() throws Exception {
        activate();
        Location swappedPillar = new Location(arena, 12.5D, 102.0D, -8.5D);
        spawns().put(player.getUniqueId(), swappedPillar);
        lowMoveInCurrentWorld();
        assertEquals(2, lives().get(player.getUniqueId()));
        assertEquals(swappedPillar.getBlockX(), player.getLocation().getBlockX());
        assertEquals(swappedPillar.getBlockZ(), player.getLocation().getBlockZ());
        assertEquals(0.0F, player.getFallDistance());
    }

    @Test
    void priorLifeGraceTaskCannotClearNewLifeProtection() throws Exception {
        activate();
        lowMoveInCurrentWorld();
        @SuppressWarnings("unchecked")
        Map<UUID, Location> frozen = (Map<UUID, Location>) field("respawnFreezeLocations").get(service);
        Location nextLifeLock = pillar().clone();
        frozen.put(player.getUniqueId(), nextLifeLock);
        server.getScheduler().performTicks(40);
        assertSame(nextLifeLock, frozen.get(player.getUniqueId()));
        assertTrue(uuids("lifeLossInProgress").contains(player.getUniqueId()));
        assertTrue(player.isInvulnerable());
    }

    @Test
    void managedAndStandaloneModesShareTheSameLegitimateLossGate() throws Exception {
        activate();
        set("managedRound", managedRequest());
        lowMoveInCurrentWorld();
        assertEquals(2, lives().get(player.getUniqueId()));
        assertFalse(uuids("spectators").contains(player.getUniqueId()));
    }

    private void activate() throws Exception {
        player.teleport(pillar());
        set("matchStarting", false);
        set("matchActive", true);
    }

    private PlayerMoveEvent lowMoveInCurrentWorld() {
        Location from = player.getLocation().clone();
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, from.clone().subtract(0, 45, 0));
        // MockBukkit has no BlockMock collision shapes; an empty shape keeps this
        // fixture focused on the life-loss path rather than arena block geometry.
        VoxelShape emptyShape = mock(VoxelShape.class);
        when(emptyShape.getBoundingBoxes()).thenReturn(List.of());
        try (MockedConstruction<BlockMock> ignored = mockConstruction(BlockMock.class,
            (block, context) -> when(block.getCollisionShape()).thenReturn(emptyShape))) {
            service.onPlayerMove(event);
        }
        return event;
    }

    private EntityDamageEvent voidDamage() {
        return new EntityDamageEvent(player, EntityDamageEvent.DamageCause.VOID, 4.0D);
    }

    private Location pillar() {
        return new Location(arena, 4.5D, 102.0D, 7.5D);
    }

    private ManagedMinigameRound.Request managedRequest() {
        UUID id = player.getUniqueId();
        return new ManagedMinigameRound.Request(UUID.randomUUID(), UUID.randomUUID(),
            ManagedMinigameRound.Game.VOID_ROULETTE, List.of(id), Map.of(id, RouletteTeam.RED), result -> {});
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> uuids(String name) throws Exception {
        return (Set<UUID>) field(name).get(service);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Integer> lives() throws Exception {
        return (Map<UUID, Integer>) field("lives").get(service);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Location> spawns() throws Exception {
        return (Map<UUID, Location>) field("playerSpawns").get(service);
    }

    private Field field(String name) throws Exception {
        Field field = VoidRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void set(String name, Object value) throws Exception {
        field(name).set(service, value);
    }
}
