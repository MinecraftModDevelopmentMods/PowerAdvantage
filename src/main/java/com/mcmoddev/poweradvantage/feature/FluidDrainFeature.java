package com.mcmoddev.poweradvantage.feature;

import javax.annotation.Nullable;

import com.mcmoddev.lib.feature.FluidTankFeature;
import com.mcmoddev.poweradvantage.util.FluidPathTracer;
import com.mcmoddev.poweradvantage.util.MachineHelpers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

public class FluidDrainFeature extends FluidTankFeature implements ITickable {
	private final static int TICKS_PER_WORK = 10;
	private final TileEntity source;
	private int nextWork = TICKS_PER_WORK;
	private final int configuredCapacity;
	
	public FluidDrainFeature(String key, int capacity, TileEntity source) {
		super(key, capacity, fs -> true, fs -> true);
		this.source = source;
		this.configuredCapacity = capacity;
	}

	@Nullable
	private TileEntity getAdjacentTE(EnumFacing facing) {
		BlockPos pos = source.getPos().offset(facing);
		World world = source.getWorld();

		if (world == null || !world.isBlockLoaded(pos)) return null;
		return world.getTileEntity(pos);
	}

	@Override
	public void update() {
		if (nextWork == 0) {
			nextWork = TICKS_PER_WORK;
		} else {
			nextWork--;
			return;
		}

		if (getExternalTank().getFluid() != null) {
			// see if we can push to a neighboring TE that has fluid handler caps
			for (EnumFacing f : EnumFacing.VALUES) {
				MachineHelpers.doFluidSendInteraction(source, source.getWorld().getTileEntity(source.getPos().offset(f)), configuredCapacity, f);
			}
		}

		// try to fill from the world
		FluidStack contained = getExternalTank().getFluid();
		if (contained == null || contained.amount != configuredCapacity) {
			FluidStack fluidFill = FluidPathTracer.trace(source.getWorld(), source.getPos().up(), contained == null ? null : contained.getFluid(), configuredCapacity <= 4000 ? configuredCapacity : 4000);
			getExternalTank().fill(fluidFill, true);
		}
	}
}
