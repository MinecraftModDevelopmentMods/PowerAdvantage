package cyano.poweradvantage.api.modsupport.techreborn;

import cyano.poweradvantage.api.ConduitType;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Created by Chris on 4/10/2016.
 */
public class TileEntityTRElectricityConverter extends TileEntityTRConverter {
	public TileEntityTRElectricityConverter() {
		super(new ConduitType("electricity"), TileEntityTRElectricityConverter.class.getSimpleName());
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return true;
	}
}