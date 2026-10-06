package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.TeamProtectionService;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FrozenChestpieceListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:frozen_chestpiece";
    private static final int ACTIVE_TICKS = 160;
    private static final BlockData ICE_PARTICLE = Material.PACKED_ICE.createBlockData();
    private static final double HIT_RADIUS_XZ = 2.6D;
    private static final double HIT_RADIUS_Y = 1.8D;
    private static final long FREEZE_INTERVAL_MS = 350L;
    private static final long DAMAGE_INTERVAL_MS = 450L;
    private static final double COLLISION_DAMAGE = 4.0D;
    private static final Map<UUID, Long> activeUntilMs = new HashMap<>();
    private static final Map<UUID, Long> frozenTargetCooldown = new HashMap<>();
    private static final Map<UUID, Long> frozenTargetDamageCooldown = new HashMap<>();

    public static void activate(Player player) {
        if (!hasFrozenChestpieceAccess(player)) {
            return;
        }

        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.frozenChestpieceCooldown);
        if (cooldown > 0) {
            player.sendMessage(HydraMessages.cooldown("Frozen Chestpiece", cooldown));
            return;
        }

        activeUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + (ACTIVE_TICKS * 50L));
        CooldownRegistry.setCooldown(player, UltraRoyales.frozenChestpieceCooldown, 120, false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ACTIVE_TICKS, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ACTIVE_TICKS, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ACTIVE_TICKS, 1, false, true, true));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.15f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.1f, 1.0f);

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    activeUntilMs.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (ticks >= ACTIVE_TICKS || !isActive(player)) {
                    activeUntilMs.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                ticks += 2;

                spawnIceBallVisual(player, ticks);

                for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), HIT_RADIUS_XZ, HIT_RADIUS_Y, HIT_RADIUS_XZ)) {
                    if (!(entity instanceof LivingEntity target)) {
                        continue;
                    }
                    if (target.getUniqueId().equals(player.getUniqueId()) || target.isDead()) {
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    long last = frozenTargetCooldown.getOrDefault(target.getUniqueId(), 0L);
                    if (now - last >= FREEZE_INTERVAL_MS) {
                        frozenTargetCooldown.put(target.getUniqueId(), now);
                        target.setFreezeTicks(Math.max(target.getFreezeTicks(), 140));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, true, true));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 1, false, true, true));
                        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.95f, 1.15f);
                    }

                    if (TeamProtectionService.blocks(player, target)) continue;
                    long lastDamage = frozenTargetDamageCooldown.getOrDefault(target.getUniqueId(), 0L);
                    if (now - lastDamage >= DAMAGE_INTERVAL_MS) {
                        frozenTargetDamageCooldown.put(target.getUniqueId(), now);
                        target.setNoDamageTicks(0);
                        target.damage(COLLISION_DAMAGE, player);
                    }

                    Vector push = player.getVelocity();
                    if (push.lengthSquared() > 0.0001D) {
                        target.setVelocity(push.normalize().multiply(0.75D).setY(0.3D));
                    }
                }

                Vector movement = player.getVelocity();
                Vector horizontal = movement.clone().setY(0.0D);
                if (horizontal.lengthSquared() > 0.001D) {
                    player.setVelocity(horizontal.normalize().multiply(0.72D).setY(Math.max(-0.1D, movement.getY())));
                }
            }
        }.runTaskTimer(UltraRoyales.getPlugin(), 0L, 2L);
    }

    @EventHandler
    public void onShiftRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (!isFrozenChestpiece(player.getInventory().getChestplate())) {
            return;
        }

        event.setCancelled(true);
        activate(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeUntilMs.remove(event.getPlayer().getUniqueId());
    }

    private static boolean isActive(Player player) {
        Long until = activeUntilMs.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            activeUntilMs.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private static boolean isFrozenChestpiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }

    private static boolean hasFrozenChestpieceAccess(Player player) {
        return isFrozenChestpiece(player.getInventory().getItemInMainHand())
            || isFrozenChestpiece(player.getInventory().getChestplate());
    }

    private static void spawnIceBallVisual(Player player, int tickCount) {
        Location center = player.getLocation().add(0.0D, 1.0D, 0.0D);
        double spin = tickCount * 0.2D;
        double[] yOffsets = {-0.45D, 0.0D, 0.45D};
        double[] radii = {0.9D, 1.15D, 0.9D};

        for (int ring = 0; ring < yOffsets.length; ring++) {
            int points = 8 + (ring * 2);
            for (int i = 0; i < points; i++) {
                double angle = spin + ((Math.PI * 2.0D) * i / points) + (ring * 0.18D);
                double x = Math.cos(angle) * radii[ring];
                double z = Math.sin(angle) * radii[ring];
                Location point = center.clone().add(x, yOffsets[ring], z);
                player.getWorld().spawnParticle(Particle.BLOCK, point, 1, 0.0D, 0.0D, 0.0D, 0.0D, ICE_PARTICLE);
                if (i % 3 == 0) {
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }

        player.getWorld().spawnParticle(Particle.BLOCK, center, 6, 0.35D, 0.55D, 0.35D, 0.01D, ICE_PARTICLE);
    }
}
