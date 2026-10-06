package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PartyVisibilityTest {
    @Test
    void publicJoinRequiresLeaderToExposePartyAndRespectsFourPlayerCap() {
        PartyService parties = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        UUID fifth = UUID.randomUUID();

        assertEquals(PartyService.CreateResult.CREATED, parties.createParty(leader));
        assertEquals(PartyService.PartyVisibility.PRIVATE, parties.getVisibility(leader));
        assertEquals(PartyService.PublicJoinResult.PRIVATE, parties.joinPublic(second, leader));

        assertEquals(PartyService.VisibilityResult.MADE_PUBLIC, parties.toggleVisibility(leader));
        assertEquals(PartyService.PublicJoinResult.JOINED, parties.joinPublic(second, leader));
        assertEquals(PartyService.PublicJoinResult.JOINED, parties.joinPublic(third, leader));
        assertEquals(PartyService.PublicJoinResult.JOINED, parties.joinPublic(fourth, leader));
        assertEquals(PartyService.PublicJoinResult.PARTY_FULL, parties.joinPublic(fifth, leader));
        assertEquals(4, parties.getPartySize(leader));
    }

    @Test
    void visibilityFollowsPartyAcrossLeadershipTransferButOnlyLeaderCanChangeIt() {
        PartyService parties = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        parties.createParty(leader);
        parties.toggleVisibility(leader);
        parties.invite(leader, member);
        parties.acceptInvite(member, leader);

        assertEquals(PartyService.VisibilityResult.NOT_LEADER, parties.toggleVisibility(member));
        assertEquals(PartyService.PromoteResult.PROMOTED, parties.promote(leader, member));
        assertFalse(parties.isLeader(leader));
        assertTrue(parties.isLeader(member));
        assertEquals(PartyService.PartyVisibility.PUBLIC, parties.snapshot(member).visibility());
        assertEquals(PartyService.VisibilityResult.MADE_PRIVATE, parties.toggleVisibility(member));
        assertEquals(PartyService.PartyVisibility.PRIVATE, parties.getVisibility(leader));
    }

    @Test
    void privatePartyCanInviteWhileMembershipMutationsAreLocked() throws Exception {
        PartyService parties = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        parties.createParty(leader);
        var field = PartyService.class.getDeclaredField("mutationLocked");
        field.setAccessible(true);
        field.set(parties, (Predicate<UUID>) ignored -> true);

        assertEquals(PartyService.PartyVisibility.PRIVATE, parties.getVisibility(leader));
        assertEquals(PartyService.InviteResult.SENT, parties.invite(leader, target));
        assertEquals(PartyService.InviteResponse.LOCKED, parties.acceptInvite(target, leader));
    }
}
