package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hgloot.plugin.ManagedMinigameRound.Game;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class MinigameRouletteTeamPresentationTest {
    private HGLootPlugin plugin;
    private MinigameRouletteService roulette;
    private HydraPlusService tab;
    private List<Player> players;
    private Player outsider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        var server = MockBukkit.mock();
        var arena = server.addSimpleWorld("bridge-world-presentation");
        var hub = server.addSimpleWorld("GameHub");
        players = new ArrayList<>();
        for (RouletteTeam team : RouletteTeam.values()) {
            for (int i = 0; i < 2; i++) {
                Player player = server.addPlayer(team.name() + i);
                player.teleport(new Location(arena, 0, 80, 0));
                players.add(player);
            }
        }
        outsider = server.addPlayer("Outsider");
        outsider.teleport(new Location(hub, 0, 80, 0));
        plugin = mock(HGLootPlugin.class);
        BridgeService bridge = mock(BridgeService.class);
        when(plugin.bridgeService()).thenReturn(bridge);
        for (Player player : players) {
            when(bridge.playerAudienceKey(player)).thenReturn("bridge:" + arena.getName());
        }
        roulette = new MinigameRouletteService(plugin, mock(PartyService.class));
        when(plugin.minigameRouletteService()).thenReturn(roulette);
        QueueService queue = mock(QueueService.class);
        when(plugin.queueService()).thenReturn(queue);
        for (Player player : players) {
            when(queue.hungerGamesDuoTeamColorRgb(player.getUniqueId())).thenReturn(null);
            when(queue.hungerGamesDuoTeamTabIndex(player.getUniqueId())).thenReturn(null);
        }
        when(queue.hungerGamesDuoTeamColorRgb(outsider.getUniqueId())).thenReturn(null);
        when(queue.hungerGamesDuoTeamTabIndex(outsider.getUniqueId())).thenReturn(null);
        tab = new HydraPlusService(plugin);
        field("rosterLocked").setBoolean(roulette, true);
        setState("ROUND");
        field("activeRoundId").set(roulette, UUID.randomUUID());
        field("roundOrder").set(roulette, List.of(Game.BRIDGE));
        field("currentRoundIndex").setInt(roulette, 0);
        Set<UUID> roster = (Set<UUID>) field("roster").get(roulette);
        Map<UUID, RouletteTeam> teams = (Map<UUID, RouletteTeam>) field("playerTeams").get(roulette);
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            roster.add(player.getUniqueId());
            teams.put(player.getUniqueId(), RouletteTeam.values()[index / 2]);
        }
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void everyTeamUsesHgsExactDiamondColorAndTabBands() throws Exception {
        int[] hgPaletteIndices = {5, 6, 0, 2};
        for (RouletteTeam team : RouletteTeam.values()) {
            Player first = players.get(team.ordinal() * 2);
            Player second = players.get(team.ordinal() * 2 + 1);
            int hgColor = QueueService.hungerGamesTeamColorRgb(hgPaletteIndices[team.ordinal()]);
            assertEquals(hgColor, roulette.presentationTeamColorRgb(first));
            assertEquals(hgColor, roulette.presentationTeamColorRgb(second));
            for (Player member : List.of(first, second)) {
                Component base = (Component) tabMethod("tabNameComponent", member);
                assertTrue((boolean) tabMethod("showTabHealth", member));
                assertEquals(MatchTeamPresentation.tabName(base, hgColor), tabMethod("teamTabName", member));
                assertEquals(team.ordinal(), roulette.presentationTeamTabIndex(member));
            }
        }
        List<Player> sorted = new ArrayList<>(players.reversed());
        sorted.sort(Comparator.comparingInt(this::order).reversed());
        for (RouletteTeam team : RouletteTeam.values()) {
            assertEquals(Set.of(players.get(team.ordinal() * 2), players.get(team.ordinal() * 2 + 1)),
                Set.copyOf(sorted.subList(team.ordinal() * 2, team.ordinal() * 2 + 2)));
        }
        assertNull(roulette.presentationTeamOf(outsider));
        assertFalse((boolean) tabMethod("showTabHealth", outsider));
        assertEquals(tabMethod("tabNameComponent", outsider), tabMethod("teamTabName", outsider));
        Method visible = HydraPlusService.class.getDeclaredMethod("canViewInTab", Player.class, Player.class);
        visible.setAccessible(true);
        assertTrue((boolean) visible.invoke(tab, players.get(0), players.get(1)));
        assertFalse((boolean) visible.invoke(tab, outsider, players.get(0)));
    }

    @Test
    void tabRoundContextRequiresTournamentMembershipAndCurrentChildAudience() throws Exception {
        Player member = players.get(0);
        assertEquals(new MinigameRouletteService.TabRoundSnapshot(1, 1, Game.BRIDGE),
            roulette.tabRoundSnapshot(member));
        assertNull(roulette.tabRoundSnapshot(outsider));
        member.teleport(outsider.getLocation());
        assertNull(roulette.tabRoundSnapshot(member), "Hub return is not a child-match viewer");
        member.teleport(players.get(1).getLocation());
        field("roundOrder").set(roulette, List.of(Game.SPLEEF));
        assertNull(roulette.tabRoundSnapshot(member), "old Bridge audience must not label Spleef");
        field("roundOrder").set(roulette, List.of(Game.BRIDGE));
        setState("INTERMISSION");
        assertNull(roulette.tabRoundSnapshot(member));
    }

    @Test
    void hgNameplatePrefixSuffixAndColorRemainTheReference() throws Exception {
        Player green = players.get(RouletteTeam.GREEN.ordinal() * 2);
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Method apply = HydraPlusService.class.getDeclaredMethod("applyNameTags",
            Player.class, Scoreboard.class, java.util.Collection.class);
        apply.setAccessible(true);
        apply.invoke(tab, green, board, List.of(green));
        Team team = board.getTeams().stream().filter(entry -> entry.hasEntry(green.getName())).findFirst().orElseThrow();
        assertEquals(ChatColor.WHITE, team.getColor());
        assertEquals(Component.empty(), team.suffix());
        Method prefix = HydraPlusService.class.getDeclaredMethod("nameTagPrefix", Player.class);
        prefix.setAccessible(true);
        assertEquals(prefix.invoke(tab, green), team.prefix());
    }

    @Test
    void existingPrivateTeammateGlowStillUsesTheAssignedTeamAndClears() throws Exception {
        ViewerGlowService glow = mock(ViewerGlowService.class);
        Field glowField = field("glowService");
        glowField.set(roulette, glow);
        Player red = players.get(0);
        Player partner = players.get(1);
        Player opponent = players.get(2);
        Method refresh = MinigameRouletteService.class.getDeclaredMethod("refreshPrivateGlowFor", Player.class);
        refresh.setAccessible(true);
        refresh.invoke(roulette, red);
        verify(glow).setGlowing(red, partner, true);
        verify(glow).setGlowing(red, opponent, false);
        Method clear = MinigameRouletteService.class.getDeclaredMethod("clearPrivateGlow");
        clear.setAccessible(true);
        clear.invoke(roulette);
        verify(glow).setGlowing(red, partner, false);
    }

    @Test
    void stylingClearsOnIntermissionTournamentEndAndWrongRoundAudience() throws Exception {
        Player green = players.get(RouletteTeam.GREEN.ordinal() * 2);
        green.teleport(outsider.getLocation());
        assertNull(roulette.presentationTeamOf(green), "Bridge retains roster membership after a hub return");
        green.teleport(players.get(0).getLocation());
        field("roundOrder").set(roulette, List.of(Game.SPLEEF));
        assertNull(roulette.presentationTeamOf(green), "Bridge audience cannot style a Spleef round");
        field("roundOrder").set(roulette, List.of(Game.BRIDGE));
        assertEquals(RouletteTeam.GREEN, roulette.presentationTeamOf(green));

        setState("INTERMISSION");
        assertNull(roulette.presentationTeamOf(green));
        assertFalse((boolean) tabMethod("showTabHealth", green));
        assertEquals(tabMethod("tabNameComponent", green), tabMethod("teamTabName", green));
        assertEquals(980, order(green));
        setState("FINISHED");
        field("rosterLocked").setBoolean(roulette, false);
        assertNull(roulette.presentationTeamOf(green));
        assertFalse(tabMethod("teamTabName", green).toString().contains("◇"));
        assertTrue(roulette.teamOf(green.getUniqueId()) == RouletteTeam.GREEN,
            "presentation cleanup must not change authoritative assignment");
    }

    private Field field(String name) throws Exception {
        Field field = MinigameRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private void setState(String name) throws Exception {
        Field state = field("state");
        state.set(roulette, Enum.valueOf((Class<Enum>) state.getType(), name));
    }

    private Object tabMethod(String name, Player player) throws Exception {
        Method method = HydraPlusService.class.getDeclaredMethod(name, Player.class);
        method.setAccessible(true);
        return method.invoke(tab, player);
    }

    private int order(Player player) {
        try {
            return (int) tabMethod("tabListOrder", player);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
