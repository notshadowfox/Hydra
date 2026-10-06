package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.gui.GuiTitleFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

final class AuctionPaginationAndOuterEndBridgeTest {
    @Test
    void auctionTitleUsesTheCorrectSuppliedArtworkForEveryPageState() {
        assertEquals("\uE138", backgroundGlyph(GuiTitleFactory.auctionHouseTitle(false, false)));
        assertEquals("\uE135", backgroundGlyph(GuiTitleFactory.auctionHouseTitle(false, true)));
        assertEquals("\uE136", backgroundGlyph(GuiTitleFactory.auctionHouseTitle(true, true)));
        assertEquals("\uE137", backgroundGlyph(GuiTitleFactory.auctionHouseTitle(true, false)));
    }

    @Test
    void suppliedAuctionArtworkIsBundledAtExactGuiDimensions() throws Exception {
        for (String name : List.of(
            "auction_house_first_page.png",
            "auction_house_middle_page.png",
            "auction_house_last_page.png",
            "auction_house_single_page.png")) {
            Path path = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/textures/font/gui", name);
            BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, name);
            assertEquals(176, image.getWidth(), name);
            assertEquals(222, image.getHeight(), name);
        }
    }

    @Test
    void auctionUsesBakedArrowsWithInvisibleClickTargetsInExistingSlots() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/AuctionHouseService.java"));
        assertTrue(source.contains("inv.setItem(45, invisiblePageButton"));
        assertTrue(source.contains("inv.setItem(53, invisiblePageButton"));
        assertTrue(source.contains("new NamespacedKey(\"hydra\", \"gui/negative_space\")"));
        assertTrue(source.contains("GuiTitleFactory.auctionHouseTitle(hasPreviousPage, hasNextPage)"));
    }

    @Test
    void outerEndBridgeIsBundledAndRegisteredWithRequiredMarkerMaterials() throws Exception {
        Path map = Path.of("src/main/resources/Structure nbt/minigames/bridge/outerendbridge.nbt");
        assertTrue(Files.isRegularFile(map));
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BridgeService.java"));
        assertTrue(source.contains("new BridgeMap(\"outer_end\", \"Outer End\", "
            + "\"Structure nbt/minigames/bridge/outerendbridge.nbt\")"));

        byte[] decompressed;
        try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(map));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            decompressed = output.toByteArray();
        }
        String nbtStrings = new String(decompressed, StandardCharsets.ISO_8859_1);
        assertTrue(nbtStrings.contains("minecraft:creaking_heart"));
        assertTrue(nbtStrings.contains("minecraft:end_portal"));
    }

    @Test
    void outerEndBridgeAppearsInTheBridgeAdminGuiAndDispatchesSelection() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        assertTrue(menu.contains("bridgeService.isMapSelected(\"outer_end\")"));
        assertTrue(menu.contains("\"Outer End\", \"admin_bridge_map_outer_end\""));
        assertTrue(menu.contains("Bukkit.dispatchCommand(player, \"admin bridge map outer_end\")"));
    }

    private String backgroundGlyph(Component title) {
        assertEquals(1, title.children().size());
        return ((TextComponent) title.children().getFirst()).content();
    }
}
