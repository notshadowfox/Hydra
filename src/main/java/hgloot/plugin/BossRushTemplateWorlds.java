package hgloot.plugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** One owned, validated Boss Rush clone. No tile placement and no multi-match pool. */
final class BossRushTemplateWorlds {
    enum State { PREPARING, READY, CLAIMED, DISPOSING, EMPTY }

    private static final String[] HASHES = {
        "02E06FE51D41B0E55F8C7CD167FFC49AC836C602BEF7E5F1DC30407C1F654D4F",
        "B43AE73C8DE41C64E9BFB14A03F08328850DDA36129B50E8A2BCD7CC95CB354C",
        "3956670E4745C8D56EB60F1C536E1087DC910A0FF8D42F14C7FCC847833EF9DA",
        "CC12E62245CEACDE4FC70241BA5E77E806827EF6D5AA65989FA08F8D9BA70176"
    };
    private static final String MARKER = ".hydra-bossrush-clone";
    private static final String[] REGIONS = {"r.-1.-1.mca", "r.0.-1.mca", "r.-1.0.mca", "r.0.0.mca"};
    private final JavaPlugin plugin;
    private final ChunkGenerator generator;
    private final Predicate<World> validator;
    private final Consumer<World> initializer;
    private final Path container;
    private final Set<Path> copiesInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private State state = State.EMPTY;
    private int teams;
    private int generation;
    private Path folder;
    private World world;
    private Consumer<World> readyCallback;
    private Consumer<Exception> failureCallback;
    private long startedNanos;
    private long cloneNanos;
    private long loadNanos;
    private long chunksNanos;

    BossRushTemplateWorlds(JavaPlugin plugin, ChunkGenerator generator, Consumer<World> initializer,
                           Predicate<World> validator) {
        this.plugin = plugin;
        this.generator = generator;
        this.validator = validator;
        this.initializer = initializer;
        this.container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    State state() { return state; }
    int teams() { return teams; }

    /** Called once the expected occupied-team prefix is known. Main thread only. */
    void prepare(int requestedTeams) {
        if (requestedTeams < 1 || requestedTeams > 4) throw new IllegalArgumentException("teams=" + requestedTeams);
        if (state == State.PREPARING || state == State.READY) {
            if (teams == requestedTeams) return;
            if (!dispose()) return;
        } else if (state != State.EMPTY) {
            return;
        }
        teams = requestedTeams;
        state = State.PREPARING;
        int ticket = ++generation;
        startedNanos = System.nanoTime();
        String name = "BossRush-World-" + java.util.UUID.randomUUID();
        Path target = container.resolve(name).normalize();
        folder = target;
        copiesInFlight.add(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Exception failure = null;
            try {
                extract(requestedTeams, target);
            } catch (Exception ex) {
                failure = ex;
            } finally {
                copiesInFlight.remove(target);
            }
            Exception result = failure;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ticket != generation || state != State.PREPARING) {
                    if (Files.exists(target)) deleteOwned(target);
                    return;
                }
                if (result != null) { fail(result); return; }
                cloneNanos = System.nanoTime() - startedNanos;
                load(ticket, name);
            });
        });
    }

    void claim(int requestedTeams, Consumer<World> onReady, Consumer<Exception> onFailure) {
        if (state == State.READY && teams == requestedTeams) {
            readyCallback = onReady;
            failureCallback = onFailure;
            publishClaim();
            return;
        }
        if (state != State.PREPARING || teams != requestedTeams) prepare(requestedTeams);
        if (state != State.PREPARING) {
            onFailure.accept(new IllegalStateException("No safe Boss Rush arena could be prepared"));
            return;
        }
        readyCallback = onReady;
        failureCallback = onFailure;
    }

    private void publishClaim() {
        state = State.CLAIMED;
        Consumer<World> callback = readyCallback;
        readyCallback = null;
        failureCallback = null;
        if (callback != null) callback.accept(world);
    }

    private void load(int ticket, String name) {
        long start = System.nanoTime();
        try {
            WorldCreator creator = new WorldCreator(name).environment(World.Environment.NORMAL)
                .type(WorldType.FLAT).generator(generator).generateStructures(false);
            world = Bukkit.createWorld(creator);
            if (world == null) throw new IOException("Paper returned null for " + name);
            world.setAutoSave(false);
            initializer.accept(world);
            loadNanos = System.nanoTime() - start;
            warmRequiredChunks(ticket);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    private void warmRequiredChunks(int ticket) {
        long start = System.nanoTime();
        List<int[]> chunks = requiredChunks(teams);
        warmNext(ticket, chunks, 0, start);
    }

    private void warmNext(int ticket, List<int[]> chunks, int index, long start) {
        if (ticket != generation || state != State.PREPARING) return;
        if (index == chunks.size()) {
            chunksNanos = System.nanoTime() - start;
            try {
                if (!validator.test(world)) throw new IOException("Arena markers, barrels or static state failed validation");
                state = State.READY;
                plugin.getLogger().info("[BossRushTemplate] READY teams=" + teams
                    + " clone_ms=" + millis(cloneNanos) + " world_load_ms=" + millis(loadNanos)
                    + " required_chunks=" + chunks.size() + " chunk_ready_ms=" + millis(chunksNanos)
                    + " total_ms=" + millis(System.nanoTime() - startedNanos));
                if (readyCallback != null) publishClaim();
            } catch (Exception ex) { fail(ex); }
            return;
        }
        int[] chunk = chunks.get(index);
        world.getChunkAtAsync(chunk[0], chunk[1], false).whenComplete((loaded, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ticket != generation || state != State.PREPARING) return;
                if (error != null || loaded == null) {
                    fail(new IOException("Required chunk missing at " + chunk[0] + "," + chunk[1], error));
                    return;
                }
                // One completion per tick avoids a synchronous all-chunk preload and tick spikes.
                Bukkit.getScheduler().runTaskLater(plugin, () -> warmNext(ticket, chunks, index + 1, start), 1L);
            }));
    }

    static List<int[]> requiredChunks(int count) {
        int[][] centers = {{-240,-240}, {240,-240}, {-240,240}, {240,240}};
        int[][] markers = {{97,136}, {136,98}, {136,174}, {174,136}};
        Set<Long> seen = new HashSet<>();
        List<int[]> result = new ArrayList<>();
        for (int team = 0; team < count; team++) {
            int pasteX = centers[team][0] - 132;
            int pasteZ = centers[team][1] - 136;
            addChunk(result, seen, centers[team][0], centers[team][1]);
            for (int[] marker : markers) {
                int x = pasteX + marker[0], z = pasteZ + marker[1];
                for (int dx = -8; dx <= 8; dx += 8) {
                    for (int dz = -8; dz <= 8; dz += 8) addChunk(result, seen, x + dx, z + dz);
                }
            }
        }
        return result;
    }

    private static void addChunk(List<int[]> result, Set<Long> seen, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        if (seen.add(key)) result.add(new int[] {cx, cz});
    }

    private void fail(Exception ex) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRushTemplate] Arena preparation failed", ex);
        Consumer<Exception> callback = failureCallback;
        readyCallback = null;
        failureCallback = null;
        dispose();
        if (callback != null) callback.accept(ex);
    }

    /** Returns false if unload/deletion failed; such a folder is never reused. */
    boolean dispose() {
        long disposingStarted = System.nanoTime();
        int disposedTeams = teams;
        ++generation;
        state = State.DISPOSING;
        readyCallback = null;
        failureCallback = null;
        if (world != null && !Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().severe("[BossRushTemplate] Unload failed; retaining " + world.getName());
            return false;
        }
        world = null;
        if (folder != null && Files.exists(folder) && !copiesInFlight.contains(folder) && !deleteOwned(folder)) {
            plugin.getLogger().severe("[BossRushTemplate] Clone deletion failed; retaining " + folder);
            return false;
        }
        folder = null;
        teams = 0;
        state = State.EMPTY;
        if (disposedTeams > 0) plugin.getLogger().info("[BossRushTemplate] DISPOSED teams=" + disposedTeams
            + " elapsed_ms=" + millis(System.nanoTime() - disposingStarted));
        return true;
    }

    /** Crash leftovers are identified by our marker; unmarked folders are retained, never claimed. */
    void recoverStaleClones() {
        try (Stream<Path> folders = Files.list(container)) {
            folders.filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("BossRush-World-"))
                .filter(path -> Bukkit.getWorld(path.getFileName().toString()) == null)
                .forEach(path -> {
                    if (Files.isRegularFile(path.resolve(MARKER))) {
                        if (!deleteOwned(path)) plugin.getLogger().severe("[BossRushTemplate] Stale clone retained " + path);
                    } else {
                        plugin.getLogger().warning("[BossRushTemplate] Unmarked old arena retained, never claimable: " + path);
                    }
                });
        } catch (IOException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRushTemplate] Startup recovery failed", ex);
        }
    }

    private boolean deleteOwned(Path target) {
        if (!target.startsWith(container) || target.equals(container) || !Files.isRegularFile(target.resolve(MARKER))) return false;
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BossRushTemplate] Failed deleting " + target, ex);
            return false;
        }
    }

    private void extract(int count, Path target) throws Exception {
        if (!target.startsWith(container) || target.equals(container)) throw new IOException("Unsafe clone path");
        String resource = "bossrush/templates/teams-" + count + ".zip";
        byte[] bytes;
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) throw new IOException("Missing " + resource);
            bytes = input.readAllBytes();
        }
        String hash = java.util.HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!HASHES[count - 1].equals(hash)) throw new IOException("Template checksum mismatch: " + resource);
        Files.createDirectory(target);
        Files.createFile(target.resolve(MARKER));
        Set<String> required = new HashSet<>(Set.of("level.dat", "paper-world.yml"));
        for (int i = 0; i < count; i++) {
            required.add("region/" + REGIONS[i]);
            required.add("poi/" + REGIONS[i]);
        }
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (entry.isDirectory() || !required.remove(entry.getName())) throw new IOException("Unexpected or duplicate template entry: " + entry.getName());
                Path destination = target.resolve(entry.getName()).normalize();
                if (!destination.startsWith(target)) throw new IOException("Unsafe template entry");
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination);
            }
        }
        if (!required.isEmpty()) throw new IOException("Template missing entries: " + required);
    }

    private static long millis(long nanos) { return nanos / 1_000_000L; }
}
