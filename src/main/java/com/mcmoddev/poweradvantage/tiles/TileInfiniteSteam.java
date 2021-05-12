package com.mcmoddev.poweradvantage.tiles;

import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import com.mcmoddev.lib.tile.MMDStandardTileEntity;
import com.mcmoddev.poweradvantage.feature.InfiniteFluidSourceFeature;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileInfiniteSteam extends MMDStandardTileEntity {
	private final InfiniteFluidSourceFeature fluidTank;
	
	public TileInfiniteSteam() {
		super();
		this.fluidTank = new InfiniteFluidSourceFeature((TileEntity)this, 65535, "tank", "steam");
		addFeature(this.fluidTank);
	}

	@Override
	@SideOnly(Side.CLIENT)
	protected IWidgetGui getMainContentWidgetGui(GuiContext context) {
        return new GridLayout(1, 1)
                .addPiece(new FeatureWrapperGui(context, this, "tank"), 0, 0, 1, 1);
	}

}
