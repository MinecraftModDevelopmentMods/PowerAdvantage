package com.mcmoddev.poweradvantage.tiles;

import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import com.mcmoddev.lib.feature.FluidTankFeature;
import com.mcmoddev.lib.tile.MMDStandardTileEntity;
import com.mcmoddev.poweradvantage.feature.RedstoneSignallingFluidTankFeature;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class StorageTankTileEntity extends MMDStandardTileEntity implements ITickable {
	private final FluidTankFeature tank;
	private final int capacity;
	
	public StorageTankTileEntity(int capacity) {
		this.tank = new RedstoneSignallingFluidTankFeature("tank", capacity, (TileEntity)this);
		this.capacity = capacity;
		this.addFeature(this.tank);
	}
	
	public final int getCapacity() {
		return capacity;
	}
	
	public final int getCurrentFillLevel() {
		return tank.getExternalTank().getFluidAmount();
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	protected IWidgetGui getMainContentWidgetGui(GuiContext context) {
		return new GridLayout(1, 1)
				.addPiece(new FeatureWrapperGui(context, this, "tank"), 0, 0, 1, 1);
	}
}
