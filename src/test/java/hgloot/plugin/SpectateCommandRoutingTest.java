package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SpectateCommandRoutingTest {
    private HGLootPlugin plugin;
    private QueueService queues;
    private SpleefService spleef;
    private VoidRouletteService voidRoulette;
    private BridgeService bridge;
    private BossRushService bossRush;
    private MinigameRouletteService roulette;
    private GameHubService gameHub;
    private PlayerMock spectator;
    private PlayerMock target;
    private Command command;

    @BeforeEach
    void setup() throws Exception {
        var server = MockBukkit.mock();
        var hub = server.addSimpleWorld("Hub");
        var gameHubWorld = server.addSimpleWorld("GameHub");
        var match = server.addSimpleWorld("HG-World-Target");
        spectator = server.addPlayer("Spectator");
        target = server.addPlayer("Target");
        spectator.teleport(new Location(hub, 0, 80, 0));
        target.teleport(new Location(match, 0, 80, 0));
        queues = mock(QueueService.class);
        spleef = mock(SpleefService.class);
        voidRoulette = mock(VoidRouletteService.class);
        bridge = mock(BridgeService.class);
        bossRush = mock(BossRushService.class);
        roulette = mock(MinigameRouletteService.class);
        gameHub = mock(GameHubService.class);
        plugin = mock(HGLootPlugin.class, CALLS_REAL_METHODS);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SpectateCommandRoutingTest"));
        field("commandRoutes").set(plugin, new LinkedHashMap<>());
        field("tabRoutes").set(plugin, new LinkedHashMap<>());
        field("queueService").set(plugin, queues);
        field("gameHubService").set(plugin, gameHub);
        field("spleefService").set(plugin, spleef);
        field("voidRouletteService").set(plugin, voidRoulette);
        field("bridgeService").set(plugin, bridge);
        field("bossRushService").set(plugin, bossRush);
        field("minigameRouletteService").set(plugin, roulette);
        command = mock(Command.class);
        when(command.getName()).thenReturn("spectate");
        when(queues.spectatableMatchId(target)).thenReturn("queue:hungergames:HG-World-Target");
        when(queues.spectateTargetMatch(spectator, target,
            "queue:hungergames:HG-World-Target")).thenReturn(true);
        when(queues.isLobbyWorld(spectator)).thenAnswer(ignored -> spectator.getWorld() == hub);
        when(gameHub.isGameHub(gameHubWorld)).thenReturn(true);
        Method routes = HGLootPlugin.class.getDeclaredMethod("initializeRoutes");
        routes.setAccessible(true);
        routes.invoke(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void specAliasAndSpectatePrimaryWorkFromHubAndUncommittedGameHub() throws Exception {
        YamlConfiguration manifest = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/plugin.yml").toFile());
        assertTrue(manifest.getStringList("commands.spectate.aliases").contains("spec"));
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        var gameHubWorld = MockBukkit.getMock().getWorld("GameHub");
        spectator.teleport(new Location(gameHubWorld, 0, 80, 0));
        assertTrue(plugin.onCommand(spectator, command, "spectate", new String[] {target.getName()}));
        verify(queues, times(2)).spectateTargetMatch(spectator, target,
            "queue:hungergames:HG-World-Target");
    }

    @Test
    void gameHubQueueAndStartCommitmentAreRejectedWithoutSpectatorHandoff() {
        spectator.teleport(new Location(MockBukkit.getMock().getWorld("GameHub"), 0, 80, 0));
        when(queues.isPlayerActivelyQueuedOrBound(spectator.getUniqueId())).thenReturn(true);
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("Leave your queue"));
        when(queues.isPlayerActivelyQueuedOrBound(spectator.getUniqueId())).thenReturn(true);
        assertTrue(plugin.onCommand(spectator, command, "spectate", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("Leave your queue"));
        verify(queues, never()).spectateTargetMatch(spectator, target,
            "queue:hungergames:HG-World-Target");
    }

    @Test
    void bareCommandKeepsUsageBehavior() {
        assertTrue(plugin.onCommand(spectator, command, "spectate", new String[0]));
        assertEquals("§cUsage: /spectate <player>", spectator.nextMessage());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "HG Solos,queue:hungergames:HG-World-Target",
        "HG Duos,queue:hungergames_duos:HG-World-Target",
        "Battle Royale,queue:solo:HG-World-Target",
        "Spleef standalone,spleef:Spleef-Standalone-1",
        "Spleef MR,spleef:Spleef-World-1",
        "Void Roulette standalone,voidroulette:Void-Standalone-1",
        "Void Roulette MR,voidroulette:Void-World-1",
        "Bridge standalone,bridge:Bridge-Standalone-1",
        "Bridge MR,bridge:Bridge-World-1",
        "Boss Rush standalone,bossrush:Boss-World-1:RED",
        "Boss Rush MR,bossrush:Boss-World-2:BLUE"
    })
    void supportedTargetModesUseOneAuthoritativeEntry(String mode, String id) {
        when(queues.spectatableMatchId(target)).thenReturn(null);
        if (id.startsWith("queue:")) {
            when(queues.spectatableMatchId(target)).thenReturn(id);
            when(queues.spectateTargetMatch(spectator, target, id)).thenReturn(true);
        } else if (id.startsWith("spleef:")) {
            when(spleef.spectatableMatchId(target)).thenReturn(id);
            when(spleef.spectateTargetMatch(spectator, target, id)).thenReturn(true);
        } else if (id.startsWith("voidroulette:")) {
            when(voidRoulette.spectatableMatchId(target)).thenReturn(id);
            when(voidRoulette.spectateTargetMatch(spectator, target, id)).thenReturn(true);
        } else if (id.startsWith("bridge:")) {
            when(bridge.spectatableMatchId(target)).thenReturn(id);
            when(bridge.spectateTargetMatch(spectator, target, id)).thenReturn(true);
        } else {
            when(bossRush.spectatableMatchId(target)).thenReturn(id);
            when(bossRush.spectateTargetMatch(spectator, target, id)).thenReturn(true);
        }
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        if (id.startsWith("queue:")) verify(queues).spectateTargetMatch(spectator, target, id);
        else if (id.startsWith("spleef:")) verify(spleef).spectateTargetMatch(spectator, target, id);
        else if (id.startsWith("voidroulette:")) verify(voidRoulette).spectateTargetMatch(spectator, target, id);
        else if (id.startsWith("bridge:")) verify(bridge).spectateTargetMatch(spectator, target, id);
        else verify(bossRush).spectateTargetMatch(spectator, target, id);
    }

    @Test
    void activeMatchInvokerIsRejectedWithoutEntry() {
        when(queues.isPlayerInHydraMatchOrSpectating(spectator.getUniqueId())).thenReturn(true);
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("Leave your current match"));
        verify(queues, never()).spectateTargetMatch(spectator, target,
            "queue:hungergames:HG-World-Target");
    }

    @Test
    void rouletteContestantIsRejectedWithoutChangingTournamentOwnership() {
        when(roulette.isInActiveTournament(spectator.getUniqueId())).thenReturn(true);
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("Leave your current match"));
        verify(queues, never()).spectateTargetMatch(spectator, target,
            "queue:hungergames:HG-World-Target");
        verify(roulette).isInActiveTournament(spectator.getUniqueId());
    }

    @Test
    void routingServiceDoesNotCreateAnotherSpectatorRegistry() {
        for (Field field : SpectatorRoutingService.class.getDeclaredFields()) {
            assertTrue(field.getType() == HGLootPlugin.class,
                "Unexpected routing state: " + field.getName());
        }
    }

    @Test
    void endingOrAmbiguousTargetFailsWithoutAttachingToWrongMatch() {
        when(queues.spectateTargetMatch(spectator, target,
            "queue:hungergames:HG-World-Target")).thenReturn(false);
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("no longer available"));
        when(spleef.spectatableMatchId(target)).thenReturn("spleef:stale-instance");
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("not in an active spectatable match"));
        verify(spleef, never()).spectateTargetMatch(spectator, target, "spleef:stale-instance");
    }

    @Test
    void offlineSelfAndUnownedTargetsReceiveCleanFeedback() {
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {"MissingPlayer"}));
        assertTrue(spectator.nextMessage().contains("not online"));
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {spectator.getName()}));
        assertTrue(spectator.nextMessage().contains("cannot spectate yourself"));
        when(queues.spectatableMatchId(target)).thenReturn(null);
        assertTrue(plugin.onCommand(spectator, command, "spec", new String[] {target.getName()}));
        assertTrue(spectator.nextMessage().contains("not in an active spectatable match"));
    }

    private Field field(String name) throws Exception {
        Field field = HGLootPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
