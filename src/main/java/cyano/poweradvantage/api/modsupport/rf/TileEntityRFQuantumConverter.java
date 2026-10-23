package cyano.poweradvantage.api.modsupport.rf;

import cyano.poweradvantage.api.ConduitType;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityRFQuantumConverter extends TileEntityRFConverter {

	public TileEntityRFQuantumConverter() {
		super(Entities.RF_QUANTUM_CONVERTER, new ConduitType("quantum"), 1000, 1000,
				"tile.poweradvantage.converter_rf_quantum.name");
	}

}
