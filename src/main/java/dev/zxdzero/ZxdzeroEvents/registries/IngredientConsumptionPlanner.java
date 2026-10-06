package dev.zxdzero.ZxdzeroEvents.registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class IngredientConsumptionPlanner {
    private IngredientConsumptionPlanner() {
    }

    public static Optional<ConsumptionPlan> plan(ItemStack[] contents, List<ItemStack> ingredients) {
        Map<IngredientKey, Integer> remaining = aggregateRequirements(ingredients);
        if (remaining.isEmpty()) {
            return Optional.of(new ConsumptionPlan(List.of(), Map.of()));
        }

        List<SlotConsumption> removals = new ArrayList<>();
        Map<Integer, IngredientKey> expectedSlots = new HashMap<>();
        for (int slot = 0; slot < contents.length && !remaining.isEmpty(); slot++) {
            ItemStack item = contents[slot];
            IngredientKey key = IngredientKey.from(item);
            if (key == null) {
                continue;
            }

            int needed = remaining.getOrDefault(key, 0);
            if (needed <= 0) {
                continue;
            }

            int amount = Math.min(needed, item.getAmount());
            removals.add(new SlotConsumption(slot, amount));
            expectedSlots.put(slot, key);
            if (amount == needed) {
                remaining.remove(key);
            } else {
                remaining.put(key, needed - amount);
            }
        }

        if (!remaining.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ConsumptionPlan(removals, expectedSlots));
    }

    private static Map<IngredientKey, Integer> aggregateRequirements(List<ItemStack> ingredients) {
        Map<IngredientKey, Integer> required = new HashMap<>();
        for (ItemStack ingredient : ingredients) {
            IngredientKey key = IngredientKey.from(ingredient);
            if (key != null) {
                required.merge(key, ingredient.getAmount(), Math::addExact);
            }
        }
        return required;
    }

    public record SlotConsumption(int slot, int amount) {
        public SlotConsumption {
            if (slot < 0 || amount <= 0) {
                throw new IllegalArgumentException("Invalid slot consumption");
            }
        }
    }

    public static final class ConsumptionPlan {
        private final List<SlotConsumption> removals;
        private final Map<Integer, IngredientKey> expectedSlots;

        private ConsumptionPlan(List<SlotConsumption> removals, Map<Integer, IngredientKey> expectedSlots) {
            this.removals = List.copyOf(removals);
            this.expectedSlots = Map.copyOf(expectedSlots);
        }

        public List<SlotConsumption> removals() {
            return removals;
        }

        public boolean apply(PlayerInventory inventory) {
            ItemStack[] contents = inventory.getContents();
            for (SlotConsumption removal : removals) {
                if (removal.slot() >= contents.length) {
                    return false;
                }
                ItemStack item = contents[removal.slot()];
                if (!expectedSlots.get(removal.slot()).equals(IngredientKey.from(item))
                        || item.getAmount() < removal.amount()) {
                    return false;
                }
            }

            ItemStack[] updated = contents.clone();
            for (SlotConsumption removal : removals) {
                ItemStack item = updated[removal.slot()].clone();
                int remaining = item.getAmount() - removal.amount();
                if (remaining == 0) {
                    updated[removal.slot()] = null;
                } else {
                    item.setAmount(remaining);
                    updated[removal.slot()] = item;
                }
            }
            inventory.setContents(updated);
            return true;
        }
    }

    private record IngredientKey(Material material, Component displayName) {
        private static IngredientKey from(ItemStack item) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                return null;
            }
            Component displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : null;
            return new IngredientKey(item.getType(), displayName);
        }
    }
}
