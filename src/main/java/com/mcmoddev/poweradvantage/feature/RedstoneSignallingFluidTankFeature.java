package com.mcmoddev.poweradvantage.feature;

import com.mcmoddev.lib.feature.FluidTankFeature;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

public class RedstoneSignallingFluidTankFeature extends FluidTankFeature implements ITickable {
	private final TileEntity sourceTile;
	
	public RedstoneSignallingFluidTankFeature(String key, int capacity, TileEntity source) {
		super(key, capacity, fs -> true, fs -> true);
		this.sourceTile = source;
	}
	
	@Override
	public void update() {
		sourceTile.markDirty();
		sourceTile.getWorld().notifyNeighborsOfStateChange(sourceTile.getPos(), sourceTile.getWorld().getBlockState(sourceTile.getPos()).getBlock(), true);
	}

}
