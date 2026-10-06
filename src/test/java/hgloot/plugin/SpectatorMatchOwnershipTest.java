package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SpectatorMatchOwnershipTest {
    @Test
    @SuppressWarnings("unchecked")
    void bridgeAndVoidRequireActiveRegisteredTargetInExactWorld() throws Exception {
        var server = MockBukkit.mock();
        try {
            var hub = server.addSimpleWorld("Hub");
            var bridgeWorld = server.addSimpleWorld("bridge-world-12");
            var voidWorld = server.addSimpleWorld("void-world-9");
            var target = server.addPlayer("Target");
            var viewer = server.addPlayer("Viewer");
            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.namespace()).thenReturn("hydramain");
            BridgeService bridge = new BridgeService(plugin);
            VoidRouletteService voidRoulette = new VoidRouletteService(plugin);
            field(bridge, "activeWorld").set(bridge, bridgeWorld);
            field(bridge, "activeWorldName").set(bridge, bridgeWorld.getName());
            field(bridge, "matchActive").setBoolean(bridge, true);
            Class<Enum> bridgeTeam = (Class<Enum>) Class.forName("hgloot.plugin.BridgeService$BridgeTeam");
            ((Map<UUID, Object>) field(bridge, "playerTeams").get(bridge))
                .put(target.getUniqueId(), Enum.valueOf(bridgeTeam, "RED"));
            field(voidRoulette, "activeWorld").set(voidRoulette, voidWorld);
            field(voidRoulette, "activeWorldName").set(voidRoulette, voidWorld.getName());
            field(voidRoulette, "matchActive").setBoolean(voidRoulette, true);
            ((Set<UUID>) field(voidRoulette, "alivePlayers").get(voidRoulette)).add(target.getUniqueId());

            target.teleport(new Location(bridgeWorld, 0, 80, 0));
            assertEquals("bridge:bridge-world-12", bridge.spectatableMatchId(target));
            assertNull(voidRoulette.spectatableMatchId(target));
            assertFalse(bridge.spectateTargetMatch(viewer, target, "bridge:old-match"));
            target.teleport(new Location(voidWorld, 0, 80, 0));
            assertNull(bridge.spectatableMatchId(target));
            assertEquals("voidroulette:void-world-9", voidRoulette.spectatableMatchId(target));
            assertFalse(voidRoulette.spectateTargetMatch(viewer, target, "voidroulette:old-match"));
            target.teleport(new Location(hub, 0, 80, 0));
            assertNull(bridge.spectatableMatchId(target));
            assertNull(voidRoulette.spectatableMatchId(target));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void spleefManagerResolvesItsActualChildInstance() throws Exception {
        var server = MockBukkit.mock();
        try {
            var hub = server.addSimpleWorld("Hub");
            var world = server.addSimpleWorld("spleef-world-7");
            var target = server.addPlayer("Target");
            var viewer = server.addPlayer("Viewer");
            HGLootPlugin plugin = mock(HGLootPlugin.class);
            when(plugin.namespace()).thenReturn("hydramain");
            SpleefService manager = new SpleefService(plugin);
            var constructor = SpleefService.class.getDeclaredConstructor(HGLootPlugin.class, SpleefService.class);
            constructor.setAccessible(true);
            SpleefService match = constructor.newInstance(plugin, manager);
            field(match, "matchActive").setBoolean(match, true);
            field(match, "activeWorldName").set(match, world.getName());
            ((Set<UUID>) field(match, "alivePlayers").get(match)).add(target.getUniqueId());
            ((Map<UUID, SpleefService>) field(manager, "activeMatchesByPlayer").get(manager))
                .put(target.getUniqueId(), match);
            target.teleport(new Location(world, 0, 80, 0));
            assertEquals("spleef:spleef-world-7", manager.spectatableMatchId(target));
            assertFalse(manager.spectateTargetMatch(viewer, target, "spleef:prior-round"));
            target.teleport(new Location(hub, 0, 80, 0));
            assertNull(manager.spectatableMatchId(target));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void childSpectatorEntryDoesNotMutateTournamentRosterOrAliveTeams() throws Exception {
        for (String service : new String[] {"SpleefService", "BridgeService", "VoidRouletteService"}) {
            String source = Files.readString(Path.of("src/main/java/hgloot/plugin/" + service + ".java"));
            int start = source.indexOf("public boolean spectateTargetMatch(Player spectator, Player target, String expectedMatchId)");
            org.junit.jupiter.api.Assertions.assertTrue(start >= 0);
            int end = source.indexOf("public String playerAudienceKey", start);
            if (end < start) end = Math.min(source.length(), start + 3200);
            String entry = source.substring(start, end);
            org.junit.jupiter.api.Assertions.assertTrue(entry.contains("externalSpectators.add(spectatorId)"));
            org.junit.jupiter.api.Assertions.assertTrue(source.contains("requeueCandidates.removeAll(externalSpectators);"));
            assertFalse(entry.contains("managedRound.roster().add("));
            assertFalse(entry.contains("alivePlayers.add(spectatorId)"));
            assertFalse(entry.contains("playerTeams.put(spectatorId"));
        }
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
