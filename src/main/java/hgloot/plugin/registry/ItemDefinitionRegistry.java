package hgloot.plugin.registry;

import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

public interface ItemDefinitionRegistry {
    void register(String key, Supplier<ItemStack> factory);

    Supplier<ItemStack> findFactory(String key);

    ItemStack create(String key);

    Set<String> keys();
}
