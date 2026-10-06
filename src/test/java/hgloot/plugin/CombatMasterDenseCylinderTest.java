package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class CombatMasterDenseCylinderTest {
    @Test
    void normalSkullsFormStaggeredOutwardFacingFullHeightRings() {
        Location center = new Location(null, 100, 64, 200);
        Location first = CombatMasterService.denseSkullLocation(center, 0, 0);
        Location neighbor = CombatMasterService.denseSkullLocation(center, 0, 1);
        Location staggered = CombatMasterService.denseSkullLocation(center, 1, 0);
        Location top = CombatMasterService.denseSkullLocation(center, 31, 0);

        assertEquals(106.0, first.getX(), 0.001);
        assertEquals(64.0, first.getY(), 0.001);
        assertEquals(200.0, first.getZ(), 0.001);
        assertEquals(-90.0, first.getYaw(), 0.001);
        assertEquals(200.588, neighbor.getZ(), 0.01);
        assertEquals(64.75, staggered.getY(), 0.001);
        assertTrue(staggered.getZ() > first.getZ() && staggered.getZ() < neighbor.getZ());
        assertEquals(87.25, top.getY(), 0.001);
    }

    @Test
    void denseVariantIsOptInAndCleansUpEntitiesAndDarkness() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(config.contains("dense-skull-test: false"));
        assertTrue(source.contains("DENSE_COLUMNS = 64"));
        assertTrue(source.contains("DENSE_ROWS = 32"));
        assertTrue(source.contains("new Vector3f(1, 1, 1)"));
        assertTrue(source.contains("entity.setVisibleByDefault(false)"));
        assertTrue(source.contains("entity.setDisplayHeight(50.0f)"));
        assertFalse(source.contains("sendHurtAnimation("));
        assertTrue(source.contains("PotionEffectType.DARKNESS"));
        assertTrue(source.contains("clearEmergenceEffects(a)"));
        assertTrue(source.contains("clearCylinder(a)"));
        assertTrue(source.contains("sphericalPulse(a, tick)"));
        assertTrue(source.contains("if (tick == 145) continueDenseCylinderRise(a)"));
        assertTrue(source.contains("dissipateDenseCylinder(a, tick)"));
        assertTrue(source.contains("DENSE_EXTRA_RISE = 24.0f"));
        assertTrue(source.contains("player.getLocation().distanceSquared(a.arenaSpawn) <= 64.0 * 64.0"));
    }

    @Test
    void blueSkullsAreStableAndDistributedAcrossEveryRing() throws Exception {
        int blueCount = 0;
        for (int row = 0; row < 32; row++) {
            int inRing = 0;
            for (int column = 0; column < 64; column++) {
                if (CombatMasterService.isBlueSkull(row, column)) {
                    inRing++;
                    blueCount++;
                }
            }
            assertTrue(inRing >= 7 && inRing <= 8);
        }
        assertTrue(blueCount >= 224 && blueCount <= 256);
        Path root = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra");
        String item = Files.readString(root.resolve("items/combat_master/blue_wither_skull.json"));
        String model = Files.readString(root.resolve("models/item/combat_master/blue_wither_skull.json"));
        assertTrue(item.contains("hydra:item/combat_master/blue_wither_skull"));
        assertFalse(item.contains("minecraft:constant"));
        assertTrue(model.contains("hydra:item/combat_master/blue_wither_projectile"));
        assertFalse(model.contains("minecraft:item/template_skull"));
        assertTrue(model.contains("\"fixed\""));
        assertTrue(Files.size(root.resolve("textures/item/combat_master/blue_wither_projectile.png")) > 0);
        String packMetadata = Files.readString(Path.of("rp_model_staging/normalized_rp_assets/pack.mcmeta"));
        assertTrue(packMetadata.contains("\"min_format\":75"));
        assertTrue(packMetadata.contains("\"max_format\":75"));
    }

    @Test
    void blueCubeFacesOppositeItsInherentFrontAtCardinalRingPositionsWithoutGlow() throws Exception {
        Location center = new Location(null, 100, 64, 200);
        for (int column : new int[] {0, 16, 32, 48}) {
            Location normal = CombatMasterService.denseSkullLocation(center, 0, column, false);
            Location blue = CombatMasterService.denseSkullLocation(center, 0, column, true);
            assertEquals(normal.getX(), blue.getX(), 0.001);
            assertEquals(normal.getY(), blue.getY(), 0.001);
            assertEquals(normal.getZ(), blue.getZ(), 0.001);
            assertEquals(180.0f, blue.getYaw() - normal.getYaw(), 0.001);
        }
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        assertFalse(source.contains("entity.setGlowing(true)"));
        assertFalse(source.contains("entity.setGlowColorOverride("));
        assertFalse(source.contains("entity.setBrightness(new Display.Brightness(15, 15))"));
    }
}
