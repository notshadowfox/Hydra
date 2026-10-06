package hgloot.plugin.network;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HgDevelopmentBridgeTest {
    @Test void stalledServerThreadDoesNotInventCoordinatorFailure() {
        var watch = new HgDevelopmentBridge.ConnectivityWatch();
        watch.completed(true, 100);
        assertFalse(watch.expired(120_000));
    }
    @Test void realHttpFailureTimesOutWithoutSlidingDeadlineAndRecoveryClearsIt() {
        var watch = new HgDevelopmentBridge.ConnectivityWatch();
        watch.completed(false, 100);
        assertFalse(watch.expired(30_099));
        watch.completed(false, 20_000);
        assertTrue(watch.expired(30_100));
        watch.completed(true, 31_000);
        assertFalse(watch.expired(100_000));
    }
    NetworkNodeConfig config(boolean enabled, BackendRole role, String host) {
        return new NetworkNodeConfig(enabled, role, "HG-1", URI.create("http://" + host + ":8765"), "fixture", 1, 3000);
    }
    @Test void adapterRejectsDisabledNonHgAndNonLoopbackConfigurations() {
        assertThrows(IllegalArgumentException.class, () -> new HgDevelopmentBridge(null, config(false, BackendRole.HG, "127.0.0.1"), null));
        assertThrows(IllegalArgumentException.class, () -> new HgDevelopmentBridge(null, config(true, BackendRole.HUB, "127.0.0.1"), null));
        assertThrows(IllegalArgumentException.class, () -> new HgDevelopmentBridge(null, config(true, BackendRole.HG, "192.0.2.1"), null));
        assertDoesNotThrow(() -> new HgDevelopmentBridge(null, config(true, BackendRole.HG, "127.0.0.1"), null));
    }
    @Test void bundledConfigKeepsBothNetworkFlagsOff() throws Exception {
        var yaml = new YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        assertFalse(yaml.getBoolean("network.enabled"));
        assertFalse(yaml.getBoolean("network.hg_handshake_development"));
    }
    @Test void rosterRetainsPartyMembershipAndOddDuosWithoutReassignment() {
        UUID one = UUID.randomUUID(), two = UUID.randomUUID(), solo = UUID.randomUUID();
        var party = new HgDevelopmentBridge.Group(UUID.randomUUID(), one, List.of(new NetworkDtos.Member(one, "One"), new NetworkDtos.Member(two, "Two")));
        var single = new HgDevelopmentBridge.Group(UUID.randomUUID(), solo, List.of(new NetworkDtos.Member(solo, "Solo")));
        var command = new HgDevelopmentBridge.Command("PREPARE", UUID.randomUUID(), UUID.randomUUID(), "HG_DUOS", null, List.of(party, single));
        assertEquals(List.of(one, two, solo), command.roster());
        assertEquals(2, command.groups().getFirst().members().size());
    }
}
