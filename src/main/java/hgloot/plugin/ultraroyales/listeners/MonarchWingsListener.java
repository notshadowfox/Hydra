package hgloot.plugin.ultraroyales.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MonarchWingsListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:monarch_wings";
    private static final long ASCEND_COOLDOWN_TICKS = 8L;
    private static final Map<UUID, Long> nextAscendTick = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding() || !isMonarchWings(player.getInventory().getChestplate())) {
            return;
        }

        Vector velocity = player.getVelocity();
        Vector forward = player.getLocation().getDirection().clone().normalize();
        double horizontalSpeed = velocity.clone().setY(0.0D).length();
        double boost = Math.max(0.03D, horizontalSpeed * 0.03D);
        Vector boosted = velocity.add(forward.multiply(boost));
        player.setVelocity(boosted);

        if (Bukkit.getCurrentTick() % 4L == 0L) {
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !player.isGliding() || !isMonarchWings(player.getInventory().getChestplate())) {
            return;
        }

        long nowTick = Bukkit.getCurrentTick();
        long readyAt = nextAscendTick.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > nowTick) {
            return;
        }
        nextAscendTick.put(player.getUniqueId(), nowTick + ASCEND_COOLDOWN_TICKS);

        Location target = findSafeAscendTarget(player.getLocation(), 20);
        player.teleport(target);
        Vector velocity = player.getVelocity();
        Vector forward = player.getLocation().getDirection().clone().normalize();
        player.setVelocity(forward.multiply(Math.max(0.8D, velocity.clone().setY(0.0D).length())).setY(Math.max(0.8D, velocity.getY() + 0.8D)));
        player.setFallDistance(0.0F);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 18, 0.4D, 0.2D, 0.4D, 0.01D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.85F, 1.45F);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nextAscendTick.remove(event.getPlayer().getUniqueId());
    }

    private Location findSafeAscendTarget(Location origin, int ascendBlocks) {
        World world = origin.getWorld();
        if (world == null) {
            return origin;
        }

        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int targetY = Math.max(minY, Math.min(maxY, origin.getBlockY() + ascendBlocks));

        for (int y = targetY; y <= maxY; y++) {
            if (world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, origin.getX(), y, origin.getZ(), origin.getYaw(), origin.getPitch());
            }
        }

        return new Location(world, origin.getX(), targetY, origin.getZ(), origin.getYaw(), origin.getPitch());
    }

    private boolean isMonarchWings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent() && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }
}
