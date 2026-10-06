package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import javax.imageio.ImageIO;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class SpleefLassoAndGaleMaceTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private SpleefService spleef;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        server.addSimpleWorld("spleef_gale_test");
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
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
    void lassoArcLiftsAboveBothEndpointsAndSupportsFullThreeDimensions() {
        Location highTarget = new Location(null, 4.0D, 6.0D, 2.0D);
        Location lowUser = new Location(null, 0.0D, 0.0D, 0.0D);
        Location midpointDown = SpleefService.lassoArcPoint(highTarget, lowUser, 0.5D);

        assertEquals(2.0D, midpointDown.getX(), 0.0001D);
        assertEquals(1.0D, midpointDown.getZ(), 0.0001D);
        assertTrue(maxArcHeight(highTarget, lowUser) > 6.0D);
        assertTrue(maxArcHeight(lowUser, highTarget) > 6.0D);
        assertEquals(highTarget.toVector(), SpleefService.lassoArcPoint(highTarget, lowUser, 0.0D).toVector());
        assertEquals(lowUser.toVector(), SpleefService.lassoArcPoint(highTarget, lowUser, 1.0D).toVector());
    }

    @Test
    void lassoUsesEightBlockAimedRayTraceAndConsumesOnlyOnSuccess() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        assertTrue(source.contains("private static final double LASSO_RANGE_BLOCKS = 8.0D;"));
        assertTrue(source.contains("player.getWorld().rayTraceEntities("));
        assertTrue(source.contains("!player.hasLineOfSight(target)"));
        String pull = source.substring(source.indexOf("private boolean pullWithLasso"),
            source.indexOf("private boolean tryActivateGaleMace"));
        assertTrue(pull.contains("consumeOne(player, item, hand);"));
        assertTrue(pull.contains("startLassoArc(player, target, start, destination);"));
        assertTrue(source.contains("private static final int LASSO_ARC_TICKS = 10;"));
        assertTrue(source.contains("isLassoMovementClear(target, velocity)"));
        assertTrue(source.contains("target.setVelocity(new Vector(0.0D, 0.0D, 0.0D));"));
        Vector bounded = SpleefService.lassoPullVelocity(new Vector(8, 2, 0), new Vector());
        assertTrue(bounded.length() <= 1.200001D);
    }

    @Test
    void lassoProjectedMovementStopsAtSolidBlocksButCanCrossAir() throws Exception {
        var world = server.addSimpleWorld("lasso_collision_test");
        PlayerMock target = server.addPlayer("LassoTarget");
        target.teleport(new Location(world, 0.5D, 65.0D, 0.5D));
        Method clear = SpleefService.class.getDeclaredMethod("isLassoMovementClear", Player.class, Vector.class);
        clear.setAccessible(true);

        assertTrue((boolean) clear.invoke(spleef, target, new Vector(1.0D, 0.0D, 0.0D)));
        world.getBlockAt(1, 65, 0).setType(org.bukkit.Material.STONE);
        assertFalse((boolean) clear.invoke(spleef, target, new Vector(1.0D, 0.0D, 0.0D)));
    }

    @Test
    void galeMaceItemShowsWindBurstFiveAndIsAvailableToAdmins() throws Exception {
        ItemStack item = createPowerup("GALE_MACE");
        NamespacedKey itemKey = new NamespacedKey(plugin, "spleef_item");
        assertEquals("gale_mace", item.getItemMeta().getPersistentDataContainer()
            .get(itemKey, PersistentDataType.STRING));
        assertEquals(5, item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.WIND_BURST));
        assertTrue(item.getItemMeta().getLore().stream().map(ChatColor::stripColor)
            .anyMatch("Mobility · Single Use"::equals));

        PlayerMock admin = server.addPlayer("Admin");
        Method method = SpleefService.class.getDeclaredMethod("giveAdminTestItems", Player.class);
        method.setAccessible(true);
        method.invoke(spleef, admin);
        assertTrue(Arrays.stream(admin.getInventory().getContents())
            .filter(stack -> stack != null && stack.hasItemMeta())
            .anyMatch(stack -> "gale_mace".equals(stack.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING))));
    }

    @Test
    void galeMaceUsesCustomTwoPlayerLaunchAndCancelsDamage() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/SpleefService.java"));
        String preAttack = source.substring(source.indexOf("public void onPrePlayerAttack"),
            source.indexOf("public void onDamageByEntity"));
        assertTrue(preAttack.contains("tryActivateGaleMace(attacker, target)"));
        assertTrue(preAttack.contains("event.setCancelled(true);"));
        String gale = source.substring(source.indexOf("private boolean tryActivateGaleMace"),
            source.indexOf("public void onToggleFlight"));
        assertTrue(gale.contains("consumeOne(player, item, EquipmentSlot.HAND);"));
        assertTrue(gale.contains("GALE_MACE_WIELDER_LAUNCH_VELOCITY"));
        assertTrue(gale.contains("GALE_MACE_TARGET_LAUNCH_VELOCITY"));
        assertTrue(gale.contains("target.setVelocity"));
        assertTrue(!gale.contains("getNearbyEntities"));
    }

    @Test
    void galeMaceLaunchVelocitiesReachRequestedApproximateHeights() throws Exception {
        assertEquals(35.0D, simulatedApex(readStaticDouble("GALE_MACE_WIELDER_LAUNCH_VELOCITY")), 0.1D);
        assertEquals(8.0D, simulatedApex(readStaticDouble("GALE_MACE_TARGET_LAUNCH_VELOCITY")), 0.1D);
    }

    @Test
    void zeroEffectiveDamageFromWeaknessStillActivatesExactlyOnce() throws Exception {
        SpleefService match = activeMatch();
        PlayerMock attacker = server.addPlayer("Attacker");
        PlayerMock target = server.addPlayer("Target");
        addAlive(match, attacker, target);
        attacker.getInventory().setItemInMainHand(createPowerup("GALE_MACE"));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 10));
        double targetHealth = target.getHealth();

        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(attacker, target, true);
        match.onPrePlayerAttack(event);

        assertTrue(event.isCancelled());
        assertEquals(2.78D, attacker.getVelocity().getY(), 0.0001D);
        assertEquals(1.185D, target.getVelocity().getY(), 0.0001D);
        assertEquals(targetHealth, target.getHealth(), 0.0001D);
        assertTrue(attacker.getInventory().getItemInMainHand().getType().isAir());

        Vector firstLaunch = attacker.getVelocity().clone();
        match.onPrePlayerAttack(new PrePlayerAttackEntityEvent(attacker, target, true));
        assertEquals(firstLaunch, attacker.getVelocity());
    }

    @Test
    void invalidPreAttackDoesNotConsumeGaleMace() throws Exception {
        SpleefService match = activeMatch();
        PlayerMock attacker = server.addPlayer("Attacker");
        PlayerMock target = server.addPlayer("Target");
        addAlive(match, attacker, target);
        attacker.getInventory().setItemInMainHand(createPowerup("GALE_MACE"));

        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(attacker, target, false);
        match.onPrePlayerAttack(event);

        assertEquals("gale_mace", attacker.getInventory().getItemInMainHand().getItemMeta()
            .getPersistentDataContainer().get(new NamespacedKey(plugin, "spleef_item"), PersistentDataType.STRING));
        assertEquals(new Vector(), attacker.getVelocity());
    }

    @Test
    void suppliedGaleMaceTextureAndModelsArePresent() throws Exception {
        Path texture = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/textures/item/spleef/gale_mace.png");
        Path item = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/items/spleef_gale_mace.json");
        Path model = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/models/item/spleef/gale_mace.json");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(Files.readString(item).contains("hydra:item/spleef/gale_mace"));
        assertTrue(Files.readString(model).contains("hydra:item/spleef/gale_mace"));
    }

    private ItemStack createPowerup(String name) throws Exception {
        Class<?> powerupClass = Class.forName("hgloot.plugin.SpleefService$Powerup");
        Object powerup = Arrays.stream(powerupClass.getEnumConstants())
            .filter(value -> value.toString().equals(name))
            .findFirst()
            .orElseThrow();
        Method method = SpleefService.class.getDeclaredMethod("createPowerupItem", powerupClass);
        method.setAccessible(true);
        return (ItemStack) method.invoke(spleef, powerup);
    }

    private SpleefService activeMatch() throws Exception {
        Constructor<SpleefService> constructor = SpleefService.class
            .getDeclaredConstructor(HGLootPlugin.class, SpleefService.class);
        constructor.setAccessible(true);
        SpleefService match = constructor.newInstance(plugin, spleef);
        setField(match, "matchActive", true);
        setField(match, "activeWorldName", server.getWorlds().getFirst().getName());
        return match;
    }

    @SuppressWarnings("unchecked")
    private void addAlive(SpleefService match, Player... players) throws Exception {
        Field field = SpleefService.class.getDeclaredField("alivePlayers");
        field.setAccessible(true);
        Set<java.util.UUID> alive = (Set<java.util.UUID>) field.get(match);
        Arrays.stream(players).map(Player::getUniqueId).forEach(alive::add);
    }

    private void setField(SpleefService match, String name, Object value) throws Exception {
        Field field = SpleefService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(match, value);
    }

    private double readStaticDouble(String name) throws Exception {
        Field field = SpleefService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(null);
    }

    private double simulatedApex(double initialVelocity) {
        double height = 0.0D;
        double velocity = initialVelocity;
        while (velocity > 0.0D) {
            height += velocity;
            velocity = (velocity - 0.08D) * 0.98D;
        }
        return height;
    }

    private double maxArcHeight(Location start, Location destination) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 100; i++) {
            maximum = Math.max(maximum, SpleefService.lassoArcPoint(start, destination, i / 100.0D).getY());
        }
        return maximum;
    }
}
