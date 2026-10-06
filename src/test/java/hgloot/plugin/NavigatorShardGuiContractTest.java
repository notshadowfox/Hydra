package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class NavigatorShardGuiContractTest {
    private static final Path GUI_TEXTURES = Path.of(
        "rp_model_staging/normalized_rp_assets/assets/hydra/textures/font/gui");

    @Test
    void navigatorUsesRequestedVisualSlotRegionsAndAuthoritativeHgEntryPoints() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));

        assertTrue(menu.contains("new int[] {9, 10, 11, 12, 18, 19, 20, 21}"));
        assertTrue(menu.contains("new int[] {14, 15, 16, 17, 23, 24, 25, 26}"));
        assertTrue(menu.contains("new int[] {38, 39, 40, 41, 42, 47, 48, 49, 50, 51}"));
        assertTrue(menu.contains("new int[] {18, 19, 20, 21, 27, 28, 29, 30}"));
        assertTrue(menu.contains("new int[] {23, 24, 25, 26, 32, 33, 34, 35}"));
        assertTrue(menu.contains("queueService.joinHungerGamesSolo(player)"));
        assertTrue(menu.contains("queueService.joinHungerGamesDuos(player)"));
        assertTrue(queue.contains("joinQueue(player, QueueType.HUNGERGAMES, false)"));
        assertTrue(queue.contains("type == QueueType.HUNGERGAMES_DUOS && members.size() > 2"));
        assertTrue(queue.contains("joinQueue(player, QueueType.HUNGERGAMES_DUOS, true)"));
    }

    @Test
    void shardConsumptionOccursOnlyAfterOpenAndUsesExistingRewardPool() throws Exception {
        String progression = Files.readString(Path.of("src/main/java/hgloot/plugin/ProgressionService.java"));
        int menuStart = progression.indexOf("private void openShardMenu");
        int openingStart = progression.indexOf("private void beginShardOpening");
        String menu = progression.substring(menuStart, openingStart);
        String opening = progression.substring(openingStart, progression.indexOf("private Inventory shardInventory"));

        assertTrue(menu.contains("getShardCrateRewardCosmeticIds"));
        assertTrue(!menu.contains("state.shards--"));
        assertTrue(opening.contains("activeShardCrates.containsKey"));
        assertTrue(opening.contains("getShardCrateRewardCosmeticIds"));
        assertTrue(opening.contains("state.shards--"));
        assertTrue(progression.contains("menuService.getCosmeticRarity(rewardId)"));
        assertTrue(progression.contains("SHARD_OPENING_VIEW"));
    }

    @Test
    void suppliedGuiTexturesUseTheExpectedCanvas() throws Exception {
        for (String file : new String[] {
            "navigator.png", "navigator_hunger_games.png", "shard_open.png", "shard_opening.png"
        }) {
            BufferedImage image = ImageIO.read(GUI_TEXTURES.resolve(file).toFile());
            assertEquals(176, image.getWidth(), file);
            assertEquals(222, image.getHeight(), file);
        }
    }

    @Test
    void sidebarUsesTheExistingShardArtworkAsAFontGlyph() throws Exception {
        String icons = Files.readString(Path.of("src/main/java/hgloot/plugin/util/HydraIcons.java"));
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        String progression = Files.readString(Path.of("src/main/java/hgloot/plugin/ProgressionService.java"));
        String font = Files.readString(Path.of(
            "rp_model_staging/normalized_rp_assets/assets/minecraft/font/default.json"));
        assertTrue(icons.contains("SHARD_CHAR"));
        assertTrue(queue.contains("SIDEBAR_INFO_COLOR + \"Shards \" + HydraIcons.SHARD + \" \""));
        assertEquals(25, ProgressionService.FRAGMENTS_PER_SHARD);
        assertTrue(progression.contains("state.shardFragments / FRAGMENTS_PER_SHARD"));
        assertTrue(progression.contains("state.shardFragments %= FRAGMENTS_PER_SHARD"));
        assertTrue(queue.contains("sidebarSystem(\"Tier\""));
        assertTrue(font.contains("hydra:item/shard.png"));
        assertTrue(font.contains("\\ue206"));
    }
}
