package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BossRushSpectatorUiTest {
    @Test
    @SuppressWarnings("unchecked")
    void assignedRoundSpawnIsStableAndUsedThroughoutSpectatorLifecycle() throws Exception {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("BossRush-World-Test");
            var player = server.addPlayer();
            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.namespace()).thenReturn("hydramain");
            BossRushService rush = new BossRushService(plugin, mock(CombatMasterService.class));
            Map<UUID, Location> spawns = (Map<UUID, Location>) field(rush, "playerStartingSpawns").get(rush);
            Location assigned = new Location(world, 12.5D, 91.0D, -8.5D);
            spawns.put(player.getUniqueId(), assigned);

            Method startingSpawn = BossRushService.class.getDeclaredMethod("playerStartingSpawn", UUID.class);
            startingSpawn.setAccessible(true);
            Location first = (Location) startingSpawn.invoke(rush, player.getUniqueId());
            assertEquals(assigned, first);
            assertNotSame(assigned, first);
            first.add(0, 20, 0);
            assertEquals(assigned, startingSpawn.invoke(rush, player.getUniqueId()));

            String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
            assertTrue(source.contains("event.setRespawnLocation(spawn);"));
            assertTrue(source.contains("Location spawn = playerStartingSpawn(player.getUniqueId());"));
            assertTrue(source.contains("Location destination = playerStartingSpawn(event.getPlayer().getUniqueId());"));
            assertFalse(source.contains("spectatorSpawn"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void spectatorKitIsDeferredUntilAfterDeathAndGrantedAfterInventoryClear() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/BossRushService.java"));
        assertTrue(source.contains("if (playerId.equals(player.getUniqueId())) continue;"));
        assertTrue(source.contains("!teammate.isDead()) prepareSpectator(teammate)"));
        assertTrue(source.contains("player.isDead() || activeWorld == null"));
        String respawn = source.substring(source.indexOf("public void onRespawn(PlayerRespawnEvent event)"),
            source.indexOf("public void onQuit(PlayerQuitEvent event)"));
        assertTrue(respawn.indexOf("Bukkit.getScheduler().runTask(plugin")
            < respawn.indexOf("prepareSpectator(player)"));
        int prepare = source.indexOf("private void prepareSpectator(Player player)");
        int clear = source.indexOf("player.getInventory().clear();", prepare);
        int teleport = source.indexOf("player.teleport(spawn);", prepare);
        int compass = source.indexOf("player.getInventory().setItem(0, spectatorItem(Material.COMPASS", prepare);
        int leave = source.indexOf("player.getInventory().setItem(8, spectatorItem(Material.PAPER", prepare);
        assertTrue(prepare < clear && clear < teleport && teleport < compass && compass < leave);
    }

    @Test
    @SuppressWarnings("unchecked")
    void preCancelledAirClicksUseCompassAndLeaveWithoutSneaking() throws Exception {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("BossRush-World-Test");
            var spectator = server.addPlayer();
            spectator.teleport(new Location(world, 0, 90, 0));
            spectator.setGameMode(GameMode.ADVENTURE);
            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.getName()).thenReturn("HydraMain");
            when(plugin.namespace()).thenReturn("hydramain");
            BossRushService rush = new BossRushService(plugin, mock(CombatMasterService.class));
            field(rush, "activeWorld").set(rush, world);
            Field state = field(rush, "state");
            state.set(rush, Enum.valueOf((Class<Enum>) state.getType(), "ACTIVE"));
            ((Set<UUID>) field(rush, "participants").get(rush)).add(spectator.getUniqueId());
            assertFalse(spectator.isSneaking());

            Method itemMethod = BossRushService.class.getDeclaredMethod(
                "spectatorItem", Material.class, String.class, String.class);
            itemMethod.setAccessible(true);
            ItemStack compass = (ItemStack) itemMethod.invoke(rush, Material.COMPASS, "Teleport Compass", "teleport");
            PlayerInteractEvent compassClick = new PlayerInteractEvent(spectator, Action.RIGHT_CLICK_AIR,
                compass, null, BlockFace.SELF, EquipmentSlot.HAND);
            compassClick.setCancelled(true);
            rush.onSpectatorInteract(compassClick);
            assertEquals("Boss Rush Spectate", org.bukkit.ChatColor.stripColor(spectator.getOpenInventory().getTitle()));

            ItemStack leave = (ItemStack) itemMethod.invoke(rush, Material.PAPER, "Return to Hub", "hub");
            PlayerInteractEvent leaveClick = new PlayerInteractEvent(spectator, Action.RIGHT_CLICK_AIR,
                leave, null, BlockFace.SELF, EquipmentSlot.HAND);
            leaveClick.setCancelled(true);
            rush.onSpectatorInteract(leaveClick);
            verify(plugin).transitionTravel(eq(spectator), any());
            assertFalse(spectator.isSneaking());
            assertFalse(BossRushService.class.getMethod("onSpectatorInteract", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class).ignoreCancelled());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void customSpectatorTargetsOwnTeamUntilArenaFinishesAndOwnsSidebar() throws Exception {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("BossRush-World-Test");
            var spectator = server.addPlayer();
            var teammate = server.addPlayer();
            var opponent = server.addPlayer();
            for (Player player : new Player[] {spectator, teammate, opponent}) {
                player.teleport(new Location(world, 0, 90, 0));
            }

            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.getName()).thenReturn("HydraMain");
            when(plugin.namespace()).thenReturn("hydramain");
            BossRushService rush = new BossRushService(plugin, mock(CombatMasterService.class));
            field(rush, "activeWorld").set(rush, world);
            Field state = field(rush, "state");
            state.set(rush, Enum.valueOf((Class<Enum>) state.getType(), "ACTIVE"));
            Set<UUID> participants = (Set<UUID>) field(rush, "participants").get(rush);
            Set<UUID> alive = (Set<UUID>) field(rush, "alivePlayers").get(rush);
            Map<UUID, RouletteTeam> teams = (Map<UUID, RouletteTeam>) field(rush, "playerTeams").get(rush);
            for (Player player : new Player[] {spectator, teammate, opponent}) participants.add(player.getUniqueId());
            alive.add(teammate.getUniqueId());
            alive.add(opponent.getUniqueId());
            teams.put(spectator.getUniqueId(), RouletteTeam.RED);
            teams.put(teammate.getUniqueId(), RouletteTeam.RED);
            teams.put(opponent.getUniqueId(), RouletteTeam.BLUE);

            Class<?> arenaType = Class.forName("hgloot.plugin.BossRushService$ArenaInstance");
            var constructor = arenaType.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object arena = constructor.newInstance(RouletteTeam.RED, 0, 0, 0, 0, null);
            Map<RouletteTeam, Object> arenas = (EnumMap<RouletteTeam, Object>) field(rush, "arenas").get(rush);
            arenas.put(RouletteTeam.RED, arena);

            // MockBukkit does not implement setCanPickupItems, so exercise the
            // target restriction and sidebar independently of inventory setup.
            spectator.setGameMode(GameMode.ADVENTURE);
            rush.updateBossRushSidebar(spectator);
            assertTrue(rush.ownsSidebar(spectator));
            assertNotNull(spectator.getScoreboard().getObjective("bossrush"));

            Method allowed = BossRushService.class.getDeclaredMethod("canSpectateTarget", Player.class, Player.class);
            allowed.setAccessible(true);
            assertTrue((boolean) allowed.invoke(rush, spectator, teammate));
            assertFalse((boolean) allowed.invoke(rush, spectator, opponent));
            Field completed = field(arena, "completed");
            completed.setBoolean(arena, true);
            assertTrue((boolean) allowed.invoke(rush, spectator, opponent));

        } finally {
            MockBukkit.unmock();
        }
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
