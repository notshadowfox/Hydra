package hgloot.plugin.announcement;

import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.recipe.RecipeBookDefinition.AvailableView;
import hgloot.plugin.recipe.RecipeBookDefinition.GridPosition;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientChoice;
import hgloot.plugin.recipe.RecipeBookDefinition.IngredientSpec;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookDefinition.ShapedLayout;
import hgloot.plugin.recipe.RecipeBookRegistry;
import hgloot.plugin.ultraroyales.Items;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import hgloot.plugin.util.HydraIcons;

/**
 * Easy Craft / situational recipe assistant.
 *
 * <p>Watches players for meaningful Hydra recipes. When a recipe becomes
 * craftable, the player gets a clickable prompt. Clicking opens a normal
 * vanilla workbench, moves matching ingredients from the inventory into the
 * 3x3 crafting matrix, and lets the player manually take the recipe result.</p>
 *
 * <p>Recipe layouts and result prototypes come from the authoritative recipe
 * book registry, so Easy Craft cannot fall behind the real Bukkit recipes.</p>
 */
public final class SituationalAnnouncementService implements Listener {
    private static final String CONFIG_ROOT = "announcements.situational.recipe_assist";

    private static final Set<String> LEGACY_GLOBAL_ASSIST_RECIPE_IDS = Set.of(
        "life_elixir", "gilded_sword", "light_apple", "packed_ice"
    );

    private final JavaPlugin plugin;
    private final Supplier<RecipeBookRegistry> registrySupplier;
    private final Map<UUID, Map<String, Long>> cooldownUntilMs = new HashMap<>();
    private List<RecipeAssistDefinition> recipes = List.of();
    private boolean recipesLoaded;
    private BukkitTask task;

    public SituationalAnnouncementService(JavaPlugin plugin, Supplier<RecipeBookRegistry> registrySupplier) {
        this.plugin = plugin;
        this.registrySupplier = registrySupplier;
    }

    public void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 80L, 60L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        cooldownUntilMs.clear();
    }

    /** Entry point for the {@code /recipes craft <id>} chat-prompt click. */
    public boolean openCraftingAssist(Player player, String recipeId) {
        if (player == null || recipeId == null || recipeId.isBlank()) {
            return false;
        }
        if (!ensureRecipesLoaded()) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED
                + "Easy Craft is still loading. Try again in a moment.");
            return true;
        }
        RecipeAssistDefinition recipe = recipeById(recipeId);
        if (recipe == null) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Unknown recipe assist.");
            return true;
        }
        if (!isRecipeAvailableTo(player, recipe)) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED
                + recipe.title() + " can only be quick-crafted during an active Hunger Games match.");
            return true;
        }
        if (isCraftLimitReached(player, recipe)) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED
                + "You have reached the maximum crafts for this item this match.");
            return true;
        }
        PreparedRecipe prepared = prepareRecipe(player.getInventory(), recipe, true);
        if (prepared == null) {
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED
                + "You no longer have the ingredients for " + recipe.title() + ".");
            return true;
        }

        InventoryView view = player.openWorkbench(player.getLocation(), true);
        if (view == null || !(view.getTopInventory() instanceof CraftingInventory craftingInventory)) {
            returnConsumed(player.getInventory(), Arrays.asList(prepared.grid()));
            player.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not open a crafting table.");
            return true;
        }

        craftingInventory.setMatrix(prepared.grid());
        player.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW
            + recipe.title() + ChatColor.GRAY + " ingredients placed in the crafting table.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);

        Bukkit.getScheduler().runTask(plugin, () -> refreshAssistedResult(player, recipe, view));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkRecipes(event.getPlayer()), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> checkRecipes(player), 2L);
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean(CONFIG_ROOT + ".enabled", true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkRecipes(player);
        }
    }

    private void checkRecipes(Player player) {
        if (!isEligible(player) || !ensureRecipesLoaded()) {
            return;
        }
        if (!plugin.getConfig().getBoolean(CONFIG_ROOT + ".enabled", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (RecipeAssistDefinition recipe : recipes) {
            if (!isRecipeAvailableTo(player, recipe) || isCraftLimitReached(player, recipe)) {
                continue;
            }
            if (cooldown(player.getUniqueId(), recipe.id()) > now) {
                continue;
            }
            if (prepareRecipe(player.getInventory(), recipe, false) == null) {
                continue;
            }
            setCooldown(player.getUniqueId(), recipe.id(), now);
            sendRecipePrompt(player, recipe);
            return;
        }
    }

    private void sendRecipePrompt(Player player, RecipeAssistDefinition recipe) {
        Component clickHere = Component.text("      CLICK HERE", NamedTextColor.BLUE)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/recipes craft " + recipe.id()))
            .hoverEvent(HoverEvent.showText(
                Component.text("Open a crafting table for " + recipe.title(), NamedTextColor.GRAY)));
        Component message = Component.text("    ", NamedTextColor.GRAY)
            .append(HydraIcons.component(HydraIcons.EXCLAMATION_ORANGE_CHAR))
            .append(Component.text(" Crafting Available ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(HydraIcons.component(HydraIcons.EXCLAMATION_ORANGE_CHAR))
            .append(Component.newline())
            .append(Component.text(" You can craft a ", NamedTextColor.GRAY))
            .append(Component.text(recipe.title(), NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.newline())
            .append(clickHere);
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    private void refreshAssistedResult(Player player, RecipeAssistDefinition recipe, InventoryView expectedView) {
        if (player == null || !player.isOnline() || player.getOpenInventory() != expectedView) {
            return;
        }
        if (!(expectedView.getTopInventory() instanceof CraftingInventory craftingInventory)) {
            return;
        }
        ItemStack currentResult = craftingInventory.getResult();
        if (currentResult == null || currentResult.getType().isAir()) {
            ItemStack fallbackResult = buildResult(recipe, craftingInventory.getMatrix());
            if (fallbackResult != null) {
                craftingInventory.setResult(fallbackResult);
            }
        }
        player.updateInventory();
    }

    private ItemStack buildResult(RecipeAssistDefinition recipe, ItemStack[] grid) {
        return recipe.id().equals("gilded_sword")
            ? Items.gildedSword(swordTypeIn(grid))
            : recipe.fallbackResult().clone();
    }

    private Material swordTypeIn(ItemStack[] grid) {
        if (grid != null) {
            for (ItemStack stack : grid) {
                if (stack != null && RecipeBookCatalog.GILDED_SWORD_MATERIALS.contains(stack.getType())) {
                    return stack.getType();
                }
            }
        }
        return Material.DIAMOND_SWORD;
    }

    private long cooldown(UUID playerId, String recipeId) {
        Map<String, Long> byRecipe = cooldownUntilMs.get(playerId);
        return byRecipe == null ? 0L : byRecipe.getOrDefault(recipeId, 0L);
    }

    private void setCooldown(UUID playerId, String recipeId, long now) {
        int cooldownSeconds = Math.max(10, plugin.getConfig().getInt(CONFIG_ROOT + ".cooldown-seconds", 180));
        cooldownUntilMs.computeIfAbsent(playerId, ignored -> new HashMap<>())
            .put(recipeId, now + cooldownSeconds * 1000L);
    }

    private RecipeAssistDefinition recipeById(String id) {
        for (RecipeAssistDefinition recipe : recipes) {
            if (recipe.id().equalsIgnoreCase(id)) {
                return recipe;
            }
        }
        return null;
    }

    boolean supportsRecipe(String id) {
        ensureRecipesLoaded();
        return recipeById(id) != null;
    }

    private boolean ensureRecipesLoaded() {
        if (recipesLoaded) {
            return true;
        }
        RecipeBookRegistry registry = registrySupplier.get();
        if (registry == null || !registry.isFrozen()) {
            return false;
        }
        recipes = loadRecipeAssists(registry);
        recipesLoaded = true;
        return true;
    }

    private List<RecipeAssistDefinition> loadRecipeAssists(RecipeBookRegistry registry) {
        return registry.visibleRecipes(RecipeMode.HUNGER_GAMES, RecipeCategory.BASIC).stream()
            .filter(recipe -> recipe.view() instanceof AvailableView && recipe.layout() instanceof ShapedLayout)
            .map(this::toRecipeAssist)
            .toList();
    }

    private RecipeAssistDefinition toRecipeAssist(RecipeBookRegistry.VisibleRecipe visible) {
        AvailableView view = (AvailableView) visible.view();
        ShapedLayout layout = (ShapedLayout) visible.layout();
        List<SlotRequirement> slots = new ArrayList<>(java.util.Collections.nCopies(9, null));
        for (Map.Entry<GridPosition, IngredientSpec> entry : layout.cells().entrySet()) {
            IngredientSpec ingredient = entry.getValue();
            if (ingredient.requiredAmount() != 1) {
                throw new IllegalStateException("Easy Craft requires one item per shaped slot: " + visible.id());
            }
            GridPosition position = entry.getKey();
            boolean rejectCustomItems = visible.id().getKey().equals("gilded_sword")
                && position.row() == 1 && position.column() == 1;
            slots.set(position.row() * 3 + position.column(),
                new SlotRequirement(ingredient.choice(), rejectCustomItems));
        }
        String title = PlainTextComponentSerializer.plainText().serialize(view.presentation().title());
        String id = visible.id().getKey();
        return new RecipeAssistDefinition(
            id,
            title,
            java.util.Collections.unmodifiableList(new ArrayList<>(slots)),
            view.result().createItem(),
            !LEGACY_GLOBAL_ASSIST_RECIPE_IDS.contains(id)
        );
    }

    private boolean isRecipeAvailableTo(Player player, RecipeAssistDefinition recipe) {
        if (!recipe.requiresActiveHungerGames()) {
            return true;
        }
        return plugin instanceof HGLootPlugin hydra
            && hydra.queueService() != null
            && hydra.queueService().isActiveHungerGamesPlayer(player);
    }

    private boolean isCraftLimitReached(Player player, RecipeAssistDefinition recipe) {
        return plugin instanceof HGLootPlugin hydra
            && hydra.queueService() != null
            && hydra.queueService().isHungerGamesCraftLimitReached(player, recipe.id());
    }

    /**
     * Simulates pulling one item per requirement from a working copy of the
     * player's inventory, so repeated ingredients are counted correctly. Only
     * writes back to the real inventory when {@code consume} is true.
     */
    private PreparedRecipe prepareRecipe(PlayerInventory inventory, RecipeAssistDefinition recipe, boolean consume) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] work = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            work[i] = contents[i] == null ? null : contents[i].clone();
        }
        List<ItemStack> grid = new ArrayList<>();
        for (SlotRequirement requirement : recipe.slots()) {
            if (requirement == null) {
                grid.add(null);
                continue;
            }
            ItemStack taken = takeOne(work, requirement);
            if (taken == null) {
                return null;
            }
            grid.add(taken);
        }
        if (consume) {
            inventory.setContents(work);
        }
        return new PreparedRecipe(grid.toArray(ItemStack[]::new));
    }

    private ItemStack takeOne(ItemStack[] work, SlotRequirement requirement) {
        for (int slot = 0; slot < work.length; slot++) {
            ItemStack stack = work[slot];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            if (!requirement.matches(stack)) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            stack.setAmount(stack.getAmount() - 1);
            work[slot] = stack.getAmount() <= 0 ? null : stack;
            return one;
        }
        return null;
    }

    private void returnConsumed(PlayerInventory inventory, List<ItemStack> consumed) {
        for (ItemStack item : consumed) {
            if (item != null && !item.getType().isAir()) {
                inventory.addItem(item);
            }
        }
    }

    private boolean isEligible(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private record RecipeAssistDefinition(
        String id,
        String title,
        List<SlotRequirement> slots,
        ItemStack fallbackResult,
        boolean requiresActiveHungerGames
    ) {
    }

    private record PreparedRecipe(ItemStack[] grid) {
    }

    private record SlotRequirement(IngredientChoice choice, boolean rejectCustomModelItems) {
        private boolean matches(ItemStack stack) {
            if (stack == null || !choice.matches(stack)) {
                return false;
            }
            if (!rejectCustomModelItems) {
                return true;
            }
            ItemMeta meta = stack.getItemMeta();
            return meta == null
                || (!meta.hasItemModel() && !meta.hasCustomModelData() && meta.lore() == null);
        }
    }
}
