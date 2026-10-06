package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;
import org.bukkit.scoreboard.Team;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.logging.Logger;

final class KitEditorServiceTest {
    @TempDir Path temp;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void editorEntryAndHeaderHaveNoRedundantInstructions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/KitEditorService.java"))
            .replace("\r\n", "\n");
        assertFalse(source.contains("Rearrange your Bridge loadout, then confirm to save."));
        assertFalse(source.contains("Rearrange your inventory and hotbar, then confirm to save."));
        assertTrue(source.contains("header.text(Component.text(session.definition().displayName() + \" KIT EDITOR\", NamedTextColor.AQUA));"));
    }

    @Test
    void voidFallReturnsToAssignedWorldSpawnAndClearsMomentum() throws Exception {
        // MockBukkit does not implement Server#getWorldContainer, so exercise the real
        // movement/damage handlers on an instance with only their session state installed.
        KitEditorService editor = mock(KitEditorService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        setTestField(editor, "plugin", plugin);
        setTestField(editor, "room", KitEditorService.RoomLayout.defaults());
        setTestField(editor, "sessions", new HashMap<UUID, Object>());
        setTestField(editor, "internalTeleports", new java.util.HashSet<UUID>());
        setTestField(editor, "pendingVoidReturns", new java.util.HashSet<UUID>());
        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenReturn(true);
        attachTestSession(editor, id, world);

        var from = new Location(world, 9, 80, 45);
        var boundary = new Location(world, 9, 79, 45);
        var below = new Location(world, 9, 78.99, 45);
        assertFalse(KitEditorService.belowEditorIsland(boundary));
        assertTrue(KitEditorService.belowEditorIsland(below));
        var atBoundary = new PlayerMoveEvent(player, from, boundary);
        editor.onMove(atBoundary);
        assertEquals(79, atBoundary.getTo().getY());
        verify(player, never()).setVelocity(any(Vector.class));
        verify(player, never()).teleport(any(Location.class));

        var move = new PlayerMoveEvent(player, boundary, below);
        editor.onMove(move);
        assertTrue(move.isCancelled());
        var repeatedMove = new PlayerMoveEvent(player, boundary, below);
        editor.onMove(repeatedMove);
        assertTrue(repeatedMove.isCancelled());
        verify(player, never()).teleport(any(Location.class));
        MockBukkit.getMock().getScheduler().performOneTick();
        org.mockito.ArgumentCaptor<Location> destination = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(destination.capture());
        assertEquals(world, destination.getValue().getWorld());
        assertEquals(9, destination.getValue().getX());
        assertEquals(81, destination.getValue().getY());
        assertEquals(45, destination.getValue().getZ());
        assertEquals(180.0F, destination.getValue().getYaw());
        verify(player).setVelocity(new Vector());
        verify(player).setFallDistance(0.0F);

        Player outsider = mock(Player.class);
        when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
        var outsiderMove = new PlayerMoveEvent(outsider, from, below);
        editor.onMove(outsiderMove);
        assertEquals(78.99, outsiderMove.getTo().getY());
        verify(outsider, never()).setVelocity(any(Vector.class));

        var voidDamage = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.VOID, 20.0D);
        editor.onDamage(voidDamage);
        assertTrue(voidDamage.isCancelled());
        verify(player, times(2)).teleport(destination.capture());
        assertEquals(world, destination.getValue().getWorld());
        assertEquals(9, destination.getValue().getX());
        assertEquals(81, destination.getValue().getY());
        assertEquals(45, destination.getValue().getZ());
    }

    @SuppressWarnings("unchecked")
    private static void attachTestSession(KitEditorService editor, UUID owner, World world) throws Exception {
        Class<?> type = Class.forName("hgloot.plugin.KitEditorService$Session");
        var constructor = type.getDeclaredConstructor(KitDefinition.class, KitEditorWorldPool.Slot.class,
            List.class, Scoreboard.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(new BridgeKitDefinition(), new KitEditorWorldPool.Slot(1, world),
            List.of(), null);
        var field = KitEditorService.class.getDeclaredField("sessions");
        field.setAccessible(true);
        ((Map<UUID, Object>) field.get(editor)).put(owner, session);
    }

    private static void setTestField(KitEditorService editor, String name, Object value) throws Exception {
        var field = KitEditorService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(editor, value);
    }

    @Test
    void firstTimePlayerUsesAuthoritativeBridgeItemsAndDefaultSlots() {
        KitEditorService editor = editor();
        UUID id = UUID.randomUUID();
        assertEquals(new BridgeKitDefinition().defaultSlots(), editor.layout(id, BridgeKitDefinition.ID));
        assertEquals(64, BridgeKitDefinition.items(Material.RED_WOOL).get("blocks").getAmount());
        assertEquals(Material.BLUE_WOOL, BridgeKitDefinition.items(Material.BLUE_WOOL).get("blocks").getType());
        assertEquals(8, BridgeKitDefinition.items(Material.RED_WOOL).get("golden_apples").getAmount());
        assertEquals(1, BridgeKitDefinition.items(Material.RED_WOOL).get("arrow").getAmount());
        assertEquals(Material.RED_TERRACOTTA, new BridgeKitDefinition().previewItems().get("blocks").getType());
        assertEquals(Material.LEATHER_HELMET, BridgeKitDefinition.armor(org.bukkit.Color.RED)[3].getType());
    }

    @Test
    void bossRushDefaultLayoutUsesAuthoritativeGearAndShieldOffhand() {
        KitEditorService editor = editor();
        UUID playerId = UUID.randomUUID();
        Map<String, Integer> slots = editor.layout(playerId, BossRushKitDefinition.ID);
        assertEquals(new BossRushKitDefinition().defaultSlots(), slots);
        assertEquals(KitLayout.OFFHAND_SLOT, slots.get("shield"));
        assertEquals(7, slots.size());
        assertEquals(7, slots.values().stream().distinct().count());
        Map<String, ItemStack> items = BossRushService.standardKitItems();
        assertEquals(Material.DIAMOND_SWORD, items.get("sword").getType());
        assertEquals(Material.DIAMOND_AXE, items.get("axe").getType());
        assertEquals(Material.BOW, items.get("bow").getType());
        assertEquals(new ItemStack(Material.COOKED_BEEF, 5), items.get("food"));
        assertEquals(new ItemStack(Material.GOLDEN_APPLE, 3), items.get("golden_apples"));
        assertEquals(new ItemStack(Material.ARROW, 10), items.get("arrows"));
        assertEquals(Material.SHIELD, items.get("shield").getType());
        PlayerInventory inventory = mock(PlayerInventory.class);
        BossRushService.grantStandardKit(inventory, playerId, editor);
        verify(inventory).setItemInOffHand(new ItemStack(Material.SHIELD));
        verify(inventory).setItem(0, items.get("sword"));
        verify(inventory).setItem(8, items.get("arrows"));
    }

    @Test
    void bossRushLayoutPersistsPerPlayerAndEveryGrantUsesOnlySavedPositions() {
        KitEditorService editor = editor();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<String, Integer> moved = new HashMap<>(editor.layout(first, BossRushKitDefinition.ID));
        moved.put("sword", 4);
        moved.put("shield", 5);
        assertTrue(editor.saveLayout(first, BossRushKitDefinition.ID, moved));

        KitEditorService reopened = editor();
        assertEquals(moved, reopened.layout(first, BossRushKitDefinition.ID));
        assertEquals(0, reopened.layout(second, BossRushKitDefinition.ID).get("sword"));
        assertEquals(KitLayout.OFFHAND_SLOT, reopened.layout(second, BossRushKitDefinition.ID).get("shield"));

        PlayerInventory inventory = mock(PlayerInventory.class);
        BossRushService.grantStandardKit(inventory, first, reopened);
        verify(inventory).setItem(4, BossRushService.standardKitItems().get("sword"));
        verify(inventory).setItem(5, new ItemStack(Material.SHIELD));
        verify(inventory, never()).setItemInOffHand(any(ItemStack.class));
        verify(inventory).setArmorContents(any(ItemStack[].class));
        BossRushService.grantStandardKit(inventory, first, reopened);
        verify(inventory, times(2)).setItem(4, BossRushService.standardKitItems().get("sword"));
    }

    @Test
    void bossRushCaptureRejectsMissingDuplicateAndForeignItems() {
        KitDefinition definition = new BossRushKitDefinition();
        ItemStack[] storage = new ItemStack[KitLayout.STORAGE_SLOTS];
        definition.previewItems().forEach((id, item) -> {
            int slot = definition.defaultSlots().get(id);
            if (slot < KitLayout.STORAGE_SLOTS) storage[slot] = item.clone();
        });
        ItemStack shield = definition.previewItems().get("shield");
        assertEquals(definition.defaultSlots(), KitLayout.capture(definition, storage, shield).orElseThrow());
        storage[4] = new ItemStack(Material.DIAMOND_SWORD);
        assertTrue(KitLayout.capture(definition, storage, shield).isEmpty());
        storage[4] = null;
        assertTrue(KitLayout.capture(definition, storage, null).isEmpty());
        assertTrue(KitEditorService.allowedEditorOffhandItem(definition, shield));
        assertFalse(KitEditorService.allowedEditorOffhandItem(definition, definition.previewItems().get("sword")));
    }

    @Test
    void savedLayoutsPersistPerPlayerAndApplyToLiveBridgeItems() {
        KitEditorService editor = editor();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<String, Integer> moved = new HashMap<>(editor.layout(first, BridgeKitDefinition.ID));
        moved.put("sword", 7);
        moved.put("arrow", 6);
        assertTrue(editor.saveLayout(first, BridgeKitDefinition.ID, moved));

        KitEditorService reloaded = editor();
        assertEquals(7, reloaded.layout(first, BridgeKitDefinition.ID).get("sword"));
        assertEquals(6, reloaded.bridgeArrowSlot(first));
        assertEquals(0, reloaded.layout(second, BridgeKitDefinition.ID).get("sword"));
        assertEquals(8, reloaded.bridgeArrowSlot(second));
        PlayerInventory inventory = mock(PlayerInventory.class);
        reloaded.equipBridgeKit(inventory, first, Material.BLUE_WOOL);
        verify(inventory).setItem(7, new ItemStack(Material.IRON_SWORD));
        verify(inventory).setItem(6, new ItemStack(Material.ARROW));
        verify(inventory).setItem(3, new ItemStack(Material.BLUE_WOOL, 64));
    }

    @Test
    void invalidAndOldLayoutsCannotDuplicateSlotsOrLoseNewKitItems() throws Exception {
        KitEditorService editor = editor();
        UUID id = UUID.randomUUID();
        Path file = temp.resolve("kit-editor/layouts").resolve(id + ".yml");
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("kits.bridge.slots.sword", 4);
        yaml.set("kits.bridge.slots.bow", 4);
        yaml.set("kits.bridge.slots.pickaxe", 100);
        yaml.set("kits.bridge.slots.retired_item", 5);
        yaml.save(file.toFile());

        Map<String, Integer> layout = editor.layout(id, BridgeKitDefinition.ID);
        assertEquals(6, layout.size());
        assertEquals(6, layout.values().stream().distinct().count());
        assertFalse(layout.containsKey("retired_item"));
        assertTrue(layout.values().stream().allMatch(slot -> slot >= 0 && slot < 36));
    }

    @Test
    void confirmValidationRejectsDuplicationLossAmountChangeAndForeignItems() {
        KitDefinition definition = new BridgeKitDefinition();
        Map<String, ItemStack> expected = definition.previewItems();
        ItemStack[] inventory = new ItemStack[36];
        Map<String, Integer> slots = new BridgeKitDefinition().defaultSlots();
        expected.forEach((key, item) -> inventory[slots.get(key)] = item.clone());
        assertTrue(KitLayout.capture(definition, inventory, null).isPresent());

        inventory[7] = new ItemStack(Material.IRON_SWORD);
        assertTrue(KitLayout.capture(definition, inventory, null).isEmpty());
        inventory[7] = null;
        inventory[3].setAmount(63);
        assertTrue(KitLayout.capture(definition, inventory, null).isEmpty());
        inventory[3].setAmount(64);
        inventory[0] = null;
        assertTrue(KitLayout.capture(definition, inventory, null).isEmpty());
        inventory[0] = new ItemStack(Material.DIAMOND_SWORD);
        assertTrue(KitLayout.capture(definition, inventory, null).isEmpty());
    }

    @Test
    void bridgeBlocksCanBeSavedToOffhandAndRegrantedWithTeamColor() {
        KitDefinition definition = new BridgeKitDefinition();
        ItemStack[] storage = new ItemStack[KitLayout.STORAGE_SLOTS];
        Map<String, ItemStack> items = definition.previewItems();
        items.forEach((key, item) -> {
            if (!"blocks".equals(key)) storage[definition.defaultSlots().get(key)] = item.clone();
        });
        Map<String, Integer> saved = KitLayout.capture(definition, storage, items.get("blocks")).orElseThrow();
        assertEquals(KitLayout.OFFHAND_SLOT, saved.get("blocks"));
        assertEquals(saved, KitLayout.normalize(definition, saved));

        PlayerInventory inventory = mock(PlayerInventory.class);
        KitLayout.place(inventory, BridgeKitDefinition.items(Material.BLUE_WOOL), saved);
        verify(inventory).setItemInOffHand(new ItemStack(Material.BLUE_WOOL, 64));
        verify(inventory).setItem(0, new ItemStack(Material.IRON_SWORD));
    }

    @Test
    void invalidOffhandItemsAndSavedSlotsNeverReplaceAuthoritativeKit() {
        KitDefinition definition = new BridgeKitDefinition();
        Map<String, Integer> invalid = new HashMap<>(definition.defaultSlots());
        invalid.put("sword", KitLayout.OFFHAND_SLOT);
        assertEquals(0, KitLayout.normalize(definition, invalid).get("sword"));

        ItemStack[] storage = new ItemStack[KitLayout.STORAGE_SLOTS];
        definition.previewItems().forEach((key, item) -> storage[definition.defaultSlots().get(key)] = item.clone());
        assertTrue(KitLayout.capture(definition, storage, new ItemStack(Material.DIAMOND)).isEmpty());
        storage[3] = null;
        assertTrue(KitLayout.capture(definition, storage, new ItemStack(Material.RED_TERRACOTTA, 63)).isEmpty());
        assertTrue(KitLayout.capture(definition, storage, new ItemStack(Material.RED_TERRACOTTA, 64)).isPresent());

        assertTrue(KitEditorService.allowedEditorOffhandItem(definition, null));
        assertTrue(KitEditorService.allowedEditorOffhandItem(definition,
            new ItemStack(Material.RED_TERRACOTTA, 64)));
        assertFalse(KitEditorService.allowedEditorOffhandItem(definition,
            new ItemStack(Material.IRON_SWORD)));
    }

    @Test
    void blocksCanReachOffhandByCursorNumberKeyOrInventoryFKey() {
        KitDefinition definition = new BridgeKitDefinition();
        ItemStack blocks = definition.previewItems().get("blocks");
        ItemStack sword = definition.previewItems().get("sword");
        int offhand = KitLayout.OFFHAND_SLOT;

        assertTrue(KitEditorService.editorClickAllowed(definition, ClickType.LEFT, offhand,
            null, blocks, null));
        assertTrue(KitEditorService.editorClickAllowed(definition, ClickType.RIGHT, offhand,
            null, blocks, null));
        assertTrue(KitEditorService.editorClickAllowed(definition, ClickType.NUMBER_KEY, offhand,
            null, null, blocks));
        assertTrue(KitEditorService.editorClickAllowed(definition, ClickType.SWAP_OFFHAND, 3,
            blocks, null, null));
        assertFalse(KitEditorService.editorClickAllowed(definition, ClickType.SWAP_OFFHAND, 0,
            sword, null, null));
        assertFalse(KitEditorService.editorClickAllowed(definition, ClickType.LEFT, offhand,
            null, sword, null));
        assertFalse(KitEditorService.editorClickAllowed(definition, ClickType.LEFT, 36,
            null, blocks, null));
    }

    @Test
    void resetIsOnlyAWorkingCopyUntilConfirmPersistsIt() {
        KitEditorService editor = editor();
        UUID id = UUID.randomUUID();
        Map<String, Integer> saved = new HashMap<>(editor.layout(id, BridgeKitDefinition.ID));
        saved.put("sword", 7);
        assertTrue(editor.saveLayout(id, BridgeKitDefinition.ID, saved));
        Map<String, Integer> reset = KitLayout.normalize(new BridgeKitDefinition(), null);
        assertEquals(0, reset.get("sword"));
        assertEquals(7, editor.layout(id, BridgeKitDefinition.ID).get("sword"));
        assertTrue(editor.saveLayout(id, BridgeKitDefinition.ID, reset));
        assertEquals(0, editor.layout(id, BridgeKitDefinition.ID).get("sword"));
    }

    @Test
    void simultaneousConfirmCancelAndDisconnectKeepInventoriesAndLayoutsSeparate() {
        var server = MockBukkit.getMock();
        var hub = server.addSimpleWorld("KitEditorTestHub");
        var first = server.addPlayer();
        var second = server.addPlayer();
        first.teleport(new Location(hub, 1, 65, 1));
        second.teleport(new Location(hub, 2, 65, 2));
        first.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        second.getInventory().setItem(0, new ItemStack(Material.EMERALD));

        HGLootPlugin plugin = mock(HGLootPlugin.class);
        QueueService queue = mock(QueueService.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("KitEditorServiceTest"));
        when(plugin.queueService()).thenReturn(queue);
        when(queue.isLobbyWorld(first)).thenReturn(true);
        when(queue.isLobbyWorld(second)).thenReturn(true);
        KitEditorService editor = new KitEditorService(plugin);

        assertTrue(editor.begin(first, BridgeKitDefinition.ID));
        assertTrue(editor.begin(second, BridgeKitDefinition.ID));
        assertTrue(Math.abs(first.getLocation().getX() - second.getLocation().getX()) >= 40);
        first.getInventory().setItem(7, first.getInventory().getItem(0));
        first.getInventory().setItem(0, null);
        editor.confirm(first);
        assertFalse(editor.isEditing(first.getUniqueId()));
        assertTrue(editor.isEditing(second.getUniqueId()));
        assertEquals(Material.DIAMOND, first.getInventory().getItem(0).getType());
        assertEquals(hub, first.getWorld());
        assertEquals(7, editor.layout(first.getUniqueId(), BridgeKitDefinition.ID).get("sword"));
        editor.cancel(second);
        assertEquals(Material.EMERALD, second.getInventory().getItem(0).getType());
        assertEquals(0, editor.layout(second.getUniqueId(), BridgeKitDefinition.ID).get("sword"));

        assertTrue(editor.begin(second, BridgeKitDefinition.ID));
        editor.onQuit(new org.bukkit.event.player.PlayerQuitEvent(second, net.kyori.adventure.text.Component.empty()));
        assertFalse(editor.isEditing(second.getUniqueId()));
        assertFalse(editor.isRecovering(second.getUniqueId()));
        assertEquals(Material.EMERALD, second.getInventory().getItem(0).getType());
    }

    @Test
    void entryAndExitRunExistingLifecycleOnlyAtBlackMidpoint() {
        var server = MockBukkit.getMock();
        var hub = server.addSimpleWorld("KitEditorFadeHub");
        var player = server.addPlayer();
        player.teleport(new Location(hub, 1, 65, 1));
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        KitEditorService editor = editingService(player);
        TransitionService transition = mock(TransitionService.class);
        AtomicReference<Runnable> midpoint = new AtomicReference<>();
        when(transition.transition(any(), any(), any())).thenAnswer(call -> {
            midpoint.set(call.getArgument(2));
            return true;
        });
        editor.setTransitionService(transition);

        assertTrue(editor.beginWithTransition(player, BridgeKitDefinition.ID));
        assertFalse(editor.isEditing(player.getUniqueId()));
        assertEquals(hub, player.getWorld());
        midpoint.get().run();
        assertTrue(editor.isEditing(player.getUniqueId()));
        assertTrue(player.getWorld().getName().startsWith(KitEditorWorldPool.PREFIX));

        editor.cancel(player);
        assertTrue(editor.isEditing(player.getUniqueId()));
        midpoint.get().run();
        assertFalse(editor.isEditing(player.getUniqueId()));
        assertEquals(hub, player.getWorld());
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
    }

    @Test
    void resetAndPhysicalControlsAreSessionScopedAndRequireConfirmation() {
        var server = MockBukkit.getMock();
        var hub = server.addSimpleWorld("KitEditorControlsHub");
        var first = server.addPlayer();
        var second = server.addPlayer();
        first.teleport(new Location(hub, 1, 65, 1));
        second.teleport(new Location(hub, 2, 65, 2));
        KitEditorService editor = editingService(first, second);
        Map<String, Integer> saved = new HashMap<>(editor.layout(first.getUniqueId(), BridgeKitDefinition.ID));
        saved.put("sword", 7);
        assertTrue(editor.saveLayout(first.getUniqueId(), BridgeKitDefinition.ID, saved));
        assertTrue(editor.begin(first, BridgeKitDefinition.ID));
        assertTrue(editor.begin(second, BridgeKitDefinition.ID));
        assertNotNull(first.getScoreboard().getObjective("kit_editor"));
        assertEquals(Material.RED_TERRACOTTA, first.getInventory().getItem(3).getType());
        assertEquals(Material.LEATHER_HELMET, first.getInventory().getHelmet().getType());

        // A click at the other player's control must not affect either session.
        interact(editor, first, 96 + 5, 45);
        assertTrue(editor.isEditing(first.getUniqueId()));
        assertTrue(editor.isEditing(second.getUniqueId()));
        interact(editor, first, 5, 45);
        assertEquals(Material.IRON_SWORD, first.getInventory().getItem(0).getType());
        assertEquals(7, editor.layout(first.getUniqueId(), BridgeKitDefinition.ID).get("sword"));
        interact(editor, first, 3, 42);
        assertFalse(editor.isEditing(first.getUniqueId()));
        assertNull(first.getScoreboard().getObjective("kit_editor"));
        assertEquals(0, editor.layout(first.getUniqueId(), BridgeKitDefinition.ID).get("sword"));
        assertEquals(0, editor.layout(second.getUniqueId(), BridgeKitDefinition.ID).get("sword"));
        editor.cancel(second);
    }

    @Test
    void suppliedMapIsPinnedAndEachWorldUsesIdenticalLocalLandmarks() throws Exception {
        byte[] map = KitEditorServiceTest.class.getResourceAsStream("/kit-editor/Kit.nbt").readAllBytes();
        assertEquals("BDC142DC4999A0164C6F8C1E086C6728740210BF317EB7E4C33BCAB8E5BF55F8",
            HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(map)));

        var world = MockBukkit.getMock().addSimpleWorld("KitEditorMapLandmarks");
        var room = KitEditorService.RoomLayout.defaults();
        assertEquals(new Location(world, 9, 81, 45, 180.0F, 0.0F), room.spawn(world));
        assertEquals(new Location(world, 9.5, 82, 37.5, 0.0F, 0.0F), room.mannequin(world));
        assertEquals(new Location(world, 9, 81, 37), room.mannequinPost(world));
        assertEquals(new Location(world, 5, 81, 45), room.post(world, KitEditorService.Control.RESET));
        assertEquals(new Location(world, 3, 81, 42), room.post(world, KitEditorService.Control.CONFIRM));
        assertEquals(new Location(world, 5, 81, 39), room.post(world, KitEditorService.Control.CANCEL));
        var generator = new KitEditorService.EmptyKitEditorGenerator(81);
        assertFalse(generator.shouldGenerateNoise());
        assertFalse(generator.shouldGenerateSurface());
        assertFalse(generator.shouldGenerateCaves());
        assertFalse(generator.shouldGenerateStructures());
        assertFalse(generator.shouldGenerateBedrock());
        assertEquals(new Location(world, 9, 81, 45, 180.0F, 0.0F),
            generator.getFixedSpawnLocation(world, new java.util.Random(1)));
        assertEquals(0, KitEditorService.countIslandPosts(room, world));
        room.post(world, KitEditorService.Control.RESET).getBlock().setType(Material.WARPED_FENCE);
        assertEquals(1, KitEditorService.countIslandPosts(room, world));
        room.post(world, KitEditorService.Control.CONFIRM).getBlock().setType(Material.WARPED_FENCE);
        room.post(world, KitEditorService.Control.CANCEL).getBlock().setType(Material.WARPED_FENCE);
        room.mannequinPost(world).getBlock().setType(Material.WARPED_FENCE);
        assertEquals(4, KitEditorService.countIslandPosts(room, world));
    }

    @Test
    void mannequinNameUsesOwnerUsernameWithoutRankOrGenericLabel() {
        var player = MockBukkit.getMock().addPlayer("TestPlayer");
        assertEquals(net.kyori.adventure.text.Component.text("TestPlayer",
            net.kyori.adventure.text.format.NamedTextColor.WHITE), KitEditorMannequin.displayName(player));
        NpcVisual visual = new NpcVisual(UUID.randomUUID(), UUID.randomUUID(), "npcinternal12345",
            player.getLocation(), player.getWorld(), null, null);
        KitEditorMannequin.hideInternalName(player, visual);
        Team team = player.getScoreboard().getTeam("kit_preview");
        assertNotNull(team);
        assertEquals(Team.OptionStatus.NEVER, team.getOption(Team.Option.NAME_TAG_VISIBILITY));
        assertTrue(team.hasEntry(visual.profileName()));
    }

    @Test
    void mannequinCopiesOnlineTextureAndMirrorsHeadRelativeToSouthFacingBody() throws Exception {
        var owner = MockBukkit.getMock().addPlayer("TestOwner");
        var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        var texture = mock(com.destroystokyo.paper.profile.ProfileProperty.class);
        when(texture.getName()).thenReturn("textures");
        when(texture.getValue()).thenReturn("online-texture");
        when(texture.getSignature()).thenReturn("signature");
        when(profile.clone()).thenReturn(profile);
        when(profile.getProperties()).thenReturn(Set.of(texture));
        var player = mock(org.bukkit.entity.Player.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        assertEquals(List.of(new SkinPropertyData("textures", "online-texture", "signature")),
            KitEditorMannequin.skinProperties(player));

        var mannequin = new KitEditorMannequin(mock(JavaPlugin.class));
        var bridge = mock(NpcNmsBridge.class);
        var field = KitEditorMannequin.class.getDeclaredField("bridge");
        field.setAccessible(true);
        field.set(mannequin, bridge);
        NpcVisual visual = new NpcVisual(UUID.randomUUID(), UUID.randomUUID(), "npcinternal12345",
            owner.getLocation(), owner.getWorld(), null, null);
        var preview = new KitEditorMannequin.Preview(owner.getUniqueId(), visual, null, null, 0.0F, 180.0F);
        mannequin.look(owner, preview, new Location(owner.getWorld(), 9, 81, 45, 195.0F, -15.0F));
        verify(bridge).sendPreviewLook(owner, visual, 0.0F, 15.0F, -15.0F);
    }

    @Test
    void numberedWorldPoolAllocatesLowestFreeWorldAndNeverDoubleAssigns() throws Exception {
        var server = MockBukkit.getMock();
        AtomicInteger resets = new AtomicInteger();
        KitEditorWorldPool pool = new KitEditorWorldPool(new KitEditorWorldPool.Preparer() {
            @Override public World prepare(int number) { return server.addSimpleWorld("Hydra_kit." + number); }
            @Override public void reset(World world) { resets.incrementAndGet(); }
        });
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID(), fourth = UUID.randomUUID();
        var one = pool.acquire(first);
        var two = pool.acquire(second);
        var three = pool.acquire(third);
        var four = pool.acquire(fourth);
        assertEquals(List.of("Hydra_kit.1", "Hydra_kit.2", "Hydra_kit.3", "Hydra_kit.4"),
            List.of(one.name(), two.name(), three.name(), four.name()));
        assertEquals(KitEditorWorldPool.State.TAKEN, pool.state(1));
        assertThrows(IllegalStateException.class, () -> pool.release(one, second));
        pool.release(two, second);
        assertEquals(1, resets.get());
        assertEquals(KitEditorWorldPool.State.AVAILABLE, pool.state(2));
        assertEquals(two, pool.acquire(UUID.randomUUID()));
        assertEquals(KitEditorWorldPool.State.TAKEN, pool.state(2));
    }

    @Test
    void failedWorldResetNeverMakesSlotAvailable() throws Exception {
        var server = MockBukkit.getMock();
        KitEditorWorldPool pool = new KitEditorWorldPool(new KitEditorWorldPool.Preparer() {
            @Override public World prepare(int number) { return server.addSimpleWorld("Hydra_kit." + number); }
            @Override public void reset(World world) throws IOException { throw new IOException("test reset failed"); }
        });
        UUID owner = UUID.randomUUID();
        var one = pool.acquire(owner);
        assertThrows(IOException.class, () -> pool.release(one, owner));
        assertEquals(KitEditorWorldPool.State.RESETTING, pool.state(1));
        assertEquals("Hydra_kit.2", pool.acquire(UUID.randomUUID()).name());
    }

    @Test
    void existingNumberedWorldsInitializeAvailableWithoutUsingOldSharedWorld() throws Exception {
        var server = MockBukkit.getMock();
        KitEditorWorldPool pool = new KitEditorWorldPool(new KitEditorWorldPool.Preparer() {
            @Override public World prepare(int number) { return server.addSimpleWorld("Hydra_kit." + number); }
            @Override public void reset(World world) { }
        });
        pool.initialize(List.of(3, 1, 2));
        assertEquals(KitEditorWorldPool.State.AVAILABLE, pool.state(1));
        assertEquals("Hydra_kit.1", pool.acquire(UUID.randomUUID()).name());
        assertEquals("Hydra_kit.2", pool.acquire(UUID.randomUUID()).name());
        assertEquals("Hydra_kit.3", pool.acquire(UUID.randomUUID()).name());
        assertEquals("Hydra_kit.4", pool.acquire(UUID.randomUUID()).name());
    }

    @Test
    void oldEditorConfigMigratesWithoutTouchingOtherSettings() {
        var config = new YamlConfiguration();
        config.set("kit-editor.map-room.world", "legacy-map");
        config.set("kit-editor.map-room.spawn.x", 13.5);
        config.set("kit-editor.room.world", "legacy-room");
        config.set("combat-master.transition.golden-apples", 8);
        assertTrue(KitEditorService.RoomLayout.migrateLegacyLayout(config));
        assertEquals(3, config.getInt("kit-editor.map-room.layout-version"));
        assertFalse(config.isSet("kit-editor.map-room.world"));
        assertFalse(config.isSet("kit-editor.map-room.spawn.x"));
        assertFalse(config.isSet("kit-editor.map-room.mannequin.y"));
        assertFalse(config.isSet("kit-editor.room.world"));
        assertEquals(8, config.getInt("combat-master.transition.golden-apples"));
        assertFalse(KitEditorService.RoomLayout.migrateLegacyLayout(config));
    }

    @Test
    void hubCommandCancelsAndRestoresOriginalInventoryWithoutSaving() {
        var server = MockBukkit.getMock();
        var hub = server.addSimpleWorld("KitEditorCommandHub");
        var player = server.addPlayer();
        player.teleport(new Location(hub, 1, 65, 1));
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        KitEditorService editor = editingService(player);
        assertTrue(editor.begin(player, BridgeKitDefinition.ID));
        assertFalse(editor.beforeCommand(player, "hub"));
        assertFalse(editor.isEditing(player.getUniqueId()));
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
        assertEquals(hub, player.getWorld());
        assertFalse(editor.isRecovering(player.getUniqueId()));
        assertEquals(0, editor.layout(player.getUniqueId(), BridgeKitDefinition.ID).get("sword"));
    }

    @Test
    void editorBlocksItemLeakageContainersAndDamage() {
        var server = MockBukkit.getMock();
        var hub = server.addSimpleWorld("KitEditorSafetyHub");
        var player = server.addPlayer();
        player.teleport(new Location(hub, 1, 65, 1));
        KitEditorService editor = editingService(player);
        assertTrue(editor.begin(player, BridgeKitDefinition.ID));
        var dropped = player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.IRON_SWORD));
        var drop = new PlayerDropItemEvent(player, dropped);
        editor.onDrop(drop);
        assertTrue(drop.isCancelled());
        var pickup = new EntityPickupItemEvent(player, dropped, 0);
        editor.onPickup(pickup);
        assertTrue(pickup.isCancelled());
        Inventory chest = org.mockito.Mockito.mock(Inventory.class);
        when(chest.getType()).thenReturn(InventoryType.CHEST);
        InventoryOpenEvent open = org.mockito.Mockito.mock(InventoryOpenEvent.class);
        when(open.getPlayer()).thenReturn(player);
        when(open.getInventory()).thenReturn(chest);
        editor.onOpen(open);
        verify(open).setCancelled(true);
        var damage = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 20.0D);
        editor.onDamage(damage);
        assertTrue(damage.isCancelled());
        editor.cancel(player);
    }

    @Test
    void queuedPlayerCannotEnterAndRecoveryPendingBlocksCommands() throws Exception {
        var server = MockBukkit.getMock();
        var hub = server.addSimpleWorld("KitEditorQueueHub");
        var player = server.addPlayer();
        player.teleport(new Location(hub, 1, 65, 1));
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        QueueService queue = mock(QueueService.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.queueService()).thenReturn(queue);
        when(queue.isLobbyWorld(player)).thenReturn(true);
        when(queue.isPlayerQueued(player.getUniqueId())).thenReturn(true);
        KitEditorService editor = new KitEditorService(plugin);
        assertFalse(editor.begin(player, BridgeKitDefinition.ID));
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
        assertFalse(editor.isRecovering(player.getUniqueId()));
        Path pending = temp.resolve("kit-editor/recovery").resolve(player.getUniqueId() + ".yml");
        Files.createDirectories(pending.getParent());
        Files.writeString(pending, "recovery: pending");
        assertTrue(editor.beforeCommand(player, "rejoin"));
    }

    private KitEditorService editingService(org.bukkit.entity.Player... players) {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        QueueService queue = mock(QueueService.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("KitEditorServiceTest"));
        when(plugin.queueService()).thenReturn(queue);
        for (var player : players) when(queue.isLobbyWorld(player)).thenReturn(true);
        return new KitEditorService(plugin);
    }

    private void interact(KitEditorService editor, org.bukkit.entity.Player player, int x, int z) {
        Interaction hitbox = player.getWorld().getEntitiesByClass(Interaction.class).stream()
            .filter(entity -> Math.abs(entity.getLocation().getX() - x - 0.5) < 0.1
                && Math.abs(entity.getLocation().getZ() - z - 0.5) < 0.1)
            .findFirst().orElseThrow();
        editor.onInteractEntity(new PlayerInteractEntityEvent(player, hitbox, EquipmentSlot.HAND));
    }

    private KitEditorService editor() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        return new KitEditorService(plugin);
    }
}
