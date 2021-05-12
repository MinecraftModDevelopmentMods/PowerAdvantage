package com.mcmoddev.poweradvantage.blocks;

import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.machine.BlockSimpleMachine;
import com.mcmoddev.poweradvantage.tiles.StorageTankTileEntity;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class StorageTankBlock extends BlockSimpleMachine<StorageTankTileEntity> {
	public StorageTankBlock(MMDMaterial material, String name, int capacity) {
		super(material, StorageTankTileEntity.class, () -> new StorageTankTileEntity(capacity), Material.PISTON);
		this.setTranslationKey(PowerAdvantage.MODID + "." + name);
		this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH);
	}
	
	@Override
	public boolean canProvidePower(IBlockState state) {
		return true;
	}
	
    @Override
    public boolean getWeakChanges(IBlockAccess world, BlockPos pos)
    {
        return true;
    }
    
	@Override
    public int getStrongPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side)
    {
        return blockState.getWeakPower(blockAccess, pos, side);
    }
	
	@Override
	public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		TileEntity te = blockAccess.getTileEntity(pos);
		if (te != null && te instanceof StorageTankTileEntity) {
			StorageTankTileEntity ste = ((StorageTankTileEntity)te); 
			int pl = (int)(((float)ste.getCurrentFillLevel() / (float)ste.getCapacity()) * 15);
			return pl;
		} else {
			return 0;
		}
	}
	
	@Override
    public boolean canEntitySpawn(IBlockState state, Entity entityIn)
    {
        return false;
    }
	
	@Override
	public boolean isFullBlock(IBlockState state) {
		return false;
	}
	
	@Override
    public boolean isOpaqueCube(IBlockState bs) {
        return false;
    }
	
	@Override
    public EnumBlockRenderType getRenderType(IBlockState bs) {
        return EnumBlockRenderType.MODEL;
    }
	
	@Override
	public boolean hasComparatorInputOverride(IBlockState bs) {
		return true;
	}
	
	@Override
	public int getComparatorInputOverride(IBlockState bs, World world, BlockPos coord) {
		return this.getWeakPower(bs, world, coord, null);
	}

}
