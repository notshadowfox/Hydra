package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class SpleefBoomerangTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private SpleefService spleef;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        spleef = new SpleefService(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void outgoingCurveIsEightForwardAndFourToTheChosenSide() {
        Vector start = SpleefService.boomerangOutgoingOffset(0.0D, 1.0D);
        Vector rightMidpoint = SpleefService.boomerangOutgoingOffset(0.5D, 1.0D);
        Vector leftMidpoint = SpleefService.boomerangOutgoingOffset(0.5D, -1.0D);
        Vector end = SpleefService.boomerangOutgoingOffset(1.0D, 1.0D);

        assertEquals(new Vector(), start);
        assertEquals(4.0D, rightMidpoint.getX(), 0.0001D);
        assertEquals(-4.0D, leftMidpoint.getX(), 0.0001D);
        assertEquals(4.0D, rightMidpoint.getZ(), 0.0001D);
        assertEquals(0.0D, end.getX(), 0.0001D);
        assertEquals(8.0D, end.getZ(), 0.0001D);
    }

    @Test
    void flightUsesPointFourForwardBlocksPerTickWithoutReducingRange() throws Exception {
        Field outbound = SpleefService.class.getDeclaredField("BOOMERANG_OUTBOUND_TICKS");
        Field returning = SpleefService.class.getDeclaredField("BOOMERANG_RETURN_TICKS");
        outbound.setAccessible(true);
        returning.setAccessible(true);
        assertEquals(20, outbound.getInt(null));
        assertEquals(20, returning.getInt(null));
        assertEquals(0.4D, SpleefService.boomerangOutgoingOffset(1, 1).getZ()
            / outbound.getInt(null), 0.0001D);
    }

    @Test
    void offhandBoomerangWinsRightClickWithoutChangingOtherHandsOrDoubleFiring() throws Exception {
        ItemStack boomerang = createBoomerang();
        ItemStack ordinary = new ItemStack(org.bukkit.Material.STONE);
        assertEquals(EquipmentSlot.OFF_HAND,
            spleef.boomerangInteractionHand(EquipmentSlot.HAND, true, boomerang));
        assertEquals(EquipmentSlot.HAND,
            spleef.boomerangInteractionHand(EquipmentSlot.HAND, false, boomerang));
        assertEquals(EquipmentSlot.HAND,
            spleef.boomerangInteractionHand(EquipmentSlot.HAND, true, ordinary));
        assertEquals(EquipmentSlot.OFF_HAND,
            spleef.boomerangInteractionHand(EquipmentSlot.OFF_HAND, true, boomerang));
        var playerId = java.util.UUID.randomUUID();
        assertTrue(spleef.claimBoomerangActivation(playerId, 100));
        assertFalse(spleef.claimBoomerangActivation(playerId, 100));
        assertTrue(spleef.claimBoomerangActivation(playerId, 101));
    }

    @Test
    void offhandConsumptionLeavesMainHandUntouched() throws Exception {
        PlayerMock player = server.addPlayer("DualWielder");
        ItemStack main = new ItemStack(org.bukkit.Material.STONE);
        ItemStack boomerang = createBoomerang();
        boomerang.setAmount(2);
        player.getInventory().setItemInMainHand(main);
        player.getInventory().setItemInOffHand(boomerang);
        Method consume = SpleefService.class.getDeclaredMethod("consumeOne", Player.class,
            ItemStack.class, EquipmentSlot.class);
        consume.setAccessible(true);

        consume.invoke(spleef, player, boomerang, EquipmentSlot.OFF_HAND);

        assertEquals(org.bukkit.Material.STONE, player.getInventory().getItemInMainHand().getType());
        assertEquals(1, player.getInventory().getItemInOffHand().getAmount());
    }

    @Test
    void boomerangUsesApprovedLoreAndItsCustomModel() throws Exception {
        ItemStack item = createBoomerang();
        assertTrue(item.getItemMeta().hasLore());
        assertTrue(item.getItemMeta().getLore().stream()
            .map(org.bukkit.ChatColor::stripColor)
            .anyMatch("Destruction · Single Use"::equals));
        NamespacedKey itemKey = new NamespacedKey(plugin, "spleef_item");
        assertEquals("boomerang",
            item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        assertTrue(source.contains("new NamespacedKey(\"hydra\", \"spleef_\" + powerup.key())"));
    }

    @Test
    void safetyRadiusFollowsOwnerAndIncludesOutgoingAndReturnSamples() {
        PlayerMock owner = server.addPlayer("Owner");
        owner.teleport(new Location(owner.getWorld(), 10.0D, 80.0D, 10.0D));
        Location near = owner.getLocation().clone().add(1.99D, 0.35D, 0.0D);
        Location edge = owner.getLocation().clone().add(2.0D, 0.35D, 0.0D);
        Location outside = owner.getLocation().clone().add(2.01D, 0.35D, 0.0D);

        assertTrue(SpleefService.isInsideBoomerangSafetyRadius(near, owner));
        assertTrue(SpleefService.isInsideBoomerangSafetyRadius(edge, owner));
        assertFalse(SpleefService.isInsideBoomerangSafetyRadius(outside, owner));

        Location oldNear = near.clone();
        owner.teleport(owner.getLocation().clone().add(8.0D, 0.0D, 0.0D));
        assertFalse(SpleefService.isInsideBoomerangSafetyRadius(oldNear, owner));
        assertTrue(SpleefService.isInsideBoomerangSafetyRadius(
            owner.getLocation().clone().add(1.0D, 0.35D, 0.0D), owner));
    }

    @Test
    void adminTestItemsIncludeBoomerang() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        Method method = SpleefService.class.getDeclaredMethod("giveAdminTestItems", Player.class);
        method.setAccessible(true);
        method.invoke(spleef, admin);
        NamespacedKey itemKey = new NamespacedKey(plugin, "spleef_item");
        assertTrue(Arrays.stream(admin.getInventory().getContents())
            .filter(item -> item != null && item.hasItemMeta())
            .anyMatch(item -> "boomerang".equals(item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING))));
    }

    @Test
    void suppliedTextureAndModelAssetsArePresentAndValid() throws Exception {
        Path texture = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/textures/item/spleef/boomerang.png");
        Path item = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/items/spleef_boomerang.json");
        Path model = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/models/item/spleef/boomerang.json");
        assertTrue(Files.isRegularFile(texture));
        assertTrue(Files.isRegularFile(item));
        assertTrue(Files.isRegularFile(model));
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(Files.readString(item).contains("hydra:item/spleef/boomerang"));
        assertTrue(Files.readString(model).contains("hydra:item/spleef/boomerang"));
    }

    @Test
    void gameplayWiringConsumesOnceBreaksTilesImmediatelyAndNeverDamagesPlayers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        assertTrue(source.contains("case \"boomerang\" ->"));
        assertTrue(source.contains("consumeOne(player, item, hand);"));
        assertTrue(source.contains("throwBoomerang(player, left ? -1.0D : 1.0D);"));
        assertTrue(source.contains("breakTile(block, causeId, true);"));
        assertTrue(source.contains("if (!ownerSafe)"));
        assertTrue(source.contains("return !block.isPassable();"));
        String boomerangSection = source.substring(source.indexOf("private void throwBoomerang"),
            source.indexOf("private void activateStopWatch"));
        assertFalse(boomerangSection.contains("startDecay("));
        assertFalse(boomerangSection.toLowerCase(Locale.ROOT).contains("damage("));
        assertFalse(boomerangSection.contains("setVelocity("));
    }

    private ItemStack createBoomerang() throws Exception {
        Class<?> powerupClass = Class.forName("hgloot.plugin.SpleefService$Powerup");
        Object boomerang = Arrays.stream(powerupClass.getEnumConstants())
            .filter(value -> value.toString().equals("BOOMERANG"))
            .findFirst()
            .orElseThrow();
        Method method = SpleefService.class.getDeclaredMethod("createPowerupItem", powerupClass);
        method.setAccessible(true);
        return (ItemStack) method.invoke(spleef, boomerang);
    }
}
