package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BossRushCombatMasterBindingTest {
    @Test
    void bossRushUsesStandardControllerAndRequiresReadinessBinding() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));

        assertTrue(source.contains("spawnCombatMasterEncounter("));
        assertTrue(source.contains("bindEncounter(bossId, teamPlayers)"));
        assertTrue(source.contains("failed readiness binding for"));
        assertFalse(source.contains("spawnCombatMaster(bossSpawn.add(0.0D, 1.0D, 0.0D), true"));
    }

    @Test
    void encounterApiMatchesWorkingAdminSpawnProfile() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));

        assertTrue(source.contains("return spawnCombatMasterAt(spawn, false, completion == null ? ignored -> {} : completion);"));
        assertTrue(source.contains("No viewers until bindEncounter assigns an authoritative team roster"));
        assertTrue(source.contains("active.task == null || active.task.isCancelled()"));
        assertTrue(source.contains("active.bar.addPlayer(player)"));
    }

    @Test
    void arenaTransfersPlayersAtFadeMidpointBeforeDelayedBossBinding() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));

        int prepare = source.indexOf("preparePlayer(player, spawn);");
        int freeze = source.indexOf("freezePlayerForStartup(player, spawn);");
        int freezeState = source.indexOf("state = State.PLAYER_FREEZE;");
        int delayedStart = source.indexOf("spawnBossesAndReleasePlayers(players), 20L");
        int spawn = source.indexOf("spawnCombatMasterEncounter(arena.bossSpawn()");
        int bind = source.indexOf("bindEncounter(bossId, teamPlayers)");
        int active = source.indexOf("state = State.ACTIVE;", bind);
        int release = source.indexOf("releaseStartupFreeze(players);", active);

        assertTrue(freezeState >= 0 && freezeState < prepare && prepare < freeze);
        assertTrue(source.contains("plugin.transitionTravel(player, () -> {"));
        assertTrue(source.contains("activeWorld != spawn.getWorld()"));
        assertTrue(freeze < delayedStart);
        assertTrue(delayedStart < spawn && spawn < bind);
        assertTrue(bind < active && active < release);
        assertTrue(source.contains("state != State.PLAYER_FREEZE"));
        assertTrue(source.contains("cancelStartupTask();"));
    }

    @Test
    void palmBarrageRevalidatesMeleeRangeForEveryStrike() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));

        assertTrue(source.contains("MELEE_ABILITY_RANGE = 4.5D"));
        assertTrue(source.contains("a.cdB <= 0 && isValidMeleeTarget(a, t)"));
        assertTrue(source.contains("damageMeleeTarget(a, t, 3.0D)"));
        assertTrue(source.contains("damageMeleeTarget(a, t, 4.0D)"));
        assertTrue(source.contains("target.getWorld() == active.boss.getWorld()"));
    }

    @Test
    void bossRushCosmeticHatRetainsDiamondHelmetMaterial() throws Exception {
        String bossRush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));

        assertTrue(bossRush.contains("boolean isInActiveMatch(UUID playerId)"));
        assertTrue(menu.contains("hydra.bossRushService().isInActiveMatch(playerId)"));
        assertTrue(menu.contains("bossRushArmor ? Material.DIAMOND_HELMET : cosmetic.material"));
        assertTrue(menu.contains("createHeadCosmeticItem(cosmetic, helmetMaterial)"));
    }
}
