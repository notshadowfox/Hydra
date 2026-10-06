package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.ultraroyales.UltraRoyales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PodRecorder implements Listener {
    private static final File podsFile = new File(UltraRoyales.getPlugin().getDataFolder(), "pods.yml");
    private static final FileConfiguration podsConfig = YamlConfiguration.loadConfiguration(podsFile);

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!player.hasPermission("pods.set")) return;

        ItemStack item = e.getItem();
        if (!e.getAction().toString().contains("RIGHT_CLICK") || e.getClickedBlock() == null || item == null || item.getType() != Material.BLAZE_ROD) return;
        if (!item.hasItemMeta() || !Objects.equals(item.getItemMeta().displayName(), Component.text("Pod Wand"))) return;

        // Get block above one clicked
        Location loc = e.getClickedBlock().getLocation().add(0, 1, 0);

        // Store manually so yaw/pitch are excluded
        Map<String, Object> serialized = new HashMap<>();
        serialized.put("world", loc.getWorld().getName());
        serialized.put("x", loc.getX());
        serialized.put("y", loc.getY());
        serialized.put("z", loc.getZ());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pods = (List<Map<String, Object>>) podsConfig.getList("pods", new ArrayList<>());
        pods.add(serialized);
        podsConfig.set("pods", pods);

        try {
            podsConfig.save(podsFile);
            player.sendMessage(Component.text("Pod saved! Total: " + pods.size(), NamedTextColor.GREEN));
        } catch (IOException error) {
            UltraRoyales.getPlugin().getLogger().warning(Arrays.toString(error.getStackTrace()));
        }

        player.sendMessage(Component.text(
                String.format("Stored location %.1f, %.1f, %.1f",
                        loc.getX(), loc.getY(), loc.getZ()
                ),
                NamedTextColor.GREEN
        ));
    }

    public static List<Location> getPods() {
        List<Location> locations = new ArrayList<>();

        for (Map<?, ?> map : podsConfig.getMapList("pods")) {
            String worldName = (String) map.get("world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue; // skip if world not loaded

            double x = ((Number) map.get("x")).doubleValue();
            double y = ((Number) map.get("y")).doubleValue();
            double z = ((Number) map.get("z")).doubleValue();

            locations.add(new Location(world, x, y, z));
        }

        return locations;
    }
}

