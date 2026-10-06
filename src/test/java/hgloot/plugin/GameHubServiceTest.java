package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GameHubServiceTest {
    @Test
    void gameHubIsAProtectedPersistentVoidWorldBuiltFromTheWaitingLobby() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/GameHubService.java"));

        assertTrue(source.contains("WORLD_NAME = \"Hydra-GameHub\""));
        assertTrue(source.contains("gamehub/waiting_lobby.nbt"));
        assertTrue(source.contains("creator.generator(new EmptyGenerator"));
        assertTrue(source.contains("SPAWN_X = 38"));
        assertTrue(source.contains("SPAWN_Y = 63"));
        assertTrue(source.contains("SPAWN_Z = -1"));
        assertTrue(source.contains("FIGHT_PIT_Y = 58.0D"));
        assertTrue(source.contains("isFightPit(Location location)"));
        assertTrue(source.contains("target.setPVP(true)"));
        assertTrue(source.contains("unbreakableItem(Material.IRON_SWORD)"));
        assertTrue(source.contains("new ItemStack(Material.ARROW, 10)"));
        assertTrue(source.contains("meta.setUnbreakable(true)"));
        assertTrue(source.contains("FIGHT_PIT_RESPAWN_X = -22"));
        assertTrue(source.contains("FIGHT_PIT_RESPAWN_Y = 62"));
        assertTrue(source.contains("FIGHT_PIT_RESPAWN_Z = 0"));
        assertTrue(source.contains("event.setRespawnLocation(new Location(world == null"));
        assertTrue(source.contains("GameRules.IMMEDIATE_RESPAWN, true"));
        assertTrue(source.contains("90.0F, 0.0F"));
        assertTrue(source.contains("Component.text(\"+1 Kill\""));
        assertTrue(source.contains("Entered the Fight Pit. Survive."));
        assertFalse(source.contains("Queue ownership remains active."));
        assertTrue(source.contains("fightPitKillLeaders(int limit)"));
        assertFalse(source.contains("Enchantment.POWER"));
        assertTrue(source.contains("leaveFightPit(event.getPlayer())"));
        assertTrue(source.contains("event.getCause() == EntityDamageEvent.DamageCause.FALL"));
        assertFalse(source.contains("fightPitEntryFallProtection"));
        assertTrue(source.contains("kills % 2 == 0"));
        assertTrue(source.contains("kills % 5 == 0"));
        assertTrue(source.contains("gameHubOwnershipTag(playerId)"));
        assertTrue(source.contains("onBreak(BlockBreakEvent"));
        assertTrue(source.contains("onPlace(BlockPlaceEvent"));
        assertFalse(source.contains("pendingPlayers"), "A stale delayed teleport must not outlive queue ownership");
    }

    @Test
    void fightPitCancelsEveryFallIncludingAfterRouletteReturn() {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("Hydra-GameHub");
            var player = server.addPlayer();
            player.teleport(new Location(world, -22.0D, 40.0D, 0.0D));
            var gameHub = new GameHubService(org.mockito.Mockito.mock(HGLootPlugin.class));
            for (int landing = 0; landing < 2; landing++) {
                var fall = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 20.0D);
                gameHub.onDamage(fall);
                assertTrue(fall.isCancelled(), "Fall landing " + landing + " must be cancelled");
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void staleFightPitMembershipCannotOverrideHgRespawnLocation() throws Exception {
        var server = MockBukkit.mock();
        try {
            var gameHubWorld = server.addSimpleWorld("Hydra-GameHub");
            var hgWorld = server.addSimpleWorld("HG-World-Respawn-Test");
            var player = server.addPlayer();
            player.teleport(new Location(hgWorld, 4, 80, 4));
            var gameHub = new GameHubService(org.mockito.Mockito.mock(HGLootPlugin.class));
            var worldField = GameHubService.class.getDeclaredField("world");
            worldField.setAccessible(true);
            worldField.set(gameHub, gameHubWorld);
            var statesField = GameHubService.class.getDeclaredField("fightPitStates");
            statesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var states = (java.util.Map<java.util.UUID, Object>) statesField.get(gameHub);
            states.put(player.getUniqueId(), null);
            var respawn = new org.bukkit.event.player.PlayerRespawnEvent(player, hgWorld.getSpawnLocation(), false);
            gameHub.onRespawn(respawn);
            assertTrue(respawn.getRespawnLocation().getWorld() == hgWorld);
            assertFalse(states.containsKey(player.getUniqueId()));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void gameHubMovementDisablesCollisionButDoesNotChangeMatchWorlds() {
        var server = MockBukkit.mock();
        try {
            var hub = server.addSimpleWorld("Hydra-GameHub");
            var match = server.addSimpleWorld("Bridge-World-Test");
            var player = server.addPlayer();
            var gameHub = new GameHubService(org.mockito.Mockito.mock(HGLootPlugin.class));
            player.teleport(new Location(hub, 0, 63, 0));
            player.setCollidable(true);
            gameHub.onMove(new org.bukkit.event.player.PlayerMoveEvent(player,
                player.getLocation(), player.getLocation().clone().add(1, 0, 0)));
            assertFalse(player.isCollidable());
            player.teleport(new Location(match, 0, 63, 0));
            gameHub.onChangedWorld(new org.bukkit.event.player.PlayerChangedWorldEvent(player, hub));
            assertTrue(player.isCollidable());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void gameHubTransfersDoNotInvokeDestructiveHubExit() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));

        int transferStart = queue.indexOf("public boolean sendPlayerToGameHub");
        int transferEnd = queue.indexOf("public void sendAdminList", transferStart);
        String transfer = queue.substring(transferStart, transferEnd);
        assertTrue(transfer.contains("hydra.gameHubService().enter(player)"));
        assertFalse(transfer.contains("removeFromQueue"));
        assertFalse(transfer.contains("removeFromMatch"));
        assertTrue(roulette.contains("plugin.queueService().sendPlayerToGameHub(player)"));
        assertTrue(roulette.contains("if (!disabling) plugin.queueService().sendPlayerToHub(player)"),
            "Completed tournament cleanup must still return to the main Hub");
    }

    @Test
    void hungerGamesUsesGameHubBeforeMatchAndMainHubAfterMatch() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));

        assertTrue(queue.contains("isHungerGames(type) && plugin instanceof HGLootPlugin hydra"));
        assertTrue(queue.contains("hydra.gameHubService().enter(player)"),
            "Joining the HG queue should continue to stage players in the Game Hub");

        int winnerStart = queue.indexOf("private void startWinnerCountdown");
        int winnerEnd = queue.indexOf("private void finishMatchWorld", winnerStart);
        String winnerReturn = queue.substring(winnerStart, winnerEnd);
        assertTrue(winnerReturn.contains("hydra.transitionTravelGroup(returning, player -> returnToHub(player, true), finish)"));
        assertFalse(winnerReturn.contains("sendPlayerToGameHub(currentWinner)"),
            "Completed HG winners must return to the normal Hub");

        int cleanupStart = winnerEnd;
        int cleanupEnd = queue.indexOf("private void", cleanupStart + "private void".length());
        String matchCleanup = queue.substring(cleanupStart, cleanupEnd);
        assertTrue(matchCleanup.contains("returnToHub(player, true)"));
        assertFalse(matchCleanup.contains("sendPlayerToGameHub(player)"),
            "Completed HG spectators must return to the normal Hub");
    }

    @Test
    void forceEndReturnsActivePlayersBeforeOwnershipIsCleared() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        int cleanupStart = queue.indexOf("private void finishMatchWorld");
        int cleanupEnd = queue.indexOf("private void sweepMatchRuntimeEntities", cleanupStart);
        String cleanup = queue.substring(cleanupStart, cleanupEnd);
        int returnIndex = cleanup.indexOf("returnToHub(player, true)");
        int removeIndex = cleanup.indexOf("inMatchPlayers.remove(id)");
        assertTrue(returnIndex >= 0 && returnIndex < removeIndex,
            "Match cleanup must route active players before deleting authoritative ownership");
    }

    @Test
    void localMatchCompletionPrefersConfiguredHydraHubOverGenericSpawnCommand() throws Exception {
        String queue = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));
        int start = queue.indexOf("private void returnToHub");
        int end = queue.indexOf("public void returnPlayerToHydraHub", start);
        String method = queue.substring(start, end);
        assertTrue(method.indexOf("player.teleport(lobbySpawn)") < method.indexOf("teleportViaSpawnCommand(player)"),
            "A generic per-world spawn command must not override Hydra's authoritative Hub location");
    }

    @Test
    void queueOwnershipTagsComeFromAuthoritativeQueueAndTournamentState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/QueueService.java"));

        assertTrue(source.contains("minigameRouletteService().isTournamentMember(playerId)"));
        assertTrue(source.contains("return \"MR\""));
        assertTrue(source.contains("return \"HG\""));
        assertTrue(source.contains("? \"BR\" : null"));
    }

    @Test
    void hubInventoryAndCosmeticsDoNotOverwriteTheFightPitKit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));

        assertTrue(source.contains("hydra.gameHubService().isFightPit(player.getLocation())"));
    }
}
