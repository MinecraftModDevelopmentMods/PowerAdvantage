package com.mcmoddev.poweradvantage.blocks;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.machine.BlockSimpleMachine;
import com.mcmoddev.poweradvantage.tiles.TileInfiniteSteam;

import net.minecraft.block.material.Material;

public class BlockInfiniteSteam extends BlockSimpleMachine<TileInfiniteSteam> {
	public BlockInfiniteSteam() {
		super(Materials.getMaterialByName("steel"), TileInfiniteSteam.class, () -> new TileInfiniteSteam(), Material.PISTON);
		setTranslationKey(PowerAdvantage.MODID + ".infinite_steam");
	}
}
