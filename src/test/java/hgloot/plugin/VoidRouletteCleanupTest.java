package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.event.entity.EntityKnockbackEvent.Cause;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class VoidRouletteCleanupTest {
    private ServerMock server;
    private HGLootPlugin plugin;
    private VoidRouletteService vr;
    private TeamProtectionService teams;
    private Player attacker;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = mock(HGLootPlugin.class);
        when(plugin.getName()).thenReturn("HydraMain");
        when(plugin.namespace()).thenReturn("hydramain");
        teams = mock(TeamProtectionService.class);
        when(plugin.teamProtectionService()).thenReturn(teams);
        vr = new VoidRouletteService(plugin);
        attacker = server.addPlayer("Attacker");
        target = server.addPlayer("Target");
        players("alivePlayers").addAll(Set.of(attacker.getUniqueId(), target.getUniqueId()));
        set("activeWorldName", target.getWorld().getName());
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void bridgeOwnershipRejectsVrAdmission() throws Exception {
        BridgeService bridge = mock(BridgeService.class);
        when(plugin.bridgeService()).thenReturn(bridge);
        when(bridge.isInBridge(target.getUniqueId())).thenReturn(true);
        players("alivePlayers").clear();
        target.setOp(true);
        vr.join(target);
        assertFalse(players("queuedPlayers").contains(target.getUniqueId()));
    }

    @Test
    void poolIncludesEveryInventoryMaterialExactlyOnce() {
        Material[] pool = VoidRouletteService.buildRandomItemPool();
        Set<Material> actual = Set.copyOf(Arrays.asList(pool));
        assertEquals(pool.length, actual.size());
        for (Material material : Material.values()) {
            assertEquals(!material.isLegacy() && !material.isAir() && material.isItem(),
                actual.contains(material), material.name());
        }
        assertTrue(actual.containsAll(Set.of(Material.ENDER_DRAGON_SPAWN_EGG, Material.DRAGON_EGG,
            Material.NETHERITE_SWORD, Material.ELYTRA, Material.TOTEM_OF_UNDYING, Material.TNT,
            Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.WIND_CHARGE, Material.MACE,
            Material.COMMAND_BLOCK, Material.BARRIER)));
        assertFalse(actual.contains(Material.END_PORTAL));
        assertFalse(actual.contains(Material.WATER));
    }

    @Test
    void livingPlayersCanLoseHungerEatAndPickUpFood() {
        target.setFoodLevel(14);
        FoodLevelChangeEvent hunger = new FoodLevelChangeEvent(target, 13);
        vr.onFood(hunger);
        assertFalse(hunger.isCancelled());
        assertEquals(14, target.getFoodLevel());
        FoodLevelChangeEvent eating = new FoodLevelChangeEvent(target, 18, new ItemStack(Material.APPLE));
        vr.onFood(eating);
        assertFalse(eating.isCancelled());
        Item food = mock(Item.class);
        when(food.getItemStack()).thenReturn(new ItemStack(Material.APPLE));
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(target, food, 0);
        vr.onPickup(pickup);
        assertFalse(pickup.isCancelled());
        verify(food, never()).remove();
    }

    @Test
    void spectatorsRemainProtectedAndStartFreezeStillBlocksPickup() throws Exception {
        set("startFreezeActive", true);
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(target, mock(Item.class), 0);
        vr.onPickup(pickup);
        assertTrue(pickup.isCancelled());
        players("spectators").add(target.getUniqueId());
        FoodLevelChangeEvent hunger = new FoodLevelChangeEvent(target, 15);
        vr.onFood(hunger);
        assertTrue(hunger.isCancelled());
    }

    @Test
    void thrownAndMeleeTridentsUseSixBaseDamage() {
        Trident trident = mock(Trident.class);
        when(trident.getShooter()).thenReturn(attacker);
        EntityDamageByEntityEvent thrown = new EntityDamageByEntityEvent(trident, target, DamageCause.PROJECTILE, 8);
        vr.onEntityDamage(thrown);
        assertEquals(6, thrown.getDamage());
        attacker.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        EntityDamageByEntityEvent melee = new EntityDamageByEntityEvent(attacker, target, DamageCause.ENTITY_ATTACK, 9);
        vr.onEntityDamage(melee);
        assertEquals(6, melee.getDamage());
    }

    @Test
    void tridentKnockbackTriplesOnlyItsOwnImpulse() {
        Trident trident = mock(Trident.class);
        when(trident.getShooter()).thenReturn(attacker);
        Vector original = new Vector(0.4, 0.2, -0.3);
        EntityPushedByEntityAttackEvent event = new EntityPushedByEntityAttackEvent(target, Cause.ENTITY_ATTACK, trident, original);
        vr.onTridentKnockback(event);
        assertEquals(original.clone().multiply(3), event.getKnockback());
        assertEquals(new Vector(0.4, 0.2, -0.3), original);
        attacker.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        EntityPushedByEntityAttackEvent melee = new EntityPushedByEntityAttackEvent(target, Cause.ENTITY_ATTACK, attacker, original);
        vr.onTridentKnockback(melee);
        assertEquals(original.clone().multiply(3), melee.getKnockback());
    }

    @Test
    void arrowsDoNotBecomeTridentsWhenShooterSwitchesWeapons() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        attacker.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        target.setLastDamageCause(new EntityDamageByEntityEvent(arrow, target, DamageCause.PROJECTILE, 4));
        Vector original = new Vector(0.4, 0.2, 0);
        EntityPushedByEntityAttackEvent event = new EntityPushedByEntityAttackEvent(target, Cause.ENTITY_ATTACK, attacker, original);
        vr.onTridentKnockback(event);
        assertEquals(original, event.getKnockback());
    }

    @Test
    void teammatesAndSpectatorsDoNotReceiveBoostedKnockback() throws Exception {
        Trident trident = mock(Trident.class);
        when(trident.getShooter()).thenReturn(attacker);
        when(teams.areProtectedTeammates(attacker, target)).thenReturn(true);
        Vector original = new Vector(0.4, 0.2, 0);
        EntityPushedByEntityAttackEvent event = new EntityPushedByEntityAttackEvent(target, Cause.ENTITY_ATTACK, trident, original);
        vr.onTridentKnockback(event);
        assertEquals(original, event.getKnockback());
        players("alivePlayers").remove(target.getUniqueId());
        when(teams.areProtectedTeammates(attacker, target)).thenReturn(false);
        vr.onTridentKnockback(event);
        assertEquals(original, event.getKnockback());
    }

    @Test
    void cycleCleanupKeepsRolledMacesAndWindCharges() throws Exception {
        ItemStack special = (ItemStack) call("createVoidbreakerMace");
        target.getInventory().setItem(0, special);
        target.getInventory().setItem(1, new ItemStack(Material.MACE));
        target.getInventory().setItem(2, new ItemStack(Material.WIND_CHARGE, 7));
        Method cleanup = VoidRouletteService.class.getDeclaredMethod("removeVoidbreakerItems", Player.class);
        cleanup.setAccessible(true);
        cleanup.invoke(vr, target);
        assertNull(target.getInventory().getItem(0));
        assertEquals(Material.MACE, target.getInventory().getItem(1).getType());
        assertEquals(7, target.getInventory().getItem(2).getAmount());
    }

    @Test
    void returnItemUsesExistingBackspaceModel() throws Exception {
        ItemStack item = (ItemStack) call("createSpectatorHubPaper");
        assertEquals(Material.PAPER, item.getType());
        assertTrue(item.getItemMeta().getDisplayName().contains("Return to Hub"));
        // MockBukkit's meta copy does not preserve item-model; verify both source wiring and real pack assets.
        for (String service : java.util.List.of("VoidRouletteService", "SpleefService")) {
            String source = Files.readString(Path.of("src/main/java/hgloot/plugin/" + service + ".java"));
            int start = source.indexOf("private ItemStack createSpectatorHubPaper()");
            String method = source.substring(start, source.indexOf("\n    }", start));
            assertTrue(method.contains("meta.setItemModel(NamespacedKey.fromString(\"hydra:backspace\"))"));
            assertTrue(method.contains("item.setItemMeta(meta)"));
        }
        assertTrue(Files.exists(Path.of("rp_model_staging/normalized_rp_assets/assets/hydra/items/backspace.json")));
    }

    @Test
    void roundStartKeepsFullHungerZeroSaturationAndNoBundledCharge() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/VoidRouletteService.java"))
            .replace("\r\n", "\n");
        assertTrue(source.contains("player.setFoodLevel(20);\n            player.setSaturation(0.0F);"));
        int start = source.indexOf("private void activateVoidbreakerCycle");
        String cycle = source.substring(start, source.indexOf("private ", start + 12));
        assertTrue(cycle.contains("giveItem(player, createVoidbreakerMace())"));
        assertFalse(cycle.contains("Material.WIND_CHARGE"));
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> players(String name) throws Exception {
        Field field = VoidRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Set<UUID>) field.get(vr);
    }

    private void set(String name, Object value) throws Exception {
        Field field = VoidRouletteService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(vr, value);
    }

    private Object call(String name) throws Exception {
        Method method = VoidRouletteService.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(vr);
    }
}
