package hgloot.plugin.ultraroyales;

import org.bukkit.World;
import org.bukkit.entity.Entity;

public final class LegendaryModeRules {

    private LegendaryModeRules() {
    }

    public static boolean isHungerGamesWorld(World world) {
        if (world == null) {
            return false;
        }
        String prefix = UltraRoyales.getPlugin() == null
            ? "hg_match"
            : UltraRoyales.getPlugin().getConfig().getString("queue.match_world.prefix", "hg_match");
        String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "hg_match" : prefix.trim().toLowerCase();
        String worldName = world.getName().toLowerCase();
        return worldName.startsWith("hg-world-")
            || worldName.startsWith(normalizedPrefix + "_hungergames_");
    }

    public static boolean isHungerGamesEntity(Entity entity) {
        return entity != null && isHungerGamesWorld(entity.getWorld());
    }
}
