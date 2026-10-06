package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class QueueAccessNavigatorContractTest {
    private static final Path PLUGIN_YML = Path.of("src/main/resources/plugin.yml");
    private static final Path MENU_SERVICE = Path.of("src/main/java/hgloot/plugin/MenuService.java");

    @Test
    void standalonePermissionsAreNotGrantedToNormalOrTrialStaffPlayers() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(PLUGIN_YML.toFile());

        for (String permission : List.of("hgloot.bridge", "hgloot.spleef", "hgloot.voidroulette")) {
            assertEquals("false", yaml.getString("permissions." + permission + ".default"), permission);
        }
        assertNull(yaml.get("permissions.rank.staff.trial_staff.children.hgloot.spleef"));

        for (String command : List.of("bridge", "spleef", "voidroulette", "maceroulette")) {
            assertEquals("hgloot.admin", yaml.getString("commands." + command + ".permission"), command);
        }
        assertNull(yaml.get("commands.roulette.permission"));
    }

    @Test
    void publicNavigatorHasNoStandaloneQueueHitboxes() throws Exception {
        String menu = Files.readString(MENU_SERVICE).replace("\r\n", "\n");
        String minigamesRegion = section(menu,
            "if (theme.equalsIgnoreCase(\"navigator_minigames\"))",
            "if (theme.equalsIgnoreCase(\"socials\"))");
        String adminMinigamesMenu = section(menu,
            "public void openPlayerMinigameRouletteMenu(Player player)",
            "public void openPlayerLobbyCosmeticsMenu(Player player)");

        assertFalse(minigamesRegion.contains("player_spleef_join"));
        assertFalse(minigamesRegion.contains("player_voidroulette_join"));
        assertFalse(minigamesRegion.contains("player_bridge_join"));
        assertTrue(adminMinigamesMenu.contains("if (!player.hasPermission(\"hgloot.admin\"))"));
        assertTrue(adminMinigamesMenu.contains("player_minigame_roulette_join"));
        assertTrue(adminMinigamesMenu.contains("player_spleef_join"));
        assertTrue(adminMinigamesMenu.contains("player_voidroulette_join"));

        assertTrue(menu.contains("if (action.equals(\"player_spleef_join\")) {\n"
            + "            if (!player.hasPermission(\"hgloot.admin\"))"));
        assertTrue(menu.contains("if (action.equals(\"player_voidroulette_join\")) {\n"
            + "            if (!player.hasPermission(\"hgloot.admin\"))"));
        assertFalse(menu.contains("if (action.equals(\"player_bridge_join\"))"));
    }

    @Test
    void navigatorDocumentsCurrentHgDuosContract() throws Exception {
        String menu = Files.readString(MENU_SERVICE);

        assertTrue(menu.contains("Scavenge and fight together until one duo remains."));
        assertTrue(menu.contains("Bring a partner or find one when you queue."));
        assertFalse(menu.contains("Queue with an exactly two-player party."));
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, "missing start marker: " + startMarker);
        assertTrue(end > start, "missing end marker: " + endMarker);
        return source.substring(start, end);
    }
}
