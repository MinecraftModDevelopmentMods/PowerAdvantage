package com.mcmoddev.poweradvantage.tiles;

import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import com.mcmoddev.lib.tile.MMDStandardTileEntity;
import com.mcmoddev.poweradvantage.feature.FluidDischargeFeature;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileFluidDischarge extends MMDStandardTileEntity {

	public TileFluidDischarge() {
		super();
		this.addFeature(new FluidDischargeFeature("discharge", 1000, this));
	}

	@Override
	@SideOnly(Side.CLIENT)
	protected IWidgetGui getMainContentWidgetGui(GuiContext context) {
		return  new GridLayout(1, 1)
				.addPiece(new FeatureWrapperGui(context, this, "discharge"), 0, 0, 1, 1);
	}

}
