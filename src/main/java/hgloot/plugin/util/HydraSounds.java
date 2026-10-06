package hgloot.plugin.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class HydraSounds {

    /** Quiet, player-local interface vocabulary. Frequent cues stay below gameplay sounds. */
    public enum UiCue {
        OPEN(Sound.UI_BUTTON_CLICK, 0.28F, 1.00F),
        CATEGORY(Sound.UI_BUTTON_CLICK, 0.32F, 1.32F),
        PAGE_FORWARD(Sound.ITEM_BOOK_PAGE_TURN, 0.34F, 1.20F),
        PAGE_BACK(Sound.ITEM_BOOK_PAGE_TURN, 0.34F, 0.88F),
        SELECT(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.36F, 1.50F),
        FAVORITE_ON(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.30F, 1.55F),
        FAVORITE_OFF(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.26F, 0.85F),
        TOGGLE_ON(Sound.BLOCK_NOTE_BLOCK_PLING, 0.34F, 1.35F),
        TOGGLE_OFF(Sound.BLOCK_NOTE_BLOCK_BASS, 0.32F, 1.10F),
        CONFIRM(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.46F, 1.10F),
        RESET(Sound.ITEM_BOOK_PAGE_TURN, 0.40F, 0.82F),
        CANCEL(Sound.UI_BUTTON_CLICK, 0.38F, 0.70F),
        RECIPE_OPEN(Sound.ITEM_BOOK_PAGE_TURN, 0.58F, 0.72F),
        RECIPE_SELECT(Sound.ITEM_BOOK_PAGE_TURN, 0.46F, 1.04F),
        UNAVAILABLE(Sound.BLOCK_NOTE_BLOCK_BASS, 0.32F, 0.72F);

        public final Sound sound;
        public final float volume;
        public final float pitch;

        UiCue(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static final long UI_MIN_INTERVAL_NANOS = 70_000_000L;
    private static final Map<UUID, Long> lastUiCueNanos = new HashMap<>();

    private HydraSounds() {
    }

    /** Play only to the interacting player; never broadcast a menu click into an arena. */
    public static void playUi(Player player, UiCue cue) {
        if (player == null || !player.isOnline() || cue == null) return;
        long now = System.nanoTime();
        if (!allowUiCue(player.getUniqueId(), now)) return;
        player.playSound(player.getLocation(), cue.sound, SoundCategory.MASTER, cue.volume, cue.pitch);
    }

    static boolean allowUiCue(UUID playerId, long now) {
        Long last = lastUiCueNanos.get(playerId);
        if (last != null && now - last >= 0L && now - last < UI_MIN_INTERVAL_NANOS) return false;
        lastUiCueNanos.put(playerId, now);
        return true;
    }

    public static void forgetUi(Player player) {
        if (player != null) lastUiCueNanos.remove(player.getUniqueId());
    }

    /** Queue membership changed: stronger than a menu click, still self-only. */
    public static void playQueueJoin(Player player) {
        play(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85F, 1.2F);
    }

    /** A low, audible confirmation of a successful exit, distinct from an error. */
    public static void playQueueLeave(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_HIT,
                SoundCategory.MASTER, 0.75F, 0.85F);
        }
    }

    public static void playClick(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.7F, 1.15F);
    }

    public static void playSuccess(Player player) {
        play(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85F, 1.2F);
    }

    public static void playError(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.75F);
    }

    public static void playCountdownTick(Player player, int secondsRemaining) {
        int clamped = Math.max(1, Math.min(10, secondsRemaining));
        float pitch = 0.9F + ((10 - clamped) * 0.055F);
        play(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.95F, pitch);
    }

    public static void playGo(Player player) {
        play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.05F, 1.2F);
    }

    /** A quiet between-round reveal, distinct from a final victory cue. */
    public static void playRoundResult(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.48F, 1.15F);
    }

    /** A scored-on round is a setback, not a final match defeat. */
    public static void playRoundLoss(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.58F, 0.92F);
    }

    public static void playGameWin(Player player) {
        play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
    }

    public static void playGameLoss(Player player) {
        play(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.68F, 0.88F);
    }

    public static void playSpectatorEnter(Player player) {
        play(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.62F, 1.12F);
    }

    public static void playKill(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.95F, 1.15F);
    }

    public static void playJoin(Player player) {
        play(player, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.55F, 1.25F);
    }

    public static void playBorderWarning(Player player) {
        play(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.9F, 0.8F);
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null || !player.isOnline()) {
            return;
        }
        Location location = player.getLocation();
        if (location == null) {
            return;
        }
        player.playSound(location, sound, volume, pitch);
    }
}
