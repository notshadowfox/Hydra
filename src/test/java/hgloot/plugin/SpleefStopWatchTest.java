package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class SpleefStopWatchTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private SpleefService spleef;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        when(plugin.menuService()).thenReturn(mock(MenuService.class));
        TeamProtectionService teams = mock(TeamProtectionService.class);
        when(teams.areProtectedTeammates(any(Player.class), any(Player.class))).thenReturn(false);
        when(plugin.teamProtectionService()).thenReturn(teams);
        spleef = new SpleefService(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void itemUsesExactDescriptionAndSingleUseIdentity() throws Exception {
        ItemStack item = createStopWatch();
        NamespacedKey itemKey = new NamespacedKey(plugin, "spleef_item");
        assertEquals("stop_watch",
            item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
        List<String> lore = item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
        assertTrue(lore.contains("Defensive · Single Use"));
        assertTrue(lore.contains("Time waits for no one. Except you, apparently."));
        assertTrue(lore.contains("Freeze yourself in place for 5 seconds."));
    }

    @Test
    void adminItemsAndRecipeBookIncludeStopWatch() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        Method method = SpleefService.class.getDeclaredMethod("giveAdminTestItems", Player.class);
        method.setAccessible(true);
        method.invoke(spleef, admin);
        NamespacedKey itemKey = new NamespacedKey(plugin, "spleef_item");
        assertTrue(Arrays.stream(admin.getInventory().getContents())
            .filter(item -> item != null && item.hasItemMeta())
            .anyMatch(item -> "stop_watch".equals(item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING))));

        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        assertTrue(source.contains("inventory.setItem(40, createTutorialItem(Powerup.STOP_WATCH))"));
    }

    @Test
    void freezeContractIsExactlyFiveSecondsAndBlocksMovementAndActions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        assertTrue(source.contains("private static final int STOP_WATCH_TICKS = 100;"));
        assertTrue(source.contains("player.setGravity(false);"));
        assertTrue(source.contains("player.setGravity(true);"));
        assertTrue(source.contains("stopWatchAnchors.containsKey(player.getUniqueId())"));
        assertTrue(source.contains("event.setVelocity(new Vector());"));
        assertTrue(source.contains("match = matchFor(attacker);"));
        assertTrue(source.contains("case \"stop_watch\" ->"));
        assertTrue(source.contains("finishStopWatch(playerId, true);"));
        assertTrue(source.contains("Sound.BLOCK_NOTE_BLOCK_HAT"));
        assertTrue(source.contains("Sound.BLOCK_NOTE_BLOCK_BELL"));
    }

    @Test
    void frozenShovelIsBlockedButNormalSnowballsCanBeThrown() throws Exception {
        SpleefService match = activeMatch();
        PlayerMock player = activePlayer(match);
        freeze(match, player);
        player.getInventory().setItemInMainHand(createShovel());
        PlayerInteractEvent shovel = interact(player, Action.RIGHT_CLICK_AIR);
        match.onInteract(shovel);
        assertTrue(shovel.isCancelled());
        assertEquals(org.bukkit.event.Event.Result.DENY, shovel.useItemInHand());

        player.getInventory().setItemInMainHand(new ItemStack(Material.SNOWBALL));
        PlayerInteractEvent snowball = interact(player, Action.RIGHT_CLICK_AIR);
        boolean initialCancelled = snowball.isCancelled();
        org.bukkit.event.Event.Result initialItemUse = snowball.useItemInHand();
        match.onInteract(snowball);
        assertEquals(initialCancelled, snowball.isCancelled());
        assertEquals(initialItemUse, snowball.useItemInHand());

        var block = player.getWorld().getBlockAt(0, 65, 0);
        block.setType(Material.SNOW_BLOCK);
        player.getInventory().setItemInMainHand(createShovel());
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        match.onBlockBreak(breakEvent);
        assertTrue(breakEvent.isCancelled());
    }

    @Test
    void frozenPlayerRemainsAnchoredAndRejectsExternalVelocity() throws Exception {
        SpleefService match = activeMatch();
        PlayerMock player = activePlayer(match);
        Location anchor = player.getLocation().clone();
        freeze(match, player);
        Location displaced = anchor.clone().add(2.0D, -3.0D, 1.0D);
        displaced.setYaw(45.0F);
        PlayerMoveEvent move = new PlayerMoveEvent(player, anchor, displaced);
        match.onMove(move);
        assertEquals(anchor.getX(), move.getTo().getX());
        assertEquals(anchor.getY(), move.getTo().getY());
        assertEquals(anchor.getZ(), move.getTo().getZ());
        assertEquals(45.0F, move.getTo().getYaw());
        PlayerVelocityEvent velocity = new PlayerVelocityEvent(player, new Vector(1.0D, 2.0D, 3.0D));
        match.onVelocity(velocity);
        assertEquals(new Vector(), velocity.getVelocity());
        assertEquals(new Vector(), player.getVelocity());
    }

    @Test
    void shovelAndMovementReturnToNormalAfterFreezeEnds() throws Exception {
        SpleefService match = activeMatch();
        PlayerMock player = activePlayer(match);
        freeze(match, player);
        Method finish = SpleefService.class.getDeclaredMethod("finishStopWatch", java.util.UUID.class, boolean.class);
        finish.setAccessible(true);
        finish.invoke(match, player.getUniqueId(), false);
        player.getInventory().setItemInMainHand(createShovel());
        PlayerInteractEvent shovel = interact(player, Action.RIGHT_CLICK_AIR);
        boolean initialCancelled = shovel.isCancelled();
        match.onInteract(shovel);
        assertEquals(initialCancelled, shovel.isCancelled());
        Location from = player.getLocation();
        Location to = from.clone().add(1.0D, 0.0D, 0.0D);
        PlayerMoveEvent move = new PlayerMoveEvent(player, from, to);
        match.onMove(move);
        assertEquals(to, move.getTo());
    }

    @Test
    void frozenPowerupInteractionPathsAreNotRejectedAtEntry() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        String interact = source.substring(source.indexOf("public void onInteract(PlayerInteractEvent"),
            source.indexOf("private void handleSpectatorInteract"));
        assertTrue(interact.contains("if (frozen && \"shovel\".equals(type))"));
        for (String type : List.of("lasso", "mega_snowball", "super_feather", "invis_orb",
            "levitation_orb", "boomerang", "stop_watch")) {
            assertTrue(interact.contains("case \"" + type + "\" ->"), type);
            assertFalse(interact.contains("frozen && \"" + type + "\""), type);
        }
        String entity = source.substring(source.indexOf("public void onInteractEntity"),
            source.indexOf("private boolean pullWithLasso"));
        assertFalse(entity.contains("stopWatchAnchors"));
        String attack = source.substring(source.indexOf("private boolean tryActivateGaleMace"),
            source.indexOf("public void onToggleFlight"));
        assertFalse(attack.contains("stopWatchAnchors"));
    }

    @Test
    void frozenPlayerCanActivateInventoryPowerups() throws Exception {
        SpleefService match = activeMatch();
        for (String powerup : List.of("SUPER_FEATHER", "INVIS_ORB", "LEVITATION_ORB", "BOOMERANG")) {
            PlayerMock player = activePlayer(match);
            freeze(match, player);
            player.getInventory().setItemInMainHand(createPowerup(powerup));
            match.onInteract(interact(player, Action.RIGHT_CLICK_AIR));
            assertTrue(player.getInventory().getItemInMainHand().getType().isAir(), powerup);
        }
    }

    @Test
    void frozenPlayerCanUseMegaSnowballAndGaleMace() throws Exception {
        SpleefService match = activeMatch();
        PlayerMock player = activePlayer(match);
        freeze(match, player);
        player.getInventory().setItemInMainHand(createPowerup("MEGA_SNOWBALL"));
        match.onInteract(interact(player, Action.RIGHT_CLICK_AIR));
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());

        PlayerMock target = activePlayer(match);
        target.teleport(player.getLocation().clone().add(1.0D, 0.0D, 0.0D));
        player.getInventory().setItemInMainHand(createPowerup("GALE_MACE"));
        PrePlayerAttackEntityEvent attack = new PrePlayerAttackEntityEvent(player, target, true);
        match.onPrePlayerAttack(attack);
        assertTrue(attack.isCancelled());
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    void suppliedTextureAndModelsArePresent() throws Exception {
        Path texture = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/textures/item/spleef/stop_watch.png");
        Path item = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/items/spleef_stop_watch.json");
        Path model = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/models/item/spleef/stop_watch.json");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(Files.readString(item).contains("hydra:item/spleef/stop_watch"));
        assertTrue(Files.readString(model).contains("hydra:item/spleef/stop_watch"));
    }

    private ItemStack createStopWatch() throws Exception {
        Class<?> powerupClass = Class.forName("hgloot.plugin.SpleefService$Powerup");
        Object stopWatch = Arrays.stream(powerupClass.getEnumConstants())
            .filter(value -> value.toString().equals("STOP_WATCH"))
            .findFirst()
            .orElseThrow();
        Method method = SpleefService.class.getDeclaredMethod("createPowerupItem", powerupClass);
        method.setAccessible(true);
        return (ItemStack) method.invoke(spleef, stopWatch);
    }

    private ItemStack createPowerup(String name) throws Exception {
        Class<?> powerupClass = Class.forName("hgloot.plugin.SpleefService$Powerup");
        Object powerup = Arrays.stream(powerupClass.getEnumConstants())
            .filter(value -> value.toString().equals(name)).findFirst().orElseThrow();
        Method method = SpleefService.class.getDeclaredMethod("createPowerupItem", powerupClass);
        method.setAccessible(true);
        return (ItemStack) method.invoke(spleef, powerup);
    }

    private ItemStack createShovel() throws Exception {
        Method method = SpleefService.class.getDeclaredMethod("createShovel", Material.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(spleef, Material.STONE_SHOVEL);
    }

    private SpleefService activeMatch() throws Exception {
        Constructor<SpleefService> constructor = SpleefService.class
            .getDeclaredConstructor(HGLootPlugin.class, SpleefService.class);
        constructor.setAccessible(true);
        SpleefService match = constructor.newInstance(plugin, spleef);
        field("matchActive").set(match, true);
        var world = server.addSimpleWorld("spleef_stop_watch_test");
        field("activeWorld").set(match, world);
        field("activeWorldName").set(match, world.getName());
        return match;
    }

    @SuppressWarnings("unchecked")
    private PlayerMock activePlayer(SpleefService match) throws Exception {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(server.getWorld("spleef_stop_watch_test"), 0.5D, 65.0D, 0.5D));
        ((Set<java.util.UUID>) field("alivePlayers").get(match)).add(player.getUniqueId());
        return player;
    }

    @SuppressWarnings("unchecked")
    private void freeze(SpleefService match, Player player) throws Exception {
        ((Map<java.util.UUID, Location>) field("stopWatchAnchors").get(match))
            .put(player.getUniqueId(), player.getLocation().clone());
    }

    private Field field(String name) throws Exception {
        Field field = SpleefService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private PlayerInteractEvent interact(PlayerMock player, Action action) {
        return new PlayerInteractEvent(player, action, player.getInventory().getItemInMainHand(),
            null, BlockFace.SELF, EquipmentSlot.HAND);
    }
}
