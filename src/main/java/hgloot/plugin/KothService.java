package hgloot.plugin;

import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.util.SafeItemDelivery;
import hgloot.plugin.ultraroyales.LegendaryModeRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import hgloot.plugin.util.HydraIcons;

@SuppressWarnings("deprecation")
public final class KothService implements Listener {
    private static final String PREFIX = HydraIcons.EXCLAMATION_ORANGE + " ";
    private static final int MIN_COUNTDOWN_SECONDS = 5;
    private static final int MIN_CAPTURE_SECONDS = 5;
    private static final int DEFAULT_COUNTDOWN_SECONDS = 30;
    private static final int DEFAULT_CAPTURE_SECONDS = 20;
    private static final double CAPTURE_RADIUS = 2.5D;
    private static final double DEFAULT_HALF_EXTENT_X = 2.5D;
    private static final double DEFAULT_HALF_EXTENT_Z = 2.5D;
    private static final int MIN_Y_PADDING = 3;
    private static final int MAX_Y_PADDING = 5;
    private static final int RING_POINTS = 36;
    private static final int HG_CAPTURE_PRESTIGE_XP = 35;
    private static final double HG_CAPTURE_SHARD_FRAGMENT_CHANCE = 0.50D;
    private static final long VISUAL_PERIOD_TICKS = 8L;
    private static final Particle.DustOptions RING_DUST = new Particle.DustOptions(Color.fromRGB(255, 215, 75), 1.1f);

    private final JavaPlugin plugin;
    private final Map<String, KothEvent> activeEvents = new LinkedHashMap<>();

    private BukkitTask tickTask;
    private BukkitTask visualTask;
    private Location pendingPos1;
    private Zone configuredZone;
    private int configuredCountdownSeconds = DEFAULT_COUNTDOWN_SECONDS;
    private int configuredCaptureSeconds = DEFAULT_CAPTURE_SECONDS;
    private QueueService queueService;

    public KothService(JavaPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (visualTask != null) {
            visualTask.cancel();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        visualTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderVisuals, 1L, VISUAL_PERIOD_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
        stopAll(false);
    }

    public boolean setPos1(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        this.pendingPos1 = location.clone();
        return true;
    }

    public SetPos2Result setPos2(Location location) {
        if (location == null || location.getWorld() == null) {
            return SetPos2Result.INVALID_LOCATION;
        }
        if (pendingPos1 == null || pendingPos1.getWorld() == null) {
            return SetPos2Result.MISSING_POS1;
        }
        if (!pendingPos1.getWorld().getName().equals(location.getWorld().getName())) {
            return SetPos2Result.WORLD_MISMATCH;
        }
        this.configuredZone = Zone.from(pendingPos1, location);
        this.pendingPos1 = null;
        saveZone();
        return SetPos2Result.OK;
    }

    public StartResult startEvent(Integer overrideDelaySeconds) {
        if (configuredZone == null) {
            return StartResult.NO_ZONE;
        }
        int delay = overrideDelaySeconds == null
            ? configuredCountdownSeconds
            : Math.max(MIN_COUNTDOWN_SECONDS, overrideDelaySeconds);
        return beginEvent(configuredZone, delay, configuredCaptureSeconds);
    }

    public StartResult startRuntimeEvent(Location center, Integer overrideDelaySeconds) {
        return startRuntimeEvent(center, overrideDelaySeconds, null);
    }

    public StartResult startRuntimeEvent(Location center, Integer overrideDelaySeconds, Integer overrideCaptureSeconds) {
        if (center == null || center.getWorld() == null) {
            return StartResult.INVALID_WORLD;
        }
        int delay = overrideDelaySeconds == null ? 0 : Math.max(0, overrideDelaySeconds);
        int captureSeconds = overrideCaptureSeconds == null
            ? configuredCaptureSeconds
            : Math.max(MIN_CAPTURE_SECONDS, overrideCaptureSeconds);
        return beginEvent(Zone.fromCenter(center), delay, captureSeconds);
    }

    public StartResult startRuntimeEvent(
        Location center,
        Integer overrideDelaySeconds,
        Integer overrideCaptureSeconds,
        int sizeX,
        int sizeZ,
        int minYOffset,
        int maxYOffset
    ) {
        if (center == null || center.getWorld() == null) {
            return StartResult.INVALID_WORLD;
        }
        int delay = overrideDelaySeconds == null ? 0 : Math.max(0, overrideDelaySeconds);
        int captureSeconds = overrideCaptureSeconds == null
            ? configuredCaptureSeconds
            : Math.max(MIN_CAPTURE_SECONDS, overrideCaptureSeconds);
        return beginEvent(Zone.fromCenter(center, sizeX, sizeZ, minYOffset, maxYOffset), delay, captureSeconds);
    }

    public boolean stopEvent() {
        if (activeEvents.isEmpty()) {
            return false;
        }
        stopAll(true);
        return true;
    }

    public boolean stopEventInWorld(String worldName) {
        KothEvent event = eventForWorld(worldName);
        if (event == null) {
            return false;
        }
        stopEvent(event, false);
        return true;
    }

    public String activeWorldName() {
        return activeEvents.keySet().stream().findFirst().orElse(null);
    }

    public List<String> activeWorldNames() {
        return new ArrayList<>(activeEvents.keySet());
    }

    public boolean isActiveInWorld(String worldName) {
        return eventForWorld(worldName) != null;
    }

    public CountdownAdjustResult adjustCountdownInWorld(String worldName, int deltaSeconds) {
        KothEvent event = eventForWorld(worldName);
        if (event == null || event.state != State.STARTING) {
            return CountdownAdjustResult.NO_MATCHING_EVENT;
        }
        int updated = event.countdownRemaining + deltaSeconds;
        if (updated <= 0) {
            activateEvent(event);
            return CountdownAdjustResult.STARTED_NOW;
        }
        event.countdownRemaining = updated;
        event.activeCountdownSeconds = Math.max(1, updated);
        updateBossBar(event);
        return CountdownAdjustResult.UPDATED;
    }

    public CountdownAdjustResult startNowInWorld(String worldName) {
        KothEvent event = eventForWorld(worldName);
        if (event == null || event.state != State.STARTING) {
            return CountdownAdjustResult.NO_MATCHING_EVENT;
        }
        return adjustCountdownInWorld(worldName, -event.countdownRemaining);
    }

    public boolean setCountdownSeconds(int seconds) {
        if (seconds < MIN_COUNTDOWN_SECONDS) {
            return false;
        }
        this.configuredCountdownSeconds = seconds;
        plugin.getConfig().set("koth.countdown_seconds", seconds);
        plugin.saveConfig();
        return true;
    }

    public boolean setCaptureSeconds(int seconds) {
        if (seconds < MIN_CAPTURE_SECONDS) {
            return false;
        }
        this.configuredCaptureSeconds = seconds;
        plugin.getConfig().set("koth.capture_seconds", seconds);
        plugin.saveConfig();
        return true;
    }

    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Configured Countdown: " + configuredCountdownSeconds + "s");
        lines.add("Configured Capture: " + configuredCaptureSeconds + "s");
        lines.add("Configured Zone: " + (configuredZone == null ? "not set" : configuredZone.summary()));
        lines.add("Active Events: " + activeEvents.size());
        for (KothEvent event : activeEvents.values()) {
            lines.addAll(statusLines(event));
        }
        return lines;
    }

    public List<String> statusLines(String worldName) {
        KothEvent event = eventForWorld(worldName);
        if (event == null) {
            return List.of();
        }
        return statusLines(event);
    }

    public String sidebarLine(String worldName) {
        KothEvent event = eventForWorld(worldName);
        if (event == null) {
            return null;
        }
        if (event.state == State.STARTING) {
            return ChatColor.AQUA + "KOTH: " + ChatColor.WHITE + formatClock(event.countdownRemaining);
        }
        Occupancy occupancy = activeOccupancy(event);
        if (isContested(occupancy)) {
            return ChatColor.AQUA + "KOTH: " + ChatColor.RED + "Contested";
        }
        if (event.capturingPlayerId != null && occupancy.capturers().size() == 1 && occupancy.present().size() == 1) {
            int remaining = Math.max(0, event.activeCaptureSeconds - event.captureProgressSeconds);
            return ChatColor.AQUA + "KOTH: " + ChatColor.WHITE + formatClock(remaining);
        }
        return ChatColor.AQUA + "KOTH: " + ChatColor.GREEN + "Active";
    }

    public String sidebarLocationLine(String worldName) {
        KothEvent event = eventForWorld(worldName);
        if (event == null) return null;
        if (event.state == State.STARTING) return sidebarLine(worldName);
        return activeCoordinatesLine(event.zone.centerX(), event.zone.centerZ());
    }

    static String activeCoordinatesLine(double centerX, double centerZ) {
        return ChatColor.AQUA + "KOTH: " + ChatColor.WHITE
            + (int) Math.floor(centerX) + ", " + (int) Math.floor(centerZ);
    }

    private List<String> statusLines(KothEvent event) {
        List<String> lines = new ArrayList<>();
        lines.add("World: " + event.zone.worldName());
        lines.add("State: " + event.state.name());
        lines.add("Capture: " + event.activeCaptureSeconds + "s");
        lines.add("Zone: " + event.zone.summary());
        if (event.state == State.STARTING) {
            lines.add("Starts in: " + event.countdownRemaining + "s");
        } else if (event.state == State.ACTIVE) {
            Player capturer = event.capturingPlayerId == null ? null : Bukkit.getPlayer(event.capturingPlayerId);
            lines.add("Capturer: " + (capturer == null ? "none" : capturer.getName()));
            lines.add("Progress: " + event.captureProgressSeconds + "/" + event.activeCaptureSeconds + "s");
        }
        return lines;
    }

    private StartResult beginEvent(Zone zone, int delaySeconds, int captureSeconds) {
        if (zone == null) {
            return StartResult.NO_ZONE;
        }
        World world = Bukkit.getWorld(zone.worldName());
        if (world == null) {
            return StartResult.INVALID_WORLD;
        }
        if (activeEvents.containsKey(zone.worldName())) {
            return StartResult.ALREADY_RUNNING;
        }
        KothEvent event = new KothEvent(zone, Math.max(0, delaySeconds), Math.max(MIN_CAPTURE_SECONDS, captureSeconds));
        activeEvents.put(zone.worldName(), event);
        initializeBeaconBeam(event, world);
        showBossBar(event);
        if (delaySeconds <= 0) {
            activateEvent(event);
            return StartResult.STARTED;
        }
        event.state = State.STARTING;
        updateBossBar(event);
        broadcast(event, PREFIX + ChatColor.YELLOW + "KOTH starts in " + delaySeconds + "s. "
            + ChatColor.GRAY + "Capture it for a random production legendary.");
        broadcastTitle(zone.worldName(), ChatColor.GOLD + "KOTH STARTING", ChatColor.YELLOW + "Capture the hill soon", Sound.BLOCK_BEACON_POWER_SELECT, 1.0f);
        return StartResult.STARTED;
    }

    private void tick() {
        for (KothEvent event : new ArrayList<>(activeEvents.values())) {
            tickEvent(event);
        }
    }

    private void tickEvent(KothEvent event) {
        if (event.state == State.IDLE) {
            stopEvent(event, false);
            return;
        }
        if (event.state == State.STARTING) {
            event.countdownRemaining--;
            if (event.countdownRemaining <= 0) {
                activateEvent(event);
            } else if (shouldAnnounceCountdown(event.countdownRemaining)) {
                broadcast(event, PREFIX + ChatColor.YELLOW + "KOTH starts in " + event.countdownRemaining + "s.");
            }
            updateBossBar(event);
            return;
        }

        Occupancy occupancy = activeOccupancy(event);
        if (occupancy.capturers().size() == 1 && occupancy.present().size() == 1) {
            Player capturer = occupancy.capturers().get(0);
            if (!capturer.getUniqueId().equals(event.capturingPlayerId)) {
                event.capturingPlayerId = capturer.getUniqueId();
                event.captureProgressSeconds = 0;
                announceOccupancy(event, "capturing:" + capturer.getUniqueId(),
                    PREFIX + ChatColor.GOLD + capturer.getName() + ChatColor.YELLOW + " is capturing KOTH.");
            }
            event.captureProgressSeconds++;
            if (event.captureProgressSeconds >= event.activeCaptureSeconds) {
                String rewardName = rewardWinner(capturer);
                broadcast(event, PREFIX + ChatColor.GREEN + "CAPTURED "
                    + ChatColor.GOLD + capturer.getName()
                    + ChatColor.GREEN + " won " + (rewardName == null ? "a legendary" : rewardName) + ".");
                stopEvent(event, false);
                return;
            }
        } else if (occupancy.capturers().isEmpty()) {
            if (event.capturingPlayerId != null || event.captureProgressSeconds > 0) {
                announceOccupancy(event, "empty", PREFIX + ChatColor.GRAY + "KOTH is no longer being captured.");
            }
            event.capturingPlayerId = null;
            event.captureProgressSeconds = 0;
        } else if (isContested(occupancy)) {
            announceOccupancy(event, "contested", PREFIX + ChatColor.RED + "KOTH CONTESTED. "
                + ChatColor.GRAY + "Clear the hill.");
            event.capturingPlayerId = null;
            event.captureProgressSeconds = 0;
        } else {
            event.capturingPlayerId = null;
            event.captureProgressSeconds = 0;
        }
        updateBossBar(event);
    }

    private Occupancy activeOccupancy(KothEvent event) {
        List<Player> present = new ArrayList<>();
        List<Player> capturers = new ArrayList<>();
        World world = Bukkit.getWorld(event.zone.worldName());
        if (world == null) {
            return new Occupancy(present, capturers);
        }
        for (Player player : world.getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (queueService != null && !queueService.isPlayerInMatch(player.getUniqueId())) {
                continue;
            }
            if (!event.zone.contains(player.getLocation())) {
                continue;
            }
            if (queueService != null && queueService.isCustomSpectator(player.getUniqueId())) {
                continue;
            }
            if (isEligible(player)) {
                present.add(player);
                capturers.add(player);
            }
        }
        return new Occupancy(present, capturers);
    }

    private boolean isEligible(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        GameMode gameMode = player.getGameMode();
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
    }

    private boolean isContested(Occupancy occupancy) {
        if (occupancy == null) {
            return false;
        }
        return occupancy.capturers().size() > 1
            || (occupancy.capturers().size() == 1 && occupancy.present().size() > 1);
    }

    private void showBossBar(KothEvent event) {
        event.bossBar.setVisible(true);
        refreshBossBarPlayers(event);
    }

    private void updateBossBar(KothEvent event) {
        refreshBossBarPlayers(event);
        if (event.state == State.STARTING) {
            event.bossBar.setColor(BarColor.YELLOW);
            event.bossBar.setTitle(ChatColor.GOLD + "KOTH starts in " + ChatColor.YELLOW + event.countdownRemaining + "s");
            double progress = event.activeCountdownSeconds <= 0
                ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, event.countdownRemaining / (double) event.activeCountdownSeconds));
            event.bossBar.setProgress(progress);
            return;
        }
        Occupancy occupancy = activeOccupancy(event);
        if (isContested(occupancy)) {
            event.bossBar.setColor(BarColor.RED);
            event.bossBar.setTitle(ChatColor.RED + "KOTH contested");
            event.bossBar.setProgress(1.0D);
            return;
        }
        Player capturer = event.capturingPlayerId == null ? null : Bukkit.getPlayer(event.capturingPlayerId);
        if (capturer != null && occupancy.capturers().size() == 1 && occupancy.present().size() == 1) {
            event.bossBar.setColor(BarColor.GREEN);
            event.bossBar.setTitle(ChatColor.GOLD + capturer.getName() + ChatColor.YELLOW + " capturing KOTH "
                + ChatColor.GRAY + "(" + event.captureProgressSeconds + "/" + event.activeCaptureSeconds + "s)");
            event.bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, event.captureProgressSeconds / (double) event.activeCaptureSeconds)));
            return;
        }
        event.bossBar.setColor(BarColor.BLUE);
        event.bossBar.setTitle(ChatColor.GOLD + "KOTH active" + ChatColor.GRAY + " - enter the hill to capture");
        event.bossBar.setProgress(0.0D);
    }

    private void refreshBossBarPlayers(KothEvent event) {
        event.bossBar.removeAll();
        World world = Bukkit.getWorld(event.zone.worldName());
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player != null && player.isOnline()) {
                event.bossBar.addPlayer(player);
            }
        }
    }

    private void announceOccupancy(KothEvent event, String stateKey, String message) {
        if (stateKey.equals(event.lastOccupancyState)) {
            return;
        }
        event.lastOccupancyState = stateKey;
        broadcast(event, message);
    }

    private void activateEvent(KothEvent event) {
        event.state = State.ACTIVE;
        event.countdownRemaining = 0;
        event.captureProgressSeconds = 0;
        event.capturingPlayerId = null;
        event.lastOccupancyState = "";
        updateBossBar(event);
        broadcast(event, PREFIX + ChatColor.GREEN + "KOTH is active "
            + ChatColor.GRAY + "hold the point for " + ChatColor.GOLD + "a legendary");
        broadcastTitle(event.zone.worldName(), ChatColor.GOLD + "KOTH ACTIVE", ChatColor.YELLOW + "Hold the point for a legendary", Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
    }

    private void broadcastTitle(String worldName, String title, String subtitle, Sound sound, float pitch) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle, 5, 35, 10);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, pitch);
            }
        }
    }

    private void stopAll(boolean announceStop) {
        for (KothEvent event : new ArrayList<>(activeEvents.values())) {
            stopEvent(event, announceStop);
        }
    }

    private void stopEvent(KothEvent event, boolean announceStop) {
        if (event == null) {
            return;
        }
        String stoppingWorldName = event.zone.worldName();
        removeBeaconBeam(event);
        activeEvents.remove(stoppingWorldName);
        if (announceStop) {
            sendWorldPlayers(stoppingWorldName, PREFIX + ChatColor.RED + "KOTH stopped.");
        }
        event.bossBar.removeAll();
        event.bossBar.setVisible(false);
    }

    private void broadcast(KothEvent event, String message) {
        if (event != null) {
            sendWorldPlayers(event.zone.worldName(), message);
        }
    }

    private void sendWorldPlayers(String worldName, String message) {
        if (worldName == null || worldName.isBlank() || message == null || message.isBlank()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    private KothEvent eventForWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return activeEvents.get(worldName);
    }

    private boolean shouldAnnounceCountdown(int seconds) {
        return seconds == 30 || seconds == 15 || seconds == 10 || seconds <= 5;
    }

    private String formatClock(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int minutes = safe / 60;
        int seconds = safe % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void loadFromConfig() {
        configuredCountdownSeconds = Math.max(
            MIN_COUNTDOWN_SECONDS,
            plugin.getConfig().getInt("koth.countdown_seconds", DEFAULT_COUNTDOWN_SECONDS)
        );
        configuredCaptureSeconds = Math.max(
            MIN_CAPTURE_SECONDS,
            plugin.getConfig().getInt("koth.capture_seconds", DEFAULT_CAPTURE_SECONDS)
        );
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("koth.region");
        if (section == null) {
            configuredZone = null;
            return;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            configuredZone = null;
            return;
        }
        if (section.contains("center_x") && section.contains("center_z")) {
            configuredZone = new Zone(
                worldName,
                section.getDouble("center_x"),
                section.getDouble("center_z"),
                section.getInt("min_y"),
                section.getInt("max_y"),
                section.getDouble("half_extent_x", DEFAULT_HALF_EXTENT_X),
                section.getDouble("half_extent_z", DEFAULT_HALF_EXTENT_Z)
            );
            return;
        }
        int minX = section.getInt("min_x");
        int minY = section.getInt("min_y");
        int minZ = section.getInt("min_z");
        int maxX = section.getInt("max_x");
        int maxY = section.getInt("max_y");
        int maxZ = section.getInt("max_z");
        configuredZone = new Zone(
            worldName,
            ((minX + 0.5D) + (maxX + 0.5D)) / 2.0D,
            ((minZ + 0.5D) + (maxZ + 0.5D)) / 2.0D,
            minY - MIN_Y_PADDING,
            maxY + MAX_Y_PADDING,
            Math.max(DEFAULT_HALF_EXTENT_X, ((Math.abs(maxX - minX) + 1) / 2.0D)),
            Math.max(DEFAULT_HALF_EXTENT_Z, ((Math.abs(maxZ - minZ) + 1) / 2.0D))
        );
    }

    private void saveZone() {
        if (configuredZone == null) {
            plugin.getConfig().set("koth.region", null);
        } else {
            plugin.getConfig().set("koth.region.world", configuredZone.worldName());
            plugin.getConfig().set("koth.region.center_x", configuredZone.centerX());
            plugin.getConfig().set("koth.region.center_z", configuredZone.centerZ());
            plugin.getConfig().set("koth.region.min_y", configuredZone.minY());
            plugin.getConfig().set("koth.region.max_y", configuredZone.maxY());
            plugin.getConfig().set("koth.region.radius", CAPTURE_RADIUS);
            plugin.getConfig().set("koth.region.half_extent_x", configuredZone.halfExtentX());
            plugin.getConfig().set("koth.region.half_extent_z", configuredZone.halfExtentZ());
            plugin.getConfig().set("koth.region.min_x", null);
            plugin.getConfig().set("koth.region.min_z", null);
            plugin.getConfig().set("koth.region.max_x", null);
            plugin.getConfig().set("koth.region.max_z", null);
        }
        plugin.saveConfig();
    }

    private void renderVisuals() {
        for (KothEvent event : new ArrayList<>(activeEvents.values())) {
            World world = Bukkit.getWorld(event.zone.worldName());
            if (world == null) {
                continue;
            }
            ensureBeaconBeam(event, world);
            renderGroundRing(world, event.zone);
        }
    }

    private void renderGroundRing(World world, Zone zone) {
        for (int i = 0; i < RING_POINTS; i++) {
            double t = (double) i / RING_POINTS;
            double perimeter = (zone.halfExtentX() * 4.0D) + (zone.halfExtentZ() * 4.0D);
            double distance = t * perimeter;
            double x;
            double z;
            if (distance < zone.halfExtentX() * 2.0D) {
                x = zone.centerX() - zone.halfExtentX() + distance;
                z = zone.centerZ() - zone.halfExtentZ();
            } else if (distance < (zone.halfExtentX() * 2.0D) + (zone.halfExtentZ() * 2.0D)) {
                x = zone.centerX() + zone.halfExtentX();
                z = zone.centerZ() - zone.halfExtentZ() + (distance - (zone.halfExtentX() * 2.0D));
            } else if (distance < (zone.halfExtentX() * 4.0D) + (zone.halfExtentZ() * 2.0D)) {
                x = zone.centerX() + zone.halfExtentX() - (distance - ((zone.halfExtentX() * 2.0D) + (zone.halfExtentZ() * 2.0D)));
                z = zone.centerZ() + zone.halfExtentZ();
            } else {
                x = zone.centerX() - zone.halfExtentX();
                z = zone.centerZ() + zone.halfExtentZ() - (distance - ((zone.halfExtentX() * 4.0D) + (zone.halfExtentZ() * 2.0D)));
            }
            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);
            double y = world.getHighestBlockYAt(blockX, blockZ) + 0.18D;
            Location point = new Location(world, x, y, z);
            world.spawnParticle(Particle.DUST, point, 2, 0.04D, 0.03D, 0.04D, 0.0D, RING_DUST, true);
        }
    }

    private void initializeBeaconBeam(KothEvent event, World world) {
        removeBeaconBeam(event);
        if (world == null || event == null) {
            return;
        }
        Zone zone = event.zone;
        int x = zone.centerBlockX();
        int z = zone.centerBlockZ();
        int topY = world.getHighestBlockYAt(x, z);
        int baseY = Math.max(world.getMinHeight(), topY - 1);
        int beaconBlockY = baseY + 1;
        event.beaconWorldName = world.getName();
        event.beaconX = x;
        event.beaconY = beaconBlockY;
        event.beaconZ = z;
        captureBeaconRestoreStates(event, world, baseY);
        placeBeaconBeam(event, world, baseY);
    }

    private void ensureBeaconBeam(KothEvent event, World world) {
        if (event == null || world == null || event.beaconWorldName == null || !event.beaconWorldName.equals(world.getName())) {
            return;
        }
        int baseY = event.beaconY - 1;
        if (baseY < world.getMinHeight()) {
            return;
        }
        if (world.getBlockAt(event.beaconX, event.beaconY, event.beaconZ).getType() != Material.BEACON) {
            placeBeaconBeam(event, world, baseY);
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getBlockAt(event.beaconX + dx, baseY, event.beaconZ + dz).getType() != Material.IRON_BLOCK) {
                    placeBeaconBeam(event, world, baseY);
                    return;
                }
            }
        }
    }

    private void captureBeaconRestoreStates(KothEvent event, World world, int baseY) {
        event.beaconRestoreStates.clear();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                event.beaconRestoreStates.add(world.getBlockAt(event.beaconX + dx, baseY, event.beaconZ + dz).getState());
            }
        }
        event.beaconRestoreStates.add(world.getBlockAt(event.beaconX, event.beaconY, event.beaconZ).getState());
    }

    private void placeBeaconBeam(KothEvent event, World world, int baseY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(event.beaconX + dx, baseY, event.beaconZ + dz).setType(Material.IRON_BLOCK, false);
            }
        }
        world.getBlockAt(event.beaconX, event.beaconY, event.beaconZ).setType(Material.BEACON, false);
    }

    private void removeBeaconBeam(KothEvent event) {
        if (event == null) {
            return;
        }
        if (event.beaconRestoreStates.isEmpty()) {
            event.clearBeaconTracking();
            return;
        }
        World world = event.beaconWorldName == null ? null : Bukkit.getWorld(event.beaconWorldName);
        if (world == null) {
            event.clearBeaconTracking();
            return;
        }
        for (int i = event.beaconRestoreStates.size() - 1; i >= 0; i--) {
            event.beaconRestoreStates.get(i).update(true, false);
        }
        event.clearBeaconTracking();
    }

    private String rewardWinner(Player capturer) {
        ItemStack reward = hgloot.plugin.ultraroyales.OriginalLegendaryRecipes
            .randomProductionReward(ThreadLocalRandom.current());
        if (reward == null) {
            plugin.getLogger().severe("[KOTH] Production legendary pool was empty; reward was not issued.");
            return null;
        }
        reward = Items.withModeLabel(reward, LegendaryModeRules.isHungerGamesWorld(capturer.getWorld()));
        SafeItemDelivery.Destination destination = SafeItemDelivery.deliver(capturer, reward);
        if (destination == SafeItemDelivery.Destination.ENDER_CHEST) {
            capturer.sendMessage(PREFIX + ChatColor.YELLOW
                + "Your inventory was full, so the legendary was placed in your Ender Chest.");
        } else if (destination == SafeItemDelivery.Destination.OWNED_DROP) {
            capturer.sendMessage(PREFIX + ChatColor.YELLOW
                + "Your inventory and Ender Chest were full, so the legendary was dropped for you.");
        }
        String rewardName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
            ? ChatColor.stripColor(reward.getItemMeta().getDisplayName()) : "a production legendary";
        capturer.sendMessage(PREFIX + ChatColor.GREEN + "You received " + ChatColor.GOLD + rewardName
            + ChatColor.GREEN + " for capturing KOTH.");
        awardHungerGamesProgression(capturer);
        capturer.playSound(capturer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        return rewardName;
    }

    private void awardHungerGamesProgression(Player capturer) {
        if (!LegendaryModeRules.isHungerGamesWorld(capturer.getWorld()) || !(plugin instanceof HGLootPlugin hydra)) {
            return;
        }
        ProgressionService progressionService = hydra.progressionService();
        if (progressionService == null) {
            return;
        }
        progressionService.queuePrestigeXp(capturer.getUniqueId(), HG_CAPTURE_PRESTIGE_XP, "HG_KOTH_CAPTURE");
        if (ThreadLocalRandom.current().nextDouble() <= HG_CAPTURE_SHARD_FRAGMENT_CHANCE) {
            progressionService.queueShardFragments(capturer.getUniqueId(), 1, "HG_KOTH_CAPTURE");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (KothEvent kothEvent : activeEvents.values()) {
            if (player.getWorld() != null && player.getWorld().getName().equals(kothEvent.zone.worldName())) {
                kothEvent.bossBar.addPlayer(player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        for (KothEvent kothEvent : activeEvents.values()) {
            kothEvent.bossBar.removePlayer(event.getPlayer());
            if (playerId.equals(kothEvent.capturingPlayerId)) {
                kothEvent.capturingPlayerId = null;
                kothEvent.captureProgressSeconds = 0;
                kothEvent.lastOccupancyState = "";
            }
        }
    }

    public enum SetPos2Result {
        OK,
        MISSING_POS1,
        WORLD_MISMATCH,
        INVALID_LOCATION
    }

    public enum StartResult {
        STARTED,
        NO_ZONE,
        INVALID_WORLD,
        ALREADY_RUNNING
    }

    public enum CountdownAdjustResult {
        UPDATED,
        STARTED_NOW,
        NO_MATCHING_EVENT
    }

    private record Occupancy(List<Player> present, List<Player> capturers) {
    }

    private enum State {
        IDLE,
        STARTING,
        ACTIVE
    }

    private static final class KothEvent {
        private final Zone zone;
        private final BossBar bossBar;
        private int activeCountdownSeconds;
        private int activeCaptureSeconds;
        private int countdownRemaining;
        private UUID capturingPlayerId;
        private int captureProgressSeconds;
        private String lastOccupancyState = "";
        private State state = State.IDLE;
        private final List<BlockState> beaconRestoreStates = new ArrayList<>();
        private String beaconWorldName;
        private int beaconX;
        private int beaconY;
        private int beaconZ;

        private KothEvent(Zone zone, int delaySeconds, int captureSeconds) {
            this.zone = zone;
            this.activeCountdownSeconds = Math.max(0, delaySeconds);
            this.activeCaptureSeconds = Math.max(MIN_CAPTURE_SECONDS, captureSeconds);
            this.countdownRemaining = Math.max(0, delaySeconds);
            this.bossBar = Bukkit.createBossBar(ChatColor.GOLD + "KOTH", BarColor.YELLOW, BarStyle.SOLID);
            this.bossBar.setVisible(false);
        }

        private void clearBeaconTracking() {
            beaconRestoreStates.clear();
            beaconWorldName = null;
            beaconX = 0;
            beaconY = 0;
            beaconZ = 0;
        }
    }

    private record Zone(String worldName, double centerX, double centerZ, int minY, int maxY, double halfExtentX, double halfExtentZ) {
        private static Zone from(Location a, Location b) {
            if (a == null || a.getWorld() == null || b == null || b.getWorld() == null) {
                throw new IllegalArgumentException("KOTH positions must be in loaded worlds.");
            }
            double centerX = ((a.getBlockX() + 0.5D) + (b.getBlockX() + 0.5D)) / 2.0D;
            double centerZ = ((a.getBlockZ() + 0.5D) + (b.getBlockZ() + 0.5D)) / 2.0D;
            return new Zone(
                a.getWorld().getName(),
                centerX,
                centerZ,
                Math.min(a.getBlockY(), b.getBlockY()) - MIN_Y_PADDING,
                Math.max(a.getBlockY(), b.getBlockY()) + MAX_Y_PADDING,
                DEFAULT_HALF_EXTENT_X,
                DEFAULT_HALF_EXTENT_Z
            );
        }

        private static Zone fromCenter(Location center) {
            if (center == null || center.getWorld() == null) {
                throw new IllegalArgumentException("KOTH center must be in a loaded world.");
            }
            int blockY = center.getBlockY();
            return new Zone(
                center.getWorld().getName(),
                center.getBlockX() + 0.5D,
                center.getBlockZ() + 0.5D,
                blockY - MIN_Y_PADDING,
                blockY + MAX_Y_PADDING,
                DEFAULT_HALF_EXTENT_X,
                DEFAULT_HALF_EXTENT_Z
            );
        }

        private static Zone fromCenter(Location center, int sizeX, int sizeZ, int minYOffset, int maxYOffset) {
            if (center == null || center.getWorld() == null) {
                throw new IllegalArgumentException("KOTH center must be in a loaded world.");
            }
            int blockY = center.getBlockY();
            return new Zone(
                center.getWorld().getName(),
                center.getBlockX() + 0.5D,
                center.getBlockZ() + 0.5D,
                blockY + Math.min(minYOffset, maxYOffset),
                blockY + Math.max(minYOffset, maxYOffset),
                Math.max(0.5D, Math.max(1, sizeX) / 2.0D),
                Math.max(0.5D, Math.max(1, sizeZ) / 2.0D)
            );
        }

        private boolean contains(Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }
            if (!location.getWorld().getName().equals(worldName)) {
                return false;
            }
            double y = location.getY();
            if (y < minY || y > maxY + 1.999D) {
                return false;
            }
            return Math.abs(location.getX() - centerX) <= halfExtentX
                && Math.abs(location.getZ() - centerZ) <= halfExtentZ;
        }

        private int centerBlockX() {
            return (int) Math.floor(centerX);
        }

        private int centerBlockZ() {
            return (int) Math.floor(centerZ);
        }

        private String summary() {
            return String.format(
                Locale.US,
                "%s [center=%.1f,%.1f size=%.1fx%.1f y=%d..%d]",
                worldName,
                centerX,
                centerZ,
                halfExtentX * 2.0D,
                halfExtentZ * 2.0D,
                minY,
                maxY
            );
        }
    }
}
