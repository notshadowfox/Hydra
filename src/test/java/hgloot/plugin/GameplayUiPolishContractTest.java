package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GameplayUiPolishContractTest {
    @Test
    void hgSpectatorsReceiveRequeueAndReturnControls() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        assertTrue(queue.contains("SPECTATOR_ACTION_HG_REQUEUE"));
        assertTrue(queue.contains("QueueType type = resolveQueueTypeForWorld(worldName)"));
        assertTrue(queue.contains("if (isHungerGames(type))"));
        assertTrue(queue.contains("joinQueue(player, duos ? QueueType.HUNGERGAMES_DUOS : QueueType.HUNGERGAMES, false)"));
        assertTrue(queue.contains("\"hydra:requeue\""));
        assertTrue(queue.contains("\"hydra:backspace\""));
    }

    @Test
    void productionLegendaryPoolExcludesGeneratorAndBetaDefinitions() throws Exception {
        String recipes = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/ultraroyales/OriginalLegendaryRecipes.java"));
        String koth = Files.readString(Path.of("src/main/java/hgloot/plugin/KothService.java"));
        assertTrue(recipes.contains("PRODUCTION_REWARDS = ORIGINAL_EIGHT.stream()"));
        assertTrue(recipes.contains("!recipe.id().equals(\"royal_roulette\")"));
        assertTrue(koth.contains("randomProductionReward(ThreadLocalRandom.current())"));
        assertFalse(koth.contains("Items.royalRoulette()"));
    }

    @Test
    void shardOddsDriveBothSelectionAndTooltipAndMenuReturnsToOpen() throws Exception {
        String progression = Files.readString(Path.of("src/main/java/hgloot/plugin/ProgressionService.java"));
        assertTrue(progression.contains("shardRarityWeights()"));
        assertTrue(progression.contains("positiveWeight(\"common\", 50.0D)"));
        assertTrue(progression.contains("positiveWeight(\"limited\", 2.0D)"));
        assertTrue(progression.contains("selectShardReward(rewardIds)"));
        assertTrue(progression.contains("weights.get(rarity) * 100.0D"));
        assertTrue(progression.contains("scheduleShardMenuReturn(playerId)"));
        assertTrue(progression.contains("openShardMenu(online)"));
    }

    @Test
    void coinPresentationKeepsLegacyPersistenceKeysAndAliases() throws Exception {
        String progression = Files.readString(Path.of("src/main/java/hgloot/plugin/ProgressionService.java"));
        String auction = Files.readString(Path.of("src/main/java/hgloot/plugin/AuctionHouseService.java"));
        assertTrue(progression.contains("wallet.silver"));
        assertTrue(auction.contains("casino.silver"));
        assertTrue(auction.contains("SILVER(\"Coins\")"));
        assertTrue(auction.contains("sub.equals(\"addcoins\") || sub.equals(\"addgold\") || sub.equals(\"addsilver\")"));
    }

    @Test
    void bossRushUsesActualMatchCountdownAndTenSecondBossFreeze() throws Exception {
        String bossRush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        assertTrue(bossRush.contains("BOSS_START_FREEZE_TICKS = 10 * 20"));
        assertTrue(bossRush.contains("startEncounterCountdown(players)"));
        assertTrue(bossRush.contains("ChatColor.GREEN + \"GO\""));
        assertTrue(bossRush.contains("state = State.ACTIVE"));
    }

    @Test
    void bridgeSpectatorLeaveItemUsesExistingBackspaceAsset() throws Exception {
        String bridge = Files.readString(Path.of("src/main/java/hgloot/plugin/BridgeService.java"));
        assertTrue(bridge.contains("SPECTATOR_ACTION_HUB.equals(action)"));
        assertTrue(bridge.contains("meta.setItemModel(NamespacedKey.fromString(\"hydra:backspace\"))"));
    }
}
