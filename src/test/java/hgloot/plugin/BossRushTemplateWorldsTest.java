package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class BossRushTemplateWorldsTest {
    private static final String[] REGIONS = {"r.-1.-1.mca", "r.0.-1.mca", "r.-1.0.mca", "r.0.0.mca"};

    @Test
    void eachImmutableTemplateContainsOnlyItsOccupiedPrefix() throws Exception {
        for (int teams = 1; teams <= 4; teams++) {
            Set<String> entries = new HashSet<>();
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                    "bossrush/templates/teams-" + teams + ".zip")) {
                assertTrue(input != null, "Missing variant " + teams);
                try (ZipInputStream zip = new ZipInputStream(input)) {
                    for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                        assertTrue(entries.add(entry.getName()), "Duplicate entry");
                        assertFalse(entry.getName().startsWith("entities/"));
                        assertFalse(entry.getName().contains("playerdata"));
                        assertFalse(entry.getName().contains("uid.dat"));
                        assertFalse(entry.getName().contains("session.lock"));
                    }
                }
            }
            assertTrue(entries.contains("level.dat"));
            assertEquals(2 + teams * 2, entries.size());
            for (int i = 0; i < REGIONS.length; i++) {
                assertEquals(i < teams, entries.contains("region/" + REGIONS[i]));
                assertEquals(i < teams, entries.contains("poi/" + REGIONS[i]));
            }
        }
    }

    @Test
    void requiredChunkSetGrowsOnlyWithOccupiedTeamPrefix() {
        int previous = 0;
        for (int teams = 1; teams <= 4; teams++) {
            var chunks = BossRushTemplateWorlds.requiredChunks(teams);
            Set<String> unique = new HashSet<>();
            for (int[] chunk : chunks) unique.add(chunk[0] + "," + chunk[1]);
            assertEquals(chunks.size(), unique.size());
            assertTrue(chunks.size() > previous);
            assertTrue(chunks.size() <= 20 * teams, "Must not warm the full saved world");
            previous = chunks.size();
        }
    }
}
