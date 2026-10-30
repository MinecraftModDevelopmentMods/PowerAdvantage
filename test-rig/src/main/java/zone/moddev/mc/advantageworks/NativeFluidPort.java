package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/** Forge 25 uses the Advantage native-fluid API, not Forge 28's later fluid capability. */
final class NativeFluidPort {
    private final IAdvantageFluidHandler handler;
    private final EnumFacing face;

    NativeFluidPort(TileEntity tile, EnumFacing face) {
        if (!(tile instanceof IAdvantageFluidHandler)) throw new IllegalStateException("Missing sided Advantage fluid handler");
        handler = (IAdvantageFluidHandler)tile;
        this.face = face;
    }

    int fill(FluidStack stack, boolean execute) { return handler.fill(face, stack, execute); }
    FluidStack drain(int amount, boolean execute) { return handler.drain(face, amount, execute); }
    FluidStack getFluidInTank(int index) { return handler.getTankInfo(face)[index].fluid; }
    int amount(int index) {
        FluidStack fluid = getFluidInTank(index);
        return fluid == null ? 0 : fluid.amount;
    }
}
