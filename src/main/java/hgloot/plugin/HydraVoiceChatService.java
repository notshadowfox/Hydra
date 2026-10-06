package hgloot.plugin;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class HydraVoiceChatService implements Listener, VoicechatPlugin {
    private static final long JOIN_CHECK_DELAY_TICKS = 100L;

    private final JavaPlugin plugin;
    private final Set<UUID> voiceActivePlayers = new HashSet<>();
    private final Set<UUID> voiceDetectedPlayers = new HashSet<>();
    private final Set<UUID> joinMessageSent = new HashSet<>();
    private final Map<String, Group> teamGroups = new HashMap<>();
    private final Map<UUID, Group> assignedTeamGroups = new HashMap<>();
    private VoicechatServerApi voicechatApi;
    private BukkitTask teamSyncTask;
    private boolean registered;

    public HydraVoiceChatService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (registered) {
            return;
        }
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (plugin instanceof HGLootPlugin hydra && hydra.playerSettingsService() != null) {
            Bukkit.getOnlinePlayers().forEach(player -> hydra.playerSettingsService().loadVoiceSnapshot(player.getUniqueId()));
        }
        if (service != null) {
            service.registerPlugin(this);
            registered = true;
            plugin.getLogger().info("[VoiceChat] Hydra voice integration registered.");
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::start, 1L);
    }

    public void shutdown() {
        if (teamSyncTask != null) {
            teamSyncTask.cancel();
            teamSyncTask = null;
        }
        clearTeamGroups();
        voicechatApi = null;
        registered = false;
        voiceActivePlayers.clear();
        voiceDetectedPlayers.clear();
        joinMessageSent.clear();
    }

    public boolean isVoiceChatActive(UUID playerId) {
        return playerId != null && voiceActivePlayers.contains(playerId);
    }

    @Override
    public String getPluginId() {
        return "hydramain";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (!(api instanceof VoicechatServerApi serverApi)) {
            plugin.getLogger().warning("[VoiceChat] Server API unavailable; automatic team groups disabled.");
            return;
        }
        voicechatApi = serverApi;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (teamSyncTask != null) teamSyncTask.cancel();
            teamSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncTeamGroups, 1L, 20L);
        });
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(PlayerConnectedEvent.class, this::onVoicePlayerConnected);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onVoicePlayerDisconnected);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onSoundPacket);
        registration.registerEvent(EntitySoundPacketEvent.class, this::onSoundPacket);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onSoundPacket);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (plugin instanceof HGLootPlugin hydra && hydra.playerSettingsService() != null) {
            hydra.playerSettingsService().loadVoiceSnapshot(playerId);
        }
        joinMessageSent.remove(playerId);
        voiceActivePlayers.remove(playerId);
        voiceDetectedPlayers.remove(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendJoinVoiceMessage(player), JOIN_CHECK_DELAY_TICKS);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (plugin instanceof HGLootPlugin hydra && hydra.playerSettingsService() != null) {
            hydra.playerSettingsService().unloadVoiceSnapshot(playerId);
        }
        voiceActivePlayers.remove(playerId);
        voiceDetectedPlayers.remove(playerId);
        joinMessageSent.remove(playerId);
    }

    private void onVoicePlayerConnected(PlayerConnectedEvent event) {
        syncConnectionState(event.getConnection());
        Bukkit.getScheduler().runTask(plugin, this::syncTeamGroups);
    }

    private void onVoicePlayerDisconnected(PlayerDisconnectedEvent event) {
        UUID playerId = event.getPlayerUuid();
        if (playerId == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> voiceActivePlayers.remove(playerId));
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || connection.getPlayer() == null) {
            return;
        }
        if (plugin instanceof HGLootPlugin hydra
            && (hydra.isModerationMuted(connection.getPlayer().getUuid())
                || !proxyVoiceEnabled(connection.getPlayer().getUuid()))) {
            event.cancel();
        }
    }

    private void onSoundPacket(SoundPacketEvent<?> event) {
        VoicechatConnection receiver = event.getReceiverConnection();
        VoicechatConnection sender = event.getSenderConnection();
        if ((receiver != null && receiver.getPlayer() != null
                && !proxyVoiceEnabled(receiver.getPlayer().getUuid()))
            || (sender != null && sender.getPlayer() != null
                && !proxyVoiceEnabled(sender.getPlayer().getUuid()))) {
            event.cancel();
        }
    }

    private boolean proxyVoiceEnabled(UUID playerId) {
        return !(plugin instanceof HGLootPlugin hydra) || hydra.playerSettingsService() == null
            || hydra.playerSettingsService().enabledFromVoiceThread(playerId, PlayerSettingsService.Toggle.PROXY_CHAT);
    }

    public void onProxyPreferenceChanged(Player player) {
        UUID playerId = player.getUniqueId();
        if (!proxyVoiceEnabled(playerId)) {
            voiceActivePlayers.remove(playerId);
        } else if (voicechatApi != null) {
            VoicechatConnection connection = voicechatApi.getConnectionOf(playerId);
            if (connection != null && !connection.isDisabled()) voiceActivePlayers.add(playerId);
        }
        syncTeamGroups();
    }

    private void syncConnectionState(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) {
            return;
        }
        UUID playerId = connection.getPlayer().getUuid();
        Bukkit.getScheduler().runTask(plugin, () -> {
            voiceDetectedPlayers.add(playerId);
            if (connection.isDisabled() || !proxyVoiceEnabled(playerId)) {
                voiceActivePlayers.remove(playerId);
                return;
            }
            voiceActivePlayers.add(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && joinMessageSent.add(playerId)) {
                sendVoiceEnabledMessage(player);
            }
        });
    }

    private void syncTeamGroups() {
        if (voicechatApi == null || !(plugin instanceof HGLootPlugin hydra)
            || hydra.teamProtectionService() == null) return;
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Set<UUID> seen = new HashSet<>();
        Set<String> activeKeys = new HashSet<>();
        for (Player player : onlinePlayers) {
            UUID playerId = player.getUniqueId();
            seen.add(playerId);
            if (!proxyVoiceEnabled(playerId)) {
                clearTeamGroup(playerId);
                continue;
            }
            Set<UUID> members = hydra.teamProtectionService().currentTeamMembers(player, onlinePlayers);
            if (members.isEmpty()) {
                clearTeamGroup(playerId);
                continue;
            }
            String teamKey = teamKey(members);
            activeKeys.add(teamKey);
            Group group = teamGroups.computeIfAbsent(teamKey,
                ignored -> voicechatApi.createGroup("Hydra Team", UUID.randomUUID().toString()));
            VoicechatConnection connection = voicechatApi.getConnectionOf(playerId);
            if (connection == null || connection.isDisabled()) {
                continue;
            }
            if (connection.getGroup() == null || !connection.getGroup().getId().equals(group.getId())) {
                connection.setGroup(group);
            }
            assignedTeamGroups.put(playerId, group);
        }
        assignedTeamGroups.keySet().removeIf(playerId -> !seen.contains(playerId));
        teamGroups.keySet().removeIf(key -> !activeKeys.contains(key));
    }

    private void clearTeamGroup(UUID playerId) {
        Group assigned = assignedTeamGroups.remove(playerId);
        if (assigned == null || voicechatApi == null) return;
        VoicechatConnection connection = voicechatApi.getConnectionOf(playerId);
        if (connection != null && connection.getGroup() != null
            && connection.getGroup().getId().equals(assigned.getId())) {
            connection.setGroup(null);
        }
    }

    private void clearTeamGroups() {
        for (UUID playerId : new HashSet<>(assignedTeamGroups.keySet())) {
            clearTeamGroup(playerId);
        }
        assignedTeamGroups.clear();
        teamGroups.clear();
    }

    static String teamKey(Set<UUID> members) {
        return members.stream()
            .sorted(Comparator.comparing(UUID::toString))
            .map(UUID::toString)
            .reduce((left, right) -> left + ":" + right)
            .orElse("");
    }

    private void sendJoinVoiceMessage(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (joinMessageSent.contains(playerId)) {
            return;
        }
        if (voiceActivePlayers.contains(playerId)) {
            if (joinMessageSent.add(playerId)) {
                sendVoiceEnabledMessage(player);
            }
            return;
        }
        if (!voiceDetectedPlayers.contains(playerId)) {
            if (joinMessageSent.add(playerId)) {
                player.sendMessage("§7Install Simple Voice Chat for proximity chat.");
            }
        }
    }

    private void sendVoiceEnabledMessage(Player player) {
        player.sendMessage("§aProximity Chat: Enabled");
        if (plugin instanceof HGLootPlugin hydra
            && hydra.queueService() != null
            && hydra.queueService().isVoiceChatSilverBonusEnabled()) {
            player.sendMessage("§aVoice Bonus: §e+" + hydra.queueService().voiceChatSilverBonusPercent() + "% Rewards");
        }
    }
}
