package dev.zxdzero.ZxdzeroEvents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemHelper {
    private static final NamespacedKey damageKey = new NamespacedKey("zxdzeroevents", "attack_damage");
    private static final NamespacedKey speedKey = new NamespacedKey("zxdzeroevents", "attack_speed");

    public static List<Component> loreBuilder(List<String> lore) {
        List<Component> list = new ArrayList<>();
        for (int i = 0; i < lore.size(); i++) {
            Component component = Component.text(" - " + lore.get(i), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
            list.add(component);
        }

        return list;
    }

    public static ItemMeta weaponBuilder(ItemMeta meta, double attackDamage, double attackSpeed) {
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                damageKey,
                attackDamage,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        ));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                speedKey,
                attackSpeed - 4.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(List.of(
                Component.text(""),
                Component.text("When in Main Hand:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(" " + attackDamage + " Attack Damage", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text(" " + attackSpeed +  " Attack Speed", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)
        ));
        return meta;
    }
}
