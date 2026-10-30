package zone.moddev.mc.advantageworks;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;

final class NativeWorldFluids {
    static boolean isSource(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockLiquid) return state.getValue(BlockLiquid.LEVEL) == 0;
        if (state.getBlock() instanceof IFluidBlock) return ((IFluidBlock)state.getBlock()).canDrain(world, pos);
        return false;
    }
    static boolean isEmpty(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return !state.getMaterial().isLiquid() && !(state.getBlock() instanceof IFluidBlock);
    }
    static Vec3d flow(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().modifyAcceleration(world, pos, null, Vec3d.ZERO);
    }
}
