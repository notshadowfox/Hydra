package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FlightEntryPointContractTest {
    @Test
    void commandAndSettingsUseTheSameToggleRatherThanDirectBukkitFlightState() throws Exception {
        String commands = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        String fly = commands.substring(commands.indexOf("private boolean handleFly("),
            commands.indexOf("boolean isInAnyHydraQueueOrMatch("));
        assertTrue(fly.contains("playerSettingsService.flightEnabled(player)"));
        assertTrue(fly.contains("playerSettingsService.setFlightEnabled(player, enable)"));
        assertTrue(fly.contains("hasTrialStaffOrHigher(player)"));
        assertTrue(fly.contains("player.setAllowFlight(enable)"), "Staff administrative flight stays separate");
        String hydraPlus = fly.substring(fly.indexOf("if (playerSettingsService == null"));
        assertFalse(hydraPlus.contains("player.setAllowFlight("));

        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String settings = menu.substring(menu.indexOf("public void openPlayerSettingsMenu("),
            menu.indexOf("public void openPlayerKitEditorMenu("));
        assertTrue(settings.contains("settings.flightEnabled(player)"));
        assertTrue(menu.contains("settings.setFlightEnabled(player, !settings.flightEnabled(player))"));
    }
}
