package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.init.Fluids;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldFluidPickupTest {
    @BeforeClass public static void bootstrap() { zone.moddev.mc.poweradvantage.support.PowerNativeTestBootstrap.register(); }

    @Test public void waterloggedHostSurvivesTheNativePickup() {
        AtomicReference<IBlockState> state = new AtomicReference<>(Blocks.OAK_SLAB.getDefaultState()
                .with(BlockStateProperties.WATERLOGGED, true));
        assertSame(Fluids.WATER, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ORIGIN, state.get()));
        assertSame(Blocks.OAK_SLAB, state.get().getBlock());
        assertFalse(state.get().get(BlockStateProperties.WATERLOGGED));
    }

    @Test public void ordinaryWaterSourceBecomesAir() {
        AtomicReference<IBlockState> state = new AtomicReference<>(Blocks.WATER.getDefaultState());
        assertSame(Fluids.WATER, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ORIGIN, state.get()));
        assertTrue(state.get().isAir());
    }

    @Test public void flowingWaterDoesNotProduceABucket() {
        AtomicReference<IBlockState> state = new AtomicReference<>(Blocks.WATER.getDefaultState().with(BlockFlowingFluid.LEVEL, 3));
        IBlockState original = state.get();
        assertSame(Fluids.EMPTY, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ORIGIN, state.get()));
        assertSame(original, state.get());
    }

    @Test public void nonPickupBlockIsNeverRemoved() {
        AtomicReference<IBlockState> state = new AtomicReference<>(Blocks.BEDROCK.getDefaultState());
        assertSame(Fluids.EMPTY, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ORIGIN, state.get()));
        assertSame(Blocks.BEDROCK, state.get().getBlock());
    }

    private static IWorld world(AtomicReference<IBlockState> state) {
        return (IWorld)Proxy.newProxyInstance(IWorld.class.getClassLoader(), new Class<?>[]{IWorld.class}, (proxy, method, args) -> {
            if (method.getName().equals("setBlockState")) { state.set((IBlockState)args[1]); return true; }
            if (method.getName().equals("getBlockState")) return state.get();
            throw new AssertionError("Unexpected world operation " + method.getName());
        });
    }
}
