package hgloot.plugin.network;

import com.google.gson.JsonObject;
import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.PartyService;
import hgloot.plugin.QueueService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

public final class NetworkBridgeService implements Listener {
    private final HGLootPlugin plugin;
    private final NetworkNodeConfig config;
    private final CoordinatorClient client;
    private final Map<UUID, NetworkDtos.PlayerAssignment> assignments = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> launchingReservations = new ConcurrentHashMap<>();
    private BukkitTask heartbeatTask;
    private volatile String activeReservationId;
    private volatile String activeWorldName;
    private final HgDevelopmentBridge development;

    public NetworkBridgeService(HGLootPlugin plugin, NetworkNodeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.client = new CoordinatorClient(config, plugin.getLogger());
        this.development = plugin.getConfig().getBoolean("network.hg_handshake_development", false)
            ? new HgDevelopmentBridge(plugin, config, client) : null;
    }

    public void start() {
        if (development != null) { development.start(); return; }
        if (!config.enabled()) {
            return;
        }
        sendHeartbeat("AVAILABLE");
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            () -> sendHeartbeat(currentNodeStatus()),
            config.heartbeatSeconds() * 20L,
            config.heartbeatSeconds() * 20L
        );
        plugin.getLogger().info("[Network] enabled role=" + config.role() + " node=" + config.nodeName()
            + " coordinator=" + config.coordinatorUri());
    }

    public void shutdown() {
        if (development != null) { development.shutdown(); return; }
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        if (config.enabled()) {
            sendHeartbeat("OFFLINE");
        }
        assignments.clear();
        launchingReservations.clear();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public BackendRole role() {
        return config.role();
    }

    public boolean ownsHungerGamesQueueEntry() {
        return enabled() && role() == BackendRole.HUB;
    }

    public void queueHungerGames(Player leader, boolean duos, PartyService partyService) {
        if (!ownsHungerGamesQueueEntry() || leader == null) {
            return;
        }
        List<Player> players = new ArrayList<>();
        UUID partyId = null;
        if (duos) {
            PartyService.PartySnapshot party = partyService == null ? null : partyService.snapshot(leader.getUniqueId());
            if (party == null || !leader.getUniqueId().equals(party.leaderId()) || party.members().size() != 2) {
                leader.sendMessage(ChatColor.RED + "HG Duos requires a two-player party led by you.");
                return;
            }
            partyId = party.leaderId();
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null || !member.isOnline()) {
                    leader.sendMessage(ChatColor.RED + "Every party member must be online before queueing.");
                    return;
                }
                players.add(member);
            }
        } else {
            players.add(leader);
        }

        List<NetworkDtos.Member> members = players.stream()
            .map(player -> new NetworkDtos.Member(player.getUniqueId(), player.getName()))
            .toList();
        NetworkDtos.QueueJoinRequest request = new NetworkDtos.QueueJoinRequest(
            UUID.randomUUID(), leader.getUniqueId(), duos ? "DUOS" : "SOLO", partyId, members
        );
        leader.sendMessage(ChatColor.YELLOW + "Joining the network Hunger Games queue...");
        client.post("/v1/hg/queue/join", request, NetworkDtos.QueueJoinResponse.class)
            .whenComplete((response, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    client.logFailure("HG queue join", error);
                    leader.sendMessage(ChatColor.RED + "The network queue is unavailable. Try again shortly.");
                    return;
                }
                if (!response.accepted()) {
                    leader.sendMessage(ChatColor.RED + response.message());
                    return;
                }
                for (Player member : players) {
                    member.sendMessage(ChatColor.GREEN + "Joined Hunger Games " + (duos ? "Duos" : "Solo") + ".");
                }
            }));
    }

    public boolean leaveNetworkSession(Player player) {
        if (development != null) return development.leave(player);
        if (!enabled() || player == null) {
            return false;
        }
        NetworkDtos.PlayerAssignment cached = assignments.get(player.getUniqueId());
        if (cached == null || (!cached.queued() && !cached.assignedToMatch())) {
            return false;
        }
        JsonObject body = new JsonObject();
        body.addProperty("playerId", player.getUniqueId().toString());
        client.post("/v1/hg/queue/leave", body, NetworkDtos.BasicResponse.class)
            .whenComplete((response, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    client.logFailure("HG queue leave", error);
                    player.sendMessage(ChatColor.RED + "Could not leave the network queue right now.");
                    return;
                }
                assignments.remove(player.getUniqueId());
                player.sendMessage(response.accepted() ? ChatColor.YELLOW + "Left Hunger Games."
                    : ChatColor.RED + response.message());
            }));
        return true;
    }

    public String ownershipTag(UUID playerId) {
        NetworkDtos.PlayerAssignment assignment = assignments.get(playerId);
        return assignment != null && (assignment.queued() || assignment.assignedToMatch()) ? "HG" : null;
    }

    public boolean transferToHub(Player player) {
        if (development != null) return development.returnToHub(player);
        if (!enabled() || role() != BackendRole.HG || player == null) {
            return false;
        }
        JsonObject body = new JsonObject();
        body.addProperty("playerId", player.getUniqueId().toString());
        client.post("/v1/player/send-hub", body, NetworkDtos.BasicResponse.class)
            .exceptionally(error -> {
                client.logFailure("send player to Hub", error);
                return null;
            });
        return true;
    }

    public void matchStarted(String worldName) {
        if (development != null) { development.matchStarted(worldName); return; }
        if (!enabled() || role() != BackendRole.HG || activeReservationId == null) {
            return;
        }
        activeWorldName = worldName;
        postReservationState("started", worldName);
    }

    public void matchFinished(String worldName) {
        if (development != null) { development.matchFinished(worldName); return; }
        if (!enabled() || role() != BackendRole.HG || activeReservationId == null
            || (activeWorldName != null && !activeWorldName.equals(worldName))) {
            return;
        }
        postReservationState("complete", worldName);
    }

    public void worldCleaned(String worldName) {
        if (development != null) { development.worldCleaned(worldName); return; }
        if (!enabled() || role() != BackendRole.HG || activeReservationId == null
            || (activeWorldName != null && !activeWorldName.equals(worldName))) {
            return;
        }
        String completedReservation = activeReservationId;
        postReservationState("cleaned", worldName);
        activeReservationId = null;
        activeWorldName = null;
        launchingReservations.remove(completedReservation);
    }

    public void reservationFailed(String reason) {
        if (development != null) { development.failed(reason); return; }
        if (!enabled() || role() != BackendRole.HG || activeReservationId == null) {
            return;
        }
        String failedReservation = activeReservationId;
        plugin.getLogger().warning("[Network] rejecting HG reservation=" + failedReservation + " reason=" + reason);
        postReservationState(failedReservation, "failed", null);
        activeReservationId = null;
        activeWorldName = null;
        launchingReservations.remove(failedReservation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (development != null) { development.joined(event.getPlayer()); return; }
        if (!enabled()) {
            return;
        }
        Player player = event.getPlayer();
        refreshAssignment(player);
    }

    private void refreshAssignment(Player player) {
        String id = URLEncoder.encode(player.getUniqueId().toString(), StandardCharsets.UTF_8);
        client.get("/v1/player/" + id, NetworkDtos.PlayerAssignment.class)
            .whenComplete((assignment, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    client.logFailure("player assignment lookup", error);
                    return;
                }
                if (assignment == null || assignment.state() == null) {
                    assignments.remove(player.getUniqueId());
                    return;
                }
                if (role() == BackendRole.HUB) {
                    restorePartySnapshot(assignment);
                }
                if ("NONE".equalsIgnoreCase(assignment.state())) {
                    assignments.remove(player.getUniqueId());
                    return;
                }
                assignments.put(player.getUniqueId(), assignment);
                if (role() == BackendRole.GAMEHUB && assignment.queued()) {
                    if (plugin.gameHubService() != null) {
                        plugin.gameHubService().enter(player);
                    }
                    if (plugin.menuService() != null) {
                        plugin.menuService().refreshHubInterface(player);
                    }
                } else if (role() == BackendRole.HG && assignment.assignedToMatch()) {
                    loadReservationAndLaunch(assignment.reservationId());
                }
            }));
    }

    @EventHandler
    public void onDevelopmentQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (development != null) development.quit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDevelopmentMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (development != null) development.move(event);
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onDevelopmentDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (development != null && event.getEntity() instanceof Player player && development.awaitingStart(player)) event.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onDevelopmentInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (development != null && development.awaitingStart(event.getPlayer())) event.setCancelled(true);
    }

    private void restorePartySnapshot(NetworkDtos.PlayerAssignment assignment) {
        if (assignment.partyId() == null || assignment.members() == null || assignment.members().size() < 2
            || plugin.partyService() == null) {
            return;
        }
        plugin.partyService().restoreNetworkParty(
            assignment.partyId(),
            assignment.members().stream().map(NetworkDtos.Member::uuid).toList()
        );
    }

    private void loadReservationAndLaunch(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return;
        }
        AtomicBoolean launching = launchingReservations.computeIfAbsent(reservationId, ignored -> new AtomicBoolean());
        if (!launching.compareAndSet(false, true)) {
            return;
        }
        client.get("/v1/hg/reservations/" + url(reservationId), NetworkDtos.Reservation.class)
            .whenComplete((reservation, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    launching.set(false);
                    client.logFailure("HG reservation lookup", error);
                    return;
                }
                List<UUID> expected = reservation.members().stream().map(NetworkDtos.Member::uuid).toList();
                boolean allOnline = expected.stream().allMatch(id -> {
                    Player player = Bukkit.getPlayer(id);
                    return player != null && player.isOnline();
                });
                if (!allOnline) {
                    launching.set(false);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> loadReservationAndLaunch(reservationId), 10L);
                    return;
                }
                Map<UUID, UUID> teamByPlayer = new HashMap<>();
                if (reservation.teams() != null) {
                    for (NetworkDtos.Team team : reservation.teams()) {
                        for (UUID memberId : team.members()) {
                            teamByPlayer.put(memberId, team.teamId());
                        }
                    }
                }
                activeReservationId = reservationId;
                boolean duos = "DUOS".equalsIgnoreCase(reservation.mode());
                if (!plugin.queueService().startNetworkHungerGamesReservation(
                    reservationId, expected, teamByPlayer, duos
                )) {
                    activeReservationId = null;
                    launchingReservations.remove(reservationId);
                    postReservationState(reservationId, "failed", null);
                }
            }));
    }

    private void postReservationState(String state, String worldName) {
        postReservationState(activeReservationId, state, worldName);
    }

    private void postReservationState(String reservation, String state, String worldName) {
        if (reservation == null || reservation.isBlank()) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("node", config.nodeName());
        body.addProperty("world", worldName == null ? "" : worldName);
        client.post("/v1/hg/reservations/" + url(reservation) + "/" + state, body, NetworkDtos.BasicResponse.class)
            .exceptionally(error -> {
                client.logFailure("reservation " + state, error);
                return null;
            });
    }

    private void sendHeartbeat(String status) {
        JsonObject body = new JsonObject();
        body.addProperty("node", config.nodeName());
        body.addProperty("role", config.role().name());
        body.addProperty("status", status);
        body.addProperty("players", Bukkit.getOnlinePlayers().size());
        body.addProperty("version", plugin.getDescription().getVersion());
        client.post("/v1/nodes/heartbeat", body, NetworkDtos.BasicResponse.class)
            .exceptionally(error -> {
                client.logFailure("heartbeat", error);
                return null;
            });
    }

    private String currentNodeStatus() {
        if (role() != BackendRole.HG) {
            return "ONLINE";
        }
        return activeReservationId == null ? "AVAILABLE" : activeWorldName == null ? "RESERVED" : "BUSY";
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
