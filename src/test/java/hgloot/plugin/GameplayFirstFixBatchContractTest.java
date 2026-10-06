package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameplayFirstFixBatchContractTest {
    @Test
    void hungerGamesGraceOwnsHiddenFireResistanceAndTimedRemoval() throws Exception {
        String queue = read("src/main/java/hgloot/plugin/QueueService.java");
        assertTrue(queue.contains("PotionEffectType.FIRE_RESISTANCE"));
        assertTrue(queue.contains("hungerGamesCombatGraceSeconds * 20 + 5"));
        assertTrue(queue.contains("false,\n            false,\n            false"));
        assertTrue(queue.contains("current.removePotionEffect(PotionEffectType.FIRE_RESISTANCE)"));
        assertTrue(queue.contains("player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE)"));
    }

    @Test
    void hungerGamesBorderUsesAtLeastSixtySecondsAndCapsEdgeSpeed() throws Exception {
        String border = read("src/main/java/hgloot/plugin/MatchBorderService.java");
        assertTrue(border.contains("world.getWorldBorder().changeSize(target, seconds * 20L)"));
        assertTrue(border.contains("return Math.max(60, (int) Math.ceil"));
        assertTrue(border.contains("MAX_HG_EDGE_SPEED"));
        assertFalse(border.contains("world.getWorldBorder().changeSize(target, 60L);"));
    }

    @Test
    void partyMutationLockExcludesQueuesButKeepsActiveModesLocked() throws Exception {
        String plugin = read("src/main/java/hgloot/plugin/HGLootPlugin.java");
        int start = plugin.indexOf("private boolean isPartyMutationLocked");
        int end = plugin.indexOf("public MenuService menuService", start);
        String lock = plugin.substring(start, end);
        assertFalse(lock.contains("isPlayerQueued"));
        assertTrue(lock.contains("isPlayerInHydraMatchOrSpectating"));
        assertTrue(lock.contains("isInActiveSpleefMatch"));
        assertTrue(lock.contains("isInActiveVoidRouletteMatch"));
        assertTrue(lock.contains("isInActiveBridgeMatch"));
        assertTrue(lock.contains("isInActiveTournament"));
    }

    @Test
    void hungerGamesLabelsDistinguishSoloAndDuos() throws Exception {
        String queue = read("src/main/java/hgloot/plugin/QueueService.java");
        assertTrue(queue.contains("? \"Hunger Games Duos\""));
        assertTrue(queue.contains(": \"Hunger Games Solos\""));
        assertTrue(queue.contains("hungerGamesMatchModeLabel(matchWorldName) + \" started. \""));
        assertFalse(queue.contains("case SOLO, HUNGERGAMES -> \"Solo Queue\""));
    }

    @Test
    void spleefMusicIsRemovedFromRuntimeCommandsAndPack() throws Exception {
        String spleef = read("src/main/java/hgloot/plugin/SpleefService.java");
        String menu = read("src/main/java/hgloot/plugin/MenuService.java");
        String pluginYml = read("src/main/resources/plugin.yml");
        assertFalse(spleef.contains("spleef.low_layer"));
        assertFalse(spleef.contains("Spleef music"));
        assertFalse(spleef.contains("bottomSong"));
        assertFalse(menu.contains("player_settings_spleef_music_toggle"));
        assertFalse(pluginYml.contains("/spleef <join|leave|status|music>"));
        assertFalse(Files.exists(Path.of(
            "rp_model_staging/normalized_rp_assets/assets/hydra/sounds/spleef/low_layer.ogg")));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
