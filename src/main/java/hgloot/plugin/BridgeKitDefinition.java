package hgloot.plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/** Bridge's authoritative starting items; team color is supplied by Bridge at match time. */
public final class BridgeKitDefinition implements KitDefinition {
    public static final String ID = "bridge";
    public static final String ARROW = "arrow";
    private static final Map<String, Integer> DEFAULT_SLOTS = Map.of(
        "sword", 0, "bow", 1, "pickaxe", 2, "blocks", 3, "golden_apples", 4, ARROW, 8
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Map<String, Integer> defaultSlots() {
        return DEFAULT_SLOTS;
    }

    @Override
    public Map<String, ItemStack> previewItems() {
        return items(Material.RED_TERRACOTTA);
    }

    @Override
    public ItemStack[] previewArmor() {
        return armor(Color.RED);
    }

    @Override
    public boolean allowsOffhand(String itemId) {
        return "blocks".equals(itemId);
    }

    public static ItemStack[] armor(Color color) {
        return new ItemStack[] {
            coloredArmor(Material.LEATHER_BOOTS, color),
            coloredArmor(Material.LEATHER_LEGGINGS, color),
            coloredArmor(Material.LEATHER_CHESTPLATE, color),
            coloredArmor(Material.LEATHER_HELMET, color)
        };
    }

    private static ItemStack coloredArmor(Material material, Color color) {
        ItemStack armor = new ItemStack(material);
        if (armor.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            armor.setItemMeta(meta);
        }
        return armor;
    }

    public static Map<String, ItemStack> items(Material teamBlock) {
        Map<String, ItemStack> items = new LinkedHashMap<>();
        items.put("sword", new ItemStack(Material.IRON_SWORD));
        items.put("bow", new ItemStack(Material.BOW));
        items.put("pickaxe", new ItemStack(Material.IRON_PICKAXE));
        items.put("blocks", new ItemStack(teamBlock, 64));
        items.put("golden_apples", new ItemStack(Material.GOLDEN_APPLE, 8));
        items.put(ARROW, new ItemStack(Material.ARROW));
        return items;
    }
}
