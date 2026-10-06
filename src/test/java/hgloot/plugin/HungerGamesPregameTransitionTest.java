package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HungerGamesPregameTransitionTest {
    private static final Path QUEUE_SERVICE = Path.of("src/main/java/hgloot/plugin/QueueService.java");

    @Test
    void hungerGamesPrewarmWaitsForTheGameHubPresentationWindow() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);
        int joinStart = source.indexOf("private void joinQueue(Player player, QueueType type, boolean includeParty)");
        int joinEnd = source.indexOf("private void joinDuoQueue", joinStart);
        String join = source.substring(joinStart, joinEnd);
        int helperStart = source.indexOf("private void requestPrewarmAfterPlayerStaging");
        int helperEnd = source.indexOf("private void logHungerGamesPlayerTransition", helperStart);
        String helper = source.substring(helperStart, helperEnd);

        assertTrue(join.indexOf("sendPlayerToGameHub(player, () -> hydra.menuService().refreshHubInterface(player))")
            < join.indexOf("requestPrewarmAfterPlayerStaging(type)"));
        assertTrue(helper.contains("!isHungerGames(type)"));
        assertTrue(helper.contains("runTaskLater"));
        assertTrue(helper.contains("HUNGER_GAMES_GAMEHUB_SETTLE_TICKS"));
        assertTrue(helper.contains("queueSize(type) <= 0"));
        assertTrue(helper.indexOf("runTaskLater") < helper.lastIndexOf("maybePrewarmMatchWorld(type)"));
    }

    @Test
    void synchronousHgWorldCreationIsDeferredOutOfTheQueueJoinTick() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);
        int methodStart = source.indexOf("private CompletableFuture<World> createHungerGamesMatchWorldAsync");
        int methodEnd = source.indexOf("private World createHungerGamesMatchWorldBase", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("Bukkit.getScheduler().runTask(plugin"));
        assertTrue(method.indexOf("Bukkit.getScheduler().runTask(plugin")
            < method.indexOf("createHungerGamesMatchWorldBase(worldName, type)"));
    }

    @Test
    void hgLaunchRequiresEveryPreparationStageToReachReady() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);

        assertTrue(source.contains("HungerGamesPreparationStage.WORLD_CREATED"));
        assertTrue(source.contains("HungerGamesPreparationStage.PRELOAD_COMPLETE"));
        assertTrue(source.contains("HungerGamesPreparationStage.STRUCTURE_PASTE_COMPLETE"));
        assertTrue(source.contains("HungerGamesPreparationStage.LOOT_COMPLETE"));
        assertTrue(source.contains("HungerGamesPreparationStage.READY"));

        int readinessStart = source.indexOf("private boolean ensureMatchWorldReady");
        int readinessEnd = source.indexOf("private void continueStartMatchAfterBrWorldReady", readinessStart);
        String readiness = source.substring(readinessStart, readinessEnd);
        assertTrue(readiness.contains("isHungerGames(type)"));
        assertTrue(readiness.contains("!= HungerGamesPreparationStage.READY"));
    }

    @Test
    void minigameRouletteQueueFlowRemainsIndependent() throws Exception {
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        int joinStart = roulette.indexOf("private void joinQueue(Player player, boolean includeParty)");
        int joinEnd = roulette.indexOf("private void joinPartyQueue", joinStart);
        String join = roulette.substring(joinStart, joinEnd);

        assertTrue(join.contains("plugin.queueService().sendPlayerToGameHub(player)"));
        assertFalse(join.contains("maybePrewarmMatchWorld"));
    }

    @Test
    void cleanupDropsHgReadinessStateWithTheWorld() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);
        int cleanupStart = source.indexOf("private void cleanupSingleWorld");
        int cleanupEnd = source.indexOf("private void", cleanupStart + "private void".length());
        String cleanup = source.substring(cleanupStart, cleanupEnd);

        assertTrue(cleanup.contains("hungerGamesPreparationStages.remove(worldName)"));
        assertTrue(cleanup.contains("hungerGamesPreparationStartedAtMs.remove(worldName)"));
    }

    @Test
    void readyHgArenaKeepsItsReadinessChunkLoadedUntilConsumedOrCleaned() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);
        int finalizeStart = source.indexOf("private World finalizeHungerGamesMatchWorld");
        int finalizeEnd = source.indexOf("private void markHungerGamesPreparationStage", finalizeStart);
        String finalize = source.substring(finalizeStart, finalizeEnd);
        assertTrue(finalize.contains("holdHungerGamesReadySpawnChunk(world)"));
        assertTrue(finalize.indexOf("holdHungerGamesReadySpawnChunk(world)")
            < finalize.indexOf("HungerGamesPreparationStage.READY"));

        int consumeStart = source.indexOf("private void consumePrewarmedWorld");
        int consumeEnd = source.indexOf("private void startMatch", consumeStart);
        String consume = source.substring(consumeStart, consumeEnd);
        assertTrue(consume.contains("releaseHungerGamesReadySpawnChunk(worldName)"));

        int cleanupStart = source.indexOf("private void cleanupSingleWorld");
        int cleanupEnd = source.indexOf("private void", cleanupStart + "private void".length());
        String cleanup = source.substring(cleanupStart, cleanupEnd);
        assertTrue(cleanup.contains("releaseHungerGamesReadySpawnChunk(worldName)"));
    }

    @Test
    void completedPrewarmDeduplicationDoesNotCountTheCompletingFlightAgainstItself() throws Exception {
        String source = Files.readString(QUEUE_SERVICE);
        int prewarmStart = source.indexOf("private void maybePrewarmMatchWorld");
        int prewarmEnd = source.indexOf("private boolean shouldDeferPrewarmedWorldCleanup", prewarmStart);
        String prewarm = source.substring(prewarmStart, prewarmEnd);
        int duplicateStart = prewarm.indexOf("if (!isBattleRoyaleType(type)");
        int duplicateEnd = prewarm.indexOf("if (world == null)", duplicateStart);
        String duplicateCheck = prewarm.substring(duplicateStart, duplicateEnd);

        assertTrue(duplicateCheck.contains("hasEnoughCompletedPrewarmedMatchWorlds(type)"));
        assertFalse(duplicateCheck.contains("!shouldRequestPrewarmedMatchWorld(type)"));

        int completedStart = source.indexOf("private boolean hasEnoughCompletedPrewarmedMatchWorlds");
        int completedEnd = source.indexOf("private boolean", completedStart + "private boolean".length());
        String completed = source.substring(completedStart, completedEnd);
        assertTrue(completed.contains("prewarmedMatchWorldCount(type) + detachedPrewarmedMatchWorldCount(type)"));
        assertFalse(completed.contains("prewarmInProgress"));
    }
}
