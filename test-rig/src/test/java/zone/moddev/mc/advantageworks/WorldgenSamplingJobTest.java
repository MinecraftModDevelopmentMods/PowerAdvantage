package zone.moddev.mc.advantageworks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldgenSamplingJobTest {
    @Test public void everyTickHasAFiniteBatchAndAllHaloPopulationPrecedesCounting() {
        FakeWorld world = new FakeWorld();
        WorldgenSamplingJob job = job(world, 16);
        assertEquals(0, world.biomeQueries);
        int ticks = 0;
        while (!job.finished()) {
            int biomes = world.biomeQueries, populated = world.populated.size(), sampled = world.sampled.size();
            long blocks = world.blocks;
            WorldgenSamplingJob.Phase phase = job.phase();
            job.tick();
            assertTrue(world.biomeQueries - biomes <= WorldgenSamplingJob.SEARCH_BATCH);
            assertTrue(world.populated.size() - populated <= WorldgenSamplingJob.POPULATION_BATCH);
            assertTrue(world.sampled.size() - sampled <= WorldgenSamplingJob.COUNT_BATCH);
            assertTrue(world.blocks - blocks <= WorldgenSamplingJob.COUNT_BATCH * 16L * 16L * 97L);
            if (phase != WorldgenSamplingJob.Phase.COUNT) assertEquals(0, world.sampled.size());
            assertTrue("Job did not finish", ++ticks < 5000);
        }
        assertNull(job.failure());
        assertTrue(ticks > 100);
        assertEquals(world.sampled.size() * 16L * 16L * 97L, world.blocks);
        assertEquals(0, world.minY);
        assertEquals(96, world.maxY);
        assertEquals(new LinkedHashSet<>(world.populated).size(), world.populated.size());
        assertEquals(new LinkedHashSet<>(world.sampled).size(), world.sampled.size());
    }

    @Test public void resultsAndVisitOrderEqualTheOldAlgorithmAtNegativeCoordinates() {
        assertOldAlgorithmEqual(1, false);
        assertOldAlgorithmEqual(2, false);
    }

    @Test public void radiusSixteenRetainsTheExactOldCountsCentersHaloAndBounds() {
        assertOldAlgorithmEqual(16, false, true);
    }

    @Test public void absentBiomeCentersRetainNotFoundAndTheSpawnSquare() {
        assertOldAlgorithmEqual(1, true);
    }

    @Test public void nativeBlockCenterDistanceTieBreakIsNotIntegerCornerDistance() {
        FakeWorld world = new FakeWorld();
        world.nativeDistanceTie = true;
        WorldgenSamplingJob job = new WorldgenSamplingJob(world, 0, 70, 0, 64, 1);
        finish(job);
        // Integer corners tie: (0,-20) visits first, but the native center prefers (-16,-12).
        assertEquals("-16,-12", ((Map<?, ?>) job.result().get("centers")).get("DESERT"));
    }

    @Test public void duplicateRequestsDoNotReplaceAnActiveJob() {
        WorldgenSamplingJob.Slot slot = new WorldgenSamplingJob.Slot();
        WorldgenSamplingJob first = job(new FakeWorld(), 1);
        slot.start(first);
        try { slot.start(job(new FakeWorld(), 1)); fail("Duplicate job accepted"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("already running")); }
        assertSame(first, slot.current());
        slot.release();
        WorldgenSamplingJob next = job(new FakeWorld(), 1);
        slot.start(next);
        assertSame(next, slot.current());
    }

    @Test public void populationFailureIsTerminalAndNeverProducesAPartialReport() {
        FakeWorld world = new FakeWorld();
        world.failPopulation = true;
        WorldgenSamplingJob job = job(world, 1);
        finish(job);
        assertEquals(WorldgenSamplingJob.Phase.FAILED, job.phase());
        assertNotNull(job.failure());
        assertEquals(0, world.sampled.size());
        int populated = world.populated.size();
        job.tick();
        assertEquals(populated, world.populated.size());
        requireIncomplete(job);
    }

    @Test public void countFailureIsTerminalAndNeverProducesAPartialReport() {
        FakeWorld world = new FakeWorld();
        world.failCount = true;
        WorldgenSamplingJob job = job(world, 1);
        finish(job);
        assertEquals(WorldgenSamplingJob.Phase.FAILED, job.phase());
        assertNotNull(job.failure());
        long blocks = world.blocks;
        job.tick();
        assertEquals(blocks, world.blocks);
        requireIncomplete(job);
    }

    @Test public void anUnfinishedJobHasNoCompletedResult() { requireIncomplete(job(new FakeWorld(), 1)); }

    @Test public void completionIsIdempotent() {
        FakeWorld world = new FakeWorld();
        WorldgenSamplingJob job = job(world, 1);
        finish(job);
        Map<String, Object> result = job.result();
        long blocks = world.blocks;
        job.tick();
        assertEquals(result, job.result());
        assertEquals(blocks, world.blocks);
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsZeroRadius() { job(new FakeWorld(), 0); }
    @Test(expected = IllegalArgumentException.class) public void rejectsRadiusAboveSixteen() { job(new FakeWorld(), 17); }

    private static WorldgenSamplingJob job(FakeWorld world, int radius) {
        return new WorldgenSamplingJob(world, -1, 70, -17, 64, radius);
    }

    private static void finish(WorldgenSamplingJob job) {
        int ticks = 0;
        while (!job.finished()) {
            job.tick();
            assertTrue(++ticks < 5000);
        }
    }

    private static void requireIncomplete(WorldgenSamplingJob job) {
        try { job.result(); fail("Partial report accepted"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("not complete")); }
    }

    private static void assertOldAlgorithmEqual(int radius, boolean absent) {
        assertOldAlgorithmEqual(radius, absent, false);
    }

    private static void assertOldAlgorithmEqual(int radius, boolean absent, boolean disjoint) {
        FakeWorld oldWorld = new FakeWorld(), batchedWorld = new FakeWorld();
        oldWorld.absent = batchedWorld.absent = absent;
        oldWorld.disjoint = batchedWorld.disjoint = disjoint;
        Map<String, Object> old = synchronousReference(oldWorld, radius);
        WorldgenSamplingJob job = job(batchedWorld, radius);
        finish(job);
        assertNull(job.failure());
        assertEquals(old, job.result());
        assertEquals(oldWorld.populated, batchedWorld.populated);
        assertEquals(oldWorld.sampled, batchedWorld.sampled);
        assertEquals(oldWorld.biomeQueries, batchedWorld.biomeQueries);
        assertEquals(oldWorld.blocks, batchedWorld.blocks);
        if (disjoint) {
            assertEquals(3267, job.result().get("chunksScanned"));
            assertEquals(4107, batchedWorld.populated.size());
            assertEquals(3267L * 16L * 16L * 97L, batchedWorld.blocks);
        }
    }

    // Independent transcription of C's synchronous loops, including native candidate-center distance.
    private static Map<String, Object> synchronousReference(FakeWorld world, int radius) {
        int spawnX = -1, spawnY = 70, spawnZ = -17;
        Set<Long> chunks = new LinkedHashSet<>();
        Map<String, String> centers = new LinkedHashMap<>();
        square(chunks, spawnX >> 4, spawnZ >> 4, radius);
        centers.put("spawn", (spawnX >> 4) + "," + (spawnZ >> 4));
        for (String biome : Arrays.asList("DESERT", "OCEAN")) {
            int[] located = null;
            double best = Double.POSITIVE_INFINITY;
            for (int offsetZ = -256; offsetZ <= 256; offsetZ += 4) {
                for (int offsetX = -256; offsetX <= 256; offsetX += 4) {
                    int x = ((spawnX >> 4) + offsetX) << 4, z = ((spawnZ >> 4) + offsetZ) << 4;
                    if (!world.matchesBiome(biome, x, 64, z)) continue;
                    double dx = x + 0.5D - spawnX, dy = 64.5D - spawnY, dz = z + 0.5D - spawnZ;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < best) { located = new int[] {x >> 4, z >> 4}; best = distance; }
                }
            }
            centers.put(biome, located == null ? "NOT_FOUND" : located[0] + "," + located[1]);
            if (located != null) square(chunks, located[0], located[1], radius);
        }
        Set<Long> halo = new LinkedHashSet<>();
        for (long packed : chunks) square(halo, (int) (packed >> 32), (int) packed, 2);
        for (long packed : halo) world.populateChunk((int) (packed >> 32), (int) packed);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("poweradvantage:crude_oil", "DESERT");
        expected.put("mineralogy:crude_oil", "OCEAN");
        expected.put("electricadvantage:li_ore", null);
        expected.put("electricadvantage:sulfur_ore", null);
        expected.put("mineralogy:sulfur_ore", null);
        Map<String, Long> counts = new LinkedHashMap<>(), violations = new LinkedHashMap<>(), boundaries = new LinkedHashMap<>();
        for (String id : expected.keySet()) {
            counts.put(id, 0L);
            if (expected.get(id) != null) { violations.put(id, 0L); boundaries.put(id, 0L); }
        }
        for (long packed : chunks) {
            int chunkX = (int) (packed >> 32), chunkZ = (int) packed;
            WorldgenSamplingJob.SampleChunk chunk = world.sampleChunk(chunkX, chunkZ);
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int y = 0; y <= 96; y++) {
                        int x = (chunkX << 4) + localX, z = (chunkZ << 4) + localZ;
                        String name = chunk.blockName(x, y, z);
                        if (name == null || !counts.containsKey(name)) continue;
                        counts.put(name, counts.get(name) + 1L);
                        String biome = expected.get(name);
                        if (biome != null && !chunk.centerMatches(biome)) violations.put(name, violations.get(name) + 1L);
                        else if (biome != null && !world.matchesBiome(biome, x, y, z)) boundaries.put(name, boundaries.get(name) + 1L);
                    }
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("radius", radius);
        payload.put("chunksScanned", chunks.size());
        payload.put("centers", centers);
        payload.put("counts", counts);
        payload.put("biomeViolations", violations);
        payload.put("biomeBoundaryBlocks", boundaries);
        return payload;
    }

    private static void square(Set<Long> chunks, int centerX, int centerZ, int radius) {
        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) chunks.add(pack(x, z));
        }
    }

    private static long pack(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static final class FakeWorld implements WorldgenSamplingJob.WorldAccess {
        final List<Long> populated = new ArrayList<>(), sampled = new ArrayList<>();
        int biomeQueries, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        long blocks;
        boolean absent, failPopulation, failCount, nativeDistanceTie, disjoint;

        @Override public boolean matchesBiome(String expected, int x, int y, int z) {
            biomeQueries++;
            if (absent) return false;
            if (nativeDistanceTie) return "DESERT".equals(expected)
                    && ((x == 0 && z == -320) || (x == -256 && z == -192));
            if (disjoint) return z == -32 && x == ("DESERT".equals(expected) ? -1296 : 1264);
            boolean region = "DESERT".equals(expected) ? x < 0 : x >= 64;
            return region && (y == 64 || ((x + z) & 1) != 0);
        }

        @Override public void populateChunk(int x, int z) {
            if (failPopulation) throw new IllegalStateException("population failed");
            populated.add(pack(x, z));
        }

        @Override public WorldgenSamplingJob.SampleChunk sampleChunk(int chunkX, int chunkZ) {
            sampled.add(pack(chunkX, chunkZ));
            return new WorldgenSamplingJob.SampleChunk() {
                @Override public boolean centerMatches(String expected) {
                    return "DESERT".equals(expected) ? chunkX < 0 : chunkX >= 4;
                }
                @Override public String blockName(int x, int y, int z) {
                    if (failCount) throw new IllegalStateException("count failed");
                    if ((x >> 4) != chunkX || (z >> 4) != chunkZ || y < 0 || y > 96) {
                        throw new AssertionError("Scan escaped the original chunk/y bounds");
                    }
                    blocks++;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    if ((z & 15) != 0) return null;
                    if ((x & 15) == 0 && y == 0) return "poweradvantage:crude_oil";
                    if ((x & 15) == 1 && y == 96) return "mineralogy:crude_oil";
                    if ((x & 15) == 2 && y == 48) return "electricadvantage:li_ore";
                    if ((x & 15) == 3 && y == 49) return "electricadvantage:sulfur_ore";
                    if ((x & 15) == 4 && y == 50) return "mineralogy:sulfur_ore";
                    return "minecraft:stone";
                }
            };
        }
    }
}
