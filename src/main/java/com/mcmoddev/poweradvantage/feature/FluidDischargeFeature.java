package com.mcmoddev.poweradvantage.feature;

import com.mcmoddev.lib.feature.FluidTankFeature;
import com.mcmoddev.poweradvantage.util.FillablePathTracer;
import com.mcmoddev.poweradvantage.util.FluidUtils;
import com.mcmoddev.poweradvantage.util.MachineHelpers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.fluids.Fluid;

public class FluidDischargeFeature extends FluidTankFeature implements ITickable {
	private final static int TICKS_PER_WORK = 10;
	private final TileEntity source;
	private int nextWork = TICKS_PER_WORK;
	private final int configuredCapacity;

	public FluidDischargeFeature(String key, int capacity, TileEntity source) {
		super(key, capacity, (fs) -> true, (fs) -> true);
		this.source = source;
		this.configuredCapacity = capacity;
	}

	@Override
	public void update() {
		if ( nextWork > 0) {
			nextWork--;
			return;
		} else {
			nextWork = TICKS_PER_WORK;
		}

		if (getExternalTank().getFluidAmount() < configuredCapacity)
			for (EnumFacing f : EnumFacing.VALUES) {
				MachineHelpers.doFluidGetInteraction(source, source.getWorld().getTileEntity(source.getPos().offset(f)), configuredCapacity, f);
			}

		if ( getExternalTank().getFluid() == null || getExternalTank().getFluidAmount() < 1000 ) return;

		Fluid ff = FluidUtils.getFluidFromBlock(source.getWorld(), source.getPos().down());
		if (FluidUtils.isFillableBlock(source.getWorld(), source.getPos().down()) || FluidUtils.isFillableFluid(source.getWorld(), source.getPos().down()) || FluidUtils.fluidsMatch(getExternalTank().getFluid().getFluid(), ff)) {
			int drainBuckets = getExternalTank().getFluidAmount() / 1000;
			int drainAmount = FillablePathTracer.fillBlocks(source.getWorld(), source.getPos().down(), getExternalTank().getFluid().getFluid(), drainBuckets);
			getExternalTank().drain(drainAmount, true);
		}
	}
}
