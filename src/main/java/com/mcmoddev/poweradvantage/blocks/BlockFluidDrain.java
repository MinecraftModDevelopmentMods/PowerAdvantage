package com.mcmoddev.poweradvantage.blocks;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.machine.BlockSimpleMachine;
import com.mcmoddev.poweradvantage.tiles.TileFluidDrain;

import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;

public class BlockFluidDrain extends BlockSimpleMachine<TileFluidDrain> {
	public BlockFluidDrain() {
		super( Materials.getMaterialByName("steel"), TileFluidDrain.class, () -> new TileFluidDrain(), Material.PISTON);
        setTranslationKey(PowerAdvantage.MODID + ".fluid_drain");
        this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH);
	}
}
