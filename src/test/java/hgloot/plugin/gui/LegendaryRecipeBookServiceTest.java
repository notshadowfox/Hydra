package hgloot.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.recipe.RecipeBookDefinition;
import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePlacement;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePresentation;
import hgloot.plugin.recipe.RecipeBookDefinition.ResultPrototype;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapelessLayout;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookRegistry;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class LegendaryRecipeBookServiceTest {
    private ServerMock server;
    private LegendaryRecipeBookService service;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        service = new LegendaryRecipeBookService(plugin, registry());
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void recipeBookUsesOnlyTwoIndexPagesAndPaperNavigation() {
        service.openIndex(player);
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        assertNull(player.getOpenInventory().getTopInventory().getItem(9));
        assertEquals(Material.PAPER, player.getOpenInventory().getTopInventory().getItem(17).getType());
        assertEquals(18, recipeSlot());
        assertFourthContentRowIsEmpty();

        InventoryClickEvent next = click(17);
        service.onInventoryClick(next);
        assertTrue(next.isCancelled());
        assertEquals(Material.PAPER, player.getOpenInventory().getTopInventory().getItem(9).getType());
        assertNull(player.getOpenInventory().getTopInventory().getItem(17));
        assertEquals(18, recipeSlot());
        assertFourthContentRowIsEmpty();

        InventoryClickEvent previous = click(9);
        service.onInventoryClick(previous);
        assertTrue(previous.isCancelled());
        assertNull(player.getOpenInventory().getTopInventory().getItem(9));
        assertEquals(Material.PAPER, player.getOpenInventory().getTopInventory().getItem(17).getType());
    }

    @Test
    void contextualBookReplacesPreviousModeItemsAndExplainsEmptyModes() {
        AtomicReference<LegendaryRecipeBookService.BookContext> context =
            new AtomicReference<>(LegendaryRecipeBookService.BookContext.SPLEEF);
        LegendaryRecipeBookService contextual = new LegendaryRecipeBookService(plugin(), registry(),
            (ignoredPlugin, ignoredRegistry) -> {}, ignored -> context.get(), mode -> switch (mode) {
                case SPLEEF -> List.of(new ItemStack(Material.SNOWBALL), new ItemStack(Material.FEATHER));
                case VOID_ROULETTE -> List.of(new ItemStack(Material.MACE));
                case HUNGER_GAMES, BOSS_RUSH, EMPTY -> List.of();
            });

        contextual.openIndex(player);
        assertEquals(Material.SNOWBALL, player.getOpenInventory().getTopInventory().getItem(18).getType());
        assertEquals(Material.FEATHER, player.getOpenInventory().getTopInventory().getItem(19).getType());

        context.set(LegendaryRecipeBookService.BookContext.VOID_ROULETTE);
        contextual.announceContextUpdate(player);
        assertEquals(Material.MACE, player.getOpenInventory().getTopInventory().getItem(31).getType());
        assertNull(player.getOpenInventory().getTopInventory().getItem(9));
        assertNull(player.getOpenInventory().getTopInventory().getItem(17));
        assertNull(player.getOpenInventory().getTopInventory().getItem(19));

        context.set(LegendaryRecipeBookService.BookContext.EMPTY);
        contextual.announceContextUpdate(player);
        assertNull(player.getOpenInventory().getTopInventory().getItem(18));
        assertEquals(Material.BARRIER, player.getOpenInventory().getTopInventory().getItem(22).getType());
    }

    @Test
    void differentPlayersKeepIndependentCurrentContexts() {
        PlayerMock other = server.addPlayer("OtherReader");
        LegendaryRecipeBookService contextual = new LegendaryRecipeBookService(plugin(), registry(),
            (ignoredPlugin, ignoredRegistry) -> {},
            reader -> reader.equals(player) ? LegendaryRecipeBookService.BookContext.SPLEEF
                : LegendaryRecipeBookService.BookContext.VOID_ROULETTE,
            mode -> mode == LegendaryRecipeBookService.BookContext.SPLEEF
                ? List.of(new ItemStack(Material.SNOWBALL)) : List.of(new ItemStack(Material.MACE)));
        contextual.openIndex(player);
        contextual.openIndex(other);
        assertEquals(Material.SNOWBALL, player.getOpenInventory().getTopInventory().getItem(18).getType());
        assertEquals(Material.MACE, other.getOpenInventory().getTopInventory().getItem(31).getType());
    }

    @Test
    void bossRushOpensOnlyCanonicalLegendaryRewardsAndNeverOffersUtilityNavigation() {
        RecipeBookRegistry rewardRegistry = new RecipeBookRegistry();
        int order = 0;
        for (String id : RecipeBookCatalog.approvedPublicLegendaryRecipeIds()) {
            rewardRegistry.register(legendaryDefinition(id, Material.NETHER_STAR, order++));
        }
        rewardRegistry.register(legendaryDefinition("unreleased_legendary", Material.DIAMOND, order));
        rewardRegistry.freeze();
        LegendaryRecipeBookService bossBook = new LegendaryRecipeBookService(plugin(),
            rewardRegistry, (ignoredPlugin, ignoredRegistry) -> {},
            ignored -> LegendaryRecipeBookService.BookContext.BOSS_RUSH, ignored -> List.of());
        bossBook.openIndex(player);
        assertNull(player.getOpenInventory().getTopInventory().getItem(9));
        assertNull(player.getOpenInventory().getTopInventory().getItem(17));
        long rewards = java.util.stream.IntStream.rangeClosed(18, 44)
            .filter(slot -> player.getOpenInventory().getTopInventory().getItem(slot) != null).count();
        assertEquals(7, rewards);
    }

    @Test
    void recipeDetailUsesInvisibleSlotTenBackControl() {
        service.openIndex(player);
        int recipeSlot = recipeSlot();
        InventoryClickEvent openRecipe = click(recipeSlot);
        service.onInventoryClick(openRecipe);
        assertTrue(openRecipe.isCancelled());
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        assertEquals(Material.PAPER, player.getOpenInventory().getTopInventory().getItem(9).getType());
        assertNull(player.getOpenInventory().getTopInventory().getItem(17));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(33));
        assertFourthContentRowIsEmpty();

        InventoryClickEvent back = click(9);
        service.onInventoryClick(back);
        assertTrue(back.isCancelled());
        assertEquals(Material.PAPER, player.getOpenInventory().getTopInventory().getItem(17).getType());
    }

    @Test
    void bookOpenRecipeSelectionAndPageChangesHaveDistinctSemanticCues() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/gui/LegendaryRecipeBookService.java"));
        assertTrue(source.contains("HydraSounds.UiCue.RECIPE_OPEN"));
        assertTrue(source.contains("case OpenRecipe ignored -> HydraSounds.UiCue.RECIPE_SELECT"));
        assertTrue(source.contains("HydraSounds.UiCue.PAGE_BACK : HydraSounds.UiCue.PAGE_FORWARD"));
        assertTrue(source.contains("HydraSounds.playUi(player, cue)"));
        assertTrue(source.contains("HydraSounds.UiCue.UNAVAILABLE"));
    }

    @Test
    void recipeBookCatalogUsesOnlyApprovedPublicLegendaryRecipes() {
        assertEquals(
            List.of(
                "spider_staff",
                "dwarven_bow",
                "sponge_saber",
                "ghlochester",
                "knights_saddle",
                "electric_conch",
                "skeletal_barber"
            ),
            RecipeBookCatalog.approvedPublicLegendaryRecipeIds()
        );
    }

    @Test
    void recipeBookIndexShowsSubDescriptionNotesInLore() {
        RecipeBookRegistry notesRegistry = new RecipeBookRegistry();
        notesRegistry.register(definitionWithNotes("ghost_knife", RecipeCategory.BASIC, Material.STONE_SWORD,
            List.of("Single-use dash utility.")));
        notesRegistry.freeze();

        LegendaryRecipeBookService notesService = new LegendaryRecipeBookService(plugin(), notesRegistry);
        notesService.openIndex(player);

        ItemStack icon = player.getOpenInventory().getTopInventory().getItem(recipeSlot());
        List<Component> lore = icon.getItemMeta().lore();

        assertNotNull(lore);
        assertTrue(lore.stream().map(PlainTextComponentSerializer.plainText()::serialize)
            .anyMatch(line -> line.contains("Single-use dash utility.")));
    }

    @Test
    void packedIceDetailRotatesAllFourPlankIngredientsTogether() {
        RecipeBookRegistry packedIceRegistry = new RecipeBookRegistry();
        packedIceRegistry.register(packedIceDefinition());
        packedIceRegistry.freeze();
        LegendaryRecipeBookService catalogService = new LegendaryRecipeBookService(
            plugin(), packedIceRegistry);
        catalogService.openIndex(player);

        int packedIceSlot = -1;
        for (int slot = 18; slot <= 44; slot++) {
            ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
            if (item != null && item.getType() == Material.PACKED_ICE) {
                packedIceSlot = slot;
                break;
            }
        }
        assertTrue(packedIceSlot >= 0, "Packed Ice recipe was not visible on the basic page");
        catalogService.onInventoryClick(click(packedIceSlot));

        server.getScheduler().performOneTick();
        Material first = player.getOpenInventory().getTopInventory().getItem(20).getType();
        assertEquals(Material.OAK_PLANKS, first);
        assertEquals(first, player.getOpenInventory().getTopInventory().getItem(28).getType());
        assertEquals(first, player.getOpenInventory().getTopInventory().getItem(30).getType());
        assertEquals(first, player.getOpenInventory().getTopInventory().getItem(38).getType());

        server.getScheduler().performTicks(20L);
        Material second = player.getOpenInventory().getTopInventory().getItem(20).getType();
        assertEquals(Material.SPRUCE_PLANKS, second);
        assertEquals(second, player.getOpenInventory().getTopInventory().getItem(28).getType());
        assertEquals(second, player.getOpenInventory().getTopInventory().getItem(30).getType());
        assertEquals(second, player.getOpenInventory().getTopInventory().getItem(38).getType());
    }

    @Test
    void startKeepsRecipeBookAvailableWhenBootstrapThrows() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        LegendaryRecipeBookService resilientService = new LegendaryRecipeBookService(
            plugin,
            registry(),
            (ignoredPlugin, ignoredRegistry) -> {
                throw new IllegalStateException("bootstrap failed");
            }
        );

        resilientService.start();
        resilientService.openIndex(player);

        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
    }

    private InventoryClickEvent click(int slot) {
        return new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            slot,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
    }

    private int recipeSlot() {
        for (int slot = 18; slot <= 44; slot++) {
            ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
            if (item != null && item.getType() != Material.PAPER) {
                return slot;
            }
        }
        throw new IllegalStateException("No recipe slot found");
    }

    private void assertFourthContentRowIsEmpty() {
        for (int slot = 45; slot <= 53; slot++) {
            assertNull(player.getOpenInventory().getTopInventory().getItem(slot), "Unexpected item in raw slot " + slot);
        }
    }

    private static RecipeBookRegistry registry() {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        registry.register(definition("basic_test", RecipeCategory.BASIC, Material.DIAMOND));
        registry.register(definition("legendary_test", RecipeCategory.LEGENDARY, Material.NETHER_STAR));
        registry.freeze();
        return registry;
    }

    private static PluginMock plugin() {
        return MockBukkit.createMockPlugin();
    }

    private static RecipeBookDefinition definition(String id, RecipeCategory category, Material result) {
        return definitionWithNotes(id, category, result, List.of());
    }

    private static RecipeBookDefinition legendaryDefinition(String id, Material result, int order) {
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        AvailableView view = new AvailableView(
            new RecipePresentation(Component.text(id), List.of()),
            new ResultPrototype(new ItemStack(result)),
            new RecipePlacement(RecipeCategory.LEGENDARY, order));
        modes.put(RecipeMode.BATTLE_ROYALE, view);
        modes.put(RecipeMode.HUNGER_GAMES, view);
        return new RecipeBookDefinition(new NamespacedKey("hydra", id),
            new ShapelessLayout(List.of(new IngredientSpec(new MaterialChoice(Material.STONE), 12))), modes);
    }

    private static RecipeBookDefinition definitionWithNotes(String id, RecipeCategory category, Material result, List<String> notes) {
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        modes.put(RecipeMode.BATTLE_ROYALE, availableWithNotes(category, result, notes));
        modes.put(RecipeMode.HUNGER_GAMES, availableWithNotes(category, result, notes));
        return new RecipeBookDefinition(
            new NamespacedKey("hydra", id),
            new ShapelessLayout(List.of(new IngredientSpec(new MaterialChoice(Material.STONE), 12))),
            modes
        );
    }

    private static RecipeBookDefinition packedIceDefinition() {
        EnumMap<RecipeMode, RecipeBookDefinition.ModeRecipeView> modes = new EnumMap<>(RecipeMode.class);
        modes.put(RecipeMode.BATTLE_ROYALE, available(RecipeCategory.BASIC, Material.PACKED_ICE));
        modes.put(RecipeMode.HUNGER_GAMES, available(RecipeCategory.BASIC, Material.PACKED_ICE));
        return new RecipeBookDefinition(
            new NamespacedKey("hydra", "packed_ice"),
            new ShapedLayout(3, 3, Map.of(
                new GridPosition(0, 1), new IngredientSpec(new MaterialChoice(Material.OAK_PLANKS), 1),
                new GridPosition(1, 0), new IngredientSpec(new MaterialChoice(Material.OAK_PLANKS), 1),
                new GridPosition(1, 2), new IngredientSpec(new MaterialChoice(Material.OAK_PLANKS), 1),
                new GridPosition(2, 1), new IngredientSpec(new MaterialChoice(Material.OAK_PLANKS), 1)
            )),
            modes
        );
    }

    private static AvailableView available(RecipeCategory category, Material result) {
        return availableWithNotes(category, result, List.of());
    }

    private static AvailableView availableWithNotes(RecipeCategory category, Material result, List<String> notes) {
        return new AvailableView(
            new RecipePresentation(Component.text("Test Recipe"), notes.stream().map(note -> (Component) Component.text(note)).toList()),
            new ResultPrototype(new ItemStack(result)),
            new RecipePlacement(category, 0)
        );
    }
}
