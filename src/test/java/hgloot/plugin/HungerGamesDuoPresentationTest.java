package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HungerGamesDuoPresentationTest {
    @Test
    void authoritativeTeamsReceiveStableDistinctColorsIncludingOddSoloTeam() {
        UUID partyLeader = UUID.randomUUID();
        UUID partyMember = UUID.randomUUID();
        UUID oddSolo = UUID.randomUUID();
        Map<UUID, UUID> teams = QueueService.assignHungerGamesDuoTeams(
            List.of(partyLeader, partyMember, oddSolo),
            Map.of(partyLeader, partyLeader, partyMember, partyLeader, oddSolo, oddSolo),
            new Random(42L)
        );
        List<UUID> teamIds = teams.values().stream().distinct().toList();
        Map<UUID, Integer> first = QueueService.assignHungerGamesDuoTeamColors(teamIds);
        Map<UUID, Integer> second = QueueService.assignHungerGamesDuoTeamColors(teamIds);

        assertEquals(first, second);
        assertEquals(first.get(teams.get(partyLeader)), first.get(teams.get(partyMember)));
        assertNotEquals(first.get(teams.get(partyLeader)), first.get(teams.get(oddSolo)));
        assertTrue(first.containsKey(teams.get(oddSolo)));
    }

    @Test
    void paletteDistinguishesAllTwelveSupportedDuoTeams() {
        List<UUID> teams = new ArrayList<>();
        for (int index = 0; index < 12; index++) teams.add(UUID.randomUUID());
        Map<UUID, Integer> colors = QueueService.assignHungerGamesDuoTeamColors(teams);

        assertEquals(12, colors.size());
        assertEquals(12L, colors.values().stream().distinct().count());
    }

    @Test
    void tabDiamondWrapsExistingFormattingAndNeverUsesTeammateText() throws Exception {
        String tab = Files.readString(Path.of("src/main/java/hgloot/plugin/HydraPlusService.java"));
        String shared = Files.readString(Path.of("src/main/java/hgloot/plugin/MatchTeamPresentation.java"));
        assertTrue(tab.contains("Component baseTabName = tabNameComponent(player)"));
        assertTrue(tab.contains("MatchTeamPresentation.tabName(baseTabName, teamColorRgb)"));
        assertTrue(shared.contains("Component.text(\"◇ \", TextColor.color(colorRgb)).append(base)"));
        assertTrue(tab.contains("if (teamColorRgb == null) return baseTabName"));
        assertFalse(tab.contains("TEAMMATE"));
    }

    @Test
    void glowBoundaryAndLifecycleRemainLivingPlayerOnly() throws Exception {
        assertFalse(QueueService.shouldShowHungerGamesDuoGlow(24.0D * 24.0D));
        assertTrue(QueueService.shouldShowHungerGamesDuoGlow(25.0D * 25.0D));
        assertTrue(QueueService.shouldShowHungerGamesDuoGlow(26.0D * 26.0D));

        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        assertTrue(queue.contains("isAliveHungerGamesParticipant(viewerId)"));
        assertTrue(queue.contains("isAliveHungerGamesParticipant(targetId)"));
        assertTrue(queue.contains("clearHungerGamesDuoGlows()"));
        assertTrue(queue.contains("hungerGamesDuoTeamColors.remove(rootWorldName)"));
        assertTrue(queue.contains("if (snapshot.duoTeamId != null)"));
    }
}
