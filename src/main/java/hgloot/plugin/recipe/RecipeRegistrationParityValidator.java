package hgloot.plugin.recipe;

import dev.zxdzero.ZxdzeroEvents.registries.RecipeManager;
import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.FingerprintNode;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialSetChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.ResultIdentity;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapelessLayout;
import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.ultraroyales.OriginalLegendaryRecipes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

public final class RecipeRegistrationParityValidator {
    private RecipeRegistrationParityValidator() {
    }

    public static void validate(Plugin plugin, RecipeBookRegistry registry) {
        validateShaped(plugin, registry, "life_elixir", "life_elixir");
        validateGildedFamily(plugin);
        validateShaped(plugin, registry, "light_apple", "light_apple");
        validateShaped(plugin, registry, "packed_ice", "packed_ice_from_water");
        validateShaped(plugin, registry, "ghost_knife", "hg_ghost_knife");
        validateShaped(plugin, registry, "frog_shoes", "hg_frog_shoes");
        validateShaped(plugin, registry, "fletchers_pack", "hg_fletchers_pack");
        validateShaped(plugin, registry, "divers_sponge", "hg_divers_sponge");
        validateShaped(plugin, registry, "scavengers_rod", "hg_scavengers_rod");
        validateShaped(plugin, registry, "wind_pack", "hg_wind_pack");
        validateShaped(plugin, registry, "web_bundle", "hg_web_bundle");
        validateShaped(plugin, registry, "tracking_compass", "hg_tracking_compass");
        validatePedestalRecipes(plugin, registry);
    }

    private static void validateShaped(Plugin plugin, RecipeBookRegistry registry, String catalogId, String runtimeId) {
        RecipeBookRegistry.VisibleRecipe visible = requireVisible(registry, catalogId);
        if (!(visible.layout() instanceof ShapedLayout expected)) {
            throw new IllegalStateException("Catalog recipe is not shaped: " + catalogId);
        }
        Recipe recipe = Bukkit.getRecipe(new NamespacedKey(plugin, runtimeId));
        if (!(recipe instanceof ShapedRecipe actual)) {
            throw new IllegalStateException("Missing shaped Bukkit recipe: " + runtimeId);
        }
        compareResult(catalogId, result(visible), actual.getResult());
        compareShaped(catalogId, expected, actual);
    }

    private static void validateShapeless(Plugin plugin, RecipeBookRegistry registry, String catalogId, String runtimeId) {
        RecipeBookRegistry.VisibleRecipe visible = requireVisible(registry, catalogId);
        if (!(visible.layout() instanceof ShapelessLayout expected)) {
            throw new IllegalStateException("Catalog recipe is not shapeless: " + catalogId);
        }
        Recipe recipe = Bukkit.getRecipe(new NamespacedKey(plugin, runtimeId));
        if (!(recipe instanceof ShapelessRecipe actual)) {
            throw new IllegalStateException("Missing shapeless Bukkit recipe: " + runtimeId);
        }
        compareResult(catalogId, result(visible), actual.getResult());
        List<String> expectedChoices = expected.ingredients().stream()
            .map(IngredientSpec::choice)
            .map(IngredientChoice::fingerprint)
            .map(RecipeRegistrationParityValidator::fingerprintText)
            .sorted()
            .toList();
        List<String> actualChoices = actual.getChoiceList().stream()
            .map(RecipeRegistrationParityValidator::normalizeChoice)
            .map(RecipeRegistrationParityValidator::fingerprintText)
            .sorted()
            .toList();
        if (!expectedChoices.equals(actualChoices)) {
            throw new IllegalStateException("Shapeless ingredient mismatch for " + catalogId
                + ": expected=" + expectedChoices + " actual=" + actualChoices);
        }
    }

    private static void compareShaped(String id, ShapedLayout expected, ShapedRecipe actual) {
        int minRow = expected.cells().keySet().stream().mapToInt(GridPosition::row).min()
            .orElseThrow(() -> new IllegalStateException("Shaped recipe has no ingredients: " + id));
        int maxRow = expected.cells().keySet().stream().mapToInt(GridPosition::row).max().orElseThrow();
        int minColumn = expected.cells().keySet().stream().mapToInt(GridPosition::column).min().orElseThrow();
        int maxColumn = expected.cells().keySet().stream().mapToInt(GridPosition::column).max().orElseThrow();
        int occupiedHeight = maxRow - minRow + 1;
        int occupiedWidth = maxColumn - minColumn + 1;
        List<String> shape = canonicalShape(actual.getShape(), occupiedWidth, occupiedHeight);
        Map<Character, RecipeChoice> choices = actual.getChoiceMap();
        for (int row = 0; row < occupiedHeight; row++) {
            for (int column = 0; column < occupiedWidth; column++) {
                IngredientSpec expectedIngredient = expected.cells().get(new GridPosition(row + minRow, column + minColumn));
                char symbol = shape.get(row).charAt(column);
                RecipeChoice actualChoice = symbol == ' ' ? null : choices.get(symbol);
                if (expectedIngredient == null && actualChoice == null) {
                    continue;
                }
                if (expectedIngredient == null || actualChoice == null || expectedIngredient.requiredAmount() != 1) {
                    throw new IllegalStateException("Shaped occupancy mismatch for " + id + " at "
                        + (row + minRow) + "," + (column + minColumn));
                }
                FingerprintNode expectedFingerprint = expectedIngredient.choice().fingerprint();
                FingerprintNode actualFingerprint = normalizeChoice(actualChoice);
                if (!expectedFingerprint.equals(actualFingerprint)) {
                    throw new IllegalStateException("Shaped ingredient mismatch for " + id + " at "
                        + (row + minRow) + "," + (column + minColumn)
                        + ": expected=" + fingerprintText(expectedFingerprint)
                        + " actual=" + fingerprintText(actualFingerprint));
                }
            }
        }
    }

    private static void validateGildedFamily(Plugin plugin) {
        for (Material material : RecipeBookCatalog.GILDED_SWORD_MATERIALS) {
            String materialId = material.name().toLowerCase();
            Recipe recipe = Bukkit.getRecipe(new NamespacedKey(plugin, "gilded_" + materialId));
            if (!(recipe instanceof ShapedRecipe actual)) {
                throw new IllegalStateException("Missing Gilded Sword recipe for " + material);
            }
            if (!ResultIdentity.from(Items.gildedSword(material)).equals(ResultIdentity.from(actual.getResult()))) {
                throw new IllegalStateException("Gilded Sword result mismatch for " + material);
            }
            validateGildedLayout(material, actual.getShape(), actual.getChoiceMap());
        }
    }

    static void validateGildedLayout(Material baseMaterial, String[] rawShape, Map<Character, RecipeChoice> choices) {
        List<String> shape = canonicalShape(rawShape, 3, 3);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                boolean center = row == 1 && column == 1;
                boolean quartz = (row == 0 && column == 1)
                    || (row == 1 && (column == 0 || column == 2))
                    || (row == 2 && column == 1);
                char symbol = shape.get(row).charAt(column);
                RecipeChoice actualChoice = symbol == ' ' ? null : choices.get(symbol);
                if (!center && !quartz) {
                    if (actualChoice != null) {
                        throw new IllegalStateException("Gilded Sword occupancy mismatch for " + baseMaterial
                            + " at " + row + "," + column + ": actualShape=" + shape);
                    }
                    continue;
                }
                Material expectedMaterial = center ? baseMaterial : Material.QUARTZ;
                assertChoice("Gilded Sword " + expectedMaterial + " at " + row + "," + column,
                    actualChoice, new MaterialChoice(expectedMaterial));
            }
        }
    }

    private static void validatePedestalRecipes(Plugin plugin, RecipeBookRegistry registry) {
        String namespace = plugin.getName().toLowerCase();
        for (String recipeId : RecipeBookCatalog.approvedPublicLegendaryRecipeIds()) {
            OriginalLegendaryRecipes.LegendaryRecipeDefinition source = OriginalLegendaryRecipes.byId(recipeId)
                .orElseThrow(() -> new IllegalStateException("Missing approved legendary definition: " + recipeId));
            RecipeBookRegistry.VisibleRecipe visible = requireVisible(registry, source.id());
            RecipeManager.PedestalRecipe runtime = RecipeManager.getRecipe(namespace + ":" + source.id());
            if (runtime == null) {
                throw new IllegalStateException("Missing pedestal recipe: " + source.id());
            }
            compareResult(source.id(), result(visible), runtime.result());
            Map<Material, Integer> expected = aggregateMaterials(visible.layout().ingredients());
            Map<Material, Integer> actual = new HashMap<>();
            for (ItemStack ingredient : runtime.ingredients()) {
                actual.merge(ingredient.getType(), ingredient.getAmount(), Integer::sum);
            }
            if (!expected.equals(actual)) {
                throw new IllegalStateException("Pedestal ingredient mismatch for " + source.id()
                    + ": expected=" + expected + " actual=" + actual);
            }
        }
    }

    private static Map<Material, Integer> aggregateMaterials(List<IngredientSpec> ingredients) {
        Map<Material, Integer> totals = new HashMap<>();
        for (IngredientSpec ingredient : ingredients) {
            if (!(ingredient.choice() instanceof MaterialChoice choice)) {
                throw new IllegalStateException("Pedestal parity currently requires material choices");
            }
            totals.merge(choice.material(), ingredient.requiredAmount(), Integer::sum);
        }
        return totals;
    }

    private static RecipeBookRegistry.VisibleRecipe requireVisible(RecipeBookRegistry registry, String id) {
        return registry.resolveVisible(new NamespacedKey("hydra", id), RecipeMode.BATTLE_ROYALE)
            .orElseThrow(() -> new IllegalStateException("Missing catalog recipe: " + id));
    }

    private static ItemStack result(RecipeBookRegistry.VisibleRecipe visible) {
        if (!(visible.view() instanceof AvailableView available)) {
            throw new IllegalStateException("Recipe is not available: " + visible.id());
        }
        return available.result().createItem();
    }

    private static void compareResult(String id, ItemStack expected, ItemStack actual) {
        ResultIdentity expectedIdentity = ResultIdentity.from(expected);
        ResultIdentity actualIdentity = ResultIdentity.from(actual);
        if (!expectedIdentity.equals(actualIdentity)) {
            throw new IllegalStateException("Result mismatch for " + id
                + ": expected=" + expectedIdentity + " actual=" + actualIdentity);
        }
    }

    private static void assertChoice(String id, RecipeChoice actual, IngredientChoice expected) {
        if (actual == null || !expected.fingerprint().equals(normalizeChoice(actual))) {
            throw new IllegalStateException("Recipe choice mismatch for " + id);
        }
    }

    private static FingerprintNode normalizeChoice(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            List<Material> choices = materials.getChoices();
            if (choices.size() == 1) {
                return new MaterialChoice(choices.getFirst()).fingerprint();
            }
            return new MaterialSetChoice(choices).fingerprint();
        }
        if (choice instanceof RecipeChoice.ExactChoice exact) {
            List<String> values = exact.getChoices().stream()
                .map(ItemStack::serializeAsBytes)
                .map(bytes -> Integer.toHexString(java.util.Arrays.hashCode(bytes)))
                .sorted()
                .map(hash -> "runtime-bytes:" + hash)
                .toList();
            return new FingerprintNode("exact_choice", values);
        }
        throw new IllegalStateException("Unsupported Bukkit recipe choice: " + choice.getClass().getName());
    }

    private static String fingerprintText(FingerprintNode node) {
        return node.domain() + node.values();
    }

    static List<String> canonicalShape(String[] shape, int width, int height) {
        if (shape == null || shape.length != height || width <= 0 || height <= 0) {
            throw new IllegalStateException("Invalid shaped recipe dimensions");
        }
        List<String> canonical = new ArrayList<>(height);
        for (String row : shape) {
            String value = row == null ? "" : row;
            if (value.length() > width) {
                throw new IllegalStateException("Shaped recipe row exceeds expected width: " + value);
            }
            canonical.add(value + " ".repeat(width - value.length()));
        }
        return List.copyOf(canonical);
    }
}
