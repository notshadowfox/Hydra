package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatMasterProductionPackTest {
    private static final Path BETTERMODEL = Path.of(
        "rp_model_staging/normalized_rp_assets/assets/bettermodel");

    @Test
    void productionGeneratedItemsResolveToPinnedModelsAndApprovedTexture() throws Exception {
        StringBuilder itemDefinitions = new StringBuilder();
        for (String part : new String[] {
            "body", "head", "left_arm", "left_leg", "right_arm", "right_leg", "shield", "staff"
        }) {
            itemDefinitions.append(Files.readString(BETTERMODEL.resolve(
                "items/c/combat_master_" + part + ".json")));
        }
        for (char model = 'a'; model <= 'i'; model++) {
            assertTrue(itemDefinitions.toString().contains("bettermodel:c/" + model));
            assertTrue(Files.readString(BETTERMODEL.resolve("models/c/" + model + ".json"))
                .contains("bettermodel:item/a"));
        }
        assertArrayEquals(
            Files.readAllBytes(BETTERMODEL.resolve("textures/item/combat_master_combat_master.png")),
            Files.readAllBytes(BETTERMODEL.resolve("textures/item/a.png")));
    }
}
