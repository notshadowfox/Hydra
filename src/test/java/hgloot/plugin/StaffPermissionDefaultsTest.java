package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StaffPermissionDefaultsTest {
    @Test
    void helperInheritsModerationToolsAndTempBanCommandsAreRegistered() throws Exception {
        YamlConfiguration pluginYml = new YamlConfiguration();
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertTrue(stream != null, "plugin.yml must be available to tests");
            pluginYml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        assertTrue(pluginYml.getBoolean("permissions.rank.staff.helper.children.rank.staff.trial_staff"));
        assertTrue(pluginYml.getBoolean("permissions.rank.staff.trial_staff.children.hgloot.moderation.tempban"));
        assertTrue(pluginYml.isConfigurationSection("commands.tempban"));
        assertTrue(pluginYml.isConfigurationSection("commands.unban"));
    }
}
