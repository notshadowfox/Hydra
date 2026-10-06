package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SpinningTopHatTest {
    private PluginMock plugin;
    private MenuService menu;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        menu = new MenuService(plugin, null, null, null);
    }

    @AfterEach
    void tearDown() {
        menu.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void variantIsAdminGrantableButNotAnEconomyReward() {
        UUID playerId = UUID.randomUUID();
        assertTrue(menu.getAllHeadCosmeticIds().contains("spinning_tophat"));
        assertTrue(menu.isManualGrantOnlyHeadCosmetic("spinning_tophat"));
        assertFalse(menu.isAuctionSellableHeadCosmetic("spinning_tophat"));
        assertFalse(menu.getShardCrateRewardCosmeticIds(playerId).contains("spinning_tophat"));
        assertTrue(menu.addHeadCosmeticOwnership(playerId, "spinning_tophat"));
        assertTrue(menu.ownsHeadCosmetic(playerId, "spinning_tophat"));
        assertTrue(menu.isAuctionSellableHeadCosmetic("tophat"));
    }

    @Test
    void frameModelsMoveLeftAndPreserveNormalHatHeadTransform() throws Exception {
        Path assets = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra");
        JsonObject normal = JsonParser.parseString(Files.readString(
            assets.resolve("models/item/cosmetics/tophat.json"))).getAsJsonObject();
        JsonObject normalHead = normal.getAsJsonObject("display").getAsJsonObject("head");

        for (int frame = 0; frame < 16; frame++) {
            String id = "spinning_tophat_%02d".formatted(frame);
            JsonObject item = JsonParser.parseString(Files.readString(assets.resolve("items/" + id + ".json"))).getAsJsonObject();
            JsonObject model = JsonParser.parseString(Files.readString(
                assets.resolve("models/item/cosmetics/" + id + ".json"))).getAsJsonObject();
            JsonObject head = model.getAsJsonObject("display").getAsJsonObject("head");

            assertEquals("hydra:item/cosmetics/" + id, item.getAsJsonObject("model").get("model").getAsString());
            assertEquals("hydra:item/cosmetics/tophat", model.get("parent").getAsString());
            assertEquals(normalHead.get("translation"), head.get("translation"));
            assertEquals(normalHead.get("scale"), head.get("scale"));
            float rotation = head.getAsJsonArray("rotation").get(1).getAsFloat();
            float expected = ((-22.5F * frame + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
            assertEquals(expected, rotation);
            assertEquals(new NamespacedKey("hydra", id), MenuService.spinningTopHatFrameModel(frame));
        }
        assertEquals(MenuService.spinningTopHatFrameModel(0), MenuService.spinningTopHatFrameModel(16));
    }

    @Test
    void selectedVariantAnimatesHelmetSlotWithoutDisplayEntity() throws Exception {
        var player = MockBukkit.getMock().addPlayer();
        assertTrue(menu.addHeadCosmeticOwnership(player.getUniqueId(), "spinning_tophat"));
        Field serviceField = MenuService.class.getDeclaredField("cosmeticService");
        serviceField.setAccessible(true);
        CosmeticService service = (CosmeticService) serviceField.get(menu);
        service.setSelectedHatCosmeticId(player.getUniqueId(), "spinning_tophat");
        assertEquals("spinning_tophat", service.selectedHatCosmeticId(player.getUniqueId()));
        Method refresh = MenuService.class.getDeclaredMethod("refreshHeadCosmetic", org.bukkit.entity.Player.class);
        refresh.setAccessible(true);
        Field tick = MenuService.class.getDeclaredField("spinningTopHatTick");
        tick.setAccessible(true);

        tick.setLong(menu, 0L);
        refresh.invoke(menu, player);
        assertEquals(1010, player.getInventory().getHelmet().getItemMeta().getCustomModelData());
        assertTrue(player.getInventory().getHelmet().getItemMeta().getPersistentDataContainer().has(
            new NamespacedKey(plugin, "spinning_tophat"), org.bukkit.persistence.PersistentDataType.BYTE));
        tick.setLong(menu, 1L);
        refresh.invoke(menu, player);
        assertEquals(1010, player.getInventory().getHelmet().getItemMeta().getCustomModelData());
        assertTrue(player.getWorld().getEntities().stream().noneMatch(entity -> entity instanceof org.bukkit.entity.ItemDisplay));

        service.setSelectedHatCosmeticId(player.getUniqueId(), null);
        refresh.invoke(menu, player);
        assertTrue(player.getInventory().getHelmet() == null);
    }
}
