package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.fluid.FluidTankInfo;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import cyano.poweradvantage.api.simple.TileEntitySimpleFluidMachine;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.block.BlockState;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.world.IWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.init.Entities;
import zone.moddev.mc.poweradvantage.util.FluidHandlerHelper;

/** Pulls source blocks or adjacent Advantage tanks into the pipe network. */
public class FluidDrainTileEntity extends TileEntitySimpleFluidMachine {
    public FluidDrainTileEntity() {
        super(Entities.FLUID_DRAIN, FluidTank.BUCKET_VOLUME, "tile.poweradvantage.fluid_drain.name");
    }

    @Override
    public void powerUpdate() {
        if (getTank().getFluidAmount() > 0) tryPushFluid(getPos().down(), Direction.UP);

        for (Direction side : Direction.values()) {
            BlockPos adjacent = getPos().offset(side);
            IAdvantageFluidHandler other = FluidHandlerHelper.getHandler(
                    getWorld().getTileEntity(adjacent), side.getOpposite());
            if (other == null) continue;
            for (FluidTankInfo info : other.getTankInfo(side.getOpposite())) {
                FluidStack available = info.fluid;
                if (available == null || (getTank().getFluid() != null
                        && !getTank().getFluid().getFluid().isEquivalentTo(available.getFluid()))) continue;
                FluidStack drained = other.drain(side.getOpposite(), getTank().getCapacity() - getTank().getFluidAmount(), false);
                if (drained == null) continue;
                int accepted = getTank().fill(drained, false);
                if (accepted > 0) {
                    FluidStack moved = other.drain(side.getOpposite(), accepted, true);
                    if (moved != null) getTank().fill(moved, true);
                    super.powerUpdate();
                    return;
                }
            }
        }

        if (getTank().getFluidAmount() == 0) {
            BlockPos source = scanFluidSpaceForSourceBlock(getWorld(), getPos().up(), 32);
            if (source != null) {
                Fluid fluid = pickupSource(getWorld(), source, getWorld().getBlockState(source));
                if (fluid != net.minecraft.fluid.Fluids.EMPTY)
                    getTank().fill(new FluidStack(fluid, FluidTank.BUCKET_VOLUME), true);
            }
        }
        super.powerUpdate();
    }

    static Fluid pickupSource(IWorld world, BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof IBucketPickupHandler)) return net.minecraft.fluid.Fluids.EMPTY;
        Fluid picked = ((IBucketPickupHandler)state.getBlock()).pickupFluid(world, position, state);
        return picked == null ? net.minecraft.fluid.Fluids.EMPTY : picked;
    }

    private void tryPushFluid(BlockPos position, Direction face) {
        IAdvantageFluidHandler handler = FluidHandlerHelper.getHandler(getWorld().getTileEntity(position), face);
        if (handler == null || getTank().getFluid() == null) return;
        int accepted = handler.fill(face, getTank().getFluid(), true);
        if (accepted > 0) {
            getTank().drain(accepted, true);
            sync();
        }
    }

    public FluidStack getFluid() { return getTank().getFluid(); }
    public int getFluidCapacity() { return getTank().getCapacity(); }
    public int getRedstoneOutput() { return getTank().getFluidAmount() * 15 / getTank().getCapacity(); }
    @Override public boolean canAccept(Fluid fluid) { return true; }
    @Override protected ItemStack[] getInventory() { return new ItemStack[0]; }
    @Override public void tickUpdate(boolean server) {}
    @Override public boolean isPowerSink(ConduitType type) { return false; }
    @Override public boolean isPowerSource(ConduitType type) { return true; }
    @Override public boolean isFluidSource() { return true; }
    @Override public boolean isFluidSink() { return false; }

    public static BlockPos scanFluidSpaceForSourceBlock(World world, BlockPos initial, int range) {
        BlockPos position = initial;
        Direction[] directions = { Direction.UP, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST };
        while (range-- > 0) {
            IFluidState state = world.getFluidState(position);
            if (!state.isEmpty() && state.isSource()
                    && world.getBlockState(position).getBlock() instanceof IBucketPickupHandler) return position;
            java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
            for (Direction direction : directions) {
                BlockPos candidate = position.offset(direction);
                IFluidState candidateState = world.getFluidState(candidate);
                if (!candidateState.isEmpty() && (state.isEmpty()
                        || candidateState.getFluid().isEquivalentTo(state.getFluid()))) candidates.add(candidate);
            }
            if (candidates.isEmpty()) return null;
            position = candidates.get(candidates.size() == 1 ? 0 : world.rand.nextInt(candidates.size()));
        }
        return null;
    }

    public static boolean isFluidBlock(net.minecraft.block.Block block, Fluid fluid) {
        return block.getDefaultState().getFluidState().getFluid().isEquivalentTo(fluid);
    }

    public static boolean isFluidSourceBlock(net.minecraft.block.Block block, Fluid fluid, World world, BlockPos pos) {
        IFluidState state = world.getFluidState(pos);
        return !state.isEmpty() && state.isSource() && state.getFluid().isEquivalentTo(fluid);
    }
}
