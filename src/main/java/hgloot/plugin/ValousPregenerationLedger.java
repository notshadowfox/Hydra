package hgloot.plugin;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable handoff contract between Valous pregeneration and postprocessing.
 */
record ValousPregenerationLedger(
    String worldName,
    UUID worldId,
    UUID generationToken,
    String queueProfileKey,
    String pregenerationMode,
    int minBlockX,
    int maxBlockX,
    int minBlockZ,
    int maxBlockZ,
    int minChunkX,
    int maxChunkX,
    int minChunkZ,
    int maxChunkZ,
    int expectedChunkCount,
    Set<Long> completedChunkKeys,
    Set<Long> failedChunkKeys,
    long completedAtMs,
    boolean canceled,
    String generationVersion,
    String datapackVersion
) {
    ValousPregenerationLedger {
        completedChunkKeys = Set.copyOf(completedChunkKeys);
        failedChunkKeys = Set.copyOf(failedChunkKeys);
    }

    boolean isComplete() {
        return !canceled
            && failedChunkKeys.isEmpty()
            && completedChunkKeys.size() == expectedChunkCount;
    }

    boolean contains(long chunkKey) {
        return completedChunkKeys.contains(chunkKey);
    }

    boolean insideExpectedBounds(int chunkX, int chunkZ) {
        return chunkX >= minChunkX
            && chunkX <= maxChunkX
            && chunkZ >= minChunkZ
            && chunkZ <= maxChunkZ;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
