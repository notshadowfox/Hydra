package hgloot.plugin.ultraroyales;

import dev.zxdzero.ZxdzeroEvents.registries.ItemMenuRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemsMenuManager implements Listener {

    private static final JavaPlugin plugin = UltraRoyales.getPlugin();

    public static void registerMenus() {
        ItemMenuRegistry.registerItemMenu(
                plugin,
                "ultraroyales_items",
                new ItemStack(Material.DIAMOND),
                Component.text("Ultra Royales Items", NamedTextColor.LIGHT_PURPLE),
                ItemsMenuManager::createMenu
        );
    }
    private static Inventory createMenu() {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Ultra Royales Items"));
        inv.setItem(0, Items.knightsSaddle());
        inv.setItem(1, Items.dwarvenBow());
        inv.setItem(2, Items.ghlochester());
        inv.setItem(3, Items.spiderStaff());
        inv.setItem(4, Items.spongeSaber());
        inv.setItem(5, Items.skeletalBarber());
        inv.setItem(6, Items.electricConch());
//        inv.setItem(7, Items.bingoSpawnEgg());
        ItemStack heartItem = Items.heartItem();
        heartItem.setAmount(64);
        inv.setItem(8, heartItem);

        inv.setItem(9, Items.lifeElixir(1));
        inv.setItem(10, Items.memoryBreaker());
        inv.setItem(11, Items.sonicWrench());
        inv.setItem(12, Items.aegis());
        inv.setItem(13, Items.frozenChestpiece());
        inv.setItem(14, Items.meltedScythe());
        inv.setItem(15, Items.royalRoulette());
        inv.setItem(16, Items.bookOfWelding());
        return inv;
    }
}

