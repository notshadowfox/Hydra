package hgloot.plugin.hunger;

import hgloot.plugin.HGLootPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class HungerGamesUtilityService implements Listener {
    private static final int SPONGE_DURATION_TICKS = 15 * 20;
    private static final int SPONGE_TRAIL_HOLD_TICKS = 50;
    private static final int SPONGE_RESTORE_PER_PASS = 16;
    static final float FROG_BOOTS_TOGGLE_VOLUME = 0.65F;
    static final float FROG_BOOTS_OFF_PITCH = 0.95F;
    static final float FROG_BOOTS_ON_PITCH = 1.20F;
    static final int SCAVENGER_MAX_CATCHES = 6;
    private static final List<ScavengerReward> SCAVENGER_RARE_REWARDS = List.of(
        reward(Material.DIAMOND, 1, 2),
        reward(Material.GOLDEN_APPLE, 2, 2),
        reward(Material.ENDER_PEARL, 1, 1),
        reward(Material.IRON_CHESTPLATE, 1, 1),
        reward(Material.IRON_LEGGINGS, 1, 1),
        rewardWithBonus(Material.CROSSBOW, 1, 1, Material.ARROW, 10, 14),
        reward(Material.TNT, 2, 3),
        reward(Material.WIND_CHARGE, 4, 6),
        reward(Material.COBWEB, 3, 4),
        reward(Material.GOLDEN_CARROT, 4, 6)
    );
    private static final List<ScavengerReward> SCAVENGER_HIGH_REWARDS = List.of(
        reward(Material.DIAMOND, 2, 3),
        reward(Material.DIAMOND_HELMET, 1, 1),
        reward(Material.DIAMOND_BOOTS, 1, 1),
        reward(Material.GOLDEN_APPLE, 2, 3),
        reward(Material.ENDER_PEARL, 2, 2),
        reward(Material.TNT, 3, 4),
        reward(Material.WIND_CHARGE, 6, 8),
        reward(Material.COBWEB, 4, 6),
        rewardWithBonus(Material.CROSSBOW, 1, 1, Material.ARROW, 16, 16)
    );
    private static final List<ScavengerReward> SCAVENGER_TOP_REWARDS = List.of(
        reward(Material.DIAMOND, 4, 4),
        reward(Material.DIAMOND_HELMET, 1, 1),
        reward(Material.DIAMOND_BOOTS, 1, 1),
        reward(Material.GOLDEN_APPLE, 3, 3),
        reward(Material.ENDER_PEARL, 2, 3),
        reward(Material.WIND_CHARGE, 8, 8),
        reward(Material.COBWEB, 6, 6),
        reward(Material.TNT, 5, 5)
    );
    private final HGLootPlugin plugin;
    private final Map<UUID, SpongeState> sponges = new HashMap<>();
    private final Map<BlockKey, TemporaryBlock> temporaryBlocks = new HashMap<>();
    private final Map<UUID, Long> ghostLandings = new HashMap<>();
    private final Map<UUID, TrackingState> trackers = new HashMap<>();
    private final Map<UUID, Boolean> frogBootsDisabled = new HashMap<>();
    private final LocatorBarBridge locatorBar;
    private BukkitTask task;
    private long tick;

    public HungerGamesUtilityService(HGLootPlugin plugin) {
        this.plugin = plugin;
        LocatorBarBridge bridge;
        try { bridge = new LocatorBarBridge(plugin); }
        catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "[HG Utility] Private Locator Bar support is unavailable; Tracking Compass will stay disabled.", exception);
            bridge = null;
        }
        this.locatorBar = bridge;
    }

    public void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        sponges.values().forEach(this::restoreSponge);
        sponges.clear();
        temporaryBlocks.values().forEach(TemporaryBlock::restore);
        temporaryBlocks.clear();
        ghostLandings.clear();
        frogBootsDisabled.clear();
        for (UUID trackerId : new HashSet<>(trackers.keySet())) stopTracking(trackerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        if (!plugin.queueService().isActiveHungerGamesPlayer(player)) return;
        if (plugin.queueService().isHungerGamesStartFrozen(player.getUniqueId())
            || (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.isCancelled())) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        String id = HungerGamesUtilityItems.utilityId(item);
        if (id == null) return;
        // Preserve old tagged inventory items without allowing their vanilla Ender Eye use.
        if ("smoke_bomb".equals(id)) {
            event.setCancelled(true);
            return;
        }
        switch (id) {
            case "ghost_knife" -> useGhostKnife(event, player, item);
            case "divers_sponge" -> useSponge(event, player, item);
            case "tracking_compass" -> useTrackingCompass(event, player, item);
            default -> { }
        }
    }

    private void useTrackingCompass(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        if (locatorBar == null) {
            player.sendMessage(Component.text("Tracking Compass is unavailable on this server build.", NamedTextColor.RED));
            return;
        }
        Player target = plugin.queueService().activeHungerGamesEnemies(player).stream()
            .min(java.util.Comparator.comparingDouble(enemy -> enemy.getLocation().distanceSquared(player.getLocation())))
            .orElse(null);
        if (target == null) {
            player.sendMessage(Component.text("No valid living enemy can be tracked.", NamedTextColor.RED));
            return;
        }
        stopTracking(player.getUniqueId());
        UUID waypointId = UUID.randomUUID();
        trackers.put(player.getUniqueId(), new TrackingState(target.getUniqueId(), waypointId, false));
        locatorBar.add(player, waypointId, target.getLocation());
        consumeOne(player, item);
        breakEffect(player, Material.COMPASS);
        player.sendMessage(Component.text("Tracking " + target.getName() + ".", NamedTextColor.RED));
    }

    private void useGhostKnife(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(1.65D).add(new Vector(0.0D, 0.26D, 0.0D)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 30, 0, false, false, false));
        ghostLandings.put(player.getUniqueId(), tick);
        consumeOne(player, item);
        breakEffect(player, Material.WOODEN_SWORD);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8F, 1.15F);
    }

    private void useSponge(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        if (sponges.containsKey(player.getUniqueId())) return;
        sponges.put(player.getUniqueId(), new SpongeState(
            player.getUniqueId(), tick, tick + SPONGE_DURATION_TICKS
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_SPONGE_ABSORB, 1.0F, 1.0F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onActiveSpongeDrop(PlayerDropItemEvent event) {
        if (sponges.containsKey(event.getPlayer().getUniqueId())
            && "divers_sponge".equals(HungerGamesUtilityItems.utilityId(event.getItemDrop().getItemStack()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onActiveSpongeMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !sponges.containsKey(player.getUniqueId())) return;
        if ("divers_sponge".equals(HungerGamesUtilityItems.utilityId(event.getCurrentItem()))
            || "divers_sponge".equals(HungerGamesUtilityItems.utilityId(event.getCursor()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onActiveSpongeDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !sponges.containsKey(player.getUniqueId())) return;
        if ("divers_sponge".equals(HungerGamesUtilityItems.utilityId(event.getOldCursor()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onScavengerCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
            || !plugin.queueService().isActiveHungerGamesPlayer(event.getPlayer())) return;
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND
            ? EquipmentSlot.OFF_HAND
            : EquipmentSlot.HAND;
        ItemStack rod = itemInHand(player, hand);
        if (!"scavengers_rod".equals(HungerGamesUtilityItems.utilityId(rod))) return;
        event.setCancelled(true);
        event.setExpToDrop(0);
        if (event.getCaught() instanceof Item caught) caught.remove();
        event.getHook().remove();
        Bukkit.getScheduler().runTask(plugin, () -> completeScavengerCatch(player, hand));
    }

    private void completeScavengerCatch(Player player, EquipmentSlot hand) {
        if (player == null || !player.isOnline() || !plugin.queueService().isActiveHungerGamesPlayer(player)) return;
        ItemStack rod = itemInHand(player, hand);
        if (!"scavengers_rod".equals(HungerGamesUtilityItems.utilityId(rod))) return;
        org.bukkit.inventory.meta.ItemMeta meta = rod.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(
            new org.bukkit.NamespacedKey(plugin, "scavenger_uses"), PersistentDataType.INTEGER, 0) + 1;
        scavengerRoll(uses).forEach(reward -> giveOrDrop(player, reward));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "scavenger_uses"), PersistentDataType.INTEGER, uses);
        rod.setItemMeta(meta);
        if (uses >= SCAVENGER_MAX_CATCHES) {
            consumeOne(player, rod, hand);
            breakEffect(player, Material.FISHING_ROD);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL
            || ghostLandings.remove(player.getUniqueId()) == null) return;
        event.setCancelled(true);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity());
    }

    private void tick() {
        tick++;
        tickGhostLandings();
        if ((tick & 1L) == 0L) {
            tickFrogShoes();
            tickSponges();
        }
        if (tick % 5L == 0L) tickTrackers();
        restoreExpiredBlocks();
    }

    private void tickTrackers() {
        for (UUID trackerId : new HashSet<>(trackers.keySet())) {
            TrackingState state = trackers.get(trackerId);
            Player tracker = Bukkit.getPlayer(trackerId);
            if (state == null || tracker == null || !plugin.queueService().isAliveHungerGamesParticipant(trackerId)
                || !plugin.queueService().isAliveHungerGamesParticipant(state.target)) {
                stopTracking(trackerId);
                continue;
            }
            Player target = Bukkit.getPlayer(state.target);
            if (target == null || !target.isOnline()) {
                if (!state.removed) {
                    locatorBar.remove(tracker, state.waypoint);
                    trackers.put(trackerId, new TrackingState(state.target, state.waypoint, true));
                }
                continue;
            }
            if (!tracker.getWorld().equals(target.getWorld()) || tracker.getLocation().distanceSquared(target.getLocation()) <= 100.0D) {
                stopTracking(trackerId);
                continue;
            }
            if (state.removed) {
                locatorBar.add(tracker, state.waypoint, target.getLocation());
                trackers.put(trackerId, new TrackingState(state.target, state.waypoint, false));
            } else locatorBar.update(tracker, state.waypoint, target.getLocation());
        }
    }

    private void stopTracking(UUID trackerId) {
        TrackingState state = trackers.remove(trackerId);
        Player tracker = Bukkit.getPlayer(trackerId);
        if (state != null && tracker != null && locatorBar != null) locatorBar.remove(tracker, state.waypoint);
    }

    private void tickGhostLandings() {
        for (UUID id : new HashSet<>(ghostLandings.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !plugin.queueService().isActiveHungerGamesPlayer(player)) {
                ghostLandings.remove(id);
            } else if (tick >= ghostLandings.getOrDefault(id, tick) + 3L && player.isOnGround()) {
                ghostLandings.remove(id);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.setFallDistance(0.0F);
            }
        }
    }

    private void tickFrogShoes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean validFrogBoots = plugin.queueService().isActiveHungerGamesPlayer(player)
                && "frog_shoes".equals(HungerGamesUtilityItems.utilityId(player.getInventory().getBoots()));
            FrogBootsTransition transition = updateFrogBootsToggleState(
                frogBootsDisabled, player.getUniqueId(), validFrogBoots, player.isSneaking());
            if (transition != FrogBootsTransition.NONE) {
                sendFrogBootsToggleFeedback(player, transition);
            }
            if (!validFrogBoots || player.isSneaking() || player.getEyeLocation().getBlock().isLiquid()) continue;
            Vector forward = player.getLocation().getDirection().setY(0).normalize();
            for (double distance : new double[]{0.0D, 0.8D, 1.6D}) {
                Location sample = player.getLocation().clone().add(forward.clone().multiply(distance));
                placeFrogPad(sample);
            }
        }
    }

    static FrogBootsTransition updateFrogBootsToggleState(
        Map<UUID, Boolean> disabledStates,
        UUID playerId,
        boolean validFrogBoots,
        boolean sneaking
    ) {
        if (!validFrogBoots) {
            disabledStates.remove(playerId);
            return FrogBootsTransition.NONE;
        }
        Boolean previous = disabledStates.put(playerId, sneaking);
        if (previous == null || previous == sneaking) return FrogBootsTransition.NONE;
        return sneaking ? FrogBootsTransition.DISABLED : FrogBootsTransition.ENABLED;
    }

    private void sendFrogBootsToggleFeedback(Player player, FrogBootsTransition transition) {
        boolean disabled = transition == FrogBootsTransition.DISABLED;
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_LEATHERWORKER,
            FROG_BOOTS_TOGGLE_VOLUME, disabled ? FROG_BOOTS_OFF_PITCH : FROG_BOOTS_ON_PITCH);
        player.sendMessage(ChatColor.GREEN + "[Frog Boots] " + ChatColor.GRAY + "Lily Pads "
            + (disabled ? ChatColor.RED + "OFF" : ChatColor.GREEN + "ON"));
    }

    private void placeFrogPad(Location sample) {
        for (int offset = 0; offset >= -2; offset--) {
            Block water = sample.clone().add(0, offset - 1, 0).getBlock();
            Block pad = water.getRelative(0, 1, 0);
            if (!isFrogBootsSurfaceWater(water.getType()) || pad.getType() != Material.AIR) continue;
            BlockKey key = BlockKey.of(pad);
            TemporaryBlock state = temporaryBlocks.get(key);
            if (state == null) {
                state = new TemporaryBlock(pad, pad.getBlockData().clone(), tick + 30L);
                temporaryBlocks.put(key, state);
                pad.setType(Material.LILY_PAD, false);
            } else {
                state.expiresAt = tick + 30L;
            }
            return;
        }
    }

    static boolean isFrogBootsSurfaceWater(Material material) {
        return material == Material.WATER || material == Material.SEAGRASS;
    }

    private void tickSponges() {
        Iterator<SpongeState> iterator = sponges.values().iterator();
        while (iterator.hasNext()) {
            SpongeState state = iterator.next();
            Player player = Bukkit.getPlayer(state.owner);
            if (player == null || !plugin.queueService().isActiveHungerGamesPlayer(player)) {
                restoreSponge(state);
                if (player != null) finishSponge(player);
                iterator.remove();
                continue;
            }
            if (tick >= state.expiresAt) {
                if (!state.finishing) {
                    state.finishing = true;
                    state.restored.values().forEach(block -> block.expiresAt = Math.min(block.expiresAt, tick));
                    finishSponge(player);
                }
                restoreReleasedSpongeBlocks(state, SPONGE_RESTORE_PER_PASS);
                if (state.restored.isEmpty()) iterator.remove();
                continue;
            }
            updateSpongeDurability(player, state);
            Location center = player.getLocation().add(0, 1, 0);
            for (int x = -4; x <= 4; x++) for (int y = -2; y <= 2; y++) for (int z = -4; z <= 4; z++) {
                if ((x * x) + (z * z) > 16) continue;
                Block block = center.clone().add(x, y, z).getBlock();
                BlockKey key = BlockKey.of(block);
                TemporaryBlock saved = state.restored.get(key);
                if (saved == null) {
                    if (!isSpongeFluidBlock(block)) continue;
                    saved = new TemporaryBlock(block, block.getBlockData().clone(), tick + SPONGE_TRAIL_HOLD_TICKS);
                    state.restored.put(key, saved);
                }
                saved.expiresAt = tick + SPONGE_TRAIL_HOLD_TICKS;
                clearSpongeFluid(saved);
            }
            restoreReleasedSpongeBlocks(state, SPONGE_RESTORE_PER_PASS);
            player.sendActionBar(Component.text("Diver's Sponge: " + Math.max(0, (state.expiresAt - tick + 19) / 20) + "s",
                NamedTextColor.AQUA));
        }
    }

    private boolean isSpongeFluidBlock(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN || type == Material.SEAGRASS
            || type == Material.TALL_SEAGRASS || type == Material.KELP || type == Material.KELP_PLANT) return true;
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private void clearSpongeFluid(TemporaryBlock saved) {
        BlockData current = saved.block.getBlockData();
        if (saved.original instanceof Waterlogged && current instanceof Waterlogged waterlogged) {
            if (waterlogged.isWaterlogged()) {
                waterlogged.setWaterlogged(false);
                saved.block.setBlockData(current, false);
            }
            return;
        }
        if (saved.block.getType() != Material.AIR) saved.block.setType(Material.AIR, false);
    }

    private void restoreReleasedSpongeBlocks(SpongeState state, int limit) {
        int restored = 0;
        for (Iterator<Map.Entry<BlockKey, TemporaryBlock>> it = state.restored.entrySet().iterator(); it.hasNext() && restored < limit;) {
            Map.Entry<BlockKey, TemporaryBlock> entry = it.next();
            if (tick < entry.getValue().expiresAt) continue;
            entry.getValue().restore();
            it.remove();
            restored++;
        }
    }

    private void restoreExpiredBlocks() {
        for (Iterator<Map.Entry<BlockKey, TemporaryBlock>> it = temporaryBlocks.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BlockKey, TemporaryBlock> entry = it.next();
            if (tick >= entry.getValue().expiresAt) {
                entry.getValue().restore();
                it.remove();
            }
        }
    }

    private void restoreSponge(SpongeState state) {
        state.restored.values().forEach(TemporaryBlock::restore);
        state.restored.clear();
    }

    private void cleanupPlayer(Player player) {
        ghostLandings.remove(player.getUniqueId());
        frogBootsDisabled.remove(player.getUniqueId());
        stopTracking(player.getUniqueId());
        SpongeState sponge = sponges.remove(player.getUniqueId());
        if (sponge != null) {
            restoreSponge(sponge);
            finishSponge(player);
        }
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void updateSpongeDurability(Player player, SpongeState state) {
        ItemStack sponge = findUtility(player, "divers_sponge");
        if (sponge == null || !(sponge.getItemMeta() instanceof Damageable damageable)) return;
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : SPONGE_DURATION_TICKS;
        long elapsed = Math.max(0L, tick - state.startedAt);
        damageable.setDamage(Math.min(maxDamage - 1,
            (int) Math.round((elapsed / (double) SPONGE_DURATION_TICKS) * maxDamage)));
        sponge.setItemMeta(damageable);
    }

    private void finishSponge(Player player) {
        ItemStack sponge = findUtility(player, "divers_sponge");
        if (sponge != null) {
            if (sponge.getAmount() <= 1) player.getInventory().removeItem(sponge);
            else sponge.setAmount(sponge.getAmount() - 1);
        }
        breakEffect(player, Material.SPONGE);
    }

    private ItemStack findUtility(Player player, String utilityId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (utilityId.equals(HungerGamesUtilityItems.utilityId(item))) return item;
        }
        return null;
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    private List<ItemStack> scavengerRoll(int successfulCatch) {
        ScavengerTier tier = scavengerTierForRoll(successfulCatch, ThreadLocalRandom.current().nextInt(100));
        List<ScavengerReward> pool = scavengerRewards(tier);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).createItems();
    }

    static ScavengerTier scavengerTierForRoll(int successfulCatch, int roll) {
        if (successfulCatch < 1 || successfulCatch > SCAVENGER_MAX_CATCHES) {
            throw new IllegalArgumentException("successfulCatch must be between 1 and " + SCAVENGER_MAX_CATCHES);
        }
        if (roll < 0 || roll >= 100) {
            throw new IllegalArgumentException("roll must be between 0 and 99");
        }
        if (successfulCatch == SCAVENGER_MAX_CATCHES) {
            return roll < 60 ? ScavengerTier.HIGH : ScavengerTier.TOP;
        }
        if (roll < 35) return ScavengerTier.RARE;
        return roll < 75 ? ScavengerTier.HIGH : ScavengerTier.TOP;
    }

    static List<ScavengerReward> scavengerRewards(ScavengerTier tier) {
        return switch (tier) {
            case RARE -> SCAVENGER_RARE_REWARDS;
            case HIGH -> SCAVENGER_HIGH_REWARDS;
            case TOP -> SCAVENGER_TOP_REWARDS;
        };
    }

    private static ScavengerReward reward(Material material, int min, int max) {
        return new ScavengerReward(material, min, max, null, 0, 0);
    }

    private static ScavengerReward rewardWithBonus(
        Material material,
        int min,
        int max,
        Material bonusMaterial,
        int bonusMin,
        int bonusMax
    ) {
        return new ScavengerReward(material, min, max, bonusMaterial, bonusMin, bonusMax);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void consumeOne(Player player, ItemStack item) {
        consumeOne(player, item, EquipmentSlot.HAND);
    }

    private void consumeOne(Player player, ItemStack item, EquipmentSlot hand) {
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        }
        else item.setAmount(item.getAmount() - 1);
    }

    private void breakEffect(Player player, Material material) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
        player.getWorld().spawnParticle(Particle.ITEM, player.getEyeLocation(), 12, 0.2, 0.2, 0.2, 0.05, new ItemStack(material));
    }

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) { return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }
    private static final class TemporaryBlock {
        private final Block block; private final BlockData original; private long expiresAt;
        private TemporaryBlock(Block block, BlockData original, long expiresAt) { this.block=block; this.original=original; this.expiresAt=expiresAt; }
        private void restore() { if (block.getWorld() != null) block.setBlockData(original, false); }
    }
    private static final class SpongeState {
        private final UUID owner; private final long startedAt; private final long expiresAt;
        private final Map<BlockKey, TemporaryBlock> restored = new HashMap<>();
        private boolean finishing;
        private SpongeState(UUID owner, long startedAt, long expiresAt) {
            this.owner=owner; this.startedAt=startedAt; this.expiresAt=expiresAt;
        }
    }
    private record TrackingState(UUID target, UUID waypoint, boolean removed) {}

    enum ScavengerTier {
        RARE,
        HIGH,
        TOP
    }

    enum FrogBootsTransition {
        NONE,
        DISABLED,
        ENABLED
    }

    record ScavengerReward(
        Material material,
        int minAmount,
        int maxAmount,
        Material bonusMaterial,
        int bonusMinAmount,
        int bonusMaxAmount
    ) {
        private List<ItemStack> createItems() {
            List<ItemStack> items = new ArrayList<>();
            items.add(randomStack(material, minAmount, maxAmount));
            if (bonusMaterial != null) {
                items.add(randomStack(bonusMaterial, bonusMinAmount, bonusMaxAmount));
            }
            return List.copyOf(items);
        }

        private static ItemStack randomStack(Material material, int min, int max) {
            return new ItemStack(material, ThreadLocalRandom.current().nextInt(min, max + 1));
        }
    }
}
