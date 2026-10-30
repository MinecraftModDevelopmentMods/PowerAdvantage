package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldFluidPickupTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.register(); }

    @Test public void waterloggedHostSurvivesTheNativePickup() {
        AtomicReference<BlockState> state = new AtomicReference<>(Blocks.OAK_SLAB.getDefaultState()
                .with(BlockStateProperties.WATERLOGGED, true));
        assertSame(Fluids.WATER, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ZERO, state.get()));
        assertSame(Blocks.OAK_SLAB, state.get().getBlock());
        assertFalse(state.get().get(BlockStateProperties.WATERLOGGED));
    }

    @Test public void ordinaryWaterSourceBecomesAir() {
        AtomicReference<BlockState> state = new AtomicReference<>(Blocks.WATER.getDefaultState());
        assertSame(Fluids.WATER, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ZERO, state.get()));
        assertTrue(state.get().isAir());
    }

    @Test public void flowingWaterDoesNotProduceABucket() {
        AtomicReference<BlockState> state = new AtomicReference<>(Blocks.WATER.getDefaultState().with(FlowingFluidBlock.LEVEL, 3));
        BlockState original = state.get();
        assertSame(Fluids.EMPTY, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ZERO, state.get()));
        assertSame(original, state.get());
    }

    @Test public void nonPickupBlockIsNeverRemoved() {
        AtomicReference<BlockState> state = new AtomicReference<>(Blocks.BEDROCK.getDefaultState());
        assertSame(Fluids.EMPTY, FluidDrainTileEntity.pickupSource(world(state), BlockPos.ZERO, state.get()));
        assertSame(Blocks.BEDROCK, state.get().getBlock());
    }

    private static IWorld world(AtomicReference<BlockState> state) {
        return (IWorld)Proxy.newProxyInstance(IWorld.class.getClassLoader(), new Class<?>[]{IWorld.class}, (proxy, method, args) -> {
            if (method.getName().equals("setBlockState")) { state.set((BlockState)args[1]); return true; }
            if (method.getName().equals("getBlockState")) return state.get();
            throw new AssertionError("Unexpected world operation " + method.getName());
        });
    }
}
