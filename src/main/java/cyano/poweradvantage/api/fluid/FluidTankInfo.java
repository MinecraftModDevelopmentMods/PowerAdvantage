package cyano.poweradvantage.api.fluid;

import javax.annotation.Nullable;

/** Immutable snapshot of an Advantage fluid tank. */
public final class FluidTankInfo {
    @Nullable public final FluidStack fluid;
    public final int capacity;

    public FluidTankInfo(@Nullable FluidStack fluid, int capacity) {
        this.fluid = fluid == null ? null : fluid.copy();
        this.capacity = capacity;
    }
}
