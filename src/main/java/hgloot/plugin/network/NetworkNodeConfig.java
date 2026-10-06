package hgloot.plugin.network;

import java.net.URI;
import org.bukkit.configuration.file.FileConfiguration;

public record NetworkNodeConfig(
    boolean enabled,
    BackendRole role,
    String nodeName,
    URI coordinatorUri,
    String sharedSecret,
    int heartbeatSeconds,
    int requestTimeoutMillis
) {
    public static NetworkNodeConfig load(FileConfiguration config) {
        boolean requestedEnabled = config.getBoolean("network.enabled", false);
        BackendRole role = BackendRole.parse(config.getString("network.server_role", "STANDALONE"));
        String nodeName = config.getString("network.node_name", role.name());
        String coordinator = config.getString("network.coordinator_url", "http://127.0.0.1:8765");
        String secret = config.getString("network.shared_secret", "CHANGE_ME");
        if (requestedEnabled && !role.networked()) {
            throw new IllegalArgumentException("network.enabled requires HUB, GAMEHUB, or HG server_role");
        }
        if (requestedEnabled && (secret == null || secret.isBlank() || "CHANGE_ME".equals(secret))) {
            throw new IllegalArgumentException("network.shared_secret must be configured on network backends");
        }
        return new NetworkNodeConfig(
            requestedEnabled,
            role,
            nodeName == null || nodeName.isBlank() ? role.name() : nodeName.trim(),
            URI.create(coordinator),
            secret == null ? "" : secret,
            Math.max(1, config.getInt("network.heartbeat_seconds", 2)),
            Math.max(500, config.getInt("network.request_timeout_millis", 3000))
        );
    }
}
