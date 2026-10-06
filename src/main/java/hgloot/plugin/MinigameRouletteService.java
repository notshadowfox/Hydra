package hgloot.plugin;

import hgloot.plugin.ManagedMinigameRound.Game;
import hgloot.plugin.ManagedMinigameRound.Request;
import hgloot.plugin.ManagedMinigameRound.Result;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.util.HydraSounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class MinigameRouletteService implements Listener {
    private static final String PREFIX = HydraIcons.EXCLAMATION + " " + ChatColor.GRAY;
    private static final int MIN_PLAYERS = 4;
    private static final int MAX_PLAYERS = 16;
    private static final int QUEUE_COUNTDOWN_SECONDS = 30;
    private static final int FULL_COUNTDOWN_SECONDS = 10;
    private static final int INTERMISSION_SECONDS = 15;
    private static final int MAX_SPLEEF_START_ATTEMPTS = 3;

    private final HGLootPlugin plugin;
    private final PartyService partyService;
    private final ViewerGlowService glowService;
    private final LinkedHashSet<UUID> queuedPlayers = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> roster = new LinkedHashSet<>();
    private final Set<UUID> roundHubPlayers = new HashSet<>();
    private final Map<UUID, RouletteTeam> playerTeams = new HashMap<>();
    private final EnumMap<RouletteTeam, Integer> points = new EnumMap<>(RouletteTeam.class);
    private final EnumMap<RouletteTeam, Integer> roundWins = new EnumMap<>(RouletteTeam.class);
    private final List<Map<RouletteTeam, Integer>> roundPlacementHistory = new ArrayList<>();
    private final Map<UUID, String> sidebarSignatures = new HashMap<>();
    private final Set<UUID> rewardedPlayers = new HashSet<>();
    private final Set<UUID> intermissionCuePlayed = new HashSet<>();
    private final Set<String> failedSpleefMaps = new HashSet<>();
    private List<Game> roundOrder = List.of();
    private UUID tournamentId;
    private UUID activeRoundId;
    private UUID lastUtilityBookCueRound;
    private int currentRoundIndex = -1;
    private int countdownSeconds;
    private int roundStartAttempt;
    private boolean rosterLocked;
    private boolean cleanupStarted;
    private State state = State.IDLE;
    private BukkitTask countdownTask;
    private BukkitTask intermissionTask;
    private BukkitTask roundRetryTask;
    private BukkitTask glowTask;
    private BossBar intermissionBar;
    private RouletteIntermissionDialog.Snapshot intermissionSnapshot;

    public MinigameRouletteService(HGLootPlugin plugin, PartyService partyService) {
        this.plugin = plugin;
        this.partyService = partyService;
        this.glowService = new ViewerGlowService(plugin);
        resetScores();
    }

    public void start() {
        glowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            refreshPrivateGlow();
            updateQueuedSidebars();
        }, 10L, 10L);
    }

    public void shutdown() {
        cleanupTournament("Plugin disabled.", true);
        if (glowTask != null) glowTask.cancel();
        glowTask = null;
        queuedPlayers.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!isTournamentLocked(playerId)) return;
        sidebarSignatures.remove(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreTournamentUi(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (rosterLocked && roster.contains(playerId)) return;
        if (queuedPlayers.remove(playerId)) {
            sidebarSignatures.remove(playerId);
            if (queuedPlayers.size() < MIN_PLAYERS) cancelCountdown();
            updateQueuedSidebars();
            plugin.menuService().refreshHubInterface(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!isTournamentLocked(event.getPlayer().getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshPrivateGlowFor(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!isTournamentLocked(event.getPlayer().getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshPrivateGlowFor(event.getPlayer()));
    }

    public boolean isQueued(UUID playerId) {
        return playerId != null && queuedPlayers.contains(playerId);
    }

    public boolean isLobbyTimeContext(UUID playerId) {
        return queuedPlayers.contains(playerId) || (roster.contains(playerId)
            && (state == State.INTERMISSION || state == State.FINISHED));
    }

    public int queuedCount() {
        return queuedPlayers.size();
    }

    public int playingCount() {
        if (!rosterLocked) {
            return 0;
        }
        int count = 0;
        for (UUID playerId : roster) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public int sidebarActivityCount() {
        return rosterLocked ? playingCount() : queuedCount();
    }

    public boolean isTournamentMember(UUID playerId) {
        return playerId != null && (queuedPlayers.contains(playerId) || roster.contains(playerId));
    }

    public boolean isInActiveTournament(UUID playerId) {
        return playerId != null && rosterLocked && roster.contains(playerId);
    }

    /** Current round, or the upcoming round while players wait in GameHub. */
    public Game utilityBookGame(UUID playerId) {
        if (!isInActiveTournament(playerId)) return null;
        int index = state == State.INTERMISSION ? currentRoundIndex + 1 : currentRoundIndex;
        return (state == State.ROUND || state == State.INTERMISSION)
            && index >= 0 && index < roundOrder.size() ? roundOrder.get(index) : null;
    }

    public boolean ownsSidebar(UUID playerId) {
        return playerId != null && (queuedPlayers.contains(playerId) || roster.contains(playerId));
    }

    public void refreshTournamentSidebar(Player player) {
        if (player != null && rosterLocked && roster.contains(player.getUniqueId())) {
            sidebarSignatures.remove(player.getUniqueId());
            updateStandingsSidebar(player);
        }
    }

    public void openIntermissionPreview(Player player) {
        if (player == null) return;
        RouletteIntermissionDialog.Snapshot snapshot = intermissionSnapshot;
        RouletteTeam previewTeam = playerTeams.getOrDefault(player.getUniqueId(), RouletteTeam.BLUE);
        int teamPlayers;
        int place;
        if (snapshot == null) {
            EnumMap<RouletteTeam, Integer> previewPoints = new EnumMap<>(RouletteTeam.class);
            previewPoints.put(RouletteTeam.RED, 24);
            previewPoints.put(RouletteTeam.BLUE, 19);
            previewPoints.put(RouletteTeam.GREEN, 14);
            previewPoints.put(RouletteTeam.YELLOW, 8);
            snapshot = new RouletteIntermissionDialog.Snapshot(Game.SPLEEF, Game.BRIDGE.displayName(), 2,
                previewPoints,
                List.of(
                    new RouletteIntermissionDialog.ResultLine(1, player.getName(), RouletteTeam.BLUE,
                        11, 8, 3, 3, false),
                    new RouletteIntermissionDialog.ResultLine(2, "PlayerTwo", RouletteTeam.RED,
                        7, 7, 0, 0, false),
                    new RouletteIntermissionDialog.ResultLine(3, "PlayerThree", RouletteTeam.GREEN,
                        7, 6, 1, 1, false),
                    new RouletteIntermissionDialog.ResultLine(4, "PlayerFour", RouletteTeam.YELLOW,
                        5, 5, 0, 0, false)
                ), "");
            teamPlayers = 4;
            place = 2;
        } else {
            teamPlayers = (int) roster.stream().filter(id -> playerTeams.get(id) == previewTeam).count();
            place = rankTeams(points).getOrDefault(previewTeam, 4);
        }
        player.showDialog(RouletteIntermissionDialog.create(snapshot, previewTeam, teamPlayers, place));
    }

    public void openPodiumPreview(Player player) {
        if (player == null) return;
        RouletteTeam viewerTeam = playerTeams.getOrDefault(player.getUniqueId(), RouletteTeam.RED);
        RoulettePodiumDialog.Snapshot snapshot = new RoulettePodiumDialog.Snapshot(List.of(
            new RoulettePodiumDialog.Standing(1, RouletteTeam.RED, 34, 2),
            new RoulettePodiumDialog.Standing(2, RouletteTeam.BLUE, 31, 1),
            new RoulettePodiumDialog.Standing(3, RouletteTeam.GREEN, 24, 0),
            new RoulettePodiumDialog.Standing(4, RouletteTeam.YELLOW, 18, 0)
        ), List.of(RouletteTeam.RED), true);
        player.showDialog(RoulettePodiumDialog.create(snapshot, viewerTeam));
    }

    public boolean isTournamentLocked(UUID playerId) {
        return rosterLocked && playerId != null && roster.contains(playerId);
    }

    public boolean isActiveRoundMember(UUID playerId) {
        return isTournamentLocked(playerId) && state == State.ROUND && activeRoundId != null;
    }

    /** Current child round only for a committed player actually in its arena. */
    public TabRoundSnapshot tabRoundSnapshot(Player viewer) {
        if (viewer == null || !isActiveRoundMember(viewer.getUniqueId())
            || presentationTeamOf(viewer) == null || currentGame() == null) return null;
        return new TabRoundSnapshot(currentRoundIndex + 1, roundOrder.size(), currentGame());
    }

    public record TabRoundSnapshot(int round, int totalRounds, Game game) {}

    public UUID activeRoundIdFor(UUID playerId) {
        return isActiveRoundMember(playerId) ? activeRoundId : null;
    }

    public RouletteTeam teamOf(UUID playerId) {
        return playerTeams.get(playerId);
    }

    public RouletteTeam presentationTeamOf(Player player) {
        if (player == null || !player.isOnline() || player.getWorld() == null
            || !rosterLocked || state != State.ROUND || activeRoundId == null
            || !roster.contains(player.getUniqueId())) return null;
        RouletteTeam team = playerTeams.get(player.getUniqueId());
        Game game = currentGame();
        if (team == null || game == null) return null;
        String audience = switch (game) {
            case BRIDGE -> plugin.bridgeService() == null ? null : plugin.bridgeService().playerAudienceKey(player);
            case SPLEEF -> plugin.spleefService() == null ? null : plugin.spleefService().playerAudienceKey(player);
            case VOID_ROULETTE -> plugin.voidRouletteService() == null ? null : plugin.voidRouletteService().playerAudienceKey(player);
            case BOSS_RUSH -> plugin.bossRushService() == null ? null : plugin.bossRushService().playerAudienceKey(player);
        };
        String worldName = player.getWorld().getName();
        boolean inCurrentArena = switch (game) {
            case BRIDGE -> ("bridge:" + worldName).equals(audience);
            case SPLEEF -> ("spleef:" + worldName).equals(audience);
            case VOID_ROULETTE -> ("voidroulette:" + worldName).equals(audience);
            case BOSS_RUSH -> audience != null && audience.startsWith("bossrush:" + worldName + ":");
        };
        return inCurrentArena ? team : null;
    }

    public Integer presentationTeamColorRgb(Player player) {
        RouletteTeam team = presentationTeamOf(player);
        if (team == null) return null;
        // The four Roulette teams use the matching entries in HG's authoritative palette.
        int paletteIndex = switch (team) {
            case RED -> 5;
            case BLUE -> 6;
            case GREEN -> 0;
            case YELLOW -> 2;
        };
        return QueueService.hungerGamesTeamColorRgb(paletteIndex);
    }

    public Integer presentationTeamTabIndex(Player player) {
        RouletteTeam team = presentationTeamOf(player);
        return team == null ? null : team.ordinal();
    }

    public boolean blockOtherModeEntry(Player player) {
        if (!isTournamentMember(player.getUniqueId())) {
            return false;
        }
        player.sendMessage(PREFIX + ChatColor.YELLOW + "You are already committed to Minigame Roulette.");
        return true;
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "join" -> {
                if (sender instanceof Player player) joinQueue(player);
                else sender.sendMessage(ChatColor.RED + "Only players can join the queue directly.");
                yield true;
            }
            case "leave" -> {
                if (sender instanceof Player player) leaveQueue(player);
                else sender.sendMessage(ChatColor.RED + "Only players can leave the queue directly.");
                yield true;
            }
            case "status" -> {
                sendStatus(sender);
                yield true;
            }
            case "forcestart" -> {
                startTournament(sender);
                yield true;
            }
            case "ignorepr" -> {
                startIgnoringPlayerRequirements(sender);
                yield true;
            }
            case "skip" -> {
                skipActiveRound(sender);
                yield true;
            }
            case "stop", "forceend" -> {
                stopTournament("Tournament stopped by staff.");
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Minigame Roulette stopped.");
                yield true;
            }
            case "forcejoin" -> {
                forceJoin(sender, args);
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED
                    + "Usage: /admin roulette <join|leave|status|forcestart|ignorepr|skip|stop|forcejoin>");
                yield true;
            }
        };
    }

    public boolean handlePlayerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use Minigame Roulette commands.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "join" -> {
                joinFromNavigator(player);
                yield true;
            }
            case "leave" -> {
                leaveQueue(player);
                yield true;
            }
            case "status" -> {
                sendStatus(player);
                yield true;
            }
            case "ignorepr" -> {
                if (!player.hasPermission("hgloot.admin.roulette")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to bypass Roulette player requirements.");
                } else {
                    startIgnoringPlayerRequirements(player);
                }
                yield true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Usage: /roulette <join|leave|status|ignorepr>");
                yield true;
            }
        };
    }

    public List<String> completePlayerCommand(String[] args) {
        return completePlayerCommand(args, false);
    }

    public List<String> completePlayerCommand(String[] args, boolean admin) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = admin
            ? List.of("join", "leave", "status", "ignorepr")
            : List.of("join", "leave", "status");
        return options.stream()
            .filter(value -> value.startsWith(prefix))
            .toList();
    }

    public List<String> completeAdmin(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("join", "leave", "status", "forcestart", "ignorepr", "skip", "stop", "forcejoin").stream()
                .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("forcejoin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> values = new ArrayList<>(List.of("@a"));
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
            return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    private void forceJoin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /admin roulette forcejoin <player|@a>");
            return;
        }
        if (args[1].equalsIgnoreCase("@a")) {
            Bukkit.getOnlinePlayers().forEach(player -> joinQueue(player, false));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) sender.sendMessage(ChatColor.RED + "Player not found.");
        else joinQueue(target, false);
    }

    private void startIgnoringPlayerRequirements(CommandSender sender) {
        if (rosterLocked) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Minigame Roulette is already running.");
            return;
        }
        startTournament(sender, 1);
    }

    private void skipActiveRound(CommandSender sender) {
        Game game = currentGame();
        if (!rosterLocked || state != State.ROUND || activeRoundId == null || game == null) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "There is no active Roulette round to skip.");
            return;
        }
        UUID skippedRound = activeRoundId;
        activeRoundId = null;
        switch (game) {
            case BRIDGE -> plugin.bridgeService().stopManagedRound("Round skipped by staff.");
            case SPLEEF -> plugin.spleefService().stopTournamentRound("Round skipped by staff.");
            case VOID_ROULETTE -> plugin.voidRouletteService().stopManagedRound("Round skipped by staff.");
            case BOSS_RUSH -> plugin.bossRushService().stopManagedRound("Round skipped by staff.");
        }
        plugin.getLogger().info("[Roulette] Staff skipped round=" + skippedRound + " game=" + game);
        broadcastRoster(ChatColor.YELLOW + game.displayName() + " was skipped by staff. No points were awarded.");
        skipCurrentRound(game, "Skipped by staff.");
    }

    public void joinFromNavigator(Player player) {
        if (player == null) return;
        if (isTournamentLocked(player.getUniqueId())) {
            if (state == State.ROUND && rejoinCurrentRoundSpectator(player)) return;
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Your Roulette tournament is still active.");
            return;
        }
        joinQueue(player);
    }

    public boolean returnEliminatedPlayerToHub(Player player) {
        if (player == null || !isTournamentLocked(player.getUniqueId()) || state != State.ROUND) return false;
        Game game = currentGame();
        if (game == null) return false;
        boolean returned = switch (game) {
            case SPLEEF -> plugin.spleefService().returnTournamentSpectatorToHub(player);
            case BRIDGE -> plugin.bridgeService().returnTournamentSpectatorToHub(player);
            case VOID_ROULETTE -> plugin.voidRouletteService().returnTournamentSpectatorToHub(player);
            case BOSS_RUSH -> plugin.bossRushService().returnTournamentSpectatorToHub(player);
        };
        if (returned) roundHubPlayers.add(player.getUniqueId());
        return returned;
    }

    /** /hub keeps tournament membership; the active child mode owns its current-round forfeit. */
    public boolean returnCurrentRoundPlayerToHub(Player player) {
        if (player == null || !isActiveRoundMember(player.getUniqueId())) return false;
        if (roundHubPlayers.contains(player.getUniqueId())) return true;
        Game game = currentGame();
        if (game == null) return false;
        if (player.getWorld() != null && player.getWorld().getName().equalsIgnoreCase("Hub")
            && !isAliveInCurrentRound(game, player.getUniqueId())) {
            roundHubPlayers.add(player.getUniqueId());
            return true;
        }
        boolean returned = switch (game) {
            case SPLEEF -> plugin.spleefService().forfeitTournamentPlayerToHub(player)
                || plugin.spleefService().returnTournamentSpectatorToHub(player);
            case BRIDGE -> plugin.bridgeService().forfeitTournamentPlayerToHub(player)
                || plugin.bridgeService().returnTournamentSpectatorToHub(player);
            case VOID_ROULETTE -> plugin.voidRouletteService().forfeitTournamentPlayerToHub(player)
                || plugin.voidRouletteService().returnTournamentSpectatorToHub(player);
            case BOSS_RUSH -> plugin.bossRushService().forfeitTournamentPlayerToHub(player)
                || plugin.bossRushService().returnTournamentSpectatorToHub(player);
        };
        if (returned) roundHubPlayers.add(player.getUniqueId());
        return returned;
    }

    private boolean isAliveInCurrentRound(Game game, UUID playerId) {
        return switch (game) {
            case SPLEEF -> plugin.spleefService().isAliveSpleefPlayer(playerId);
            case BRIDGE -> plugin.bridgeService().isBridgeAlive(playerId);
            case VOID_ROULETTE -> plugin.voidRouletteService().isAudienceAlive(playerId);
            case BOSS_RUSH -> plugin.bossRushService().isAliveParticipant(playerId);
        };
    }

    public boolean leaveTournament(Player player) {
        if (player == null || !isTournamentLocked(player.getUniqueId())) return false;
        UUID playerId = player.getUniqueId();
        if (state == State.ROUND && !returnCurrentRoundPlayerToHub(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not leave this round safely. Please try again.");
            return false;
        }
        if (state == State.ROUND && currentGame() == Game.BOSS_RUSH) {
            plugin.bossRushService().forgetTournamentLeaver(playerId);
        }
        forOnlineRoster(target -> {
            if (target.equals(player)) return;
            glowService.setGlowing(player, target, target.isGlowing());
            glowService.setGlowing(target, player, player.isGlowing());
        });
        roster.remove(playerId);
        queuedPlayers.remove(playerId);
        playerTeams.remove(playerId);
        roundHubPlayers.remove(playerId);
        sidebarSignatures.remove(playerId);
        if (intermissionBar != null) intermissionBar.removePlayer(player);
        player.closeDialog();
        if (state != State.ROUND) plugin.queueService().sendPlayerToHub(player);
        player.sendMessage(PREFIX + ChatColor.GREEN + "You left Minigame Roulette.");
        HydraSounds.playQueueLeave(player);
        refreshPrivateGlow();
        if (roster.isEmpty()) cleanupTournament("All players left Minigame Roulette.", false);
        return true;
    }

    private boolean rejoinCurrentRoundSpectator(Player player) {
        if (roundHubPlayers.contains(player.getUniqueId())) return false;
        Game game = currentGame();
        return game != null && switch (game) {
            case SPLEEF -> plugin.spleefService().rejoin(player, false);
            case BRIDGE -> plugin.bridgeService().rejoinTournamentSpectator(player);
            case VOID_ROULETTE -> plugin.voidRouletteService().rejoin(player, false);
            case BOSS_RUSH -> plugin.bossRushService().rejoin(player);
        };
    }

    public boolean rejoinTournamentPlayer(Player player) {
        if (player == null || !player.isOnline() || !isTournamentLocked(player.getUniqueId())
            || state != State.ROUND || roundHubPlayers.contains(player.getUniqueId())) {
            return false;
        }
        Game game = currentGame();
        if (game == null) {
            return false;
        }
        boolean restored = switch (game) {
            case SPLEEF -> plugin.spleefService().rejoin(player, false);
            case BRIDGE -> plugin.bridgeService().rejoin(player)
                || plugin.bridgeService().rejoinTournamentSpectator(player);
            case VOID_ROULETTE -> plugin.voidRouletteService().rejoin(player, false);
            case BOSS_RUSH -> plugin.bossRushService().rejoin(player);
        };
        if (restored) {
            sidebarSignatures.remove(player.getUniqueId());
        }
        return restored;
    }

    private Game currentGame() {
        return currentRoundIndex >= 0 && currentRoundIndex < roundOrder.size() ? roundOrder.get(currentRoundIndex) : null;
    }

    private void joinQueue(Player player) {
        joinQueue(player, true);
    }

    private void joinQueue(Player player, boolean includeParty) {
        if (plugin.kitEditorService() != null && (plugin.kitEditorService().isEditing(player.getUniqueId())
            || plugin.kitEditorService().isRecovering(player.getUniqueId()))) {
            player.sendMessage(PREFIX + ChatColor.RED + "Finish or cancel your kit edit before queueing.");
            HydraSounds.playError(player);
            return;
        }
        if (plugin.bossRushService() != null && plugin.bossRushService().ownsPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already committed to Boss Rush.");
            HydraSounds.playError(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (rosterLocked) {
            player.sendMessage(PREFIX + ChatColor.RED + "A Minigame Roulette tournament is already running.");
            HydraSounds.playError(player);
            return;
        }
        if (queuedPlayers.contains(playerId)) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "You are already queued for Minigame Roulette.");
            HydraSounds.playError(player);
            return;
        }
        if (includeParty && partyService.isInParty(playerId)) {
            joinPartyQueue(player);
            return;
        }
        if (queuedPlayers.size() >= MAX_PLAYERS || isBusyElsewhere(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + (queuedPlayers.size() >= MAX_PLAYERS
                ? "Minigame Roulette is full." : "Leave your current queue or match first."));
            HydraSounds.playError(player);
            return;
        }
        queuedPlayers.add(playerId);
        HydraSounds.playQueueJoin(player);
        sidebarSignatures.remove(playerId);
        plugin.queueService().clearSidebarCache(playerId);
        broadcastQueue(ChatColor.WHITE + player.getName() + ChatColor.GRAY + " joined Minigame Roulette "
            + ChatColor.WHITE + queuedPlayers.size() + "/" + MAX_PLAYERS);
        updateQueuedSidebars();
        plugin.menuService().refreshHubInterface(player);
        plugin.queueService().sendPlayerToGameHub(player);
        if (queuedPlayers.size() >= MIN_PLAYERS && countdownTask == null) startCountdown();
        if (queuedPlayers.size() == MAX_PLAYERS && countdownSeconds > FULL_COUNTDOWN_SECONDS) {
            countdownSeconds = FULL_COUNTDOWN_SECONDS;
        }
    }

    private void joinPartyQueue(Player leader) {
        UUID leaderId = leader.getUniqueId();
        if (!partyService.isLeader(leaderId)) {
            leader.sendMessage(PREFIX + ChatColor.RED + "Only the party leader can queue the party.");
            HydraSounds.playError(leader);
            return;
        }
        List<UUID> members = new ArrayList<>(partyService.getPartyMembers(leaderId));
        if (members.isEmpty()) {
            leader.sendMessage(PREFIX + ChatColor.RED + "Your party has no available members.");
            HydraSounds.playError(leader);
            return;
        }

        int additions = 0;
        List<Player> onlineMembers = new ArrayList<>(members.size());
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.isOnline()) {
                leader.sendMessage(PREFIX + ChatColor.RED + "All party members must be online before queueing.");
                HydraSounds.playError(leader);
                return;
            }
            if (plugin.kitEditorService() != null && (plugin.kitEditorService().isEditing(memberId)
                || plugin.kitEditorService().isRecovering(memberId))) {
                leader.sendMessage(PREFIX + ChatColor.RED + member.getName()
                    + " must finish their kit edit before queueing.");
                HydraSounds.playError(leader);
                return;
            }
            if (plugin.bossRushService() != null && plugin.bossRushService().ownsPlayer(memberId)) {
                plugin.getLogger().fine("[PartyCommitment] MR queue rejected leader=" + leaderId
                    + " member=" + memberId + " owner=BossRush");
                leader.sendMessage(PREFIX + ChatColor.RED + member.getName() + " is committed to Boss Rush.");
                HydraSounds.playError(leader);
                return;
            }
            if (!queuedPlayers.contains(memberId) && isBusyElsewhere(memberId)) {
                plugin.getLogger().fine("[PartyCommitment] MR queue rejected leader=" + leaderId
                    + " member=" + memberId + " owner=other queue/match");
                leader.sendMessage(PREFIX + ChatColor.RED + member.getName()
                    + " must leave their current queue or match first.");
                HydraSounds.playError(leader);
                return;
            }
            if (!queuedPlayers.contains(memberId)) {
                additions++;
            }
            onlineMembers.add(member);
        }
        if (additions == 0) {
            leader.sendMessage(PREFIX + ChatColor.GRAY + "Your party is already queued for Minigame Roulette.");
            HydraSounds.playError(leader);
            return;
        }
        if (queuedPlayers.size() + additions > MAX_PLAYERS) {
            leader.sendMessage(PREFIX + ChatColor.RED + "Minigame Roulette does not have room for your entire party.");
            HydraSounds.playError(leader);
            return;
        }

        Set<UUID> newlyQueued = new HashSet<>(members);
        newlyQueued.removeAll(queuedPlayers);
        queuedPlayers.addAll(members);
        for (Player member : onlineMembers) {
            UUID memberId = member.getUniqueId();
            if (newlyQueued.contains(memberId)) HydraSounds.playQueueJoin(member);
            sidebarSignatures.remove(memberId);
            plugin.queueService().clearSidebarCache(memberId);
        }
        broadcastQueue(ChatColor.WHITE + partyLabel(onlineMembers) + ChatColor.GRAY
            + " joined Minigame Roulette " + ChatColor.WHITE + queuedPlayers.size() + "/" + MAX_PLAYERS);
        updateQueuedSidebars();
        for (Player member : onlineMembers) {
            plugin.menuService().refreshHubInterface(member);
            plugin.queueService().sendPlayerToGameHub(member);
        }
        if (queuedPlayers.size() >= MIN_PLAYERS && countdownTask == null) startCountdown();
        if (queuedPlayers.size() == MAX_PLAYERS && countdownSeconds > FULL_COUNTDOWN_SECONDS) {
            countdownSeconds = FULL_COUNTDOWN_SECONDS;
        }
    }

    private String partyLabel(List<Player> members) {
        List<String> names = members.stream().map(Player::getName).toList();
        return names.isEmpty() ? "A party" : String.join(", ", names);
    }

    public boolean leaveQueue(Player player) {
        if (player == null) return false;
        if (isTournamentLocked(player.getUniqueId())) return leaveTournament(player);
        if (!queuedPlayers.remove(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "You are not queued for Minigame Roulette.");
            HydraSounds.playError(player);
            return false;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "You left Minigame Roulette.");
        HydraSounds.playQueueLeave(player);
        sidebarSignatures.remove(player.getUniqueId());
        plugin.queueService().refreshSidebar(player);
        updateQueuedSidebars();
        if (queuedPlayers.size() < MIN_PLAYERS) cancelCountdown();
        plugin.queueService().returnPlayerToHydraHubPresented(player);
        return true;
    }

    private void stopTournament(String reason) {
        if (rosterLocked) {
            cleanupTournament(reason, false);
            return;
        }
        cancelCountdown();
        List<UUID> queued = new ArrayList<>(queuedPlayers);
        queuedPlayers.clear();
        for (UUID playerId : queued) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            player.sendMessage(PREFIX + ChatColor.YELLOW + reason);
            plugin.queueService().clearSidebarCache(playerId);
            plugin.queueService().sendPlayerToHub(player);
        }
        cleanupStarted = true;
        state = State.IDLE;
    }

    private boolean isBusyElsewhere(UUID playerId) {
        return plugin.queueService().isPlayerQueued(playerId)
            || plugin.queueService().isPlayerInHydraMatchOrSpectating(playerId)
            || (plugin.kitEditorService() != null && (plugin.kitEditorService().isEditing(playerId)
                || plugin.kitEditorService().isRecovering(playerId)))
            || plugin.spleefService().isInSpleef(playerId)
            || plugin.voidRouletteService().isInVoidRoulette(playerId)
            || plugin.bridgeService().isInBridge(playerId);
    }

    private void startCountdown() {
        cancelCountdown();
        state = State.QUEUED;
        countdownSeconds = QUEUE_COUNTDOWN_SECONDS;
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            queuedPlayers.removeIf(playerId -> Bukkit.getPlayer(playerId) == null);
            if (queuedPlayers.size() < MIN_PLAYERS) {
                cancelCountdown();
                return;
            }
            if (queuedPlayers.size() == MAX_PLAYERS && countdownSeconds > FULL_COUNTDOWN_SECONDS) {
                countdownSeconds = FULL_COUNTDOWN_SECONDS;
            }
            if (countdownSeconds <= 0) {
                cancelCountdown();
                startTournament(Bukkit.getConsoleSender());
                return;
            }
            if (countdownSeconds <= 5 || countdownSeconds % 5 == 0) {
                broadcastQueue(ChatColor.AQUA + "Minigame Roulette starts in "
                    + ChatColor.WHITE + countdownSeconds + "s");
            }
            updateQueuedSidebars();
            countdownSeconds--;
        }, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = null;
        countdownSeconds = 0;
        if (!rosterLocked) state = queuedPlayers.isEmpty() ? State.IDLE : State.QUEUED;
    }

    private void startTournament(CommandSender sender) {
        startTournament(sender, MIN_PLAYERS);
    }

    private void startTournament(CommandSender sender, int requiredPlayers) {
        if (rosterLocked) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Minigame Roulette is already running.");
            return;
        }
        List<UUID> selected = queuedPlayers.stream().filter(id -> {
            Player player = Bukkit.getPlayer(id);
            return player != null && player.isOnline();
        }).limit(MAX_PLAYERS).toList();
        if (selected.size() < requiredPlayers) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Need at least " + requiredPlayers + " queued player(s).");
            return;
        }
        cleanupStarted = false;
        cancelCountdown();
        tournamentId = UUID.randomUUID();
        rosterLocked = true;
        roster.clear();
        roster.addAll(selected);
        queuedPlayers.removeAll(selected);
        for (UUID playerId : selected) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                plugin.menuService().refreshHubInterface(player);
            }
        }
        playerTeams.clear();
        playerTeams.putAll(assignTeams(selected));
        // The tournament's team prefix is fixed now; prepare Boss Rush while earlier rounds run.
        if (plugin.bossRushService() != null) {
            int bossRushTeams = (int) playerTeams.values().stream().distinct().count();
            plugin.bossRushService().prepareForTeamCount(bossRushTeams);
        }
        if (plugin.hydraPlusService() != null) {
            plugin.hydraPlusService().refreshNow();
        }
        resetScores();
        roundPlacementHistory.clear();
        rewardedPlayers.clear();
        List<Game> order = new ArrayList<>(List.of(
            Game.BRIDGE,
            Game.SPLEEF,
            Game.VOID_ROULETTE,
            Game.BOSS_RUSH
        ));
        Collections.shuffle(order);
        roundOrder = List.copyOf(order);
        currentRoundIndex = -1;
        broadcastRoster(ChatColor.GREEN + "Minigame Roulette started. Order: " + ChatColor.WHITE
            + order.stream().map(Game::displayName).reduce((a, b) -> a + ", " + b).orElse(""));
        beginNextRound();
    }

    private void beginNextRound() {
        if (cleanupStarted || !rosterLocked) return;
        roundHubPlayers.clear();
        currentRoundIndex++;
        if (currentRoundIndex >= roundOrder.size()) {
            finishTournament();
            return;
        }
        state = State.ROUND;
        activeRoundId = UUID.randomUUID();
        roundStartAttempt = 0;
        failedSpleefMaps.clear();
        launchCurrentRound();
    }

    private void launchCurrentRound() {
        if (cleanupStarted || !rosterLocked || activeRoundId == null
            || currentRoundIndex < 0 || currentRoundIndex >= roundOrder.size()) {
            return;
        }
        Game game = roundOrder.get(currentRoundIndex);
        Request request = new Request(tournamentId, activeRoundId, game, new ArrayList<>(roster),
            playerTeams, this::acceptRoundResult);
        long selectedNanos = game == Game.BOSS_RUSH ? System.nanoTime() : 0L;
        roundStartAttempt++;
        boolean started = switch (game) {
            case BRIDGE -> plugin.bridgeService().startManagedRound(request);
            case SPLEEF -> plugin.spleefService().startTournamentRound(request, failedSpleefMaps);
            case VOID_ROULETTE -> plugin.voidRouletteService().startManagedRound(request);
            case BOSS_RUSH -> plugin.bossRushService().startManagedRound(request, selectedNanos);
        };
        if (!started) {
            if (game == Game.SPLEEF) {
                handleSpleefStartFailure(request);
                return;
            }
            acceptRoundResult(Result.failed(request, game.displayName() + " failed to start."));
            return;
        }
        broadcastRoster(ChatColor.AQUA + "Round " + (currentRoundIndex + 1) + "/" + roundOrder.size() + ": "
            + ChatColor.WHITE + game.displayName());
        UUID announcedRound = activeRoundId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.ROUND || !announcedRound.equals(activeRoundId)
                || announcedRound.equals(lastUtilityBookCueRound)) return;
            lastUtilityBookCueRound = announcedRound;
            if (game != Game.SPLEEF) {
                forOnlineRoster(player -> plugin.utilityBookService().announceContextUpdate(player));
            }
        }, 20L);
        if (plugin.hydraPlusService() != null) plugin.hydraPlusService().refreshNow();
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshPrivateGlow, 10L);
    }

    private void handleSpleefStartFailure(Request request) {
        SpleefService.ManagedStartFailure failure = plugin.spleefService().consumeManagedStartFailure();
        String reason = failure == null || failure.reason().isBlank()
            ? "Spleef failed before activation."
            : failure.reason();
        if (failure != null && !failure.mapKey().isBlank()) {
            failedSpleefMaps.add(failure.mapKey());
        }
        plugin.getLogger().warning("[Roulette][SpleefRetry] tournament=" + tournamentId
            + " round=" + activeRoundId + " attempt=" + roundStartAttempt + "/" + MAX_SPLEEF_START_ATTEMPTS
            + " map=" + (failure == null || failure.mapName().isBlank() ? "unknown" : failure.mapName())
            + " reason=" + reason);
        if (roundStartAttempt >= MAX_SPLEEF_START_ATTEMPTS) {
            broadcastRoster(ChatColor.YELLOW + "Spleef could not start after " + MAX_SPLEEF_START_ATTEMPTS
                + " attempts. No points were awarded; the tournament will continue.");
            skipCurrentRound(request.game(), reason);
            return;
        }
        broadcastRoster(ChatColor.YELLOW + "Spleef arena preparation failed. Trying another map ("
            + (roundStartAttempt + 1) + "/" + MAX_SPLEEF_START_ATTEMPTS + ").");
        UUID expectedRound = activeRoundId;
        roundRetryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            roundRetryTask = null;
            if (cleanupStarted || state != State.ROUND || !request.tournamentId().equals(tournamentId)
                || !expectedRound.equals(activeRoundId)) {
                return;
            }
            launchCurrentRound();
        }, 20L);
    }

    private void skipCurrentRound(Game game, String reason) {
        activeRoundId = null;
        EnumMap<RouletteTeam, Integer> neutralPlacements = new EnumMap<>(RouletteTeam.class);
        for (RouletteTeam team : RouletteTeam.values()) neutralPlacements.put(team, 4);
        roundPlacementHistory.add(Map.copyOf(neutralPlacements));
        intermissionSnapshot = new RouletteIntermissionDialog.Snapshot(game, nextGameName(),
            currentRoundIndex + 2, points, List.of(), "Round skipped: " + reason + " No points awarded.");
        startIntermission();
    }

    private void acceptRoundResult(Result result) {
        if (cleanupStarted || !rosterLocked || result == null
            || !result.tournamentId().equals(tournamentId)
            || !result.roundId().equals(activeRoundId)
            || currentRoundIndex < 0 || currentRoundIndex >= roundOrder.size()
            || result.game() != roundOrder.get(currentRoundIndex)) {
            return;
        }
        if (!result.successful()) {
            cleanupTournament(result.failureReason(), false);
            return;
        }
        activeRoundId = null;
        EnumMap<RouletteTeam, Integer> placementRoundPoints = emptyTeamScores();
        EnumMap<RouletteTeam, Integer> roundPoints = emptyTeamScores();
        List<RouletteIntermissionDialog.ResultLine> resultLines = new ArrayList<>();
        if (result.game() == Game.BRIDGE || result.game() == Game.BOSS_RUSH) {
            List<RouletteTeam> placements = completeTeamPlacements(result.teamPlacements());
            for (int i = 0; i < placements.size(); i++) {
                RouletteTeam team = placements.get(i);
                int teamKills = result.game() == Game.BRIDGE
                    ? result.teamEliminations().getOrDefault(team, 0)
                    : result.teamObjectiveScores().getOrDefault(team, 0);
                RouletteScoring.TeamAward award = result.game() == Game.BRIDGE
                    ? RouletteScoring.bridgeAward(i, teamKills)
                    : RouletteScoring.placementOnlyAward(i, teamKills);
                placementRoundPoints.merge(team, award.placementPoints(), Integer::sum);
                roundPoints.merge(team, award.totalPoints(), Integer::sum);
                resultLines.add(new RouletteIntermissionDialog.ResultLine(i + 1,
                    team.displayName() + " Team", team, award.totalPoints(),
                    award.placementPoints(), award.eliminationsOrObjective(), award.bonusPoints(), true));
            }
        } else {
            List<RouletteTeam> placements = RouletteScoring.teamPlacementsFromPlayers(
                completePlayerPlacements(result.playerPlacements()), playerTeams);
            Map<RouletteTeam, Integer> teamEliminations = RouletteScoring.teamEliminations(
                result.playerEliminations(), playerTeams);
            for (int i = 0; i < placements.size(); i++) {
                RouletteTeam team = placements.get(i);
                RouletteScoring.TeamAward award = RouletteScoring.eliminationAward(i,
                    teamEliminations.getOrDefault(team, 0));
                placementRoundPoints.merge(team, award.placementPoints(), Integer::sum);
                roundPoints.merge(team, award.totalPoints(), Integer::sum);
                resultLines.add(new RouletteIntermissionDialog.ResultLine(i + 1,
                    team.displayName() + " Team", team, award.totalPoints(),
                    award.placementPoints(), award.eliminationsOrObjective(), award.bonusPoints(), true));
            }
        }
        roundPoints.forEach((team, award) -> points.merge(team, award, Integer::sum));
        Map<RouletteTeam, Integer> placements = rankTeams(placementRoundPoints);
        roundPlacementHistory.add(Map.copyOf(placements));
        int best = placements.values().stream().min(Integer::compareTo).orElse(1);
        placements.forEach((team, placement) -> {
            if (placement == best) roundWins.merge(team, 1, Integer::sum);
        });
        intermissionSnapshot = new RouletteIntermissionDialog.Snapshot(result.game(), nextGameName(),
            currentRoundIndex + 2, points, resultLines, "");
        startIntermission();
    }

    private List<UUID> completePlayerPlacements(List<UUID> reported) {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        for (UUID id : reported) if (roster.contains(id)) ordered.add(id);
        roster.stream().filter(id -> !ordered.contains(id)).sorted().forEach(ordered::add);
        return new ArrayList<>(ordered);
    }

    private List<RouletteTeam> completeTeamPlacements(List<RouletteTeam> reported) {
        LinkedHashSet<RouletteTeam> ordered = new LinkedHashSet<>(reported);
        ordered.addAll(Arrays.asList(RouletteTeam.values()));
        return new ArrayList<>(ordered);
    }

    private Map<RouletteTeam, Integer> rankTeams(Map<RouletteTeam, Integer> scores) {
        List<RouletteTeam> sorted = new ArrayList<>(Arrays.asList(RouletteTeam.values()));
        sorted.sort(Comparator.comparingInt((RouletteTeam team) -> scores.getOrDefault(team, 0)).reversed()
            .thenComparingInt(RouletteTeam::ordinal));
        Map<RouletteTeam, Integer> placements = new EnumMap<>(RouletteTeam.class);
        Integer previous = null;
        int placement = 0;
        for (int i = 0; i < sorted.size(); i++) {
            int score = scores.getOrDefault(sorted.get(i), 0);
            if (previous == null || score != previous) placement = i + 1;
            placements.put(sorted.get(i), placement);
            previous = score;
        }
        return placements;
    }

    private void startIntermission() {
        if (cleanupStarted) return;
        state = State.INTERMISSION;
        intermissionCuePlayed.clear();
        clearPrivateGlow();
        removeIntermissionUi();
        forOnlineRoster(player -> {
            boolean staged = plugin.queueService().sendPlayerToGameHub(player, () -> {
                if (state != State.INTERMISSION || cleanupStarted) return;
                player.setGameMode(GameMode.ADVENTURE);
                showIntermissionDialog(player);
            });
            if (!staged) {
                player.setGameMode(GameMode.ADVENTURE);
                showIntermissionDialog(player);
            }
        });
        if (plugin.hydraPlusService() != null) plugin.hydraPlusService().refreshNow();
        intermissionBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_10);
        final int[] seconds = {INTERMISSION_SECONDS};
        intermissionTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (cleanupStarted || state != State.INTERMISSION) return;
            intermissionBar.setTitle(ChatColor.AQUA + "Waiting for next round to start "
                + ChatColor.GRAY + "\u2022 " + ChatColor.WHITE + seconds[0] + "s");
            intermissionBar.setProgress(Math.max(0.0D, Math.min(1.0D,
                seconds[0] / (double) INTERMISSION_SECONDS)));
            forOnlineRoster(player -> {
                if (!intermissionBar.getPlayers().contains(player)) intermissionBar.addPlayer(player);
                updateStandingsSidebar(player);
            });
            if (seconds[0]-- <= 0) {
                removeIntermissionUi();
                beginNextRound();
            }
        }, 0L, 20L);
    }

    private void finishTournament() {
        state = State.FINISHED;
        List<RouletteTeam> winners = finalWinners();
        RoulettePodiumDialog.Snapshot podium = finalPodiumSnapshot(winners);
        Map<UUID, RouletteTeam> finalPlayerTeams = Map.copyOf(playerTeams);
        List<UUID> finalAudience = List.copyOf(roster);
        if (plugin.battlePassService() != null && tournamentId != null) {
            Map<RouletteTeam, Integer> placements = new java.util.EnumMap<>(RouletteTeam.class);
            for (RoulettePodiumDialog.Standing standing : podium.standings()) {
                placements.put(standing.team(), standing.placement());
            }
            String completedTournamentId = tournamentId.toString();
            for (UUID playerId : finalAudience) {
                RouletteTeam team = finalPlayerTeams.get(playerId);
                if (team != null) {
                    plugin.battlePassService().completeRouletteTournament(
                        playerId,
                        completedTournamentId,
                        placements.getOrDefault(team, 4)
                    );
                }
            }
        }
        List<CompletableFuture<Void>> presentations = rewardTournamentWinners(winners);
        broadcastRoster(ChatColor.GOLD + "Minigame Roulette complete. Winner: " + ChatColor.WHITE
            + winners.stream().map(RouletteTeam::displayName).reduce((a, b) -> a + " & " + b).orElse("None"));
        CompletableFuture.allOf(presentations.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    plugin.getLogger().warning("[Roulette] Victory presentation ended with an error: " + error.getMessage());
                }
                cleanupTournament("Tournament complete.", false);
                showFinalPodium(finalAudience, finalPlayerTeams, podium);
            })
        );
    }

    private void showFinalPodium(List<UUID> finalAudience, Map<UUID, RouletteTeam> finalPlayerTeams,
                                 RoulettePodiumDialog.Snapshot podium) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID playerId : finalAudience) {
                Player player = Bukkit.getPlayer(playerId);
                RouletteTeam team = finalPlayerTeams.get(playerId);
                if (player != null && player.isOnline() && team != null) {
                    player.showDialog(RoulettePodiumDialog.create(podium, team));
                }
            }
        });
    }

    private RoulettePodiumDialog.Snapshot finalPodiumSnapshot(List<RouletteTeam> winners) {
        List<RouletteTeam> sorted = new ArrayList<>(Arrays.asList(RouletteTeam.values()));
        sorted.sort(this::compareFinalTeams);
        List<RoulettePodiumDialog.Standing> standings = new ArrayList<>();
        int placement = 0;
        RouletteTeam previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            RouletteTeam team = sorted.get(i);
            if (previous == null || compareFinalTeams(previous, team) != 0) placement = i + 1;
            standings.add(new RoulettePodiumDialog.Standing(placement, team,
                points.getOrDefault(team, 0), roundWins.getOrDefault(team, 0)));
            previous = team;
        }
        return new RoulettePodiumDialog.Snapshot(standings, winners, false);
    }

    private List<CompletableFuture<Void>> rewardTournamentWinners(List<RouletteTeam> winners) {
        Set<RouletteTeam> winningTeams = Set.copyOf(winners);
        List<CompletableFuture<Void>> presentations = new ArrayList<>();
        for (UUID playerId : roster) {
            if (!winningTeams.contains(playerTeams.get(playerId)) || !rewardedPlayers.add(playerId)) continue;
            VictoryResultsService.RewardSnapshot reward = plugin.queueService().recordMinigameRouletteWin(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + ChatColor.GOLD + "Tournament reward: " + ChatColor.WHITE
                    + reward.xp() + " XP, " + reward.coins() + " Coins, and "
                    + reward.fragments() + " Shard Fragments.");
                presentations.add(plugin.victoryResultsService().showVictory(player, reward));
            }
        }
        return presentations;
    }

    private List<RouletteTeam> finalWinners() {
        List<RouletteTeam> sorted = new ArrayList<>(Arrays.asList(RouletteTeam.values()));
        sorted.sort(this::compareFinalTeams);
        List<RouletteTeam> winners = new ArrayList<>();
        if (sorted.isEmpty()) return winners;
        RouletteTeam first = sorted.get(0);
        winners.add(first);
        for (int i = 1; i < sorted.size() && compareFinalTeams(first, sorted.get(i)) == 0; i++) {
            winners.add(sorted.get(i));
        }
        return winners;
    }

    private int compareFinalTeams(RouletteTeam left, RouletteTeam right) {
        int comparison = Integer.compare(points.getOrDefault(right, 0), points.getOrDefault(left, 0));
        if (comparison != 0) return comparison;
        comparison = Integer.compare(roundWins.getOrDefault(right, 0), roundWins.getOrDefault(left, 0));
        if (comparison != 0) return comparison;
        for (int i = roundPlacementHistory.size() - 1; i >= 0; i--) {
            Map<RouletteTeam, Integer> round = roundPlacementHistory.get(i);
            comparison = Integer.compare(round.getOrDefault(left, 4), round.getOrDefault(right, 4));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private void cleanupTournament(String reason, boolean disabling) {
        if (cleanupStarted) return;
        cleanupStarted = true;
        List<Player> onlineRoster = roster.stream()
            .map(Bukkit::getPlayer)
            .filter(player -> player != null && player.isOnline())
            .toList();
        clearPrivateGlow();
        activeRoundId = null;
        if (roundRetryTask != null) roundRetryTask.cancel();
        roundRetryTask = null;
        roundStartAttempt = 0;
        failedSpleefMaps.clear();
        cancelCountdown();
        removeIntermissionUi();
        plugin.bridgeService().stopManagedRound(reason);
        plugin.spleefService().stopTournamentRound(reason);
        plugin.voidRouletteService().stopManagedRound(reason);
        plugin.bossRushService().stopManagedRound(reason);
        plugin.bossRushService().discardUnclaimedArena();
        for (Player player : onlineRoster) {
            player.closeInventory();
            player.setItemOnCursor(null);
            player.setFallDistance(0.0F);
            player.setFireTicks(0);
            player.setInvulnerable(false);
            player.setCollidable(plugin.gameHubService() == null
                || !plugin.gameHubService().isGameHub(player.getWorld()));
            player.setCanPickupItems(true);
            player.setFlying(false);
            if (!disabling) plugin.queueService().sendPlayerToHub(player);
        }
        forOnlineRoster(viewer -> forOnlineRoster(target -> viewer.showPlayer(plugin, target)));
        roster.clear();
        roundHubPlayers.clear();
        intermissionCuePlayed.clear();
        playerTeams.clear();
        sidebarSignatures.clear();
        roundOrder = List.of();
        roundPlacementHistory.clear();
        intermissionSnapshot = null;
        tournamentId = null;
        currentRoundIndex = -1;
        rosterLocked = false;
        state = queuedPlayers.isEmpty() ? State.IDLE : State.QUEUED;
        for (Player player : onlineRoster) {
            plugin.queueService().clearSidebarCache(player.getUniqueId());
            plugin.queueService().refreshSidebar(player);
        }
        if (!disabling && "Tournament complete.".equals(reason)) {
            plugin.playerSettingsService().requeueAfterCompletedSession(
                onlineRoster.stream().map(Player::getUniqueId).toList(),
                PlayerSettingsService.AutoQueue.MINIGAME_ROULETTE);
        }
        if (plugin.hydraPlusService() != null) {
            plugin.hydraPlusService().refreshNow();
        }
    }

    private void removeIntermissionUi() {
        if (intermissionTask != null) intermissionTask.cancel();
        intermissionTask = null;
        if (intermissionBar != null) intermissionBar.removeAll();
        intermissionBar = null;
        forOnlineRoster(Player::closeDialog);
    }

    private void showIntermissionDialog(Player player) {
        if (player == null || intermissionSnapshot == null) return;
        RouletteTeam team = playerTeams.get(player.getUniqueId());
        if (team == null) return;
        int teamPlayers = (int) roster.stream().filter(id -> playerTeams.get(id) == team).count();
        int place = rankTeams(points).getOrDefault(team, 4);
        player.showDialog(RouletteIntermissionDialog.create(intermissionSnapshot, team, teamPlayers, place));
        playIntermissionCueOnce(player);
    }

    void playIntermissionCueOnce(Player player) {
        if (player == null || !player.isOnline()) return;
        if (intermissionCuePlayed.add(player.getUniqueId())) {
            HydraSounds.playRoundResult(player);
        }
    }

    private String playerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    private void updateStandingsSidebar(Player player) {
        RouletteTeam team = playerTeams.get(player.getUniqueId());
        if (team == null) return;
        List<String> lines = List.of(
            ChatColor.GRAY + "Round " + ChatColor.WHITE + "\u2022 " + (currentRoundIndex + 1) + "/" + roundOrder.size(),
            ChatColor.GRAY + "Next " + ChatColor.WHITE + "\u2022 " + nextGameName(),
            " ",
            teamLine(RouletteTeam.RED), teamLine(RouletteTeam.BLUE),
            teamLine(RouletteTeam.GREEN), teamLine(RouletteTeam.YELLOW),
            "  ",
            ChatColor.GRAY + "Your Team " + ChatColor.WHITE + "\u2022 " + team.color() + team.displayName(),
            "   ",
            ChatColor.AQUA + "HydraMC.net"
        );
        String signature = String.join("|", lines);
        Objective current = player.getScoreboard() == null ? null : player.getScoreboard().getObjective("roulette");
        if (signature.equals(sidebarSignatures.get(player.getUniqueId()))
            && current != null && current.getDisplaySlot() == DisplaySlot.SIDEBAR) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = sidebarBoard(player, manager);
        Objective previous = board.getObjective("roulette");
        if (previous != null) previous.unregister();
        Objective objective = board.registerNewObjective("roulette", Criteria.DUMMY,
            ChatColor.AQUA + "" + ChatColor.BOLD + "MINIGAME ROULETTE");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Set<String> used = new HashSet<>();
        int score = lines.size();
        for (String raw : lines) {
            String line = raw;
            while (!used.add(line)) line += ChatColor.RESET;
            objective.getScore(line).setScore(score--);
        }
        assignSidebarBoard(player, board);
        sidebarSignatures.put(player.getUniqueId(), signature);
    }

    private void updateQueuedSidebars() {
        for (UUID playerId : new ArrayList<>(queuedPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            appendFightPitLeaders(lines);
            lines.add(" ");
            lines.add(ChatColor.GRAY + "Mode " + ChatColor.WHITE + "• Minigame Roulette");
            lines.add(ChatColor.GRAY + "Queue " + ChatColor.WHITE + "• Roulette");
            lines.add(ChatColor.GRAY + "Players " + ChatColor.WHITE + "• " + queuedPlayers.size() + "/" + MAX_PLAYERS);
            lines.add(ChatColor.GRAY + "Needed " + ChatColor.WHITE + "• " + MIN_PLAYERS);
            lines.add(ChatColor.GRAY + "Start " + ChatColor.WHITE + "• "
                + (countdownTask == null ? "Waiting" : countdownSeconds + "s"));
            lines.add("  ");
            lines.add(ChatColor.AQUA + "HydraMC.net");
            applySidebar(player, lines);
        }
    }

    private void appendFightPitLeaders(List<String> lines) {
        GameHubService gameHub = plugin.gameHubService();
        List<GameHubService.FightPitLeader> leaders = gameHub == null
            ? List.of()
            : gameHub.fightPitKillLeaders(3);
        if (leaders.isEmpty()) {
            lines.add(ChatColor.GRAY + "No Fight Pit kills");
            return;
        }
        for (int index = 0; index < leaders.size(); index++) {
            GameHubService.FightPitLeader leader = leaders.get(index);
            lines.add(ChatColor.GOLD + "" + (index + 1) + ". " + ChatColor.WHITE + leader.playerName()
                + ChatColor.GRAY + " - " + ChatColor.RED + leader.kills());
        }
    }

    private void applySidebar(Player player, List<String> lines) {
        String signature = String.join("|", lines);
        Objective current = player.getScoreboard() == null ? null : player.getScoreboard().getObjective("roulette");
        if (signature.equals(sidebarSignatures.get(player.getUniqueId()))
            && current != null && current.getDisplaySlot() == DisplaySlot.SIDEBAR) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard board = sidebarBoard(player, manager);
        Objective previous = board.getObjective("roulette");
        if (previous != null) previous.unregister();
        Objective objective = board.registerNewObjective("roulette", Criteria.DUMMY,
            ChatColor.AQUA + "" + ChatColor.BOLD + "MINIGAME ROULETTE");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Set<String> used = new HashSet<>();
        int score = lines.size();
        for (String raw : lines) {
            String line = raw;
            while (!used.add(line)) line += ChatColor.RESET;
            objective.getScore(line).setScore(score--);
        }
        assignSidebarBoard(player, board);
        sidebarSignatures.put(player.getUniqueId(), signature);
    }

    private Scoreboard sidebarBoard(Player player, ScoreboardManager manager) {
        Scoreboard board = player.getScoreboard();
        return board == null || board == manager.getMainScoreboard() ? manager.getNewScoreboard() : board;
    }

    private void assignSidebarBoard(Player player, Scoreboard board) {
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
            if (plugin.hydraPlusService() != null) plugin.hydraPlusService().applyToViewer(player);
        }
    }

    private String teamLine(RouletteTeam team) {
        return team.color() + team.displayName() + ChatColor.GRAY + " \u2022 "
            + ChatColor.WHITE + points.getOrDefault(team, 0);
    }

    private String nextGameName() {
        int next = currentRoundIndex + 1;
        return next >= 0 && next < roundOrder.size() ? roundOrder.get(next).displayName() : "Final Results";
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Minigame Roulette");
        sender.sendMessage(ChatColor.GRAY + "State: " + ChatColor.WHITE + state);
        sender.sendMessage(ChatColor.GRAY + "Queued: " + ChatColor.WHITE + queuedPlayers.size() + "/" + MAX_PLAYERS);
        sender.sendMessage(ChatColor.GRAY + "Roster: " + ChatColor.WHITE + roster.size());
        sender.sendMessage(ChatColor.GRAY + "Round: " + ChatColor.WHITE
            + (currentRoundIndex < 0 ? "none" : (currentRoundIndex + 1) + "/" + roundOrder.size()));
    }

    private Map<UUID, RouletteTeam> assignTeams(List<UUID> selected) {
        EnumMap<RouletteTeam, Integer> remaining = teamCapacities(selected.size());
        List<List<UUID>> groups = partyGroups(selected);
        groups.sort(Comparator.<List<UUID>>comparingInt(List::size).reversed()
            .thenComparing(group -> group.get(0)));
        Map<UUID, RouletteTeam> assignments = new HashMap<>();
        for (List<UUID> group : groups) {
            RouletteTeam intact = Arrays.stream(RouletteTeam.values())
                .filter(team -> remaining.get(team) >= group.size())
                .max(Comparator.comparingInt((RouletteTeam team) -> remaining.get(team))
                    .thenComparingInt(RouletteTeam::ordinal)).orElse(null);
            if (intact != null) {
                for (UUID member : group) assignments.put(member, intact);
                remaining.put(intact, remaining.get(intact) - group.size());
                continue;
            }
            for (UUID member : group) {
                RouletteTeam team = Arrays.stream(RouletteTeam.values())
                    .filter(candidate -> remaining.get(candidate) > 0)
                    .max(Comparator.comparingInt((RouletteTeam candidate) -> remaining.get(candidate))
                        .thenComparingInt(RouletteTeam::ordinal)).orElseThrow();
                assignments.put(member, team);
                remaining.put(team, remaining.get(team) - 1);
            }
        }
        return assignments;
    }

    private EnumMap<RouletteTeam, Integer> teamCapacities(int playerCount) {
        EnumMap<RouletteTeam, Integer> capacities = new EnumMap<>(RouletteTeam.class);
        int base = playerCount / RouletteTeam.values().length;
        int remainder = playerCount % RouletteTeam.values().length;
        for (RouletteTeam team : RouletteTeam.values()) {
            capacities.put(team, base + (team.ordinal() < remainder ? 1 : 0));
        }
        return capacities;
    }

    private List<List<UUID>> partyGroups(List<UUID> selected) {
        Set<UUID> selectedSet = Set.copyOf(selected);
        Set<UUID> visited = new HashSet<>();
        List<List<UUID>> groups = new ArrayList<>();
        for (UUID playerId : selected) {
            if (!visited.add(playerId)) continue;
            List<UUID> party = partyService.getPartyMembers(playerId).stream()
                .filter(selectedSet::contains).sorted().toList();
            if (party.isEmpty()) groups.add(List.of(playerId));
            else {
                visited.addAll(party);
                groups.add(new ArrayList<>(party));
            }
        }
        return groups;
    }

    private EnumMap<RouletteTeam, Integer> emptyTeamScores() {
        EnumMap<RouletteTeam, Integer> scores = new EnumMap<>(RouletteTeam.class);
        for (RouletteTeam team : RouletteTeam.values()) scores.put(team, 0);
        return scores;
    }

    private void resetScores() {
        points.clear();
        roundWins.clear();
        for (RouletteTeam team : RouletteTeam.values()) {
            points.put(team, 0);
            roundWins.put(team, 0);
        }
    }

    private void broadcastQueue(String message) {
        for (UUID id : queuedPlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(PREFIX + message);
        }
    }

    private void broadcastRoster(String message) {
        forOnlineRoster(player -> player.sendMessage(PREFIX + message));
    }

    private void forOnlineRoster(Consumer<Player> consumer) {
        for (UUID id : roster) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) consumer.accept(player);
        }
    }

    private void restoreTournamentUi(Player player) {
        if (!isTournamentLocked(player.getUniqueId())) return;
        sidebarSignatures.remove(player.getUniqueId());
        if (state == State.INTERMISSION) {
            plugin.queueService().sendPlayerToHub(player);
            player.setGameMode(GameMode.ADVENTURE);
            if (intermissionBar != null) intermissionBar.addPlayer(player);
            updateStandingsSidebar(player);
            showIntermissionDialog(player);
        } else if (state == State.ROUND) {
            if (rejoinTournamentPlayer(player)) {
                refreshPrivateGlowFor(player);
                return;
            }
            plugin.queueService().sendPlayerToHub(player);
            player.setGameMode(GameMode.ADVENTURE);
            updateStandingsSidebar(player);
            player.sendMessage(PREFIX + ChatColor.YELLOW
                + "You were eliminated from this round and will return for the next one.");
        }
        refreshPrivateGlowFor(player);
    }

    private void refreshPrivateGlow() {
        if (!rosterLocked) return;
        forOnlineRoster(this::refreshPrivateGlowFor);
    }

    private void refreshPrivateGlowFor(Player viewer) {
        if (!isTournamentLocked(viewer.getUniqueId())) return;
        RouletteTeam viewerTeam = playerTeams.get(viewer.getUniqueId());
        forOnlineRoster(target -> {
            if (viewer.equals(target)) return;
            boolean teammateGlow = state == State.ROUND && viewerTeam != null
                && viewerTeam == playerTeams.get(target.getUniqueId())
                && PlayerSettingsService.enabledOrDefault(plugin, viewer.getUniqueId(), PlayerSettingsService.Toggle.TEAM_GLOW);
            glowService.setGlowing(viewer, target, teammateGlow || target.isGlowing());
        });
    }

    private void clearPrivateGlow() {
        forOnlineRoster(viewer -> forOnlineRoster(target -> {
            if (!viewer.equals(target)) glowService.setGlowing(viewer, target, target.isGlowing());
        }));
    }

    private enum State {
        IDLE,
        QUEUED,
        ROUND,
        INTERMISSION,
        FINISHED
    }
}
