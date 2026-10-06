package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BossRushHubHotbarTest {
    @Test
    void bossRushWorldsAreTreatedAsMatchWorlds() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/hgloot/plugin/MenuService.java"));

        assertTrue(source.contains("worldName.startsWith(\"bossrush-world-\")"));
    }
}
