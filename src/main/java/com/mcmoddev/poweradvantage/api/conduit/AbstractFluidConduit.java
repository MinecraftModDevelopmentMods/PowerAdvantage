package com.mcmoddev.poweradvantage.api.conduit;

import com.mcmoddev.lib.block.BlockMMDBlock;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.EnumPipeType;
import com.mcmoddev.poweradvantage.api.ITypedConduit;
import com.mcmoddev.poweradvantage.api.machine.BlockSimpleMachine;
import com.mcmoddev.poweradvantage.tiles.TileFluidConduit;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.Fluid;

public abstract class AbstractFluidConduit extends Block implements ITypedConduit {
	private final int transferRate;
	
	public AbstractFluidConduit(MMDMaterial material, int rate) {
		super(Material.PISTON);
		this.blockHardness = material.getBlockHardness();
		this.blockResistance = material.getBlastResistance();
		this.setTranslationKey(PowerAdvantage.MODID + "." + material.getCapitalizedName() + ".conduit");
		this.transferRate = rate;
	}
	
	public final int getTransferRate() {
		return this.transferRate;
	}
		
	@Override
	public boolean canAcceptConnection(EnumPipeType type, EnumFacing facing) {
		if (type == EnumPipeType.TYPE_FLUID) return true;
		return false;
	}

	@Override
	public EnumPipeType conduitType() {
		return EnumPipeType.TYPE_FLUID;
	}

	@Override
	abstract public int fill(int amount, boolean simulate);
	
	@Override
	abstract public int drain(int amount, boolean simulate);
	
	@Override
	abstract public boolean forceConnection( EnumFacing side);
}
