package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class LobbySettingsContextTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private QueueService queue;
    private PlayerSettingsService settings;
    private World hub;
    private World match;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        hub = server.addSimpleWorld("Hub");
        match = server.addSimpleWorld("Match");
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        queue = mock(QueueService.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.queueService()).thenReturn(queue);
        settings = new PlayerSettingsService(plugin);
        when(plugin.playerSettingsService()).thenReturn(settings);
        when(queue.isLobbyWorld(org.mockito.ArgumentMatchers.any())).thenAnswer(call ->
            ((PlayerMock) call.getArgument(0)).getWorld() == hub);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test
    void personalLobbyTimesDifferAndActiveMatchResetsThenLobbyReapplies() {
        PlayerMock day = server.addPlayer("DayViewer");
        PlayerMock night = server.addPlayer("NightViewer");
        day.teleport(new Location(hub, 0, 80, 0));
        night.teleport(new Location(hub, 1, 80, 0));
        settings.setLobbyTime(day, PlayerSettingsService.LobbyTime.DAY);
        settings.setLobbyTime(night, PlayerSettingsService.LobbyTime.NIGHT);
        assertEquals(1000L, day.getPlayerTime());
        assertEquals(18000L, night.getPlayerTime());

        day.teleport(new Location(match, 0, 80, 0));
        when(queue.isPlayerInHydraMatchOrSpectating(day.getUniqueId())).thenReturn(true);
        settings.refreshContext(day);
        assertNotEquals(1000L, day.getPlayerTime());
        assertEquals(18000L, night.getPlayerTime());

        when(queue.isPlayerInHydraMatchOrSpectating(day.getUniqueId())).thenReturn(false);
        day.teleport(new Location(hub, 0, 80, 0));
        settings.refreshContext(day);
        assertEquals(1000L, day.getPlayerTime());
    }

    @Test
    void lobbyFlightNeedsHydraPlusAndDoesNotRegrantInMatch() {
        PlayerMock eligible = server.addPlayer("Eligible");
        PlayerMock ineligible = server.addPlayer("Ineligible");
        eligible.teleport(new Location(hub, 0, 80, 0));
        ineligible.teleport(new Location(hub, 1, 80, 0));
        when(plugin.hasHydraPlusTierRank(eligible.getUniqueId())).thenReturn(true);
        settings.set(eligible, PlayerSettingsService.Toggle.LOBBY_FLIGHT, true);
        settings.set(ineligible, PlayerSettingsService.Toggle.LOBBY_FLIGHT, true);
        assertFalse(eligible.getAllowFlight());
        assertFalse(ineligible.getAllowFlight());
        assertTrue(settings.flightEnabled(eligible));
        assertFalse(settings.flightEnabled(ineligible));
        assertEquals(Material.ELYTRA, eligible.getInventory().getChestplate().getType());
        assertEquals(1, eligible.getInventory().getChestplate().getEnchantmentLevel(Enchantment.BINDING_CURSE));

        eligible.teleport(new Location(match, 0, 80, 0));
        eligible.setAllowFlight(false); // Match startup owns this transition.
        when(queue.isPlayerInHydraMatchOrSpectating(eligible.getUniqueId())).thenReturn(true);
        settings.refreshContext(eligible);
        assertFalse(eligible.getAllowFlight());
        assertNull(eligible.getInventory().getChestplate());

        when(queue.isPlayerInHydraMatchOrSpectating(eligible.getUniqueId())).thenReturn(false);
        eligible.teleport(new Location(hub, 0, 80, 0));
        settings.refreshContext(eligible);
        assertFalse(eligible.getAllowFlight());
        assertEquals(Material.ELYTRA, eligible.getInventory().getChestplate().getType());
    }

    @Test
    void sharedToggleEquipsCanonicalBoundWingsAndDisablesFromEitherInterface() {
        PlayerMock player = server.addPlayer("Wings");
        player.teleport(new Location(hub, 0, 80, 0));
        when(plugin.hasHydraPlusTierRank(player.getUniqueId())).thenReturn(true);

        assertTrue(settings.setFlightEnabled(player, true)); // /fly entry point.
        assertTrue(settings.flightEnabled(player)); // Settings rendering source.
        assertFalse(player.getAllowFlight(), "Hydra+ wings must not grant vanilla flight");
        ItemStack equipped = player.getInventory().getChestplate();
        assertEquals(Material.ELYTRA, equipped.getType());
        assertTrue(equipped.isSimilar(hgloot.plugin.ultraroyales.Items.monarchWings()) == false);
        assertEquals(3, equipped.getEnchantmentLevel(Enchantment.PROTECTION));
        assertEquals(1, equipped.getEnchantmentLevel(Enchantment.BINDING_CURSE));
        settings.refreshContext(player);
        settings.refreshContext(player);
        assertFalse(player.getAllowFlight());
        assertEquals(Material.ELYTRA, player.getInventory().getChestplate().getType());
        assertEquals(0, java.util.Arrays.stream(player.getInventory().getStorageContents())
            .filter(item -> item != null && item.getType() == Material.ELYTRA).count());
        settings.set(player, PlayerSettingsService.Toggle.LOBBY_FLIGHT, false); // Settings entry point.
        assertFalse(settings.flightEnabled(player));
        assertFalse(player.getAllowFlight());
        assertNull(player.getInventory().getChestplate());

        settings.set(player, PlayerSettingsService.Toggle.LOBBY_FLIGHT, true);
        assertTrue(settings.flightEnabled(player)); // /fly sees ON.
        assertTrue(settings.setFlightEnabled(player, false)); // /fly turns it OFF.
        assertFalse(settings.flightEnabled(player)); // Settings sees OFF.
        assertNull(player.getInventory().getChestplate());
    }

    @Test
    void legacyHydraPlusLobbyFlightIsClearedWhileWingsStayEquipped() {
        PlayerMock player = server.addPlayer("LegacyFlight");
        player.teleport(new Location(hub, 0, 80, 0));
        when(plugin.hasHydraPlusTierRank(player.getUniqueId())).thenReturn(true);
        player.setAllowFlight(true); // Still connected after 2.0.221 grant.
        player.setFlying(true);
        assertTrue(settings.setFlightEnabled(player, true));
        assertFalse(player.getAllowFlight());
        assertFalse(player.isFlying());
        assertEquals(Material.ELYTRA, player.getInventory().getChestplate().getType());
    }

    @Test
    void occupiedChestSlotRefusesEnableAndDisableNeverRemovesUnownedItem() {
        PlayerMock player = server.addPlayer("Armored");
        player.teleport(new Location(hub, 0, 80, 0));
        when(plugin.hasHydraPlusTierRank(player.getUniqueId())).thenReturn(true);
        ItemStack armor = new ItemStack(Material.DIAMOND_CHESTPLATE);
        player.getInventory().setChestplate(armor);
        assertFalse(settings.setFlightEnabled(player, true));
        assertFalse(settings.flightEnabled(player));
        assertEquals(armor, player.getInventory().getChestplate());
        assertTrue(settings.setFlightEnabled(player, false));
        assertEquals(armor, player.getInventory().getChestplate());
        ItemStack playerOwnedWings = hgloot.plugin.ultraroyales.Items.monarchWings();
        player.getInventory().setChestplate(playerOwnedWings);
        assertTrue(settings.setFlightEnabled(player, false));
        assertEquals(playerOwnedWings, player.getInventory().getChestplate());
    }

    @Test
    void logoutAndRankLossRemoveOnlyHydraGrantedWings() {
        PlayerMock player = server.addPlayer("RankLoss");
        player.teleport(new Location(hub, 0, 80, 0));
        when(plugin.hasHydraPlusTierRank(player.getUniqueId())).thenReturn(true);
        assertTrue(settings.setFlightEnabled(player, true));
        settings.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, ""));
        assertNull(player.getInventory().getChestplate());
        assertTrue(settings.flightEnabled(player)); // Preference safely reapplies on a later join.
        settings.refreshContext(player);
        assertEquals(Material.ELYTRA, player.getInventory().getChestplate().getType());
        when(plugin.hasHydraPlusTierRank(player.getUniqueId())).thenReturn(false);
        settings.refreshContext(player);
        assertFalse(settings.flightEnabled(player));
        assertNull(player.getInventory().getChestplate());
    }

    @Test
    void baseHydraRankIsNotHydraPlusFlightEligible() {
        java.util.UUID id = java.util.UUID.randomUUID();
        doCallRealMethod().when(plugin).hasHydraPlusTierRank(id);
        when(plugin.hydraRankModeFor(id)).thenReturn("base", "tier1", "tier2", "tier3");
        assertFalse(plugin.hasHydraPlusTierRank(id));
        assertTrue(plugin.hasHydraPlusTierRank(id));
        assertTrue(plugin.hasHydraPlusTierRank(id));
        assertTrue(plugin.hasHydraPlusTierRank(id));
    }
}
