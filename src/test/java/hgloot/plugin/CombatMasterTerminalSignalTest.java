package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class CombatMasterTerminalSignalTest {
    @Test
    void deadBossReportsDefeatedExactlyOnceEvenIfItsEntityIsAlreadyInvalid() throws Exception {
        Harness harness = harness(true);
        assertFalse(harness.service.maintainCombatMaster(harness.id));
        assertEquals(CombatMasterService.Completion.DEFEATED, harness.result.get());
        assertEquals(1, harness.signals.get());
        assertFalse(harness.service.maintainCombatMaster(harness.id));
        assertEquals(1, harness.signals.get());
        assertTrue(harness.bosses.isEmpty());
    }

    @Test
    void removedButNotDeadBossReportsCanceledExactlyOnce() throws Exception {
        Harness harness = harness(false);
        assertFalse(harness.service.maintainCombatMaster(harness.id));
        assertEquals(CombatMasterService.Completion.CANCELED, harness.result.get());
        assertEquals(1, harness.signals.get());
        assertTrue(harness.bosses.isEmpty());
    }

    private static Harness harness(boolean dead) throws Exception {
        CombatMasterService service = new CombatMasterService(mock(JavaPlugin.class));
        WitherSkeleton boss = mock(WitherSkeleton.class);
        UUID id = UUID.randomUUID();
        when(boss.getUniqueId()).thenReturn(id);
        when(boss.isDead()).thenReturn(dead);
        when(boss.isValid()).thenReturn(false);
        Class<?> activeType = Class.forName("hgloot.plugin.CombatMasterService$ActiveBoss");
        Constructor<?> constructor = activeType.getDeclaredConstructor(WitherSkeleton.class, BossBar.class, Location.class);
        constructor.setAccessible(true);
        Object active = constructor.newInstance(boss, mock(BossBar.class), new Location(null, 0, 0, 0));
        AtomicReference<CombatMasterService.Completion> result = new AtomicReference<>();
        AtomicInteger signals = new AtomicInteger();
        Field completion = activeType.getDeclaredField("completion");
        completion.setAccessible(true);
        completion.set(active, (Consumer<CombatMasterService.Completion>) outcome -> {
            result.set(outcome);
            signals.incrementAndGet();
        });
        Field bossesField = CombatMasterService.class.getDeclaredField("bosses");
        bossesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> bosses = (Map<UUID, Object>) bossesField.get(service);
        bosses.put(id, active);
        return new Harness(service, id, bosses, result, signals);
    }

    private record Harness(CombatMasterService service, UUID id, Map<UUID, Object> bosses,
                           AtomicReference<CombatMasterService.Completion> result, AtomicInteger signals) {}
}
