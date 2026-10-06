package hgloot.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

public final class VenomSpikeService {
    private static final String BUNDLED_SLIME_SPIKE_RESOURCE = "venom_spikes/slime_spike.nbt";
    private static final String SLIME_SPIKE_FILE = "slime_spike.nbt";
    private static final int LIFETIME_TICKS = 100;
    private static final int HAZARD_INTERVAL_TICKS = 5;
    private static final double METABALL_RADIUS = 2.0D;
    private static final double METABALL_THRESHOLD = 1.0D;
    private static final double VISUAL_BLEND_THRESHOLD = 0.72D;

    private final JavaPlugin plugin;
    private final List<ActiveSpike> activeSpikes = new ArrayList<>();
    private Structure cachedSpike;

    public VenomSpikeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        for (ActiveSpike spike : new ArrayList<>(activeSpikes)) {
            spike.cleanup();
        }
        activeSpikes.clear();
    }

    public boolean spawnTestSpike(Player player) {
        if (player == null) {
            return false;
        }
        Structure structure = loadSpikeStructure();
        if (structure == null) {
            player.sendMessage(Component.text("[Venom Spike] Could not load Slime spike NBT.").color(NamedTextColor.RED));
            return false;
        }

        Location origin = resolveFloorPlacement(player);
        if (origin == null || origin.getWorld() == null) {
            player.sendMessage(Component.text("[Venom Spike] Could not find a floor placement.").color(NamedTextColor.RED));
            return false;
        }

        ActiveSpike spike = new ActiveSpike(origin, structure.getSize());
        spike.captureBefore();
        try {
            structure.place(origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
        } catch (Exception ex) {
            plugin.getLogger().warning("[Venom Spike] Failed placing test spike: " + ex.getMessage());
            spike.cleanup();
            player.sendMessage(Component.text("[Venom Spike] Placement failed. Check console.").color(NamedTextColor.RED));
            return false;
        }
        spike.capturePlaced();
        spike.applyVisualBlend();
        spike.startHazard();
        activeSpikes.add(spike);

        World world = origin.getWorld();
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, origin.clone().add(0.5D, 0.2D, 0.5D), 18, 0.6D, 0.2D, 0.6D, 0.03D);
        world.playSound(origin, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 0.9f, 0.75f);
        player.sendMessage(Component.text("[Venom Spike] Test spike spawned.").color(NamedTextColor.GREEN));
        return true;
    }

    public boolean spawnSpikeAt(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return false;
        }
        Structure structure = loadSpikeStructure();
        if (structure == null) {
            return false;
        }
        ActiveSpike spike = new ActiveSpike(origin.getBlock().getLocation(), structure.getSize());
        spike.captureBefore();
        try {
            structure.place(spike.origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, ThreadLocalRandom.current());
        } catch (Exception ex) {
            plugin.getLogger().warning("[Venom Spike] Failed placing chamber spike: " + ex.getMessage());
            spike.cleanup();
            return false;
        }
        spike.capturePlaced();
        spike.applyVisualBlend();
        spike.startHazard();
        activeSpikes.add(spike);

        World world = origin.getWorld();
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, origin.clone().add(0.5D, 0.2D, 0.5D), 14, 0.55D, 0.2D, 0.55D, 0.03D);
        world.playSound(origin, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 0.7f, 0.75f);
        return true;
    }

    private Structure loadSpikeStructure() {
        if (cachedSpike != null) {
            return cachedSpike;
        }
        Path path = resolveSpikePath();
        if (path == null) {
            return null;
        }
        try {
            cachedSpike = Bukkit.getStructureManager().loadStructure(path.toFile());
            plugin.getLogger().info("[Venom Spike] Loaded test spike NBT from " + path + ".");
            return cachedSpike;
        } catch (IOException ex) {
            plugin.getLogger().warning("[Venom Spike] Failed loading " + path + ": " + ex.getMessage());
            return null;
        }
    }

    private Path resolveSpikePath() {
        Path bundled = plugin.getDataFolder().toPath().resolve("venom_spikes").resolve(SLIME_SPIKE_FILE);
        if (!Files.isRegularFile(bundled)) {
            try {
                plugin.saveResource(BUNDLED_SLIME_SPIKE_RESOURCE, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[Venom Spike] Bundled Slime spike resource is missing: " + BUNDLED_SLIME_SPIKE_RESOURCE);
            }
        }
        if (Files.isRegularFile(bundled)) {
            return bundled;
        }

        return null;
    }

    private Location resolveFloorPlacement(Player player) {
        Block target = player.getTargetBlockExact(80);
        if (target == null || target.getType().isAir()) {
            Location base = player.getLocation().getBlock().getLocation();
            return base.add(0.0D, 0.0D, 0.0D);
        }
        Location place = target.getLocation();
        if (target.getType().isSolid()) {
            place.add(0.0D, 1.0D, 0.0D);
        }
        return place;
    }

    private final class ActiveSpike {
        private final Location origin;
        private final BlockVector size;
        private final List<BlockSnapshot> snapshots = new ArrayList<>();
        private BukkitTask hazardTask;
        private BukkitTask cleanupTask;

        private ActiveSpike(Location origin, BlockVector size) {
            this.origin = origin.getBlock().getLocation();
            this.size = size;
        }

        private void captureBefore() {
            forEachBlock((block, index) -> snapshots.add(new BlockSnapshot(block, block.getState(), null)));
        }

        private void capturePlaced() {
            for (BlockSnapshot snapshot : snapshots) {
                snapshot.placed = snapshot.block.getState();
            }
        }

        private void startHazard() {
            hazardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHazard, 0L, HAZARD_INTERVAL_TICKS);
            cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, LIFETIME_TICKS);
        }

        private void applyVisualBlend() {
            World world = origin.getWorld();
            if (world == null) {
                return;
            }
            Location center = hazardCenter();
            List<MetaballNode> nodes = metaballNodes(center);
            int radius = (int) Math.ceil(METABALL_RADIUS + 1.0D);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -1; y <= 1; y++) {
                        Block block = world.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z
                        );
                        Location sample = block.getLocation().add(0.5D, 0.65D, 0.5D);
                        double field = venomMetaballField(sample, nodes);
                        if (field < VISUAL_BLEND_THRESHOLD) {
                            continue;
                        }
                        blendBlock(block, field);
                    }
                }
            }
        }

        private void blendBlock(Block block, double field) {
            if (block == null || isAlreadyTracked(block)) {
                return;
            }
            Material type = block.getType();
            if (type.isAir()) {
                Block below = block.getRelative(BlockFace.DOWN);
                if (!below.getType().isSolid() || !isReplaceableBlendBlock(type)) {
                    return;
                }
                trackAndSet(block, Material.LIME_CARPET);
                return;
            }
            if (!isBlendableSurfaceBlock(type)) {
                return;
            }
            Material coating = field > 1.45D ? Material.SLIME_BLOCK : Material.MOSS_BLOCK;
            if (type == Material.SAND || type == Material.RED_SAND || type == Material.GRAVEL) {
                coating = Material.LIME_TERRACOTTA;
            } else if (type == Material.STONE || type == Material.DEEPSLATE || type == Material.BLACKSTONE || type == Material.COBBLESTONE) {
                coating = Material.MOSSY_COBBLESTONE;
            }
            trackAndSet(block, coating);
        }

        private boolean isAlreadyTracked(Block block) {
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot.block.equals(block)) {
                    return true;
                }
            }
            return false;
        }

        private void trackAndSet(Block block, Material material) {
            BlockSnapshot snapshot = new BlockSnapshot(block, block.getState(), null);
            snapshots.add(snapshot);
            block.setType(material, false);
            snapshot.placed = block.getState();
        }

        private boolean isReplaceableBlendBlock(Material type) {
            return type.isAir()
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.FERN
                || type == Material.DEAD_BUSH
                || type == Material.SNOW;
        }

        private boolean isBlendableSurfaceBlock(Material type) {
            return type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT
                || type == Material.PODZOL
                || type == Material.MUD
                || type == Material.MOSS_BLOCK
                || type == Material.SAND
                || type == Material.RED_SAND
                || type == Material.GRAVEL
                || type == Material.STONE
                || type == Material.COBBLESTONE
                || type == Material.DEEPSLATE
                || type == Material.BLACKSTONE;
        }

        private void tickHazard() {
            World world = origin.getWorld();
            if (world == null) {
                cleanup();
                return;
            }
            Location center = hazardCenter();
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 5, 0.65D, 0.35D, 0.65D, 0.01D);
            List<MetaballNode> nodes = metaballNodes(center);
            for (Player player : world.getPlayers()) {
                if (player.isDead() || player.isInvulnerable()) {
                    continue;
                }
                Location loc = player.getLocation();
                if (venomMetaballField(loc, nodes) < METABALL_THRESHOLD) {
                    continue;
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 45, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 0, false, true, true));
                if (player.getNoDamageTicks() <= 0) {
                    player.damage(1.0D);
                }
            }
        }

        private List<MetaballNode> metaballNodes(Location center) {
            return List.of(
                new MetaballNode(center, 1.0D),
                new MetaballNode(center.clone().add(0.55D, -0.08D, 0.15D), 0.48D),
                new MetaballNode(center.clone().add(-0.35D, 0.05D, -0.45D), 0.42D),
                new MetaballNode(center.clone().add(0.05D, 0.48D, -0.1D), 0.35D)
            );
        }

        private double venomMetaballField(Location location, List<MetaballNode> nodes) {
            double field = 0.0D;
            double radiusSq = METABALL_RADIUS * METABALL_RADIUS;
            for (MetaballNode node : nodes) {
                double dx = location.getX() - node.center.getX();
                double dy = (location.getY() + 0.8D - node.center.getY()) * 1.35D;
                double dz = location.getZ() - node.center.getZ();
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq > radiusSq) {
                    continue;
                }
                field += (radiusSq * node.weight) / Math.max(0.25D, distSq);
            }
            return field;
        }

        private Location hazardCenter() {
            double x = Math.max(1.0D, size.getBlockX()) * 0.5D;
            double y = Math.max(1.0D, size.getBlockY()) * 0.45D;
            double z = Math.max(1.0D, size.getBlockZ()) * 0.5D;
            return origin.clone().add(x, y, z);
        }

        private void cleanup() {
            if (hazardTask != null) {
                hazardTask.cancel();
                hazardTask = null;
            }
            if (cleanupTask != null) {
                cleanupTask.cancel();
                cleanupTask = null;
            }
            for (BlockSnapshot snapshot : snapshots) {
                if (!snapshot.matchesPlaced()) {
                    continue;
                }
                snapshot.before.update(true, false);
            }
            activeSpikes.remove(this);
            World world = origin.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.SMOKE, hazardCenter(), 12, 0.55D, 0.3D, 0.55D, 0.02D);
                world.playSound(origin, Sound.BLOCK_TRIAL_SPAWNER_BREAK, 0.5f, 1.35f);
            }
        }

        private void forEachBlock(BlockConsumer consumer) {
            World world = origin.getWorld();
            if (world == null) {
                return;
            }
            int index = 0;
            int maxX = Math.max(1, size.getBlockX());
            int maxY = Math.max(1, size.getBlockY());
            int maxZ = Math.max(1, size.getBlockZ());
            for (int x = 0; x < maxX; x++) {
                for (int y = 0; y < maxY; y++) {
                    for (int z = 0; z < maxZ; z++) {
                        consumer.accept(world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z), index++);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface BlockConsumer {
        void accept(Block block, int index);
    }

    private record MetaballNode(Location center, double weight) {
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
            if (placed == null) {
                return true;
            }
            BlockState current = block.getState();
            return current.getType() == placed.getType()
                && current.getBlockData().matches(placed.getBlockData());
        }
    }
}
