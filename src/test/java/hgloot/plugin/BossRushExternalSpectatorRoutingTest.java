package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BossRushExternalSpectatorRoutingTest {
    @Test
    @SuppressWarnings("unchecked")
    void externalObserverIsTeamIsolatedButNeverBecomesContestant() throws Exception {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("bossrush-match-1");
            var observer = server.addPlayer("Observer");
            var red = server.addPlayer("RedTarget");
            var blue = server.addPlayer("BlueTarget");
            observer.teleport(new Location(world, 4, 90, 4));
            red.teleport(new Location(world, 5, 90, 5));
            blue.teleport(new Location(world, 60, 90, 60));
            observer.setGameMode(GameMode.ADVENTURE);
            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.namespace()).thenReturn("hydramain");
            when(plugin.hydraPlusService()).thenReturn(mock(HydraPlusService.class));
            doAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return true;
            }).when(plugin).transitionTravel(any(Player.class), any(Runnable.class));
            BossRushService rush = new BossRushService(plugin, mock(CombatMasterService.class));
            field(rush, "activeWorld").set(rush, world);
            field(rush, "activeWorldName").set(rush, world.getName());
            Field state = field(rush, "state");
            state.set(rush, Enum.valueOf((Class<Enum>) state.getType(), "ACTIVE"));
            Set<UUID> participants = (Set<UUID>) field(rush, "participants").get(rush);
            Set<UUID> alive = (Set<UUID>) field(rush, "alivePlayers").get(rush);
            Map<UUID, RouletteTeam> teams = (Map<UUID, RouletteTeam>) field(rush, "playerTeams").get(rush);
            Map<UUID, Location> spawns = (Map<UUID, Location>) field(rush, "playerStartingSpawns").get(rush);
            Map<UUID, RouletteTeam> external = (Map<UUID, RouletteTeam>) field(rush, "externalSpectators").get(rush);
            participants.add(red.getUniqueId());
            participants.add(blue.getUniqueId());
            alive.add(red.getUniqueId());
            alive.add(blue.getUniqueId());
            teams.put(red.getUniqueId(), RouletteTeam.RED);
            teams.put(blue.getUniqueId(), RouletteTeam.BLUE);
            spawns.put(red.getUniqueId(), red.getLocation());
            spawns.put(blue.getUniqueId(), blue.getLocation());
            assertEquals(2, rush.tabActivityCount(), "standalone BR count excludes external viewers");
            assertEquals(RouletteTeam.RED, rush.tabArenaTeam(red));
            assertEquals(RouletteTeam.BLUE, rush.tabArenaTeam(blue));
            Class<?> arenaType = Class.forName("hgloot.plugin.BossRushService$ArenaInstance");
            var constructor = arenaType.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Map<RouletteTeam, Object> arenas = (EnumMap<RouletteTeam, Object>) field(rush, "arenas").get(rush);
            arenas.put(RouletteTeam.RED, constructor.newInstance(RouletteTeam.RED, 0, 0, 0, 0, null));
            arenas.put(RouletteTeam.BLUE, constructor.newInstance(RouletteTeam.BLUE, 0, 0, 0, 0, null));

            assertEquals("bossrush:bossrush-match-1:RED", rush.spectatableMatchId(red));
            assertEquals("bossrush:bossrush-match-1:BLUE", rush.spectatableMatchId(blue));
            Method register = BossRushService.class.getDeclaredMethod("registerExternalSpectator",
                Player.class, RouletteTeam.class, Location.class);
            register.setAccessible(true);
            register.invoke(rush, observer, RouletteTeam.RED, red.getLocation());
            assertEquals(RouletteTeam.RED, external.get(observer.getUniqueId()));
            assertEquals(RouletteTeam.RED, rush.tabArenaTeam(observer));
            assertEquals(2, rush.tabActivityCount(), "an external spectator is not a BR player");
            assertFalse(participants.contains(observer.getUniqueId()));
            assertFalse(alive.contains(observer.getUniqueId()));
            assertFalse(teams.containsKey(observer.getUniqueId()));
            assertTrue(rush.isExternalSpectator(observer.getUniqueId()));
            assertTrue(rush.ownsSidebar(observer));
            assertTrue(rush.isAudienceSpectator(observer.getUniqueId()));
            assertEquals(rush.playerAudienceKey(red), rush.playerAudienceKey(observer));
            assertFalse(rush.playerAudienceKey(blue).equals(rush.playerAudienceKey(observer)));
            assertEquals(null, rush.spectatableMatchId(observer));
            Method allowed = BossRushService.class.getDeclaredMethod("canSpectateTarget", Player.class, Player.class);
            allowed.setAccessible(true);
            assertTrue((boolean) allowed.invoke(rush, observer, red));
            assertFalse((boolean) allowed.invoke(rush, observer, blue));
            assertNotNull(spawns.get(observer.getUniqueId()));

            // Once the observer's own arena is over, a permitted cross-team
            // teleport must change the viewing audience, not tournament ownership.
            Method completed = arenaType.getDeclaredMethod("completed", boolean.class);
            completed.setAccessible(true);
            completed.invoke(arenas.get(RouletteTeam.RED), true);
            assertTrue((boolean) allowed.invoke(rush, observer, blue));
            Method teleport = BossRushService.class.getDeclaredMethod("teleportSpectatorToTarget", Player.class, String.class);
            teleport.setAccessible(true);
            teleport.invoke(rush, observer, blue.getUniqueId().toString());
            assertEquals(RouletteTeam.RED, external.get(observer.getUniqueId()));
            assertEquals(RouletteTeam.BLUE, rush.tabArenaTeam(observer));
            assertEquals(rush.playerAudienceKey(blue), rush.playerAudienceKey(observer));
            assertFalse(rush.playerAudienceKey(red).equals(rush.playerAudienceKey(observer)));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void resultAndCleanupContractExcludesExternalObserverFromRewardsAndRequeue() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        assertTrue(source.contains("List<UUID> requeueCandidates = List.copyOf(participants);"));
        assertTrue(source.contains("returningIds.addAll(externalSpectators.keySet());"));
        assertTrue(source.contains("restoring.addAll(externalSpectators.keySet());"));
        assertTrue(source.contains("externalSpectators.clear();"));
        assertTrue(source.contains("if (externalSpectators.containsKey(player.getUniqueId())) leaveExternalSpectator(player);"));
        assertTrue(source.contains("viewers.addAll(externalSpectators.keySet());"));
        assertTrue(source.contains("for (UUID playerId : externalSpectators.keySet()) {"));
    }

    private static Field field(BossRushService rush, String name) throws Exception {
        Field result = rush.getClass().getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
