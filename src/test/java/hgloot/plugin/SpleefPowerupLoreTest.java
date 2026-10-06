package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpleefPowerupLoreTest {
    @Test
    void livePowerupFactoryRendersAuthoritativeDescriptionAndAbilityLore() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));

        assertTrue(source.contains("ChatColor.AQUA + powerup.category()"));
        assertTrue(source.contains("ChatColor.GRAY + powerup.description()"));
        assertTrue(source.contains("ChatColor.GRAY + powerup.ability()"));
        assertTrue(source.contains("inventory.setItem(20, createTutorialItem(Powerup.MEGA_SNOWBALL))"));
        assertTrue(source.contains("inventory.setItem(32, createTutorialItem(Powerup.LEVITATION_ORB))"));
        assertTrue(source.contains("inventory.setItem(34, createTutorialItem(Powerup.BOOMERANG))"));
        assertTrue(source.contains("inventory.setItem(40, createTutorialItem(Powerup.STOP_WATCH))"));
        assertTrue(source.contains("inventory.setItem(42, createTutorialItem(Powerup.GALE_MACE))"));
        assertFalse(source.contains("createTutorialItem(Powerup powerup, String description, String ability)"));
    }

    @Test
    void everyPowerupDefinitionCarriesPlayerFacingLore() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));

        assertTrue(source.contains("A snowball with absolutely no respect for property."));
        assertTrue(source.contains("Your cowboy impression finally has a purpose."));
        assertTrue(source.contains("A feather blessed by the divine. Gravity disagrees."));
        assertTrue(source.contains("Now you see me. Now you really shouldn't."));
        assertTrue(source.contains("Proving that gravity is merely a suggestion."));
        assertTrue(source.contains("What goes around comes around. So does the floor."));
        assertTrue(source.contains("Defensive \\u00B7 Single Use"));
        assertTrue(source.contains("Time waits for no one. Except you, apparently."));
        assertTrue(source.contains("Freeze yourself in place for 5 seconds."));
        assertTrue(source.contains("Gravity has favorites. You're not one of them."));
        assertTrue(source.contains("Hit a player to launch yourself. Deals no damage."));
    }
}
