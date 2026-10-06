package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

final class BridgeWorldStartupContractTest {
    private static final Path SERVICE = Path.of("src/main/java/hgloot/plugin/BridgeService.java");
    private static final Path MAPS = Path.of("src/main/resources/Structure nbt/minigames/bridge");

    @Test
    void acceptedMatchRemainsStartingThroughMapPreparationAndFadeMidpoint() throws Exception {
        String source = Files.readString(SERVICE);
        int start = source.indexOf("private boolean startMatch(List<UUID> participants, boolean forced)");
        int end = source.indexOf("private BridgeTeam bridgeTeam(", start);
        assertTrue(start >= 0 && end > start);
        String match = source.substring(start, end);

        int accepted = match.indexOf("matchStarting = true;");
        int world = match.indexOf("World world = createMatchWorld();");
        int load = match.indexOf("Bukkit.getStructureManager().loadStructure(mapPath.toFile())");
        int place = match.indexOf("placeStructureCentered(world, structure)");
        int goalScan = match.indexOf("discoverGoalClusters(world)");
        int fade = match.indexOf("plugin.transitionTravel(player, () -> {");
        int guard = match.indexOf("if (!matchStarting || activeWorld != world || !player.isOnline()) return;", fade);
        int teleport = match.indexOf("player.teleport(destination);", guard);
        int started = match.indexOf("matchActive = true;");
        int clear = match.indexOf("matchStarting = false;", started);

        assertTrue(accepted > match.indexOf("if (participants.size() < floor)"));
        assertTrue(accepted < world && world < load && load < place && place < goalScan);
        assertTrue(goalScan < fade && fade < guard && guard < teleport);
        assertTrue(started > goalScan && clear > started);
    }

    @Test
    void bundledStructuresArePresentAndStaleInstanceFoldersAreSkipped() throws Exception {
        String source = Files.readString(SERVICE);
        for (String name : List.of("Bridge.nbt", "Bridge ocean.nbt", "outerendbridge.nbt")) {
            Path map = MAPS.resolve(name);
            assertTrue(Files.isRegularFile(map), name);
            assertTrue(Files.size(map) > 0, name);
            try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(map));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                gzip.transferTo(output);
                assertTrue(new String(output.toByteArray(), StandardCharsets.ISO_8859_1)
                    .contains("minecraft:end_portal"), name);
            }
        }
        int create = source.indexOf("private World createMatchWorld()");
        int next = source.indexOf("private boolean placeStructureCentered(", create);
        String world = source.substring(create, next);
        assertTrue(world.contains("\"Bridge-World-\" + nextWorldNumber++"));
        assertTrue(world.contains("Files.exists(Bukkit.getWorldContainer().toPath().resolve(worldName))"));
        assertTrue(world.indexOf("creator.generator(new EmptyBridgeChunkGenerator")
            < world.indexOf("Bukkit.createWorld(creator)"));
    }
}
