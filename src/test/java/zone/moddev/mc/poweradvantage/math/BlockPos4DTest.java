package zone.moddev.mc.poweradvantage.math;

import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlockPos4DTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.bootStrap(); }

    @Test public void customDimensionKeysNeverShareCoordinates() {
        RegistryKey<World> first = RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("test:first"));
        RegistryKey<World> second = RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("test:second"));
        BlockPos4D pos = new BlockPos4D(first, new BlockPos(-17, 80, 32));
        assertNotEquals(pos, new BlockPos4D(second, pos.pos));
        assertEquals(pos, BlockPos4D.fromNBT(pos.toNBT()));
        assertFalse(pos.equals(null));
    }

    @Test public void cacheKeysCopyMutableInputPositions() {
        BlockPos.Mutable source = new BlockPos.Mutable(1, 2, 3);
        BlockPos4D key = new BlockPos4D(World.OVERWORLD, source);
        source.set(4, 5, 6);
        assertEquals(new BlockPos(1, 2, 3), key.pos);
    }

    @Test public void legacyVanillaCoordinatesRetainTheirDimension() {
        assertEquals(new BlockPos4D(World.NETHER, new BlockPos(1, 2, 3)),
                BlockPos4D.fromNBT(new IntArrayNBT(new int[] {-1, 1, 2, 3})));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownNumericDimensionsAreNotInvented() { BlockPos4D.legacyDimension(7); }
}
