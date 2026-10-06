package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.util.HydraSounds;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MenuUiFeedbackTest {
    @Test
    void cosmeticsNavigationHasDistinctQuietSemanticCues() {
        assertEquals(HydraSounds.UiCue.PAGE_BACK,
            MenuService.uiCueForAction("player_cosmetics_page:hats:0", 45));
        assertEquals(HydraSounds.UiCue.PAGE_FORWARD,
            MenuService.uiCueForAction("player_cosmetics_page:hats:2", 53));
        assertEquals(HydraSounds.UiCue.PAGE_BACK,
            MenuService.uiCueForAction("player_cosmetics_favorites_page:0", 45));
        assertEquals(HydraSounds.UiCue.CATEGORY,
            MenuService.uiCueForAction("player_cosmetics_category:hats", 10));
        assertEquals(HydraSounds.UiCue.SELECT,
            MenuService.uiCueForAction("player_cosmetic_head_select:none@hats", 20));
        assertNull(MenuService.uiCueForAction("noop", 20));
    }

    @Test
    void settingsAndBackActionsUseTheSamePlayerLocalVocabulary() {
        assertEquals(HydraSounds.UiCue.PAGE_BACK,
            MenuService.uiCueForAction("back_player_menu", 45));
        assertEquals(HydraSounds.UiCue.SELECT,
            MenuService.uiCueForAction("player_settings_lobby_time", 24));
        assertEquals(HydraSounds.UiCue.OPEN,
            MenuService.uiCueForAction("open_player_settings", 11));
        assertTrue(HydraSounds.UiCue.PAGE_BACK.volume < 0.5F);
    }

    @Test
    void socialCategoriesAndPagesReuseTheExistingNavigationVocabulary() {
        assertEquals(HydraSounds.UiCue.CATEGORY, MenuService.uiCueForAction("friends_page:all:0", 10));
        assertEquals(HydraSounds.UiCue.CATEGORY, MenuService.uiCueForAction("friends_page:requests:0", 11));
        assertEquals(HydraSounds.UiCue.CATEGORY, MenuService.uiCueForAction("friends_page:blocked:0", 12));
        assertEquals(HydraSounds.UiCue.PAGE_BACK, MenuService.uiCueForAction("friends_page:all:0", 18));
        assertEquals(HydraSounds.UiCue.PAGE_FORWARD, MenuService.uiCueForAction("friends_page:all:1", 26));
        assertEquals(HydraSounds.UiCue.CATEGORY, MenuService.uiCueForAction("open_player_friends_menu", 0));
        assertEquals(HydraSounds.UiCue.CATEGORY, MenuService.uiCueForAction("open_player_party_invite_friends", 0));
        assertEquals(HydraSounds.UiCue.CATEGORY, MenuService.uiCueForAction("open_player_party_menu", 0));
        assertEquals(HydraSounds.UiCue.PAGE_BACK, MenuService.uiCueForAction("open_player_party_menu", 22));
    }

    @Test
    void successfulQueueExitPathsAndMenuClicksAreWired() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        String leave = queue.substring(queue.indexOf("public void leaveQueueOrMatch(Player player)"),
            queue.indexOf("private void", queue.indexOf("public void leaveQueueOrMatch(Player player)")));
        assertEquals(3, leave.split("HydraSounds.playQueueLeave\\(", -1).length - 1);

        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        String click = menu.substring(menu.indexOf("public void handleClick(Player player, InventoryClickEvent event)"),
            menu.indexOf("private String themedEmptySlotAction", menu.indexOf("public void handleClick(Player player, InventoryClickEvent event)")));
        assertTrue(click.contains("playMenuCue(player, regionAction, event.getRawSlot())"));
        assertTrue(click.contains("playMenuCue(player, action, event.getRawSlot())"));
        assertTrue(click.contains("UiCue.FAVORITE_ON : HydraSounds.UiCue.FAVORITE_OFF"));
        assertTrue(click.contains("UiCue.TOGGLE_OFF : HydraSounds.UiCue.TOGGLE_ON"));
    }

    @Test
    void rouletteQueueCuesFollowSuccessfulMembershipChangesOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));
        String soloJoin = source.substring(source.indexOf("private void joinQueue(Player player, boolean includeParty)"),
            source.indexOf("private void joinPartyQueue(", source.indexOf("private void joinQueue(Player player, boolean includeParty)")));
        String partyJoin = source.substring(source.indexOf("private void joinPartyQueue("),
            source.indexOf("public boolean leaveQueue(", source.indexOf("private void joinPartyQueue(")));
        String leave = source.substring(source.indexOf("public boolean leaveQueue("),
            source.indexOf("private ", source.indexOf("public boolean leaveQueue(")));
        assertTrue(soloJoin.indexOf("HydraSounds.playQueueJoin(player)") > soloJoin.indexOf("queuedPlayers.add(playerId)"));
        assertTrue(partyJoin.contains("if (newlyQueued.contains(memberId)) HydraSounds.playQueueJoin(member)"));
        assertTrue(leave.indexOf("HydraSounds.playQueueLeave(player)") > leave.indexOf("queuedPlayers.remove(player.getUniqueId())"));
        assertTrue(leave.contains("HydraSounds.playError(player)"));
    }

    @Test
    void kitEditorPhysicalControlsOnlyAcknowledgeTheirOwnActions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/KitEditorService.java"));
        String confirm = source.substring(source.indexOf("public void confirm(Player player)"),
            source.indexOf("public void cancel(Player player)"));
        String cancel = source.substring(source.indexOf("public void cancel(Player player)"),
            source.indexOf("private void endWithTransition(Player player, boolean confirmed)"));
        String reset = source.substring(source.indexOf("public void reset(Player player)"),
            source.indexOf("boolean saveLayout(", source.indexOf("public void reset(Player player)")));
        assertTrue(confirm.indexOf("UiCue.CONFIRM") > confirm.indexOf("if (!saveLayout("));
        assertTrue(confirm.contains("UiCue.UNAVAILABLE"));
        assertTrue(cancel.contains("UiCue.CANCEL"));
        assertTrue(reset.contains("UiCue.RESET"));
    }
}
