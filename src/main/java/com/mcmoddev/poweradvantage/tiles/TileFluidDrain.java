package com.mcmoddev.poweradvantage.tiles;

import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import com.mcmoddev.lib.tile.MMDStandardTileEntity;
import com.mcmoddev.poweradvantage.feature.FluidDrainFeature;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileFluidDrain extends MMDStandardTileEntity {
	private final FluidDrainFeature feat;
	public TileFluidDrain() {
		super();
		feat = new FluidDrainFeature("drain", 2000, this);
		this.addFeature(feat);
	}

	@Override
	@SideOnly(Side.CLIENT)
	protected IWidgetGui getMainContentWidgetGui(GuiContext context) {
		return  new GridLayout(1, 1)
                .addPiece(new FeatureWrapperGui(context, this, "drain"), 0, 0, 1, 1);
	}

}
