package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BossRushKitTest {
    @BeforeEach
    void setup() { MockBukkit.mock(); }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    @Test
    void authoritativeKitIncludesShieldOnEveryPreparePath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));

        assertTrue(source.contains("grantStandardKit(player.getInventory(), player.getUniqueId(), plugin.kitEditorService())"));
        assertTrue(source.contains("preparePlayer(player, spawn)"));
        assertTrue(source.contains("preparePlayer(player, playerStartingSpawn(player.getUniqueId()))"));
        assertEquals(Material.SHIELD, BossRushService.standardKitItems().get("shield").getType());
        assertTrue(source.contains("Material.DIAMOND_SWORD, Enchantment.SHARPNESS"));
        assertTrue(source.contains("Material.DIAMOND_AXE, Enchantment.SHARPNESS"));
        assertTrue(source.contains("Material.BOW, Enchantment.POWER"));
        assertTrue(source.contains("new ItemStack(Material.ARROW, 10)"));
        assertTrue(source.contains("new ItemStack(Material.GOLDEN_APPLE, 3)"));
        assertTrue(source.contains("new ItemStack(Material.COOKED_BEEF, 5)"));
        assertTrue(source.contains("item.addEnchantment(enchantment, 1)"));
    }

    @Test
    void allFourBossArmorPiecesHaveProtectionBindingAndMending() throws Exception {
        Method method = BossRushService.class.getDeclaredMethod("bossArmor", Material.class);
        method.setAccessible(true);
        for (Material material : new Material[] {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}) {
            ItemStack item = (ItemStack) method.invoke(null, material);
            assertEquals(material, item.getType());
            assertEquals(1, item.getEnchantmentLevel(Enchantment.PROTECTION));
            assertEquals(1, item.getEnchantmentLevel(Enchantment.BINDING_CURSE));
            assertEquals(1, item.getEnchantmentLevel(Enchantment.MENDING));
        }
    }
}
