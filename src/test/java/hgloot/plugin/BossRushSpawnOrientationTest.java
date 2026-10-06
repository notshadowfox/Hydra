package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class BossRushSpawnOrientationTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void everyOccupiedTeamInAllFourLayoutsFacesItsOwnBossWithoutMovingOrTilting() throws Exception {
        World world = MockBukkit.mock().addSimpleWorld("BossRush-orientation");
        Method centerMethod = BossRushService.class.getDeclaredMethod("arenaCenter", RouletteTeam.class);
        centerMethod.setAccessible(true);
        RouletteTeam[] order = {RouletteTeam.RED, RouletteTeam.BLUE, RouletteTeam.GREEN, RouletteTeam.YELLOW};
        for (int occupiedTeams = 1; occupiedTeams <= 4; occupiedTeams++) {
            for (int index = 0; index < occupiedTeams; index++) {
                int[] center = (int[]) centerMethod.invoke(null, order[index]);
                Location assigned = new Location(world, center[0] - 17.5D, 90.0D,
                    center[1] + 9.5D, 123.0F, 4.0F);
                Location boss = new Location(world, center[0] + 2.5D, 95.0D, center[1] - 3.5D);
                Location faced = BossRushService.faceBoss(assigned, boss);
                assertEquals(assigned.getX(), faced.getX());
                assertEquals(assigned.getY(), faced.getY());
                assertEquals(assigned.getZ(), faced.getZ());
                assertEquals(assigned.getPitch(), faced.getPitch());
                assertEquals(123.0F, assigned.getYaw(), "the configured spawn marker is not mutated");
                Vector expected = boss.toVector().subtract(faced.toVector()).setY(0).normalize();
                Vector actual = faced.getDirection().setY(0).normalize();
                assertTrue(actual.dot(expected) > 0.999D, order[index] + " faces its own boss");
            }
        }
    }
}
