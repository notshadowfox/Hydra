package hgloot.plugin.gui;

import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.ManagedMinigameRound.Game;
import hgloot.plugin.util.HydraSounds;
import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.recipe.RecipeBookDefinition;
import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.DisabledView;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientDisplay;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.MaterialChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.ModeRecipeView;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipePresentation;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookRegistry;
import hgloot.plugin.recipe.RecipeBookRegistry.VisibleRecipe;
import hgloot.plugin.recipe.RecipeRegistrationParityValidator;
import hgloot.plugin.recipe.RecipeRuntimeRegistrar;
import hgloot.plugin.ultraroyales.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class LegendaryRecipeBookService implements Listener {
    private static final int INDEX_SIZE = 54;
    private static final int DETAIL_SIZE = 54;
    private static final int INGREDIENT_PAGE_SIZE = 9;
    private static final int[] GRID_SLOTS = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int[] CHECKLIST_SLOTS = {19, 20, 21, 22, 23, 28, 29, 30, 31};
    private static final int PREVIOUS_SLOT = 9;
    private static final int NEXT_SLOT = 17;
    private static final int RESULT_SLOT = 33;
    private static final List<Integer> INDEX_RECIPE_SLOTS = List.of(
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    );
    private static final Component UNAVAILABLE_MESSAGE = Component.text(
        "The recipe book is unavailable right now.",
        NamedTextColor.RED
    );

    private final Plugin plugin;
    private final BiConsumer<Plugin, RecipeBookRegistry> bootstrap;
    private final Function<Player, BookContext> contextResolver;
    private final Function<BookContext, List<ItemStack>> previewProvider;
    private RecipeBookRegistry registry;
    private boolean available;
    private final Map<UUID, BukkitTask> animationTasks = new HashMap<>();

    public LegendaryRecipeBookService(HGLootPlugin plugin) {
        this(plugin, null, defaultBootstrap());
    }

    LegendaryRecipeBookService(Plugin plugin, RecipeBookRegistry registry) {
        this(plugin, registry, defaultBootstrap());
    }

    LegendaryRecipeBookService(Plugin plugin, RecipeBookRegistry registry, BiConsumer<Plugin, RecipeBookRegistry> bootstrap) {
        this(plugin, registry, bootstrap, null, null);
    }

    LegendaryRecipeBookService(Plugin plugin, RecipeBookRegistry registry,
                               BiConsumer<Plugin, RecipeBookRegistry> bootstrap,
                               Function<Player, BookContext> contextResolver,
                               Function<BookContext, List<ItemStack>> previewProvider) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bootstrap = Objects.requireNonNullElse(bootstrap, defaultBootstrap());
        this.contextResolver = contextResolver;
        this.previewProvider = previewProvider;
        if (registry != null) {
            this.registry = Objects.requireNonNull(registry, "registry");
            if (!registry.isFrozen()) {
                throw new IllegalArgumentException("Injected recipe registry must be frozen");
            }
            this.available = true;
            return;
        }
        this.available = false;
    }

    public void start() {
        try {
            if (registry == null) {
                registry = RecipeBookCatalog.createRegistry();
            }
            bootstrap.accept(plugin, registry);
            available = true;
            plugin.getLogger().info("[Recipes] event=registry_frozen status=ready");
        } catch (RuntimeException exception) {
            if (registry == null) {
                registry = null;
                available = false;
                plugin.getLogger().log(Level.SEVERE, "[Recipes] event=registry_freeze_failed status=disabled", exception);
                return;
            }
            available = true;
            plugin.getLogger().log(Level.WARNING, "[Recipes] event=bootstrap_warning status=degraded", exception);
        }
    }

    public void shutdown() {
        animationTasks.values().forEach(BukkitTask::cancel);
        animationTasks.clear();
        available = false;
        registry = null;
    }

    public RecipeBookRegistry recipeRegistry() {
        return available ? registry : null;
    }

    public void openIndex(Player player) {
        openSafely(player, () -> renderIndex(player), HydraSounds.UiCue.RECIPE_OPEN);
    }

    public void openHungerGamesIndex(Player player) {
        openSafely(player, () -> openIndexPage(player, RecipeBookPage.BASIC, BookContext.HUNGER_GAMES), HydraSounds.UiCue.RECIPE_OPEN);
    }

    public void announceContextUpdate(Player player) {
        if (player == null || !player.isOnline()) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
            openSafely(player, () -> renderIndex(player), null);
        }
        player.sendActionBar(Component.text("Utility Book Updated", NamedTextColor.AQUA));
        HydraSounds.playUi(player, HydraSounds.UiCue.CONFIRM);
    }

    private void renderIndex(Player player) {
        BookContext context = resolveContext(player);
        if (context == BookContext.HUNGER_GAMES) {
            openIndexPage(player, RecipeBookPage.BASIC, context);
            return;
        }
        if (context == BookContext.BOSS_RUSH) {
            openIndexPage(player, RecipeBookPage.LEGENDARY, context);
            return;
        }
        List<ItemStack> items = previewProvider != null ? previewProvider.apply(context) : switch (context) {
            case SPLEEF -> ((HGLootPlugin) plugin).spleefService().utilityBookItems();
            case VOID_ROULETTE -> List.of(((HGLootPlugin) plugin).voidRouletteService().utilityBookVoidbreakerPreview());
            case HUNGER_GAMES, BOSS_RUSH, EMPTY -> List.of();
        };
        String title = switch (context) {
            case SPLEEF -> "Spleef Utility Items";
            case VOID_ROULETTE -> "Void Roulette Legendary";
            case BOSS_RUSH -> "Boss Rush Legendary";
            case HUNGER_GAMES, EMPTY -> "Utility Book";
        };
        MenuHolder holder = createMenu(INDEX_SIZE, title,
            context == BookContext.VOID_ROULETTE ? "recipes_legendary" : "recipes", context);
        if (items.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = requireMeta(empty);
            meta.displayName(text("NO SPECIAL ITEMS", NamedTextColor.AQUA));
            meta.lore(List.of(text("This mode has no special utility items.", NamedTextColor.GRAY)));
            empty.setItemMeta(meta);
            holder.getInventory().setItem(22, empty);
        } else {
            List<Integer> slots = context == BookContext.VOID_ROULETTE
                ? List.of(31) : orderedRecipeSlots(items.size());
            for (int index = 0; index < items.size(); index++) {
                holder.getInventory().setItem(slots.get(index), items.get(index));
            }
        }
        holder.seal();
        player.openInventory(holder.getInventory());
    }

    private BookContext resolveContext(Player player) {
        if (contextResolver != null) return contextResolver.apply(player);
        if (!(plugin instanceof HGLootPlugin hydra)) return BookContext.HUNGER_GAMES;
        if (player == null || player.getWorld() == null) return BookContext.EMPTY;
        UUID id = player.getUniqueId();
        Game round = hydra.minigameRouletteService() == null ? null
            : hydra.minigameRouletteService().utilityBookGame(id);
        if (round != null) return switch (round) {
            case SPLEEF -> BookContext.SPLEEF;
            case VOID_ROULETTE -> BookContext.VOID_ROULETTE;
            case BOSS_RUSH -> BookContext.BOSS_RUSH;
            case BRIDGE -> BookContext.EMPTY;
        };
        if (hydra.spleefService() != null && hydra.spleefService().isInSpleef(id)) return BookContext.SPLEEF;
        if (hydra.voidRouletteService() != null && hydra.voidRouletteService().isInVoidRoulette(id))
            return BookContext.VOID_ROULETTE;
        if (hydra.bossRushService() != null && hydra.bossRushService().ownsPlayer(id))
            return BookContext.BOSS_RUSH;
        String world = player.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (world.startsWith("hg_match_") || world.startsWith("hg-world-")
            || hydra.queueService() != null && (hydra.queueService().isPlayerQueuedFor(id,
                hgloot.plugin.QueueService.QueueType.HUNGERGAMES)
                || hydra.queueService().isPlayerQueuedFor(id,
                    hgloot.plugin.QueueService.QueueType.HUNGERGAMES_DUOS))) return BookContext.HUNGER_GAMES;
        return BookContext.EMPTY;
    }

    private void openIndexPage(Player player, RecipeBookPage page, BookContext context) {
        requireAvailable();
        if (context != BookContext.HUNGER_GAMES
            && !(context == BookContext.BOSS_RUSH && page == RecipeBookPage.LEGENDARY)) return;
        List<VisibleRecipe> recipes = registry.visibleRecipes(RecipeMode.BATTLE_ROYALE, page.category());
        if (context == BookContext.BOSS_RUSH) {
            List<String> rewardIds = hgloot.plugin.recipe.RecipeBookCatalog.approvedPublicLegendaryRecipeIds();
            recipes = recipes.stream().filter(recipe -> rewardIds.contains(recipe.id().getKey())).toList();
        }
        List<Integer> recipeSlots = orderedRecipeSlots(recipes.size());
        MenuHolder holder = createMenu(INDEX_SIZE, page.title(), page.theme(), context);

        for (int index = 0; index < recipes.size(); index++) {
            VisibleRecipe recipe = recipes.get(index);
            addButton(holder, recipeSlots.get(index), indexIcon(recipe), new OpenRecipe(recipe.id()));
        }

        if (context == BookContext.HUNGER_GAMES && page == RecipeBookPage.BASIC) {
            addButton(holder, NEXT_SLOT, navigationItem("Next Page ->"), new OpenBookPage(RecipeBookPage.LEGENDARY));
        } else if (context == BookContext.HUNGER_GAMES) {
            addButton(holder, PREVIOUS_SLOT, navigationItem("<- Previous Page"), new OpenBookPage(RecipeBookPage.BASIC));
        }
        holder.seal();
        player.openInventory(holder.getInventory());
        startGildedSwordIndexAnimation(player, holder.getInventory(), recipes, recipeSlots);
    }

    private void openRecipePage(Player player, NamespacedKey recipeId, BookContext context) {
        requireAvailable();
        VisibleRecipe recipe = registry.resolveVisible(recipeId, RecipeMode.BATTLE_ROYALE)
            .orElseThrow(() -> new IllegalArgumentException("Recipe is hidden or missing: " + recipeId));
        if (context == BookContext.BOSS_RUSH
            && (recipeCategory(recipe.view()) != RecipeCategory.LEGENDARY
                || !hgloot.plugin.recipe.RecipeBookCatalog.approvedPublicLegendaryRecipeIds()
                    .contains(recipe.id().getKey()))) return;
        RecipePresentation presentation = presentation(recipe.view());
        MenuHolder holder = createMenu(
            DETAIL_SIZE,
            plainTitle(presentation.title()),
            recipeCategory(recipe.view()) == RecipeCategory.LEGENDARY
                ? "recipes_crafting_legendary"
                : "recipes_crafting_utility",
            context
        );

        ItemStack result = context == BookContext.HUNGER_GAMES && plugin instanceof HGLootPlugin hydra
            && hydra.queueService().isHungerGamesCraftLimitReached(player, recipe.id().getKey())
            ? craftLimitDisplay()
            : resultDisplay(recipe.view(), presentation);
        holder.getInventory().setItem(RESULT_SLOT, result);

        if (recipe.layout() instanceof ShapedLayout shaped) {
            renderShaped(holder.getInventory(), shaped);
        } else {
            renderChecklist(holder.getInventory(), recipe);
        }
        RecipeBookPage sourcePage = recipeCategory(recipe.view()) == RecipeCategory.LEGENDARY
            ? RecipeBookPage.LEGENDARY
            : RecipeBookPage.BASIC;
        addButton(holder, PREVIOUS_SLOT, navigationItem("Back"), new OpenBookPage(sourcePage));
        holder.seal();
        player.openInventory(holder.getInventory());
        startRecipeDetailAnimation(player, holder.getInventory(), recipe, presentation);
    }

    private void renderShaped(Inventory inventory, ShapedLayout shaped) {
        for (Map.Entry<GridPosition, IngredientSpec> entry : shaped.cells().entrySet()) {
            GridPosition position = entry.getKey();
            int gridIndex = position.row() * 3 + position.column();
            inventory.setItem(GRID_SLOTS[gridIndex], ingredientItem(entry.getValue()));
        }
    }

    private void renderChecklist(Inventory inventory, VisibleRecipe recipe) {
        List<IngredientSpec> ingredients = recipe.layout().ingredients();
        if (ingredients.size() > INGREDIENT_PAGE_SIZE) {
            throw new IllegalArgumentException("Recipe has too many visible ingredients: " + recipe.id());
        }
        for (int index = 0; index < ingredients.size(); index++) {
            inventory.setItem(CHECKLIST_SLOTS[index], ingredientItem(ingredients.get(index)));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (holder.context != resolveContext(player)) {
            openIndex(player);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        MenuAction action = holder.actionAt(event.getRawSlot());
        if (action == null) {
            return;
        }
        HydraSounds.UiCue cue = switch (action) {
            case OpenBookPage open -> open.page() == RecipeBookPage.BASIC
                ? HydraSounds.UiCue.PAGE_BACK : HydraSounds.UiCue.PAGE_FORWARD;
            case OpenRecipe ignored -> HydraSounds.UiCue.RECIPE_SELECT;
        };
        openSafely(player, () -> dispatch(player, holder.context, action), cue);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
            && event.getInventory().getHolder() instanceof MenuHolder) {
            cancelAnimation(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelAnimation(event.getPlayer().getUniqueId());
    }

    private void dispatch(Player player, BookContext context, MenuAction action) {
        switch (action) {
            case OpenBookPage open -> openIndexPage(player, open.page(), context);
            case OpenRecipe open -> openRecipePage(player, open.recipeId(), context);
        }
    }

    private void openSafely(Player player, Runnable renderer, HydraSounds.UiCue cue) {
        if (player == null) {
            return;
        }
        if (!available || registry == null) {
            player.sendMessage(UNAVAILABLE_MESSAGE);
            HydraSounds.playUi(player, HydraSounds.UiCue.UNAVAILABLE);
            return;
        }
        try {
            renderer.run();
            if (cue != null) HydraSounds.playUi(player, cue);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "[Recipes] event=render_failed player=" + player.getUniqueId(), exception);
            player.closeInventory();
            player.sendMessage(UNAVAILABLE_MESSAGE);
            HydraSounds.playUi(player, HydraSounds.UiCue.UNAVAILABLE);
        }
    }

    private void requireAvailable() {
        if (!available || registry == null || !registry.isFrozen()) {
            throw new IllegalStateException("Recipe book registry is unavailable");
        }
    }

    private static BiConsumer<Plugin, RecipeBookRegistry> defaultBootstrap() {
        return (plugin, registry) -> {
            RecipeRuntimeRegistrar.registerBukkitRecipes(plugin, registry);
            RecipeRegistrationParityValidator.validate(plugin, registry);
        };
    }

    private MenuHolder createMenu(int size, String title, String theme, BookContext context) {
        MenuHolder holder = new MenuHolder(context);
        Inventory inventory = Bukkit.createInventory(holder, size, GuiTitleFactory.themedTitle(theme, title));
        holder.setInventory(inventory);
        return holder;
    }

    private void addButton(MenuHolder holder, int slot, ItemStack icon, MenuAction action) {
        holder.addButton(slot, icon, action);
    }

    private ItemStack indexIcon(VisibleRecipe recipe) {
        return indexIcon(recipe, null);
    }

    private ItemStack indexIcon(VisibleRecipe recipe, ItemStack override) {
        RecipePresentation presentation = presentation(recipe.view());
        ItemStack icon = override == null
            ? switch (recipe.view()) {
                case AvailableView available -> available.result().createItem();
                case DisabledView disabled -> disabled.preview().createItem();
                default -> throw new IllegalArgumentException("Hidden recipe cannot be rendered");
            }
            : override.clone();
        ItemMeta meta = requireMeta(icon);
        List<Component> lore = new ArrayList<>(presentation.notes());
        lore.add(Component.empty());
        if (recipe.view() instanceof DisabledView disabled) {
            lore.add(disabled.disabledReason().color(NamedTextColor.RED));
        } else {
            lore.add(text("Click to view the recipe.", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        icon.setAmount(1);
        return icon;
    }

    private ItemStack resultDisplay(ModeRecipeView view, RecipePresentation presentation) {
        return resultDisplay(view, presentation, null);
    }

    private ItemStack resultDisplay(ModeRecipeView view, RecipePresentation presentation, ItemStack override) {
        ItemStack item = override == null ? switch (view) {
            case AvailableView available -> available.result().createItem();
            case DisabledView disabled -> disabled.preview().createItem();
            default -> throw new IllegalArgumentException("Hidden recipe cannot be rendered");
        } : override.clone();
        ItemMeta meta = requireMeta(item);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!presentation.notes().isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(presentation.notes());
        }
        if (view instanceof DisabledView disabled) {
            lore.add(Component.empty());
            lore.add(text("Unavailable", NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
            lore.add(disabled.disabledReason());
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void startGildedSwordIndexAnimation(
        Player player,
        Inventory inventory,
        List<VisibleRecipe> recipes,
        List<Integer> recipeSlots
    ) {
        for (int index = 0; index < recipes.size(); index++) {
            VisibleRecipe recipe = recipes.get(index);
            if (recipe.id().getKey().equals("gilded_sword")) {
                int slot = recipeSlots.get(index);
                startGildedSwordAnimation(player, inventory, (sword, ignored) ->
                    inventory.setItem(slot, indexIcon(recipe, sword)));
                return;
            }
        }
    }

    private void startRecipeDetailAnimation(
        Player player,
        Inventory inventory,
        VisibleRecipe recipe,
        RecipePresentation presentation
    ) {
        if (recipe.id().getKey().equals("gilded_sword")) {
            startMaterialAnimation(player, inventory, RecipeBookCatalog.GILDED_SWORD_MATERIALS, material -> {
                ItemStack sword = Items.gildedSword(material);
                inventory.setItem(GRID_SLOTS[4], ingredientItem(new IngredientSpec(new MaterialChoice(material), 1)));
                inventory.setItem(RESULT_SLOT, resultDisplay(recipe.view(), presentation, sword));
            });
        } else if (recipe.id().getKey().equals("packed_ice")) {
            startMaterialAnimation(player, inventory, RecipeBookCatalog.PLANK_MATERIALS, material -> {
                ItemStack plank = ingredientItem(new IngredientSpec(new MaterialChoice(material), 1));
                for (int gridIndex : new int[]{1, 3, 5, 7}) {
                    inventory.setItem(GRID_SLOTS[gridIndex], plank.clone());
                }
            });
        }
    }

    private void startGildedSwordAnimation(
        Player player,
        Inventory inventory,
        BiConsumer<ItemStack, Material> renderer
    ) {
        startMaterialAnimation(player, inventory, RecipeBookCatalog.GILDED_SWORD_MATERIALS,
            material -> renderer.accept(Items.gildedSword(material), material));
    }

    private void startMaterialAnimation(
        Player player,
        Inventory inventory,
        List<Material> materials,
        java.util.function.Consumer<Material> renderer
    ) {
        cancelAnimation(player.getUniqueId());
        final int[] index = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                cancelAnimation(player.getUniqueId());
                return;
            }
            Material material = materials.get(index[0]++ % materials.size());
            renderer.accept(material);
        }, 0L, 20L);
        animationTasks.put(player.getUniqueId(), task);
    }

    private void cancelAnimation(UUID playerId) {
        BukkitTask task = animationTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private ItemStack ingredientItem(IngredientSpec ingredient) {
        IngredientDisplay display = ingredient.choice().display();
        ItemStack item = display.icon().createItem();
        int visibleAmount = Math.max(1, Math.min(ingredient.requiredAmount(), item.getMaxStackSize()));
        item.setAmount(visibleAmount);
        ItemMeta meta = requireMeta(item);
        List<Component> lore = new ArrayList<>();
        lore.add(text("Required: " + ingredient.requiredAmount(), NamedTextColor.GREEN));
        lore.addAll(display.qualifiers().stream().map(line -> line.colorIfAbsent(NamedTextColor.GRAY)).toList());
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navigationItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = requireMeta(item);
        meta.displayName(text(name, NamedTextColor.YELLOW));
        meta.setItemModel(new NamespacedKey("hydra", "gui/negative_space"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack craftLimitDisplay() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = requireMeta(item);
        meta.displayName(text("Craft Limit Reached", NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(text("You have reached the maximum crafts for this item this match.", NamedTextColor.GRAY)));
        meta.setItemModel(new NamespacedKey("hydra", "gui/craft_limit"));
        item.setItemMeta(meta);
        return item;
    }

    private static List<Integer> orderedRecipeSlots(int recipeCount) {
        if (recipeCount < 0 || recipeCount > INDEX_RECIPE_SLOTS.size()) {
            throw new IllegalArgumentException("Recipe page supports at most " + INDEX_RECIPE_SLOTS.size()
                + " entries: " + recipeCount);
        }
        List<Integer> slots = new ArrayList<>(recipeCount);
        if (recipeCount == 0) {
            return slots;
        }
        for (int offset = 0; offset < recipeCount; offset++) {
            slots.add(INDEX_RECIPE_SLOTS.get(offset));
        }
        return List.copyOf(slots);
    }

    private static RecipePresentation presentation(ModeRecipeView view) {
        return switch (view) {
            case AvailableView available -> available.presentation();
            case DisabledView disabled -> disabled.presentation();
            default -> throw new IllegalArgumentException("Hidden recipe has no presentation");
        };
    }

    private static RecipeCategory recipeCategory(ModeRecipeView view) {
        return switch (view) {
            case AvailableView available -> available.placement().category();
            case DisabledView disabled -> disabled.placement().category();
            default -> throw new IllegalArgumentException("Hidden recipe has no category");
        };
    }

    private static ItemMeta requireMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("Item has no metadata: " + item.getType());
        }
        return meta;
    }

    private static Component text(String value, TextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String plainTitle(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    private enum RecipeBookPage {
        BASIC("Utility Items", RecipeCategory.BASIC, "recipes"),
        LEGENDARY("Legendary Items", RecipeCategory.LEGENDARY, "recipes_legendary");

        private final String title;
        private final RecipeCategory category;
        private final String theme;

        RecipeBookPage(String title, RecipeCategory category, String theme) {
            this.title = title;
            this.category = category;
            this.theme = theme;
        }

        private String title() {
            return title;
        }

        private RecipeCategory category() {
            return category;
        }

        private String theme() {
            return theme;
        }
    }

    enum BookContext {
        HUNGER_GAMES, SPLEEF, VOID_ROULETTE, BOSS_RUSH, EMPTY
    }

    private sealed interface MenuAction permits OpenBookPage, OpenRecipe {
    }

    private record OpenBookPage(RecipeBookPage page) implements MenuAction {
    }

    private record OpenRecipe(NamespacedKey recipeId) implements MenuAction {
    }

    private static final class MenuHolder implements InventoryHolder {
        private final BookContext context;
        private final Map<Integer, MenuAction> pendingActions = new HashMap<>();
        private Map<Integer, MenuAction> actions = Map.of();
        private Inventory inventory;
        private boolean sealed;

        private MenuHolder(BookContext context) {
            this.context = context;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void addButton(int slot, ItemStack icon, MenuAction action) {
            if (sealed) {
                throw new IllegalStateException("Menu is sealed");
            }
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(action, "action");
            if (pendingActions.putIfAbsent(slot, action) != null) {
                throw new IllegalArgumentException("Duplicate clickable slot: " + slot);
            }
            inventory.setItem(slot, icon);
        }

        private void seal() {
            actions = Map.copyOf(pendingActions);
            pendingActions.clear();
            sealed = true;
        }

        private MenuAction actionAt(int slot) {
            return actions.get(slot);
        }
    }
}
