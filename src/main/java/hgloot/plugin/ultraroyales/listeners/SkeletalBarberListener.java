package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.ultraroyales.UltraRoyales;
import hgloot.plugin.util.HydraIcons;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class SkeletalBarberListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:skeletalbarber";
    private static final long MARK_DURATION_MS = 5_000L;
    private static final int HUD_SEGMENTS = 12;
    private static final double MARKED_BONUS_DAMAGE = 1.0D;
    private static final double EXECUTE_HEALTH_FRACTION = 0.35D;
    private static final double EXECUTE_TRUE_DAMAGE = 2.0D;
    private static final int EXECUTE_WITHER_TICKS = 60;
    private static final int EXECUTE_WITHER_AMPLIFIER = 1;
    private static final double KILL_HEAL_AMOUNT = 4.0D;

    private static BukkitTask hudTask;
    private final Map<UUID, MarkState> activeMarks = new HashMap<>();

    public SkeletalBarberListener() {
        startHudTask();
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) {
            return;
        }
        if (attacker.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        if (!isSkeletalBarber(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        long now = System.currentTimeMillis();
        purgeExpiredMarks(now);

        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        MarkState state = activeMarks.get(attackerId);
        boolean newMark = state == null || !state.targetId().equals(targetId) || state.isExpired(now);
        if (newMark) {
            state = new MarkState(targetId, now + MARK_DURATION_MS, false);
            playMarkFeedback(target);
        } else {
            state = state.refresh(now + MARK_DURATION_MS);
        }
        activeMarks.put(attackerId, state);

        event.setDamage(event.getDamage() + MARKED_BONUS_DAMAGE);

        if (!state.executeUsed() && isBelowExecuteThreshold(target)) {
            activeMarks.put(attackerId, state.withExecuteUsed());
            applyTrueDamage(attacker, target, EXECUTE_TRUE_DAMAGE);
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER,
                EXECUTE_WITHER_TICKS,
                EXECUTE_WITHER_AMPLIFIER,
                false,
                true,
                true
            ));
            playExecuteFeedback(attacker, target);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null) {
            clearMarksForPlayer(victim.getUniqueId());
            return;
        }

        UUID killerId = killer.getUniqueId();
        MarkState state = activeMarks.get(killerId);
        long now = System.currentTimeMillis();
        if (state != null
            && !state.isExpired(now)
            && state.targetId().equals(victim.getUniqueId())
            && isSkeletalBarber(killer.getInventory().getItemInMainHand())) {
            heal(killer, KILL_HEAL_AMOUNT);
            playKillHealFeedback(killer);
        }

        activeMarks.remove(killerId);
        clearMarksForPlayer(victim.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearMarksForPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearMarksForPlayer(event.getPlayer().getUniqueId());
    }

    private void startHudTask() {
        if (hudTask != null && !hudTask.isCancelled()) {
            return;
        }
        hudTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                purgeExpiredMarks(now);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isSkeletalBarber(player.getInventory().getItemInMainHand())) {
                        showBarberHud(player, now);
                    }
                }
            }
        }.runTaskTimer(UltraRoyales.getPlugin(), 1L, 4L);
    }

    private void showBarberHud(Player player, long now) {
        MarkState state = activeMarks.get(player.getUniqueId());
        int segments = 0;
        boolean executeUsed = false;
        if (state != null && !state.isExpired(now)) {
            long remainingMs = Math.max(0L, state.expiresAtMs() - now);
            segments = Math.max(1, Math.min(HUD_SEGMENTS, (int) Math.ceil((remainingMs / (double) MARK_DURATION_MS) * HUD_SEGMENTS)));
            executeUsed = state.executeUsed();
        }

        Component hud = HydraIcons.spongeSaberWaterBar(segments);
        if (executeUsed) {
            hud = hud.append(Component.text("  EXEC", NamedTextColor.GRAY));
        }
        UltraRoyales.holdBossRushDamageActionBar(player, 1500L);
        player.sendActionBar(hud);
    }

    private boolean isBelowExecuteThreshold(Player target) {
        double maxHealth = maxHealth(target);
        if (maxHealth <= 0.0D) {
            return false;
        }
        return target.getHealth() / maxHealth < EXECUTE_HEALTH_FRACTION;
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

    private void heal(Player player, double amount) {
        if (amount <= 0.0D || player.isDead()) {
            return;
        }
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? player.getMaxHealth() : attribute.getValue();
    }

    private void playMarkFeedback(Player target) {
        target.getWorld().spawnParticle(
            Particle.CLOUD,
            target.getLocation().add(0.0D, 1.0D, 0.0D),
            18,
            0.35D,
            0.5D,
            0.35D,
            0.02D
        );
        target.getWorld().spawnParticle(
            Particle.SOUL,
            target.getLocation().add(0.0D, 1.1D, 0.0D),
            8,
            0.25D,
            0.4D,
            0.25D,
            0.01D
        );
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SKELETON_HURT, 0.9F, 0.75F);
    }

    private void playExecuteFeedback(Player attacker, Player target) {
        target.getWorld().spawnParticle(
            Particle.SOUL_FIRE_FLAME,
            target.getLocation().add(0.0D, 1.0D, 0.0D),
            24,
            0.35D,
            0.55D,
            0.35D,
            0.025D
        );
        target.getWorld().spawnParticle(
            Particle.DAMAGE_INDICATOR,
            target.getLocation().add(0.0D, 1.1D, 0.0D),
            8,
            0.25D,
            0.35D,
            0.25D,
            0.0D
        );
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 1.0F, 0.7F);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8F, 0.65F);
    }

    private void playKillHealFeedback(Player player) {
        player.getWorld().spawnParticle(
            Particle.HEART,
            player.getLocation().add(0.0D, 1.35D, 0.0D),
            4,
            0.25D,
            0.25D,
            0.25D,
            0.0D
        );
        player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.9F, 0.8F);
    }

    private void purgeExpiredMarks(long now) {
        Iterator<Map.Entry<UUID, MarkState>> iterator = activeMarks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private void clearMarksForPlayer(UUID playerId) {
        activeMarks.remove(playerId);
        activeMarks.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
    }

    private boolean isSkeletalBarber(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }

    private record MarkState(UUID targetId, long expiresAtMs, boolean executeUsed) {
        private MarkState refresh(long nextExpiresAtMs) {
            return new MarkState(targetId, nextExpiresAtMs, executeUsed);
        }

        private MarkState withExecuteUsed() {
            return new MarkState(targetId, expiresAtMs, true);
        }

        private boolean isExpired(long now) {
            return now >= expiresAtMs;
        }
    }
}
