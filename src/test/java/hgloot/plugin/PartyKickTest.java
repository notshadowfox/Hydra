package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyKickTest {
    @Test
    void leaderCanRemoveMemberWhilePartyMutationsAreLocked() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyService parties = new PartyService();
        parties.createParty(leader);
        parties.invite(leader, member);
        parties.acceptInvite(member, leader);
        try {
            var field = PartyService.class.getDeclaredField("mutationLocked");
            field.setAccessible(true);
            field.set(parties, (java.util.function.Predicate<UUID>) ignored -> true);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }

        assertEquals(PartyService.KickResult.KICKED, parties.kick(leader, member));
        assertFalse(parties.isInParty(member));
    }
}
