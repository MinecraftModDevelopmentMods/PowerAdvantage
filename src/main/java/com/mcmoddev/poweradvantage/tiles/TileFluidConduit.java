package com.mcmoddev.poweradvantage.tiles;

import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.tile.MMDStandardTileEntity;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileFluidConduit extends MMDStandardTileEntity {

	public TileFluidConduit() {
		
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	protected IWidgetGui getMainContentWidgetGui(GuiContext context) {
		// TODO Auto-generated method stub
		return null;
	}

}
