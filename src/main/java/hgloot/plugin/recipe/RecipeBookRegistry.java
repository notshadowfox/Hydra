package hgloot.plugin.recipe;

import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.DisabledView;
import hgloot.plugin.recipe.RecipeBookDefinition.HiddenView;
import hgloot.plugin.recipe.RecipeBookDefinition.ModeRecipeView;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePlacement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;

public final class RecipeBookRegistry {
    private final Map<NamespacedKey, RecipeBookDefinition> pending = new HashMap<>();
    private Map<NamespacedKey, RecipeBookDefinition> frozenEntries = Map.of();
    private Map<IndexKey, List<VisibleRecipe>> frozenIndexes = Map.of();
    private boolean frozen;

    public void register(RecipeBookDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("Recipe book registry is frozen");
        }
        Objects.requireNonNull(definition, "definition");
        RecipeBookDefinition previous = pending.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate recipe ID: " + definition.id());
        }
    }

    public void freeze() {
        if (frozen) {
            return;
        }
        Map<IndexKey, Map<Integer, NamespacedKey>> occupied = new HashMap<>();
        Map<IndexKey, List<VisibleRecipe>> indexes = new HashMap<>();

        for (RecipeBookDefinition definition : pending.values()) {
            for (RecipeMode mode : RecipeMode.values()) {
                ModeRecipeView view = definition.modes().get(mode);
                if (view == null) {
                    throw new IllegalStateException("Recipe " + definition.id() + " has no explicit " + mode + " view");
                }
                if (view instanceof HiddenView) {
                    continue;
                }
                RecipePlacement placement = placement(view);
                IndexKey key = new IndexKey(mode, placement.category());
                NamespacedKey conflict = occupied
                    .computeIfAbsent(key, ignored -> new HashMap<>())
                    .putIfAbsent(placement.order(), definition.id());
                if (conflict != null) {
                    throw new IllegalStateException(
                        "Duplicate recipe order " + placement.order() + " for " + key + ": " + conflict + " and " + definition.id()
                    );
                }
                indexes.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new VisibleRecipe(definition.id(), definition.layout(), mode, view));
            }
        }

        indexes.values().forEach(list -> list.sort(Comparator.comparingInt(recipe -> placement(recipe.view()).order())));
        Map<IndexKey, List<VisibleRecipe>> immutableIndexes = new HashMap<>();
        indexes.forEach((key, value) -> immutableIndexes.put(key, List.copyOf(value)));
        frozenEntries = Map.copyOf(pending);
        frozenIndexes = Map.copyOf(immutableIndexes);
        pending.clear();
        frozen = true;
    }

    public List<VisibleRecipe> visibleRecipes(RecipeMode mode, RecipeCategory category) {
        requireFrozen();
        return frozenIndexes.getOrDefault(new IndexKey(mode, category), List.of());
    }

    public Optional<VisibleRecipe> resolveVisible(NamespacedKey id, RecipeMode mode) {
        requireFrozen();
        RecipeBookDefinition definition = frozenEntries.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        ModeRecipeView view = definition.modes().get(mode);
        if (view == null || view instanceof HiddenView) {
            return Optional.empty();
        }
        return Optional.of(new VisibleRecipe(id, definition.layout(), mode, view));
    }

    public boolean isFrozen() {
        return frozen;
    }

    private static RecipePlacement placement(ModeRecipeView view) {
        return switch (view) {
            case AvailableView available -> available.placement();
            case DisabledView disabled -> disabled.placement();
            case HiddenView ignored -> throw new IllegalArgumentException("Hidden recipe has no placement");
        };
    }

    private void requireFrozen() {
        if (!frozen) {
            throw new IllegalStateException("Recipe book registry is not frozen");
        }
    }

    private record IndexKey(RecipeMode mode, RecipeCategory category) {
    }

    public record VisibleRecipe(
        NamespacedKey id,
        RecipeBookDefinition.RecipeLayout layout,
        RecipeMode mode,
        ModeRecipeView view
    ) {
    }
}
