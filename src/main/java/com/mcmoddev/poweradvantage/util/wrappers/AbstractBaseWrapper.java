package com.mcmoddev.poweradvantage.util.wrappers;

import java.util.function.Supplier;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

public abstract class AbstractBaseWrapper<T, W> implements IWrapper<T> {
	private T wrapped;
	
	public AbstractBaseWrapper(Supplier<T> wrapper) {
		wrapped = wrapper.get();
	}

	public abstract boolean canInterface(EnumFacing side);

	protected T getWrapped() {
		return wrapped;
	}
	
	@Override
	public T getCapability(EnumFacing side) {
		return canInterface(side)?wrapped:null;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing side) {
		return canInterface(side) && capability == wrapped;
	}

	@Override
	public abstract int getAcceptableAmount();
	
	@Override
	public abstract int getAvailableAmount();

	public abstract W getRepresentation();
	public abstract W drain(int amount, boolean simulate);
	public abstract int fill(W source, boolean simulate);
	
}
