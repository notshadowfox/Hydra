package hgloot.plugin.announcement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.PlayerSettingsService;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class RotatingTipSettingsTest {
    private ServerMock server;

    @BeforeEach void setup() { server = MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test
    void tipsAndRejoinRemindersHaveIndependentPerPlayerAudience() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("announcements.rotating.messages", List.of(
            "Disconnected mid-match? Use /rejoin to jump back in.", "Use /social to manage friends."));
        when(plugin.getConfig()).thenReturn(config);
        PlayerSettingsService settings = new PlayerSettingsService(plugin);
        when(plugin.playerSettingsService()).thenReturn(settings);
        PlayerMock all = server.addPlayer("AllTips");
        PlayerMock noRejoin = server.addPlayer("NoRejoin");
        PlayerMock noTips = server.addPlayer("NoTips");
        settings.set(noRejoin, PlayerSettingsService.Toggle.REJOIN_REMINDER, false);
        settings.set(noTips, PlayerSettingsService.Toggle.ROTATING_TIPS, false);

        RotatingAnnouncementService announcements = new RotatingAnnouncementService(plugin);
        Method next = RotatingAnnouncementService.class.getDeclaredMethod("broadcastNext");
        next.setAccessible(true);
        next.invoke(announcements);
        assertTrue(all.nextMessage().contains("/rejoin"));
        assertNull(noRejoin.nextMessage());
        assertNull(noTips.nextMessage());

        next.invoke(announcements);
        assertTrue(all.nextMessage().contains("/social"));
        assertTrue(noRejoin.nextMessage().contains("/social"));
        assertNull(noTips.nextMessage());
    }
}
