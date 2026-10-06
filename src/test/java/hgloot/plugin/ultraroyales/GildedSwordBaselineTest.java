package hgloot.plugin.ultraroyales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hgloot.plugin.recipe.RecipeBookCatalog;
import java.lang.reflect.Field;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class GildedSwordBaselineTest {
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
    void everyCraftedVariantIsUnenchantedWithoutLosingItsModel() {
        for (Material material : RecipeBookCatalog.GILDED_SWORD_MATERIALS) {
            ItemStack sword = Items.gildedSword(material);
            assertEquals(material, sword.getType());
            assertEquals(0, sword.getEnchantmentLevel(Enchantment.SHARPNESS));
            assertEquals(0, sword.getEnchantmentLevel(Enchantment.UNBREAKING));
            assertEquals(true, Items.isGildedSword(sword));
            assertNotNull(sword.getItemMeta().getItemModel());
            assertNotNull(sword.getItemMeta().displayName());
        }
    }

    @Test
    void renamedVanillaSwordIsNotGilded() {
        ItemStack renamed = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = renamed.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Gilded Diamond Sword"));
        renamed.setItemMeta(meta);
        assertEquals(false, Items.isGildedSword(renamed));
    }
}
