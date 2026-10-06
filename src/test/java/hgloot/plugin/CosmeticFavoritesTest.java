package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class CosmeticFavoritesTest {
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void favoritesPersistAcrossCosmeticCategoriesAndAreRemovedWithOwnership() {
        UUID playerId = UUID.randomUUID();
        CosmeticStateStore stateStore = createStore();
        stateStore.load(List.of("halo", "trail_blackstatic"), value -> value, (key, value) -> false, value -> false);
        stateStore.grantOwnership(playerId, "halo", List.of(), id -> true);
        stateStore.grantOwnership(playerId, "trail_blackstatic", List.of(), id -> true);

        assertTrue(stateStore.setFavoriteCosmetic(playerId, "halo", true));
        assertTrue(stateStore.setFavoriteCosmetic(playerId, "trail_blackstatic", true));
        assertTrue(stateStore.favoriteCosmeticIds(playerId).containsAll(List.of("halo", "trail_blackstatic")));

        CosmeticStateStore reloaded = createStore();
        reloaded.load(List.of("halo", "trail_blackstatic"), value -> value, (key, value) -> false, value -> false);
        assertTrue(reloaded.favoriteCosmeticIds(playerId).containsAll(List.of("halo", "trail_blackstatic")));

        assertTrue(reloaded.revokeOwnership(playerId, "trail_blackstatic", List.of(), id -> true));
        assertFalse(reloaded.favoriteCosmeticIds(playerId).contains("trail_blackstatic"));
        assertTrue(reloaded.favoriteCosmeticIds(playerId).contains("halo"));
    }

    @Test
    void cosmeticMenuUsesLeftClickToEquipAndRightClickToFavorite() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));

        assertTrue(source.contains("event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT"));
        assertTrue(source.contains("Left-click to equip."));
        assertTrue(source.contains("Right-click to "));
        assertTrue(source.contains("for (CosmeticCategory category : displayedCosmeticCategories())"));
    }

    private CosmeticStateStore createStore() {
        return new CosmeticStateStore(
            plugin,
            "test.cosmetics.owned",
            "test.cosmetics.selected_hat",
            "test.cosmetics.selected_kill_message",
            "test.cosmetics.selected_legendary",
            "test.cosmetics.selected_rank_style",
            "test.cosmetics.selected_system",
            "test.cosmetics.favorite_hats"
        );
    }
}
