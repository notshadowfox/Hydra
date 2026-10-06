package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.TeamProtectionService;
import hgloot.plugin.ultraroyales.LegendaryModeRules;
import hgloot.plugin.ultraroyales.UltraRoyales;
import hgloot.plugin.util.HydraIcons;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class ElectricConchListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:electricconch";
    private static final Set<String> MODEL_IDS = Set.of(
        MODEL_ID,
        "ultraroyales:electric_conch",
        "ultraroyales:electricconch_futuristic",
        "ultraroyales:electric_conch_futuristic",
        "ultraroyales:electricconch_obsidian",
        "ultraroyales:electric_conch_obsidian"
    );
    private static final int MAX_CHARGES = 2;
    private static final double AURA_RADIUS = 7.0D;
    private static final double AURA_RADIUS_SQUARED = AURA_RADIUS * AURA_RADIUS;
    private static final int AURA_DURATION_TICKS = 100;
    private static final int AURA_TICK_INTERVAL = 5;
    private static final long TARGET_ZAP_COOLDOWN_MS = 1000L;
    private static final long INTER_CHARGE_DELAY_MS = 4000L;
    private static final double KNOCKBACK = 1.55D;
    private static final double LIGHTNING_TRUE_DAMAGE = 3.0D;
    private static final double HUNGER_GAMES_TRUE_DAMAGE = 2.0D;
    private static final Map<UUID, Integer> charges = new HashMap<>();
    private static final Map<UUID, Long> nextChargeUseMs = new HashMap<>();
    private static final Map<UUID, BukkitTask> activeAuraTasks = new HashMap<>();
    private static final Set<UUID> applyingElectricDamage = new HashSet<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack held = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (!isElectricConch(held)) {
            return;
        }
        event.setCancelled(true);

        if (hand == EquipmentSlot.OFF_HAND) {
            UltraRoyales.holdBossRushDamageActionBar(player, 2000L);
            player.sendActionBar(Component.text("Use Electric Conch from your main hand.", NamedTextColor.AQUA));
            return;
        }

        UUID playerId = player.getUniqueId();
        int rechargeCooldown = CooldownRegistry.getCooldown(player, UltraRoyales.conchCooldown);
        if (rechargeCooldown > 0) {
            charges.put(playerId, 0);
            showConchHud(player, 0, rechargeCooldown);
            return;
        }

        int current = charges.getOrDefault(playerId, MAX_CHARGES);
        if (current <= 0) {
            // Recover from stale state (e.g., after plugin reload) once cooldown is clear.
            current = MAX_CHARGES;
            charges.put(playerId, MAX_CHARGES);
        }

        long now = System.currentTimeMillis();
        long readyAt = nextChargeUseMs.getOrDefault(playerId, 0L);
        if (now < readyAt) {
            int seconds = Math.max(1, (int) Math.ceil((readyAt - now) / 1000.0D));
            showConchHud(player, current, seconds);
            return;
        }

        double knockbackMultiplier = current >= 2 ? 1.30D : 1.0D;
        current--;
        charges.put(playerId, current);
        emitPulse(player, knockbackMultiplier);

        if (current <= 0) {
            nextChargeUseMs.remove(playerId);
            CooldownRegistry.setCooldown(player, UltraRoyales.conchCooldown, 30, false);
            showConchHud(player, 0, 30);
            player.sendMessage(HydraMessages.warning("Electric Conch depleted. Full recharge started"));
            player.getServer().getScheduler().runTaskLater(UltraRoyales.getPlugin(), () -> {
                if (player.isOnline()) {
                    charges.put(playerId, MAX_CHARGES);
                    nextChargeUseMs.remove(playerId);
                    player.sendMessage(HydraMessages.ready("Electric Conch"));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.35f);
                }
            }, 20L * 30L);
        } else {
            nextChargeUseMs.put(playerId, now + INTER_CHARGE_DELAY_MS);
            showConchHud(player, current, (int) (INTER_CHARGE_DELAY_MS / 1000L));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelActiveAura(event.getPlayer().getUniqueId());
        nextChargeUseMs.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)) {
            return;
        }
        if (isElectricConch(player.getInventory().getItemInMainHand())
            || isElectricConch(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            trident.remove();
            player.updateInventory();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target instanceof Player player && !isCombatTarget(player)) {
            return;
        }
        if (applyingElectricDamage.contains(target.getUniqueId())) {
            return;
        }
        if (!LegendaryModeRules.isHungerGamesWorld(attacker.getWorld())) {
            return;
        }
        if (!isElectricConch(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        applyElectricDamage(attacker, target, HUNGER_GAMES_TRUE_DAMAGE);
        spawnElectricParticles(target.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.25D, 0.3D, 0.25D, 0.02D);
    }

    private void emitPulse(Player player, double knockbackMultiplier) {
        startStormAura(player, knockbackMultiplier);
    }

    private static void showConchHud(Player player, int currentCharges, int cooldownSeconds) {
        int clampedCharges = Math.max(0, Math.min(MAX_CHARGES, currentCharges));
        int waterBarEquivalent = clampedCharges * 6;
        Component hud = HydraIcons.spongeSaberWaterBar(waterBarEquivalent);
        if (cooldownSeconds > 0) {
            hud = hud.append(Component.text("  CD " + cooldownSeconds + "s", NamedTextColor.AQUA));
        }
        UltraRoyales.holdBossRushDamageActionBar(player, 1500L);
        player.sendActionBar(hud);
    }

    private void startStormAura(Player owner, double knockbackMultiplier) {
        UUID ownerId = owner.getUniqueId();
        cancelActiveAura(ownerId);

        World auraWorld = owner.getWorld();
        Location start = owner.getLocation();
        spawnElectricParticles(start.clone().add(0.0D, 1.0D, 0.0D), 120, 1.8D, 0.9D, 1.8D, 0.09D);
        auraWorld.spawnParticle(Particle.ELECTRIC_SPARK, start.clone().add(0.0D, 1.0D, 0.0D), 18, 0.45D, 0.35D, 0.45D, 0.03D);
        auraWorld.playSound(start, Sound.ITEM_TRIDENT_THUNDER, 1.55f, 0.95f);
        auraWorld.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.35f, 0.85f);
        auraWorld.playSound(start, Sound.BLOCK_BEACON_DEACTIVATE, 0.75f, 1.65f);
        if (knockbackMultiplier > 1.0D) {
            auraWorld.spawnParticle(Particle.END_ROD, start.clone().add(0.0D, 1.0D, 0.0D), 12, 0.15D, 0.1D, 0.15D, 0.01D);
            auraWorld.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.55f, 1.6f);
        }

        BukkitRunnable aura = new BukkitRunnable() {
            private final Map<UUID, Long> nextZapAt = new HashMap<>();
            private int ticks;

            @Override
            public void run() {
                if (!owner.isOnline() || owner.isDead() || owner.getWorld() != auraWorld || ticks >= AURA_DURATION_TICKS) {
                    activeAuraTasks.remove(ownerId);
                    cancel();
                    return;
                }
                ticks += AURA_TICK_INTERVAL;

                Location center = owner.getLocation();
                drawAuraRing(center);
                spawnElectricParticles(center.clone().add(0.0D, 1.0D, 0.0D), 24, 0.55D, 0.45D, 0.55D, 0.025D);

                long now = System.currentTimeMillis();
                Set<UUID> processedTargets = new HashSet<>();
                for (Player target : Bukkit.getOnlinePlayers()) {
                    UUID targetId = target.getUniqueId();
                    if (targetId.equals(ownerId) || !isCombatTarget(target) || target.getWorld() != auraWorld) {
                        continue;
                    }
                    if (!isInsideAura(center, target.getLocation())) {
                        continue;
                    }
                    if (now < nextZapAt.getOrDefault(targetId, 0L)) {
                        continue;
                    }

                    processedTargets.add(targetId);
                    nextZapAt.put(targetId, now + TARGET_ZAP_COOLDOWN_MS);
                    zapTarget(owner, target, knockbackMultiplier);
                }

                for (Entity entity : auraWorld.getNearbyEntities(center, AURA_RADIUS, AURA_RADIUS, AURA_RADIUS)) {
                    if (!(entity instanceof LivingEntity target) || target instanceof Player || target.isDead()) {
                        continue;
                    }
                    UUID targetId = target.getUniqueId();
                    if (targetId.equals(ownerId) || processedTargets.contains(targetId)) {
                        continue;
                    }
                    if (!isInsideAura(center, target.getLocation())) {
                        continue;
                    }
                    if (now < nextZapAt.getOrDefault(targetId, 0L)) {
                        continue;
                    }

                    nextZapAt.put(targetId, now + TARGET_ZAP_COOLDOWN_MS);
                    zapTarget(owner, target, knockbackMultiplier);
                }
            }
        };
        activeAuraTasks.put(ownerId, aura.runTaskTimer(UltraRoyales.getPlugin(), 0L, AURA_TICK_INTERVAL));
    }

    private boolean isInsideAura(Location center, Location target) {
        if (center == null || target == null || center.getWorld() != target.getWorld()) {
            return false;
        }
        double dx = target.getX() - center.getX();
        double dy = Math.abs(target.getY() - center.getY());
        double dz = target.getZ() - center.getZ();
        return dy <= AURA_RADIUS && (dx * dx) + (dz * dz) <= AURA_RADIUS_SQUARED;
    }

    private void zapTarget(Player owner, LivingEntity target, double knockbackMultiplier) {
        if (target instanceof Player player && !isCombatTarget(player)) {
            return;
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, true, true), true);
        spawnElectricParticles(target.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.35D, 0.45D, 0.35D, 0.025D);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.25D, 0.35D, 0.25D, 0.02D);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.65f, 1.45f);
        if (TeamProtectionService.blocks(owner, target)) return;
        applyElectricDamage(owner, target, LIGHTNING_TRUE_DAMAGE);

        Vector away = target.getLocation().toVector().subtract(owner.getLocation().toVector());
        if (away.lengthSquared() > 0.0001D) {
            target.setVelocity(away.normalize().multiply(KNOCKBACK * knockbackMultiplier).setY(0.32D));
        }
    }

    private boolean isCombatTarget(Player player) {
        return player != null
            && player.isOnline()
            && !player.isDead()
            && player.getGameMode() != GameMode.SPECTATOR
            && !player.isInvulnerable();
    }

    private void drawAuraRing(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 229, 255), 1.1F);
        int points = 42;
        double y = center.getY() + 0.18D;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            Location point = new Location(
                world,
                center.getX() + (Math.cos(angle) * AURA_RADIUS),
                y,
                center.getZ() + (Math.sin(angle) * AURA_RADIUS)
            );
            world.spawnParticle(Particle.DUST, point, 1, 0.02D, 0.02D, 0.02D, 0.0D, dust, true);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.035D, 0.035D, 0.035D, 0.0D);
            }
        }
    }

    private void cancelActiveAura(UUID ownerId) {
        BukkitTask existing = activeAuraTasks.remove(ownerId);
        if (existing != null) {
            existing.cancel();
        }
    }

    private void applyElectricDamage(Player source, LivingEntity target, double amount) {
        if (source == null || target == null || target.isDead() || amount <= 0.0D) {
            return;
        }
        double beforeHealth = target.getHealth();
        target.setNoDamageTicks(0);
        applyingElectricDamage.add(target.getUniqueId());
        try {
            target.damage(amount, source);
        } finally {
            applyingElectricDamage.remove(target.getUniqueId());
        }
        if (target.isDead() || target.getHealth() < beforeHealth - 0.001D) {
            return;
        }
        if (!LegendaryModeRules.isHungerGamesWorld(source.getWorld()) || source.getWorld() != target.getWorld()) {
            return;
        }
        target.setNoDamageTicks(0);
        target.setLastDamage(amount);
        target.setHealth(Math.max(0.0D, beforeHealth - amount));
    }

    private void spawnElectricParticles(Location location, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 229, 255), 1.15F);
        world.spawnParticle(Particle.DUST, location, Math.max(3, count / 3), offsetX, offsetY, offsetZ, speed, dust);
        world.spawnParticle(Particle.END_ROD, location, Math.max(2, count / 4), offsetX * 0.65D, offsetY * 0.65D, offsetZ * 0.65D, speed * 0.6D);
    }

    private boolean isElectricConch(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.TRIDENT || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelDataComponent()) {
            List<String> models = meta.getCustomModelDataComponent().getStrings();
            for (String model : models) {
                if (MODEL_IDS.contains(model)) {
                    return true;
                }
            }
        }
        if (meta.hasItemModel()) {
            NamespacedKey itemModel = meta.getItemModel();
            if (itemModel != null && MODEL_IDS.contains(itemModel.asString())) {
                return true;
            }
        }
        return meta.hasDisplayName() && "Electric Conch".equals(org.bukkit.ChatColor.stripColor(meta.getDisplayName()));
    }
}
