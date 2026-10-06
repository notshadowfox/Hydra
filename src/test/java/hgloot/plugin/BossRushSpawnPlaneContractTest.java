package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BossRushSpawnPlaneContractTest {
    @Test
    void bossRushUsesTheArenaCombatPlaneForMobsAndPedestals() throws Exception {
        String bossRush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));

        assertTrue(bossRush.contains("private static final int MOB_SPAWN_Y = 87;"));
        assertTrue(bossRush.contains("private static final int PEDESTAL_SPAWN_Y = MOB_SPAWN_Y;"));
        assertTrue(bossRush.contains("arena.bossSpawn(arenaMobSpawn(arena, 4, 0))"));
        assertTrue(bossRush.contains("spawnBossRushWave(arenaMobSpawn(arena, 0, 0)"));
        assertTrue(bossRush.contains("Location healerAt = arenaMobSpawn(arena, 11, 11)"));
        assertTrue(bossRush.contains("PEDESTAL_SPAWN_Y"));
    }

    @Test
    void combatWaveDoesNotClimbToBarrierTops() throws Exception {
        String chamber = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatChamberService.java"));
        String method = chamber.substring(chamber.indexOf("private Location bossRushSafeSpawn"),
            chamber.indexOf("private Structure loadVenomRing"));

        assertFalse(method.contains("desired.getBlockY() + 20"));
        assertFalse(method.contains("for (int y"));
        assertTrue(method.contains("int y = desired.getBlockY()"));
    }

    @Test
    void masteryPoisonParticleSuppliesItsRequiredColorPayload() throws Exception {
        String combatMaster = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        String poisonCloud = combatMaster.substring(combatMaster.indexOf("private void poisonCloud"),
            combatMaster.indexOf("private void igniteFloor"));

        assertTrue(poisonCloud.contains("Particle.ENTITY_EFFECT"));
        assertTrue(poisonCloud.contains("org.bukkit.Color.fromRGB(0, 199, 179)"));
    }
}
