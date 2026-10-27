package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import cyano.poweradvantage.api.simple.TileEntitySimpleFluidMachine;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.init.Entities;
import zone.moddev.mc.poweradvantage.util.FluidHandlerHelper;

/** Places stored fluid into the world or into a tank below. */
public class FluidDischargeTileEntity extends TileEntitySimpleFluidMachine {
    private int oldLevel = -1;

    public FluidDischargeTileEntity() {
        super(Entities.FLUID_DISCHARGE, FluidTank.BUCKET_VOLUME, "tile.poweradvantage.fluid_discharge.name");
    }

    @Override
    public void powerUpdate() {
        BlockPos below = getBlockPos().below();
        IAdvantageFluidHandler other = FluidHandlerHelper.getHandler(getLevel().getBlockEntity(below), Direction.UP);
        if (getTank().getFluidAmount() > 0 && other != null) {
            int accepted = other.fill(Direction.UP, getTank().getFluid(), true);
            if (accepted > 0) getTank().drain(accepted, true);
        } else if (getTank().getFluidAmount() >= FluidTank.BUCKET_VOLUME) {
            Fluid fluid = getTank().getFluid().getFluid();
            BlockPos target = findPlacement(getLevel(), below, fluid, 32);
            if (target != null) {
                getLevel().setBlock(target, fluid.defaultFluidState().createLegacyBlock(), 3);
                getTank().drain(FluidTank.BUCKET_VOLUME, true);
            }
        }
        if (oldLevel != getTank().getFluidAmount()) {
            oldLevel = getTank().getFluidAmount();
            sync();
        }
        super.powerUpdate();
    }

    private static BlockPos findPlacement(World world, BlockPos initial, Fluid fluid, int range) {
        if (world.isEmptyBlock(initial)) return initial;
        BlockPos position = initial;
        Direction[] directions = { Direction.DOWN, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST };
        while (range-- > 0) {
            FluidState state = world.getFluidState(position);
            if (state.isEmpty()) return position;
            java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
            for (Direction direction : directions) {
                BlockPos candidate = position.relative(direction);
                FluidState candidateState = world.getFluidState(candidate);
                if (world.isEmptyBlock(candidate) || (!candidateState.isEmpty()
                        && candidateState.getType().isSame(fluid) && !candidateState.isSource())) {
                    candidates.add(candidate);
                }
            }
            if (candidates.isEmpty()) return null;
            position = candidates.get(candidates.size() == 1 ? 0 : world.random.nextInt(candidates.size()));
        }
        return null;
    }

    @Override public void tickUpdate(boolean server) {}
    public FluidStack getFluid() { return getTank().getFluid(); }
    public int getFluidCapacity() { return getTank().getCapacity(); }
    @Override public boolean canAccept(Fluid fluid) { return true; }
    @Override protected ItemStack[] getInventory() { return new ItemStack[0]; }
    public int getRedstoneOutput() { return getTank().getFluidAmount() * 15 / getTank().getCapacity(); }
    @Override public boolean isPowerSink(ConduitType type) { return true; }
    @Override public boolean isPowerSource(ConduitType type) { return false; }
    @Override public boolean isFluidSource() { return false; }
    @Override public boolean isFluidSink() { return true; }

    @Override public CompoundNBT getUpdateTag() {
        CompoundNBT tag = new CompoundNBT();
        getTank().writeToNBT(tag);
        return tag;
    }
    @Override public void handleUpdateTag(net.minecraft.block.BlockState state, CompoundNBT tag) { getTank().readFromNBT(tag); }
    @Override public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(getBlockPos(), 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket packet) {
        handleUpdateTag(getBlockState(), packet.getTag());
    }

    public static BlockPos scanFluidSpaceForNonsourceBlock(World world, BlockPos initial, Fluid fluid, int range) {
        return findPlacement(world, initial, fluid, range);
    }
    public static boolean isFluidBlock(net.minecraft.block.Block block, Fluid fluid) {
        return FluidDrainTileEntity.isFluidBlock(block, fluid);
    }
    public static boolean isFluidSourceBlock(net.minecraft.block.Block block, Fluid fluid, World world, BlockPos pos) {
        return FluidDrainTileEntity.isFluidSourceBlock(block, fluid, world, pos);
    }
}
