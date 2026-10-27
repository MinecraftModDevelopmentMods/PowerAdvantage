package zone.moddev.mc.advantageworks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.IEntityReader;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeHarvestIsolationTest {
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(0, 0, 0, 1, 1, 1).grow(2);

    @BeforeClass public static void initializeNativeRegistries() { Bootstrap.register(); }

    private static ItemEntity item(Item type, int count) {
        return new ItemEntity(null, 0.5, 0.5, 0.5, new ItemStack(type, count));
    }

    @Test public void nativeDefaultQueryReproducesFourThenSixThenEightBeforeTickCleanup() {
        QueryReader reader = new QueryReader();
        ItemEntity first = item(Items.GUNPOWDER, 4);
        reader.index.add(first);
        assertEquals(4, count(reader.getEntitiesWithinAABB(ItemEntity.class, BOUNDS)));
        reader.getEntitiesWithinAABB(ItemEntity.class, BOUNDS).forEach(ItemEntity::remove);
        assertTrue(first.removed);
        assertTrue(EntityPredicates.NOT_SPECTATING.test(first));
        assertSame(first, reader.getEntitiesWithinAABB(ItemEntity.class, BOUNDS).get(0));
        reader.index.add(item(Items.GUNPOWDER, 2));
        assertEquals(6, count(reader.getEntitiesWithinAABB(ItemEntity.class, BOUNDS)));
        reader.getEntitiesWithinAABB(ItemEntity.class, BOUNDS).forEach(ItemEntity::remove);
        reader.index.add(item(Items.GUNPOWDER, 2));
        assertEquals(8, count(reader.getEntitiesWithinAABB(ItemEntity.class, BOUNDS)));
    }

    @Test public void removedEarlierDropCannotInflateTheNextTrial() {
        QueryReader reader = new QueryReader();
        reader.index.add(item(Items.GUNPOWDER, 4));
        NativeHarvestProbe.harvestDrops(reader, BOUNDS).forEach(ItemEntity::remove);
        assertTrue(NativeHarvestProbe.harvestDrops(reader, BOUNDS).isEmpty());
        reader.index.add(item(Items.GUNPOWDER, 2));
        assertEquals(2, count(NativeHarvestProbe.harvestDrops(reader, BOUNDS)));
    }

    @Test public void removedSulfurLikeDropCannotContaminateSilkOrWrongToolCases() {
        QueryReader reader = new QueryReader();
        ItemEntity powder = item(Items.GUNPOWDER, 4);
        reader.index.add(powder);
        powder.remove();
        assertTrue(NativeHarvestProbe.harvestDrops(reader, BOUNDS).isEmpty());
        ItemEntity ore = item(Items.IRON_ORE, 1);
        reader.index.add(ore);
        assertEquals(Collections.singletonList(ore), NativeHarvestProbe.harvestDrops(reader, BOUNDS));
    }

    @Test public void everyUnexpectedLiveIdentityAndQuantityRemainsVisibleToTheOracle() {
        QueryReader reader = new QueryReader();
        ItemEntity expected = item(Items.IRON_ORE, 1), unexpected = item(Items.GUNPOWDER, 5);
        reader.index.add(expected);
        reader.index.add(unexpected);
        assertEquals(reader.index, NativeHarvestProbe.harvestDrops(reader, BOUNDS));
        assertEquals(6, count(NativeHarvestProbe.harvestDrops(reader, BOUNDS)));
    }

    @Test public void fixtureQueryKeepsTheOriginalSpatialBoundary() {
        QueryReader reader = new QueryReader();
        ItemEntity outside = item(Items.GUNPOWDER, 4);
        outside.setPosition(32, 0.5, 0.5);
        reader.index.add(outside);
        assertTrue(NativeHarvestProbe.harvestDrops(reader, BOUNDS).isEmpty());
    }

    private static int count(List<ItemEntity> entities) {
        return entities.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /** Models only Chunk's index and predicate dispatch; native entities/default queries are real. */
    private static final class QueryReader implements IEntityReader {
        final List<ItemEntity> index = new ArrayList<>();

        @Override public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type,
                AxisAlignedBB bounds, Predicate<? super T> filter) {
            List<T> found = new ArrayList<>();
            for (ItemEntity entity : index) if (type.isInstance(entity) && entity.getBoundingBox().intersects(bounds)) {
                T value = type.cast(entity);
                if (filter == null || filter.test(value)) found.add(value);
            }
            return found;
        }

        @Override public List<Entity> getEntitiesInAABBexcluding(Entity except, AxisAlignedBB bounds, Predicate<? super Entity> filter) {
            throw new UnsupportedOperationException("Not used by harvest queries");
        }

        @Override public List<? extends PlayerEntity> getPlayers() { return Collections.emptyList(); }
    }
}
