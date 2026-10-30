package cyano.poweradvantage.api.modsupport.techreborn;

import cyano.poweradvantage.api.ConduitType;
import net.minecraft.entity.player.PlayerEntity;
import zone.moddev.mc.poweradvantage.init.Entities;

/**
 * Created by Chris on 4/10/2016.
 */
public class TileEntityTRElectricityConverter extends TileEntityTRConverter {
	public TileEntityTRElectricityConverter() {
		super(Entities.TR_ELECTRICITY_CONVERTER, new ConduitType("electricity"),
				"tile.poweradvantage.converter_tr_electricity.name");
	}
	
	@Override
	public boolean stillValid(PlayerEntity player) {
		return true;
	}
}
