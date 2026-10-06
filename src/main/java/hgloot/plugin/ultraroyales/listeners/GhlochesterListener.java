package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.TeamProtectionService;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class GhlochesterListener implements Listener {

    private static final double COOLDOWN_SECONDS = 12.0D;
    private static final long HOOK_MARK_DURATION_MS = 20_000L;
    private static final int NORMAL_DEBUFF_TICKS = 60;
    private static final int DEEP_DEBUFF_TICKS = 60;
    private static final double NORMAL_PULL_STRENGTH = 0.65D;
    private static final double DEEP_PULL_STRENGTH = 0.9D;
    private static final double DEEP_TRUE_DAMAGE = 1.5D;
    private static final Map<UUID, HookMark> hookMarks = new HashMap<>();

    @EventHandler
    public void onBobberHook(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (isGhlochester(player.getInventory().getItemInMainHand())
                || isGhlochester(player.getInventory().getItemInOffHand())) {
                event.getHook().setVelocity(event.getHook().getVelocity().multiply(1.55D));
                event.getHook().getWorld().spawnParticle(Particle.CRIT, event.getHook().getLocation(), 6, 0.05D, 0.05D, 0.05D, 0.01D);
            }
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        if (!(event.getCaught() instanceof Player target)) {
            return;
        }
        if (player.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        if (!isGhlochester(player.getInventory().getItemInMainHand())
            && !isGhlochester(player.getInventory().getItemInOffHand())) {
            return;
        }

        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.ghlochesterCooldown);
        if (cooldown > 0) {
            player.sendMessage(HydraMessages.cooldown("Ghlochester", cooldown));
            return;
        }

        long now = System.currentTimeMillis();
        purgeExpiredMarks(now);

        UUID attackerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        HookMark mark = hookMarks.get(attackerId);
        boolean deepHook = mark != null && !mark.isExpired(now) && mark.targetId().equals(targetId);

        if (deepHook) {
            applyDeepHook(player, target);
            hookMarks.remove(attackerId);
        } else {
            applyNormalHook(player, target);
            hookMarks.put(attackerId, new HookMark(targetId, now + HOOK_MARK_DURATION_MS));
        }

        CooldownRegistry.setCooldown(player, UltraRoyales.ghlochesterCooldown, COOLDOWN_SECONDS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearMarksForPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearMarksForPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearMarksForPlayer(event.getPlayer().getUniqueId());
    }

    private void applyNormalHook(Player player, Player target) {
        if (TeamProtectionService.blocks(player, target)) {
            return;
        }
        applyHookPull(player, target, NORMAL_PULL_STRENGTH);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, NORMAL_DEBUFF_TICKS, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, true, true));
        spawnHookParticles(target, false);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0F, 0.75F);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.85F, 0.8F);
    }

    private void applyDeepHook(Player player, Player target) {
        if (TeamProtectionService.blocks(player, target)) {
            return;
        }
        applyHookPull(player, target, DEEP_PULL_STRENGTH);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DEEP_DEBUFF_TICKS, 2, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, DEEP_DEBUFF_TICKS, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DEEP_DEBUFF_TICKS, 0, false, true, true));
        applyTrueDamage(player, target, DEEP_TRUE_DAMAGE);
        spawnHookParticles(target, true);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.75F, 1.25F);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.65F);
    }

    private void applyHookPull(Player player, Player target, double strength) {
        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() <= 0.0001D) {
            return;
        }
        Vector velocity = pull.normalize().multiply(strength);
        velocity.setY(0.12D);
        target.setVelocity(target.getVelocity().add(velocity));
    }

    private void applyTrueDamage(Player source, Player target, double amount) {
        if (amount <= 0.0D || target.isDead()) {
            return;
        }
        double beforeHealth = target.getHealth();
        target.setNoDamageTicks(0);
        target.setLastDamage(amount);
        target.setHealth(Math.max(0.0D, beforeHealth - amount));
        target.setKiller(source);
    }

    private void spawnHookParticles(Player target, boolean deepHook) {
        target.getWorld().spawnParticle(
            Particle.SMOKE,
            target.getLocation().add(0.0D, 1.0D, 0.0D),
            deepHook ? 38 : 24,
            0.35D,
            0.45D,
            0.35D,
            deepHook ? 0.04D : 0.02D
        );
        target.getWorld().spawnParticle(
            Particle.TRIAL_OMEN,
            target.getLocation().add(0.0D, 1.0D, 0.0D),
            deepHook ? 14 : 8,
            0.25D,
            0.35D,
            0.25D,
            0.0D
        );
        if (deepHook) {
            target.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                target.getLocation().add(0.0D, 1.0D, 0.0D),
                12,
                0.25D,
                0.35D,
                0.25D,
                0.015D
            );
        }
    }

    private void purgeExpiredMarks(long now) {
        Iterator<Map.Entry<UUID, HookMark>> iterator = hookMarks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private void clearMarksForPlayer(UUID playerId) {
        hookMarks.remove(playerId);
        hookMarks.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
    }

    private boolean isGhlochester(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:ghlochester");
    }

    private record HookMark(UUID targetId, long expiresAtMs) {
        private boolean isExpired(long now) {
            return now >= expiresAtMs;
        }
    }
}
