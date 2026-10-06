package hgloot.plugin.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManagedLootService {
    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_HG_MAP = "hungergames_map";
    public static final String SOURCE_STRUCTURE = "structure";
    public static final String SOURCE_CORNUCOPIA = "cornucopia";

    private final JavaPlugin plugin;
    private final LootTableService lootTableService;
    private final NamespacedKey lootTableKey;
    private final NamespacedKey sourceKey;
    private final NamespacedKey groupKey;

    public ManagedLootService(JavaPlugin plugin, LootTableService lootTableService) {
        this.plugin = plugin;
        this.lootTableService = lootTableService;
        this.lootTableKey = new NamespacedKey(plugin, "placed_loot_table_id");
        this.sourceKey = new NamespacedKey(plugin, "managed_loot_source");
        this.groupKey = new NamespacedKey(plugin, "managed_loot_group");
    }

    public int populateManagedContainer(Block target, String tableId) {
        return populateManagedContainer(target, tableId, SOURCE_MANUAL, null);
    }

    public int populateManagedContainer(Block target, String tableId, String source, String groupId) {
        if (target == null || tableId == null || tableId.isBlank()) {
            return 0;
        }
        if (!lootTableService.hasTable(tableId)) {
            plugin.getLogger().warning("populateManagedContainer: missing table=" + tableId);
            return 0;
        }
        if (!(target.getState() instanceof Container container)) {
            return 0;
        }
        markManagedContainer(container, tableId, source, groupId);
        Inventory inventory = writableInventory(container);
        inventory.clear();
        int generated = lootTableService.fillInventory(inventory, tableId);
        flushContainer(container);
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureContainerHasLoot(target, tableId), 1L);
        return generated;
    }

    public String readManagedTableId(Container container) {
        ManagedLootMetadata metadata = readMetadata(container);
        return metadata == null ? null : metadata.tableId();
    }

    boolean isManagedContainer(Container container) {
        return readMetadata(container) != null;
    }

    public List<Block> collectContainers(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (world == null) {
            return List.of();
        }
        List<Block> containers = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isSupportedLootContainer(block.getType())) {
                        continue;
                    }
                    if (block.getState() instanceof Container) {
                        containers.add(block);
                    }
                }
            }
        }
        sortContainers(containers);
        return containers;
    }

    public List<Block> collectManagedContainers(World world, String source, String groupId) {
        if (world == null) {
            return List.of();
        }
        List<Block> containers = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof Container container)) {
                    continue;
                }
                if (!isSupportedLootContainer(state.getType())) {
                    continue;
                }
                ManagedLootMetadata metadata = readMetadata(container);
                if (metadata == null) {
                    continue;
                }
                if (source != null && !source.equalsIgnoreCase(metadata.source())) {
                    continue;
                }
                if (groupId != null && !Objects.equals(groupId, metadata.groupId())) {
                    continue;
                }
                containers.add(container.getBlock());
            }
        }
        sortContainers(containers);
        return containers;
    }

    public void clearManagedContainer(Block block) {
        if (!(block.getState() instanceof Container container)) {
            return;
        }
        Inventory inventory = writableInventory(container);
        inventory.clear();
        flushContainer(container);
    }

    private void ensureContainerHasLoot(Block block, String tableId) {
        if (!(block.getState() instanceof Container container)) {
            return;
        }
        Inventory inventory = writableInventory(container);
        if (countStacks(inventory) > 0) {
            return;
        }
        lootTableService.fillInventory(inventory, tableId);
        flushContainer(container);
    }

    private void markManagedContainer(Container container, String tableId, String source, String groupId) {
        if (!(container instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().set(lootTableKey, PersistentDataType.STRING, tableId);
        if (source != null && !source.isBlank()) {
            tile.getPersistentDataContainer().set(sourceKey, PersistentDataType.STRING, source);
        } else {
            tile.getPersistentDataContainer().remove(sourceKey);
        }
        if (groupId != null && !groupId.isBlank()) {
            tile.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, groupId);
        } else {
            tile.getPersistentDataContainer().remove(groupKey);
        }
        tile.update(true, false);
    }

    private ManagedLootMetadata readMetadata(Container container) {
        if (!(container instanceof TileState tile)) {
            return null;
        }
        String tableId = tile.getPersistentDataContainer().get(lootTableKey, PersistentDataType.STRING);
        if (tableId == null || tableId.isBlank()) {
            return null;
        }
        String source = tile.getPersistentDataContainer().get(sourceKey, PersistentDataType.STRING);
        String groupId = tile.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
        return new ManagedLootMetadata(tableId, source, groupId);
    }

    private Inventory writableInventory(Container container) {
        if (container instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        return container.getInventory();
    }

    private void flushContainer(Container container) {
        if (container instanceof Chest) {
            return;
        }
        container.update(true, false);
    }

    private int countStacks(Inventory inventory) {
        int stacks = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) != null && !inventory.getItem(slot).getType().isAir()) {
                stacks++;
            }
        }
        return stacks;
    }

    private void sortContainers(List<Block> containers) {
        containers.sort(
            Comparator.comparingInt(Block::getX)
                .thenComparingInt(Block::getZ)
                .thenComparingInt(Block::getY)
        );
    }

    private boolean isSupportedLootContainer(Material material) {
        return material == Material.CHEST
            || material == Material.TRAPPED_CHEST
            || material == Material.BARREL;
    }

    private record ManagedLootMetadata(String tableId, String source, String groupId) {}
}
