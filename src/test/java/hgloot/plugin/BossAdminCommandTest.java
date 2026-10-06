package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BossAdminCommandTest {
    @Test
    void bossMenuUsesRegisteredAdminCommandPath() throws Exception {
        String menuSource = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));

        assertTrue(menuSource.contains("admin bosses summon combat_master"));
        assertTrue(menuSource.contains("openCombatMasterMenu(player)"));
        assertFalse(menuSource.contains("dispatchCommand(player, \"combatmaster"));
    }

    @Test
    void adminBossCommandExposesImplementedBossOnly() throws Exception {
        String pluginSource = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));

        assertTrue(pluginSource.contains("handleAdminBosses(player, trimLeadingArg(args))"));
        assertTrue(pluginSource.contains("combat_master \" + ChatColor.GRAY + \"(implemented and summonable)"));
        assertTrue(pluginSource.contains("No other custom boss controller is currently registered"));
    }
}
