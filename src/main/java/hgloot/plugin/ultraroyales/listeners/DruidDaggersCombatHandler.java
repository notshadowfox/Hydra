package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.QueueService;
import hgloot.plugin.ultraroyales.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class DruidDaggersCombatHandler implements Listener {
    private static final String MODEL_ID = "ultraroyales:druids_daggers";
    private static final double DAGGER_DAMAGE = 5.0D;
    private static final double VANILLA_DIAMOND_SWORD_DAMAGE = 7.0D;
    private static final float ECHO_MIN_ATTACK_STRENGTH = 0.9F;
    private static final int REVERSED_EFFECT_MAX_TICKS = 200;
    private static final double FALLBACK_REACH = 3.0D;
    private static final double RAY_SIZE = 0.35D;

    private final HGLootPlugin plugin;
    private final NamespacedKey boundOffhandKey;
    private final Map<UUID, Long> lastAttackTick = new HashMap<>();
    private final Map<UUID, AttackHand> lastAttackHand = new HashMap<>();
    private final Set<UUID> dispatchingOffhandDamage = new HashSet<>();
    private final BukkitTask offhandSyncTask;

    public DruidDaggersCombatHandler(HGLootPlugin plugin) {
        this.plugin = plugin;
        this.boundOffhandKey = new NamespacedKey(plugin, "druids_bound_offhand");
        this.offhandSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                syncDruidOffhand(player);
            }
        }, 1L, 2L);
        validateDaggerModelMapping();
    }

    public void shutdown() {
        offhandSyncTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBoundDruidDagger(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
        }
        lastAttackTick.clear();
        lastAttackHand.clear();
        dispatchingOffhandDamage.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDruidDaggersHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !ensureCompletePair(player)) {
            return;
        }
        QueueService queueService = plugin.queueService();
        if (queueService == null || !queueService.canUseLegendaryCombat(player)) {
            debug(player, "offhand_strike_rejected", "reason=match_state source=melee");
            return;
        }
        if (event.getEntity() instanceof Player target
            && queueService.isProtectedMatchTeammate(player.getUniqueId(), target.getUniqueId())) {
            event.setCancelled(true);
            debug(player, "offhand_strike_rejected", "reason=team_protection target=" + target.getName());
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean offhandDispatch = dispatchingOffhandDamage.contains(playerId);
        long currentTick = Bukkit.getCurrentTick();
        if (!offhandDispatch && lastAttackTick.getOrDefault(playerId, Long.MIN_VALUE) == currentTick) {
            event.setCancelled(true);
            debug(player, "duplicate_interaction_suppressed", "source=main_hand");
            return;
        }
        if (!offhandDispatch) {
            rememberAttack(playerId, currentTick, AttackHand.MAIN_HAND);
        }

        float attackStrength = player.getAttackCooldown();
        double scaledDamage = event.getDamage() * (DAGGER_DAMAGE / VANILLA_DIAMOND_SWORD_DAMAGE);
        event.setDamage(scaledDamage);
        if (event.getEntity() instanceof LivingEntity target) {
            reversePotionEffects(target);
            if (attackStrength >= ECHO_MIN_ATTACK_STRENGTH) {
                scheduleDruidEcho(player, target, scaledDamage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!ensureCompletePair(player)) {
            logIncompletePair(player);
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity target)
            || !isLegalTarget(player, target)
            || !isWithinMeleeReach(player, target)
            || !player.hasLineOfSight(target)) {
            return;
        }
        event.setCancelled(true);
        attemptOffhandStrike(player, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isRightClick(event.getAction())) {
            return;
        }
        Player player = event.getPlayer();
        if (!ensureCompletePair(player)) {
            logIncompletePair(player);
            return;
        }

        LivingEntity target = findOffhandTarget(player);
        boolean airAttempt = event.getAction() == Action.RIGHT_CLICK_AIR;
        if (target == null && !airAttempt) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (target != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
        attemptOffhandStrike(player, target);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncDruidOffhand(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isBoundDruidDagger(event.getOffHandItem()) || isBoundDruidDagger(event.getMainHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean boundOffhand = isBoundDruidDagger(player.getInventory().getItemInOffHand());
        boolean boundInteraction = event.getRawSlot() == 45
            || isBoundDruidDagger(current)
            || isBoundDruidDagger(cursor)
            || event.getClick() == ClickType.SWAP_OFFHAND
            || event.getClick() == ClickType.DOUBLE_CLICK && boundOffhand;
        if (boundInteraction && (boundOffhand || isBoundDruidDagger(current) || isBoundDruidDagger(cursor))) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> syncDruidOffhand(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && event.getRawSlots().contains(45)
            && isBoundDruidDagger(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isBoundDruidDagger(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> syncDruidOffhand(event.getPlayer()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearPlayerState(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        clearPlayerState(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> syncDruidOffhand(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearPlayerState(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> syncDruidOffhand(event.getPlayer()));
    }

    private void attemptOffhandStrike(Player player, LivingEntity target) {
        QueueService queueService = plugin.queueService();
        if (queueService == null || !queueService.canUseLegendaryCombat(player)) {
            debug(player, "offhand_strike_rejected", "reason=match_state");
            return;
        }
        if (!hasCompletePair(player)) {
            debug(player, "offhand_strike_rejected", "reason=invalid_equipment");
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTick = Bukkit.getCurrentTick();
        if (lastAttackTick.getOrDefault(playerId, Long.MIN_VALUE) == currentTick) {
            debug(player, "duplicate_interaction_suppressed", "source=offhand");
            return;
        }
        if (target instanceof Player targetPlayer
            && queueService.isProtectedMatchTeammate(playerId, targetPlayer.getUniqueId())) {
            debug(player, "offhand_strike_rejected", "reason=team_protection target=" + targetPlayer.getName());
            return;
        }

        rememberAttack(playerId, currentTick, AttackHand.OFF_HAND);
        player.swingOffHand();
        if (target == null) {
            player.resetCooldown();
            debug(player, "offhand_strike_accepted", "target=none");
            return;
        }

        dispatchingOffhandDamage.add(playerId);
        try {
            player.attack(target);
        } finally {
            dispatchingOffhandDamage.remove(playerId);
        }
        target.getWorld().spawnParticle(
            Particle.CRIT,
            target.getLocation().add(0.0D, target.getHeight() * 0.55D, 0.0D),
            4,
            0.12D,
            0.18D,
            0.12D,
            0.01D
        );
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.55F, 1.2F);
        debug(player, "offhand_strike_accepted", "target=" + describe(target));
    }

    private LivingEntity findOffhandTarget(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < 0.0001D) {
            return null;
        }
        double reach = resolveReach(player);
        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
            eye,
            direction,
            reach,
            RAY_SIZE,
            entity -> isLegalTarget(player, entity)
        );
        if (entityHit == null || !(entityHit.getHitEntity() instanceof LivingEntity target)
            || entityHit.getHitPosition() == null) {
            return null;
        }
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
            eye,
            direction,
            reach,
            FluidCollisionMode.NEVER,
            true
        );
        if (blockHit != null && blockHit.getHitPosition() != null) {
            double blockDistance = blockHit.getHitPosition().distanceSquared(eye.toVector());
            double entityDistance = entityHit.getHitPosition().distanceSquared(eye.toVector());
            if (blockDistance <= entityDistance) {
                return null;
            }
        }
        return target;
    }

    private boolean isLegalTarget(Player attacker, Entity entity) {
        if (!(entity instanceof LivingEntity target) || target.isDead() || !target.isValid()
            || entity.getUniqueId().equals(attacker.getUniqueId())) {
            return false;
        }
        if (!(target instanceof Player targetPlayer)) {
            return true;
        }
        QueueService queueService = plugin.queueService();
        return queueService != null && queueService.isLegalLegendaryCombatTarget(attacker, targetPlayer);
    }

    private boolean isWithinMeleeReach(Player player, LivingEntity target) {
        double reach = resolveReach(player) + Math.max(0.25D, target.getWidth() * 0.5D);
        return player.getEyeLocation().distanceSquared(target.getEyeLocation()) <= reach * reach;
    }

    private double resolveReach(Player player) {
        AttributeInstance reach = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        return reach == null ? FALLBACK_REACH : Math.max(1.0D, reach.getValue());
    }

    private void scheduleDruidEcho(Player attacker, LivingEntity target, double damage) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!attacker.isOnline() || attacker.isDead() || target.isDead() || !target.isValid()) {
                return;
            }
            applySnapshotLegendaryDamage(target, damage);
            target.getWorld().spawnParticle(
                Particle.CRIT,
                target.getLocation().add(0.0D, 1.0D, 0.0D),
                10,
                0.2D,
                0.2D,
                0.2D,
                0.01D
            );
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.35F);
        }, 1L);
    }

    private void syncDruidOffhand(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean holdingMainDagger = hasModel(mainHand, MODEL_ID) && !isBoundDruidDagger(mainHand);
        if (holdingMainDagger) {
            if (!isBoundDruidDagger(offHand)) {
                if (offHand != null && offHand.getType() != Material.AIR) {
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(offHand.clone());
                    for (ItemStack overflowItem : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                    }
                }
                player.getInventory().setItemInOffHand(createBoundOffhandDagger());
            }
            return;
        }
        if (isBoundDruidDagger(offHand)) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }
    }

    private ItemStack createBoundOffhandDagger() {
        ItemStack offhand = Items.druidsDaggers();
        ItemMeta meta = offhand.getItemMeta();
        meta.customName(Component.text("Druid's Dagger", NamedTextColor.GREEN));
        meta.getPersistentDataContainer().set(boundOffhandKey, PersistentDataType.BOOLEAN, true);
        meta.setEnchantmentGlintOverride(true);
        offhand.setItemMeta(meta);
        return offhand;
    }

    private boolean hasCompletePair(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return hasModel(mainHand, MODEL_ID)
            && !isBoundDruidDagger(mainHand)
            && hasModel(offHand, MODEL_ID)
            && isBoundDruidDagger(offHand);
    }

    private boolean ensureCompletePair(Player player) {
        if (hasCompletePair(player)) {
            return true;
        }
        syncDruidOffhand(player);
        return hasCompletePair(player);
    }

    private boolean hasModel(ItemStack item, String modelId) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(modelId)) {
            return true;
        }
        NamespacedKey itemModel = meta.hasItemModel() ? meta.getItemModel() : null;
        return itemModel != null && modelId.equals(itemModel.asString());
    }

    private boolean isBoundDruidDagger(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(boundOffhandKey, PersistentDataType.BOOLEAN);
    }

    private void reversePotionEffects(LivingEntity target) {
        List<PotionEffect> replacements = new ArrayList<>();
        boolean changed = false;
        for (PotionEffect effect : target.getActivePotionEffects()) {
            PotionEffectType opposite = reversePotionEffectType(effect.getType());
            if (opposite == null) {
                continue;
            }
            target.removePotionEffect(effect.getType());
            replacements.add(new PotionEffect(
                opposite,
                Math.min(effect.getDuration(), REVERSED_EFFECT_MAX_TICKS),
                effect.getAmplifier(),
                true,
                true,
                true
            ));
            changed = true;
        }
        for (PotionEffect replacement : replacements) {
            target.addPotionEffect(replacement);
        }
        if (changed) {
            target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0.0D, 1.0D, 0.0D), 8, 0.35D, 0.45D, 0.35D, 0.02D);
        }
    }

    private PotionEffectType reversePotionEffectType(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return PotionEffectType.SLOWNESS;
        if (type == PotionEffectType.SLOWNESS) return PotionEffectType.SPEED;
        if (type == PotionEffectType.STRENGTH) return PotionEffectType.WEAKNESS;
        if (type == PotionEffectType.RESISTANCE) return PotionEffectType.WEAKNESS;
        if (type == PotionEffectType.WEAKNESS) return PotionEffectType.STRENGTH;
        if (type == PotionEffectType.REGENERATION) return PotionEffectType.POISON;
        if (type == PotionEffectType.POISON) return PotionEffectType.REGENERATION;
        if (type == PotionEffectType.HASTE) return PotionEffectType.MINING_FATIGUE;
        if (type == PotionEffectType.MINING_FATIGUE) return PotionEffectType.HASTE;
        if (type == PotionEffectType.JUMP_BOOST) return PotionEffectType.SLOWNESS;
        return null;
    }

    private void applySnapshotLegendaryDamage(LivingEntity target, double damage) {
        if (target == null || target.isDead() || !target.isValid() || damage <= 0.0D) {
            return;
        }
        target.setNoDamageTicks(0);
        target.setLastDamage(damage);
        target.setHealth(Math.max(0.0D, target.getHealth() - damage));
    }

    private void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        lastAttackTick.remove(playerId);
        lastAttackHand.remove(playerId);
        dispatchingOffhandDamage.remove(playerId);
    }

    private void rememberAttack(UUID playerId, long currentTick, AttackHand hand) {
        lastAttackTick.put(playerId, currentTick);
        lastAttackHand.put(playerId, hand);
    }

    private void validateDaggerModelMapping() {
        ItemStack item = Items.druidsDaggers();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasItemModel()
            && new NamespacedKey("ultraroyales", "druids_daggers").equals(meta.getItemModel())) {
            plugin.getLogger().log(Level.FINE, "[DruidDaggers] event=dagger_model_mapping_resolved model=" + MODEL_ID);
            return;
        }
        plugin.getLogger().warning("[DruidDaggers] event=dagger_model_mapping_missing model=" + MODEL_ID);
    }

    private void logIncompletePair(Player player) {
        boolean mainDagger = hasModel(player.getInventory().getItemInMainHand(), MODEL_ID);
        boolean offhandDagger = hasModel(player.getInventory().getItemInOffHand(), MODEL_ID);
        if (mainDagger || offhandDagger) {
            debug(player, "offhand_strike_rejected", "reason=invalid_equipment");
        }
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private String describe(LivingEntity target) {
        return target instanceof Player player ? player.getName() : target.getType().getKey().asString();
    }

    private void debug(Player player, String event, String details) {
        plugin.getLogger().log(
            Level.FINE,
            "[DruidDaggers] event=" + event
                + " player=" + (player == null ? "unknown" : player.getName())
                + (details == null || details.isBlank() ? "" : " " + details)
        );
    }

    private enum AttackHand {
        MAIN_HAND,
        OFF_HAND
    }
}
