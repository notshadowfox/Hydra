package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.ultraroyales.UltraRoyales;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class GhostKnifeListener implements Listener {

    private final HGLootPlugin plugin;

    public GhostKnifeListener(HGLootPlugin plugin) {
        this.plugin = plugin;
    }

    private static final String MODEL_ID = "ultraroyales:ghostknife";
    private static final int COOLDOWN_SECONDS = 10;
    private static final double LUNGE_HORIZONTAL_SPEED = 1.65D;
    private static final double LUNGE_VERTICAL_SPEED = 0.26D;

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.queueService() != null && plugin.queueService().isActiveHungerGamesPlayer(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isGhostKnife(item)) {
            return;
        }
        event.setCancelled(true);

        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.ghostKnifeCooldown);
        if (cooldown > 0) {
            player.sendActionBar(HydraMessages.cooldown("Ghost Knife", cooldown));
            return;
        }

        Vector direction = player.getLocation().getDirection().clone();
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        direction.normalize();
        player.setVelocity(direction.multiply(LUNGE_HORIZONTAL_SPEED).setY(LUNGE_VERTICAL_SPEED));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 0, false, true, true));
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0.0D, 1.0D, 0.0D), 22, 0.35D, 0.25D, 0.35D, 0.02D);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0.0D, 1.0D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8F, 1.15F);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.4F);
        CooldownRegistry.setCooldown(player, UltraRoyales.ghostKnifeCooldown, COOLDOWN_SECONDS, false);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        if (!isGhostKnife(inventory.getFirstItem()) && !isGhostKnife(inventory.getSecondItem())) {
            return;
        }
        event.setResult(null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.GRINDSTONE) {
            return;
        }
        for (ItemStack stack : event.getInventory().getContents()) {
            if (isGhostKnife(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isGhostKnife(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }
}
