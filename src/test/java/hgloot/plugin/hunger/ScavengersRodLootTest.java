package hgloot.plugin.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class ScavengersRodLootTest {
    @Test
    void firstFiveCatchesUseExactRareHighTopDistribution() {
        for (int catchNumber = 1; catchNumber < HungerGamesUtilityService.SCAVENGER_MAX_CATCHES; catchNumber++) {
            Map<HungerGamesUtilityService.ScavengerTier, Integer> counts = tierCounts(catchNumber);
            assertEquals(35, counts.get(HungerGamesUtilityService.ScavengerTier.RARE));
            assertEquals(40, counts.get(HungerGamesUtilityService.ScavengerTier.HIGH));
            assertEquals(25, counts.get(HungerGamesUtilityService.ScavengerTier.TOP));
        }
    }

    @Test
    void sixthCatchCanOnlyRollSixtyPercentHighFortyPercentTop() {
        Map<HungerGamesUtilityService.ScavengerTier, Integer> counts =
            tierCounts(HungerGamesUtilityService.SCAVENGER_MAX_CATCHES);

        assertEquals(0, counts.get(HungerGamesUtilityService.ScavengerTier.RARE));
        assertEquals(60, counts.get(HungerGamesUtilityService.ScavengerTier.HIGH));
        assertEquals(40, counts.get(HungerGamesUtilityService.ScavengerTier.TOP));
    }

    @Test
    void poolsContainOnlyApprovedRewardsAndNeverForbiddenDiamondGear() {
        Set<Material> forbidden = Set.of(
            Material.DIAMOND_SWORD,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS
        );
        Set<Material> weakRemovedRewards = Set.of(
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.IRON_SWORD,
            Material.IRON_PICKAXE,
            Material.IRON_HELMET,
            Material.IRON_BOOTS,
            Material.BOW,
            Material.EXPERIENCE_BOTTLE,
            Material.WATER_BUCKET
        );
        Set<Material> generated = new HashSet<>();
        for (HungerGamesUtilityService.ScavengerTier tier : HungerGamesUtilityService.ScavengerTier.values()) {
            List<HungerGamesUtilityService.ScavengerReward> rewards = HungerGamesUtilityService.scavengerRewards(tier);
            assertFalse(rewards.isEmpty());
            for (HungerGamesUtilityService.ScavengerReward reward : rewards) {
                generated.add(reward.material());
                if (reward.bonusMaterial() != null) generated.add(reward.bonusMaterial());
            }
        }

        assertTrue(generated.stream().noneMatch(forbidden::contains));
        assertTrue(generated.stream().noneMatch(weakRemovedRewards::contains));
    }

    @Test
    void crossbowRewardsIncludeTheApprovedArrowPayouts() {
        HungerGamesUtilityService.ScavengerReward rareCrossbow = rewardFor(
            HungerGamesUtilityService.ScavengerTier.RARE, Material.CROSSBOW);
        assertEquals(Material.ARROW, rareCrossbow.bonusMaterial());
        assertEquals(10, rareCrossbow.bonusMinAmount());
        assertEquals(14, rareCrossbow.bonusMaxAmount());

        HungerGamesUtilityService.ScavengerReward highCrossbow = rewardFor(
            HungerGamesUtilityService.ScavengerTier.HIGH, Material.CROSSBOW);
        assertEquals(Material.ARROW, highCrossbow.bonusMaterial());
        assertEquals(16, highCrossbow.bonusMinAmount());
        assertEquals(16, highCrossbow.bonusMaxAmount());
    }

    @Test
    void allRequestedStackRangesAreExact() {
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.DIAMOND, 1, 2);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.GOLDEN_APPLE, 2, 2);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.ENDER_PEARL, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.IRON_CHESTPLATE, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.IRON_LEGGINGS, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.TNT, 2, 3);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.WIND_CHARGE, 4, 6);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.COBWEB, 3, 4);
        assertReward(HungerGamesUtilityService.ScavengerTier.RARE, Material.GOLDEN_CARROT, 4, 6);

        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.DIAMOND, 2, 3);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.DIAMOND_HELMET, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.DIAMOND_BOOTS, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.GOLDEN_APPLE, 2, 3);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.ENDER_PEARL, 2, 2);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.TNT, 3, 4);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.WIND_CHARGE, 6, 8);
        assertReward(HungerGamesUtilityService.ScavengerTier.HIGH, Material.COBWEB, 4, 6);

        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.DIAMOND, 4, 4);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.DIAMOND_HELMET, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.DIAMOND_BOOTS, 1, 1);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.GOLDEN_APPLE, 3, 3);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.ENDER_PEARL, 2, 3);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.WIND_CHARGE, 8, 8);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.COBWEB, 6, 6);
        assertReward(HungerGamesUtilityService.ScavengerTier.TOP, Material.TNT, 5, 5);
    }

    @Test
    void successfulCatchLifecycleAndSharedCraftLimitRemainUnchanged() throws Exception {
        String utility = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java"));
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        String recipe = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/recipe/RecipeBookCatalog.java"));

        assertEquals(6, HungerGamesUtilityService.SCAVENGER_MAX_CATCHES);
        assertTrue(utility.contains("event.getState() != PlayerFishEvent.State.CAUGHT_FISH"));
        assertTrue(utility.contains("event.getHand() == EquipmentSlot.OFF_HAND"));
        assertTrue(utility.contains("ItemStack rod = itemInHand(player, hand)"));
        assertTrue(utility.contains("if (uses >= SCAVENGER_MAX_CATCHES)"));
        assertTrue(utility.contains("consumeOne(player, rod, hand)"));
        assertTrue(utility.contains("breakEffect(player, Material.FISHING_ROD)"));
        assertFalse(utility.contains("FishHook.class"));
        assertTrue(queue.contains("Map.entry(\"scavengers_rod\", 1)"));
        assertTrue(recipe.contains("cell(1, 1, Material.FISHING_ROD)"));
        assertTrue(recipe.contains("cell(2, 1, Material.CHEST)"));
    }

    @Test
    void scavengerServiceDoesNotOwnNormalHgChestTables() throws Exception {
        String utility = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java"));
        assertFalse(utility.contains("hg_single"));
        assertFalse(utility.contains("hg_refill"));
    }

    private static Map<HungerGamesUtilityService.ScavengerTier, Integer> tierCounts(int catchNumber) {
        Map<HungerGamesUtilityService.ScavengerTier, Integer> counts =
            new EnumMap<>(HungerGamesUtilityService.ScavengerTier.class);
        for (HungerGamesUtilityService.ScavengerTier tier : HungerGamesUtilityService.ScavengerTier.values()) {
            counts.put(tier, 0);
        }
        for (int roll = 0; roll < 100; roll++) {
            counts.compute(HungerGamesUtilityService.scavengerTierForRoll(catchNumber, roll),
                (ignored, count) -> count + 1);
        }
        return counts;
    }

    private static HungerGamesUtilityService.ScavengerReward rewardFor(
        HungerGamesUtilityService.ScavengerTier tier,
        Material material
    ) {
        HungerGamesUtilityService.ScavengerReward reward = HungerGamesUtilityService.scavengerRewards(tier)
            .stream()
            .filter(candidate -> candidate.material() == material)
            .findFirst()
            .orElse(null);
        assertNotNull(reward);
        return reward;
    }

    private static void assertReward(
        HungerGamesUtilityService.ScavengerTier tier,
        Material material,
        int minAmount,
        int maxAmount
    ) {
        HungerGamesUtilityService.ScavengerReward reward = rewardFor(tier, material);
        assertEquals(minAmount, reward.minAmount());
        assertEquals(maxAmount, reward.maxAmount());
    }
}
