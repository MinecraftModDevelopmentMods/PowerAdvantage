package com.mcmoddev.poweradvantage.tiles;

import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import com.mcmoddev.lib.tile.MMDStandardTileEntity;
import com.mcmoddev.poweradvantage.feature.InfiniteBatteryFeature;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileInfiniteEnergy extends MMDStandardTileEntity {
	public TileInfiniteEnergy() {
		super();
		this.addFeature(new InfiniteBatteryFeature(this));
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	protected IWidgetGui getMainContentWidgetGui(GuiContext context) {
        return new GridLayout(1, 1)
                .addPiece(new FeatureWrapperGui(context, this, "infinite_battery"), 0, 0, 1, 1);
	}

}
