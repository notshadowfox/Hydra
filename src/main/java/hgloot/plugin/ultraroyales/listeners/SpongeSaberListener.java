package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.TeamProtectionService;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.util.HydraIcons;
import hgloot.plugin.ultraroyales.LegendaryModeRules;
import hgloot.plugin.ultraroyales.UltraRoyales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SpongeSaberListener implements Listener {
    public static final NamespacedKey SPONGE_POWER = new NamespacedKey(UltraRoyales.getPlugin(), "sponge_power");
    public static final NamespacedKey SPONGE_PROGRESS = new NamespacedKey(UltraRoyales.getPlugin(), "sponge_progress");
    public static final int MAX_POWER = 12;
    private static final int WATER_SCAN_RADIUS = 8;
    private static final int WATER_SCAN_VERTICAL_RANGE = 4;
    private static final int PASSIVE_NEAR_WATER_MS = 3000;
    private static final int PASSIVE_IN_WATER_MS = 2000;
    private static final int ABSORB_WATER_GAIN = 3;
    private static final int ABSORB_COOLDOWN_MS = 4000;
    private static final int WAVE_COOLDOWN_SECONDS = 6;
    private static final double WAVE_STEP_BLOCKS = 0.75;
    private static final double HUNGER_GAMES_MELEE_DAMAGE = 7.5D;
    private static final double MAX_WATER_MELEE_DAMAGE = 9.0D;
    private static final String SPONGE_SABER_DRY_MODEL_ID = "ultraroyales:sponge_saber";
    private static final String SPONGE_SABER_WET_MODEL_ID = "ultraroyales:wet_sponge_saber";
    private static final String SPONGE_SABER_WATER_MODEL_ID = "ultraroyales:active_sponge_saber";
    private static BukkitTask hudTask;
    private static final Set<UUID> hudActive = new HashSet<>();
    private static final Map<UUID, Long> nextAmbientAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> nextPassiveWaterAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> nextAbsorbAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> liveSpongePower = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> liveSpongeProgress = new ConcurrentHashMap<>();

    public SpongeSaberListener() {
        startHudTask();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isSpongeSaber(item)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (player.isSneaking()) {
            absorbWater(player, item);
        } else {
            castWaveSlash(player, item);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!LegendaryModeRules.isHungerGamesWorld(attacker.getWorld())) {
            return;
        }
        if (!isSpongeSaber(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        ItemStack item = attacker.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            event.setDamage(HUNGER_GAMES_MELEE_DAMAGE);
            return;
        }

        int currentPower = getPower(attacker, meta);
        double waterRatio = currentPower / (double) MAX_POWER;
        event.setDamage(HUNGER_GAMES_MELEE_DAMAGE + ((MAX_WATER_MELEE_DAMAGE - HUNGER_GAMES_MELEE_DAMAGE) * waterRatio));
        if (currentPower < MAX_POWER) {
            addWater(attacker, item, 1, true);
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (isSpongeSaber(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getFirstItem();
        if (isSpongeSaber(base)) {
            event.setResult(null);
        }
    }

    public static void absorb(Player player, ItemStack item) {
        absorbWater(player, item);
    }

    public static void collect(Player player, ItemStack item) {
        addWater(player, item, 1, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restoreXp(event.getPlayer());
        nextAmbientAt.remove(event.getPlayer().getUniqueId());
        nextPassiveWaterAt.remove(event.getPlayer().getUniqueId());
        nextAbsorbAt.remove(event.getPlayer().getUniqueId());
        liveSpongePower.remove(event.getPlayer().getUniqueId());
        liveSpongeProgress.remove(event.getPlayer().getUniqueId());
    }

    private static void startHudTask() {
        if (hudTask != null && !hudTask.isCancelled()) {
            return;
        }
        hudTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    if (!isSpongeSaber(mainHand)) {
                        restoreXp(player);
                        nextAmbientAt.remove(player.getUniqueId());
                        nextPassiveWaterAt.remove(player.getUniqueId());
                        nextAbsorbAt.remove(player.getUniqueId());
                        continue;
                    }

                    ItemMeta meta = mainHand.getItemMeta();
                    if (meta == null) {
                        restoreXp(player);
                        nextAmbientAt.remove(player.getUniqueId());
                        nextPassiveWaterAt.remove(player.getUniqueId());
                        nextAbsorbAt.remove(player.getUniqueId());
                        continue;
                    }

                    updatePassiveWater(player, mainHand, meta);
                    ItemStack liveHand = player.getInventory().getItemInMainHand();
                    meta = liveHand == null ? null : liveHand.getItemMeta();
                    if (meta != null) {
                        updateSpongeSaberVisualState(player, liveHand, meta);
                    }
                    showSaberHud(player, meta == null ? 0 : getPower(player, meta));
                    playAmbientIfDue(player);
                }
            }
        }.runTaskTimer(UltraRoyales.getPlugin(), 1L, 4L);
    }

    private static void setSaberState(Player player, ItemStack source, int power, int progress) {
        if (player == null || source == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        liveSpongePower.put(playerId, Math.max(0, Math.min(MAX_POWER, power)));
        liveSpongeProgress.put(playerId, Math.max(0, progress));

        ItemStack liveItem = player.getInventory().getItemInMainHand();
        if (isSpongeSaber(liveItem)) {
            ItemMeta liveMeta = liveItem.getItemMeta();
            if (liveMeta != null) {
                updateSpongeSaberVisualState(player, liveItem, liveMeta);
            }
        }
        showSaberHud(player, power);
    }

    private static void showSaberHud(Player player, int power) {
        int clampedPower = Math.max(0, Math.min(MAX_POWER, power));
        Component hud = HydraIcons.spongeSaberWaterBar(clampedPower);
        int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.saberCooldown);
        if (cooldown > 0) {
            hud = hud.append(Component.text("  CD " + cooldown + "s", NamedTextColor.AQUA));
        }
        UltraRoyales.holdBossRushDamageActionBar(player, 1500L);
        player.sendActionBar(hud);
    }

    private static void restoreXp(Player player) {
        if (!hudActive.remove(player.getUniqueId())) {
            return;
        }
        player.sendExperienceChange(player.getExp(), player.getLevel());
    }

    private static boolean isSpongeSaber(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelDataComponent()) {
            return false;
        }

        return meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:sponge_saber")
            || meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:dry_sponge_saber")
            || meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:wet_sponge_saber")
            || meta.getCustomModelDataComponent().getStrings().contains("ultraroyales:active_sponge_saber");
    }

    private static void updateSpongeSaberVisualState(Player player, ItemStack item, ItemMeta meta) {
        if (player == null || item == null || meta == null) {
            return;
        }
        if (!meta.hasCustomModelDataComponent()) {
            return;
        }

        int power = getPower(player, meta);
        int progress = getProgress(player, meta);
        String desiredModel = desiredSpongeSaberModel(power);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        List<String> models = customModelData.getStrings();
        if (models.contains(desiredModel)) {
            return;
        }

        customModelData.setStrings(List.of(desiredModel));
        meta.setCustomModelDataComponent(customModelData);
        writeSpongeStateToItem(meta, power, progress);
        item.setItemMeta(meta);
    }

    private static String desiredSpongeSaberModel(int power) {
        if (power >= 8) {
            return SPONGE_SABER_WATER_MODEL_ID;
        }
        if (power >= 4) {
            return SPONGE_SABER_WET_MODEL_ID;
        }
        return SPONGE_SABER_DRY_MODEL_ID;
    }

    private static void absorbWater(Player player, ItemStack item) {
        if (player == null || item == null) {
            return;
        }
        if (!isNearWater(player) && !player.isInWater() && !player.isSwimming()) {
            UltraRoyales.holdBossRushDamageActionBar(player, 2000L);
            player.sendActionBar(Component.text("No nearby water", NamedTextColor.AQUA));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.75f);
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextAbsorbAt.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            double seconds = (next - now) / 1000.0D;
            UltraRoyales.holdBossRushDamageActionBar(player, 2000L);
            player.sendActionBar(Component.text("Absorb CD " + Math.max(1, (int) Math.ceil(seconds)) + "s", NamedTextColor.AQUA));
            return;
        }

        addWater(player, item, ABSORB_WATER_GAIN, true);
        nextAbsorbAt.put(player.getUniqueId(), now + ABSORB_COOLDOWN_MS);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.75f, 1.25f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.55f, 1.5f);
        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.55D, 0.5D, 0.55D, 0.05D);
        player.getWorld().spawnParticle(Particle.BUBBLE_POP, player.getLocation().add(0.0D, 1.0D, 0.0D), 14, 0.45D, 0.45D, 0.45D, 0.03D);
    }

    private static void updatePassiveWater(Player player, ItemStack item, ItemMeta meta) {
        boolean inWater = player.isSwimming() || player.isInWater();
        boolean nearWater = inWater || isNearWater(player);
        if (!nearWater) {
            nextPassiveWaterAt.remove(player.getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        long interval = inWater ? PASSIVE_IN_WATER_MS : PASSIVE_NEAR_WATER_MS;
        long nextGain = nextPassiveWaterAt.getOrDefault(player.getUniqueId(), now + interval);
        if (now < nextGain) {
            nextPassiveWaterAt.putIfAbsent(player.getUniqueId(), nextGain);
            return;
        }
        addWater(player, item, 1, false);
        nextPassiveWaterAt.put(player.getUniqueId(), now + interval);
    }

    private static boolean isNearWater(Player player) {
        Location center = player.getLocation();
        int radiusSquared = WATER_SCAN_RADIUS * WATER_SCAN_RADIUS;

        for (int x = -WATER_SCAN_RADIUS; x <= WATER_SCAN_RADIUS; x++) {
            for (int y = -WATER_SCAN_VERTICAL_RANGE; y <= WATER_SCAN_VERTICAL_RANGE; y++) {
                for (int z = -WATER_SCAN_RADIUS; z <= WATER_SCAN_RADIUS; z++) {
                    if ((x * x) + (z * z) > radiusSquared) {
                        continue;
                    }

                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();
                    if (isWaterSource(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isWaterSource(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
            return true;
        }

        if (type == Material.WATER_CAULDRON) {
            return true;
        }

        BlockData data = block.getBlockData();
        return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static void addWater(Player player, ItemStack item, int waterGain, boolean feedback) {
        if (player == null || item == null || waterGain <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int power = Math.min(MAX_POWER, getPower(player, meta) + waterGain);
        setSaberState(player, item, power, 0);
        if (feedback) {
            player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.25D, 0.25D, 0.25D, 0.03D);
        }
    }

    private static int getPower(Player player, ItemMeta meta) {
        if (player != null) {
            Integer livePower = liveSpongePower.get(player.getUniqueId());
            if (livePower != null) {
                return Math.max(0, livePower);
            }
        }
        Integer storedPower = meta.getPersistentDataContainer().get(SPONGE_POWER, PersistentDataType.INTEGER);
        return storedPower == null ? 0 : Math.max(0, storedPower);
    }

    private static int getProgress(Player player, ItemMeta meta) {
        if (player != null) {
            Integer liveProgress = liveSpongeProgress.get(player.getUniqueId());
            if (liveProgress != null) {
                return Math.max(0, liveProgress);
            }
        }
        Integer storedProgress = meta.getPersistentDataContainer().get(SPONGE_PROGRESS, PersistentDataType.INTEGER);
        return storedProgress == null ? 0 : Math.max(0, storedProgress);
    }

    private static void writeSpongeStateToItem(ItemMeta meta, int power, int progress) {
        if (power > 0) {
            meta.getPersistentDataContainer().set(SPONGE_POWER, PersistentDataType.INTEGER, power);
        } else {
            meta.getPersistentDataContainer().remove(SPONGE_POWER);
        }
        if (progress > 0) {
            meta.getPersistentDataContainer().set(SPONGE_PROGRESS, PersistentDataType.INTEGER, progress);
        } else {
            meta.getPersistentDataContainer().remove(SPONGE_PROGRESS);
        }
    }

    private static void forceWriteSpongeState(ItemStack item, int power, int progress) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        writeSpongeStateToItem(meta, power, progress);
        item.setItemMeta(meta);
    }

    private static void castWaveSlash(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        double cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.saberCooldown);
        if (cooldown > 0) {
            showSaberHud(player, getPower(player, meta));
            return;
        }
        int currentPower = getPower(player, meta);
        WaveTier tier = WaveTier.fromWater(currentPower);
        CooldownRegistry.setCooldown(player, UltraRoyales.saberCooldown, WAVE_COOLDOWN_SECONDS, false);
        setSaberState(player, item, 0, 0);
        forceWriteSpongeState(item, 0, 0);

        Location origin = waveSurfaceLocation(player.getLocation());
        Vector facing = player.getLocation().getDirection().clone().setY(0.0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = player.getLocation().getDirection().clone();
        }
        final Vector forward = facing.normalize();
        Vector side = new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
        final Vector right = side;
        final double waveRange = tier.range;
        final double hitRadius = tier.hitRadius;
        final double damage = tier.damage;
        final double knockback = tier.knockback;
        final double waveScale = tier.visualScale;
        final int waveTicks = Math.max(4, (int) Math.ceil(waveRange / WAVE_STEP_BLOCKS));

        if (tier.selfPushback > 0) {
            player.setVelocity(player.getVelocity().add(forward.clone().multiply(-tier.selfPushback).setY(0.08D)));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_DROWNED_SHOOT, tier.soundVolume, tier.soundPitch);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, tier.soundVolume, 0.95f);

        Set<UUID> hitTargets = new HashSet<>();
        new BukkitRunnable() {
            private int tick;
            private double traveled;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                tick++;
                traveled += WAVE_STEP_BLOCKS;

                Location center = waveSurfaceLocation(origin.clone().add(forward.clone().multiply(traveled)));
                spawnWaveParticles(center, right, forward, tick, hitRadius, waveScale);
                affectWaveEnvironment(center, right, forward, hitRadius);
                damageWaveTargets(player, center, forward, hitRadius, damage, knockback, hitTargets);

                if (tick >= waveTicks || traveled >= waveRange) {
                    cancel();
                }
            }
        }.runTaskTimer(UltraRoyales.getPlugin(), 0L, 1L);
    }

    private static Location waveSurfaceLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 2, location.getBlockY() + 2);
        int minY = Math.max(world.getMinHeight(), location.getBlockY() - 18);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (isWaveSurface(block)) {
                return new Location(world, location.getX(), y + 0.08D, location.getZ(), location.getYaw(), location.getPitch());
            }
        }

        return location.clone().add(0.0D, -0.35D, 0.0D);
    }

    private static boolean isWaveSurface(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.LAVA) {
            return true;
        }
        return type.isSolid() && !type.isAir();
    }

    private static void spawnWaveParticles(Location center, Vector side, Vector forward, int tick, double hitRadius, double waveScale) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        // Emit a vertical, moving sheet of water particles to form a "wave wall".
        double halfWidth = Math.max(1.5D, hitRadius);
        double wallHeight = 1.6D + (1.0D * waveScale);
        double widthStep = 0.38;
        double heightStep = 0.36;
        for (double y = 0.05; y <= wallHeight; y += heightStep) {
            for (double offset = -halfWidth; offset <= halfWidth; offset += widthStep) {
                double depth = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.32;
                Location point = center.clone()
                    .add(side.clone().multiply(offset))
                    .add(forward.clone().multiply(depth))
                    .add(0.0, y, 0.0);
                world.spawnParticle(Particle.FALLING_WATER, point, 1, 0.0, 0.0, 0.0, 0.0);
                if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                    world.spawnParticle(Particle.SPLASH, point, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }

        world.spawnParticle(Particle.SPLASH, center.clone().add(0.0, 0.2, 0.0), 18, 1.4, 0.25, 1.4, 0.01);
        world.spawnParticle(Particle.BUBBLE_POP, center.clone().add(0.0, 0.35, 0.0), 12, 1.0, 0.25, 1.0, 0.02);
        if (tick % 3 == 0) {
            world.playSound(center, Sound.ENTITY_GENERIC_SPLASH, 0.35f, 0.95f);
        }
    }

    private static void affectWaveEnvironment(Location center, Vector side, Vector forward, double hitRadius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int samples = Math.max(1, (int) Math.ceil(hitRadius * 1.8D));
        for (int i = -samples; i <= samples; i++) {
            double offset = i * 0.65D;
            if (Math.abs(offset) > hitRadius) {
                continue;
            }
            Location sample = center.clone().add(side.clone().multiply(offset));
            Block block = sample.getBlock();
            Block feet = block.getRelative(0, -1, 0);
            lightlyAffectWaveBlock(block);
            lightlyAffectWaveBlock(feet);
        }
        for (Entity entity : world.getNearbyEntities(center, hitRadius, 1.25D, hitRadius)) {
            if (entity instanceof Item item) {
                item.setVelocity(item.getVelocity().add(forward.clone().multiply(0.08D).setY(0.04D)));
            }
        }
    }

    private static void lightlyAffectWaveBlock(Block block) {
        Material type = block.getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            block.setType(Material.AIR, false);
            return;
        }
        if (type == Material.LAVA) {
            block.setType(Material.COBBLESTONE, false);
            return;
        }
        if (type == Material.SHORT_GRASS
            || type == Material.TALL_GRASS
            || type == Material.FERN
            || type == Material.LARGE_FERN
            || type == Material.DEAD_BUSH
            || type.name().endsWith("_FLOWER")) {
            block.setType(Material.AIR, false);
        }
    }

    private static void damageWaveTargets(Player source, Location center, Vector forward, double hitRadius, double damage, double knockback, Set<UUID> hitTargets) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, hitRadius, 1.5, hitRadius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.getUniqueId().equals(source.getUniqueId())) {
                continue;
            }
            if (!hitTargets.add(living.getUniqueId())) {
                continue;
            }
            if (TeamProtectionService.blocks(source, living)) continue;

            living.damage(damage, source);
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true, true));
            Vector knock = forward.clone().multiply(knockback).setY(0.18 + Math.min(0.22D, knockback * 0.12D));
            living.setVelocity(living.getVelocity().add(knock));
            living.getWorld().spawnParticle(Particle.SPLASH, living.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.35, 0.3, 0.05);
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
        }
    }

    private static void playAmbientIfDue(Player player) {
        long now = System.currentTimeMillis();
        long next = nextAmbientAt.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            return;
        }

        Sound[] palette = new Sound[] {
            Sound.AMBIENT_UNDERWATER_LOOP,
            Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS,
            Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE
        };
        Sound selected = palette[ThreadLocalRandom.current().nextInt(palette.length)];
        player.playSound(player.getLocation(), selected, 0.16f, 1.02f);

        long delayMs = 5000L + ThreadLocalRandom.current().nextLong(2000L);
        nextAmbientAt.put(player.getUniqueId(), now + delayMs);
    }

    private enum WaveTier {
        BACKWASH(4.0D, 1.2D, 2.0D, 0.55D, 0.28D, 0.55D, 0.65f, 0.95f),
        NORMAL(7.0D, 1.7D, 4.0D, 0.85D, 0.0D, 1.0D, 0.9f, 1.0f),
        CRASHING(11.0D, 2.4D, 5.5D, 1.25D, 0.0D, 1.45D, 1.15f, 0.9f);

        private final double range;
        private final double hitRadius;
        private final double damage;
        private final double knockback;
        private final double selfPushback;
        private final double visualScale;
        private final float soundVolume;
        private final float soundPitch;

        WaveTier(double range, double hitRadius, double damage, double knockback, double selfPushback, double visualScale, float soundVolume, float soundPitch) {
            this.range = range;
            this.hitRadius = hitRadius;
            this.damage = damage;
            this.knockback = knockback;
            this.selfPushback = selfPushback;
            this.visualScale = visualScale;
            this.soundVolume = soundVolume;
            this.soundPitch = soundPitch;
        }

        private static WaveTier fromWater(int water) {
            if (water >= 8) {
                return CRASHING;
            }
            if (water >= 4) {
                return NORMAL;
            }
            return BACKWASH;
        }
    }
}
