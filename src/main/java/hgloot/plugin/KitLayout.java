package hgloot.plugin;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Slot mappings only. Never stores item stacks in player kit-layout data. */
public final class KitLayout {
    public static final int STORAGE_SLOTS = 36;
    public static final int OFFHAND_SLOT = 40;

    private KitLayout() {
    }

    public static Map<String, Integer> normalize(KitDefinition definition, Map<String, Integer> saved) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (String itemId : definition.previewItems().keySet()) {
            Integer slot = saved == null ? null : saved.get(itemId);
            if (slot != null && ((slot >= 0 && slot < STORAGE_SLOTS)
                || (slot == OFFHAND_SLOT && definition.allowsOffhand(itemId))) && used.add(slot)) {
                result.put(itemId, slot);
            }
        }
        for (String itemId : definition.previewItems().keySet()) {
            if (result.containsKey(itemId)) continue;
            int preferred = definition.defaultSlots().getOrDefault(itemId, -1);
            if (preferred < 0 || (preferred >= STORAGE_SLOTS
                && !(preferred == OFFHAND_SLOT && definition.allowsOffhand(itemId)))
                || used.contains(preferred)) {
                preferred = firstFree(used);
            }
            used.add(preferred);
            result.put(itemId, preferred);
        }
        return Map.copyOf(result);
    }

    public static void place(PlayerInventory inventory, Map<String, ItemStack> items, Map<String, Integer> slots) {
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
            int slot = slots.get(entry.getKey());
            if (slot == OFFHAND_SLOT) inventory.setItemInOffHand(entry.getValue().clone());
            else inventory.setItem(slot, entry.getValue().clone());
        }
    }

    public static Optional<Map<String, Integer>> capture(KitDefinition definition, ItemStack[] storage, ItemStack offhand) {
        if (storage == null || storage.length < STORAGE_SLOTS) return Optional.empty();
        Map<String, ItemStack> expected = definition.previewItems();
        Map<String, Integer> found = new LinkedHashMap<>();
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack actual = storage[slot];
            if (actual == null || actual.getType() == Material.AIR) continue;
            String match = null;
            for (Map.Entry<String, ItemStack> entry : expected.entrySet()) {
                if (actual.getAmount() == entry.getValue().getAmount()
                    && actual.isSimilar(entry.getValue()) && !found.containsKey(entry.getKey())) {
                    match = entry.getKey();
                    break;
                }
            }
            if (match == null) return Optional.empty();
            found.put(match, slot);
        }
        if (offhand != null && offhand.getType() != Material.AIR) {
            String match = null;
            for (Map.Entry<String, ItemStack> entry : expected.entrySet()) {
                if (definition.allowsOffhand(entry.getKey()) && !found.containsKey(entry.getKey())
                    && offhand.getAmount() == entry.getValue().getAmount()
                    && offhand.isSimilar(entry.getValue())) {
                    match = entry.getKey();
                    break;
                }
            }
            if (match == null) return Optional.empty();
            found.put(match, OFFHAND_SLOT);
        }
        return found.size() == expected.size() ? Optional.of(Map.copyOf(found)) : Optional.empty();
    }

    private static int firstFree(Set<Integer> used) {
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            if (!used.contains(slot)) return slot;
        }
        throw new IllegalArgumentException("Kit has more items than storage slots");
    }
}
