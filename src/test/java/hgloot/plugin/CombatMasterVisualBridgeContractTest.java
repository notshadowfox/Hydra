package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatMasterVisualBridgeContractTest {
    @Test
    void onlyOptInAdminTestAttachesRenderingAndVolleyHasOneGuardedRelease() throws Exception {
        String service = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        String rush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        int volley = service.indexOf("private void shootVolley(");
        int next = service.indexOf("private void placeAnchor(", volley);

        assertTrue(service.contains("public UUID spawnVisualTest(Location spawn)"));
        assertTrue(service.contains("if (bridge.attach(active.boss))"));
        assertTrue(service.substring(volley, next).contains("if (animateAttack(a))"));
        assertTrue(service.substring(volley, next).contains("scheduleVolley(a, t, MODEL_VOLLEY_RELEASE_TICKS)"));
        assertTrue(service.substring(volley, next).contains("if (canReleaseVolley(a, t)) fireVolley(a, t);"));
        assertTrue(service.contains("MODEL_VOLLEY_RELEASE_TICKS = 10L"));
        assertTrue(service.contains("a.pendingVolleyTasks.clear();"));
        assertFalse(rush.contains("spawnVisualTest("));
        assertFalse(rush.contains("CombatMasterVisualBridge"));
    }

    @Test
    void deathVisualCannotDelayRewardsOrEncounterCompletion() throws Exception {
        String service = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        int death = service.indexOf("public void onEntityDeath(EntityDeathEvent event)");
        int rewards = service.indexOf("event.getDrops().add(namedItem(Material.HEAVY_CORE", death);
        int visual = service.indexOf("bridge.death(boss, boss.getLocation()", death);
        int cleanup = service.indexOf("cleanup(a, Completion.DEFEATED);", death);
        assertTrue(death >= 0 && rewards < visual && visual < cleanup);
        assertTrue(service.substring(visual, cleanup).contains("event.getDrops().clear();"));
        assertTrue(service.contains("pendingRewards.put(bossId"));
        assertTrue(service.contains("bridge.detach(a.boss.getUniqueId())"));
    }

}
