package hgloot.plugin.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LootTableServiceRuntimeTest {
    private JavaPlugin plugin;
    private LootTableService service;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        Path lootDirectory = plugin.getDataFolder().toPath().resolve("loot_tables");
        Files.createDirectories(lootDirectory);
        for (String table : List.of("hg_single", "hg_refill")) {
            Files.copy(Path.of("src/main/resources/loot_tables", table + ".json"),
                lootDirectory.resolve(table + ".json"));
        }
        service = new LootTableService(plugin);
        service.loadAllTables();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void runtimeGeneratorProducesExactlyFiveCoreAndTwoCraftingRolls() {
        Set<Material> ingredients = Set.of(Material.QUARTZ, Material.LILY_PAD, Material.SLIME_BALL, Material.INK_SAC);
        for (String table : List.of("hg_single", "hg_refill")) {
            for (int iteration = 0; iteration < 1_000; iteration++) {
                List<ItemStack> generated = service.generateLoot(table);
                assertEquals(7, generated.size(), table + " iteration " + iteration);
                long ingredientStacks = generated.stream().filter(item -> ingredients.contains(item.getType())).count();
                assertEquals(2, ingredientStacks, table + " iteration " + iteration);
                assertTrue(generated.stream().allMatch(item -> item.getAmount() > 0));
            }
        }
    }

    @Test
    void foodIsMoreFrequentButNeverGuaranteedAndUsesTheLargerStacks() throws Exception {
        var randomField = LootTableService.class.getDeclaredField("random");
        randomField.setAccessible(true);
        ((Random) randomField.get(service)).setSeed(171L);
        Set<Material> cooked = Set.of(Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN);
        for (String table : List.of("hg_single", "hg_refill")) {
            boolean refill = table.equals("hg_refill");
            int foodChests = 0;
            Set<Material> seen = new HashSet<>();
            for (int i = 0; i < 10_000; i++) {
                boolean foodFound = false;
                for (ItemStack item : service.generateLoot(table)) {
                    if (cooked.contains(item.getType())) {
                        assertTrue(item.getAmount() >= (refill ? 3 : 2));
                        assertTrue(item.getAmount() <= (refill ? 5 : 4));
                        foodFound = true;
                        seen.add(item.getType());
                    } else if (item.getType() == Material.GOLDEN_CARROT) {
                        assertTrue(item.getAmount() >= 2 && item.getAmount() <= 3);
                        foodFound = true;
                    }
                }
                if (foodFound) foodChests++;
            }
            assertEquals(cooked, seen);
            double expectedChance = refill ? 0.42358926751480586D : 0.48556051442903736D;
            assertEquals(expectedChance, foodChests / 10_000.0D, 0.02D, table);
            assertTrue(foodChests < 10_000, "food must not be guaranteed");
        }
    }

    @Test
    void goldenAppleOddsAndAmountsRemainExactlyAtThePreFoodBalanceValues() throws Exception {
        for (String table : List.of("hg_single", "hg_refill")) {
            boolean refill = table.equals("hg_refill");
            JsonObject root = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/loot_tables", table + ".json"))).getAsJsonObject();
            var tiers = root.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("tiers");
            double appleChance = 0;
            int appleEntries = 0;
            for (var tierElement : tiers) {
                JsonObject tier = tierElement.getAsJsonObject();
                int totalEntryWeight = 0;
                JsonObject apple = null;
                for (var entryElement : tier.getAsJsonArray("entries")) {
                    JsonObject entry = entryElement.getAsJsonObject();
                    totalEntryWeight += weight(entry);
                    if (entry.get("name").getAsString().equals("minecraft:golden_apple")) apple = entry;
                }
                if (apple == null) continue;
                appleEntries++;
                appleChance += tier.get("weight").getAsDouble() / 100.0D * weight(apple) / totalEntryWeight;
                String name = tier.get("name").getAsString();
                int expectedCount = name.equals("top") || (refill && name.equals("high")) ? 2 : 1;
                int actualCount = apple.has("functions")
                    ? apple.getAsJsonArray("functions").get(0).getAsJsonObject().get("count").getAsInt() : 1;
                assertEquals(expectedCount, actualCount, table + " " + name);
            }
            assertEquals(refill ? 4 : 3, appleEntries);
            double previousChance = refill ? 0.35D / 15 + 0.23D / 13 + 0.10D / 11 + 0.05D / 9
                : 0.13D / 14 + 0.10D / 10 + 0.05D / 9;
            assertEquals(previousChance, appleChance, 1.0e-12D, table);
        }
    }

    private static int weight(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsInt() : 1;
    }

    @Test
    void runtimeRefillNeverEmitsRemovedEarlyGameItems() {
        Set<Material> forbidden = Set.of(Material.WOODEN_SWORD, Material.WOODEN_AXE, Material.STONE_SWORD,
            Material.STONE_AXE, Material.COPPER_SWORD, Material.COPPER_AXE, Material.COPPER_PICKAXE,
            Material.COPPER_INGOT, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS);
        for (int iteration = 0; iteration < 5_000; iteration++) {
            assertFalse(service.generateLoot("hg_refill").stream().anyMatch(item -> forbidden.contains(item.getType())));
        }
    }
}
