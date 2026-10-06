package dev.zxdzero.ZxdzeroEvents.registries;

import org.bukkit.Bukkit;

/**
 * Legacy compatibility shim for older plugins that still reference
 * dev.zxdzero.ZxdzeroEvents.registries.Util.
 */
public final class Util {
    private Util() {
    }

    /**
     * Historical API expected by older Wither Games builds.
     * Returns true to keep legacy startup flow intact.
     */
    public static boolean sessionCheck() {
        return true;
    }

    /**
     * Optional helper for quick diagnostics in other legacy callers.
     */
    public static boolean isPluginEnabled(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return false;
        }
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }
}
