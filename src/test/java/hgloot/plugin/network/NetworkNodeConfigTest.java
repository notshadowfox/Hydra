package hgloot.plugin.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NetworkNodeConfigTest {
    @Test
    void loadsSecureHungerGamesNode() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("network.enabled", true);
        yaml.set("network.server_role", "HG");
        yaml.set("network.node_name", "HG-1");
        yaml.set("network.shared_secret", "local-secret");

        NetworkNodeConfig config = NetworkNodeConfig.load(yaml);

        assertTrue(config.enabled());
        assertEquals(BackendRole.HG, config.role());
        assertEquals("HG-1", config.nodeName());
    }

    @Test
    void rejectsDefaultSecretOnNetworkNode() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("network.enabled", true);
        yaml.set("network.server_role", "HUB");
        yaml.set("network.shared_secret", "CHANGE_ME");

        assertThrows(IllegalArgumentException.class, () -> NetworkNodeConfig.load(yaml));
    }

    @Test
    void preservesStandaloneDefault() {
        NetworkNodeConfig config = NetworkNodeConfig.load(new YamlConfiguration());
        assertEquals(BackendRole.STANDALONE, config.role());
        assertEquals(false, config.enabled());
    }
}
