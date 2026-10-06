package hgloot.plugin;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

public final class HubProtectionService implements Listener {
    private static final long JOIN_APPLY_DELAY_TICKS = 5L;
    private static final long HUB_WORLD_APPLY_DELAY_TICKS = 1L;
    private static final int HUB_VOID_OFFSET = 4;

    private final HGLootPlugin plugin;
    private final MenuService menuService;
    private final QueueService queueService;
    private final NpcService npcService;

    public HubProtectionService(
        HGLootPlugin plugin,
        MenuService menuService,
        QueueService queueService,
        NpcService npcService
    ) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.queueService = queueService;
        this.npcService = npcService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleHubStateApply(event.getPlayer(), JOIN_APPLY_DELAY_TICKS, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleHubStateApply(event.getPlayer(), HUB_WORLD_APPLY_DELAY_TICKS, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location respawn = event.getRespawnLocation();
        if (respawn == null || !isHubWorld(respawn.getWorld())) {
            return;
        }
        scheduleHubStateApply(player, HUB_WORLD_APPLY_DELAY_TICKS, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isHubWorld(event.getBlock().getWorld()) && !hasHubBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isHubWorld(event.getBlock().getWorld()) && !hasHubBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isHubPlayer(event.getPlayer()) && !hasHubBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (isHubWorld(event.getEntity().getWorld())
            && (!(event.getEntity() instanceof Player player) || !hasHubBypass(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHubPlayer(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHubPlayer(player)) {
            return;
        }
        if (isNpcEntity(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isHubWorld(event.getEntity().getWorld())) {
            return;
        }
        if (isNpcEntity(event.getEntity())) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isHubWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!isHubWorld(event.getWorld())) {
            return;
        }
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
        clearWeather(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!isHubWorld(event.getWorld())) {
            return;
        }
        if (event.toThunderState()) {
            event.setCancelled(true);
        }
        clearWeather(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isHubPlayer(player)) {
            return;
        }
        if (hasHubBypass(player)) {
            return;
        }
        if (event.getView().getType() == InventoryType.CRAFTING
            || event.getView().getType() == InventoryType.CREATIVE) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }
        if (event.getHotbarButton() >= 0
            || event.isShiftClick()
            || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory().equals(player.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isHubPlayer(player) && !hasHubBypass(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && isHubPlayer(player) && !hasHubBypass(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHubPlayer(player)) {
            return;
        }
        if (hasHubBypass(player)) {
            return;
        }
        if (menuService != null && menuService.isHubItem(event.getItem())) {
            return;
        }
        if (isVanillaButtonUse(event.getAction(),
            event.getClickedBlock() == null ? null : event.getClickedBlock().getType())) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        event.setCancelled(true);
    }

    static boolean isVanillaButtonUse(Action action, Material clickedType) {
        return action == Action.RIGHT_CLICK_BLOCK && clickedType != null
            && clickedType.name().endsWith("_BUTTON");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isHubPlayer(event.getPlayer())) {
            return;
        }
        if (hasHubBypass(event.getPlayer())) {
            return;
        }
        if (isNpcEntity(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isHubPlayer(event.getPlayer()) && !hasHubBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !isHubWorld(to.getWorld())) {
            return;
        }
        if (to.getY() >= voidThreshold(to.getWorld())) {
            return;
        }
        applyHubPlayerState(event.getPlayer(), true);
    }

    private void scheduleHubStateApply(Player player, long delayTicks, boolean teleportToSpawn) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyHubPlayerState(player, teleportToSpawn), delayTicks);
    }

    private void applyHubPlayerState(Player player, boolean teleportToSpawn) {
        if (player == null || !player.isOnline() || !isHubPlayer(player)) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        clearWeather(world);
        if (teleportToSpawn) {
            Location spawn = centeredSpawn(world);
            if (spawn != null && player.getLocation().distanceSquared(spawn) > 0.64D) {
                player.teleport(spawn);
            }
        }
        if (hasHubBypass(player)) {
            healAndFeed(player);
            syncFlight(player);
            return;
        }
        if (menuService != null) {
            menuService.preparePlayerForHub(player);
        } else {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[] {null, null, null, null});
            player.getInventory().setItemInOffHand(null);
        }
        clearEffects(player);
        healAndFeed(player);
        syncFlight(player);
        player.closeInventory();
        player.updateInventory();
    }

    private void healAndFeed(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = maxHealth == null ? player.getHealth() : maxHealth.getValue();
        if (health > 0.0D) {
            player.setHealth(health);
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    private boolean hasHubBypass(Player player) {
        return player != null && player.hasPermission("hgloot.hub.bypass");
    }

    private void syncFlight(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.playerSettingsService() != null) {
            plugin.playerSettingsService().refreshContext(player);
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        UUID playerId = player.getUniqueId();
        boolean restrictedContext = queueService != null
            && (queueService.isPlayerQueued(playerId)
            || queueService.isPlayerInMatch(playerId)
            || queueService.isCustomSpectator(playerId));
        if (restrictedContext) {
            player.setFlying(false);
            player.setAllowFlight(false);
            return;
        }
        boolean adminFlightAllowed = player.hasPermission("hgloot.fly");
        if (adminFlightAllowed) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private void clearEffects(Player player) {
        if (player == null || player.getActivePotionEffects().isEmpty()) {
            return;
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect == null || effect.getType() == null) {
                continue;
            }
            player.removePotionEffect(effect.getType());
        }
    }

    private Location centeredSpawn(World world) {
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        if (spawn == null) {
            return null;
        }
        return spawn.clone().add(0.5D, 0.0D, 0.5D);
    }

    private int voidThreshold(World world) {
        return world == null ? Integer.MIN_VALUE : world.getMinHeight() + HUB_VOID_OFFSET;
    }

    private void clearWeather(World world) {
        if (world == null) {
            return;
        }
        if (world.hasStorm()) {
            world.setStorm(false);
        }
        if (world.isThundering()) {
            world.setThundering(false);
        }
        world.setWeatherDuration(0);
        world.setThunderDuration(0);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (!(damager instanceof org.bukkit.entity.Projectile projectile)) {
            return null;
        }
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player ? player : null;
    }

    private boolean isNpcEntity(Entity entity) {
        return npcService != null && npcService.isNpcEntity(entity);
    }

    private boolean isHubPlayer(Player player) {
        return player != null && isHubWorld(player.getWorld());
    }

    private boolean isHubWorld(World world) {
        if (world == null) {
            return false;
        }
        String worldName = world.getName();
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        String normalized = worldName.toLowerCase(Locale.ROOT);
        return normalized.equals("hub") || normalized.startsWith("hub_");
    }
}
