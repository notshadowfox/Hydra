package hgloot.plugin.zxdzeroevents;

import dev.zxdzero.ZxdzeroEvents.PedestalManager;
import dev.zxdzero.ZxdzeroEvents.commands.PedestalCommand;
import dev.zxdzero.ZxdzeroEvents.listeners.PlayerInteractListener;
import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import dev.zxdzero.ZxdzeroEvents.registries.ItemMenuRegistry;
import dev.zxdzero.ZxdzeroEvents.registries.RecipeManager;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Boots the ZxdzeroEvents registries/listeners inside HydraMain so UltraRoyales
 * can run without an external ZxdzeroEvents plugin jar.
 */
public final class ZxdzeroEventsCoreService {
    private final JavaPlugin plugin;
    private PedestalManager pedestalManager;
    private PedestalCommand pedestalCommand;
    private boolean started;

    public ZxdzeroEventsCoreService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) {
            return;
        }

        try {
            CooldownRegistry.initialize(plugin);
        } catch (IllegalStateException ignored) {
            // Already initialized in this runtime.
        }

        this.pedestalManager = new PedestalManager(plugin);
        this.pedestalCommand = new PedestalCommand(pedestalManager);
        plugin.getServer().getPluginManager().registerEvents(pedestalManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerInteractListener(), plugin);

        started = true;
        plugin.getLogger().info("[ZxdzeroEventsCore] Initialized internal API.");
    }

    public void shutdown() {
        if (!started) {
            return;
        }
        ItemMenuRegistry.clearAllMenus();
        RecipeManager.unregisterRecipesFromPlugin(plugin);
        pedestalCommand = null;
        pedestalManager = null;
        started = false;
    }

    public boolean handlePedestal(CommandSender sender, String[] args) {
        return pedestalCommand != null && pedestalCommand.execute(sender, args);
    }

    public List<String> completePedestal(String[] args) {
        return pedestalCommand == null ? List.of() : pedestalCommand.complete(args);
    }

    public boolean placePedestal(Location location, String recipeId) {
        if (!started || pedestalManager == null || location == null || location.getWorld() == null
            || recipeId == null || recipeId.isBlank()) {
            return false;
        }
        try {
            pedestalManager.placePedestal(location, recipeId);
            return true;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[ZxdzeroEventsCore] Failed to place pedestal " + recipeId
                + " at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                + " in " + location.getWorld().getName() + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean placeRewardPedestal(Location location, String recipeId) {
        if (!started || pedestalManager == null || location == null || location.getWorld() == null
            || recipeId == null || recipeId.isBlank()) {
            return false;
        }
        try {
            pedestalManager.placeRewardPedestal(location, recipeId);
            return true;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[ZxdzeroEventsCore] Failed to place reward pedestal " + recipeId
                + " at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                + " in " + location.getWorld().getName() + ": " + exception.getMessage());
            return false;
        }
    }

    public int clearPedestals(Location center, double radius) {
        if (!started || pedestalManager == null || center == null || center.getWorld() == null || radius <= 0.0D) {
            return 0;
        }
        int removed = 0;
        for (int attempts = 0; attempts < 64; attempts++) {
            if (!pedestalManager.removePedestal(center, radius)) {
                break;
            }
            removed++;
        }
        return removed;
    }
}
