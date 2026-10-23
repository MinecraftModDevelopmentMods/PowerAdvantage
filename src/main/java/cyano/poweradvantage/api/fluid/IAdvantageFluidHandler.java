package cyano.poweradvantage.api.fluid;

import javax.annotation.Nullable;

import net.minecraft.fluid.Fluid;
import net.minecraft.util.EnumFacing;

/**
 * Stable millibucket fluid contract used by Advantage machines on Forge 1.13.
 * It avoids Forge 25's unfinished transitional FluidStack implementation.
 */
public interface IAdvantageFluidHandler {
    FluidTankInfo[] getTankInfo(@Nullable EnumFacing face);
    int fill(@Nullable EnumFacing face, FluidStack resource, boolean doFill);
    FluidStack drain(@Nullable EnumFacing face, FluidStack resource, boolean doDrain);
    FluidStack drain(@Nullable EnumFacing face, int amount, boolean doDrain);
    boolean canFill(@Nullable EnumFacing face, Fluid fluid);
    boolean canDrain(@Nullable EnumFacing face, Fluid fluid);
}
