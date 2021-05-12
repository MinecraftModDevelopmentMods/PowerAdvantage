package com.mcmoddev.poweradvantage.util.wrappers;

import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class FluidWrapper extends AbstractBaseWrapper<Capability<IFluidHandler>, FluidStack> {
	public FluidWrapper() {
		super( () -> CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
	}
	
	public boolean canInterface(EnumFacing side) {
		return true;
	}

	private IFluidHandler getFluidHandler() {
		return getWrapped().getDefaultInstance();
	}
	
	@Override
	public int getAcceptableAmount() {
		IFluidHandler k = getFluidHandler();
		FluidStack p = k.drain(Fluid.BUCKET_VOLUME, false);
		if (p == null) {
			return k.fill(FluidRegistry.getFluidStack("water", Integer.MAX_VALUE), false);
		} else {
			p.amount = Integer.MAX_VALUE;
			return k.fill(p, false);
		}
	}
	
	@Override
	public int getAvailableAmount() {
		IFluidHandler k = getFluidHandler();
		FluidStack p = k.drain(Integer.MAX_VALUE, false);
		if (p == null) {
			return k.fill(FluidRegistry.getFluidStack("water", Integer.MAX_VALUE), false);
		} else {
			return p.amount;
		}
	}

	public FluidStack getRepresentation() {
		FluidStack p = getFluidHandler().drain(Integer.MAX_VALUE, false);
		if (p == null) {
			return FluidRegistry.getFluidStack("water", 1000);
		} else {
			p.amount = 1000;
			return p;
		}
	}
	
	public FluidStack drain(int amount, boolean simulate) {
		return getFluidHandler().drain(amount, simulate);
	}

	@Override
	public int fill(FluidStack source, boolean simulate) {
		return getFluidHandler().fill(source, simulate);
	}

}
