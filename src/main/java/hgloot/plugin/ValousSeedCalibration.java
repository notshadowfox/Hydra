package hgloot.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

final class ValousSeedCalibration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int STATIC_FEED_TARGET = 100;
    private static final long BASELINE_SEQUENCE = 0x56414C4F55534C31L;
    private static final long QUICK_SEQUENCE = 0x56414C4F55534C32L;

    private ValousSeedCalibration() {
    }

    static Path run(int requestedSamples, Path output) {
        int samples = Math.max(100_000, requestedSamples);
        long startedAt = System.nanoTime();
        List<Gate> gates = currentGates();
        Observation[] baseline = evaluate(samples, BASELINE_SEQUENCE);
        JsonObject report = new JsonObject();
        report.addProperty("schema_version", 1);
        report.addProperty("analysis", "valous_seed_gate_v1_2_4");
        report.addProperty("sample_count", samples);
        report.addProperty("deterministic_sequence", Long.toUnsignedString(BASELINE_SEQUENCE));
        report.add("gate_inventory", gateInventory(gates));
        report.add("baseline", analyze(baseline, gates));
        report.add("biome_candidates", analyzeBiomeCandidates(baseline, 17));

        Observation[] quick = evaluate(samples, QUICK_SEQUENCE);
        JsonObject quickAnalysis = analyzeQuickPolicy(quick);
        report.add("proposed_quick_policy", quickAnalysis);
        report.addProperty("duration_ms", elapsedMillis(startedAt));
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, GSON.toJson(report), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write Valous seed calibration.", ex);
        }
        return output;
    }

    static Path runQuickOnly(int requestedSamples, Path output) {
        int samples = Math.max(100_000, requestedSamples);
        long startedAt = System.nanoTime();
        Observation[] quick = evaluate(samples, QUICK_SEQUENCE);
        JsonObject report = new JsonObject();
        report.addProperty("schema_version", 1);
        report.addProperty("analysis", "valous_quick_policy_v1_2_4");
        report.addProperty("sample_count", samples);
        report.addProperty("deterministic_sequence", Long.toUnsignedString(QUICK_SEQUENCE));
        report.add("proposed_quick_policy", analyzeQuickPolicy(quick));
        report.addProperty("duration_ms", elapsedMillis(startedAt));
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, GSON.toJson(report), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write Valous Quick seed calibration.", ex);
        }
        return output;
    }

    private static Observation[] evaluate(int samples, long sequence) {
        Observation[] observations = new Observation[samples];
        IntStream.range(0, samples).parallel().forEach(index -> {
            long seed = splitMix64(sequence + index);
            ValousSeedSelector.Evaluation terrain =
                ValousSeedSelector.evaluateDetailedProxy(seed);
            ValousSeedSelector.PrototypeEvaluation prototype =
                ValousSeedSelector.evaluatePrototypeProxy(seed);
            observations[index] = new Observation(seed, terrain, prototype);
        });
        return observations;
    }

    private static JsonObject analyze(Observation[] observations, List<Gate> gates) {
        JsonObject result = new JsonObject();
        JsonArray table = new JsonArray();
        long cumulativePasses = observations.length;
        Map<String, Integer> combinations = new LinkedHashMap<>();
        int[][] coFailures = new int[gates.size()][gates.size()];
        boolean[][] failuresBySeed = new boolean[observations.length][gates.size()];

        for (int seedIndex = 0; seedIndex < observations.length; seedIndex++) {
            Observation observation = observations[seedIndex];
            List<String> failed = new ArrayList<>();
            for (int gateIndex = 0; gateIndex < gates.size(); gateIndex++) {
                boolean failedGate = !gates.get(gateIndex).passes.test(observation);
                failuresBySeed[seedIndex][gateIndex] = failedGate;
                if (failedGate) {
                    failed.add(gates.get(gateIndex).id);
                }
            }
            combinations.merge(String.join(",", failed), 1, Integer::sum);
        }

        for (int left = 0; left < gates.size(); left++) {
            for (int right = left + 1; right < gates.size(); right++) {
                int both = 0;
                for (boolean[] seedFailures : failuresBySeed) {
                    if (seedFailures[left] && seedFailures[right]) {
                        both++;
                    }
                }
                coFailures[left][right] = both;
                coFailures[right][left] = both;
            }
        }

        for (int gateIndex = 0; gateIndex < gates.size(); gateIndex++) {
            Gate gate = gates.get(gateIndex);
            double[] values = Arrays.stream(observations)
                .mapToDouble(gate.value)
                .sorted()
                .toArray();
            long individualPasses = Arrays.stream(observations)
                .filter(gate.passes)
                .count();
            long cumulative = 0;
            for (int seedIndex = 0; seedIndex < observations.length; seedIndex++) {
                boolean passed = true;
                for (int prior = 0; prior <= gateIndex; prior++) {
                    if (failuresBySeed[seedIndex][prior]) {
                        passed = false;
                        break;
                    }
                }
                if (passed) {
                    cumulative++;
                }
            }
            cumulativePasses = cumulative;
            int strongestCoFailure = -1;
            int strongestCount = -1;
            for (int other = 0; other < gates.size(); other++) {
                if (other != gateIndex && coFailures[gateIndex][other] > strongestCount) {
                    strongestCount = coFailures[gateIndex][other];
                    strongestCoFailure = other;
                }
            }
            JsonObject row = gate.toJson();
            row.addProperty("individual_passes", individualPasses);
            row.addProperty("individual_pass_rate", individualPasses / (double) observations.length);
            row.addProperty("cumulative_passes", cumulative);
            row.addProperty("cumulative_pass_rate", cumulative / (double) observations.length);
            row.addProperty("median", percentile(values, 0.50D));
            row.addProperty("p05", percentile(values, 0.05D));
            row.addProperty("p95", percentile(values, 0.95D));
            double[] failedMargins = Arrays.stream(observations)
                .filter(observation -> !gate.passes.test(observation))
                .mapToDouble(gate::margin)
                .sorted()
                .toArray();
            row.addProperty("failed_margin_median", percentile(failedMargins, 0.50D));
            row.addProperty("failed_margin_p05", percentile(failedMargins, 0.05D));
            row.addProperty("failed_margin_p95", percentile(failedMargins, 0.95D));
            row.addProperty(
                "most_common_cofailing_gate",
                strongestCoFailure < 0 ? "" : gates.get(strongestCoFailure).id
            );
            row.addProperty("cofailure_count", Math.max(0, strongestCount));
            table.add(row);
        }
        result.add("gate_table", table);
        result.addProperty("all_gate_passes", cumulativePasses);
        result.addProperty(
            "all_gate_pass_rate",
            cumulativePasses / (double) observations.length
        );

        JsonArray correlations = new JsonArray();
        for (int left = 0; left < gates.size(); left++) {
            for (int right = left + 1; right < gates.size(); right++) {
                JsonObject pair = new JsonObject();
                pair.addProperty("left", gates.get(left).id);
                pair.addProperty("right", gates.get(right).id);
                pair.addProperty("both_failed", coFailures[left][right]);
                correlations.add(pair);
            }
        }
        result.add("pairwise_failure_counts", correlations);

        JsonArray commonCombinations = new JsonArray();
        combinations.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(20)
            .forEach(entry -> {
                JsonObject combination = new JsonObject();
                combination.addProperty("failed_gates", entry.getKey());
                combination.addProperty("count", entry.getValue());
                commonCombinations.add(combination);
            });
        result.add("common_failure_combinations", commonCombinations);
        return result;
    }

    private static JsonArray analyzeBiomeCandidates(Observation[] baseline, int target) {
        JsonArray candidates = new JsonArray();
        Arrays.stream(baseline)
            .filter(observation -> observation.terrain.biomeShapeAccepted())
            .limit(target)
            .forEach(observation -> {
                ValousSeedSelector.PrototypeEvaluation exactPrototype =
                    ValousSeedSelector.evaluateExactPrototype(
                        observation.seed,
                        observation.prototype
                    );
                ValousSeedSelector.Evaluation exactTerrain =
                    ValousSeedSelector.evaluateExactTerrain(
                        observation.seed,
                        observation.terrain
                    );
                JsonObject candidate = observationJson(observation);
                candidate.add("exact_terrain", terrainJson(exactTerrain));
                candidate.add("exact_prototype", prototypeJson(exactPrototype));
                candidate.add("predicted_hard_failures",
                    strings(observation.prototype.failedPredictedHardChecks()));
                candidate.add("exact_hard_failures",
                    strings(exactTerrain.failedHardChecks()));
                candidates.add(candidate);
            });
        return candidates;
    }

    private static JsonObject analyzeQuickPolicy(Observation[] observations) {
        JsonObject result = new JsonObject();
        result.add("proxy_gate_analysis", analyze(observations, quickGates()));
        List<Observation> proxyAccepted = Arrays.stream(observations)
            .filter(ValousSeedCalibration::quickProxyHardAccepted)
            .toList();
        result.addProperty("sample_count", observations.length);
        result.addProperty("deterministic_sequence", Long.toUnsignedString(QUICK_SEQUENCE));
        result.addProperty("proxy_hard_accepted", proxyAccepted.size());
        result.addProperty(
            "proxy_hard_acceptance_rate",
            proxyAccepted.size() / (double) observations.length
        );
        double rate = proxyAccepted.size() / (double) observations.length;
        result.addProperty("median_attempts", rate <= 0.0D
            ? Double.POSITIVE_INFINITY : Math.log(0.5D) / Math.log(1.0D - rate));
        result.addProperty("p95_attempts", rate <= 0.0D
            ? Double.POSITIVE_INFINITY : Math.log(0.05D) / Math.log(1.0D - rate));

        JsonArray policy = new JsonArray();
        for (String rule : List.of(
            "center_value>=-0.05",
            "center_land_ratio>=0.65",
            "protected_center_entrance_candidates==0",
            "lush_region_samples>=32",
            "molten_region_samples>=32",
            "frozen_region_samples>=16"
        )) {
            policy.add(rule);
        }
        result.add("proxy_hard_rules", policy);

        int feedTarget = Math.min(STATIC_FEED_TARGET, proxyAccepted.size());
        int exactCenterSafe = 0;
        int exactBiomeVolumes = 0;
        int pairFeeds = 0;
        int sculkFeeds = 0;
        int clearanceFeeds = 0;
        int completeFeeds = 0;
        JsonArray feeds = new JsonArray();
        for (int index = 0; index < feedTarget; index++) {
            Observation observation = proxyAccepted.get(index);
            ValousSeedSelector.Evaluation exactTerrain =
                ValousSeedSelector.evaluateExactTerrain(observation.seed, observation.terrain);
            ValousSeedSelector.PrototypeEvaluation exactPrototype =
                ValousSeedSelector.evaluateExactPrototype(observation.seed, observation.prototype);
            ValousSeedSelector.StaticPlannerFeed feed =
                ValousSeedSelector.evaluateStaticPlannerFeed(observation.seed);
            boolean centerSafe = exactTerrain.centerValue() >= 68.0D
                && exactTerrain.centerLandRatio() >= 0.75D;
            boolean volumes = exactPrototype.actualLushSamples() >= 2
                && exactPrototype.actualMoltenSamples() >= 2
                && exactPrototype.actualFrozenSamples() >= 2
                && exactPrototype.actualSculkSamples() >= 2;
            boolean pairs = feed.lushMoltenPairs() > 0;
            boolean sculk = feed.containedSculkCenters() > 0;
            boolean clearances = feed.validLushCenters() > 0
                && feed.validMoltenCenters() > 0
                && feed.containedSculkCenters() > 0;
            if (centerSafe) {
                exactCenterSafe++;
            }
            if (volumes) {
                exactBiomeVolumes++;
            }
            if (pairs) {
                pairFeeds++;
            }
            if (sculk) {
                sculkFeeds++;
            }
            if (clearances) {
                clearanceFeeds++;
            }
            if (centerSafe && volumes && feed.completePlanFeasible()) {
                completeFeeds++;
            }
            JsonObject feedJson = new JsonObject();
            feedJson.addProperty("seed", observation.seed);
            feedJson.addProperty("center_safe", centerSafe);
            feedJson.addProperty("exact_biome_volumes", volumes);
            feedJson.addProperty("lush_regions", feed.lushRegions());
            feedJson.addProperty("molten_regions", feed.moltenRegions());
            feedJson.addProperty("frozen_regions", feed.frozenRegions());
            feedJson.addProperty("sculk_regions", feed.sculkRegions());
            feedJson.addProperty("lush_molten_pairs", feed.lushMoltenPairs());
            feedJson.addProperty("valid_lush_centers", feed.validLushCenters());
            feedJson.addProperty("valid_molten_centers", feed.validMoltenCenters());
            feedJson.addProperty("contained_sculk_centers", feed.containedSculkCenters());
            feedJson.addProperty("complete_plan_feasible",
                centerSafe && volumes && feed.completePlanFeasible());
            feeds.add(feedJson);
        }
        result.addProperty("static_feed_seeds", feedTarget);
        result.addProperty("exact_center_safe", exactCenterSafe);
        result.addProperty("exact_biome_volumes", exactBiomeVolumes);
        result.addProperty("with_lush_molten_pairs", pairFeeds);
        result.addProperty("with_sculk_targets", sculkFeeds);
        result.addProperty("with_valid_center_clearances", clearanceFeeds);
        result.addProperty("predicted_complete_plan_feasible", completeFeeds);
        result.add("static_planner_feeds", feeds);
        double exactConditionalRate = feedTarget == 0
            ? 0.0D
            : completeFeeds / (double) feedTarget;
        result.addProperty("estimated_final_hard_acceptance_rate", rate * exactConditionalRate);
        result.addProperty("recommendation",
            rate * exactConditionalRate >= 0.01D
                ? "QUICK_READY_FOR_RUNTIME"
                : "QUICK_STILL_OVERCONSTRAINED");
        return result;
    }

    private static boolean quickProxyHardAccepted(Observation observation) {
        ValousSeedSelector.Evaluation terrain = observation.terrain;
        ValousSeedSelector.PrototypeEvaluation prototype = observation.prototype;
        return terrain.centerValue() >= -0.05D
            && terrain.centerLandRatio() >= 0.65D
            && prototype.protectedCenterEntranceCandidates() == 0
            && prototype.lushRegionSamples() >= 32
            && prototype.moltenRegionSamples() >= 32
            && prototype.frozenRegionSamples() >= 16;
    }

    private static List<Gate> quickGates() {
        return List.of(
            gate("quick_center_noise", "hard", "center_value>=-0.05",
                "NormalNoise island signal at 0,0", "single origin sample", true,
                observation -> observation.terrain.centerValue(),
                observation -> observation.terrain.centerValue() >= -0.05D),
            gate("quick_center_land", "hard", "center_land_ratio>=0.65",
                "island noise land ratio radius 160", "16 radial samples", true,
                observation -> observation.terrain.centerLandRatio(),
                observation -> observation.terrain.centerLandRatio() >= 0.65D),
            gate("quick_cornucopia_entrance_overlap", "hard",
                "protected_center_entrances==0",
                "natural entrance noise masks", "8 block grid radius 128", true,
                observation -> observation.prototype.protectedCenterEntranceCandidates(),
                observation -> observation.prototype.protectedCenterEntranceCandidates() == 0),
            gate("quick_lush_volume", "hard", "lush_region_samples>=32",
                "cave macro noise at proxy depth", "8 block grid -240..240", true,
                observation -> observation.prototype.lushRegionSamples(),
                observation -> observation.prototype.lushRegionSamples() >= 32),
            gate("quick_molten_volume", "hard", "molten_region_samples>=32",
                "cave macro noise at proxy depth", "8 block grid -240..240", true,
                observation -> observation.prototype.moltenRegionSamples(),
                observation -> observation.prototype.moltenRegionSamples() >= 32),
            gate("quick_frozen_volume", "hard", "frozen_region_samples>=16",
                "cave macro/detail noise at proxy depth", "8 block grid -240..240", true,
                observation -> observation.prototype.frozenRegionSamples(),
                observation -> observation.prototype.frozenRegionSamples() >= 16)
        );
    }

    private static List<Gate> currentGates() {
        List<Gate> gates = new ArrayList<>();
        gates.add(gate("coarse_center_noise", "hard", "center_value>=0.0",
            "NormalNoise island signal at 0,0", "16-40 block radial probes", true,
            observation -> observation.terrain.centerValue(),
            observation -> observation.terrain.centerValue() >= 0.0D));
        gates.add(gate("coarse_center_land", "hard", "center_land_ratio>=0.75",
            "island noise land ratio radius 160", "16 radial samples", true,
            observation -> observation.terrain.centerLandRatio(),
            observation -> observation.terrain.centerLandRatio() >= 0.75D));
        gates.add(gate("coarse_inner_land", "hard", "inner_land_ratio>=0.60",
            "island noise land ratio radius 500", "24 radial samples", true,
            observation -> observation.terrain.innerLandRatio(),
            observation -> observation.terrain.innerLandRatio() >= 0.60D));
        gates.add(gate("coarse_shoulder_land", "hard", "shoulder_land_ratio>=0.25",
            "island noise land ratio radius 750", "32 radial samples", true,
            observation -> observation.terrain.shoulderLandRatio(),
            observation -> observation.terrain.shoulderLandRatio() >= 0.25D));
        gates.add(gate("coarse_outer_ocean", "hard", "outer_land_ratio<=0.65",
            "island noise land ratio radius 950", "40 radial samples", true,
            observation -> observation.terrain.outerLandRatio(),
            observation -> observation.terrain.outerLandRatio() <= 0.65D));
        gates.add(gate("shape_interior_land", "hard", "interior_land_ratio>=0.82",
            "exact Minecraft NormalNoise island mask", "64 block grid to radius 1024", true,
            observation -> observation.terrain.interiorLandRatio(),
            observation -> observation.terrain.interiorLandRatio() >= 0.82D));
        gates.add(gate("shape_connected_land", "hard", "connected_land_ratio>=0.95",
            "cardinal flood fill over island mask", "64 block grid", true,
            observation -> observation.terrain.connectedLandRatio(),
            observation -> observation.terrain.connectedLandRatio() >= 0.95D));
        gates.add(gate("shape_continuous_rays", "hard", "continuous_ray_ratio>=0.65",
            "radial coastline transitions", "72 rays at 32 blocks", true,
            observation -> observation.terrain.continuousRayRatio(),
            observation -> observation.terrain.continuousRayRatio() >= 0.65D));
        gates.add(gate("shape_minimum_coast", "hard", "minimum_coast_radius>=384",
            "radial coastline transitions", "72 rays at 32 blocks", true,
            observation -> observation.terrain.minimumCoastRadius(),
            observation -> observation.terrain.minimumCoastRadius() >= 384.0D));
        gates.add(gate("shape_median_coast_low", "hard", "median_coast_radius>=560",
            "radial coastline transitions", "72 rays", true,
            observation -> observation.terrain.medianCoastRadius(),
            observation -> observation.terrain.medianCoastRadius() >= 560.0D));
        gates.add(gate("shape_median_coast_high", "hard", "median_coast_radius<=1000",
            "radial coastline transitions", "72 rays", true,
            observation -> observation.terrain.medianCoastRadius(),
            observation -> observation.terrain.medianCoastRadius() <= 1000.0D));
        gates.add(gate("shape_maximum_coast", "hard", "maximum_coast_radius<=1120",
            "radial coastline transitions", "72 rays", true,
            observation -> observation.terrain.maximumCoastRadius(),
            observation -> observation.terrain.maximumCoastRadius() <= 1120.0D));
        gates.add(gate("mushroom_ratio_low", "quality", "mushroom_ratio>=0.58",
            "surface_regions noise", "64 block surface grid", true,
            observation -> observation.terrain.mushroomRatio(),
            observation -> observation.terrain.mushroomRatio() >= 0.58D));
        gates.add(gate("mushroom_ratio_high", "quality", "mushroom_ratio<=0.78",
            "surface_regions noise", "64 block surface grid", true,
            observation -> observation.terrain.mushroomRatio(),
            observation -> observation.terrain.mushroomRatio() <= 0.78D));
        gates.add(gate("surface_region_contiguity", "hard", "dominant_boilgrounds_ratio>=0.40",
            "largest connected Boilgrounds component", "64 block surface grid", true,
            observation -> observation.terrain.dominantBoilgroundsRatio(),
            observation -> observation.terrain.dominantBoilgroundsRatio() >= 0.40D));
        gates.add(gate("cornucopia_entrance_overlap", "hard", "protected_center_entrances==0",
            "natural entrance noise masks", "8 block grid radius 128", true,
            observation -> observation.prototype.protectedCenterEntranceCandidates(),
            observation -> observation.prototype.protectedCenterEntranceCandidates() == 0));
        gates.add(gate("predicted_lush_volume", "hard", "lush_region_samples>=4",
            "cave macro noise at proxy depth", "8 block grid -240..240", true,
            observation -> observation.prototype.lushRegionSamples(),
            observation -> observation.prototype.lushRegionSamples() >= 4));
        gates.add(gate("predicted_molten_volume", "hard", "molten_region_samples>=4",
            "cave macro noise at proxy depth", "8 block grid -240..240", true,
            observation -> observation.prototype.moltenRegionSamples(),
            observation -> observation.prototype.moltenRegionSamples() >= 4));
        gates.add(gate("predicted_frozen_volume", "hard", "frozen_region_samples>=2",
            "cave macro/detail noise at proxy depth", "8 block grid -240..240", true,
            observation -> observation.prototype.frozenRegionSamples(),
            observation -> observation.prototype.frozenRegionSamples() >= 2));
        gates.add(gate("predicted_quick_boilgrounds_low", "hard", "ratio>=0.10",
            "surface noise over predicted Quick land", "64 block grid -240..240", true,
            observation -> observation.prototype.predictedQuickBoilgroundsRatio(),
            observation -> observation.prototype.predictedQuickBoilgroundsRatio() >= 0.10D));
        gates.add(gate("predicted_quick_boilgrounds_high", "hard", "ratio<=0.55",
            "surface noise over predicted Quick land", "64 block grid -240..240", true,
            observation -> observation.prototype.predictedQuickBoilgroundsRatio(),
            observation -> observation.prototype.predictedQuickBoilgroundsRatio() <= 0.55D));
        return List.copyOf(gates);
    }

    private static Gate gate(
        String id,
        String classification,
        String threshold,
        String source,
        String resolution,
        boolean minecraftNoise,
        ToDoubleFunction<Observation> value,
        Predicate<Observation> passes
    ) {
        return new Gate(
            id,
            classification,
            threshold,
            source,
            resolution,
            minecraftNoise,
            value,
            passes
        );
    }

    private static JsonArray gateInventory(List<Gate> gates) {
        JsonArray result = new JsonArray();
        gates.forEach(gate -> result.add(gate.toJson()));
        return result;
    }

    private static JsonObject observationJson(Observation observation) {
        JsonObject result = new JsonObject();
        result.addProperty("seed", observation.seed);
        result.add("terrain", terrainJson(observation.terrain));
        result.add("prototype", prototypeJson(observation.prototype));
        return result;
    }

    private static JsonObject terrainJson(ValousSeedSelector.Evaluation value) {
        JsonObject result = new JsonObject();
        result.addProperty("center_value", value.centerValue());
        result.addProperty("center_land_ratio", value.centerLandRatio());
        result.addProperty("inner_land_ratio", value.innerLandRatio());
        result.addProperty("shoulder_land_ratio", value.shoulderLandRatio());
        result.addProperty("outer_land_ratio", value.outerLandRatio());
        result.addProperty("mushroom_ratio", value.mushroomRatio());
        result.addProperty("interior_land_ratio", value.interiorLandRatio());
        result.addProperty("connected_land_ratio", value.connectedLandRatio());
        result.addProperty("continuous_ray_ratio", value.continuousRayRatio());
        result.addProperty("minimum_coast_radius", value.minimumCoastRadius());
        result.addProperty("median_coast_radius", value.medianCoastRadius());
        result.addProperty("maximum_coast_radius", value.maximumCoastRadius());
        result.addProperty("dominant_boilgrounds_ratio", value.dominantBoilgroundsRatio());
        return result;
    }

    private static JsonObject prototypeJson(ValousSeedSelector.PrototypeEvaluation value) {
        JsonObject result = new JsonObject();
        result.addProperty("lush_entrance_candidates", value.lushEntranceCandidates());
        result.addProperty("molten_entrance_candidates", value.moltenEntranceCandidates());
        result.addProperty("protected_center_entrance_candidates",
            value.protectedCenterEntranceCandidates());
        result.addProperty("lush_region_samples", value.lushRegionSamples());
        result.addProperty("molten_region_samples", value.moltenRegionSamples());
        result.addProperty("frozen_region_samples", value.frozenRegionSamples());
        result.addProperty("predicted_quick_boilgrounds_ratio",
            value.predictedQuickBoilgroundsRatio());
        result.addProperty("quick_boilgrounds_ratio", value.quickBoilgroundsRatio());
        result.addProperty("actual_lush_samples", value.actualLushSamples());
        result.addProperty("actual_molten_samples", value.actualMoltenSamples());
        result.addProperty("actual_frozen_samples", value.actualFrozenSamples());
        result.addProperty("actual_sculk_samples", value.actualSculkSamples());
        return result;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static double percentile(double[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0.0D;
        }
        int index = Math.min(
            sorted.length - 1,
            Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1)
        );
        return sorted[index];
    }

    private static long splitMix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record Observation(
        long seed,
        ValousSeedSelector.Evaluation terrain,
        ValousSeedSelector.PrototypeEvaluation prototype
    ) {
    }

    private record Gate(
        String id,
        String classification,
        String threshold,
        String source,
        String resolution,
        boolean minecraftNoise,
        ToDoubleFunction<Observation> value,
        Predicate<Observation> passes
    ) {
        double margin(Observation observation) {
            double observed = value.applyAsDouble(observation);
            int lower = threshold.lastIndexOf(">=");
            if (lower >= 0) {
                return observed - Double.parseDouble(threshold.substring(lower + 2));
            }
            int upper = threshold.lastIndexOf("<=");
            if (upper >= 0) {
                return Double.parseDouble(threshold.substring(upper + 2)) - observed;
            }
            if (threshold.endsWith("==0")) {
                return -Math.abs(observed);
            }
            return passes.test(observation) ? 0.0D : -1.0D;
        }

        JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.addProperty("gate_id", id);
            result.addProperty("classification", classification);
            result.addProperty("threshold", threshold);
            result.addProperty("source", source);
            result.addProperty("sampling_resolution", resolution);
            result.addProperty("uses_exact_minecraft_noise", minecraftNoise);
            result.addProperty("mode_specific", id.startsWith("quick_"));
            result.addProperty("original_reason", originalReason(id));
            result.addProperty("obsolete_after_postprocessor", obsoleteAfterPostprocessor(id));
            result.addProperty("protects", protects(id));
            return result;
        }

        private static String originalReason(String id) {
            if (id.contains("center") || id.contains("cornucopia")) {
                return "Protect the Cornucopia and guarantee usable land at the origin.";
            }
            if (id.contains("lush") || id.contains("molten") || id.contains("frozen")) {
                return "Require a meaningful underground biome region for prototype routing.";
            }
            if (id.contains("mushroom") || id.contains("boilgrounds")
                || id.contains("surface_region")) {
                return "Preserve the intended surface-biome composition and readability.";
            }
            return "Preserve the approved island silhouette, connectivity, and coastline.";
        }

        private static boolean obsoleteAfterPostprocessor(String id) {
            return id.contains("lush_entrance")
                || id.contains("molten_return")
                || id.contains("route")
                || id.contains("cave_air");
        }

        private static String protects(String id) {
            if (id.contains("center") || id.contains("cornucopia")) {
                return "gameplay_safety";
            }
            if (id.contains("volume")) {
                return "route_feasibility";
            }
            return "visual_quality";
        }
    }
}
