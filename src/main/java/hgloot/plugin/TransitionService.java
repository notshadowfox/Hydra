package hgloot.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Short, player-local presentation around an existing synchronous state change. */
public final class TransitionService implements Listener {
    public enum Preset {
        NORMAL(8, 3, 10);

        final int fadeOutTicks;
        final int blackHoldTicks;
        final int fadeInTicks;

        Preset(int fadeOutTicks, int blackHoldTicks, int fadeInTicks) {
            this.fadeOutTicks = fadeOutTicks;
            this.blackHoldTicks = blackHoldTicks;
            this.fadeInTicks = fadeInTicks;
        }
    }

    private static final Key FONT = Key.key("hydra:transition");
    private static final int FRAMES = 8;
    private static final int MAX_DESTINATION_WAIT_TICKS = 40;
    private static final int DESTINATION_SETTLE_TICKS = 3;
    private final JavaPlugin plugin;
    private final Predicate<Player> destinationReadiness;
    private final Map<UUID, Active> active = new HashMap<>();

    public TransitionService(JavaPlugin plugin) {
        this(plugin, TransitionService::destinationReady);
    }

    TransitionService(JavaPlugin plugin, Predicate<Player> destinationReadiness) {
        this.plugin = plugin;
        this.destinationReadiness = destinationReadiness;
    }

    /** Returns false if this player is already fading. Overlapping requests are rejected. */
    public boolean transition(Player player, Preset preset, Runnable midpoint) {
        if (!player.isOnline() || active.containsKey(player.getUniqueId())) return false;
        Active state = new Active(player, preset, midpoint);
        active.put(player.getUniqueId(), state);
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(state), 0L, 1L);
        return true;
    }

    /** Keep a match world loaded until every connected player has reached its exit midpoint. */
    public void transitionGroup(Collection<Player> players, Consumer<Player> midpoint, Runnable afterMidpoints) {
        ArrayList<Player> online = new ArrayList<>();
        for (Player player : players) if (player != null && player.isOnline()) online.add(player);
        if (online.isEmpty()) {
            afterMidpoints.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(online.size());
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finishGroup = () -> {
            if (completed.compareAndSet(false, true)) afterMidpoints.run();
        };
        for (Player player : online) {
            Runnable action = () -> {
                try {
                    if (player.isOnline()) midpoint.accept(player);
                } finally {
                    if (remaining.decrementAndGet() == 0) finishGroup.run();
                }
            };
            if (!transition(player, Preset.NORMAL, action)) action.run();
        }
        // A quit cancels that player's midpoint; it must not retain the arena indefinitely.
        Bukkit.getScheduler().runTaskLater(plugin, finishGroup, 20L);
    }

    public boolean isTransitioning(UUID playerId) {
        return active.containsKey(playerId);
    }

    private void tick(Active state) {
        if (active.get(state.player.getUniqueId()) != state || !state.player.isOnline()) {
            finish(state);
            return;
        }
        int tick = state.tick++;
        if (tick < state.preset.fadeOutTicks) {
            frame(state.player, Math.min(FRAMES, (tick + 1) * FRAMES / state.preset.fadeOutTicks));
            return;
        }
        if (!state.midpointRan) {
            state.midpointRan = true;
            frame(state.player, FRAMES);
            try {
                state.midpoint.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Screen-transition midpoint failed for " + state.player.getUniqueId(), exception);
            }
            return;
        }
        int holdEnd = state.preset.fadeOutTicks + state.preset.blackHoldTicks;
        // A loaded server chunk is not necessarily sent to the client yet.
        // Let the destination remain ready for a few ticks before revealing it.
        state.readyTicks = destinationReadiness.test(state.player) ? state.readyTicks + 1 : 0;
        if (tick < holdEnd || (state.readyTicks < DESTINATION_SETTLE_TICKS
            && state.waitTicks < MAX_DESTINATION_WAIT_TICKS)) {
            if (tick >= holdEnd) state.waitTicks++;
            frame(state.player, FRAMES);
            return;
        }
        int inTick = tick - holdEnd - state.waitTicks;
        if (inTick < state.preset.fadeInTicks) {
            frame(state.player, Math.max(1, FRAMES - (inTick + 1) * FRAMES / state.preset.fadeInTicks));
            return;
        }
        finish(state);
    }

    static boolean destinationReady(Player player) {
        var location = player.getLocation();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return location.getWorld().isChunkLoaded(chunkX, chunkZ)
            && player.isChunkSent(Chunk.getChunkKey(chunkX, chunkZ));
    }

    static TextComponent frameComponent(int level) {
        // One wide bitmap glyph spans the view. Repeating narrower glyphs
        // leaves a font-character join at the exact center of the screen.
        char glyph = (char) ('\ue300' + Math.max(1, Math.min(FRAMES, level)) - 1);
        return Component.text(String.valueOf(glyph), NamedTextColor.WHITE).font(FONT);
    }

    private static void frame(Player player, int level) {
        player.sendActionBar(frameComponent(level));
    }

    private void finish(Active state) {
        if (active.remove(state.player.getUniqueId(), state)) {
            if (state.task != null) state.task.cancel();
            if (state.player.isOnline()) state.player.sendActionBar(Component.empty());
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Active state = active.get(event.getPlayer().getUniqueId());
        if (state != null) finish(state);
    }

    public void shutdown() {
        for (Active state : java.util.List.copyOf(active.values())) finish(state);
    }

    private static final class Active {
        final Player player;
        final Preset preset;
        final Runnable midpoint;
        BukkitTask task;
        int tick;
        int waitTicks;
        int readyTicks;
        boolean midpointRan;

        Active(Player player, Preset preset, Runnable midpoint) {
            this.player = player;
            this.preset = preset;
            this.midpoint = midpoint;
        }
    }
}
