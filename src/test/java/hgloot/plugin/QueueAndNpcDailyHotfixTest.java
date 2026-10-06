package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class QueueAndNpcDailyHotfixTest {
    @Test
    void hungerGamesUsesProtectedLeaveQueueControlAndAuthoritativeLeavePath() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));

        assertTrue(menu.contains("HUB_ITEM_LEAVE_HUNGER_GAMES"));
        assertTrue(menu.contains("QueueService.QueueType.HUNGERGAMES"));
        assertTrue(menu.contains("queueService.leaveQueueOrMatch(player)"));
        assertTrue(menu.contains("HUB_ITEM_LEAVE_ROULETTE, HUB_ITEM_LEAVE_HUNGER_GAMES -> QUEUE_CONTROL_SLOT"));
        assertTrue(queue.contains("public boolean isPlayerQueuedFor(UUID playerId, QueueType type)"));
    }

    @Test
    void npcDailyActionsPersistAndUseProgressionServices() throws Exception {
        String npc = Files.readString(Path.of("src/main/java/hgloot/plugin/NpcService.java"));
        String daily = Files.readString(Path.of("src/main/java/hgloot/plugin/DailyRewardService.java"));

        assertTrue(npc.contains("/npc set <id> daily <add|remove|clear>"));
        assertTrue(npc.contains("dailyRewardService.addOrReplace"));
        assertTrue(npc.contains("dailyRewardService.remove"));
        assertTrue(npc.contains("dailyRewardService.claim"));
        assertTrue(daily.contains("ZoneId.of(\"America/New_York\")"));
        assertTrue(daily.contains("npc_daily_global_claim"));
        assertTrue(daily.contains("progressionService.grantShards"));
        assertTrue(daily.contains("progressionService.grantShardFragments"));
        assertTrue(daily.contains("progressionService.grantPrestigeXp"));
        assertTrue(daily.contains("progressionService.queueSilver"));
        assertTrue(npc.contains("trimmed.regionMatches(true, 0, \"daily:\""));
    }
}
