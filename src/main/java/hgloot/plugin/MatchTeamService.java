package hgloot.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;

public final class MatchTeamService {
    private final Map<String, MatchSnapshot> matches = new HashMap<>();
    private final Map<UUID, LinkedHashSet<String>> matchesByPlayer = new HashMap<>();

    public synchronized boolean registerMatch(String matchId, Collection<TeamDefinition> definitions) {
        if (matchId == null || matchId.isBlank() || definitions == null || definitions.isEmpty()
            || matches.containsKey(matchId)) {
            return false;
        }

        Map<String, MatchTeam> teams = new LinkedHashMap<>();
        Map<UUID, String> teamByPlayer = new HashMap<>();
        for (TeamDefinition definition : definitions) {
            if (definition == null || definition.id() == null || definition.id().isBlank()
                || definition.members() == null || definition.members().isEmpty()
                || teams.containsKey(definition.id())) {
                return false;
            }
            LinkedHashSet<UUID> members = new LinkedHashSet<>();
            for (UUID memberId : definition.members()) {
                if (memberId == null || teamByPlayer.putIfAbsent(memberId, definition.id()) != null) {
                    return false;
                }
                members.add(memberId);
            }
            teams.put(definition.id(), new MatchTeam(
                matchId,
                definition.id(),
                definition.displayName(),
                definition.color(),
                Set.copyOf(members)
            ));
        }

        MatchSnapshot snapshot = new MatchSnapshot(matchId, Map.copyOf(teams), Map.copyOf(teamByPlayer));
        matches.put(matchId, snapshot);
        for (UUID playerId : teamByPlayer.keySet()) {
            matchesByPlayer.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>()).add(matchId);
        }
        return true;
    }

    public synchronized MatchTeam teamOf(String matchId, UUID playerId) {
        MatchSnapshot match = matches.get(matchId);
        if (match == null || playerId == null) {
            return null;
        }
        String teamId = match.teamByPlayer().get(playerId);
        return teamId == null ? null : match.teams().get(teamId);
    }

    public synchronized List<MatchTeam> teamsFor(UUID playerId) {
        Set<String> matchIds = matchesByPlayer.get(playerId);
        if (matchIds == null || matchIds.isEmpty()) {
            return List.of();
        }
        List<MatchTeam> teams = new ArrayList<>();
        for (String matchId : matchIds) {
            MatchTeam team = teamOf(matchId, playerId);
            if (team != null) {
                teams.add(team);
            }
        }
        return List.copyOf(teams);
    }

    public synchronized boolean sameTeam(String matchId, UUID first, UUID second) {
        if (first == null || second == null) {
            return false;
        }
        MatchTeam firstTeam = teamOf(matchId, first);
        MatchTeam secondTeam = teamOf(matchId, second);
        return firstTeam != null && secondTeam != null && firstTeam.id().equals(secondTeam.id());
    }

    public synchronized boolean removeMatch(String matchId) {
        MatchSnapshot removed = matches.remove(matchId);
        if (removed == null) {
            return false;
        }
        for (UUID playerId : removed.teamByPlayer().keySet()) {
            LinkedHashSet<String> matchIds = matchesByPlayer.get(playerId);
            if (matchIds != null) {
                matchIds.remove(matchId);
                if (matchIds.isEmpty()) {
                    matchesByPlayer.remove(playerId);
                }
            }
        }
        return true;
    }

    public synchronized void shutdown() {
        matches.clear();
        matchesByPlayer.clear();
    }

    public record TeamDefinition(String id, String displayName, ChatColor color, Collection<UUID> members) {
        public TeamDefinition {
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
            color = color == null ? ChatColor.WHITE : color;
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    public record MatchTeam(String matchId, String id, String displayName, ChatColor color, Set<UUID> members) {
        public MatchTeam {
            members = Set.copyOf(members);
        }
    }

    private record MatchSnapshot(String matchId, Map<String, MatchTeam> teams, Map<UUID, String> teamByPlayer) {}
}
