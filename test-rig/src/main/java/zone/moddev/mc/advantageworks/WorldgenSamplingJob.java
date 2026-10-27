package zone.moddev.mc.advantageworks;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The original sample order, with a bounded batch and a return to the server between phases. */
final class WorldgenSamplingJob {
    static final int SEARCH_BATCH = 256;
    static final int PLAN_BATCH = 64;
    static final int POPULATION_BATCH = 4;
    static final int COUNT_BATCH = 2;
    private static final int SEARCH_SIDE = 129;

    interface WorldAccess {
        boolean matchesBiome(String expected, int x, int y, int z);
        void populateChunk(int chunkX, int chunkZ);
        SampleChunk sampleChunk(int chunkX, int chunkZ);
    }

    interface SampleChunk {
        boolean centerMatches(String expected);
        String blockName(int x, int y, int z);
    }

    enum Phase { SPAWN, DESERT, DESERT_SQUARE, OCEAN, OCEAN_SQUARE, HALO, POPULATE, COUNT, DONE, FAILED }

    static final class Slot {
        private WorldgenSamplingJob active;
        void start(WorldgenSamplingJob job) {
            if (active != null) throw new IllegalStateException("A worldgen sample is already running");
            active = job;
        }
        WorldgenSamplingJob current() { return active; }
        void release() { active = null; }
    }

    private final WorldAccess world;
    private final int spawnX, spawnY, spawnZ, seaLevel, radius;
    private final Set<Long> chunks = new LinkedHashSet<>();
    private final Set<Long> populationChunks = new LinkedHashSet<>();
    private final Map<String, String> expectedBiomes = new LinkedHashMap<>();
    private final Map<String, String> centers = new LinkedHashMap<>();
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final Map<String, Long> violations = new LinkedHashMap<>();
    private final Map<String, Long> boundaryBlocks = new LinkedHashMap<>();
    private Phase phase = Phase.SPAWN;
    private int squareX, squareZ, squareIndex, searchIndex;
    private int locatedX, locatedZ;
    private boolean located;
    private double bestDistance = Double.POSITIVE_INFINITY;
    private Iterator<Long> iterator;
    private RuntimeException failure;

    WorldgenSamplingJob(WorldAccess world, int spawnX, int spawnY, int spawnZ, int seaLevel, int radius) {
        if (radius < 1 || radius > 16) throw new IllegalArgumentException("Worldgen radius must be 1..16");
        this.world = world;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.seaLevel = seaLevel;
        this.radius = radius;
        squareX = spawnX >> 4;
        squareZ = spawnZ >> 4;
        centers.put("spawn", squareX + "," + squareZ);
        expectedBiomes.put("poweradvantage:crude_oil", "DESERT");
        expectedBiomes.put("mineralogy:crude_oil", "OCEAN");
        expectedBiomes.put("electricadvantage:li_ore", null);
        expectedBiomes.put("electricadvantage:sulfur_ore", null);
        expectedBiomes.put("mineralogy:sulfur_ore", null);
        for (String id : expectedBiomes.keySet()) {
            counts.put(id, 0L);
            if (expectedBiomes.get(id) != null) {
                violations.put(id, 0L);
                boundaryBlocks.put(id, 0L);
            }
        }
    }

    void tick() {
        if (finished()) return;
        try {
            switch (phase) {
                case SPAWN: addSquare(Phase.DESERT); break;
                case DESERT: search("DESERT", Phase.DESERT_SQUARE, Phase.OCEAN); break;
                case DESERT_SQUARE: addSquare(Phase.OCEAN); break;
                case OCEAN: search("OCEAN", Phase.OCEAN_SQUARE, Phase.HALO); break;
                case OCEAN_SQUARE: addSquare(Phase.HALO); break;
                case HALO: planHalo(); break;
                case POPULATE: populate(); break;
                case COUNT: count(); break;
                default: break;
            }
        } catch (RuntimeException exception) {
            failure = exception;
            phase = Phase.FAILED;
        }
    }

    Phase phase() { return phase; }
    boolean finished() { return phase == Phase.DONE || phase == Phase.FAILED; }
    RuntimeException failure() { return failure; }

    Map<String, Object> result() {
        if (phase != Phase.DONE) throw new IllegalStateException("Worldgen sample is not complete", failure);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("radius", radius);
        payload.put("chunksScanned", chunks.size());
        payload.put("centers", centers);
        payload.put("counts", counts);
        payload.put("biomeViolations", violations);
        payload.put("biomeBoundaryBlocks", boundaryBlocks);
        return payload;
    }

    private void addSquare(Phase next) {
        int side = radius * 2 + 1;
        for (int work = 0; work < SEARCH_BATCH && squareIndex < side * side; work++, squareIndex++) {
            chunks.add(pack(squareX - radius + squareIndex % side, squareZ - radius + squareIndex / side));
        }
        if (squareIndex == side * side) {
            squareIndex = 0;
            phase = next;
        }
    }

    private void search(String expected, Phase found, Phase absent) {
        for (int work = 0; work < SEARCH_BATCH && searchIndex < SEARCH_SIDE * SEARCH_SIDE;
                work++, searchIndex++) {
            int x = ((spawnX >> 4) - 256 + searchIndex % SEARCH_SIDE * 4) << 4;
            int z = ((spawnZ >> 4) - 256 + searchIndex / SEARCH_SIDE * 4) << 4;
            if (!world.matchesBiome(expected, x, seaLevel, z)) continue;
            // Vec3i.distanceSq(Vec3i) uses the candidate block's center in the mapped native API.
            double dx = x + 0.5D - spawnX, dy = seaLevel + 0.5D - spawnY, dz = z + 0.5D - spawnZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                located = true;
                locatedX = x >> 4;
                locatedZ = z >> 4;
                bestDistance = distance;
            }
        }
        if (searchIndex == SEARCH_SIDE * SEARCH_SIDE) {
            centers.put(expected, located ? locatedX + "," + locatedZ : "NOT_FOUND");
            squareX = locatedX;
            squareZ = locatedZ;
            phase = located ? found : absent;
            searchIndex = 0;
            located = false;
            bestDistance = Double.POSITIVE_INFINITY;
        }
    }

    private void planHalo() {
        if (iterator == null) iterator = chunks.iterator();
        for (int work = 0; work < PLAN_BATCH && iterator.hasNext(); work++) {
            long packed = iterator.next();
            int x = (int) (packed >> 32), z = (int) packed;
            for (int haloZ = z - 2; haloZ <= z + 2; haloZ++) {
                for (int haloX = x - 2; haloX <= x + 2; haloX++) {
                    populationChunks.add(pack(haloX, haloZ));
                }
            }
        }
        if (!iterator.hasNext()) {
            iterator = populationChunks.iterator();
            phase = Phase.POPULATE;
        }
    }

    private void populate() {
        for (int work = 0; work < POPULATION_BATCH && iterator.hasNext(); work++) {
            long packed = iterator.next();
            world.populateChunk((int) (packed >> 32), (int) packed);
        }
        if (!iterator.hasNext()) {
            iterator = chunks.iterator();
            phase = Phase.COUNT;
        }
    }

    private void count() {
        for (int work = 0; work < COUNT_BATCH && iterator.hasNext(); work++) {
            long packed = iterator.next();
            int chunkX = (int) (packed >> 32), chunkZ = (int) packed;
            SampleChunk chunk = world.sampleChunk(chunkX, chunkZ);
            int minX = chunkX << 4, minZ = chunkZ << 4;
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int y = 0; y <= 96; y++) {
                        int x = minX + localX, z = minZ + localZ;
                        String name = chunk.blockName(x, y, z);
                        if (name == null || !counts.containsKey(name)) continue;
                        counts.put(name, counts.get(name) + 1L);
                        String expected = expectedBiomes.get(name);
                        if (expected != null && !chunk.centerMatches(expected)) {
                            violations.put(name, violations.get(name) + 1L);
                        } else if (expected != null && !world.matchesBiome(expected, x, y, z)) {
                            boundaryBlocks.put(name, boundaryBlocks.get(name) + 1L);
                        }
                    }
                }
            }
        }
        if (!iterator.hasNext()) phase = Phase.DONE;
    }

    private static long pack(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
}
