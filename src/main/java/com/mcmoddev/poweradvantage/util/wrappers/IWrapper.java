package com.mcmoddev.poweradvantage.util.wrappers;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

@SuppressWarnings("rawtypes")
public interface IWrapper<T> {
	T getCapability(EnumFacing side);
	boolean hasCapability(Capability<?> capability, EnumFacing side);
	int getAcceptableAmount();
	int getAvailableAmount();
}
