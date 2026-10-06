package hgloot.plugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeamProtectionService implements Listener {
    private final HGLootPlugin plugin;

    public TeamProtectionService(HGLootPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean areProtectedTeammates(Player first, Player second) {
        return first != null && second != null
            && areProtectedTeammates(first.getUniqueId(), second.getUniqueId());
    }

    public static boolean blocks(Player owner, Entity target) {
        if (!(target instanceof Player player) || owner == null) return false;
        JavaPlugin providing = JavaPlugin.getProvidingPlugin(TeamProtectionService.class);
        return providing instanceof HGLootPlugin hydra
            && hydra.teamProtectionService() != null
            && hydra.teamProtectionService().areProtectedTeammates(owner, player);
    }

    public boolean areProtectedTeammates(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        if (plugin.queueService() != null && plugin.queueService().isProtectedMatchTeammate(first, second)) return true;
        if (plugin.bossRushService() != null && plugin.bossRushService().areTeammates(first, second)) return true;

        MatchTeamService teams = plugin.matchTeamService();
        if (teams != null) {
            Set<String> firstTeams = new HashSet<>();
            for (MatchTeamService.MatchTeam team : teams.teamsFor(first)) {
                firstTeams.add(team.matchId() + "\u0000" + team.id());
            }
            for (MatchTeamService.MatchTeam team : teams.teamsFor(second)) {
                if (firstTeams.contains(team.matchId() + "\u0000" + team.id())) return true;
            }
        }

        MinigameRouletteService roulette = plugin.minigameRouletteService();
        return roulette != null
            && roulette.isTournamentLocked(first)
            && roulette.isTournamentLocked(second)
            && roulette.teamOf(first) != null
            && roulette.teamOf(first) == roulette.teamOf(second);
    }

    public Set<UUID> currentTeamMembers(Player player, Collection<? extends Player> onlinePlayers) {
        if (player == null || !player.isOnline() || onlinePlayers == null) return Set.of();
        Set<UUID> members = new LinkedHashSet<>();
        members.add(player.getUniqueId());
        for (Player candidate : onlinePlayers) {
            if (candidate != null && candidate.isOnline() && areProtectedTeammates(player, candidate)) {
                members.add(candidate.getUniqueId());
            }
        }
        return members.size() < 2 ? Set.of() : Set.copyOf(members);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player owner = playerOwner(event.getDamager());
        if (areProtectedTeammates(owner, target)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player target)
            || !(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (areProtectedTeammates(shooter, target)) event.setCancelled(true);
    }

    private static Player playerOwner(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return player;
        return null;
    }
}
