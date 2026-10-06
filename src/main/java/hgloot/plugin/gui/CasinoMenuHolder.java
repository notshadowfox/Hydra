package hgloot.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CasinoMenuHolder implements InventoryHolder {
    private final String view;
    private final int page;
    private Inventory inventory;

    public CasinoMenuHolder(String view, int page) {
        this.view = view;
        this.page = page;
    }

    public String view() {
        return view;
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
