package hgloot.plugin;

import hgloot.plugin.util.HydraIcons;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

@SuppressWarnings("deprecation")
public final class HydraPlusService implements Listener {
    private static final String HYDRA_PLUS_PERMISSION = "rank.hydra_plus";
    private static final String HYDRA_PLUS_TWO_STAR_PERMISSION = "rank.hydra_plus.2";
    private static final String HYDRA_PLUS_THREE_STAR_PERMISSION = "rank.hydra_plus.3";
    private static final String TEAM_PREFIX = "hp_";
    private static final int REFRESH_TICKS = 20;
    private static final long TRAIL_INTERVAL_MS = 150L;
    private static final long JOIN_BROADCAST_DELAY_TICKS = 20L;
    private static final String RANK_STYLE_CLASSIC = "rank_style_classic";
    private static final String RANK_STYLE_MINT = "rank_style_mint";
    private static final String RANK_STYLE_SOLAR = "rank_style_solar";
    private static final String HUB_AUDIENCE_KEY = "hub";
    private static final TextColor TAB_TITLE_COLOR = TextColor.color(0x00E5FF);
    private static final TextColor TAB_LABEL_COLOR = TextColor.color(0x9CAEB8);
    private static final TextColor TAB_VALUE_COLOR = TextColor.color(0xF4FBFF);
    private static final TextColor TAB_STATUS_COLOR = TextColor.color(0xD5E3E8);
    private static final TextColor TAB_POPULATION_COLOR = TextColor.color(0x899CA5);
    private static final TextColor TAB_FOOTER_URL_COLOR = TextColor.color(0x7E9099);
    private static final TextColor TAB_ACCENT_COLOR = TextColor.color(0x6FF3FF);
    private static final TextColor TAB_DIVIDER_COLOR = TextColor.color(0x2B4650);
    private static final Key TAB_LOGO_FONT = Key.key("hydra", "gui");
    private static final String TAB_LOGO_GLYPH = "\uE13D";
    private static final int TAB_NAME_COLUMN_WIDTH = 22;
    // Keep the bitmap's ascent near its height and position its baseline with normal text lines.
    private static final int TAB_LOGO_LEADING_LINES = 4;
    private static final int TAB_LOGO_TRAILING_LINES = 1;
    private static final String[] PLAYER_JOIN_MESSAGES = {
        "has entered the arena.",
        "has entered the chat.",
        "is now here."
    };
    private static final TextColor HYDRA_PLUS_CLASSIC_START = TextColor.color(0x00FFAA);
    private static final TextColor HYDRA_PLUS_CLASSIC_END = TextColor.color(0x00AFFF);
    private static final TextColor HYDRA_PLUS_MINT_START = TextColor.color(0x74F89B);
    private static final TextColor HYDRA_PLUS_MINT_END = TextColor.color(0x00C9A7);
    private static final TextColor HYDRA_PLUS_SOLAR_START = TextColor.color(0xFFD166);
    private static final TextColor HYDRA_PLUS_SOLAR_END = TextColor.color(0xFF8C42);
    private static final TextColor PLAYER_RANK_START = TextColor.color(0xB8B8B8);
    private static final TextColor PLAYER_RANK_END = TextColor.color(0x6F6F6F);
    private static final TextColor STAFF_MOD_START = TextColor.color(0x30F277);
    private static final TextColor STAFF_MOD_END = TextColor.color(0x00A86B);
    private static final TextColor STAFF_ADMIN_START = TextColor.color(0xFFD166);
    private static final TextColor STAFF_ADMIN_END = TextColor.color(0xFF7A1A);
    private static final TextColor STAFF_DEV_START = TextColor.color(0xC084FC);
    private static final TextColor STAFF_DEV_END = TextColor.color(0x7C3AED);
    private static final TextColor STAFF_OWNER_START = TextColor.color(0xFF4D5E);
    private static final TextColor STAFF_OWNER_END = TextColor.color(0x8B0000);
    private static final TextColor STAFF_CREATOR_START = TextColor.color(0xE22510);
    private static final TextColor STAFF_CREATOR_END = TextColor.color(0xCC210E);
    private static final TextColor STAFF_MEDIA_START = TextColor.color(0xE22510);
    private static final TextColor STAFF_MEDIA_END = TextColor.color(0xCC210E);
    private static final TextColor STAFF_HELPER_ONE = TextColor.color(0x9D4EBD);
    private static final TextColor STAFF_HELPER_TWO = TextColor.color(0x9542B6);
    private static final TextColor STAFF_HELPER_THREE = TextColor.color(0x206694);
    private static final TextColor STAFF_HELPER_FOUR = TextColor.color(0x195B88);
    private static final TextColor STAFF_STAFF_START = TextColor.color(0x2ECC71);
    private static final TextColor STAFF_STAFF_END = TextColor.color(0x24B962);
    private static final TextColor STAFF_TRIAL_STAFF_START = TextColor.color(0x93DBB1);
    private static final TextColor STAFF_TRIAL_STAFF_END = TextColor.color(0x7FD2A2);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer CHAT_LEGACY = LegacyComponentSerializer.builder()
        .character('§')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastTrailAtMs = new HashMap<>();
    private final Map<UUID, ChatMode> chatModes = new HashMap<>();
    private volatile Map<UUID, ChatRoutingSnapshot> chatRoutingSnapshots = Map.of();
    private BukkitTask refreshTask;

    public HydraPlusService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, REFRESH_TICKS);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        lastTrailAtMs.clear();
        chatModes.clear();
        chatRoutingSnapshots = Map.of();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    public void refreshNow() {
        refreshAll();
    }

    public void togglePartyChat(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (chatModes.getOrDefault(playerId, ChatMode.PUBLIC) == ChatMode.PARTY) {
            setChatMode(player, ChatMode.PUBLIC);
            return;
        }
        if (!(plugin instanceof HGLootPlugin hydra)
            || hydra.partyService() == null
            || !hydra.partyService().isInParty(playerId)) {
            player.sendMessage(ChatColor.RED + "You are not in a party.");
            return;
        }
        setChatMode(player, ChatMode.PARTY);
    }

    public void toggleTeamChat(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (chatModes.getOrDefault(playerId, ChatMode.PUBLIC) == ChatMode.TEAM) {
            setChatMode(player, ChatMode.PUBLIC);
            return;
        }
        if (teamRecipients(player, Bukkit.getOnlinePlayers()).isEmpty()) {
            player.sendMessage(ChatColor.RED + "You are not on a team.");
            return;
        }
        setChatMode(player, ChatMode.TEAM);
    }

    private void setChatMode(Player player, ChatMode mode) {
        if (mode == ChatMode.PUBLIC) {
            chatModes.remove(player.getUniqueId());
            player.sendMessage("Seeing messages from public chat");
        } else {
            chatModes.put(player.getUniqueId(), mode);
            player.sendMessage(mode == ChatMode.PARTY
                ? "Seeing messages from party" : "Seeing messages from team");
        }
        refreshAll();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Map<UUID, ChatRoutingSnapshot> snapshots = chatRoutingSnapshots;
        ChatRoutingSnapshot speaker = snapshots.get(player.getUniqueId());
        if (speaker == null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, this::refreshAll);
            return;
        }
        if (speaker.mode() != ChatMode.PUBLIC) {
            event.setCancelled(true);
            UUID senderId = player.getUniqueId();
            String message = event.getMessage();
            ChatMode expectedMode = speaker.mode();
            Runnable delivery = () -> deliverPrivateChat(senderId, expectedMode, message);
            if (event.isAsynchronous()) {
                Bukkit.getScheduler().runTask(plugin, delivery);
            } else {
                delivery.run();
            }
            return;
        }
        event.getRecipients().removeIf(recipient -> {
            ChatRoutingSnapshot target = snapshots.get(recipient.getUniqueId());
            return target == null || !speaker.canReach(recipient.getUniqueId(), target.audience());
        });
        String chatName = speaker.chatName();
        ChatColor messageColor = speaker.messageColor();
        event.setFormat(chatName + ChatColor.DARK_GRAY + " » " + messageColor + "%2$s");
    }

    private void deliverPrivateChat(UUID senderId, ChatMode expectedMode, String message) {
        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()
            || chatModes.getOrDefault(senderId, ChatMode.PUBLIC) != expectedMode) {
            return;
        }

        Set<UUID> recipients = switch (expectedMode) {
            case PARTY -> partyRecipients(sender);
            case TEAM -> teamRecipients(sender, Bukkit.getOnlinePlayers());
            case PUBLIC -> Set.of();
        };
        if (recipients.isEmpty()) {
            chatModes.remove(senderId);
            refreshAll();
            String channel = expectedMode == ChatMode.PARTY ? "Party" : "Team";
            sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + channel
                + " chat is no longer available. Your message was not sent.");
            return;
        }

        String prefix = expectedMode == ChatMode.PARTY
            ? ChatColor.LIGHT_PURPLE + "[Party] "
            : ChatColor.AQUA + "[Team] ";
        String formatted = prefix
            + CHAT_LEGACY.serialize(chatNameComponent(sender))
            + ChatColor.DARK_GRAY + ": "
            + (hasHydraPlus(sender) || hasStaffRank(sender) ? ChatColor.WHITE : ChatColor.GRAY)
            + message;
        for (UUID recipientId : recipients) {
            Player recipient = Bukkit.getPlayer(recipientId);
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage(formatted);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(null);
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            broadcastHubJoinMessage(player);
        }, JOIN_BROADCAST_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        chatModes.remove(playerId);
        lastTrailAtMs.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> updateFlightState(event.getPlayer()), 2L);
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!shouldShowTrail(player)) {
            return;
        }
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) < 0.01D) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastTrailAtMs.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < TRAIL_INTERVAL_MS) {
            return;
        }
        lastTrailAtMs.put(player.getUniqueId(), now);
        GradientColors colors = hydraGradient(player);
        Particle.DustTransition dust = new Particle.DustTransition(
            Color.fromRGB(colors.start().red(), colors.start().green(), colors.start().blue()),
            Color.fromRGB(colors.end().red(), colors.end().green(), colors.end().blue()),
            1.2F
        );
        player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION,
            player.getLocation().clone().add(0.0D, 0.1D, 0.0D),
            6,
            0.18D,
            0.05D,
            0.18D,
            0.0D,
            dust
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !hasHydraPlus(killer)) {
            return;
        }
        killer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
            event.getEntity().getLocation().clone().add(0.0D, 1.0D, 0.0D),
            24,
            0.5D,
            0.6D,
            0.5D,
            0.05D
        );
        killer.getWorld().playSound(event.getEntity().getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.15F);
    }

    private void refreshAll() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<UUID, ChatRoutingSnapshot> updatedChatRouting = new HashMap<>();
        for (Player player : onlinePlayers) {
            ChatMode mode = chatModes.getOrDefault(player.getUniqueId(), ChatMode.PUBLIC);
            Set<UUID> recipients = switch (mode) {
                case PARTY -> partyRecipients(player);
                case TEAM -> teamRecipients(player, onlinePlayers);
                case PUBLIC -> Set.of();
            };
            if (mode != ChatMode.PUBLIC && recipients.isEmpty()) {
                chatModes.remove(player.getUniqueId());
                mode = ChatMode.PUBLIC;
            }
            updatedChatRouting.put(player.getUniqueId(), new ChatRoutingSnapshot(
                audienceFor(player),
                CHAT_LEGACY.serialize(chatNameComponent(player)),
                hasHydraPlus(player) || hasStaffRank(player) ? ChatColor.WHITE : ChatColor.GRAY,
                mode,
                recipients
            ));
        }
        chatRoutingSnapshots = Map.copyOf(updatedChatRouting);
        TabActivityCounts activityCounts = tabActivityCounts();
        for (Player player : onlinePlayers) {
            updateFlightState(player);
            player.playerListName(teamTabName(player));
            player.setPlayerListOrder(tabListOrder(player));
            player.sendPlayerListHeaderAndFooter(
                tabHeaderComponent(player, onlinePlayers.size(), activityCounts),
                tabFooterComponent()
            );
        }
        for (Player viewer : onlinePlayers) {
            applyToViewer(viewer, onlinePlayers);
        }
    }

    private Set<UUID> partyRecipients(Player player) {
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.partyService() == null) return Set.of();
        List<UUID> members = hydra.partyService().getPartyMembers(player.getUniqueId());
        return members.isEmpty() ? Set.of() : Set.copyOf(members);
    }

    private Set<UUID> teamRecipients(Player player, Collection<? extends Player> onlinePlayers) {
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.teamProtectionService() == null) return Set.of();
        return hydra.teamProtectionService().currentTeamMembers(player, onlinePlayers);
    }

    public void applyToViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        applyToViewer(viewer, Bukkit.getOnlinePlayers());
    }

    private void applyToViewer(Player viewer, Collection<? extends Player> players) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        List<Player> visiblePlayers = new ArrayList<>();
        for (Player target : players) {
            if (target == null || !target.isOnline()) {
                continue;
            }
            boolean visible = canViewInTab(viewer, target);
            if (visible) {
                visiblePlayers.add(target);
                if (!viewer.equals(target) && !shouldPreserveTemporaryHide(viewer, target)) {
                    viewer.showPlayer(plugin, target);
                }
            } else if (!viewer.equals(target)) {
                viewer.hidePlayer(plugin, target);
            }
        }
        applyNameTags(viewer, viewer.getScoreboard(), visiblePlayers);
    }

    private void applyNameTags(Player viewer, Scoreboard board, Collection<? extends Player> players) {
        if (board == null) {
            return;
        }
        for (Player target : players) {
            String entry = target.getName();
            String teamName = teamName(target);
            for (Team existing : board.getTeams()) {
                if (existing.getName().startsWith(TEAM_PREFIX)
                    && existing.hasEntry(entry)
                    && !existing.getName().equals(teamName)) {
                    existing.removeEntry(entry);
                }
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            team.prefix(nameTagPrefix(target));
            team.suffix(Component.empty());
            ChatColor privateTeamColor = plugin instanceof HGLootPlugin hydra
                && hydra.bridgeService() != null
                ? hydra.bridgeService().privateTeamColor(viewer.getUniqueId(), target.getUniqueId())
                : null;
            team.setColor(privateTeamColor == null ? ChatColor.WHITE : privateTeamColor);
            boolean inGameHub = plugin instanceof HGLootPlugin hydra
                && hydra.gameHubService() != null
                && hydra.gameHubService().isGameHub(target.getWorld());
            team.setOption(Team.Option.COLLISION_RULE,
                inGameHub ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS);
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }
    }

    private void broadcastHubJoinMessage(Player player) {
        if (player == null || !player.isOnline() || !isHubAudience(player)) {
            return;
        }
        Component message = scopedJoinMessage(player);
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (isHubAudience(recipient)) {
                recipient.sendMessage(message);
            }
        }
        if (hasHydraPlus(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F);
        }
    }

    private Component scopedJoinMessage(Player player) {
        String name = player.getName();
        if (plugin instanceof HGLootPlugin hydra && hydra.hasStaffRank(player.getUniqueId())) {
            return switch (hydra.staffRankModeFor(player.getUniqueId())) {
                case "owner" -> joinMessage(player, name, "has entered. Behave.");
                case "dev" -> joinMessage(player, name, "joined. All bow to the court.");
                case "admin" -> joinMessage(player, name, "entered the control room.");
                case "mod" -> joinMessage(player, name, "is now watching.");
                case "staff" -> joinMessage(player, name, "is now watching.");
                case "trialstaff" -> joinMessage(player, name, "clocked in for chaos.");
                case "helper" -> joinMessage(player, name, "is now here.");
                case "media" -> joinMessage(player, name, "joined in style.");
                case "creator" -> joinMessage(player, name, "joined in style.");
                default -> normalJoinMessage(name);
            };
        }
        if (hasHydraPlus(player)) {
            return joinMessage(player, name, "joined in style.");
        }
        return normalJoinMessage(name);
    }

    private Component joinMessage(Player player, String playerName, String suffix) {
        return Component.empty()
            .append(rankTagComponent(true, player))
            .append(Component.text(" "))
            .append(Component.text(playerName, TextColor.color(0xFFFFFF)))
            .append(Component.text(" " + suffix, TextColor.color(0xAAAAAA)));
    }

    private Component normalJoinMessage(String playerName) {
        String suffix = PLAYER_JOIN_MESSAGES[ThreadLocalRandom.current().nextInt(PLAYER_JOIN_MESSAGES.length)];
        return Component.empty()
            .append(Component.text(playerName, TextColor.color(0xFFFFFF)))
            .append(Component.text(" " + suffix, TextColor.color(0xAAAAAA)));
    }

    private static boolean canReceiveChat(Audience speakerAudience, Audience recipientAudience) {
        if (!Objects.equals(speakerAudience.key(), recipientAudience.key())) {
            return false;
        }
        if (!speakerAudience.match()) {
            return !recipientAudience.match();
        }
        if (speakerAudience.spectator()) {
            return recipientAudience.spectator();
        }
        return !recipientAudience.spectator();
    }

    private boolean canViewInTab(Player viewer, Player target) {
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline()) {
            return false;
        }
        if (viewer.equals(target)) {
            return true;
        }
        Audience viewerAudience = audienceFor(viewer);
        Audience targetAudience = audienceFor(target);
        if (!Objects.equals(viewerAudience.key(), targetAudience.key())) {
            return false;
        }
        if (!viewerAudience.match()) {
            return !targetAudience.match();
        }
        if (!viewerAudience.spectator() && targetAudience.spectator()) {
            return false;
        }
        return true;
    }

    private boolean shouldPreserveTemporaryHide(Player viewer, Player target) {
        if (!(plugin instanceof HGLootPlugin hydra)
            || hydra.spleefService() == null
            || viewer == null
            || target == null
            || !hydra.spleefService().isSpleefInvisHidden(target.getUniqueId())) {
            return false;
        }
        Audience viewerAudience = audienceFor(viewer);
        Audience targetAudience = audienceFor(target);
        return Objects.equals(viewerAudience.key(), targetAudience.key())
            && viewerAudience.key().startsWith("spleef:");
    }

    private boolean isHubAudience(Player player) {
        Audience audience = audienceFor(player);
        return !audience.match() && HUB_AUDIENCE_KEY.equals(audience.key());
    }

    private Audience audienceFor(Player player) {
        if (player == null || !player.isOnline()) {
            return new Audience("offline", false, false);
        }
        if (plugin instanceof HGLootPlugin hydra) {
            UUID playerId = player.getUniqueId();
            if (hydra.queueService() != null) {
                String key = hydra.queueService().playerAudienceKey(player);
                if (key != null) {
                    return new Audience(key, true, hydra.queueService().isMatchAudienceSpectator(playerId));
                }
            }
            if (hydra.spleefService() != null) {
                String key = hydra.spleefService().playerAudienceKey(player);
                if (key != null) {
                    return new Audience(key, true, hydra.spleefService().isAudienceSpectator(playerId));
                }
            }
            if (hydra.voidRouletteService() != null) {
                String key = hydra.voidRouletteService().playerAudienceKey(player);
                if (key != null) {
                    return new Audience(key, true, hydra.voidRouletteService().isAudienceSpectator(playerId));
                }
            }
            if (hydra.bridgeService() != null) {
                String key = hydra.bridgeService().playerAudienceKey(player);
                if (key != null) {
                    return new Audience(key, true, hydra.bridgeService().isAudienceSpectator(playerId));
                }
            }
            if (hydra.bossRushService() != null) {
                String key = hydra.bossRushService().playerAudienceKey(player);
                if (key != null) {
                    return new Audience(key, true, hydra.bossRushService().isAudienceSpectator(playerId));
                }
            }
        }
        World world = player.getWorld();
        if (isGeneratedMatchWorld(world)) {
            return new Audience("world:" + world.getName(), true, false);
        }
        return new Audience(HUB_AUDIENCE_KEY, false, false);
    }

    private boolean isGeneratedMatchWorld(World world) {
        if (world == null || world.getName() == null) {
            return false;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        return worldName.startsWith("hg_match_")
            || worldName.startsWith("hg-world-")
            || worldName.startsWith("br-world-")
            || worldName.startsWith("spleef-world-")
            || worldName.startsWith("voidroulette-world-")
            || worldName.startsWith("bridge-world-");
    }

    private Component tabNameComponent(Player player) {
        Component rankPrefix = tabRankPrefixComponent(player);
        String name = player.getName() == null ? "" : player.getName();
        Audience audience = audienceFor(player);
        boolean spectator = audience.match() && audience.spectator();
        boolean showHealth = !spectator && showTabHealth(player);
        Component row = Component.empty().append(rankPrefix)
            .append(Component.text(spectator || showHealth
                ? tabPad(name, TAB_NAME_COLUMN_WIDTH) : name, TAB_VALUE_COLOR));
        if (spectator) {
            row = row.append(Component.text("SPEC", TAB_LABEL_COLOR));
        } else if (showHealth) {
            int displayHealth = Math.max(0, (int) Math.ceil(player.getHealth()));
            row = row.append(Component.text(tabPad(displayHealth + "❤", 6), TextColor.color(0x55FF55)));
        }
        return row;
    }

    private Component tabRankPrefixComponent(Player player) {
        if (!hasStaffRank(player) && !hasHydraPlus(player)) {
            return Component.empty();
        }
        return Component.empty()
            .append(rankTagComponent(true, player))
            .append(Component.text("  "));
    }

    private static String tabPad(String text, int width) {
        return text + " ".repeat(Math.max(2, width - text.length()));
    }

    private Component hungerGamesDuoTabName(Player player) {
        Component baseTabName = tabNameComponent(player);
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.queueService() == null) {
            return baseTabName;
        }
        Integer teamColorRgb = hydra.queueService().hungerGamesDuoTeamColorRgb(player.getUniqueId());
        if (teamColorRgb == null) return baseTabName;
        return MatchTeamPresentation.tabName(baseTabName, teamColorRgb);
    }

    private Component teamTabName(Player player) {
        Component hgName = hungerGamesDuoTabName(player);
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.queueService() == null
            || hydra.queueService().hungerGamesDuoTeamColorRgb(player.getUniqueId()) != null
            || hydra.minigameRouletteService() == null) return hgName;
        return MatchTeamPresentation.tabName(hgName,
            hydra.minigameRouletteService().presentationTeamColorRgb(player));
    }

    private boolean showTabHealth(Player player) {
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.queueService() == null || player == null) {
            return false;
        }
        return hydra.queueService().isPlayerInHydraMatchOrSpectating(player.getUniqueId())
            || (hydra.minigameRouletteService() != null
                && hydra.minigameRouletteService().presentationTeamOf(player) != null);
    }

    private Component tabHeaderComponent(Player player, int onlineCount, TabActivityCounts counts) {
        int ping = player == null ? 0 : Math.max(0, player.getPing());
        Component header = Component.empty();
        for (int line = 0; line < TAB_LOGO_LEADING_LINES; line++) {
            header = header.append(Component.newline());
        }
        header = header.append(Component.text(TAB_LOGO_GLYPH, NamedTextColor.WHITE).font(TAB_LOGO_FONT));
        for (int line = 0; line < TAB_LOGO_TRAILING_LINES; line++) {
            header = header.append(Component.newline());
        }
        return header
            .append(Component.text("HYDRA NETWORK", TAB_TITLE_COLOR).decorate(TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text(onlineCount + (onlineCount == 1 ? " PLAYER ONLINE" : " PLAYERS ONLINE"),
                TAB_STATUS_COLOR))
            .append(Component.text("  •  ", TAB_DIVIDER_COLOR))
            .append(Component.text("PING ", TAB_LABEL_COLOR))
            .append(Component.text(ping + "ms", TAB_STATUS_COLOR))
            .append(Component.newline())
            .append(tabContextLine(player, counts))
            .append(Component.newline());
    }

    private Component tabContextLine(Player viewer, TabActivityCounts counts) {
        if (viewer != null && plugin instanceof HGLootPlugin hydra) {
            MinigameRouletteService roulette = hydra.minigameRouletteService();
            MinigameRouletteService.TabRoundSnapshot round = roulette == null
                ? null : roulette.tabRoundSnapshot(viewer);
            if (round != null) {
                return Component.text("MR  •  ROUND " + round.round() + "/" + round.totalRounds()
                    + "  •  " + round.game().displayName().toUpperCase(Locale.ROOT), TAB_POPULATION_COLOR);
            }
            QueueService queues = hydra.queueService();
            QueueService.TabMatchSnapshot match = queues == null ? null : queues.tabMatchSnapshot(viewer);
            if (match != null) {
                Component line = Component.text(match.mode() + "  •  " + match.alive() + " ALIVE",
                    TAB_POPULATION_COLOR);
                if (match.borderSeconds() != null) {
                    int seconds = match.borderSeconds();
                    line = line.append(Component.text("  •  BORDER " + seconds / 60 + ":"
                        + String.format(Locale.ROOT, "%02d", seconds % 60), TAB_POPULATION_COLOR));
                }
                return line;
            }
            BossRushService bossRush = hydra.bossRushService();
            RouletteTeam team = bossRush == null ? null : bossRush.tabArenaTeam(viewer);
            if (team != null) {
                return Component.text("BOSS RUSH  •  TEAM ", TAB_POPULATION_COLOR)
                    .append(Component.text(team.displayName().toUpperCase(Locale.ROOT), tabTeamColor(team)));
            }
            if (hydra.bridgeService() != null && hydra.bridgeService().playerAudienceKey(viewer) != null) {
                return Component.text("BRIDGE", TAB_POPULATION_COLOR);
            }
            if (hydra.spleefService() != null && hydra.spleefService().playerAudienceKey(viewer) != null) {
                return Component.text("SPLEEF", TAB_POPULATION_COLOR);
            }
            if (hydra.voidRouletteService() != null
                && hydra.voidRouletteService().playerAudienceKey(viewer) != null) {
                return Component.text("VOID ROULETTE", TAB_POPULATION_COLOR);
            }
            if (audienceFor(viewer).match()) {
                return Component.empty(); // No stale or guessed match label during a transition.
            }
        }
        return Component.empty().append(Component.text("HG ", TAB_POPULATION_COLOR))
            .append(Component.text(counts.hg(), TAB_POPULATION_COLOR))
            .append(Component.text("  •  ", TAB_DIVIDER_COLOR))
            .append(Component.text("BR ", TAB_POPULATION_COLOR))
            .append(Component.text(counts.br(), TAB_POPULATION_COLOR))
            .append(Component.text("  •  ", TAB_DIVIDER_COLOR))
            .append(Component.text("MR ", TAB_POPULATION_COLOR))
            .append(Component.text(counts.roulette(), TAB_POPULATION_COLOR));
    }

    private TextColor tabTeamColor(RouletteTeam team) {
        return switch (team) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW;
        };
    }

    private Component tabFooterComponent() {
        return Component.empty()
            .append(Component.text("STORE   ", TAB_ACCENT_COLOR))
            .append(Component.text("store.hydramc.net    ", TAB_FOOTER_URL_COLOR))
            .append(Component.newline())
            .append(Component.text("DISCORD ", TAB_ACCENT_COLOR))
            .append(Component.text("discord.gg/ZS5hXqyrzh", TAB_FOOTER_URL_COLOR));
    }

    private TabActivityCounts tabActivityCounts() {
        if (!(plugin instanceof HGLootPlugin hydra)) {
            return new TabActivityCounts(0, 0, 0);
        }
        int hg = hydra.queueService() == null ? 0 : hydra.queueService().hungerGamesTabActivityCount();
        int br = hydra.bossRushService() == null ? 0 : hydra.bossRushService().tabActivityCount();
        int roulette = hydra.minigameRouletteService() == null
            ? 0 : hydra.minigameRouletteService().sidebarActivityCount();
        return new TabActivityCounts(hg, br, roulette);
    }

    private Component chatNameComponent(Player player) {
        if (!hasStaffRank(player) && !hasHydraPlus(player)) {
            return Component.text(player.getName(), TextColor.color(0xFFFFFF));
        }
        return Component.empty()
            .append(rankTagComponent(true, player))
            .append(Component.text(" "))
            .append(Component.text(player.getName(), TextColor.color(0xFFFFFF)));
    }

    private Component nameTagPrefix(Player player) {
        return Component.empty()
            .append(rankTagComponent(true, player))
            .append(Component.text(" "));
    }

    public String scoreboardRankTag(Player player) {
        if (player == null || !(plugin instanceof HGLootPlugin hydra)) {
            return ChatColor.WHITE + "Player";
        }
        String staffRank = hydra.staffRankModeFor(player.getUniqueId());
        if (!"none".equalsIgnoreCase(staffRank)) {
            return switch (staffRank) {
                case "owner" -> ChatColor.RED + "OWNER";
                case "dev" -> ChatColor.LIGHT_PURPLE + "DEVELOPER";
                case "admin" -> ChatColor.GOLD + "ADMIN";
                case "mod" -> ChatColor.GREEN + "MOD";
                case "staff" -> ChatColor.GREEN + "STAFF";
                case "trialstaff" -> ChatColor.AQUA + "TRIAL STAFF";
                case "helper" -> ChatColor.AQUA + "HELPER";
                case "media" -> ChatColor.LIGHT_PURPLE + "MEDIA";
                case "creator" -> ChatColor.LIGHT_PURPLE + "CREATOR";
                default -> ChatColor.GREEN + "STAFF";
            };
        }
        return switch (hydra.hydraRankModeFor(player.getUniqueId())) {
            case "tier3" -> ChatColor.AQUA + "HYDRA+++";
            case "tier2" -> ChatColor.DARK_AQUA + "HYDRA++";
            case "tier1" -> ChatColor.BLUE + "HYDRA+";
            case "base" -> ChatColor.BLUE + "HYDRA";
            default -> ChatColor.WHITE + "Player";
        };
    }

    private String hydraPlusJoinMessage(Player player) {
        return ChatColor.DARK_GRAY + "┃ "
            + ChatColor.WHITE + player.getName()
            + ChatColor.GRAY + " has joined "
            + ChatColor.DARK_GRAY + "("
            + LEGACY.serialize(hydraTagComponent(false, player))
            + ChatColor.DARK_GRAY + ")";
    }

    private Component hydraTagComponent(boolean bracketed, Player player) {
        String label = hydraTierLabel(player);
        GradientColors colors = hydraGradient(player);
        return gradientComponent(label.toUpperCase(Locale.ROOT), colors.colors(), true);
    }

    private Component rankTagComponent(boolean bracketed, Player player) {
        if (hasStaffRank(player)) {
            return staffTagComponent(bracketed, player);
        }
        if (hasHydraPlus(player)) {
            return hydraTagComponent(bracketed, player);
        }
        return gradientComponent("PLAYER", PLAYER_RANK_START, PLAYER_RANK_END, true);
    }

    private Component staffTagComponent(boolean bracketed, Player player) {
        String label = staffRankDisplayLabel(player);
        GradientColors colors = staffGradient(player);
        return gradientComponent(label, colors.colors(), true);
    }

    private String hydraTierLabel(Player player) {
        return switch (hydraPlusStars(player)) {
            case 3 -> "Hydra+++";
            case 2 -> "Hydra++";
            case 1 -> "Hydra+";
            default -> "Hydra";
        };
    }

    private int hydraPlusStars(Player player) {
        if (player == null) {
            return 0;
        }
        if (plugin instanceof HGLootPlugin hydra) {
            return switch (hydra.hydraRankModeFor(player.getUniqueId())) {
                case "tier3" -> 3;
                case "tier2" -> 2;
                case "tier1" -> 1;
                case "base" -> 0;
                default -> 0;
            };
        }
        return hasHydraPlus(player) ? 1 : 0;
    }

    private boolean hasHydraPlus(Player player) {
        if (player == null) {
            return false;
        }
        if (plugin instanceof HGLootPlugin hydra) {
            return hydra.hasHydraPlusRank(player.getUniqueId());
        }
        return player.hasPermission("rank.hydra")
            || player.hasPermission(HYDRA_PLUS_PERMISSION)
            || player.hasPermission(HYDRA_PLUS_TWO_STAR_PERMISSION)
            || player.hasPermission(HYDRA_PLUS_THREE_STAR_PERMISSION);
    }

    private boolean hasStaffRank(Player player) {
        return player != null
            && plugin instanceof HGLootPlugin hydra
            && hydra.hasStaffRank(player.getUniqueId());
    }

    private String staffRankDisplayLabel(Player player) {
        if (player == null || !(plugin instanceof HGLootPlugin hydra)) {
            return "STAFF";
        }
        return switch (hydra.staffRankModeFor(player.getUniqueId())) {
            case "owner" -> "OWNER";
            case "dev" -> "DEVELOPER";
            case "admin" -> "ADMIN";
            case "mod" -> "MOD";
            case "staff" -> "STAFF";
            case "trialstaff" -> "TRIAL STAFF";
            case "helper" -> "HELPER";
            case "media" -> "MEDIA";
            case "creator" -> "CREATOR";
            default -> "STAFF";
        };
    }

    private GradientColors staffGradient(Player player) {
        if (player == null || !(plugin instanceof HGLootPlugin hydra)) {
            return new GradientColors(STAFF_MOD_START, STAFF_MOD_END);
        }
        return switch (hydra.staffRankModeFor(player.getUniqueId())) {
            case "owner" -> new GradientColors(STAFF_OWNER_START, STAFF_OWNER_END);
            case "dev" -> new GradientColors(STAFF_DEV_START, STAFF_DEV_END);
            case "admin" -> new GradientColors(STAFF_ADMIN_START, STAFF_ADMIN_END);
            case "creator" -> new GradientColors(STAFF_CREATOR_START, STAFF_CREATOR_END);
            case "media" -> new GradientColors(STAFF_MEDIA_START, STAFF_MEDIA_END);
            case "helper" -> new GradientColors(List.of(STAFF_HELPER_ONE, STAFF_HELPER_TWO, STAFF_HELPER_THREE, STAFF_HELPER_FOUR));
            case "staff" -> new GradientColors(STAFF_STAFF_START, STAFF_STAFF_END);
            case "trialstaff" -> new GradientColors(STAFF_TRIAL_STAFF_START, STAFF_TRIAL_STAFF_END);
            default -> new GradientColors(STAFF_MOD_START, STAFF_MOD_END);
        };
    }

    private GradientColors hydraGradient(Player player) {
        String styleId = RANK_STYLE_CLASSIC;
        if (plugin instanceof HGLootPlugin hydra && hydra.menuService() != null) {
            styleId = hydra.menuService().selectedHydraRankStyleId(player == null ? null : player.getUniqueId());
        }
        return switch (styleId == null ? RANK_STYLE_CLASSIC : styleId.toLowerCase(Locale.ROOT)) {
            case RANK_STYLE_MINT -> new GradientColors(HYDRA_PLUS_MINT_START, HYDRA_PLUS_MINT_END);
            case RANK_STYLE_SOLAR -> new GradientColors(HYDRA_PLUS_SOLAR_START, HYDRA_PLUS_SOLAR_END);
            default -> new GradientColors(HYDRA_PLUS_CLASSIC_START, HYDRA_PLUS_CLASSIC_END);
        };
    }

    private boolean shouldShowTrail(Player player) {
        if (!hasHydraPlus(player) || !isHubWorld(player)) {
            return false;
        }
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.menuService() == null) {
            return true;
        }
        return hydra.menuService().hydraParticlesEnabled(player);
    }

    private boolean isHubWorld(Player player) {
        if (player == null) {
            return false;
        }
        World world = player.getWorld();
        if (world == null) {
            return false;
        }
        String worldName = world.getName();
        return worldName != null && worldName.equalsIgnoreCase("Hub");
    }

    private void updateFlightState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin instanceof HGLootPlugin hydra && hydra.playerSettingsService() != null) {
            hydra.playerSettingsService().refreshContext(player);
            return;
        }
        if (!(plugin instanceof HGLootPlugin hydra) || hydra.queueService() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        boolean eligible = hasHydraPlus(player)
            && isHubWorld(player)
            && !hydra.queueService().isPlayerQueued(playerId)
            && !hydra.queueService().isPlayerInMatch(playerId)
            && !hydra.queueService().isCustomSpectator(playerId);
        if (eligible) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            return;
        }
        if (hydra.queueService().isCustomSpectator(playerId)) {
            return;
        }
        if (hasHydraPlus(player) && player.getAllowFlight() && !player.isOp() && !player.hasPermission("hgloot.fly")) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    // List-order is descending. HG and MR use the same team bands; rank only sorts within a team.
    private int tabListOrder(Player player) {
        if (player != null && plugin instanceof HGLootPlugin hydra && hydra.queueService() != null) {
            Integer teamIndex = hydra.queueService().hungerGamesDuoTeamTabIndex(player.getUniqueId());
            if (teamIndex == null && hydra.minigameRouletteService() != null) {
                teamIndex = hydra.minigameRouletteService().presentationTeamTabIndex(player);
            }
            return MatchTeamPresentation.tabOrder(teamIndex, tabSortOrder(player));
        }
        return MatchTeamPresentation.tabOrder(null, tabSortOrder(player));
    }

    private int tabSortOrder(Player player) {
        if (player == null) {
            return 99;
        }
        if (plugin instanceof HGLootPlugin hydra && hydra.hasStaffRank(player.getUniqueId())) {
            return switch (hydra.staffRankModeFor(player.getUniqueId())) {
                case "owner" -> 0;
                case "dev" -> 1;
                case "admin" -> 2;
                case "mod" -> 3;
                case "staff" -> 4;
                case "trialstaff" -> 5;
                case "helper" -> 6;
                case "media" -> 7;
                case "creator" -> 8;
                default -> 5;
            };
        }
        if (hasHydraPlus(player)) {
            return 10;
        }
        return 20;
    }

    private String teamName(Player player) {
        String compact = player.getUniqueId().toString().replace("-", "").toLowerCase(Locale.ROOT);
        String cleanName = player.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (cleanName.length() > 6) {
            cleanName = cleanName.substring(0, 6);
        }
        if (cleanName.isBlank()) {
            cleanName = "player";
        }
        return String.format(Locale.ROOT, "%s%02d_%s%s", TEAM_PREFIX, tabSortOrder(player), cleanName, compact.substring(0, 4));
    }

    private Component gradientComponent(String text, TextColor start, TextColor end) {
        return gradientComponent(text, start, end, false);
    }

    private Component gradientComponent(String text, TextColor start, TextColor end, boolean bold) {
        return gradientComponent(text, List.of(start, end), bold);
    }

    private Component gradientComponent(String text, List<TextColor> colors, boolean bold) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        List<TextColor> palette = colors == null || colors.isEmpty()
            ? List.of(TextColor.color(0xFFFFFF))
            : colors;
        int length = text.length();
        Component out = Component.empty();
        for (int i = 0; i < length; i++) {
            double ratio = length <= 1 ? 0.0D : (double) i / (double) (length - 1);
            Component letter = Component.text(String.valueOf(text.charAt(i)), colorAt(palette, ratio));
            if (bold) {
                letter = letter.decoration(TextDecoration.BOLD, true);
            }
            out = out.append(letter);
        }
        return out;
    }

    private TextColor colorAt(List<TextColor> colors, double ratio) {
        if (colors.size() <= 1) {
            return colors.get(0);
        }
        double scaled = Math.max(0.0D, Math.min(1.0D, ratio)) * (colors.size() - 1);
        int index = Math.min(colors.size() - 2, (int) Math.floor(scaled));
        double localRatio = scaled - index;
        TextColor start = colors.get(index);
        TextColor end = colors.get(index + 1);
        return TextColor.color(
            interpolate(start.red(), end.red(), localRatio),
            interpolate(start.green(), end.green(), localRatio),
            interpolate(start.blue(), end.blue(), localRatio)
        );
    }

    private int interpolate(int start, int end, double ratio) {
        return (int) Math.round(start + ((end - start) * ratio));
    }

    private record Audience(String key, boolean match, boolean spectator) {
    }

    private enum ChatMode {
        PUBLIC,
        PARTY,
        TEAM
    }

    private record ChatRoutingSnapshot(
        Audience audience,
        String chatName,
        ChatColor messageColor,
        ChatMode mode,
        Set<UUID> privateRecipients
    ) {
        private ChatRoutingSnapshot {
            privateRecipients = privateRecipients == null ? Set.of() : Set.copyOf(privateRecipients);
        }

        private boolean canReach(UUID recipientId, Audience recipientAudience) {
            return mode == ChatMode.PUBLIC
                ? canReceiveChat(audience, recipientAudience)
                : privateRecipients.contains(recipientId);
        }
    }

    private record TabActivityCounts(int hg, int br, int roulette) {
    }

    private record GradientColors(List<TextColor> colors) {
        private GradientColors {
            colors = colors == null || colors.isEmpty()
                ? List.of(TextColor.color(0xFFFFFF))
                : List.copyOf(colors);
        }

        private GradientColors(TextColor start, TextColor end) {
            this(List.of(start, end));
        }

        private TextColor start() {
            return colors.get(0);
        }

        private TextColor end() {
            return colors.get(colors.size() - 1);
        }
    }
}
