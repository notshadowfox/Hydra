package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

final class HungerGamesDuoTabOrderTest {
    @RegisterExtension
    static final TestExecutionExceptionHandler unsupportedOperationsFail = (context, failure) -> {
        if (failure instanceof org.mockbukkit.mockbukkit.exception.UnimplementedOperationException) {
            throw new AssertionError("Unsupported fixture operations must not skip Tab tests", failure);
        }
        throw failure;
    };

    private static final String MATCH = "HG-World-1";
    private QueueService queues;
    private HGLootPlugin plugin;
    private HydraPlusService tab;
    @TempDir
    Path worldContainer;

    @BeforeEach
    void setup() {
        MockBukkit.mock(new ServerMock() {
            @Override
            public File getWorldContainer() {
                return worldContainer.toFile();
            }
        });
        try {
            queues = new QueueService(MockBukkit.createMockPlugin(), null);
        } catch (org.mockbukkit.mockbukkit.exception.UnimplementedOperationException unsupported) {
            throw new AssertionError("Queue fixture must not skip Tab tests", unsupported);
        }
        plugin = mock(HGLootPlugin.class);
        when(plugin.queueService()).thenReturn(queues);
        tab = new HydraPlusService(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void teamIsPrimaryAndRankIsSecondaryAcrossAllTwelveTeams() throws Exception {
        List<Player> expected = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Player regular = player("Regular" + index, null);
            Player staff = player("Staff" + index, index % 2 == 0 ? "dev" : "owner");
            team(MATCH, regular, staff);
            expected.add(staff);
            expected.add(regular);
        }
        List<Player> actual = new ArrayList<>(expected.reversed());
        actual.sort(Comparator.comparingInt(this::order).reversed());
        assertEquals(expected, actual, "a later team's owner must not split an earlier team");
    }

    @Test
    void hubAndSoloRetainExactRankOrder() throws Exception {
        Player owner = player("Owner", "owner");
        Player dev = player("Dev", "dev");
        Player plus = player("Plus", null);
        Player regular = player("Regular", null);
        when(plugin.hasHydraPlusRank(plus.getUniqueId())).thenReturn(true);
        List<Player> players = List.of(owner, dev, plus, regular);
        assertEquals(List.of(1000, 999, 990, 980), players.stream().map(this::order).toList());
        for (Player player : players) {
            this.<UUID, String>map("inMatchWorlds").put(player.getUniqueId(), "HG-World-Solo");
            assertNull(queues.hungerGamesDuoTeamTabIndex(player.getUniqueId()));
        }
        assertEquals(List.of(1000, 999, 990, 980), players.stream().map(this::order).toList());
    }

    @Test
    void rosterOrderSurvivesSpectatingReconnectAndOddSoloTeam() throws Exception {
        Player first = player("First", null);
        Player partner = player("Partner", "dev");
        Player odd = player("Odd", "owner");
        team(MATCH, first, partner);
        team(MATCH, odd);
        int previousOrder = order(partner);
        map("inMatchWorlds").remove(partner.getUniqueId());
        map("duoTeamByPlayer").remove(partner.getUniqueId());
        this.<UUID, String>map("spectatingMatchWorlds").put(partner.getUniqueId(), MATCH);
        assertEquals(previousOrder, order(partner));
        assertEquals(0, queues.hungerGamesDuoTeamTabIndex(partner.getUniqueId()));
        assertEquals(1, queues.hungerGamesDuoTeamTabIndex(odd.getUniqueId()));
        map("spectatingMatchWorlds").remove(partner.getUniqueId());
        assertEquals(999, order(partner), "no ownership means the normal rank order");
        this.<UUID, String>map("inMatchWorlds").put(partner.getUniqueId(), MATCH);
        assertEquals(previousOrder, order(partner));
    }

    @Test
    void simultaneousMatchesUseTheirOwnRostersAndCleanupRestoresRankOrder() throws Exception {
        Player first = player("First", null);
        Player second = player("Second", "dev");
        Player elsewhere = player("Elsewhere", "owner");
        team(MATCH, first);
        team(MATCH, second);
        team("HG-World-2", elsewhere);
        assertEquals(1, queues.hungerGamesDuoTeamTabIndex(second.getUniqueId()));
        assertEquals(0, queues.hungerGamesDuoTeamTabIndex(elsewhere.getUniqueId()));
        map("hungerGamesDuoRosters").remove(MATCH);
        set("hungerGamesDuoWorlds").remove(MATCH);
        assertNull(queues.hungerGamesDuoTeamTabIndex(first.getUniqueId()));
        assertEquals(980, order(first));
        assertEquals(999, order(second));
        assertTrue(order(elsewhere) > 1000);
    }

    @Test
    void sortingDoesNotChangeDiamondOrRankNameComponents() throws Exception {
        Player dev = player("Developer", "dev");
        Player teammate = player("Teammate", null);
        team(MATCH, dev, teammate);
        Component base = (Component) invoke("tabNameComponent", dev);
        Component expected = Component.text("\u25c7 ",
            TextColor.color(queues.hungerGamesDuoTeamColorRgb(dev.getUniqueId()))).append(base);
        assertEquals(expected, invoke("hungerGamesDuoTabName", dev));
        order(dev);
        assertEquals(expected, invoke("hungerGamesDuoTabName", dev));
    }

    private Player player(String name, String staffRank) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.getHealth()).thenReturn(40.0);
        when(plugin.hasStaffRank(player.getUniqueId())).thenReturn(staffRank != null);
        if (staffRank != null) when(plugin.staffRankModeFor(player.getUniqueId())).thenReturn(staffRank);
        return player;
    }

    private void team(String world, Player... members) throws Exception {
        set("hungerGamesDuoWorlds").add(world);
        Map<UUID, Set<UUID>> roster = this.<String, Map<UUID, Set<UUID>>>map("hungerGamesDuoRosters")
            .computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        UUID team = members[0].getUniqueId();
        Set<UUID> ids = new LinkedHashSet<>();
        for (Player member : members) {
            UUID id = member.getUniqueId();
            ids.add(id);
            this.<UUID, String>map("inMatchWorlds").put(id, world);
            this.<UUID, UUID>map("duoTeamByPlayer").put(id, team);
        }
        roster.put(team, ids);
        this.<String, Map<UUID, Integer>>map("hungerGamesDuoTeamColors")
            .put(world, QueueService.assignHungerGamesDuoTeamColors(roster.keySet()));
    }

    private int order(Player player) {
        try {
            return (int) invoke("tabListOrder", player);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private Object invoke(String name, Player player) throws Exception {
        Method method = HydraPlusService.class.getDeclaredMethod(name, Player.class);
        method.setAccessible(true);
        return method.invoke(tab, player);
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> map(String name) throws Exception {
        return (Map<K, V>) field(name);
    }

    @SuppressWarnings("unchecked")
    private Set<String> set(String name) throws Exception {
        return (Set<String>) field(name);
    }

    private Object field(String name) throws Exception {
        Field field = QueueService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(queues);
    }
}
