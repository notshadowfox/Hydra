package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class HungerGamesDuoLeaveRoutingTest {
    @Test
    void teamQueueRemovalReturnsEveryOnlineHgDuoMemberToHubBeforeEarlyReturn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        int branchStart = source.indexOf("if (!queuedTeam.isEmpty()) {");
        int branchEnd = source.indexOf("if (inMatchPlayers.contains(playerId))", branchStart);
        String teamLeaveBranch = source.substring(branchStart, branchEnd);

        assertTrue(teamLeaveBranch.contains("removeFromQueue(id, queuedType)"));
        assertTrue(teamLeaveBranch.contains("if (isHungerGames(queuedType))"));
        assertTrue(teamLeaveBranch.contains("returnToHubPresented(member, true)"));
        assertTrue(teamLeaveBranch.indexOf("returnToHubPresented(member, true)") < teamLeaveBranch.indexOf("return;"));
    }
}
