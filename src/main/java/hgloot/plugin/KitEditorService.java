package hgloot.plugin;

import hgloot.plugin.util.HydraSounds;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.Structure;
import org.bukkit.util.Vector;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.bukkit.generator.WorldInfo;
import java.util.Random;

/** Real-inventory editor; only semantic slot mappings are persisted as kit preferences. */
public final class KitEditorService implements Listener {
    private final JavaPlugin plugin;
    private TransitionService transitions;
    private final RoomLayout room;
    private final KitEditorMannequin mannequins;
    private final KitEditorWorldPool worlds;
    private final Map<String, KitDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> pendingVoidReturns = new HashSet<>();
    private final Path layoutDir;
    private final Path recoveryDir;
    private Structure mapStructure;

    public KitEditorService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.room = RoomLayout.from(plugin);
        this.mannequins = new KitEditorMannequin(plugin);
        this.worlds = new KitEditorWorldPool(new KitEditorWorldPool.Preparer() {
            @Override public World prepare(int number) throws IOException { return prepareEditorWorld(number); }
            @Override public void reset(World editorWorld) throws IOException { resetEditorWorld(editorWorld); }
        });
        this.layoutDir = plugin.getDataFolder().toPath().resolve("kit-editor/layouts");
        this.recoveryDir = plugin.getDataFolder().toPath().resolve("kit-editor/recovery");
        register(new BridgeKitDefinition());
        register(new BossRushKitDefinition());
        initializeEditorWorlds();
    }

    public void register(KitDefinition definition) {
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate kit ID: " + definition.id());
        }
    }

    public void setTransitionService(TransitionService transitions) {
        this.transitions = transitions;
    }

    /** Presentation wrapper; the existing authoritative entry runs at full black. */
    public boolean beginWithTransition(Player player, String kitId) {
        if (transitions == null) return begin(player, kitId);
        if (!canEnter(player) || isEditing(player.getUniqueId()) || isRecovering(player.getUniqueId())) {
            return begin(player, kitId);
        }
        player.closeInventory();
        return transitions.transition(player, TransitionService.Preset.NORMAL, () -> begin(player, kitId));
    }

    public boolean isEditing(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isRecovering(UUID playerId) {
        return Files.exists(recoveryFile(playerId));
    }

    public Map<String, Integer> layout(UUID playerId, String kitId) {
        KitDefinition definition = definitions.get(kitId);
        if (definition == null) throw new IllegalArgumentException("Unknown kit ID: " + kitId);
        File file = layoutDir.resolve(playerId + ".yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Integer> saved = new LinkedHashMap<>();
        String root = "kits." + kitId + ".slots";
        if (yaml.isConfigurationSection(root)) {
            for (String key : yaml.getConfigurationSection(root).getKeys(false)) {
                if (yaml.isInt(root + "." + key)) saved.put(key, yaml.getInt(root + "." + key));
            }
        }
        return KitLayout.normalize(definition, saved);
    }

    public int bridgeArrowSlot(UUID playerId) {
        return layout(playerId, BridgeKitDefinition.ID).get(BridgeKitDefinition.ARROW);
    }

    public void equipBridgeKit(PlayerInventory inventory, UUID playerId, Material teamBlock) {
        KitLayout.place(inventory, BridgeKitDefinition.items(teamBlock), layout(playerId, BridgeKitDefinition.ID));
    }

    public void equipBossRushKit(PlayerInventory inventory, UUID playerId) {
        KitLayout.place(inventory, BossRushService.standardKitItems(), layout(playerId, BossRushKitDefinition.ID));
    }

    public boolean begin(Player player, String kitId) {
        KitDefinition definition = definitions.get(kitId);
        if (definition == null) return false;
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return false;
        if (!canEnter(player)) {
            player.sendMessage("You can only edit a kit from Hub or GameHub while not queued or in a match.");
            return false;
        }
        if (Files.exists(recoveryFile(id))) {
            recover(player);
            player.sendMessage("Your previous kit edit was recovered. Open Kit Editor again.");
            return false;
        }
        if (!saveRecovery(player)) {
            player.sendMessage("Kit Editor could not safely save your current state. No items were changed.");
            return false;
        }
        KitEditorWorldPool.Slot slot = null;
        try {
            slot = worlds.acquire(id);
            World editorWorld = slot.world;
            plugin.getLogger().info("[KitEditor] world=" + slot.name() + " state=TAKEN owner=" + player.getName());
            Session session = new Session(definition, slot, new ArrayList<>(), player.getScoreboard());
            sessions.put(id, session);
            player.closeInventory();
            clearForEditing(player);
            KitLayout.place(player.getInventory(), definition.previewItems(), layout(id, kitId));
            player.getInventory().setArmorContents(definition.previewArmor());
            spawnLabels(session);
            player.setGameMode(GameMode.ADVENTURE);
            player.setInvulnerable(true);
            player.setCanPickupItems(false);
            Location requested = room.spawn(editorWorld);
            if (!teleportInternally(player, requested)) throw new IllegalStateException("Editor teleport failed");
            Location arrived = player.getLocation();
            plugin.getLogger().info("[KitEditor] entry player=" + player.getName() + " world=" + slot.name()
                + " requested=" + formatLocation(requested) + " actual=" + formatLocation(arrived));
            if (!sameEntryLocation(requested, arrived)) {
                throw new IllegalStateException("Editor teleport destination was changed: " + formatLocation(arrived));
            }
            applyEditorSidebar(player, definition);
            session.preview = mannequins.spawn(player, room.mannequin(editorWorld));
            mannequins.update(player, session.preview, definition);
            mannequins.look(player, session.preview);
            player.updateInventory();
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not start kit edit for " + id, exception);
            if (sessions.containsKey(id)) end(player, false);
            else if (!recover(player)) {
                plugin.getLogger().severe("Kit Editor startup recovery remains on disk for " + id);
            }
            if (slot != null && !sessions.containsKey(id)
                && slot.state() == KitEditorWorldPool.State.TAKEN && id.equals(slot.owner())
                && !slot.world.equals(player.getWorld())) {
                releaseWorld(slot, id);
            }
            player.sendMessage("Kit Editor could not start. Please try again or contact staff if your state was not restored.");
            return false;
        }
    }

    private boolean canEnter(Player player) {
        if (!player.isOnline() || player.isDead()) return false;
        HGLootPlugin hydra = plugin instanceof HGLootPlugin value ? value : null;
        if (hydra == null || hydra.queueService() == null) return false;
        UUID id = player.getUniqueId();
        if (!hydra.queueService().isLobbyWorld(player)
            && (hydra.gameHubService() == null || !hydra.gameHubService().isGameHub(player.getWorld()))) return false;
        if (hydra.gameHubService() != null && hydra.gameHubService().isFightPit(player.getLocation())) return false;
        return !hydra.queueService().isPlayerQueued(id)
            && !hydra.queueService().isPlayerCommittedToMatchStart(id)
            && !hydra.queueService().isPlayerInHydraMatchOrSpectating(id)
            && (hydra.minigameRouletteService() == null || !hydra.minigameRouletteService().isQueued(id))
            && (hydra.spleefService() == null || !hydra.spleefService().isInSpleef(id))
            && (hydra.voidRouletteService() == null || !hydra.voidRouletteService().isInVoidRoulette(id))
            && (hydra.bridgeService() == null || !hydra.bridgeService().isInBridge(id))
            && (hydra.bossRushService() == null || !hydra.bossRushService().ownsPlayer(id));
    }

    public void confirm(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (transitions != null && transitions.isTransitioning(player.getUniqueId())) return;
        if (!empty(player.getItemOnCursor())) {
            HydraSounds.playUi(player, HydraSounds.UiCue.UNAVAILABLE);
            player.sendMessage("Place all kit items in your inventory before confirming.");
            return;
        }
        Optional<Map<String, Integer>> captured = KitLayout.capture(session.definition(),
            player.getInventory().getStorageContents(), player.getInventory().getItemInOffHand());
        if (captured.isEmpty()) {
            HydraSounds.playUi(player, HydraSounds.UiCue.UNAVAILABLE);
            player.sendMessage("Kit items or amounts changed. Restore all standard items before confirming.");
            return;
        }
        if (!saveLayout(player.getUniqueId(), session.definition().id(), captured.get())) {
            HydraSounds.playUi(player, HydraSounds.UiCue.UNAVAILABLE);
            player.sendMessage("Your layout could not be saved. The editor remains open.");
            return;
        }
        HydraSounds.playUi(player, HydraSounds.UiCue.CONFIRM);
        endWithTransition(player, true);
    }

    public void cancel(Player player) {
        if (!isEditing(player.getUniqueId())
            || (transitions != null && transitions.isTransitioning(player.getUniqueId()))) return;
        HydraSounds.playUi(player, HydraSounds.UiCue.CANCEL);
        endWithTransition(player, false);
    }

    private void endWithTransition(Player player, boolean confirmed) {
        if (!isEditing(player.getUniqueId())) return;
        if (transitions == null) {
            end(player, confirmed);
            return;
        }
        transitions.transition(player, TransitionService.Preset.NORMAL, () -> end(player, confirmed));
    }

    public void reset(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setItemInOffHand(null);
        KitLayout.place(player.getInventory(), session.definition().previewItems(),
            KitLayout.normalize(session.definition(), null));
        player.getInventory().setArmorContents(session.definition().previewArmor());
        player.updateInventory();
        mannequins.update(player, session.preview, session.definition());
        HydraSounds.playUi(player, HydraSounds.UiCue.RESET);
        player.sendMessage(session.definition().displayName() + " kit reset to default. Confirm to save this layout.");
    }

    boolean saveLayout(UUID id, String kitId, Map<String, Integer> slots) {
        Path file = layoutDir.resolve(id + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        String root = "kits." + kitId + ".slots";
        yaml.set(root, null);
        slots.forEach((itemId, slot) -> yaml.set(root + "." + itemId, slot));
        return saveAtomic(yaml, file);
    }

    private boolean saveRecovery(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", player.getLocation().clone());
        yaml.set("game-mode", player.getGameMode().name());
        yaml.set("storage", Arrays.asList(cloneItems(player.getInventory().getStorageContents())));
        yaml.set("armor", Arrays.asList(cloneItems(player.getInventory().getArmorContents())));
        yaml.set("offhand", cloneItem(player.getInventory().getItemInOffHand()));
        yaml.set("cursor", cloneItem(player.getItemOnCursor()));
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("exhaustion", player.getExhaustion());
        yaml.set("level", player.getLevel());
        yaml.set("experience", player.getExp());
        yaml.set("allow-flight", player.getAllowFlight());
        yaml.set("flying", player.isFlying());
        yaml.set("invulnerable", player.isInvulnerable());
        yaml.set("collidable", player.isCollidable());
        yaml.set("can-pickup", player.getCanPickupItems());
        yaml.set("fire-ticks", player.getFireTicks());
        yaml.set("freeze-ticks", player.getFreezeTicks());
        yaml.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        return saveAtomic(yaml, recoveryFile(player.getUniqueId()));
    }

    private boolean saveAtomic(YamlConfiguration yaml, Path file) {
        Path temp = file.resolveSibling(file.getFileName() + ".new");
        try {
            Files.createDirectories(file.getParent());
            yaml.save(temp.toFile());
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save Kit Editor state: " + file, exception);
            return false;
        }
    }

    private void clearForEditing(Player player) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setFallDistance(0);
    }

    private void end(Player player, boolean confirmed) {
        pendingVoidReturns.remove(player.getUniqueId());
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        removeLabels(session);
        mannequins.remove(session.preview);
        if (!recover(player)) {
            plugin.getLogger().severe("Kit Editor recovery remains on disk for " + player.getUniqueId());
            player.getInventory().clear();
            player.setItemOnCursor(null);
            sessions.put(player.getUniqueId(), session);
        } else {
            releaseWorld(session.slot, player.getUniqueId());
            restoreSidebar(player, session);
            player.sendMessage(confirmed ? session.definition().displayName() + " kit layout saved." : "Kit edit cancelled.");
        }
    }

    private void releaseWorld(KitEditorWorldPool.Slot slot, UUID owner) {
        try {
            worlds.release(slot, owner);
            plugin.getLogger().info("[KitEditor] world=" + slot.name() + " state=AVAILABLE");
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Kit Editor world " + slot.name()
                + " failed to reset and remains unavailable", exception);
        }
    }

    private boolean recover(Player player) {
        Path file = recoveryFile(player.getUniqueId());
        if (!Files.exists(file)) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        Location location = yaml.getLocation("location");
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().severe("Kit Editor recovery location unavailable for " + player.getUniqueId());
            return false;
        }
        try {
            player.setItemOnCursor(null);
            player.closeInventory();
            player.getInventory().clear();
            player.setGameMode(GameMode.valueOf(yaml.getString("game-mode", "SURVIVAL")));
            player.setAllowFlight(yaml.getBoolean("allow-flight"));
            player.setFlying(yaml.getBoolean("flying"));
            player.setInvulnerable(yaml.getBoolean("invulnerable"));
            player.setCollidable(yaml.getBoolean("collidable", true));
            player.setCanPickupItems(yaml.getBoolean("can-pickup", true));
            if (!teleportInternally(player, location)) return false;
            player.getInventory().setStorageContents(readItems(yaml.getList("storage"), 36));
            player.getInventory().setArmorContents(readItems(yaml.getList("armor"), 4));
            player.getInventory().setItemInOffHand(yaml.getItemStack("offhand"));
            player.setItemOnCursor(yaml.getItemStack("cursor"));
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            for (Object value : yaml.getList("effects", List.of())) {
                if (value instanceof PotionEffect effect) player.addPotionEffect(effect);
            }
            player.setFoodLevel(yaml.getInt("food", 20));
            player.setSaturation((float) yaml.getDouble("saturation", 20));
            player.setExhaustion((float) yaml.getDouble("exhaustion", 0));
            player.setLevel(yaml.getInt("level", 0));
            player.setExp((float) yaml.getDouble("experience", 0));
            player.setHealth(Math.max(1.0D, Math.min(player.getMaxHealth(), yaml.getDouble("health", 20))));
            player.setFireTicks(yaml.getInt("fire-ticks"));
            player.setFreezeTicks(yaml.getInt("freeze-ticks"));
            player.updateInventory();
            player.saveData();
            Files.delete(file);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore Kit Editor state for " + player.getUniqueId(), exception);
            return false;
        }
    }

    private static ItemStack[] readItems(List<?> list, int length) {
        ItemStack[] items = new ItemStack[length];
        if (list == null) return items;
        for (int index = 0; index < Math.min(length, list.size()); index++) {
            if (list.get(index) instanceof ItemStack item) items[index] = item.clone();
        }
        return items;
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] items = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) items[index] = cloneItem(source[index]);
        return items;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private Path recoveryFile(UUID id) {
        return recoveryDir.resolve(id + ".yml");
    }

    private void initializeEditorWorlds() {
        File[] folders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (folders == null) return;
        List<Integer> numbers = new ArrayList<>();
        for (File folder : folders) {
            if (!folder.getName().matches("Hydra_kit\\.[1-9][0-9]*")) continue;
            try {
                numbers.add(Integer.parseInt(folder.getName().substring(KitEditorWorldPool.PREFIX.length())));
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Ignoring invalid Kit Editor world folder: " + folder.getName());
            }
        }
        numbers.sort(Integer::compareTo);
        for (int number : numbers) {
            try {
                worlds.initialize(List.of(number));
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Kit Editor world " + KitEditorWorldPool.PREFIX + number
                    + " could not be reset; it will not be offered to players", exception);
            }
        }
    }

    private World prepareEditorWorld(int number) throws IOException {
        String name = KitEditorWorldPool.PREFIX + number;
        World editorWorld = Bukkit.getWorld(name);
        if (editorWorld == null) {
            editorWorld = Bukkit.createWorld(new WorldCreator(name)
                .generator(new EmptyKitEditorGenerator(81)).generateStructures(false));
        }
        if (editorWorld == null) throw new IOException("Could not create Kit Editor world " + name);
        editorWorld.setPVP(false);
        editorWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        editorWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        editorWorld.setTime(6000);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 3; z++) editorWorld.getChunkAt(x, z).load();
        }
        resetEditorWorld(editorWorld);
        return editorWorld;
    }

    private Structure mapStructure() throws IOException {
        if (mapStructure != null) return mapStructure;
        try (InputStream stream = plugin.getResource("kit-editor/Kit.nbt")) {
            if (stream == null) throw new IOException("Packaged Kit Editor map is missing");
            mapStructure = Bukkit.getStructureManager().loadStructure(stream);
        }
        if (mapStructure == null) throw new IOException("Could not load packaged Kit Editor map");
        return mapStructure;
    }

    private void resetEditorWorld(World editorWorld) throws IOException {
        if (!editorWorld.getPlayers().isEmpty()) {
            throw new IOException("Kit Editor world " + editorWorld.getName() + " still has players");
        }
        for (org.bukkit.entity.Entity entity : editorWorld.getEntities()) entity.remove();
        Structure structure = mapStructure();
        if (structure.getSize().getBlockX() != 36 || structure.getSize().getBlockY() != 81
            || structure.getSize().getBlockZ() != 52) {
            throw new IOException("Unexpected Kit Editor map dimensions: " + structure.getSize());
        }
        structure.place(new Location(editorWorld, 0, room.pasteY(), 0), true,
            StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
        for (Control control : Control.values()) {
            if (room.post(editorWorld, control).getBlock().getType() != Material.WARPED_FENCE) {
                throw new IOException("Kit Editor " + control + " marker is not a warped fence");
            }
        }
        if (room.mannequinPost(editorWorld).getBlock().getType() != Material.WARPED_FENCE) {
            throw new IOException("Kit Editor mannequin marker is not a warped fence");
        }
        if (editorWorld.getBlockAt(64, 64, 64).getType() != Material.AIR
            || editorWorld.getBlockAt(64, -32, 64).getType() != Material.AIR) {
            throw new IOException("Kit Editor world " + editorWorld.getName() + " contains terrain outside the island");
        }
    }

    static int countIslandPosts(RoomLayout room, World editorWorld) {
        int count = 0;
        for (Control control : Control.values()) {
            if (room.post(editorWorld, control).getBlock().getType() == Material.WARPED_FENCE) count++;
        }
        if (room.mannequinPost(editorWorld).getBlock().getType() == Material.WARPED_FENCE) count++;
        return count;
    }

    private void spawnLabels(Session session) {
        World editorWorld = session.slot.world;
        TextDisplay header = editorWorld.spawn(room.header(editorWorld), TextDisplay.class);
        header.text(Component.text(session.definition().displayName() + " KIT EDITOR", NamedTextColor.AQUA));
        header.setBillboard(Display.Billboard.CENTER);
        header.setPersistent(false);
        session.displays().add(header.getUniqueId());
        for (Control control : Control.values()) {
            Location label = room.label(editorWorld, control);
            TextDisplay display = editorWorld.spawn(label, TextDisplay.class);
            display.text(Component.text(control.label(), control.color()));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            session.displays().add(display.getUniqueId());

            // Text displays have no usable click surface; this invisible hitbox sits on the text.
            Interaction hitbox = editorWorld.spawn(label.clone().subtract(0, 0.45, 0), Interaction.class);
            hitbox.setInteractionWidth(2.4F);
            hitbox.setInteractionHeight(1.15F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            session.displays().add(hitbox.getUniqueId());
            session.controls.put(hitbox.getUniqueId(), control);
        }
    }

    private void removeLabels(Session session) {
        for (UUID id : session.displays()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    private boolean teleportInternally(Player player, Location destination) {
        internalTeleports.add(player.getUniqueId());
        try {
            return player.teleport(destination);
        } finally {
            internalTeleports.remove(player.getUniqueId());
        }
    }

    private static boolean sameEntryLocation(Location requested, Location actual) {
        return requested.getWorld().equals(actual.getWorld())
            && requested.distanceSquared(actual) < 0.000001
            && Math.abs(requested.getYaw() - actual.getYaw()) < 0.01F
            && Math.abs(requested.getPitch() - actual.getPitch()) < 0.01F;
    }

    private static String formatLocation(Location location) {
        return location.getWorld().getName() + "(" + location.getX() + "," + location.getY() + ","
            + location.getZ() + "; yaw=" + location.getYaw() + ", pitch=" + location.getPitch() + ")";
    }

    public void shutdown() {
        pendingVoidReturns.clear();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) end(player, false);
            else {
                Session session = sessions.remove(id);
                removeLabels(session);
                mannequins.remove(session.preview);
                // The recovery snapshot remains authoritative; do not advertise the slot as available.
            }
        }
    }

    private void applyEditorSidebar(Player player, KitDefinition definition) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("kit_editor", Criteria.DUMMY,
            Component.text("KIT EDITOR", NamedTextColor.AQUA));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("Mode: " + definition.displayName()).setScore(3);
        objective.getScore("Editing Layout").setScore(2);
        objective.getScore("Confirm to save").setScore(1);
        player.setScoreboard(board);
    }

    private void restoreSidebar(Player player, Session session) {
        if (session.previousSidebar != null) player.setScoreboard(session.previousSidebar);
        if (plugin instanceof HGLootPlugin hydra && hydra.queueService() != null) {
            hydra.queueService().clearSidebarCache(player.getUniqueId());
            hydra.queueService().refreshSidebar(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isEditing(player.getUniqueId())) return;
        Session session = sessions.get(player.getUniqueId());
        boolean crafting = event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
        boolean playerSlot = event.getClickedInventory() instanceof PlayerInventory;
        ItemStack hotbar = event.getClick() == ClickType.NUMBER_KEY
            ? player.getInventory().getItem(event.getHotbarButton()) : null;
        if (!crafting || !playerSlot || !editorClickAllowed(session.definition, event.getClick(),
            event.getSlot(), event.getCurrentItem(), event.getCursor(), hotbar)) {
            event.setCancelled(true);
        }
        refreshPreviewNextTick(player);
    }

    static boolean editorClickAllowed(KitDefinition definition, ClickType click, int slot,
                                      ItemStack current, ItemStack cursor, ItemStack hotbar) {
        boolean storage = slot >= 0 && slot < KitLayout.STORAGE_SLOTS;
        boolean offhand = slot == KitLayout.OFFHAND_SLOT;
        if (!storage && !offhand) return false;
        // F inside the inventory is an InventoryClickEvent, not PlayerSwapHandItemsEvent.
        if (click == ClickType.SWAP_OFFHAND) return storage && allowedEditorOffhandItem(definition, current);
        if (click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.NUMBER_KEY) {
            return storage || allowedEditorOffhandItem(definition,
                click == ClickType.NUMBER_KEY ? hotbar : cursor);
        }
        return false;
    }

    static boolean allowedEditorOffhandItem(KitDefinition definition, ItemStack item) {
        if (empty(item)) return true;
        for (Map.Entry<String, ItemStack> entry : definition.previewItems().entrySet()) {
            if (definition.allowsOffhand(entry.getKey()) && item.isSimilar(entry.getValue())) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isEditing(player.getUniqueId())) return;
        Session session = sessions.get(player.getUniqueId());
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
            || event.getRawSlots().stream().anyMatch(raw -> {
                if (raw < event.getView().getTopInventory().getSize()) return true;
                int slot = event.getView().convertSlot(raw);
                if (slot >= 0 && slot < KitLayout.STORAGE_SLOTS) return false;
                return slot != KitLayout.OFFHAND_SLOT
                    || !allowedEditorOffhandItem(session.definition, event.getNewItems().get(raw));
            })) event.setCancelled(true);
        refreshPreviewNextTick(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isEditing(player.getUniqueId())
            && event.getInventory().getType() != InventoryType.CRAFTING) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (sessions.containsKey(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        Location to = event.getTo();
        if (session == null || to == null) return;
        if (session.slot.world.equals(to.getWorld()) && belowEditorIsland(to)) {
            event.setCancelled(true);
            scheduleVoidReturn(event.getPlayer(), session);
            return;
        }
        if (!session.slot.world.equals(to.getWorld()) || to.getX() < 0 || to.getX() >= 36
            || to.getZ() < 0 || to.getZ() >= 52
            || to.getY() >= room.pasteY() + 81) {
            event.setTo(room.spawn(session.slot.world));
        } else if (event.getFrom().getYaw() != to.getYaw() || event.getFrom().getPitch() != to.getPitch()) {
            mannequins.look(event.getPlayer(), session.preview, to);
        }
    }

    static boolean belowEditorIsland(Location location) {
        return location.getY() < 79.0;
    }

    private void scheduleVoidReturn(Player player, Session session) {
        UUID id = player.getUniqueId();
        if (!pendingVoidReturns.add(id)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!pendingVoidReturns.remove(id) || sessions.get(id) != session || !player.isOnline()
                || !session.slot.world.equals(player.getWorld())) return;
            if (teleportInternally(player, room.spawn(session.slot.world))) clearFallMomentum(player);
        });
    }

    private static void clearFallMomentum(Player player) {
        player.setVelocity(new Vector());
        player.setFallDistance(0.0F);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())
            && !internalTeleports.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isEditing(event.getPlayer().getUniqueId())) return;
        String[] pieces = event.getMessage().substring(1).split(" ", 2);
        if (transitions != null && safeExitCommand(pieces[0])) {
            event.setCancelled(true);
            String command = event.getMessage().substring(1);
            transitions.transition(event.getPlayer(), TransitionService.Preset.NORMAL, () -> {
                end(event.getPlayer(), false);
                if (!isEditing(event.getPlayer().getUniqueId()) && event.getPlayer().isOnline()) {
                    event.getPlayer().performCommand(command);
                }
            });
            return;
        }
        if (beforeCommand(event.getPlayer(), pieces[0])) event.setCancelled(true);
    }

    private static boolean safeExitCommand(String name) {
        return Set.of("hub", "leave", "menu", "settings", "rejoin")
            .contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** Returns true when the command is blocked; safe Hydra navigation cancels the edit first. */
    public boolean beforeCommand(Player player, String commandName) {
        if (!isEditing(player.getUniqueId())) {
            if (!isRecovering(player.getUniqueId())) return false;
            player.sendMessage("Your kit edit state is being restored. Please wait or contact staff.");
            return true;
        }
        String name = commandName.toLowerCase(java.util.Locale.ROOT);
        if (safeExitCommand(name)) {
            end(player, false);
            return false;
        }
        player.sendMessage("Finish or cancel your kit edit first.");
        return true;
    }

    private void refreshPreviewNextTick(Player player) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session session = sessions.get(id);
            if (session != null && player.isOnline()) mannequins.update(player, session.preview, session.definition());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) refreshPreviewNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerArmSwingEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) mannequins.swing(event.getPlayer(), session.preview,
            event.getHand() == EquipmentSlot.OFF_HAND);
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onDrop(PlayerDropItemEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isEditing(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onBreak(BlockBreakEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onPlace(BlockPlaceEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!allowedEditorOffhandItem(session.definition, player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
        refreshPreviewNextTick(player);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onConsume(PlayerItemConsumeEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onInteractEntity(PlayerInteractEntityEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Control control = session.controls.get(event.getRightClicked().getUniqueId());
        if (control == null) return;
        switch (control) {
            case RESET -> reset(event.getPlayer());
            case CONFIRM -> confirm(event.getPlayer());
            case CANCEL -> cancel(event.getPlayer());
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
            && teleportInternally(player, room.spawn(session.slot.world))) {
            pendingVoidReturns.remove(player.getUniqueId());
            clearFallMomentum(player);
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onPvp(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isEditing(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isEditing(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!isEditing(id)) return;
        end(event.getPlayer(), false);
        // If Paper cannot teleport a player during quit, retain the recovery snapshot but free
        // the world only after the disconnect has removed the player from it.
        if (sessions.containsKey(id)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) return;
                Session session = sessions.remove(id);
                if (session == null) return;
                removeLabels(session);
                mannequins.remove(session.preview);
                if (session.slot.world.getPlayers().isEmpty()) releaseWorld(session.slot, id);
            });
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (Files.exists(recoveryFile(id))) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline() && recover(event.getPlayer())) {
                    event.getPlayer().sendMessage("An unfinished kit edit was discarded and your inventory was restored.");
                }
            });
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorldChange(PlayerChangedWorldEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && !session.slot.world.equals(event.getPlayer().getWorld())) end(event.getPlayer(), false);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorldUnload(WorldUnloadEvent event) {
        KitEditorWorldPool.Slot slot = worlds.forWorld(event.getWorld());
        if (slot != null) {
            for (UUID id : new ArrayList<>(sessions.keySet())) {
                Session session = sessions.get(id);
                if (session != null && session.slot == slot) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) end(player, false);
                }
            }
            worlds.invalidate(event.getWorld());
        }
    }

    private static final class Session {
        private final KitDefinition definition;
        private final KitEditorWorldPool.Slot slot;
        private final List<UUID> displays;
        private final Map<UUID, Control> controls = new HashMap<>();
        private final Scoreboard previousSidebar;
        private KitEditorMannequin.Preview preview;

        private Session(KitDefinition definition, KitEditorWorldPool.Slot slot,
                        List<UUID> displays, Scoreboard previousSidebar) {
            this.definition = definition;
            this.slot = slot;
            this.displays = displays;
            this.previousSidebar = previousSidebar;
        }

        KitDefinition definition() { return definition; }
        List<UUID> displays() { return displays; }
    }

    enum Control {
        RESET("RESET TO DEFAULT\nRestore the default Bridge layout.", NamedTextColor.GOLD),
        CONFIRM("CONFIRM KIT EDIT\nSave your layout and return.", NamedTextColor.GREEN),
        CANCEL("CANCEL KIT EDIT\nDiscard changes and return.", NamedTextColor.RED);

        private final String label;
        private final NamedTextColor color;

        Control(String label, NamedTextColor color) {
            this.label = label;
            this.color = color;
        }

        String label() { return label; }
        NamedTextColor color() { return color; }
    }

    static final class EmptyKitEditorGenerator extends ChunkGenerator {
        private final double spawnY;

        EmptyKitEditorGenerator(double spawnY) { this.spawnY = spawnY; }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public boolean shouldGenerateStructures(@NotNull WorldInfo info, @NotNull Random random,
                                                          int chunkX, int chunkZ) { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            return new Location(world, 9, spawnY, 45, 180.0F, 0.0F);
        }
    }

    record RoomLayout(int pasteY, int mannequinX, int mannequinZ,
                      int resetX, int resetZ, int confirmX, int confirmZ, int cancelX, int cancelZ,
                      int postY, double labelY, double headerY) {
        static RoomLayout from(JavaPlugin plugin) {
            var config = plugin.getConfig();
            if (config == null) return defaults();
            String root = "kit-editor.map-room.";
            if (migrateLegacyLayout(config)) plugin.saveConfig();
            return new RoomLayout(config.getInt(root + "paste-y", 50),
                config.getInt(root + "mannequin.x", 9), config.getInt(root + "mannequin.z", 37),
                config.getInt(root + "controls.reset.x", 5), config.getInt(root + "controls.reset.z", 45),
                config.getInt(root + "controls.confirm.x", 3), config.getInt(root + "controls.confirm.z", 42),
                config.getInt(root + "controls.cancel.x", 5), config.getInt(root + "controls.cancel.z", 39),
                config.getInt(root + "post-y", 31), config.getDouble(root + "label-y", 33.35),
                config.getDouble(root + "header-y", 35.7));
        }

        static boolean migrateLegacyLayout(FileConfiguration config) {
            String root = "kit-editor.map-room.";
            if (config.getInt(root + "layout-version", 0) >= 3) return false;
            config.set(root + "layout-version", 3);
            config.set(root + "world", null);
            config.set(root + "cell-spacing", null);
            config.set(root + "spawn", null);
            config.set(root + "mannequin.y", null);
            config.set(root + "mannequin.yaw", null);
            config.set("kit-editor.room", null);
            return true;
        }

        static RoomLayout defaults() {
            return new RoomLayout(50, 9, 37, 5, 45, 3, 42, 5, 39, 31, 33.35, 35.7);
        }

        Location spawn(World world) {
            return new Location(world, 9, 81, 45, 180.0F, 0.0F);
        }

        Location mannequin(World world) {
            return new Location(world, mannequinX + 0.5, 82.0, mannequinZ + 0.5, 0.0F, 0.0F);
        }

        Location mannequinPost(World world) {
            return new Location(world, mannequinX, pasteY + postY, mannequinZ);
        }

        Location header(World world) {
            return new Location(world, mannequinX + 0.5, pasteY + headerY, mannequinZ + 0.5);
        }

        Location post(World world, Control control) {
            int x = switch (control) {
                case RESET -> resetX;
                case CONFIRM -> confirmX;
                case CANCEL -> cancelX;
            };
            int z = switch (control) {
                case RESET -> resetZ;
                case CONFIRM -> confirmZ;
                case CANCEL -> cancelZ;
            };
            return new Location(world, x, pasteY + postY, z);
        }

        Location label(World world, Control control) {
            return post(world, control).add(0.5, labelY - postY, 0.5);
        }
    }
}
