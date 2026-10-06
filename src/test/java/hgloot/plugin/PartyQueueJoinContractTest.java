package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PartyQueueJoinContractTest {
    @Test
    void hungerGamesUsesAtomicLeaderPartyJoinWhileSoloStaysSolo() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        String join = source.substring(source.indexOf("public void joinQueue"),
            source.indexOf("private void joinDuoQueue"));
        String partyJoin = source.substring(source.indexOf("private void joinPartyQueue"));

        assertTrue(join.contains("joinQueue(player, type, true)"));
        assertTrue(join.contains("type == QueueType.SOLO"));
        assertTrue(join.contains("joinPartyQueue(player, type)"));
        assertTrue(partyJoin.contains("Only the party leader can queue the party."));
        assertTrue(partyJoin.contains("does not have room for your entire party."));
        assertTrue(partyJoin.indexOf("queueSize(type) + additions")
            < partyJoin.indexOf("queues.get(type).add(memberId)"));
    }

    @Test
    void rouletteQueuesPartyTogetherButStaffAndSoloToolsStayIndividual() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        String partyJoin = source.substring(source.indexOf("private void joinPartyQueue"),
            source.indexOf("private String partyLabel"));

        assertTrue(source.contains("joinQueue(player, true)"));
        assertTrue(source.contains("joinQueue(player, false)"));
        assertTrue(partyJoin.contains("Only the party leader can queue the party."));
        assertTrue(partyJoin.contains("does not have room for your entire party."));
        assertTrue(partyJoin.indexOf("queuedPlayers.size() + additions")
            < partyJoin.indexOf("queuedPlayers.addAll(members)"));
        assertFalse(partyJoin.contains("queuedPlayers.add(memberId)"));
    }
}
