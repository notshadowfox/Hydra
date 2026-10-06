package hgloot.plugin;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.WitherSkeleton;

/** Optional Java 21 boundary; the Java 25 renderer lives in a separate plugin. */
public interface CombatMasterVisualBridge {
    boolean attach(WitherSkeleton boss);
    boolean attack(UUID bossId);
    /** Returns the death visual's lifetime in ticks, or zero if it could not start. */
    int death(WitherSkeleton boss, Location at);
    void detach(UUID bossId);
}
