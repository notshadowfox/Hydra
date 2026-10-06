package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

final class PlayerSettingsServiceTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private YamlConfiguration stored;
    private PlayerSettingsService settings;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = mock(HGLootPlugin.class);
        stored = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(stored);
        settings = new PlayerSettingsService(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsAreIndependentAndMissingNewKeysDoNotResetOldOnes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (PlayerSettingsService.Toggle toggle : PlayerSettingsService.Toggle.values()) {
            assertEquals(toggle.defaultValue(), settings.enabled(first, toggle), toggle.name());
        }
        assertEquals(PlayerSettingsService.LobbyTime.SERVER_DEFAULT, settings.lobbyTime(first));
        stored.set(PlayerSettingsService.path(first, PlayerSettingsService.Toggle.TEAM_GLOW), false);
        assertFalse(settings.enabled(first, PlayerSettingsService.Toggle.TEAM_GLOW));
        assertTrue(settings.enabled(second, PlayerSettingsService.Toggle.TEAM_GLOW));
        assertTrue(settings.enabled(first, PlayerSettingsService.Toggle.FRIEND_REQUESTS));
    }

    @Test
    void toggleWritesImmediatelyAndSurvivesServiceRecreation() throws Exception {
        Player player = server.addPlayer("SettingsOwner");
        settings.set(player, PlayerSettingsService.Toggle.ROTATING_TIPS, false);
        verify(plugin).saveConfig();
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(stored.saveToString());
        when(plugin.getConfig()).thenReturn(reloaded);
        assertFalse(new PlayerSettingsService(plugin).enabled(player.getUniqueId(),
            PlayerSettingsService.Toggle.ROTATING_TIPS));
        assertTrue(settings.enabled(UUID.randomUUID(), PlayerSettingsService.Toggle.ROTATING_TIPS));
    }

    @Test
    void lobbyTimeCyclesAllFiveChoicesAndRecoversFromBadStoredValue() {
        Player player = server.addPlayer("TimeOwner");
        PlayerSettingsService.LobbyTime time = settings.lobbyTime(player.getUniqueId());
        for (PlayerSettingsService.LobbyTime expected : new PlayerSettingsService.LobbyTime[] {
            PlayerSettingsService.LobbyTime.DAY, PlayerSettingsService.LobbyTime.SUNSET,
            PlayerSettingsService.LobbyTime.NIGHT, PlayerSettingsService.LobbyTime.SUNRISE,
            PlayerSettingsService.LobbyTime.SERVER_DEFAULT
        }) {
            time = time.next();
            settings.setLobbyTime(player, time);
            assertEquals(expected, new PlayerSettingsService(plugin).lobbyTime(player.getUniqueId()));
        }
        verify(plugin, times(5)).saveConfig();
        stored.set("player-settings." + player.getUniqueId() + ".lobby-time", "BOGUS");
        assertEquals(PlayerSettingsService.LobbyTime.SERVER_DEFAULT, settings.lobbyTime(player.getUniqueId()));
    }

    @Test
    void nightVisionOptOutDoesNotRemoveAnotherSourcesEffect() {
        Player player = server.addPlayer("NightOwner");
        settings.set(player, PlayerSettingsService.Toggle.NIGHT_VISION, false);
        settings.applyHydraNightVision(player);
        assertFalse(player.hasPotionEffect(PotionEffectType.NIGHT_VISION));

        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.NIGHT_VISION, 500, 2));
        settings.applyHydraNightVision(player);
        settings.clearHydraNightVision(player);
        assertEquals(2, player.getPotionEffect(PotionEffectType.NIGHT_VISION).getAmplifier());
    }

    @Test
    void spectatorOptOutIsPerViewerAndDoesNotHideLivingPlayers() {
        Player offViewer = server.addPlayer("OffViewer");
        Player onViewer = server.addPlayer("OnViewer");
        Player otherSpectator = server.addPlayer("OtherSpectator");
        Player living = server.addPlayer("Living");
        settings.set(offViewer, PlayerSettingsService.Toggle.SPECTATOR_VISIBILITY, false);
        settings.syncSpectatorPair(offViewer, otherSpectator);
        settings.syncSpectatorPair(onViewer, otherSpectator);
        assertFalse(offViewer.canSee(otherSpectator));
        assertTrue(onViewer.canSee(otherSpectator));
        assertTrue(offViewer.canSee(living));
        settings.set(offViewer, PlayerSettingsService.Toggle.SPECTATOR_VISIBILITY, true);
        settings.syncSpectatorPair(offViewer, otherSpectator);
        assertTrue(offViewer.canSee(otherSpectator));
    }
}
