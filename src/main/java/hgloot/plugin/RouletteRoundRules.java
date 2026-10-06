package hgloot.plugin;

import java.util.Collection;
import java.util.UUID;

final class RouletteRoundRules {
    private RouletteRoundRules() {
    }

    static boolean onlyOneTeamRemains(Collection<UUID> alivePlayers, ManagedMinigameRound.Request round) {
        if (round == null || alivePlayers == null || alivePlayers.size() < 2) {
            return false;
        }
        RouletteTeam survivingTeam = null;
        for (UUID playerId : alivePlayers) {
            RouletteTeam team = round.teams().get(playerId);
            if (team == null) {
                return false;
            }
            if (survivingTeam == null) {
                survivingTeam = team;
            } else if (survivingTeam != team) {
                return false;
            }
        }
        return survivingTeam != null;
    }
}
