package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts supplement the disposable Paper encounter check. */
class CombatMasterTransitionContractTest {
    private static String service() throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
    }

    @Test
    void thresholdTransitionHasBoundedSingleOwnerLifecycle() throws Exception {
        String source = service();
        assertTrue(source.contains("TRANSITION_TICKS = 240"));
        assertTrue(source.contains("active.transitionTicks >= 0 || wanted.ordinal()"));
        assertTrue(source.contains("if (a.transitionTicks >= 0) { tickTransition(a); return; }"));
        assertTrue(source.contains("a.boss.teleport(a.arenaSpawn)"));
        assertTrue(source.contains("a.boss.setInvulnerable(true)"));
        assertTrue(source.contains("a.boss.setInvulnerable(false)"));
        assertTrue(source.contains("clearCylinder(a);"));
        assertTrue(source.contains("a.pauseTicks = TRANSITION_RECOVERY_TICKS"));
        assertFalse(source.contains("strikeLightning("));
    }

    @Test
    void encounterAudienceAndSupplyAreOwnedByBoundPlayers() throws Exception {
        String source = service();
        assertTrue(source.contains("active.encounterPlayers = new HashSet<>()"));
        assertTrue(source.contains("active.encounterPlayers.add(player.getUniqueId())"));
        assertTrue(source.contains("a.encounterPlayers.contains(player.getUniqueId())"));
        assertTrue(source.contains("if (tick == 80) distributeSupplies(a)"));
        assertTrue(source.contains("player.getInventory().addItem(stack).values()"));
        assertTrue(source.contains("dropItemNaturally(player.getLocation(), leftover)"));
        assertTrue(source.contains("a.bar.removeAll(); a.bar.setVisible(false)"));
    }

    @Test
    void adminPreviewAndLocalVisualToggleUseRealEncounter() throws Exception {
        String rush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        assertTrue(rush.contains("case \"transition\" -> previewTransition(player)"));
        assertTrue(rush.contains("combatMasterService.previewNextPhase(arena.bossId())"));
        assertTrue(rush.contains("combat-master.bossrush-visual-test"));
        assertTrue(rush.contains("combatMasterService.attachEncounterVisual(bossId)"));
        assertFalse(rush.contains("spawnVisualTest("));
    }
}
