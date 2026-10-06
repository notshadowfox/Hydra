package hgloot.plugin.announcement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.recipe.RecipeBookDefinition;
import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePlacement;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePresentation;
import hgloot.plugin.recipe.RecipeBookDefinition.ResultPrototype;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookRegistry;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SituationalAnnouncementServiceTest {
    @BeforeAll
    static void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void authoritativeHgUtilitiesAreAvailableToEasyCraft() {
        RecipeBookRegistry registry = registryWithUtilities(List.of(
            "life_elixir", "gilded_sword", "light_apple", "packed_ice",
            "ghost_knife", "frog_shoes", "fletchers_pack", "divers_sponge", "scavengers_rod",
            "wind_pack", "web_bundle", "tracking_compass", "future_utility"
        ));
        SituationalAnnouncementService service = new SituationalAnnouncementService(
            MockBukkit.createMockPlugin(), () -> registry
        );

        List.of(
            "life_elixir",
            "gilded_sword",
            "light_apple",
            "packed_ice",
            "ghost_knife",
            "frog_shoes",
            "fletchers_pack",
            "divers_sponge",
            "scavengers_rod",
            "wind_pack",
            "web_bundle",
            "tracking_compass",
            "future_utility"
        ).forEach(recipeId -> assertTrue(service.supportsRecipe(recipeId), recipeId));
    }

    private RecipeBookRegistry registryWithUtilities(List<String> ids) {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        int order = 0;
        for (String id : ids) {
            EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
            AvailableView view = new AvailableView(
                new RecipePresentation(Component.text(id), List.of()),
                new ResultPrototype(new ItemStack(Material.PAPER)),
                new RecipePlacement(RecipeCategory.BASIC, order++)
            );
            modes.put(RecipeMode.BATTLE_ROYALE, view);
            modes.put(RecipeMode.HUNGER_GAMES, view);
            registry.register(new RecipeBookDefinition(
                new NamespacedKey("hydra", id),
                new ShapedLayout(3, 3, Map.of(
                    new GridPosition(1, 1), new IngredientSpec(new MaterialChoice(Material.STONE), 1)
                )),
                modes
            ));
        }
        registry.freeze();
        return registry;
    }
}
