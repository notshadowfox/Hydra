package hgloot.plugin.registry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class HydraRecipeRegistry implements RecipeDefinitionRegistry {
    private final Set<String> keys = new LinkedHashSet<>();

    @Override
    public void register(String key) {
        keys.add(normalizeKey(key));
    }

    @Override
    public boolean contains(String key) {
        if (key == null) {
            return false;
        }
        return keys.contains(normalizeKey(key));
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
