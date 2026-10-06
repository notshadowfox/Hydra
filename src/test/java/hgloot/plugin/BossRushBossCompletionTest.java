package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BossRushBossCompletionTest {
    private static final Class<?> ARENA_CLASS = findArenaClass();

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void defeatedBossCompletesExactlyOneArenaAndDuplicateSignalDoesNotAwardTwice() throws Exception {
        Fixture fixture = fixture();
        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.DEFEATED);

        assertTrue(flag(fixture.red, "completed"));
        assertFalse(flag(fixture.red, "eliminated"));
        assertNull(value(fixture.red, "bossId"));
        assertFalse(fixture.bossTeams.containsKey(fixture.redBoss));
        assertFalse(allTeamsFinished(fixture.service));

        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.DEFEATED);
        assertTrue(flag(fixture.red, "completed"));
        assertFalse(flag(fixture.red, "eliminated"));
    }

    @Test
    void canceledBossCannotLeaveArenaWaitingForTimeoutOrOverrideDefeat() throws Exception {
        Fixture fixture = fixture();
        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.CANCELED);
        assertFalse(flag(fixture.red, "completed"));
        assertTrue(flag(fixture.red, "eliminated"));
        assertNull(value(fixture.red, "bossId"));

        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.DEFEATED);
        assertFalse(flag(fixture.red, "completed"));
        assertTrue(flag(fixture.red, "eliminated"));
    }

    @Test
    void offlineAndAlreadySpectatingParticipantsDoNotGateBossCompletion() throws Exception {
        Fixture fixture = fixture();
        @SuppressWarnings("unchecked")
        Set<UUID> participants = (Set<UUID>) value(fixture.service, "participants");
        @SuppressWarnings("unchecked")
        Map<UUID, RouletteTeam> teams = (Map<UUID, RouletteTeam>) value(fixture.service, "playerTeams");
        UUID disconnected = UUID.randomUUID();
        UUID spectator = UUID.randomUUID();
        participants.add(disconnected);
        participants.add(spectator);
        teams.put(disconnected, RouletteTeam.RED);
        teams.put(spectator, RouletteTeam.RED);
        // Neither player is in alivePlayers. The outcome is arena-owned, not roster-owned.
        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.DEFEATED);
        assertTrue(flag(fixture.red, "completed"));
    }

    @Test
    void allArenasResolvedMakesMatchEligibleToFinishWithoutTimer() throws Exception {
        Fixture fixture = fixture();
        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.DEFEATED);
        @SuppressWarnings("unchecked")
        EnumMap<RouletteTeam, Object> arenas = (EnumMap<RouletteTeam, Object>) value(fixture.service, "arenas");
        Object blue = arenas.get(RouletteTeam.BLUE);
        Method resolve = ARENA_CLASS.getDeclaredMethod("resolve", CombatMasterService.Completion.class);
        resolve.setAccessible(true);
        assertTrue((boolean) resolve.invoke(blue, CombatMasterService.Completion.CANCELED));
        assertTrue(allTeamsFinished(fixture.service));
        assertFalse((boolean) resolve.invoke(blue, CombatMasterService.Completion.DEFEATED));
        assertTrue(flag(blue, "eliminated"));
    }

    @Test
    void completionAfterTimeoutBeginsCannotChangeTheResult() throws Exception {
        Fixture fixture = fixture();
        Class<?> stateType = Class.forName("hgloot.plugin.BossRushService$State");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object ending = Enum.valueOf((Class<Enum>) stateType, "ENDING");
        set(fixture.service, "state", ending);
        complete(fixture.service, RouletteTeam.RED, CombatMasterService.Completion.DEFEATED);
        assertFalse(flag(fixture.red, "completed"));
        assertFalse(flag(fixture.red, "eliminated"));
    }

    @Test
    void deathAndTimeoutUseOneTerminalMatchGateForStandaloneAndManagedRounds() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        assertTrue(source.contains("if (state != State.ACTIVE || !arena.resolve(completion)) return;"));
        assertTrue(source.contains("if (allTeamsFinished()) finishMatch(\"All team arenas finished\", false);"));
        assertTrue(source.contains("if (elapsedTicks >= ROUND_SECONDS * 20) finishMatch(\"Time expired\", false);"));
        assertTrue(source.contains("if (state != State.ACTIVE) return;"));
        assertTrue(source.contains("state = State.ENDING;"));
        assertTrue(source.contains("dispatchManagedResult(request,"));
        assertTrue(source.contains("requeueAfterCompletedSession("));
        assertTrue(source.contains("cleanupWorld();"));
        assertTrue(source.contains("resetState();"));
        assertTrue(source.contains("templateWorlds.dispose();"));
    }

    @Test
    void phaseBoundaryAndDeadEntityFallbackRetainDefeatedOutcome() throws Exception {
        String combat = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        String rush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        assertTrue(combat.contains("case MASTERY -> 0.0D;"));
        assertTrue(combat.contains("cleanup(a, Completion.DEFEATED);"));
        assertTrue(combat.contains("cleanup(a, a.boss.isDead() ? Completion.DEFEATED : Completion.CANCELED);"));
        assertTrue(combat.contains("cleanup(active, active.boss.isDead() ? Completion.DEFEATED : Completion.CANCELED);"));
        assertTrue(rush.contains("onBossComplete(arena.team(), CombatMasterService.Completion.CANCELED);"));
        assertTrue(rush.contains("if (arena.bossId() == null) {"));
    }

    private static Fixture fixture() throws Exception {
        MockBukkit.mock();
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        when(plugin.namespace()).thenReturn("hydramain");
        BossRushService service = new BossRushService(plugin, mock(CombatMasterService.class));
        @SuppressWarnings("unchecked")
        EnumMap<RouletteTeam, Object> arenas = (EnumMap<RouletteTeam, Object>) value(service, "arenas");
        Object red = arena(RouletteTeam.RED);
        Object blue = arena(RouletteTeam.BLUE);
        UUID redBoss = UUID.randomUUID();
        UUID blueBoss = UUID.randomUUID();
        set(red, "bossId", redBoss);
        set(blue, "bossId", blueBoss);
        arenas.put(RouletteTeam.RED, red);
        arenas.put(RouletteTeam.BLUE, blue);
        @SuppressWarnings("unchecked")
        Map<UUID, RouletteTeam> bossTeams = (Map<UUID, RouletteTeam>) value(service, "bossTeams");
        bossTeams.put(redBoss, RouletteTeam.RED);
        bossTeams.put(blueBoss, RouletteTeam.BLUE);
        Class<?> stateType = Class.forName("hgloot.plugin.BossRushService$State");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object active = Enum.valueOf((Class<Enum>) stateType, "ACTIVE");
        set(service, "state", active);
        return new Fixture(service, red, redBoss, bossTeams);
    }

    private static Object arena(RouletteTeam team) throws Exception {
        Constructor<?> constructor = ARENA_CLASS.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(team, 0, 0, 0, 0, null);
    }

    private static void complete(BossRushService service, RouletteTeam team,
                                 CombatMasterService.Completion completion) throws Exception {
        Method method = BossRushService.class.getDeclaredMethod("onBossComplete", RouletteTeam.class,
            CombatMasterService.Completion.class);
        method.setAccessible(true);
        method.invoke(service, team, completion);
    }

    private static boolean allTeamsFinished(BossRushService service) throws Exception {
        Method method = BossRushService.class.getDeclaredMethod("allTeamsFinished");
        method.setAccessible(true);
        return (boolean) method.invoke(service);
    }

    private static boolean flag(Object target, String field) throws Exception {
        return (boolean) value(target, field);
    }

    private static Object value(Object target, String field) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        return declared.get(target);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private static Class<?> findArenaClass() {
        try {
            return Class.forName("hgloot.plugin.BossRushService$ArenaInstance");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Fixture(BossRushService service, Object red, UUID redBoss,
                           Map<UUID, RouletteTeam> bossTeams) {}
}
