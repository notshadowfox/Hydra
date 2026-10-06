package hgloot.plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class ManagedMinigameRound {
    private ManagedMinigameRound() {
    }

    public enum Game {
        BRIDGE("Bridge"),
        SPLEEF("Spleef"),
        VOID_ROULETTE("Void Roulette"),
        BOSS_RUSH("Boss Rush");

        private final String displayName;

        Game(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Request(
        UUID tournamentId,
        UUID roundId,
        Game game,
        List<UUID> roster,
        Map<UUID, RouletteTeam> teams,
        Consumer<Result> callback
    ) {
        public Request {
            Objects.requireNonNull(tournamentId, "tournamentId");
            Objects.requireNonNull(roundId, "roundId");
            Objects.requireNonNull(game, "game");
            roster = List.copyOf(roster);
            teams = Map.copyOf(teams);
            Objects.requireNonNull(callback, "callback");
        }
    }

    public record Result(
        UUID tournamentId,
        UUID roundId,
        Game game,
        boolean successful,
        List<UUID> playerPlacements,
        List<RouletteTeam> teamPlacements,
        Map<UUID, Integer> playerEliminations,
        Map<RouletteTeam, Integer> teamEliminations,
        Map<RouletteTeam, Integer> teamObjectiveScores,
        String failureReason
    ) {
        public Result {
            playerPlacements = List.copyOf(playerPlacements);
            teamPlacements = List.copyOf(teamPlacements);
            playerEliminations = copyNonNegative(playerEliminations, "playerEliminations");
            teamEliminations = copyNonNegative(teamEliminations, "teamEliminations");
            teamObjectiveScores = copyNonNegative(teamObjectiveScores, "teamObjectiveScores");
            failureReason = failureReason == null ? "" : failureReason;
        }

        public static Result players(Request request, List<UUID> placements) {
            return players(request, placements, Map.of());
        }

        public static Result players(Request request, List<UUID> placements, Map<UUID, Integer> eliminations) {
            return new Result(request.tournamentId(), request.roundId(), request.game(), true,
                placements, List.of(), eliminations, Map.of(), Map.of(), "");
        }

        public static Result teams(Request request, List<RouletteTeam> placements) {
            return teams(request, placements, Map.of());
        }

        public static Result teams(Request request, List<RouletteTeam> placements,
                                   Map<RouletteTeam, Integer> eliminations) {
            return new Result(request.tournamentId(), request.roundId(), request.game(), true,
                List.of(), placements, Map.of(), eliminations, Map.of(), "");
        }

        public static Result teamsByObjective(Request request, List<RouletteTeam> placements,
                                              Map<RouletteTeam, Integer> objectiveScores) {
            return new Result(request.tournamentId(), request.roundId(), request.game(), true,
                List.of(), placements, Map.of(), Map.of(), objectiveScores, "");
        }

        public static Result failed(Request request, String reason) {
            return new Result(request.tournamentId(), request.roundId(), request.game(), false,
                List.of(), List.of(), Map.of(), Map.of(), Map.of(), reason);
        }

        private static <K> Map<K, Integer> copyNonNegative(Map<K, Integer> source, String name) {
            Objects.requireNonNull(source, name);
            for (Map.Entry<K, Integer> entry : source.entrySet()) {
                Objects.requireNonNull(entry.getKey(), name + " key");
                if (entry.getValue() == null || entry.getValue() < 0) {
                    throw new IllegalArgumentException(name + " values must be non-negative");
                }
            }
            return Map.copyOf(source);
        }
    }
}
