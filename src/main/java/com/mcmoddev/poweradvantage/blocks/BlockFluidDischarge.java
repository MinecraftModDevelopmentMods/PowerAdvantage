package com.mcmoddev.poweradvantage.blocks;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.machine.BlockSimpleMachine;
import com.mcmoddev.poweradvantage.tiles.TileFluidDischarge;

import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;

public class BlockFluidDischarge extends BlockSimpleMachine<TileFluidDischarge> {
	public BlockFluidDischarge() {
		super( Materials.getMaterialByName("steel"), TileFluidDischarge.class, () -> new TileFluidDischarge(), Material.PISTON );
        setTranslationKey(PowerAdvantage.MODID + ".fluid_discharge");
        this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH);
	}
}
