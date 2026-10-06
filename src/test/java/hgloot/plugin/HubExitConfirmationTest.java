package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HubExitConfirmationTest {
    @Test
    void rouletteConfirmationIsPlayerAndRoundScoped() {
        HubExitConfirmation confirmations = new HubExitConfirmation();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID round = UUID.randomUUID();
        assertFalse(confirmations.confirm(first, round, true, 1_000L));
        assertFalse(confirmations.confirm(second, round, true, 1_001L));
        assertTrue(confirmations.confirm(first, round, true, 1_002L));
        assertFalse(confirmations.confirm(first, round, true, 1_003L), "confirmation is consumed once");
        assertTrue(confirmations.confirm(second, round, true, 1_004L));
    }

    @Test
    void expiredChangedRoundAndStandaloneConfirmationsCannotCarryOver() {
        HubExitConfirmation confirmations = new HubExitConfirmation();
        UUID player = UUID.randomUUID();
        UUID round = UUID.randomUUID();
        UUID nextRound = UUID.randomUUID();
        assertFalse(confirmations.confirm(player, round, false, 1_000L));
        assertFalse(confirmations.confirm(player, round, false, 11_001L));
        assertFalse(confirmations.confirm(player, nextRound, false, 11_002L));
        assertFalse(confirmations.confirm(player, null, true, 11_003L));
        assertTrue(confirmations.confirm(player, null, true, 11_004L));
        assertFalse(confirmations.confirm(player, round, true, 11_005L));
        confirmations.clear(player);
        assertFalse(confirmations.confirm(player, round, true, 11_006L));
        assertTrue(confirmations.confirm(player, null, false, 11_007L));
    }
}
