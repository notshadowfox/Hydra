package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class BossRushTournamentLeaveTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void leavingTheTournamentDropsBossRushParticipantOwnership() throws Exception {
        MockBukkit.mock();
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        when(plugin.namespace()).thenReturn("hydramain");
        BossRushService bossRush = new BossRushService(plugin, mock(CombatMasterService.class));
        UUID playerId = UUID.randomUUID();
        ((Set<UUID>) field("participants").get(bossRush)).add(playerId);
        ((Set<UUID>) field("alivePlayers").get(bossRush)).add(playerId);
        ((Map<UUID, RouletteTeam>) field("playerTeams").get(bossRush)).put(playerId, RouletteTeam.RED);

        bossRush.forgetTournamentLeaver(playerId);

        assertFalse(((Set<UUID>) field("participants").get(bossRush)).contains(playerId));
        assertFalse(((Set<UUID>) field("alivePlayers").get(bossRush)).contains(playerId));
        assertFalse(((Map<UUID, RouletteTeam>) field("playerTeams").get(bossRush)).containsKey(playerId));
    }

    private static Field field(String name) throws Exception {
        Field field = BossRushService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
