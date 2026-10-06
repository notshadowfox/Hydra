package hgloot.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
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
 * Incremental, block-backed validation for the development-only Valous prototype.
 *
 * <p>The validator never edits terrain. It samples the fully pregenerated world,
 * verifies biome volume and coarse physical connectivity, then writes diagnostic
 * artifacts before allowing QueueService to expose the world as READY.</p>
 */
final class ValousPrototypeValidator {
    static final String MUSHROOM_THICKET = "hydra_valous:mushroom_thicket";
    static final String MUSHROOM_THICKET_COAST = "hydra_valous:mushroom_thicket_coast";
    static final String BOILGROUNDS = "hydra_valous:boilgrounds";
    static final String LUSH_JUNGLE = "hydra_valous:lush_jungle";
    static final String MOLTEN_CAVERNS = "hydra_valous:molten_caverns";
    static final String FROZEN_CAVES = "hydra_valous:frozen_caves";
    static final String SCULK_ABYSS = "hydra_valous:sculk_abyss";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int GRID_STEP = 8;
    private static final int COLUMNS_PER_TICK = 8;
    private static final long SCAN_TICK_BUDGET_NANOS = 6_500_000L;
    private static final int MIN_SCAN_Y = -192;
    private static final int MAX_SCAN_Y = 112;
    private static final int CENTER_PROTECTION_RADIUS = 128;
    private static final int EDGE_MARGIN = 16;
    private static final int[] BIOME_MAP_LEVELS = {80, 0, -60, -120, -170};
    private static final int[] CAVE_QUALITY_LEVELS = {20, 0, -20, -40, -60, -80, -100, -120, -140, -160};
    private static final int MIN_UNDERGROUND_AIR_SAMPLES = 12;
    private static final int MAX_UNSUPPORTED_TREE_SAMPLES = 0;
    private static final double MAX_COAST_SHARE_OF_MUSHROOM_LAND = 0.45D;
    private static final double MAX_OPEN_CAVE_PLANE_RATIO = 0.72D;
    private static final double MAX_ADJACENT_CAVE_PLANE_DELTA = 0.55D;
    private static final int ROUTE_STANCE_SEARCH_RADIUS = 8;
    private static final int ROUTE_STANCE_SEARCH_BELOW = 6;
    private static final int ROUTE_STANCE_SEARCH_ABOVE = 2;
    private static final int SHORELINE_SEARCH_RADIUS = 16;

    private ValousPrototypeValidator() {
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

    record Settings(
        String generationVersion,
        String datapackVersion,
        String selectionPolicy,
        ValousSeedSelector.Selection seedSelection,
        long seedSelectionMs,
        long generationStartedAtMs,
        int playableDiameter,
        Path persistentReportRoot,
        ValousRoutePostProcessor.Result postProcessing,
        ValousPregenerationLedger pregenerationLedger,
        ValousChunkHandoff.Result chunkHandoff,
        Runnable scanComplete
    ) {
    }

    record BlockPoint(int x, int y, int z) {
        JsonArray toJson() {
            JsonArray value = new JsonArray();
            value.add(x);
            value.add(y);
            value.add(z);
            return value;
        }
    }

    record UnsupportedTreePoint(
        int x,
        int y,
        int z,
        int supportGap,
        String biome,
        String blockBelow
    ) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("x", x);
            value.addProperty("y", y);
            value.addProperty("z", z);
            value.addProperty("support_gap", supportGap);
            value.addProperty("biome", biome);
            value.addProperty("block_below", blockBelow);
            return value;
        }
    }

    private record RouteAuditResult(
        boolean passed,
        String failure,
        int centerlinePoints,
        List<BlockPoint> stances,
        ValousRoutePostProcessor.Point start,
        ValousRoutePostProcessor.Point end
    ) {
    }

    private record ConnectivityMetrics(
        int meaningfulPassableVolume,
        int largestConnectedVolume,
        double largestConnectedRatio,
        int majorDisconnectedRegions,
        List<String> dominantBiomes,
        boolean surfaceConnected
    ) {
        JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("meaningful_passable_volume", meaningfulPassableVolume);
            value.addProperty("largest_connected_volume", largestConnectedVolume);
            value.addProperty("largest_connected_ratio", largestConnectedRatio);
            value.addProperty("major_disconnected_regions", majorDisconnectedRegions);
            JsonArray biomes = new JsonArray();
            dominantBiomes.forEach(biomes::add);
            value.add("dominant_biomes", biomes);
            value.addProperty("surface_connected", surfaceConnected);
            return value;
        }
    }

    record Result(
        boolean passed,
        String failureReason,
        long validationMs,
        long generationMs,
        Map<String, Integer> biomeSampleCounts,
        Map<String, Double> biomeVolumeRatios,
        BlockPoint lushEntrance,
        BlockPoint moltenReturn,
        int lushToMoltenSampledLength,
        int moltenToSurfaceSampledLength,
        Map<String, Boolean> checks,
        Path reportDirectory,
        Path manifestPath
    ) {
        String summary() {
            return passed
                ? "pass"
                : "fail:" + (failureReason == null || failureReason.isBlank() ? "unknown" : failureReason);
        }
    }

    static final class Job {
        private final JavaPlugin plugin;
        private final World world;
        private final Settings settings;
        private final Consumer<Result> completion;
        private final long startedAtMs = System.currentTimeMillis();
        private final List<GridColumn> columns = new ArrayList<>();
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Long, List<Node>> nodesByColumn = new HashMap<>();
        private final Map<String, Integer> biomeSampleCounts = new LinkedHashMap<>();
        private final Map<String, Integer> undergroundAirCounts = new LinkedHashMap<>();
        private final Map<String, Integer> surfaceBiomeCounts = new LinkedHashMap<>();
        private final Map<String, int[]> undergroundMaterialCounts = new LinkedHashMap<>();
        private final int halfWidth;
        private final int minCoordinate;
        private final int gridWidth;
        private final String[][] biomeMaps = new String[BIOME_MAP_LEVELS.length][];
        private final int[] caveAirCounts;
        private final int[] openCavePlaneCounts = new int[CAVE_QUALITY_LEVELS.length];
        private final int[] surfaceHeights;
        private final List<RouteAuditWork> routeAuditWork = new ArrayList<>();
        private final Map<String, RouteAuditResult> routeAuditResults = new LinkedHashMap<>();
        private int routeAuditIndex;
        private int columnIndex;
        private int unsupportedTreeSamples;
        private final List<UnsupportedTreePoint> unsupportedTreePoints = new ArrayList<>();
        private int centerLandSamples;
        private int centerSamples;
        private int surfaceLandSamples;
        private int boilgroundsSurfaceSamples;
        private int sculkRouteDrySamples;
        private int sculkRouteLavaSamples;
        private long maxScanTickNanos;
        private boolean canceled;
        private BukkitTask task;

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
            this.halfWidth = Math.max(64, (settings.playableDiameter() / 2) - EDGE_MARGIN);
            this.minCoordinate = Math.floorDiv(-halfWidth, GRID_STEP) * GRID_STEP;
            int maxCoordinate = Math.floorDiv(halfWidth, GRID_STEP) * GRID_STEP;
            this.gridWidth = ((maxCoordinate - minCoordinate) / GRID_STEP) + 1;
            int mapSize = gridWidth * gridWidth;
            for (int index = 0; index < biomeMaps.length; index++) {
                biomeMaps[index] = new String[mapSize];
            }
            this.caveAirCounts = new int[mapSize];
            this.surfaceHeights = new int[mapSize];
            Arrays.fill(surfaceHeights, world.getMinHeight());
            for (int z = minCoordinate; z <= maxCoordinate; z += GRID_STEP) {
                for (int x = minCoordinate; x <= maxCoordinate; x += GRID_STEP) {
                    columns.add(new GridColumn(x, z));
                }
            }
            for (String biome : List.of(
                MUSHROOM_THICKET,
                MUSHROOM_THICKET_COAST,
                BOILGROUNDS,
                LUSH_JUNGLE,
                MOLTEN_CAVERNS,
                FROZEN_CAVES,
                SCULK_ABYSS
            )) {
                biomeSampleCounts.put(biome, 0);
                undergroundAirCounts.put(biome, 0);
                surfaceBiomeCounts.put(biome, 0);
                undergroundMaterialCounts.put(biome, new int[4]);
            }
            ValousRoutePostProcessor.Result postProcessing = settings.postProcessing();
            if (postProcessing != null) {
                routeAuditWork.add(new RouteAuditWork(
                    "lush_surface",
                    postProcessing.lushEntrancePath(),
                    postProcessing.lushSurface(),
                    postProcessing.lushTarget(),
                    LUSH_JUNGLE,
                    true,
                    false
                ));
                routeAuditWork.add(new RouteAuditWork(
                    "lush_to_molten",
                    postProcessing.connectorPath(),
                    postProcessing.lushTarget(),
                    postProcessing.moltenTarget(),
                    MOLTEN_CAVERNS,
                    false,
                    false
                ));
                routeAuditWork.add(new RouteAuditWork(
                    "frozen_connection",
                    postProcessing.frozenConnectorPath(),
                    null,
                    postProcessing.frozenTarget(),
                    FROZEN_CAVES,
                    false,
                    false
                ));
                routeAuditWork.add(new RouteAuditWork(
                    "molten_surface",
                    postProcessing.moltenReturnPath(),
                    postProcessing.moltenTarget(),
                    postProcessing.moltenSurface(),
                    MOLTEN_CAVERNS,
                    false,
                    true
                ));
                routeAuditWork.add(new RouteAuditWork(
                    "sculk_upper",
                    postProcessing.sculkConnectorPath(),
                    postProcessing.sculkTarget(),
                    null,
                    SCULK_ABYSS,
                    false,
                    false
                ));
            }
        }

        private void start() {
            plugin.getLogger().info("[Queue][ValousPrototype] event=validation_started world="
                + world.getName() + " seed=" + world.getSeed() + " grid_step=" + GRID_STEP
                + " columns=" + columns.size());
            this.task = new BukkitRunnable() {
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
        }

        private void tick() {
            if (canceled || !plugin.isEnabled()) {
                cancel();
                return;
            }
            long tickStartedAt = System.nanoTime();
            while (routeAuditIndex < routeAuditWork.size()
                && System.nanoTime() - tickStartedAt < SCAN_TICK_BUDGET_NANOS) {
                RouteAuditWork audit = routeAuditWork.get(routeAuditIndex);
                if (audit.tick()) {
                    RouteAuditResult result = audit.result();
                    routeAuditResults.put(audit.id(), result);
                    plugin.getLogger().info(
                        "[Queue][ValousPrototype] event=exact_route_audit world="
                            + world.getName() + " route=" + audit.id()
                            + " status=" + (result.passed() ? "pass" : "fail")
                            + " points=" + result.centerlinePoints()
                            + " stances=" + result.stances().size()
                            + (result.failure().isEmpty()
                                ? ""
                                : " reason=" + result.failure())
                    );
                    routeAuditIndex++;
                }
            }
            if (routeAuditIndex < routeAuditWork.size()) {
                maxScanTickNanos = Math.max(maxScanTickNanos, System.nanoTime() - tickStartedAt);
                return;
            }
            int end = Math.min(columns.size(), columnIndex + COLUMNS_PER_TICK);
            while (columnIndex < end
                && System.nanoTime() - tickStartedAt < SCAN_TICK_BUDGET_NANOS) {
                scanColumn(columns.get(columnIndex), columnIndex);
                columnIndex++;
            }
            maxScanTickNanos = Math.max(maxScanTickNanos, System.nanoTime() - tickStartedAt);
            if (columnIndex < columns.size()) {
                return;
            }
            if (task != null) {
                task.cancel();
            }
            if (settings.scanComplete() != null) {
                settings.scanComplete().run();
            }
            plugin.getLogger().info("[Queue][ValousPrototype] event=block_scan_complete world="
                + world.getName() + " columns=" + columns.size()
                + " exact_route_stances=" + routeAuditResults.values().stream()
                    .mapToInt(result -> result.stances().size()).sum()
                + " max_tick_ms=" + String.format(
                    Locale.ROOT,
                    "%.3f",
                    maxScanTickNanos / 1_000_000.0D
                ));
            java.util.concurrent.CompletableFuture
                .supplyAsync(this::analyzeAndWrite)
                .whenComplete((result, error) -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (canceled) {
                            return;
                        }
                        if (error != null) {
                            plugin.getLogger().log(
                                java.util.logging.Level.SEVERE,
                                "[Queue][ValousPrototype] event=validation_exception world=" + world.getName(),
                                error
                            );
                            completion.accept(failedResult("validation_exception:"
                                + error.getClass().getSimpleName()));
                            return;
                        }
                        completion.accept(result);
                    });
                });
        }

        private void scanColumn(GridColumn column, int mapIndex) {
            int x = column.x();
            int z = column.z();
            int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            surfaceHeights[mapIndex] = surfaceY;
            double distanceSquared = (double) x * x + (double) z * z;
            if (distanceSquared <= (double) CENTER_PROTECTION_RADIUS * CENTER_PROTECTION_RADIUS) {
                centerSamples++;
                if (surfaceY >= 68) {
                    centerLandSamples++;
                }
            }
            for (int treeDx = 0; treeDx < GRID_STEP; treeDx += 4) {
                for (int treeDz = 0; treeDz < GRID_STEP; treeDz += 4) {
                    int treeX = x + treeDx;
                    int treeZ = z + treeDz;
                    int treeSurfaceY = treeDx == 0 && treeDz == 0
                        ? surfaceY
                        : world.getHighestBlockYAt(
                            treeX,
                            treeZ,
                            HeightMap.MOTION_BLOCKING_NO_LEAVES
                        );
                    if (hasUnsupportedTreeBase(treeX, treeZ, treeSurfaceY)) {
                        unsupportedTreeSamples++;
                    }
                }
            }
            if (surfaceY >= 64) {
                String surfaceKey = biomeKey(world.getBiome(x, surfaceY, z));
                surfaceBiomeCounts.merge(surfaceKey, 1, Integer::sum);
            }

            for (int levelIndex = 0; levelIndex < BIOME_MAP_LEVELS.length; levelIndex++) {
                String key = biomeKey(world.getBiome(x, BIOME_MAP_LEVELS[levelIndex], z));
                biomeMaps[levelIndex][mapIndex] = key;
                biomeSampleCounts.merge(key, 1, Integer::sum);
                if (levelIndex == 0 && surfaceY >= 64) {
                    if (BOILGROUNDS.equals(key)) {
                        boilgroundsSurfaceSamples++;
                    }
                    if (MUSHROOM_THICKET.equals(key)
                        || MUSHROOM_THICKET_COAST.equals(key)
                        || BOILGROUNDS.equals(key)) {
                        surfaceLandSamples++;
                    }
                }
            }

            int minY = Math.max(world.getMinHeight() + 1, MIN_SCAN_Y);
            int maxY = Math.min(world.getMaxHeight() - 2, MAX_SCAN_Y);
            for (int y = minY; y <= maxY; y++) {
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                Block below = world.getBlockAt(x, y - 1, z);
                String key = biomeKey(world.getBiome(x, y, z));
                int qualityLevelIndex = caveQualityLevelIndex(y);
                if (qualityLevelIndex >= 0 && isDryOpenCell(feet, head)) {
                    openCavePlaneCounts[qualityLevelIndex]++;
                }
                if (y < 30) {
                    int[] materials = undergroundMaterialCounts.computeIfAbsent(
                        key,
                        ignored -> new int[4]
                    );
                    Material type = feet.getType();
                    if (type == Material.WATER) {
                        materials[1]++;
                    } else if (type == Material.LAVA) {
                        materials[2]++;
                    } else if (feet.isPassable()) {
                        materials[0]++;
                    } else {
                        materials[3]++;
                    }
                    if (isInsideSculkRouteVolume(x, y, z)) {
                        if (type == Material.LAVA) {
                            sculkRouteLavaSamples++;
                        } else if (feet.isPassable() && type != Material.WATER) {
                            sculkRouteDrySamples++;
                        }
                    }
                }
                if (!isTraversable(feet, head, below, y)) {
                    continue;
                }
                if (y < 30) {
                    caveAirCounts[mapIndex]++;
                    undergroundAirCounts.merge(key, 1, Integer::sum);
                }
                Material feetType = feet.getType();
                Node node = new Node(
                    nodes.size(),
                    x,
                    y,
                    z,
                    key,
                    feetType == Material.WATER || head.getType() == Material.WATER,
                    y >= 60
                );
                nodes.add(node);
                nodesByColumn.computeIfAbsent(columnKey(x, z), ignored -> new ArrayList<>()).add(node);
            }
        }

        private int caveQualityLevelIndex(int y) {
            for (int index = 0; index < CAVE_QUALITY_LEVELS.length; index++) {
                if (CAVE_QUALITY_LEVELS[index] == y) {
                    return index;
                }
            }
            return -1;
        }

        private boolean isDryOpenCell(Block feet, Block head) {
            Material feetType = feet.getType();
            Material headType = head.getType();
            return feet.isPassable()
                && head.isPassable()
                && feetType != Material.WATER
                && feetType != Material.LAVA
                && headType != Material.WATER
                && headType != Material.LAVA;
        }

        private boolean isInsideSculkRouteVolume(int x, int y, int z) {
            ValousRoutePostProcessor.Result postProcessing = settings.postProcessing();
            if (postProcessing == null || postProcessing.sculkTarget() == null) {
                return false;
            }
            ValousRoutePostProcessor.Point target = postProcessing.sculkTarget();
            int dx = x - target.x();
            int dz = z - target.z();
            return (dx * dx) + (dz * dz) <= 40 * 40
                && y >= target.y()
                && y <= target.y() + 44;
        }

        private boolean hasUnsupportedTreeBase(int x, int z, int surfaceY) {
            if (!isShorelineColumn(x, z, surfaceY)) {
                return false;
            }
            int minY = 60;
            int terrainY = supportingTerrainY(x, z, surfaceY);
            int maxY = Math.min(
                world.getMaxHeight() - 5,
                Math.min(Math.max(60, surfaceY) + 16, terrainY + 10)
            );
            for (int y = minY; y <= maxY; y++) {
                Block stem = world.getBlockAt(x, y, z);
                if (stem.getType() != Material.MUSHROOM_STEM
                    || !unsupported(world.getBlockAt(x, y - 1, z))) {
                    continue;
                }
                int verticalStemBlocks = 1;
                for (int offset = 1; offset <= 12; offset++) {
                    if (world.getBlockAt(x, y + offset, z).getType() == Material.MUSHROOM_STEM) {
                        verticalStemBlocks++;
                    } else {
                        break;
                    }
                }
                // Short vertical stem runs also occur inside mushroom caps and branches.
                if (verticalStemBlocks >= 10) {
                    Block below = world.getBlockAt(x, y - 1, z);
                    int gap = supportGap(x, y, z);
                    String biome = biomeKey(world.getBiome(x, y, z));
                    if (!isTrunkCluster(x, y, z)) {
                        continue;
                    }
                    boolean unsafeCoastPlacement = MUSHROOM_THICKET_COAST.equals(biome)
                        && (below.getType() == Material.WATER || gap > 0);
                    // Templates intentionally extend their broad roots downward by up to
                    // eight blocks. Runs beginning higher than terrainY + 10 are model
                    // branches/cap supports rather than a visible trunk base.
                    if (gap <= 8 && !unsafeCoastPlacement) {
                        continue;
                    }
                    unsupportedTreePoints.add(new UnsupportedTreePoint(
                        x,
                        y,
                        z,
                        gap,
                        biome,
                        below.getType().getKey().toString()
                    ));
                    return true;
                }
            }
            return false;
        }

        private boolean isShorelineColumn(int x, int z, int surfaceY) {
            String biome = biomeKey(world.getBiome(x, Math.max(64, surfaceY), z));
            if (MUSHROOM_THICKET_COAST.equals(biome)) {
                return true;
            }
            for (int dx = -SHORELINE_SEARCH_RADIUS; dx <= SHORELINE_SEARCH_RADIUS; dx += 4) {
                for (int dz = -SHORELINE_SEARCH_RADIUS; dz <= SHORELINE_SEARCH_RADIUS; dz += 4) {
                    if ((dx * dx) + (dz * dz)
                        > SHORELINE_SEARCH_RADIUS * SHORELINE_SEARCH_RADIUS) {
                        continue;
                    }
                    int nearbySurface = world.getHighestBlockYAt(
                        x + dx,
                        z + dz,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES
                    );
                    if (nearbySurface <= 63
                        && world.getBlockAt(x + dx, 63, z + dz).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isTrunkCluster(int x, int y, int z) {
            int clusteredColumns = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if ((dx * dx) + (dz * dz) > 10) {
                        continue;
                    }
                    Block candidate = world.getBlockAt(x + dx, y, z + dz);
                    if (candidate.getType() != Material.MUSHROOM_STEM
                        || !unsupported(world.getBlockAt(x + dx, y - 1, z + dz))) {
                        continue;
                    }
                    int verticalRun = 1;
                    for (int offset = 1; offset <= 8; offset++) {
                        if (world.getBlockAt(x + dx, y + offset, z + dz).getType()
                            != Material.MUSHROOM_STEM) {
                            break;
                        }
                        verticalRun++;
                    }
                    if (verticalRun >= 6 && ++clusteredColumns >= 4) {
                        return true;
                    }
                }
            }
            return false;
        }

        private BlockPoint findRouteStance(
            ValousRoutePostProcessor.Point point,
            BlockPoint previous,
            boolean allowWater
        ) {
            if (point == null) {
                return null;
            }
            BlockPoint best = null;
            long bestScore = Long.MAX_VALUE;
            for (int verticalOffset = -ROUTE_STANCE_SEARCH_BELOW;
                verticalOffset <= ROUTE_STANCE_SEARCH_ABOVE;
                verticalOffset++) {
                int y = point.y() + verticalOffset;
                for (int radius = 0; radius <= ROUTE_STANCE_SEARCH_RADIUS; radius++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                                continue;
                            }
                            Block feet = world.getBlockAt(point.x() + dx, y, point.z() + dz);
                            Block head = world.getBlockAt(point.x() + dx, y + 1, point.z() + dz);
                            Block below = world.getBlockAt(point.x() + dx, y - 1, point.z() + dz);
                            if (isDryTraversable(feet, head, below)
                                || (allowWater && safeWaterColumn(feet, head))) {
                                BlockPoint candidate = new BlockPoint(feet.getX(), y, feet.getZ());
                                long centerDistance = squaredDistance(candidate, point);
                                long continuityDistance = previous == null
                                    ? 0L
                                    : squaredDistance(candidate, previous);
                                long score = (continuityDistance * 16L) + centerDistance;
                                if (score < bestScore) {
                                    best = candidate;
                                    bestScore = score;
                                }
                            }
                        }
                    }
                }
            }
            return best;
        }

        private static boolean safeWaterColumn(Block feet, Block head) {
            return feet.getType() == Material.WATER && head.getType() == Material.WATER;
        }

        private static long squaredDistance(
            BlockPoint left,
            ValousRoutePostProcessor.Point right
        ) {
            long dx = left.x() - right.x();
            long dy = left.y() - right.y();
            long dz = left.z() - right.z();
            return (dx * dx) + (dy * dy) + (dz * dz);
        }

        private static long squaredDistance(BlockPoint left, BlockPoint right) {
            long dx = left.x() - right.x();
            long dy = left.y() - right.y();
            long dz = left.z() - right.z();
            return (dx * dx) + (dy * dy) + (dz * dz);
        }

        private static boolean isDryTraversable(Block feet, Block head, Block below) {
            return feet.isPassable()
                && head.isPassable()
                && feet.getType() != Material.WATER
                && head.getType() != Material.WATER
                && !hazard(feet.getType())
                && !hazard(head.getType())
                && !below.isPassable()
                && !hazard(below.getType());
        }

        private final class RouteAuditWork {
            private final String id;
            private final List<ValousRoutePostProcessor.Point> points;
            private final ValousRoutePostProcessor.Point start;
            private final ValousRoutePostProcessor.Point end;
            private final String destinationBiome;
            private final boolean requireSurfaceStart;
            private final boolean requireSurfaceEnd;
            private final List<BlockPoint> stances = new ArrayList<>();
            private int index;
            private String failure = "";

            private RouteAuditWork(
                String id,
                List<ValousRoutePostProcessor.Point> points,
                ValousRoutePostProcessor.Point start,
                ValousRoutePostProcessor.Point end,
                String destinationBiome,
                boolean requireSurfaceStart,
                boolean requireSurfaceEnd
            ) {
                this.id = id;
                this.points = points == null ? List.of() : points;
                this.start = start;
                this.end = end;
                this.destinationBiome = destinationBiome;
                this.requireSurfaceStart = requireSurfaceStart;
                this.requireSurfaceEnd = requireSurfaceEnd;
            }

            private boolean tick() {
                if (!failure.isEmpty() || points.isEmpty()) {
                    if (points.isEmpty()) {
                        failure = "missing_centerline";
                    }
                    return true;
                }
                int processed = 0;
                while (index < points.size() && processed++ < 4) {
                    ValousRoutePostProcessor.Point point = points.get(index);
                    BlockPoint previous = stances.isEmpty() ? null : stances.getLast();
                    BlockPoint stance = findRouteStance(point, previous, "lush_surface".equals(id));
                    if (stance == null) {
                        failure = "no_dry_stance:index=" + index + ":center="
                            + point.x() + "," + point.y() + "," + point.z();
                        return true;
                    }
                    if (!stances.isEmpty() && !routeStepConnected(stances.getLast(), stance)) {
                        BlockPoint disconnectedFrom = stances.getLast();
                        failure = "disconnected_step:index=" + index + ":previous="
                            + disconnectedFrom.x() + "," + disconnectedFrom.y() + ","
                            + disconnectedFrom.z()
                            + ":current=" + stance.x() + "," + stance.y() + "," + stance.z();
                        return true;
                    }
                    stances.add(stance);
                    index++;
                }
                if (index < points.size()) {
                    return false;
                }
                if (requireSurfaceStart && !surfaceReachable(stances.getFirst())) {
                    failure = "surface_start_not_reached";
                } else if (requireSurfaceEnd && !surfaceReachable(stances.getLast())) {
                    failure = "surface_end_not_reached";
                }
                ValousRoutePostProcessor.Point destinationPoint =
                    requireSurfaceEnd || end == null ? start : end;
                if (failure.isEmpty() && destinationBiome != null && destinationPoint != null
                    && !destinationBiome.equals(biomeKey(world.getBiome(
                        destinationPoint.x(),
                        destinationPoint.y(),
                        destinationPoint.z()
                    )))) {
                    failure = "destination_biome_mismatch:expected=" + destinationBiome
                        + ":actual=" + biomeKey(world.getBiome(
                            destinationPoint.x(),
                            destinationPoint.y(),
                            destinationPoint.z()
                        ));
                }
                return true;
            }

            private RouteAuditResult result() {
                return new RouteAuditResult(
                    failure.isEmpty(),
                    failure,
                    points.size(),
                    List.copyOf(stances),
                    start,
                    end
                );
            }

            private String id() {
                return id;
            }
        }

        private boolean surfaceReachable(BlockPoint stance) {
            if (stance == null || stance.y() < 60) {
                return false;
            }
            int surfaceY = supportingTerrainY(
                stance.x(),
                stance.z(),
                Math.max(112, stance.y() + 24)
            );
            return stance.y() >= surfaceY - 6;
        }

        private static boolean routeStepConnected(BlockPoint left, BlockPoint right) {
            int dx = left.x() - right.x();
            int dz = left.z() - right.z();
            return (dx * dx) + (dz * dz) <= 144
                && Math.abs(left.y() - right.y()) <= 6;
        }

        private int supportingTerrainY(int x, int z, int startY) {
            int minY = Math.max(world.getMinHeight() + 1, 48);
            int maxY = Math.min(world.getMaxHeight() - 2, Math.max(minY, startY));
            for (int y = maxY; y >= minY; y--) {
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type.isAir()
                    || block.isPassable()
                    || type == Material.MUSHROOM_STEM
                    || type == Material.RED_MUSHROOM_BLOCK
                    || type == Material.BROWN_MUSHROOM_BLOCK
                    || type == Material.SHROOMLIGHT) {
                    continue;
                }
                return y;
            }
            return minY;
        }

        private int supportGap(int x, int y, int z) {
            for (int gap = 1; gap <= 16; gap++) {
                if (!unsupported(world.getBlockAt(x, y - gap, z))) {
                    return gap - 1;
                }
            }
            return 16;
        }

        private Result analyzeAndWrite() {
            long analysisStartedAtMs = System.currentTimeMillis();
            List<Node> surfaceToLush = routeAuditNodes("lush_surface");
            List<Node> lushToMolten = routeAuditNodes("lush_to_molten");
            List<Node> moltenToSurface = routeAuditNodes("molten_surface");
            ConnectivityMetrics connectivity = analyzeConnectivity();
            ValousMorphologyGeometry.EntranceMetrics entranceMetrics =
                settings.postProcessing() == null
                    ? ValousMorphologyGeometry.entranceMetrics(List.of())
                    : ValousMorphologyGeometry.entranceMetrics(
                        settings.postProcessing().lushEntrancePath()
                    );

            Map<String, Boolean> checks = new LinkedHashMap<>();
            checks.put("post_processing_manifest_passed", settings.postProcessing() != null
                && settings.postProcessing().passed());
            checks.put("center_surface_safe", centerSamples > 0
                && centerLandSamples / (double) centerSamples >= 0.90D);
            checks.put("lush_air_volume", undergroundAirCounts.getOrDefault(LUSH_JUNGLE, 0)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("molten_air_volume", undergroundAirCounts.getOrDefault(MOLTEN_CAVERNS, 0)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("frozen_air_volume", undergroundAirCounts.getOrDefault(FROZEN_CAVES, 0)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("sculk_air_volume", undergroundAirCounts.getOrDefault(SCULK_ABYSS, 0)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("lush_dry_traversable_air", dryPassableSamples(LUSH_JUNGLE)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("molten_dry_traversable_air", dryPassableSamples(MOLTEN_CAVERNS)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("frozen_dry_traversable_air", dryPassableSamples(FROZEN_CAVES)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("sculk_dry_traversable_air", dryPassableSamples(SCULK_ABYSS)
                >= MIN_UNDERGROUND_AIR_SAMPLES);
            checks.put("sculk_not_lava_dominated", sculkRouteDrySamples > sculkRouteLavaSamples);
            double boilRatio = surfaceLandSamples == 0
                ? 0.0D
                : boilgroundsSurfaceSamples / (double) surfaceLandSamples;
            int inlandMushroomSamples = surfaceBiomeCounts.getOrDefault(MUSHROOM_THICKET, 0);
            int coastSamples = surfaceBiomeCounts.getOrDefault(MUSHROOM_THICKET_COAST, 0);
            int mushroomLandSamples = inlandMushroomSamples + coastSamples;
            double coastShare = mushroomLandSamples == 0
                ? 1.0D
                : coastSamples / (double) mushroomLandSamples;
            double maxOpenPlaneRatio = maxOpenCavePlaneRatio();
            double maxAdjacentOpenPlaneDelta = maxAdjacentOpenCavePlaneDelta();
            checks.put("boilgrounds_surface_region", boilRatio >= 0.15D && boilRatio <= 0.50D);
            checks.put("technical_coast_is_narrow",
                mushroomLandSamples > 0 && coastShare <= MAX_COAST_SHARE_OF_MUSHROOM_LAND);
            checks.put("no_giant_cave_air_plane", maxOpenPlaneRatio <= MAX_OPEN_CAVE_PLANE_RATIO);
            checks.put("no_abrupt_cave_layer_cutoff",
                maxAdjacentOpenPlaneDelta <= MAX_ADJACENT_CAVE_PLANE_DELTA);
            checks.put("shoreline_tree_support", unsupportedTreeSamples <= MAX_UNSUPPORTED_TREE_SAMPLES);
            checks.put("lush_surface_entrance", routeAuditPassed("lush_surface"));
            checks.put("lush_to_molten_connection", routeAuditPassed("lush_to_molten"));
            checks.put("molten_surface_return", routeAuditPassed("molten_surface"));
            checks.put("sculk_upper_connection", routeAuditPassed("sculk_upper"));
            checks.put("lush_surface_route_not_submerged", routeAuditPassed("lush_surface"));
            checks.put("lush_to_molten_route_not_submerged", routeAuditPassed("lush_to_molten"));
            checks.put("molten_return_route_not_submerged", routeAuditPassed("molten_surface"));
            checks.put("lush_entrance_straightness", entranceMetrics.passed());
            checks.put("dominant_cavern_ratio", connectivity.largestConnectedRatio() >= 0.65D);
            checks.put("required_route_network_contains_all_biomes", requiredRouteNetworkPassed());
            checks.put("dominant_cavern_has_surface_return", connectivity.surfaceConnected());
            checks.put("major_connections_are_broad", majorConnectionsAreBroad());
            checks.put("caverns_span_multiple_depths", cavernsSpanMultipleDepths());

            boolean passed = checks.values().stream().allMatch(Boolean::booleanValue);
            String failureReason = checks.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .reduce((left, right) -> left + "," + right)
                .orElse("");

            Map<String, Double> ratios = new LinkedHashMap<>();
            int undergroundTotal = undergroundAirCounts.values().stream().mapToInt(Integer::intValue).sum();
            for (String biome : biomeSampleCounts.keySet()) {
                ratios.put(
                    biome,
                    undergroundTotal == 0
                        ? 0.0D
                        : undergroundAirCounts.getOrDefault(biome, 0) / (double) undergroundTotal
                );
            }

            BlockPoint lushEntrance = routeEndpoint("lush_surface", true);
            BlockPoint moltenReturn = routeEndpoint("molten_surface", false);
            int lushMoltenLength = routeAuditLength("lush_to_molten");
            int returnLength = routeAuditLength("molten_surface");
            long finishedAtMs = System.currentTimeMillis();
            Path reportDirectory = settings.persistentReportRoot().resolve(world.getName());
            Path manifestPath = world.getWorldFolder().toPath().resolve("valous-prototype-manifest.json");
            Result result = new Result(
                passed,
                failureReason,
                finishedAtMs - startedAtMs,
                finishedAtMs - settings.generationStartedAtMs(),
                Map.copyOf(biomeSampleCounts),
                Map.copyOf(ratios),
                lushEntrance,
                moltenReturn,
                lushMoltenLength,
                returnLength,
                Map.copyOf(checks),
                reportDirectory,
                manifestPath
            );
            try {
                writeReports(
                    result,
                    surfaceToLush,
                    lushToMolten,
                    moltenToSurface,
                    connectivity,
                    entranceMetrics,
                    analysisStartedAtMs
                );
            } catch (IOException ex) {
                throw new IllegalStateException("Failed writing Valous prototype reports.", ex);
            }
            return result;
        }

        private double maxOpenCavePlaneRatio() {
            if (columns.isEmpty()) {
                return 1.0D;
            }
            int max = Arrays.stream(openCavePlaneCounts).max().orElse(columns.size());
            return max / (double) columns.size();
        }

        private double maxAdjacentOpenCavePlaneDelta() {
            if (columns.isEmpty()) {
                return 1.0D;
            }
            double maxDelta = 0.0D;
            for (int index = 1; index < openCavePlaneCounts.length; index++) {
                double previous = openCavePlaneCounts[index - 1] / (double) columns.size();
                double current = openCavePlaneCounts[index] / (double) columns.size();
                maxDelta = Math.max(maxDelta, Math.abs(current - previous));
            }
            return maxDelta;
        }

        private int dryPassableSamples(String biome) {
            int[] counts = undergroundMaterialCounts.get(biome);
            return counts == null ? 0 : counts[0];
        }

        private int lavaSamples(String biome) {
            int[] counts = undergroundMaterialCounts.get(biome);
            return counts == null ? 0 : counts[2];
        }

        private boolean majorConnectionsAreBroad() {
            ValousRoutePostProcessor.Result postProcessing = settings.postProcessing();
            if (postProcessing == null) {
                return false;
            }
            Map<String, ValousRoutePostProcessor.ConnectionQuality> qualities =
                postProcessing.connectionQuality();
            return broadEnough(qualities.get("lush_to_molten"), 25, 16)
                && broadEnough(qualities.get("central_to_frozen"), 24, 16)
                && broadEnough(qualities.get("sculk_descent"), 25, 16);
        }

        private static boolean broadEnough(
            ValousRoutePostProcessor.ConnectionQuality quality,
            int minimumWidth,
            int minimumHeight
        ) {
            return quality != null
                && quality.minimumWidth() >= minimumWidth
                && quality.minimumHeight() >= minimumHeight;
        }

        private boolean majorConnectionsUseNaturalAir() {
            ValousRoutePostProcessor.Result postProcessing = settings.postProcessing();
            if (postProcessing == null) {
                return false;
            }
            Map<String, ValousRoutePostProcessor.ConnectionQuality> qualities =
                postProcessing.connectionQuality();
            return naturalEnough(qualities.get("lush_to_molten"), 0.10D)
                && naturalEnough(qualities.get("central_to_frozen"), 0.10D);
        }

        private boolean requiredRouteNetworkPassed() {
            return routeAuditPassed("lush_surface")
                && routeAuditPassed("lush_to_molten")
                && routeAuditPassed("frozen_connection")
                && routeAuditPassed("sculk_upper")
                && routeAuditPassed("molten_surface");
        }

        private static boolean naturalEnough(
            ValousRoutePostProcessor.ConnectionQuality quality,
            double minimumRatio
        ) {
            return quality != null && quality.naturalAirRatio() >= minimumRatio;
        }

        private boolean cavernsSpanMultipleDepths() {
            int populatedLevels = 0;
            for (int count : openCavePlaneCounts) {
                if (count >= Math.max(4, columns.size() / 100)) {
                    populatedLevels++;
                }
            }
            int deepOpen = 0;
            for (int index = 0; index < CAVE_QUALITY_LEVELS.length; index++) {
                if (CAVE_QUALITY_LEVELS[index] <= -120) {
                    deepOpen += openCavePlaneCounts[index];
                }
            }
            return populatedLevels >= 6 && deepOpen >= Math.max(8, columns.size() / 80);
        }

        private ConnectivityMetrics analyzeConnectivity() {
            if (nodes.isEmpty()) {
                return new ConnectivityMetrics(0, 0, 0.0D, 0, List.of(), false);
            }
            int[] componentByNode = new int[nodes.size()];
            Arrays.fill(componentByNode, -1);
            List<ComponentSummary> components = new ArrayList<>();
            int componentId = 0;
            for (Node node : nodes) {
                if (node.water() || componentByNode[node.id()] >= 0) {
                    continue;
                }
                components.add(buildComponent(node, componentId++, componentByNode));
            }
            List<ComponentSummary> meaningful = components.stream()
                .filter(component -> component.size() >= 8)
                .sorted(Comparator.comparingInt(ComponentSummary::size).reversed())
                .toList();
            int total = meaningful.stream().mapToInt(ComponentSummary::size).sum();
            if (meaningful.isEmpty()) {
                return new ConnectivityMetrics(0, 0, 0.0D, 0, List.of(), false);
            }
            ComponentSummary largest = meaningful.getFirst();
            List<String> biomes = new ArrayList<>();
            if (!largest.lushNodes().isEmpty()) {
                biomes.add(LUSH_JUNGLE);
            }
            if (!largest.moltenNodes().isEmpty()) {
                biomes.add(MOLTEN_CAVERNS);
            }
            if (!largest.frozenNodes().isEmpty()) {
                biomes.add(FROZEN_CAVES);
            }
            if (!largest.sculkNodes().isEmpty()) {
                biomes.add(SCULK_ABYSS);
            }
            return new ConnectivityMetrics(
                total,
                largest.size(),
                total == 0 ? 0.0D : largest.size() / (double) total,
                meaningful.size(),
                List.copyOf(biomes),
                !largest.surfaceNodes().isEmpty()
            );
        }

        private boolean routeAuditPassed(String id) {
            RouteAuditResult audit = routeAuditResults.get(id);
            return audit != null && audit.passed();
        }

        private List<Node> routeAuditNodes(String id) {
            RouteAuditResult audit = routeAuditResults.get(id);
            if (audit == null || audit.stances().isEmpty()) {
                return List.of();
            }
            List<Node> route = new ArrayList<>(audit.stances().size());
            for (BlockPoint stance : audit.stances()) {
                route.add(new Node(
                    route.size(),
                    stance.x(),
                    stance.y(),
                    stance.z(),
                    biomeKey(world.getBiome(stance.x(), stance.y(), stance.z())),
                    false,
                    stance.y() >= 60
                ));
            }
            return List.copyOf(route);
        }

        private int routeAuditLength(String id) {
            RouteAuditResult audit = routeAuditResults.get(id);
            if (audit == null || audit.stances().size() < 2) {
                return 0;
            }
            double length = 0.0D;
            for (int index = 1; index < audit.stances().size(); index++) {
                BlockPoint left = audit.stances().get(index - 1);
                BlockPoint right = audit.stances().get(index);
                double dx = left.x() - right.x();
                double dy = left.y() - right.y();
                double dz = left.z() - right.z();
                length += Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
            }
            return (int) Math.round(length);
        }

        private BlockPoint routeEndpoint(String id, boolean first) {
            RouteAuditResult audit = routeAuditResults.get(id);
            if (audit == null || audit.stances().isEmpty()) {
                return null;
            }
            return first ? audit.stances().getFirst() : audit.stances().getLast();
        }

        private static boolean routeHasDryTraversal(List<Node> route) {
            if (route == null || route.isEmpty()) {
                return false;
            }
            long dry = route.stream().filter(node -> !node.water()).count();
            return dry >= 2 && dry == route.size();
        }

        private ComponentSummary buildComponent(Node start, int componentId, int[] componentByNode) {
            ArrayDeque<Node> queue = new ArrayDeque<>();
            List<Node> surface = new ArrayList<>();
            List<Node> lush = new ArrayList<>();
            List<Node> molten = new ArrayList<>();
            List<Node> frozen = new ArrayList<>();
            List<Node> sculk = new ArrayList<>();
            int size = 0;
            queue.add(start);
            componentByNode[start.id()] = componentId;
            while (!queue.isEmpty()) {
                Node current = queue.removeFirst();
                size++;
                if (current.surface()) {
                    surface.add(current);
                }
                if (LUSH_JUNGLE.equals(current.biome())) {
                    lush.add(current);
                } else if (MOLTEN_CAVERNS.equals(current.biome())) {
                    molten.add(current);
                } else if (FROZEN_CAVES.equals(current.biome())) {
                    frozen.add(current);
                } else if (SCULK_ABYSS.equals(current.biome())) {
                    sculk.add(current);
                }
                for (Node neighbor : neighbors(current)) {
                    if (neighbor.water() || componentByNode[neighbor.id()] >= 0) {
                        continue;
                    }
                    componentByNode[neighbor.id()] = componentId;
                    queue.addLast(neighbor);
                }
            }
            return new ComponentSummary(componentId, size, surface, lush, molten, frozen, sculk);
        }

        private static Node nearestNode(
            List<Node> candidates,
            ValousRoutePostProcessor.Point target
        ) {
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            if (target == null) {
                return candidates.getFirst();
            }
            return candidates.stream()
                .min(Comparator.comparingDouble(node -> {
                    double dx = node.x() - target.x();
                    double dy = node.y() - target.y();
                    double dz = node.z() - target.z();
                    return (dx * dx) + (dy * dy) + (dz * dz);
                }))
                .orElse(candidates.getFirst());
        }

        private List<Node> shortestPath(
            Node start,
            java.util.function.Predicate<Node> target,
            int componentId,
            int[] componentByNode
        ) {
            if (start == null) {
                return List.of();
            }
            int[] previous = new int[nodes.size()];
            Arrays.fill(previous, -1);
            boolean[] visited = new boolean[nodes.size()];
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.add(start);
            visited[start.id()] = true;
            Node found = null;
            while (!queue.isEmpty()) {
                Node current = queue.removeFirst();
                if (target.test(current)) {
                    found = current;
                    break;
                }
                for (Node neighbor : neighbors(current)) {
                    if (componentByNode[neighbor.id()] != componentId || visited[neighbor.id()]) {
                        continue;
                    }
                    visited[neighbor.id()] = true;
                    previous[neighbor.id()] = current.id();
                    queue.addLast(neighbor);
                }
            }
            if (found == null) {
                return List.of();
            }
            ArrayDeque<Node> reversed = new ArrayDeque<>();
            for (int id = found.id(); id >= 0; id = previous[id]) {
                reversed.addFirst(nodes.get(id));
                if (id == start.id()) {
                    break;
                }
            }
            return List.copyOf(reversed);
        }

        private List<Node> neighbors(Node node) {
            List<Node> result = new ArrayList<>(20);
            addNearby(result, nodesByColumn.get(columnKey(node.x(), node.z())), node, 5, true);
            addNearby(result, nodesByColumn.get(columnKey(node.x() + GRID_STEP, node.z())), node, 3, false);
            addNearby(result, nodesByColumn.get(columnKey(node.x() - GRID_STEP, node.z())), node, 3, false);
            addNearby(result, nodesByColumn.get(columnKey(node.x(), node.z() + GRID_STEP)), node, 3, false);
            addNearby(result, nodesByColumn.get(columnKey(node.x(), node.z() - GRID_STEP)), node, 3, false);
            return result;
        }

        private static void addNearby(
            List<Node> result,
            List<Node> candidates,
            Node origin,
            int maxVerticalDifference,
            boolean sameColumn
        ) {
            if (candidates == null || candidates.isEmpty()) {
                return;
            }
            for (Node candidate : candidates) {
                if (candidate.id() == origin.id()) {
                    continue;
                }
                int difference = Math.abs(candidate.y() - origin.y());
                if (difference > maxVerticalDifference) {
                    if (candidate.y() > origin.y()) {
                        break;
                    }
                    continue;
                }
                if (!sameColumn || origin.water() || candidate.water()) {
                    result.add(candidate);
                }
            }
        }

        private void writeReports(
            Result result,
            List<Node> surfaceToLush,
            List<Node> lushToMolten,
            List<Node> moltenToSurface,
            ConnectivityMetrics connectivity,
            ValousMorphologyGeometry.EntranceMetrics entranceMetrics,
            long analysisStartedAtMs
        ) throws IOException {
            Files.createDirectories(result.reportDirectory());
            for (int index = 0; index < BIOME_MAP_LEVELS.length; index++) {
                writeScaledImage(
                    biomeImage(biomeMaps[index]),
                    result.reportDirectory().resolve("biomes-y" + signedLevel(BIOME_MAP_LEVELS[index]) + ".png")
                );
            }
            writeScaledImage(caveAirImage(), result.reportDirectory().resolve("cave-air.png"));
            writeScaledImage(
                routeImage(surfaceToLush, lushToMolten, moltenToSurface, false),
                result.reportDirectory().resolve("entrances.png")
            );
            writeScaledImage(
                routeImage(surfaceToLush, lushToMolten, moltenToSurface, true),
                result.reportDirectory().resolve("lush-to-molten-route.png")
            );

            JsonObject manifest = manifest(result);
            Files.writeString(result.manifestPath(), GSON.toJson(manifest), StandardCharsets.UTF_8);
            Files.writeString(
                result.reportDirectory().resolve("valous-prototype-manifest.json"),
                GSON.toJson(manifest),
                StandardCharsets.UTF_8
            );
            JsonObject validation = new JsonObject();
            validation.addProperty("world_id", world.getName());
            validation.addProperty("seed", world.getSeed());
            validation.addProperty("status", result.passed() ? "pass" : "fail");
            validation.addProperty("failure_reason", result.failureReason());
            validation.addProperty("validation_ms", result.validationMs());
            validation.addProperty("analysis_ms", System.currentTimeMillis() - analysisStartedAtMs);
            validation.addProperty("max_scan_tick_ms", maxScanTickNanos / 1_000_000.0D);
            validation.addProperty("unsupported_tree_samples", unsupportedTreeSamples);
            JsonArray unsupportedTrees = new JsonArray();
            unsupportedTreePoints.forEach(point -> unsupportedTrees.add(point.toJson()));
            validation.add("unsupported_tree_points", unsupportedTrees);
            validation.addProperty("boilgrounds_surface_ratio", surfaceLandSamples == 0
                ? 0.0D
                : boilgroundsSurfaceSamples / (double) surfaceLandSamples);
            int inlandMushroomSamples = surfaceBiomeCounts.getOrDefault(MUSHROOM_THICKET, 0);
            int coastSamples = surfaceBiomeCounts.getOrDefault(MUSHROOM_THICKET_COAST, 0);
            int mushroomLandSamples = inlandMushroomSamples + coastSamples;
            validation.addProperty("technical_coast_share", mushroomLandSamples == 0
                ? 1.0D
                : coastSamples / (double) mushroomLandSamples);
            JsonObject cavePlaneRatios = new JsonObject();
            for (int index = 0; index < CAVE_QUALITY_LEVELS.length; index++) {
                cavePlaneRatios.addProperty(
                    Integer.toString(CAVE_QUALITY_LEVELS[index]),
                    columns.isEmpty() ? 1.0D : openCavePlaneCounts[index] / (double) columns.size()
                );
            }
            validation.add("dry_open_cave_plane_ratios", cavePlaneRatios);
            validation.addProperty("max_dry_open_cave_plane_ratio", maxOpenCavePlaneRatio());
            validation.addProperty("max_adjacent_cave_plane_delta", maxAdjacentOpenCavePlaneDelta());
            JsonObject surfaceBiomes = new JsonObject();
            surfaceBiomeCounts.forEach(surfaceBiomes::addProperty);
            validation.add("surface_biomes", surfaceBiomes);
            JsonObject undergroundMaterials = new JsonObject();
            undergroundMaterialCounts.forEach((biome, counts) -> {
                JsonObject materials = new JsonObject();
                materials.addProperty("passable", counts[0]);
                materials.addProperty("water", counts[1]);
                materials.addProperty("lava", counts[2]);
                materials.addProperty("solid", counts[3]);
                undergroundMaterials.add(biome, materials);
            });
            validation.add("underground_materials", undergroundMaterials);
            JsonObject traversableAir = new JsonObject();
            undergroundAirCounts.forEach(traversableAir::addProperty);
            validation.add("underground_traversable_air", traversableAir);
            JsonObject sculkRouteMaterials = new JsonObject();
            sculkRouteMaterials.addProperty("dry_passable", sculkRouteDrySamples);
            sculkRouteMaterials.addProperty("lava", sculkRouteLavaSamples);
            validation.add("sculk_route_materials", sculkRouteMaterials);
            JsonObject routeAudits = new JsonObject();
            routeAuditResults.forEach((id, audit) -> {
                JsonObject routeAudit = new JsonObject();
                routeAudit.addProperty("status", audit.passed() ? "pass" : "fail");
                routeAudit.addProperty("failure", audit.failure());
                routeAudit.addProperty("centerline_points", audit.centerlinePoints());
                routeAudit.addProperty("dry_stances", audit.stances().size());
                JsonArray first = audit.stances().isEmpty()
                    ? new JsonArray()
                    : audit.stances().getFirst().toJson();
                JsonArray last = audit.stances().isEmpty()
                    ? new JsonArray()
                    : audit.stances().getLast().toJson();
                routeAudit.add("first_stance", first);
                routeAudit.add("last_stance", last);
                routeAudits.add(id, routeAudit);
            });
            validation.add("exact_route_audits", routeAudits);
            validation.add("dominant_cavern_connectivity", connectivity.toJson());
            JsonObject entranceMorphology = new JsonObject();
            entranceMorphology.addProperty("vertical_drop", entranceMetrics.verticalDrop());
            entranceMorphology.addProperty(
                "maximum_horizontal_drift",
                entranceMetrics.maximumHorizontalDrift()
            );
            entranceMorphology.addProperty(
                "average_horizontal_drift",
                entranceMetrics.averageHorizontalDrift()
            );
            entranceMorphology.addProperty(
                "directional_reversals",
                entranceMetrics.directionalReversals()
            );
            entranceMorphology.addProperty("circles_center", entranceMetrics.circlesCenter());
            entranceMorphology.addProperty(
                "horizontal_to_vertical_ratio",
                entranceMetrics.horizontalToVerticalRatio()
            );
            entranceMorphology.addProperty("status", entranceMetrics.passed() ? "pass" : "fail");
            validation.add("entrance_morphology", entranceMorphology);
            JsonObject connectionMorphology = new JsonObject();
            if (settings.postProcessing() != null) {
                settings.postProcessing().connectionQuality().forEach(
                    (id, quality) -> connectionMorphology.add(id, quality.toJson())
                );
            }
            validation.add("connection_morphology", connectionMorphology);
            validation.addProperty(
                "major_connections_use_natural_air",
                majorConnectionsUseNaturalAir()
            );
            JsonObject checks = new JsonObject();
            result.checks().forEach(checks::addProperty);
            validation.add("checks", checks);
            Files.writeString(
                result.reportDirectory().resolve("validation.json"),
                GSON.toJson(validation),
                StandardCharsets.UTF_8
            );
        }

        private JsonObject manifest(Result result) {
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", 1);
            root.addProperty("world_id", world.getName());
            root.addProperty("seed", world.getSeed());
            root.addProperty("generation_version", settings.generationVersion());
            root.addProperty("datapack_version", settings.datapackVersion());
            root.addProperty("selection_policy", settings.selectionPolicy());
            root.addProperty("seed_selection_ms", settings.seedSelectionMs());
            root.addProperty("generation_ms", result.generationMs());
            if (settings.seedSelection() != null) {
                ValousSeedSelector.Selection selection = settings.seedSelection();
                JsonObject selectionJson = new JsonObject();
                selectionJson.addProperty("policy", selection.policy().id());
                selectionJson.addProperty("attempts", selection.attempts());
                selectionJson.addProperty("hard_candidate_count", selection.hardCandidateCount());
                selectionJson.addProperty("soft_score", selection.softScore());
                selectionJson.addProperty("diagnostic_fallback", selection.diagnosticFallback());
                JsonArray comparisons = new JsonArray();
                for (ValousSeedSelector.CandidateComparison candidate
                    : selection.candidateComparisons()) {
                    JsonObject candidateJson = new JsonObject();
                    candidateJson.addProperty("seed", candidate.seed());
                    candidateJson.addProperty("soft_score", candidate.softScore());
                    candidateJson.addProperty(
                        "island_quality_score",
                        candidate.islandQualityScore()
                    );
                    candidateJson.addProperty(
                        "quick_boilgrounds_ratio",
                        candidate.quickBoilgroundsRatio()
                    );
                    candidateJson.addProperty(
                        "lush_molten_pairs",
                        candidate.lushMoltenPairs()
                    );
                    candidateJson.addProperty(
                        "contained_sculk_centers",
                        candidate.containedSculkCenters()
                    );
                    comparisons.add(candidateJson);
                }
                selectionJson.add("candidate_comparisons", comparisons);
                root.add("seed_selection", selectionJson);
            }

            JsonObject biomes = new JsonObject();
            for (String biome : biomeSampleCounts.keySet()) {
                JsonObject data = new JsonObject();
                data.addProperty("sample_count", undergroundAirCounts.getOrDefault(biome, 0));
                data.addProperty("volume_ratio", result.biomeVolumeRatios().getOrDefault(biome, 0.0D));
                biomes.add(biome, data);
            }
            root.add("biomes", biomes);

            JsonObject entrances = new JsonObject();
            entrances.add("lush", entranceJson(result.lushEntrance(), LUSH_JUNGLE, 21, 48));
            entrances.add("molten_return", entranceJson(result.moltenReturn(), MOLTEN_CAVERNS, 15, 40));
            root.add("entrances", entrances);

            JsonObject routes = new JsonObject();
            JsonObject lushMolten = new JsonObject();
            lushMolten.addProperty("status", result.checks().getOrDefault("lush_to_molten_connection", false)
                ? "pass" : "fail");
            lushMolten.addProperty("sampled_length", result.lushToMoltenSampledLength());
            routes.add("lush_to_molten", lushMolten);
            JsonObject moltenSurface = new JsonObject();
            moltenSurface.addProperty("status", result.checks().getOrDefault("molten_surface_return", false)
                ? "pass" : "fail");
            moltenSurface.addProperty("sampled_length", result.moltenToSurfaceSampledLength());
            routes.add("molten_to_surface", moltenSurface);
            root.add("routes", routes);

            if (settings.postProcessing() != null) {
                ValousRoutePostProcessor.Settings postSettings = new ValousRoutePostProcessor.Settings(
                    settings.generationVersion(),
                    settings.datapackVersion(),
                    settings.selectionPolicy(),
                    settings.playableDiameter(),
                    settings.persistentReportRoot(),
                    settings.pregenerationLedger(),
                    settings.chunkHandoff()
                );
                root.add(
                    "post_processing",
                    settings.postProcessing().toJson(world.getName(), world.getSeed(), postSettings)
                );
            }

            JsonObject validation = new JsonObject();
            validation.addProperty("status", result.passed() ? "pass" : "fail");
            JsonObject checks = new JsonObject();
            result.checks().forEach(checks::addProperty);
            validation.add("checks", checks);
            root.add("validation", validation);
            return root;
        }

        private static JsonObject entranceJson(
            BlockPoint center,
            String destinationBiome,
            int horizontalRadius,
            int verticalRadius
        ) {
            JsonObject value = new JsonObject();
            if (center == null) {
                value.add("center", new JsonArray());
                value.add("bounds", new JsonObject());
            } else {
                value.add("center", center.toJson());
                JsonObject bounds = new JsonObject();
                bounds.add("min", new BlockPoint(
                    center.x() - horizontalRadius,
                    center.y() - verticalRadius,
                    center.z() - horizontalRadius
                ).toJson());
                bounds.add("max", new BlockPoint(
                    center.x() + horizontalRadius,
                    center.y() + verticalRadius,
                    center.z() + horizontalRadius
                ).toJson());
                value.add("bounds", bounds);
            }
            value.addProperty("destination_biome", destinationBiome);
            return value;
        }

        private BufferedImage biomeImage(String[] values) {
            BufferedImage image = new BufferedImage(gridWidth, gridWidth, BufferedImage.TYPE_INT_ARGB);
            for (int index = 0; index < values.length; index++) {
                image.setRGB(index % gridWidth, index / gridWidth, biomeColor(values[index]).getRGB());
            }
            return image;
        }

        private BufferedImage caveAirImage() {
            BufferedImage image = new BufferedImage(gridWidth, gridWidth, BufferedImage.TYPE_INT_ARGB);
            int maximum = Arrays.stream(caveAirCounts).max().orElse(1);
            for (int index = 0; index < caveAirCounts.length; index++) {
                int value = maximum <= 0 ? 0 : Math.min(255, (int) Math.round(caveAirCounts[index] * 255.0D / maximum));
                image.setRGB(index % gridWidth, index / gridWidth, new Color(value, value, value).getRGB());
            }
            return image;
        }

        private BufferedImage routeImage(
            List<Node> surfaceToLush,
            List<Node> lushToMolten,
            List<Node> moltenToSurface,
            boolean routeOnly
        ) {
            BufferedImage image = routeOnly
                ? new BufferedImage(gridWidth, gridWidth, BufferedImage.TYPE_INT_ARGB)
                : biomeImage(biomeMaps[0]);
            if (routeOnly) {
                Graphics2D background = image.createGraphics();
                background.setColor(Color.BLACK);
                background.fillRect(0, 0, gridWidth, gridWidth);
                background.dispose();
            }
            paintPath(image, surfaceToLush, new Color(68, 220, 105));
            paintPath(image, lushToMolten, new Color(255, 177, 58));
            paintPath(image, moltenToSurface, new Color(232, 70, 50));
            return image;
        }

        private void paintPath(BufferedImage image, List<Node> path, Color color) {
            for (Node node : path) {
                int pixelX = (node.x() - minCoordinate) / GRID_STEP;
                int pixelZ = (node.z() - minCoordinate) / GRID_STEP;
                if (pixelX >= 0 && pixelX < gridWidth && pixelZ >= 0 && pixelZ < gridWidth) {
                    image.setRGB(pixelX, pixelZ, color.getRGB());
                }
            }
        }

        private static void writeScaledImage(BufferedImage source, Path target) throws IOException {
            int scale = 4;
            BufferedImage scaled = new BufferedImage(
                source.getWidth() * scale,
                source.getHeight() * scale,
                BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            graphics.drawImage(source, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
            graphics.dispose();
            ImageIO.write(scaled, "png", target.toFile());
        }

        private Result failedResult(String reason) {
            long now = System.currentTimeMillis();
            return new Result(
                false,
                reason,
                now - startedAtMs,
                now - settings.generationStartedAtMs(),
                Map.copyOf(biomeSampleCounts),
                Map.of(),
                null,
                null,
                0,
                0,
                Map.of("validator_completed", false),
                settings.persistentReportRoot().resolve(world.getName()),
                world.getWorldFolder().toPath().resolve("valous-prototype-manifest.json")
            );
        }
    }

    private record GridColumn(int x, int z) {
    }

    private record Node(
        int id,
        int x,
        int y,
        int z,
        String biome,
        boolean water,
        boolean surface
    ) {
        BlockPoint point() {
            return new BlockPoint(x, y, z);
        }
    }

    private record ComponentSummary(
        int id,
        int size,
        List<Node> surfaceNodes,
        List<Node> lushNodes,
        List<Node> moltenNodes,
        List<Node> frozenNodes,
        List<Node> sculkNodes
    ) {
        int routeScore() {
            return size + (surfaceNodes.size() * 2) + (lushNodes.size() * 3)
                + (moltenNodes.size() * 3) + (frozenNodes.size() * 3)
                + (sculkNodes.size() * 2);
        }
    }

    private static boolean isTraversable(Block feet, Block head, Block below, int y) {
        Material feetType = feet.getType();
        Material headType = head.getType();
        if (hazard(feetType) || hazard(headType) || !feet.isPassable() || !head.isPassable()) {
            return false;
        }
        boolean water = feetType == Material.WATER || headType == Material.WATER;
        if (water) {
            return y % 4 == 0 || !below.isPassable();
        }
        return !below.isPassable() && !hazard(below.getType());
    }

    private static boolean unsupported(Block below) {
        Material type = below.getType();
        return type.isAir() || below.isPassable() || type == Material.WATER || type == Material.LAVA;
    }

    private static boolean hazard(Material type) {
        return type == Material.LAVA
            || type == Material.FIRE
            || type == Material.SOUL_FIRE
            || type == Material.POWDER_SNOW;
    }

    private static String biomeKey(Biome biome) {
        if (biome == null) {
            return "minecraft:plains";
        }
        NamespacedKey key = biome.getKey();
        return key == null ? "minecraft:plains" : key.toString();
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static double horizontalDistance(Node left, Node right) {
        double dx = left.x() - right.x();
        double dz = left.z() - right.z();
        return Math.sqrt((dx * dx) + (dz * dz));
    }

    private static int sampledLength(List<Node> path) {
        if (path.size() < 2) {
            return 0;
        }
        double length = 0.0D;
        for (int index = 1; index < path.size(); index++) {
            Node previous = path.get(index - 1);
            Node current = path.get(index);
            double dx = current.x() - previous.x();
            double dy = current.y() - previous.y();
            double dz = current.z() - previous.z();
            length += Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        }
        return (int) Math.round(length);
    }

    private static Color biomeColor(String biome) {
        return switch (biome == null ? "" : biome) {
            case MUSHROOM_THICKET -> new Color(76, 43, 96);
            case MUSHROOM_THICKET_COAST -> new Color(119, 86, 135);
            case BOILGROUNDS -> new Color(137, 70, 42);
            case LUSH_JUNGLE -> new Color(48, 132, 72);
            case MOLTEN_CAVERNS -> new Color(210, 78, 34);
            case FROZEN_CAVES -> new Color(97, 180, 224);
            case SCULK_ABYSS -> new Color(20, 62, 70);
            case "minecraft:deep_ocean" -> new Color(20, 42, 91);
            default -> new Color(80, 80, 80);
        };
    }

    private static String signedLevel(int level) {
        return level < 0 ? "minus" + Math.abs(level) : Integer.toString(level);
    }
}
