package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.TeamProtectionService;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DwarvenBowListener implements Listener {

    private final NamespacedKey dwarvenArrow = new NamespacedKey(UltraRoyales.getPlugin(), "dwarven_arrow");
    private final NamespacedKey dwarvenHitProcessed = new NamespacedKey(UltraRoyales.getPlugin(), "dwarven_hit_processed");
    private static final long FRACTURE_WINDOW_MS = 8_000L;
    private static final long FRACTURE_COOLDOWN_MS = 10_000L;
    private static final int MAX_FRACTURE_STACKS = 3;
    private final Map<UUID, FractureState> fracturePrimed = new HashMap<>();
    private final Map<UUID, Long> fractureCooldowns = new HashMap<>();

    @EventHandler
    public void onBowFire(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        if (event.getBow() == null || !event.getBow().hasItemMeta()) {
            return;
        }
        ItemMeta bowMeta = event.getBow().getItemMeta();
        if (bowMeta == null || !bowMeta.hasCustomModelDataComponent()) {
            return;
        }
        if (!bowMeta.getCustomModelDataComponent().getStrings().contains("ultraroyales:dwarvenbow")) {
            return;
        }

        arrow.getPersistentDataContainer().set(dwarvenArrow, PersistentDataType.BYTE, (byte) 1);
        arrow.setVelocity(arrow.getVelocity().multiply(0.86D)); // Slightly slower draw speed feel.
        arrow.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 8, 0.05D, 0.05D, 0.05D, 0.01D);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!isDwarvenArrow(arrow)) {
            return;
        }
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) {
            return;
        }

        handleDwarvenHit(arrow, shooter, target);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (!isDwarvenBow(event.getItem())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        if (isDwarvenBow(inventory.getFirstItem()) || isDwarvenBow(inventory.getSecondItem())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.GRINDSTONE) {
            return;
        }
        for (ItemStack stack : event.getInventory().getContents()) {
            if (isDwarvenBow(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        fracturePrimed.remove(playerId);
        fractureCooldowns.remove(playerId);
    }

    private void handleDwarvenHit(Arrow arrow, LivingEntity shooter, LivingEntity target) {
        if (arrow.getPersistentDataContainer().has(dwarvenHitProcessed, PersistentDataType.BYTE)) {
            return;
        }
        arrow.getPersistentDataContainer().set(dwarvenHitProcessed, PersistentDataType.BYTE, (byte) 1);

        if (!(shooter instanceof org.bukkit.entity.Player player)) {
            return;
        }

        UUID shooterId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long cooldownUntil = fractureCooldowns.get(shooterId);
        if (cooldownUntil != null) {
            if (now < cooldownUntil) {
                return;
            }
            fractureCooldowns.remove(shooterId);
        }

        UUID targetId = target.getUniqueId();
        if (TeamProtectionService.blocks(player, target)) {
            return;
        }
        FractureState primed = fracturePrimed.get(shooterId);
        boolean sameTarget = primed != null
            && primed.targetId.equals(targetId)
            && now <= primed.expiresAtMs;
        int stacks = sameTarget ? Math.min(MAX_FRACTURE_STACKS, primed.stacks + 1) : 1;
        fracturePrimed.put(shooterId, new FractureState(targetId, now + FRACTURE_WINDOW_MS, stacks));

        double bonusDamage = switch (stacks) {
            case 1 -> 0.75D;
            case 2 -> 1.5D;
            default -> 2.4D;
        };
        target.setNoDamageTicks(0);
        target.damage(bonusDamage, shooter);

        if (stacks >= 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stacks == 2 ? 35 : 45, 0, false, true, true));
        }
        if (stacks >= MAX_FRACTURE_STACKS) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0, false, true, true));
            fractureCooldowns.put(shooterId, now + FRACTURE_COOLDOWN_MS);
            fracturePrimed.remove(shooterId);
        }

        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0.0D, 1.0D, 0.0D), 8 + (stacks * 7), 0.25D, 0.3D, 0.25D, 0.02D);
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0D, 1.0D, 0.0D), 5 + (stacks * 5), 0.22D, 0.25D, 0.22D, 0.03D);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.7f + (stacks * 0.08f), 0.82f + (stacks * 0.07f));
        if (stacks >= MAX_FRACTURE_STACKS) {
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 1.1f);
        }
    }

    private boolean isDwarvenArrow(AbstractArrow arrow) {
        Byte tagged = arrow.getPersistentDataContainer().get(dwarvenArrow, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private boolean isDwarvenBow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = item.getItemMeta();
        return itemMeta != null
            && itemMeta.hasCustomModelDataComponent()
            && itemMeta.getCustomModelDataComponent().getStrings().contains("ultraroyales:dwarvenbow");
    }

    private record FractureState(UUID targetId, long expiresAtMs, int stacks) {
    }
}
