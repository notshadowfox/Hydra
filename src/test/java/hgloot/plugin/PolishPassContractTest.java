package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PolishPassContractTest {
    @Test
    void hungerGamesDeathsUseMatchAudienceInsteadOfGlobalDeathBroadcast() throws Exception {
        String queue = source("src/main/java/hgloot/plugin/QueueService.java");
        assertTrue(queue.contains("if (isHungerGames(inMatchTypes.get(playerId))) {\n            sendHungerGamesDeathMessage(event);"));
        assertTrue(queue.contains("String audienceKey = playerAudienceKey(victim);"));
        assertTrue(queue.contains("event.deathMessage(null);"));
        assertTrue(queue.contains("audienceKey.equals(playerAudienceKey(viewer))"));
    }

    @Test
    void spectateAcceptsGameHubAndKeepsBusyStateGuard() throws Exception {
        String plugin = source("src/main/java/hgloot/plugin/HGLootPlugin.java");
        String router = source("src/main/java/hgloot/plugin/SpectatorCommandRouter.java");
        String routing = source("src/main/java/hgloot/plugin/SpectatorRoutingService.java");
        assertTrue(plugin.contains("return SpectatorCommandRouter.handle(this, sender, args);"));
        assertTrue(router.contains("new SpectatorRoutingService(plugin).spectate(player, args[0])"));
        assertTrue(routing.contains("plugin.gameHubService().isGameHub(player.getWorld())"));
        assertTrue(routing.contains("queue.isPlayerActivelyQueuedOrBound(playerId)"));
        assertTrue(routing.contains("Leave your queue before spectating a match."));
        assertTrue(routing.contains("Leave your current match before spectating another one."));
        assertTrue(routing.contains("bossRush.spectateTargetMatch(viewer, watched, resolved)"));
    }

    @Test
    void retiredSmokeHasNoCloudOrBlindnessPath() throws Exception {
        String utility = source("src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java");
        assertFalse(utility.contains("SMOKE_DURATION_TICKS"));
        assertFalse(utility.contains("Particle.CAMPFIRE_COSY_SMOKE"));
        assertFalse(utility.contains("PotionEffectType.BLINDNESS"));
        assertFalse(utility.contains("throwSmoke("));
    }

    @Test
    void spongeClearsLavaThroughExistingTemporaryFluidPath() throws Exception {
        String utility = source("src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java");
        assertTrue(utility.contains("type == Material.WATER || type == Material.LAVA"));
        assertTrue(utility.contains("state.restored.put(key, saved)"));
        assertTrue(utility.contains("clearSpongeFluid(saved)"));
        assertTrue(utility.contains("restoreReleasedSpongeBlocks(state, SPONGE_RESTORE_PER_PASS)"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of(file)).replace("\r\n", "\n");
    }
}
