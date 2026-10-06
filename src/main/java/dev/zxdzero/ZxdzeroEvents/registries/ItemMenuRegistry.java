package dev.zxdzero.ZxdzeroEvents.registries;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ItemMenuRegistry {
    private static final Map<String, ItemMenuEntry> registeredMenus = new HashMap<>();
    private static final Logger logger = Logger.getLogger(ItemMenuRegistry.class.getName());

    /**
     * Registers an item menu for a plugin.
     *
     * @param plugin The plugin registering the menu
     * @param menuId The ID for this menu (without namespace)
     * @param buttonItem The item to display as the button in the main menu
     * @param buttonName The display name for the button
     * @param inventorySupplier A supplier that creates the inventory when called
     * @return true if registered successfully, false if ID already exists
     */
    public static boolean registerItemMenu(Plugin plugin, String menuId, ItemStack buttonItem,
                                           Component buttonName, Supplier<Inventory> inventorySupplier) {
        if (plugin == null || menuId == null || buttonItem == null ||
                buttonName == null || inventorySupplier == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        if (menuId.contains(":")) {
            throw new IllegalArgumentException("Menu ID cannot contain ':' - namespace is automatically added");
        }

        String namespacedId = plugin.getName().toLowerCase() + ":" + menuId;

        if (registeredMenus.containsKey(namespacedId)) {
            logger.warning("Item menu with ID '" + namespacedId + "' already exists. Registration failed.");
            return false;
        }

        // Clone the button item and set the display name
        ItemStack button = buttonItem.clone();
        button.editMeta(meta -> meta.displayName(buttonName));

        ItemMenuEntry entry = new ItemMenuEntry(plugin, namespacedId, button, inventorySupplier);
        registeredMenus.put(namespacedId, entry);

        logger.info("Registered item menu '" + namespacedId + "' from plugin " + plugin.getName());
        return true;
    }

    /**
     * Unregisters an item menu.
     *
     * @param namespacedId The namespaced ID of the menu to remove
     * @return true if removed, false if it didn't exist
     */
    public static boolean unregisterItemMenu(String namespacedId) {
        boolean removed = registeredMenus.remove(namespacedId) != null;
        if (removed) {
            logger.info("Unregistered item menu '" + namespacedId + "'");
        }
        return removed;
    }

    /**
     * Unregisters all item menus from a specific plugin.
     *
     * @param plugin The plugin whose menus should be removed
     * @return the number of menus that were removed
     */
    public static int unregisterMenusFromPlugin(Plugin plugin) {
        if (plugin == null) {
            return 0;
        }

        String namespace = plugin.getName().toLowerCase() + ":";
        var toRemove = registeredMenus.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(namespace))
                .map(Map.Entry::getKey)
                .toList();

        toRemove.forEach(registeredMenus::remove);

        if (!toRemove.isEmpty()) {
            logger.info("Unregistered " + toRemove.size() + " item menus from plugin " + plugin.getName());
        }

        return toRemove.size();
    }

    /**
     * Gets an item menu entry by its namespaced ID.
     *
     * @param namespacedId The namespaced ID
     * @return The menu entry, or null if not found
     */
    public static ItemMenuEntry getItemMenu(String namespacedId) {
        return registeredMenus.get(namespacedId);
    }

    /**
     * Gets all registered menu IDs.
     *
     * @return A set of all namespaced menu IDs
     */
    public static Set<String> getAllMenuIds() {
        return Set.copyOf(registeredMenus.keySet());
    }

    /**
     * Creates the main items menu inventory with all registered plugin buttons.
     *
     * @return The main menu inventory
     */
    public static Inventory createMainItemsMenu() {
        int size = Math.max(9, (int) Math.ceil(registeredMenus.size() / 9.0) * 9);
        size = Math.min(size, 54); // Cap at 54 slots (6 rows)

        Inventory mainMenu = Bukkit.createInventory(null, size, Component.text("Items Menu"));

        int slot = 0;
        for (ItemMenuEntry entry : registeredMenus.values()) {
            if (slot >= size) break; // Safety check
            mainMenu.setItem(slot, entry.getButtonItem());
            slot++;
        }

        return mainMenu;
    }

    /**
     * Finds the menu entry that corresponds to a clicked button item.
     *
     * @param clickedItem The item that was clicked
     * @return The menu entry, or null if no match found
     */
    public static ItemMenuEntry findMenuByButton(ItemStack clickedItem) {
        if (clickedItem == null) return null;

        return registeredMenus.values().stream()
                .filter(entry -> entry.getButtonItem().isSimilar(clickedItem))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the number of registered menus.
     *
     * @return The number of registered menus
     */
    public static int getMenuCount() {
        return registeredMenus.size();
    }

    /**
     * Clears all registered menus.
     */
    public static void clearAllMenus() {
        int count = registeredMenus.size();
        registeredMenus.clear();
        logger.info("Cleared all " + count + " registered item menus");
    }

    /**
     * Represents a registered item menu entry.
     */
    public static class ItemMenuEntry {
        private final Plugin plugin;
        private final String namespacedId;
        private final ItemStack buttonItem;
        private final Supplier<Inventory> inventorySupplier;

        public ItemMenuEntry(Plugin plugin, String namespacedId, ItemStack buttonItem,
                             Supplier<Inventory> inventorySupplier) {
            this.plugin = plugin;
            this.namespacedId = namespacedId;
            this.buttonItem = buttonItem;
            this.inventorySupplier = inventorySupplier;
        }

        public Plugin getPlugin() {
            return plugin;
        }

        public String getNamespacedId() {
            return namespacedId;
        }

        public ItemStack getButtonItem() {
            return buttonItem.clone(); // Return a copy to prevent modification
        }

        public Inventory createInventory() {
            return inventorySupplier.get();
        }
    }
}