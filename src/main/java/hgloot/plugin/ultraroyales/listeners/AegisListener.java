package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.TeamProtectionService;
import hgloot.plugin.ultraroyales.UltraRoyales;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public class AegisListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:aegis";
    private static final double REFLECT_RATIO = 2.0D;
    private static final long BLOCK_WINDOW_MS = 900L;
    private static final Map<UUID, Long> activeBlockUntil = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onAegisUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isAegis(item)) {
            return;
        }

        Player player = event.getPlayer();
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.aegisCooldown);
        if (cooldown > 0) {
            event.setCancelled(true);
            player.sendActionBar(HydraMessages.cooldown("Aegis", cooldown));
            return;
        }

        event.setCancelled(true);
        activeBlockUntil.put(player.getUniqueId(), System.currentTimeMillis() + BLOCK_WINDOW_MS);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.15f);
        player.sendActionBar(HydraMessages.activated("Aegis"));
    }

    @EventHandler
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        if (!isHoldingAegis(defender)) {
            return;
        }

        int cooldown = CooldownRegistry.getCooldown(defender, UltraRoyales.aegisCooldown);
        if (cooldown > 0) {
            return;
        }

        long activeUntil = activeBlockUntil.getOrDefault(defender.getUniqueId(), 0L);
        if (activeUntil < System.currentTimeMillis()) {
            return;
        }

        double reflectedDamage = Math.max(4.0D, event.getFinalDamage() * REFLECT_RATIO);
        Location center = defender.getLocation();
        int hitCount = 0;
        Set<UUID> damagedTargets = new HashSet<>();

        LivingEntity directAttacker = resolveDirectAttacker(event.getDamager(), defender);
        if (directAttacker != null && TeamProtectionService.blocks(defender, directAttacker)) {
            return;
        }
        if (directAttacker != null) {
            directAttacker.setNoDamageTicks(0);
            directAttacker.damage(reflectedDamage, defender);
            Vector knockback = directAttacker.getLocation().toVector().subtract(center.toVector());
            if (knockback.lengthSquared() > 0.0001D) {
                directAttacker.setVelocity(knockback.normalize().multiply(0.5D).setY(0.25D));
            }
            damagedTargets.add(directAttacker.getUniqueId());
            hitCount++;
        }

        if (hitCount <= 0) {
            return;
        }

        event.setCancelled(true);
        activeBlockUntil.remove(defender.getUniqueId());
        CooldownRegistry.setCooldown(defender, UltraRoyales.aegisCooldown, 120, false);
        defender.getWorld().spawnParticle(Particle.TRIAL_OMEN, center.add(0.0D, 1.0D, 0.0D), 50, 1.0D, 0.7D, 1.0D, 0.01D);
        defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.25f, 0.85f);
        defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.25f);
    }

    private boolean isHoldingAegis(Player player) {
        return isAegis(player.getInventory().getItemInMainHand())
            || isAegis(player.getInventory().getItemInOffHand());
    }

    private boolean isAegis(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }

    private LivingEntity resolveDirectAttacker(Entity damager, Player defender) {
        LivingEntity attacker = null;
        if (damager instanceof LivingEntity living) {
            attacker = living;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            attacker = shooter;
        }

        if (attacker == null || attacker.getUniqueId().equals(defender.getUniqueId()) || attacker.isDead()) {
            return null;
        }
        return attacker;
    }
}
