package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class HydraTabPresentationTest {
    private static final Path ASSET_ROOT = Path.of("rp_model_staging/normalized_rp_assets/assets/hydra");

    @Test
    void mainTabAlwaysShowsRealLogoAndTruthfulStatusRows() throws Exception {
        HydraPlusService service = new HydraPlusService(mock(HGLootPlugin.class));
        Player viewer = mock(Player.class);
        when(viewer.getPing()).thenReturn(42);
        Component header = header(service, viewer, 17, 3, 0, 6);

        assertEquals(Key.key("hydra", "gui"), header.children().get(4).font());
        String plain = PlainTextComponentSerializer.plainText().serialize(header);
        assertTrue(plain.startsWith("\n".repeat(4) + "\uE13D" + "\n"
            + "HYDRA NETWORK\n17 PLAYERS ONLINE  •  PING 42ms"));
        assertTrue(plain.contains("HG 3  •  BR 0  •  MR 6"));
        assertTrue(plain.endsWith("MR 6\n"));
        assertFalse(plain.contains("ROULETTE "));
        assertFalse(plain.contains("SPLEEF"));
        assertFalse(plain.contains("ONLINE PLAYERS ──"));
        assertFalse(plain.contains("MINIGAMES"));
        assertEquals(1, plain.split("HYDRA NETWORK", -1).length - 1);
    }

    @Test
    void firstHeaderRefreshDoesNotWaitForAResourcePackStatusEvent() throws Exception {
        HydraPlusService service = new HydraPlusService(mock(HGLootPlugin.class));
        Player viewer = mock(Player.class);
        String plain = PlainTextComponentSerializer.plainText().serialize(header(service, viewer, 1, 0, 0, 0));
        assertTrue(plain.startsWith("\n".repeat(4) + "\uE13D" + "\n"
            + "HYDRA NETWORK\n1 PLAYER ONLINE"));
        assertEquals(1, plain.split("\uE13D", -1).length - 1);
    }

    @Test
    void headerKeepsItsHierarchyWithLargerRealisticCounts() throws Exception {
        HydraPlusService service = new HydraPlusService(mock(HGLootPlugin.class));
        Player viewer = mock(Player.class);
        when(viewer.getPing()).thenReturn(117);
        String plain = PlainTextComponentSerializer.plainText().serialize(
            header(service, viewer, 64, 24, 0, 28));
        assertTrue(plain.contains("HYDRA NETWORK\n64 PLAYERS ONLINE  •  PING 117ms"));
        assertTrue(plain.contains("HG 24  •  BR 0  •  MR 28\n"));
        assertFalse(plain.contains("ONLINE PLAYERS"));
    }

    @Test
    void modePopulationUsesHgBossRushAndRouletteSources() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        QueueService queues = mock(QueueService.class);
        BossRushService bossRush = mock(BossRushService.class);
        MinigameRouletteService roulette = mock(MinigameRouletteService.class);
        when(plugin.queueService()).thenReturn(queues);
        when(plugin.bossRushService()).thenReturn(bossRush);
        when(plugin.minigameRouletteService()).thenReturn(roulette);
        when(queues.hungerGamesTabActivityCount()).thenReturn(3);
        when(bossRush.tabActivityCount()).thenReturn(2);
        when(roulette.sidebarActivityCount()).thenReturn(6);
        HydraPlusService service = new HydraPlusService(plugin);
        Method countsMethod = HydraPlusService.class.getDeclaredMethod("tabActivityCounts");
        countsMethod.setAccessible(true);
        Object counts = countsMethod.invoke(service);
        for (var entry : java.util.Map.of("hg", 3, "br", 2, "roulette", 6).entrySet()) {
            Method count = counts.getClass().getDeclaredMethod(entry.getKey());
            count.setAccessible(true);
            assertEquals(entry.getValue(), count.invoke(counts));
        }
        verify(queues, never()).battleRoyaleTabActivityCount();
    }

    @Test
    void footerKeepsLinksOnSeparateReadableLines() throws Exception {
        HydraPlusService service = new HydraPlusService(mock(HGLootPlugin.class));
        Method footerMethod = HydraPlusService.class.getDeclaredMethod("tabFooterComponent");
        footerMethod.setAccessible(true);
        Component footer = (Component) footerMethod.invoke(service);
        assertNull(footer.font());
        String plain = PlainTextComponentSerializer.plainText().serialize(footer);
        String[] lines = plain.split("\n", -1);
        assertEquals("STORE   store.hydramc.net    ", lines[0]);
        assertEquals("DISCORD discord.gg/ZS5hXqyrzh", lines[1]);
        assertEquals(lines[0].length(), lines[1].length());
        assertEquals(lines[0].indexOf("store.hydramc.net"), lines[1].indexOf("discord.gg/ZS5hXqyrzh"));
    }

    @Test
    void realPlayerRowsRetainRankAndVanillaPingWithoutDuplicatedNumbers() throws Exception {
        HydraPlusService service = new HydraPlusService(mock(HGLootPlugin.class));
        Player subject = mock(Player.class);
        when(subject.getName()).thenReturn("Alex");
        when(subject.getPing()).thenReturn(38);
        Method nameMethod = HydraPlusService.class.getDeclaredMethod("tabNameComponent", Player.class);
        nameMethod.setAccessible(true);
        Component row = (Component) nameMethod.invoke(service, subject);
        assertNull(row.font());
        String plain = PlainTextComponentSerializer.plainText().serialize(row);
        assertTrue(plain.startsWith("Alex"));
        assertFalse(plain.contains("PLAYER"));
        assertFalse(plain.contains("38ms"));
        assertEquals("Alex", plain);
    }

    @Test
    void suppliedTransparentLogoHasExactlyOneDedicatedGlyph() throws Exception {
        Path logo = ASSET_ROOT.resolve("textures/font/gui/hydra_2.png");
        BufferedImage image = ImageIO.read(logo.toFile());
        assertEquals(300, image.getWidth());
        assertEquals(402, image.getHeight());
        assertEquals(0, (image.getRGB(0, 0) >>> 24) & 0xff);

        JsonObject font = JsonParser.parseString(Files.readString(ASSET_ROOT.resolve("font/gui.json")))
            .getAsJsonObject();
        JsonArray providers = font.getAsJsonArray("providers");
        int matches = 0;
        for (int index = 0; index < providers.size(); index++) {
            JsonObject provider = providers.get(index).getAsJsonObject();
            if (provider.has("file") && provider.get("file").getAsString().equals("hydra:font/gui/hydra_2.png")) {
                matches++;
                assertEquals("\uE13D", provider.getAsJsonArray("chars").get(0).getAsString());
                assertEquals(44, provider.get("height").getAsInt());
                assertEquals(40, provider.get("ascent").getAsInt());
                assertEquals(4, provider.get("height").getAsInt() - provider.get("ascent").getAsInt());
            }
        }
        assertEquals(1, matches);
    }

    @Test
    void hgAndBattleRoyaleUseExactMatchSnapshotInsteadOfGlobalCounts() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        QueueService queues = mock(QueueService.class);
        Player viewer = mock(Player.class);
        when(plugin.queueService()).thenReturn(queues);
        HydraPlusService service = new HydraPlusService(plugin);
        when(queues.tabMatchSnapshot(viewer)).thenReturn(new QueueService.TabMatchSnapshot("HG SOLOS", 12, 146));
        assertEquals("HG SOLOS  •  12 ALIVE  •  BORDER 2:26", context(service, viewer));
        when(queues.tabMatchSnapshot(viewer)).thenReturn(new QueueService.TabMatchSnapshot("HG DUOS", 8, null));
        assertEquals("HG DUOS  •  8 ALIVE", context(service, viewer));
        when(queues.tabMatchSnapshot(viewer)).thenReturn(new QueueService.TabMatchSnapshot("BATTLE ROYALE", 5, null));
        assertEquals("BATTLE ROYALE  •  5 ALIVE", context(service, viewer));
    }

    @Test
    void bossRushContextIsTheViewerOwnArena() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        BossRushService bossRush = mock(BossRushService.class);
        Player viewer = mock(Player.class);
        when(plugin.bossRushService()).thenReturn(bossRush);
        when(bossRush.tabArenaTeam(viewer)).thenReturn(RouletteTeam.BLUE);
        assertEquals("BOSS RUSH  •  TEAM BLUE", context(new HydraPlusService(plugin), viewer));
    }

    @Test
    void mrContextRequiresActualTournamentRoundAndStandaloneModesStayStandalone() throws Exception {
        MockBukkit.mock();
        try {
            HGLootPlugin plugin = mock(HGLootPlugin.class);
            MinigameRouletteService roulette = mock(MinigameRouletteService.class);
            BridgeService bridge = mock(BridgeService.class);
            SpleefService spleef = mock(SpleefService.class);
            VoidRouletteService voidRoulette = mock(VoidRouletteService.class);
            Player viewer = mock(Player.class);
            when(plugin.minigameRouletteService()).thenReturn(roulette);
            when(plugin.bridgeService()).thenReturn(bridge);
            when(plugin.spleefService()).thenReturn(spleef);
            when(plugin.voidRouletteService()).thenReturn(voidRoulette);
            HydraPlusService service = new HydraPlusService(plugin);
            when(roulette.tabRoundSnapshot(viewer)).thenReturn(
                new MinigameRouletteService.TabRoundSnapshot(2, 4, ManagedMinigameRound.Game.BRIDGE));
            assertEquals("MR  •  ROUND 2/4  •  BRIDGE", context(service, viewer));
            when(roulette.tabRoundSnapshot(viewer)).thenReturn(null);
            when(bridge.playerAudienceKey(viewer)).thenReturn("bridge:arena");
            assertEquals("BRIDGE", context(service, viewer));
            when(bridge.playerAudienceKey(viewer)).thenReturn(null);
            when(spleef.playerAudienceKey(viewer)).thenReturn("spleef:arena");
            assertEquals("SPLEEF", context(service, viewer));
            when(spleef.playerAudienceKey(viewer)).thenReturn(null);
            when(voidRoulette.playerAudienceKey(viewer)).thenReturn("voidroulette:arena");
            assertEquals("VOID ROULETTE", context(service, viewer));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void spectatorRowHasTruthfulMarkerInsteadOfHealth() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        QueueService queues = mock(QueueService.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Alex");
        when(plugin.queueService()).thenReturn(queues);
        when(queues.playerAudienceKey(player)).thenReturn("hg:arena");
        when(queues.isMatchAudienceSpectator(player.getUniqueId())).thenReturn(true);
        HydraPlusService service = new HydraPlusService(plugin);
        Method nameMethod = HydraPlusService.class.getDeclaredMethod("tabNameComponent", Player.class);
        nameMethod.setAccessible(true);
        String row = PlainTextComponentSerializer.plainText().serialize((Component) nameMethod.invoke(service, player));
        assertTrue(row.startsWith("Alex"));
        assertTrue(row.endsWith("SPEC"));
        assertFalse(row.contains("❤"));
    }

    private static String context(HydraPlusService service, Player viewer) throws Exception {
        Class<?> countsType = Class.forName("hgloot.plugin.HydraPlusService$TabActivityCounts");
        Constructor<?> constructor = countsType.getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        Method method = HydraPlusService.class.getDeclaredMethod("tabContextLine", Player.class, countsType);
        method.setAccessible(true);
        return PlainTextComponentSerializer.plainText().serialize((Component) method.invoke(service, viewer,
            constructor.newInstance(3, 2, 6)));
    }

    private static Component header(HydraPlusService service, Player viewer, int online,
                                    int hg, int br, int roulette) throws Exception {
        Class<?> countsType = Class.forName("hgloot.plugin.HydraPlusService$TabActivityCounts");
        Constructor<?> constructor = countsType.getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        Method headerMethod = HydraPlusService.class.getDeclaredMethod("tabHeaderComponent",
            Player.class, int.class, countsType);
        headerMethod.setAccessible(true);
        return (Component) headerMethod.invoke(service, viewer, online,
            constructor.newInstance(hg, br, roulette));
    }
}
