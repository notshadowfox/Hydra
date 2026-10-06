package hgloot.plugin.recipe;

import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialSetChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePlacement;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePresentation;
import hgloot.plugin.recipe.RecipeBookDefinition.ResultPrototype;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapelessLayout;
import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.hunger.HungerGamesUtilityItems;
import hgloot.plugin.ultraroyales.OriginalLegendaryRecipes;
import hgloot.plugin.ultraroyales.OriginalLegendaryRecipes.LegendaryIngredient;
import hgloot.plugin.ultraroyales.OriginalLegendaryRecipes.LegendaryRecipeDefinition;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RecipeBookCatalog {
    private static final List<String> APPROVED_PUBLIC_LEGENDARY_RECIPE_IDS = List.of(
        "spider_staff",
        "dwarven_bow",
        "sponge_saber",
        "ghlochester",
        "knights_saddle",
        "electric_conch",
        "skeletal_barber"
    );

    public static final List<Material> GILDED_SWORD_MATERIALS = List.of(
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.COPPER_SWORD,
        Material.IRON_SWORD,
        Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD
    );
    public static final List<Material> PLANK_MATERIALS = List.of(
        Material.OAK_PLANKS,
        Material.SPRUCE_PLANKS,
        Material.BIRCH_PLANKS,
        Material.JUNGLE_PLANKS,
        Material.ACACIA_PLANKS,
        Material.DARK_OAK_PLANKS,
        Material.MANGROVE_PLANKS,
        Material.CHERRY_PLANKS,
        Material.BAMBOO_PLANKS,
        Material.CRIMSON_PLANKS,
        Material.WARPED_PLANKS
    );

    private RecipeBookCatalog() {
    }

    public static List<String> approvedPublicLegendaryRecipeIds() {
        return APPROVED_PUBLIC_LEGENDARY_RECIPE_IDS;
    }

    public static RecipeBookRegistry createRegistry() {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        registerBasicRecipes(registry);
        registerLegendaryRecipes(registry);
        registry.freeze();
        return registry;
    }

    private static void registerBasicRecipes(RecipeBookRegistry registry) {
        registry.register(shaped(
            "life_elixir",
            0,
            "Life Elixir",
            Items.lifeElixir(1),
            List.of("A drinkable Golden Apple.", "Grants 12 Absorption Hearts."),
            cells(
                cell(0, 0, Material.LAPIS_LAZULI), cell(0, 1, Material.LAPIS_LAZULI), cell(0, 2, Material.LAPIS_LAZULI),
                cell(1, 0, Material.LAPIS_LAZULI), cell(1, 1, Material.EXPERIENCE_BOTTLE), cell(1, 2, Material.LAPIS_LAZULI),
                cell(2, 0, Material.LAPIS_LAZULI), cell(2, 1, Material.GOLDEN_APPLE), cell(2, 2, Material.LAPIS_LAZULI)
            )
        ));

        Map<GridPosition, IngredientSpec> gildedCells = new LinkedHashMap<>();
        gildedCells.put(new GridPosition(0, 1), ingredient(Material.QUARTZ));
        gildedCells.put(new GridPosition(1, 0), ingredient(Material.QUARTZ));
        gildedCells.put(new GridPosition(1, 1), new IngredientSpec(new MaterialSetChoice(GILDED_SWORD_MATERIALS), 1));
        gildedCells.put(new GridPosition(1, 2), ingredient(Material.QUARTZ));
        gildedCells.put(new GridPosition(2, 1), ingredient(Material.QUARTZ));
        registry.register(definition(
            "gilded_sword",
            new ShapedLayout(3, 3, gildedCells),
            1,
            "Gilded Sword",
            Items.gildedSword(Material.DIAMOND_SWORD),
            List.of(
                "Slightly better than the original."
            ),
            RecipeCategory.BASIC
        ));

        registry.register(shaped(
            "light_apple",
            2,
            "Light Apple",
            lightApple(),
            List.of("A low cost Golden Apple."),
            cells(
                cell(0, 1, Material.GOLD_INGOT),
                cell(1, 0, Material.GOLD_INGOT), cell(1, 1, Material.APPLE), cell(1, 2, Material.GOLD_INGOT),
                cell(2, 1, Material.GOLD_INGOT)
            )
        ));

        registry.register(shaped(
            "packed_ice",
            3,
            "Packed Ice",
            new ItemStack(Material.PACKED_ICE, 8),
            List.of("Any listed plank type can be used."),
            cells(
                cell(0, 0, Material.COBBLESTONE), choiceCell(0, 1, PLANK_MATERIALS), cell(0, 2, Material.COBBLESTONE),
                choiceCell(1, 0, PLANK_MATERIALS), cell(1, 1, Material.WATER_BUCKET), choiceCell(1, 2, PLANK_MATERIALS),
                cell(2, 0, Material.COBBLESTONE), choiceCell(2, 1, PLANK_MATERIALS), cell(2, 2, Material.COBBLESTONE)
            )
        ));

        registry.register(shaped("ghost_knife", 5, "Ghost Knife", HungerGamesUtilityItems.ghostKnife(),
            List.of("Single-use dash utility. Maximum 3 crafts per match."), cells(
                cell(0, 1, Material.IRON_INGOT), cell(1, 0, Material.WIND_CHARGE), cell(1, 1, Material.STONE_SWORD),
                cell(1, 2, Material.WIND_CHARGE), cell(2, 1, Material.IRON_INGOT))));
        registry.register(shaped("frog_shoes", 6, "Frog Boots", HungerGamesUtilityItems.frogShoes(),
            List.of("Creates temporary lily pads. Hold Shift to disable."), cells(
                cell(0, 1, Material.DIAMOND), cell(1, 0, Material.LILY_PAD), cell(1, 1, Material.DIAMOND_BOOTS),
                cell(1, 2, Material.LILY_PAD), cell(2, 1, Material.SLIME_BLOCK))));
        registry.register(shaped("fletchers_pack", 7, "Fletcher's Pack", HungerGamesUtilityItems.fletchersPack(),
            List.of("Produces 8-12 normal arrows."), cells(
                cell(0, 0, Material.ARROW), cell(0, 1, Material.STICK), cell(0, 2, Material.ARROW),
                cell(1, 0, Material.STICK), cell(1, 1, Material.OAK_LOG), cell(1, 2, Material.STICK),
                cell(2, 0, Material.ARROW), cell(2, 1, Material.STICK), cell(2, 2, Material.ARROW))));
        registry.register(shaped("divers_sponge", 8, "Diver's Sponge", HungerGamesUtilityItems.diversSponge(),
            List.of("15-second moving air pocket. Maximum 1 craft per match."), cells(
                cell(0, 1, Material.WATER_BUCKET), cell(1, 0, Material.SLIME_BALL), cell(1, 2, Material.SLIME_BALL),
                cell(2, 0, Material.QUARTZ), cell(2, 2, Material.QUARTZ))));
        registry.register(shaped("scavengers_rod", 9, "Scavenger's Rod", HungerGamesUtilityItems.scavengersRod(),
            List.of("Six successful Scavenger catches. Maximum 1 craft per match."), cells(
                cell(0, 0, Material.QUARTZ), cell(0, 1, Material.GOLD_INGOT), cell(0, 2, Material.QUARTZ),
                cell(1, 0, Material.IRON_INGOT), cell(1, 1, Material.FISHING_ROD), cell(1, 2, Material.IRON_INGOT),
                cell(2, 1, Material.CHEST))));
        registry.register(shaped("wind_pack", 10, "Wind Pack", HungerGamesUtilityItems.windPack(),
            List.of("Produces 4-8 Wind Charges. Maximum 1 craft per match."), cells(
                cell(0, 1, Material.QUARTZ), cell(1, 0, Material.GOLD_INGOT), cell(1, 1, Material.WIND_CHARGE),
                cell(1, 2, Material.GOLD_INGOT), cell(2, 1, Material.QUARTZ))));
        registry.register(shaped("web_bundle", 11, "Web Bundle", HungerGamesUtilityItems.webBundle(),
            List.of("Produces 4-6 Cobwebs. Maximum 1 craft per match."), cells(
                cell(0, 1, Material.STICK), cell(1, 0, Material.STICK), cell(1, 1, Material.COBWEB),
                cell(1, 2, Material.STICK), cell(2, 1, Material.STICK))));
        registry.register(shaped("tracking_compass", 12, "Tracking Compass", HungerGamesUtilityItems.trackingCompass(),
            List.of("Tracks the nearest valid enemy. Maximum 1 craft per match."), cells(
                cell(0, 1, Material.DIAMOND), cell(1, 0, Material.IRON_INGOT), cell(1, 1, Material.ENDER_PEARL),
                cell(1, 2, Material.IRON_INGOT), cell(2, 1, Material.QUARTZ))));
    }

    private static void registerLegendaryRecipes(RecipeBookRegistry registry) {
        int order = 0;
        for (String recipeId : APPROVED_PUBLIC_LEGENDARY_RECIPE_IDS) {
            LegendaryRecipeDefinition recipe = OriginalLegendaryRecipes.byId(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Missing approved legendary recipe: " + recipeId));
            List<IngredientSpec> ingredients = recipe.allIngredients().stream()
                .map(RecipeBookCatalog::legendaryIngredient)
                .toList();
            registry.register(definition(
                recipe.id(),
                new ShapelessLayout(ingredients),
                order++,
                recipe.title(),
                recipe.resultSupplier().get(),
                recipe.notes(),
                RecipeCategory.LEGENDARY
            ));
        }
    }

    private static IngredientSpec legendaryIngredient(LegendaryIngredient ingredient) {
        ItemStack stack = ingredient.toItemStack();
        return new IngredientSpec(new MaterialChoice(stack.getType()), stack.getAmount());
    }

    private static RecipeBookDefinition shaped(
        String id,
        int order,
        String title,
        ItemStack result,
        List<String> notes,
        Map<GridPosition, IngredientSpec> cells
    ) {
        return definition(id, new ShapedLayout(3, 3, cells), order, title, result, notes, RecipeCategory.BASIC);
    }

    private static RecipeBookDefinition definition(
        String id,
        RecipeBookDefinition.RecipeLayout layout,
        int order,
        String title,
        ItemStack result,
        List<String> notes,
        RecipeCategory category
    ) {
        RecipePresentation presentation = new RecipePresentation(
            text(title, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
            notes.stream().map(note -> text(note, NamedTextColor.GRAY)).toList()
        );
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        for (RecipeMode mode : RecipeMode.values()) {
            modes.put(mode, new AvailableView(presentation, new ResultPrototype(result), new RecipePlacement(category, order)));
        }
        return new RecipeBookDefinition(new NamespacedKey("hydra", id), layout, modes);
    }

    @SafeVarargs
    private static Map<GridPosition, IngredientSpec> cells(Map.Entry<GridPosition, IngredientSpec>... entries) {
        Map<GridPosition, IngredientSpec> cells = new LinkedHashMap<>();
        for (Map.Entry<GridPosition, IngredientSpec> entry : entries) {
            cells.put(entry.getKey(), entry.getValue());
        }
        return cells;
    }

    private static Map.Entry<GridPosition, IngredientSpec> cell(int row, int column, Material material) {
        return Map.entry(new GridPosition(row, column), ingredient(material));
    }

    private static Map.Entry<GridPosition, IngredientSpec> choiceCell(int row, int column, List<Material> materials) {
        return Map.entry(new GridPosition(row, column), new IngredientSpec(new MaterialSetChoice(materials), 1));
    }

    private static IngredientSpec ingredient(Material material) {
        return new IngredientSpec(new MaterialChoice(material), 1);
    }

    private static ItemStack lightApple() {
        ItemStack stack = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(text("Light Apple", NamedTextColor.YELLOW));
            meta.setItemModel(new NamespacedKey("hydra", "light_apple"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

}
