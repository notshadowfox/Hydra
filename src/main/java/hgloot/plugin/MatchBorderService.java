package hgloot.plugin;

import hgloot.plugin.util.HydraSounds;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

//keeps match border timing and shrink behavior together
final class MatchBorderService {
    private static final double MAX_HG_EDGE_SPEED = 4.5D;
    private final Map<String, Long> nextShrinkAtMs = new HashMap<>();
    private final Map<String, Integer> hgPhaseByWorld = new HashMap<>();

    record Settings(int intervalSeconds, double minSize, double step, int durationSeconds, double initialSize) {}

    void schedule(String worldName, long nextAtMs) {
        nextShrinkAtMs.put(worldName, nextAtMs);
    }

    Long scheduledAt(String worldName) {
        return nextShrinkAtMs.get(worldName);
    }

    void clear(String worldName) {
        nextShrinkAtMs.remove(worldName);
        hgPhaseByWorld.remove(worldName);
    }

    void clearAll() {
        nextShrinkAtMs.clear();
        hgPhaseByWorld.clear();
    }

    int timerRemaining(String worldName, int intervalSeconds, long now) {
        long next = nextShrinkAtMs.getOrDefault(worldName, now + Math.max(1, intervalSeconds) * 1000L);
        return (int) Math.max(0L, (next - now + 999L) / 1000L);
    }

    int adjustTimer(String worldName, int intervalSeconds, int deltaSeconds, long now) {
        long next = nextShrinkAtMs.getOrDefault(worldName, now + Math.max(1, intervalSeconds) * 1000L);
        long adjusted = Math.max(now + 1000L, next + deltaSeconds * 1000L);
        nextShrinkAtMs.put(worldName, adjusted);
        return (int) Math.max(0L, (adjusted - now + 999L) / 1000L);
    }

    Integer tabCountdown(World world, String worldName, Settings settings, long now) {
        Long next = nextShrinkAtMs.get(worldName);
        if (world == null || next == null || next <= now || settings.step() <= 0.0D
            || world.getWorldBorder().getSize() <= settings.minSize() + 0.01D) return null;
        return (int) Math.max(1L, (next - now + 999L) / 1000L);
    }

    int sidebarNext(String worldName, Settings settings, long now) {
        if (settings.step() <= 0.0D) return 0;
        return timerRemaining(worldName, settings.intervalSeconds(), now);
    }

    void applyInitial(World world, double centerX, double centerZ, double size) {
        world.getWorldBorder().setCenter(centerX, centerZ);
        world.getWorldBorder().setSize(size);
    }

    void tick(World world, Supplier<World> linkedNether, boolean hungerGames, Long startedAt, Settings settings, long now) {
        if (hungerGames) {
            tickHungerGames(world, startedAt, settings, now);
        } else {
            tickGeneric(world, linkedNether, settings, now);
        }
    }

    private void tickGeneric(World world, Supplier<World> linkedNether, Settings settings, long now) {
        String worldName = world.getName();
        long intervalMs = settings.intervalSeconds() * 1000L;
        if (intervalMs <= 0L || settings.step() <= 0.0D) return;
        long nextAt = nextShrinkAtMs.getOrDefault(worldName, now + intervalMs);
        if (now < nextAt) return;
        double currentSize = world.getWorldBorder().getSize();
        if (currentSize <= settings.minSize() + 0.01D) {
            nextShrinkAtMs.put(worldName, now + intervalMs);
            return;
        }
        double target = Math.max(settings.minSize(), currentSize - settings.step());
        if (settings.durationSeconds() > 0) {
            world.getWorldBorder().changeSize(target, settings.durationSeconds());
        } else {
            world.getWorldBorder().setSize(target);
        }
        syncLinkedNether(linkedNether.get(), target, settings.durationSeconds());
        nextShrinkAtMs.put(worldName, now + intervalMs);
        for (Player player : world.getPlayers()) {
            player.sendTitle("", ChatColor.RED + "ZONE SHRINKING", 0, 25, 8);
            HydraSounds.playBorderWarning(player);
        }
    }

    private void tickHungerGames(World world, Long startedAt, Settings settings, long now) {
        if (startedAt == null) return;
        String worldName = world.getName();
        long elapsed = Math.max(0L, (now - startedAt) / 1000L);
        int phase;
        double ratio;
        if (elapsed >= 570L) { phase = 4; ratio = 0.135D; }
        else if (elapsed >= 490L) { phase = 3; ratio = 0.25D; }
        else if (elapsed >= 410L) { phase = 2; ratio = 0.45D; }
        else if (elapsed >= 330L) { phase = 1; ratio = 0.70D; }
        else return;
        if (hgPhaseByWorld.getOrDefault(worldName, 0) >= phase) return;
        hgPhaseByWorld.put(worldName, phase);
        double target = Math.max(settings.minSize(), settings.initialSize() * ratio);
        int seconds = hungerGamesShrinkSeconds(world.getWorldBorder().getSize(), target);
        world.getWorldBorder().changeSize(target, seconds * 20L);
        for (Player player : world.getPlayers()) {
            player.sendActionBar(Component.text("Border closing slowly toward the center.", NamedTextColor.RED));
            HydraSounds.playBorderWarning(player);
        }
    }

    void syncLinkedNether(World linkedWorld, double targetSize, int durationSeconds) {
        if (linkedWorld == null) return;
        linkedWorld.getWorldBorder().setCenter(0.0D, 0.0D);
        if (durationSeconds > 0) linkedWorld.getWorldBorder().changeSize(targetSize, durationSeconds);
        else linkedWorld.getWorldBorder().setSize(targetSize);
    }

    String hungerGamesSidebar(String worldName, Long startedAt, long now) {
        return hungerGamesStatus(startedAt, hgPhaseByWorld.getOrDefault(worldName, 0), now);
    }

    static int hungerGamesShrinkSeconds(double currentDiameter, double targetDiameter) {
        return Math.max(60, (int) Math.ceil(Math.max(0.0D, currentDiameter - targetDiameter)
            / (2.0D * MAX_HG_EDGE_SPEED)));
    }

    static String hungerGamesStatus(Long startedAt, int phase, long now) {
        if (startedAt == null) return null;
        long elapsed = Math.max(0L, (now - startedAt) / 1000L);
        long[] starts = {330L, 410L, 490L, 570L};
        if (phase > 0 && phase <= starts.length && elapsed < starts[phase - 1] + 60L) return "Shrinking";
        if (phase >= starts.length) return "Final";
        return formatClock((int) Math.max(0L, starts[Math.max(0, phase)] - elapsed));
    }

    private static String formatClock(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60);
    }
}
