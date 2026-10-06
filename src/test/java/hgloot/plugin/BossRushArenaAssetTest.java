package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class BossRushArenaAssetTest {
    @Test
    void bundlesEveryIncrementalArenaTile() {
        ClassLoader loader = BossRushArenaAssetTest.class.getClassLoader();
        int count = 0;
        for (int x = 0; x < 33; x++) {
            for (int z = 0; z < 35; z++) {
                String resource = "bossrush/verdant_tiles8/tile_" + x + "_" + z + ".nbt";
                try (InputStream stream = loader.getResourceAsStream(resource)) {
                    assertNotNull(stream, "Missing " + resource);
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
                count++;
            }
        }
        assertEquals(1155, count);
    }

    @Test
    void originalArenaAssetHasExpectedModernNbtEnvelope() throws IOException {
        try (InputStream raw = BossRushArenaAssetTest.class.getClassLoader()
            .getResourceAsStream("bossrush/the_verdant_sanctum.nbt")) {
            assertNotNull(raw);
            try (DataInputStream input = new DataInputStream(new GZIPInputStream(raw))) {
                assertEquals(10, input.readUnsignedByte(), "Structure root must be an NBT compound");
                int rootNameLength = input.readUnsignedShort();
                assertTrue(rootNameLength >= 0);
            }
        }
    }

    @Test
    void runtimeMarkerContractMatchesReplacementSanctuary() throws IOException {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));

        assertTrue(source.contains("{97, 136}"));
        assertTrue(source.contains("{136, 98}"));
        assertTrue(source.contains("{136, 174}"));
        assertTrue(source.contains("{174, 136}"));
        assertTrue(source.contains("arena.playerSpawns().get(teammateIndex % arena.playerSpawns().size())"));
        assertTrue(source.contains("EXPECTED_REFILL_BARRELS = 32"));
        assertTrue(source.contains("arena.barrels().size() != EXPECTED_REFILL_BARRELS"));
        assertTrue(source.contains("failPreparation(\"Arena marker validation failed:"));
    }
}
