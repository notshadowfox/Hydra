package hgloot.plugin.announcement;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import hgloot.plugin.HydraMessages;

public final class AnnouncementService {
    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 60;
    private static final int TITLE_FADE_OUT_TICKS = 15;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;

    public AnnouncementService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void global(String scope, String message) {
        Bukkit.broadcast(HydraMessages.scopedInfo(scope, message));
    }

    public void globalTitle(String subtitle) {
        if (subtitle == null || subtitle.isBlank()) {
            return;
        }
        Title title = Title.title(
            LEGACY.deserialize(subtitle),
            Component.empty(),
            Title.Times.times(
                ticksToDuration(TITLE_FADE_IN_TICKS),
                ticksToDuration(TITLE_STAY_TICKS),
                ticksToDuration(TITLE_FADE_OUT_TICKS)
            )
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
        plugin.getLogger().info("[Announcement] " + subtitle);
    }

    public void world(World world, String scope, String message) {
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            player.sendMessage(HydraMessages.scopedInfo(scope, message));
        }
    }

    public void admin(String scope, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("hgloot.admin")) {
                player.sendMessage(HydraMessages.scopedInfo(scope, message));
            }
        }
        plugin.getLogger().info("[" + scope + "] " + message);
    }

    public void direct(CommandSender sender, String scope, String message) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(HydraMessages.legacy(HydraMessages.scopedInfo(scope, message)));
    }

    private static Duration ticksToDuration(int ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }
}
