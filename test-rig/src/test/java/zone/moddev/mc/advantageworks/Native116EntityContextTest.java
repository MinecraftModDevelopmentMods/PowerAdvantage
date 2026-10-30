package zone.moddev.mc.advantageworks;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.profiler.EmptyProfiler;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class Native116EntityContextTest {
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void observesActualRegistryKeyAndWorldIdentity() throws Exception {
        ServerWorld world = world(World.NETHER);
        BoatEntity boat = new BoatEntity(EntityType.BOAT, world);
        JsonObject actual = Native116EntityContext.observe(boat, world);
        assertEquals("minecraft:the_nether", actual.get("worldDimensionKey").getAsString());
        assertTrue(actual.get("sameWorld").getAsBoolean());
        assertEquals("minecraft:the_nether", actual.getAsJsonObject("entityWorlds").get(boat.getUUID().toString()).getAsString());
    }

    @Test public void observesEndWithoutAnyExpectedFixtureDimension() throws Exception {
        ServerWorld world = world(World.END);
        BoatEntity boat = new BoatEntity(EntityType.BOAT, world);
        assertEquals("minecraft:the_end", Native116EntityContext.observe(boat, world).get("worldDimensionKey").getAsString());
    }

    @Test public void recordsActualPassengerWorldAndUuid() throws Exception {
        ServerWorld world = world(World.NETHER);
        BoatEntity vehicle = new BoatEntity(EntityType.BOAT, world), passenger = new BoatEntity(EntityType.BOAT, world);
        assertTrue(passenger.startRiding(vehicle, true));
        JsonObject actual = Native116EntityContext.observe(vehicle, world).getAsJsonObject("entityWorlds");
        assertEquals(2, actual.entrySet().size());
        assertEquals("minecraft:the_nether", actual.get(passenger.getUUID().toString()).getAsString());
    }

    @Test public void rejectsDifferentWorldInstanceEvenWithSameRegistryKey() throws Exception {
        ServerWorld actual = world(World.NETHER), other = world(World.NETHER);
        reject(new BoatEntity(EntityType.BOAT, actual), other);
    }

    @Test public void rejectsPassengerWithDifferentActualWorld() throws Exception {
        ServerWorld world = world(World.NETHER);
        BoatEntity vehicle = new BoatEntity(EntityType.BOAT, world), passenger = new BoatEntity(EntityType.BOAT, world);
        assertTrue(passenger.startRiding(vehicle, true));
        passenger.level = world(World.END);
        reject(vehicle, world);
    }

    @Test public void rejectsDuplicatePassengerUuid() throws Exception {
        ServerWorld world = world(World.NETHER);
        BoatEntity vehicle = new BoatEntity(EntityType.BOAT, world), passenger = new BoatEntity(EntityType.BOAT, world);
        passenger.setUUID(vehicle.getUUID());
        assertTrue(passenger.startRiding(vehicle, true));
        reject(vehicle, world);
    }

    @Test public void observationDoesNotChangeEntityIdentityOrPassengers() throws Exception {
        ServerWorld world = world(World.NETHER);
        BoatEntity boat = new BoatEntity(EntityType.BOAT, world);
        UUID before = boat.getUUID();
        Native116EntityContext.observe(boat, world);
        assertEquals(before, boat.getUUID());
        assertTrue(boat.getPassengers().isEmpty());
    }

    @Test public void rejectsMissingContext() throws Exception {
        reject(null, world(World.NETHER));
    }

    private static void reject(BoatEntity entity, ServerWorld world) {
        try { Native116EntityContext.observe(entity, world); fail("Invalid native context accepted"); }
        catch (IllegalStateException expected) { }
    }

    private static ServerWorld world(RegistryKey<World> key) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        ServerWorld world = (ServerWorld) ((Unsafe) field.get(null)).allocateInstance(ServerWorld.class);
        set(world, "dimension", key);
        set(world, "profiler", (Supplier<IProfiler>) () -> EmptyProfiler.INSTANCE);
        return world;
    }

    private static void set(ServerWorld world, String name, Object value) throws Exception {
        Field field = World.class.getDeclaredField(name); field.setAccessible(true); field.set(world, value);
    }
}
