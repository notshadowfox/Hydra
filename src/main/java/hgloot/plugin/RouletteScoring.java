package hgloot.plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RouletteScoring {
    private static final int[] TEAM_PLACEMENT_POINTS = {8, 6, 4, 2};
    private static final int BRIDGE_KILLS_PER_POINT = 4;
    private static final int BRIDGE_KILL_BONUS_CAP = 2;

    private RouletteScoring() {
    }

    static int teamPlacementPoints(int placementIndex) {
        return pointsAt(TEAM_PLACEMENT_POINTS, placementIndex);
    }

    static int bridgeKillBonus(int teamKills) {
        return Math.min(BRIDGE_KILL_BONUS_CAP, Math.max(0, teamKills) / BRIDGE_KILLS_PER_POINT);
    }

    static TeamAward eliminationAward(int placementIndex, int teamEliminations) {
        int placementPoints = teamPlacementPoints(placementIndex);
        int creditedEliminations = Math.max(0, teamEliminations);
        return new TeamAward(placementPoints, creditedEliminations, creditedEliminations,
            placementPoints + creditedEliminations);
    }

    static TeamAward bridgeAward(int placementIndex, int teamKills) {
        int placementPoints = teamPlacementPoints(placementIndex);
        int killBonus = bridgeKillBonus(teamKills);
        return new TeamAward(placementPoints, Math.max(0, teamKills), killBonus,
            placementPoints + killBonus);
    }

    static TeamAward placementOnlyAward(int placementIndex, int objectiveScore) {
        int placementPoints = teamPlacementPoints(placementIndex);
        return new TeamAward(placementPoints, Math.max(0, objectiveScore), 0, placementPoints);
    }

    private static int pointsAt(int[] points, int placementIndex) {
        return placementIndex >= 0 && placementIndex < points.length ? points[placementIndex] : 0;
    }

    record TeamAward(int placementPoints, int eliminationsOrObjective, int bonusPoints, int totalPoints) {
    }

    static List<RouletteTeam> teamPlacementsFromPlayers(List<UUID> playerPlacements,
                                                       Map<UUID, RouletteTeam> playerTeams) {
        LinkedHashSet<RouletteTeam> ordered = new LinkedHashSet<>();
        for (UUID playerId : playerPlacements) {
            RouletteTeam team = playerTeams.get(playerId);
            if (team != null) ordered.add(team);
        }
        for (RouletteTeam team : RouletteTeam.values()) ordered.add(team);
        return new ArrayList<>(ordered);
    }

    static Map<RouletteTeam, Integer> teamEliminations(Map<UUID, Integer> playerEliminations,
                                                        Map<UUID, RouletteTeam> playerTeams) {
        EnumMap<RouletteTeam, Integer> totals = new EnumMap<>(RouletteTeam.class);
        playerEliminations.forEach((playerId, count) -> {
            RouletteTeam team = playerTeams.get(playerId);
            if (team != null && count != null && count > 0) totals.merge(team, count, Integer::sum);
        });
        return totals;
    }
}
