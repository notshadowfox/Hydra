package hgloot.plugin.registry;

import java.util.Set;

public interface RecipeDefinitionRegistry {
    void register(String key);

    boolean contains(String key);

    Set<String> keys();
}
