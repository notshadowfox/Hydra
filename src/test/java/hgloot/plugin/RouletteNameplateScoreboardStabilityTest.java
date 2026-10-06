package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class RouletteNameplateScoreboardStabilityTest {
    private HGLootPlugin plugin;
    private Player viewer;
    private Player teammate;
    private Scoreboard board;
    private Team nameplate;
    private Component prefix;

    @BeforeEach
    void setup() throws Exception {
        var server = MockBukkit.mock();
        var world = server.addSimpleWorld("BossRush-World-NameplateTest");
        viewer = server.addPlayer("Viewer");
        teammate = server.addPlayer("Teammate");
        viewer.teleport(new Location(world, 0, 90, 0));
        teammate.teleport(new Location(world, 1, 90, 0));
        plugin = mock(HGLootPlugin.class);
        when(plugin.namespace()).thenReturn("hydramain");
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        viewer.setScoreboard(board);
        Method applyNameTags = HydraPlusService.class.getDeclaredMethod("applyNameTags",
            Player.class, Scoreboard.class, Collection.class);
        applyNameTags.setAccessible(true);
        applyNameTags.invoke(new HydraPlusService(plugin), viewer, board, List.of(teammate));
        nameplate = board.getEntryTeam(teammate.getName());
        prefix = nameplate.prefix();
        assertTrue(!prefix.equals(Component.empty()));
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void bossRushTimerAndSpectatorRefreshKeepTheViewerNameplateBoard() throws Exception {
        BossRushService bossRush = new BossRushService(plugin, mock(CombatMasterService.class));
        field(bossRush, "activeWorld").set(bossRush, viewer.getWorld());
        setState(bossRush, "ACTIVE");
        ((Set<UUID>) field(bossRush, "participants").get(bossRush)).add(viewer.getUniqueId());
        ((Set<UUID>) field(bossRush, "alivePlayers").get(bossRush)).add(viewer.getUniqueId());
        ((Map<UUID, RouletteTeam>) field(bossRush, "playerTeams").get(bossRush))
            .put(viewer.getUniqueId(), RouletteTeam.RED);

        for (int tick = 0; tick < 20; tick++) {
            bossRush.updateBossRushSidebar(viewer);
            assertNameplateStillPresent();
            assertEquals(DisplaySlot.SIDEBAR, board.getObjective("bossrush").getDisplaySlot());
        }

        ((Set<UUID>) field(bossRush, "alivePlayers").get(bossRush)).remove(viewer.getUniqueId());
        bossRush.updateBossRushSidebar(viewer);
        assertNameplateStillPresent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rouletteQueueAndIntermissionRefreshKeepNameplatesAcrossChildSidebar() throws Exception {
        MinigameRouletteService roulette = new MinigameRouletteService(plugin, mock(PartyService.class));
        ((Set<UUID>) field(roulette, "queuedPlayers").get(roulette)).add(viewer.getUniqueId());
        Method queued = MinigameRouletteService.class.getDeclaredMethod("updateQueuedSidebars");
        queued.setAccessible(true);
        queued.invoke(roulette);
        assertNameplateStillPresent();
        ((Set<UUID>) field(roulette, "queuedPlayers").get(roulette)).add(teammate.getUniqueId());
        queued.invoke(roulette);
        assertNameplateStillPresent();

        ((Set<UUID>) field(roulette, "roster").get(roulette)).add(viewer.getUniqueId());
        ((Map<UUID, RouletteTeam>) field(roulette, "playerTeams").get(roulette))
            .put(viewer.getUniqueId(), RouletteTeam.RED);
        field(roulette, "roundOrder").set(roulette, List.of(ManagedMinigameRound.Game.BOSS_RUSH));
        field(roulette, "currentRoundIndex").setInt(roulette, 0);
        Method standings = MinigameRouletteService.class.getDeclaredMethod("updateStandingsSidebar", Player.class);
        standings.setAccessible(true);
        standings.invoke(roulette, viewer);
        assertNameplateStillPresent();

        board.registerNewObjective("child", org.bukkit.scoreboard.Criteria.DUMMY, "Child")
            .setDisplaySlot(DisplaySlot.SIDEBAR);
        standings.invoke(roulette, viewer);
        assertEquals(DisplaySlot.SIDEBAR, board.getObjective("roulette").getDisplaySlot());
        assertNameplateStillPresent();
    }

    private void assertNameplateStillPresent() {
        assertSame(board, viewer.getScoreboard());
        assertSame(nameplate, board.getEntryTeam(teammate.getName()));
        assertEquals(prefix, nameplate.prefix());
    }

    private static Field field(Object service, String name) throws Exception {
        Field field = service.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static void setState(BossRushService bossRush, String name) throws Exception {
        Field state = field(bossRush, "state");
        state.set(bossRush, Enum.valueOf((Class<Enum>) state.getType(), name));
    }
}
