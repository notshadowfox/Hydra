package hgloot.plugin.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetiredUtilityRecipeTest {
    @Test
    void obsidianIsAbsentFromCatalogRuntimeAndAssistedCrafting() throws Exception {
        String catalog = read("src/main/java/hgloot/plugin/recipe/RecipeBookCatalog.java");
        String registrar = read("src/main/java/hgloot/plugin/recipe/RecipeRuntimeRegistrar.java");
        String validator = read("src/main/java/hgloot/plugin/recipe/RecipeRegistrationParityValidator.java");
        String announcements = read("src/main/java/hgloot/plugin/announcement/SituationalAnnouncementService.java");

        assertFalse(catalog.contains("\"obsidian\""));
        assertFalse(validator.contains("validateShapeless(plugin, registry, \"obsidian\""));
        assertFalse(announcements.contains("recipe(\"obsidian\""));
        assertTrue(registrar.contains("removeRecipe(new NamespacedKey(plugin, \"obsidian_from_buckets\"))"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
