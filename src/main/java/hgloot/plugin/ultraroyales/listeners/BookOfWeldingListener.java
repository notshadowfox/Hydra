package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.HydraMessages;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class BookOfWeldingListener implements Listener {
    private static final String MODEL_ID = "ultraroyales:book_of_welding";
    private static final Component AUTO_SMELT_LORE = Component.text("Insta-smelts mined ores.")
        .color(TextColor.color(0xffa94d))
        .decoration(TextDecoration.ITALIC, false);

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !isAnvil(clicked.getType())) {
            return;
        }
        ItemStack book = event.getItem();
        if (!isBookOfWelding(book)) {
            return;
        }

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        ItemStack target = hand == EquipmentSlot.HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (target == null || target.getType().isAir() || isBookOfWelding(target)) {
            player.sendMessage(HydraMessages.error("Hold the pickaxe you want to weld in your other hand"));
            event.setCancelled(true);
            return;
        }
        if (!isPickaxe(target.getType())) {
            player.sendMessage(HydraMessages.error("Book of Welding only works on pickaxes"));
            event.setCancelled(true);
            return;
        }
        if (hasAutoSmelt(target)) {
            player.sendMessage(HydraMessages.warning("That pickaxe is already welded"));
            event.setCancelled(true);
            return;
        }

        ItemStack welded = target.clone();
        ItemMeta weldedMeta = welded.getItemMeta();
        if (weldedMeta == null) {
            player.sendMessage(HydraMessages.error("That pickaxe cannot be welded"));
            event.setCancelled(true);
            return;
        }
        weldedMeta.getPersistentDataContainer().set(autoSmeltKey(), PersistentDataType.BYTE, (byte) 1);
        addAutoSmeltLore(weldedMeta);
        welded.setItemMeta(weldedMeta);

        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInOffHand(welded);
            consumeBook(player.getInventory().getItemInMainHand(), player, true);
        } else {
            player.getInventory().setItemInMainHand(welded);
            consumeBook(player.getInventory().getItemInOffHand(), player, false);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.1f);
        player.sendMessage(HydraMessages.success("Book of Welding welded your pickaxe with insta-smelt"));
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!hasAutoSmelt(tool)) {
            return;
        }

        Collection<ItemStack> defaultDrops = event.getBlock().getDrops(tool, event.getPlayer());
        if (defaultDrops.isEmpty()) {
            return;
        }

        List<ItemStack> smeltedDrops = new ArrayList<>();
        boolean changed = false;
        for (ItemStack drop : defaultDrops) {
            ItemStack transformed = smeltDrop(drop);
            if (transformed.getType() != drop.getType()) {
                changed = true;
            }
            smeltedDrops.add(transformed);
        }
        if (!changed) {
            return;
        }

        event.setDropItems(false);
        for (ItemStack stack : smeltedDrops) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), stack);
        }
    }

    private static ItemStack smeltDrop(ItemStack drop) {
        if (drop == null || drop.getType().isAir()) {
            return drop;
        }
        Material smeltedType = switch (drop.getType()) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
        if (smeltedType == null) {
            return drop.clone();
        }
        ItemStack smelted = new ItemStack(smeltedType, Math.max(1, drop.getAmount()));
        return smelted;
    }

    private static void addAutoSmeltLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }
        if (!lore.contains(AUTO_SMELT_LORE)) {
            lore.add(Component.empty());
            lore.add(AUTO_SMELT_LORE);
        }
        meta.lore(lore);
    }

    private static boolean hasAutoSmelt(ItemStack item) {
        if (item == null || item.getType().isAir() || !isPickaxe(item.getType()) || !item.hasItemMeta()) {
            return false;
        }
        Byte tagged = item.getItemMeta().getPersistentDataContainer().get(autoSmeltKey(), PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private static NamespacedKey autoSmeltKey() {
        return new NamespacedKey(UltraRoyales.getPlugin(), "book_of_welding_auto_smelt");
    }

    private static void consumeBook(ItemStack stack, Player player, boolean mainHand) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            return;
        }
        if (mainHand) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private static boolean isAnvil(Material material) {
        return material == Material.ANVIL
            || material == Material.CHIPPED_ANVIL
            || material == Material.DAMAGED_ANVIL;
    }

    private static boolean isPickaxe(Material material) {
        return material != null && material.name().endsWith("_PICKAXE");
    }

    private static boolean isBookOfWelding(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }
}
