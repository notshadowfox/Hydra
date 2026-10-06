package hgloot.plugin;

import static hgloot.plugin.QueueService.QueueType.HUNGERGAMES;
import static hgloot.plugin.QueueService.QueueType.HUNGERGAMES_DUOS;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class HungerGamesLocalQueueSeparationTest {
    @RegisterExtension
    static final TestExecutionExceptionHandler unsupportedOperationsFail = (context, failure) -> {
        if (failure instanceof org.mockbukkit.mockbukkit.exception.UnimplementedOperationException) {
            throw new AssertionError("Unimplemented fixture operation must not skip a separation test", failure);
        }
        throw failure;
    };

    private ServerMock server;
    private QueueService service;
    private PartyService parties;
    @TempDir
    Path worldContainer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new ServerMock() {
            @Override
            public File getWorldContainer() {
                return worldContainer.toFile();
            }
        });
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        plugin.getConfig().set("queue.match_world.enabled", false);
        plugin.getConfig().set("queue.settings.min_players", 2);
        plugin.getConfig().set("queue.settings.ideal_players", 24);
        plugin.getConfig().set("queue.settings.auto_win_trigger", false);
        try {
            service = new QueueService(plugin, null);
        } catch (org.mockbukkit.mockbukkit.exception.UnimplementedOperationException unsupported) {
            throw new AssertionError("Queue fixture must not silently skip all separation tests", unsupported);
        }
        parties = new PartyService();
        service.setPartyService(parties);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void queueTypesAndCanonicalNetworkKeyStayDistinct() {
        assertEquals(HUNGERGAMES, QueueService.QueueType.fromKey("hungergames"));
        assertEquals(HUNGERGAMES_DUOS, QueueService.QueueType.fromKey("hungergames_duos"));
        assertTrue(QueueService.isHungerGames(HUNGERGAMES));
        assertTrue(QueueService.isHungerGames(HUNGERGAMES_DUOS));
        assertFalse(QueueService.isHungerGames(QueueService.QueueType.DUO));
        assertFalse(QueueService.isHungerGames(null));
    }

    @Test
    void simultaneousQueuesCountOnlyTheirOwnPlayersAndHaveIndependentThirtySecondCountdowns() throws Exception {
        PlayerMock solo = player("Solo");
        PlayerMock duo = player("Duo");
        service.joinHungerGamesSolo(solo);
        service.joinHungerGamesDuos(duo);
        assertTrue(map("countdowns").isEmpty(), "one player in each mode must not meet mincount two");
        assertNotEquals(id(false), id(true));
        assertTrue(service.isPlayerQueuedFor(solo.getUniqueId(), HUNGERGAMES));
        assertTrue(service.isPlayerQueuedFor(duo.getUniqueId(), HUNGERGAMES_DUOS));
        service.joinHungerGamesSolo(player("Solo2"));
        assertEquals(30, seconds(HUNGERGAMES));
        assertFalse(map("countdowns").containsKey(HUNGERGAMES_DUOS));
        service.joinHungerGamesDuos(player("Duo2"));
        assertEquals(30, seconds(HUNGERGAMES_DUOS));
        assertNotSame(map("countdowns").get(HUNGERGAMES), map("countdowns").get(HUNGERGAMES_DUOS));
        invoke("tickQueues");
        assertEquals(29, seconds(HUNGERGAMES));
        assertEquals(29, seconds(HUNGERGAMES_DUOS));
        service.leaveQueueOrMatch(solo);
        invoke("tickQueues");
        assertFalse(map("countdowns").containsKey(HUNGERGAMES));
        assertEquals(28, seconds(HUNGERGAMES_DUOS));
        assertEquals(3, service.hungerGamesWaitingCount());
        assertEquals(3, service.hungerGamesTabActivityCount());
    }

    @Test
    void rejectedSoloSwitchAndDuplicateDuoJoinDoNotDestroyPartyPairing() throws Exception {
        PlayerMock leader = player("Leader");
        PlayerMock member = player("Member");
        party(leader, member);
        service.joinHungerGamesDuos(leader);
        String duoId = id(true);
        Object before = invoke("hungerGamesDuoQueueGroup", member.getUniqueId());
        service.joinHungerGamesDuos(leader);
        assertEquals(before, invoke("hungerGamesDuoQueueGroup", member.getUniqueId()));
        set("idealPlayers", 2);
        service.joinHungerGamesSolo(player("Solo1"));
        service.joinHungerGamesSolo(player("Solo2"));
        service.joinHungerGamesSolo(leader);
        assertTrue(service.isPlayerQueuedFor(leader.getUniqueId(), HUNGERGAMES_DUOS));
        assertTrue(service.isPlayerQueuedFor(member.getUniqueId(), HUNGERGAMES_DUOS));
        assertEquals(before, invoke("hungerGamesDuoQueueGroup", member.getUniqueId()));
        assertEquals(duoId, id(true));
        service.leaveQueueOrMatch(member);
        assertFalse(service.isPlayerQueued(leader.getUniqueId()));
        assertTrue(service.hungerGamesLocalLobbyIds(true).isEmpty());
        assertEquals(2, service.hungerGamesWaitingCount());
    }

    @Test
    void genericSoloEntryNeverQueuesThePartyAndOversizedDuosRejectAtomically() {
        PlayerMock leader = player("Leader");
        PlayerMock member = player("Member");
        PlayerMock third = player("Third");
        party(leader, member);
        parties.invite(leader.getUniqueId(), third.getUniqueId());
        parties.acceptInvite(third.getUniqueId(), leader.getUniqueId());
        service.joinQueue(leader, HUNGERGAMES);
        service.joinHungerGamesDuos(leader);
        assertTrue(service.isPlayerQueuedFor(leader.getUniqueId(), HUNGERGAMES));
        assertFalse(service.isPlayerQueued(member.getUniqueId()));
        assertFalse(service.isPlayerQueued(third.getUniqueId()));
        assertTrue(service.hungerGamesLocalLobbyIds(true).isEmpty());
    }

    @Test
    void partiesCreatedAfterSeparateQueueJoinsArePreservedWithoutRequeueing() throws Exception {
        PlayerMock leader = player("TestLeader");
        PlayerMock member = player("TestMember");
        PlayerMock other = player("Other");
        service.joinHungerGamesDuos(leader);
        service.joinHungerGamesDuos(other);
        service.joinHungerGamesDuos(member);
        String lobby = id(true);
        int countdown = seconds(HUNGERGAMES_DUOS);
        party(leader, member);
        List<?> selected = (List<?>) invoke("selectWaitingHungerGamesDuos", 3);
        Map<?, ?> assigned = (Map<?, ?>) invoke("assignHungerGamesDuoTeams", selected);
        assertEquals(assigned.get(leader.getUniqueId()), assigned.get(member.getUniqueId()));
        assertNotEquals(assigned.get(leader.getUniqueId()), assigned.get(other.getUniqueId()));
        assertEquals(lobby, id(true));
        assertEquals(countdown, seconds(HUNGERGAMES_DUOS));
        assertTrue(map("duoTeamByPlayer").isEmpty(), "waiting groups must not become active teams early");
    }

    @Test
    void disbandAndNewPartnerImmediatelyReplaceOldWaitingGroupAndLeaveBehavior() throws Exception {
        PlayerMock leader = player("Leader");
        PlayerMock oldPartner = player("OldPartner");
        PlayerMock newPartner = player("NewPartner");
        party(leader, oldPartner);
        service.joinHungerGamesDuos(leader);
        service.joinHungerGamesDuos(newPartner);
        parties.disband(leader.getUniqueId());
        assertEquals(oldPartner.getUniqueId(), invoke("hungerGamesDuoQueueGroup", oldPartner.getUniqueId()));
        party(leader, newPartner);
        assertEquals(leader.getUniqueId(), invoke("hungerGamesDuoQueueGroup", newPartner.getUniqueId()));
        service.leaveQueueOrMatch(oldPartner);
        assertTrue(service.isPlayerQueued(leader.getUniqueId()));
        assertTrue(service.isPlayerQueued(newPartner.getUniqueId()));
        service.leaveQueueOrMatch(newPartner);
        assertFalse(service.isPlayerQueued(leader.getUniqueId()));
        assertFalse(service.isPlayerQueued(newPartner.getUniqueId()));
    }

    @Test
    void rosterCapacityNeverSplitsANewlyFormedParty() throws Exception {
        PlayerMock first = player("First");
        PlayerMock leader = player("Leader");
        PlayerMock last = player("Last");
        PlayerMock partner = player("Partner");
        for (PlayerMock p : List.of(first, leader, last, partner)) service.joinHungerGamesDuos(p);
        party(leader, partner);
        assertEquals(List.of(first.getUniqueId(), last.getUniqueId()), invoke("selectWaitingHungerGamesDuos", 2));
        assertEquals(List.of(first.getUniqueId(), leader.getUniqueId(), partner.getUniqueId()),
            invoke("selectWaitingHungerGamesDuos", 3));
    }

    @Test
    void partyInAnotherQueueDoesNotMoveOrAssignThatMember() throws Exception {
        PlayerMock leader = player("Leader");
        PlayerMock member = player("Member");
        service.joinHungerGamesDuos(leader);
        service.joinHungerGamesSolo(member);
        party(leader, member);
        assertEquals(List.of(leader.getUniqueId()), invoke("selectWaitingHungerGamesDuos", 24));
        assertTrue(service.isPlayerQueuedFor(member.getUniqueId(), HUNGERGAMES));
        service.leaveQueueOrMatch(leader);
        assertTrue(service.isPlayerQueuedFor(member.getUniqueId(), HUNGERGAMES));
    }

    @Test
    void growingPartyBeyondDuoLimitRemovesOnlyItsWaitingDuoMembers() throws Exception {
        PlayerMock leader = player("Leader");
        PlayerMock member = player("Member");
        PlayerMock third = player("Third");
        PlayerMock unrelated = player("Unrelated");
        party(leader, member);
        service.joinHungerGamesDuos(leader);
        service.joinHungerGamesSolo(third);
        service.joinHungerGamesDuos(unrelated);
        parties.invite(leader.getUniqueId(), third.getUniqueId());
        assertEquals(PartyService.InviteResponse.ACCEPTED, parties.acceptInvite(third.getUniqueId(), leader.getUniqueId()));
        invoke("tickQueues");
        assertFalse(service.isPlayerQueued(leader.getUniqueId()));
        assertFalse(service.isPlayerQueued(member.getUniqueId()));
        assertTrue(service.isPlayerQueuedFor(third.getUniqueId(), HUNGERGAMES));
        assertTrue(service.isPlayerQueuedFor(unrelated.getUniqueId(), HUNGERGAMES_DUOS));
    }

    @Test
    void partyChangesDuringWorldPreparationApplyButReservedTeamSnapshotDoesNotChange() throws Exception {
        PlayerMock leader = player("Leader");
        PlayerMock member = player("Member");
        service.joinHungerGamesDuos(leader);
        service.joinHungerGamesDuos(member);
        set("matchWorldEnabled", true);
        setOf("prewarmInProgress").add(HUNGERGAMES_DUOS);
        service.forceStartHungerGames(true, id(true), server.getConsoleSender());
        party(leader, member);
        List<?> selected = (List<?>) invoke("selectWaitingHungerGamesDuos", 24);
        Map<?, ?> assigned = (Map<?, ?>) invoke("assignHungerGamesDuoTeams", selected);
        Map<?, ?> frozen = Map.copyOf(assigned);
        assertEquals(assigned.get(leader.getUniqueId()), assigned.get(member.getUniqueId()));
        invoke("reservePlayersForMatchLaunch", selected, HUNGERGAMES_DUOS, "HG-World-1");
        parties.disband(leader.getUniqueId());
        assertEquals(frozen, assigned, "launch callbacks keep the captured teams, not current party membership");
        assertEquals(List.of(), invoke("selectWaitingHungerGamesDuos", 24));
        assertTrue(map("pendingMatchLaunches").containsKey(leader.getUniqueId()));
        assertTrue(map("pendingMatchLaunches").containsKey(member.getUniqueId()));
    }

    @Test
    void foreignStaleAndNetworkWorldIdsCannotStartOrMutateEitherQueue() throws Exception {
        PlayerMock solo = player("Solo");
        service.joinHungerGamesSolo(solo);
        service.joinHungerGamesDuos(player("Duo"));
        String stale = id(false);
        String duoId = id(true);
        assertFalse(service.forceStartHungerGames(false, duoId, server.getConsoleSender()));
        assertFalse(service.forceStartHungerGames(true, stale, server.getConsoleSender()));
        assertFalse(service.forceStartHungerGames(false, "HG-World-1", server.getConsoleSender()));
        service.clearQueue(HUNGERGAMES);
        service.joinHungerGamesSolo(solo);
        assertNotEquals(stale, id(false));
        assertFalse(service.forceStartHungerGames(false, stale, server.getConsoleSender()));
        assertEquals(duoId, id(true));
        assertTrue(setOf("matchStartInProgress").isEmpty());
        assertTrue(map("pendingMatchStarts").isEmpty());
    }

    @Test
    void forceStartsWaitIndependentlyAndClearingSoloCannotCancelDuosPreparation() throws Exception {
        service.joinHungerGamesSolo(player("Solo"));
        service.joinHungerGamesDuos(player("Duo"));
        set("matchWorldEnabled", true);
        // Hold the existing async-prewarm boundary so no world generation is required by this unit test.
        setOf("prewarmInProgress").addAll(List.of(HUNGERGAMES, HUNGERGAMES_DUOS));
        assertTrue(service.forceStartHungerGames(false, id(false), server.getConsoleSender()));
        assertTrue(service.forceStartHungerGames(true, id(true), server.getConsoleSender()));
        assertEquals(2, map("pendingMatchStarts").size());
        Object duosPending = map("pendingMatchStarts").get(HUNGERGAMES_DUOS);
        assertEquals(1, ((Deque<?>) duosPending).size());
        assertFalse(service.forceStartHungerGames(true, id(true), server.getConsoleSender()));
        service.clearQueue(HUNGERGAMES);
        assertFalse(map("pendingMatchStarts").containsKey(HUNGERGAMES));
        assertSame(duosPending, map("pendingMatchStarts").get(HUNGERGAMES_DUOS));
        assertTrue(setOf("matchStartInProgress").contains(HUNGERGAMES_DUOS));
        assertFalse(setOf("matchStartInProgress").contains(HUNGERGAMES));
        assertEquals(1, service.hungerGamesWaitingCount());
    }

    @Test
    void clearedGenerationCannotResumeIntoTheReplacementQueue() throws Exception {
        PlayerMock solo = player("Solo");
        service.joinHungerGamesSolo(solo);
        set("matchWorldEnabled", true);
        setOf("prewarmInProgress").add(HUNGERGAMES);
        assertTrue(service.forceStartHungerGames(false, id(false), server.getConsoleSender()));
        invoke("continueStartMatchAfterBrWorldReady", HUNGERGAMES, true, server.getConsoleSender());
        service.clearQueue(HUNGERGAMES);
        service.joinHungerGamesSolo(solo);
        server.getScheduler().performOneTick();
        assertTrue(map("pendingMatchStarts").isEmpty());
        assertFalse(setOf("matchStartInProgress").contains(HUNGERGAMES));
        assertTrue(service.isPlayerQueuedFor(solo.getUniqueId(), HUNGERGAMES));
    }

    @Test
    void launchReservationRetiresOnlyItsGenerationAndRemainsCancellable() throws Exception {
        PlayerMock solo = player("Solo");
        service.joinHungerGamesSolo(solo);
        service.joinHungerGamesDuos(player("Duo"));
        String oldSolo = id(false);
        String duos = id(true);
        invoke("reservePlayersForMatchLaunch", List.of(solo.getUniqueId()), HUNGERGAMES, "HG-World-1");
        assertTrue(service.hungerGamesLocalLobbyIds(false).isEmpty());
        assertEquals(duos, id(true));
        assertTrue(map("pendingMatchLaunches").containsKey(solo.getUniqueId()));
        service.joinHungerGamesDuos(solo);
        assertFalse(service.isPlayerQueued(solo.getUniqueId()));
        service.leaveQueueOrMatch(solo);
        assertFalse(map("pendingMatchLaunches").containsKey(solo.getUniqueId()));
        service.joinHungerGamesSolo(solo);
        assertNotEquals(oldSolo, id(false));
    }

    @Test
    void clearingNextQueueDoesNotReleaseTheOlderLaunchGuard() throws Exception {
        PlayerMock first = player("First");
        PlayerMock next = player("Next");
        service.joinHungerGamesSolo(first);
        setOf("matchStartInProgress").add(HUNGERGAMES);
        invoke("reservePlayersForMatchLaunch", List.of(first.getUniqueId()), HUNGERGAMES, "HG-World-1");
        service.joinHungerGamesSolo(next);
        service.clearQueue(HUNGERGAMES);
        assertTrue(setOf("matchStartInProgress").contains(HUNGERGAMES));
        assertTrue(map("pendingMatchLaunches").containsKey(first.getUniqueId()));
    }

    @Test
    void abandonedLaunchRetainsItsGuardUntilTheOutstandingCallbackFinishes() throws Exception {
        PlayerMock first = player("First");
        PlayerMock next = player("Next");
        service.joinHungerGamesSolo(first);
        setOf("matchStartInProgress").add(HUNGERGAMES);
        invoke("reservePlayersForMatchLaunch", List.of(first.getUniqueId()), HUNGERGAMES, "HG-World-1");
        service.leaveQueueOrMatch(first);
        assertTrue(map("pendingMatchLaunches").isEmpty());
        service.joinHungerGamesSolo(next);
        service.clearQueue(HUNGERGAMES);
        service.joinHungerGamesSolo(next);
        assertTrue(setOf("matchStartInProgress").contains(HUNGERGAMES));
        assertFalse(service.forceStartHungerGames(false, id(false), server.getConsoleSender()));
        invoke("clearMatchStartInProgress", HUNGERGAMES);
        set("matchWorldEnabled", true);
        setOf("prewarmInProgress").add(HUNGERGAMES);
        assertTrue(service.forceStartHungerGames(false, id(false), server.getConsoleSender()));
        assertEquals(1, map("pendingMatchStarts").size());
    }

    @Test
    void prewarmDemandOwnershipAndCleanupAreModeScoped() throws Exception {
        service.joinHungerGamesSolo(player("Solo"));
        service.joinHungerGamesDuos(player("Duo"));
        set("matchWorldEnabled", true);
        map("managedMatchWorldTypes").put("HG-World-1", HUNGERGAMES);
        map("managedMatchWorldTypes").put("HG-World-2", HUNGERGAMES_DUOS);
        setOf("detachedPrewarmedWorldsPendingShutdown").add("HG-World-1");
        assertEquals(false, invoke("shouldRequestPrewarmedMatchWorld", HUNGERGAMES));
        assertEquals(true, invoke("shouldRequestPrewarmedMatchWorld", HUNGERGAMES_DUOS));
        assertEquals(HUNGERGAMES_DUOS, service.resolveQueueTypeForWorld("HG-World-2"));
        assertTrue(service.isHungerGamesMatchWorldName("HG-World-2"));
        invoke("addPrewarmedMatchWorld", HUNGERGAMES_DUOS, "HG-World-2");
        assertEquals(false, invoke("shouldRequestPrewarmedMatchWorld", HUNGERGAMES_DUOS));
        service.clearQueue(HUNGERGAMES);
        assertEquals(List.of("HG-World-2"), invoke("prewarmedMatchWorldsFor", HUNGERGAMES_DUOS));
    }

    @Test
    void bothModesRetainHgHealthRewardsAndReviveClassification() throws Exception {
        PlayerMock player = player("Player");
        for (QueueService.QueueType type : List.of(HUNGERGAMES, HUNGERGAMES_DUOS)) {
            invoke("fullyHealMatchPlayer", player, type);
            assertEquals(40.0D, player.getHealth());
            assertEquals(true, invoke("supportsReviveSnapshot", type));
            assertEquals(invoke("matchWinSilverReward", HUNGERGAMES), invoke("matchWinSilverReward", type));
            assertEquals(false, invoke("useBrTerrain", type));
            invoke("recordLifetimeKill", player.getUniqueId(), type);
            invoke("recordLifetimeWin", player.getUniqueId(), type);
            invoke("restoreStandardPlayerHealth", player);
            assertEquals(20.0D, player.getHealth());
        }
        assertEquals(2, map("hgLifetimeKills").get(player.getUniqueId()));
        assertEquals(2, map("hgLifetimeWins").get(player.getUniqueId()));
        assertTrue(map("brLifetimeKills").isEmpty());
        assertTrue(map("brLifetimeWins").isEmpty());
    }

    @Test
    void statusAndSidebarExposeTheSameModeSpecificGeneration() throws Exception {
        PlayerMock solo = player("Solo");
        PlayerMock duo = player("Duo");
        service.joinHungerGamesSolo(solo);
        service.joinHungerGamesDuos(duo);
        while (solo.nextMessage() != null) { }
        service.sendStatus(solo, HUNGERGAMES);
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = solo.nextMessage()) != null) messages.add(message);
        assertTrue(messages.stream().anyMatch(line -> line.contains(id(false))));
        assertFalse(messages.stream().anyMatch(line -> line.contains(id(true))));
        assertLobbySidebar(solo, "Solos", "Duos", id(false), id(true));
        assertLobbySidebar(duo, "Duos", "Solos", id(true), id(false));
    }

    private PlayerMock player(String name) {
        PlayerMock player = new PlayerMock(server, name) {
            private boolean canPickupItems = true;

            @Override
            public void setCanPickupItems(boolean canPickupItems) {
                this.canPickupItems = canPickupItems;
            }

            @Override
            public boolean getCanPickupItems() {
                return canPickupItems;
            }
        };
        server.addPlayer(player);
        player.setOp(true);
        return player;
    }

    private void assertLobbySidebar(PlayerMock player, String ownType, String foreignType,
                                    String ownId, String foreignId) throws Exception {
        Objective sidebar = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        assertNotNull(sidebar);
        // MockBukkit getEntries() omits objective scores; inspect the displayed objective directly.
        String ownTypeLine = (String) invoke("sidebarSystem", "Type", ownType, ChatColor.WHITE);
        String foreignTypeLine = (String) invoke("sidebarSystem", "Type", foreignType, ChatColor.WHITE);
        String ownLobbyLine = (String) invoke("sidebarSystem", "Lobby", ownId, ChatColor.WHITE);
        String foreignLobbyLine = (String) invoke("sidebarSystem", "Lobby", foreignId, ChatColor.WHITE);
        assertTrue(sidebar.getScore(ownTypeLine).isScoreSet());
        assertFalse(sidebar.getScore(foreignTypeLine).isScoreSet());
        assertFalse(sidebar.getScore(ownLobbyLine).isScoreSet());
        assertFalse(sidebar.getScore(foreignLobbyLine).isScoreSet());
    }

    private void party(PlayerMock leader, PlayerMock member) {
        parties.createParty(leader.getUniqueId());
        parties.invite(leader.getUniqueId(), member.getUniqueId());
        parties.acceptInvite(member.getUniqueId(), leader.getUniqueId());
    }

    private String id(boolean duos) {
        return service.hungerGamesLocalLobbyIds(duos).getFirst();
    }

    private int seconds(QueueService.QueueType type) throws Exception {
        Object countdown = map("countdowns").get(type);
        Field field = countdown.getClass().getDeclaredField("secondsRemaining");
        field.setAccessible(true);
        return field.getInt(countdown);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> map(String name) throws Exception {
        return (Map<Object, Object>) field(name).get(service);
    }

    @SuppressWarnings("unchecked")
    private Set<Object> setOf(String name) throws Exception {
        return (Set<Object>) field(name).get(service);
    }

    private void set(String name, Object value) throws Exception {
        field(name).set(service, value);
    }

    private Field field(String name) throws Exception {
        Field field = QueueService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private Object invoke(String name, Object... args) throws Exception {
        for (Method method : QueueService.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try {
                    return method.invoke(service, args);
                } catch (InvocationTargetException exception) {
                    if (exception.getCause() instanceof Exception cause) throw cause;
                    throw exception;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
}
