package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GameHubPlayerCollisionTest {
    @Test
    void nametagTeamsDisableHubCollisionAndRestoreItInMatches() throws Exception {
        var server = MockBukkit.mock();
        try {
            var hub = server.addSimpleWorld("Hydra-GameHub");
            var match = server.addSimpleWorld("Bridge-World-Test");
            Player viewer = server.addPlayer();
            Player hubPlayer = server.addPlayer();
            Player matchPlayer = server.addPlayer();
            viewer.teleport(new Location(hub, 0, 63, 0));
            hubPlayer.teleport(new Location(hub, 1, 63, 0));
            matchPlayer.teleport(new Location(match, 1, 63, 0));

            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.gameHubService()).thenReturn(new GameHubService(plugin));
            when(plugin.staffRankModeFor(any())).thenReturn("none");
            HydraPlusService service = new HydraPlusService(plugin);
            Method apply = HydraPlusService.class.getDeclaredMethod("applyNameTags",
                Player.class, org.bukkit.scoreboard.Scoreboard.class, java.util.Collection.class);
            apply.setAccessible(true);
            apply.invoke(service, viewer, viewer.getScoreboard(), List.of(viewer, hubPlayer, matchPlayer));

            assertEquals(Team.OptionStatus.NEVER, viewer.getScoreboard()
                .getEntryTeam(hubPlayer.getName()).getOption(Team.Option.COLLISION_RULE));
            assertEquals(Team.OptionStatus.ALWAYS, viewer.getScoreboard()
                .getEntryTeam(matchPlayer.getName()).getOption(Team.Option.COLLISION_RULE));

            hubPlayer.teleport(new Location(match, 2, 63, 0));
            apply.invoke(service, viewer, viewer.getScoreboard(), List.of(viewer, hubPlayer, matchPlayer));
            assertEquals(Team.OptionStatus.ALWAYS, viewer.getScoreboard()
                .getEntryTeam(hubPlayer.getName()).getOption(Team.Option.COLLISION_RULE));
        } finally {
            MockBukkit.unmock();
        }
    }
}
