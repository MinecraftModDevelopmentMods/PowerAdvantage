package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class ColdInspectionPlanTest {
    @Test public void requestsBothBlockAndCrossedActorChunksBeforeObservation() {
        JsonObject fixture = fixture(0);
        position(fixture, 50016, 88, 50016);
        actor(fixture, at(50015.5, 88.5, 50016.5));
        String untouched = fixture.toString();
        MachineOperationalProbe.InspectionPlan plan = new MachineOperationalProbe.InspectionPlan(fixture, 100);
        assertEquals(2, plan.chunks.size());
        assertTrue(plan.chunks.contains(new ChunkPos(3126, 3126)));
        assertTrue(plan.chunks.contains(new ChunkPos(3125, 3126)));
        assertEquals(untouched, fixture.toString());
        assertEquals(1, plan.actorIds.size());
    }

    @Test public void actorOnlyFixtureAndPositionOutsideBayStillRequestTheirActualChunk() {
        JsonObject fixture = fixture(0);
        actor(fixture, at(49999.75, 0, 50000));
        MachineOperationalProbe.InspectionPlan plan = new MachineOperationalProbe.InspectionPlan(fixture, 100);
        assertEquals(1, plan.chunks.size());
        assertTrue(plan.chunks.contains(new ChunkPos(3124, 3125)));
        assertTrue(plan.positions.isEmpty());
    }

    @Test public void identicalActorAndBlockChunksAreDeduplicated() {
        JsonObject fixture = fixture(0);
        position(fixture, 50016, 88, 50016);
        position(fixture, 50017, 89, 50017);
        actor(fixture, at(50016.5, 88.5, 50016.5));
        assertEquals(1, new MachineOperationalProbe.InspectionPlan(fixture, 100).chunks.size());
    }

    @Test public void new159CaseIndexAndMaximumBoundUseTheirOwnRecordedOrigin() {
        for (int index : new int[]{158, 780}) {
            int x = 50000 + index * 64;
            JsonObject fixture = fixture(index);
            position(fixture, x + 31, 111, 50031);
            actor(fixture, at(x + 16.5, 88, 50016.5));
            MachineOperationalProbe.InspectionPlan plan = new MachineOperationalProbe.InspectionPlan(fixture, 100);
            assertEquals(index, plan.index);
            assertTrue(plan.positions.contains(new BlockPos(x + 31, 111, 50031)));
        }
    }

    @Test public void invalidFractionalNonfiniteOrMissingIndexesAreRejected() {
        for (double index : new double[]{-1, 781, 1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            JsonObject fixture = fixture(0);
            fixture.addProperty("index", index);
            assertThrows(IllegalArgumentException.class, () -> new MachineOperationalProbe.InspectionPlan(fixture, 100));
        }
        JsonObject missing = fixture(0);
        missing.remove("index");
        assertThrows(IllegalArgumentException.class, () -> new MachineOperationalProbe.InspectionPlan(missing, 100));
    }

    @Test public void blockPositionsCannotEscapeTheirOwnBayOrBecomeFractional() {
        double[][] invalid = {{49999, 88, 50016}, {50032, 88, 50016}, {50016, 78, 50016},
                {50016, 112, 50016}, {50016, 88, 49999}, {50016, 88, 50032},
                {50016.25, 88, 50016}, {Double.NaN, 88, 50016}, {Double.POSITIVE_INFINITY, 88, 50016}};
        for (double[] value : invalid) {
            JsonObject fixture = fixture(0);
            JsonObject point = new JsonObject();
            point.addProperty("x", value[0]); point.addProperty("y", value[1]); point.addProperty("z", value[2]);
            fixture.getAsJsonArray("positions").add(point);
            assertThrows(IllegalArgumentException.class, () -> new MachineOperationalProbe.InspectionPlan(fixture, 100));
        }
    }

    @Test public void missingAuthoritativeArraysAreNotTreatedAsEmptyEvidence() {
        for (String key : new String[]{"positions", "entities"}) {
            JsonObject fixture = fixture(0);
            fixture.remove(key);
            assertThrows(IllegalArgumentException.class, () -> new MachineOperationalProbe.InspectionPlan(fixture, 100));
        }
    }

    @Test public void actorBoundsAllowNativeWorldHeightAnd64BlockHorizontalBoundary() {
        assertEquals(new BlockPos(50064, 0, 50000),
                MachineOperationalProbe.InspectionPlan.savedActorPosition(0, at(50064, 0, 50000)));
        assertEquals(new BlockPos(49936, 255, 50000),
                MachineOperationalProbe.InspectionPlan.savedActorPosition(0, at(49936, 255, 50000)));
    }

    @Test public void actorCoordinatesMustBeFiniteAndStayWithinWorldHeightAndRadius() {
        double[][] invalid = {{50064.01, 80, 50000}, {50050, 80, 50050}, {50000, -0.1, 50000},
                {50000, 255.1, 50000}, {Double.NaN, 80, 50000}, {50000, Double.NaN, 50000},
                {50000, 80, Double.NaN}, {Double.NEGATIVE_INFINITY, 80, 50000},
                {50000, Double.POSITIVE_INFINITY, 50000}, {50000, 80, Double.POSITIVE_INFINITY}};
        for (double[] value : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> MachineOperationalProbe.InspectionPlan.savedActorPosition(0, at(value[0], value[1], value[2])));
        }
    }

    @Test public void actorNeedsExactlyThreeNativeDoublesNotAStringOrFloatList() {
        assertThrows(IllegalArgumentException.class,
                () -> MachineOperationalProbe.InspectionPlan.savedActorPosition(0, new NBTTagCompound()));
        NBTTagCompound string = new NBTTagCompound();
        string.setString("Pos", "[50016,88,50016]");
        assertThrows(IllegalArgumentException.class, () -> MachineOperationalProbe.InspectionPlan.savedActorPosition(0, string));
        NBTTagCompound shortList = new NBTTagCompound();
        NBTTagList two = new NBTTagList();
        two.appendTag(new NBTTagDouble(50016)); two.appendTag(new NBTTagDouble(88));
        shortList.setTag("Pos", two);
        assertThrows(IllegalArgumentException.class, () -> MachineOperationalProbe.InspectionPlan.savedActorPosition(0, shortList));
        NBTTagCompound floats = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagFloat(50016)); list.appendTag(new NBTTagFloat(88)); list.appendTag(new NBTTagFloat(50016));
        floats.setTag("Pos", list);
        assertThrows(IllegalArgumentException.class, () -> MachineOperationalProbe.InspectionPlan.savedActorPosition(0, floats));
    }

    @Test public void duplicateActorIdentitiesAreRejectedNotCollapsed() {
        JsonObject fixture = fixture(0);
        actor(fixture, at(50016.5, 88, 50016.5));
        fixture.getAsJsonArray("entities").add(fixture.getAsJsonArray("entities").get(0));
        assertThrows(IllegalArgumentException.class, () -> new MachineOperationalProbe.InspectionPlan(fixture, 100));
    }

    @Test public void requestTickNeverObservesReadinessEvenWhenNativeLoadIsImmediate() {
        MachineOperationalProbe.InspectionPlan plan = plan();
        assertFalse(plan.ready(100, chunk -> { throw new AssertionError("Readiness was inspected on the request tick"); }));
        assertTrue(plan.ready(101, chunk -> true));
    }

    @Test public void allChunksMustBeLoadedAndEveryRequestedChunkIsChecked() {
        MachineOperationalProbe.InspectionPlan plan = plan();
        AtomicInteger calls = new AtomicInteger();
        assertFalse(plan.ready(101, chunk -> calls.incrementAndGet() != 1));
        assertEquals(plan.chunks.size(), calls.get());
        assertTrue(plan.ready(102, chunk -> true));
    }

    @Test public void readinessMayWaitButNeverBeyond80Ticks() {
        MachineOperationalProbe.InspectionPlan plan = plan();
        assertFalse(plan.ready(179, chunk -> false));
        assertTrue(plan.ready(180, chunk -> true));
        assertThrows(IllegalStateException.class, () -> plan.ready(180, chunk -> false));
        assertThrows(IllegalStateException.class, () -> plan.ready(181, chunk -> true));
    }

    @Test public void emptyFixtureAlsoWaitsUntilALaterTick() {
        MachineOperationalProbe.InspectionPlan plan = new MachineOperationalProbe.InspectionPlan(fixture(0), 100);
        assertFalse(plan.ready(100, chunk -> true));
        assertTrue(plan.ready(101, chunk -> true));
    }

    @Test public void malformedActorEncodingIsNotTreatedAsAnAbsentActor() {
        JsonObject fixture = fixture(0);
        actor(fixture, at(50016, 88, 50016));
        fixture.getAsJsonArray("entities").get(0).getAsJsonObject().addProperty("nbt", "not native actor data");
        assertThrows(IllegalArgumentException.class, () -> new MachineOperationalProbe.InspectionPlan(fixture, 100));
    }

    private static MachineOperationalProbe.InspectionPlan plan() {
        JsonObject fixture = fixture(0);
        position(fixture, 50016, 88, 50016);
        actor(fixture, at(50015.5, 88.5, 50016.5));
        return new MachineOperationalProbe.InspectionPlan(fixture, 100);
    }

    private static JsonObject fixture(int index) {
        JsonObject fixture = new JsonObject();
        fixture.addProperty("id", "steam/cold-inspector-fixture");
        fixture.addProperty("index", index);
        fixture.add("positions", new JsonArray());
        fixture.add("entities", new JsonArray());
        return fixture;
    }

    private static void position(JsonObject fixture, int x, int y, int z) {
        JsonObject point = new JsonObject();
        point.addProperty("x", x); point.addProperty("y", y); point.addProperty("z", z);
        fixture.getAsJsonArray("positions").add(point);
    }

    private static void actor(JsonObject fixture, NBTTagCompound tag) {
        JsonObject actor = new JsonObject();
        actor.addProperty("uuid", new UUID(0, fixture.getAsJsonArray("entities").size() + 1).toString());
        actor.addProperty("nbt", tag.toString());
        fixture.getAsJsonArray("entities").add(actor);
    }

    private static NBTTagCompound at(double x, double y, double z) {
        NBTTagList pos = new NBTTagList();
        pos.appendTag(new NBTTagDouble(x)); pos.appendTag(new NBTTagDouble(y)); pos.appendTag(new NBTTagDouble(z));
        NBTTagCompound result = new NBTTagCompound();
        result.setTag("Pos", pos);
        return result;
    }
}
