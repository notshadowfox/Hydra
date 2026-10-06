package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;

import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.recipe.RecipeBookDefinition.*;
import hgloot.plugin.recipe.RecipeBookRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class AdminUtilityMenuTest {
    private ServerMock server;
    private PluginMock plugin;
    private MenuService menus;
    private PlayerMock player;
    private RecipeBookRegistry registry;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        var ultraPlugin = hgloot.plugin.ultraroyales.UltraRoyales.class.getDeclaredField("plugin");
        ultraPlugin.setAccessible(true);
        ultraPlugin.set(null, plugin);
        menus = new MenuService(plugin, null, null, null);
        // MockBukkit lacks Material's default attribute registry; only stub that unrelated sword factory.
        try (var items = org.mockito.Mockito.mockStatic(hgloot.plugin.ultraroyales.Items.class, invocation -> {
            if (invocation.getMethod().getName().equals("gildedSword")) {
                return new ItemStack((Material) invocation.getArgument(0));
            }
            return invocation.callRealMethod();
        })) {
            registry = RecipeBookCatalog.createRegistry();
        } catch (org.mockbukkit.mockbukkit.exception.UnimplementedOperationException unsupported) {
            throw new AssertionError("Admin GUI coverage must execute, not silently skip", unsupported);
        }
        menus.setHungerGamesUtilityRegistry(() -> registry);
        player = server.addPlayer();
        player.addAttachment(plugin, "hgloot.admin", true);
    }

    @AfterEach
    void teardown() throws Exception {
        var ultraPlugin = hgloot.plugin.ultraroyales.UltraRoyales.class.getDeclaredField("plugin");
        ultraPlugin.setAccessible(true);
        ultraPlugin.set(null, null);
        MockBukkit.unmock();
    }

    @Test
    void utilityMenuContainsEveryAuthoritativeHgUtilityAndNoLegendaries() {
        menus.openAdminHungerGamesUtilitiesMenu(player);
        Set<String> actual = new HashSet<>();
        for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
            String action = action(item);
            if (action != null && action.startsWith("admin_hg_utility_give:")) {
                actual.add(action.substring("admin_hg_utility_give:".length()));
            }
        }
        Set<String> expected = new HashSet<>();
        registry.visibleRecipes(RecipeMode.HUNGER_GAMES, RecipeCategory.BASIC)
            .forEach(recipe -> expected.add(recipe.id().getKey()));
        assertEquals(12, actual.size()); // Retired Super Stew and Smoke Bomb are not active HG utilities.
        assertFalse(actual.contains("smoke_bomb"));
        assertEquals(expected, actual);
    }

    @Test
    void leftClickGrantsCleanAuthoritativeOutputWithoutMenuActionOrCraftConsumption() {
        menus.openAdminHungerGamesUtilitiesMenu(player);
        InventoryClickEvent event = click(utilitySlot("ghost_knife"), ClickType.LEFT);
        menus.handleClick(player, event);
        assertTrue(event.isCancelled());
        AvailableView ghost = (AvailableView) registry.resolveVisible(new NamespacedKey("hydra", "ghost_knife"),
            RecipeMode.HUNGER_GAMES).orElseThrow().view();
        assertTrue(player.getInventory().containsAtLeast(ghost.result().createItem(), 1));
        for (ItemStack item : player.getInventory().getStorageContents()) assertNull(action(item));
    }

    @Test
    void shiftRightAndHotbarClicksNeverPickUpOrGrantMenuIcons() {
        menus.openAdminHungerGamesUtilitiesMenu(player);
        for (ClickType type : new ClickType[]{ClickType.SHIFT_LEFT, ClickType.RIGHT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK}) {
            InventoryClickEvent event = click(utilitySlot("ghost_knife"), type);
            menus.handleClick(player, event);
            assertTrue(event.isCancelled());
        }
        assertTrue(player.getInventory().isEmpty());
    }

    @Test
    void permissionRevocationBlocksGrantFromAlreadyOpenMenu() {
        menus.openAdminHungerGamesUtilitiesMenu(player);
        player.addAttachment(plugin, "hgloot.admin", false);
        menus.handleClick(player, click(utilitySlot("ghost_knife"), ClickType.LEFT));
        assertTrue(player.getInventory().isEmpty());
    }

    @Test
    void bossRushSectionHasNavigationOnlyAndIsAdminOnly() {
        menus.openAdminBossRushToolsMenu(player);
        int actions = 0;
        for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
            String action = action(item);
            if (action != null && !action.equals("noop")) {
                assertEquals("back_admin_main", action);
                actions++;
            }
        }
        assertEquals(1, actions);
        player.closeInventory();
        var closedView = player.getOpenInventory();
        player.addAttachment(plugin, "hgloot.admin", false);
        menus.openAdminBossRushToolsMenu(player);
        assertSame(closedView, player.getOpenInventory());
    }

    @Test
    void hgClearButtonDispatchesSupportedCommandWithoutLegacyQueueArgument() {
        menus.openAdminHungerGamesToolsMenu(player);
        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            bukkit.when(() -> org.bukkit.Bukkit.dispatchCommand(player, "admin hg clear")).thenReturn(true);
            bukkit.clearInvocations();
            menus.handleClick(player, click(12, ClickType.LEFT));
            bukkit.verify(() -> org.bukkit.Bukkit.dispatchCommand(player, "admin hg clear"));
        }
    }

    @Test
    void randomizedPacksGrantVanillaCraftOutputsWithinTheRealRanges() {
        Map<String, Material> materials = Map.of("fletchers_pack", Material.ARROW,
            "wind_pack", Material.WIND_CHARGE, "web_bundle", Material.COBWEB);
        for (String id : materials.keySet()) {
            menus.openAdminHungerGamesUtilitiesMenu(player);
            int slot = -1;
            var inventory = player.getOpenInventory().getTopInventory();
            for (int i = 0; i < inventory.getSize(); i++) {
                if (("admin_hg_utility_give:" + id).equals(action(inventory.getItem(i)))) slot = i;
            }
            assertTrue(slot >= 0);
            Set<Integer> quantities = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                player.getInventory().clear();
                menus.handleClick(player, click(slot, ClickType.LEFT));
                ItemStack result = player.getInventory().getItem(0);
                assertNotNull(result);
                assertTrue(result.isSimilar(new ItemStack(materials.get(id))));
                int min = id.equals("fletchers_pack") ? 8 : 4;
                int max = id.equals("fletchers_pack") ? 12 : id.equals("wind_pack") ? 8 : 6;
                assertTrue(result.getAmount() >= min && result.getAmount() <= max);
                quantities.add(result.getAmount());
            }
            assertTrue(quantities.size() > 1, "admin grants must not use the fixed display quantity");
        }
    }

    @Test
    void ghostKnifeRecipeMatchesApprovedCrossAndRetainsSingleUseWoodenKnife() {
        var recipe = registry.resolveVisible(new NamespacedKey("hydra", "ghost_knife"), RecipeMode.HUNGER_GAMES).orElseThrow();
        ShapedLayout layout = (ShapedLayout) recipe.layout();
        Map<GridPosition, Material> expected = Map.of(
            new GridPosition(0, 1), Material.IRON_INGOT,
            new GridPosition(1, 0), Material.WIND_CHARGE,
            new GridPosition(1, 1), Material.STONE_SWORD,
            new GridPosition(1, 2), Material.WIND_CHARGE,
            new GridPosition(2, 1), Material.IRON_INGOT);
        assertEquals(expected.keySet(), layout.cells().keySet());
        expected.forEach((position, material) -> {
            assertEquals(new MaterialChoice(material), layout.cells().get(position).choice());
            assertEquals(1, layout.cells().get(position).requiredAmount());
        });
        ItemStack knife = ((AvailableView) recipe.view()).result().createItem();
        assertEquals(Material.WOODEN_SWORD, knife.getType());
        assertEquals(1, knife.getAmount());
    }

    @Test
    void countdownMigrationUpdatesOldTenOnceAndPreservesCustomSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("queue.hunger_games.countdown_seconds", 10);
        HGLootPlugin.migrateHungerGamesCountdown(config);
        assertEquals(30, config.getInt("queue.hunger_games.countdown_seconds"));
        config.set("queue.hunger_games.countdown_seconds", 10);
        HGLootPlugin.migrateHungerGamesCountdown(config);
        assertEquals(10, config.getInt("queue.hunger_games.countdown_seconds"));
        YamlConfiguration custom = new YamlConfiguration();
        custom.set("queue.hunger_games.countdown_seconds", 45);
        HGLootPlugin.migrateHungerGamesCountdown(custom);
        assertEquals(45, custom.getInt("queue.hunger_games.countdown_seconds"));
    }

    private InventoryClickEvent click(int slot, ClickType type) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            slot, type, InventoryAction.PICKUP_ALL, type == ClickType.NUMBER_KEY ? 0 : -1);
    }

    private int utilitySlot(String id) {
        var inventory = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (("admin_hg_utility_give:" + id).equals(action(inventory.getItem(slot)))) return slot;
        }
        throw new AssertionError("Missing HG utility: " + id);
    }

    private String action(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "menu_action"), PersistentDataType.STRING);
    }
}
