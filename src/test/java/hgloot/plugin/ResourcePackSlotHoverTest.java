package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ResourcePackSlotHoverTest {
    private static final Path CONTAINER_SPRITES = Path.of(
            "rp_model_staging",
            "normalized_rp_assets",
            "assets",
            "minecraft",
            "textures",
            "gui",
            "sprites",
            "container");

    @Test
    void overridesOnlyBothTransparentSlotHighlightLayers() throws IOException {
        assertTransparentHighlight("slot_highlight_back.png");
        assertTransparentHighlight("slot_highlight_front.png");
        assertFalse(Files.exists(CONTAINER_SPRITES.resolve("slot.png")),
                "The visible vanilla slot background must not be overridden");

        try (Stream<Path> files = Files.list(CONTAINER_SPRITES)) {
            Set<String> names = files
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "slot_highlight_back.png",
                    "slot_highlight_back.png.mcmeta",
                    "slot_highlight_front.png",
                    "slot_highlight_front.png.mcmeta"), names);
        }
    }

    private static void assertTransparentHighlight(String fileName) throws IOException {
        Path texture = CONTAINER_SPRITES.resolve(fileName);
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "Texture must be a readable PNG: " + fileName);
        assertEquals(24, image.getWidth(), fileName + " width");
        assertEquals(24, image.getHeight(), fileName + " height");

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(0, (image.getRGB(x, y) >>> 24) & 0xff,
                        fileName + " must be fully transparent at " + x + "," + y);
            }
        }

        String metadata = Files.readString(texture.resolveSibling(fileName + ".mcmeta"));
        assertTrue(metadata.contains("\"type\": \"nine_slice\""), fileName + " scaling type");
        assertTrue(metadata.contains("\"width\": 24"), fileName + " metadata width");
        assertTrue(metadata.contains("\"height\": 24"), fileName + " metadata height");
        assertTrue(metadata.contains("\"border\": 4"), fileName + " metadata border");
    }
}
