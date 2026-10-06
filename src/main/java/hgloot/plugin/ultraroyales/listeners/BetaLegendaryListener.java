/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.Color
 *  org.bukkit.FireworkEffect
 *  org.bukkit.FireworkEffect$Type
 *  org.bukkit.FluidCollisionMode
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.block.BlockFace
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.EnderPearl
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Firework
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.event.Event$Result
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.enchantment.EnchantItemEvent
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDamageEvent$DamageCause
 *  org.bukkit.event.entity.EntityResurrectEvent
 *  org.bukkit.event.entity.ProjectileHitEvent
 *  org.bukkit.event.inventory.ClickType
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.event.inventory.PrepareAnvilEvent
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerItemHeldEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerRespawnEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.FireworkMeta
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.components.CustomModelDataComponent
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.util.RayTraceResult
 *  org.bukkit.util.Vector
 */
package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.TeamProtectionService;
import hgloot.plugin.ultraroyales.UltraRoyales;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class BetaLegendaryListener
implements Listener {
    private static final String GOBLIN_MASK_ID = "ultraroyales:goblin_mask";
    private static final String HOOKED_GRAPPLER_ID = "ultraroyales:hooked_grappler";
    private static final String DIAMOND_TOTEM_ID = "ultraroyales:diamond_totem";
    private static final String WONDER_PICKAXE_ID = "ultraroyales:wonder_pickaxe";
    private static final String LOKI_SCEPTER_ID = "ultraroyales:loki_scepter";
    private static final String ARCHITECTS_DRILL_ID = "ultraroyales:architects_drill";
    private static final String SCARABS_WRATH_ID = "ultraroyales:scarabs_wrath";
    private static final double GOBLIN_SCALE = 0.7;
    private static final int DIAMOND_TOTEM_COOLDOWN_SECONDS = 300;
    private static final int DIAMOND_TOTEM_EFFECT_TICKS = 200;
    private static final long HOOKED_GRAPPLER_FALL_PROTECT_MS = 5000L;
    private static final int HOOKED_GRAPPLER_COOLDOWN_SECONDS = 3;
    private static final int LOKI_SCEPTER_COOLDOWN_SECONDS = 12;
    private static final int LOKI_BARRAGE_PROJECTILES = 3;
    private static final double LOKI_PROJECTILE_SPEED = 1.7;
    private static final double LOKI_GOOD_PEARL_CHANCE = 0.08;
    private static final double LOKI_PEARL_BLAST_RADIUS = 4.5;
    private static final double LOKI_PEARL_BAD_DAMAGE = 6.0;
    private static final double LOKI_PEARL_GOOD_DAMAGE = 4.0;
    private static final int SCARAB_ROT_DURATION_TICKS = 130;
    private static final int SCARAB_ROT_TICK_INTERVAL = 20;
    private static final int SCARAB_ROT_MAX_STAGE = 5;
    private static final int ARCHITECTS_DRILL_DURATION_TICKS = 100;
    private static final int ARCHITECTS_DRILL_OVERHEAT_SECONDS = 60;
    private static final int ENDERIOUS_COOLDOWN_SECONDS = 8;
    private static final int WONDER_PICKAXE_CAP = 10;
    private static final NamespacedKey WONDER_PICKAXE_BREAKS = new NamespacedKey((Plugin)UltraRoyales.getPlugin(), "wonder_pickaxe_breaks");
    private static final NamespacedKey LOKI_PROJECTILE_DAMAGE = new NamespacedKey((Plugin)UltraRoyales.getPlugin(), "loki_projectile_damage");
    private static final NamespacedKey LOKI_PROJECTILE_OWNER = new NamespacedKey((Plugin)UltraRoyales.getPlugin(), "loki_projectile_owner");
    private static final NamespacedKey PLASMA_PROJECTILE_OWNER = new NamespacedKey((Plugin)UltraRoyales.getPlugin(), "plasma_projectile_owner");
    private static final Set<Enchantment> WONDER_PICKAXE_EXCLUDED = Set.of(Enchantment.BREACH, Enchantment.DENSITY);
    private final Map<UUID, HangingState> hangingPlayers = new HashMap<UUID, HangingState>();
    private final Map<UUID, Long> grapplerFallProtectionUntilMs = new HashMap<UUID, Long>();
    private final Map<UUID, UUID> lokiPearls = new HashMap<UUID, UUID>();
    private final Map<UUID, LokiMode> lokiModes = new HashMap<UUID, LokiMode>();
    private final Map<UUID, Long> architectsDrillActiveUntilTick = new HashMap<UUID, Long>();
    private final Map<UUID, Long> architectsDrillOverheatUntilMs = new HashMap<UUID, Long>();
    private final Map<UUID, Integer> architectsDrillCombo = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> architectsDrillLastHitTick = new HashMap<UUID, Integer>();
    private final Map<UUID, Long> architectsDrillNextStrikeTick = new HashMap<UUID, Long>();
    private final Map<UUID, RotState> scarabRotStates = new HashMap<UUID, RotState>();
    private final List<ZeroGravityZone> zeroGravityZones = new ArrayList<ZeroGravityZone>();
    private final Map<UUID, Integer> zeroGravityAfflicted = new HashMap<UUID, Integer>();

    public BetaLegendaryListener() {
        new BukkitRunnable(){

            public void run() {
                long nowTick = Bukkit.getCurrentTick();
                for (Player player : UltraRoyales.getPlugin().getServer().getOnlinePlayers()) {
                    BetaLegendaryListener.this.updateGoblinMaskScale(player);
                }
                BetaLegendaryListener.this.tickArchitectsDrill(nowTick);
                BetaLegendaryListener.this.tickScarabRot();
                BetaLegendaryListener.this.tickZeroGravityZones();
            }
        }.runTaskTimer((Plugin)UltraRoyales.getPlugin(), 1L, 2L);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onGrapplerUse(PlayerInteractEvent event) {
        if (!this.isRightClick(event.getAction())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!this.hasModel(item, HOOKED_GRAPPLER_ID)) {
            return;
        }
        Player player = event.getPlayer();
        if (this.hangingPlayers.containsKey(player.getUniqueId())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            this.releaseWallCling(player);
            return;
        }
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.hookedGrapplerCooldown);
        if (cooldown > 0) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            player.sendMessage(HydraMessages.cooldown("Hooked Grappler", cooldown));
            return;
        }
        event.setCancelled(true);
        if (this.tryAttachToWall(player)) {
            return;
        }
        RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 24.0, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) {
            player.sendMessage(HydraMessages.error("No surface in range"));
            return;
        }
        Location anchor = hit.getHitPosition().toLocation(player.getWorld());
        Vector pull = anchor.toVector().subtract(player.getLocation().toVector());
        if (pull.lengthSquared() < 0.01) {
            return;
        }
        Vector velocity = pull.normalize().multiply(2.35);
        velocity.setY(Math.max(-0.25, Math.min(1.2, velocity.getY() + 0.22)));
        player.setVelocity(velocity);
        player.getWorld().spawnParticle(Particle.CLOUD, anchor, 14, 0.15, 0.15, 0.15, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.75f);
        CooldownRegistry.setCooldown(player, UltraRoyales.hookedGrapplerCooldown, HOOKED_GRAPPLER_COOLDOWN_SECONDS);
        this.grapplerFallProtectionUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + HOOKED_GRAPPLER_FALL_PROTECT_MS);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onLokiModeSwitch(PlayerInteractEvent event) {
        if (!this.isRightClick(event.getAction())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!this.hasModel(item, LOKI_SCEPTER_ID)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        LokiMode next = this.nextLokiMode(this.lokiModes.getOrDefault(player.getUniqueId(), LokiMode.PEARL));
        this.lokiModes.put(player.getUniqueId(), next);
        player.sendActionBar(Component.text("Loki Scepter: " + next.displayName(), NamedTextColor.LIGHT_PURPLE));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, next == LokiMode.PEARL ? 1.1f : 1.45f);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onLokiUse(PlayerInteractEvent event) {
        if (!this.isRightClick(event.getAction())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!this.hasModel(item, LOKI_SCEPTER_ID)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.lokiScepterCooldown);
        if (cooldown > 0) {
            player.sendMessage(HydraMessages.cooldown("Loki's Scepter", cooldown));
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        LokiMode mode = this.lokiModes.getOrDefault(player.getUniqueId(), LokiMode.PEARL);
        if (mode == LokiMode.BARRAGE) {
            this.launchLokiBarrage(player);
            CooldownRegistry.setCooldown(player, UltraRoyales.lokiScepterCooldown, LOKI_SCEPTER_COOLDOWN_SECONDS);
            return;
        }
        UltraRoyales.getPlugin().getServer().getScheduler().runTask((Plugin)UltraRoyales.getPlugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            EnderPearl pearl = (EnderPearl)player.launchProjectile(EnderPearl.class);
            pearl.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(LOKI_PROJECTILE_SPEED));
            pearl.setGravity(true);
            this.lokiPearls.put(pearl.getUniqueId(), player.getUniqueId());
            pearl.getPersistentDataContainer().set(UltraRoyales.lokiPearlKey, PersistentDataType.BOOLEAN, true);
            pearl.getPersistentDataContainer().set(LOKI_PROJECTILE_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.1f);
        });
        CooldownRegistry.setCooldown(player, UltraRoyales.lokiScepterCooldown, LOKI_SCEPTER_COOLDOWN_SECONDS);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArchitectsDrillUse(PlayerInteractEvent event) {
        if (!this.isRightClick(event.getAction()) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!this.hasModel(event.getItem(), ARCHITECTS_DRILL_ID)) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long overheatUntil = this.architectsDrillOverheatUntilMs.getOrDefault(player.getUniqueId(), 0L);
        if (overheatUntil > now) {
            long seconds = Math.max(1L, (overheatUntil - now + 999L) / 1000L);
            player.sendMessage(HydraMessages.cooldown("Architect's Drill overheat", seconds));
            event.setCancelled(true);
            return;
        }
        this.architectsDrillActiveUntilTick.put(player.getUniqueId(), (long)Bukkit.getCurrentTick() + ARCHITECTS_DRILL_DURATION_TICKS);
        this.architectsDrillCombo.put(player.getUniqueId(), 0);
        this.architectsDrillLastHitTick.remove(player.getUniqueId());
        this.architectsDrillNextStrikeTick.put(player.getUniqueId(), (long)Bukkit.getCurrentTick());
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.7f);
        player.sendMessage(HydraMessages.activated("Architect's Drill"));
        event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArchitectsDrillReach(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.hasModel(player.getInventory().getItemInMainHand(), ARCHITECTS_DRILL_ID)) {
            return;
        }
        long nowTick = Bukkit.getCurrentTick();
        if (this.architectsDrillActiveUntilTick.getOrDefault(player.getUniqueId(), 0L) <= nowTick) {
            return;
        }
        RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 5.0, entity -> entity instanceof LivingEntity && entity != player);
        Entity hitEntity = hit == null ? null : hit.getHitEntity();
        if (!(hitEntity instanceof LivingEntity target)) {
            return;
        }
        if (player.getLocation().distanceSquared(target.getLocation()) <= 11.0) {
            return;
        }
        this.applySnapshotLegendaryDamage(target, 0.5);
        player.swingMainHand();
        event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onEnderiousUse(PlayerInteractEvent event) {
        if (!this.isRightClick(event.getAction()) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!this.isEnderious(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.enderiousCooldown);
        if (cooldown > 0) {
            player.sendMessage(HydraMessages.cooldown("Plasma Distorter", cooldown));
            event.setCancelled(true);
            return;
        }
        Snowball plasma = player.launchProjectile(Snowball.class);
        plasma.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.85D));
        plasma.getPersistentDataContainer().set(PLASMA_PROJECTILE_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
        CooldownRegistry.setCooldown(player, UltraRoyales.enderiousCooldown, ENDERIOUS_COOLDOWN_SECONDS);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0.0, 1.0, 0.0), 22, 0.35, 0.35, 0.35, 0.06);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.65f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 0.65f);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true)
    public void onPlasmaDistorterHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String ownerRaw = projectile.getPersistentDataContainer().get(PLASMA_PROJECTILE_OWNER, PersistentDataType.STRING);
        if (ownerRaw == null) {
            return;
        }
        projectile.getPersistentDataContainer().remove(PLASMA_PROJECTILE_OWNER);
        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException ex) {
            projectile.remove();
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        Location impact = projectile.getLocation();
        World world = impact.getWorld();
        if (world == null) {
            projectile.remove();
            return;
        }
        world.spawnParticle(Particle.END_ROD, impact, 34, 0.55D, 0.35D, 0.55D, 0.05D);
        world.spawnParticle(Particle.ELECTRIC_SPARK, impact, 28, 0.65D, 0.45D, 0.65D, 0.08D);
        world.playSound(impact, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.45f);
        for (Entity entity : world.getNearbyEntities(impact, 3.0D, 2.2D, 3.0D)) {
            if (!(entity instanceof LivingEntity target) || target.isDead() || target.getUniqueId().equals(ownerId)) {
                continue;
            }
            if (owner != null && TeamProtectionService.blocks(owner, target)) {
                continue;
            }
            target.setNoDamageTicks(0);
            if (owner != null) {
                target.damage(5.0D, owner);
            } else {
                target.damage(5.0D);
            }
            Vector push = target.getLocation().toVector().subtract(impact.toVector());
            if (push.lengthSquared() > 0.0001D) {
                target.setVelocity(push.normalize().multiply(0.65D).setY(0.22D));
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 70, 0, false, true, true));
        }
        projectile.remove();
    }

    @EventHandler(ignoreCancelled=true)
    public void onLokiPearlHit(ProjectileHitEvent event) {
        Player player;
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof EnderPearl)) {
            return;
        }
        EnderPearl pearl = (EnderPearl)projectile;
        if (!pearl.getPersistentDataContainer().has(UltraRoyales.lokiPearlKey, PersistentDataType.BOOLEAN)) {
            return;
        }
        UUID ownerId = this.lokiPearls.remove(pearl.getUniqueId());
        Location location = pearl.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        this.spawnLokiFirework(location, ownerId);
        if (ownerId != null && (player = UltraRoyales.getPlugin().getServer().getPlayer(ownerId)) != null) {
            player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 60));
            this.applyLokiPearlRoll(player, location);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack totem = this.findDiamondTotem(player);
        if (totem == null) {
            return;
        }
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.diamondTotemCooldown);
        if (cooldown > 0) {
            event.setCancelled(true);
            player.sendMessage(HydraMessages.cooldown("Diamond Totem", cooldown));
            return;
        }
        CooldownRegistry.setCooldown(player, UltraRoyales.diamondTotemCooldown, DIAMOND_TOTEM_COOLDOWN_SECONDS);
        UltraRoyales.getPlugin().getServer().getScheduler().runTask((Plugin)UltraRoyales.getPlugin(), () -> {
            this.applyDiamondTotemEffects(player);
            this.restoreDiamondTotem(player, totem);
        });
    }

    @EventHandler(ignoreCancelled=true)
    public void onWonderPickaxeBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!this.hasModel(item, WONDER_PICKAXE_ID)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int blocks = meta.getPersistentDataContainer().getOrDefault(WONDER_PICKAXE_BREAKS, PersistentDataType.INTEGER, 0) + 1;
        meta.getPersistentDataContainer().set(WONDER_PICKAXE_BREAKS, PersistentDataType.INTEGER, blocks);
        item.setItemMeta(meta);
        if (blocks % 10 != 0) {
            return;
        }
        ArrayList<Enchantment> available = new ArrayList<Enchantment>();
        for (Enchantment enchantment : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (WONDER_PICKAXE_EXCLUDED.contains(enchantment)) continue;
            available.add(enchantment);
        }
        if (available.isEmpty()) {
            return;
        }
        Enchantment rolled = (Enchantment)available.get((int)(Math.random() * (double)available.size()));
        int nextLevel = Math.min(WONDER_PICKAXE_CAP, item.getEnchantmentLevel(rolled) + 1);
        item.addUnsafeEnchantment(rolled, nextLevel);
        player.sendMessage(HydraMessages.success("Wonder Pickaxe gained " + this.prettyEnchantmentName(rolled) + " " + this.roman(nextLevel)));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBetaLegendaryCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (this.hasModel(weapon, SCARABS_WRATH_ID) && event.getEntity() instanceof LivingEntity target) {
            this.applyScarabRot(player, target);
        }
        if (this.hasModel(weapon, ARCHITECTS_DRILL_ID)) {
            long nowTick = Bukkit.getCurrentTick();
            long activeUntil = this.architectsDrillActiveUntilTick.getOrDefault(player.getUniqueId(), 0L);
            if (activeUntil > nowTick) {
                int combo = this.advanceArchitectsDrillCombo(player.getUniqueId(), nowTick);
                event.setDamage(0.5);
                if (event.getEntity() instanceof LivingEntity target) {
                    target.setNoDamageTicks(Math.min(target.getNoDamageTicks(), Math.max(0, 8 - combo)));
                }
                this.architectsDrillNextStrikeTick.put(player.getUniqueId(), nowTick + (long)this.architectsDrillStrikeIntervalTicks(combo));
                player.setCooldown(Material.DIAMOND_SWORD, Math.max(2, this.architectsDrillStrikeIntervalTicks(combo)));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onLokiProjectileDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework) {
            String owner = firework.getPersistentDataContainer().get(LOKI_PROJECTILE_OWNER, PersistentDataType.STRING);
            if (owner != null && event.getEntity().getUniqueId().toString().equals(owner)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        Double damage = projectile.getPersistentDataContainer().get(LOKI_PROJECTILE_DAMAGE, PersistentDataType.DOUBLE);
        if (damage == null) {
            return;
        }
        String owner = projectile.getPersistentDataContainer().get(LOKI_PROJECTILE_OWNER, PersistentDataType.STRING);
        if (owner != null && event.getEntity().getUniqueId().toString().equals(owner)) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(Math.max(0.1, damage));
        if (event.getEntity() instanceof LivingEntity target) {
            target.setNoDamageTicks(Math.min(target.getNoDamageTicks(), 4));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onScarabRotHealing(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        RotState state = this.scarabRotStates.get(target.getUniqueId());
        if (state == null) {
            return;
        }
        event.setAmount(event.getAmount() * 0.55D);
        target.getWorld().spawnParticle(Particle.DUST_PLUME, target.getLocation().add(0.0D, 1.0D, 0.0D), 5 + state.stage(), 0.25D, 0.25D, 0.25D, 0.01D);
    }

    @EventHandler(ignoreCancelled=true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (this.hasModel(first, GOBLIN_MASK_ID) || this.hasModel(second, GOBLIN_MASK_ID)) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onEnchant(EnchantItemEvent event) {
        if (this.hasModel(event.getItem(), GOBLIN_MASK_ID)) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage(HydraMessages.error("Goblin Mask cannot be enchanted"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        HangingState state = this.hangingPlayers.get(event.getPlayer().getUniqueId());
        if (state == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() || this.isGrounded(player)) {
            this.releaseWallCling(player);
            return;
        }
        player.setGravity(false);
        Location next = player.getLocation();
        Vector velocity = next.toVector().subtract(state.anchor.toVector()).multiply(0.25);
        player.setVelocity(velocity.multiply(-1.0));
        player.setFallDistance(0.0f);
    }

    @EventHandler(ignoreCancelled=true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        long now = System.currentTimeMillis();
        long protectedUntil = this.grapplerFallProtectionUntilMs.getOrDefault(player.getUniqueId(), 0L);
        if (protectedUntil > now) {
            event.setCancelled(true);
            player.setFallDistance(0.0f);
            return;
        }
        if (protectedUntil > 0L) {
            this.grapplerFallProtectionUntilMs.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.clearTransientStates(event.getPlayer());
        this.updateGoblinMaskScale(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.clearTransientStates(event.getPlayer());
        this.updateGoblinMaskScale(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        this.clearTransientStates(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.clearTransientStates(event.getPlayer());
    }

    private void tickArchitectsDrill(long nowTick) {
        ArrayList<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, Long> entry : this.architectsDrillActiveUntilTick.entrySet()) {
            UUID playerId = entry.getKey();
            if (entry.getValue() <= nowTick) {
                expired.add(playerId);
                continue;
            }
            Player player = UltraRoyales.getPlugin().getServer().getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                expired.add(playerId);
                continue;
            }
            long nextStrike = this.architectsDrillNextStrikeTick.getOrDefault(playerId, nowTick);
            if (nextStrike > nowTick) continue;
            LivingEntity target = this.findArchitectsDrillTarget(player);
            if (target == null) {
                this.architectsDrillNextStrikeTick.put(playerId, nowTick + 2L);
                continue;
            }
            if (TeamProtectionService.blocks(player, target)) {
                this.architectsDrillNextStrikeTick.put(playerId, nowTick + 2L);
                continue;
            }
            int combo = this.advanceArchitectsDrillCombo(playerId, nowTick);
            this.applySnapshotLegendaryDamage(target, 0.5);
            Vector push = player.getLocation().getDirection().clone().normalize().multiply(0.2);
            target.setVelocity(target.getVelocity().multiply(0.75).add(push.setY(0.04)));
            player.swingMainHand();
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35f, 1.25f + (float)combo * 0.08f);
            player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 5 + combo, 0.18, 0.18, 0.18, 0.01);
            this.architectsDrillNextStrikeTick.put(playerId, nowTick + (long)this.architectsDrillStrikeIntervalTicks(combo));
            player.setCooldown(Material.DIAMOND_SWORD, Math.max(2, this.architectsDrillStrikeIntervalTicks(combo)));
        }
        for (UUID playerId : expired) {
            this.architectsDrillActiveUntilTick.remove(playerId);
            this.architectsDrillCombo.remove(playerId);
            this.architectsDrillLastHitTick.remove(playerId);
            this.architectsDrillNextStrikeTick.remove(playerId);
            this.architectsDrillOverheatUntilMs.put(playerId, System.currentTimeMillis() + ARCHITECTS_DRILL_OVERHEAT_SECONDS * 1000L);
            Player player = UltraRoyales.getPlugin().getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            player.sendMessage(HydraMessages.warning("Architect's Drill overheated"));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.7f);
        }
    }

    private void tickScarabRot() {
        long nowTick = Bukkit.getCurrentTick();
        ArrayList<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, RotState> entry : this.scarabRotStates.entrySet()) {
            LivingEntity target;
            RotState state;
            UUID targetId = entry.getKey();
            Entity rawTarget = targetId.equals((state = entry.getValue()).attackerId()) ? null : UltraRoyales.getPlugin().getServer().getEntity(targetId);
            target = rawTarget instanceof LivingEntity living ? living : null;
            if (target == null || !target.isValid() || target.isDead()) {
                expired.add(targetId);
                continue;
            }
            if (state.expiresAtTick() <= nowTick) {
                expired.add(targetId);
                continue;
            }
            if (state.nextTickAt() > nowTick) {
                continue;
            }
            double damage = 0.45D + (state.stage() * 0.35D);
            this.applySnapshotLegendaryDamage(target, Math.min(2.25D, damage));
            this.scarabRotStates.put(targetId, new RotState(state.attackerId(), state.expiresAtTick(), state.stage(), nowTick + SCARAB_ROT_TICK_INTERVAL));
            target.getWorld().spawnParticle(Particle.DUST_PLUME, target.getLocation().add(0.0, 0.8, 0.0), 8 + state.stage(), 0.28, 0.28, 0.28, 0.012);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SUSPICIOUS_GRAVEL_BREAK, 0.45f, 0.65f);
        }
        expired.forEach(this.scarabRotStates::remove);
    }

    private void applyScarabRot(Player attacker, LivingEntity target) {
        if (attacker == null || target == null || target.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }
        long nowTick = Bukkit.getCurrentTick();
        RotState previous = this.scarabRotStates.get(target.getUniqueId());
        int nextStage = previous == null ? 1 : Math.min(SCARAB_ROT_MAX_STAGE, previous.stage() + 1);
        this.scarabRotStates.put(
            target.getUniqueId(),
            new RotState(attacker.getUniqueId(), nowTick + SCARAB_ROT_DURATION_TICKS, nextStage, nowTick + SCARAB_ROT_TICK_INTERVAL)
        );
        target.getWorld().spawnParticle(Particle.DUST_PLUME, target.getLocation().add(0.0, 1.0, 0.0), 10 + nextStage, 0.32, 0.42, 0.32, 0.018);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SUSPICIOUS_SAND_BREAK, 0.7f, 0.7f + (nextStage * 0.06f));
    }

    private void applySnapshotLegendaryDamage(LivingEntity target, double damage) {
        if (target == null || target.isDead() || !target.isValid() || damage <= 0.0D) {
            return;
        }
        target.setNoDamageTicks(0);
        target.setLastDamage(damage);
        target.setHealth(Math.max(0.0D, target.getHealth() - damage));
    }

    private void tickZeroGravityZones() {
        long nowTick = Bukkit.getCurrentTick();
        this.zeroGravityZones.removeIf(zone -> zone.expiresAtTick() <= nowTick);
        HashMap<UUID, Integer> touched = new HashMap<UUID, Integer>();
        for (ZeroGravityZone zone2 : this.zeroGravityZones) {
            World world = zone2.center().getWorld();
            if (world == null) continue;
            for (Player player : world.getPlayers()) {
                if (!player.isOnline() || player.isDead() || player.getLocation().distanceSquared(zone2.center()) > 64.0) continue;
                touched.merge(player.getUniqueId(), 1, (a, b) -> a + b);
                player.setGravity(false);
                Vector velocity = player.getVelocity().clone();
                velocity.setX(velocity.getX() * 0.99);
                velocity.setZ(velocity.getZ() * 0.99);
                double floatBias = player.isSneaking() ? -0.025 : 0.018;
                velocity.setY(Math.max(-0.12, Math.min(0.12, velocity.getY() + floatBias)));
                player.setVelocity(velocity);
                player.setFallDistance(0.0f);
            }
        }
        for (UUID playerId : new ArrayList<UUID>(this.zeroGravityAfflicted.keySet())) {
            if (touched.containsKey(playerId)) continue;
            Player player = UltraRoyales.getPlugin().getServer().getPlayer(playerId);
            if (player != null && player.isOnline() && !this.hangingPlayers.containsKey(playerId)) {
                player.setGravity(true);
            }
            this.zeroGravityAfflicted.remove(playerId);
        }
        this.zeroGravityAfflicted.clear();
        this.zeroGravityAfflicted.putAll(touched);
    }

    private void updateGoblinMaskScale(Player player) {
        double target;
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }
        ItemStack helmet = player.getInventory().getHelmet();
        target = this.hasModel(helmet, GOBLIN_MASK_ID) ? GOBLIN_SCALE : 1.0;
        if (Math.abs(scale.getBaseValue() - target) > 0.001) {
            scale.setBaseValue(target);
        }
    }

    private void clearTransientStates(Player player) {
        if (player == null) {
            return;
        }
        this.hangingPlayers.remove(player.getUniqueId());
        this.grapplerFallProtectionUntilMs.remove(player.getUniqueId());
        this.lokiModes.remove(player.getUniqueId());
        this.architectsDrillActiveUntilTick.remove(player.getUniqueId());
        this.architectsDrillCombo.remove(player.getUniqueId());
        this.architectsDrillLastHitTick.remove(player.getUniqueId());
        this.architectsDrillNextStrikeTick.remove(player.getUniqueId());
        this.scarabRotStates.remove(player.getUniqueId());
        this.zeroGravityAfflicted.remove(player.getUniqueId());
        if (!player.hasGravity()) {
            player.setGravity(true);
        }
        player.setFallDistance(0.0f);
    }

    private boolean tryAttachToWall(Player player) {
        if (this.isGrounded(player)) {
            return false;
        }
        double momentum = player.getVelocity().length();
        double clingRange = Math.min(12.0, 2.75 + momentum * 4.5);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), clingRange, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) {
            return false;
        }
        BlockFace face = hit.getHitBlockFace();
        if (face == null || face == BlockFace.UP) {
            return false;
        }
        Location anchor = hit.getHitPosition().toLocation(player.getWorld());
        this.hangingPlayers.put(player.getUniqueId(), new HangingState(anchor));
        this.grapplerFallProtectionUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + HOOKED_GRAPPLER_FALL_PROTECT_MS);
        player.setGravity(false);
        player.setVelocity(new Vector(0.0, 0.0, 0.0));
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.15f);
        return true;
    }

    private void releaseWallCling(Player player) {
        if (player == null) {
            return;
        }
        this.hangingPlayers.remove(player.getUniqueId());
        if (!player.hasGravity()) {
            player.setGravity(true);
        }
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.85f, 1.2f);
    }

    private boolean isGrounded(Player player) {
        if (player == null) {
            return false;
        }
        Location location = player.getLocation();
        Block feet = location.clone().subtract(0.0, 0.1, 0.0).getBlock();
        return feet.isLiquid() || !feet.isPassable();
    }

    private ItemStack findDiamondTotem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (this.hasModel(mainHand, DIAMOND_TOTEM_ID)) {
            return mainHand.clone();
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (this.hasModel(offHand, DIAMOND_TOTEM_ID)) {
            return offHand.clone();
        }
        return null;
    }

    private void restoreDiamondTotem(Player player, ItemStack item) {
        if (!player.isOnline()) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!this.hasModel(mainHand, DIAMOND_TOTEM_ID)) {
            if (offHand == null || offHand.getType() == Material.AIR) {
                player.getInventory().setItemInOffHand(item.clone());
            } else if (mainHand == null || mainHand.getType() == Material.AIR) {
                player.getInventory().setItemInMainHand(item.clone());
            } else {
                player.getInventory().addItem(new ItemStack[]{item.clone()});
            }
        }
    }

    private void applyDiamondTotemEffects(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 2, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 3, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 7, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, true, true));
    }

    private LokiMode nextLokiMode(LokiMode current) {
        return current == LokiMode.PEARL ? LokiMode.BARRAGE : LokiMode.PEARL;
    }

    private void launchLokiBarrage(Player player) {
        Location eye = player.getEyeLocation();
        for (int index = 0; index < LOKI_BARRAGE_PROJECTILES; ++index) {
            LokiProjectile rolled = LokiProjectile.random();
            Vector direction = this.spreadDirection(eye.getDirection(), index - 1);
            Projectile projectile = rolled.launch(player);
            projectile.setVelocity(direction.multiply(LOKI_PROJECTILE_SPEED));
            projectile.setGravity(true);
            projectile.getPersistentDataContainer().set(LOKI_PROJECTILE_DAMAGE, PersistentDataType.DOUBLE, rolled.damage());
            projectile.getPersistentDataContainer().set(LOKI_PROJECTILE_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setDamage(rolled.damage());
            }
            if (projectile instanceof Fireball fireball) {
                fireball.setYield(0.0f);
                fireball.setIsIncendiary(false);
            }
        }
        player.getWorld().spawnParticle(Particle.WITCH, player.getEyeLocation().add(player.getLocation().getDirection()), 18, 0.35, 0.25, 0.35, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.35f);
    }

    private Vector spreadDirection(Vector baseDirection, int spreadIndex) {
        Vector forward = baseDirection.clone();
        if (forward.lengthSquared() < 0.001) {
            forward = new Vector(0.0, 0.0, 1.0);
        }
        forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 0.001) {
            right = new Vector(1.0, 0.0, 0.0);
        } else {
            right.normalize();
        }
        Vector lift = right.clone().crossProduct(forward).normalize();
        return forward.add(right.multiply(spreadIndex * 0.13)).add(lift.multiply(0.035)).normalize();
    }

    private void applyLokiPearlRoll(Player player, Location location) {
        if (Math.random() < LOKI_GOOD_PEARL_CHANCE) {
            this.damageLokiPearlTargets(player, location, LOKI_PEARL_GOOD_DAMAGE, false);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, true, true));
            player.sendMessage(HydraMessages.success("Loki smiled on that pearl."));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.45f);
            player.getWorld().spawnParticle(Particle.END_ROD, location, 18, 0.4, 0.5, 0.4, 0.03);
            return;
        }
        this.damageLokiPearlTargets(player, location, LOKI_PEARL_BAD_DAMAGE, true);
        player.sendMessage(HydraMessages.warning("Loki's pearl erupted."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 0.9f, 0.8f);
        player.getWorld().spawnParticle(Particle.SMOKE, location, 22, 0.45, 0.55, 0.45, 0.02);
    }

    private void damageLokiPearlTargets(Player owner, Location location, double damage, boolean curseTargets) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(location, LOKI_PEARL_BLAST_RADIUS, LOKI_PEARL_BLAST_RADIUS, LOKI_PEARL_BLAST_RADIUS)) {
            if (!(entity instanceof LivingEntity target) || entity.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            if (TeamProtectionService.blocks(owner, target)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(location) > LOKI_PEARL_BLAST_RADIUS * LOKI_PEARL_BLAST_RADIUS) {
                continue;
            }
            target.setNoDamageTicks(0);
            target.damage(damage, (Entity)owner);
            if (curseTargets) {
                this.applyRandomLokiCurse(target);
            }
        }
    }

    private void applyRandomLokiCurse(LivingEntity target) {
        int roll = (int)(Math.random() * 5.0);
        switch (roll) {
            case 0 -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true, true));
            case 1 -> target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
            case 2 -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 0, false, true, true));
            case 3 -> target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, true, true));
            default -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, true, true));
        }
    }

    private void spawnLokiFirework(Location location, UUID ownerId) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Firework firework = (Firework)world.spawn(location.clone().add(0.0, 0.2, 0.0), Firework.class);
        if (ownerId != null) {
            firework.getPersistentDataContainer().set(LOKI_PROJECTILE_OWNER, PersistentDataType.STRING, ownerId.toString());
        }
        FireworkMeta meta = firework.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BURST).withColor(new Color[]{Color.PURPLE, Color.ORANGE, Color.WHITE}).withFade(Color.FUCHSIA).trail(true).flicker(true).build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        UltraRoyales.getPlugin().getServer().getScheduler().runTaskLater((Plugin)UltraRoyales.getPlugin(), () -> ((Firework)firework).detonate(), 1L);
    }

    private boolean hasModel(ItemStack item, String modelId) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent() && meta.getCustomModelDataComponent().getStrings().contains(modelId);
    }

    private boolean isEnderious(ItemStack item) {
        if (item == null || (item.getType() != Material.SNOWBALL && item.getType() != Material.MACE) || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(UltraRoyales.enderiousItemKey, PersistentDataType.BOOLEAN)
            || (meta.hasCustomModelDataComponent() && meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:enderious"));
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private String prettyEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey().replace('_', ' ');
        String[] parts = key.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    private int advanceArchitectsDrillCombo(UUID playerId, long nowTick) {
        int combo = nowTick - (long)this.architectsDrillLastHitTick.getOrDefault(playerId, Integer.MIN_VALUE).intValue() <= 16L ? this.architectsDrillCombo.getOrDefault(playerId, 0) + 1 : 1;
        combo = Math.min(6, combo);
        this.architectsDrillCombo.put(playerId, combo);
        this.architectsDrillLastHitTick.put(playerId, (int)nowTick);
        return combo;
    }

    private int architectsDrillStrikeIntervalTicks(int combo) {
        return switch (Math.max(1, Math.min(6, combo))) {
            case 1 -> 12;
            case 2 -> 10;
            case 3 -> 8;
            case 4 -> 6;
            case 5 -> 5;
            default -> 4;
        };
    }

    private LivingEntity findArchitectsDrillTarget(Player player) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 5.5, 1.15, entity -> entity instanceof LivingEntity && entity != player);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
            return living;
        }
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        Vector facing = player.getEyeLocation().getDirection().normalize();
        for (Entity entity : player.getNearbyEntities(5.5, 2.0, 5.5)) {
            if (!(entity instanceof LivingEntity living) || entity == player || living.isDead()) {
                continue;
            }
            Vector toTarget = living.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
            double distanceSquared = toTarget.lengthSquared();
            if (distanceSquared > 30.25 || distanceSquared >= closestDistance) {
                continue;
            }
            if (facing.dot(toTarget.normalize()) < 0.72) {
                continue;
            }
            closest = living;
            closestDistance = distanceSquared;
        }
        return closest;
    }

    private record ZeroGravityZone(UUID ownerId, Location center, long expiresAtTick) {
    }

    private record RotState(UUID attackerId, long expiresAtTick, int stage, long nextTickAt) {
    }

    private record HangingState(Location anchor) {
    }

    private enum LokiMode {
        PEARL("Pearl"),
        BARRAGE("Spread");

        private final String displayName;

        LokiMode(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return this.displayName;
        }
    }

    private enum LokiProjectile {
        ARROW(Arrow.class, 5.0),
        WITHER_SKULL(WitherSkull.class, 7.0),
        BLUE_WITHER_SKULL(WitherSkull.class, 5.5),
        FIREBALL(Fireball.class, 6.0),
        SNOWBALL(Snowball.class, 3.0),
        TRIDENT(Trident.class, 6.5),
        EGG(Egg.class, 2.5);

        private static final LokiProjectile[] VALUES = values();
        private final Class<? extends Projectile> projectileClass;
        private final double damage;

        LokiProjectile(Class<? extends Projectile> projectileClass, double damage) {
            this.projectileClass = projectileClass;
            this.damage = damage;
        }

        private static LokiProjectile random() {
            return VALUES[(int)(Math.random() * VALUES.length)];
        }

        private Projectile launch(Player player) {
            Projectile projectile = player.launchProjectile(this.projectileClass);
            if (this == BLUE_WITHER_SKULL && projectile instanceof WitherSkull skull) {
                skull.setCharged(true);
            }
            return projectile;
        }

        private double damage() {
            return this.damage;
        }

    }
}
