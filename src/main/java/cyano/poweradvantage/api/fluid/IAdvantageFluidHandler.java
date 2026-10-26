package cyano.poweradvantage.api.fluid;

import javax.annotation.Nullable;

import net.minecraft.fluid.Fluid;
import net.minecraft.util.Direction;

/**
 * Stable millibucket fluid contract used by Advantage machines. On Forge 1.14
 * it is also exposed through the native sided fluid capability.
 */
public interface IAdvantageFluidHandler {
    FluidTankInfo[] getTankInfo(@Nullable Direction face);
    int fill(@Nullable Direction face, FluidStack resource, boolean doFill);
    FluidStack drain(@Nullable Direction face, FluidStack resource, boolean doDrain);
    FluidStack drain(@Nullable Direction face, int amount, boolean doDrain);
    boolean canFill(@Nullable Direction face, Fluid fluid);
    boolean canDrain(@Nullable Direction face, Fluid fluid);
}
