package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.ultraroyales.Items;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

/** Only the canonical Gilded Sword family receives Hydra's three table choices. */
public final class GildedSwordEnchantListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPrepare(PrepareItemEnchantEvent event) {
        if (!Items.isGildedSword(event.getItem())) {
            return;
        }
        EnchantmentOffer[] offers = event.getOffers();
        for (int slot = 0; slot < Math.min(3, offers.length); slot++) {
            EnchantmentOffer offer = offers[slot];
            if (offer == null) {
                continue; // Preserve vanilla's unavailable slot and its cost.
            }
            offer.setEnchantment(enchantmentFor(slot));
            offer.setEnchantmentLevel(1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!Items.isGildedSword(event.getItem())) {
            return;
        }
        int slot = event.whichButton();
        if (slot < 0 || slot > 2) {
            return;
        }
        // Bukkit still performs its normal lapis and XP charge. Only the result changes.
        event.getEnchantsToAdd().clear();
        event.getEnchantsToAdd().put(enchantmentFor(slot), 1);
    }

    private static Enchantment enchantmentFor(int slot) {
        return slot == 2 ? Enchantment.SHARPNESS : Enchantment.UNBREAKING;
    }
}
