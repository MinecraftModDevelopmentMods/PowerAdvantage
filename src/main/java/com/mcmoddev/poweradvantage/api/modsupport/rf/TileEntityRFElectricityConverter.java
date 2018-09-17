package com.mcmoddev.poweradvantage.api.modsupport.rf;

import com.mcmoddev.poweradvantage.api.ConduitType;

public class TileEntityRFElectricityConverter extends TileEntityRFConverter {

	public TileEntityRFElectricityConverter() {
		super(new ConduitType("electricity"), 8000, 1000, TileEntityRFElectricityConverter.class.getSimpleName());
	}

}