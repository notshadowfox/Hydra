package hgloot.plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Presentation-only victory reward tally. Reward granting remains owned by the calling game service. */
public final class VictoryResultsService implements Listener {
    static final int VICTORY_TICK = 0;
    static final int LABELS_TICK = 13;
    static final int XP_START_TICK = 17;
    static final int XP_END_TICK = 37;
    static final int COINS_START_TICK = 41;
    static final int COINS_END_TICK = 61;
    static final int FRAGMENTS_START_TICK = 65;
    static final int FRAGMENTS_END_TICK = 74;
    static final int WIN_BONUS_TICK = 82;
    static final int COMPLETE_TICK = 118;

    private static final Title.Times OVERLAY_TIMES = Title.Times.times(
        Duration.ZERO,
        Duration.ofMillis((COMPLETE_TICK + 10L) * 50L),
        Duration.ofMillis(150L)
    );

    private final HGLootPlugin plugin;
    private final Map<UUID, RunningPresentation> running = new HashMap<>();
    private final SoundCue victoryPrimary;
    private final SoundCue victoryAccent;
    private final SoundCue xpTick;
    private final SoundCue xpComplete;
    private final SoundCue coinsTick;
    private final SoundCue fragmentsTick;

    public VictoryResultsService(HGLootPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection sounds = plugin.getConfig().getConfigurationSection("results.victory.sounds");
        this.victoryPrimary = cue(sounds, "victory_primary", "minecraft:ui.toast.challenge_complete", 0.75F, 1.0F);
        this.victoryAccent = cue(sounds, "victory_accent", "minecraft:entity.firework_rocket.large_blast", 0.28F, 1.0F);
        this.xpTick = cue(sounds, "xp_tick", "minecraft:entity.experience_orb.pickup", 0.35F, 0.85F);
        this.xpComplete = cue(sounds, "xp_complete", "minecraft:entity.player.levelup", 0.20F, 1.35F);
        this.coinsTick = cue(sounds, "coins_tick", "minecraft:block.note_block.pling", 0.42F, 0.90F);
        this.fragmentsTick = cue(sounds, "fragments_tick", "minecraft:block.amethyst_block.chime", 0.55F, 1.0F);
    }

    public CompletableFuture<Void> showVictory(Player player, RewardSnapshot rewards) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        cancel(player.getUniqueId(), true);
        RewardSnapshot snapshot = rewards == null ? RewardSnapshot.none() : rewards;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        RunningPresentation presentation = new RunningPresentation(player.getUniqueId(), snapshot, completion);
        presentation.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(presentation), 0L, 1L);
        running.put(player.getUniqueId(), presentation);
        return completion;
    }

    public void shutdown() {
        for (UUID playerId : running.keySet().toArray(UUID[]::new)) {
            cancel(playerId, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cancel(event.getPlayer().getUniqueId(), true);
    }

    private void tick(RunningPresentation presentation) {
        Player player = Bukkit.getPlayer(presentation.playerId);
        if (player == null || !player.isOnline()) {
            cancel(presentation.playerId, false);
            return;
        }
        int tick = presentation.tick++;
        if (tick == VICTORY_TICK) {
            player.showTitle(Title.title(victoryTitle(), Component.empty(), OVERLAY_TIMES));
            play(player, victoryPrimary, victoryPrimary.pitch);
            play(player, victoryAccent, victoryAccent.pitch);
        }
        if (tick == LABELS_TICK) {
            renderValues(player, presentation.rewards, 0, 0, 0, Phase.XP);
        }
        if (inRange(tick, XP_START_TICK, XP_END_TICK)) {
            int value = interpolate(presentation.rewards.xp, tick, XP_START_TICK, XP_END_TICK);
            renderValues(player, presentation.rewards, value, 0, 0, Phase.XP);
            playTallyTick(player, presentation, Phase.XP, tick, XP_START_TICK, XP_END_TICK, xpTick);
            if (tick == XP_END_TICK && presentation.rewards.xp > 0) play(player, xpComplete, xpComplete.pitch);
        }
        if (inRange(tick, COINS_START_TICK, COINS_END_TICK)) {
            int value = interpolate(presentation.rewards.coins, tick, COINS_START_TICK, COINS_END_TICK);
            renderValues(player, presentation.rewards, presentation.rewards.xp, value, 0, Phase.COINS);
            playTallyTick(player, presentation, Phase.COINS, tick, COINS_START_TICK, COINS_END_TICK, coinsTick);
        }
        if (inRange(tick, FRAGMENTS_START_TICK, FRAGMENTS_END_TICK)) {
            int value = interpolate(presentation.rewards.fragments, tick, FRAGMENTS_START_TICK, FRAGMENTS_END_TICK);
            renderValues(player, presentation.rewards, presentation.rewards.xp, presentation.rewards.coins, value, Phase.FRAGMENTS);
            playFragmentTick(player, presentation, tick, value);
        }
        if (tick == FRAGMENTS_END_TICK + 1) {
            renderValues(player, presentation.rewards, presentation.rewards.xp, presentation.rewards.coins,
                presentation.rewards.fragments, Phase.COMPLETE);
        }
        if (tick == WIN_BONUS_TICK && presentation.rewards.winBonusXp > 0) {
            player.sendActionBar(Component.text("WIN BONUS  ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("EXP +" + presentation.rewards.winBonusXp, NamedTextColor.YELLOW, TextDecoration.BOLD)));
            play(player, xpComplete, 1.55F);
        }
        if (tick >= COMPLETE_TICK) {
            cancel(presentation.playerId, true);
        }
    }

    private static Component victoryTitle() {
        return Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private void renderValues(Player player, RewardSnapshot rewards, int xp, int coins, int fragments, Phase active) {
        Component values = labeledValue("EXP", xp, rewards.xp, active == Phase.XP, NamedTextColor.YELLOW);
        if (active.ordinal() >= Phase.COINS.ordinal()) {
            values = values.append(Component.text("        "))
                .append(labeledValue("COINS", coins, rewards.coins, active == Phase.COINS, NamedTextColor.GOLD));
        }
        if (active.ordinal() >= Phase.FRAGMENTS.ordinal()) {
            values = values.append(Component.text("        "))
                .append(labeledValue("FRAGMENTS", fragments, rewards.fragments,
                    active == Phase.FRAGMENTS, NamedTextColor.LIGHT_PURPLE));
        }
        player.sendActionBar(values);
    }

    private static Component labeledValue(String label, int current, int target, boolean active, NamedTextColor color) {
        return Component.text(label + " ", NamedTextColor.GRAY).append(value(current, target, active, color));
    }

    private static Component value(int current, int target, boolean active, NamedTextColor color) {
        String prefix = current == target ? "+" : "";
        NamedTextColor renderedColor = active || current == target ? color : NamedTextColor.DARK_GRAY;
        Component value = Component.text(prefix + Math.max(0, current), renderedColor);
        return active ? value.decorate(TextDecoration.BOLD) : value;
    }

    private void playTallyTick(Player player, RunningPresentation presentation, Phase phase, int tick,
                               int start, int end, SoundCue cue) {
        int target = phase == Phase.XP ? presentation.rewards.xp : presentation.rewards.coins;
        if (target <= 0) return;
        int audibleTicks = Math.min(12, Math.max(8, end - start + 1));
        int step = Math.max(1, (end - start + 1) / audibleTicks);
        if ((tick - start) % step != 0 && tick != end) return;
        float progress = normalized(tick, start, end);
        play(player, cue, cue.pitch + progress * 0.45F);
    }

    private void playFragmentTick(Player player, RunningPresentation presentation, int tick, int value) {
        if (presentation.rewards.fragments <= 0 || value == presentation.lastFragmentValue) return;
        presentation.lastFragmentValue = value;
        if (value > 0) play(player, fragmentsTick, fragmentsTick.pitch + normalized(tick, FRAGMENTS_START_TICK, FRAGMENTS_END_TICK) * 0.25F);
    }

    private void play(Player player, SoundCue cue, float pitch) {
        if (cue.volume <= 0.0F || cue.key.isBlank()) return;
        player.playSound(player.getLocation(), cue.key, SoundCategory.MASTER, cue.volume, Math.max(0.5F, Math.min(2.0F, pitch)));
    }

    private void cancel(UUID playerId, boolean clearHud) {
        RunningPresentation presentation = running.remove(playerId);
        if (presentation == null) return;
        if (presentation.task != null) presentation.task.cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (clearHud && player != null && player.isOnline()) {
            player.clearTitle();
            player.sendActionBar(Component.empty());
        }
        presentation.completion.complete(null);
    }

    static int interpolate(int target, int tick, int start, int end) {
        if (target <= 0 || tick <= start) return 0;
        if (tick >= end) return Math.max(0, target);
        double progress = normalized(tick, start, end);
        double eased = 1.0D - Math.pow(1.0D - progress, 2.2D);
        return Math.min(target, Math.max(0, (int) Math.round(target * eased)));
    }

    private static float normalized(int tick, int start, int end) {
        return Math.max(0.0F, Math.min(1.0F, (tick - start) / (float) Math.max(1, end - start)));
    }

    private static boolean inRange(int tick, int start, int end) {
        return tick >= start && tick <= end;
    }

    private static SoundCue cue(ConfigurationSection section, String path, String fallbackKey, float fallbackVolume, float fallbackPitch) {
        if (section == null) return new SoundCue(fallbackKey, fallbackVolume, fallbackPitch);
        String key = section.getString(path + ".key", fallbackKey);
        float volume = (float) section.getDouble(path + ".volume", fallbackVolume);
        float pitch = (float) section.getDouble(path + ".pitch", fallbackPitch);
        return new SoundCue(key == null ? fallbackKey : key, volume, pitch);
    }

    public record RewardSnapshot(int xp, int coins, int fragments, Integer killStreak, int winBonusXp) {
        public RewardSnapshot(int xp, int coins, int fragments, Integer killStreak) {
            this(xp, coins, fragments, killStreak, 0);
        }
        public RewardSnapshot {
            xp = Math.max(0, xp);
            coins = Math.max(0, coins);
            fragments = Math.max(0, fragments);
            winBonusXp = Math.max(0, winBonusXp);
            if (killStreak != null && killStreak < 0) killStreak = null;
        }

        public static RewardSnapshot none() {
            return new RewardSnapshot(0, 0, 0, null, 0);
        }
    }

    private enum Phase { XP, COINS, FRAGMENTS, COMPLETE }

    private record SoundCue(String key, float volume, float pitch) {}

    private static final class RunningPresentation {
        private final UUID playerId;
        private final RewardSnapshot rewards;
        private final CompletableFuture<Void> completion;
        private BukkitTask task;
        private int tick;
        private int lastFragmentValue = -1;

        private RunningPresentation(UUID playerId, RewardSnapshot rewards, CompletableFuture<Void> completion) {
            this.playerId = playerId;
            this.rewards = rewards;
            this.completion = completion;
        }
    }
}
