package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class UtilityBookNavigationContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/" + name + ".java"));
    }

    @Test
    void hubAndGameHubHaveDistinctIntentionalHotbars() throws Exception {
        String menu = source("MenuService");
        int gameHubStart = menu.indexOf("if (gameHub) {", menu.indexOf("private boolean ensurePlayerHubHotbar"));
        int gameHubEnd = menu.indexOf("ensureHubSlot(player, NAVIGATOR_SLOT, createNavigatorHubItem())", gameHubStart);
        String gameHub = menu.substring(gameHubStart, gameHubEnd);
        assertTrue(gameHub.contains("NAVIGATOR_SLOT, createUtilityBookHubItem()"));
        assertTrue(gameHub.contains("QUEUE_CONTROL_SLOT, createGameHubLeaveItem()"));
        assertFalse(gameHub.contains("createNavigatorHubItem()"));
        assertFalse(gameHub.contains("createEconomyHubItem()"));
        assertFalse(gameHub.contains("createCosmeticsHubItem"));
        assertFalse(gameHub.contains("createSocialHubItem"));
        assertTrue(menu.contains("SETTINGS_SLOT, createSettingsHubItem()"));
        assertTrue(menu.contains("BATTLE_PASS_SLOT, createBattlePassHubItem()"));
        assertFalse(menu.contains("ensureHubSlot(player, RECIPES_SLOT"));
        assertTrue(menu.contains("case HUB_ITEM_SETTINGS -> {"));
        assertTrue(menu.contains("case HUB_ITEM_BATTLE_PASS -> {"));
        assertTrue(menu.contains("Bukkit.dispatchCommand(player, \"battlepass\")"));
    }

    @Test
    void rouletteChangesBookContextOncePerSuccessfulRound() throws Exception {
        String roulette = source("MinigameRouletteService");
        assertTrue(roulette.contains("public Game utilityBookGame(UUID playerId)"));
        assertTrue(roulette.contains("if (!started) {"));
        assertTrue(roulette.contains("announcedRound.equals(activeRoundId)"));
        assertTrue(roulette.contains("announcedRound.equals(lastUtilityBookCueRound)"));
        assertTrue(roulette.contains("if (game != Game.SPLEEF)"));
        assertTrue(roulette.contains("forOnlineRoster(player -> plugin.utilityBookService().announceContextUpdate(player))"));
        assertTrue(roulette.split("announceContextUpdate\\(", -1).length == 2,
            "Only the round-change path should announce a book update");
    }

    @Test
    void spleefRetainsGraceSetupWithoutOpeningTutorialGui() throws Exception {
        String spleef = source("SpleefService");
        int begin = spleef.indexOf("Runnable beginAfterArrival = () -> {");
        int end = spleef.indexOf("for (int i = 0; i < participants.size(); i++)", begin);
        String arrival = spleef.substring(begin, end);
        assertTrue(arrival.contains("startMatchDecayTicker()"));
        assertTrue(arrival.contains("startGraceCountdown()"));
        assertTrue(arrival.contains("plugin.utilityBookService().openIndex(viewer)"));
        assertTrue(arrival.contains("if (managedRound != null)"));
        assertFalse(arrival.contains("openTutorialMenu"));
        assertTrue(spleef.contains("Arrays.stream(Powerup.values()).map(this::createPowerupItem)"));
    }
}
