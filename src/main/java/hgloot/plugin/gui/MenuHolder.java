package hgloot.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    private Inventory inventory;
    private final String theme;

    public MenuHolder() {
        this(null);
    }

    public MenuHolder(String theme) {
        this.theme = theme;
    }

    public String theme() {
        return theme;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
