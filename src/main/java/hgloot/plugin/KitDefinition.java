package hgloot.plugin;

import java.util.Map;
import org.bukkit.inventory.ItemStack;

/** A fixed kit's editable item positions. The owning game remains authoritative for item contents. */
public interface KitDefinition {
    String id();

    Map<String, Integer> defaultSlots();

    Map<String, ItemStack> previewItems();

    default ItemStack[] previewArmor() {
        return new ItemStack[4];
    }

    default String displayName() {
        return id().toUpperCase(java.util.Locale.ROOT);
    }

    default boolean allowsOffhand(String itemId) {
        return false;
    }
}
