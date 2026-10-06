package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.junit.jupiter.api.Test;

class ProjectileTrailScopeTest {
    @Test
    void fishingHooksCannotCarryRepeatingCombatCosmeticParticles() {
        assertFalse(MenuService.isTrailEligibleProjectile(mock(FishHook.class)));
        assertFalse(MenuService.isTrailEligibleProjectile(null));
        assertTrue(MenuService.isTrailEligibleProjectile(mock(Arrow.class)));
        assertTrue(MenuService.isTrailEligibleProjectile(mock(Trident.class)));
        assertTrue(MenuService.isTrailEligibleProjectile(mock(Snowball.class)));
        assertTrue(MenuService.isTrailEligibleProjectile(mock(Egg.class)));
    }
}
