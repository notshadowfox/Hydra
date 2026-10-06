package hgloot.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Read-only, ledger-backed Valous review export.
 *
 * <p>World reads happen incrementally on the primary thread. Rendering, report
 * generation, and compression only use captured primitive arrays off-thread.</p>
 */
final class ValousWorldReviewExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final long TICK_BUDGET_NANOS = 6_000_000L;
    private static final long HARD_TICK_LIMIT_NANOS = 50_000_000L;
    private static final int MAX_CONCURRENT_ACQUISITIONS = 4;
    private static final int[] REVIEW_LEVELS = {
        120, 100, 80, 60, 40, 30, 20, 0, -20, -40,
        -60, -80, -100, -120, -140, -160, -180, -195, -200
    };

    private static final Map<String, Color> BIOME_COLORS = Map.ofEntries(
        Map.entry(ValousPrototypeValidator.MUSHROOM_THICKET, new Color(126, 76, 151)),
        Map.entry(ValousPrototypeValidator.MUSHROOM_THICKET_COAST, new Color(163, 111, 180)),
        Map.entry(ValousPrototypeValidator.BOILGROUNDS, new Color(154, 70, 42)),
        Map.entry(ValousPrototypeValidator.LUSH_JUNGLE, new Color(48, 145, 83)),
        Map.entry(ValousPrototypeValidator.MOLTEN_CAVERNS, new Color(194, 72, 35)),
        Map.entry(ValousPrototypeValidator.FROZEN_CAVES, new Color(91, 183, 220)),
        Map.entry(ValousPrototypeValidator.SCULK_ABYSS, new Color(21, 63, 72)),
        Map.entry("minecraft:deep_ocean", new Color(25, 66, 134))
    );

    private ValousWorldReviewExporter() {
    }

    enum Mode {
        REVIEW("review"),
        FULL("full"),
        ROUTE("route");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        static Mode parse(String value) {
            if (value == null || value.isBlank()) {
                return REVIEW;
            }
            for (Mode mode : values()) {
                if (mode.key.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return null;
        }
    }

    interface LifecycleGuard {
        boolean isCurrent();

        boolean isCanceled();

        boolean isWorldUnloading();
    }

    record Settings(
        Mode mode,
        int playableDiameter,
        int safeEditDiameter,
        Path outputRoot,
        Path validationReport,
        Path routeReport,
        ValousRoutePostProcessor.Result route,
        ValousPrototypeValidator.Result validation,
        LifecycleGuard guard
    ) {
    }

    record Result(
        boolean passed,
        String failureReason,
        String worldName,
        Mode mode,
        Path reportDirectory,
        Path indexPath,
        Path zipPath,
        long durationMs,
        long maxTickNanos,
        long averageTickNanos,
        long p95TickNanos,
        int chunksProcessed,
        long samplesInspected,
        int filesCreated,
        long outputBytes
    ) {
        String summary() {
            return passed ? "pass" : "fail:" + failureReason;
        }
    }

    static Job start(
        JavaPlugin plugin,
        World world,
        ValousPregenerationLedger ledger,
        Settings settings,
        Consumer<Result> completion
    ) {
        Job job = new Job(plugin, world, ledger, settings, completion);
        job.start();
        return job;
    }

    static final class Job {
        private final JavaPlugin plugin;
        private final World world;
        private final ValousPregenerationLedger ledger;
        private final Settings settings;
        private final Consumer<Result> completion;
        private final long startedAtMs = System.currentTimeMillis();
        private final UUIDSnapshot identity;
        private final Deque<Long> remaining = new ArrayDeque<>();
        private final Map<Long, CompletableFuture<Chunk>> pending = new LinkedHashMap<>();
        private final Set<Long> held = new LinkedHashSet<>();
        private final List<Long> tickDurations = new ArrayList<>();
        private final int sampleStep;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int width;
        private final int height;
        private final int[] levels;
        private final short[] surfaceHeight;
        private final short[] surfaceWaterDepth;
        private final int[] surfaceBiome;
        private final int[] surfaceMaterial;
        private final byte[][] occupancy;
        private final int[][] sliceBiomes;
        private final byte[][] crossNorthSouth;
        private final byte[][] crossEastWest;
        private final Map<String, Long> biomeSamples = new LinkedHashMap<>();
        private final Map<Integer, long[]> yStatistics = new LinkedHashMap<>();
        private BukkitTask task;
        private boolean canceled;
        private boolean finished;
        private int processedChunks;
        private long samplesInspected;
        private long maxTickNanos;

        private Job(
            JavaPlugin plugin,
            World world,
            ValousPregenerationLedger ledger,
            Settings settings,
            Consumer<Result> completion
        ) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.world = Objects.requireNonNull(world, "world");
            this.ledger = Objects.requireNonNull(ledger, "ledger");
            this.settings = Objects.requireNonNull(settings, "settings");
            this.completion = Objects.requireNonNull(completion, "completion");
            this.identity = new UUIDSnapshot(world.getUID().toString(), ledger.generationToken().toString());
            int pregeneratedWidth = ledger.maxBlockX() - ledger.minBlockX() + 1;
            this.sampleStep = pregeneratedWidth <= 768 ? 4 : pregeneratedWidth <= 1_280 ? 6 : 8;
            this.minX = alignUp(ledger.minBlockX(), sampleStep);
            this.maxX = alignDown(ledger.maxBlockX(), sampleStep);
            this.minZ = alignUp(ledger.minBlockZ(), sampleStep);
            this.maxZ = alignDown(ledger.maxBlockZ(), sampleStep);
            this.width = ((maxX - minX) / sampleStep) + 1;
            this.height = ((maxZ - minZ) / sampleStep) + 1;
            this.levels = reviewLevels(settings);
            int cells = width * height;
            this.surfaceHeight = new short[cells];
            this.surfaceWaterDepth = new short[cells];
            this.surfaceBiome = new int[cells];
            this.surfaceMaterial = new int[cells];
            this.occupancy = new byte[levels.length][cells];
            this.sliceBiomes = new int[levels.length][cells];
            int verticalCells = ((120 - Math.max(world.getMinHeight(), -208)) / 2) + 1;
            this.crossNorthSouth = new byte[verticalCells][height];
            this.crossEastWest = new byte[verticalCells][width];
            ledger.completedChunkKeys().stream()
                .sorted(Comparator.comparingLong(Long::longValue))
                .forEach(remaining::addLast);
        }

        private static int[] reviewLevels(Settings settings) {
            Set<Integer> values = new LinkedHashSet<>();
            Arrays.stream(REVIEW_LEVELS).forEach(values::add);
            ValousRoutePostProcessor.Result route = settings.route();
            if (route != null) {
                for (ValousRoutePostProcessor.Point point : new ValousRoutePostProcessor.Point[] {
                    route.lushSurface(),
                    route.lushTarget(),
                    route.moltenTarget(),
                    route.moltenSurface(),
                    route.sculkTarget()
                }) {
                    if (point != null) {
                        values.add(point.y());
                    }
                }
                addPathMedian(values, route.connectorPath());
                addPathMedian(values, route.sculkConnectorPath());
            }
            return values.stream()
                .filter(y -> y <= 120 && y >= -208)
                .sorted(Comparator.reverseOrder())
                .mapToInt(Integer::intValue)
                .toArray();
        }

        private static void addPathMedian(Set<Integer> values, List<ValousRoutePostProcessor.Point> path) {
            if (path != null && !path.isEmpty()) {
                values.add(path.get(path.size() / 2).y());
            }
        }

        private void start() {
            String failure = initialSafetyFailure();
            if (!failure.isEmpty()) {
                finishFailure(failure);
                return;
            }
            plugin.getLogger().info("[Queue][ValousReview] event=started world=" + world.getName()
                + " token=" + ledger.generationToken()
                + " mode=" + settings.mode().key()
                + " chunks=" + remaining.size()
                + " sample_step=" + sampleStep
                + " levels=" + levels.length
                + " generate=false");
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    tick();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        void cancel() {
            canceled = true;
            if (!finished) {
                finishFailure("canceled");
            }
        }

        String status() {
            int total = ledger.expectedChunkCount();
            double percent = total == 0 ? 0.0D : processedChunks * 100.0D / total;
            return world.getName() + " " + settings.mode().key()
                + " " + processedChunks + "/" + total
                + " (" + String.format(Locale.ROOT, "%.1f", percent) + "%)";
        }

        String worldName() {
            return world.getName();
        }

        boolean isFinished() {
            return finished;
        }

        private String initialSafetyFailure() {
            if (!ledger.isComplete()) {
                return "pregeneration_ledger_incomplete";
            }
            if (!world.getUID().equals(ledger.worldId())) {
                return "world_uuid_mismatch";
            }
            if (!settings.guard().isCurrent() || settings.guard().isCanceled()) {
                return "stale_generation_token";
            }
            if (settings.guard().isWorldUnloading()) {
                return "world_unloading";
            }
            for (long key : ledger.completedChunkKeys()) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                if (!ledger.insideExpectedBounds(chunkX, chunkZ)) {
                    return "ledger_chunk_outside_expected_bounds";
                }
            }
            return "";
        }

        private void tick() {
            if (finished) {
                return;
            }
            if (canceled || !plugin.isEnabled() || !settings.guard().isCurrent()
                || settings.guard().isCanceled() || settings.guard().isWorldUnloading()) {
                finishFailure(settings.guard().isWorldUnloading()
                    ? "world_unloading"
                    : "canceled_or_stale");
                return;
            }
            long tickStarted = System.nanoTime();
            resolvePending(tickStarted);
            while (!remaining.isEmpty() && pending.size() < MAX_CONCURRENT_ACQUISITIONS
                && System.nanoTime() - tickStarted < TICK_BUDGET_NANOS) {
                long key = remaining.removeFirst();
                if (!ledger.contains(key)) {
                    finishFailure("chunk_absent_from_completed_ledger");
                    return;
                }
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                try {
                    CompletableFuture<Chunk> future = world.isChunkLoaded(chunkX, chunkZ)
                        ? CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ))
                        : world.getChunkAtAsync(chunkX, chunkZ, false, true);
                    pending.put(key, future);
                } catch (RuntimeException ex) {
                    finishFailure("no_generation_acquisition_exception:"
                        + ex.getClass().getSimpleName());
                    return;
                }
            }
            long elapsed = System.nanoTime() - tickStarted;
            tickDurations.add(elapsed);
            maxTickNanos = Math.max(maxTickNanos, elapsed);
            if (elapsed > HARD_TICK_LIMIT_NANOS) {
                finishFailure("hard_tick_limit_exceeded");
                return;
            }
            if (remaining.isEmpty() && pending.isEmpty() && held.isEmpty()) {
                finishCapture();
            }
        }

        private void resolvePending(long tickStarted) {
            List<Long> resolved = new ArrayList<>();
            for (Map.Entry<Long, CompletableFuture<Chunk>> entry : pending.entrySet()) {
                if (System.nanoTime() - tickStarted >= TICK_BUDGET_NANOS) {
                    break;
                }
                if (!entry.getValue().isDone()) {
                    continue;
                }
                resolved.add(entry.getKey());
                try {
                    Chunk chunk = entry.getValue().join();
                    if (chunk == null) {
                        finishFailure("no_generation_acquisition_returned_null");
                        return;
                    }
                    long key = ValousPregenerationLedger.chunkKey(chunk.getX(), chunk.getZ());
                    if (!ledger.contains(key)) {
                        finishFailure("acquired_chunk_outside_ledger");
                        return;
                    }
                    world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
                    held.add(key);
                    sampleChunk(chunk);
                    world.removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
                    held.remove(key);
                    processedChunks++;
                } catch (RuntimeException ex) {
                    finishFailure("chunk_sampling_exception:" + ex.getClass().getSimpleName());
                    return;
                }
            }
            resolved.forEach(pending::remove);
        }

        private void sampleChunk(Chunk chunk) {
            int chunkMinX = chunk.getX() << 4;
            int chunkMinZ = chunk.getZ() << 4;
            int startX = alignUp(Math.max(minX, chunkMinX), sampleStep);
            int endX = Math.min(maxX, chunkMinX + 15);
            int startZ = alignUp(Math.max(minZ, chunkMinZ), sampleStep);
            int endZ = Math.min(maxZ, chunkMinZ + 15);
            if (startX > endX || startZ > endZ) {
                return;
            }
            for (int z = startZ; z <= endZ; z += sampleStep) {
                for (int x = startX; x <= endX; x += sampleStep) {
                    int index = ((z - minZ) / sampleStep) * width + ((x - minX) / sampleStep);
                    int surfaceY = world.getHighestBlockYAt(
                        x,
                        z,
                        HeightMap.OCEAN_FLOOR
                    );
                    int fluidSurfaceY = world.getHighestBlockYAt(
                        x,
                        z,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES
                    );
                    surfaceHeight[index] = (short) surfaceY;
                    surfaceWaterDepth[index] = (short) (
                        world.getBlockAt(x, fluidSurfaceY, z).getType() == Material.WATER
                            ? Math.max(0, fluidSurfaceY - surfaceY)
                            : 0
                    );
                    String surfaceKey = biomeKey(world, x, surfaceY, z);
                    surfaceBiome[index] = color(BIOME_COLORS.getOrDefault(
                        surfaceKey,
                        new Color(88, 88, 88)
                    ));
                    surfaceMaterial[index] = color(materialColor(
                        world.getBlockAt(x, surfaceY, z).getType()
                    ));
                    for (int levelIndex = 0; levelIndex < levels.length; levelIndex++) {
                        int y = levels[levelIndex];
                        Block block = world.getBlockAt(x, y, z);
                        Block above = world.getBlockAt(x, y + 1, z);
                        byte state = occupancy(block, above);
                        occupancy[levelIndex][index] = state;
                        String key = biomeKey(world, x, y, z);
                        sliceBiomes[levelIndex][index] = color(BIOME_COLORS.getOrDefault(
                            key,
                            new Color(72, 72, 72)
                        ));
                        biomeSamples.merge(key, 1L, Long::sum);
                        yStatistics.computeIfAbsent(y, ignored -> new long[5])[state]++;
                        samplesInspected++;
                    }
                }
            }
            sampleCrossSections(chunk);
        }

        private void sampleCrossSections(Chunk chunk) {
            int minReviewY = Math.max(world.getMinHeight(), -208);
            if ((chunk.getZ() << 4) <= 0 && ((chunk.getZ() << 4) + 15) >= 0) {
                int startX = alignUp(Math.max(minX, chunk.getX() << 4), sampleStep);
                int endX = Math.min(maxX, (chunk.getX() << 4) + 15);
                for (int x = startX; x <= endX; x += sampleStep) {
                    int column = (x - minX) / sampleStep;
                    for (int y = minReviewY, row = 0; y <= 120; y += 2, row++) {
                        crossEastWest[row][column] = occupancy(
                            world.getBlockAt(x, y, 0),
                            world.getBlockAt(x, y + 1, 0)
                        );
                    }
                }
            }
            if ((chunk.getX() << 4) <= 0 && ((chunk.getX() << 4) + 15) >= 0) {
                int startZ = alignUp(Math.max(minZ, chunk.getZ() << 4), sampleStep);
                int endZ = Math.min(maxZ, (chunk.getZ() << 4) + 15);
                for (int z = startZ; z <= endZ; z += sampleStep) {
                    int column = (z - minZ) / sampleStep;
                    for (int y = minReviewY, row = 0; y <= 120; y += 2, row++) {
                        crossNorthSouth[row][column] = occupancy(
                            world.getBlockAt(0, y, z),
                            world.getBlockAt(0, y + 1, z)
                        );
                    }
                }
            }
        }

        private void finishCapture() {
            if (finished) {
                return;
            }
            if (processedChunks != ledger.expectedChunkCount()) {
                finishFailure("processed_chunk_count_mismatch");
                return;
            }
            finished = true;
            if (task != null) {
                task.cancel();
            }
            Captured captured = new Captured(
                world.getName(),
                world.getSeed(),
                identity,
                settings.mode(),
                sampleStep,
                minX,
                maxX,
                minZ,
                maxZ,
                width,
                height,
                levels,
                surfaceHeight,
                surfaceWaterDepth,
                surfaceBiome,
                surfaceMaterial,
                occupancy,
                sliceBiomes,
                crossNorthSouth,
                crossEastWest,
                Map.copyOf(biomeSamples),
                copyStatistics(yStatistics),
                settings.playableDiameter(),
                settings.safeEditDiameter(),
                ledger,
                settings.route(),
                settings.validation(),
                settings.validationReport(),
                settings.routeReport(),
                startedAtMs,
                maxTickNanos,
                List.copyOf(tickDurations),
                processedChunks,
                samplesInspected
            );
            CompletableFuture.supplyAsync(() -> render(captured, settings.outputRoot()))
                .whenComplete((result, error) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            plugin.getLogger().log(
                                java.util.logging.Level.SEVERE,
                                "[Queue][ValousReview] event=render_failed world="
                                    + world.getName(),
                                error
                            );
                            completion.accept(failureResult("render_failed:"
                                + error.getClass().getSimpleName()));
                            return;
                        }
                        plugin.getLogger().info("[Queue][ValousReview] event=complete world="
                            + world.getName() + " mode=" + settings.mode().key()
                            + " duration_ms=" + result.durationMs()
                            + " max_tick_ms=" + String.format(
                                Locale.ROOT,
                                "%.3f",
                                result.maxTickNanos() / 1_000_000.0D
                            )
                            + " chunks=" + result.chunksProcessed()
                            + " files=" + result.filesCreated()
                            + " output_bytes=" + result.outputBytes()
                            + " generate=false modified=false");
                        completion.accept(result);
                    })
                );
        }

        private void finishFailure(String reason) {
            if (finished) {
                return;
            }
            finished = true;
            if (task != null) {
                task.cancel();
            }
            releaseHeld();
            plugin.getLogger().warning("[Queue][ValousReview] event=failed world=" + world.getName()
                + " mode=" + settings.mode().key() + " reason=" + reason
                + " chunks=" + processedChunks + " generate=false modified=false");
            completion.accept(failureResult(reason));
        }

        private Result failureResult(String reason) {
            return new Result(
                false,
                reason,
                world.getName(),
                settings.mode(),
                null,
                null,
                null,
                System.currentTimeMillis() - startedAtMs,
                maxTickNanos,
                average(tickDurations),
                percentile(tickDurations, 0.95D),
                processedChunks,
                samplesInspected,
                0,
                0L
            );
        }

        private void releaseHeld() {
            for (long key : List.copyOf(held)) {
                try {
                    world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
                } catch (RuntimeException ignored) {
                    // Cleanup is best effort; the failure is already being reported.
                }
            }
            held.clear();
        }
    }

    private record UUIDSnapshot(String worldId, String generationToken) {
    }

    private record Captured(
        String worldName,
        long seed,
        UUIDSnapshot identity,
        Mode mode,
        int sampleStep,
        int minX,
        int maxX,
        int minZ,
        int maxZ,
        int width,
        int height,
        int[] levels,
        short[] surfaceHeight,
        short[] surfaceWaterDepth,
        int[] surfaceBiome,
        int[] surfaceMaterial,
        byte[][] occupancy,
        int[][] sliceBiomes,
        byte[][] crossNorthSouth,
        byte[][] crossEastWest,
        Map<String, Long> biomeSamples,
        Map<Integer, long[]> yStatistics,
        int playableDiameter,
        int safeEditDiameter,
        ValousPregenerationLedger ledger,
        ValousRoutePostProcessor.Result route,
        ValousPrototypeValidator.Result validation,
        Path validationReport,
        Path routeReport,
        long startedAtMs,
        long maxTickNanos,
        List<Long> tickDurations,
        int chunksProcessed,
        long samplesInspected
    ) {
    }

    private static Result render(Captured data, Path outputRoot) {
        try {
            String timestamp = TIMESTAMP.format(Instant.now());
            Path directory = outputRoot.resolve(data.worldName()).resolve(timestamp);
            Path maps = directory.resolve("maps");
            Path slices = maps.resolve("slices");
            Path biomes = maps.resolve("biomes");
            Path caveAir = maps.resolve("cave_air");
            Path fluids = maps.resolve("fluids");
            Path routes = maps.resolve("routes");
            Path issues = maps.resolve("issues");
            Path surface = maps.resolve("surface");
            Path structures = maps.resolve("structures");
            Path crossNs = directory.resolve("cross_sections").resolve("north_south");
            Path crossEw = directory.resolve("cross_sections").resolve("east_west");
            Path routeCrops = directory.resolve("route_crops");
            Path dataDir = directory.resolve("data");
            for (Path path : List.of(
                slices, biomes, caveAir, fluids, routes, issues, surface,
                structures, crossNs, crossEw, dataDir,
                routeCrops.resolve("lush_entrance"),
                routeCrops.resolve("lush_arrival"),
                routeCrops.resolve("lush_molten_connector"),
                routeCrops.resolve("molten_arrival"),
                routeCrops.resolve("molten_return"),
                routeCrops.resolve("sculk_chamber"),
                routeCrops.resolve("sculk_connector")
            )) {
                Files.createDirectories(path);
            }
            Files.writeString(
                routeCrops.resolve("README.txt"),
                "Full-resolution route crops are scheduled for exporter Phase 2.\n"
                    + "Phase 1 route review is provided through the Y-slice route overlays.\n",
                StandardCharsets.UTF_8
            );
            Files.writeString(
                structures.resolve("README.txt"),
                "Phase 1 exports validator tree-support markers on overview and slice maps.\n"
                    + "Detailed structure footprint analysis is scheduled for Phase 2.\n",
                StandardCharsets.UTF_8
            );

            JsonObject issueJson = readJson(data.validationReport());
            List<IssuePoint> issuePoints = issuePoints(issueJson);
            writeSurfaceMaps(data, surface, issuePoints);
            writeSliceMaps(data, slices, biomes, caveAir, fluids, routes, issuePoints);
            writeEntranceCenterline(data, routes.resolve("entrance-centerline.png"));
            writeCrossSection(
                data.crossNorthSouth(),
                crossNs.resolve("center-z-axis.png"),
                "North-South center cross-section",
                data
            );
            writeCrossSection(
                data.crossEastWest(),
                crossEw.resolve("center-x-axis.png"),
                "East-West center cross-section",
                data
            );
            copyIfExists(data.validationReport(), directory.resolve("validation.json"));
            copyIfExists(data.routeReport(), directory.resolve("route-postprocess.json"));
            writeCsv(data, dataDir, issuePoints);
            JsonObject manifest = manifest(data, timestamp, issuePoints);
            JsonObject metrics = metrics(data);
            JsonObject issuesJson = issuesJson(issuePoints);
            JsonObject legends = legends();
            writeJson(directory.resolve("manifest.json"), manifest);
            writeJson(directory.resolve("metrics.json"), metrics);
            writeJson(directory.resolve("issues.json"), issuesJson);
            writeJson(directory.resolve("legends.json"), legends);
            Files.writeString(
                directory.resolve("review-summary.md"),
                markdownSummary(data, issuePoints),
                StandardCharsets.UTF_8
            );
            Files.writeString(
                directory.resolve("index.html"),
                html(data, issuePoints),
                StandardCharsets.UTF_8
            );
            Files.copy(
                surface.resolve("height.png"),
                directory.resolve("overview.png"),
                StandardCopyOption.REPLACE_EXISTING
            );
            Path zip = outputRoot.resolve("valous-review-" + data.worldName()
                + "-" + timestamp + ".zip");
            zipDirectory(directory, zip);
            int files;
            try (var paths = Files.walk(directory)) {
                files = (int) paths.filter(Files::isRegularFile).count();
            }
            long bytes;
            try (var paths = Files.walk(directory)) {
                bytes = paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    })
                    .sum() + Files.size(zip);
            }
            return new Result(
                true,
                "",
                data.worldName(),
                data.mode(),
                directory,
                directory.resolve("index.html"),
                zip,
                System.currentTimeMillis() - data.startedAtMs(),
                data.maxTickNanos(),
                average(data.tickDurations()),
                percentile(data.tickDurations(), 0.95D),
                data.chunksProcessed(),
                data.samplesInspected(),
                files,
                bytes
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed rendering Valous review package.", ex);
        }
    }

    private static void writeSurfaceMaps(
        Captured data,
        Path surface,
        List<IssuePoint> issues
    ) throws IOException {
        int[] heights = new int[data.surfaceHeight().length];
        int[] slopes = new int[data.surfaceHeight().length];
        int[] waterDepth = new int[data.surfaceHeight().length];
        for (int index = 0; index < heights.length; index++) {
            int y = data.surfaceHeight()[index];
            heights[index] = color(heightColor(y));
            int x = index % data.width();
            int z = index / data.width();
            int maxDelta = 0;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= data.width() || nz >= data.height()) {
                        continue;
                    }
                    maxDelta = Math.max(
                        maxDelta,
                        Math.abs(y - data.surfaceHeight()[nz * data.width() + nx])
                    );
                }
            }
            slopes[index] = color(slopeColor(maxDelta));
            int depth = data.surfaceWaterDepth()[index];
            waterDepth[index] = color(depth > 0
                ? new Color(25, 70, Math.min(220, 100 + depth * 5))
                : new Color(22, 25, 30));
        }
        writeMap(data, heights, surface.resolve("height.png"), "Surface height", issues, null);
        writeMap(data, data.surfaceBiome(), surface.resolve("biomes.png"), "Surface biomes", issues, null);
        writeMap(data, data.surfaceMaterial(), surface.resolve("materials.png"), "Surface materials", issues, null);
        writeMap(data, slopes, surface.resolve("slope.png"), "Surface slope", issues, null);
        writeMap(data, waterDepth, surface.resolve("water-depth.png"), "Water depth", issues, null);
    }

    private static void writeSliceMaps(
        Captured data,
        Path slices,
        Path biomes,
        Path caveAir,
        Path fluids,
        Path routes,
        List<IssuePoint> issues
    ) throws IOException {
        for (int index = 0; index < data.levels().length; index++) {
            int y = data.levels()[index];
            int[] occupancyColors = new int[data.occupancy()[index].length];
            int[] caveColors = new int[occupancyColors.length];
            int[] fluidColors = new int[occupancyColors.length];
            for (int cell = 0; cell < occupancyColors.length; cell++) {
                byte state = data.occupancy()[index][cell];
                occupancyColors[cell] = color(occupancyColor(state));
                caveColors[cell] = color(caveColor(state));
                fluidColors[cell] = color(fluidColor(state));
            }
            String name = yName(y);
            writeMap(data, occupancyColors, slices.resolve(name + ".png"),
                "Terrain occupancy Y=" + y, issues, y);
            writeMap(data, data.sliceBiomes()[index], biomes.resolve(name + ".png"),
                "Biome Y=" + y, issues, y);
            writeMap(data, caveColors, caveAir.resolve(name + ".png"),
                "Cave air Y=" + y, issues, y);
            writeMap(data, fluidColors, fluids.resolve(name + ".png"),
                "Fluids Y=" + y, issues, y);
            writeMap(data, caveColors, routes.resolve(name + ".png"),
                "Routes Y=" + y, issues, y);
        }
    }

    private static void writeMap(
        Captured data,
        int[] pixels,
        Path target,
        String title,
        List<IssuePoint> issues,
        Integer routeY
    ) throws IOException {
        int scale = Math.max(2, Math.min(5, 1_024 / Math.max(data.width(), data.height())));
        int header = 52;
        BufferedImage image = new BufferedImage(
            data.width() * scale,
            data.height() * scale + header,
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int z = 0; z < data.height(); z++) {
            for (int x = 0; x < data.width(); x++) {
                graphics.setColor(new Color(pixels[z * data.width() + x], true));
                graphics.fillRect(x * scale, z * scale + header, scale, scale);
            }
        }
        drawOverlays(graphics, data, scale, header, routeY, issues);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        graphics.drawString(title + " | " + data.worldName() + " | seed " + data.seed(), 10, 20);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        graphics.drawString("North up | +X right | +Z down | " + data.sampleStep()
            + " blocks/pixel | generated " + Instant.now(), 10, 40);
        graphics.dispose();
        ImageIO.write(image, "png", target.toFile());
    }

    private static void drawOverlays(
        Graphics2D graphics,
        Captured data,
        int scale,
        int header,
        Integer routeY,
        List<IssuePoint> issues
    ) {
        graphics.setStroke(new BasicStroke(1.4F));
        graphics.setColor(new Color(55, 210, 210));
        graphics.drawRect(0, header, data.width() * scale - 1, data.height() * scale - 1);
        drawBoundary(graphics, data, data.playableDiameter(), scale, header, new Color(255, 225, 65));
        drawBoundary(graphics, data, data.safeEditDiameter(), scale, header, new Color(255, 145, 38));
        graphics.setColor(new Color(40, 220, 255, 170));
        graphics.drawOval(
            worldToPixelX(data, -128, scale),
            worldToPixelZ(data, -128, scale, header),
            (256 / data.sampleStep()) * scale,
            (256 / data.sampleStep()) * scale
        );
        int centerX = worldToPixelX(data, 0, scale);
        int centerZ = worldToPixelZ(data, 0, scale, header);
        graphics.setColor(Color.WHITE);
        graphics.drawLine(centerX - 5, centerZ, centerX + 5, centerZ);
        graphics.drawLine(centerX, centerZ - 5, centerX, centerZ + 5);
        for (int grid = -2_048; grid <= 2_048; grid += 256) {
            if (grid >= data.minX() && grid <= data.maxX()) {
                int x = worldToPixelX(data, grid, scale);
                graphics.setColor(new Color(255, 255, 255, 55));
                graphics.drawLine(x, header, x, header + data.height() * scale);
            }
            if (grid >= data.minZ() && grid <= data.maxZ()) {
                int z = worldToPixelZ(data, grid, scale, header);
                graphics.setColor(new Color(255, 255, 255, 55));
                graphics.drawLine(0, z, data.width() * scale, z);
            }
        }
        if (data.route() != null) {
            drawRoute(graphics, data, data.route().lushEntrancePath(), scale, header, routeY, Color.GREEN);
            drawRoute(graphics, data, data.route().connectorPath(), scale, header, routeY, Color.YELLOW);
            drawRoute(graphics, data, data.route().moltenReturnPath(), scale, header, routeY, Color.ORANGE);
            drawRoute(graphics, data, data.route().sculkConnectorPath(), scale, header, routeY, Color.CYAN);
        }
        graphics.setColor(Color.MAGENTA);
        for (IssuePoint issue : issues) {
            int x = worldToPixelX(data, issue.x(), scale);
            int z = worldToPixelZ(data, issue.z(), scale, header);
            graphics.drawOval(x - 4, z - 4, 8, 8);
            graphics.drawLine(x - 5, z - 5, x + 5, z + 5);
            graphics.drawLine(x - 5, z + 5, x + 5, z - 5);
        }
    }

    private static void drawBoundary(
        Graphics2D graphics,
        Captured data,
        int diameter,
        int scale,
        int header,
        Color color
    ) {
        int half = diameter / 2;
        int x = worldToPixelX(data, -half, scale);
        int z = worldToPixelZ(data, -half, scale, header);
        int size = (diameter / data.sampleStep()) * scale;
        graphics.setColor(color);
        graphics.drawRect(x, z, size, size);
    }

    private static void drawRoute(
        Graphics2D graphics,
        Captured data,
        List<ValousRoutePostProcessor.Point> path,
        int scale,
        int header,
        Integer routeY,
        Color color
    ) {
        if (path == null || path.size() < 2) {
            return;
        }
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(2.3F));
        for (int index = 1; index < path.size(); index++) {
            ValousRoutePostProcessor.Point left = path.get(index - 1);
            ValousRoutePostProcessor.Point right = path.get(index);
            if (routeY != null
                && Math.abs(((left.y() + right.y()) / 2) - routeY) > 4) {
                continue;
            }
            graphics.drawLine(
                worldToPixelX(data, left.x(), scale),
                worldToPixelZ(data, left.z(), scale, header),
                worldToPixelX(data, right.x(), scale),
                worldToPixelZ(data, right.z(), scale, header)
            );
        }
    }

    private static void writeCrossSection(
        byte[][] values,
        Path target,
        String title,
        Captured data
    ) throws IOException {
        int scale = 3;
        int header = 42;
        int width = values[0].length * scale;
        int height = values.length * scale + header;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, width, height);
        for (int row = 0; row < values.length; row++) {
            int imageY = header + (values.length - 1 - row) * scale;
            for (int column = 0; column < values[row].length; column++) {
                graphics.setColor(occupancyColor(values[row][column]));
                graphics.fillRect(column * scale, imageY, scale, scale);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        graphics.drawString(title + " | " + data.worldName(), 8, 18);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        graphics.drawString("Y -208 to 120 | " + data.sampleStep() + " horizontal blocks/pixel", 8, 34);
        graphics.dispose();
        ImageIO.write(image, "png", target.toFile());
    }

    private static void writeEntranceCenterline(Captured data, Path target) throws IOException {
        List<ValousRoutePostProcessor.Point> path = data.route() == null
            ? List.of()
            : data.route().lushEntrancePath();
        int width = 720;
        int height = 460;
        int left = 72;
        int right = 24;
        int top = 72;
        int bottom = 48;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(17, 19, 24));
        graphics.fillRect(0, 0, width, height);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        graphics.setColor(Color.WHITE);
        graphics.drawString("Lush entrance centerline | " + data.worldName(), 12, 22);

        if (path == null || path.size() < 2) {
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            graphics.setColor(new Color(255, 156, 66));
            graphics.drawString("No Lush entrance path is available.", 12, 48);
            graphics.dispose();
            ImageIO.write(image, "png", target.toFile());
            return;
        }

        ValousRoutePostProcessor.Point origin = path.getFirst();
        ValousMorphologyGeometry.EntranceMetrics metrics =
            ValousMorphologyGeometry.entranceMetrics(path);
        int minY = path.stream().mapToInt(ValousRoutePostProcessor.Point::y).min().orElse(origin.y());
        int maxY = path.stream().mapToInt(ValousRoutePostProcessor.Point::y).max().orElse(origin.y());
        double maxRange = Math.max(10.0D, metrics.maximumHorizontalDrift() + 2.0D);
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        graphics.setColor(new Color(157, 168, 181));
        graphics.drawString(String.format(
            Locale.ROOT,
            "drop=%d max-drift=%.2f avg-drift=%.2f ratio=%.3f result=%s",
            metrics.verticalDrop(),
            metrics.maximumHorizontalDrift(),
            metrics.averageHorizontalDrift(),
            metrics.horizontalToVerticalRatio(),
            metrics.passed() ? "PASS" : "FAIL"
        ), 12, 42);
        graphics.drawString("Horizontal offset from the surface center. Dashed lines mark +/-8 blocks.",
            12, 59);

        graphics.setColor(new Color(52, 59, 70));
        graphics.fillRect(left, top, plotWidth, plotHeight);
        graphics.setStroke(new BasicStroke(1.0F));
        graphics.setColor(new Color(95, 105, 119));
        graphics.drawRect(left, top, plotWidth, plotHeight);
        int zeroX = left + plotWidth / 2;
        graphics.setColor(new Color(205, 211, 220));
        graphics.drawLine(zeroX, top, zeroX, top + plotHeight);
        float[] dash = {5.0F, 5.0F};
        graphics.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10.0F, dash, 0.0F));
        graphics.setColor(new Color(255, 156, 66));
        for (int limit : new int[] {-8, 8}) {
            int limitX = left + (int) Math.round(((limit + maxRange) / (maxRange * 2.0D)) * plotWidth);
            graphics.drawLine(limitX, top, limitX, top + plotHeight);
        }

        drawCenterlineSeries(graphics, path, origin.x(), true, minY, maxY,
            maxRange, left, top, plotWidth, plotHeight, new Color(83, 221, 116));
        drawCenterlineSeries(graphics, path, origin.z(), false, minY, maxY,
            maxRange, left, top, plotWidth, plotHeight, new Color(69, 216, 232));
        graphics.setStroke(new BasicStroke(1.0F));
        graphics.setColor(new Color(83, 221, 116));
        graphics.drawString("X drift", left, height - 18);
        graphics.setColor(new Color(69, 216, 232));
        graphics.drawString("Z drift", left + 80, height - 18);
        graphics.setColor(new Color(205, 211, 220));
        graphics.drawString("Y " + maxY, 12, top + 4);
        graphics.drawString("Y " + minY, 12, top + plotHeight);
        graphics.dispose();
        ImageIO.write(image, "png", target.toFile());
    }

    private static void drawCenterlineSeries(
        Graphics2D graphics,
        List<ValousRoutePostProcessor.Point> path,
        int origin,
        boolean xAxis,
        int minY,
        int maxY,
        double maxRange,
        int left,
        int top,
        int plotWidth,
        int plotHeight,
        Color color
    ) {
        graphics.setStroke(new BasicStroke(2.2F));
        graphics.setColor(color);
        int previousX = 0;
        int previousY = 0;
        boolean first = true;
        for (ValousRoutePostProcessor.Point point : path) {
            double drift = (xAxis ? point.x() : point.z()) - origin;
            int imageX = left + (int) Math.round(((drift + maxRange) / (maxRange * 2.0D)) * plotWidth);
            int imageY = top + (int) Math.round(((maxY - point.y())
                / (double) Math.max(1, maxY - minY)) * plotHeight);
            if (!first) {
                graphics.drawLine(previousX, previousY, imageX, imageY);
            }
            first = false;
            previousX = imageX;
            previousY = imageY;
        }
    }

    private static void writeCsv(
        Captured data,
        Path directory,
        List<IssuePoint> issues
    ) throws IOException {
        StringBuilder biomes = new StringBuilder("biome,sample_count,percentage\n");
        long total = data.biomeSamples().values().stream().mapToLong(Long::longValue).sum();
        data.biomeSamples().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> biomes.append(entry.getKey()).append(',')
                .append(entry.getValue()).append(',')
                .append(total == 0 ? 0.0D : entry.getValue() * 100.0D / total)
                .append('\n'));
        Files.writeString(directory.resolve("biome_coverage.csv"), biomes, StandardCharsets.UTF_8);

        StringBuilder yLevels = new StringBuilder("y,solid,air,water,lava,passable\n");
        data.yStatistics().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
            .forEach(entry -> {
                long[] values = entry.getValue();
                yLevels.append(entry.getKey());
                for (long value : values) {
                    yLevels.append(',').append(value);
                }
                yLevels.append('\n');
            });
        Files.writeString(directory.resolve("y_level_statistics.csv"), yLevels, StandardCharsets.UTF_8);

        StringBuilder issueCsv = new StringBuilder("severity,type,x,y,z,description\n");
        for (IssuePoint issue : issues) {
            issueCsv.append(issue.severity()).append(',').append(issue.type()).append(',')
                .append(issue.x()).append(',').append(issue.y()).append(',').append(issue.z())
                .append(',').append(csv(issue.description())).append('\n');
        }
        Files.writeString(directory.resolve("issue_list.csv"), issueCsv, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("connected_regions.csv"),
            connectedRegionsCsv(data),
            StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("entrance_list.csv"),
            entranceCsv(data.route()), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("structure_list.csv"),
            "structure_id,x,y,z,bounds,support_percentage,water_intersection\n",
            StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("route_clearance.csv"),
            routeCsv(data), StandardCharsets.UTF_8);
    }

    private static String connectedRegionsCsv(Captured data) {
        StringBuilder value = new StringBuilder(
            "region_id,volume,largest_ratio,major_region_count,biomes,surface_connected\n"
        );
        JsonObject validation = readJson(data.validationReport());
        if (!validation.has("dominant_cavern_connectivity")) {
            return value.toString();
        }
        JsonObject connectivity = validation.getAsJsonObject("dominant_cavern_connectivity");
        value.append("dominant,")
            .append(connectivity.get("largest_connected_volume").getAsInt()).append(',')
            .append(connectivity.get("largest_connected_ratio").getAsDouble()).append(',')
            .append(connectivity.get("major_disconnected_regions").getAsInt()).append(',')
            .append(csv(connectivity.getAsJsonArray("dominant_biomes").toString())).append(',')
            .append(connectivity.get("surface_connected").getAsBoolean()).append('\n');
        return value.toString();
    }

    private static String entranceCsv(ValousRoutePostProcessor.Result route) {
        StringBuilder value = new StringBuilder("id,x,y,z,destination\n");
        if (route != null) {
            appendPoint(value, "lush_entrance", route.lushSurface(), ValousPrototypeValidator.LUSH_JUNGLE);
            appendPoint(value, "molten_return", route.moltenSurface(), ValousPrototypeValidator.MOLTEN_CAVERNS);
            appendPoint(value, "sculk_chamber", route.sculkTarget(), ValousPrototypeValidator.SCULK_ABYSS);
        }
        return value.toString();
    }

    private static void appendPoint(
        StringBuilder value,
        String id,
        ValousRoutePostProcessor.Point point,
        String destination
    ) {
        if (point != null) {
            value.append(id).append(',').append(point.x()).append(',').append(point.y())
                .append(',').append(point.z()).append(',').append(destination).append('\n');
        }
    }

    private static String routeCsv(Captured data) {
        StringBuilder value = new StringBuilder(
            "component,start,end,sampled_length,minimum_width,median_width,minimum_height,"
                + "natural_air_ratio,classification,validation\n"
        );
        ValousRoutePostProcessor.Result route = data.route();
        if (route == null) {
            return value.toString();
        }
        appendRoute(value, "lush_entrance", route.lushSurface(), route.lushTarget(),
            pathLength(route.lushEntrancePath()), route.connectionQuality().get("lush_entrance"),
            check(data, "lush_surface_entrance"));
        appendRoute(value, "lush_to_molten", route.lushTarget(), route.moltenTarget(),
            route.connectorSampledLength(), route.connectionQuality().get("lush_to_molten"),
            check(data, "lush_to_molten_connection"));
        appendRoute(value, "molten_return", route.moltenTarget(), route.moltenSurface(),
            pathLength(route.moltenReturnPath()), route.connectionQuality().get("molten_return"),
            check(data, "molten_surface_return"));
        appendRoute(value, "sculk_connection", null, route.sculkTarget(),
            route.sculkConnectorSampledLength(), route.connectionQuality().get("sculk_descent"),
            check(data, "sculk_upper_connection"));
        return value.toString();
    }

    private static void appendRoute(
        StringBuilder value,
        String id,
        ValousRoutePostProcessor.Point start,
        ValousRoutePostProcessor.Point end,
        int length,
        ValousRoutePostProcessor.ConnectionQuality quality,
        boolean passed
    ) {
        value.append(id).append(',').append(point(start)).append(',')
            .append(point(end)).append(',').append(length).append(',');
        if (quality == null) {
            value.append("0,0,0,0.0,unknown,");
        } else {
            value.append(quality.minimumWidth()).append(',')
                .append(quality.medianWidth()).append(',')
                .append(quality.minimumHeight()).append(',')
                .append(quality.naturalAirRatio()).append(',')
                .append(quality.classification()).append(',');
        }
        value
            .append(passed ? "pass" : "fail").append('\n');
    }

    private static JsonObject manifest(
        Captured data,
        String timestamp,
        List<IssuePoint> issues
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("world_name", data.worldName());
        root.addProperty("world_uuid", data.identity().worldId());
        root.addProperty("generation_token", data.identity().generationToken());
        root.addProperty("seed", data.seed());
        root.addProperty("mode", data.mode().key());
        root.addProperty("export_timestamp", timestamp);
        root.addProperty("read_only", true);
        root.addProperty("missing_chunk_generation_allowed", false);
        root.addProperty("sample_step", data.sampleStep());
        root.addProperty("generation_version", data.ledger().generationVersion());
        root.addProperty("datapack_version", data.ledger().datapackVersion());
        root.addProperty("playable_diameter", data.playableDiameter());
        root.addProperty("safe_edit_diameter", data.safeEditDiameter());
        root.addProperty("pregeneration_min_x", data.minX());
        root.addProperty("pregeneration_max_x", data.maxX());
        root.addProperty("pregeneration_min_z", data.minZ());
        root.addProperty("pregeneration_max_z", data.maxZ());
        root.addProperty("chunks_expected", data.ledger().expectedChunkCount());
        root.addProperty("chunks_processed", data.chunksProcessed());
        root.addProperty("issue_count", issues.size());
        JsonArray levels = new JsonArray();
        Arrays.stream(data.levels()).forEach(levels::add);
        root.add("y_levels", levels);
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("important_y_stack", true);
        capabilities.addProperty("every_y_slider", false);
        capabilities.addProperty("advanced_region_graph", false);
        capabilities.addProperty("comparison_ready_schema", true);
        root.add("phase1_capabilities", capabilities);
        return root;
    }

    private static JsonObject metrics(Captured data) {
        JsonObject root = new JsonObject();
        root.addProperty("chunks_processed", data.chunksProcessed());
        root.addProperty("samples_inspected", data.samplesInspected());
        root.addProperty("maximum_tick_ms", data.maxTickNanos() / 1_000_000.0D);
        root.addProperty("average_tick_ms", average(data.tickDurations()) / 1_000_000.0D);
        root.addProperty("p95_tick_ms", percentile(data.tickDurations(), 0.95D) / 1_000_000.0D);
        JsonObject biomes = new JsonObject();
        data.biomeSamples().forEach(biomes::addProperty);
        root.add("biome_sample_counts", biomes);
        return root;
    }

    private static JsonObject issuesJson(List<IssuePoint> issues) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        JsonArray values = new JsonArray();
        for (IssuePoint issue : issues) {
            JsonObject value = new JsonObject();
            value.addProperty("severity", issue.severity());
            value.addProperty("type", issue.type());
            value.addProperty("x", issue.x());
            value.addProperty("y", issue.y());
            value.addProperty("z", issue.z());
            value.addProperty("description", issue.description());
            values.add(value);
        }
        root.add("issues", values);
        return root;
    }

    private static JsonObject legends() {
        JsonObject root = new JsonObject();
        JsonObject biomes = new JsonObject();
        BIOME_COLORS.forEach((key, value) -> biomes.addProperty(key, hex(value)));
        root.add("biomes", biomes);
        JsonObject occupancy = new JsonObject();
        occupancy.addProperty("solid", "#2c3038");
        occupancy.addProperty("air", "#101217");
        occupancy.addProperty("water", "#236acb");
        occupancy.addProperty("lava", "#ef5a24");
        occupancy.addProperty("player_passable", "#61d6c6");
        root.add("terrain_occupancy", occupancy);
        JsonObject overlays = new JsonObject();
        overlays.addProperty("playable_border", "#ffe141");
        overlays.addProperty("safe_edit_border", "#ff9126");
        overlays.addProperty("cornucopia", "#28dcff");
        overlays.addProperty("issue", "#ff00ff");
        root.add("overlays", overlays);
        return root;
    }

    private static String markdownSummary(Captured data, List<IssuePoint> issues) {
        String validator = data.validation() == null
            ? "UNKNOWN"
            : data.validation().passed() ? "PASS" : "FAIL";
        String recommendation = data.validation() != null && data.validation().passed()
            ? issues.isEmpty() ? "Ready for controlled testing" : "Pass with warnings"
            : "Reject world";
        return """
            # Valous World Review

            ## World Identity

            - World: %s
            - Seed: %d
            - Mode: %s
            - Playable size: %d
            - Generation version: %s
            - Datapack version: %s
            - Sampling: %d blocks per pixel

            ## Result

            - Validator: %s
            - Export issues: %d
            - Route postprocessor: %s

            ## Surface Summary

            - Surface height, biome, material, slope, and water-depth maps exported.
            - Cornucopia, playable border, safe-edit border, routes, and issue markers overlaid.

            ## Underground Summary

            - Important Y-level biome, cave-air, terrain, and fluid maps exported.
            - Center north-south and east-west cross-sections exported.

            ## Route Summary

            - Lush entrance: %s
            - Lush to Molten: %s
            - Molten return: %s
            - Sculk upper connection: %s

            ## Performance

            - Chunks inspected: %d
            - Samples inspected: %d
            - Maximum sampling tick: %.3f ms
            - Missing-chunk generation: disabled
            - World modification: none

            ## Final Recommendation

            %s
            """.formatted(
                data.worldName(),
                data.seed(),
                data.mode().key(),
                data.playableDiameter(),
                data.ledger().generationVersion(),
                data.ledger().datapackVersion(),
                data.sampleStep(),
                validator,
                issues.size(),
                data.route() == null ? "UNKNOWN" : data.route().summary(),
                passFail(check(data, "lush_surface_entrance")),
                passFail(check(data, "lush_to_molten_connection")),
                passFail(check(data, "molten_surface_return")),
                passFail(check(data, "sculk_upper_connection")),
                data.chunksProcessed(),
                data.samplesInspected(),
                data.maxTickNanos() / 1_000_000.0D,
                recommendation
            );
    }

    private static String html(Captured data, List<IssuePoint> issues) {
        StringBuilder options = new StringBuilder();
        for (int y : data.levels()) {
            String name = yName(y);
            options.append("<option value=\"maps/cave_air/").append(name)
                .append(".png\">Cave air Y=").append(y).append("</option>");
            options.append("<option value=\"maps/biomes/").append(name)
                .append(".png\">Biome Y=").append(y).append("</option>");
            options.append("<option value=\"maps/fluids/").append(name)
                .append(".png\">Fluids Y=").append(y).append("</option>");
            options.append("<option value=\"maps/routes/").append(name)
                .append(".png\">Routes Y=").append(y).append("</option>");
        }
        String issueRows = issues.isEmpty()
            ? "<tr><td colspan=\"5\">No validator issue coordinates.</td></tr>"
            : issues.stream().map(issue -> "<tr><td>" + escape(issue.severity())
                + "</td><td>" + escape(issue.type()) + "</td><td>" + issue.x()
                + "</td><td>" + issue.y() + "</td><td>" + issue.z() + "</td></tr>")
                .reduce("", String::concat);
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Valous Review - %s</title>
              <style>
                :root{color-scheme:dark;--bg:#111318;--panel:#1b1f26;--line:#343b46;--text:#e8edf2;--muted:#9da8b5;--cyan:#45d8e8;--orange:#ff9c42}
                *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px Arial,sans-serif}
                header{padding:20px 28px;border-bottom:1px solid var(--line);background:#171a20;position:sticky;top:0;z-index:2}
                h1,h2{margin:0 0 12px}main{padding:22px 28px;display:grid;gap:20px}.band{border-top:1px solid var(--line);padding-top:18px}
                .meta{display:flex;gap:20px;flex-wrap:wrap;color:var(--muted)}.maps{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:12px}
                figure{margin:0;background:var(--panel);border:1px solid var(--line);padding:10px}img{display:block;width:100%%;height:auto;image-rendering:auto}
                select{background:#242a33;color:var(--text);border:1px solid var(--line);padding:9px;min-width:320px}
                table{width:100%%;border-collapse:collapse;background:var(--panel)}th,td{text-align:left;padding:8px;border-bottom:1px solid var(--line)}
                a{color:var(--cyan)}.warning{color:var(--orange)}
              </style>
            </head>
            <body>
              <header><h1>Valous World Review</h1><div class="meta"><span>%s</span><span>Seed %d</span><span>%s mode</span><span>%d blocks/pixel</span></div></header>
              <main>
                <section><h2>Overview</h2><div class="maps"><figure><img src="overview.png" alt="Surface overview"></figure><figure><img src="maps/surface/biomes.png" alt="Surface biomes"></figure></div></section>
                <section class="band"><h2>Surface Review</h2><div class="maps"><figure><img src="maps/surface/materials.png"></figure><figure><img src="maps/surface/slope.png"></figure><figure><img src="maps/surface/water-depth.png"></figure></div></section>
                <section class="band"><h2>Underground Review</h2><p><select id="layer">%s</select></p><figure><img id="layerImage" src="maps/cave_air/%s.png"></figure></section>
                <section class="band"><h2>Entrance Morphology</h2><figure><img src="maps/routes/entrance-centerline.png" alt="Lush entrance centerline"></figure></section>
                <section class="band"><h2>Cross-Sections</h2><div class="maps"><figure><img src="cross_sections/north_south/center-z-axis.png"></figure><figure><img src="cross_sections/east_west/center-x-axis.png"></figure></div></section>
                <section class="band"><h2>Issues</h2><table><thead><tr><th>Severity</th><th>Type</th><th>X</th><th>Y</th><th>Z</th></tr></thead><tbody>%s</tbody></table></section>
                <section class="band"><h2>Files</h2><p><a href="review-summary.md">Review summary</a> | <a href="manifest.json">Manifest</a> | <a href="metrics.json">Metrics</a> | <a href="validation.json">Validation</a> | <a href="route-postprocess.json">Route report</a></p></section>
              </main>
              <script>
                const select=document.getElementById('layer'),image=document.getElementById('layerImage');
                select.addEventListener('change',()=>image.src=select.value);
              </script>
            </body>
            </html>
            """.formatted(
                escape(data.worldName()),
                escape(data.worldName()),
                data.seed(),
                data.mode().key(),
                data.sampleStep(),
                options,
                yName(data.levels()[0]),
                issueRows
            );
    }

    private record IssuePoint(
        String severity,
        String type,
        int x,
        int y,
        int z,
        String description
    ) {
    }

    private static List<IssuePoint> issuePoints(JsonObject validation) {
        List<IssuePoint> result = new ArrayList<>();
        if (validation == null || !validation.has("unsupported_tree_points")) {
            return result;
        }
        for (var element : validation.getAsJsonArray("unsupported_tree_points")) {
            JsonObject point = element.getAsJsonObject();
            int gap = point.has("support_gap") ? point.get("support_gap").getAsInt() : 0;
            result.add(new IssuePoint(
                gap > 8 ? "high" : "medium",
                "unsupported_tree",
                point.get("x").getAsInt(),
                point.get("y").getAsInt(),
                point.get("z").getAsInt(),
                "Tree support gap " + gap
            ));
        }
        return List.copyOf(result);
    }

    private static JsonObject readJson(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try {
            return GSON.fromJson(Files.readString(path), JsonObject.class);
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static void copyIfExists(Path source, Path target) throws IOException {
        if (source != null && Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.writeString(target, "{}\n", StandardCharsets.UTF_8);
        }
    }

    private static void writeJson(Path target, JsonObject value) throws IOException {
        Files.writeString(target, GSON.toJson(value), StandardCharsets.UTF_8);
    }

    private static void zipDirectory(Path directory, Path zip) throws IOException {
        try (OutputStream output = Files.newOutputStream(zip);
             ZipOutputStream archive = new ZipOutputStream(output);
             var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String name = directory.relativize(file).toString().replace('\\', '/');
                archive.putNextEntry(new ZipEntry(name));
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
    }

    private static Map<Integer, long[]> copyStatistics(Map<Integer, long[]> source) {
        Map<Integer, long[]> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.clone()));
        return Map.copyOf(copy);
    }

    private static byte occupancy(Block block, Block above) {
        Material type = block.getType();
        if (type == Material.WATER) {
            return 2;
        }
        if (type == Material.LAVA) {
            return 3;
        }
        if (block.isPassable()) {
            return above.isPassable() && above.getType() != Material.WATER
                && above.getType() != Material.LAVA ? (byte) 4 : (byte) 1;
        }
        return 0;
    }

    private static String biomeKey(World world, int x, int y, int z) {
        try {
            return world.getBiome(x, y, z).getKey().toString();
        } catch (RuntimeException ex) {
            return "minecraft:unknown";
        }
    }

    private static Color materialColor(Material material) {
        return switch (material) {
            case MYCELIUM -> new Color(112, 76, 129);
            case COARSE_DIRT -> new Color(119, 82, 54);
            case DIRT -> new Color(125, 89, 58);
            case ROOTED_DIRT -> new Color(112, 79, 56);
            case GRAVEL -> new Color(125, 121, 119);
            case CLAY -> new Color(151, 158, 171);
            case SAND -> new Color(218, 207, 157);
            case STONE -> new Color(120, 120, 120);
            case TUFF -> new Color(92, 99, 94);
            case PACKED_MUD -> new Color(141, 104, 78);
            case BASALT -> new Color(74, 70, 74);
            case MAGMA_BLOCK -> new Color(196, 75, 30);
            case WATER -> new Color(35, 103, 196);
            case LAVA -> new Color(239, 84, 29);
            default -> new Color(78, 83, 88);
        };
    }

    private static Color heightColor(int y) {
        if (y < 63) {
            return new Color(24, 64, Math.min(205, 100 + (63 - y) * 3));
        }
        double normalized = Math.max(0.0D, Math.min(1.0D, (y - 63) / 70.0D));
        return new Color(
            (int) (62 + normalized * 150),
            (int) (103 + normalized * 130),
            (int) (62 + normalized * 120)
        );
    }

    private static Color slopeColor(int delta) {
        if (delta <= 2) {
            return new Color(49, 132, 84);
        }
        if (delta <= 6) {
            return new Color(166, 153, 57);
        }
        if (delta <= 12) {
            return new Color(213, 116, 41);
        }
        return new Color(193, 42, 45);
    }

    private static Color occupancyColor(byte state) {
        return switch (state) {
            case 1 -> new Color(42, 47, 56);
            case 2 -> new Color(35, 106, 203);
            case 3 -> new Color(239, 90, 36);
            case 4 -> new Color(97, 214, 198);
            default -> new Color(28, 31, 37);
        };
    }

    private static Color caveColor(byte state) {
        return state == 4
            ? new Color(86, 218, 192)
            : state == 2 ? new Color(35, 106, 203)
            : state == 3 ? new Color(239, 90, 36)
            : state == 1 ? new Color(66, 72, 82)
            : new Color(22, 25, 30);
    }

    private static Color fluidColor(byte state) {
        return state == 2
            ? new Color(35, 106, 203)
            : state == 3 ? new Color(239, 90, 36)
            : new Color(20, 23, 28);
    }

    private static int color(Color color) {
        return color.getRGB();
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static int alignUp(int value, int step) {
        return Math.floorDiv(value + step - 1, step) * step;
    }

    private static int alignDown(int value, int step) {
        return Math.floorDiv(value, step) * step;
    }

    private static int worldToPixelX(Captured data, int x, int scale) {
        return ((x - data.minX()) / data.sampleStep()) * scale;
    }

    private static int worldToPixelZ(Captured data, int z, int scale, int header) {
        return ((z - data.minZ()) / data.sampleStep()) * scale + header;
    }

    private static String yName(int y) {
        return y < 0 ? "y-neg-" + Math.abs(y) : "y-" + y;
    }

    private static long average(List<Long> values) {
        return values.isEmpty()
            ? 0L
            : (long) values.stream().mapToLong(Long::longValue).average().orElse(0.0D);
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.min(
            sorted.size() - 1,
            Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1)
        );
        return sorted.get(index);
    }

    private static int pathLength(List<ValousRoutePostProcessor.Point> path) {
        if (path == null) {
            return 0;
        }
        int length = 0;
        for (int index = 1; index < path.size(); index++) {
            ValousRoutePostProcessor.Point a = path.get(index - 1);
            ValousRoutePostProcessor.Point b = path.get(index);
            length += (int) Math.round(Math.sqrt(
                Math.pow(a.x() - b.x(), 2)
                    + Math.pow(a.y() - b.y(), 2)
                    + Math.pow(a.z() - b.z(), 2)
            ));
        }
        return length;
    }

    private static boolean check(Captured data, String key) {
        return data.validation() != null
            && data.validation().checks().getOrDefault(key, false);
    }

    private static String passFail(boolean passed) {
        return passed ? "PASS" : "FAIL";
    }

    private static String point(ValousRoutePostProcessor.Point point) {
        return point == null ? "" : point.x() + " " + point.y() + " " + point.z();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
