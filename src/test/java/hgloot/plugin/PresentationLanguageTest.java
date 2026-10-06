package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.util.HydraIcons;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

final class PresentationLanguageTest {
    @Test
    void genericNoticeVariantsUseOneAquaTextMarker() {
        assertEquals("[!]", ChatColor.stripColor(HydraIcons.EXCLAMATION));
        assertEquals(HydraIcons.EXCLAMATION, HydraIcons.EXCLAMATION_ORANGE);
        assertEquals(HydraIcons.EXCLAMATION, HydraIcons.EXCLAMATION_RED);
        assertEquals("[!]", HydraIcons.EXCLAMATION_CHAR);
        assertEquals(NamedTextColor.AQUA,
            ((TextComponent) HydraIcons.component(HydraIcons.EXCLAMATION_CHAR)).color());
        assertEquals(NamedTextColor.WHITE,
            ((TextComponent) HydraIcons.component(HydraIcons.LOCKED_CHAR)).color());
    }

    @Test
    void sharedMessageColorsFollowTheirMeaning() {
        assertEquals(HydraMessages.INFO_AQUA,
            ((TextComponent) HydraMessages.info("Info")).color());
        assertEquals(HydraMessages.SUCCESS_GREEN,
            ((TextComponent) HydraMessages.success("Done")).color());
        assertEquals(HydraMessages.ERROR_RED,
            ((TextComponent) HydraMessages.error("Denied")).color());
        assertEquals("[!]", HydraIcons.EXCLAMATION_CHAR);
    }

    @Test
    void navigatorUsesSpecificModeNamesAndGrayBodyCopy() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        int start = menu.indexOf("public void openPlayerMenu(Player player)");
        int end = menu.indexOf("public void openPlayerMinigamesMenu(Player player)", start);
        String navigator = menu.substring(start, end);

        assertTrue(menu.contains("SECONDARY_TEXT_COLOR = ChatColor.GRAY"));
        assertTrue(navigator.contains("ChatColor.AQUA + \"BATTLE ROYALE\""));
        assertTrue(navigator.contains("ChatColor.RED + \"Currently Unavailable\""));
        assertTrue(navigator.contains("ChatColor.AQUA + \"HUNGER GAMES SOLOS\""));
        assertTrue(navigator.contains("ChatColor.AQUA + \"HUNGER GAMES DUOS\""));
        assertTrue(navigator.contains("ChatColor.AQUA + \"MINIGAME ROULETTE\""));
        assertTrue(navigator.contains("Scavenge, craft, and outlast every opponent."));
        assertTrue(navigator.contains("Scavenge and fight together until one duo remains."));
        assertTrue(navigator.contains("playing now"));
        assertFalse(navigator.contains("Queue by yourself."));
        assertFalse(navigator.contains("currently playing!"));
        assertFalse(navigator.contains("Click to Join Roulette"));
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        assertTrue(queue.contains("\"Hunger Games Duos\""));
        assertTrue(queue.contains("\"Hunger Games Solos\""));
    }

    @Test
    void normalModeMessagesAvoidOldPrefixesAndInternalWording() throws Exception {
        String bridge = Files.readString(Path.of("src/main/java/hgloot/plugin/BridgeService.java"));
        String rush = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        String intermission = Files.readString(Path.of("src/main/java/hgloot/plugin/RouletteIntermissionDialog.java"));
        String podium = Files.readString(Path.of("src/main/java/hgloot/plugin/RoulettePodiumDialog.java"));

        assertTrue(bridge.contains("private static final String PREFIX = HydraIcons.EXCLAMATION"));
        assertFalse(bridge.contains("ChatColor.AQUA + \"[Bridge] \""));
        assertTrue(rush.contains("Deal the most damage to Combat Master."));
        assertFalse(rush.contains("only boss in this prototype"));
        assertTrue(intermission.contains("The next match starts automatically."));
        assertFalse(intermission.contains("Teams stay locked."));
        assertFalse(podium.contains("match locks have been cleared"));
    }
}
