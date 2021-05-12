package com.mcmoddev.poweradvantage.api;

import net.minecraft.util.EnumFacing;

public interface ITypedConduit {
	boolean canAcceptConnection(EnumPipeType type, EnumFacing facing);
	EnumPipeType conduitType();
	int fill(int amount, boolean simulate );
	int drain(int amount, boolean simulate );
	boolean forceConnection( EnumFacing side );
}
