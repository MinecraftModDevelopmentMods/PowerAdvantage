package zone.moddev.mc.advantageworks;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

final class NativeFluids {
    static Fluid get(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null && name.startsWith("minecraft:")) fluid = FluidRegistry.getFluid(name.substring(10));
        if (fluid == null && (name.equals("poweradvantage:crude_oil") || name.equals("poweradvantage:refined_oil")))
            fluid = FluidRegistry.getFluid(name.substring("poweradvantage:".length()));
        if (fluid == null) throw new IllegalArgumentException("Unregistered fixture fluid " + name);
        return fluid;
    }
}
