package hgloot.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bridges completed Valous pregeneration into no-generation postprocessing.
 */
final class ValousChunkHandoff {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long TIMEOUT_MS = 10_000L;
    private static final int MAX_CONCURRENT_ACQUISITIONS = 32;

    private ValousChunkHandoff() {
    }

    interface LifecycleGuard {
        boolean isCurrent();

        boolean isCanceled();

        boolean isWorldUnloading();

        String flightState();
    }

    static Job start(
        JavaPlugin plugin,
        World world,
        ValousPregenerationLedger ledger,
        int playableDiameter,
        Path reportRoot,
        LifecycleGuard guard,
        Consumer<Result> completion
    ) {
        Job job = new Job(plugin, world, ledger, playableDiameter, reportRoot, guard, completion);
        job.start();
        return job;
    }

    record FailureDiagnostic(
        int blockX,
        int blockY,
        int blockZ,
        int chunkX,
        int chunkZ,
        long chunkKey,
        boolean insideRequestedBounds,
        boolean insideExpectedChunkBounds,
        boolean presentInCompletedLedger,
        boolean paperGenerated,
        boolean paperLoaded,
        String acquisitionResult,
        String exceptionClass,
        String exceptionMessage,
        String stackTrace,
        int retryCount,
        UUID worldId,
        UUID generationToken,
        String flightState,
        boolean worldUnloading,
        boolean cancellationRequested
    ) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            JsonArray block = new JsonArray();
            block.add(blockX);
            block.add(blockY);
            block.add(blockZ);
            value.add("block", block);
            JsonArray chunk = new JsonArray();
            chunk.add(chunkX);
            chunk.add(chunkZ);
            value.add("chunk", chunk);
            value.addProperty("chunk_key", Long.toUnsignedString(chunkKey));
            value.addProperty("inside_requested_bounds", insideRequestedBounds);
            value.addProperty("inside_expected_chunk_bounds", insideExpectedChunkBounds);
            value.addProperty("present_in_completed_ledger", presentInCompletedLedger);
            value.addProperty("paper_generated", paperGenerated);
            value.addProperty("paper_loaded", paperLoaded);
            value.addProperty("acquisition_result", acquisitionResult);
            value.addProperty("exception_class", exceptionClass);
            value.addProperty("exception_message", exceptionMessage);
            value.addProperty("stack_trace", stackTrace);
            value.addProperty("retry_count", retryCount);
            value.addProperty("world_uuid", worldId == null ? "" : worldId.toString());
            value.addProperty("generation_token", generationToken == null ? "" : generationToken.toString());
            value.addProperty("flight_state", flightState);
            value.addProperty("world_unloading", worldUnloading);
            value.addProperty("cancellation_requested", cancellationRequested);
            return value;
        }
    }

    record Result(
        boolean passed,
        String failureReason,
        int expectedLedgerCount,
        int completedLedgerCount,
        int failedLedgerCount,
        int requiredChunkCount,
        long handoffMs,
        boolean visibilityBarrierNeeded,
        int peakHeldCount,
        int releasedCount,
        Map<String, Integer> failureCategories,
        FailureDiagnostic firstFailure,
        Lease lease,
        Path reportPath
    ) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("status", passed ? "pass" : "fail");
            value.addProperty("failure_reason", failureReason == null ? "" : failureReason);
            value.addProperty("expected_ledger_count", expectedLedgerCount);
            value.addProperty("completed_ledger_count", completedLedgerCount);
            value.addProperty("failed_ledger_count", failedLedgerCount);
            value.addProperty("required_chunk_count", requiredChunkCount);
            value.addProperty("handoff_ms", handoffMs);
            value.addProperty("visibility_barrier_needed", visibilityBarrierNeeded);
            value.addProperty("peak_held_count", peakHeldCount);
            value.addProperty("released_count", lease == null ? releasedCount : lease.releasedCount());
            value.addProperty("hold_duration_ms", lease == null ? 0L : lease.holdDurationMs());
            JsonObject categories = new JsonObject();
            failureCategories.forEach(categories::addProperty);
            value.add("failure_categories", categories);
            value.add("first_failure", firstFailure == null ? new JsonObject() : firstFailure.toJson());
            return value;
        }
    }

    static final class Lease {
        private final JavaPlugin plugin;
        private final World world;
        private final Set<Long> heldKeys;
        private final long acquiredAtMs;
        private final Path releaseReportPath;
        private boolean released;
        private int releasedCount;
        private long releasedAtMs;

        private Lease(JavaPlugin plugin, World world, Set<Long> heldKeys, Path reportPath) {
            this.plugin = plugin;
            this.world = world;
            this.heldKeys = Set.copyOf(heldKeys);
            this.acquiredAtMs = System.currentTimeMillis();
            this.releaseReportPath = reportPath.resolveSibling("chunk-handoff-release.json");
        }

        boolean contains(long key) {
            return heldKeys.contains(key);
        }

        int heldCount() {
            return heldKeys.size();
        }

        synchronized int releasedCount() {
            return releasedCount;
        }

        synchronized long holdDurationMs() {
            return (released ? releasedAtMs : System.currentTimeMillis()) - acquiredAtMs;
        }

        synchronized int release(String reason) {
            if (released) {
                return releasedCount;
            }
            released = true;
            releasedAtMs = System.currentTimeMillis();
            int failures = 0;
            for (long key : heldKeys) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                try {
                    world.removePluginChunkTicket(chunkX, chunkZ, plugin);
                    releasedCount++;
                } catch (RuntimeException ex) {
                    failures++;
                }
            }
            plugin.getLogger().info("[Queue][ValousHandoff] event=holds_released world="
                + world.getName() + " held=" + heldKeys.size()
                + " released=" + releasedCount + " failures=" + failures
                + " hold_ms=" + holdDurationMs()
                + " reason=" + reason);
            JsonObject releaseReport = new JsonObject();
            releaseReport.addProperty("world", world.getName());
            releaseReport.addProperty("world_uuid", world.getUID().toString());
            releaseReport.addProperty("held_count", heldKeys.size());
            releaseReport.addProperty("released_count", releasedCount);
            releaseReport.addProperty("release_failures", failures);
            releaseReport.addProperty("hold_duration_ms", holdDurationMs());
            releaseReport.addProperty("reason", reason);
            try {
                Files.createDirectories(releaseReportPath.getParent());
                Files.writeString(
                    releaseReportPath,
                    GSON.toJson(releaseReport),
                    StandardCharsets.UTF_8
                );
            } catch (Exception ex) {
                plugin.getLogger().warning("[Queue][ValousHandoff] event=release_report_failed world="
                    + world.getName() + " error=" + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
            }
            return releasedCount;
        }
    }

    static final class Job {
        private final JavaPlugin plugin;
        private final World world;
        private final ValousPregenerationLedger ledger;
        private final Path reportPath;
        private final LifecycleGuard guard;
        private final Consumer<Result> completion;
        private final Deque<Long> remaining;
        private final Set<Long> requiredKeys;
        private final Set<Long> heldKeys = new LinkedHashSet<>();
        private final Map<Long, PendingAcquisition> pending = new LinkedHashMap<>();
        private final Map<Long, Integer> retries = new LinkedHashMap<>();
        private final Map<String, Integer> failureCategories = new LinkedHashMap<>();
        private final long startedAtMs = System.currentTimeMillis();
        private BukkitTask task;
        private FailureDiagnostic firstFailure;
        private boolean barrierNeeded;
        private boolean complete;
        private int peakHeldCount;

        private Job(
            JavaPlugin plugin,
            World world,
            ValousPregenerationLedger ledger,
            int playableDiameter,
            Path reportRoot,
            LifecycleGuard guard,
            Consumer<Result> completion
        ) {
            this.plugin = plugin;
            this.world = world;
            this.ledger = ledger;
            this.reportPath = reportRoot.resolve(world.getName()).resolve("chunk-handoff.json");
            this.guard = guard;
            this.completion = completion;
            this.requiredKeys = ValousRoutePostProcessor.requiredScanChunkKeys(playableDiameter);
            this.remaining = new ArrayDeque<>(requiredKeys);
        }

        private void start() {
            if (!ledger.isComplete()) {
                finish(false, "pregeneration_ledger_incomplete");
                return;
            }
            if (!world.getUID().equals(ledger.worldId())) {
                finish(false, "world_uuid_mismatch");
                return;
            }
            for (long key : requiredKeys) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                if (!ledger.insideExpectedBounds(chunkX, chunkZ) || !ledger.contains(key)) {
                    recordFailure(key, "absent_from_completed_ledger", null);
                    finish(false, "required_chunk_absent_from_completed_ledger");
                    return;
                }
            }
            plugin.getLogger().info("[Queue][ValousHandoff] event=started world=" + world.getName()
                + " token=" + ledger.generationToken()
                + " ledger=" + ledger.completedChunkKeys().size() + "/" + ledger.expectedChunkCount()
                + " required=" + requiredKeys.size() + " timeout_ms=" + TIMEOUT_MS);
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    tick();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        void cancel() {
            if (complete) {
                return;
            }
            finish(false, "handoff_canceled");
        }

        private void tick() {
            if (complete) {
                return;
            }
            if (!plugin.isEnabled() || !guard.isCurrent() || guard.isCanceled() || guard.isWorldUnloading()) {
                finish(false, guard.isWorldUnloading() ? "world_unloading_during_handoff" : "stale_or_canceled_handoff");
                return;
            }

            List<Long> resolved = new ArrayList<>();
            for (Map.Entry<Long, PendingAcquisition> entry : pending.entrySet()) {
                PendingAcquisition acquisition = entry.getValue();
                if (!acquisition.future().isDone()) {
                    continue;
                }
                resolved.add(entry.getKey());
                try {
                    Chunk chunk = acquisition.future().join();
                    if (chunk == null) {
                        retryOrFail(entry.getKey(), "no_generation_acquisition_returned_null", null);
                        continue;
                    }
                    world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
                    if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                        retryOrFail(entry.getKey(), "acquired_chunk_not_loaded", null);
                        continue;
                    }
                    heldKeys.add(entry.getKey());
                    peakHeldCount = Math.max(peakHeldCount, heldKeys.size());
                } catch (RuntimeException ex) {
                    retryOrFail(entry.getKey(), "no_generation_acquisition_exception", ex);
                }
            }
            resolved.forEach(pending::remove);

            while (!remaining.isEmpty() && pending.size() < MAX_CONCURRENT_ACQUISITIONS) {
                long key = remaining.removeFirst();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                if (!ledger.contains(key)) {
                    recordFailure(key, "absent_from_completed_ledger", null);
                    finish(false, "required_chunk_absent_from_completed_ledger");
                    return;
                }
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                    heldKeys.add(key);
                    peakHeldCount = Math.max(peakHeldCount, heldKeys.size());
                    continue;
                }
                barrierNeeded = true;
                CompletableFuture<Chunk> future;
                try {
                    future = world.getChunkAtAsync(chunkX, chunkZ, false, true);
                } catch (RuntimeException ex) {
                    retryOrFail(key, "no_generation_acquisition_threw", ex);
                    continue;
                }
                pending.put(key, new PendingAcquisition(future));
            }

            if (complete) {
                return;
            }
            if (remaining.isEmpty() && pending.isEmpty() && heldKeys.size() == requiredKeys.size()) {
                finish(true, "");
                return;
            }
            if (System.currentTimeMillis() - startedAtMs >= TIMEOUT_MS) {
                long key = !pending.isEmpty()
                    ? pending.keySet().iterator().next()
                    : remaining.isEmpty() ? 0L : remaining.peekFirst();
                recordFailure(key, "handoff_visibility_timeout", null);
                finish(false, "ledger_chunk_unavailable_after_visibility_barrier");
            }
        }

        private void retryOrFail(long key, String category, RuntimeException exception) {
            failureCategories.merge(category, 1, Integer::sum);
            recordFailure(key, category, exception);
            int retry = retries.merge(key, 1, Integer::sum);
            if (System.currentTimeMillis() - startedAtMs < TIMEOUT_MS) {
                remaining.addLast(key);
                return;
            }
            if (retry > 0) {
                finish(false, "ledger_chunk_unavailable_after_visibility_barrier");
            }
        }

        private void recordFailure(long key, String result, RuntimeException exception) {
            if (firstFailure != null) {
                return;
            }
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            int blockX = chunkX * 16;
            int blockZ = chunkZ * 16;
            String stack = "";
            if (exception != null) {
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                stack = writer.toString();
            }
            firstFailure = new FailureDiagnostic(
                blockX,
                ValousRoutePostProcessor.minimumScanY(),
                blockZ,
                chunkX,
                chunkZ,
                key,
                blockX >= ledger.minBlockX() && blockX <= ledger.maxBlockX()
                    && blockZ >= ledger.minBlockZ() && blockZ <= ledger.maxBlockZ(),
                ledger.insideExpectedBounds(chunkX, chunkZ),
                ledger.contains(key),
                world.isChunkGenerated(chunkX, chunkZ),
                world.isChunkLoaded(chunkX, chunkZ),
                result,
                exception == null ? "" : exception.getClass().getName(),
                exception == null || exception.getMessage() == null ? "" : exception.getMessage(),
                stack,
                retries.getOrDefault(key, 0),
                world.getUID(),
                ledger.generationToken(),
                guard.flightState(),
                guard.isWorldUnloading(),
                guard.isCanceled()
            );
        }

        private void finish(boolean passed, String reason) {
            if (complete) {
                return;
            }
            complete = true;
            if (task != null) {
                task.cancel();
            }
            Lease lease = passed ? new Lease(plugin, world, heldKeys, reportPath) : null;
            int released = 0;
            if (!passed) {
                Lease failedLease = new Lease(plugin, world, heldKeys, reportPath);
                released = failedLease.release("handoff_failed");
            }
            Result result = new Result(
                passed,
                reason,
                ledger.expectedChunkCount(),
                ledger.completedChunkKeys().size(),
                ledger.failedChunkKeys().size(),
                requiredKeys.size(),
                System.currentTimeMillis() - startedAtMs,
                barrierNeeded,
                peakHeldCount,
                released,
                Map.copyOf(failureCategories),
                firstFailure,
                lease,
                reportPath
            );
            writeReport(result);
            if (firstFailure != null) {
                plugin.getLogger().warning("[Queue][ValousHandoff] event=chunk_rejected world="
                    + world.getName() + " token=" + ledger.generationToken()
                    + " block=" + firstFailure.blockX() + "," + firstFailure.blockY() + "," + firstFailure.blockZ()
                    + " chunk=" + firstFailure.chunkX() + "," + firstFailure.chunkZ()
                    + " key=" + Long.toUnsignedString(firstFailure.chunkKey())
                    + " ledger=" + firstFailure.presentInCompletedLedger()
                    + " generated=" + firstFailure.paperGenerated()
                    + " loaded=" + firstFailure.paperLoaded()
                    + " acquisition=" + firstFailure.acquisitionResult()
                    + " exception=" + firstFailure.exceptionClass()
                    + " retries=" + firstFailure.retryCount()
                    + " state=" + firstFailure.flightState()
                    + " unloading=" + firstFailure.worldUnloading()
                    + " canceled=" + firstFailure.cancellationRequested());
            }
            plugin.getLogger().info("[Queue][ValousHandoff] event=complete world=" + world.getName()
                + " token=" + ledger.generationToken()
                + " status=" + (passed ? "pass" : "fail:" + reason)
                + " handoff_ms=" + result.handoffMs()
                + " barrier_needed=" + result.visibilityBarrierNeeded()
                + " held=" + result.peakHeldCount()
                + " released=" + result.releasedCount());
            completion.accept(result);
        }

        private void writeReport(Result result) {
            try {
                Files.createDirectories(reportPath.getParent());
                JsonObject root = result.toJson();
                root.addProperty("world_id", world.getName());
                root.addProperty("world_uuid", world.getUID().toString());
                root.addProperty("generation_token", ledger.generationToken().toString());
                root.addProperty("queue_profile", ledger.queueProfileKey());
                root.addProperty("pregeneration_mode", ledger.pregenerationMode());
                root.addProperty("generation_version", ledger.generationVersion());
                root.addProperty("datapack_version", ledger.datapackVersion());
                root.addProperty("completion_timestamp", ledger.completedAtMs());
                Files.writeString(reportPath, GSON.toJson(root), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Queue][ValousHandoff] event=report_write_failed world="
                    + world.getName() + " reason=" + ex.getClass().getSimpleName());
            }
        }
    }

    private record PendingAcquisition(CompletableFuture<Chunk> future) {
    }
}
