package zone.moddev.mc.poweradvantage.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;

public final class FluidIdHelper {

	private FluidIdHelper() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static int getFluidId(Fluid fluid) {
		if (fluid == null) return 0;
		ResourceLocation name = IRegistry.field_212619_h.getKey(fluid);
		return name == null ? 0 : name.toString().hashCode();
	}

	public static Fluid getFluid(int id) {
		if (id == 0) return null;
		for (Fluid fluid : IRegistry.field_212619_h) {
			ResourceLocation name = IRegistry.field_212619_h.getKey(fluid);
			if (name != null && name.toString().hashCode() == id) return fluid;
		}
		return null;
	}
}
