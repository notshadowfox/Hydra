package hgloot.plugin.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

public final class HydraItemRegistry implements ItemDefinitionRegistry {
    private final Map<String, Supplier<ItemStack>> factories = new LinkedHashMap<>();

    @Override
    public void register(String key, Supplier<ItemStack> factory) {
        String normalized = normalizeKey(key);
        factories.put(normalized, factory);
    }

    @Override
    public Supplier<ItemStack> findFactory(String key) {
        if (key == null) {
            return null;
        }
        return factories.get(normalizeKey(key));
    }

    @Override
    public ItemStack create(String key) {
        Supplier<ItemStack> factory = findFactory(key);
        if (factory == null) {
            return null;
        }
        ItemStack stack = factory.get();
        return stack == null ? null : stack.clone();
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(factories.keySet()));
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
