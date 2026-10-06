package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HungerGamesDuoAssignmentTest {
    @Test
    void preservesPartyPairsAndPairsRemainingSolos() {
        UUID partyLeader = UUID.randomUUID();
        UUID partyMember = UUID.randomUUID();
        UUID soloOne = UUID.randomUUID();
        UUID soloTwo = UUID.randomUUID();

        Map<UUID, UUID> assigned = QueueService.assignHungerGamesDuoTeams(
            List.of(partyLeader, partyMember, soloOne, soloTwo),
            Map.of(partyLeader, partyLeader, partyMember, partyLeader,
                soloOne, soloOne, soloTwo, soloTwo),
            new Random(12345L)
        );

        assertEquals(assigned.get(partyLeader), assigned.get(partyMember));
        assertEquals(assigned.get(soloOne), assigned.get(soloTwo));
        assertNotEquals(assigned.get(partyLeader), assigned.get(soloOne));
    }

    @Test
    void oddSoloRemainsAValidOnePlayerDuoTeam() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID odd = UUID.randomUUID();

        Map<UUID, UUID> assigned = QueueService.assignHungerGamesDuoTeams(
            List.of(first, second, odd), Map.of(), new Random(7L));

        assertEquals(3, assigned.size());
        long distinctTeams = assigned.values().stream().distinct().count();
        assertEquals(2L, distinctTeams);
    }
}
