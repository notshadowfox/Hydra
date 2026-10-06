package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

final class HudCleanupPresentationTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/hgloot/plugin/" + name + ".java"));
    }

    @Test
    void hubAndHungerGamesQueuePrioritizeRelevantInformation() throws Exception {
        String queue = source("QueueService");
        String hub = queue.substring(queue.indexOf("private void updateLobbySidebar("),
            queue.indexOf("public String activeMatchModeLabel("));
        assertTrue(hub.contains("sidebarSystem(\"Coins\""));
        assertTrue(hub.contains("Shards "));
        assertTrue(hub.contains("sidebarSystem(\"Fragments\""));
        assertFalse(hub.contains("appendFightPitLeaders"));
        assertFalse(hub.contains("hungerGamesTabActivityCount()"));
        assertFalse(hub.contains("miniGamesTabActivityCount()"));
        String waiting = queue.substring(queue.indexOf("private void updateQueueSidebar("),
            queue.indexOf("private void sendHungerGamesDeathMessage("));
        String hg = waiting.substring(waiting.indexOf("if (isHungerGames(type))"), waiting.indexOf("String startLabel"));
        assertTrue(hg.contains("\"Type\""));
        assertTrue(hg.contains("\"Duos\" : \"Solos\""));
        assertTrue(hg.contains("\"Players\""));
        assertTrue(hg.contains("seconds < 0 ? \"Waiting\" : \"Starting\""));
        assertTrue(hg.contains("if (seconds >= 0)"));
        assertTrue(hg.contains("\"HUNGER GAMES\""));
        assertFalse(hg.contains("Lobby"));
        assertFalse(hg.contains("FightPit"));
    }

    @Test
    void spleefActiveBoardKeepsMatchStateAndDropsAccountProgression() throws Exception {
        String spleef = source("SpleefService");
        int sidebar = spleef.indexOf("private void updateSpleefSidebar(Player player)");
        int activeStart = spleef.indexOf("if (!alivePlayers.contains(playerId)", sidebar);
        String active = spleef.substring(activeStart,
            spleef.indexOf("applySpleefSidebar(player, lines);", activeStart));
        assertTrue(active.contains("\"Alive\""));
        assertTrue(active.contains("\"Status\""));
        assertTrue(active.contains("\"Spectating\""));
        assertTrue(active.contains("\"Starts\""));
        assertTrue(active.contains("if (alive)"));
        assertTrue(active.contains("\"Shovel\""));
        assertTrue(active.contains("\"Powerups\""));
        assertTrue(active.contains("\"Your Team\""));
        assertFalse(active.contains("\"Level\""));
        assertFalse(active.contains("\"Tier\""));
        assertFalse(active.contains("\"XP\""));
    }

    @Test
    void bossRushRaceShowsOwnDamageAndAccurateLeaderOrTie() throws Exception {
        EnumMap<RouletteTeam, Double> damage = new EnumMap<>(RouletteTeam.class);
        damage.put(RouletteTeam.RED, 1842.0D);
        damage.put(RouletteTeam.BLUE, 2104.0D);
        List<RouletteTeam> teams = List.of(RouletteTeam.RED, RouletteTeam.BLUE);
        String behind = BossRushService.damageRaceLine(RouletteTeam.RED, damage, teams);
        assertTrue(behind.contains("YOUR TEAM"));
        assertTrue(behind.contains("1,842 DMG"));
        assertTrue(behind.contains("LEADER"));
        assertTrue(behind.contains("2,104"));
        String leading = BossRushService.damageRaceLine(RouletteTeam.BLUE, damage, teams);
        assertTrue(leading.contains("LEADING"));
        damage.put(RouletteTeam.RED, 2104.0D);
        assertTrue(BossRushService.damageRaceLine(RouletteTeam.RED, damage, teams).contains("TIED FOR LEAD"));
        String boss = source("BossRushService");
        assertTrue(boss.contains("holdDamageActionBar(Player player, long millis)"));
        assertTrue(boss.contains("now < damageActionBarHeldUntil.getOrDefault(id, 0L)"));
        assertTrue(boss.contains("ChatColor.GRAY + \"Damage \" + ChatColor.GOLD + roundedDamage(team)"),
            "The approved Boss Rush sidebar remains intact");
        assertTrue(source("CombatMasterService").contains("holdDamageActionBar(player, 2500L)"));
        for (String listener : List.of("LegendaryRechargeBarListener", "ElectricConchListener",
                "SpongeSaberListener", "SkeletalBarberListener", "SpiderStaffListener")) {
            String itemSource = Files.readString(Path.of(
                "src/main/java/hgloot/plugin/ultraroyales/listeners/" + listener + ".java"));
            assertTrue(itemSource.contains("holdBossRushDamageActionBar(player,"), listener);
        }
    }

    @Test
    void kothRevealsActualEventCenterOnlyAfterCountdown() throws Exception {
        assertEquals(ChatColor.AQUA + "KOTH: " + ChatColor.WHITE + "-33, 45",
            KothService.activeCoordinatesLine(-32.5D, 45.5D));
        String koth = source("KothService");
        String queue = source("QueueService");
        assertTrue(queue.contains("kothService.sidebarLocationLine(rootWorldName)"));
        assertTrue(queue.contains("kothLine = scheduledHungerGamesKothSidebarLine(rootWorldName, now)"));
        String sidebar = koth.substring(koth.indexOf("public String sidebarLocationLine("),
            koth.indexOf("private List<String> statusLines("));
        assertTrue(sidebar.contains("event.state == State.STARTING"));
        assertTrue(sidebar.contains("return sidebarLine(worldName)"));
        assertTrue(sidebar.contains("activeCoordinatesLine(event.zone.centerX(), event.zone.centerZ())"));
    }

    @Test
    void hungerGamesBorderUsesMatchStartAndActualPhaseWithoutInventingTimer() {
        long started = 1_000_000L;
        assertNull(QueueService.hungerGamesBorderStatus(null, 0, started));
        assertEquals("05:30", QueueService.hungerGamesBorderStatus(started, 0, started));
        assertEquals("Shrinking", QueueService.hungerGamesBorderStatus(started, 1, started + 331_000L));
        assertEquals("00:19", QueueService.hungerGamesBorderStatus(started, 1, started + 391_000L));
        assertEquals("Final", QueueService.hungerGamesBorderStatus(started, 4, started + 631_000L));
    }
}
