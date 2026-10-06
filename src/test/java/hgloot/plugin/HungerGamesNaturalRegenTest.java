package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HungerGamesNaturalRegenTest {
    @Test
    void hungerGamesDoesNotOverrideSatiatedRegeneration() throws Exception {
        String queueSource = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/QueueService.java"));
        String pluginSource = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/HGLootPlugin.java"));

        assertFalse(queueSource.contains("onHungerGamesNaturalRegen"));
        assertFalse(queueSource.contains("RegainReason.SATIATED"));
        assertTrue(pluginSource.contains("normal saturation healing"));
    }
}
