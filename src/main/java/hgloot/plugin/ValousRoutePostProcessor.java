package hgloot.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Development-only, post-generation traversal builder for Valous factory worlds.
 *
 * <p>This class never generates chunks or assigns biomes. It works only against
 * fully pregenerated, player-empty worlds and edits blocks in bounded server-tick
 * batches before the world can become READY.</p>
 */
final class ValousRoutePostProcessor {
    static final String VERSION = "route-postprocess-morphology-v1";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long TICK_BUDGET_NANOS = 8_000_000L;
    private static final long HARD_STAGE_TICK_LIMIT_NANOS = 50_000_000L;
    private static final int MAX_EXPANSION_KEYS_PER_TICK = 512;
    private static final int MAX_SCAN_SAMPLES_PER_TICK = 192;
    private static final int MAX_EXTRACTION_SAMPLES_PER_TICK = 256;
    private static final int MAX_EDIT_BLOCKS_PER_TICK = 1_024;
    private static final int GRID_STEP = 8;
    private static final int Y_STEP = 8;
    private static final int MIN_SCAN_Y = -192;
    private static final int MAX_SCAN_Y = 20;
    private static final int MIN_BIOME_VOLUME_SAMPLES = 32;
    private static final int PREGENERATED_EDGE_MARGIN = 16;
    private static final int CENTER_PROTECTION_RADIUS = 128;
    private static final int WORLD_BORDER_SAFETY = 4;
    private static final int LUSH_CENTER_REACH = 24;
    private static final int CONNECTOR_CENTER_REACH = 24;
    private static final int MOLTEN_CENTER_REACH = 22;
    private static final int SCULK_CENTER_REACH = 48;
    private static final int VALIDATION_BORDER_MARGIN = 16;
    private static final int MAX_LUSH_REGION_ALTERNATIVES = 16;
    private static final int MAX_REGION_PAIR_COMBINATIONS = 8;
    private static final int MAX_ENDPOINT_ALTERNATIVES = 8;
    private static final int MAX_TOTAL_PLANNING_ATTEMPTS = 64;
    private static final long PAIR_PREPARATION_TIMEOUT_NANOS = 30_000_000_000L;
    private static final long PLANNING_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long EXPANSION_TIMEOUT_MS = 10_000L;
    private static final int MAX_CONCURRENT_EXPANSION_LOADS = 32;

    private ValousRoutePostProcessor() {
    }

    static Job start(
        JavaPlugin plugin,
        World world,
        Settings settings,
        Consumer<Result> completion
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(completion, "completion");
        Job job = new Job(plugin, world, settings, completion);
        job.start();
        return job;
    }

    static int minimumScanY() {
        return MIN_SCAN_Y;
    }

    static Set<Long> requiredScanChunkKeys(int playableDiameter) {
        int halfWidth = Math.max(64, (playableDiameter / 2) - PREGENERATED_EDGE_MARGIN);
        Set<Long> keys = new LinkedHashSet<>();
        for (int z = -halfWidth; z <= halfWidth; z += GRID_STEP) {
            for (int x = -halfWidth; x <= halfWidth; x += GRID_STEP) {
                keys.add(ValousPregenerationLedger.chunkKey(x >> 4, z >> 4));
            }
        }
        return Set.copyOf(keys);
    }

    record Settings(
        String generationVersion,
        String datapackVersion,
        String selectionPolicy,
        int playableDiameter,
        Path persistentReportRoot,
        ValousPregenerationLedger pregenerationLedger,
        ValousChunkHandoff.Result chunkHandoff
    ) {
    }

    record Point(int x, int y, int z) {
        JsonArray toJson() {
            JsonArray array = new JsonArray();
            array.add(x);
            array.add(y);
            array.add(z);
            return array;
        }
    }

    record Bounds(Point min, Point max) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.add("min", min.toJson());
            value.add("max", max.toJson());
            return value;
        }

        Bounds include(Bounds other) {
            return new Bounds(
                new Point(
                    Math.min(min.x(), other.min().x()),
                    Math.min(min.y(), other.min().y()),
                    Math.min(min.z(), other.min().z())
                ),
                new Point(
                    Math.max(max.x(), other.max().x()),
                    Math.max(max.y(), other.max().y()),
                    Math.max(max.z(), other.max().z())
                )
            );
        }

        Bounds inset(int horizontal, int vertical) {
            return new Bounds(
                new Point(min.x() + horizontal, min.y() + vertical, min.z() + horizontal),
                new Point(max.x() - horizontal, max.y() - vertical, max.z() - horizontal)
            );
        }

        boolean contains(Point point) {
            return point != null
                && point.x() >= min.x() && point.x() <= max.x()
                && point.y() >= min.y() && point.y() <= max.y()
                && point.z() >= min.z() && point.z() <= max.z();
        }

        boolean contains(Bounds other) {
            return other != null && contains(other.min()) && contains(other.max());
        }
    }

    record ChunkPoint(int x, int z) {
        JsonArray toJson() {
            JsonArray value = new JsonArray();
            value.add(x);
            value.add(z);
            return value;
        }
    }

    record ConnectionQuality(
        int minimumWidth,
        int medianWidth,
        int minimumHeight,
        int sampledLength,
        double naturalAirRatio,
        String classification
    ) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("minimum_width", minimumWidth);
            value.addProperty("median_width", medianWidth);
            value.addProperty("minimum_height", minimumHeight);
            value.addProperty("sampled_length", sampledLength);
            value.addProperty("natural_air_ratio", naturalAirRatio);
            value.addProperty("postprocessed_air_ratio", 1.0D - naturalAirRatio);
            value.addProperty("classification", classification);
            return value;
        }
    }

    record Result(
        boolean passed,
        String failureReason,
        long scanMs,
        long planningMs,
        long processingMs,
        long maxTickNanos,
        Point lushTarget,
        Point moltenTarget,
        Point frozenTarget,
        Point sculkTarget,
        Point lushSurface,
        Point moltenSurface,
        Bounds lushEntranceBounds,
        Bounds connectorBounds,
        Bounds moltenReturnBounds,
        Bounds sculkChamberBounds,
        List<Point> lushEntrancePath,
        List<Point> connectorPath,
        List<Point> frozenConnectorPath,
        List<Point> moltenReturnPath,
        List<Point> sculkConnectorPath,
        int connectorSampledLength,
        int sculkConnectorSampledLength,
        List<ChunkPoint> modifiedChunks,
        long blocksRemoved,
        long blocksPlaced,
        long fluidsRemoved,
        String idempotencyChecksum,
        Map<String, Integer> biomeVolumeSamples,
        Map<String, ConnectionQuality> connectionQuality,
        Path manifestPath,
        Path reportPath
    ) {
        String summary() {
            return passed ? "pass" : "fail:" + failureReason;
        }

        JsonObject toJson(String worldName, long seed, Settings settings) {
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", 2);
            root.addProperty("world_id", worldName);
            root.addProperty("seed", seed);
            root.addProperty("generation_version", settings.generationVersion());
            root.addProperty("datapack_version", settings.datapackVersion());
            root.addProperty("selection_policy", settings.selectionPolicy());
            root.addProperty("post_processing_version", VERSION);
            root.addProperty("status", passed ? "pass" : "fail");
            root.addProperty("failure_reason", failureReason == null ? "" : failureReason);
            JsonObject timing = new JsonObject();
            timing.addProperty("scan_ms", scanMs);
            timing.addProperty("planning_ms", planningMs);
            timing.addProperty("processing_ms", processingMs);
            timing.addProperty("max_tick_ms", maxTickNanos / 1_000_000.0D);
            root.add("timing", timing);
            JsonObject ledger = new JsonObject();
            ledger.addProperty("world_uuid", settings.pregenerationLedger().worldId().toString());
            ledger.addProperty("generation_token", settings.pregenerationLedger().generationToken().toString());
            ledger.addProperty("expected_chunks", settings.pregenerationLedger().expectedChunkCount());
            ledger.addProperty("completed_chunks", settings.pregenerationLedger().completedChunkKeys().size());
            ledger.addProperty("failed_chunks", settings.pregenerationLedger().failedChunkKeys().size());
            ledger.addProperty("completed_at_ms", settings.pregenerationLedger().completedAtMs());
            root.add("pregeneration_ledger", ledger);
            root.add("chunk_handoff", settings.chunkHandoff() == null
                ? new JsonObject()
                : settings.chunkHandoff().toJson());
            JsonObject regions = new JsonObject();
            regions.add("lush", pointJson(lushTarget, ValousPrototypeValidator.LUSH_JUNGLE));
            regions.add("molten", pointJson(moltenTarget, ValousPrototypeValidator.MOLTEN_CAVERNS));
            regions.add("frozen", pointJson(frozenTarget, ValousPrototypeValidator.FROZEN_CAVES));
            regions.add("sculk", pointJson(sculkTarget, ValousPrototypeValidator.SCULK_ABYSS));
            root.add("selected_regions", regions);
            JsonObject routes = new JsonObject();
            routes.add("lush_entrance", routeJson(
                lushSurface, lushTarget, lushEntranceBounds, lushEntrancePath, pathLength(lushEntrancePath)
            ));
            routes.add("lush_to_molten", routeJson(
                lushTarget,
                moltenTarget,
                connectorBounds,
                connectorPath,
                connectorSampledLength
            ));
            routes.add("frozen_connection", routeJson(
                firstPoint(frozenConnectorPath),
                frozenTarget,
                pathBounds(frozenConnectorPath),
                frozenConnectorPath,
                pathLength(frozenConnectorPath)
            ));
            routes.add("molten_return", routeJson(
                moltenTarget, moltenSurface, moltenReturnBounds, moltenReturnPath, pathLength(moltenReturnPath)
            ));
            routes.add("sculk_connection", routeJson(
                null,
                sculkTarget,
                sculkChamberBounds,
                sculkConnectorPath,
                sculkConnectorSampledLength
            ));
            root.add("routes", routes);
            JsonObject edits = new JsonObject();
            edits.addProperty("modified_chunk_count", modifiedChunks.size());
            JsonArray chunks = new JsonArray();
            modifiedChunks.forEach(chunk -> chunks.add(chunk.toJson()));
            edits.add("modified_chunks", chunks);
            edits.addProperty("blocks_removed", blocksRemoved);
            edits.addProperty("blocks_placed", blocksPlaced);
            edits.addProperty("fluids_removed", fluidsRemoved);
            edits.addProperty("idempotency_checksum", idempotencyChecksum);
            root.add("edits", edits);
            JsonObject biomes = new JsonObject();
            biomeVolumeSamples.forEach(biomes::addProperty);
            root.add("biome_volume_samples", biomes);
            JsonObject morphology = new JsonObject();
            ValousMorphologyGeometry.EntranceMetrics entrance =
                ValousMorphologyGeometry.entranceMetrics(lushEntrancePath);
            JsonObject entranceJson = new JsonObject();
            entranceJson.addProperty("vertical_drop", entrance.verticalDrop());
            entranceJson.addProperty("maximum_horizontal_drift", entrance.maximumHorizontalDrift());
            entranceJson.addProperty("average_horizontal_drift", entrance.averageHorizontalDrift());
            entranceJson.addProperty("directional_reversals", entrance.directionalReversals());
            entranceJson.addProperty("circles_center", entrance.circlesCenter());
            entranceJson.addProperty("horizontal_to_vertical_ratio", entrance.horizontalToVerticalRatio());
            entranceJson.addProperty("status", entrance.passed() ? "pass" : "fail");
            morphology.add("lush_entrance", entranceJson);
            JsonObject connections = new JsonObject();
            connectionQuality.forEach((id, quality) -> connections.add(id, quality.toJson()));
            morphology.add("connections", connections);
            root.add("morphology", morphology);
            return root;
        }

        private static JsonObject pointJson(Point point, String biome) {
            JsonObject value = new JsonObject();
            value.add("center", point == null ? new JsonArray() : point.toJson());
            value.addProperty("biome", biome);
            return value;
        }

        private static Point firstPoint(List<Point> path) {
            return path == null || path.isEmpty() ? null : path.getFirst();
        }

        private static Bounds pathBounds(List<Point> path) {
            if (path == null || path.isEmpty()) {
                return null;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Point point : path) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
                maxZ = Math.max(maxZ, point.z());
            }
            return new Bounds(new Point(minX, minY, minZ), new Point(maxX, maxY, maxZ));
        }

        private static JsonObject routeJson(
            Point start,
            Point end,
            Bounds bounds,
            List<Point> path,
            int sampledLength
        ) {
            JsonObject value = new JsonObject();
            value.add("start", start == null ? new JsonArray() : start.toJson());
            value.add("end", end == null ? new JsonArray() : end.toJson());
            value.add("bounds", bounds == null ? new JsonObject() : bounds.toJson());
            value.addProperty("sampled_length", sampledLength);
            JsonArray points = new JsonArray();
            if (path != null) {
                path.forEach(point -> points.add(point.toJson()));
            }
            value.add("path", points);
            return value;
        }

        private static int pathLength(List<Point> path) {
            int total = 0;
            if (path == null) {
                return total;
            }
            for (int index = 1; index < path.size(); index++) {
                Point left = path.get(index - 1);
                Point right = path.get(index);
                double dx = right.x() - left.x();
                double dy = right.y() - left.y();
                double dz = right.z() - left.z();
                total += (int) Math.round(Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)));
            }
            return total;
        }
    }

    static final class Job {
        private final JavaPlugin plugin;
        private final World world;
        private final Settings settings;
        private final Consumer<Result> completion;
        private final int halfWidth;
        private final Bounds pregeneratedBounds;
        private final Bounds playableBounds;
        private final Bounds safeEditBounds;
        private final Bounds lushCenterBounds;
        private final Bounds connectorCenterBounds;
        private final Bounds moltenCenterBounds;
        private final Bounds sculkCenterBounds;
        private final Bounds validationBounds;
        private final List<SampleCoordinate> scanCoordinates = new ArrayList<>();
        private final Map<String, List<RegionSample>> samplesByBiome = new LinkedHashMap<>();
        private final List<EditShape> carveShapes = new ArrayList<>();
        private final List<FloorPatch> floorPatches = new ArrayList<>();
        private final List<FluidSealPatch> fluidSealPatches = new ArrayList<>();
        private final Set<Long> modifiedChunkKeys = new LinkedHashSet<>();
        private final Set<Long> heldChunkKeys = new LinkedHashSet<>();
        private final Set<Long> additionalChunkKeys = new LinkedHashSet<>();
        private final Deque<Long> expansionRemaining = new ArrayDeque<>();
        private final Map<Long, CompletableFuture<Chunk>> expansionPending = new LinkedHashMap<>();
        private final Map<String, Integer> planningFailureCounts = new LinkedHashMap<>();
        private final Map<String, Integer> pairRejectionCounts = new LinkedHashMap<>();
        private final Map<String, ConnectionQuality> connectionQuality = new LinkedHashMap<>();
        private final List<PlanningAttempt> planningAttempts = new ArrayList<>();
        private final Map<String, RegionSample> lushRepresentatives = new LinkedHashMap<>();
        private final Map<String, RegionSample> moltenRepresentatives = new LinkedHashMap<>();
        private final Map<String, RegionSample> frozenRepresentatives = new LinkedHashMap<>();
        private final Map<String, RegionSample> sculkRepresentatives = new LinkedHashMap<>();
        private final Map<Stage, Long> maxTickNanosByStage = new LinkedHashMap<>();
        private final Map<WorkStage, StageTiming> stageTimings = new LinkedHashMap<>();
        private final MessageDigest checksum;
        private final long startedAtMs = System.currentTimeMillis();

        private BukkitTask task;
        private Stage stage = Stage.SCANNING;
        private boolean canceled;
        private String failureReason = "";
        private int scanIndex;
        private int shapeIndex;
        private int floorIndex;
        private int fluidSealIndex;
        private ShapeCursor shapeCursor;
        private FloorCursor floorCursor;
        private FluidSealCursor fluidSealCursor;
        private long scanCompletedAtMs;
        private long planningCompletedAtMs;
        private long processingCompletedAtMs;
        private long maxTickNanos;
        private long blocksRemoved;
        private long blocksPlaced;
        private long fluidsRemoved;
        private Point lushTarget;
        private Point moltenTarget;
        private Point frozenTarget;
        private Point sculkTarget;
        private Point sculkUpperTarget;
        private Point lushSurface;
        private Point moltenSurface;
        private Bounds lushEntranceBounds;
        private Bounds connectorBounds;
        private Bounds moltenReturnBounds;
        private Bounds sculkChamberBounds;
        private List<Point> lushEntrancePath = List.of();
        private List<Point> connectorPath = List.of();
        private List<Point> frozenConnectorPath = List.of();
        private List<Point> moltenReturnPath = List.of();
        private List<Point> sculkConnectorPath = List.of();
        private int connectorSampledLength;
        private int sculkConnectorSampledLength;
        private long pendingChunkKey = Long.MIN_VALUE;
        private CompletableFuture<Chunk> pendingChunkLoad;
        private SampleCoordinate firstScanFailure;
        private long expansionStartedAtMs;
        private long expansionPreparationStartedAtMs;
        private long expansionPreparationCompletedAtMs;
        private ValousChunkHandoff.Lease expansionInitialLease;
        private int expansionMinChunkX;
        private int expansionMaxChunkX;
        private int expansionMaxChunkZ;
        private int expansionCursorChunkX;
        private int expansionCursorChunkZ;
        private int expansionExpectedChunkCount;
        private int expansionPreparedChunkCount;
        private int peakHeldChunks;
        private int planningAttemptCount;
        private boolean planningInitialized;
        private ExtractionPhase extractionPhase = ExtractionPhase.LUSH;
        private int extractionIndex;
        private long planningStartedAtNanos;
        private long planningAttemptStartedAtNanos;
        private long planningStartedAtMs;
        private int planningPairIndex;
        private int planningSculkIndex;
        private int validRegionPairCount;
        private PlanningPhase planningPhase = PlanningPhase.LUSH_LAVA;
        private int planningLavaIndex;
        private int planningLushPairIndex;
        private int planningMoltenPairIndex;
        private final Map<RegionSample, Double> lushLavaExposure = new LinkedHashMap<>();
        private final Map<RegionSample, Double> moltenLavaExposure = new LinkedHashMap<>();
        private final List<RegionPair> bestRegionPairs = new ArrayList<>();
        private List<RegionSample> lushCandidates = List.of();
        private List<RegionSample> moltenCandidates = List.of();
        private List<RegionSample> frozenCandidates = List.of();
        private List<RegionSample> sculkCandidates = List.of();
        private List<RegionPair> regionPairs = List.of();
        private long expectedRemovedOperations;
        private long expectedPlacedOperations;
        private long expectedFluidCandidateOperations;
        private long lastRouteVolumeNanos;
        private long lastEnvelopeValidationNanos;
        private int lastRouteVolumeUnits;
        private int tickWorkUnits;
        private boolean hardTickLimitExceeded;

        private Job(
            JavaPlugin plugin,
            World world,
            Settings settings,
            Consumer<Result> completion
        ) {
            this.plugin = plugin;
            this.world = world;
            this.settings = settings;
            this.completion = completion;
            this.halfWidth = Math.max(64, (settings.playableDiameter() / 2) - PREGENERATED_EDGE_MARGIN);
            ValousPregenerationLedger ledger = settings.pregenerationLedger();
            this.pregeneratedBounds = new Bounds(
                new Point(ledger.minBlockX(), world.getMinHeight() + 1, ledger.minBlockZ()),
                new Point(ledger.maxBlockX(), world.getMaxHeight() - 1, ledger.maxBlockZ())
            );
            double borderHalf = world.getWorldBorder().getSize() / 2.0D;
            double borderCenterX = world.getWorldBorder().getCenter().getX();
            double borderCenterZ = world.getWorldBorder().getCenter().getZ();
            this.playableBounds = new Bounds(
                new Point(
                    (int) Math.floor(borderCenterX - borderHalf),
                    world.getMinHeight() + 1,
                    (int) Math.floor(borderCenterZ - borderHalf)
                ),
                new Point(
                    (int) Math.ceil(borderCenterX + borderHalf) - 1,
                    world.getMaxHeight() - 1,
                    (int) Math.ceil(borderCenterZ + borderHalf) - 1
                )
            );
            this.safeEditBounds = intersect(
                pregeneratedBounds,
                playableBounds.inset(WORLD_BORDER_SAFETY, 0)
            );
            this.lushCenterBounds = safeEditBounds.inset(LUSH_CENTER_REACH, 0);
            this.connectorCenterBounds = safeEditBounds.inset(CONNECTOR_CENTER_REACH, 0);
            this.moltenCenterBounds = safeEditBounds.inset(MOLTEN_CENTER_REACH, 0);
            this.sculkCenterBounds = safeEditBounds.inset(SCULK_CENTER_REACH, 0);
            this.validationBounds = intersect(
                pregeneratedBounds,
                playableBounds.inset(VALIDATION_BORDER_MARGIN, 0)
            );
            for (String biome : List.of(
                ValousPrototypeValidator.LUSH_JUNGLE,
                ValousPrototypeValidator.MOLTEN_CAVERNS,
                ValousPrototypeValidator.FROZEN_CAVES,
                ValousPrototypeValidator.SCULK_ABYSS
            )) {
                samplesByBiome.put(biome, new ArrayList<>());
            }
            for (int z = -halfWidth; z <= halfWidth; z += GRID_STEP) {
                for (int x = -halfWidth; x <= halfWidth; x += GRID_STEP) {
                    for (int y = MIN_SCAN_Y; y <= MAX_SCAN_Y; y += Y_STEP) {
                        scanCoordinates.add(new SampleCoordinate(x, y, z));
                    }
                }
            }
            try {
                this.checksum = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 unavailable", ex);
            }
        }

        private void start() {
            if (!world.getPlayers().isEmpty()) {
                finishFailed("world_accessible_to_players");
                return;
            }
            if (settings.chunkHandoff() == null || !settings.chunkHandoff().passed()) {
                finishFailed("chunk_handoff_failed:"
                    + (settings.chunkHandoff() == null
                        ? "missing_result"
                        : settings.chunkHandoff().failureReason()));
                return;
            }
            if (settings.chunkHandoff().lease() == null
                || settings.chunkHandoff().lease().heldCount()
                    != requiredScanChunkKeys(settings.playableDiameter()).size()) {
                finishFailed("chunk_handoff_lease_incomplete");
                return;
            }
            Result existing = readExistingManifest();
            if (existing != null && existing.passed()) {
                plugin.getLogger().info("[Queue][ValousPostProcess] event=idempotent_manifest_reused world="
                    + world.getName() + " checksum=" + existing.idempotencyChecksum());
                completion.accept(existing);
                return;
            }
            plugin.getLogger().info("[Queue][ValousPostProcess] event=started world=" + world.getName()
                + " version=" + VERSION + " samples=" + scanCoordinates.size()
                + " tick_budget_ms=" + (TICK_BUDGET_NANOS / 1_000_000L)
                + " pregenerated_bounds=" + boundsText(pregeneratedBounds)
                + " playable_bounds=" + boundsText(playableBounds)
                + " safe_edit_bounds=" + boundsText(safeEditBounds)
                + " lush_centers=" + boundsText(lushCenterBounds)
                + " connector_centers=" + boundsText(connectorCenterBounds)
                + " molten_centers=" + boundsText(moltenCenterBounds)
                + " sculk_centers=" + boundsText(sculkCenterBounds)
                + " validation_bounds=" + boundsText(validationBounds));
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    tick();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        void cancel() {
            canceled = true;
            if (task != null) {
                task.cancel();
            }
            releaseChunkTickets();
        }

        private void tick() {
            if (canceled || !plugin.isEnabled()) {
                cancel();
                return;
            }
            if (!world.getPlayers().isEmpty()) {
                finishFailed("world_became_accessible_to_players");
                return;
            }
            long tickStartedAt = System.nanoTime();
            Stage tickStage = stage;
            tickWorkUnits = 0;
            long fluidsBeforeTick = fluidsRemoved;
            switch (stage) {
                case SCANNING -> scanTick(tickStartedAt);
                case EXPANSION_PREPARING -> expansionPreparationTick(tickStartedAt);
                case EXPANDING -> expansionTick();
                case EXTRACTING -> extractionTick(tickStartedAt);
                case PLANNING -> plan();
                case CARVING -> carveTick(tickStartedAt);
                case FLUID_SEALING -> fluidSealTick(tickStartedAt);
                case FLOORING -> floorTick(tickStartedAt);
                case WRITING -> finishSuccess();
                case COMPLETE -> {
                    return;
                }
            }
            long elapsed = System.nanoTime() - tickStartedAt;
            maxTickNanos = Math.max(maxTickNanos, elapsed);
            maxTickNanosByStage.merge(tickStage, elapsed, Math::max);
            WorkStage workStage = switch (tickStage) {
                case SCANNING -> WorkStage.INITIAL_SCAN;
                case EXPANSION_PREPARING -> WorkStage.EXPANSION_PREPARATION;
                case EXPANDING -> WorkStage.SCAN_EXPANSION;
                case EXTRACTING -> WorkStage.REGION_EXTRACTION;
                case CARVING -> WorkStage.BLOCK_REMOVAL;
                case FLUID_SEALING -> WorkStage.FLUID_REPLACEMENT;
                case FLOORING -> WorkStage.BLOCK_PLACEMENT;
                case WRITING -> WorkStage.MANIFEST_SERIALIZATION;
                case PLANNING, COMPLETE -> null;
            };
            if (workStage != null) {
                recordStageTick(workStage, elapsed, tickWorkUnits);
            }
            if (tickStage == Stage.CARVING && fluidsRemoved > fluidsBeforeTick) {
                recordStageTick(
                    WorkStage.FLUID_REPLACEMENT,
                    0L,
                    (int) Math.min(Integer.MAX_VALUE, fluidsRemoved - fluidsBeforeTick)
                );
            }
            if (elapsed > HARD_STAGE_TICK_LIMIT_NANOS
                && tickStage != Stage.COMPLETE
                && stage != Stage.COMPLETE) {
                hardTickLimitExceeded = true;
                finishFailed("stage_tick_budget_exceeded:"
                    + (workStage == null
                        ? tickStage.name().toLowerCase(Locale.ROOT)
                        : workStage.id));
            }
        }

        private void scanTick(long tickStartedAt) {
            int processed = 0;
            while (scanIndex < scanCoordinates.size()
                && processed < MAX_SCAN_SAMPLES_PER_TICK
                && System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                SampleCoordinate coordinate = scanCoordinates.get(scanIndex);
                ChunkAvailability availability = ensureGeneratedChunkLoaded(coordinate.x(), coordinate.z());
                if (availability == ChunkAvailability.WAITING) {
                    return;
                }
                if (availability == ChunkAvailability.INVALID) {
                    firstScanFailure = coordinate;
                    long key = ValousPregenerationLedger.chunkKey(coordinate.x() >> 4, coordinate.z() >> 4);
                    plugin.getLogger().warning("[Queue][ValousPostProcess] event=scan_chunk_rejected world="
                        + world.getName() + " block=" + coordinate.x() + "," + coordinate.y() + "," + coordinate.z()
                        + " chunk=" + (coordinate.x() >> 4) + "," + (coordinate.z() >> 4)
                        + " key=" + Long.toUnsignedString(key)
                        + " ledger=" + settings.pregenerationLedger().contains(key)
                        + " generated=" + world.isChunkGenerated(coordinate.x() >> 4, coordinate.z() >> 4)
                        + " loaded=" + world.isChunkLoaded(coordinate.x() >> 4, coordinate.z() >> 4));
                    finishFailed("missing_or_out_of_bounds_chunk_during_scan");
                    return;
                }
                scanIndex++;
                processed++;
                tickWorkUnits++;
                String biome = biomeKey(world.getBiome(coordinate.x(), coordinate.y(), coordinate.z()));
                List<RegionSample> samples = samplesByBiome.get(biome);
                if (samples == null) {
                    continue;
                }
                Block feet = world.getBlockAt(coordinate.x(), coordinate.y(), coordinate.z());
                Block head = world.getBlockAt(coordinate.x(), coordinate.y() + 1, coordinate.z());
                boolean passable = safePassable(feet) && safePassable(head);
                samples.add(new RegionSample(
                    coordinate.x(),
                    coordinate.y(),
                    coordinate.z(),
                    biome,
                    passable
                ));
            }
            if (scanIndex >= scanCoordinates.size()) {
                scanCompletedAtMs = System.currentTimeMillis();
                plugin.getLogger().info("[Queue][ValousPostProcess] event=scan_complete world="
                    + world.getName() + " scan_ms=" + (scanCompletedAtMs - startedAtMs)
                    + " lush=" + samplesByBiome.get(ValousPrototypeValidator.LUSH_JUNGLE).size()
                    + " molten=" + samplesByBiome.get(ValousPrototypeValidator.MOLTEN_CAVERNS).size()
                    + " frozen=" + samplesByBiome.get(ValousPrototypeValidator.FROZEN_CAVES).size()
                    + " sculk=" + samplesByBiome.get(ValousPrototypeValidator.SCULK_ABYSS).size());
                beginPlayableExpansion();
            }
        }

        private void beginPlayableExpansion() {
            expansionInitialLease = settings.chunkHandoff().lease();
            expansionMinChunkX = playableBounds.min().x() >> 4;
            expansionMaxChunkX = playableBounds.max().x() >> 4;
            expansionCursorChunkX = expansionMinChunkX;
            expansionCursorChunkZ = playableBounds.min().z() >> 4;
            expansionMaxChunkZ = playableBounds.max().z() >> 4;
            expansionExpectedChunkCount =
                (expansionMaxChunkX - expansionMinChunkX + 1)
                    * (expansionMaxChunkZ - expansionCursorChunkZ + 1);
            expansionPreparedChunkCount = 0;
            expansionPreparationStartedAtMs = System.currentTimeMillis();
            stage = Stage.EXPANSION_PREPARING;
            plugin.getLogger().info(
                "[Queue][ValousPostProcess] event=scan_expansion_preparation_started world="
                    + world.getName()
                    + " required_chunks=" + expansionExpectedChunkCount
                    + " tick_budget_ms=" + (TICK_BUDGET_NANOS / 1_000_000L)
                    + " max_keys_per_tick=" + MAX_EXPANSION_KEYS_PER_TICK
                    + " generate=false"
            );
        }

        private void expansionPreparationTick(long tickStartedAt) {
            int processed = 0;
            while (expansionCursorChunkZ <= expansionMaxChunkZ
                && processed < MAX_EXPANSION_KEYS_PER_TICK
                && System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                long key = ValousPregenerationLedger.chunkKey(
                    expansionCursorChunkX,
                    expansionCursorChunkZ
                );
                if (!settings.pregenerationLedger().contains(key)) {
                    finishFailed("playable_chunk_absent_from_completed_ledger");
                    return;
                }
                if (!expansionInitialLease.contains(key)) {
                    expansionRemaining.addLast(key);
                    additionalChunkKeys.add(key);
                }
                expansionPreparedChunkCount++;
                processed++;
                tickWorkUnits++;
                expansionCursorChunkX++;
                if (expansionCursorChunkX > expansionMaxChunkX) {
                    expansionCursorChunkX = expansionMinChunkX;
                    expansionCursorChunkZ++;
                }
            }
            if (expansionCursorChunkZ <= expansionMaxChunkZ) {
                return;
            }

            expansionPreparationCompletedAtMs = System.currentTimeMillis();
            plugin.getLogger().info(
                "[Queue][ValousPostProcess] event=scan_expansion_preparation_complete world="
                    + world.getName()
                    + " required_chunks=" + expansionExpectedChunkCount
                    + " prepared_chunks=" + expansionPreparedChunkCount
                    + " additional_chunks=" + additionalChunkKeys.size()
                    + " duration_ms="
                    + (expansionPreparationCompletedAtMs - expansionPreparationStartedAtMs)
                    + " generate=false"
            );
            if (expansionRemaining.isEmpty()) {
                beginExtraction();
                return;
            }
            expansionStartedAtMs = System.currentTimeMillis();
            stage = Stage.EXPANDING;
            plugin.getLogger().info("[Queue][ValousPostProcess] event=scan_expansion_started world="
                + world.getName() + " initial_chunks=" + expansionInitialLease.heldCount()
                + " additional_chunks=" + additionalChunkKeys.size()
                + " reason=component_edit_envelopes_reach_playable_edge"
                + " generate=false");
        }

        private void expansionTick() {
            if (System.currentTimeMillis() - expansionStartedAtMs > EXPANSION_TIMEOUT_MS) {
                finishFailed("ledger_backed_scan_expansion_timeout");
                return;
            }
            List<Long> resolved = new ArrayList<>();
            for (Map.Entry<Long, CompletableFuture<Chunk>> entry : expansionPending.entrySet()) {
                if (!entry.getValue().isDone()) {
                    continue;
                }
                resolved.add(entry.getKey());
                try {
                    Chunk chunk = entry.getValue().join();
                    if (chunk == null) {
                        finishFailed("ledger_backed_scan_expansion_returned_null");
                        return;
                    }
                    long key = ValousPregenerationLedger.chunkKey(chunk.getX(), chunk.getZ());
                    if (key != entry.getKey() || !settings.pregenerationLedger().contains(key)) {
                        finishFailed("ledger_backed_scan_expansion_key_mismatch");
                        return;
                    }
                    holdChunk(chunk.getX(), chunk.getZ(), key);
                    tickWorkUnits++;
                    peakHeldChunks = Math.max(
                        peakHeldChunks,
                        settings.chunkHandoff().lease().heldCount() + heldChunkKeys.size()
                    );
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(
                        java.util.logging.Level.WARNING,
                        "[Queue][ValousPostProcess] event=scan_expansion_failed world="
                            + world.getName() + " key=" + Long.toUnsignedString(entry.getKey()),
                        ex
                    );
                    finishFailed("ledger_backed_scan_expansion_exception:"
                        + ex.getClass().getSimpleName());
                    return;
                }
            }
            resolved.forEach(expansionPending::remove);

            while (!expansionRemaining.isEmpty()
                && expansionPending.size() < MAX_CONCURRENT_EXPANSION_LOADS) {
                long key = expansionRemaining.removeFirst();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                if (!settings.pregenerationLedger().contains(key)) {
                    finishFailed("scan_expansion_requested_chunk_outside_ledger");
                    return;
                }
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    holdChunk(chunkX, chunkZ, key);
                    tickWorkUnits++;
                    peakHeldChunks = Math.max(
                        peakHeldChunks,
                        settings.chunkHandoff().lease().heldCount() + heldChunkKeys.size()
                    );
                    continue;
                }
                try {
                    expansionPending.put(key, world.getChunkAtAsync(chunkX, chunkZ, false, true));
                } catch (RuntimeException ex) {
                    finishFailed("scan_expansion_no_generation_acquisition_threw:"
                        + ex.getClass().getSimpleName());
                    return;
                }
            }

            if (expansionRemaining.isEmpty()
                && expansionPending.isEmpty()
                && heldChunkKeys.size() == additionalChunkKeys.size()) {
                plugin.getLogger().info("[Queue][ValousPostProcess] event=scan_expansion_complete world="
                    + world.getName() + " initial_chunks=" + settings.chunkHandoff().lease().heldCount()
                    + " additional_chunks=" + heldChunkKeys.size()
                    + " peak_held=" + peakHeldChunks
                    + " duration_ms=" + (System.currentTimeMillis() - expansionStartedAtMs)
                    + " generate=false");
                beginExtraction();
            }
        }

        private void beginExtraction() {
            List<RegionSample> lush = samplesByBiome.get(ValousPrototypeValidator.LUSH_JUNGLE);
            List<RegionSample> molten = samplesByBiome.get(ValousPrototypeValidator.MOLTEN_CAVERNS);
            List<RegionSample> frozen = samplesByBiome.get(ValousPrototypeValidator.FROZEN_CAVES);
            List<RegionSample> sculk = samplesByBiome.get(ValousPrototypeValidator.SCULK_ABYSS);
            if (lush.size() < MIN_BIOME_VOLUME_SAMPLES
                || molten.size() < MIN_BIOME_VOLUME_SAMPLES
                || frozen.size() < MIN_BIOME_VOLUME_SAMPLES
                || sculk.size() < MIN_BIOME_VOLUME_SAMPLES) {
                finishFailed("required_biome_volume_missing");
                return;
            }
            extractionPhase = ExtractionPhase.LUSH;
            extractionIndex = 0;
            stage = Stage.EXTRACTING;
            plugin.getLogger().info("[Queue][ValousPostProcess] event=region_extraction_started world="
                + world.getName() + " lush_samples=" + lush.size()
                + " molten_samples=" + molten.size()
                + " sculk_samples=" + sculk.size()
                + " tick_budget_ms=" + (TICK_BUDGET_NANOS / 1_000_000L));
        }

        private void extractionTick(long tickStartedAt) {
            int processed = 0;
            while (extractionPhase != ExtractionPhase.COMPLETE
                && processed < MAX_EXTRACTION_SAMPLES_PER_TICK
                && System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                List<RegionSample> source = extractionSource(extractionPhase);
                if (extractionIndex >= source.size()) {
                    extractionPhase = extractionPhase.next();
                    extractionIndex = 0;
                    continue;
                }
                RegionSample sample = source.get(extractionIndex++);
                processed++;
                tickWorkUnits++;
                switch (extractionPhase) {
                    case LUSH -> considerLushRepresentative(sample);
                    case MOLTEN -> considerMoltenRepresentative(sample);
                    case FROZEN -> considerFrozenRepresentative(sample);
                    case SCULK -> considerSculkRepresentative(sample);
                    case COMPLETE -> {
                    }
                }
            }
            if (extractionPhase != ExtractionPhase.COMPLETE) {
                return;
            }
            lushCandidates = sortedRepresentatives(lushRepresentatives, lushCandidateComparator());
            moltenCandidates = sortedRepresentatives(moltenRepresentatives, moltenCandidateComparator());
            frozenCandidates = sortedRepresentatives(
                frozenRepresentatives,
                frozenCandidateComparator()
            );
            sculkCandidates = sortedRepresentatives(
                sculkRepresentatives,
                sculkCandidateComparator()
            ).stream().limit(MAX_ENDPOINT_ALTERNATIVES).toList();
            long passableSculk = sculkCandidates.stream().filter(RegionSample::passable).count();
            plugin.getLogger().info("[Queue][ValousPostProcess] event=region_extraction_complete world="
                + world.getName() + " lush_regions=" + lushCandidates.size()
                + " molten_regions=" + moltenCandidates.size()
                + " frozen_regions=" + frozenCandidates.size()
                + " sculk_targets=" + sculkCandidates.size()
                + " sculk_existing_air=" + passableSculk
                + " sculk_contained_carve_targets=" + sculkCandidates.size());
            stage = Stage.PLANNING;
        }

        private List<RegionSample> extractionSource(ExtractionPhase phase) {
            return switch (phase) {
                case LUSH -> samplesByBiome.get(ValousPrototypeValidator.LUSH_JUNGLE);
                case MOLTEN -> samplesByBiome.get(ValousPrototypeValidator.MOLTEN_CAVERNS);
                case FROZEN -> samplesByBiome.get(ValousPrototypeValidator.FROZEN_CAVES);
                case SCULK -> samplesByBiome.get(ValousPrototypeValidator.SCULK_ABYSS);
                case COMPLETE -> List.of();
            };
        }

        private void considerLushRepresentative(RegionSample sample) {
            if (sample.y() < -140 || sample.y() > 20
                || radius(sample.x(), sample.z()) < 154.0D
                || !lushCenterBounds.contains(sample.point())
                || !hasLandSurface(sample)) {
                return;
            }
            mergeRepresentative(lushRepresentatives, sample, lushCandidateComparator());
        }

        private void considerMoltenRepresentative(RegionSample sample) {
            if (sample.y() < -120 || sample.y() > 10
                || radius(sample.x(), sample.z()) < 154.0D
                || !moltenCenterBounds.contains(sample.point())
                || !hasLandSurface(sample)) {
                return;
            }
            mergeRepresentative(moltenRepresentatives, sample, moltenCandidateComparator());
        }

        private void considerFrozenRepresentative(RegionSample sample) {
            if (sample.y() < -150 || sample.y() > -40
                || radius(sample.x(), sample.z()) < 156.0D
                || !connectorCenterBounds.contains(sample.point())) {
                return;
            }
            mergeRepresentative(frozenRepresentatives, sample, frozenCandidateComparator());
        }

        private void considerSculkRepresentative(RegionSample sample) {
            if (sample.y() < -176 || sample.y() > -112
                || radius(sample.x(), sample.z()) < 180.0D
                || !sculkCenterBounds.contains(sample.point())
                || !sculkCarveEnvelopeContained(sample)) {
                return;
            }
            mergeRepresentative(sculkRepresentatives, sample, sculkCandidateComparator());
        }

        private void mergeRepresentative(
            Map<String, RegionSample> representatives,
            RegionSample sample,
            Comparator<RegionSample> comparator
        ) {
            representatives.merge(
                regionCell(sample),
                sample,
                (current, candidate) -> comparator.compare(candidate, current) < 0
                    ? candidate
                    : current
            );
        }

        private static List<RegionSample> sortedRepresentatives(
            Map<String, RegionSample> representatives,
            Comparator<RegionSample> comparator
        ) {
            return representatives.values().stream().sorted(comparator).toList();
        }

        private static String regionCell(RegionSample sample) {
            return Math.floorDiv(sample.x(), 64)
                + ":" + Math.floorDiv(sample.y(), 48)
                + ":" + Math.floorDiv(sample.z(), 64);
        }

        private void plan() {
            if (!planningInitialized) {
                if (planningStartedAtNanos == 0L) {
                    planningStartedAtNanos = System.nanoTime();
                    planningStartedAtMs = System.currentTimeMillis();
                    plugin.getLogger().info(
                        "[Queue][ValousPostProcess] event=pair_planning_started world="
                            + world.getName() + " lush_regions=" + lushCandidates.size()
                            + " molten_regions=" + moltenCandidates.size()
                            + " tick_budget_ms=" + (TICK_BUDGET_NANOS / 1_000_000L)
                    );
                }
                if (System.nanoTime() - planningStartedAtNanos >= PAIR_PREPARATION_TIMEOUT_NANOS) {
                    planningFailureCounts.merge("pair_preparation_timeout", 1, Integer::sum);
                    finishFailed("pair_preparation_timeout");
                    return;
                }
                preparePlanningTick();
                return;
            }

            if (planningAttemptCount >= MAX_TOTAL_PLANNING_ATTEMPTS
                || planningPairIndex >= regionPairs.size()) {
                finishFailed("bounded_route_replanning_exhausted");
                return;
            }
            if (System.nanoTime() - planningAttemptStartedAtNanos >= PLANNING_TIMEOUT_NANOS) {
                planningFailureCounts.merge("planning_timeout", 1, Integer::sum);
                finishFailed("planning_timeout");
                return;
            }

            RegionPair pair = regionPairs.get(planningPairIndex);
            RegionSample selectedSculk = sculkCandidates.get(planningSculkIndex);
            planningSculkIndex++;
            if (planningSculkIndex >= sculkCandidates.size()) {
                planningSculkIndex = 0;
                planningPairIndex++;
            }
            planningAttemptCount++;
            long attemptStarted = System.nanoTime();
            RegionSample upperSculkConnection = selectUpperConnection(
                lushCandidates,
                moltenCandidates,
                frozenCandidates,
                selectedSculk
            );
            RegionSample selectedFrozen = selectFrozenChamber(frozenCandidates, pair);
            String failure = buildCompletePlan(
                pair,
                selectedFrozen,
                selectedSculk,
                upperSculkConnection
            );
            long attemptDuration = System.nanoTime() - attemptStarted;
            long pathfindingDuration = Math.max(
                0L,
                attemptDuration - lastRouteVolumeNanos - lastEnvelopeValidationNanos
            );
            recordStageTick(WorkStage.PATHFINDING, pathfindingDuration, 1);
            recordStageTick(
                WorkStage.ROUTE_VOLUME_CONSTRUCTION,
                lastRouteVolumeNanos,
                lastRouteVolumeUnits
            );
            recordStageTick(
                WorkStage.ENVELOPE_VALIDATION,
                lastEnvelopeValidationNanos,
                carveShapes.size() + floorPatches.size()
            );
            Bounds attemptBounds = completePlanBounds();
            Set<Long> attemptChunks = chunksForCompletePlan();
            PlanningAttempt attempt = new PlanningAttempt(
                planningAttemptCount,
                regionId("lush", pair.lush()),
                pair.lush().point(),
                regionId("molten", pair.molten()),
                pair.molten().point(),
                regionId("sculk", selectedSculk),
                selectedSculk.point(),
                pair.estimatedConnectorLength(),
                attemptBounds,
                attemptChunks.size(),
                pair.borderClearance(),
                pair.cornucopiaClearance(),
                rejectionComponent(failure),
                failure == null ? "" : normalizeRejectionReason(failure),
                attemptDuration
            );
            planningAttempts.add(attempt);
            if (failure != null) {
                planningFailureCounts.merge(attempt.rejectionReason(), 1, Integer::sum);
                plugin.getLogger().info("[Queue][ValousPostProcess] event=plan_rejected world="
                    + world.getName() + " attempt=" + planningAttemptCount
                    + " reason=" + attempt.rejectionReason()
                    + " component=" + attempt.rejectionComponent()
                    + " lush=" + pointText(pair.lush().point())
                    + " molten=" + pointText(pair.molten().point())
                    + " sculk=" + pointText(selectedSculk.point())
                    + " duration_ms=" + formatMillis(attemptDuration));
                return;
            }
            planningCompletedAtMs = System.currentTimeMillis();
            stage = Stage.CARVING;
            plugin.getLogger().info("[Queue][ValousPostProcess] event=plan_complete world=" + world.getName()
                + " planning_ms=" + (planningCompletedAtMs - planningStartedAtMs)
                + " attempts=" + planningAttemptCount
                + " lush=" + pointText(lushTarget) + " molten=" + pointText(moltenTarget)
                + " frozen=" + pointText(frozenTarget)
                + " sculk=" + pointText(sculkTarget) + " shapes=" + carveShapes.size()
                + " floor_patches=" + floorPatches.size()
                + " connector_length=" + connectorSampledLength
                + " sculk_connector_length=" + sculkConnectorSampledLength
                + " complete_envelope=" + boundsText(completePlanBounds())
                + " expected_remove_ops=" + expectedRemovedOperations
                + " expected_place_ops=" + expectedPlacedOperations
                + " expected_fluid_candidates=" + expectedFluidCandidateOperations);
        }

        private void preparePlanningTick() {
            long tickStartedAt = System.nanoTime();
            int workUnits = 0;
            WorkStage workStage = planningPhase == PlanningPhase.PAIR_ENUMERATION
                ? WorkStage.PAIR_ENUMERATION
                : WorkStage.PAIR_SCORING;

            while (System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                switch (planningPhase) {
                    case LUSH_LAVA -> {
                        if (planningLavaIndex >= lushCandidates.size()) {
                            planningLavaIndex = 0;
                            planningPhase = PlanningPhase.MOLTEN_LAVA;
                            continue;
                        }
                        RegionSample sample = lushCandidates.get(planningLavaIndex++);
                        lushLavaExposure.put(sample, lavaExposure(sample));
                        workUnits++;
                    }
                    case MOLTEN_LAVA -> {
                        if (planningLavaIndex >= moltenCandidates.size()) {
                            planningLavaIndex = 0;
                            planningPhase = PlanningPhase.PAIR_ENUMERATION;
                            workStage = WorkStage.PAIR_ENUMERATION;
                            continue;
                        }
                        RegionSample sample = moltenCandidates.get(planningLavaIndex++);
                        moltenLavaExposure.put(sample, lavaExposure(sample));
                        workUnits++;
                    }
                    case PAIR_ENUMERATION -> {
                        if (lushCandidates.isEmpty()
                            || moltenCandidates.isEmpty()
                            || planningLushPairIndex >= lushCandidates.size()) {
                            completePairPreparation();
                            recordStageTick(
                                WorkStage.PAIR_ENUMERATION,
                                System.nanoTime() - tickStartedAt,
                                workUnits
                            );
                            return;
                        }
                        RegionSample lush = lushCandidates.get(planningLushPairIndex);
                        RegionSample molten = moltenCandidates.get(planningMoltenPairIndex++);
                        if (planningMoltenPairIndex >= moltenCandidates.size()) {
                            planningMoltenPairIndex = 0;
                            planningLushPairIndex++;
                        }
                        RegionPair pair = evaluateRegionPair(lush, molten);
                        if (pair != null) {
                            validRegionPairCount++;
                            retainBestRegionPair(pair);
                        }
                        workUnits++;
                    }
                    case COMPLETE -> {
                        completePairPreparation();
                        return;
                    }
                }
            }
            recordStageTick(workStage, System.nanoTime() - tickStartedAt, workUnits);
        }

        private void completePairPreparation() {
            planningPhase = PlanningPhase.COMPLETE;
            bestRegionPairs.sort(regionPairComparator());
            regionPairs = List.copyOf(bestRegionPairs);
            planningInitialized = true;
            planningAttemptStartedAtNanos = System.nanoTime();
            plugin.getLogger().info(
                "[Queue][ValousPostProcess] event=pair_planning_complete world="
                    + world.getName() + " valid_pairs=" + validRegionPairCount
                    + " retained_pairs=" + regionPairs.size()
                    + " preparation_ms=" + (System.currentTimeMillis() - planningStartedAtMs)
            );
            if (regionPairs.isEmpty() || sculkCandidates.isEmpty()) {
                finishFailed(regionPairs.isEmpty()
                    ? "lush_molten_region_pair_unavailable"
                    : "sculk_target_unavailable");
            }
        }

        private RegionPair evaluateRegionPair(RegionSample lush, RegionSample molten) {
            double horizontalDistance = horizontalDistance(molten, lush);
            if (horizontalDistance < 56.0D || horizontalDistance > 260.0D) {
                return rejectRegionPair("distance_out_of_range");
            }
            if (((long) molten.x() * lush.x()) + ((long) molten.z() * lush.z()) <= 0L) {
                return rejectRegionPair("opposite_center_hemisphere");
            }
            Bounds minimumConnectorEnvelope = new Bounds(
                new Point(
                    Math.min(lush.x(), molten.x()) - CONNECTOR_CENTER_REACH,
                    Math.min(lush.y(), molten.y()) - CONNECTOR_CENTER_REACH,
                    Math.min(lush.z(), molten.z()) - CONNECTOR_CENTER_REACH
                ),
                new Point(
                    Math.max(lush.x(), molten.x()) + CONNECTOR_CENTER_REACH,
                    Math.max(lush.y(), molten.y()) + CONNECTOR_CENTER_REACH,
                    Math.max(lush.z(), molten.z()) + CONNECTOR_CENTER_REACH
                )
            );
            if (!safeEditBounds.contains(minimumConnectorEnvelope)
                || !playableBounds.contains(minimumConnectorEnvelope)
                || !pregeneratedBounds.contains(minimumConnectorEnvelope)) {
                return rejectRegionPair("endpoint_envelope_out_of_bounds");
            }

            List<Point> connector = ValousMorphologyGeometry.broadCurve(
                lush.point(),
                molten.point(),
                world.getSeed() ^ 0x4c5553484d4f4cL,
                1.0D
            );
            if (connector.isEmpty()
                || !pathEnvelopeInside(connector, CONNECTOR_CENTER_REACH, connectorCenterBounds)) {
                return rejectRegionPair("broad_connector_envelope_out_of_bounds");
            }
            Point centralCenter = connector.get(connector.size() / 2);
            List<Ellipsoid> morphologyShapes = new ArrayList<>();
            morphologyShapes.addAll(irregularChamberShapes(
                lush.point(), 34, 18, 31, RoutePalette.LUSH, 11L
            ));
            morphologyShapes.addAll(irregularChamberShapes(
                centralCenter, 42, 20, 36, RoutePalette.TRANSITION, 29L
            ));
            morphologyShapes.addAll(irregularChamberShapes(
                molten.point(), 32, 16, 30, RoutePalette.MOLTEN, 31L
            ));
            for (Ellipsoid shape : morphologyShapes) {
                if (!editEnvelopeInside(shape)) {
                    return rejectRegionPair("broad_chamber_envelope_out_of_bounds");
                }
            }
            double borderClearance = Math.min(
                borderClearance(lush.point(), lushCenterBounds),
                borderClearance(molten.point(), moltenCenterBounds)
            );
            double cornucopiaClearance = morphologyShapes.stream()
                .mapToDouble(shape -> radius(shape.center().x(), shape.center().z())
                    - Math.max(shape.radiusX(), shape.radiusZ()))
                .min()
                .orElse(0.0D);
            int estimatedLength = (int) Math.round(Math.sqrt(
                (horizontalDistance * horizontalDistance)
                    + Math.pow(molten.y() - lush.y(), 2)
            ));
            long estimatedExcavation = Math.max(1L, estimatedLength) * 201L;
            double airScore = (lush.passable() ? 24.0D : 0.0D)
                + (molten.passable() ? 24.0D : 0.0D);
            double lavaPenalty = lushLavaExposure.getOrDefault(lush, 0.0D)
                + moltenLavaExposure.getOrDefault(molten, 0.0D);
            double score = (borderClearance * 2.0D)
                + cornucopiaClearance
                + airScore
                - Math.abs(horizontalDistance - 140.0D)
                - (Math.abs(molten.y() - lush.y()) * 0.35D)
                - (estimatedExcavation / 4_000.0D)
                - (lavaPenalty * 8.0D);
            return new RegionPair(
                lush,
                molten,
                score,
                estimatedLength,
                estimatedExcavation,
                borderClearance,
                cornucopiaClearance,
                lavaPenalty,
                List.copyOf(connector),
                centralCenter
            );
        }

        private RegionPair rejectRegionPair(String reason) {
            pairRejectionCounts.merge(reason, 1, Integer::sum);
            return null;
        }

        private void retainBestRegionPair(RegionPair candidate) {
            if (bestRegionPairs.size() < MAX_REGION_PAIR_COMBINATIONS) {
                bestRegionPairs.add(candidate);
                return;
            }
            Comparator<RegionPair> comparator = regionPairComparator();
            int worstIndex = 0;
            for (int index = 1; index < bestRegionPairs.size(); index++) {
                if (comparator.compare(bestRegionPairs.get(index), bestRegionPairs.get(worstIndex)) > 0) {
                    worstIndex = index;
                }
            }
            if (comparator.compare(candidate, bestRegionPairs.get(worstIndex)) < 0) {
                bestRegionPairs.set(worstIndex, candidate);
            }
        }

        private String buildCompletePlan(
            RegionPair pair,
            RegionSample selectedFrozen,
            RegionSample selectedSculk,
            RegionSample upperSculkConnection
        ) {
            resetPlannedEdits();
            long routeVolumeStarted = System.nanoTime();
            if (upperSculkConnection == null) {
                lastRouteVolumeNanos = System.nanoTime() - routeVolumeStarted;
                return "sculk_upper_connection_target_unavailable";
            }
            if (selectedFrozen == null) {
                lastRouteVolumeNanos = System.nanoTime() - routeVolumeStarted;
                return "frozen_chamber_target_unavailable";
            }
            lushTarget = pair.lush().point();
            moltenTarget = pair.molten().point();
            frozenTarget = selectedFrozen.point();
            sculkTarget = selectedSculk.point();
            sculkUpperTarget = upperSculkConnection.point();
            lushSurface = surfacePoint(lushTarget);
            moltenSurface = surfaceReturnPoint(moltenTarget);
            if (lushSurface == null || moltenSurface == null) {
                return "surface_anchor_unavailable";
            }

            int lushShapeStart = carveShapes.size();
            int lushFloorStart = floorPatches.size();
            List<Point> lushAxis = ValousMorphologyGeometry.verticalBreachAxis(
                new Point(lushSurface.x(), lushSurface.y() - 1, lushSurface.z()),
                new Point(lushTarget.x(), lushTarget.y() + 5, lushTarget.z()),
                world.getSeed()
            );
            ValousMorphologyGeometry.EntranceMetrics entranceMetrics =
                ValousMorphologyGeometry.entranceMetrics(lushAxis);
            if (!entranceMetrics.passed()) {
                return "lush_entrance_morphology_rejected";
            }
            addVerticalBreach(lushAxis, RoutePalette.LUSH);
            addIrregularChamberCluster(lushTarget, 34, 18, 31, RoutePalette.LUSH, 11L);
            lushEntrancePath = List.copyOf(lushAxis);
            connectionQuality.put("lush_entrance", quality(
                lushAxis, 26, 32, 10, "vertical_breach"
            ));
            lushEntranceBounds = boundsForEdits(
                lushShapeStart,
                carveShapes.size(),
                lushFloorStart,
                floorPatches.size()
            );

            int connectorShapeStart = carveShapes.size();
            int connectorFloorStart = floorPatches.size();
            List<Point> connector = pair.connectorPath();
            if (connector.isEmpty()
                || !pathEnvelopeInside(connector, CONNECTOR_CENTER_REACH, connectorCenterBounds)) {
                return "lush_to_molten_path_crosses_safe_bounds";
            }
            connectorPath = List.copyOf(connector);
            addBroadCavernThroat(connector, 14, 22, RoutePalette.TRANSITION, 23L);
            connectionQuality.put("lush_to_molten", quality(
                connector, 28, 36, 18, "cavern_throat"
            ));
            Point centralCenter = pair.centralCenter();
            addIrregularChamberCluster(centralCenter, 42, 20, 36, RoutePalette.TRANSITION, 29L);
            addIrregularChamberCluster(moltenTarget, 32, 16, 30, RoutePalette.MOLTEN, 31L);
            connectorBounds = boundsForEdits(
                connectorShapeStart,
                carveShapes.size(),
                connectorFloorStart,
                floorPatches.size()
            );
            connectorSampledLength = sampledLength(connector);

            int moltenShapeStart = carveShapes.size();
            int moltenFloorStart = floorPatches.size();
            List<Point> moltenReturn = ValousMorphologyGeometry.broadCurve(
                new Point(moltenTarget.x(), moltenTarget.y() + 2, moltenTarget.z()),
                new Point(moltenSurface.x(), moltenSurface.y() - 1, moltenSurface.z()),
                world.getSeed() ^ 0x52455455524eL,
                1.35D
            );
            if (moltenReturn.isEmpty()
                || !pathEnvelopeInside(moltenReturn, 7, safeEditBounds.inset(7, 0))) {
                return "molten_return_crosses_safe_bounds";
            }
            addTerracedReturn(moltenReturn, RoutePalette.MOLTEN);
            moltenReturnPath = List.copyOf(moltenReturn);
            connectionQuality.put("molten_return", quality(
                moltenReturn, 12, 12, 10, "terraced_return"
            ));
            moltenReturnBounds = boundsForEdits(
                moltenShapeStart,
                carveShapes.size(),
                moltenFloorStart,
                floorPatches.size()
            );

            int frozenFloorY = clamp(frozenTarget.y(), -135, -60);
            Point frozenCenter = new Point(frozenTarget.x(), frozenFloorY + 7, frozenTarget.z());
            addIrregularChamberCluster(frozenCenter, 38, 18, 34, RoutePalette.FROZEN, 41L);
            List<Point> frozenConnection = ValousMorphologyGeometry.broadCurve(
                centralCenter,
                frozenCenter,
                world.getSeed() ^ 0x46524f5a454eL,
                0.85D
            );
            if (frozenConnection.isEmpty()
                || !pathEnvelopeInside(frozenConnection, 18, connectorCenterBounds)) {
                return "frozen_connection_crosses_safe_bounds";
            }
            frozenConnectorPath = List.copyOf(frozenConnection);
            addBroadCavernThroat(frozenConnection, 12, 19, RoutePalette.FROZEN, 43L);
            connectionQuality.put("central_to_frozen", quality(
                frozenConnection, 24, 31, 16, "cavern_throat"
            ));
            fluidSealPatches.add(new FluidSealPatch(
                frozenCenter,
                31,
                34,
                RoutePalette.FROZEN.floor(frozenCenter)
            ));
            floorPatches.add(new FloorPatch(
                new Point(frozenCenter.x(), frozenFloorY, frozenCenter.z()),
                17,
                Material.PACKED_ICE
            ));

            int sculkShapeStart = carveShapes.size();
            int sculkFloorStart = floorPatches.size();
            int sculkFloorY = clamp(sculkTarget.y(), -165, -120);
            Point chamberCenter = new Point(sculkTarget.x(), sculkFloorY + 12, sculkTarget.z());
            addIrregularChamberCluster(chamberCenter, 44, 22, 40, RoutePalette.SCULK, 53L);
            fluidSealPatches.add(new FluidSealPatch(
                chamberCenter,
                38,
                42,
                RoutePalette.SCULK.floor(chamberCenter)
            ));
            addSculkFloor(chamberCenter, 34, sculkFloorY);
            List<Point> sculkConnector = ValousMorphologyGeometry.broadCurve(
                new Point(sculkTarget.x(), sculkFloorY + 2, sculkTarget.z()),
                sculkUpperTarget,
                world.getSeed() ^ 0x5343554c4bL,
                0.65D
            );
            if (sculkConnector.isEmpty()
                || !pathEnvelopeInside(sculkConnector, 20, connectorCenterBounds)) {
                return "sculk_connector_crosses_safe_bounds";
            }
            sculkConnectorPath = List.copyOf(sculkConnector);
            addBroadCavernThroat(sculkConnector, 13, 21, RoutePalette.SCULK, 59L);
            connectionQuality.put("sculk_descent", quality(
                sculkConnector, 26, 34, 18, "major_descent"
            ));
            sculkConnectorSampledLength = sampledLength(sculkConnector);
            sculkChamberBounds = boundsForEdits(
                sculkShapeStart,
                carveShapes.size(),
                sculkFloorStart,
                floorPatches.size()
            );

            lastRouteVolumeNanos = System.nanoTime() - routeVolumeStarted;
            lastRouteVolumeUnits = carveShapes.size() + floorPatches.size() + fluidSealPatches.size();
            long envelopeStarted = System.nanoTime();
            String boundsFailure = validateCompletePlan();
            lastEnvelopeValidationNanos = System.nanoTime() - envelopeStarted;
            if (boundsFailure != null) {
                return boundsFailure;
            }
            calculateExpectedEditOperations();
            return null;
        }

        private void carveTick(long tickStartedAt) {
            int processed = 0;
            while (shapeIndex < carveShapes.size()
                && processed < MAX_EDIT_BLOCKS_PER_TICK
                && System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                EditShape shape = carveShapes.get(shapeIndex);
                if (shapeCursor == null) {
                    shapeCursor = new ShapeCursor(shape.bounds());
                }
                Point point = shapeCursor.next();
                if (point == null) {
                    shapeIndex++;
                    shapeCursor = null;
                    continue;
                }
                processed++;
                if (!shape.contains(point.x(), point.y(), point.z())) {
                    continue;
                }
                if (!editTo(point.x(), point.y(), point.z(), Material.AIR)) {
                    return;
                }
            }
            tickWorkUnits = processed;
            if (shapeIndex >= carveShapes.size()) {
                stage = Stage.FLUID_SEALING;
            }
        }

        private void fluidSealTick(long tickStartedAt) {
            int processed = 0;
            while (fluidSealIndex < fluidSealPatches.size()
                && processed < MAX_EDIT_BLOCKS_PER_TICK
                && System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                FluidSealPatch patch = fluidSealPatches.get(fluidSealIndex);
                if (fluidSealCursor == null) {
                    fluidSealCursor = new FluidSealCursor(patch);
                }
                Point point = fluidSealCursor.next();
                if (point == null) {
                    fluidSealIndex++;
                    fluidSealCursor = null;
                    continue;
                }
                processed++;
                Block block = world.getBlockAt(point.x(), point.y(), point.z());
                if (block.getType() != Material.WATER && block.getType() != Material.LAVA) {
                    continue;
                }
                if (!editTo(point.x(), point.y(), point.z(), patch.material())) {
                    return;
                }
            }
            tickWorkUnits = processed;
            if (fluidSealIndex >= fluidSealPatches.size()) {
                stage = Stage.FLOORING;
            }
        }

        private void floorTick(long tickStartedAt) {
            int processed = 0;
            while (floorIndex < floorPatches.size()
                && processed < MAX_EDIT_BLOCKS_PER_TICK
                && System.nanoTime() - tickStartedAt < TICK_BUDGET_NANOS) {
                FloorPatch patch = floorPatches.get(floorIndex);
                if (floorCursor == null) {
                    floorCursor = new FloorCursor(patch);
                }
                Point point = floorCursor.next();
                if (point == null) {
                    floorIndex++;
                    floorCursor = null;
                    continue;
                }
                processed++;
                if (!editTo(point.x(), point.y(), point.z(), patch.material())) {
                    return;
                }
            }
            tickWorkUnits = processed;
            if (floorIndex >= floorPatches.size()) {
                processingCompletedAtMs = System.currentTimeMillis();
                stage = Stage.WRITING;
            }
        }

        private boolean editTo(int x, int y, int z, Material target) {
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1 || !loadedAndInside(x, z)) {
                finishFailed("edit_crossed_missing_chunk_or_world_bounds");
                return false;
            }
            Block block = world.getBlockAt(x, y, z);
            Material current = block.getType();
            if (current == target) {
                return true;
            }
            if (current == Material.BEDROCK) {
                return true;
            }
            if (current == Material.WATER || current == Material.LAVA) {
                fluidsRemoved++;
            }
            if (target.isAir()) {
                blocksRemoved++;
            } else {
                blocksPlaced++;
            }
            block.setType(target, false);
            modifiedChunkKeys.add(chunkKey(x >> 4, z >> 4));
            checksum.update((x + "," + y + "," + z + ":" + current + ">" + target + "\n")
                .getBytes(StandardCharsets.UTF_8));
            return true;
        }

        private void finishSuccess() {
            Result result = result(true, "");
            try {
                writeManifest(result);
            } catch (IOException ex) {
                finishFailed("manifest_write_failed:" + ex.getClass().getSimpleName());
                return;
            }
            stage = Stage.COMPLETE;
            if (task != null) {
                task.cancel();
            }
            releaseChunkTickets();
            plugin.getLogger().info("[Queue][ValousPostProcess] event=complete world=" + world.getName()
                + " status=pass processing_ms=" + result.processingMs()
                + " max_tick_ms=" + String.format(Locale.ROOT, "%.3f", result.maxTickNanos() / 1_000_000.0D)
                + " modified_chunks=" + result.modifiedChunks().size()
                + " blocks_removed=" + result.blocksRemoved()
                + " blocks_placed=" + result.blocksPlaced()
                + " fluids_removed=" + result.fluidsRemoved()
                + " checksum=" + result.idempotencyChecksum());
            completion.accept(result);
        }

        private void finishFailed(String reason) {
            if (stage == Stage.COMPLETE) {
                return;
            }
            failureReason = reason == null ? "unknown" : reason;
            processingCompletedAtMs = System.currentTimeMillis();
            Result result = result(false, failureReason);
            try {
                writeManifest(result);
            } catch (IOException ignored) {
                // The original failure is more useful than a secondary report failure.
            }
            stage = Stage.COMPLETE;
            if (task != null) {
                task.cancel();
            }
            releaseChunkTickets();
            plugin.getLogger().warning("[Queue][ValousPostProcess] event=failed world=" + world.getName()
                + " reason=" + failureReason);
            completion.accept(result);
        }

        private Result result(boolean passed, String reason) {
            long now = System.currentTimeMillis();
            long scanMs = scanCompletedAtMs == 0 ? now - startedAtMs : scanCompletedAtMs - startedAtMs;
            long planningMs = planningCompletedAtMs == 0 || scanCompletedAtMs == 0
                ? 0L
                : planningCompletedAtMs - scanCompletedAtMs;
            long processingMs = planningCompletedAtMs == 0
                ? 0L
                : Math.max(0L, (processingCompletedAtMs == 0 ? now : processingCompletedAtMs) - planningCompletedAtMs);
            Path reportDirectory = settings.persistentReportRoot().resolve(world.getName());
            Path manifest = world.getWorldFolder().toPath().resolve("valous-route-postprocess.json");
            Map<String, Integer> volumes = new LinkedHashMap<>();
            samplesByBiome.forEach((key, value) -> volumes.put(key, value.size()));
            return new Result(
                passed,
                reason,
                scanMs,
                planningMs,
                processingMs,
                maxTickNanos,
                lushTarget,
                moltenTarget,
                frozenTarget,
                sculkTarget,
                lushSurface,
                moltenSurface,
                lushEntranceBounds,
                connectorBounds,
                moltenReturnBounds,
                sculkChamberBounds,
                lushEntrancePath,
                connectorPath,
                frozenConnectorPath,
                moltenReturnPath,
                sculkConnectorPath,
                connectorSampledLength,
                sculkConnectorSampledLength,
                modifiedChunkKeys.stream()
                    .map(key -> new ChunkPoint((int) (key >> 32), (int) (long) key))
                    .toList(),
                blocksRemoved,
                blocksPlaced,
                fluidsRemoved,
                hex(checksum.digest()),
                Map.copyOf(volumes),
                Map.copyOf(connectionQuality),
                manifest,
                reportDirectory.resolve("route-postprocess.json")
            );
        }

        private void writeManifest(Result result) throws IOException {
            JsonObject json = result.toJson(world.getName(), world.getSeed(), settings);
            JsonObject bounds = new JsonObject();
            bounds.add("pregenerated", pregeneratedBounds.toJson());
            bounds.add("playable", playableBounds.toJson());
            bounds.add("safe_edit", safeEditBounds.toJson());
            bounds.add("lush_center", lushCenterBounds.toJson());
            bounds.add("connector_centerline", connectorCenterBounds.toJson());
            bounds.add("molten_center", moltenCenterBounds.toJson());
            bounds.add("sculk_center", sculkCenterBounds.toJson());
            bounds.add("validation", validationBounds.toJson());
            JsonObject radii = new JsonObject();
            radii.addProperty("lush_max_horizontal_reach", LUSH_CENTER_REACH);
            radii.addProperty("connector_carve_radius", CONNECTOR_CENTER_REACH);
            radii.addProperty("molten_max_horizontal_reach", MOLTEN_CENTER_REACH);
            radii.addProperty("sculk_chamber_radius", SCULK_CENTER_REACH);
            bounds.add("component_radii", radii);
            json.add("formal_bounds", bounds);

            JsonObject expansion = new JsonObject();
            expansion.addProperty("initial_scan_chunks",
                settings.chunkHandoff() == null || settings.chunkHandoff().lease() == null
                ? 0
                : settings.chunkHandoff().lease().heldCount());
            expansion.addProperty("additional_chunks_acquired", additionalChunkKeys.size());
            expansion.addProperty("required_chunks", expansionExpectedChunkCount);
            expansion.addProperty("prepared_chunks", expansionPreparedChunkCount);
            expansion.addProperty(
                "preparation_ms",
                expansionPreparationCompletedAtMs <= 0L
                    ? 0L
                    : expansionPreparationCompletedAtMs - expansionPreparationStartedAtMs
            );
            expansion.addProperty("peak_held_chunks", peakHeldChunks);
            expansion.addProperty("generation_allowed", false);
            json.add("ledger_backed_scan_expansion", expansion);

            JsonObject planning = new JsonObject();
            planning.addProperty("attempt_count", planningAttemptCount);
            planning.addProperty("attempt_limit", MAX_TOTAL_PLANNING_ATTEMPTS);
            planning.addProperty("timeout_ms", PLANNING_TIMEOUT_NANOS / 1_000_000L);
            planning.addProperty("lush_region_count", lushCandidates.size());
            planning.addProperty("molten_region_count", moltenCandidates.size());
            planning.addProperty("sculk_region_count", sculkCandidates.size());
            planning.addProperty(
                "sculk_existing_passable_air_count",
                sculkCandidates.stream().filter(RegionSample::passable).count()
            );
            planning.addProperty("sculk_contained_carve_target_count", sculkCandidates.size());
            planning.addProperty("valid_global_pair_count", validRegionPairCount);
            planning.addProperty("retained_pair_count", regionPairs.size());
            JsonObject pairRejections = new JsonObject();
            pairRejectionCounts.forEach(pairRejections::addProperty);
            planning.add("pair_rejection_counts", pairRejections);
            planning.add("lush_regions", regionsToJson(lushCandidates, "lush"));
            planning.add("molten_regions", regionsToJson(moltenCandidates, "molten"));
            planning.add("sculk_regions", regionsToJson(sculkCandidates, "sculk"));
            JsonArray attempts = new JsonArray();
            planningAttempts.forEach(attempt -> attempts.add(attempt.toJson()));
            planning.add("attempts", attempts);
            JsonObject failures = new JsonObject();
            planningFailureCounts.forEach(failures::addProperty);
            planning.add("failure_counts", failures);
            planning.add("complete_edit_envelope",
                completePlanBounds() == null ? new JsonObject() : completePlanBounds().toJson());
            JsonObject expected = new JsonObject();
            expected.addProperty("removed_block_operations_upper_estimate", expectedRemovedOperations);
            expected.addProperty("placed_block_operations_upper_estimate", expectedPlacedOperations);
            expected.addProperty(
                "fluid_change_candidates_upper_estimate",
                expectedFluidCandidateOperations
            );
            expected.addProperty("modified_chunk_count", chunksForCompletePlan().size());
            planning.add("expected_edits", expected);
            planning.addProperty("atomic_validation_passed",
                planningCompletedAtMs > 0 && failureReason.isEmpty());
            json.add("planning", planning);
            JsonObject stageTicks = new JsonObject();
            maxTickNanosByStage.forEach((stage, nanos) ->
                stageTicks.addProperty(
                    stage.name().toLowerCase(Locale.ROOT),
                    nanos / 1_000_000.0D
                )
            );
            json.add("max_tick_ms_by_stage", stageTicks);
            JsonObject detailedStageTimings = new JsonObject();
            stageTimings.forEach((workStage, timing) ->
                detailedStageTimings.add(workStage.id, timing.toJson())
            );
            json.add("stage_timings", detailedStageTimings);
            json.addProperty("hard_stage_tick_limit_ms",
                HARD_STAGE_TICK_LIMIT_NANOS / 1_000_000.0D);
            json.addProperty("hard_stage_tick_limit_exceeded", hardTickLimitExceeded);
            if (firstScanFailure != null) {
                JsonObject scanFailure = new JsonObject();
                scanFailure.add("block", new Point(
                    firstScanFailure.x(),
                    firstScanFailure.y(),
                    firstScanFailure.z()
                ).toJson());
                int chunkX = firstScanFailure.x() >> 4;
                int chunkZ = firstScanFailure.z() >> 4;
                long key = ValousPregenerationLedger.chunkKey(chunkX, chunkZ);
                JsonArray chunk = new JsonArray();
                chunk.add(chunkX);
                chunk.add(chunkZ);
                scanFailure.add("chunk", chunk);
                scanFailure.addProperty("chunk_key", Long.toUnsignedString(key));
                scanFailure.addProperty("present_in_completed_ledger",
                    settings.pregenerationLedger().contains(key));
                scanFailure.addProperty("paper_generated", world.isChunkGenerated(chunkX, chunkZ));
                scanFailure.addProperty("paper_loaded", world.isChunkLoaded(chunkX, chunkZ));
                json.add("scan_failure", scanFailure);
            }
            Files.createDirectories(result.reportPath().getParent());
            Files.writeString(result.manifestPath(), GSON.toJson(json), StandardCharsets.UTF_8);
            Files.writeString(result.reportPath(), GSON.toJson(json), StandardCharsets.UTF_8);
        }

        private Result readExistingManifest() {
            Path manifest = world.getWorldFolder().toPath().resolve("valous-route-postprocess.json");
            if (!Files.isRegularFile(manifest)) {
                return null;
            }
            try {
                JsonObject root = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                if (!"pass".equals(root.get("status").getAsString())
                    || root.get("seed").getAsLong() != world.getSeed()
                    || !VERSION.equals(root.get("post_processing_version").getAsString())
                    || !settings.generationVersion().equals(root.get("generation_version").getAsString())
                    || !settings.datapackVersion().equals(root.get("datapack_version").getAsString())
                    || !root.has("selection_policy")
                    || !settings.selectionPolicy().equals(
                        root.get("selection_policy").getAsString())) {
                    return null;
                }
                JsonObject timing = root.getAsJsonObject("timing");
                JsonObject regions = root.getAsJsonObject("selected_regions");
                JsonObject routes = root.getAsJsonObject("routes");
                JsonObject edits = root.getAsJsonObject("edits");
                Map<String, Integer> volumes = new LinkedHashMap<>();
                root.getAsJsonObject("biome_volume_samples").entrySet().forEach(
                    entry -> volumes.put(entry.getKey(), entry.getValue().getAsInt())
                );
                List<ChunkPoint> chunks = new ArrayList<>();
                Map<String, ConnectionQuality> qualities = new LinkedHashMap<>();
                if (root.has("morphology")) {
                    JsonObject connections = root.getAsJsonObject("morphology")
                        .getAsJsonObject("connections");
                    if (connections != null) {
                        connections.entrySet().forEach(entry -> {
                            JsonObject value = entry.getValue().getAsJsonObject();
                            qualities.put(entry.getKey(), new ConnectionQuality(
                                value.get("minimum_width").getAsInt(),
                                value.get("median_width").getAsInt(),
                                value.get("minimum_height").getAsInt(),
                                value.get("sampled_length").getAsInt(),
                                value.get("natural_air_ratio").getAsDouble(),
                                value.get("classification").getAsString()
                            ));
                        });
                    }
                }
                JsonArray chunkValues = edits.getAsJsonArray("modified_chunks");
                if (chunkValues != null) {
                    for (JsonElement element : chunkValues) {
                        JsonArray value = element.getAsJsonArray();
                        chunks.add(new ChunkPoint(value.get(0).getAsInt(), value.get(1).getAsInt()));
                    }
                }
                Path report = settings.persistentReportRoot()
                    .resolve(world.getName())
                    .resolve("route-postprocess.json");
                return new Result(
                    true,
                    "",
                    timing.get("scan_ms").getAsLong(),
                    timing.get("planning_ms").getAsLong(),
                    timing.get("processing_ms").getAsLong(),
                    Math.round(timing.get("max_tick_ms").getAsDouble() * 1_000_000.0D),
                    readPoint(regions.getAsJsonObject("lush").getAsJsonArray("center")),
                    readPoint(regions.getAsJsonObject("molten").getAsJsonArray("center")),
                    regions.has("frozen")
                        ? readPoint(regions.getAsJsonObject("frozen").getAsJsonArray("center"))
                        : null,
                    readPoint(regions.getAsJsonObject("sculk").getAsJsonArray("center")),
                    readPoint(routes.getAsJsonObject("lush_entrance").getAsJsonArray("start")),
                    readPoint(routes.getAsJsonObject("molten_return").getAsJsonArray("end")),
                    readBounds(routes.getAsJsonObject("lush_entrance").getAsJsonObject("bounds")),
                    readBounds(routes.getAsJsonObject("lush_to_molten").getAsJsonObject("bounds")),
                    readBounds(routes.getAsJsonObject("molten_return").getAsJsonObject("bounds")),
                    readBounds(routes.getAsJsonObject("sculk_connection").getAsJsonObject("bounds")),
                    readPath(routes.getAsJsonObject("lush_entrance").getAsJsonArray("path")),
                    readPath(routes.getAsJsonObject("lush_to_molten").getAsJsonArray("path")),
                    routes.has("frozen_connection")
                        ? readPath(routes.getAsJsonObject("frozen_connection").getAsJsonArray("path"))
                        : List.of(),
                    readPath(routes.getAsJsonObject("molten_return").getAsJsonArray("path")),
                    readPath(routes.getAsJsonObject("sculk_connection").getAsJsonArray("path")),
                    routes.getAsJsonObject("lush_to_molten").get("sampled_length").getAsInt(),
                    routes.getAsJsonObject("sculk_connection").get("sampled_length").getAsInt(),
                    List.copyOf(chunks),
                    edits.get("blocks_removed").getAsLong(),
                    edits.get("blocks_placed").getAsLong(),
                    edits.get("fluids_removed").getAsLong(),
                    edits.get("idempotency_checksum").getAsString(),
                    Map.copyOf(volumes),
                    Map.copyOf(qualities),
                    manifest,
                    report
                );
            } catch (RuntimeException | IOException ex) {
                plugin.getLogger().warning("[Queue][ValousPostProcess] event=manifest_reuse_failed world="
                    + world.getName() + " reason=" + ex.getClass().getSimpleName());
                return null;
            }
        }

        private static Point readPoint(JsonArray value) {
            if (value == null || value.size() != 3) {
                return null;
            }
            return new Point(value.get(0).getAsInt(), value.get(1).getAsInt(), value.get(2).getAsInt());
        }

        private static Bounds readBounds(JsonObject value) {
            if (value == null) {
                return null;
            }
            Point min = readPoint(value.getAsJsonArray("min"));
            Point max = readPoint(value.getAsJsonArray("max"));
            return min == null || max == null ? null : new Bounds(min, max);
        }

        private static List<Point> readPath(JsonArray values) {
            if (values == null) {
                return List.of();
            }
            List<Point> points = new ArrayList<>(values.size());
            for (JsonElement value : values) {
                Point point = readPoint(value.getAsJsonArray());
                if (point != null) {
                    points.add(point);
                }
            }
            return List.copyOf(points);
        }

        private Comparator<RegionSample> lushCandidateComparator() {
            return Comparator
                .comparing((RegionSample sample) -> !sample.passable())
                .thenComparingDouble(sample -> -borderClearance(sample.point(), lushCenterBounds))
                .thenComparingDouble(sample -> Math.abs(radius(sample.x(), sample.z()) - 180.0D))
                .thenComparingInt(sample -> Math.abs(sample.y() + 64))
                .thenComparingInt(RegionSample::x)
                .thenComparingInt(RegionSample::z);
        }

        private Comparator<RegionSample> moltenCandidateComparator() {
            return Comparator
                .comparing((RegionSample sample) -> !sample.passable())
                .thenComparingDouble(sample -> -borderClearance(sample.point(), moltenCenterBounds))
                .thenComparingInt(sample -> Math.abs(sample.y() + 72))
                .thenComparingInt(RegionSample::x)
                .thenComparingInt(RegionSample::z);
        }

        private Comparator<RegionSample> frozenCandidateComparator() {
            return Comparator
                .comparing((RegionSample sample) -> !sample.passable())
                .thenComparingDouble(sample -> -borderClearance(sample.point(), connectorCenterBounds))
                .thenComparingInt(sample -> Math.abs(sample.y() + 96))
                .thenComparingInt(RegionSample::x)
                .thenComparingInt(RegionSample::z);
        }

        private Comparator<RegionPair> regionPairComparator() {
            return Comparator
                .comparingDouble(RegionPair::score)
                .reversed()
                .thenComparingInt(pair -> pair.lush().x())
                .thenComparingInt(pair -> pair.lush().z())
                .thenComparingInt(pair -> pair.molten().x())
                .thenComparingInt(pair -> pair.molten().z());
        }

        private Comparator<RegionSample> sculkCandidateComparator() {
            return Comparator
                .comparing((RegionSample sample) -> !sample.passable())
                .thenComparingDouble(sample -> -borderClearance(sample.point(), sculkCenterBounds))
                .thenComparingInt(sample -> Math.abs(sample.y() + 145))
                .thenComparingDouble(sample -> Math.abs(radius(sample.x(), sample.z()) - 185.0D))
                .thenComparingInt(RegionSample::x)
                .thenComparingInt(RegionSample::z);
        }

        private boolean sculkCarveEnvelopeContained(RegionSample sample) {
            int floorY = clamp(sample.y(), -165, -120);
            Point chamberCenter = new Point(sample.x(), floorY + 22, sample.z());
            Bounds chamber = new Bounds(
                new Point(
                    chamberCenter.x() - SCULK_CENTER_REACH,
                    chamberCenter.y() - 22,
                    chamberCenter.z() - SCULK_CENTER_REACH
                ),
                new Point(
                    chamberCenter.x() + SCULK_CENTER_REACH,
                    chamberCenter.y() + 22,
                    chamberCenter.z() + SCULK_CENTER_REACH
                )
            );
            return safeEditBounds.contains(chamber)
                && playableBounds.contains(chamber)
                && pregeneratedBounds.contains(chamber);
        }

        private double lavaExposure(RegionSample sample) {
            int lava = 0;
            for (int y = sample.y() - 2; y <= sample.y() + 2; y += 2) {
                if (world.getBlockAt(sample.x(), y, sample.z()).getType() == Material.LAVA) {
                    lava++;
                }
            }
            return lava;
        }

        private void recordStageTick(WorkStage workStage, long elapsedNanos, int workUnits) {
            stageTimings.computeIfAbsent(workStage, ignored -> new StageTiming())
                .record(elapsedNanos, workUnits);
            if (elapsedNanos > HARD_STAGE_TICK_LIMIT_NANOS) {
                hardTickLimitExceeded = true;
                plugin.getLogger().severe(
                    "[Queue][ValousPostProcess] event=stage_tick_limit_exceeded world="
                        + world.getName() + " stage=" + workStage.id
                        + " duration_ms=" + formatMillis(elapsedNanos)
                        + " work_units=" + workUnits
                );
            }
        }

        private JsonArray regionsToJson(List<RegionSample> regions, String prefix) {
            JsonArray values = new JsonArray();
            for (RegionSample region : regions) {
                JsonObject value = new JsonObject();
                value.addProperty("id", regionId(prefix, region));
                value.add("center", region.point().toJson());
                value.addProperty("existing_passable_air", region.passable());
                value.addProperty(
                    "contained_carve_target",
                    !"sculk".equals(prefix) || sculkCarveEnvelopeContained(region)
                );
                value.addProperty("border_clearance",
                    borderClearance(region.point(), switch (prefix) {
                        case "lush" -> lushCenterBounds;
                        case "molten" -> moltenCenterBounds;
                        default -> sculkCenterBounds;
                    }));
                values.add(value);
            }
            return values;
        }

        private static String regionId(String prefix, RegionSample region) {
            return prefix + ":" + region.x() + ":" + region.y() + ":" + region.z();
        }

        private static String rejectionComponent(String failure) {
            if (failure == null || failure.isBlank()) {
                return "";
            }
            if (failure.contains("lush_to_molten") || failure.contains("connector")) {
                return failure.contains("sculk") ? "sculk_connector" : "lush_to_molten";
            }
            if (failure.contains("lush")) {
                return "lush_surface_entrance";
            }
            if (failure.contains("molten")) {
                return "molten_surface_return";
            }
            if (failure.contains("sculk")) {
                return "sculk_chamber";
            }
            if (failure.contains("chunk") || failure.contains("ledger")) {
                return "required_chunks";
            }
            if (failure.contains("cornucopia") || failure.contains("bounds")) {
                return "complete_envelope";
            }
            return "complete_plan";
        }

        private static String normalizeRejectionReason(String failure) {
            if (failure == null || failure.isBlank()) {
                return "";
            }
            if (failure.contains("cornucopia")) {
                return "crosses_cornucopia";
            }
            if (failure.contains("pregenerated")) {
                return "crosses_pregenerated_bounds";
            }
            if (failure.contains("playable")) {
                return "crosses_playable_bounds";
            }
            if (failure.contains("safe")) {
                return "crosses_safe_edit_bounds";
            }
            if (failure.contains("ledger") || failure.contains("chunk_not_held")) {
                return "missing_ledger_chunk";
            }
            if (failure.contains("lush_to_molten")) {
                return "no_lush_molten_path";
            }
            if (failure.contains("lush") || failure.contains("surface_anchor")) {
                return "no_surface_lush_route";
            }
            if (failure.contains("molten")) {
                return "no_molten_surface_route";
            }
            if (failure.contains("sculk")) {
                return "no_sculk_connector";
            }
            return failure;
        }

        private static String formatMillis(long nanos) {
            return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
        }

        private RegionSample selectUpperConnection(
            List<RegionSample> lush,
            List<RegionSample> molten,
            List<RegionSample> frozen,
            RegionSample sculk
        ) {
            if (!sculkChamberFeasible(sculk)) {
                return null;
            }
            return java.util.stream.Stream.of(lush, molten, frozen)
                .flatMap(List::stream)
                .filter(RegionSample::passable)
                .filter(sample -> sample.y() >= -112)
                .filter(sample -> connectorCenterBounds.contains(sample.point()))
                .filter(sample -> ((long) sample.x() * sculk.x()) + ((long) sample.z() * sculk.z()) > 0L)
                .filter(sample -> sculkConnectorFeasible(sculk, sample))
                .min(Comparator
                    .comparingDouble((RegionSample sample) -> distance(sample, sculk))
                    .thenComparingDouble(sample -> -borderClearance(sample.point(), connectorCenterBounds)))
                .orElse(null);
        }

        private RegionSample selectFrozenChamber(List<RegionSample> frozen, RegionPair pair) {
            double midpointX = (pair.lush().x() + pair.molten().x()) / 2.0D;
            double midpointZ = (pair.lush().z() + pair.molten().z()) / 2.0D;
            Bounds centerBounds = safeEditBounds.inset(24, 0);
            return frozen.stream()
                .filter(sample -> sample.y() >= -150 && sample.y() <= -40)
                .filter(sample -> radius(sample.x(), sample.z()) >= CENTER_PROTECTION_RADIUS + 28.0D)
                .filter(sample -> centerBounds.contains(sample.point()))
                .filter(sample -> frozenAttachmentFeasible(sample, pair.centralCenter()))
                .min(Comparator
                    .comparingDouble((RegionSample sample) -> {
                        double dx = sample.x() - midpointX;
                        double dz = sample.z() - midpointZ;
                        return (dx * dx) + (dz * dz);
                    })
                    .thenComparingDouble(sample -> -borderClearance(sample.point(), centerBounds)))
                .orElse(null);
        }

        private boolean frozenAttachmentFeasible(RegionSample sample, Point centralCenter) {
            int frozenFloorY = clamp(sample.y(), -135, -60);
            Point frozenCenter = new Point(sample.x(), frozenFloorY + 7, sample.z());
            for (Ellipsoid shape : irregularChamberShapes(
                frozenCenter, 38, 18, 34, RoutePalette.FROZEN, 41L
            )) {
                if (!editEnvelopeInside(shape)) {
                    return false;
                }
            }
            if (!radialPatchInside(frozenCenter, 34)
                || !radialPatchInside(new Point(frozenCenter.x(), frozenFloorY, frozenCenter.z()), 17)) {
                return false;
            }
            List<Point> connection = ValousMorphologyGeometry.broadCurve(
                centralCenter,
                frozenCenter,
                world.getSeed() ^ 0x46524f5a454eL,
                0.85D
            );
            return !connection.isEmpty()
                && pathEnvelopeInside(connection, 18, connectorCenterBounds);
        }

        private boolean sculkChamberFeasible(RegionSample sculk) {
            int sculkFloorY = clamp(sculk.y(), -165, -120);
            Point chamberCenter = new Point(sculk.x(), sculkFloorY + 12, sculk.z());
            for (Ellipsoid shape : irregularChamberShapes(
                chamberCenter, 44, 22, 40, RoutePalette.SCULK, 53L
            )) {
                if (!editEnvelopeInside(shape)) {
                    return false;
                }
            }
            if (!radialPatchInside(chamberCenter, 42)
                || !radialPatchInside(new Point(chamberCenter.x(), sculkFloorY, chamberCenter.z()), 34)) {
                return false;
            }
            return true;
        }

        private boolean sculkConnectorFeasible(RegionSample sculk, RegionSample upper) {
            int sculkFloorY = clamp(sculk.y(), -165, -120);
            List<Point> connection = ValousMorphologyGeometry.broadCurve(
                new Point(sculk.x(), sculkFloorY + 2, sculk.z()),
                upper.point(),
                world.getSeed() ^ 0x5343554c4bL,
                0.65D
            );
            return !connection.isEmpty()
                && pathEnvelopeInside(connection, 20, connectorCenterBounds);
        }

        private boolean radialPatchInside(Point center, int radius) {
            Bounds bounds = new Bounds(
                new Point(center.x() - radius, center.y() - radius, center.z() - radius),
                new Point(center.x() + radius, center.y() + radius, center.z() + radius)
            );
            return pregeneratedBounds.contains(bounds)
                && playableBounds.contains(bounds)
                && safeEditBounds.contains(bounds)
                && radius(center.x(), center.z()) - radius >= CENTER_PROTECTION_RADIUS;
        }

        private Point surfacePoint(Point target) {
            if (!loadedAndInside(target.x(), target.z())) {
                return null;
            }
            int y = world.getHighestBlockYAt(
                target.x(),
                target.z(),
                HeightMap.MOTION_BLOCKING_NO_LEAVES
            );
            if (y < 64 || y >= world.getMaxHeight() - 4) {
                return null;
            }
            return new Point(target.x(), y + 1, target.z());
        }

        private Point surfaceReturnPoint(Point target) {
            Point best = null;
            long bestScore = Long.MAX_VALUE;
            for (int radius : new int[] {56, 72, 88}) {
                for (int direction = 0; direction < 16; direction++) {
                    double angle = (Math.PI * 2.0D * direction / 16.0D)
                        + (((world.getSeed() >>> 8) & 255L) / 255.0D * 0.35D);
                    int x = target.x() + (int) Math.round(Math.cos(angle) * radius);
                    int z = target.z() + (int) Math.round(Math.sin(angle) * radius);
                    if (!loadedAndInside(x, z)
                        || radius(x, z) < CENTER_PROTECTION_RADIUS + 24
                        || !safeEditBounds.inset(10, 0).contains(new Point(x, target.y(), z))) {
                        continue;
                    }
                    int surfaceY = world.getHighestBlockYAt(
                        x,
                        z,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES
                    );
                    if (surfaceY < 64 || surfaceY >= world.getMaxHeight() - 4) {
                        continue;
                    }
                    long heightPenalty = Math.abs(surfaceY - 76L) * 8L;
                    long score = heightPenalty + routeExcavationCost(List.of(
                        target,
                        new Point(x, surfaceY + 1, z)
                    ));
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Point(x, surfaceY + 1, z);
                    }
                }
            }
            return best;
        }

        private boolean hasLandSurface(RegionSample sample) {
            int y = world.getHighestBlockYAt(
                sample.x(),
                sample.z(),
                HeightMap.MOTION_BLOCKING_NO_LEAVES
            );
            return y >= 64 && y < world.getMaxHeight() - 4;
        }

        private void addVerticalBreach(List<Point> axis, RoutePalette palette) {
            for (int index = 0; index < axis.size(); index += 3) {
                Point point = axis.get(index);
                int horizontalRadius = 13 + morphValue(point, 71L, 6);
                addSphere(
                    point,
                    horizontalRadius,
                    5 + morphValue(point, 73L, 3),
                    13 + morphValue(point, 79L, 6),
                    palette
                );
            }
            Point last = axis.getLast();
            if (!carveShapes.isEmpty() && !axis.isEmpty()) {
                addSphere(last, 18, 10, 18, palette);
            }
            for (int index = 0; index < axis.size(); index += 3) {
                Point point = axis.get(index);
                floorPatches.add(new FloorPatch(
                    new Point(point.x() + 7, point.y() - 3, point.z()),
                    3,
                    palette.floor(point)
                ));
            }
            floorPatches.add(new FloorPatch(
                new Point(last.x() + 7, last.y() - 3, last.z()),
                5,
                palette.floor(last)
            ));
        }

        private void addBroadCavernThroat(
            List<Point> path,
            int minimumRadius,
            int maximumRadius,
            RoutePalette palette,
            long salt
        ) {
            int spread = Math.max(1, maximumRadius - minimumRadius + 1);
            for (int index = 0; index < path.size(); index += 3) {
                Point point = path.get(index);
                int radiusX = minimumRadius + morphValue(point, salt + index, spread);
                int radiusZ = minimumRadius + morphValue(point, salt + index + 17L, spread);
                int radiusY = Math.max(9, Math.min(radiusX, radiusZ) - 4);
                addSphere(point, radiusX, radiusY, radiusZ, palette);
            }
            for (int index = 0; index < path.size(); index += 2) {
                Point point = path.get(index);
                floorPatches.add(new FloorPatch(
                    new Point(point.x(), point.y() - 3, point.z()),
                    Math.max(4, minimumRadius / 3),
                    palette.floor(point)
                ));
            }
        }

        private void addTerracedReturn(List<Point> path, RoutePalette palette) {
            for (int index = 0; index < path.size(); index += 2) {
                Point point = path.get(index);
                addSphere(point, 6, 5, 6, palette);
                floorPatches.add(new FloorPatch(
                    new Point(point.x(), point.y() - 4, point.z()),
                    4,
                    palette.floor(point)
                ));
            }
        }

        private void addIrregularChamberCluster(
            Point center,
            int radiusX,
            int radiusY,
            int radiusZ,
            RoutePalette palette,
            long salt
        ) {
            carveShapes.addAll(irregularChamberShapes(
                center, radiusX, radiusY, radiusZ, palette, salt
            ));
            floorPatches.add(new FloorPatch(
                new Point(center.x(), center.y() - radiusY + 2, center.z()),
                Math.max(8, Math.min(radiusX, radiusZ) / 2),
                palette.floor(center)
            ));
        }

        private List<Ellipsoid> irregularChamberShapes(
            Point center,
            int radiusX,
            int radiusY,
            int radiusZ,
            RoutePalette palette,
            long salt
        ) {
            List<Ellipsoid> shapes = new ArrayList<>(6);
            shapes.add(new Ellipsoid(center, radiusX, radiusY, radiusZ, palette));
            for (int index = 0; index < 5; index++) {
                double angle = ((morphValue(center, salt + index, 360)) / 180.0D) * Math.PI;
                int horizontalOffset = Math.max(8, Math.min(radiusX, radiusZ) / 2)
                    + morphValue(center, salt + 31L + index, 8);
                Point offset = new Point(
                    center.x() + (int) Math.round(Math.cos(angle) * horizontalOffset),
                    center.y() - (radiusY / 4) + morphValue(center, salt + 47L + index, radiusY / 2 + 1),
                    center.z() + (int) Math.round(Math.sin(angle) * horizontalOffset)
                );
                shapes.add(new Ellipsoid(
                    offset,
                    Math.max(12, radiusX / 2 + morphValue(offset, salt + 61L, radiusX / 4 + 1)),
                    Math.max(8, radiusY / 2 + morphValue(offset, salt + 67L, radiusY / 3 + 1)),
                    Math.max(12, radiusZ / 2 + morphValue(offset, salt + 71L, radiusZ / 4 + 1)),
                    palette
                ));
            }
            return List.copyOf(shapes);
        }

        private int morphValue(Point point, long salt, int bound) {
            if (bound <= 1) {
                return 0;
            }
            long value = world.getSeed() ^ salt;
            value ^= point.x() * 0x9e3779b97f4a7c15L;
            value ^= point.y() * 0xc2b2ae3d27d4eb4fL;
            value ^= point.z() * 0x165667b19e3779f9L;
            value ^= value >>> 29;
            return (int) Math.floorMod(value, bound);
        }

        private ConnectionQuality quality(
            List<Point> path,
            int minimumWidth,
            int medianWidth,
            int minimumHeight,
            String classification
        ) {
            return new ConnectionQuality(
                minimumWidth,
                medianWidth,
                minimumHeight,
                sampledLength(path),
                naturalAirRatio(path, Math.max(3, minimumWidth / 4)),
                classification
            );
        }

        private double naturalAirRatio(List<Point> path, int sampleRadius) {
            if (path == null || path.isEmpty()) {
                return 0.0D;
            }
            int passable = 0;
            int samples = 0;
            int[][] offsets = {
                {0, 0},
                {sampleRadius, 0},
                {-sampleRadius, 0},
                {0, sampleRadius},
                {0, -sampleRadius}
            };
            for (int index = 0; index < path.size(); index += 3) {
                Point point = path.get(index);
                for (int[] offset : offsets) {
                    Block feet = world.getBlockAt(point.x() + offset[0], point.y(), point.z() + offset[1]);
                    Block head = world.getBlockAt(point.x() + offset[0], point.y() + 1, point.z() + offset[1]);
                    samples++;
                    if (feet.isPassable() && head.isPassable()
                        && feet.getType() != Material.WATER && feet.getType() != Material.LAVA
                        && head.getType() != Material.WATER && head.getType() != Material.LAVA) {
                        passable++;
                    }
                }
            }
            return samples == 0 ? 0.0D : passable / (double) samples;
        }

        private void addSphere(Point center, int radiusX, int radiusY, int radiusZ, RoutePalette palette) {
            carveShapes.add(new Ellipsoid(center, radiusX, radiusY, radiusZ, palette));
        }

        private void addSculkFloor(Point center, int radius, int floorY) {
            floorPatches.add(new FloorPatch(
                new Point(center.x(), floorY, center.z()),
                radius,
                Material.SCULK
            ));
        }

        private boolean pathEnvelopeInside(List<Point> path, int carveRadius, Bounds centerBounds) {
            if (!ValousMorphologyGeometry.horizontalEnvelopeInside(
                path,
                carveRadius,
                centerBounds.min().x(),
                centerBounds.max().x(),
                centerBounds.min().z(),
                centerBounds.max().z(),
                CENTER_PROTECTION_RADIUS
            )) {
                return false;
            }
            for (Point point : path) {
                if (!loadedAndInside(point.x(), point.z())) {
                    return false;
                }
            }
            return true;
        }

        private boolean editEnvelopeInside(Ellipsoid shape) {
            Bounds bounds = shape.bounds();
            return pregeneratedBounds.contains(bounds)
                && playableBounds.contains(bounds)
                && safeEditBounds.contains(bounds)
                && radius(shape.center().x(), shape.center().z())
                    - Math.max(shape.radiusX(), shape.radiusZ()) >= CENTER_PROTECTION_RADIUS;
        }

        private long routeExcavationCost(List<Point> path) {
            long cost = 0L;
            for (Point point : path) {
                for (int y = point.y() - 2; y <= point.y() + 2; y++) {
                    Block block = world.getBlockAt(point.x(), y, point.z());
                    Material material = block.getType();
                    if (material == Material.LAVA) {
                        cost += 24L;
                    } else if (material == Material.WATER) {
                        cost += 8L;
                    } else if (!block.isPassable()) {
                        cost += 2L;
                    }
                }
                if (point.y() > 24) {
                    cost += 100L;
                }
            }
            return cost;
        }

        private Bounds boundsForShapes(int fromInclusive, int toExclusive) {
            Bounds bounds = null;
            for (int index = fromInclusive; index < toExclusive; index++) {
                Bounds current = carveShapes.get(index).bounds();
                bounds = bounds == null ? current : bounds.include(current);
            }
            return bounds;
        }

        private Bounds boundsForEdits(
            int shapeFromInclusive,
            int shapeToExclusive,
            int floorFromInclusive,
            int floorToExclusive
        ) {
            Bounds bounds = boundsForShapes(shapeFromInclusive, shapeToExclusive);
            for (int index = floorFromInclusive; index < floorToExclusive; index++) {
                Bounds current = floorPatches.get(index).bounds();
                bounds = bounds == null ? current : bounds.include(current);
            }
            return bounds;
        }

        private String validateCompletePlan() {
            if (!safeEditBounds.contains(lushEntranceBounds)) {
                return "lush_entrance_envelope_crosses_safe_bounds";
            }
            if (!safeEditBounds.contains(connectorBounds)) {
                return "lush_to_molten_envelope_crosses_safe_bounds";
            }
            if (!safeEditBounds.contains(moltenReturnBounds)) {
                return "molten_return_envelope_crosses_safe_bounds";
            }
            if (!safeEditBounds.contains(sculkChamberBounds)) {
                return "sculk_envelope_crosses_safe_bounds";
            }
            for (EditShape shape : carveShapes) {
                Bounds bounds = shape.bounds();
                if (!pregeneratedBounds.contains(bounds)) {
                    return "shape_crosses_pregenerated_bounds";
                }
                if (!playableBounds.contains(bounds)) {
                    return "shape_crosses_playable_bounds";
                }
                if (!safeEditBounds.contains(bounds)) {
                    return "shape_crosses_safe_edit_bounds";
                }
                if (shape instanceof Ellipsoid ellipsoid
                    && radius(ellipsoid.center().x(), ellipsoid.center().z())
                        - Math.max(ellipsoid.radiusX(), ellipsoid.radiusZ()) < CENTER_PROTECTION_RADIUS) {
                    return "shape_crosses_cornucopia_exclusion";
                }
            }
            for (FloorPatch patch : floorPatches) {
                Bounds bounds = patch.bounds();
                if (!pregeneratedBounds.contains(bounds)
                    || !playableBounds.contains(bounds)
                    || !safeEditBounds.contains(bounds)) {
                    return "floor_patch_crosses_route_bounds";
                }
                if (radius(patch.center().x(), patch.center().z()) - patch.radius()
                    < CENTER_PROTECTION_RADIUS) {
                    return "floor_patch_crosses_cornucopia_exclusion";
                }
            }
            for (FluidSealPatch patch : fluidSealPatches) {
                Bounds bounds = patch.bounds();
                if (!pregeneratedBounds.contains(bounds)
                    || !playableBounds.contains(bounds)
                    || !safeEditBounds.contains(bounds)) {
                    return "fluid_seal_crosses_route_bounds";
                }
                if (radius(patch.center().x(), patch.center().z()) - patch.outerRadius()
                    < CENTER_PROTECTION_RADIUS) {
                    return "fluid_seal_crosses_cornucopia_exclusion";
                }
            }
            for (long key : chunksForCompletePlan()) {
                if (!settings.pregenerationLedger().contains(key)) {
                    return "plan_chunk_absent_from_completed_ledger";
                }
                if (!settings.chunkHandoff().lease().contains(key)
                    && !heldChunkKeys.contains(key)) {
                    return "plan_chunk_not_held";
                }
            }
            return null;
        }

        private void resetPlannedEdits() {
            carveShapes.clear();
            floorPatches.clear();
            fluidSealPatches.clear();
            lushTarget = null;
            moltenTarget = null;
            frozenTarget = null;
            sculkTarget = null;
            sculkUpperTarget = null;
            lushSurface = null;
            moltenSurface = null;
            lushEntranceBounds = null;
            connectorBounds = null;
            moltenReturnBounds = null;
            sculkChamberBounds = null;
            lushEntrancePath = List.of();
            connectorPath = List.of();
            moltenReturnPath = List.of();
            sculkConnectorPath = List.of();
            connectorSampledLength = 0;
            sculkConnectorSampledLength = 0;
            connectionQuality.clear();
            expectedRemovedOperations = 0L;
            expectedPlacedOperations = 0L;
            expectedFluidCandidateOperations = 0L;
            lastRouteVolumeNanos = 0L;
            lastEnvelopeValidationNanos = 0L;
            lastRouteVolumeUnits = 0;
        }

        private Bounds completePlanBounds() {
            Bounds result = null;
            for (EditShape shape : carveShapes) {
                result = result == null ? shape.bounds() : result.include(shape.bounds());
            }
            for (FloorPatch patch : floorPatches) {
                result = result == null ? patch.bounds() : result.include(patch.bounds());
            }
            for (FluidSealPatch patch : fluidSealPatches) {
                result = result == null ? patch.bounds() : result.include(patch.bounds());
            }
            return result;
        }

        private Set<Long> chunksForCompletePlan() {
            Bounds bounds = completePlanBounds();
            return bounds == null ? Set.of() : chunkKeysForBounds(bounds);
        }

        private void calculateExpectedEditOperations() {
            long removeOperations = 0L;
            for (EditShape shape : carveShapes) {
                if (shape instanceof Ellipsoid ellipsoid) {
                    removeOperations += Math.round(
                        (4.0D / 3.0D)
                            * Math.PI
                            * ellipsoid.radiusX()
                            * ellipsoid.radiusY()
                            * ellipsoid.radiusZ()
                    );
                } else {
                    Bounds bounds = shape.bounds();
                    removeOperations += (long) (bounds.max().x() - bounds.min().x() + 1)
                        * (bounds.max().y() - bounds.min().y() + 1)
                        * (bounds.max().z() - bounds.min().z() + 1);
                }
            }
            long placeOperations = 0L;
            for (FloorPatch patch : floorPatches) {
                placeOperations += Math.round(Math.PI * patch.radius() * patch.radius());
            }
            long fluidSealOperations = 0L;
            for (FluidSealPatch patch : fluidSealPatches) {
                fluidSealOperations += patch.volumeUpperBound();
            }
            expectedRemovedOperations = removeOperations;
            expectedPlacedOperations = placeOperations + fluidSealOperations;
            expectedFluidCandidateOperations = removeOperations + fluidSealOperations;
        }

        private ChunkAvailability ensureGeneratedChunkLoaded(int x, int z) {
            if (Math.abs(x) > halfWidth || Math.abs(z) > halfWidth) {
                return ChunkAvailability.INVALID;
            }
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long key = chunkKey(chunkX, chunkZ);
            ValousChunkHandoff.Result handoff = settings.chunkHandoff();
            if (!settings.pregenerationLedger().contains(key)
                || handoff == null
                || handoff.lease() == null
                || !handoff.lease().contains(key)) {
                return ChunkAvailability.INVALID;
            }
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                return ChunkAvailability.READY;
            }
            return ChunkAvailability.INVALID;
        }

        private void holdChunk(int chunkX, int chunkZ, long key) {
            if (heldChunkKeys.add(key)) {
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            }
        }

        private void releaseChunkTickets() {
            int failures = 0;
            for (long key : heldChunkKeys) {
                try {
                    world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
                } catch (RuntimeException ex) {
                    failures++;
                }
            }
            plugin.getLogger().info("[Queue][ValousPostProcess] event=additional_holds_released world="
                + world.getName() + " held=" + heldChunkKeys.size()
                + " released=" + (heldChunkKeys.size() - failures)
                + " failures=" + failures);
            heldChunkKeys.clear();
        }

        private boolean loadedAndInside(int x, int z) {
            long key = chunkKey(x >> 4, z >> 4);
            return pregeneratedBounds.contains(new Point(x, 0, z))
                && settings.pregenerationLedger().contains(key)
                && (settings.chunkHandoff().lease().contains(key) || heldChunkKeys.contains(key))
                && world.isChunkLoaded(x >> 4, z >> 4);
        }

        private static boolean safePassable(Block block) {
            Material type = block.getType();
            return block.isPassable()
                && type != Material.WATER
                && type != Material.LAVA
                && type != Material.FIRE
                && type != Material.SOUL_FIRE
                && type != Material.POWDER_SNOW;
        }

        private static String biomeKey(Biome biome) {
            NamespacedKey key = biome == null ? null : biome.getKey();
            return key == null ? "" : key.toString();
        }

        private static long chunkKey(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        private static double radius(int x, int z) {
            return Math.hypot(x, z);
        }

        private static double horizontalDistance(RegionSample left, RegionSample right) {
            return Math.hypot(left.x() - right.x(), left.z() - right.z());
        }

        private static double distance(RegionSample left, RegionSample right) {
            double dx = left.x() - right.x();
            double dy = left.y() - right.y();
            double dz = left.z() - right.z();
            return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        }

        private static int sampledLength(List<Point> path) {
            int total = 0;
            for (int index = 1; index < path.size(); index++) {
                Point left = path.get(index - 1);
                Point right = path.get(index);
                total += (int) Math.round(Math.sqrt(
                    Math.pow(right.x() - left.x(), 2)
                        + Math.pow(right.y() - left.y(), 2)
                        + Math.pow(right.z() - left.z(), 2)
                ));
            }
            return total;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static Bounds intersect(Bounds left, Bounds right) {
            return new Bounds(
                new Point(
                    Math.max(left.min().x(), right.min().x()),
                    Math.max(left.min().y(), right.min().y()),
                    Math.max(left.min().z(), right.min().z())
                ),
                new Point(
                    Math.min(left.max().x(), right.max().x()),
                    Math.min(left.max().y(), right.max().y()),
                    Math.min(left.max().z(), right.max().z())
                )
            );
        }

        private static Set<Long> chunkKeysForBounds(Bounds bounds) {
            Set<Long> keys = new LinkedHashSet<>();
            int minChunkX = bounds.min().x() >> 4;
            int maxChunkX = bounds.max().x() >> 4;
            int minChunkZ = bounds.min().z() >> 4;
            int maxChunkZ = bounds.max().z() >> 4;
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    keys.add(ValousPregenerationLedger.chunkKey(chunkX, chunkZ));
                }
            }
            return Set.copyOf(keys);
        }

        private static double borderClearance(Point point, Bounds bounds) {
            return Math.min(
                Math.min(point.x() - bounds.min().x(), bounds.max().x() - point.x()),
                Math.min(point.z() - bounds.min().z(), bounds.max().z() - point.z())
            );
        }

        private static String boundsText(Bounds bounds) {
            if (bounds == null) {
                return "none";
            }
            return bounds.min().x() + "," + bounds.min().y() + "," + bounds.min().z()
                + ".." + bounds.max().x() + "," + bounds.max().y() + "," + bounds.max().z();
        }

        private static String pointText(Point point) {
            return point.x() + "," + point.y() + "," + point.z();
        }

        private static String hex(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        }
    }

    private enum Stage {
        SCANNING,
        EXPANSION_PREPARING,
        EXPANDING,
        EXTRACTING,
        PLANNING,
        CARVING,
        FLUID_SEALING,
        FLOORING,
        WRITING,
        COMPLETE
    }

    private enum ExtractionPhase {
        LUSH,
        MOLTEN,
        FROZEN,
        SCULK,
        COMPLETE;

        ExtractionPhase next() {
            return switch (this) {
                case LUSH -> MOLTEN;
                case MOLTEN -> FROZEN;
                case FROZEN -> SCULK;
                case SCULK, COMPLETE -> COMPLETE;
            };
        }
    }

    private enum PlanningPhase {
        LUSH_LAVA,
        MOLTEN_LAVA,
        PAIR_ENUMERATION,
        COMPLETE
    }

    private enum WorkStage {
        INITIAL_SCAN("initial_scan"),
        EXPANSION_PREPARATION("expansion_preparation"),
        SCAN_EXPANSION("scan_expansion"),
        REGION_EXTRACTION("region_extraction"),
        PAIR_ENUMERATION("pair_enumeration"),
        PAIR_SCORING("pair_scoring"),
        PATHFINDING("pathfinding"),
        ROUTE_VOLUME_CONSTRUCTION("route_volume_construction"),
        ENVELOPE_VALIDATION("envelope_validation"),
        BLOCK_REMOVAL("block_removal"),
        BLOCK_PLACEMENT("block_placement"),
        FLUID_REPLACEMENT("fluid_replacement"),
        FINAL_ROUTE_VALIDATION("final_route_validation"),
        MANIFEST_SERIALIZATION("manifest_serialization");

        private final String id;

        WorkStage(String id) {
            this.id = id;
        }
    }

    private enum ChunkAvailability {
        READY,
        WAITING,
        INVALID
    }

    private enum RoutePalette {
        LUSH,
        TRANSITION,
        MOLTEN,
        FROZEN,
        SCULK;

        Material floor(Point point) {
            return switch (this) {
                case LUSH -> ((point.x() * 31L + point.z() * 17L) & 3L) == 0L
                    ? Material.MUD
                    : Material.MOSS_BLOCK;
                case TRANSITION -> point.y() < -32 ? Material.DEEPSLATE : Material.STONE;
                case MOLTEN -> ((point.x() * 13L + point.z() * 29L) & 3L) == 0L
                    ? Material.BLACKSTONE
                    : Material.BASALT;
                case FROZEN -> ((point.x() * 19L + point.z() * 23L) & 3L) == 0L
                    ? Material.CALCITE
                    : Material.PACKED_ICE;
                case SCULK -> ((point.x() * 7L + point.z() * 11L) & 3L) == 0L
                    ? Material.TUFF
                    : Material.DEEPSLATE;
            };
        }
    }

    private interface EditShape {
        Bounds bounds();

        boolean contains(int x, int y, int z);
    }

    private record Ellipsoid(
        Point center,
        int radiusX,
        int radiusY,
        int radiusZ,
        RoutePalette palette
    ) implements EditShape {
        @Override
        public Bounds bounds() {
            return new Bounds(
                new Point(center.x() - radiusX, center.y() - radiusY, center.z() - radiusZ),
                new Point(center.x() + radiusX, center.y() + radiusY, center.z() + radiusZ)
            );
        }

        @Override
        public boolean contains(int x, int y, int z) {
            double dx = (x - center.x()) / (double) radiusX;
            double dy = (y - center.y()) / (double) radiusY;
            double dz = (z - center.z()) / (double) radiusZ;
            double irregular = (((x * 31L) ^ (y * 17L) ^ (z * 13L)) & 15L) / 150.0D;
            return (dx * dx) + (dy * dy) + (dz * dz) <= 1.0D - irregular;
        }
    }

    private record FloorPatch(Point center, int radius, Material material) {
        Bounds bounds() {
            return new Bounds(
                new Point(center.x() - radius, center.y(), center.z() - radius),
                new Point(center.x() + radius, center.y(), center.z() + radius)
            );
        }
    }

    private record FluidSealPatch(
        Point center,
        int innerRadius,
        int outerRadius,
        Material material
    ) {
        Bounds bounds() {
            return new Bounds(
                new Point(
                    center.x() - outerRadius,
                    center.y() - outerRadius,
                    center.z() - outerRadius
                ),
                new Point(
                    center.x() + outerRadius,
                    center.y() + outerRadius,
                    center.z() + outerRadius
                )
            );
        }

        long volumeUpperBound() {
            long width = (outerRadius * 2L) + 1L;
            return width * width * width;
        }
    }

    private static final class ShapeCursor {
        private final Bounds bounds;
        private int x;
        private int y;
        private int z;
        private boolean complete;

        private ShapeCursor(Bounds bounds) {
            this.bounds = bounds;
            this.x = bounds.min().x();
            this.y = bounds.min().y();
            this.z = bounds.min().z();
        }

        private Point next() {
            if (complete) {
                return null;
            }
            Point current = new Point(x, y, z);
            x++;
            if (x > bounds.max().x()) {
                x = bounds.min().x();
                z++;
                if (z > bounds.max().z()) {
                    z = bounds.min().z();
                    y++;
                    if (y > bounds.max().y()) {
                        complete = true;
                    }
                }
            }
            return current;
        }
    }

    private static final class FloorCursor {
        private final FloorPatch patch;
        private int dx;
        private int dz;
        private boolean complete;

        private FloorCursor(FloorPatch patch) {
            this.patch = patch;
            this.dx = -patch.radius();
            this.dz = -patch.radius();
        }

        private Point next() {
            while (!complete) {
                int currentDx = dx;
                int currentDz = dz;
                dz++;
                if (dz > patch.radius()) {
                    dz = -patch.radius();
                    dx++;
                    if (dx > patch.radius()) {
                        complete = true;
                    }
                }
                if ((currentDx * currentDx) + (currentDz * currentDz)
                    <= patch.radius() * patch.radius()) {
                    return new Point(
                        patch.center().x() + currentDx,
                        patch.center().y(),
                        patch.center().z() + currentDz
                    );
                }
            }
            return null;
        }
    }

    private static final class FluidSealCursor {
        private final FluidSealPatch patch;
        private int dx;
        private int dy;
        private int dz;
        private boolean complete;

        private FluidSealCursor(FluidSealPatch patch) {
            this.patch = patch;
            this.dx = -patch.outerRadius();
            this.dy = -patch.outerRadius();
            this.dz = -patch.outerRadius();
        }

        private Point next() {
            int innerSquared = patch.innerRadius() * patch.innerRadius();
            int outerSquared = patch.outerRadius() * patch.outerRadius();
            while (!complete) {
                int currentDx = dx;
                int currentDy = dy;
                int currentDz = dz;
                dz++;
                if (dz > patch.outerRadius()) {
                    dz = -patch.outerRadius();
                    dy++;
                    if (dy > patch.outerRadius()) {
                        dy = -patch.outerRadius();
                        dx++;
                        if (dx > patch.outerRadius()) {
                            complete = true;
                        }
                    }
                }
                int distanceSquared = (currentDx * currentDx)
                    + (currentDy * currentDy)
                    + (currentDz * currentDz);
                if (distanceSquared > innerSquared && distanceSquared <= outerSquared) {
                    return new Point(
                        patch.center().x() + currentDx,
                        patch.center().y() + currentDy,
                        patch.center().z() + currentDz
                    );
                }
            }
            return null;
        }
    }

    private record SampleCoordinate(int x, int y, int z) {
    }

    private record RegionSample(int x, int y, int z, String biome, boolean passable) {
        Point point() {
            return new Point(x, y, z);
        }
    }

    private record RegionPair(
        RegionSample lush,
        RegionSample molten,
        double score,
        int estimatedConnectorLength,
        long estimatedExcavation,
        double borderClearance,
        double cornucopiaClearance,
        double lavaExposure,
        List<Point> connectorPath,
        Point centralCenter
    ) {
    }

    private record PlanningAttempt(
        int attempt,
        String lushRegionId,
        Point lushCenter,
        String moltenRegionId,
        Point moltenCenter,
        String sculkRegionId,
        Point sculkCenter,
        int estimatedConnectorLength,
        Bounds plannedBounds,
        int requiredChunks,
        double borderClearance,
        double cornucopiaClearance,
        String rejectionComponent,
        String rejectionReason,
        long planningNanos
    ) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("attempt", attempt);
            value.addProperty("lush_region_id", lushRegionId);
            value.add("lush_center", lushCenter.toJson());
            value.addProperty("molten_region_id", moltenRegionId);
            value.add("molten_center", moltenCenter.toJson());
            value.addProperty("sculk_region_id", sculkRegionId);
            value.add("sculk_center", sculkCenter.toJson());
            value.addProperty("estimated_connector_length", estimatedConnectorLength);
            value.add("complete_planned_bounds",
                plannedBounds == null ? new JsonObject() : plannedBounds.toJson());
            value.addProperty("required_chunks", requiredChunks);
            value.addProperty("border_clearance", borderClearance);
            value.addProperty("cornucopia_clearance", cornucopiaClearance);
            value.addProperty("rejection_component", rejectionComponent);
            value.addProperty("rejection_reason", rejectionReason);
            value.addProperty("result", rejectionReason.isBlank() ? "pass" : "rejected");
            value.addProperty("planning_ms", planningNanos / 1_000_000.0D);
            return value;
        }
    }

    private static final class StageTiming {
        private final List<Long> tickNanos = new ArrayList<>();
        private long totalNanos;
        private long workUnits;
        private int maxOperationsInTick;

        private void record(long nanos, int operations) {
            long safeNanos = Math.max(0L, nanos);
            int safeOperations = Math.max(0, operations);
            tickNanos.add(safeNanos);
            totalNanos += safeNanos;
            workUnits += safeOperations;
            maxOperationsInTick = Math.max(maxOperationsInTick, safeOperations);
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            List<Long> sorted = new ArrayList<>(tickNanos);
            Collections.sort(sorted);
            long maximum = sorted.isEmpty() ? 0L : sorted.getLast();
            long p95 = sorted.isEmpty()
                ? 0L
                : sorted.get(Math.min(
                    sorted.size() - 1,
                    (int) Math.ceil(sorted.size() * 0.95D) - 1
                ));
            value.addProperty("total_ms", totalNanos / 1_000_000.0D);
            value.addProperty("work_units", workUnits);
            value.addProperty("ticks", tickNanos.size());
            value.addProperty(
                "average_tick_ms",
                tickNanos.isEmpty() ? 0.0D : (totalNanos / (double) tickNanos.size()) / 1_000_000.0D
            );
            value.addProperty("p95_tick_ms", p95 / 1_000_000.0D);
            value.addProperty("max_tick_ms", maximum / 1_000_000.0D);
            value.addProperty("max_operations_in_tick", maxOperationsInTick);
            return value;
        }
    }
}
