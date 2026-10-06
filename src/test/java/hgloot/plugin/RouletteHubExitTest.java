package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hgloot.plugin.ManagedMinigameRound.Game;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

final class RouletteHubExitTest {
    private HGLootPlugin plugin;
    private MinigameRouletteService roulette;
    private BridgeService bridge;
    private SpleefService spleef;
    private VoidRouletteService voidRoulette;
    private BossRushService bossRush;
    private Player player;
    private Player teammate;
    private ServerMock server;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        server = MockBukkit.mock();
        var arena = server.addSimpleWorld("MR-Hub-Exit-Test");
        player = server.addPlayer("LeavingPlayer");
        teammate = server.addPlayer("StayingPlayer");
        player.teleport(new Location(arena, 0, 80, 0));
        teammate.teleport(new Location(arena, 1, 80, 0));
        plugin = mock(HGLootPlugin.class);
        bridge = mock(BridgeService.class);
        spleef = mock(SpleefService.class);
        voidRoulette = mock(VoidRouletteService.class);
        bossRush = mock(BossRushService.class);
        when(plugin.bridgeService()).thenReturn(bridge);
        when(plugin.spleefService()).thenReturn(spleef);
        when(plugin.voidRouletteService()).thenReturn(voidRoulette);
        when(plugin.bossRushService()).thenReturn(bossRush);
        roulette = new MinigameRouletteService(plugin, mock(PartyService.class));
        field("glowService").set(roulette, mock(ViewerGlowService.class));
        field("rosterLocked").setBoolean(roulette, true);
        field("state").set(roulette, enumState("ROUND"));
        field("activeRoundId").set(roulette, UUID.randomUUID());
        field("tournamentId").set(roulette, UUID.randomUUID());
        field("currentRoundIndex").setInt(roulette, 0);
        ((Set<UUID>) field("roster").get(roulette)).addAll(List.of(player.getUniqueId(), teammate.getUniqueId()));
        ((Map<UUID, RouletteTeam>) field("playerTeams").get(roulette)).put(player.getUniqueId(), RouletteTeam.RED);
        ((Map<UUID, RouletteTeam>) field("playerTeams").get(roulette)).put(teammate.getUniqueId(), RouletteTeam.BLUE);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void livingAndSpectatorHubRoutesCoverEveryChildModeWithoutLeavingTheTournament() throws Exception {
        for (Game game : Game.values()) {
            field("roundOrder").set(roulette, List.of(game));
            ((Set<UUID>) field("roundHubPlayers").get(roulette)).clear();
            reset(bridge, spleef, voidRoulette, bossRush);
            stubReturn(game, true);
            assertTrue(roulette.returnCurrentRoundPlayerToHub(player), game + " living /hub");
            assertTrue(roulette.isTournamentLocked(player.getUniqueId()));
            assertFalse(roulette.rejoinTournamentPlayer(player), "Hub lasts for this round");
            assertTrue(((Set<UUID>) field("roundHubPlayers").get(roulette)).contains(player.getUniqueId()));
            verifyLivingRoute(game);

            ((Set<UUID>) field("roundHubPlayers").get(roulette)).clear();
            reset(bridge, spleef, voidRoulette, bossRush);
            stubReturn(game, false);
            assertTrue(roulette.returnCurrentRoundPlayerToHub(player), game + " spectator /hub");
            assertTrue(roulette.isTournamentLocked(player.getUniqueId()));
            verifySpectatorRoute(game);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRemovesCommitmentAndNextRoundClearsHubHold() throws Exception {
        field("roundOrder").set(roulette, List.of(Game.BRIDGE, Game.SPLEEF));
        when(bridge.forfeitTournamentPlayerToHub(player)).thenReturn(true);
        assertTrue(roulette.returnCurrentRoundPlayerToHub(player));
        assertTrue(roulette.isTournamentLocked(player.getUniqueId()));
        assertTrue(roulette.leaveTournament(player));
        assertFalse(roulette.isTournamentLocked(player.getUniqueId()));
        assertFalse(((Set<UUID>) field("roundHubPlayers").get(roulette)).contains(player.getUniqueId()));

        ((Set<UUID>) field("roundHubPlayers").get(roulette)).add(teammate.getUniqueId());
        when(spleef.startTournamentRound(any(), any())).thenReturn(true);
        Method next = MinigameRouletteService.class.getDeclaredMethod("beginNextRound");
        next.setAccessible(true);
        next.invoke(roulette);
        assertFalse(((Set<UUID>) field("roundHubPlayers").get(roulette)).contains(teammate.getUniqueId()));
        assertTrue(roulette.isActiveRoundMember(teammate.getUniqueId()));
        verify(spleef).startTournamentRound(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void spectatorAlreadyInHubRemainsCommittedWithoutReenteringTheChildRound() throws Exception {
        var hub = server.addSimpleWorld("Hub");
        player.teleport(new Location(hub, 0, 80, 0));
        for (Game game : Game.values()) {
            field("roundOrder").set(roulette, List.of(game));
            ((Set<UUID>) field("roundHubPlayers").get(roulette)).clear();
            assertTrue(roulette.returnCurrentRoundPlayerToHub(player));
            assertTrue(roulette.isTournamentLocked(player.getUniqueId()));
            assertFalse(roulette.rejoinTournamentPlayer(player));
        }
    }

    private void stubReturn(Game game, boolean living) {
        switch (game) {
            case BRIDGE -> {
                if (living) when(bridge.forfeitTournamentPlayerToHub(player)).thenReturn(true);
                else when(bridge.returnTournamentSpectatorToHub(player)).thenReturn(true);
            }
            case SPLEEF -> {
                if (living) when(spleef.forfeitTournamentPlayerToHub(player)).thenReturn(true);
                else when(spleef.returnTournamentSpectatorToHub(player)).thenReturn(true);
            }
            case VOID_ROULETTE -> {
                if (living) when(voidRoulette.forfeitTournamentPlayerToHub(player)).thenReturn(true);
                else when(voidRoulette.returnTournamentSpectatorToHub(player)).thenReturn(true);
            }
            case BOSS_RUSH -> {
                if (living) when(bossRush.forfeitTournamentPlayerToHub(player)).thenReturn(true);
                else when(bossRush.returnTournamentSpectatorToHub(player)).thenReturn(true);
            }
        }
    }

    private void verifyLivingRoute(Game game) {
        switch (game) {
            case BRIDGE -> verify(bridge).forfeitTournamentPlayerToHub(player);
            case SPLEEF -> verify(spleef).forfeitTournamentPlayerToHub(player);
            case VOID_ROULETTE -> verify(voidRoulette).forfeitTournamentPlayerToHub(player);
            case BOSS_RUSH -> verify(bossRush).forfeitTournamentPlayerToHub(player);
        }
    }

    private void verifySpectatorRoute(Game game) {
        switch (game) {
            case BRIDGE -> verify(bridge).returnTournamentSpectatorToHub(player);
            case SPLEEF -> verify(spleef).returnTournamentSpectatorToHub(player);
            case VOID_ROULETTE -> verify(voidRoulette).returnTournamentSpectatorToHub(player);
            case BOSS_RUSH -> verify(bossRush).returnTournamentSpectatorToHub(player);
        }
    }

    private Field field(String name) throws Exception {
        Field field = MinigameRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object enumState(String name) throws Exception {
        return Enum.valueOf((Class<Enum>) field("state").getType(), name);
    }
}
