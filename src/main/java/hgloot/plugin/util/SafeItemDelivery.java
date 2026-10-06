package hgloot.plugin.util;

import java.util.Map;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Delivers rewards without silently discarding overflow from a full inventory. */
public final class SafeItemDelivery {
    private SafeItemDelivery() {}

    public static Destination deliver(Player player, ItemStack reward) {
        if (player == null || reward == null || reward.getType().isAir()) {
            return Destination.REJECTED;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward);
        if (overflow.isEmpty()) {
            return Destination.INVENTORY;
        }
        Map<Integer, ItemStack> enderOverflow = player.getEnderChest()
            .addItem(overflow.values().toArray(ItemStack[]::new));
        if (enderOverflow.isEmpty()) {
            return Destination.ENDER_CHEST;
        }
        for (ItemStack stack : enderOverflow.values()) {
            Item dropped = player.getWorld().dropItem(player.getLocation(), stack);
            dropped.setOwner(player.getUniqueId());
            dropped.setPickupDelay(0);
        }
        return Destination.OWNED_DROP;
    }

    public enum Destination {
        INVENTORY,
        ENDER_CHEST,
        OWNED_DROP,
        REJECTED
    }
}
