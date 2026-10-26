package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTankInfo;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.fluid.Fluid;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/** A side-bound foreign capability; reacquire it after removal or capability invalidation. */
final class AdvantageFluidAdapter implements IAdvantageFluidHandler {
    private final TileEntity tile;
    private final Direction side;

    AdvantageFluidAdapter(TileEntity tile, Direction side) {
        this.tile = tile;
        this.side = side;
    }

    private IFluidHandler handler() {
        return tile.isRemoved() ? null : tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side).orElse(null);
    }

    @Override public FluidTankInfo[] getTankInfo(Direction ignored) {
        IFluidHandler handler = handler();
        if (handler == null) return new FluidTankInfo[0];
        FluidTankInfo[] result = new FluidTankInfo[handler.getTanks()];
        for (int i = 0; i < result.length; i++) result[i] = new FluidTankInfo(
                ForgeFluidAdapter.fromForge(handler.getFluidInTank(i)), handler.getTankCapacity(i));
        return result;
    }
    @Override public int fill(Direction ignored, FluidStack resource, boolean execute) {
        IFluidHandler handler = handler();
        if (handler == null || resource == null || resource.amount <= 0) return 0;
        return Math.max(0, Math.min(resource.amount, handler.fill(ForgeFluidAdapter.toForge(resource),
                execute ? FluidAction.EXECUTE : FluidAction.SIMULATE)));
    }
    @Override public FluidStack drain(Direction ignored, FluidStack resource, boolean execute) {
        IFluidHandler handler = handler();
        if (handler == null || resource == null || resource.amount <= 0) return null;
        FluidStack result = ForgeFluidAdapter.fromForge(handler.drain(ForgeFluidAdapter.toForge(resource),
                execute ? FluidAction.EXECUTE : FluidAction.SIMULATE));
        return result == null || !resource.isFluidEqual(result) ? null : new FluidStack(result, Math.min(resource.amount, result.amount));
    }
    @Override public FluidStack drain(Direction ignored, int amount, boolean execute) {
        IFluidHandler handler = handler();
        if (handler == null || amount <= 0) return null;
        FluidStack result = ForgeFluidAdapter.fromForge(handler.drain(amount, execute ? FluidAction.EXECUTE : FluidAction.SIMULATE));
        return result == null ? null : new FluidStack(result, Math.min(amount, result.amount));
    }
    @Override public boolean canFill(Direction ignored, Fluid fluid) {
        IFluidHandler handler = handler();
        if (handler == null || fluid == null) return false;
        net.minecraftforge.fluids.FluidStack probe = new net.minecraftforge.fluids.FluidStack(fluid, 1);
        for (int i = 0; i < handler.getTanks(); i++) if (handler.isFluidValid(i, probe)) return true;
        return false;
    }
    @Override public boolean canDrain(Direction ignored, Fluid fluid) {
        return fluid != null && drain(side, new FluidStack(fluid, 1), false) != null;
    }
}
