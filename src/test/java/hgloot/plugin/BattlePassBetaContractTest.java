package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class BattlePassBetaContractTest {
    private static final Path GUI_TEXTURES = Path.of(
        "rp_model_staging/normalized_rp_assets/assets/hydra/textures/font/gui");

    @Test
    void betaSeasonHasSeventyTiersAndConfigurableXp() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/battlepass.yml").toFile());
        assertEquals("beta", config.getString("active-season"));
        assertEquals(70, config.getInt("seasons.beta.tiers"));
        assertEquals(3000, config.getInt("seasons.beta.xp-per-tier"));
        assertEquals(300, config.getInt("seasons.beta.gameplay.hunger-games.complete"));
        assertEquals(50, config.getInt("seasons.beta.gameplay.hunger-games.elimination"));
        assertEquals(250, config.getInt("seasons.beta.gameplay.hunger-games.elimination-cap"));
        assertEquals(400, config.getInt("seasons.beta.gameplay.minigame-roulette.complete"));
    }

    @Test
    void rewardTracksAndPageControlsUseCorrectedRows() throws Exception {
        String service = Files.readString(Path.of("src/main/java/hgloot/plugin/BattlePassService.java"));
        assertTrue(service.contains("PREVIOUS_SLOT = 45"));
        assertTrue(service.contains("NEXT_SLOT = 53"));
        assertTrue(service.contains("safePage == 1 ? 19 : 18"));
        assertTrue(service.contains("safePage == 1 ? 28 : 27"));
        assertTrue(service.contains("page == 1 ? 19 : 18"));
        assertTrue(service.contains("page == 1 ? 28 : 27"));
    }

    @Test
    void schemaTwoLevelAndPartialProgressRestoreToOriginalCurve() {
        int oldXp = 0;
        for (int internalLevel = 1; internalLevel <= 17; internalLevel++) {
            int index = internalLevel - 1;
            oldXp += 150 + (index * 35) + (index * index * 6);
        }
        int nextIndex = 17;
        int nextRequirement = 150 + (nextIndex * 35) + (nextIndex * nextIndex * 6);
        oldXp += nextRequirement / 2;

        int restored = ProgressionService.restoreLegacyLevelXp(49_500, 3000);
        assertTrue(Math.abs(oldXp - restored) <= 1, "restored=" + restored + " expected=" + oldXp);
    }

    @Test
    void battlePassXpRemainsSeparateFromLevelXp() throws Exception {
        String battlePass = Files.readString(Path.of("src/main/java/hgloot/plugin/BattlePassService.java"));
        String progression = Files.readString(Path.of("src/main/java/hgloot/plugin/ProgressionService.java"));
        assertTrue(battlePass.contains("return state(playerId).xp"));
        assertTrue(battlePass.contains("state.xp = clampAdd(state.xp, amount)"));
        assertTrue(battlePass.contains("data.set(base + \".xp\", state.xp)"));
        assertFalse(battlePass.contains("progression.grantPrestigeXp"));
        assertTrue(progression.contains("legacyPrestigeLevelSize(level)"));
        assertTrue(progression.contains("state.xpSchema == 2"));
    }

    @Test
    void allEightSuppliedPagesUseTheHydraGuiCanvas() throws Exception {
        for (int page = 1; page <= 8; page++) {
            BufferedImage image = ImageIO.read(GUI_TEXTURES.resolve("battlepass_page_" + page + ".png").toFile());
            assertEquals(176, image.getWidth(), "page " + page);
            assertEquals(222, image.getHeight(), "page " + page);
        }
    }

    @Test
    void claimsUsePersistentTransactionIdsAndAdminStaysUnderAdmin() throws Exception {
        String service = Files.readString(Path.of("src/main/java/hgloot/plugin/BattlePassService.java"));
        String plugin = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(service.contains("grantCoinsOnce"));
        assertTrue(service.contains("battlepass:\" + seasonId"));
        assertTrue(service.contains("Track.FREE"));
        assertTrue(service.contains("Track.PREMIUM"));
        assertTrue(service.contains("return state(playerId).xp"));
        assertTrue(service.contains("schema-version\", 3"));
        assertTrue(plugin.contains("case \"battlepass\""));
        assertTrue(pluginYml.contains("aliases: [bp]"));
        assertFalse(pluginYml.contains("battlepass addxp"));
    }

    @Test
    void hgGameHubHandoffRejectsActiveAndSpectatingHgOwnership() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        assertTrue(queue.contains("isHungerGames(activeType)"));
        assertTrue(queue.contains("isHungerGames(spectatorType)"));
        assertTrue(queue.contains("Rejected stale HG handoff"));
        assertTrue(queue.contains("completeHgMatch"));
        assertTrue(queue.contains("recordHgElimination"));
        assertTrue(queue.contains("HG_KILL_XP_REWARD"));
        assertTrue(queue.contains("HG_WIN_XP_REWARD"));
        assertTrue(queue.contains("grantPrestigeXp(playerId, xp, \"MINIGAME_ROULETTE_WIN\")"));
        assertTrue(queue.contains("new VictoryResultsService.RewardSnapshot(xp, coins, fragments, null)"));
    }
}
