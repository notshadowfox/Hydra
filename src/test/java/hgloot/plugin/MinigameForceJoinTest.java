package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MinigameForceJoinTest {
    private ServerMock server;
    private PlayerMock admin;
    private PlayerMock alice;
    private final Set<UUID> queued = new HashSet<>();
    private int calls;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void allTargetsUseJoinPathExactlyOnceAndRepeatDoesNotDuplicate() {
        execute(new String[] {"vr", "forcejoin", "@a"}, 2);
        assertEquals(2, calls);
        assertEquals(Set.of(admin.getUniqueId(), alice.getUniqueId()), queued);
        execute(new String[] {"vr", "forcejoin", "@a"}, 2);
        assertEquals(2, calls);
    }

    @Test
    void namedTargetAndTrimmedBossrushArgumentsWork() {
        execute(new String[] {"forcejoin", "Alice"}, 1);
        assertEquals(Set.of(alice.getUniqueId()), queued);
    }

    @Test
    void permissionMissingTargetAndExtraArgumentsRejectWithoutJoining() {
        admin.setOp(false);
        execute(new String[] {"vr", "forcejoin", "@a"}, 2);
        admin.setOp(true);
        execute(new String[] {"vr", "forcejoin"}, 2);
        execute(new String[] {"vr", "forcejoin", "Offline"}, 2);
        execute(new String[] {"vr", "forcejoin", "Alice", "extra"}, 2);
        assertEquals(0, calls);
    }

    @Test
    void normalAdmissionRejectionIsNotBypassed() {
        MinigameForceJoin.execute(admin, new String[] {"vr", "forcejoin", "@a"}, 2, "vr",
            player -> calls++, player -> false);
        assertEquals(2, calls);
        assertTrue(queued.isEmpty());
        assertTrue(admin.nextMessage().contains("could not join"));
    }

    @Test
    void tabCompletionIncludesAllAndOnlineNames() {
        assertTrue(MinigameForceJoin.complete("").containsAll(Set.of("@a", "Alice", "Admin")));
        assertEquals(java.util.List.of("Alice"), MinigameForceJoin.complete("ali"));
    }

    @Test
    void everyStandaloneGameUsesSharedTargetsAndExistingJoin() throws Exception {
        for (String service : java.util.List.of("VoidRouletteService", "BridgeService", "SpleefService", "BossRushService")) {
            String source = Files.readString(Path.of("src/main/java/hgloot/plugin/" + service + ".java"));
            assertTrue(source.contains("case \"forcejoin\" -> MinigameForceJoin.execute("), service);
            assertTrue(source.contains("this::join,") || source.contains("this::joinForAdmin,"), service);
        }
        String plugin = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        assertTrue(plugin.contains("case \"vr\", \"voidroulette\", \"maceroulette\" -> {"));
        assertTrue(plugin.contains("case \"vr\", \"voidroulette\", \"maceroulette\" -> voidRouletteService"));
    }

    private void execute(String[] args, int index) {
        MinigameForceJoin.execute(admin, args, index, "vr", player -> {
            calls++;
            queued.add(player.getUniqueId());
        }, player -> queued.contains(player.getUniqueId()));
    }
}
