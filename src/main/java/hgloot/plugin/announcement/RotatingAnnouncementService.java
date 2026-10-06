package hgloot.plugin.announcement;

import hgloot.plugin.HydraMessages;
import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.PlayerSettingsService;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RotatingAnnouncementService {
    private static final String CONFIG_ROOT = "announcements.rotating";
    private static final Pattern COMMAND_TOKEN = Pattern.compile("/[A-Za-z][A-Za-z0-9_-]*");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final List<String> DEFAULT_MESSAGES = List.of(
        "Disconnected mid-match? Use /rejoin to jump back in.",
        "Use /social to manage friends, parties, and invites!",
        "Use /partychat to chat with your party, then use it again to return to public chat!",
        "Use /teamchat in team modes to chat only with your teammates!",
        "Use /spectate to spectate someone in an active match!",
        "Queue Minigame Roulette to play multiple Hydra minigames back to back!",
        "Waiting for a match? Jump into the Fight Pit in GameHub!",
        "Cross-teaming is strictly prohibited and will result in a ban!",
        "Some items have unique abilities and cooldowns, so make sure to read their lore!",
        "Found a bug? Report what happened, what mode you were in, and how to reproduce it!"
    );
    private static final List<String> LEGACY_DEFAULT_MESSAGES = List.of(
        "&bUse /rejoin if you disconnect from an active match.",
        "&eReport bugs in Discord so they can be fixed before launch."
    );
    private static final List<String> PREVIOUS_DEFAULT_MESSAGES = List.of(
        "Disconnected mid-match? Use /rejoin to jump back in.",
        "Use /social to manage friends, parties, and invites!",
        "Use /party chat to chat with your party, then use it again to return to public chat!",
        "Use /team chat in team modes to chat only with your teammates!",
        "Use /spectate to spectate someone in an active match!",
        "Queue Minigame Roulette to play multiple Hydra minigames back to back!",
        "Waiting for a match? Jump into the Fight Pit in GameHub!",
        "Cross-teaming is strictly prohibited and will result in a ban!",
        "Some items have unique abilities and cooldowns, so make sure to read their lore!",
        "Found a bug? Report what happened, what mode you were in, and how to reproduce it!"
    );

    private final JavaPlugin plugin;
    private BukkitTask task;
    private int nextIndex;

    public RotatingAnnouncementService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean migrateLegacyDefaults(FileConfiguration config) {
        List<String> configured = config.getStringList(CONFIG_ROOT + ".messages");
        if (configured.equals(LEGACY_DEFAULT_MESSAGES) || configured.equals(PREVIOUS_DEFAULT_MESSAGES)) {
            config.set(CONFIG_ROOT + ".messages", DEFAULT_MESSAGES);
            return true;
        }
        return false;
    }

    public void start() {
        shutdown();
        if (!plugin.getConfig().getBoolean(CONFIG_ROOT + ".enabled", true)) {
            return;
        }
        List<String> messages = messages();
        if (messages.isEmpty()) {
            return;
        }
        long intervalTicks = Math.max(60L, plugin.getConfig().getLong(CONFIG_ROOT + ".interval-seconds", 300L) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, intervalTicks, intervalTicks);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void broadcastNext() {
        List<String> messages = messages();
        if (messages.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        if (nextIndex >= messages.size()) {
            nextIndex = 0;
        }
        String raw = messages.get(nextIndex++);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Component tip = formatTip(raw);
        boolean rejoinReminder = raw.contains("/rejoin");
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (plugin instanceof HGLootPlugin hydra && hydra.playerSettingsService() != null) {
                PlayerSettingsService settings = hydra.playerSettingsService();
                if (!settings.enabled(player.getUniqueId(), PlayerSettingsService.Toggle.ROTATING_TIPS)
                    || (rejoinReminder && !settings.enabled(player.getUniqueId(), PlayerSettingsService.Toggle.REJOIN_REMINDER))) return;
            }
            player.sendMessage(tip);
        });
    }

    static Component formatTip(String raw) {
        Component tip = Component.text("[!]", NamedTextColor.AQUA)
            .append(Component.text(" ", NamedTextColor.WHITE));
        Matcher commands = COMMAND_TOKEN.matcher(raw);
        int cursor = 0;
        while (commands.find()) {
            tip = tip.append(LEGACY.deserialize(raw.substring(cursor, commands.start()))
                .colorIfAbsent(NamedTextColor.WHITE));
            tip = tip.append(Component.text(commands.group(), HydraMessages.INFO_BLUE));
            cursor = commands.end();
        }
        return tip.append(LEGACY.deserialize(raw.substring(cursor)).colorIfAbsent(NamedTextColor.WHITE));
    }

    private List<String> messages() {
        return plugin.getConfig().getStringList(CONFIG_ROOT + ".messages");
    }
}
