package hgloot.plugin.hunger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class FrogBootsSurfaceTest {
    @Test
    void acceptsSurfaceWaterAndSeagrassWithoutTreatingOtherPlantsAsWater() {
        assertTrue(HungerGamesUtilityService.isFrogBootsSurfaceWater(Material.WATER));
        assertTrue(HungerGamesUtilityService.isFrogBootsSurfaceWater(Material.SEAGRASS));
        assertFalse(HungerGamesUtilityService.isFrogBootsSurfaceWater(Material.TALL_SEAGRASS));
        assertFalse(HungerGamesUtilityService.isFrogBootsSurfaceWater(Material.KELP));
    }

    @Test
    void sneakTransitionsEmitOnceAndRepeatedStatesStaySilent() {
        Map<UUID, Boolean> states = new HashMap<>();
        UUID playerId = UUID.randomUUID();

        assertEquals(HungerGamesUtilityService.FrogBootsTransition.NONE,
            transition(states, playerId, true, false));
        assertEquals(HungerGamesUtilityService.FrogBootsTransition.DISABLED,
            transition(states, playerId, true, true));
        assertEquals(HungerGamesUtilityService.FrogBootsTransition.NONE,
            transition(states, playerId, true, true));
        assertEquals(HungerGamesUtilityService.FrogBootsTransition.ENABLED,
            transition(states, playerId, true, false));
        assertEquals(HungerGamesUtilityService.FrogBootsTransition.NONE,
            transition(states, playerId, true, false));
    }

    @Test
    void invalidOrRemovedBootsClearStateWithoutFeedback() {
        Map<UUID, Boolean> states = new HashMap<>();
        UUID playerId = UUID.randomUUID();
        transition(states, playerId, true, false);
        transition(states, playerId, true, true);

        assertEquals(HungerGamesUtilityService.FrogBootsTransition.NONE,
            transition(states, playerId, false, true));
        assertFalse(states.containsKey(playerId));
        assertEquals(HungerGamesUtilityService.FrogBootsTransition.NONE,
            transition(states, playerId, true, true));
    }

    @Test
    void feedbackUsesLocalLeatherworkerSoundAndHigherOnPitch() throws Exception {
        assertEquals(0.65F, HungerGamesUtilityService.FROG_BOOTS_TOGGLE_VOLUME);
        assertEquals(0.95F, HungerGamesUtilityService.FROG_BOOTS_OFF_PITCH);
        assertEquals(1.20F, HungerGamesUtilityService.FROG_BOOTS_ON_PITCH);
        assertTrue(HungerGamesUtilityService.FROG_BOOTS_ON_PITCH
            > HungerGamesUtilityService.FROG_BOOTS_OFF_PITCH);

        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/hunger/HungerGamesUtilityService.java"));
        assertTrue(source.contains("player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_LEATHERWORKER"));
        assertTrue(source.contains("ChatColor.GREEN + \"[Frog Boots] \" + ChatColor.GRAY + \"Lily Pads \""));
        assertTrue(source.contains("ChatColor.RED + \"OFF\""));
        assertTrue(source.contains("ChatColor.GREEN + \"ON\""));
        assertTrue(source.contains("frogBootsDisabled.remove(player.getUniqueId())"));
        assertTrue(source.contains("frogBootsDisabled.clear()"));
    }

    private static HungerGamesUtilityService.FrogBootsTransition transition(
        Map<UUID, Boolean> states,
        UUID playerId,
        boolean validFrogBoots,
        boolean sneaking
    ) {
        return HungerGamesUtilityService.updateFrogBootsToggleState(
            states, playerId, validFrogBoots, sneaking);
    }
}
