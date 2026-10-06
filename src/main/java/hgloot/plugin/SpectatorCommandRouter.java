package hgloot.plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

//keeps both command aliases on the same route
final class SpectatorCommandRouter {
    private SpectatorCommandRouter() { }

    static boolean handle(HGLootPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can spectate matches.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /spectate <player>");
            return true;
        }
        return new SpectatorRoutingService(plugin).spectate(player, args[0]);
    }
}
