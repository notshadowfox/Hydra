package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.recipe.RecipeRuntimeRegistrar;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

final class HungerGamesFixBoardContractTest {
    private static PluginMock plugin;

    @BeforeAll
    static void setUpBukkit() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterAll
    static void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void runtimeRecipeKeysResolveToAuthoritativeUtilityIds() {
        assertEquals("light_apple", catalogId("light_apple"));
        assertEquals("tracking_compass", catalogId("hg_tracking_compass"));
        assertEquals(null, catalogId("hg_smoke_bomb"));
        assertEquals("gilded_sword", catalogId("gilded_oak"));
    }

    @Test
    void airUseAndSharedShiftCraftPathsRemainEnabled() throws Exception {
        String utilities = read("src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java");
        assertTrue(utilities.contains("@EventHandler(priority = EventPriority.HIGHEST)"));
        assertTrue(utilities.contains("event.getAction() != Action.RIGHT_CLICK_AIR"));
        assertFalse(utilities.contains("onUse(PlayerInteractEvent event) {\n        if (event.isCancelled())"));

        String queue = read("src/main/java/hgloot/plugin/QueueService.java");
        assertTrue(queue.contains("consumeSingleCraft(event.getInventory())"));
        assertTrue(queue.contains("player.getInventory().addItem(result)"));
        assertTrue(queue.contains("recordHungerGamesUtilityCraft(player, utilityId)"));
        assertFalse(queue.contains("Craft capped utilities one at a time"));
    }

    @Test
    void spongeAndRodFixesRemainWiredIntoSharedRuntime() throws Exception {
        String utilities = read("src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java");
        assertTrue(utilities.contains("SPONGE_TRAIL_HOLD_TICKS = 50"));
        assertTrue(utilities.contains("SPONGE_RESTORE_PER_PASS = 16"));
        assertTrue(utilities.contains("Material.SEAGRASS"));
        assertTrue(utilities.contains("instanceof Waterlogged"));
        assertTrue(utilities.contains("event.getHook().remove()"));
        assertTrue(utilities.contains("runTask(plugin, () -> completeScavengerCatch(player, hand))"));
    }

    @Test
    void duoIdentityUsesPrivateNameAndDistanceGlowWithoutGlobalGlowing() throws Exception {
        String queue = read("src/main/java/hgloot/plugin/QueueService.java");
        String hydraPlus = read("src/main/java/hgloot/plugin/HydraPlusService.java");
        String teamPresentation = read("src/main/java/hgloot/plugin/MatchTeamPresentation.java");
        assertTrue(queue.contains("areHungerGamesDuoTeammates"));
        assertTrue(queue.contains("isAliveHungerGamesParticipant(viewerId)"));
        assertTrue(queue.contains("isAliveHungerGamesParticipant(targetId)"));
        assertTrue(queue.contains("hungerGamesDuoGlowService.setGlowing(viewer, target, true)"));
        assertTrue(QueueService.shouldShowHungerGamesDuoGlow(625.0D));
        assertFalse(QueueService.shouldShowHungerGamesDuoGlow(624.999D));
        assertFalse(hydraPlus.contains("TEAMMATE"));
        assertTrue(hydraPlus.contains("MatchTeamPresentation.tabName(baseTabName, teamColorRgb)"));
        assertTrue(teamPresentation.contains("Component.text(\"◇ \""));
        assertFalse(hydraPlus.contains("setGlowing(true)"));
    }

    @Test
    void trackingCompassUsesOnlyItsNamespacedDirectionalModel() throws Exception {
        String utilities = read("src/main/java/hgloot/plugin/hunger/HungerGamesUtilityItems.java");
        assertTrue(utilities.contains("\"hydra:tracking_compass\""));

        Path assets = Path.of("rp_model_staging/normalized_rp_assets/assets");
        String definition = Files.readString(assets.resolve("hydra/items/tracking_compass.json"));
        assertTrue(definition.contains("\"property\": \"minecraft:compass\""));
        assertTrue(definition.contains("\"target\": \"spawn\""));
        assertTrue(definition.contains("hydra:item/tracking_compass_0"));
        assertFalse(Files.exists(assets.resolve("minecraft/items/compass.json")));

        for (int frame = 0; frame <= 8; frame++) {
            Path texture = assets.resolve("hydra/textures/item/tracking_compass/compass_base_" + frame + ".png");
            BufferedImage image = ImageIO.read(texture.toFile());
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            assertTrue(Files.exists(assets.resolve("hydra/models/item/tracking_compass_" + frame + ".json")));
        }
    }

    private static String catalogId(String runtimeId) {
        ShapedRecipe recipe = new ShapedRecipe(
            new NamespacedKey(plugin, runtimeId),
            new ItemStack(Material.STONE)
        );
        recipe.shape("A");
        recipe.setIngredient('A', Material.STONE);
        return RecipeRuntimeRegistrar.catalogIdFor(plugin, recipe);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
