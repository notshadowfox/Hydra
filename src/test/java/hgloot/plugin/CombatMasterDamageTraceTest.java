package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatMasterDamageTraceTest {
    @Test
    void capturesLargeAndLethalHitsWithoutTreatingRoutineDamageAsAnomaly() {
        assertFalse(CombatMasterService.isSuspiciousParticipantHit(6.0D, 20.0D));
        assertTrue(CombatMasterService.isSuspiciousParticipantHit(16.0D, 20.0D));
        assertTrue(CombatMasterService.isSuspiciousParticipantHit(4.0D, 4.0D));
        assertFalse(CombatMasterService.isSuspiciousParticipantHit(0.0D, 20.0D));
    }

    @Test
    void phaseDenialDiagnosticsStayScopedToTheReportedWindow() {
        assertTrue(CombatMasterService.isRelevantPhaseDiagnosis(false, 150.0D));
        assertTrue(CombatMasterService.isRelevantPhaseDiagnosis(true, 90.0D));
        assertFalse(CombatMasterService.isRelevantPhaseDiagnosis(false, 200.0D));
        assertFalse(CombatMasterService.isRelevantPhaseDiagnosis(false, 100.0D));
    }
}
