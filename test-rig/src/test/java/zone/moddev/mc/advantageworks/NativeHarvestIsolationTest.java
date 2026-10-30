package zone.moddev.mc.advantageworks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.init.Bootstrap;
import net.minecraft.world.WorldServer;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeHarvestIsolationTest {
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(0, 0, 0, 1, 1, 1).grow(2);

    @BeforeClass public static void initializeNativeRegistries() throws Exception {
        java.util.Properties platform = new java.util.Properties();
        try (java.io.InputStream input = java.nio.file.Files.newInputStream(java.nio.file.Paths.get("gradle.properties"))) {
            platform.load(input);
        }
        Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
        for (String[] mapping : new String[][]{{"mcVersion", "minecraft_version"}, {"forgeVersion", "forge_version"}}) {
            String expected = platform.getProperty(mapping[1]);
            assertNotNull("Missing configured native unit-test version", expected);
            java.lang.reflect.Field field = loader.getDeclaredField(mapping[0]);
            field.setAccessible(true);
            if (field.get(null) == null) field.set(null, expected);
            else assertEquals("Conflicting native unit-test version", expected, field.get(null));
        }
        // Match Forge's instance-call metadata path without ModLauncher's injected constructor.
        Class<?> entityEvent = Class.forName("net.minecraftforge.event.entity.EntityEvent");
        java.lang.reflect.Method listeners = Class.forName("net.minecraftforge.eventbus.api.EventListenerHelper")
                .getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
        listeners.setAccessible(true);
        Object nativeListeners = listeners.invoke(null, entityEvent, true);
        assertNotNull("Native EntityEvent listener metadata was not established", nativeListeners);
        assertSame("Real event instances must use the native listener metadata", nativeListeners,
                new net.minecraftforge.event.entity.EntityEvent(null).getListenerList());
        Bootstrap.register();
    }

    private static EntityItem item(Item type, int count) {
        return new EntityItem(null, 0.5, 0.5, 0.5, new ItemStack(type, count));
    }

    @Test public void nativeDefaultQueryReproducesFourThenSixThenEightBeforeTickCleanup() {
        QueryReader reader = QueryReader.create();
        EntityItem first = item(Items.GUNPOWDER, 4);
        reader.index.add(first);
        assertEquals(4, count(reader.getEntitiesWithinAABB(EntityItem.class, BOUNDS)));
        reader.getEntitiesWithinAABB(EntityItem.class, BOUNDS).forEach(EntityItem::remove);
        assertTrue(first.removed);
        assertTrue(EntitySelectors.NOT_SPECTATING.test(first));
        assertSame(first, reader.getEntitiesWithinAABB(EntityItem.class, BOUNDS).get(0));
        reader.index.add(item(Items.GUNPOWDER, 2));
        assertEquals(6, count(reader.getEntitiesWithinAABB(EntityItem.class, BOUNDS)));
        reader.getEntitiesWithinAABB(EntityItem.class, BOUNDS).forEach(EntityItem::remove);
        reader.index.add(item(Items.GUNPOWDER, 2));
        assertEquals(8, count(reader.getEntitiesWithinAABB(EntityItem.class, BOUNDS)));
    }

    @Test public void removedEarlierDropCannotInflateTheNextTrial() {
        QueryReader reader = QueryReader.create();
        reader.index.add(item(Items.GUNPOWDER, 4));
        NativeHarvestProbe.harvestDrops(reader, BOUNDS).forEach(EntityItem::remove);
        assertTrue(NativeHarvestProbe.harvestDrops(reader, BOUNDS).isEmpty());
        reader.index.add(item(Items.GUNPOWDER, 2));
        assertEquals(2, count(NativeHarvestProbe.harvestDrops(reader, BOUNDS)));
    }

    @Test public void removedSulfurLikeDropCannotContaminateSilkOrWrongToolCases() {
        QueryReader reader = QueryReader.create();
        EntityItem powder = item(Items.GUNPOWDER, 4);
        reader.index.add(powder);
        powder.remove();
        assertTrue(NativeHarvestProbe.harvestDrops(reader, BOUNDS).isEmpty());
        EntityItem ore = item(Item.getItemFromBlock(net.minecraft.init.Blocks.IRON_ORE), 1);
        reader.index.add(ore);
        assertEquals(Collections.singletonList(ore), NativeHarvestProbe.harvestDrops(reader, BOUNDS));
    }

    @Test public void everyUnexpectedLiveIdentityAndQuantityRemainsVisibleToTheOracle() {
        QueryReader reader = QueryReader.create();
        EntityItem expected = item(Item.getItemFromBlock(net.minecraft.init.Blocks.IRON_ORE), 1), unexpected = item(Items.GUNPOWDER, 5);
        reader.index.add(expected);
        reader.index.add(unexpected);
        assertEquals(reader.index, NativeHarvestProbe.harvestDrops(reader, BOUNDS));
        assertEquals(6, count(NativeHarvestProbe.harvestDrops(reader, BOUNDS)));
    }

    @Test public void fixtureQueryKeepsTheOriginalSpatialBoundary() {
        QueryReader reader = QueryReader.create();
        EntityItem outside = item(Items.GUNPOWDER, 4);
        outside.setPosition(32, 0.5, 0.5);
        reader.index.add(outside);
        assertTrue(NativeHarvestProbe.harvestDrops(reader, BOUNDS).isEmpty());
    }

    private static int count(List<EntityItem> entities) {
        return entities.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /** Models only Chunk's index and predicate dispatch; native entities/default queries are real. */
    private static final class QueryReader extends WorldServer {
        List<EntityItem> index;

        private QueryReader() {
            super(null, null, null, null, net.minecraft.world.dimension.DimensionType.OVERWORLD,
                    new net.minecraft.profiler.Profiler());
            throw new AssertionError("Allocate the indexed native world without a server constructor");
        }

        static QueryReader create() {
            try {
                Class<?> allocator = Class.forName("sun.misc.Unsafe");
                java.lang.reflect.Field singleton = allocator.getDeclaredField("theUnsafe");
                singleton.setAccessible(true);
                QueryReader reader = (QueryReader) allocator.getMethod("allocateInstance", Class.class)
                        .invoke(singleton.get(null), QueryReader.class);
                reader.index = new ArrayList<>();
                return reader;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot allocate the native World query fixture", failure);
            }
        }

        @Override public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type,
                AxisAlignedBB bounds, Predicate<? super T> filter) {
            List<T> found = new ArrayList<>();
            for (EntityItem entity : index) if (type.isInstance(entity) && entity.getBoundingBox().intersects(bounds)) {
                T value = type.cast(entity);
                if (filter == null || filter.test(value)) found.add(value);
            }
            return found;
        }

        @Override public List<Entity> getEntitiesInAABBexcluding(Entity except, AxisAlignedBB bounds, Predicate<? super Entity> filter) {
            throw new UnsupportedOperationException("Not used by harvest queries");
        }

    }
}
