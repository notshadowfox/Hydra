package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameplaySecondFixBatchContractTest {
    @Test
    void hungerGamesHealthHasExplicitOwnershipAndExitNormalization() throws Exception {
        String queue = read("src/main/java/hgloot/plugin/QueueService.java");
        assertTrue(queue.contains("HUNGER_GAMES_PLAYER_MAX_HEALTH = 40.0D"));
        assertTrue(queue.contains("STANDARD_PLAYER_MAX_HEALTH = 20.0D"));
        assertTrue(queue.contains("fullyHealMatchPlayer(Player player, QueueType type)"));
        assertTrue(queue.contains("if (isHungerGames(type)) {\n            restoreStandardPlayerHealth(player);"));
        assertTrue(queue.contains("clearHungerGamesCombatGrace(player);\n        restoreStandardPlayerHealth(player);"));
    }

    @Test
    void spleefUpgradeIsThresholdBoundAndDeduplicated() throws Exception {
        String spleef = read("src/main/java/hgloot/plugin/SpleefService.java");
        assertTrue(spleef.contains("if (currentBreaks >= SHOVEL_UPGRADE_BREAKS)"));
        assertTrue(spleef.contains("if (breaks == SHOVEL_UPGRADE_BREAKS)"));
        assertTrue(spleef.contains("replaceSpleefShovels(player, Material.DIAMOND_SHOVEL)"));
        assertTrue(spleef.contains("if (isSpleefShovel(inventory.getItem(slot)))"));
        assertFalse(spleef.contains("if (breaks >= SHOVEL_UPGRADE_BREAKS) {\n            player.getInventory().setItem(0"));
    }

    @Test
    void frogBootsRecipeMatchesLockedShape() throws Exception {
        String catalog = read("src/main/java/hgloot/plugin/recipe/RecipeBookCatalog.java");
        assertTrue(catalog.contains(
            "cell(0, 1, Material.DIAMOND), cell(1, 0, Material.LILY_PAD), cell(1, 1, Material.DIAMOND_BOOTS),\n"
                + "                cell(1, 2, Material.LILY_PAD), cell(2, 1, Material.SLIME_BLOCK)"));
        assertFalse(catalog.contains("cell(1, 0, Material.SLIME_BALL), cell(1, 1, Material.DIAMOND_BOOTS)"));
    }

    @Test
    void startingWaterBucketIsClearlyMoreLikelyThanLavaWithoutChangingRefill() throws Exception {
        JsonObject start = table("hg_single.json");
        JsonObject refill = table("hg_refill.json");
        WeightedEntry water = weightedEntry(start, "minecraft:water_bucket");
        WeightedEntry lava = weightedEntry(start, "minecraft:lava_bucket");
        assertEquals(3, water.entryWeight());
        assertTrue(water.perRollChance() >= lava.perRollChance() * 2.5D);
        assertEquals(1, weightedEntry(refill, "minecraft:water_bucket").entryWeight());
    }

    @Test
    void tntMinecartDamageCapsAtFiveHeartsOnlyForItsListenerPath() throws Exception {
        assertEquals(10.0D, QueueService.capTntMinecartDamage(40.0D));
        assertEquals(7.5D, QueueService.capTntMinecartDamage(7.5D));
        String queue = read("src/main/java/hgloot/plugin/QueueService.java");
        assertTrue(queue.contains("event.getDamager() instanceof ExplosiveMinecart"));
        assertTrue(queue.contains("event.setDamage(capTntMinecartDamage(event.getDamage()))"));
        assertFalse(queue.contains("event.getDamager() instanceof TNTPrimed"));
    }

    private static WeightedEntry weightedEntry(JsonObject root, String itemName) {
        JsonArray tiers = root.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("tiers");
        for (var tierElement : tiers) {
            JsonObject tier = tierElement.getAsJsonObject();
            JsonArray entries = tier.getAsJsonArray("entries");
            int totalEntryWeight = 0;
            for (var entryElement : entries) {
                JsonObject entry = entryElement.getAsJsonObject();
                totalEntryWeight += entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            }
            for (var entryElement : entries) {
                JsonObject entry = entryElement.getAsJsonObject();
                if (!itemName.equals(entry.get("name").getAsString())) {
                    continue;
                }
                int entryWeight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                double chance = (tier.get("weight").getAsDouble() / 100.0D)
                    * ((double) entryWeight / totalEntryWeight);
                return new WeightedEntry(entryWeight, chance);
            }
        }
        throw new AssertionError("Missing loot entry " + itemName);
    }

    private static JsonObject table(String file) throws Exception {
        return JsonParser.parseString(read("src/main/resources/loot_tables/" + file)).getAsJsonObject();
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    private record WeightedEntry(int entryWeight, double perRollChance) {
    }
}
