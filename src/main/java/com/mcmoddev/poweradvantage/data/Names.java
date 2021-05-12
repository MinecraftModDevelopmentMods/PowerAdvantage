package com.mcmoddev.poweradvantage.data;

import java.util.Locale;
import java.util.TreeMap;

import javax.annotation.Nonnull;

import com.google.common.collect.Maps;

import net.minecraft.util.IStringSerializable;

public enum Names implements IStringSerializable {
	INFINITE, CONVEYORFILTER, INFINITE_STEAM, INFINITE_POWER, STEEL_FRAME, FLUID_DRAIN, FLUID_DISCHARGE, FLUID_STORAGE_TANK, FLUID_METAL_TANK;
	
	protected static final TreeMap<Integer, Names> MAP = Maps.newTreeMap();

	static {
		for (final Names name : values()) {
			MAP.put(name.ordinal(), name);
		}
	}

	@Override
	public String getName() {
		return this.name();
	}

	/**
	 *
	 * @param ordinal
	 * @return
	 */
	public static Names getType(@Nonnull int ordinal) {
		if ((ordinal > values().length) || (ordinal < 0)) {
			ordinal = 0;
		}
		return values()[ordinal];
	}

	@Override
	public String toString() {
		return this.getName().toLowerCase(Locale.ROOT);
	}

}
