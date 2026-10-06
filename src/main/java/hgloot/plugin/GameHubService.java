package hgloot.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.jetbrains.annotations.NotNull;

/** Owns the shared void staging world used by queued and between-round players. */
public final class GameHubService implements Listener {
    static final String WORLD_NAME = "Hydra-GameHub";
    private static final String STRUCTURE_RESOURCE = "gamehub/waiting_lobby.nbt";
    private static final String TILE_RESOURCE_ROOT = "gamehub/waiting_lobby_tiles8/";
    private static final int TILE_SIZE = 8;
    private static final int TILE_COLUMNS = 17;
    private static final int TILE_ROWS = 10;
    private static final int PASTE_X = -65;
    private static final int PASTE_Y = 0;
    private static final int PASTE_Z = -38;
    private static final int SPAWN_X = 38;
    private static final int SPAWN_Y = 63;
    private static final int SPAWN_Z = -1;
    private static final int FIGHT_PIT_RESPAWN_X = -22;
    private static final int FIGHT_PIT_RESPAWN_Y = 62;
    private static final int FIGHT_PIT_RESPAWN_Z = 0;
    static final double FIGHT_PIT_Y = 58.0D;
    private static final int PIT_RESPAWN_CENTER_X = -31;
    private static final int PIT_RESPAWN_CENTER_Z = -3;
    private static final int PIT_RESPAWN_RADIUS = 18;
    private static final double OWNERSHIP_TAG_HEIGHT = 2.85D;
    private static final int TILES_PER_TICK = 4;
    private static final String VERSION_MARKER = ".hydra-gamehub-version";

    private final HGLootPlugin plugin;
    private World world;
    private BukkitTask placementTask;
    private BukkitTask ownershipTagTask;
    private boolean ready;
    private final Map<UUID, FightPitState> fightPitStates = new HashMap<>();
    private final Map<UUID, TextDisplay> ownershipTags = new HashMap<>();
    private final Map<UUID, String> ownershipTagLabels = new HashMap<>();

    public GameHubService(HGLootPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ownershipTagTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOwnershipTags, 5L, 5L);
        try {
            String resourceHash = resourceHash(STRUCTURE_RESOURCE);
            Path worldFolder = Bukkit.getWorldContainer().toPath().resolve(WORLD_NAME).normalize();
            Path marker = worldFolder.resolve(VERSION_MARKER);
            if (Files.isRegularFile(marker) && resourceHash.equals(Files.readString(marker).trim())) {
                world = createWorld();
                ready = world != null;
                if (ready) configureWorld(world);
                plugin.getLogger().info("[GameHub] READY existing world=" + WORLD_NAME + " asset=" + resourceHash);
                return;
            }
            if (Files.exists(worldFolder)) {
                World loaded = Bukkit.getWorld(WORLD_NAME);
                if (loaded != null && !Bukkit.unloadWorld(loaded, false)) {
                    throw new IOException("Could not unload stale game hub world");
                }
                deleteWorldFolder(worldFolder);
            }
            world = createWorld();
            if (world == null) throw new IOException("Bukkit failed to create game hub world");
            configureWorld(world);
            placeIncrementally(resourceHash, marker);
        } catch (Exception ex) {
            ready = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[GameHub] startup failed", ex);
        }
    }

    public void shutdown() {
        if (placementTask != null) placementTask.cancel();
        placementTask = null;
        if (ownershipTagTask != null) ownershipTagTask.cancel();
        ownershipTagTask = null;
        for (UUID playerId : List.copyOf(fightPitStates.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) leaveFightPit(player);
        }
        ownershipTags.values().forEach(display -> {
            if (display != null && display.isValid()) display.remove();
        });
        ownershipTags.clear();
        ownershipTagLabels.clear();
    }

    public boolean enter(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (!ready || world == null) {
            player.sendMessage(org.bukkit.ChatColor.YELLOW + "The game lobby is still preparing.");
            return false;
        }
        player.closeInventory();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0.0F);
        player.setFireTicks(0);
        boolean teleported = player.teleport(spawn());
        if (teleported) {
            player.setCollidable(false);
            plugin.menuService().preparePlayerForHub(player);
        }
        return teleported;
    }

    /** Allows callers to reject a presented handoff before showing a black screen. */
    public boolean isReadyForEntry() {
        return ready && world != null;
    }

    public boolean isGameHub(World candidate) {
        return candidate != null && WORLD_NAME.equals(candidate.getName());
    }

    public boolean isFightPit(Location location) {
        return location != null && isGameHub(location.getWorld()) && location.getY() < FIGHT_PIT_Y;
    }

    public List<FightPitLeader> fightPitKillLeaders(int limit) {
        if (limit <= 0) return List.of();
        return fightPitStates.entrySet().stream()
            .filter(entry -> entry.getValue().kills() > 0)
            .map(entry -> {
                Player player = Bukkit.getPlayer(entry.getKey());
                return player == null || !player.isOnline() || !isGameHub(player.getWorld())
                    ? null
                    : new FightPitLeader(player.getName(), entry.getValue().kills());
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparingInt(FightPitLeader::kills).reversed()
                .thenComparing(FightPitLeader::playerName, String.CASE_INSENSITIVE_ORDER))
            .limit(limit)
            .toList();
    }

    private World createWorld() {
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new EmptyGenerator(SPAWN_Y));
        creator.generateStructures(false);
        return Bukkit.createWorld(creator);
    }

    private void configureWorld(World target) {
        target.setAutoSave(true);
        target.setSpawnLocation(SPAWN_X, SPAWN_Y, SPAWN_Z);
        target.setTime(6000L);
        target.setStorm(false);
        target.setThundering(false);
        target.setPVP(true);
        target.setDifficulty(Difficulty.NORMAL);
        target.setGameRule(GameRules.ADVANCE_TIME, false);
        target.setGameRule(GameRules.ADVANCE_WEATHER, false);
        target.setGameRule(GameRules.SPAWN_MOBS, false);
        target.setGameRule(GameRules.KEEP_INVENTORY, true);
        target.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        target.getWorldBorder().setCenter(0.0D, 0.0D);
        target.getWorldBorder().setSize(170.0D);
    }

    private void placeIncrementally(String resourceHash, Path marker) throws IOException {
        Path tileDirectory = plugin.getDataFolder().toPath().resolve("gamehub").resolve("waiting_lobby_tiles8");
        Files.createDirectories(tileDirectory);
        List<Tile> tiles = new ArrayList<>(TILE_COLUMNS * TILE_ROWS);
        for (int tileX = 0; tileX < TILE_COLUMNS; tileX++) {
            for (int tileZ = 0; tileZ < TILE_ROWS; tileZ++) {
                String name = "tile_" + tileX + "_" + tileZ + ".nbt";
                Path target = tileDirectory.resolve(name);
                copyBundled(TILE_RESOURCE_ROOT + name, target);
                tiles.add(new Tile(tileX * TILE_SIZE, tileZ * TILE_SIZE, target));
            }
        }
        int[] cursor = {0};
        placementTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                for (int count = 0; count < TILES_PER_TICK && cursor[0] < tiles.size(); count++) {
                    Tile tile = tiles.get(cursor[0]++);
                    Structure structure = Bukkit.getStructureManager().loadStructure(tile.path().toFile());
                    if (structure == null) throw new IOException("Tile decoded to null: " + tile.path());
                    structure.place(new Location(world, PASTE_X + tile.x(), PASTE_Y, PASTE_Z + tile.z()),
                        true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
                }
                if (cursor[0] < tiles.size()) return;
                placementTask.cancel();
                placementTask = null;
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, resourceHash);
                world.save();
                ready = true;
                plugin.getLogger().info("[GameHub] READY world=" + WORLD_NAME + " tiles=" + tiles.size()
                    + " asset=" + resourceHash);
            } catch (Exception ex) {
                if (placementTask != null) placementTask.cancel();
                placementTask = null;
                ready = false;
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[GameHub] structure placement failed", ex);
            }
        }, 1L, 1L);
    }

    private void copyBundled(String resource, Path target) throws IOException {
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resource);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String resourceHash(String resource) throws IOException, NoSuchAlgorithmException {
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resource);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }

    private Location spawn() {
        return new Location(world, SPAWN_X + 0.5D, SPAWN_Y, SPAWN_Z + 0.5D, 90.0F, 0.0F);
    }

    private void deleteWorldFolder(Path folder) throws IOException {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path target = folder.toAbsolutePath().normalize();
        if (!target.getParent().equals(container) || !WORLD_NAME.equals(target.getFileName().toString())) {
            throw new IOException("Refusing unsafe game hub delete: " + target);
        }
        try (var walk = Files.walk(target)) {
            for (Path path : walk.sorted((left, right) -> right.compareTo(left)).toList()) Files.deleteIfExists(path);
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (isGameHub(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (isGameHub(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler public void onExplosion(EntityExplodeEvent event) {
        if (isGameHub(event.getLocation().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!isGameHub(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Player victim) || !isFightPit(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            victim.setFallDistance(0.0F);
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent combat)) return;
        Player attacker = attackingPlayer(combat);
        if (attacker == null || attacker.equals(victim) || !isFightPit(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isGameHub(player.getWorld())) return;
        event.setCancelled(true);
        player.setFoodLevel(20);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        if (isGameHub(to.getWorld()) && event.getPlayer().isCollidable()) {
            event.getPlayer().setCollidable(false);
        }
        boolean wasInPit = isFightPit(event.getFrom());
        boolean isInPit = isFightPit(to);
        if (!wasInPit && isInPit) enterFightPit(event.getPlayer());
        if (wasInPit && !isInPit) leaveFightPit(event.getPlayer());
        if (isGameHub(to.getWorld()) && to.getY() < -16.0D) event.getPlayer().teleport(spawn());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!fightPitStates.containsKey(victim.getUniqueId()) || !isGameHub(victim.getWorld())) return;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim) && isFightPit(killer.getLocation())
            && fightPitStates.containsKey(killer.getUniqueId())) {
            rewardFightPitKill(killer);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!fightPitStates.containsKey(playerId)) return;
        if (!isGameHub(event.getPlayer().getWorld())) {
            // A stale Fight Pit kit must not override another mode's respawn destination.
            fightPitStates.remove(playerId);
            return;
        }
        event.setRespawnLocation(new Location(world == null ? event.getPlayer().getWorld() : world,
            FIGHT_PIT_RESPAWN_X, FIGHT_PIT_RESPAWN_Y, FIGHT_PIT_RESPAWN_Z));
        Bukkit.getScheduler().runTask(plugin, () -> {
            leaveFightPit(event.getPlayer());
            if (isGameHub(event.getPlayer().getWorld())) event.getPlayer().setCollidable(false);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (isGameHub(event.getPlayer().getWorld())) {
            event.getPlayer().setCollidable(false);
        } else if (isGameHub(event.getFrom())) {
            event.getPlayer().setCollidable(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isGameHub(event.getPlayer().getWorld())) event.getPlayer().setCollidable(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!fightPitStates.containsKey(event.getPlayer().getUniqueId())) return;
        leaveFightPit(event.getPlayer());
        if (ready && world != null) event.getPlayer().teleport(spawn());
    }

    private void enterFightPit(Player player) {
        if (player == null || fightPitStates.containsKey(player.getUniqueId())) return;
        PlayerInventory inventory = player.getInventory();
        fightPitStates.put(player.getUniqueId(), new FightPitState(
            cloneItems(inventory.getStorageContents()),
            cloneItems(inventory.getArmorContents()),
            cloneItem(inventory.getItemInOffHand()),
            0
        ));
        equipBaseFightPitKit(player);
        heal(player);
        player.sendMessage(ChatColor.RED + "Entered the Fight Pit. Survive.");
        player.updateInventory();
    }

    private void leaveFightPit(Player player) {
        if (player == null) return;
        FightPitState state = fightPitStates.remove(player.getUniqueId());
        if (state == null) return;
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(cloneItems(state.storageContents()));
        inventory.setArmorContents(cloneItems(state.armorContents()));
        inventory.setItemInOffHand(cloneItem(state.offHand()));
        heal(player);
        player.updateInventory();
        refreshFightPitLeaderboards();
    }

    private void rewardFightPitKill(Player killer) {
        UUID killerId = killer.getUniqueId();
        FightPitState current = fightPitStates.get(killerId);
        if (current == null) return;
        int kills = current.kills() + 1;
        fightPitStates.put(killerId, current.withKills(kills));
        killer.getInventory().remove(Material.ARROW);
        killer.getInventory().addItem(new ItemStack(Material.ARROW, 10));
        killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
        if (kills % 2 == 0) upgradeRandomArmor(killer, false);
        if (kills % 5 == 0) upgradeRandomArmor(killer, true);
        heal(killer);
        refreshFightPitLeaderboards();
        killer.sendActionBar(Component.text("+1 Kill", NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, true));
    }

    private void upgradeRandomArmor(Player player, boolean netheriteUpgrade) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < armor.length; index++) {
            Material type = armor[index] == null ? Material.AIR : armor[index].getType();
            if ((!netheriteUpgrade && isIronArmor(type)) || (netheriteUpgrade && isDiamondArmor(type))) {
                candidates.add(index);
            }
        }
        if (candidates.isEmpty()) return;
        int index = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Material upgraded = netheriteUpgrade ? netheriteArmor(index) : diamondArmor(index);
        armor[index] = unbreakableItem(upgraded);
        player.getInventory().setArmorContents(armor);
        player.sendMessage(ChatColor.GREEN + "Fight Pit armor upgraded: " + formatMaterial(upgraded));
    }

    private boolean isIronArmor(Material material) {
        return material == Material.IRON_BOOTS || material == Material.IRON_LEGGINGS
            || material == Material.IRON_CHESTPLATE || material == Material.IRON_HELMET;
    }

    private boolean isDiamondArmor(Material material) {
        return material == Material.DIAMOND_BOOTS || material == Material.DIAMOND_LEGGINGS
            || material == Material.DIAMOND_CHESTPLATE || material == Material.DIAMOND_HELMET;
    }

    private Material diamondArmor(int armorIndex) {
        return switch (armorIndex) {
            case 0 -> Material.DIAMOND_BOOTS;
            case 1 -> Material.DIAMOND_LEGGINGS;
            case 2 -> Material.DIAMOND_CHESTPLATE;
            default -> Material.DIAMOND_HELMET;
        };
    }

    private Material netheriteArmor(int armorIndex) {
        return switch (armorIndex) {
            case 0 -> Material.NETHERITE_BOOTS;
            case 1 -> Material.NETHERITE_LEGGINGS;
            case 2 -> Material.NETHERITE_CHESTPLATE;
            default -> Material.NETHERITE_HELMET;
        };
    }

    private String formatMaterial(Material material) {
        String normalized = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return normalized.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + normalized.substring(1);
    }

    private void equipBaseFightPitKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[] {
            unbreakableItem(Material.IRON_BOOTS),
            unbreakableItem(Material.IRON_LEGGINGS),
            unbreakableItem(Material.IRON_CHESTPLATE),
            unbreakableItem(Material.IRON_HELMET)
        });
        inventory.setItemInOffHand(null);
        inventory.setItem(0, unbreakableItem(Material.IRON_SWORD));
        inventory.setItem(1, unbreakableItem(Material.BOW));
        inventory.setItem(2, new ItemStack(Material.ARROW, 10));
        inventory.setItem(3, new ItemStack(Material.GOLDEN_APPLE));
        player.updateInventory();
    }

    private ItemStack unbreakableItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void refreshFightPitLeaderboards() {
        for (Player online : Bukkit.getOnlinePlayers()) plugin.queueService().refreshSidebar(online);
    }

    private Location findFightPitSpawn() {
        if (world == null) return null;
        for (int attempt = 0; attempt < 96; attempt++) {
            int x = PIT_RESPAWN_CENTER_X + ThreadLocalRandom.current().nextInt(-PIT_RESPAWN_RADIUS, PIT_RESPAWN_RADIUS + 1);
            int z = PIT_RESPAWN_CENTER_Z + ThreadLocalRandom.current().nextInt(-PIT_RESPAWN_RADIUS, PIT_RESPAWN_RADIUS + 1);
            for (int y = (int) FIGHT_PIT_Y - 1; y >= 5; y--) {
                if (world.getBlockAt(x, y - 1, z).getType().isSolid()
                    && world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()) {
                    return new Location(world, x + 0.5D, y, z + 0.5D);
                }
            }
        }
        return new Location(world, -25.5D, 33.0D, -11.5D);
    }

    private Player attackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (!(event.getDamager() instanceof Projectile projectile)) return null;
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player ? player : null;
    }

    private void heal(Player player) {
        if (player == null || !player.isOnline()) return;
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = maxHealth == null ? 20.0D : maxHealth.getValue();
        if (!player.isDead()) player.setHealth(Math.max(1.0D, health));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    private void refreshOwnershipTags() {
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            if (player.isCollidable()) player.setCollidable(false);
        }
        if (plugin.queueService() == null) return;
        Set<UUID> visible = new HashSet<>();
        for (Player player : world.getPlayers()) {
            UUID playerId = player.getUniqueId();
            String label = plugin.queueService().gameHubOwnershipTag(playerId);
            if (label == null) {
                removeOwnershipTag(playerId);
                continue;
            }
            visible.add(playerId);
            TextDisplay display = ownershipTags.get(playerId);
            if (display == null || !display.isValid()) {
                display = world.spawn(player.getLocation().add(0.0D, OWNERSHIP_TAG_HEIGHT, 0.0D), TextDisplay.class, tag -> {
                    tag.setBillboard(TextDisplay.Billboard.CENTER);
                    tag.setGravity(false);
                    tag.setInvulnerable(true);
                    tag.setPersistent(false);
                    tag.setSeeThrough(true);
                    tag.setShadowed(true);
                    tag.setViewRange(32.0F);
                    tag.setTeleportDuration(4);
                });
                ownershipTags.put(playerId, display);
            }
            if (!label.equals(ownershipTagLabels.get(playerId))) {
                display.text(Component.text(label, ownershipTagColor(label)).decoration(TextDecoration.BOLD, true));
                ownershipTagLabels.put(playerId, label);
            }
            display.teleport(player.getLocation().add(0.0D, OWNERSHIP_TAG_HEIGHT, 0.0D));
        }
        for (UUID playerId : List.copyOf(ownershipTags.keySet())) {
            if (!visible.contains(playerId)) removeOwnershipTag(playerId);
        }
    }

    private NamedTextColor ownershipTagColor(String label) {
        return switch (label) {
            case "HG" -> NamedTextColor.GOLD;
            case "BR" -> NamedTextColor.AQUA;
            default -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private void removeOwnershipTag(UUID playerId) {
        TextDisplay display = ownershipTags.remove(playerId);
        ownershipTagLabels.remove(playerId);
        if (display != null && display.isValid()) display.remove();
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) clone[index] = cloneItem(source[index]);
        return clone;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private record Tile(int x, int z, Path path) {}

    private record FightPitState(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack offHand, int kills) {
        private FightPitState withKills(int updatedKills) {
            return new FightPitState(storageContents, armorContents, offHand, updatedKills);
        }
    }

    public record FightPitLeader(String playerName, int kills) {}

    private static final class EmptyGenerator extends ChunkGenerator {
        private final int spawnY;
        private EmptyGenerator(int spawnY) { this.spawnY = spawnY; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public boolean shouldGenerateStructures(@NotNull WorldInfo info, @NotNull java.util.Random random,
                                                          int chunkX, int chunkZ) { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, SPAWN_X + 0.5D, spawnY, SPAWN_Z + 0.5D);
        }
    }
}
