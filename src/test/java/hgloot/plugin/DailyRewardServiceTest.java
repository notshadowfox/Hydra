package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

final class DailyRewardServiceTest {
    private ServerMock server;
    private PluginMock plugin;
    private DailyRewardService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T16:30:00Z"), ZoneOffset.UTC);
        service = new DailyRewardService(plugin, null, clock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void parsesLegacyRewardsAndBuildsStableBundles() {
        DailyRewardService.RewardBundle bundle = service.parse("silver:200");
        bundle = service.addOrReplace(bundle, "xp", 500);
        bundle = service.addOrReplace(bundle, "coins", 750);
        bundle = service.addOrReplace(bundle, "fragments", 3);

        assertEquals("fragments=3,xp=500,coins=750", bundle.encode());
        assertEquals(750, bundle.rewards().get(DailyRewardService.RewardType.COINS));
        assertEquals("3 Fragments, 500 XP, 750 Coins", bundle.describe());

        DailyRewardService.RewardBundle removed = service.remove(bundle, "xp");
        assertEquals("fragments=3,coins=750", removed.encode());
    }

    @Test
    void countdownTargetsTheNextEasternMidnightWithoutATickingTask() {
        assertEquals("11h 30m", service.formatUntilReset(Instant.parse("2026-08-28T16:30:00Z")));
    }

    @Test
    void legacyNpcClaimForTodayMigratesToTheGlobalClaimKey() {
        PlayerMock player = server.addPlayer();
        LocalDate today = LocalDate.of(2026, 8, 28);
        NamespacedKey legacy = new NamespacedKey(plugin, "npc_daily_1234567890");
        NamespacedKey global = new NamespacedKey(plugin, "npc_daily_global_claim");
        player.getPersistentDataContainer().set(legacy, PersistentDataType.STRING, today.toString());

        assertTrue(service.claimedOn(player.getPersistentDataContainer(), today));
        assertEquals(today.toString(), player.getPersistentDataContainer().get(global, PersistentDataType.STRING));
    }
}
