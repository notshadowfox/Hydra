package hgloot.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.World;

final class ValousSeedSelector {
    private static final int MAX_ATTEMPTS = 20_000;
    private static final int MAX_BIOME_FINALISTS = 16;
    private static final int MAX_TERRAIN_FINALISTS = 3;
    private static final long MAX_PREFILTER_NANOS = 1_500_000_000L;
    // Leave room for the final exact terrain sample, which can take about 1.5 seconds.
    private static final long MAX_SELECTION_NANOS = 3_250_000_000L;
    // The measured Quick policy p95 is about 373 attempts at a 0.8% acceptance rate.
    private static final int QUICK_MAX_ATTEMPTS = 640;
    private static final long QUICK_MAX_SELECTION_NANOS = 10_000_000_000L;
    private static final double MIN_CENTER_NOISE = 0.08D;
    private static final double MIN_CENTER_HEIGHT = 68.0D;
    private static final double MIN_LANDMASS_VALUE = 0.30D;
    private static final double MUSHROOM_THRESHOLD = 0.2D;
    private static final int SHAPE_GRID_STEP = 64;
    private static final int SHAPE_RADIUS = 1024;
    private static final int COAST_RAY_COUNT = 72;
    private static final int COAST_RAY_STEP = 32;

    private static volatile NoiseBridge noiseBridge;
    private static volatile ExactTerrainBridge exactTerrainBridge;

    private ValousSeedSelector() {
    }

    static Selection select() {
        return select(Policy.FULL_PRODUCTION, false);
    }

    static Selection select(boolean allowDiagnosticFallback) {
        return select(Policy.FULL_PRODUCTION, allowDiagnosticFallback);
    }

    static Selection select(Policy policy, boolean allowDiagnosticFallback) {
        Policy resolved = policy == null ? Policy.FULL_PRODUCTION : policy;
        if (resolved == Policy.QUICK_PROTOTYPE) {
            return selectQuick(allowDiagnosticFallback);
        }
        return selectProductionPolicy(resolved, allowDiagnosticFallback);
    }

    private static Selection selectProductionPolicy(
        Policy policy,
        boolean allowDiagnosticFallback
    ) {
        long startedAtNanos = System.nanoTime();
        NoiseBridge bridge = bridge();
        Selection bestHardAccepted = null;
        double bestHardScore = Double.NEGATIVE_INFINITY;
        Selection diagnosticFallback = null;
        double diagnosticFallbackScore = Double.NEGATIVE_INFINITY;
        Evaluation bestShape = null;
        double bestShapeScore = Double.NEGATIVE_INFINITY;
        Evaluation bestLandShape = null;
        double bestLandShapeScore = Double.NEGATIVE_INFINITY;
        int coarseCandidates = 0;
        int landShapeCandidates = 0;
        int biomeCandidates = 0;
        int attempted = 0;
        List<PrefilteredCandidate> finalists = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1_000 && System.nanoTime() - startedAtNanos >= MAX_PREFILTER_NANOS) {
                break;
            }
            attempted = attempt;
            long seed = ThreadLocalRandom.current().nextLong();
            Evaluation coarseEvaluation = bridge.evaluate(seed);
            if (!coarseEvaluation.coarseAccepted()) {
                continue;
            }
            coarseCandidates++;
            coarseEvaluation = bridge.evaluateShape(seed, coarseEvaluation);
            double shapeScore = coarseEvaluation.qualityScore();
            if (shapeScore > bestShapeScore) {
                bestShape = coarseEvaluation;
                bestShapeScore = shapeScore;
            }
            double landShapeScore = coarseEvaluation.landShapeScore();
            if (landShapeScore > bestLandShapeScore) {
                bestLandShape = coarseEvaluation;
                bestLandShapeScore = landShapeScore;
            }
            if (coarseEvaluation.landShapeAccepted()) {
                landShapeCandidates++;
            }
            if (coarseEvaluation.biomeShapeAccepted()) {
                biomeCandidates++;
            }
            if (!coarseEvaluation.prefilterViable()) {
                continue;
            }
            PrototypeEvaluation prototype = bridge.evaluatePrototype(seed);
            if (!prototype.hardPredictedAccepted()) {
                continue;
            }
            finalists.add(new PrefilteredCandidate(seed, attempt, coarseEvaluation, prototype));
        }

        finalists.sort(Comparator.comparingDouble(
            (PrefilteredCandidate candidate) -> candidate.evaluation().qualityScore()
                - (Math.abs(0.325D - candidate.prototype().predictedQuickBoilgroundsRatio()) * 10.0D)
        ).reversed());
        int biomeFinalists = 0;
        int acceptedBiomeFinalists = 0;
        int terrainFinalists = 0;
        PrototypeEvaluation bestActualPrototype = null;
        double bestActualPrototypeScore = Double.NEGATIVE_INFINITY;
        for (PrefilteredCandidate finalist : finalists) {
            if (biomeFinalists >= MAX_BIOME_FINALISTS
                || System.nanoTime() - startedAtNanos >= MAX_SELECTION_NANOS) {
                break;
            }
            biomeFinalists++;
            PrototypeEvaluation prototype = exactBridge().evaluatePrototypeBiomes(
                finalist.seed(),
                finalist.prototype()
            );
            double actualPrototypeScore = prototypeScore(prototype);
            if (actualPrototypeScore > bestActualPrototypeScore) {
                bestActualPrototype = prototype;
                bestActualPrototypeScore = actualPrototypeScore;
            }
            if (!prototype.hardAccepted()) {
                continue;
            }
            acceptedBiomeFinalists++;
            if (terrainFinalists >= MAX_TERRAIN_FINALISTS) {
                break;
            }
            terrainFinalists++;
            Evaluation exactEvaluation = exactBridge().evaluate(
                finalist.seed(),
                finalist.evaluation()
            );
            boolean hardAccepted = exactEvaluation.hardAccepted();
            if (!hardAccepted
                && allowDiagnosticFallback
                && exactEvaluation.diagnosticFallbackEligible()
                && exactEvaluation.qualityScore() > diagnosticFallbackScore) {
                diagnosticFallback = new Selection(
                    policy,
                    finalist.seed(),
                    attempted,
                    exactEvaluation,
                    prototype,
                    prototypeScore(prototype) + exactEvaluation.qualityScore(),
                    1,
                    List.of(),
                    null,
                    false,
                    true,
                    exactEvaluation.failedHardChecks(),
                    exactEvaluation.failedSoftChecks(prototype),
                    elapsedMillis(startedAtNanos)
                );
                diagnosticFallbackScore = exactEvaluation.qualityScore();
            }
            if (!hardAccepted) {
                continue;
            }
            boolean preferred = exactEvaluation.accepted() && prototype.softAccepted();
            Selection acceptedSelection = new Selection(
                policy,
                finalist.seed(),
                attempted,
                exactEvaluation,
                prototype,
                prototypeScore(prototype) + exactEvaluation.qualityScore(),
                1,
                List.of(),
                null,
                preferred,
                false,
                List.of(),
                exactEvaluation.failedSoftChecks(prototype),
                elapsedMillis(startedAtNanos)
            );
            if (preferred) {
                return new Selection(
                    policy,
                    finalist.seed(),
                    attempted,
                    exactEvaluation,
                    prototype,
                    acceptedSelection.softScore(),
                    1,
                    List.of(),
                    null,
                    true,
                    false,
                    List.of(),
                    List.of(),
                    elapsedMillis(startedAtNanos)
                );
            }
            double hardScore = exactEvaluation.qualityScore() + prototypeScore(prototype);
            if (hardScore > bestHardScore) {
                bestHardAccepted = acceptedSelection;
                bestHardScore = hardScore;
            }
        }
        if (bestHardAccepted != null) {
            return new Selection(
                policy,
                bestHardAccepted.seed(),
                attempted,
                bestHardAccepted.evaluation(),
                bestHardAccepted.prototype(),
                bestHardAccepted.softScore(),
                bestHardAccepted.hardCandidateCount(),
                bestHardAccepted.candidateComparisons(),
                bestHardAccepted.plannerFeed(),
                false,
                false,
                List.of(),
                bestHardAccepted.failedSoftChecks(),
                elapsedMillis(startedAtNanos)
            );
        }
        if (allowDiagnosticFallback && diagnosticFallback != null) {
            return new Selection(
                policy,
                diagnosticFallback.seed(),
                attempted,
                diagnosticFallback.evaluation(),
                diagnosticFallback.prototype(),
                diagnosticFallback.softScore(),
                diagnosticFallback.hardCandidateCount(),
                diagnosticFallback.candidateComparisons(),
                diagnosticFallback.plannerFeed(),
                false,
                true,
                diagnosticFallback.failedHardChecks(),
                diagnosticFallback.failedSoftChecks(),
                elapsedMillis(startedAtNanos)
            );
        }
        throw new IllegalStateException(
            "No acceptable Valous island seed found after " + attempted
                + " attempts. Best shape=" + bestShape
                + ", best land=" + bestLandShape
                + ", coarse_candidates=" + coarseCandidates
                + ", land_candidates=" + landShapeCandidates
                + ", biome_candidates=" + biomeCandidates
                + ", predicted_finalists=" + finalists.size()
                + ", exact_biome_checked=" + biomeFinalists
                + ", exact_biome_accepted=" + acceptedBiomeFinalists
                + ", exact_terrain_checked=" + terrainFinalists
                + ", best_actual_prototype=" + bestActualPrototype
                + ", diagnostic_fallback_allowed=" + allowDiagnosticFallback
        );
    }

    private static Selection selectQuick(boolean allowDiagnosticFallback) {
        long startedAtNanos = System.nanoTime();
        List<QuickCandidate> accepted = new ArrayList<>();
        int attempted = 0;
        for (int attempt = 1; attempt <= QUICK_MAX_ATTEMPTS; attempt++) {
            if (attempt > 1 && System.nanoTime() - startedAtNanos >= QUICK_MAX_SELECTION_NANOS) {
                break;
            }
            attempted = attempt;
            long seed = ThreadLocalRandom.current().nextLong();
            QuickCandidate candidate = evaluateQuickCandidate(seed);
            if (candidate != null) {
                accepted.add(candidate);
                if (accepted.size() >= 4) {
                    break;
                }
            }
        }
        if (accepted.isEmpty()) {
            throw new IllegalStateException(
                "No QUICK_PROTOTYPE Valous seed passed hard gates after " + attempted
                    + " attempts. diagnostic_fallback_allowed=" + allowDiagnosticFallback
                    + " diagnostic_fallback_used=false"
            );
        }
        accepted.sort(Comparator.comparingDouble(QuickCandidate::softScore).reversed());
        QuickCandidate selected = accepted.get(0);
        List<CandidateComparison> comparisons = accepted.stream()
            .map(candidate -> new CandidateComparison(
                candidate.seed(),
                candidate.softScore(),
                candidate.evaluation().qualityScore(),
                candidate.prototype().quickBoilgroundsRatio(),
                candidate.prototype().lushEntranceCandidates(),
                candidate.prototype().moltenEntranceCandidates(),
                candidate.plannerFeed().lushMoltenPairs(),
                candidate.plannerFeed().containedSculkCenters()
            ))
            .toList();
        return new Selection(
            Policy.QUICK_PROTOTYPE,
            selected.seed(),
            attempted,
            selected.evaluation(),
            selected.prototype(),
            selected.softScore(),
            accepted.size(),
            comparisons,
            selected.plannerFeed(),
            false,
            false,
            List.of(),
            quickSoftFailures(selected),
            elapsedMillis(startedAtNanos)
        );
    }

    private static QuickCandidate evaluateQuickCandidate(long seed) {
        NoiseBridge noise = bridge();
        Evaluation proxy = noise.evaluate(seed);
        if (proxy.centerValue() < -0.05D || proxy.centerLandRatio() < 0.65D) {
            return null;
        }
        PrototypeEvaluation predicted = noise.evaluatePrototype(seed);
        if (predicted.protectedCenterEntranceCandidates() != 0
            || predicted.lushRegionSamples() < 32
            || predicted.moltenRegionSamples() < 32
            || predicted.frozenRegionSamples() < 16
            || predicted.predictedQuickBoilgroundsRatio() < 0.10D
            || predicted.predictedQuickBoilgroundsRatio() > 0.55D) {
            return null;
        }
        Evaluation exactTerrain = exactBridge().evaluate(seed, proxy);
        if (exactTerrain.centerValue() < MIN_CENTER_HEIGHT
            || exactTerrain.centerLandRatio() < 0.75D) {
            return null;
        }
        PrototypeEvaluation exactPrototype =
            exactBridge().evaluatePrototypeBiomes(seed, predicted);
        if (exactPrototype.actualLushSamples() < 2
            || exactPrototype.actualMoltenSamples() < 2
            || exactPrototype.actualFrozenSamples() < 2
            || exactPrototype.actualSculkSamples() < 2
            || exactPrototype.quickCoastShare() > 0.45D
            || exactPrototype.quickBoilgroundsRatio() < 0.15D
            || exactPrototype.quickBoilgroundsRatio() > 0.50D) {
            return null;
        }
        StaticPlannerFeed plannerFeed = exactBridge().evaluateStaticPlannerFeed(seed);
        if (!plannerFeed.completePlanFeasible()
            || plannerFeed.validLushCenters() < 1
            || plannerFeed.validMoltenCenters() < 1
            || plannerFeed.containedSculkCenters() < 1
            || plannerFeed.lushMoltenPairs() < 1) {
            return null;
        }
        double score = quickSoftScore(exactTerrain, exactPrototype, plannerFeed);
        return new QuickCandidate(seed, exactTerrain, exactPrototype, plannerFeed, score);
    }

    private static double quickSoftScore(
        Evaluation terrain,
        PrototypeEvaluation prototype,
        StaticPlannerFeed plannerFeed
    ) {
        double naturalRouteBonus = Math.min(4, prototype.lushEntranceCandidates())
            + Math.min(4, prototype.moltenEntranceCandidates());
        double plannerBonus = Math.min(4.0D, plannerFeed.lushMoltenPairs() / 500.0D);
        return terrain.qualityScore()
            + prototypeScore(prototype)
            + naturalRouteBonus
            + plannerBonus;
    }

    private static List<String> quickSoftFailures(QuickCandidate candidate) {
        List<String> failures = new ArrayList<>();
        Evaluation terrain = candidate.evaluation();
        PrototypeEvaluation prototype = candidate.prototype();
        if (!terrain.accepted()) {
            failures.add("full_island_visual_quality");
        }
        if (prototype.quickBoilgroundsRatio() < 0.30D
            || prototype.quickBoilgroundsRatio() > 0.35D) {
            failures.add("ideal_boilgrounds_ratio");
        }
        if (prototype.lushEntranceCandidates() == 0) {
            failures.add("natural_lush_entrance_absent");
        }
        if (prototype.moltenEntranceCandidates() == 0) {
            failures.add("natural_molten_return_absent");
        }
        return List.copyOf(failures);
    }

    static SampleReport sample(int requestedSamples) {
        int samples = Math.max(1_000, requestedSamples);
        long startedAtNanos = System.nanoTime();
        NoiseBridge bridge = bridge();
        int coarseAccepted = 0;
        int islandHardCandidates = 0;
        int predictedHardAccepted = 0;
        Map<String, Integer> rejects = new LinkedHashMap<>();
        for (int index = 0; index < samples; index++) {
            long seed = ThreadLocalRandom.current().nextLong();
            Evaluation coarse = bridge.evaluate(seed);
            if (!coarse.coarseAccepted()) {
                rejects.merge("coarse_island", 1, Integer::sum);
                continue;
            }
            coarseAccepted++;
            Evaluation shaped = bridge.evaluateShape(seed, coarse);
            if (!shaped.prefilterViable()) {
                rejects.merge("island_hard_prefilter", 1, Integer::sum);
                continue;
            }
            islandHardCandidates++;
            PrototypeEvaluation prototype = bridge.evaluatePrototype(seed);
            List<String> failures = prototype.failedPredictedHardChecks();
            if (!failures.isEmpty()) {
                failures.forEach(reason -> rejects.merge(reason, 1, Integer::sum));
                continue;
            }
            predictedHardAccepted++;
        }
        return new SampleReport(
            samples,
            coarseAccepted,
            islandHardCandidates,
            predictedHardAccepted,
            predictedHardAccepted / (double) samples,
            Map.copyOf(rejects),
            elapsedMillis(startedAtNanos)
        );
    }

    static PolicySampleReport samplePolicy(Policy policy, int requestedSamples) {
        Policy resolved = policy == null ? Policy.FULL_PRODUCTION : policy;
        if (resolved != Policy.QUICK_PROTOTYPE) {
            throw new IllegalArgumentException(
                "Deterministic live-policy regression is currently available for QUICK_PROTOTYPE only."
            );
        }
        int samples = Math.max(10_000, requestedSamples);
        long startedAt = System.nanoTime();
        Map<String, Integer> rejects = new LinkedHashMap<>();
        int accepted = 0;
        int staticFeedCandidates = 0;
        int staticPairCandidates = 0;
        for (int index = 0; index < samples; index++) {
            long seed = splitMix64(0x56414C4F55534C35L + index);
            NoiseBridge noise = bridge();
            Evaluation proxy = noise.evaluate(seed);
            boolean proxyPass = true;
            if (proxy.centerValue() < -0.05D) {
                rejects.merge("quick_origin_signal", 1, Integer::sum);
                proxyPass = false;
            }
            if (proxy.centerLandRatio() < 0.65D) {
                rejects.merge("quick_center_land", 1, Integer::sum);
                proxyPass = false;
            }
            PrototypeEvaluation predicted = noise.evaluatePrototype(seed);
            if (predicted.protectedCenterEntranceCandidates() != 0) {
                rejects.merge("quick_cornucopia_breach", 1, Integer::sum);
                proxyPass = false;
            }
            if (predicted.lushRegionSamples() < 32) {
                rejects.merge("quick_lush_proxy_volume", 1, Integer::sum);
                proxyPass = false;
            }
            if (predicted.moltenRegionSamples() < 32) {
                rejects.merge("quick_molten_proxy_volume", 1, Integer::sum);
                proxyPass = false;
            }
            if (predicted.frozenRegionSamples() < 16) {
                rejects.merge("quick_frozen_proxy_volume", 1, Integer::sum);
                proxyPass = false;
            }
            if (predicted.predictedQuickBoilgroundsRatio() < 0.10D
                || predicted.predictedQuickBoilgroundsRatio() > 0.55D) {
                rejects.merge("quick_surface_proxy_coverage", 1, Integer::sum);
                proxyPass = false;
            }
            if (!proxyPass) {
                continue;
            }
            Evaluation exactTerrain = exactBridge().evaluate(seed, proxy);
            if (exactTerrain.centerValue() < MIN_CENTER_HEIGHT
                || exactTerrain.centerLandRatio() < 0.75D) {
                rejects.merge("quick_exact_origin_safety", 1, Integer::sum);
                continue;
            }
            PrototypeEvaluation exactPrototype =
                exactBridge().evaluatePrototypeBiomes(seed, predicted);
            if (exactPrototype.actualLushSamples() < 2
                || exactPrototype.actualMoltenSamples() < 2
                || exactPrototype.actualFrozenSamples() < 2
                || exactPrototype.actualSculkSamples() < 2
                || exactPrototype.quickBoilgroundsRatio() < 0.15D
                || exactPrototype.quickBoilgroundsRatio() > 0.50D) {
                rejects.merge("quick_exact_biome_volume", 1, Integer::sum);
                continue;
            }
            StaticPlannerFeed feed = exactBridge().evaluateStaticPlannerFeed(seed);
            staticFeedCandidates++;
            if (feed.lushMoltenPairs() > 0) {
                staticPairCandidates++;
            }
            if (!feed.completePlanFeasible()
                || feed.validLushCenters() < 1
                || feed.validMoltenCenters() < 1
                || feed.containedSculkCenters() < 1) {
                rejects.merge("quick_static_planner_containment", 1, Integer::sum);
                continue;
            }
            accepted++;
        }
        long elapsedMs = elapsedMillis(startedAt);
        double rate = accepted / (double) samples;
        double medianAttempts = rate <= 0.0D
            ? Double.POSITIVE_INFINITY
            : Math.log(0.5D) / Math.log(1.0D - rate);
        double p95Attempts = rate <= 0.0D
            ? Double.POSITIVE_INFINITY
            : Math.log(0.05D) / Math.log(1.0D - rate);
        return new PolicySampleReport(
            resolved,
            samples,
            accepted,
            rate,
            medianAttempts,
            p95Attempts,
            elapsedMs,
            elapsedMs / (double) samples,
            staticFeedCandidates,
            staticPairCandidates,
            Map.copyOf(rejects)
        );
    }

    private static long splitMix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    static Evaluation evaluateDetailedProxy(long seed) {
        NoiseBridge activeBridge = bridge();
        return activeBridge.evaluateShape(seed, activeBridge.evaluate(seed));
    }

    static PrototypeEvaluation evaluatePrototypeProxy(long seed) {
        return bridge().evaluatePrototype(seed);
    }

    static PrototypeEvaluation evaluateExactPrototype(
        long seed,
        PrototypeEvaluation predicted
    ) {
        return exactBridge().evaluatePrototypeBiomes(seed, predicted);
    }

    static Evaluation evaluateExactTerrain(long seed, Evaluation proxy) {
        return exactBridge().evaluate(seed, proxy);
    }

    static StaticPlannerFeed evaluateStaticPlannerFeed(long seed) {
        return exactBridge().evaluateStaticPlannerFeed(seed);
    }

    static void prepare() {
        bridge();
        exactBridge();
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static double prototypeScore(PrototypeEvaluation prototype) {
        return prototype.actualLushSamples()
            + prototype.actualMoltenSamples()
            + prototype.actualFrozenSamples()
            + prototype.actualSculkSamples()
            - (Math.abs(0.325D - prototype.quickBoilgroundsRatio()) * 100.0D);
    }

    private static NoiseBridge bridge() {
        NoiseBridge current = noiseBridge;
        if (current != null) {
            return current;
        }
        synchronized (ValousSeedSelector.class) {
            if (noiseBridge == null) {
                noiseBridge = NoiseBridge.create();
            }
            return noiseBridge;
        }
    }

    private static ExactTerrainBridge exactBridge() {
        ExactTerrainBridge current = exactTerrainBridge;
        if (current != null) {
            return current;
        }
        synchronized (ValousSeedSelector.class) {
            if (exactTerrainBridge == null) {
                exactTerrainBridge = ExactTerrainBridge.create();
            }
            return exactTerrainBridge;
        }
    }

    enum Policy {
        QUICK_PROTOTYPE("quick_prototype"),
        MEDIUM_VALIDATION("medium_validation"),
        FULL_PRODUCTION("full_production");

        private final String id;

        Policy(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    record Selection(
        Policy policy,
        long seed,
        int attempts,
        Evaluation evaluation,
        PrototypeEvaluation prototype,
        double softScore,
        int hardCandidateCount,
        List<CandidateComparison> candidateComparisons,
        StaticPlannerFeed plannerFeed,
        boolean preferred,
        boolean diagnosticFallback,
        List<String> failedHardChecks,
        List<String> failedSoftChecks,
        long selectionMs
    ) {
    }

    record SampleReport(
        int sampledSeeds,
        int coarseAccepted,
        int islandHardCandidates,
        int predictedHardAccepted,
        double predictedHardAcceptanceRate,
        Map<String, Integer> rejectionCounts,
        long sampleMs
    ) {
    }

    record PolicySampleReport(
        Policy policy,
        int sampledSeeds,
        int hardAccepted,
        double hardAcceptanceRate,
        double medianAttempts,
        double p95Attempts,
        long sampleMs,
        double averageSeedMs,
        int staticFeedCandidates,
        int staticPairCandidates,
        Map<String, Integer> rejectionCounts
    ) {
    }

    record StaticPlannerFeed(
        int lushRegions,
        int moltenRegions,
        int frozenRegions,
        int sculkRegions,
        int lushMoltenPairs,
        int validLushCenters,
        int validMoltenCenters,
        int containedSculkCenters,
        boolean completePlanFeasible
    ) {
    }

    record CandidateComparison(
        long seed,
        double softScore,
        double islandQualityScore,
        double quickBoilgroundsRatio,
        int naturalLushEntrances,
        int naturalMoltenRoutes,
        int lushMoltenPairs,
        int containedSculkCenters
    ) {
    }

    private record QuickCandidate(
        long seed,
        Evaluation evaluation,
        PrototypeEvaluation prototype,
        StaticPlannerFeed plannerFeed,
        double softScore
    ) {
    }

    private record PrefilteredCandidate(
        long seed,
        int attempt,
        Evaluation evaluation,
        PrototypeEvaluation prototype
    ) {
    }

    record PrototypeEvaluation(
        int lushEntranceCandidates,
        int moltenEntranceCandidates,
        int protectedCenterEntranceCandidates,
        int lushRegionSamples,
        int moltenRegionSamples,
        int frozenRegionSamples,
        double predictedQuickBoilgroundsRatio,
        double quickBoilgroundsRatio,
        double quickCoastShare,
        int actualLushSamples,
        int actualMoltenSamples,
        int actualFrozenSamples,
        int actualSculkSamples
    ) {
        boolean hardPredictedAccepted() {
            return failedPredictedHardChecks().isEmpty();
        }

        List<String> failedPredictedHardChecks() {
            List<String> failures = new ArrayList<>();
            if (protectedCenterEntranceCandidates != 0) {
                failures.add("cornucopia_entrance_overlap");
            }
            if (lushRegionSamples < 4) {
                failures.add("lush_volume");
            }
            if (moltenRegionSamples < 4) {
                failures.add("molten_volume");
            }
            if (frozenRegionSamples < 2) {
                failures.add("frozen_volume");
            }
            if (predictedQuickBoilgroundsRatio < 0.10D
                || predictedQuickBoilgroundsRatio > 0.55D) {
                failures.add("surface_biome_coverage");
            }
            return List.copyOf(failures);
        }

        boolean hardAccepted() {
            return hardPredictedAccepted()
                && quickBoilgroundsRatio >= 0.10D
                && quickBoilgroundsRatio <= 0.55D
                && quickCoastShare <= 0.45D
                && actualLushSamples >= 2
                && actualMoltenSamples >= 2
                && actualFrozenSamples >= 2
                && actualSculkSamples >= 2;
        }

        boolean softAccepted() {
            return quickBoilgroundsRatio >= 0.15D
                && quickBoilgroundsRatio <= 0.50D;
        }

        PrototypeEvaluation withActualBiomeSamples(
            double boilgroundsRatio,
            double coastShare,
            int lushSamples,
            int moltenSamples,
            int frozenSamples,
            int sculkSamples
        ) {
            return new PrototypeEvaluation(
                lushEntranceCandidates,
                moltenEntranceCandidates,
                protectedCenterEntranceCandidates,
                lushRegionSamples,
                moltenRegionSamples,
                frozenRegionSamples,
                predictedQuickBoilgroundsRatio,
                boilgroundsRatio,
                coastShare,
                lushSamples,
                moltenSamples,
                frozenSamples,
                sculkSamples
            );
        }
    }

    record Evaluation(
        double centerValue,
        double centerLandRatio,
        double innerLandRatio,
        double shoulderLandRatio,
        double outerLandRatio,
        double mushroomRatio,
        double interiorLandRatio,
        double connectedLandRatio,
        double continuousRayRatio,
        double minimumCoastRadius,
        double medianCoastRadius,
        double maximumCoastRadius,
        double dominantBoilgroundsRatio
    ) {
        boolean coarseAccepted() {
            return centerValue >= 0.0D
                && centerLandRatio >= 0.75D
                && innerLandRatio >= 0.60D
                && shoulderLandRatio >= 0.25D
                && outerLandRatio <= 0.65D;
        }

        boolean shapeAccepted() {
            return landShapeAccepted() && biomeShapeAccepted();
        }

        boolean landShapeAccepted() {
            return interiorLandRatio >= 0.88D
                && connectedLandRatio >= 0.98D
                && continuousRayRatio >= 0.78D
                && minimumCoastRadius >= 480.0D
                && medianCoastRadius >= 680.0D
                && medianCoastRadius <= 920.0D
                && maximumCoastRadius <= 1056.0D;
        }

        boolean biomeShapeAccepted() {
            return mushroomRatio >= 0.58D
                && mushroomRatio <= 0.78D
                && dominantBoilgroundsRatio >= 0.80D;
        }

        boolean prefilterViable() {
            return interiorLandRatio >= 0.82D
                && connectedLandRatio >= 0.95D
                && continuousRayRatio >= 0.65D
                && minimumCoastRadius >= 384.0D
                && medianCoastRadius >= 560.0D
                && medianCoastRadius <= 1000.0D
                && maximumCoastRadius <= 1120.0D;
        }

        double landShapeScore() {
            return (interiorLandRatio * 5.0D)
                + (connectedLandRatio * 4.0D)
                + (continuousRayRatio * 4.0D)
                + (Math.min(minimumCoastRadius, 640.0D) / 160.0D)
                - (Math.abs(820.0D - medianCoastRadius) / 200.0D)
                - (Math.max(0.0D, maximumCoastRadius - 1024.0D) / 64.0D);
        }

        boolean accepted() {
            return centerValue >= MIN_CENTER_HEIGHT
                && centerLandRatio >= 0.875D
                && innerLandRatio >= 0.875D
                && shoulderLandRatio >= 0.5625D
                && outerLandRatio <= 0.375D
                && shapeAccepted();
        }

        boolean viable() {
            return centerValue >= MIN_CENTER_HEIGHT
                && centerLandRatio >= 0.75D
                && innerLandRatio >= 0.75D
                && shoulderLandRatio >= 0.35D
                && outerLandRatio <= 0.55D
                && prefilterViable()
                && dominantBoilgroundsRatio >= 0.40D;
        }

        boolean hardAccepted() {
            return viable();
        }

        boolean diagnosticFallbackEligible() {
            return centerValue >= MIN_CENTER_HEIGHT
                && centerLandRatio >= 0.75D
                && innerLandRatio >= 0.60D
                && shoulderLandRatio >= 0.25D
                && outerLandRatio <= 0.65D
                && prefilterViable()
                && dominantBoilgroundsRatio >= 0.35D;
        }

        List<String> failedHardChecks() {
            List<String> failures = new ArrayList<>();
            if (centerValue < MIN_CENTER_HEIGHT
                || centerLandRatio < 0.75D
                || innerLandRatio < 0.75D) {
                failures.add("island_center_safety");
            }
            if (shoulderLandRatio < 0.35D || outerLandRatio > 0.55D) {
                failures.add("island_playable_coverage");
            }
            if (!prefilterViable()) {
                failures.add("island_connectivity_bounds");
            }
            if (dominantBoilgroundsRatio < 0.40D) {
                failures.add("surface_region_contiguity");
            }
            return List.copyOf(failures);
        }

        List<String> failedSoftChecks(PrototypeEvaluation prototype) {
            List<String> failures = new ArrayList<>();
            if (!accepted()) {
                failures.add("preferred_island_quality");
            }
            if (!prototype.softAccepted()) {
                failures.add("preferred_boilgrounds_ratio");
            }
            return List.copyOf(failures);
        }

        double qualityScore() {
            double centerBonus = centerValue >= MIN_CENTER_HEIGHT ? 1.0D : 0.0D;
            return centerBonus
                + (centerLandRatio * 3.0D)
                + (innerLandRatio * 3.0D)
                + (shoulderLandRatio * 2.0D)
                - (outerLandRatio * 3.0D)
                + (interiorLandRatio * 4.0D)
                + (connectedLandRatio * 3.0D)
                + (continuousRayRatio * 3.0D)
                + (dominantBoilgroundsRatio * 2.0D)
                - (Math.abs(0.675D - mushroomRatio) * 5.0D);
        }
    }

    private static final class ExactTerrainBridge {
        private static final int SEA_LEVEL = 63;
        private static final int MIN_Y = -208;
        private static final int WORLD_HEIGHT = 720;

        private final Object registryProvider;
        private final Object noiseSettingsKey;
        private final Object generator;
        private final Object heightAccessor;
        private final Object worldSurfaceHeightmap;
        private final Method randomStateCreate;
        private final Method randomStateSampler;
        private final Method getBaseHeight;
        private final Method getNoiseBiome;
        private final Method holderUnwrapKey;
        private final Method resourceKeyIdentifier;

        private ExactTerrainBridge(
            Object registryProvider,
            Object noiseSettingsKey,
            Object generator,
            Object heightAccessor,
            Object worldSurfaceHeightmap,
            Method randomStateCreate,
            Method randomStateSampler,
            Method getBaseHeight,
            Method getNoiseBiome,
            Method holderUnwrapKey,
            Method resourceKeyIdentifier
        ) {
            this.registryProvider = registryProvider;
            this.noiseSettingsKey = noiseSettingsKey;
            this.generator = generator;
            this.heightAccessor = heightAccessor;
            this.worldSurfaceHeightmap = worldSurfaceHeightmap;
            this.randomStateCreate = randomStateCreate;
            this.randomStateSampler = randomStateSampler;
            this.getBaseHeight = getBaseHeight;
            this.getNoiseBiome = getNoiseBiome;
            this.holderUnwrapKey = holderUnwrapKey;
            this.resourceKeyIdentifier = resourceKeyIdentifier;
        }

        static ExactTerrainBridge create() {
            try {
                if (Bukkit.getWorlds().isEmpty()) {
                    throw new IllegalStateException("No loaded world is available for Valous registry access.");
                }
                Object craftServer = Bukkit.getServer();
                Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);

                Class<?> providerClass = Class.forName("net.minecraft.core.HolderGetter$Provider");
                Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
                Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
                Class<?> holderClass = Class.forName("net.minecraft.core.Holder");
                Class<?> biomeSourceClass = Class.forName("net.minecraft.world.level.biome.BiomeSource");
                Class<?> randomStateClass = Class.forName(
                    "net.minecraft.world.level.levelgen.RandomState");
                Class<?> noiseGeneratorClass = Class.forName(
                    "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator");
                Class<?> heightmapTypeClass = Class.forName(
                    "net.minecraft.world.level.levelgen.Heightmap$Types");
                Class<?> levelHeightAccessorClass = Class.forName(
                    "net.minecraft.world.level.LevelHeightAccessor");

                Object provider = minecraftServer.getClass().getMethod("registryAccess")
                    .invoke(minecraftServer);
                Object noiseSettingsRegistry = Class.forName("net.minecraft.core.registries.Registries")
                    .getField("NOISE_SETTINGS")
                    .get(null);
                Object identifier = identifierClass.getMethod("parse", String.class)
                    .invoke(null, "hydra_valous:valous");
                Object settingsKey = resourceKeyClass
                    .getMethod("create", resourceKeyClass, identifierClass)
                    .invoke(null, noiseSettingsRegistry, identifier);
                Object holderGetter = providerClass
                    .getMethod("lookupOrThrow", resourceKeyClass)
                    .invoke(provider, noiseSettingsRegistry);
                Object settingsHolder = Class.forName("net.minecraft.core.HolderGetter")
                    .getMethod("getOrThrow", resourceKeyClass)
                    .invoke(holderGetter, settingsKey);

                Object worldPresetRegistry = Class.forName("net.minecraft.core.registries.Registries")
                    .getField("WORLD_PRESET")
                    .get(null);
                Object presetIdentifier = identifierClass.getMethod("parse", String.class)
                    .invoke(null, "hydra_valous:valous");
                Object presetKey = resourceKeyClass
                    .getMethod("create", resourceKeyClass, identifierClass)
                    .invoke(null, worldPresetRegistry, presetIdentifier);
                Object presetGetter = providerClass
                    .getMethod("lookupOrThrow", resourceKeyClass)
                    .invoke(provider, worldPresetRegistry);
                Object presetHolder = Class.forName("net.minecraft.core.HolderGetter")
                    .getMethod("getOrThrow", resourceKeyClass)
                    .invoke(presetGetter, presetKey);
                Object preset = holderClass.getMethod("value").invoke(presetHolder);
                Optional<?> overworld = (Optional<?>) preset.getClass()
                    .getMethod("overworld")
                    .invoke(preset);
                Object levelStem = overworld.orElseThrow(
                    () -> new IllegalStateException("Valous world preset has no Overworld stem.")
                );
                Object generator = levelStem.getClass().getMethod("generator").invoke(levelStem);
                Object source = generator.getClass().getMethod("getBiomeSource").invoke(generator);

                Object accessor = levelHeightAccessorClass.getMethod("create", int.class, int.class)
                    .invoke(null, MIN_Y, WORLD_HEIGHT);
                Field worldSurfaceField = heightmapTypeClass.getField("WORLD_SURFACE_WG");
                Object worldSurface = worldSurfaceField.get(null);

                return new ExactTerrainBridge(
                    provider,
                    settingsKey,
                    generator,
                    accessor,
                    worldSurface,
                    randomStateClass.getMethod(
                        "create",
                        providerClass,
                        resourceKeyClass,
                        long.class
                    ),
                    randomStateClass.getMethod("sampler"),
                    noiseGeneratorClass.getMethod(
                        "getBaseHeight",
                        int.class,
                        int.class,
                        heightmapTypeClass,
                        levelHeightAccessorClass,
                        randomStateClass
                    ),
                    biomeSourceClass.getMethod(
                        "getNoiseBiome",
                        int.class,
                        int.class,
                        int.class,
                        Class.forName("net.minecraft.world.level.biome.Climate$Sampler")
                    ),
                    holderClass.getMethod("unwrapKey"),
                    resourceKeyClass.getMethod("identifier")
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(
                    "Paper 1.21.11 exact Valous terrain bridge is unavailable; "
                        + "refusing unvalidated terrain generation.",
                    ex
                );
            }
        }

        Evaluation evaluate(long seed, Evaluation coarse) {
            try {
                Object randomState = randomStateCreate.invoke(
                    null,
                    registryProvider,
                    noiseSettingsKey,
                    seed
                );
                return new Evaluation(
                    baseHeight(generator, randomState, 0, 0),
                    landRatio(generator, randomState, 160, 8),
                    landRatio(generator, randomState, 500, 12),
                    landRatio(generator, randomState, 750, 16),
                    landRatio(generator, randomState, 950, 20),
                    coarse.mushroomRatio(),
                    coarse.interiorLandRatio(),
                    coarse.connectedLandRatio(),
                    coarse.continuousRayRatio(),
                    coarse.minimumCoastRadius(),
                    coarse.medianCoastRadius(),
                    coarse.maximumCoastRadius(),
                    coarse.dominantBoilgroundsRatio()
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed evaluating exact Valous terrain height.", ex);
            }
        }

        PrototypeEvaluation evaluatePrototypeBiomes(long seed, PrototypeEvaluation predicted) {
            try {
                Object randomState = randomStateCreate.invoke(
                    null,
                    registryProvider,
                    noiseSettingsKey,
                    seed
                );
                Object sampler = randomStateSampler.invoke(randomState);
                Object biomeSource = generator.getClass().getMethod("getBiomeSource").invoke(generator);
                int surfaceLand = 0;
                int boilgrounds = 0;
                int mushroom = 0;
                int coast = 0;
                int lush = 0;
                int molten = 0;
                int frozen = 0;
                int sculk = 0;
                // Match the validator's surface grid so narrow coast failures are
                // rejected before pregeneration instead of after a completed world.
                for (int z = -240; z <= 240; z += 8) {
                    for (int x = -240; x <= 240; x += 8) {
                        String surfaceBiome = biomeKey(biomeSource, sampler, x, 80, z);
                        if (ValousPrototypeValidator.MUSHROOM_THICKET.equals(surfaceBiome)
                            || ValousPrototypeValidator.MUSHROOM_THICKET_COAST.equals(surfaceBiome)
                            || ValousPrototypeValidator.BOILGROUNDS.equals(surfaceBiome)) {
                            surfaceLand++;
                            if (ValousPrototypeValidator.BOILGROUNDS.equals(surfaceBiome)) {
                                boilgrounds++;
                            } else if (ValousPrototypeValidator.MUSHROOM_THICKET.equals(surfaceBiome)) {
                                mushroom++;
                            } else if (ValousPrototypeValidator.MUSHROOM_THICKET_COAST.equals(surfaceBiome)) {
                                coast++;
                            }
                        }
                    }
                }
                for (int z = -240; z <= 240; z += 64) {
                    for (int x = -240; x <= 240; x += 64) {
                        for (int y : new int[]{0, -60, -120, -170}) {
                            String biome = biomeKey(biomeSource, sampler, x, y, z);
                            if (ValousPrototypeValidator.LUSH_JUNGLE.equals(biome)) {
                                lush++;
                            } else if (ValousPrototypeValidator.MOLTEN_CAVERNS.equals(biome)) {
                                molten++;
                            } else if (ValousPrototypeValidator.FROZEN_CAVES.equals(biome)) {
                                frozen++;
                            } else if (ValousPrototypeValidator.SCULK_ABYSS.equals(biome)) {
                                sculk++;
                            }
                        }
                    }
                }
                double boilRatio = surfaceLand == 0 ? 0.0D : boilgrounds / (double) surfaceLand;
                int mushroomLand = mushroom + coast;
                double coastShare = mushroomLand == 0 ? 1.0D : coast / (double) mushroomLand;
                return predicted.withActualBiomeSamples(
                    boilRatio,
                    coastShare,
                    lush,
                    molten,
                    frozen,
                    sculk
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed evaluating exact Valous biome routing.", ex);
            }
        }

        StaticPlannerFeed evaluateStaticPlannerFeed(long seed) {
            try {
                Object randomState = randomStateCreate.invoke(
                    null,
                    registryProvider,
                    noiseSettingsKey,
                    seed
                );
                Object sampler = randomStateSampler.invoke(randomState);
                Object biomeSource = generator.getClass().getMethod("getBiomeSource").invoke(generator);
                List<int[]> lush = new ArrayList<>();
                List<int[]> molten = new ArrayList<>();
                List<int[]> frozen = new ArrayList<>();
                List<int[]> sculk = new ArrayList<>();
                for (int z = -224; z <= 224; z += 32) {
                    for (int x = -224; x <= 224; x += 32) {
                        for (int y : new int[]{-16, -64, -112, -160}) {
                            String biome = biomeKey(biomeSource, sampler, x, y, z);
                            int[] point = new int[]{x, y, z};
                            if (ValousPrototypeValidator.LUSH_JUNGLE.equals(biome)) {
                                lush.add(point);
                            } else if (ValousPrototypeValidator.MOLTEN_CAVERNS.equals(biome)) {
                                molten.add(point);
                            } else if (ValousPrototypeValidator.FROZEN_CAVES.equals(biome)) {
                                frozen.add(point);
                            } else if (ValousPrototypeValidator.SCULK_ABYSS.equals(biome)) {
                                sculk.add(point);
                            }
                        }
                    }
                }
                List<int[]> validLush = lush.stream()
                    .filter(point -> point[0] >= -231 && point[0] <= 230)
                    .filter(point -> point[2] >= -231 && point[2] <= 230)
                    .filter(point -> Math.hypot(point[0], point[2]) >= 154.0D)
                    .toList();
                List<int[]> validMolten = molten.stream()
                    .filter(point -> point[0] >= -235 && point[0] <= 234)
                    .filter(point -> point[2] >= -235 && point[2] <= 234)
                    .filter(point -> Math.hypot(point[0], point[2]) >= 154.0D)
                    .toList();
                List<int[]> validSculk = sculk.stream()
                    .filter(point -> point[0] >= -208 && point[0] <= 207)
                    .filter(point -> point[2] >= -208 && point[2] <= 207)
                    .filter(point -> Math.hypot(point[0], point[2]) >= 180.0D)
                    .toList();
                int pairs = 0;
                for (int[] lushPoint : validLush) {
                    for (int[] moltenPoint : validMolten) {
                        double horizontal = Math.hypot(
                            moltenPoint[0] - lushPoint[0],
                            moltenPoint[2] - lushPoint[2]
                        );
                        if (horizontal < 56.0D || horizontal > 260.0D) {
                            continue;
                        }
                        if (((long) lushPoint[0] * moltenPoint[0])
                            + ((long) lushPoint[2] * moltenPoint[2]) <= 0L) {
                            continue;
                        }
                        pairs++;
                    }
                }
                return new StaticPlannerFeed(
                    lush.size(),
                    molten.size(),
                    frozen.size(),
                    sculk.size(),
                    pairs,
                    validLush.size(),
                    validMolten.size(),
                    validSculk.size(),
                    pairs > 0 && !validSculk.isEmpty() && frozen.size() >= 2
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(
                    "Failed evaluating static Valous planner feed.",
                    ex
                );
            }
        }

        private double landRatio(Object generator, Object randomState, int radius, int samples)
            throws ReflectiveOperationException {
            int land = 0;
            for (int index = 0; index < samples; index++) {
                double angle = (Math.PI * 2.0D * index) / samples;
                int x = (int) Math.round(Math.cos(angle) * radius);
                int z = (int) Math.round(Math.sin(angle) * radius);
                int height = baseHeight(generator, randomState, x, z);
                if (height > SEA_LEVEL) {
                    land++;
                }
            }
            return land / (double) samples;
        }

        private int baseHeight(Object generator, Object randomState, int x, int z)
            throws ReflectiveOperationException {
            return ((Number) getBaseHeight.invoke(
                generator,
                x,
                z,
                worldSurfaceHeightmap,
                heightAccessor,
                randomState
            )).intValue();
        }

        private String biomeKey(Object biomeSource, Object sampler, int x, int y, int z)
            throws ReflectiveOperationException {
            Object holder = getNoiseBiome.invoke(
                biomeSource,
                Math.floorDiv(x, 4),
                Math.floorDiv(y, 4),
                Math.floorDiv(z, 4),
                sampler
            );
            Optional<?> key = (Optional<?>) holderUnwrapKey.invoke(holder);
            if (key.isEmpty()) {
                return "";
            }
            Object identifier = resourceKeyIdentifier.invoke(key.get());
            return identifier.toString();
        }
    }

    private static final class NoiseBridge {
        private static final double[] OFFSET_AMPLITUDES = {1.0D, 1.0D, 1.0D, 0.0D};
        private static final double[] LANDMASS_AMPLITUDES = {1.0D, 1.0D, 0.75D, 0.35D};
        private static final double[] SURFACE_AMPLITUDES = {1.0D, 0.7D, 0.35D};
        private static final double[] SURFACE_DETAIL_AMPLITUDES = {1.0D, 0.55D};
        private static final double[] CAVE_REGION_AMPLITUDES = {1.0D, 0.65D, 0.3D};
        private static final double[] CAVE_DETAIL_AMPLITUDES = {1.0D, 0.7D, 0.35D};
        private static final double[] ENTRANCE_AMPLITUDES = {1.0D, 0.4D};

        private final Constructor<?> randomConstructor;
        private final Method forkPositional;
        private final Method identifierParse;
        private final Method fromHashOf;
        private final Method normalNoiseCreate;
        private final Method normalNoiseGetValue;

        private NoiseBridge(
            Constructor<?> randomConstructor,
            Method forkPositional,
            Method identifierParse,
            Method fromHashOf,
            Method normalNoiseCreate,
            Method normalNoiseGetValue
        ) {
            this.randomConstructor = randomConstructor;
            this.forkPositional = forkPositional;
            this.identifierParse = identifierParse;
            this.fromHashOf = fromHashOf;
            this.normalNoiseCreate = normalNoiseCreate;
            this.normalNoiseGetValue = normalNoiseGetValue;
        }

        static NoiseBridge create() {
            try {
                Class<?> randomSourceClass = Class.forName("net.minecraft.util.RandomSource");
                Class<?> xoroshiroClass = Class.forName(
                    "net.minecraft.world.level.levelgen.XoroshiroRandomSource");
                Class<?> positionalFactoryClass = Class.forName(
                    "net.minecraft.world.level.levelgen.PositionalRandomFactory");
                Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
                Class<?> normalNoiseClass = Class.forName(
                    "net.minecraft.world.level.levelgen.synth.NormalNoise");

                return new NoiseBridge(
                    xoroshiroClass.getConstructor(long.class),
                    randomSourceClass.getMethod("forkPositional"),
                    identifierClass.getMethod("parse", String.class),
                    positionalFactoryClass.getMethod("fromHashOf", identifierClass),
                    normalNoiseClass.getMethod("create", randomSourceClass, int.class, double[].class),
                    normalNoiseClass.getMethod("getValue", double.class, double.class, double.class)
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(
                    "Paper 1.21.11 Valous noise bridge is unavailable; refusing unvalidated terrain generation.",
                    ex
                );
            }
        }

        Evaluation evaluate(long seed) {
            try {
                Object random = randomConstructor.newInstance(seed);
                Object positionalFactory = forkPositional.invoke(random);
                Object offset = createNoise(
                    positionalFactory,
                    "minecraft:offset",
                    -3,
                    OFFSET_AMPLITUDES
                );
                Object landmass = createNoise(
                    positionalFactory,
                    "hydra_valous:island_landmass",
                    -9,
                    LANDMASS_AMPLITUDES
                );
                Object surface = createNoise(
                    positionalFactory,
                    "hydra_valous:surface_regions",
                    -9,
                    SURFACE_AMPLITUDES
                );
                Object surfaceDetail = createNoise(
                    positionalFactory,
                    "hydra_valous:surface_region_detail",
                    -7,
                    SURFACE_DETAIL_AMPLITUDES
                );
                NoiseSampler sampler = new NoiseSampler(offset, landmass, surface, surfaceDetail);
                return new Evaluation(
                    sampler.sample(0, 0),
                    sampler.landRatio(160, 16),
                    sampler.landRatio(500, 24),
                    sampler.landRatio(750, 32),
                    sampler.landRatio(950, 40, 0.15D),
                    sampler.mushroomRatio(),
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed evaluating a Valous island seed.", ex);
            }
        }

        Evaluation evaluateShape(long seed, Evaluation coarse) {
            try {
                Object random = randomConstructor.newInstance(seed);
                Object positionalFactory = forkPositional.invoke(random);
                Object offset = createNoise(
                    positionalFactory,
                    "minecraft:offset",
                    -3,
                    OFFSET_AMPLITUDES
                );
                Object landmass = createNoise(
                    positionalFactory,
                    "hydra_valous:island_landmass",
                    -9,
                    LANDMASS_AMPLITUDES
                );
                Object surface = createNoise(
                    positionalFactory,
                    "hydra_valous:surface_regions",
                    -9,
                    SURFACE_AMPLITUDES
                );
                Object surfaceDetail = createNoise(
                    positionalFactory,
                    "hydra_valous:surface_region_detail",
                    -7,
                    SURFACE_DETAIL_AMPLITUDES
                );
                NoiseSampler sampler = new NoiseSampler(offset, landmass, surface, surfaceDetail);
                ShapeEvaluation shape = sampler.evaluateShape();
                return new Evaluation(
                    coarse.centerValue(),
                    coarse.centerLandRatio(),
                    coarse.innerLandRatio(),
                    coarse.shoulderLandRatio(),
                    coarse.outerLandRatio(),
                    shape.mushroomRatio(),
                    shape.interiorLandRatio(),
                    shape.connectedLandRatio(),
                    shape.continuousRayRatio(),
                    shape.minimumCoastRadius(),
                    shape.medianCoastRadius(),
                    shape.maximumCoastRadius(),
                    shape.dominantBoilgroundsRatio()
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed evaluating detailed Valous island shape.", ex);
            }
        }

        PrototypeEvaluation evaluatePrototype(long seed) {
            try {
                Object random = randomConstructor.newInstance(seed);
                Object positionalFactory = forkPositional.invoke(random);
                Object offset = createNoise(
                    positionalFactory,
                    "minecraft:offset",
                    -3,
                    OFFSET_AMPLITUDES
                );
                Object caveRegions = createNoise(
                    positionalFactory,
                    "hydra_valous:cave_regions",
                    -8,
                    CAVE_REGION_AMPLITUDES
                );
                Object caveDetail = createNoise(
                    positionalFactory,
                    "hydra_valous:cave_detail",
                    -6,
                    CAVE_DETAIL_AMPLITUDES
                );
                Object lushA = createNoise(
                    positionalFactory,
                    "hydra_valous:entrance_lush_a",
                    -7,
                    ENTRANCE_AMPLITUDES
                );
                Object lushB = createNoise(
                    positionalFactory,
                    "hydra_valous:entrance_lush_b",
                    -7,
                    ENTRANCE_AMPLITUDES
                );
                Object moltenA = createNoise(
                    positionalFactory,
                    "hydra_valous:entrance_molten_a",
                    -7,
                    ENTRANCE_AMPLITUDES
                );
                Object moltenB = createNoise(
                    positionalFactory,
                    "hydra_valous:entrance_molten_b",
                    -7,
                    ENTRANCE_AMPLITUDES
                );
                Object landmass = createNoise(
                    positionalFactory,
                    "hydra_valous:island_landmass",
                    -9,
                    LANDMASS_AMPLITUDES
                );
                Object surface = createNoise(
                    positionalFactory,
                    "hydra_valous:surface_regions",
                    -9,
                    SURFACE_AMPLITUDES
                );
                Object surfaceDetail = createNoise(
                    positionalFactory,
                    "hydra_valous:surface_region_detail",
                    -7,
                    SURFACE_DETAIL_AMPLITUDES
                );

                int lushEntrances = 0;
                int moltenEntrances = 0;
                int protectedCenterEntrances = 0;
                int lushRegions = 0;
                int moltenRegions = 0;
                int frozenRegions = 0;
                int quickLand = 0;
                int quickBoil = 0;
                for (int z = -240; z <= 240; z += 8) {
                    for (int x = -240; x <= 240; x += 8) {
                        int radiusSquared = (x * x) + (z * z);
                        double shiftX = noiseValue(offset, x * 0.25D, -10.0D, z * 0.25D) * 4.0D;
                        double shiftZ = noiseValue(offset, z * 0.25D, x * 0.25D, -10.0D) * 4.0D;
                        double region = noiseValue(
                            caveRegions,
                            (x * 0.32D) + shiftX,
                            -10.0D,
                            (z * 0.32D) + shiftZ
                        );
                        double detail = noiseValue(
                            caveDetail,
                            (x * 0.55D) + shiftX,
                            -27.0D,
                            (z * 0.55D) + shiftZ
                        );
                        if (region < -0.05D && detail < 0.15D) {
                            lushRegions++;
                        } else if (region > 0.05D) {
                            moltenRegions++;
                        }
                        if (region < -0.05D && detail >= 0.15D) {
                            frozenRegions++;
                        }

                        boolean lushEntrance = Math.max(
                            Math.abs(
                                noiseValue(lushA, x * 0.28D, 0.0D, z * 0.28D)
                                    + 0.156D
                            ),
                            Math.abs(noiseValue(lushB, x * 0.28D, 0.0D, z * 0.28D))
                        ) < 0.045D;
                        boolean moltenEntrance = Math.max(
                            Math.abs(noiseValue(moltenA, x * 0.32D, 0.0D, z * 0.32D)),
                            Math.abs(
                                noiseValue(moltenB, x * 0.32D, 0.0D, z * 0.32D)
                                    - 0.156D
                            )
                        ) < 0.04D;
                        if (radiusSquared <= 128 * 128 && (lushEntrance || moltenEntrance)) {
                            protectedCenterEntrances++;
                        }
                        if (radiusSquared >= 140 * 140 && radiusSquared <= 240 * 240) {
                            if (lushEntrance) {
                                lushEntrances++;
                            }
                            if (moltenEntrance) {
                                moltenEntrances++;
                            }
                        }
                    }
                }
                NoiseSampler surfaceSampler = new NoiseSampler(
                    offset,
                    landmass,
                    surface,
                    surfaceDetail
                );
                for (int z = -240; z <= 240; z += 64) {
                    for (int x = -240; x <= 240; x += 64) {
                        if (surfaceSampler.sample(x, z) < MIN_LANDMASS_VALUE) {
                            continue;
                        }
                        quickLand++;
                        if (surfaceSampler.surfaceSample(x, z) > MUSHROOM_THRESHOLD) {
                            quickBoil++;
                        }
                    }
                }
                return new PrototypeEvaluation(
                    lushEntrances,
                    moltenEntrances,
                    protectedCenterEntrances,
                    lushRegions,
                    moltenRegions,
                    frozenRegions,
                    quickLand == 0 ? 0.0D : quickBoil / (double) quickLand,
                    0.0D,
                    0.0D,
                    0,
                    0,
                    0,
                    0
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed evaluating Valous prototype entrance coverage.", ex);
            }
        }

        private Object createNoise(
            Object positionalFactory,
            String identifier,
            int firstOctave,
            double[] amplitudes
        ) throws ReflectiveOperationException {
            Object id = identifierParse.invoke(null, identifier);
            Object random = fromHashOf.invoke(positionalFactory, id);
            return normalNoiseCreate.invoke(null, random, firstOctave, amplitudes);
        }

        private double noiseValue(Object noise, double x, double y, double z)
            throws ReflectiveOperationException {
            return ((Number) normalNoiseGetValue.invoke(noise, x, y, z)).doubleValue();
        }

        private final class NoiseSampler {
            private final Object offset;
            private final Object landmass;
            private final Object surface;
            private final Object surfaceDetail;

            private NoiseSampler(
                Object offset,
                Object landmass,
                Object surface,
                Object surfaceDetail
            ) {
                this.offset = offset;
                this.landmass = landmass;
                this.surface = surface;
                this.surfaceDetail = surfaceDetail;
            }

            double landRatio(int radius, int samples) throws ReflectiveOperationException {
                return landRatio(radius, samples, MIN_LANDMASS_VALUE);
            }

            double landRatio(int radius, int samples, double threshold)
                throws ReflectiveOperationException {
                int land = 0;
                for (int index = 0; index < samples; index++) {
                    double angle = (Math.PI * 2.0D * index) / samples;
                    int x = (int) Math.round(Math.cos(angle) * radius);
                    int z = (int) Math.round(Math.sin(angle) * radius);
                    if (sample(x, z) >= threshold) {
                        land++;
                    }
                }
                return land / (double) samples;
            }

            double mushroomRatio() throws ReflectiveOperationException {
                int mushroom = 0;
                int land = 0;
                int[] radii = {160, 500, 750};
                int[] samples = {16, 24, 32};
                for (int ring = 0; ring < radii.length; ring++) {
                    for (int index = 0; index < samples[ring]; index++) {
                        double angle = (Math.PI * 2.0D * index) / samples[ring];
                        int x = (int) Math.round(Math.cos(angle) * radii[ring]);
                        int z = (int) Math.round(Math.sin(angle) * radii[ring]);
                        if (sample(x, z) < MIN_LANDMASS_VALUE) {
                            continue;
                        }
                        land++;
                        if (surfaceSample(x, z) <= MUSHROOM_THRESHOLD) {
                            mushroom++;
                        }
                    }
                }
                return land == 0 ? 0.0D : mushroom / (double) land;
            }

            ShapeEvaluation evaluateShape() throws ReflectiveOperationException {
                int diameter = (SHAPE_RADIUS * 2 / SHAPE_GRID_STEP) + 1;
                boolean[][] landGrid = new boolean[diameter][diameter];
                boolean[][] boilGrid = new boolean[diameter][diameter];
                int landCells = 0;
                int interiorCells = 0;
                int interiorLand = 0;
                int mushroomCells = 0;
                int boilCells = 0;
                int centerIndex = SHAPE_RADIUS / SHAPE_GRID_STEP;

                for (int gridZ = 0; gridZ < diameter; gridZ++) {
                    int z = (gridZ * SHAPE_GRID_STEP) - SHAPE_RADIUS;
                    for (int gridX = 0; gridX < diameter; gridX++) {
                        int x = (gridX * SHAPE_GRID_STEP) - SHAPE_RADIUS;
                        int distanceSquared = (x * x) + (z * z);
                        if (distanceSquared > SHAPE_RADIUS * SHAPE_RADIUS) {
                            continue;
                        }
                        boolean land = sample(x, z) >= MIN_LANDMASS_VALUE;
                        landGrid[gridZ][gridX] = land;
                        if (distanceSquared <= 650 * 650) {
                            interiorCells++;
                            if (land) {
                                interiorLand++;
                            }
                        }
                        if (!land) {
                            continue;
                        }
                        landCells++;
                        boolean mushroom = surfaceSample(x, z) <= MUSHROOM_THRESHOLD;
                        if (mushroom) {
                            mushroomCells++;
                        } else {
                            boilGrid[gridZ][gridX] = true;
                            boilCells++;
                        }
                    }
                }

                int connectedLand = componentSize(landGrid, centerIndex, centerIndex);
                List<Integer> boilComponents = componentSizes(boilGrid);
                int dominantBoil = boilComponents.isEmpty() ? 0 : boilComponents.getFirst();

                List<Integer> coastRadii = new ArrayList<>(COAST_RAY_COUNT);
                int continuousRays = 0;
                for (int ray = 0; ray < COAST_RAY_COUNT; ray++) {
                    double angle = Math.PI * 2.0D * ray / COAST_RAY_COUNT;
                    int firstWater = SHAPE_RADIUS + COAST_RAY_STEP;
                    boolean hadLand = false;
                    boolean continuous = true;
                    for (int radius = 0; radius <= SHAPE_RADIUS; radius += COAST_RAY_STEP) {
                        int x = (int) Math.round(Math.cos(angle) * radius);
                        int z = (int) Math.round(Math.sin(angle) * radius);
                        boolean land = sample(x, z) >= MIN_LANDMASS_VALUE;
                        if (land) {
                            hadLand = true;
                            continue;
                        }
                        if (hadLand) {
                            firstWater = radius;
                            break;
                        }
                        continuous = false;
                        firstWater = radius;
                        break;
                    }
                    coastRadii.add(firstWater);
                    if (continuous && firstWater >= 560) {
                        continuousRays++;
                    }
                }
                coastRadii.sort(Comparator.naturalOrder());

                return new ShapeEvaluation(
                    interiorCells == 0 ? 0.0D : interiorLand / (double) interiorCells,
                    landCells == 0 ? 0.0D : connectedLand / (double) landCells,
                    continuousRays / (double) COAST_RAY_COUNT,
                    coastRadii.getFirst(),
                    coastRadii.get(coastRadii.size() / 2),
                    coastRadii.getLast(),
                    landCells == 0 ? 0.0D : mushroomCells / (double) landCells,
                    boilCells == 0 ? 1.0D : dominantBoil / (double) boilCells
                );
            }

            private int componentSize(boolean[][] cells, int startX, int startZ) {
                if (startZ < 0 || startZ >= cells.length
                    || startX < 0 || startX >= cells[startZ].length
                    || !cells[startZ][startX]) {
                    return 0;
                }
                boolean[][] visited = new boolean[cells.length][cells[0].length];
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[] {startX, startZ});
                visited[startZ][startX] = true;
                int size = 0;
                while (!queue.isEmpty()) {
                    int[] current = queue.removeFirst();
                    size++;
                    for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        for (int offsetX = -1; offsetX <= 1; offsetX++) {
                            if (offsetX == 0 && offsetZ == 0) {
                                continue;
                            }
                            int nextX = current[0] + offsetX;
                            int nextZ = current[1] + offsetZ;
                            if (nextZ < 0 || nextZ >= cells.length
                                || nextX < 0 || nextX >= cells[nextZ].length
                                || visited[nextZ][nextX]
                                || !cells[nextZ][nextX]) {
                                continue;
                            }
                            visited[nextZ][nextX] = true;
                            queue.add(new int[] {nextX, nextZ});
                        }
                    }
                }
                return size;
            }

            private List<Integer> componentSizes(boolean[][] cells) {
                Set<Long> remaining = new HashSet<>();
                for (int z = 0; z < cells.length; z++) {
                    for (int x = 0; x < cells[z].length; x++) {
                        if (cells[z][x]) {
                            remaining.add((((long) x) << 32) ^ (z & 0xffffffffL));
                        }
                    }
                }
                List<Integer> sizes = new ArrayList<>();
                while (!remaining.isEmpty()) {
                    long first = remaining.iterator().next();
                    int startX = (int) (first >> 32);
                    int startZ = (int) first;
                    ArrayDeque<int[]> queue = new ArrayDeque<>();
                    queue.add(new int[] {startX, startZ});
                    remaining.remove(first);
                    int size = 0;
                    while (!queue.isEmpty()) {
                        int[] current = queue.removeFirst();
                        size++;
                        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                                if (offsetX == 0 && offsetZ == 0) {
                                    continue;
                                }
                                int nextX = current[0] + offsetX;
                                int nextZ = current[1] + offsetZ;
                                if (nextX < 0 || nextX >= cells[0].length
                                    || nextZ < 0 || nextZ >= cells.length) {
                                    continue;
                                }
                                long next = (((long) nextX) << 32) ^ (nextZ & 0xffffffffL);
                                if (remaining.remove(next)) {
                                    queue.add(new int[] {nextX, nextZ});
                                }
                            }
                        }
                    }
                    sizes.add(size);
                }
                sizes.sort(Comparator.reverseOrder());
                return sizes;
            }

            double sample(int x, int z) throws ReflectiveOperationException {
                double shiftX = noiseValue(offset, x * 0.25D, 0.0D, z * 0.25D) * 4.0D;
                double shiftZ = noiseValue(offset, z * 0.25D, x * 0.25D, 0.0D) * 4.0D;
                return noiseValue(
                    landmass,
                    (x * 0.45D) + shiftX,
                    0.0D,
                    (z * 0.45D) + shiftZ
                );
            }

            private double surfaceSample(int x, int z) throws ReflectiveOperationException {
                double shiftX = noiseValue(offset, x * 0.25D, 0.0D, z * 0.25D) * 4.0D;
                double shiftZ = noiseValue(offset, z * 0.25D, x * 0.25D, 0.0D) * 4.0D;
                return noiseValue(
                    surface,
                    (x * 0.65D) + shiftX,
                    0.0D,
                    (z * 0.65D) + shiftZ
                ) + (0.08D * noiseValue(
                    surfaceDetail,
                    (x * 1.35D) + shiftX,
                    0.0D,
                    (z * 1.35D) + shiftZ
                ));
            }

            private double noiseValue(Object noise, double x, double y, double z)
                throws ReflectiveOperationException {
                return ((Number) normalNoiseGetValue.invoke(noise, x, y, z)).doubleValue();
            }
        }

        private record ShapeEvaluation(
            double interiorLandRatio,
            double connectedLandRatio,
            double continuousRayRatio,
            double minimumCoastRadius,
            double medianCoastRadius,
            double maximumCoastRadius,
            double mushroomRatio,
            double dominantBoilgroundsRatio
        ) {
        }
    }
}
