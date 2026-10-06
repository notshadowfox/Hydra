package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BossRushTeamArenaIsolationTest {
    @Test
    void occupiedTeamsReceiveIndependentArenaCopiesAndBosses() throws Exception {
        String source = source();

        assertTrue(source.contains("EnumMap<RouletteTeam, ArenaInstance> arenas"));
        assertTrue(source.contains("RouletteTeam[] order = {RouletteTeam.RED, RouletteTeam.BLUE, RouletteTeam.GREEN, RouletteTeam.YELLOW}"));
        assertTrue(source.contains("templateWorlds.claim(count"));
        assertFalse(source.contains("placeArenaIncrementally("));
        assertTrue(source.contains("arenas.put(team, new ArenaInstance"));
        assertTrue(source.contains("for (ArenaInstance arena : arenas.values())"));
        assertTrue(source.contains("bossTeams.put(bossId, arena.team())"));
        assertTrue(source.contains("bindEncounter(bossId, teamPlayers)"));
        assertFalse(source.contains("UUID activeBossId"));
    }

    @Test
    void teammatesShareOneArenaAndOpposingTeamsCannotCrossBounds() throws Exception {
        String source = source();

        assertTrue(source.contains("ArenaInstance arena = arenas.get(team)"));
        assertTrue(source.contains("faceBoss(arena.playerSpawns().get(teammateIndex % arena.playerSpawns().size()), arena.bossSpawn())"));
        assertTrue(source.contains("!arena.bounds().containsXZ"));
        assertTrue(source.contains("Your team must remain inside its Boss Rush arena."));
        assertTrue(source.contains("if (team != bossTeam)"));
    }

    @Test
    void arenaOwnedSystemsDoNotUseSharedBossOrBarrels() throws Exception {
        String source = source();

        assertTrue(source.contains("arena.barrels().stream()"));
        assertTrue(source.contains("spawnRandomCombatWave(ArenaInstance arena)"));
        assertTrue(source.contains("spawnLegendaryPedestal(ArenaInstance arena)"));
        assertTrue(source.contains("healerTeams.put(healer.getUniqueId(), arena.team())"));
        assertTrue(source.contains("onBossComplete(RouletteTeam team"));
    }

    @Test
    void deathsPedestalClaimsAndChatUseTeamAudience() throws Exception {
        String source = source();
        String pedestal = Files.readString(Path.of("src/main/java/dev/zxdzero/ZxdzeroEvents/PedestalManager.java"));
        String chat = Files.readString(Path.of("src/main/java/hgloot/plugin/HydraPlusService.java"));

        assertTrue(source.contains("event.deathMessage(null)"));
        assertTrue(source.contains("sendTeamMessage(playerTeams.get(player.getUniqueId()), deathMessage)"));
        assertTrue(source.contains("broadcastTeam(team, ChatColor.RED + player.getName()"));
        assertTrue(source.contains("broadcastEncounterMessage(Player claimant, Component message)"));
        assertTrue(pedestal.contains("hydra.bossRushService().broadcastEncounterMessage(player, claimMessage)"));
        assertTrue(chat.contains("hydra.bossRushService().playerAudienceKey(player)"));
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
    }
}
