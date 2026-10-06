package hgloot.plugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import hgloot.plugin.MenuService;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;

@SuppressWarnings("deprecation")
public final class MenuListener implements Listener {
    private final MenuService menuService;

    public MenuListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        menuService.handleInventoryClickProtection(player, event);
        menuService.handleClick(player, event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (menuService.isOurMenu(event.getView())) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            menuService.handleInventoryDragProtection(player, event);
        }
    }

    @EventHandler
    public void onEntityEquipmentChanged(EntityEquipmentChangedEvent event) {
        menuService.handleEntityEquipmentChanged(event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        menuService.handleBlockPlace(event);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        menuService.handleProjectileLaunch(event);
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        menuService.handleEntityShootBow(event);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        menuService.handleProjectileHit(event);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        menuService.handleEntityDamageByEntity(event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        menuService.handleEntityDamage(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        menuService.handlePlayerDeath(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        menuService.clearArmedShot(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        menuService.handlePlayerJoin(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        menuService.handlePlayerRespawn(event);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        menuService.handlePlayerInteract(event);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        menuService.handlePlayerDropItem(event);
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        menuService.handlePlayerSwapHandItems(event);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        menuService.handlePlayerChangedWorld(event);
    }

}
