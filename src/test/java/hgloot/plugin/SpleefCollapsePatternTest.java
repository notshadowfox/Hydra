package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpleefCollapsePatternTest {
    @Test
    void layersBuildFiveTwoBlockLogicalFloorsAndPreserveTheLastFloor() {
        List<SpleefService.LogicalFloor> floors = SpleefService.buildLogicalFloors(
            2, SpleefCollapsePattern.WAVES, 129, 119, 109, 99, 89);

        assertEquals(5, floors.size());
        assertEquals(List.of(129, 130), floors.getFirst().ys());
        assertEquals(List.of(89, 90), floors.getLast().ys());
        assertEquals(4, floors.subList(0, floors.size() - 1).size());
        assertTrue(floors.getFirst().containsY(129));
        assertTrue(floors.getFirst().containsY(130));
        assertFalse(floors.getFirst().containsY(128));
    }

    @Test
    void everyPatternIsDeterministicAndUsesBothSections() {
        for (SpleefCollapsePattern pattern : SpleefCollapsePattern.values()) {
            boolean foundZero = false;
            boolean foundOne = false;
            for (int x = -32; x <= 32; x++) {
                for (int z = -32; z <= 32; z++) {
                    int first = pattern.section(x, z, 0, 0);
                    assertEquals(first, pattern.section(x, z, 0, 0));
                    foundZero |= first == 0;
                    foundOne |= first == 1;
                }
            }
            assertTrue(foundZero, pattern + " must use section zero");
            assertTrue(foundOne, pattern + " must use section one");
        }
    }

    @Test
    void sharedSelectionIsDeterministicAndNotMapBased() {
        assertEquals(SpleefCollapsePattern.selectFor(123456789L), SpleefCollapsePattern.selectFor(123456789L));
        assertEquals(SpleefCollapsePattern.selectFor(-987654321L), SpleefCollapsePattern.selectFor(-987654321L));
    }
}
