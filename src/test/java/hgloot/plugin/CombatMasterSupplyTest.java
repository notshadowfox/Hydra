package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class CombatMasterSupplyTest {
    @Test
    void fullInventoryDropsAllUndeliveredSuppliesAndReportsActualAmount() throws Exception {
        MockBukkit.mock();
        try {
            JavaPlugin plugin = mock(JavaPlugin.class);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            World world = mock(World.class);
            Location at = new Location(world, 10, 80, 10);
            ItemStack overflow = new ItemStack(Material.GOLDEN_APPLE, 8);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(at);
            when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>(Map.of(0, overflow)));

            Method giveSupply = CombatMasterService.class.getDeclaredMethod(
                "giveSupply", Player.class, Material.class, int.class);
            giveSupply.setAccessible(true);
            Object grant = giveSupply.invoke(new CombatMasterService(plugin), player, Material.GOLDEN_APPLE, 8);
            Method total = grant.getClass().getDeclaredMethod("total");
            Method dropped = grant.getClass().getDeclaredMethod("dropped");
            total.setAccessible(true);
            dropped.setAccessible(true);

            assertEquals(8, total.invoke(grant));
            assertEquals(8, dropped.invoke(grant));
            verify(world).dropItemNaturally(at, overflow);
        } finally {
            MockBukkit.unmock();
        }
    }
}
