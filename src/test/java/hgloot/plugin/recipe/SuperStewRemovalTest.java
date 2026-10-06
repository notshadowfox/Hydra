package hgloot.plugin.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SuperStewRemovalTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test
    void noCatalogEntryAndLegacyRuntimeRecipeIsUnregistered() throws Exception {
        var plugin = MockBukkit.createMockPlugin();
        var registry = new RecipeBookRegistry();
        registry.freeze();
        String catalog = Files.readString(Path.of("src/main/java/hgloot/plugin/recipe/RecipeBookCatalog.java"));
        assertFalse(catalog.contains("\"super_stew\""));

        NamespacedKey oldKey = new NamespacedKey(plugin, "super_stew");
        ShapedRecipe oldRecipe = new ShapedRecipe(oldKey, new ItemStack(Material.MUSHROOM_STEW));
        oldRecipe.shape("B").setIngredient('B', Material.BOWL);
        assertTrue(Bukkit.addRecipe(oldRecipe));
        RecipeRuntimeRegistrar.registerBukkitRecipes(plugin, registry);
        assertNull(Bukkit.getRecipe(oldKey));
    }
}
