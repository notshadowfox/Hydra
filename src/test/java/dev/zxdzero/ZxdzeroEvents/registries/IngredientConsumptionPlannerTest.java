package dev.zxdzero.ZxdzeroEvents.registries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class IngredientConsumptionPlannerTest {
    private static org.mockbukkit.mockbukkit.ServerMock server;

    @BeforeAll
    static void setUpBukkit() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void allocatesARequirementAcrossMultipleStacks() {
        ItemStack[] contents = new ItemStack[9];
        contents[1] = new ItemStack(Material.QUARTZ, 4);
        contents[7] = new ItemStack(Material.QUARTZ, 6);

        var plan = IngredientConsumptionPlanner.plan(
            contents,
            List.of(new ItemStack(Material.QUARTZ, 9))
        ).orElseThrow();

        assertEquals(
            List.of(
                new IngredientConsumptionPlanner.SlotConsumption(1, 4),
                new IngredientConsumptionPlanner.SlotConsumption(7, 5)
            ),
            plan.removals()
        );
    }

    @Test
    void returnsNoPlanAndDoesNotMutateWhenRequirementsAreMissing() {
        ItemStack[] contents = {new ItemStack(Material.QUARTZ, 3)};
        ItemStack original = contents[0].clone();

        assertTrue(IngredientConsumptionPlanner.plan(
            contents,
            List.of(new ItemStack(Material.QUARTZ, 4))
        ).isEmpty());
        assertEquals(original, contents[0]);
    }

    @Test
    void ignoresUnrelatedStacksWhenBuildingThePlan() {
        ItemStack[] contents = {
            new ItemStack(Material.DIAMOND, 8),
            new ItemStack(Material.QUARTZ, 2),
            new ItemStack(Material.IRON_INGOT, 7)
        };

        var plan = IngredientConsumptionPlanner.plan(
            contents,
            List.of(new ItemStack(Material.QUARTZ, 2))
        ).orElseThrow();

        assertEquals(List.of(new IngredientConsumptionPlanner.SlotConsumption(1, 2)), plan.removals());
    }

    @Test
    void stalePlanRefusesToMutateInventory() {
        Player player = server.addPlayer();
        player.getInventory().setItem(2, new ItemStack(Material.QUARTZ, 4));
        var plan = IngredientConsumptionPlanner.plan(
            player.getInventory().getContents(),
            List.of(new ItemStack(Material.QUARTZ, 4))
        ).orElseThrow();

        player.getInventory().setItem(2, new ItemStack(Material.DIAMOND, 4));

        assertTrue(!plan.apply(player.getInventory()));
        assertEquals(Material.DIAMOND, player.getInventory().getItem(2).getType());
        assertEquals(4, player.getInventory().getItem(2).getAmount());
    }
}
