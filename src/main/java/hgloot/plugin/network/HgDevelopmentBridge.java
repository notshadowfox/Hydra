package hgloot.plugin.network;

import hgloot.plugin.HGLootPlugin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/** Explicitly enabled, loopback-only HG adapter. All Bukkit work stays on the server thread. */
final class HgDevelopmentBridge {
    record Group(UUID id, UUID leaderId, List<NetworkDtos.Member> members) {}
    record Command(String action, UUID session, UUID attempt, String mode, String world, List<Group> groups) {
        List<UUID> roster() { return groups.stream().flatMap(g -> g.members().stream()).map(NetworkDtos.Member::uuid).toList(); }
    }
    record Poll(String node, UUID boot, long sequence, String workload, int players) {}
    record Event(String node, UUID boot, UUID session, UUID attempt, String action, String world, UUID player) {}

    private final HGLootPlugin plugin;
    private final NetworkNodeConfig config;
    private final CoordinatorClient client;
    private final UUID boot = UUID.randomUUID();
    private final Map<String, Event> pending = new LinkedHashMap<>();
    private final Set<String> sending = new HashSet<>();
    private final Set<UUID> admitted = new HashSet<>(), restored = new HashSet<>(), frozen = new HashSet<>();
    private BukkitTask task;
    private Command current;
    private boolean running, polling, preparing, ready, starting, active, cleaning;
    private String world;
    private long sequence;
    private final ConnectivityWatch connectivity = new ConnectivityWatch();

    static final class ConnectivityWatch {
        private volatile long failedSince = -1;
        synchronized void completed(boolean success, long now) {
            if (success) failedSince = -1;
            else if (failedSince < 0) failedSince = now;
        }
        boolean expired(long now) { long since = failedSince; return since >= 0 && now - since >= 30_000; }
    }

    HgDevelopmentBridge(HGLootPlugin plugin, NetworkNodeConfig config, CoordinatorClient client) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        String host = config.coordinatorUri().getHost();
        if (!config.enabled() || config.role() != BackendRole.HG || !("127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host))) {
            throw new IllegalArgumentException("HG handshake development requires networked HG role and loopback coordinator");
        }
    }

    void start() {
        running = true;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
        plugin.getLogger().warning("[Network][HGDev] DEVELOPMENT ONLY: session prepare/arrival handshake enabled; no production profiles");
    }

    void shutdown() {
        running = false;
        if (task != null) task.cancel();
        frozen.clear(); pending.clear(); sending.clear();
    }

    private void main(Runnable action) {
        if (running && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> { if (running) action.run(); });
    }

    private void tick() {
        if (!polling) {
            polling = true;
            String state = cleaning ? "CLEANING" : active ? "ACTIVE" : ready ? "READY" : preparing ? "PREPARING" : "AVAILABLE";
            client.post("/v2/hg/poll", new Poll(config.nodeName(), boot, ++sequence, state, Bukkit.getOnlinePlayers().size()), Command.class)
                .whenComplete((command, error) -> {
                    // HTTP completion is independent of server-thread map work. A delayed tick is not a failed request.
                    connectivity.completed(error == null, System.currentTimeMillis());
                    main(() -> {
                        polling = false;
                        if (error == null) {
                            try { accept(command); } catch (RuntimeException failure) { fail("command_failed", failure); }
                        }
                    });
                });
        }
        // A lost coordinator cannot leave a backend accepting gameplay indefinitely.
        if (current != null && connectivity.expired(System.currentTimeMillis()) && !cleaning) {
            cleaning = true;
            frozen.clear();
            for (UUID id : current.roster()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.kick(net.kyori.adventure.text.Component.text("Development coordinator unavailable. Reconnect to Hub."));
            }
        }
        if (cleaning && current != null && !preparing && plugin.queueService().cleanupDevelopmentHungerGames(current.attempt())) enqueue("cleaned", null);
        for (var entry : List.copyOf(pending.entrySet())) {
            String key = entry.getKey();
            Event event = entry.getValue();
            if (!sending.add(key)) continue;
            client.post("/v2/hg/event", event, NetworkDtos.BasicResponse.class).whenComplete((response, error) -> main(() -> {
                sending.remove(key);
                if (error == null && response.accepted()) {
                    pending.remove(key, event);
                    if (event.action().equals("cleaned") && current != null && event.attempt().equals(current.attempt())) reset();
                }
            }));
        }
    }

    private void accept(Command command) {
        if (command == null) return;
        if (command.action().equals("IDLE")) {
            if (current != null) { cleaning = true; frozen.clear(); }
            return;
        }
        if (current == null) {
            current = command;
            world = "HG-World-Dev-" + command.attempt();
            if (!command.action().equals("PREPARE") && !command.action().equals("CLEANUP")) {
                fail("missing_local_session", null);
                return;
            }
        }
        if (!current.attempt().equals(command.attempt()) || !current.session().equals(command.session())) {
            throw new IllegalStateException("Backend still owns a different attempt");
        }
        current = command;
        if (command.action().equals("CLEANUP")) { cleaning = true; frozen.clear(); return; }
        if (cleaning) return;
        switch (command.action()) {
            case "PREPARE" -> {
                if (ready) { enqueue("ready", null); return; }
                if (preparing) return;
                preparing = true;
                UUID attempt = command.attempt();
                try {
                    plugin.queueService().prepareDevelopmentHungerGames(attempt, command.roster().size())
                        .whenComplete((result, error) -> main(() -> {
                            if (current == null || !attempt.equals(current.attempt())) return;
                            preparing = false;
                            if (error != null) { fail("prepare_failed", error); return; }
                            world = result;
                            ready = true;
                            if (!cleaning) enqueue("ready", null);
                        }));
                } catch (RuntimeException failure) {
                    preparing = false;
                    fail("prepare_failed", failure);
                }
            }
            case "ADMIT", "START" -> {
                if (!ready) { fail("lost_prepared_world", null); return; }
                List<UUID> roster = command.roster();
                for (int index = 0; index < roster.size(); index++) {
                    UUID id = roster.get(index);
                    Player player = Bukkit.getPlayer(id);
                    if (player == null || admitted.contains(id)) continue;
                    if (!plugin.queueService().admitDevelopmentHungerGames(player, world, index)) { fail("arrival_placement_failed", null); return; }
                    admitted.add(id);
                    frozen.add(id);
                    enqueue("arrived", id);
                }
                if (command.action().equals("START") && !starting && !active) {
                    if (roster.stream().anyMatch(id -> Bukkit.getPlayer(id) == null || !admitted.contains(id))) { fail("roster_left_before_start", null); return; }
                    starting = true;
                    Map<UUID, UUID> groups = new HashMap<>();
                    command.groups().forEach(g -> g.members().forEach(m -> groups.put(m.uuid(), g.id())));
                    if (!plugin.queueService().startDevelopmentHungerGames(command.session(), world, roster, groups, "HG_DUOS".equals(command.mode()))) {
                        fail("match_start_rejected", null);
                    }
                }
            }
            case "RESTORE" -> {
                if (!active) { fail("active_world_missing", null); return; }
                for (UUID id : command.roster()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null && !restored.contains(id)) {
                        if (plugin.queueService().restoreDevelopmentHungerGames(player, world)) restored.add(id);
                        else { enqueue("return", id); restored.add(id); }
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unexpected command " + command.action());
        }
    }

    private void fail(String reason, Throwable error) {
        cleaning = true;
        frozen.clear();
        plugin.getLogger().warning("[Network][HGDev] " + reason + (error == null ? "" : ": " + error.getMessage()));
        if (current != null) enqueue("failed", null);
    }

    private void enqueue(String action, UUID player) {
        if (current == null) return;
        pending.put(action + ":" + player, new Event(config.nodeName(), boot, current.session(), current.attempt(), action, world, player));
    }

    void matchStarted(String name) {
        if (current == null || !name.equals(world)) return;
        active = true;
        frozen.clear();
        restored.addAll(admitted);
        enqueue("active", null);
    }
    void matchFinished(String name) { if (current != null && name.equals(world) && !cleaning) enqueue("complete", null); }
    void worldCleaned(String name) {
        if (!Bukkit.isPrimaryThread()) { main(() -> worldCleaned(name)); return; }
        if (current != null && name.equals(world)) { cleaning = true; frozen.clear(); enqueue("cleaned", null); }
    }
    void failed(String reason) { fail(reason, null); }
    boolean returnToHub(Player player) { if (current == null) return false; enqueue("return", player.getUniqueId()); return true; }
    boolean leave(Player player) {
        if (current == null || !current.roster().contains(player.getUniqueId())) return false;
        if (active) plugin.queueService().leaveQueueOrMatch(player);
        else enqueue("exit", player.getUniqueId());
        return true;
    }
    void joined(Player player) { restored.remove(player.getUniqueId()); admitted.remove(player.getUniqueId()); }
    void quit(Player player) { restored.remove(player.getUniqueId()); admitted.remove(player.getUniqueId()); frozen.remove(player.getUniqueId()); }
    void move(PlayerMoveEvent event) {
        if (!(event instanceof PlayerTeleportEvent) && frozen.contains(event.getPlayer().getUniqueId()) && event.hasChangedPosition()) {
            event.setTo(event.getFrom());
        }
    }
    boolean awaitingStart(Player player) {
        return current != null && !active && current.roster().contains(player.getUniqueId());
    }
    private void reset() {
        current = null; world = null;
        preparing = ready = starting = active = cleaning = false;
        pending.clear(); sending.clear(); frozen.clear(); admitted.clear(); restored.clear();
    }
}
