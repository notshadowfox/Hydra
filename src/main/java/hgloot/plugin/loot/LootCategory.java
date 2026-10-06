package hgloot.plugin.loot;

import java.util.Arrays;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.Material;

@SuppressWarnings("deprecation")
public enum LootCategory {
    STANDARD("standard", "Standard", "standard_loot_chest_", 6, Material.CHEST, ChatColor.GREEN),
    CORNUCOPIA("cornucopia", "Cornucopia", "cornucopia_chest_", 6, Material.HAY_BLOCK, ChatColor.YELLOW),
    JUNGLE("jungle", "Jungle", "jungle_chest_", 12, Material.JUNGLE_LEAVES, ChatColor.DARK_GREEN),
    SAND("sand", "Sand", "sand_structures_chest_", 7, Material.SAND, ChatColor.GOLD),
    OCEAN("ocean", "Ocean", "ocean_structures_chest_", 5, Material.HEART_OF_THE_SEA, ChatColor.AQUA),
    AIR("air", "Air", "air_balloon_chest_", 7, Material.FEATHER, ChatColor.LIGHT_PURPLE),
    BASTION("bastion", "Bastion", "bastion_chest_", 1, Material.NETHER_BRICKS, ChatColor.DARK_RED);

    private final String key;
    private final String displayName;
    private final String tablePrefix;
    private final int chestCount;
    private final Material icon;
    private final ChatColor color;

    LootCategory(String key, String displayName, String tablePrefix, int chestCount, Material icon, ChatColor color) {
        this.key = key;
        this.displayName = displayName;
        this.tablePrefix = tablePrefix;
        this.chestCount = chestCount;
        this.icon = icon;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public int chestCount() {
        return chestCount;
    }

    public Material icon() {
        return icon;
    }

    public ChatColor color() {
        return color;
    }

    public String tableId(int index) {
        return tablePrefix + index;
    }

    public static Optional<LootCategory> fromKey(String key) {
        return Arrays.stream(values()).filter(category -> category.key.equalsIgnoreCase(key)).findFirst();
    }
}
