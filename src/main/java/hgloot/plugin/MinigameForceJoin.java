package hgloot.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared admin admission and staging helpers; each game retains its gameplay checks. */
final class MinigameForceJoin {
    private MinigameForceJoin() { }

    static boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("hgloot.admin")) return true;
        sender.sendMessage(ChatColor.RED + "Direct minigame queues are for admin testing. Use /roulette join.");
        return false;
    }

    static boolean stageInGameHub(HGLootPlugin plugin, Player player) {
        if (plugin.queueService() != null && plugin.queueService().sendPlayerToGameHub(player)) return true;
        player.sendMessage(ChatColor.RED + "Could not enter GameHub. Please try again when it is ready.");
        return false;
    }

    static void execute(CommandSender sender, String[] args, int targetIndex, String mode,
                        Consumer<Player> join, Predicate<Player> queued) {
        if (!sender.hasPermission("hgloot.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to forcejoin minigames.");
            return;
        }
        if (args.length != targetIndex + 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin " + mode + " forcejoin <player|@a>");
            return;
        }
        List<Player> targets;
        if (args[targetIndex].equalsIgnoreCase("@a")) {
            targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        } else {
            Player target = Bukkit.getPlayerExact(args[targetIndex]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return;
            }
            targets = List.of(target);
        }
        int joined = 0;
        int existing = 0;
        for (Player target : targets) {
            if (queued.test(target)) {
                existing++;
                continue;
            }
            join.accept(target);
            if (queued.test(target)) joined++;
            else sender.sendMessage(ChatColor.YELLOW + target.getName() + " could not join " + mode
                + "; normal queue and match restrictions still apply.");
        }
        sender.sendMessage(ChatColor.GRAY + "Forcejoin " + mode + ": " + joined + " joined, "
            + existing + " already queued, " + (targets.size() - joined - existing) + " rejected.");
    }

    static List<String> complete(String prefix) {
        List<String> values = new ArrayList<>(List.of("@a"));
        Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT)
            .startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
