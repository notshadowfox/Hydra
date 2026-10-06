package dev.zxdzero.ZxdzeroEvents.registries;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ItemActionRegistry {
    private static final Map<CustomModelDataComponent, BiConsumer<Player, ItemStack>> actions = new HashMap<>();
    private static final Map<String, BiConsumer<Player, ItemStack>> actionsByModelId = new HashMap<>();

    /**
     * Registers custom right click behavior for an item with a specific custom model data
     *
     * @param customModelData The custom model data to tie to the behavior
     * @param action The code to execute when the item is used
     */
    public static void register(CustomModelDataComponent customModelData, BiConsumer<Player, ItemStack> action) {
        if (customModelData == null) { return; }
        actions.put(customModelData, action);
        String modelId = primaryModelId(customModelData);
        if (modelId != null) {
            actionsByModelId.put(modelId, action);
        }
    }

    /**
     * Registers custom right click behavior for an item.
     * Still uses custom model data to track it, items without custom model data may cause issues.
     *
     * @param item The item to associate with the custom behavior
     * @param action The code to execute when the item is used
     */
    public static void register(ItemStack item, BiConsumer<Player, ItemStack> action) {
        if (item == null) { return; }
        actions.put(item.getItemMeta().getCustomModelDataComponent(), action);
    }

    public static BiConsumer<Player, ItemStack> getAction(CustomModelDataComponent customModelData) {
        if (customModelData == null) {
            return null;
        }

        String modelId = primaryModelId(customModelData);
        if (modelId != null) {
            BiConsumer<Player, ItemStack> byModel = actionsByModelId.get(modelId);
            if (byModel != null) {
                return byModel;
            }
        }

        return actions.get(customModelData);
    }

    private static String primaryModelId(CustomModelDataComponent customModelData) {
        List<String> modelIds = customModelData.getStrings();
        if (modelIds == null || modelIds.isEmpty()) {
            return null;
        }
        String first = modelIds.get(0);
        if (first == null || first.isBlank()) {
            return null;
        }
        return first;
    }
}
