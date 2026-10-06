package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

class KitEditorPlaceholderTest {
    private ServerMock server;
    private PluginMock plugin;
    private MenuService menus;
    private PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        var ultraPlugin = hgloot.plugin.ultraroyales.UltraRoyales.class.getDeclaredField("plugin");
        ultraPlugin.setAccessible(true);
        ultraPlugin.set(null, plugin);
        menus = new MenuService(plugin, null, null, null);
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() throws Exception {
        var ultraPlugin = hgloot.plugin.ultraroyales.UltraRoyales.class.getDeclaredField("plugin");
        ultraPlugin.setAccessible(true);
        ultraPlugin.set(null, null);
        MockBukkit.unmock();
    }

    @Test
    void bridgeAndBossRushAreAvailableAndRemainingFutureSlotsAreInertBarriers() {
        menus.openPlayerKitEditorMenu(player);
        var inventory = player.getOpenInventory().getTopInventory();
        ItemStack bridge = inventory.getItem(19);
        assertEquals(Material.IRON_SWORD, bridge.getType());
        assertEquals("player_edit_bridge_kit", action(bridge));

        ItemStack bossRush = inventory.getItem(21);
        assertEquals(Material.DIAMOND_SWORD, bossRush.getType());
        assertEquals("player_edit_boss_rush_kit", action(bossRush));

        for (int slot : new int[] {23, 25, 37, 39, 41, 43}) {
            ItemStack placeholder = inventory.getItem(slot);
            assertEquals(Material.BARRIER, placeholder.getType(), "slot " + slot);
            assertEquals("Coming Soon!", ChatColor.stripColor(placeholder.getItemMeta().getDisplayName()));
            assertEquals("noop", action(placeholder));
            InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            menus.handleClick(player, click);
            assertTrue(click.isCancelled());
            assertSame(inventory, player.getOpenInventory().getTopInventory());
        }
        assertNull(player.nextMessage());
    }

    @Test
    void bossRushSelectionOpensTheExistingKitEditorService() {
        KitEditorService editor = mock(KitEditorService.class);
        menus.setKitEditorService(editor);
        menus.openPlayerKitEditorMenu(player);
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER, 21, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        menus.handleClick(player, click);
        assertTrue(click.isCancelled());
        verify(editor).beginWithTransition(player, BossRushKitDefinition.ID);
    }

    private String action(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "menu_action"), PersistentDataType.STRING);
    }
}
