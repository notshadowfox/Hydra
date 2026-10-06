package hgloot.plugin.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.QueueService;
import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeCategory;
import hgloot.plugin.recipe.RecipeBookDefinition.RecipeMode;
import hgloot.plugin.recipe.RecipeBookRegistry;
import hgloot.plugin.recipe.RecipeRuntimeRegistrar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class HungerGamesSmokeBombTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private PluginMock catalogPlugin;
    private HungerGamesUtilityService utilities;
    private PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        catalogPlugin = MockBukkit.createMockPlugin();
        var ultraPlugin = hgloot.plugin.ultraroyales.UltraRoyales.class.getDeclaredField("plugin");
        ultraPlugin.setAccessible(true);
        ultraPlugin.set(null, catalogPlugin);
        plugin = mock(HGLootPlugin.class);
        QueueService queues = mock(QueueService.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HungerGamesSmokeBombTest"));
        when(plugin.queueService()).thenReturn(queues);
        player = server.addPlayer();
        when(queues.isActiveHungerGamesPlayer(player)).thenReturn(true);
        utilities = new HungerGamesUtilityService(plugin);
    }

    @AfterEach
    void cleanup() throws Exception {
        var ultraPlugin = hgloot.plugin.ultraroyales.UltraRoyales.class.getDeclaredField("plugin");
        ultraPlugin.setAccessible(true);
        ultraPlugin.set(null, null);
        MockBukkit.unmock();
    }

    @Test
    void recipeAndUtilityBookNeverExposeRetiredItem() {
        RecipeBookRegistry registry = catalog();
        NamespacedKey retired = new NamespacedKey("hydra", "smoke_bomb");
        for (RecipeMode mode : RecipeMode.values()) {
            assertTrue(registry.resolveVisible(retired, mode).isEmpty());
            for (RecipeCategory category : RecipeCategory.values()) {
                assertTrue(registry.visibleRecipes(mode, category).stream()
                    .noneMatch(recipe -> "smoke_bomb".equals(recipe.id().getKey())));
            }
        }
    }

    @Test
    void oldRuntimeRecipeKeyIsRemovedAndCannotBeResolved() {
        RecipeBookRegistry registry = new RecipeBookRegistry();
        registry.freeze();
        NamespacedKey oldKey = new NamespacedKey(catalogPlugin, "hg_smoke_bomb");
        var oldRecipe = new org.bukkit.inventory.ShapedRecipe(oldKey, new ItemStack(Material.ENDER_EYE));
        oldRecipe.shape("E").setIngredient('E', Material.ENDER_EYE);
        assertTrue(Bukkit.addRecipe(oldRecipe));
        RecipeRuntimeRegistrar.registerBukkitRecipes(catalogPlugin, registry);
        assertNull(Bukkit.getRecipe(oldKey));
        assertNull(RecipeRuntimeRegistrar.catalogIdFor(catalogPlugin, new org.bukkit.inventory.ShapedRecipe(
            oldKey, new ItemStack(Material.ENDER_EYE))));
    }

    @Test
    void legacyTaggedItemIsInertOnRightClick() {
        ItemStack oldItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = oldItem.getItemMeta();
        meta.getPersistentDataContainer().set(HungerGamesUtilityItems.UTILITY_ID,
            PersistentDataType.STRING, "smoke_bomb");
        oldItem.setItemMeta(meta);
        player.getInventory().setItemInMainHand(oldItem);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            oldItem, null, BlockFace.SELF, EquipmentSlot.HAND);
        utilities.onUse(event);
        assertTrue(event.isCancelled());
        assertEquals("smoke_bomb", HungerGamesUtilityItems.utilityId(player.getInventory().getItemInMainHand()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertNull(player.getPotionEffect(PotionEffectType.BLINDNESS));
    }

    @Test
    void legacyProjectileHasNoLandingListenerOrCloudState() {
        assertTrue(List.of(HungerGamesUtilityService.class.getDeclaredMethods()).stream()
            .map(Method::getName).noneMatch(name -> name.equals("onSmokeLand") || name.equals("tickSmoke")));
        assertTrue(List.of(HungerGamesUtilityService.class.getDeclaredFields()).stream()
            .map(Field::getName).noneMatch(name -> name.equals("smokeClouds") || name.equals("hiddenPairs")));
        assertTrue(List.of(HungerGamesUtilityService.class.getDeclaredMethods()).stream()
            .noneMatch(method -> method.isAnnotationPresent(org.bukkit.event.EventHandler.class)
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == org.bukkit.event.entity.ProjectileHitEvent.class));
        assertNull(player.getPotionEffect(PotionEffectType.BLINDNESS));
        utilities.shutdown();
    }

    @Test
    void noAuthoritativeLootFactoryOrCraftLimitRemains() throws Exception {
        assertTrue(List.of(HungerGamesUtilityItems.class.getDeclaredMethods()).stream()
            .map(Method::getName).noneMatch("smokeBomb"::equals));
        assertFalse(Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"))
            .contains("Map.entry(\"smoke_bomb\""));
        assertFalse(Files.readString(Path.of("src/main/java/hgloot/plugin/recipe/RecipeBookCatalog.java"))
            .contains("shaped(\"smoke_bomb\""));
    }

    @Test
    void neighboringUtilitiesRemainInAuthoritativeCatalog() {
        RecipeBookRegistry registry = catalog();
        for (String id : List.of("ghost_knife", "divers_sponge", "scavengers_rod", "tracking_compass")) {
            assertTrue(registry.resolveVisible(new NamespacedKey("hydra", id), RecipeMode.HUNGER_GAMES).isPresent(), id);
        }
    }

    private RecipeBookRegistry catalog() {
        try (var items = org.mockito.Mockito.mockStatic(hgloot.plugin.ultraroyales.Items.class, invocation -> {
            if (invocation.getMethod().getName().equals("gildedSword")) {
                return new ItemStack((Material) invocation.getArgument(0));
            }
            return invocation.callRealMethod();
        })) {
            return RecipeBookCatalog.createRegistry();
        }
    }
}
