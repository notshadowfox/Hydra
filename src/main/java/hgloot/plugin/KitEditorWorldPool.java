package hgloot.plugin;

import java.io.IOException;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.World;

/** Main-thread Kit Editor world ownership. A slot is never available until preparation/reset succeeds. */
final class KitEditorWorldPool {
    static final String PREFIX = "Hydra_kit.";

    enum State { AVAILABLE, TAKEN, RESETTING }

    interface Preparer {
        World prepare(int number) throws IOException;
        void reset(World world) throws IOException;
    }

    static final class Slot {
        final int number;
        final World world;
        private State state;
        private UUID owner;

        Slot(int number, World world) {
            this.number = number;
            this.world = world;
            this.state = State.AVAILABLE;
        }

        String name() { return PREFIX + number; }
        State state() { return state; }
        UUID owner() { return owner; }
    }

    private final Preparer preparer;
    private final NavigableMap<Integer, Slot> slots = new TreeMap<>();
    private int nextNumber = 1;

    KitEditorWorldPool(Preparer preparer) { this.preparer = preparer; }

    synchronized void initialize(Collection<Integer> existingNumbers) throws IOException {
        for (int number : existingNumbers.stream().filter(n -> n > 0).distinct().sorted().toList()) {
            nextNumber = Math.max(nextNumber, number + 1);
            if (slots.containsKey(number)) continue;
            slots.put(number, new Slot(number, preparer.prepare(number)));
        }
    }

    synchronized Slot acquire(UUID owner) throws IOException {
        for (Slot slot : slots.values()) {
            if (slot.state == State.AVAILABLE) {
                slot.state = State.TAKEN;
                slot.owner = owner;
                return slot;
            }
        }
        int number = nextNumber;
        World world = preparer.prepare(number);
        Slot slot = new Slot(number, world);
        slot.state = State.TAKEN;
        slot.owner = owner;
        slots.put(number, slot);
        nextNumber = number + 1;
        return slot;
    }

    synchronized void release(Slot slot, UUID owner) throws IOException {
        if (slot == null || slots.get(slot.number) != slot || slot.state != State.TAKEN
            || !owner.equals(slot.owner)) throw new IllegalStateException("Kit Editor world owner mismatch");
        slot.state = State.RESETTING;
        try {
            preparer.reset(slot.world);
            slot.owner = null;
            slot.state = State.AVAILABLE;
        } catch (IOException | RuntimeException exception) {
            // Keep RESETTING; a failed reset must never leak this world to another player.
            throw exception;
        }
    }

    synchronized void invalidate(World world) {
        slots.values().removeIf(slot -> slot.world.equals(world));
    }

    synchronized Slot forWorld(World world) {
        return slots.values().stream().filter(slot -> slot.world.equals(world)).findFirst().orElse(null);
    }

    synchronized State state(int number) {
        Slot slot = slots.get(number);
        return slot == null ? null : slot.state;
    }
}
