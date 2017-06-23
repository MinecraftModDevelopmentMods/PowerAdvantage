package com.mcmoddev.poweradvantage3.multipart;

import com.mcmoddev.poweradvantage3.Tier;
import mcmultipart.api.multipart.IMultipart;
import mcmultipart.api.slot.IPartSlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class PartFluidPipe implements IMultipart {
    public Tier.FluidPipeTier tier;

	@Override
	public IPartSlot getSlotForPlacement(World world, BlockPos pos, IBlockState state, EnumFacing facing, float hitX,
			float hitY, float hitZ, EntityLivingBase placer) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IPartSlot getSlotFromWorld(IBlockAccess world, BlockPos pos, IBlockState state) {
		// TODO Auto-generated method stub
		return null;
	}


}
