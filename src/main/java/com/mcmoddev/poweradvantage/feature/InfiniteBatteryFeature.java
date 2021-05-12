package com.mcmoddev.poweradvantage.feature;

import javax.annotation.Nullable;

import com.mcmoddev.lib.energy.ForgeEnergyStorage;
import com.mcmoddev.lib.feature.FeatureDirtyLevel;
import com.mcmoddev.lib.feature.ForgeEnergyBatteryFeature;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class InfiniteBatteryFeature extends ForgeEnergyBatteryFeature implements ITickable {
	private final TileEntity source;
	private final ForgeEnergyStorage buffer;

	public InfiniteBatteryFeature(TileEntity source) {
		super("infinite_battery", 65535, 65535, 4096, 4096);
		this.source = source;
		buffer = getEnergyStorage();
	}
	
	private void doInteraction(TileEntity targetEntity, IEnergyStorage target) {
		int sendMax = target.receiveEnergy(Integer.MAX_VALUE, true);
		if (target.canReceive()) {
			target.receiveEnergy(this.buffer.take(sendMax, true), false);
			this.setDirty(FeatureDirtyLevel.GUI);
		}	
	}
	
	@Nullable
	private TileEntity getAdjacentTE(EnumFacing facing) {
		BlockPos pos = source.getPos().offset(facing);
		World world = source.getWorld();
		
		if (world == null || !world.isBlockLoaded(pos)) return null;
		return world.getTileEntity(pos);
	}
	
	@Override
	public void update() {
		if ( this.buffer.getStored() < 65535) {
			this.buffer.store(65535 - this.buffer.getStored(), true);
		}
		for( EnumFacing facing : EnumFacing.values() ) {
			TileEntity target = getAdjacentTE(facing);
			if(target != null && target.hasCapability(CapabilityEnergy.ENERGY, facing.getOpposite())) {
				IEnergyStorage tCap = (IEnergyStorage) target.getCapability(CapabilityEnergy.ENERGY, facing.getOpposite());
				doInteraction(target, tCap);
				int maxTake = tCap.extractEnergy(Integer.MAX_VALUE, true);
				tCap.extractEnergy(maxTake, false);
			}
		}
	}

}
