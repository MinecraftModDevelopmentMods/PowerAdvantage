package com.mcmoddev.poweradvantage.api.modsupport.rf;

import com.mcmoddev.poweradvantage.api.ConduitType;

public class TileEntityRFSteamConverter extends TileEntityRFConverter {

	public TileEntityRFSteamConverter() {
		super(new ConduitType("steam"), 1000, 1000, TileEntityRFSteamConverter.class.getSimpleName());
	}

}
