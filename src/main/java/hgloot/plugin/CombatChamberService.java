package hgloot.plugin;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Bogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Silverfish;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

@SuppressWarnings("deprecation")
public final class CombatChamberService implements Listener {
    private static final String VENOM_RING_RESOURCE = "combat_chamber/venom_ring.nbt";
    private static final String VENOM_RING_FILE = "venom_ring.nbt";
    private static final String VENOM_RING_LAST_FILE = "venom_ring_last.yml";
    private static final int REINFORCEMENT_CLUSTER_SIZE = 3;
    private static final int MIN_REINFORCEMENT_INTERVAL_TICKS = 20 * 2;
    private static final int SPAWN_CLUSTER_ROTATE_TICKS = 20 * 12;
    private static final double SPAWN_MIN_PLAYER_DISTANCE = 10.0D;
    private static final double SPAWN_MAX_PLAYER_DISTANCE = 36.0D;
    private static final double MONK_HEAL_RADIUS = 10.0D;
    private static final int WEB_LIFETIME_TICKS = 20 * 4;
    private static final int WEB_CAST_COOLDOWN_TICKS = 20 * 6;
    private static final int SPIKE_CAST_COOLDOWN_TICKS = 20 * 14;
    private static final int SPIKE_WARNING_TICKS = 20 * 2;
    private static final int LOST_MOB_GLOW_TICKS = 20 * 10;
    private static final double PLAYER_ENGAGEMENT_RADIUS = 24.0D;
    private static final int REINFORCEMENT_PROMPT_DELAY_TICKS = 20 * 2;
    private static final int REINFORCEMENT_CHOICE_TIMEOUT_TICKS = 20 * 15;
    private static final int NEXT_WAVE_DELAY_TICKS = 20 * 10;
    private static final int FINAL_RING_PROMPT_DELAY_TICKS = 20 * 3;
    private static final int FINAL_RING_CHOICE_TIMEOUT_TICKS = 20 * 60;
    private static final double WEB_CAST_RANGE = 24.0D;
    private static final double HAZARD_MIN_TARGET_OFFSET = 2.0D;
    private static final double HAZARD_MAX_TARGET_OFFSET = 4.0D;
    private static final String CHAMBER_SCOREBOARD_OBJECTIVE = "hydra_chamber";
    private static final int BURROWER_BURROW_INTERVAL_TICKS = 20 * 8;
    private static final int BURROWER_BURROW_DURATION_TICKS = 20 * 2;
    private static final int MOB_OUTSIDE_RECOVERY_TICKS = 20 * 4;
    private static final int TRANSITION_COOLDOWN_TICKS = 20 * 3;
    private static final int TRANSITION_WARNING_COOLDOWN_TICKS = 20 * 2;
    private static final double TRANSITION_HALF_X = 7.5D;
    private static final double TRANSITION_HALF_Y = 5.5D;
    private static final double TRANSITION_HALF_Z = 7.5D;
    private static final Vector TRANSITION_CENTER_ONE = new Vector(26.0D, 17.0D, 91.0D);
    private static final Vector TRANSITION_CENTER_TWO = new Vector(154.0D, 17.0D, 91.0D);
    private static final int WAVE_CLEAR_BASE_SILVER = 35;
    private static final int LEADER_KILL_PRESTIGE_XP = 45;
    private static final double LEADER_SHARD_FRAGMENT_CHANCE = 0.30D;
    private static final int FULL_CLEAR_PRESTIGE_XP = 90;
    private static final int FULL_CLEAR_SHARD_FRAGMENTS = 2;

    private final JavaPlugin plugin;
    private final VenomSpikeService venomSpikeService;
    private ProgressionService progressionService;
    private Structure venomRingStructure;
    private Set<BlockVector> venomRingStructureVoidPositions;
    private ActiveVenomRing activeVenomRing;

    public CombatChamberService(JavaPlugin plugin, VenomSpikeService venomSpikeService) {
        this.plugin = plugin;
        this.venomSpikeService = venomSpikeService;
    }

    public void setProgressionService(ProgressionService progressionService) {
        this.progressionService = progressionService;
    }

    public void shutdown() {
        if (activeVenomRing != null) {
            activeVenomRing.cleanup(false);
            activeVenomRing = null;
        }
    }

    public boolean testVenom(Player player) {
        if (player == null) {
            return false;
        }
        if (activeVenomRing != null) {
            activeVenomRing.cleanup(false);
            activeVenomRing = null;
        }

        Structure structure = loadVenomRing();
        if (structure == null) {
            player.sendMessage(ChatColor.RED + "[Combat Chamber] Failed to load Venom Ring.");
            return false;
        }
        Location origin = resolvePlacement(player);
        if (origin == null || origin.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "[Combat Chamber] Could not find a placement target.");
            return false;
        }
        try {
            List<BlockState> ringSnapshots = snapshotRegion(origin, structure.getSize());
            structure.place(origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
            clearStructureVoidInterior(origin, loadVenomRingStructureVoidPositions());
            saveLastVenomRing(origin, structure.getSize());
            ActiveVenomRing encounter = new ActiveVenomRing(origin, structure.getSize(), ringSnapshots);
            activeVenomRing = encounter;
            encounter.start(player);
            player.sendMessage(ChatColor.GREEN + "[Combat Chamber] Venom Ring spawned.");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("[Combat Chamber] Failed placing Venom Ring: " + ex.getMessage());
            player.sendMessage(ChatColor.RED + "[Combat Chamber] Failed to load Venom Ring.");
            return false;
        }
    }

    public boolean clearVenom(Player player) {
        if (player == null) {
            return false;
        }
        if (activeVenomRing != null) {
            activeVenomRing.cleanup(false, true);
            activeVenomRing = null;
            clearLastVenomRing();
            player.sendMessage(ChatColor.GREEN + "[Combat Chamber] Venom Ring cleared.");
            return true;
        }

        StoredRing stored = loadLastVenomRing();
        if (stored == null) {
            player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No active Ring found.");
            return false;
        }
        Set<BlockVector> voidPositions = loadVenomRingStructureVoidPositions();
        if (voidPositions.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No active Ring found.");
            return false;
        }
        clearStructureVoidInterior(stored.origin(), voidPositions);
        clearLastVenomRing();
        player.sendMessage(ChatColor.GREEN + "[Combat Chamber] Venom Ring cleared.");
        return true;
    }

    public boolean setVenomWave(Player player, int waveNumber) {
        if (player == null) {
            return false;
        }
        if (activeVenomRing == null) {
            player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No active Ring found.");
            return false;
        }
        VenomWave wave = VenomWave.fromNumber(waveNumber);
        if (wave == null) {
            player.sendMessage(ChatColor.RED + "[Combat Chamber] Use wave 1-4.");
            return false;
        }
        activeVenomRing.forceWave(wave);
        player.sendMessage(ChatColor.DARK_AQUA + "[Combat Chamber] " + ChatColor.GREEN + "Venom wave set to " + waveNumber + ".");
        return true;
    }

    public boolean chooseVenomReinforcement(Player player, boolean stay) {
        if (player == null) {
            return false;
        }
        if (activeVenomRing == null) {
            player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No active Ring found.");
            return false;
        }
        return activeVenomRing.chooseReinforcement(player, stay);
    }

    public boolean chooseFinalRingTransition(Player player, boolean stay) {
        if (player == null) {
            return false;
        }
        if (activeVenomRing == null) {
            player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No active Ring found.");
            return false;
        }
        return activeVenomRing.chooseFinalRingTransition(player, stay);
    }

    public boolean isPlayerInActiveRing(Player player) {
        if (player == null || activeVenomRing == null || player.isDead()) {
            return false;
        }
        return !activeVenomRing.escapedPlayers.contains(player.getUniqueId())
            && activeVenomRing.contains(player.getLocation());
    }

    /** Spawns one bounded reinforcement set using the authoritative Combat Chamber wave role lists. */
    public List<UUID> spawnBossRushWave(Location center, int waveIndex, int count) {
        if (center == null || center.getWorld() == null || count <= 0) return List.of();
        VenomWave wave = VenomWave.values()[Math.floorMod(waveIndex, VenomWave.values().length)];
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i / count) + ThreadLocalRandom.current().nextDouble(-0.2D, 0.2D);
            Location desired = center.clone().add(Math.cos(angle) * 17.0D, 0.0D, Math.sin(angle) * 17.0D);
            Location at = bossRushSafeSpawn(desired, center);
            VenomMobRole role = wave.roles.get(i % wave.roles.size());
            Mob mob = switch (role) {
                case BURROWER -> center.getWorld().spawn(at, Silverfish.class);
                case WAVE_ONE_BOGGED, BILE_ARCHER -> center.getWorld().spawn(at, Bogged.class);
                case VENOM_SKIRMISHER -> center.getWorld().spawn(at, CaveSpider.class);
                case WEB_CRAWLER -> center.getWorld().spawn(at, Spider.class);
                case ROT_BRUTE -> center.getWorld().spawn(at, Zombie.class);
            };
            mob.setCustomName(ChatColor.DARK_GREEN + "Combat Chamber Reinforcement");
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            spawned.add(mob.getUniqueId());
        }
        return List.copyOf(spawned);
    }

    private Location bossRushSafeSpawn(Location desired, Location fallback) {
        World world = desired.getWorld();
        int y = desired.getBlockY();
        Block floor = world.getBlockAt(desired.getBlockX(), y - 1, desired.getBlockZ());
        if (floor.getType().isSolid() && world.getBlockAt(desired.getBlockX(), y, desired.getBlockZ()).isPassable()
            && world.getBlockAt(desired.getBlockX(), y + 1, desired.getBlockZ()).isPassable()) {
            return new Location(world, desired.getBlockX() + 0.5D, y, desired.getBlockZ() + 0.5D);
        }
        return fallback.clone();
    }

    private Structure loadVenomRing() {
        if (venomRingStructure != null) {
            return venomRingStructure;
        }
        Path path = resolveVenomRingPath();
        if (path == null) {
            return null;
        }
        try {
            venomRingStructure = Bukkit.getStructureManager().loadStructure(path.toFile());
            plugin.getLogger().info("[Combat Chamber] Loaded Venom Ring NBT from " + path + ".");
            return venomRingStructure;
        } catch (IOException ex) {
            plugin.getLogger().warning("[Combat Chamber] Failed loading Venom Ring NBT: " + ex.getMessage());
            return null;
        }
    }

    private Path resolveVenomRingPath() {
        Path bundled = plugin.getDataFolder().toPath().resolve("combat_chamber").resolve(VENOM_RING_FILE);
        try {
            Files.createDirectories(bundled.getParent());
            try (InputStream in = plugin.getResource(VENOM_RING_RESOURCE)) {
                if (in == null) {
                    plugin.getLogger().warning("[Combat Chamber] Bundled Venom Ring resource is missing: " + VENOM_RING_RESOURCE);
                } else {
                    Files.copy(in, bundled, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[Combat Chamber] Failed refreshing bundled Venom Ring NBT: " + ex.getMessage());
        }
        if (Files.isRegularFile(bundled)) {
            return bundled;
        }
        return null;
    }

    private Location resolvePlacement(Player player) {
        Block target = player.getTargetBlockExact(120);
        if (target == null || target.getType().isAir()) {
            return player.getLocation().getBlock().getLocation();
        }
        Location place = target.getLocation();
        if (target.getType().isSolid()) {
            place.add(0.0D, 1.0D, 0.0D);
        }
        return place;
    }

    private List<BlockState> snapshotRegion(Location origin, BlockVector size) {
        List<BlockState> snapshots = new ArrayList<>();
        World world = origin.getWorld();
        if (world == null) {
            return snapshots;
        }
        int maxX = Math.max(1, size.getBlockX());
        int maxY = Math.max(1, size.getBlockY());
        int maxZ = Math.max(1, size.getBlockZ());
        int startX = origin.getBlockX();
        int startY = origin.getBlockY();
        int startZ = origin.getBlockZ();
        for (int x = 0; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                for (int z = 0; z < maxZ; z++) {
                    snapshots.add(world.getBlockAt(startX + x, startY + y, startZ + z).getState());
                }
            }
        }
        return snapshots;
    }

    private void restoreSnapshots(List<BlockState> snapshots) {
        for (BlockState state : snapshots) {
            state.update(true, false);
        }
    }

    private void clearStructureVoidInterior(Location origin, Set<BlockVector> voidPositions) {
        World world = origin.getWorld();
        if (world == null || voidPositions.isEmpty()) {
            return;
        }
        int startX = origin.getBlockX();
        int startY = origin.getBlockY();
        int startZ = origin.getBlockZ();
        for (BlockVector pos : voidPositions) {
            Block block = world.getBlockAt(startX + pos.getBlockX(), startY + pos.getBlockY(), startZ + pos.getBlockZ());
            Material type = block.getType();
            if (type == Material.BEDROCK || type == Material.BARRIER) {
                continue;
            }
            if (!type.isAir()) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private Set<BlockVector> loadVenomRingStructureVoidPositions() {
        if (venomRingStructureVoidPositions != null) {
            return venomRingStructureVoidPositions;
        }
        Path path = resolveVenomRingPath();
        if (path == null) {
            venomRingStructureVoidPositions = Set.of();
            return venomRingStructureVoidPositions;
        }
        try {
            venomRingStructureVoidPositions = readStructureVoidPositions(path);
            plugin.getLogger().info("[Combat Chamber] Loaded " + venomRingStructureVoidPositions.size() + " Venom Ring structure_void clear positions.");
        } catch (IOException ex) {
            plugin.getLogger().warning("[Combat Chamber] Failed reading Venom Ring structure_void positions: " + ex.getMessage());
            venomRingStructureVoidPositions = Set.of();
        }
        return venomRingStructureVoidPositions;
    }

    private Set<BlockVector> readStructureVoidPositions(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            NbtCompound root = NbtReader.readRootCompound(in);
            List<NbtCompound> palette = root.compoundList("palette");
            List<NbtCompound> blocks = root.compoundList("blocks");
            Set<Integer> structureVoidStates = new HashSet<>();
            for (int i = 0; i < palette.size(); i++) {
                if ("minecraft:structure_void".equals(palette.get(i).string("Name"))) {
                    structureVoidStates.add(i);
                }
            }
            if (structureVoidStates.isEmpty()) {
                return Set.of();
            }
            Set<BlockVector> positions = new HashSet<>();
            for (NbtCompound block : blocks) {
                if (!structureVoidStates.contains(block.intValue("state"))) {
                    continue;
                }
                List<Integer> pos = block.intList("pos");
                if (pos.size() >= 3) {
                    positions.add(new BlockVector(pos.get(0), pos.get(1), pos.get(2)));
                }
            }
            return positions;
        }
    }

    private void saveLastVenomRing(Location origin, BlockVector size) {
        Path file = venomRingLastPath();
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration config = new YamlConfiguration();
            config.set("world", origin.getWorld() == null ? "" : origin.getWorld().getName());
            config.set("x", origin.getBlockX());
            config.set("y", origin.getBlockY());
            config.set("z", origin.getBlockZ());
            config.set("size.x", size.getBlockX());
            config.set("size.y", size.getBlockY());
            config.set("size.z", size.getBlockZ());
            config.save(file.toFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("[Combat Chamber] Failed saving last Venom Ring placement: " + ex.getMessage());
        }
    }

    private StoredRing loadLastVenomRing() {
        Path file = venomRingLastPath();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        World world = Bukkit.getWorld(config.getString("world", ""));
        if (world == null) {
            return null;
        }
        Location origin = new Location(world, config.getInt("x"), config.getInt("y"), config.getInt("z"));
        BlockVector size = new BlockVector(
            Math.max(1, config.getInt("size.x", 1)),
            Math.max(1, config.getInt("size.y", 1)),
            Math.max(1, config.getInt("size.z", 1))
        );
        return new StoredRing(origin, size);
    }

    private void clearLastVenomRing() {
        try {
            Files.deleteIfExists(venomRingLastPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("[Combat Chamber] Failed clearing last Venom Ring placement file: " + ex.getMessage());
        }
    }

    private Path venomRingLastPath() {
        return plugin.getDataFolder().toPath().resolve("combat_chamber").resolve(VENOM_RING_LAST_FILE);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (activeVenomRing == null) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LivingEntity source = activeVenomRing.chamberDamageSource(event.getDamager());
        if (source != null && activeVenomRing.isChamberMob(target) && activeVenomRing.isChamberMob(source)) {
            event.setCancelled(true);
            return;
        }
        if (!activeVenomRing.isChamberMob(target)) {
            activeVenomRing.handleChamberMobDamage(event);
            return;
        }
        Player damager = damagingPlayer(event.getDamager());
        if (damager == null) {
            return;
        }
        activeVenomRing.addThreat(damager, event.getDamage());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (activeVenomRing == null) {
            return;
        }
        activeVenomRing.handleDeath(event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeVenomRing != null && activeVenomRing.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (activeVenomRing != null) {
            activeVenomRing.handleTransitionMove(event.getPlayer(), event.getTo());
        }
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private record StoredRing(Location origin, BlockVector size) {
    }

    private enum VenomMobRole {
        BURROWER,
        WAVE_ONE_BOGGED,
        VENOM_SKIRMISHER,
        WEB_CRAWLER,
        BILE_ARCHER,
        ROT_BRUTE
    }

    private enum VenomWave {
        ENTRY_PRESSURE(1, 18, 14, 0.04D, List.of(VenomMobRole.BURROWER, VenomMobRole.WAVE_ONE_BOGGED, VenomMobRole.VENOM_SKIRMISHER, VenomMobRole.WEB_CRAWLER)),
        REINFORCEMENT(2, 24, 15, 0.08D, List.of(VenomMobRole.VENOM_SKIRMISHER, VenomMobRole.WEB_CRAWLER, VenomMobRole.BILE_ARCHER)),
        DISCIPLINE_PRESSURE(3, 28, 18, 0.12D, List.of(VenomMobRole.BILE_ARCHER, VenomMobRole.ROT_BRUTE, VenomMobRole.VENOM_SKIRMISHER, VenomMobRole.WEB_CRAWLER)),
        LEADER_WAVE(4, 24, 12, 0.10D, List.of(VenomMobRole.VENOM_SKIRMISHER, VenomMobRole.WEB_CRAWLER, VenomMobRole.BILE_ARCHER, VenomMobRole.ROT_BRUTE));

        private final int number;
        private final int killQuota;
        private final int aliveCap;
        private final double keyDropChance;
        private final List<VenomMobRole> roles;

        VenomWave(int number, int killQuota, int aliveCap, double keyDropChance, List<VenomMobRole> roles) {
            this.number = number;
            this.killQuota = killQuota;
            this.aliveCap = aliveCap;
            this.keyDropChance = keyDropChance;
            this.roles = roles;
        }

        private VenomWave next() {
            int nextOrdinal = ordinal() + 1;
            return nextOrdinal >= values().length ? this : values()[nextOrdinal];
        }

        private static VenomWave fromNumber(int number) {
            for (VenomWave wave : values()) {
                if (wave.number == number) {
                    return wave;
                }
            }
            return null;
        }
    }

    private static final class NbtCompound {
        private final Map<String, Object> values;

        private NbtCompound(Map<String, Object> values) {
            this.values = values;
        }

        private String string(String key) {
            Object value = values.get(key);
            return value instanceof String string ? string : "";
        }

        private int intValue(String key) {
            Object value = values.get(key);
            return value instanceof Number number ? number.intValue() : 0;
        }

        @SuppressWarnings("unchecked")
        private List<NbtCompound> compoundList(String key) {
            Object value = values.get(key);
            return value instanceof List<?> list ? (List<NbtCompound>) list : List.of();
        }

        @SuppressWarnings("unchecked")
        private List<Integer> intList(String key) {
            Object value = values.get(key);
            return value instanceof List<?> list ? (List<Integer>) list : List.of();
        }
    }

    private static final class NbtReader {
        private static final int TAG_END = 0;
        private static final int TAG_BYTE = 1;
        private static final int TAG_SHORT = 2;
        private static final int TAG_INT = 3;
        private static final int TAG_LONG = 4;
        private static final int TAG_FLOAT = 5;
        private static final int TAG_DOUBLE = 6;
        private static final int TAG_BYTE_ARRAY = 7;
        private static final int TAG_STRING = 8;
        private static final int TAG_LIST = 9;
        private static final int TAG_COMPOUND = 10;
        private static final int TAG_INT_ARRAY = 11;
        private static final int TAG_LONG_ARRAY = 12;

        private static NbtCompound readRootCompound(DataInputStream in) throws IOException {
            int type = in.readUnsignedByte();
            if (type != TAG_COMPOUND) {
                throw new IOException("Root NBT tag was not a compound.");
            }
            in.readUTF();
            return readCompoundPayload(in);
        }

        private static NbtCompound readCompoundPayload(DataInputStream in) throws IOException {
            Map<String, Object> values = new HashMap<>();
            while (true) {
                int type = in.readUnsignedByte();
                if (type == TAG_END) {
                    return new NbtCompound(values);
                }
                String name = in.readUTF();
                values.put(name, readPayload(in, type));
            }
        }

        private static Object readPayload(DataInputStream in, int type) throws IOException {
            return switch (type) {
                case TAG_BYTE -> in.readByte();
                case TAG_SHORT -> in.readShort();
                case TAG_INT -> in.readInt();
                case TAG_LONG -> in.readLong();
                case TAG_FLOAT -> in.readFloat();
                case TAG_DOUBLE -> in.readDouble();
                case TAG_BYTE_ARRAY -> readByteArray(in);
                case TAG_STRING -> in.readUTF();
                case TAG_LIST -> readList(in);
                case TAG_COMPOUND -> readCompoundPayload(in);
                case TAG_INT_ARRAY -> readIntArray(in);
                case TAG_LONG_ARRAY -> readLongArray(in);
                default -> throw new IOException("Unsupported NBT tag type " + type);
            };
        }

        private static byte[] readByteArray(DataInputStream in) throws IOException {
            int length = in.readInt();
            byte[] data = new byte[Math.max(0, length)];
            in.readFully(data);
            return data;
        }

        private static List<?> readList(DataInputStream in) throws IOException {
            int childType = in.readUnsignedByte();
            int length = in.readInt();
            if (length <= 0) {
                return List.of();
            }
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(readPayload(in, childType));
            }
            return values;
        }

        private static int[] readIntArray(DataInputStream in) throws IOException {
            int length = in.readInt();
            int[] values = new int[Math.max(0, length)];
            for (int i = 0; i < values.length; i++) {
                values[i] = in.readInt();
            }
            return values;
        }

        private static long[] readLongArray(DataInputStream in) throws IOException {
            int length = in.readInt();
            long[] values = new long[Math.max(0, length)];
            for (int i = 0; i < values.length; i++) {
                values[i] = in.readLong();
            }
            return values;
        }
    }

    private final class ActiveVenomRing {
        private final Location origin;
        private final BlockVector size;
        private final List<BlockState> ringSnapshots;
        private final Set<UUID> chamberMobs = new HashSet<>();
        private final Set<UUID> normalMobs = new HashSet<>();
        private final Set<UUID> miniSkeletonGuards = new HashSet<>();
        private final Map<UUID, VenomMobRole> mobRoles = new HashMap<>();
        private final Map<UUID, Double> playerThreat = new HashMap<>();
        private final Map<UUID, Long> lastTargetedTick = new HashMap<>();
        private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
        private final Map<UUID, Integer> mobOutsideTicks = new HashMap<>();
        private final Map<UUID, Integer> nextBurrowTick = new HashMap<>();
        private final Map<UUID, Integer> burrowReturnTick = new HashMap<>();
        private final Map<UUID, Double> burrowHealth = new HashMap<>();
        private final Map<UUID, Integer> transitionCooldowns = new HashMap<>();
        private final Map<UUID, Integer> transitionWarningCooldowns = new HashMap<>();
        private final List<TemporaryBlocks> temporaryBlocks = new ArrayList<>();
        private final List<Location> activeSpawnZones = new ArrayList<>();
        private final Deque<UUID> recentHazardTargets = new ArrayDeque<>();
        private final Set<UUID> escapedPlayers = new HashSet<>();
        private final Set<UUID> promptedPlayers = new HashSet<>();
        private final Set<UUID> reinforcementResponses = new HashSet<>();
        private final Set<UUID> stayingPlayers = new HashSet<>();
        private final Set<UUID> finalPromptedPlayers = new HashSet<>();
        private final Set<UUID> finalResponses = new HashSet<>();
        private final Set<UUID> finalStayingPlayers = new HashSet<>();
        private final Set<VenomWave> rewardedWaveClears = EnumSet.noneOf(VenomWave.class);
        private final BossBar bossBar;
        private LivingEntity venomLeader;
        private LivingEntity monkLeader;
        private VenomWave currentWave = VenomWave.ENTRY_PRESSURE;
        private VenomWave pendingWave;
        private int waveKills;
        private int totalKills;
        private int waveSpawned;
        private int tick;
        private int lastWebCastTick = -WEB_CAST_COOLDOWN_TICKS;
        private int lastSpikeCastTick = -SPIKE_CAST_COOLDOWN_TICKS;
        private int waveStartTick;
        private int lastReinforcementTick = -MIN_REINFORCEMENT_INTERVAL_TICKS;
        private int nextSpawnClusterRotationTick;
        private int reinforcementChoiceDeadlineTick;
        private int finalChoiceDeadlineTick;
        private boolean awaitingReinforcementChoice;
        private boolean awaitingFinalChoice;
        private boolean promptDisplayed;
        private boolean finalPromptDisplayed;
        private boolean completed;
        private boolean progressionUnlocked;
        private boolean fullClearRewarded;
        private int promptGeneration;
        private int lastPlayerEngagementTick;
        private boolean mobsGlowing;
        private BukkitTask task;

        private ActiveVenomRing(Location origin, BlockVector size, List<BlockState> ringSnapshots) {
            this.origin = origin.getBlock().getLocation();
            this.size = size;
            this.ringSnapshots = List.copyOf(ringSnapshots);
            this.bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
        }

        private void start(Player starter) {
            totalKills = 0;
            startWave(VenomWave.ENTRY_PRESSURE);
            updateBossBar();
            updateChamberScoreboards();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 10L);
        }

        private void startWave(VenomWave wave) {
            completed = false;
            currentWave = wave;
            pendingWave = null;
            progressionUnlocked = false;
            awaitingReinforcementChoice = false;
            awaitingFinalChoice = false;
            promptDisplayed = false;
            finalPromptDisplayed = false;
            promptGeneration++;
            waveKills = 0;
            waveSpawned = 0;
            waveStartTick = tick;
            lastReinforcementTick = tick - currentReinforcementIntervalTicks();
            lastPlayerEngagementTick = tick;
            setMobGlow(false);
            rotateSpawnClusters(true);
            if (wave == VenomWave.LEADER_WAVE) {
                spawnLeaderWave();
            }
            spawnNormalMobsUntilPressure(true);
            announceWave(wave);
        }

        private void spawnLeaderWave() {
            World world = origin.getWorld();
            if (world == null) {
                return;
            }
            Location center = center();
            Location venomSpawn = leaderSpawnLocation(true);
            Location monkSpawn = safeSpawnNear(venomSpawn.clone().add(3.0D, 0.0D, 2.0D));
            venomLeader = world.spawn(venomSpawn, Bogged.class, mob -> {
                mob.setCustomName(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Venom Leader");
                mob.setCustomNameVisible(true);
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
                mob.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
                setHealth(mob, 120.0D);
                setSpeed(mob, 0.31D);
                setScale(mob, 1.85D);
            });
            monkLeader = world.spawn(monkSpawn, Skeleton.class, mob -> {
                mob.setCustomName(ChatColor.GREEN + "" + ChatColor.BOLD + "Monk Support Leader");
                mob.setCustomNameVisible(true);
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
                mob.getEquipment().setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
                mob.getEquipment().setHelmet(new ItemStack(Material.MOSS_BLOCK));
                setHealth(mob, 90.0D);
                setSpeed(mob, 0.28D);
                setScale(mob, 1.15D);
            });
            registerMob(venomLeader);
            registerMob(monkLeader);
            spawnMiniSkeletonGuard(monkSpawn.clone().add(2.0D, 0.0D, 1.0D));
            spawnMiniSkeletonGuard(monkSpawn.clone().add(-2.0D, 0.0D, -1.0D));

            world.playSound(center, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.0f, 0.75f);
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 45, 5.0D, 1.0D, 5.0D, 0.03D);
        }

        private void announceWave(VenomWave wave) {
            World world = origin.getWorld();
            if (world != null) {
                world.playSound(center(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 0.85f, Math.max(0.55f, 1.05f - (wave.number * 0.08f)));
                world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center(), 24 + (wave.number * 8), 4.0D, 0.8D, 4.0D, 0.025D);
            }
        }

        private void spawnMiniSkeletonGuard(Location near) {
            World world = origin.getWorld();
            if (world == null) {
                return;
            }
            Location spawn = safeSpawnNear(near);
            Skeleton guard = world.spawn(spawn, Skeleton.class, mob -> {
                mob.setCustomName(ChatColor.DARK_GREEN + "Monk Guard");
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
                ItemStack bone = new ItemStack(Material.BONE);
                bone.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
                mob.getEquipment().setItemInMainHand(bone);
                mob.getEquipment().setItemInOffHand(new ItemStack(Material.BONE));
                setHealth(mob, 26.0D);
                setSpeed(mob, 0.30D);
                setScale(mob, 0.55D);
            });
            registerMob(guard);
            miniSkeletonGuards.add(guard.getUniqueId());
        }

        private void tick() {
            tick += 10;
            updateBossBar();
            updateChamberScoreboards();
            if (isComplete()) {
                beginFinalRingChoice();
                return;
            }
            if (awaitingFinalChoice) {
                if (tick >= finalChoiceDeadlineTick || finalPromptedPlayers.isEmpty()) {
                    resolveFinalRingChoices();
                }
                return;
            }
            if (pendingWave != null) {
                return;
            }
            if (awaitingReinforcementChoice) {
                if (tick >= reinforcementChoiceDeadlineTick || promptedPlayers.isEmpty()) {
                    resolveReinforcementChoices();
                }
                return;
            }
            updateLostMobGlow();
            if (waveCombatComplete()) {
                beginReinforcementChoice(currentWave.next());
                return;
            }
            if (tick >= nextSpawnClusterRotationTick || activeSpawnZones.isEmpty()) {
                rotateSpawnClusters(false);
            }
            spawnNormalMobsUntilPressure(false);
            for (UUID id : new ArrayList<>(chamberMobs)) {
                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) {
                    continue;
                }
                recoverOutsideMob(mob);
                handleBurrower(mob);
                keepInside(mob);
                suppressSpiderClimb(mob);
                Player target = chooseTarget(mob);
                if (target != null) {
                    mob.setTarget(target);
                    lastTargetedTick.put(target.getUniqueId(), (long) tick);
                } else {
                    mob.setTarget(null);
                }
            }
            if (tick - lastWebCastTick >= currentWebCooldownTicks() && venomLeader != null && venomLeader.isValid() && !venomLeader.isDead()) {
                venomLeaderAbility();
            }
            guardMonkLeader();
            if (tick % 90 == 0 && monkLeader != null && monkLeader.isValid() && !monkLeader.isDead()) {
                monkSupportAbility();
            }
            if (tick - lastSpikeCastTick >= currentSpikeCooldownTicks()) {
                spawnVenomSpike();
            }
            applyVenomFloorEffects();
        }

        private void spawnNormalMobsUntilPressure(boolean force) {
            if (waveSpawned >= currentWave.killQuota) {
                return;
            }
            int reinforcementInterval = currentReinforcementIntervalTicks();
            if (!force && tick - lastReinforcementTick < reinforcementInterval) {
                return;
            }
            lastReinforcementTick = tick;
            int aliveNormal = livingNormalMobCount();
            int remainingSpawns = Math.max(0, currentWave.killQuota - waveSpawned);
            int desiredAlive = Math.min(currentAliveCap(), remainingSpawns);
            int spawnBudget = Math.min(remainingSpawns, Math.max(0, desiredAlive - aliveNormal));
            if (force) {
                spawnBudget = Math.min(spawnBudget, initialSpawnClusterSize());
            } else {
                spawnBudget = Math.min(spawnBudget, REINFORCEMENT_CLUSTER_SIZE);
            }
            for (int spawned = 0; spawned < spawnBudget; spawned++) {
                if (!spawnNormalMob(waveSpawned + spawned)) {
                    return;
                }
                waveSpawned++;
            }
        }

        private int currentAliveCap() {
            int elapsedSeconds = waveElapsedSeconds();
            int cap = switch (currentWave) {
                case ENTRY_PRESSURE -> timedCap(elapsedSeconds, 8, 10, 12, 14);
                case REINFORCEMENT -> timedCap(elapsedSeconds, 9, 12, 15, 15);
                case DISCIPLINE_PRESSURE -> timedCap(elapsedSeconds, 10, 14, 18, 18);
                case LEADER_WAVE -> timedCap(elapsedSeconds, 8, 10, 12, 12);
            };
            if (playersInside().size() <= 1) {
                cap = Math.max(6, cap - 3);
            }
            return Math.min(currentWave.aliveCap, cap);
        }

        private int timedCap(int elapsedSeconds, int start, int at45, int at90, int at120) {
            if (elapsedSeconds >= 120) {
                return at120;
            }
            if (elapsedSeconds >= 90) {
                return at90;
            }
            if (elapsedSeconds >= 45) {
                return at45;
            }
            return start;
        }

        private int initialSpawnClusterSize() {
            return switch (currentWave) {
                case ENTRY_PRESSURE -> 7;
                case REINFORCEMENT -> 7;
                case DISCIPLINE_PRESSURE -> 8;
                case LEADER_WAVE -> 6;
            };
        }

        private int currentReinforcementIntervalTicks() {
            int elapsedSeconds = waveElapsedSeconds();
            int interval = switch (currentWave) {
                case ENTRY_PRESSURE -> 20 * 6;
                case REINFORCEMENT -> Math.max(20 * 4, (20 * 6) - ((elapsedSeconds / 45) * 20));
                case DISCIPLINE_PRESSURE -> Math.max(MIN_REINFORCEMENT_INTERVAL_TICKS, (20 * 6) - ((elapsedSeconds / 15) * 10));
                case LEADER_WAVE -> Math.max(MIN_REINFORCEMENT_INTERVAL_TICKS, (20 * 5) - ((elapsedSeconds / 12) * 10));
            };
            if (playersInside().size() <= 1) {
                interval += 20;
            }
            return Math.max(MIN_REINFORCEMENT_INTERVAL_TICKS, interval);
        }

        private int waveElapsedSeconds() {
            return Math.max(0, tick - waveStartTick) / 20;
        }

        private boolean spawnNormalMob(int index) {
            World world = origin.getWorld();
            if (world == null) {
                return false;
            }
            Location spawn = clusteredSpawnLocation(index);
            VenomMobRole role = currentWave.roles.get(Math.floorMod(waveKills + index, currentWave.roles.size()));
            LivingEntity mob = switch (role) {
                case BURROWER -> world.spawn(spawn, Silverfish.class, silverfish -> {
                    silverfish.setCustomName(ChatColor.DARK_GREEN + "Burrower");
                    silverfish.setPersistent(true);
                    silverfish.setRemoveWhenFarAway(false);
                    setHealth(silverfish, 12.0D);
                    setSpeed(silverfish, 0.34D);
                });
                case WAVE_ONE_BOGGED -> world.spawn(spawn, Bogged.class, bogged -> {
                    bogged.setCustomName(ChatColor.GREEN + "Bogged Whelp");
                    bogged.setPersistent(true);
                    bogged.setRemoveWhenFarAway(false);
                    bogged.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                    setHealth(bogged, 24.0D);
                    setSpeed(bogged, 0.23D);
                    setScale(bogged, 0.85D);
                });
                case VENOM_SKIRMISHER -> world.spawn(spawn, CaveSpider.class, cave -> {
                    cave.setCustomName(ChatColor.DARK_GREEN + "Venom Skirmisher");
                    cave.setPersistent(true);
                    cave.setRemoveWhenFarAway(false);
                    setHealth(cave, 28.0D);
                    setSpeed(cave, 0.38D);
                });
                case WEB_CRAWLER -> world.spawn(spawn, Spider.class, spider -> {
                    spider.setCustomName(ChatColor.GREEN + "Web Crawler");
                    spider.setPersistent(true);
                    spider.setRemoveWhenFarAway(false);
                    setHealth(spider, 24.0D);
                    setSpeed(spider, 0.32D);
                    setScale(spider, 0.72D);
                });
                case BILE_ARCHER -> world.spawn(spawn, Bogged.class, bogged -> {
                    bogged.setCustomName(ChatColor.YELLOW + "Bile Archer");
                    bogged.setPersistent(true);
                    bogged.setRemoveWhenFarAway(false);
                    bogged.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                    bogged.getEquipment().setHelmet(new ItemStack(Material.SLIME_BLOCK));
                    setHealth(bogged, 30.0D);
                    setSpeed(bogged, 0.28D);
                });
                case ROT_BRUTE -> world.spawn(spawn, Zombie.class, zombie -> {
                    zombie.setCustomName(ChatColor.DARK_GREEN + "Rot Brute");
                    zombie.setPersistent(true);
                    zombie.setRemoveWhenFarAway(false);
                    zombie.getEquipment().setItemInMainHand(new ItemStack(Material.ROTTEN_FLESH));
                    setHealth(zombie, 70.0D);
                    setSpeed(zombie, 0.22D);
                    setScale(zombie, 1.35D);
                });
            };
            registerMob(mob);
            normalMobs.add(mob.getUniqueId());
            mobRoles.put(mob.getUniqueId(), role);
            if (role == VenomMobRole.BURROWER) {
                nextBurrowTick.put(mob.getUniqueId(), tick + ThreadLocalRandom.current().nextInt(50, BURROWER_BURROW_INTERVAL_TICKS + 1));
            }
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, spawn.clone().add(0.0D, 0.4D, 0.0D), 10, 0.35D, 0.2D, 0.35D, 0.02D);
            return true;
        }

        private boolean waveCombatComplete() {
            return currentWave != VenomWave.LEADER_WAVE
                && waveKills >= currentWave.killQuota
                && livingNormalMobCount() == 0;
        }

        private void beginReinforcementChoice(VenomWave nextWave) {
            if (awaitingReinforcementChoice || pendingWave != null) {
                return;
            }
            awardWaveClear(currentWave);
            awaitingReinforcementChoice = true;
            pendingWave = nextWave;
            promptGeneration++;
            int generation = promptGeneration;
            promptedPlayers.clear();
            reinforcementResponses.clear();
            stayingPlayers.clear();
            List<Player> inside = playersInside();
            for (Player player : inside) {
                promptedPlayers.add(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeVenomRing == this && awaitingReinforcementChoice && promptGeneration == generation) {
                    promptReinforcementChoice();
                }
            }, REINFORCEMENT_PROMPT_DELAY_TICKS);
            reinforcementChoiceDeadlineTick = tick + REINFORCEMENT_PROMPT_DELAY_TICKS + REINFORCEMENT_CHOICE_TIMEOUT_TICKS;
        }

        private void promptReinforcementChoice() {
            if (promptDisplayed) {
                return;
            }
            promptDisplayed = true;
            Component prompt = Component.text("[Combat Chamber] ", NamedTextColor.DARK_AQUA)
                .append(Component.text("Reinforcements are on the way.", NamedTextColor.DARK_GREEN))
                .append(Component.newline())
                .append(Component.text("Do you want to escape?", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("[Yes]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/combatchamber yes"))
                    .hoverEvent(HoverEvent.showText(Component.text("Leave the Venom Ring", NamedTextColor.GREEN))))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("[No]", NamedTextColor.DARK_GREEN)
                    .clickEvent(ClickEvent.runCommand("/combatchamber no"))
                    .hoverEvent(HoverEvent.showText(Component.text("Stay for the next wave", NamedTextColor.DARK_GREEN))));
            for (UUID id : promptedPlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && !player.isDead() && contains(player.getLocation()) && !escapedPlayers.contains(id)) {
                    player.sendMessage(prompt);
                }
            }
        }

        private boolean chooseReinforcement(Player player, boolean stay) {
            UUID id = player.getUniqueId();
            if (!awaitingReinforcementChoice || !promptedPlayers.contains(id) || escapedPlayers.contains(id)) {
                player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No reinforcement choice is active.");
                return false;
            }
            promptGeneration++;
            reinforcementResponses.add(id);
            promptedPlayers.remove(id);
            if (stay) {
                stayingPlayers.add(id);
                player.sendActionBar(Component.text("You stayed for the next wave.", NamedTextColor.DARK_GREEN));
            } else {
                escapedPlayers.add(id);
                bossBar.removePlayer(player);
                playerThreat.remove(id);
                lastTargetedTick.remove(id);
                player.sendActionBar(Component.text("You escaped the Combat Chamber.", NamedTextColor.GREEN));
                teleportPlayerOut(player);
            }
            if (promptedPlayers.isEmpty()) {
                resolveReinforcementChoices();
            }
            return true;
        }

        private void resolveReinforcementChoices() {
            if (!awaitingReinforcementChoice) {
                return;
            }
            awaitingReinforcementChoice = false;
            promptDisplayed = false;
            promptGeneration++;
            for (UUID id : new HashSet<>(promptedPlayers)) {
                if (!escapedPlayers.contains(id)) {
                    stayingPlayers.add(id);
                }
            }
            if (stayingPlayers.isEmpty() || playersInside().isEmpty()) {
                cleanup(false, true);
                activeVenomRing = null;
                return;
            }
            VenomWave next = pendingWave == null ? currentWave.next() : pendingWave;
            promptedPlayers.clear();
            reinforcementResponses.clear();
            int generation = promptGeneration;
            for (Player player : playersInside()) {
                player.sendActionBar(Component.text("Recover. Next wave begins in 10 seconds...", NamedTextColor.GOLD));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeVenomRing == this && !awaitingReinforcementChoice && pendingWave == next && promptGeneration == generation) {
                    startWave(next);
                    updateBossBar();
                }
            }, NEXT_WAVE_DELAY_TICKS);
        }

        private void beginFinalRingChoice() {
            if (awaitingFinalChoice) {
                return;
            }
            awardFullClear();
            awaitingFinalChoice = true;
            finalPromptDisplayed = false;
            promptGeneration++;
            int generation = promptGeneration;
            finalPromptedPlayers.clear();
            finalResponses.clear();
            finalStayingPlayers.clear();
            for (Player player : playersInside()) {
                finalPromptedPlayers.add(player.getUniqueId());
                player.sendActionBar(Component.text("It feels like your feet are shaking...", NamedTextColor.DARK_PURPLE));
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.55f);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeVenomRing == this && awaitingFinalChoice && promptGeneration == generation) {
                    promptFinalRingChoice();
                }
            }, FINAL_RING_PROMPT_DELAY_TICKS);
            finalChoiceDeadlineTick = tick + FINAL_RING_PROMPT_DELAY_TICKS + FINAL_RING_CHOICE_TIMEOUT_TICKS;
        }

        private void promptFinalRingChoice() {
            if (finalPromptDisplayed) {
                return;
            }
            finalPromptDisplayed = true;
            Component prompt = Component.text("[Combat Chamber] ", NamedTextColor.DARK_AQUA)
                .append(Component.text("Continue to the next ring?", NamedTextColor.DARK_PURPLE))
                .append(Component.newline())
                .append(Component.text("[Stay]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/combatchamber stay"))
                    .hoverEvent(HoverEvent.showText(Component.text("Continue deeper", NamedTextColor.GREEN))))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("[Leave]", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/combatchamber leave"))
                    .hoverEvent(HoverEvent.showText(Component.text("Extract safely", NamedTextColor.YELLOW))));
            for (UUID id : finalPromptedPlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && !player.isDead() && contains(player.getLocation()) && !escapedPlayers.contains(id)) {
                    player.sendMessage(prompt);
                }
            }
        }

        private boolean chooseFinalRingTransition(Player player, boolean stay) {
            UUID id = player.getUniqueId();
            if (!awaitingFinalChoice || !finalPromptedPlayers.contains(id) || escapedPlayers.contains(id)) {
                player.sendMessage(ChatColor.YELLOW + "[Combat Chamber] No final ring choice is active.");
                return false;
            }
            promptGeneration++;
            finalResponses.add(id);
            finalPromptedPlayers.remove(id);
            if (stay) {
                finalStayingPlayers.add(id);
                player.sendActionBar(Component.text("The next ring stirs below...", NamedTextColor.DARK_PURPLE));
            } else {
                escapedPlayers.add(id);
                bossBar.removePlayer(player);
                player.sendActionBar(Component.text("You escaped the Combat Chamber.", NamedTextColor.GREEN));
                teleportPlayerOut(player);
            }
            if (finalPromptedPlayers.isEmpty()) {
                resolveFinalRingChoices();
            }
            return true;
        }

        private void resolveFinalRingChoices() {
            if (!awaitingFinalChoice) {
                return;
            }
            awaitingFinalChoice = false;
            finalPromptDisplayed = false;
            promptGeneration++;
            for (UUID id : new HashSet<>(finalPromptedPlayers)) {
                if (!escapedPlayers.contains(id)) {
                    escapedPlayers.add(id);
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        bossBar.removePlayer(player);
                        teleportPlayerOut(player);
                    }
                }
            }
            finalPromptedPlayers.clear();
            finalResponses.clear();
            for (UUID id : finalStayingPlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && !player.isDead()) {
                    player.sendMessage(Component.text("[Combat Chamber] ", NamedTextColor.DARK_AQUA)
                        .append(Component.text("Next Ring - WIP", NamedTextColor.YELLOW)));
                }
            }
            progressionUnlocked = !finalStayingPlayers.isEmpty();
            completeEncounterOnly();
        }

        private void awardWaveClear(VenomWave wave) {
            if (progressionService == null || wave == null || rewardedWaveClears.contains(wave)) {
                return;
            }
            rewardedWaveClears.add(wave);
            int silver = WAVE_CLEAR_BASE_SILVER + ((wave.number - 1) * 15);
            for (Player player : playersInside()) {
                progressionService.queueSilver(player.getUniqueId(), silver, "COMBAT_CHAMBER:VENOM:WAVE_" + wave.number);
            }
        }

        private void awardLeaderKill(EntityDeathEvent event) {
            if (progressionService == null || event == null) {
                return;
            }
            Player killer = event.getEntity().getKiller();
            if (killer == null || !contains(killer.getLocation())) {
                return;
            }
            progressionService.queuePrestigeXp(killer.getUniqueId(), LEADER_KILL_PRESTIGE_XP, "COMBAT_CHAMBER:VENOM:LEADER_KILL");
            if (ThreadLocalRandom.current().nextDouble() <= LEADER_SHARD_FRAGMENT_CHANCE) {
                progressionService.queueShardFragments(killer.getUniqueId(), 1, "COMBAT_CHAMBER:VENOM:LEADER_FRAGMENT");
            }
        }

        private void awardFullClear() {
            if (progressionService == null || fullClearRewarded) {
                return;
            }
            fullClearRewarded = true;
            int finalSilver = WAVE_CLEAR_BASE_SILVER + ((VenomWave.LEADER_WAVE.number - 1) * 15);
            for (Player player : playersInside()) {
                progressionService.queueSilver(player.getUniqueId(), finalSilver, "COMBAT_CHAMBER:VENOM:WAVE_4");
                progressionService.queuePrestigeXp(player.getUniqueId(), FULL_CLEAR_PRESTIGE_XP, "COMBAT_CHAMBER:VENOM:FULL_CLEAR");
                progressionService.queueShardFragments(player.getUniqueId(), FULL_CLEAR_SHARD_FRAGMENTS, "COMBAT_CHAMBER:VENOM:FULL_CLEAR");
            }
        }

        private int currentWebCooldownTicks() {
            return switch (currentWave) {
                case ENTRY_PRESSURE -> WEB_CAST_COOLDOWN_TICKS + 40;
                case REINFORCEMENT -> WEB_CAST_COOLDOWN_TICKS;
                case DISCIPLINE_PRESSURE -> WEB_CAST_COOLDOWN_TICKS - 20;
                case LEADER_WAVE -> WEB_CAST_COOLDOWN_TICKS - 30;
            };
        }

        private int currentSpikeCooldownTicks() {
            return switch (currentWave) {
                case ENTRY_PRESSURE -> SPIKE_CAST_COOLDOWN_TICKS + 80;
                case REINFORCEMENT -> SPIKE_CAST_COOLDOWN_TICKS + 40;
                case DISCIPLINE_PRESSURE -> SPIKE_CAST_COOLDOWN_TICKS;
                case LEADER_WAVE -> SPIKE_CAST_COOLDOWN_TICKS - 20;
            };
        }

        private void forceWave(VenomWave wave) {
            removeWaveMobs();
            startWave(wave);
            updateBossBar();
        }

        private void removeWaveMobs() {
            for (UUID id : new ArrayList<>(normalMobs)) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
                chamberMobs.remove(id);
                mobRoles.remove(id);
            }
            normalMobs.clear();
            if (venomLeader != null && venomLeader.isValid()) {
                venomLeader.remove();
            }
            if (monkLeader != null && monkLeader.isValid()) {
                monkLeader.remove();
            }
            venomLeader = null;
            monkLeader = null;
            for (UUID id : new ArrayList<>(miniSkeletonGuards)) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
                chamberMobs.remove(id);
            }
            miniSkeletonGuards.clear();
        }

        private Location leaderSpawnLocation(boolean venom) {
            if (!activeSpawnZones.isEmpty()) {
                Location cluster = activeSpawnZones.getFirst();
                Location offset = venom ? cluster : cluster.clone().add(3.0D, 0.0D, 2.0D);
                return safeSpawnNear(offset);
            }
            double angle = venom ? Math.toRadians(35.0D) : Math.toRadians(215.0D);
            return safeSpawnNear(ringNode(angle));
        }

        private Location normalSpawnLocation(int index) {
            double[] angles = {
                Math.toRadians(15.0D),
                Math.toRadians(65.0D),
                Math.toRadians(115.0D),
                Math.toRadians(165.0D),
                Math.toRadians(205.0D),
                Math.toRadians(255.0D),
                Math.toRadians(305.0D),
                Math.toRadians(345.0D)
            };
            return safeSpawnNear(ringNode(angles[Math.floorMod(index, angles.length)]));
        }

        private Location clusteredSpawnLocation(int index) {
            if (activeSpawnZones.isEmpty()) {
                rotateSpawnClusters(true);
            }
            if (!activeSpawnZones.isEmpty()) {
                Location zone = activeSpawnZones.get(Math.floorMod(index, activeSpawnZones.size()));
                Location safe = safeSpawnNear(zone.clone().add(
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D),
                    0.0D,
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D)
                ));
                if (isGoodPlayerDistance(safe)) {
                    return safe;
                }
            }
            return normalSpawnLocation(index);
        }

        private void rotateSpawnClusters(boolean force) {
            if (!force && tick < nextSpawnClusterRotationTick) {
                return;
            }
            activeSpawnZones.clear();
            List<Player> players = playersInside();
            if (players.isEmpty()) {
                nextSpawnClusterRotationTick = tick + SPAWN_CLUSTER_ROTATE_TICKS;
                return;
            }
            double[] distances = {14.0D, 18.0D, 24.0D, 30.0D, 36.0D};
            double[] angles = {0.0D, 45.0D, 90.0D, 135.0D, 180.0D, 225.0D, 270.0D, 315.0D};
            List<Location> candidates = new ArrayList<>();
            for (Player player : players) {
                Location base = player.getLocation();
                for (double distance : distances) {
                    for (double angleDegrees : angles) {
                        double angle = Math.toRadians(angleDegrees + ThreadLocalRandom.current().nextDouble(-12.0D, 12.0D));
                        Location safe = safeSpawnAt(base.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
                        if (safe != null && isGoodPlayerDistance(safe) && !isNearExistingSpawnZone(safe)) {
                            candidates.add(safe);
                        }
                    }
                }
            }
            for (double angleDegrees : angles) {
                Location safe = safeSpawnAt(ringNode(Math.toRadians(angleDegrees + ThreadLocalRandom.current().nextDouble(-10.0D, 10.0D))));
                if (safe != null && isGoodPlayerDistance(safe) && !isNearExistingSpawnZone(safe)) {
                    candidates.add(safe);
                }
            }
            int zoneCount = Math.min(players.size() >= 2 ? 4 : 3, candidates.size());
            while (activeSpawnZones.size() < zoneCount && !candidates.isEmpty()) {
                Location selected = selectSpreadSpawnZone(candidates);
                activeSpawnZones.add(selected);
                candidates.remove(selected);
            }
            if (activeSpawnZones.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    activeSpawnZones.add(normalSpawnLocation(i));
                }
            }
            nextSpawnClusterRotationTick = tick + SPAWN_CLUSTER_ROTATE_TICKS;
        }

        private Location selectSpreadSpawnZone(List<Location> candidates) {
            if (activeSpawnZones.isEmpty()) {
                return candidates.stream()
                    .max(Comparator.comparingDouble(this::nearestPlayerDistanceSquared))
                    .orElse(candidates.getFirst());
            }
            return candidates.stream()
                .max(Comparator.comparingDouble(this::nearestSpawnZoneDistanceSquared))
                .orElse(candidates.getFirst());
        }

        private double nearestSpawnZoneDistanceSquared(Location candidate) {
            double nearest = Double.MAX_VALUE;
            for (Location zone : activeSpawnZones) {
                if (zone.getWorld() != null && zone.getWorld().equals(candidate.getWorld())) {
                    nearest = Math.min(nearest, zone.distanceSquared(candidate));
                }
            }
            return nearest;
        }

        private boolean isGoodPlayerDistance(Location location) {
            double nearest = nearestPlayerDistanceSquared(location);
            return nearest >= SPAWN_MIN_PLAYER_DISTANCE * SPAWN_MIN_PLAYER_DISTANCE
                && nearest <= SPAWN_MAX_PLAYER_DISTANCE * SPAWN_MAX_PLAYER_DISTANCE;
        }

        private double nearestPlayerDistanceSquared(Location location) {
            if (location == null) {
                return Double.MAX_VALUE;
            }
            double nearest = Double.MAX_VALUE;
            for (Player player : playersInside()) {
                nearest = Math.min(nearest, player.getLocation().distanceSquared(location));
            }
            return nearest;
        }

        private boolean isNearExistingSpawnZone(Location location) {
            for (Location zone : activeSpawnZones) {
                if (zone.getWorld() != null && zone.getWorld().equals(location.getWorld()) && zone.distanceSquared(location) < 64.0D) {
                    return true;
                }
            }
            return false;
        }

        private Location ringNode(double angle) {
            double radiusX = Math.max(4.0D, size.getBlockX() * 0.36D);
            double radiusZ = Math.max(4.0D, size.getBlockZ() * 0.36D);
            return center().add(Math.cos(angle) * radiusX, 0.0D, Math.sin(angle) * radiusZ);
        }

        private Location safeSpawnNear(Location desired) {
            Location direct = safeSpawnAt(desired);
            if (direct != null) {
                return direct;
            }
            for (int radius = 1; radius <= 10; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        Location candidate = desired.clone().add(dx, 0.0D, dz);
                        Location safe = safeSpawnAt(candidate);
                        if (safe != null) {
                            return safe;
                        }
                    }
                }
            }
            return safeFallbackSpawn();
        }

        private Location safeSpawnAt(Location desired) {
            World world = origin.getWorld();
            if (world == null || desired == null || desired.getWorld() == null || !desired.getWorld().equals(world)) {
                return null;
            }
            int x = Math.max(origin.getBlockX() + 1, Math.min(origin.getBlockX() + Math.max(1, size.getBlockX()) - 2, desired.getBlockX()));
            int z = Math.max(origin.getBlockZ() + 1, Math.min(origin.getBlockZ() + Math.max(1, size.getBlockZ()) - 2, desired.getBlockZ()));
            int top = origin.getBlockY() + Math.max(4, size.getBlockY()) + 4;
            int bottom = origin.getBlockY() - 4;
            for (int y = Math.min(top, world.getMaxHeight() - 3); y >= Math.max(bottom, world.getMinHeight() + 1); y--) {
                Block floor = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                if (!floor.getType().isSolid()) {
                    continue;
                }
                if (!feet.getType().isAir() || !head.getType().isAir() || !hasMobClearance(world, x, y, z)) {
                    continue;
                }
                Location safe = new Location(world, x + 0.5D, y, z + 0.5D);
                return contains(safe) ? safe : null;
            }
            return null;
        }

        private boolean hasMobClearance(World world, int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (!block.getType().isAir()) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private void suppressSpiderClimb(Mob mob) {
            if (!(mob instanceof Spider) && !(mob instanceof CaveSpider)) {
                return;
            }
            Location location = mob.getLocation();
            Block feet = location.getBlock();
            Vector awayFromWall = wallRepulsion(feet);
            boolean onGround = feet.getRelative(BlockFace.DOWN).getType().isSolid();
            if (awayFromWall.lengthSquared() <= 0.0D && onGround) {
                return;
            }
            Vector velocity = mob.getVelocity();
            if (awayFromWall.lengthSquared() > 0.0D) {
                awayFromWall.normalize().multiply(0.24D);
                velocity.setX((velocity.getX() * 0.35D) + awayFromWall.getX());
                velocity.setZ((velocity.getZ() * 0.35D) + awayFromWall.getZ());
            }
            if (velocity.getY() > -0.22D) {
                velocity.setY(-0.22D);
            }
            mob.setVelocity(velocity);

            if (!onGround && isSpiderTooHighForGroundMob(mob)) {
                Location safe = safeSpawnNear(mob.getLocation());
                if (safe != null && contains(safe)) {
                    mob.teleport(safe);
                }
            }
        }

        private void recoverOutsideMob(Mob mob) {
            UUID id = mob.getUniqueId();
            if (contains(mob.getLocation())) {
                mobOutsideTicks.remove(id);
                return;
            }
            int outside = mobOutsideTicks.merge(id, 10, (a, b) -> a + b);
            if (outside >= MOB_OUTSIDE_RECOVERY_TICKS) {
                Location safe = safeFallbackSpawn();
                if (safe != null && contains(safe)) {
                    mob.teleport(safe);
                    mobOutsideTicks.remove(id);
                } else {
                    removeRequiredMobAsCleared(mob);
                }
            }
        }

        private void removeRequiredMobAsCleared(Mob mob) {
            UUID id = mob.getUniqueId();
            boolean normal = normalMobs.remove(id);
            chamberMobs.remove(id);
            miniSkeletonGuards.remove(id);
            mobRoles.remove(id);
            nextBurrowTick.remove(id);
            burrowReturnTick.remove(id);
            burrowHealth.remove(id);
            mobOutsideTicks.remove(id);
            mob.remove();
            if (normal) {
                waveKills++;
                totalKills++;
            }
        }

        private void handleBurrower(Mob mob) {
            UUID id = mob.getUniqueId();
            if (mobRoles.get(id) != VenomMobRole.BURROWER) {
                return;
            }
            Integer returnTick = burrowReturnTick.get(id);
            if (returnTick != null) {
                if (tick < returnTick) {
                    return;
                }
                resurfaceBurrower(mob);
                return;
            }
            int next = nextBurrowTick.getOrDefault(id, tick + BURROWER_BURROW_INTERVAL_TICKS);
            if (tick >= next && contains(mob.getLocation())) {
                burrowBurrower(mob);
            }
        }

        private void burrowBurrower(Mob mob) {
            UUID id = mob.getUniqueId();
            burrowHealth.put(id, mob.getHealth());
            burrowReturnTick.put(id, tick + BURROWER_BURROW_DURATION_TICKS);
            mob.setInvisible(true);
            mob.setInvulnerable(true);
            mob.setAI(false);
            mob.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, mob.getLocation().add(0.0D, 0.15D, 0.0D), 18, 0.45D, 0.08D, 0.45D, 0.02D, Material.SLIME_BLOCK.createBlockData());
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SILVERFISH_STEP, 0.65f, 0.55f);
        }

        private void resurfaceBurrower(Mob mob) {
            UUID id = mob.getUniqueId();
            Player target = chooseHazardTarget(playersInside());
            Location surface = target == null ? safeFallbackSpawn() : hazardLocationNear(target.getLocation(), false);
            if (surface == null || !contains(surface)) {
                surface = safeFallbackSpawn();
            }
            if (surface != null && contains(surface)) {
                mob.teleport(surface);
                TemporaryBlocks patch = new TemporaryBlocks();
                patch.trackAndSet(surface.getBlock().getRelative(BlockFace.DOWN), Material.SLIME_BLOCK);
                temporaryBlocks.add(patch);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    patch.restore();
                    temporaryBlocks.remove(patch);
                }, 20 * 4);
            }
            double storedHealth = burrowHealth.getOrDefault(id, mob.getHealth());
            mob.setHealth(Math.max(1.0D, Math.min(maxHealth(mob), storedHealth)));
            mob.setInvisible(false);
            mob.setInvulnerable(false);
            mob.setAI(true);
            mob.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, mob.getLocation().add(0.0D, 0.2D, 0.0D), 20, 0.5D, 0.12D, 0.5D, 0.02D);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, 0.7f, 0.75f);
            burrowReturnTick.remove(id);
            burrowHealth.remove(id);
            nextBurrowTick.put(id, tick + ThreadLocalRandom.current().nextInt(80, BURROWER_BURROW_INTERVAL_TICKS + 1));
        }

        private Vector wallRepulsion(Block feet) {
            Vector away = new Vector(0.0D, 0.0D, 0.0D);
            addWallRepulsion(away, feet.getRelative(BlockFace.NORTH), 0.0D, 1.0D);
            addWallRepulsion(away, feet.getRelative(BlockFace.SOUTH), 0.0D, -1.0D);
            addWallRepulsion(away, feet.getRelative(BlockFace.EAST), -1.0D, 0.0D);
            addWallRepulsion(away, feet.getRelative(BlockFace.WEST), 1.0D, 0.0D);
            Block head = feet.getRelative(BlockFace.UP);
            addWallRepulsion(away, head.getRelative(BlockFace.NORTH), 0.0D, 1.0D);
            addWallRepulsion(away, head.getRelative(BlockFace.SOUTH), 0.0D, -1.0D);
            addWallRepulsion(away, head.getRelative(BlockFace.EAST), -1.0D, 0.0D);
            addWallRepulsion(away, head.getRelative(BlockFace.WEST), 1.0D, 0.0D);
            return away;
        }

        private void addWallRepulsion(Vector away, Block wall, double x, double z) {
            if (wall.getType().isSolid()) {
                away.add(new Vector(x, 0.0D, z));
            }
        }

        private boolean isSpiderTooHighForGroundMob(Mob mob) {
            Block below = mob.getLocation().getBlock().getRelative(BlockFace.DOWN);
            for (int i = 0; i < 3; i++) {
                if (below.getType().isSolid()) {
                    return false;
                }
                below = below.getRelative(BlockFace.DOWN);
            }
            return true;
        }

        private Location safeFallbackSpawn() {
            double[] angles = {
                Math.toRadians(0.0D),
                Math.toRadians(45.0D),
                Math.toRadians(90.0D),
                Math.toRadians(135.0D),
                Math.toRadians(180.0D),
                Math.toRadians(225.0D),
                Math.toRadians(270.0D),
                Math.toRadians(315.0D)
            };
            for (double angle : angles) {
                Location safe = safeSpawnAt(ringNode(angle));
                if (safe != null) {
                    return safe;
                }
            }
            return center();
        }

        private void venomLeaderAbility() {
            World world = origin.getWorld();
            if (world == null) {
                return;
            }
            List<Player> targets = playersInside();
            if (targets.isEmpty() || venomLeader == null) {
                return;
            }
            Location leaderLocation = venomLeader.getLocation();
            List<Player> inRange = targets.stream()
                .filter(player -> player.getLocation().distanceSquared(leaderLocation) <= WEB_CAST_RANGE * WEB_CAST_RANGE)
                .toList();
            Player target = chooseHazardTarget(inRange);
            if (target == null) {
                return;
            }
            Location webLocation = hazardLocationNear(target.getLocation(), true);
            if (webLocation == null) {
                return;
            }
            markHazardTarget(target);
            lastWebCastTick = tick;
            placeTemporaryWebPatch(webLocation);
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true, true));
            world.playSound(webLocation, Sound.ENTITY_SPIDER_AMBIENT, 0.9f, 0.65f);
        }

        private void monkSupportAbility() {
            World world = origin.getWorld();
            if (world == null || monkLeader == null) {
                return;
            }
            Location center = monkLeader.getLocation();
            LivingEntity healTarget = lowestHealthAlly(center, MONK_HEAL_RADIUS);
            if (healTarget != null) {
                double max = maxHealth(healTarget);
                healTarget.setHealth(Math.min(max, healTarget.getHealth() + 7.0D));
                healTarget.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, true, true));
                healTarget.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, healTarget.getLocation().add(0.0D, 1.0D, 0.0D), 8, 0.25D, 0.35D, 0.25D, 0.01D);
                world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.65f, 1.4f);
            }
        }

        private LivingEntity lowestHealthAlly(Location center, double radius) {
            LivingEntity best = null;
            double bestRatio = 1.01D;
            for (UUID id : chamberMobs) {
                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                    continue;
                }
                if (living.getLocation().distanceSquared(center) > radius * radius) {
                    continue;
                }
                double max = Math.max(1.0D, maxHealth(living));
                double ratio = living.getHealth() / max;
                if (ratio < bestRatio && living.getHealth() < max - 0.5D) {
                    bestRatio = ratio;
                    best = living;
                }
            }
            return best;
        }

        private void guardMonkLeader() {
            if (monkLeader == null || !monkLeader.isValid() || monkLeader.isDead()) {
                return;
            }
            LivingEntity supportTarget = lowestHealthAlly(monkLeader.getLocation(), 36.0D);
            if (supportTarget != null && !supportTarget.getUniqueId().equals(monkLeader.getUniqueId())) {
                double distanceSquared = monkLeader.getLocation().distanceSquared(supportTarget.getLocation());
                if (distanceSquared > 16.0D) {
                    if (monkLeader instanceof Mob monkMob) {
                        monkMob.setTarget(null);
                        Vector toward = supportTarget.getLocation().toVector().subtract(monkLeader.getLocation().toVector());
                        if (toward.lengthSquared() > 0.01D) {
                            monkMob.setVelocity(monkMob.getVelocity().add(toward.normalize().multiply(0.18D)));
                        }
                    }
                }
            }
            for (UUID id : new ArrayList<>(miniSkeletonGuards)) {
                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof Mob guard) || guard.isDead() || !guard.isValid()) {
                    miniSkeletonGuards.remove(id);
                    chamberMobs.remove(id);
                    continue;
                }
                double distanceSquared = guard.getLocation().distanceSquared(monkLeader.getLocation());
                if (distanceSquared > 100.0D) {
                    guard.teleport(safeSpawnNear(monkLeader.getLocation()));
                    guard.setTarget(null);
                } else if (distanceSquared > 36.0D) {
                    guard.setTarget(null);
                    Vector toward = monkLeader.getLocation().toVector().subtract(guard.getLocation().toVector());
                    if (toward.lengthSquared() > 0.01D) {
                        guard.setVelocity(guard.getVelocity().add(toward.normalize().multiply(0.22D)));
                    }
                }
            }
        }

        private void spawnVenomSpike() {
            World world = origin.getWorld();
            if (world == null || venomSpikeService == null) {
                return;
            }
            Player target = chooseHazardTarget(playersInside());
            if (target == null) {
                return;
            }
            Location spikeOrigin = hazardLocationNear(target.getLocation(), false);
            if (spikeOrigin == null || !contains(spikeOrigin)) {
                return;
            }
            if (isMobOccupying(spikeOrigin, 1.6D)) {
                return;
            }
            markHazardTarget(target);
            lastSpikeCastTick = tick;
            Location warning = spikeOrigin.clone().add(0.5D, 0.15D, 0.5D);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, warning, 26, 0.75D, 0.15D, 0.75D, 0.025D);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, warning, 18, 0.55D, 0.08D, 0.55D, 0.02D, Material.SLIME_BLOCK.createBlockData());
            world.spawnParticle(Particle.ITEM_SLIME, warning, 10, 0.45D, 0.08D, 0.45D, 0.02D);
            world.playSound(spikeOrigin, Sound.BLOCK_SLIME_BLOCK_PLACE, 0.7f, 0.55f);
            world.playSound(spikeOrigin, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.55f, 0.75f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeVenomRing == this && contains(spikeOrigin) && !isMobOccupying(spikeOrigin, 1.6D)) {
                    venomSpikeService.spawnSpikeAt(spikeOrigin);
                }
            }, SPIKE_WARNING_TICKS);
        }

        private boolean isMobOccupying(Location location, double radius) {
            World world = origin.getWorld();
            if (world == null || location == null) {
                return false;
            }
            for (Entity entity : world.getNearbyEntities(location, radius, 1.8D, radius)) {
                if (entity instanceof LivingEntity living && isChamberMob(living) && !living.isDead()) {
                    return true;
                }
            }
            return false;
        }

        private void placeTemporaryWebPatch(Location around) {
            TemporaryBlocks blocks = new TemporaryBlocks();
            Block feet = around.getBlock();
            blocks.trackAndSet(feet, Material.COBWEB);
            if (ThreadLocalRandom.current().nextBoolean()) {
                blocks.trackAndSet(feet.getRelative(1, 0, 0), Material.COBWEB);
            }
            if (ThreadLocalRandom.current().nextBoolean()) {
                blocks.trackAndSet(feet.getRelative(0, 0, 1), Material.COBWEB);
            }
            temporaryBlocks.add(blocks);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                blocks.restore();
                temporaryBlocks.remove(blocks);
            }, WEB_LIFETIME_TICKS);
        }

        private Player chooseHazardTarget(List<Player> candidates) {
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            List<Player> sorted = candidates.stream()
                .filter(player -> player != null && !player.isDead() && contains(player.getLocation()))
                .sorted(Comparator.comparing(Player::getUniqueId))
                .toList();
            if (sorted.isEmpty()) {
                return null;
            }
            if (sorted.size() == 1) {
                return sorted.getFirst();
            }
            for (Player player : sorted) {
                if (!recentHazardTargets.contains(player.getUniqueId())) {
                    return player;
                }
            }
            return sorted.get(ThreadLocalRandom.current().nextInt(sorted.size()));
        }

        private void markHazardTarget(Player player) {
            if (player == null) {
                return;
            }
            recentHazardTargets.remove(player.getUniqueId());
            recentHazardTargets.addLast(player.getUniqueId());
            while (recentHazardTargets.size() > Math.max(1, playersInside().size() - 1)) {
                recentHazardTargets.removeFirst();
            }
        }

        private Location hazardLocationNear(Location target, boolean webs) {
            if (target == null || target.getWorld() == null) {
                return null;
            }
            Vector facing = target.getDirection().clone();
            facing.setY(0.0D);
            if (facing.lengthSquared() < 0.01D) {
                facing = new Vector(1.0D, 0.0D, 0.0D);
            } else {
                facing.normalize();
            }
            Vector right = new Vector(-facing.getZ(), 0.0D, facing.getX()).normalize();
            Vector[] directions = {
                facing,
                right,
                right.clone().multiply(-1.0D),
                facing.clone().multiply(-1.0D),
                facing.clone().add(right).normalize(),
                facing.clone().subtract(right).normalize()
            };
            for (double distance = HAZARD_MIN_TARGET_OFFSET; distance <= HAZARD_MAX_TARGET_OFFSET; distance += 1.0D) {
                for (Vector direction : directions) {
                    Location candidate = target.clone().add(direction.clone().multiply(distance));
                    Location safe = webs ? safeWebLocation(candidate) : safeHazardGroundLocation(candidate);
                    if (safe != null && safe.distanceSquared(target) >= HAZARD_MIN_TARGET_OFFSET * HAZARD_MIN_TARGET_OFFSET) {
                        return safe;
                    }
                }
            }
            return null;
        }

        private Location safeWebLocation(Location desired) {
            Location ground = safeHazardGroundLocation(desired);
            if (ground == null) {
                return null;
            }
            Block block = ground.getBlock();
            if (!block.getType().isAir()) {
                return null;
            }
            return ground;
        }

        private Location safeHazardGroundLocation(Location desired) {
            World world = origin.getWorld();
            if (world == null || desired == null || desired.getWorld() == null || !desired.getWorld().equals(world)) {
                return null;
            }
            int x = desired.getBlockX();
            int z = desired.getBlockZ();
            int top = Math.min(world.getMaxHeight() - 2, desired.getBlockY() + 3);
            int bottom = Math.max(world.getMinHeight() + 1, desired.getBlockY() - 5);
            for (int y = top; y >= bottom; y--) {
                Location loc = new Location(world, x, y, z);
                if (!contains(loc)) {
                    continue;
                }
                Block floor = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                if (!floor.getType().isSolid() || !feet.getType().isAir() || !head.getType().isAir()) {
                    continue;
                }
                return new Location(world, x + 0.5D, y, z + 0.5D);
            }
            return null;
        }

        private void handleChamberMobDamage(EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof Player player) || !contains(player.getLocation())) {
                return;
            }
            LivingEntity source = chamberDamageSource(event.getDamager());
            if (source == null || !isChamberMob(source)) {
                return;
            }
            VenomMobRole role = mobRoles.get(source.getUniqueId());
            if (role == null) {
                return;
            }
            switch (role) {
                case VENOM_SKIRMISHER -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 45, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true, true));
                }
                case BURROWER, WAVE_ONE_BOGGED -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 35, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, false, true, true));
                }
                case WEB_CRAWLER -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 0, false, true, true));
                    if (ThreadLocalRandom.current().nextDouble() < 0.35D) {
                        Location web = hazardLocationNear(player.getLocation(), true);
                        if (web != null) {
                            placeTemporaryWebPatch(web);
                        }
                    }
                }
                case BILE_ARCHER -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 55, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 0, false, true, true));
                }
                case ROT_BRUTE -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
                    player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, player.getLocation().add(0.0D, 0.5D, 0.0D), 10, 0.7D, 0.25D, 0.7D, 0.02D);
                    for (Player nearby : playersInside()) {
                        if (!nearby.equals(player) && nearby.getLocation().distanceSquared(player.getLocation()) <= 9.0D) {
                            nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 0, false, true, true));
                        }
                    }
                }
            }
        }

        private void applyVenomFloorEffects() {
            for (Player player : playersInside()) {
                Block standingOn = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                Block feet = player.getLocation().getBlock();
                if (isVenomFloor(standingOn.getType()) || isVenomFloor(feet.getType())) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 45, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45, 0, false, true, true));
                }
            }
        }

        private boolean isVenomFloor(Material material) {
            return material == Material.SLIME_BLOCK || material == Material.LIME_STAINED_GLASS;
        }

        private LivingEntity chamberDamageSource(Entity damager) {
            if (damager instanceof LivingEntity living) {
                return living;
            }
            if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
                return living;
            }
            return null;
        }

        private Player chooseTarget(Mob mob) {
            if (monkLeader != null
                && mob.getUniqueId().equals(monkLeader.getUniqueId())
                && realMobCountExcludingMonk() > 0) {
                return null;
            }
            Player best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Player player : playersInside()) {
                double distance = Math.max(1.0D, Math.sqrt(player.getLocation().distanceSquared(mob.getLocation())));
                double damageThreat = playerThreat.getOrDefault(player.getUniqueId(), 0.0D);
                double lowHealth = Math.max(0.0D, 20.0D - player.getHealth()) * 0.35D;
                double leaderPressure = isNearLeader(player) ? 4.0D : 0.0D;
                long sinceTargeted = tick - lastTargetedTick.getOrDefault(player.getUniqueId(), 0L);
                double fatiguePenalty = sinceTargeted < 60 ? -7.0D : Math.min(5.0D, sinceTargeted / 80.0D);
                double score = damageThreat + lowHealth + leaderPressure + fatiguePenalty - (distance * 0.45D);
                if (score > bestScore) {
                    bestScore = score;
                    best = player;
                }
            }
            return best;
        }

        private boolean isNearLeader(Player player) {
            return (venomLeader != null && venomLeader.isValid() && player.getLocation().distanceSquared(venomLeader.getLocation()) <= 49.0D)
                || (monkLeader != null && monkLeader.isValid() && player.getLocation().distanceSquared(monkLeader.getLocation()) <= 49.0D);
        }

        private void updateLostMobGlow() {
            if (anyPlayerNearChamberMob()) {
                lastPlayerEngagementTick = tick;
                setMobGlow(false);
                return;
            }
            if (tick - lastPlayerEngagementTick >= LOST_MOB_GLOW_TICKS) {
                setMobGlow(true);
            }
        }

        private boolean anyPlayerNearChamberMob() {
            List<Player> players = playersInside();
            if (players.isEmpty()) {
                return false;
            }
            double radiusSquared = PLAYER_ENGAGEMENT_RADIUS * PLAYER_ENGAGEMENT_RADIUS;
            for (UUID id : new ArrayList<>(chamberMobs)) {
                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                    continue;
                }
                for (Player player : players) {
                    if (player.getLocation().distanceSquared(living.getLocation()) <= radiusSquared) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void setMobGlow(boolean glowing) {
            if (mobsGlowing == glowing) {
                return;
            }
            mobsGlowing = glowing;
            for (UUID id : new ArrayList<>(chamberMobs)) {
                Entity entity = Bukkit.getEntity(id);
                if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                    living.setGlowing(glowing);
                }
            }
        }

        private void keepInside(Mob mob) {
            if (contains(mob.getLocation())) {
                return;
            }
            Location fallback = safeFallbackSpawn();
            if (mob.getLocation().distanceSquared(fallback) > 225.0D) {
                mob.teleport(fallback);
            } else {
                Vector toward = fallback.toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.35D);
                mob.setVelocity(mob.getVelocity().add(toward));
            }
        }

        private void teleportPlayerOut(Player player) {
            Location exit = safeExitLocation(player);
            if (exit != null) {
                player.teleport(exit);
            }
        }

        private Location safeExitLocation(Player player) {
            World world = origin.getWorld();
            if (world == null) {
                return player.getWorld().getSpawnLocation();
            }
            for (int attempt = 0; attempt < 80; attempt++) {
                double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
                double radius = ThreadLocalRandom.current().nextDouble(90.0D, 145.0D);
                int x = (int) Math.round(Math.cos(angle) * radius);
                int z = (int) Math.round(Math.sin(angle) * radius);
                Location safe = safeSurfaceAt(world, x, z, player);
                if (safe != null) {
                    return safe;
                }
            }
            return safeSurfaceAt(world, 115, 0, player);
        }

        private Location safeSurfaceAt(World world, int x, int z, Player player) {
            int top = Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + 4);
            int bottom = Math.max(world.getMinHeight() + 1, top - 12);
            for (int y = top; y >= bottom; y--) {
                Block floor = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                if (!floor.getType().isSolid() || !feet.getType().isAir() || !head.getType().isAir()) {
                    continue;
                }
                return new Location(world, x + 0.5D, y, z + 0.5D, player.getLocation().getYaw(), player.getLocation().getPitch());
            }
            return null;
        }

        private void handleDeath(EntityDeathEvent event) {
            LivingEntity dead = event.getEntity();
            if (!chamberMobs.remove(dead.getUniqueId())) {
                return;
            }
            boolean wasMiniGuard = miniSkeletonGuards.contains(dead.getUniqueId());
            VenomMobRole deadRole = mobRoles.get(dead.getUniqueId());
            normalMobs.remove(dead.getUniqueId());
            miniSkeletonGuards.remove(dead.getUniqueId());
            mobRoles.remove(dead.getUniqueId());
            nextBurrowTick.remove(dead.getUniqueId());
            burrowReturnTick.remove(dead.getUniqueId());
            burrowHealth.remove(dead.getUniqueId());
            mobOutsideTicks.remove(dead.getUniqueId());
            if (dead.getUniqueId().equals(venomLeader == null ? null : venomLeader.getUniqueId())) {
                venomLeader = null;
                awardLeaderKill(event);
                maybeDropOminousTrialKey(event, 1.0D);
            } else if (dead.getUniqueId().equals(monkLeader == null ? null : monkLeader.getUniqueId())) {
                monkLeader = null;
                awardLeaderKill(event);
                maybeDropOminousTrialKey(event, 0.75D);
            } else if (wasMiniGuard) {
                return;
            } else {
                waveKills++;
                totalKills++;
                maybeDropTrialKey(event, deadRole);
            }
            if (isComplete()) {
                beginFinalRingChoice();
            }
        }

        private void addThreat(Player player, double amount) {
            if (player == null || !contains(player.getLocation())) {
                return;
            }
            playerThreat.merge(player.getUniqueId(), amount, (a, b) -> a + b);
        }

        private void maybeDropTrialKey(EntityDeathEvent event, VenomMobRole role) {
            double chance = currentWave.keyDropChance;
            if (role == VenomMobRole.ROT_BRUTE) {
                chance += 0.08D;
            }
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                event.getDrops().add(new ItemStack(Material.TRIAL_KEY));
            }
        }

        private void maybeDropOminousTrialKey(EntityDeathEvent event, double chance) {
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                event.getDrops().add(new ItemStack(Material.OMINOUS_TRIAL_KEY));
            }
        }

        private boolean isChamberMob(LivingEntity entity) {
            return entity != null && chamberMobs.contains(entity.getUniqueId());
        }

        private boolean isComplete() {
            if (currentWave != VenomWave.LEADER_WAVE) {
                return false;
            }
            boolean venomDead = venomLeader == null || venomLeader.isDead() || !venomLeader.isValid();
            boolean monkDead = monkLeader == null || monkLeader.isDead() || !monkLeader.isValid();
            return venomDead && monkDead && waveKills >= currentWave.killQuota && livingNormalMobCount() == 0;
        }

        private int realMobCountExcludingMonk() {
            int count = 0;
            for (UUID id : new ArrayList<>(chamberMobs)) {
                if (miniSkeletonGuards.contains(id) || id.equals(monkLeader == null ? null : monkLeader.getUniqueId())) {
                    continue;
                }
                Entity entity = Bukkit.getEntity(id);
                if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                    count++;
                }
            }
            return count;
        }

        private int livingNormalMobCount() {
            int alive = 0;
            for (UUID id : new ArrayList<>(normalMobs)) {
                Entity entity = Bukkit.getEntity(id);
                if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                    alive++;
                    continue;
                }
                normalMobs.remove(id);
                chamberMobs.remove(id);
            }
            return alive;
        }

        private int remainingMobCount() {
            int remaining = Math.max(Math.max(0, currentWave.killQuota - waveKills), livingNormalMobCount());
            if (venomLeader != null && venomLeader.isValid() && !venomLeader.isDead()) {
                remaining++;
            }
            if (monkLeader != null && monkLeader.isValid() && !monkLeader.isDead()) {
                remaining++;
            }
            return remaining;
        }

        private void updateBossBar() {
            int remaining = remainingMobCount();
            bossBar.setTitle(ChatColor.DARK_GREEN + "[Combat Chamber] " + ChatColor.WHITE + remaining + " mobs remaining");
            int waveTotal = currentWave.killQuota + (currentWave == VenomWave.LEADER_WAVE ? 2 : 0);
            bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, remaining / (double) Math.max(1, waveTotal))));
            List<Player> inside = playersInside();
            for (Player player : inside) {
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
            for (Player player : new ArrayList<>(bossBar.getPlayers())) {
                if (!inside.contains(player)) {
                    bossBar.removePlayer(player);
                }
            }
        }

        private void updateChamberScoreboards() {
            List<Player> inside = playersInside();
            for (Player player : inside) {
                previousScoreboards.putIfAbsent(player.getUniqueId(), player.getScoreboard());
                Scoreboard board = chamberScoreboard(player);
                if (board != null) {
                    reapplyNpcNameHiding(board);
                    player.setScoreboard(board);
                }
            }
            for (UUID id : new HashSet<>(previousScoreboards.keySet())) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !inside.contains(player)) {
                    restoreScoreboard(id, player);
                }
            }
        }

        private Scoreboard chamberScoreboard(Player player) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) {
                return null;
            }
            Scoreboard board = manager.getNewScoreboard();
            Objective objective = board.registerNewObjective(
                CHAMBER_SCOREBOARD_OBJECTIVE,
                Criteria.DUMMY,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "COMBAT CHAMBER"
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.getScore(ChatColor.AQUA + "Wave: " + ChatColor.WHITE + currentWave.number + "/4").setScore(5);
            objective.getScore(ChatColor.GREEN + "Kills: " + ChatColor.WHITE + totalKills).setScore(4);
            objective.getScore(ChatColor.DARK_GRAY + "------------").setScore(3);
            objective.getScore(ChatColor.YELLOW + "Mobs Left: " + ChatColor.WHITE + remainingMobCount()).setScore(2);
            objective.getScore(ChatColor.DARK_GREEN + "Players: " + ChatColor.WHITE + playersInside().size()).setScore(1);
            return board;
        }

        private void reapplyNpcNameHiding(Scoreboard board) {
            if (!(plugin instanceof HGLootPlugin hydra) || hydra.npcService() == null) {
                return;
            }
            hydra.npcService().applyHiddenNameTeam(board);
        }

        private void restoreScoreboard(UUID id, Player player) {
            Scoreboard previous = previousScoreboards.remove(id);
            if (player == null) {
                return;
            }
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            player.setScoreboard(previous != null ? previous : manager == null ? player.getScoreboard() : manager.getMainScoreboard());
        }

        private void completeEncounterOnly() {
            if (completed) {
                return;
            }
            completed = true;
            World world = origin.getWorld();
            if (world != null) {
                world.playSound(center(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                for (Player player : playersInside()) {
                    player.sendMessage(ChatColor.GREEN + "[Combat Chamber] Venom Ring cleared.");
                }
            }
            cleanup(false, false);
            activeVenomRing = this;
        }

        private void cleanup(boolean keepDead) {
            cleanup(keepDead, false);
        }

        private void cleanup(boolean keepDead, boolean restoreRing) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            awaitingReinforcementChoice = false;
            promptDisplayed = false;
            awaitingFinalChoice = false;
            finalPromptDisplayed = false;
            promptGeneration++;
            setMobGlow(false);
            for (TemporaryBlocks blocks : new ArrayList<>(temporaryBlocks)) {
                blocks.restore();
            }
            temporaryBlocks.clear();
            if (!keepDead) {
                for (UUID id : new ArrayList<>(chamberMobs)) {
                    Entity entity = Bukkit.getEntity(id);
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                }
            }
            chamberMobs.clear();
            normalMobs.clear();
            miniSkeletonGuards.clear();
            mobRoles.clear();
            mobOutsideTicks.clear();
            nextBurrowTick.clear();
            burrowReturnTick.clear();
            burrowHealth.clear();
            bossBar.removeAll();
            for (UUID id : new HashSet<>(previousScoreboards.keySet())) {
                restoreScoreboard(id, Bukkit.getPlayer(id));
            }
            if (restoreRing) {
                restoreSnapshots(ringSnapshots);
            }
        }

        private void registerMob(LivingEntity entity) {
            chamberMobs.add(entity.getUniqueId());
            entity.setGlowing(mobsGlowing);
        }

        private List<Player> playersInside() {
            World world = origin.getWorld();
            if (world == null) {
                return List.of();
            }
            List<Player> players = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                if (!player.isDead() && !escapedPlayers.contains(player.getUniqueId()) && contains(player.getLocation())) {
                    players.add(player);
                }
            }
            return players;
        }

        private void handleTransitionMove(Player player, Location to) {
            if (player == null || to == null || !contains(to)) {
                return;
            }
            TransitionZone zone = transitionZoneAt(to);
            if (zone == null) {
                return;
            }
            UUID id = player.getUniqueId();
            if (transitionCooldowns.getOrDefault(id, 0) > tick) {
                return;
            }
            if (!progressionUnlocked) {
                denyTransition(player, zone);
                return;
            }
            transitionCooldowns.put(id, tick + TRANSITION_COOLDOWN_TICKS);
            Location destination = transitionCenter(zone == TransitionZone.ONE ? TransitionZone.TWO : TransitionZone.ONE);
            Location safe = safeSpawnNear(destination);
            if (safe == null) {
                safe = destination;
            }
            player.teleport(safe);
            player.sendActionBar(Component.text("The next ring pulls at you...", NamedTextColor.DARK_PURPLE));
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 0.65f);
            world.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), 45, 0.8D, 0.8D, 0.8D, 0.08D);
        }

        private void denyTransition(Player player, TransitionZone zone) {
            UUID id = player.getUniqueId();
            if (transitionWarningCooldowns.getOrDefault(id, 0) <= tick) {
                transitionWarningCooldowns.put(id, tick + TRANSITION_WARNING_COOLDOWN_TICKS);
                player.sendActionBar(Component.text("The chamber still holds you here.", NamedTextColor.DARK_GREEN));
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 0.45f);
            }
            Vector push = player.getLocation().toVector().subtract(transitionCenter(zone).toVector());
            if (push.lengthSquared() < 0.01D) {
                push = center().toVector().subtract(transitionCenter(zone).toVector());
            }
            player.setVelocity(push.normalize().multiply(0.35D).setY(0.12D));
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.5D, 0.6D, 0.5D, 0.04D);
        }

        private TransitionZone transitionZoneAt(Location location) {
            if (isInsideTransition(location, TRANSITION_CENTER_ONE)) {
                return TransitionZone.ONE;
            }
            if (isInsideTransition(location, TRANSITION_CENTER_TWO)) {
                return TransitionZone.TWO;
            }
            return null;
        }

        private boolean isInsideTransition(Location location, Vector localCenter) {
            Location center = transitionCenter(localCenter);
            return Math.abs(location.getX() - center.getX()) <= TRANSITION_HALF_X
                && Math.abs(location.getY() - center.getY()) <= TRANSITION_HALF_Y
                && Math.abs(location.getZ() - center.getZ()) <= TRANSITION_HALF_Z;
        }

        private Location transitionCenter(TransitionZone zone) {
            return transitionCenter(zone == TransitionZone.ONE ? TRANSITION_CENTER_ONE : TRANSITION_CENTER_TWO);
        }

        private Location transitionCenter(Vector localCenter) {
            return origin.clone().add(localCenter);
        }

        private boolean contains(Location location) {
            if (location == null || location.getWorld() == null || origin.getWorld() == null || !location.getWorld().equals(origin.getWorld())) {
                return false;
            }
            return location.getX() >= origin.getX()
                && location.getX() <= origin.getX() + Math.max(1, size.getBlockX())
                && location.getY() >= origin.getY() - 3.0D
                && location.getY() <= origin.getY() + Math.max(4, size.getBlockY()) + 4.0D
                && location.getZ() >= origin.getZ()
                && location.getZ() <= origin.getZ() + Math.max(1, size.getBlockZ());
        }

        private Location center() {
            return origin.clone().add(
                Math.max(1.0D, size.getBlockX()) * 0.5D,
                1.0D,
                Math.max(1.0D, size.getBlockZ()) * 0.5D
            );
        }
    }

    private enum TransitionZone {
        ONE,
        TWO
    }

    private void setHealth(LivingEntity entity, double health) {
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        }
        entity.setHealth(Math.min(health, maxHealth(entity)));
    }

    private void setSpeed(Monster monster, double speed) {
        if (monster.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            monster.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    private void setScale(LivingEntity entity, double scale) {
        if (entity.getAttribute(Attribute.SCALE) != null) {
            entity.getAttribute(Attribute.SCALE).setBaseValue(scale);
        }
    }

    private double maxHealth(LivingEntity entity) {
        return entity.getAttribute(Attribute.MAX_HEALTH) == null
            ? entity.getHealth()
            : entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private static final class TemporaryBlocks {
        private final List<BlockSnapshot> snapshots = new ArrayList<>();

        private void trackAndSet(Block block, Material material) {
            if (block == null || block.getType() == material) {
                return;
            }
            if (!block.getType().isAir() && block.getType() != Material.SHORT_GRASS && block.getType() != Material.TALL_GRASS && block.getType() != Material.FERN) {
                return;
            }
            BlockSnapshot snapshot = new BlockSnapshot(block, block.getState(), null);
            snapshots.add(snapshot);
            block.setType(material, false);
            snapshot.placed = block.getState();
        }

        private void restore() {
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot.matchesPlaced()) {
                    snapshot.before.update(true, false);
                }
            }
        }
    }

    private static final class BlockSnapshot {
        private final Block block;
        private final BlockState before;
        private BlockState placed;

        private BlockSnapshot(Block block, BlockState before, BlockState placed) {
            this.block = block;
            this.before = before;
            this.placed = placed;
        }

        private boolean matchesPlaced() {
            return placed == null
                || (block.getType() == placed.getType() && block.getBlockData().matches(placed.getBlockData()));
        }
    }
}
