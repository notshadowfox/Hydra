package hgloot.plugin.recipe;

import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapelessLayout;
import hgloot.plugin.ultraroyales.Items;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

public final class RecipeRuntimeRegistrar {
    private static final Map<String, String> RUNTIME_IDS = Map.ofEntries(
        Map.entry("life_elixir", "life_elixir"), Map.entry("light_apple", "light_apple"),
        Map.entry("packed_ice", "packed_ice_from_water"),
        Map.entry("ghost_knife", "hg_ghost_knife"), Map.entry("frog_shoes", "hg_frog_shoes"),
        Map.entry("fletchers_pack", "hg_fletchers_pack"), Map.entry("divers_sponge", "hg_divers_sponge"),
        Map.entry("scavengers_rod", "hg_scavengers_rod"), Map.entry("wind_pack", "hg_wind_pack"),
        Map.entry("web_bundle", "hg_web_bundle"), Map.entry("tracking_compass", "hg_tracking_compass")
    );

    private RecipeRuntimeRegistrar() {
    }

    public static String catalogIdFor(Plugin plugin, Recipe recipe) {
        if (plugin == null || !(recipe instanceof Keyed keyed)) return null;
        NamespacedKey key = keyed.getKey();
        String expectedNamespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        if (!expectedNamespace.equals(key.getNamespace())) return null;
        String runtimeId = key.getKey();
        if (runtimeId.startsWith("gilded_")) return "gilded_sword";
        return RUNTIME_IDS.entrySet().stream()
            .filter(entry -> entry.getValue().equals(runtimeId))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    public static void registerBukkitRecipes(Plugin plugin, RecipeBookRegistry registry) {
        Bukkit.removeRecipe(new NamespacedKey(plugin, "obsidian_from_buckets"));
        Bukkit.removeRecipe(new NamespacedKey(plugin, "super_stew"));
        Bukkit.removeRecipe(new NamespacedKey(plugin, "hg_smoke_bomb"));
        for (RecipeBookRegistry.VisibleRecipe recipe : registry.visibleRecipes(
            RecipeMode.BATTLE_ROYALE,
            RecipeCategory.BASIC
        )) {
            String id = recipe.id().getKey();
            if (id.equals("gilded_sword")) {
                registerGildedSwordFamily(plugin);
                continue;
            }
            String runtimeId = RUNTIME_IDS.get(id);
            if (runtimeId == null) {
                throw new IllegalStateException("No Bukkit runtime ID for recipe " + recipe.id());
            }
            registerRecipe(plugin, runtimeId, recipe);
        }
    }

    private static void registerRecipe(Plugin plugin, String runtimeId, RecipeBookRegistry.VisibleRecipe recipe) {
        if (!(recipe.view() instanceof AvailableView available)) {
            throw new IllegalStateException("Cannot register unavailable recipe " + recipe.id());
        }
        NamespacedKey key = new NamespacedKey(plugin, runtimeId);
        Bukkit.removeRecipe(key);
        boolean registered = switch (recipe.layout()) {
            case ShapedLayout shaped -> Bukkit.addRecipe(createShaped(key, available, shaped));
            case ShapelessLayout shapeless -> Bukkit.addRecipe(createShapeless(key, available, shapeless));
        };
        if (!registered) {
            throw new IllegalStateException("Bukkit rejected recipe registration for " + key);
        }
    }

    private static ShapedRecipe createShaped(NamespacedKey key, AvailableView available, ShapedLayout layout) {
        ShapedRecipe recipe = new ShapedRecipe(key, available.result().createItem());
        Map<RecipeBookDefinition.FingerprintNode, Character> symbols = new LinkedHashMap<>();
        Map<Character, IngredientChoice> choices = new LinkedHashMap<>();
        String[] shape = new String[layout.height()];
        char nextSymbol = 'A';
        for (int row = 0; row < layout.height(); row++) {
            StringBuilder line = new StringBuilder(layout.width());
            for (int column = 0; column < layout.width(); column++) {
                IngredientSpec ingredient = layout.cells().get(new GridPosition(row, column));
                if (ingredient == null) {
                    line.append(' ');
                    continue;
                }
                if (ingredient.requiredAmount() != 1) {
                    throw new IllegalStateException("Shaped recipe cells must require exactly one item: " + key);
                }
                Character symbol = symbols.get(ingredient.choice().fingerprint());
                if (symbol == null) {
                    symbol = nextSymbol++;
                    symbols.put(ingredient.choice().fingerprint(), symbol);
                    choices.put(symbol, ingredient.choice());
                }
                line.append(symbol);
            }
            shape[row] = line.toString();
        }
        recipe.shape(shape);
        choices.forEach((symbol, choice) -> recipe.setIngredient(symbol, toBukkitChoice(choice)));
        return recipe;
    }

    private static ShapelessRecipe createShapeless(
        NamespacedKey key,
        AvailableView available,
        ShapelessLayout layout
    ) {
        ShapelessRecipe recipe = new ShapelessRecipe(key, available.result().createItem());
        for (IngredientSpec ingredient : layout.ingredients()) {
            for (int count = 0; count < ingredient.requiredAmount(); count++) {
                recipe.addIngredient(toBukkitChoice(ingredient.choice()));
            }
        }
        return recipe;
    }

    private static RecipeChoice toBukkitChoice(IngredientChoice choice) {
        return switch (choice) {
            case MaterialChoice material -> new RecipeChoice.MaterialChoice(material.material());
            case RecipeBookDefinition.MaterialSetChoice materials ->
                new RecipeChoice.MaterialChoice(materials.materials().stream().toList());
            case RecipeBookDefinition.ExactItemChoice ignored ->
                throw new IllegalStateException("Exact item choices are not supported by Bukkit crafting registration yet");
            case RecipeBookDefinition.PdcItemChoice ignored ->
                throw new IllegalStateException("PDC choices are not supported by Bukkit crafting registration");
        };
    }

    private static void registerGildedSwordFamily(Plugin plugin) {
        for (Material material : RecipeBookCatalog.GILDED_SWORD_MATERIALS) {
            String id = material.name().toLowerCase();
            NamespacedKey key = new NamespacedKey(plugin, "gilded_" + id);
            Bukkit.removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, Items.gildedSword(material));
            recipe.shape(" Q ", "QSQ", " Q ");
            recipe.setIngredient('Q', Material.QUARTZ);
            recipe.setIngredient('S', new RecipeChoice.MaterialChoice(material));
            if (!Bukkit.addRecipe(recipe)) {
                throw new IllegalStateException("Bukkit rejected Gilded Sword recipe registration for " + key);
            }
        }
    }
}
