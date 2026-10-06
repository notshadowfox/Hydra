package hgloot.plugin.hunger;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

public final class HungerGamesUtilityItems {
    public static final NamespacedKey UTILITY_ID = new NamespacedKey("hydra", "hg_utility_id");

    private HungerGamesUtilityItems() {}

    public static ItemStack ghostKnife() {
        return item("ghost_knife", Material.WOODEN_SWORD, "Ghost Knife", NamedTextColor.WHITE,
            "ultraroyales:ghostknife", List.of(
                "A blade that lunges you forward at such high speeds,",
                "you appear as a ghost.", "Right-click to dash.", "Consumed on first use."));
    }

    public static ItemStack frogShoes() {
        ItemStack shoes = item("frog_shoes", Material.LEATHER_BOOTS, "Frog Boots", NamedTextColor.GREEN,
            null, List.of(
                "Sprouts temporary lily pads beneath your feet.", "Hold Shift to disable."));
        ItemMeta meta = shoes.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) leather.setColor(Color.fromRGB(72, 196, 72));
        if (meta instanceof Damageable damageable) damageable.setMaxDamage(429);
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
            new NamespacedKey("hydra", "frog_shoes_armor"), 3.0D,
            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
            new NamespacedKey("hydra", "frog_shoes_toughness"), 2.0D,
            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        shoes.setItemMeta(meta);
        return shoes;
    }

    public static ItemStack fletchersPack() {
        ItemStack arrows = item("fletchers_pack", Material.ARROW, "Fletcher's Pack", NamedTextColor.YELLOW,
            null, List.of("A pre-made pack of normal arrows."));
        arrows.setAmount(10);
        return arrows;
    }

    public static ItemStack diversSponge() {
        ItemStack sponge = item("divers_sponge", Material.SPONGE, "Diver's Sponge", NamedTextColor.AQUA,
            null, List.of(
                "Creates a moving pocket of air around you.", "Right-click to activate. Lasts 15 seconds."));
        ItemMeta meta = sponge.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(300);
            damageable.setDamage(0);
            sponge.setItemMeta(meta);
        }
        return sponge;
    }

    public static ItemStack scavengersRod() {
        return item("scavengers_rod", Material.FISHING_ROD, "Scavenger's Rod", NamedTextColor.GOLD,
            null, List.of(
                "Fish to reel in valuable supplies.", "Breaks after 6 successful catches."));
    }

    public static ItemStack windPack() {
        ItemStack charges = item("wind_pack", Material.WIND_CHARGE, "Wind Pack", NamedTextColor.AQUA,
            null, List.of("A tightly packed set of Wind Charges."));
        charges.setAmount(6);
        return charges;
    }

    public static ItemStack webBundle() {
        ItemStack webs = item("web_bundle", Material.COBWEB, "Web Bundle", NamedTextColor.WHITE,
            null, List.of("A tightly packed bundle of sticky webs."));
        webs.setAmount(5);
        return webs;
    }

    public static ItemStack trackingCompass() {
        return item("tracking_compass", Material.COMPASS, "Tracking Compass", NamedTextColor.RED,
            "hydra:tracking_compass", List.of(
                "Locks onto the nearest living enemy.", "Right-click to begin the hunt."));
    }

    public static String utilityId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(UTILITY_ID, PersistentDataType.STRING);
        if (id != null) return id;
        return new NamespacedKey("hydra", "light_apple").equals(meta.getItemModel()) ? "light_apple" : null;
    }

    private static ItemStack item(String id, Material material, String name, NamedTextColor color,
                                  String model, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalStateException("Missing metadata for " + material);
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreLines.stream().map(line -> Component.text(line, NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)).toList());
        if (model != null && !model.isBlank()) meta.setItemModel(NamespacedKey.fromString(model));
        meta.getPersistentDataContainer().set(UTILITY_ID, PersistentDataType.STRING, id);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
