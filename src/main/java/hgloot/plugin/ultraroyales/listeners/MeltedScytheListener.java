package hgloot.plugin.ultraroyales.listeners;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.util.Vector;

public class MeltedScytheListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:melted_scythe";
    private static final double BASE_DAMAGE = 5.0D;

    @EventHandler
    public void onScytheHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!isMeltedScythe(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        int bonus = armorPointBonus(target);
        int sharpnessLevel = attacker.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SHARPNESS);
        double sharpnessBonus = sharpnessLevel > 0 ? (0.5D * sharpnessLevel) + 0.5D : 0.0D;
        event.setDamage(BASE_DAMAGE + bonus + sharpnessBonus);
        target.setFireTicks(Math.max(target.getFireTicks(), 200));
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0.0D, 1.0D, 0.0D), 14, 0.35D, 0.45D, 0.35D, 0.01D);
        spawnSoulSweep(attacker);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (!isMeltedScythe(event.getItem())) {
            return;
        }

        int sharpness = Math.max(1, Math.min(5, 1 + (event.getExpLevelCost() / 10)));
        int existing = event.getItem().getEnchantmentLevel(Enchantment.SHARPNESS);
        event.getEnchantsToAdd().put(Enchantment.SHARPNESS, Math.max(existing, sharpness));
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getFirstItem();
        ItemStack addition = inventory.getSecondItem();
        if (base == null || addition == null || !isMeltedScythe(base)) {
            return;
        }

        int addLevel = sharpnessFrom(addition);
        if (addLevel <= 0) {
            return;
        }

        int currentLevel = base.getEnchantmentLevel(Enchantment.SHARPNESS);
        int targetLevel = addLevel == currentLevel ? currentLevel + 1 : Math.max(currentLevel, addLevel);
        targetLevel = Math.min(5, targetLevel);
        if (targetLevel <= currentLevel) {
            return;
        }

        ItemStack result = base.clone();
        result.addUnsafeEnchantment(Enchantment.SHARPNESS, targetLevel);
        event.setResult(result);
    }

    private int armorPointBonus(LivingEntity target) {
        if (target.getAttribute(Attribute.ARMOR) == null) {
            return 0;
        }
        return Math.max(0, Math.min(8, (int) Math.floor(target.getAttribute(Attribute.ARMOR).getValue())));
    }

    private boolean isMeltedScythe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }

    private int sharpnessFrom(ItemStack item) {
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta stored) {
            return stored.getStoredEnchantLevel(Enchantment.SHARPNESS);
        }
        return item.getEnchantmentLevel(Enchantment.SHARPNESS);
    }

    private void spawnSoulSweep(Player attacker) {
        Vector forward = attacker.getLocation().getDirection().setY(0.0D);
        if (forward.lengthSquared() <= 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();

        // Draw a curved blade edge instead of a flat line so the swing reads like a scythe head.
        for (int i = 0; i <= 26; i++) {
            double t = i / 26.0D;
            double angle = Math.toRadians(-140.0D + (105.0D * t));
            double radius = 0.9D + (0.95D * t);
            Vector curve = right.clone().multiply(Math.cos(angle) * radius)
                .add(forward.clone().multiply(Math.sin(angle) * radius));
            double lift = 0.15D + (0.45D * t);

            attacker.getWorld().spawnParticle(
                Particle.SOUL,
                attacker.getLocation().add(0.0D, 1.0D + lift, 0.0D).add(curve),
                1,
                0.02D,
                0.02D,
                0.02D,
                0.0D
            );
            attacker.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                attacker.getLocation().add(0.0D, 1.0D + lift, 0.0D).add(curve),
                1,
                0.01D,
                0.01D,
                0.01D,
                0.0D
            );
        }

        for (int i = 0; i < 8; i++) {
            double t = i / 7.0D;
            Vector spine = forward.clone().multiply(0.45D + (t * 1.0D)).add(right.clone().multiply(0.18D));
            attacker.getWorld().spawnParticle(Particle.SOUL, attacker.getLocation().add(0.0D, 1.05D + (t * 0.35D), 0.0D).add(spine), 1, 0.01D, 0.01D, 0.01D, 0.0D);
        }

        attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0.0D, 1.0D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 0.85F);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.65F, 0.85F);
    }
}
