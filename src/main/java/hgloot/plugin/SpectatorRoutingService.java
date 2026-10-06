package hgloot.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

//routes spec to the match that actually owns the target
final class SpectatorRoutingService {
    private record Match(String mode, String instanceId, BiPredicate<Player, Player> enter) { }
    private final HGLootPlugin plugin;

    SpectatorRoutingService(HGLootPlugin plugin) { this.plugin = plugin; }

    boolean spectate(Player player, String targetName) {
        UUID playerId = player.getUniqueId();
        QueueService queue = plugin.queueService();
        SpleefService spleef = plugin.spleefService();
        VoidRouletteService voidRoulette = plugin.voidRouletteService();
        BridgeService bridge = plugin.bridgeService();
        BossRushService bossRush = plugin.bossRushService();
        MinigameRouletteService roulette = plugin.minigameRouletteService();
        if (queue.isPlayerActivelyQueuedOrBound(playerId)
            || (roulette != null && roulette.isQueued(playerId))
            || (spleef != null && spleef.isQueuedForSpectatorCommand(playerId))
            || (voidRoulette != null && voidRoulette.isQueuedForSpectatorCommand(playerId))
            || (bridge != null && bridge.isQueuedForSpectatorCommand(playerId))
            || (bossRush != null && bossRush.isQueuedForSpectatorCommand(playerId))) {
            return reject(player, targetName, "queued", "Leave your queue before spectating a match.");
        }
        if (queue.isPlayerInHydraMatchOrSpectating(playerId)
            || (roulette != null && roulette.isInActiveTournament(playerId))
            || (spleef != null && spleef.isInActiveSpleefMatch(playerId))
            || (voidRoulette != null && voidRoulette.isInActiveVoidRouletteMatch(playerId))
            || (bridge != null && bridge.isInActiveBridgeMatch(playerId))
            || (bossRush != null && bossRush.ownsPlayer(playerId))) {
            return reject(player, targetName, "in_match", "Leave your current match before spectating another one.");
        }
        if (!queue.isLobbyWorld(player)
            && (plugin.gameHubService() == null || !plugin.gameHubService().isGameHub(player.getWorld()))) {
            return reject(player, targetName, "outside_hub", "You can only spectate from Hub or GameHub.");
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            return reject(player, targetName, "offline", "That player is not online.");
        }
        if (target.getUniqueId().equals(playerId)) {
            return reject(player, target.getName(), "self", "You cannot spectate yourself.");
        }
        List<Match> matches;
        try {
            matches = resolve(target);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[Spectate] Target resolution failed for " + target.getUniqueId(), ex);
            return reject(player, target.getName(), "resolution_failure",
                "That match is no longer available to spectate.");
        }
        if (matches.size() != 1) {
            String reason = matches.isEmpty() ? "no_active_match" : "ambiguous_ownership";
            return reject(player, target.getName(), reason,
                target.getName() + " is not in an active spectatable match.");
        }
        Match match = matches.get(0);
        boolean entered;
        try {
            entered = match.enter().test(player, target);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[Spectate] Entry failed for " + playerId + " into " + match.instanceId(), ex);
            entered = false;
        }
        plugin.getLogger().fine("[Spectate] invoker=" + playerId + " target=" + target.getUniqueId()
            + " mode=" + match.mode() + " instance=" + match.instanceId()
            + " entry=" + (entered ? "scheduled" : "rejected"));
        if (!entered) {
            player.sendMessage(ChatColor.RED + "That match is no longer available to spectate.");
        }
        return true;
    }

    private List<Match> resolve(Player target) {
        List<Match> matches = new ArrayList<>(1);
        QueueService queue = plugin.queueService();
        String id = queue.spectatableMatchId(target);
        if (id != null) {
            String resolved = id;
            matches.add(new Match("queue", resolved,
                (viewer, watched) -> queue.spectateTargetMatch(viewer, watched, resolved)));
        }
        SpleefService spleef = plugin.spleefService();
        if (spleef != null) {
            id = spleef.spectatableMatchId(target);
            if (id != null) {
                String resolved = id;
                matches.add(new Match("spleef", resolved,
                    (viewer, watched) -> spleef.spectateTargetMatch(viewer, watched, resolved)));
            }
        }
        VoidRouletteService voidRoulette = plugin.voidRouletteService();
        if (voidRoulette != null) {
            id = voidRoulette.spectatableMatchId(target);
            if (id != null) {
                String resolved = id;
                matches.add(new Match("void_roulette", resolved,
                    (viewer, watched) -> voidRoulette.spectateTargetMatch(viewer, watched, resolved)));
            }
        }
        BridgeService bridge = plugin.bridgeService();
        if (bridge != null) {
            id = bridge.spectatableMatchId(target);
            if (id != null) {
                String resolved = id;
                matches.add(new Match("bridge", resolved,
                    (viewer, watched) -> bridge.spectateTargetMatch(viewer, watched, resolved)));
            }
        }
        BossRushService bossRush = plugin.bossRushService();
        if (bossRush != null) {
            id = bossRush.spectatableMatchId(target);
            if (id != null) {
                String resolved = id;
                matches.add(new Match("boss_rush", resolved,
                    (viewer, watched) -> bossRush.spectateTargetMatch(viewer, watched, resolved)));
            }
        }
        return matches;
    }

    private boolean reject(Player player, String target, String reason,
                                  String message) {
        plugin.getLogger().fine("[Spectate] invoker=" + player.getUniqueId() + " target=" + target
            + " rejected=" + reason);
        player.sendMessage(ChatColor.RED + message);
        return true;
    }
}
