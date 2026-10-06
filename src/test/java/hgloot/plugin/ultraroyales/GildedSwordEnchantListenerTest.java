package hgloot.plugin.ultraroyales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.ultraroyales.listeners.GildedSwordEnchantListener;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class GildedSwordEnchantListenerTest {
    private final GildedSwordEnchantListener listener = new GildedSwordEnchantListener();
    private JavaPlugin priorPlugin;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        Field field = UltraRoyales.class.getDeclaredField("plugin");
        field.setAccessible(true);
        priorPlugin = (JavaPlugin) field.get(null);
        field.set(null, MockBukkit.createMockPlugin());
    }

    @AfterEach
    void cleanup() throws Exception {
        Field field = UltraRoyales.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, priorPlugin);
        MockBukkit.unmock();
    }

    @Test
    void everyGildedTierReceivesThreeOffersWithVanillaCosts() {
        for (Material material : RecipeBookCatalog.GILDED_SWORD_MATERIALS) {
            EnchantmentOffer[] offers = offers();
            int[] costs = costs(offers);
            PrepareItemEnchantEvent event = prepare(Items.gildedSword(material), offers);
            listener.onPrepare(event);
            assertEquals(Enchantment.UNBREAKING, offers[0].getEnchantment(), material.toString());
            assertEquals(Enchantment.UNBREAKING, offers[1].getEnchantment(), material.toString());
            assertEquals(Enchantment.SHARPNESS, offers[2].getEnchantment(), material.toString());
            for (EnchantmentOffer offer : offers) assertEquals(1, offer.getEnchantmentLevel());
            assertArrayEquals(costs, costs(offers), material.toString());
        }
    }

    @Test
    void allNonGildedItemsKeepTheExactOriginalOffersAndResults() {
        ItemStack renamedSword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta renamedMeta = renamedSword.getItemMeta();
        renamedMeta.displayName(Component.text("Gilded Diamond Sword"));
        renamedSword.setItemMeta(renamedMeta);
        ItemStack[] ordinary = {
            new ItemStack(Material.DIAMOND_SWORD),
            new ItemStack(Material.WOODEN_SWORD),
            new ItemStack(Material.NETHERITE_SWORD),
            new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.DIAMOND_CHESTPLATE),
            new ItemStack(Material.IRON_PICKAXE),
            new ItemStack(Material.BOW),
            new ItemStack(Material.ENCHANTED_BOOK),
            Items.bookOfWelding(),
            renamedSword
        };
        for (ItemStack item : ordinary) {
            EnchantmentOffer[] offers = offers();
            EnchantmentOffer[] originalRefs = offers.clone();
            Enchantment[] originalEnchantments = enchantments(offers);
            int[] originalLevels = levels(offers);
            int[] originalCosts = costs(offers);
            PrepareItemEnchantEvent prepare = prepare(item, offers);
            listener.onPrepare(prepare);
            assertSame(originalRefs[0], offers[0], item.toString());
            assertSame(originalRefs[1], offers[1], item.toString());
            assertSame(originalRefs[2], offers[2], item.toString());
            assertArrayEquals(originalEnchantments, enchantments(offers), item.toString());
            assertArrayEquals(originalLevels, levels(offers), item.toString());
            assertArrayEquals(originalCosts, costs(offers), item.toString());

            Map<Enchantment, Integer> result = new HashMap<>(Map.of(Enchantment.FIRE_ASPECT, 2));
            EnchantItemEvent enchant = enchant(item, 2, result);
            listener.onEnchant(enchant);
            assertEquals(Map.of(Enchantment.FIRE_ASPECT, 2), result, item.toString());
            verify(enchant, never()).setExpLevelCost(anyInt());
        }
    }

    @Test
    void gildedResultMatchesChosenOfferWithoutChangingXpCost() {
        for (Material material : RecipeBookCatalog.GILDED_SWORD_MATERIALS) {
            for (int slot = 0; slot < 3; slot++) {
                Map<Enchantment, Integer> result = new HashMap<>(Map.of(Enchantment.FIRE_ASPECT, 2));
                EnchantItemEvent event = enchant(Items.gildedSword(material), slot, result);
                listener.onEnchant(event);
                assertEquals(Map.of(slot == 2 ? Enchantment.SHARPNESS : Enchantment.UNBREAKING, 1), result);
                verify(event, never()).setExpLevelCost(anyInt());
                verify(event, never()).setCancelled(true);
            }
        }
    }

    private static PrepareItemEnchantEvent prepare(ItemStack item, EnchantmentOffer[] offers) {
        PrepareItemEnchantEvent event = mock(PrepareItemEnchantEvent.class);
        when(event.getItem()).thenReturn(item);
        when(event.getOffers()).thenReturn(offers);
        return event;
    }

    private static EnchantItemEvent enchant(ItemStack item, int slot, Map<Enchantment, Integer> result) {
        EnchantItemEvent event = mock(EnchantItemEvent.class);
        when(event.getItem()).thenReturn(item);
        when(event.whichButton()).thenReturn(slot);
        when(event.getEnchantsToAdd()).thenReturn(result);
        return event;
    }

    private static EnchantmentOffer[] offers() {
        return new EnchantmentOffer[] {
            new EnchantmentOffer(Enchantment.SHARPNESS, 2, 4),
            new EnchantmentOffer(Enchantment.FIRE_ASPECT, 1, 12),
            new EnchantmentOffer(Enchantment.KNOCKBACK, 2, 30)
        };
    }

    private static Enchantment[] enchantments(EnchantmentOffer[] offers) {
        return java.util.Arrays.stream(offers).map(EnchantmentOffer::getEnchantment).toArray(Enchantment[]::new);
    }

    private static int[] levels(EnchantmentOffer[] offers) {
        return java.util.Arrays.stream(offers).mapToInt(EnchantmentOffer::getEnchantmentLevel).toArray();
    }

    private static int[] costs(EnchantmentOffer[] offers) {
        return java.util.Arrays.stream(offers).mapToInt(EnchantmentOffer::getCost).toArray();
    }
}
