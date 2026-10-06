package hgloot.plugin;

import org.bukkit.entity.Player;

//logs whether a delayed spec handoff still reached the right match
final class SpectatorRouteTrace {
    private SpectatorRouteTrace() { }

    static void midpoint(HGLootPlugin plugin, Player spectator, Player target,
                         String instanceId, boolean success) {
        plugin.getLogger().fine("[Spectate] invoker=" + spectator.getUniqueId()
            + " target=" + target.getUniqueId() + " instance=" + instanceId
            + " midpoint=" + (success ? "entered" : "rejected"));
    }
}
