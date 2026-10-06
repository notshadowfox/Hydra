package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class KitEditorGuiAssetTest {
    private static final Path GUI = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/textures/font/gui");

    @Test
    void suppliedKitSelectorAndSettingsPagesHaveSixRowGeometry() throws Exception {
        for (String name : new String[] {"kit_editor.png", "settingspage1.png", "settingspage2.png"}) {
            BufferedImage image = ImageIO.read(GUI.resolve(name).toFile());
            assertEquals(176, image.getWidth(), name);
            assertEquals(222, image.getHeight(), name);
        }
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String selector = menu.substring(menu.indexOf("public void openPlayerKitEditorMenu("),
            menu.indexOf("public void openPlayerCosmeticsMenu("));
        assertTrue(selector.contains("inv.setItem(19,")); // first of 19,21,23,25 / 37,39,41,43
        assertFalse(selector.contains("Back"));
        assertTrue(menu.substring(menu.indexOf("public void openPlayerSettingsMenu("),
            menu.indexOf("public void openPlayerKitEditorMenu(")).contains("settings_page_2"));
    }

    @Test
    void kitEditorArtCoversVanillaTopBand() throws Exception {
        BufferedImage image = ImageIO.read(GUI.resolve("kit_editor.png").toFile());
        for (int y = 0; y < 44; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(255, (image.getRGB(x, y) >>> 24) & 0xff,
                    "Transparent pixel exposes vanilla GUI at " + x + "," + y);
            }
        }
    }

    @Test
    void guiGlyphProvidersReferenceOnlyPresentNonCollidingAssets() throws Exception {
        String hydra = Files.readString(Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/font/gui.json"));
        String vanilla = Files.readString(Path.of("rp_model_staging/normalized_rp_assets/assets/minecraft/font/default.json"));
        for (String provider : new String[] {hydra, vanilla}) {
            assertEquals(1, provider.split("kit_editor.png", -1).length - 1);
            assertEquals(1, provider.split("settingspage1.png", -1).length - 1);
            assertEquals(1, provider.split("settingspage2.png", -1).length - 1);
            assertTrue(provider.contains("hydra:font/gui/kit_editor.png"));
            assertTrue(provider.contains("hydra:font/gui/settingspage1.png"));
            assertTrue(provider.contains("hydra:font/gui/settingspage2.png"));
            assertFalse(provider.contains("settings_single.png"));
        }
    }
}
