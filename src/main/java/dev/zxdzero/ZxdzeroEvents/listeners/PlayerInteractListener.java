package dev.zxdzero.ZxdzeroEvents.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.ItemActionRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class PlayerInteractListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction().toString().contains("RIGHT_CLICK")) {

            ItemStack item = e.getItem();
            if (item == null || !item.hasItemMeta()) return;

            ItemMeta meta = item.getItemMeta();
            if (!meta.hasCustomModelDataComponent()) return;

            BiConsumer<Player, ItemStack> action = ItemActionRegistry.getAction(meta.getCustomModelDataComponent());
            if (action != null) {
                action.accept(e.getPlayer(), item);
                e.setCancelled(true); // Prevent default use
            }
        }
    }
}
