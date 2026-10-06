package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

final class HubButtonAndIconTest {
    @Test
    void onlyRightClickedVanillaButtonsReceiveHubException() {
        for (Material material : Material.values()) {
            assertTrue(HubProtectionService.isVanillaButtonUse(Action.RIGHT_CLICK_BLOCK, material)
                == material.name().endsWith("_BUTTON"), material.name());
        }
        assertFalse(HubProtectionService.isVanillaButtonUse(Action.LEFT_CLICK_BLOCK, Material.OAK_BUTTON));
        assertFalse(HubProtectionService.isVanillaButtonUse(Action.RIGHT_CLICK_AIR, Material.OAK_BUTTON));
        assertFalse(HubProtectionService.isVanillaButtonUse(Action.RIGHT_CLICK_BLOCK, Material.LEVER));
        assertFalse(HubProtectionService.isVanillaButtonUse(Action.RIGHT_CLICK_BLOCK, null));
    }

    @Test
    void buttonExceptionAllowsBlockButDeniesHeldItemUse() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/HubProtectionService.java"));
        String handler = source.substring(source.indexOf("public void onPlayerInteract(PlayerInteractEvent event)"),
            source.indexOf("public void onPlayerInteractEntity("));
        assertTrue(handler.contains("event.setUseInteractedBlock(Event.Result.ALLOW)"));
        assertTrue(handler.contains("event.setUseItemInHand(Event.Result.DENY)"));
        assertTrue(handler.contains("event.setCancelled(true)"));
    }

    @Test
    void hubItemsUseSuppliedNamespacedModelsWithoutChangingTheirActions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String items = source.substring(source.indexOf("private ItemStack createSettingsHubItem()"),
            source.indexOf("private ItemStack createUtilityBookHubItem()"));
        assertTrue(items.contains("HUB_ITEM_SETTINGS"));
        assertTrue(items.contains("hydra:settings_hub_icon"));
        assertTrue(items.contains("HUB_ITEM_BATTLE_PASS"));
        assertTrue(items.contains("hydra:battle_pass_hub_icon"));
        for (String icon : new String[] {"settings_hub_icon", "battle_pass_hub_icon"}) {
            Path root = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra");
            assertTrue(Files.isRegularFile(root.resolve("items/" + icon + ".json")));
            assertTrue(Files.isRegularFile(root.resolve("models/item/gui/" + icon + ".json")));
            assertTrue(Files.isRegularFile(root.resolve("textures/item/gui/" + icon + ".png")));
        }
    }
}
