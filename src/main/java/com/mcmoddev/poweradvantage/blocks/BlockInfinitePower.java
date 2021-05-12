package com.mcmoddev.poweradvantage.blocks;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.machine.BlockSimpleMachine;
import com.mcmoddev.poweradvantage.tiles.TileInfiniteEnergy;

import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;

public class BlockInfinitePower extends BlockSimpleMachine<TileInfiniteEnergy> {
        public BlockInfinitePower() {
                super(Materials.getMaterialByName("steel"), TileInfiniteEnergy.class, TileInfiniteEnergy::new, Material.PISTON);
                setTranslationKey(PowerAdvantage.MODID + ".electricity");
                this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH);
        }
}
