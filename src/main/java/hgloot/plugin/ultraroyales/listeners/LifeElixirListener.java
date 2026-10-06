package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.ultraroyales.UltraRoyales;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

public class LifeElixirListener implements Listener {
    public static final NamespacedKey ELIXIR_POWER = new NamespacedKey(UltraRoyales.getPlugin(), "elixir_power");

    @EventHandler
    public void onPotionDrink(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();

        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;
        if (!meta.getPersistentDataContainer().has(ELIXIR_POWER)) return;
        Integer power = meta.getPersistentDataContainer().get(ELIXIR_POWER, PersistentDataType.INTEGER);
        if (power == null) {
            return;
        }
        e.setReplacement(Items.emptyLifeElixirBottle());
    }
}

