package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SettingsMenuContractTest {
    @Test
    void pageOneHasEveryExistingToggleAndPageTwoHasTimeOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String menu = source.substring(source.indexOf("public void openPlayerSettingsMenu("),
            source.indexOf("public void openPlayerKitEditorMenu("));
        String pageAssignments = source.substring(source.indexOf("SETTINGS_PAGE_ONE_TOGGLES"),
            source.indexOf("public void openPlayerSettingsMenu("));
        assertTrue(menu.contains("\"settings_page_1\" : \"settings_page_2\""));
        assertTrue(menu.contains("GUI_MODEL_TOGGLE_ON : GUI_MODEL_TOGGLE_OFF"));
        assertTrue(menu.contains("SETTINGS_PAGE_ONE_SLOTS : SETTINGS_PAGE_TWO_SLOTS"));
        assertTrue(menu.contains("int slot = settingSlots[i]"));
        for (PlayerSettingsService.Toggle toggle : PlayerSettingsService.Toggle.values()) {
            assertEquals(1, pageAssignments.split("PlayerSettingsService.Toggle." + toggle.name() + "\\b", -1).length - 1,
                toggle.name());
        }
        assertTrue(pageAssignments.contains("SETTINGS_PAGE_ONE_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34}"));
        assertTrue(pageAssignments.contains("SETTINGS_PAGE_TWO_TOGGLES = {}"));
        assertTrue(pageAssignments.contains("SETTINGS_PAGE_TWO_SLOTS = {}"));
        assertTrue(menu.contains("Material.GRAY_CONCRETE"));
        assertTrue(menu.contains("Material.YELLOW_CONCRETE"));
        assertTrue(menu.contains("Material.ORANGE_CONCRETE"));
        assertTrue(menu.contains("Material.BLUE_CONCRETE"));
        assertTrue(menu.contains("Material.RED_CONCRETE"));
        assertTrue(menu.contains("if (selectedPage == 2)"));
        assertTrue(menu.contains("inv.setItem(22,"));
        assertFalse(menu.contains("settings_single"));
        assertFalse(menu.contains("guiBackButton("));
        assertFalse(menu.contains("PROFANITY_FILTER"));
        assertFalse(menu.contains("settings_effects_level"));
    }

    @Test
    void togglesPersistThenImmediatelyRefreshTheMenuAndRespectHydraPlusLock() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String action = source.substring(source.indexOf("if (action.startsWith(\"player_settings_toggle:\"))"),
            source.indexOf("if (action.equals(\"admin_queue_status_br\"))"));
        assertTrue(action.contains("!hydra.playerSettingsService().canUseFlightToggle(player)"));
        assertTrue(action.contains("settings.setFlightEnabled(player, !settings.flightEnabled(player))"));
        assertTrue(action.contains("settings.set(player, setting, !settings.enabled(player.getUniqueId(), setting))"));
        assertTrue(action.contains("refreshPlayerSettingsMenu(player)"));
        assertTrue(action.contains("settings.setLobbyTime(player, settings.lobbyTime(player.getUniqueId()).next())"));
    }

    @Test
    void backgroundPageArrowsRouteOnlyBetweenTheTwoPagesWithPageCues() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        assertTrue(menu.contains("theme.equalsIgnoreCase(\"settings_page_1\") && rawSlot == 51"));
        assertTrue(menu.contains("theme.equalsIgnoreCase(\"settings_page_2\") && rawSlot == 47"));
        assertTrue(menu.contains("return \"player_settings_page_2\""));
        assertTrue(menu.contains("return \"player_settings_page_1\""));
        assertTrue(menu.contains("action.equals(\"player_settings_page_1\")) return HydraSounds.UiCue.PAGE_BACK"));
        assertTrue(menu.contains("action.equals(\"player_settings_page_2\")) return HydraSounds.UiCue.PAGE_FORWARD"));
        assertTrue(menu.contains("openPlayerSettingsMenu(player, 1)"));
        assertTrue(menu.contains("openPlayerSettingsMenu(player, 2)"));
    }

    @Test
    void pageOneUsesFullFourByThreeGridWithEmptySeparators() throws Exception {
        Field pageOneField = MenuService.class.getDeclaredField("SETTINGS_PAGE_ONE_TOGGLES");
        pageOneField.setAccessible(true);
        PlayerSettingsService.Toggle[] pageOne = (PlayerSettingsService.Toggle[]) pageOneField.get(null);
        Field pageTwoField = MenuService.class.getDeclaredField("SETTINGS_PAGE_TWO_TOGGLES");
        pageTwoField.setAccessible(true);
        PlayerSettingsService.Toggle[] pageTwo = (PlayerSettingsService.Toggle[]) pageTwoField.get(null);
        assertEquals(12, pageOne.length);
        assertEquals(PlayerSettingsService.Toggle.values().length, new HashSet<>(Arrays.asList(pageOne)).size());
        assertEquals(new HashSet<>(Arrays.asList(PlayerSettingsService.Toggle.values())),
            new HashSet<>(Arrays.asList(pageOne)));
        assertEquals(0, pageTwo.length);

        Field slotsField = MenuService.class.getDeclaredField("SETTINGS_PAGE_ONE_SLOTS");
        slotsField.setAccessible(true);
        int[] slots = (int[]) slotsField.get(null);
        assertArrayEquals(new int[] {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34}, slots);
        Set<Integer> separators = Set.of(11, 13, 15, 20, 22, 24, 29, 31, 33);
        for (int slot : slots) {
            assertFalse(separators.contains(slot), "Page 1 separator occupied: " + slot);
        }
        Field pageTwoSlotsField = MenuService.class.getDeclaredField("SETTINGS_PAGE_TWO_SLOTS");
        pageTwoSlotsField.setAccessible(true);
        assertEquals(0, ((int[]) pageTwoSlotsField.get(null)).length);

        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String menu = source.substring(source.indexOf("private void openPlayerSettingsMenu("),
            source.indexOf("private void refreshPlayerSettingsMenu("));
        assertTrue(menu.contains("if (selectedPage == 2)"));
        assertTrue(menu.contains("inv.setItem(22, button(timeIcon"));
        assertFalse(menu.contains("inv.setItem(33, button(timeIcon"));
    }
}
