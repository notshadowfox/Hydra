package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.TeamProtectionService;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class SonicWrenchListener implements Listener {

    private static final NamespacedKey SONIC_WRENCH_PROJECTILE = new NamespacedKey(UltraRoyales.getPlugin(), "sonic_wrench_projectile");
    private static final int WRENCH_COUNT = 5;
    private static final int COOLDOWN_SECONDS = 6;
    private static final double WRENCH_SPEED = 2.6D;
    private static final double DIRECT_DAMAGE = 4.0D;
    private static final double EXPLOSION_DAMAGE = 4.0D;
    private static final double EXPLOSION_RADIUS = 1.75D;

    public static void cast(Player player) {
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.sonicWrenchCooldown);
        if (cooldown > 0) {
            player.sendActionBar(HydraMessages.cooldown("Sculk Wrench", cooldown));
            return;
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.15f);
        for (int i = 0; i < WRENCH_COUNT; i++) {
            double offset = (i - ((WRENCH_COUNT - 1) / 2.0D)) * 0.12D;
            spawnWrench(player, offset);
        }
        CooldownRegistry.setCooldown(player, UltraRoyales.sonicWrenchCooldown, COOLDOWN_SECONDS, false);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Byte tagged = projectile.getPersistentDataContainer().get(SONIC_WRENCH_PROJECTILE, PersistentDataType.BYTE);
        if (tagged == null || tagged != (byte) 1) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player shooter)) {
            projectile.remove();
            return;
        }

        Location impactLocation = projectile.getLocation();
        if (event.getHitEntity() != null) {
            impactLocation = event.getHitEntity().getLocation().add(0.0D, 0.5D, 0.0D);
        } else if (event.getHitBlock() != null) {
            impactLocation = event.getHitBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        }

        spawnMiniExplosion(impactLocation, shooter);

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity target && !target.getUniqueId().equals(shooter.getUniqueId())) {
            if (TeamProtectionService.blocks(shooter, target)) {
                projectile.remove();
                return;
            }
            double beforeHealth = target.getHealth();
            target.setNoDamageTicks(0);
            target.damage(DIRECT_DAMAGE, shooter);
            normalizeToExactDamage(target, beforeHealth, DIRECT_DAMAGE);
            Vector knockback = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
            if (knockback.lengthSquared() > 0.001D) {
                target.setVelocity(knockback.normalize().multiply(0.58D).setY(0.25D));
            }
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.2D, 0.2D, 0.2D, 0.02D);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 1.3f);
        }

        createSculkImpact(impactLocation);
        projectile.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
    }

    private static void spawnWrench(Player player, double spreadOffset) {
        Snowball wrench = player.launchProjectile(Snowball.class);
        wrench.setItem(new ItemStack(Material.IRON_NUGGET));
        Vector velocity = player.getLocation().getDirection().clone().normalize();
        Vector right = new Vector(-velocity.getZ(), 0.0D, velocity.getX()).normalize().multiply(spreadOffset);
        wrench.setVelocity(velocity.add(right).normalize().multiply(WRENCH_SPEED));
        wrench.getPersistentDataContainer().set(SONIC_WRENCH_PROJECTILE, PersistentDataType.BYTE, (byte) 1);
        player.getWorld().spawnParticle(Particle.CRIT, wrench.getLocation(), 6, 0.05D, 0.05D, 0.05D, 0.01D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 1.65f);
    }

    private static void normalizeToExactDamage(LivingEntity target, double beforeHealth, double desiredDamage) {
        double afterHealth = target.getHealth();
        if (beforeHealth <= 0.0D || afterHealth <= 0.0D) {
            return;
        }

        double dealt = beforeHealth - afterHealth;
        if (Math.abs(dealt - desiredDamage) < 0.0001D) {
            return;
        }

        if (dealt < desiredDamage) {
            target.setHealth(Math.max(0.0D, afterHealth - (desiredDamage - dealt)));
            return;
        }

        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null
            ? target.getAttribute(Attribute.MAX_HEALTH).getValue()
            : afterHealth;
        target.setHealth(Math.min(maxHealth, afterHealth + (dealt - desiredDamage)));
    }

    private static void createSculkImpact(Location impactLocation) {
        if (impactLocation == null || impactLocation.getWorld() == null) {
            return;
        }

        int blockRadius = (int) Math.ceil(EXPLOSION_RADIUS);
        int baseX = impactLocation.getBlockX();
        int baseY = impactLocation.getBlockY();
        int baseZ = impactLocation.getBlockZ();
        double radiusSquared = EXPLOSION_RADIUS * EXPLOSION_RADIUS;
        boolean changed = false;
        Set<Long> visited = new HashSet<>();

        for (int offsetX = -blockRadius; offsetX <= blockRadius; offsetX++) {
            for (int offsetY = -blockRadius; offsetY <= blockRadius; offsetY++) {
                for (int offsetZ = -blockRadius; offsetZ <= blockRadius; offsetZ++) {
                    Block candidate = impactLocation.getWorld().getBlockAt(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
                    Block resolved = resolveCenterBlock(candidate.getLocation().add(0.5D, 0.5D, 0.5D));
                    if (!canConvertToSculk(resolved)) {
                        continue;
                    }

                    long key = blockKey(resolved);
                    if (!visited.add(key)) {
                        continue;
                    }

                    if (resolved.getLocation().add(0.5D, 0.5D, 0.5D).distanceSquared(impactLocation) > radiusSquared) {
                        continue;
                    }

                    if (resolved.getType() != Material.SCULK) {
                        resolved.setType(Material.SCULK, false);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            impactLocation.getWorld().spawnParticle(Particle.SCULK_SOUL, impactLocation, 18, 0.45D, 0.3D, 0.45D, 0.01D);
            impactLocation.getWorld().spawnParticle(Particle.SOUL, impactLocation, 12, 0.45D, 0.25D, 0.45D, 0.01D);
            impactLocation.getWorld().playSound(impactLocation, Sound.BLOCK_SCULK_SPREAD, 0.85F, 1.0F);
        }
    }

    private static void spawnMiniExplosion(Location impactLocation, Player shooter) {
        if (impactLocation == null || impactLocation.getWorld() == null) {
            return;
        }
        impactLocation.getWorld().spawnParticle(Particle.EXPLOSION, impactLocation, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        impactLocation.getWorld().spawnParticle(Particle.SMOKE, impactLocation, 16, 0.35D, 0.25D, 0.35D, 0.02D);
        impactLocation.getWorld().spawnParticle(Particle.SCULK_SOUL, impactLocation, 8, 0.25D, 0.2D, 0.25D, 0.01D);
        impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.35f);
        impactLocation.getWorld().createExplosion(
            impactLocation.getX(),
            impactLocation.getY(),
            impactLocation.getZ(),
            1.8f,
            false,
            true,
            shooter
        );
        for (Entity entity : impactLocation.getWorld().getNearbyEntities(impactLocation, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || living.equals(shooter) || living.isDead()) {
                continue;
            }
            if (TeamProtectionService.blocks(shooter, living)) continue;
            living.damage(EXPLOSION_DAMAGE, shooter);
            Vector knockback = living.getLocation().toVector().subtract(impactLocation.toVector());
            if (knockback.lengthSquared() > 0.001D) {
                living.setVelocity(knockback.normalize().multiply(0.75D).setY(0.33D));
            }
        }
    }

    private static Block resolveCenterBlock(Location impactLocation) {
        Block block = impactLocation.getBlock();
        if (block.isPassable()) {
            Block below = block.getRelative(BlockFace.DOWN);
            if (!isProtected(below.getType())) {
                return below;
            }
        }
        return block;
    }

    private static boolean canConvertToSculk(Block block) {
        if (block == null) {
            return false;
        }

        Material type = block.getType();
        if (type == Material.SCULK) {
            return true;
        }
        if (isProtected(type) || block.isLiquid() || !type.isBlock() || !type.isSolid()) {
            return false;
        }
        return !(block.getState() instanceof TileState);
    }

    private static long blockKey(Block block) {
        long x = ((long) block.getX() & 0x3FFFFFFL) << 38;
        long z = ((long) block.getZ() & 0x3FFFFFFL) << 12;
        long y = (long) block.getY() & 0xFFFL;
        return x | z | y;
    }

    private static boolean isProtected(Material type) {
        return type == Material.BEDROCK
            || type == Material.BARRIER
            || type == Material.END_PORTAL_FRAME
            || type == Material.END_PORTAL
            || type == Material.NETHER_PORTAL
            || type == Material.STRUCTURE_BLOCK
            || type == Material.STRUCTURE_VOID
            || type == Material.JIGSAW;
    }
}
