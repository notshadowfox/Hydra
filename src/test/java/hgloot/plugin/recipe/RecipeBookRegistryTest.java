package hgloot.plugin.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.HiddenView;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.ExactItemChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.IntegerPdcRequirement;
import hgloot.plugin.recipe.RecipeBookDefinition.PdcItemChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.PreviewPrototype;
import hgloot.plugin.recipe.RecipeBookDefinition.StringPdcRequirement;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePlacement;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePresentation;
import hgloot.plugin.recipe.RecipeBookDefinition.ResultPrototype;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapelessLayout;
import java.util.EnumMap;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class RecipeBookRegistryTest {
    @BeforeAll
    static void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void requiresAnExplicitViewForEveryMode() {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        modes.put(RecipeMode.BATTLE_ROYALE, available(0));
        registry.register(definition("missing_hg", modes));

        assertThrows(IllegalStateException.class, registry::freeze);
    }

    @Test
    void rejectsDuplicateIndexOrder() {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        registry.register(visibleInBothModes("first", 0));
        registry.register(visibleInBothModes("second", 0));

        assertThrows(IllegalStateException.class, registry::freeze);
    }

    @Test
    void hiddenRecipesHaveNoIndexOrResolvablePage() {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        modes.put(RecipeMode.BATTLE_ROYALE, available(0));
        modes.put(RecipeMode.HUNGER_GAMES, new HiddenView());
        registry.register(definition("hidden_hg", modes));
        registry.freeze();

        assertEquals(1, registry.visibleRecipes(RecipeMode.BATTLE_ROYALE, RecipeCategory.BASIC).size());
        assertEquals(0, registry.visibleRecipes(RecipeMode.HUNGER_GAMES, RecipeCategory.BASIC).size());
        assertFalse(registry.resolveVisible(new NamespacedKey("hydra", "hidden_hg"), RecipeMode.HUNGER_GAMES).isPresent());
    }

    @Test
    void exactItemFingerprintIsStableAcrossClonesAndHasReadableDiagnostics() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Recipe Token"));
        item.setItemMeta(meta);

        var first = new ExactItemChoice(item).fingerprint();
        var second = new ExactItemChoice(item.clone()).fingerprint();

        assertEquals(first, second);
        assertTrue(first.values().stream().anyMatch(value -> value.equals("material:minecraft:paper")));
        assertTrue(first.values().stream().anyMatch(value -> value.startsWith("runtime-version-local-sha256:")));
    }

    @Test
    void pdcChoiceRejectsContradictoryPrimitiveTypesForOneKey() {
        NamespacedKey key = new NamespacedKey("hydra", "item_id");
        ItemStack preview = new ItemStack(Material.PAPER);
        ItemMeta meta = preview.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "token");
        preview.setItemMeta(meta);

        assertThrows(IllegalArgumentException.class, () -> new PdcItemChoice(
            java.util.Set.of(Material.PAPER),
            List.of(new StringPdcRequirement(key, "token"), new IntegerPdcRequirement(key, 1)),
            new PreviewPrototype(preview)
        ));
    }

    @Test
    void runtimeRegistrarCreatesTheBukkitRecipeFromTheFrozenModel() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        RecipeBookRegistry registry = new RecipeBookRegistry();
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        modes.put(RecipeMode.BATTLE_ROYALE, available(0));
        modes.put(RecipeMode.HUNGER_GAMES, available(0));
        registry.register(new RecipeBookDefinition(
            new NamespacedKey("hydra", "light_apple"),
            new ShapelessLayout(List.of(new IngredientSpec(new MaterialChoice(Material.STONE), 1))),
            modes
        ));
        registry.freeze();

        RecipeRuntimeRegistrar.registerBukkitRecipes(plugin, registry);

        assertTrue(Bukkit.getRecipe(new NamespacedKey(plugin, "light_apple")) instanceof ShapelessRecipe);
    }

    private static RecipeBookDefinition visibleInBothModes(String id, int order) {
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        modes.put(RecipeMode.BATTLE_ROYALE, available(order));
        modes.put(RecipeMode.HUNGER_GAMES, available(order));
        return definition(id, modes);
    }

    private static RecipeBookDefinition definition(
        String id,
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes
    ) {
        return new RecipeBookDefinition(
            new NamespacedKey("hydra", id),
            new ShapelessLayout(List.of(new IngredientSpec(new MaterialChoice(Material.STONE), 1))),
            modes
        );
    }

    private static AvailableView available(int order) {
        return new AvailableView(
            new RecipePresentation(Component.text("Test"), List.of()),
            new ResultPrototype(new ItemStack(Material.DIAMOND)),
            new RecipePlacement(RecipeCategory.BASIC, order)
        );
    }
}
