package com.mcmoddev.poweradvantage.conduitnetwork;

import static org.junit.Assert.*;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.ITypedConduit;
import cyano.poweradvantage.api.PowerConnectorContext;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.BeforeClass;
import org.junit.Test;
import com.mcmoddev.poweradvantage.math.BlockPos4D;

public class LoadedConduitNetworkTest {
    private static final ConduitType TYPE = new ConduitType("test-chunk-boundary");

    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Test public void anUnloadedOriginIsNotReadOrCached() {
        GuardWorld world = new GuardWorld(false);
        ConduitNetworkManager manager = new ConduitNetworkManager(TYPE);
        manager.revalidate(pos(15), world, TYPE);
        assertEquals(0, world.blockReads);
        assertFalse(manager.isValidatedNetwork(pos(15)));
    }

    @Test public void anIsolatedLoadedOriginIsStillAValidatedNetwork() {
        GuardWorld world = lineWorld();
        ConduitNetworkManager manager = new ConduitNetworkManager(TYPE);
        manager.revalidate(pos(15), world, TYPE);
        assertEquals(Collections.singletonList(pos(15)), manager.getNetwork(pos(15)));
    }

    @Test public void scanningStopsAtTheUnloadedNeighbourChunk() {
        GuardWorld world = lineWorld();
        ConduitNetworkManager manager = new ConduitNetworkManager(TYPE);
        manager.revalidate(pos(15), world, TYPE);
        assertFalse(manager.areInSameNetwork(pos(15), pos(16)));
        assertTrue(world.blockReads > 0);
    }

    @Test public void requestAtAnUnloadedOriginDoesNotReadBlocksOrTiles() {
        GuardWorld world = new GuardWorld(false);
        assertTrue(new ConduitRegistry().getRequestsForPower(world, pos(15).pos, TYPE).isEmpty());
        assertEquals(0, world.blockReads);
        assertEquals(0, world.tileReads);
    }

    @Test public void collectingCachedRequestsNeverReadsAnUnloadedEntry() throws Exception {
        GuardWorld world = lineWorld();
        Method collect = ConduitRegistry.class.getDeclaredMethod("collectRequests", World.class,
                List.class, ConduitType.class, ConduitType.class);
        collect.setAccessible(true);
        Object requests = collect.invoke(new ConduitRegistry(), world,
                Arrays.asList(pos(15), pos(20)), TYPE, TYPE);
        assertTrue(((List<?>) requests).isEmpty());
        assertEquals(1, world.blockReads);
        assertEquals(0, world.tileReads);
    }

    @Test public void nativeUnloadEventDisconnectsCachedLoadedEndpoints() throws Exception {
        GuardWorld world = lineWorld();
        world.loaded.add(new ChunkPos(1, 0));
        world.loaded.add(new ChunkPos(2, 0));
        EventFixture events = new EventFixture();
        ConduitNetworkManager manager = managerFor(events.registry, world);
        manager.revalidate(pos(15), world, TYPE);
        assertTrue(manager.areInSameNetwork(pos(15), pos(33)));
        world.loaded.remove(new ChunkPos(1, 0));
        assertEquals(0, events.registry.unloads);
        events.bus.post(new TestUnload(new Chunk(world, 1, 0)));
        assertEquals("The real Forge dispatcher must deliver exactly one unload callback", 1, events.registry.unloads);
        assertEquals(0, events.registry.loads);
        assertFalse(manager.isValidatedNetwork(pos(15)));
        assertFalse(manager.isValidatedNetwork(pos(33)));
        manager.revalidate(pos(15), world, TYPE);
        assertFalse(manager.areInSameNetwork(pos(15), pos(33)));
    }

    @Test public void pipeOnlyLoadEventReconnectsWithoutReplacingAnyBlock() throws Exception {
        GuardWorld world = lineWorld();
        EventFixture events = new EventFixture();
        ConduitNetworkManager manager = managerFor(events.registry, world);
        manager.revalidate(pos(15), world, TYPE);
        assertFalse(manager.areInSameNetwork(pos(15), pos(33)));
        world.loaded.add(new ChunkPos(1, 0));
        world.loaded.add(new ChunkPos(2, 0));
        assertEquals(0, events.registry.loads);
        events.bus.post(new TestLoad(new Chunk(world, 1, 0)));
        assertEquals("The real Forge dispatcher must deliver exactly one load callback", 1, events.registry.loads);
        assertEquals(0, events.registry.unloads);
        assertFalse(manager.isValidatedNetwork(pos(15)));
        manager.revalidate(pos(15), world, TYPE);
        assertTrue(manager.areInSameNetwork(pos(15), pos(33)));
    }

    @Test public void clientChunkEventsDoNotInvalidateServerNetworks() throws Exception {
        GuardWorld server = lineWorld();
        EventFixture events = new EventFixture();
        ConduitNetworkManager manager = managerFor(events.registry, server);
        manager.createNewNetwork(pos(15));
        events.bus.post(new TestUnload(new Chunk(new GuardWorld(true), 0, 0)));
        assertEquals("Ignoring a client event must not be confused with a missing callback", 1, events.registry.unloads);
        assertTrue(manager.isValidatedNetwork(pos(15)));
    }

    private static BlockPos4D pos(int x) { return new BlockPos4D(0, new BlockPos(x, 80, 8)); }

    private static GuardWorld lineWorld() {
        GuardWorld world = new GuardWorld(false);
        world.loaded.add(new ChunkPos(0, 0));
        Block pipe = new TestConduit();
        for (int x = 15; x <= 33; x++) world.blocks.put(pos(x).pos, pipe.getDefaultState());
        return world;
    }

    private static ConduitNetworkManager managerFor(ConduitRegistry registry, GuardWorld world) throws Exception {
        registry.conduitBlockPlacedEvent(world, 0, pos(15).pos, TYPE);
        Method getManager = ConduitRegistry.class.getDeclaredMethod("getConduitNetworkManager", ConduitType.class);
        getManager.setAccessible(true);
        return (ConduitNetworkManager) getManager.invoke(registry, TYPE);
    }

    // Plain JUnit does not run Forge's event transformer. Supply only its no-arg constructor
    // and listener-list prerequisites; Forge still registers and ASM-dispatches the production methods.
    private static final class EventFixture {
        final EventBus bus = new EventBus();
        final CountingRegistry registry = new CountingRegistry();
        EventFixture() throws Exception {
            Method register = EventBus.class.getDeclaredMethod("register", Class.class, Object.class,
                    Method.class, ModContainer.class);
            register.setAccessible(true);
            register(register, TestLoad.class, "onChunkLoad", ChunkEvent.Load.class);
            register(register, TestUnload.class, "onChunkUnload", ChunkEvent.Unload.class);
        }
        private void register(Method register, Class<?> fixtureEvent, String name, Class<?> nativeEvent) throws Exception {
            Method handler = ConduitRegistry.class.getMethod(name, nativeEvent);
            assertNotNull("The production callback must retain its Forge subscription", handler.getAnnotation(SubscribeEvent.class));
            ModMetadata owner = new ModMetadata();
            owner.modId = "test-network-events";
            owner.name = "Network event unit fixture";
            owner.version = "test";
            register.invoke(bus, fixtureEvent, registry, handler, new DummyModContainer(owner));
            java.lang.reflect.Field listeners = EventBus.class.getDeclaredField("listeners");
            listeners.setAccessible(true);
            Map<?, ?> installed = (Map<?, ?>) listeners.get(bus);
            assertTrue("Forge registration must install the callback", installed.containsKey(registry));
            assertEquals(name.equals("onChunkLoad") ? 1 : 2, ((List<?>) installed.get(registry)).size());
        }
    }

    public static final class CountingRegistry extends ConduitRegistry {
        int loads, unloads;
        @Override public void onChunkLoad(ChunkEvent.Load event) { loads++; super.onChunkLoad(event); }
        @Override public void onChunkUnload(ChunkEvent.Unload event) { unloads++; super.onChunkUnload(event); }
    }

    public static final class TestLoad extends ChunkEvent.Load {
        private static final ListenerList LISTENERS = new ListenerList();
        public TestLoad() { this(new Chunk(new GuardWorld(false), 0, 0)); }
        TestLoad(Chunk chunk) { super(chunk); }
        @Override public ListenerList getListenerList() { return LISTENERS; }
    }

    public static final class TestUnload extends ChunkEvent.Unload {
        private static final ListenerList LISTENERS = new ListenerList();
        public TestUnload() { this(new Chunk(new GuardWorld(false), 0, 0)); }
        TestUnload(Chunk chunk) { super(chunk); }
        @Override public ListenerList getListenerList() { return LISTENERS; }
    }

    private static final class TestConduit extends Block implements ITypedConduit {
        TestConduit() { super(Material.ROCK); }
        @Override public boolean canAcceptConnection(PowerConnectorContext context) { return true; }
        @Override public ConduitType[] getTypes() { return new ConduitType[]{TYPE}; }
        @Override public boolean isPowerSink(ConduitType type) { return false; }
        @Override public boolean isPowerSource(ConduitType type) { return false; }
    }

    // No native provider exists here: any unloaded read fails instead of silently loading a chunk.
    private static final class GuardWorld extends World {
        final Set<ChunkPos> loaded = new HashSet<>();
        final Map<BlockPos, IBlockState> blocks = new HashMap<>();
        int blockReads, tileReads;
        GuardWorld(boolean remote) { super(null, null, new WorldProviderSurface(), new Profiler(), remote); }
        @Override protected IChunkProvider createChunkProvider() { throw new AssertionError("Unexpected provider creation"); }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return loaded.contains(new ChunkPos(x, z));
        }
        @Override public IBlockState getBlockState(BlockPos pos) {
            assertTrue("Unloaded block read at " + pos, isBlockLoaded(pos));
            blockReads++;
            return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState());
        }
        @Override public TileEntity getTileEntity(BlockPos pos) {
            assertTrue("Unloaded tile read at " + pos, isBlockLoaded(pos));
            tileReads++;
            return null;
        }
    }
}
