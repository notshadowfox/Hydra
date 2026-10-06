package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HungerGamesMegaPassContractTest {
    @Test
    void startingAndRefillTablesUseFiveCoreAndTwoIngredientRolls() throws Exception {
        assertRollContract("hg_single.json", new int[]{45, 27, 13, 10, 5});
        assertRollContract("hg_refill.json", new int[]{27, 35, 23, 10, 5});
    }

    @Test
    void ingredientPoolContainsAllFourDedicatedIngredients() throws Exception {
        for (String file : new String[]{"hg_single.json", "hg_refill.json"}) {
            JsonArray entries = table(file).getAsJsonArray("pools").get(1).getAsJsonObject().getAsJsonArray("entries");
            Set<String> names = new HashSet<>();
            entries.forEach(entry -> names.add(entry.getAsJsonObject().get("name").getAsString()));
            assertEquals(Set.of("minecraft:quartz", "minecraft:lily_pad", "minecraft:slime_ball", "minecraft:ink_sac"), names);
        }
    }

    @Test
    void forbiddenFinishedGearAndRefillDowngradesStayAbsent() throws Exception {
        Set<String> start = allNames(table("hg_single.json"));
        for (String forbidden : Set.of("minecraft:diamond_sword", "minecraft:diamond_chestplate",
            "minecraft:diamond_leggings", "minecraft:wooden_pickaxe", "minecraft:golden_pickaxe",
            "minecraft:golden_axe", "minecraft:cooked_beef", "minecraft:carrot")) {
            assertFalse(start.contains(forbidden), forbidden);
        }
        Set<String> refill = allNames(table("hg_refill.json"));
        for (String forbidden : Set.of("minecraft:wooden_sword", "minecraft:wooden_axe", "minecraft:stone_sword",
            "minecraft:stone_axe", "minecraft:copper_sword", "minecraft:copper_axe", "minecraft:copper_pickaxe",
            "minecraft:copper_ingot", "minecraft:golden_sword", "minecraft:diamond_sword",
            "minecraft:diamond_chestplate", "minecraft:diamond_leggings")) {
            assertFalse(refill.contains(forbidden), forbidden);
        }
        assertTrue(start.contains("minecraft:fishing_rod"));
        assertTrue(start.contains("minecraft:tnt"));
    }

    private static void assertRollContract(String file, int[] tierWeights) throws Exception {
        JsonArray pools = table(file).getAsJsonArray("pools");
        assertEquals(2, pools.size());
        assertEquals(5, pools.get(0).getAsJsonObject().get("rolls").getAsInt());
        assertEquals(2, pools.get(1).getAsJsonObject().get("rolls").getAsInt());
        JsonArray tiers = pools.get(0).getAsJsonObject().getAsJsonArray("tiers");
        assertEquals(tierWeights.length, tiers.size());
        int total = 0;
        for (int i = 0; i < tierWeights.length; i++) {
            int weight = tiers.get(i).getAsJsonObject().get("weight").getAsInt();
            assertEquals(tierWeights[i], weight);
            total += weight;
        }
        assertEquals(100, total);
    }

    private static JsonObject table(String file) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/loot_tables", file))).getAsJsonObject();
    }

    private static Set<String> allNames(JsonObject root) {
        Set<String> names = new HashSet<>();
        JsonArray pools = root.getAsJsonArray("pools");
        for (var poolElement : pools) {
            JsonObject pool = poolElement.getAsJsonObject();
            collect(pool.getAsJsonArray("entries"), names);
            JsonArray tiers = pool.getAsJsonArray("tiers");
            if (tiers != null) tiers.forEach(tier -> collect(tier.getAsJsonObject().getAsJsonArray("entries"), names));
        }
        return names;
    }

    private static void collect(JsonArray entries, Set<String> names) {
        if (entries == null) return;
        entries.forEach(entry -> names.add(entry.getAsJsonObject().get("name").getAsString()));
    }
}
