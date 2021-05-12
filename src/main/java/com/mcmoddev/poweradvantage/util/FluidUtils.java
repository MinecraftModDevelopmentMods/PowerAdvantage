package com.mcmoddev.poweradvantage.util;

import com.mcmoddev.lib.data.SharedStrings;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

public class FluidUtils {

	private FluidUtils() {
        throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
	}
	
	public static int getFluidAmountFromBlock(World world, BlockPos pos) {
		FluidStack fluidStack = getFluidStackFromBlock(world, pos);
		return fluidStack != null ? fluidStack.amount : 0;
	}

	public static FluidStack getFluidStackFromBlock(World world, BlockPos pos) {
		IBlockState blockState = world.getBlockState(pos);
		Block block = blockState.getBlock();
		int meta = block.getMetaFromState(blockState);
		
		if (block instanceof IFluidBlock) {
			IFluidBlock fluidBlock = ((IFluidBlock) block);
			return new FluidStack(fluidBlock.getFluid(), (int) (Fluid.BUCKET_VOLUME * fluidBlock.getFilledPercentage(world, pos)));
		} else if (block == Blocks.WATER || block == Blocks.FLOWING_WATER && meta == 0) {
				return new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME);
		} else if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA && meta == 0) {
				return new FluidStack(FluidRegistry.LAVA, Fluid.BUCKET_VOLUME);
		}

		return null;
	}

	public static Fluid getFluidFromBlock(World world, BlockPos pos) {
		FluidStack fs = getFluidStackFromBlock(world, pos);
		return fs != null ? fs.getFluid() : null;
	}
	
	public static FluidStack drainBlock(World world, BlockPos pos) {
		return drainBlock(world, pos, 1|2); // call with proper update flags
	}
	
	public static FluidStack drainBlock(World world, BlockPos pos, boolean doDrain) {
		if (doDrain) return drainBlock(world, pos);
		
		if (world == null || pos == null) return null;
		
		FluidStack fs = getFluidStackFromBlock(world, pos);
		
		if (fs == null) return null;
		
		Block f = fs.getFluid().getBlock();
		
		if (f instanceof IFluidBlock && ((IFluidBlock)f).canDrain(world, pos) ||
				f == Blocks.WATER || f == Blocks.FLOWING_WATER || f == Blocks.LAVA || f == Blocks.FLOWING_LAVA )
			return fs;
		return null;
	}
	
	public static FluidStack drainBlock(World world, BlockPos pos, int updateFlags) {
		if (world == null || pos == null) return null;
		
		FluidStack fs = getFluidStackFromBlock(world, pos);
		
		if (fs == null) return null;
		
		Block f = fs.getFluid().getBlock();
		
		if (f instanceof IFluidBlock && ((IFluidBlock) f).canDrain(world, pos)) {
				return ((IFluidBlock) f).drain(world, pos, true);
		} else {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), updateFlags);
			return fs;
		}
	}
	
	public static boolean isFillableBlock(World world, BlockPos pos)
	{
		if (world == null || pos == null) return false;

		IBlockState blockState = world.getBlockState(pos); 
		Block block = blockState.getBlock();

		if (drainBlock(world, pos, false) != null) {
			return false;
		} else if (block.isAir(blockState, world, pos)) {
			return true;
		} else if (!(block instanceof IFluidBlock || block instanceof BlockLiquid) && block.isReplaceable(world, pos)) {
			return true;
		}
		return false;
	}
	
	public static boolean isFillableFluid(World world, BlockPos pos)
	{
		if (world == null || pos == null) return false;

		IBlockState blockState = world.getBlockState(pos);
		Block block = blockState.getBlock();
		int meta = block.getMetaFromState(blockState);

		if (drainBlock(world, pos, false) != null) {
			return false;
		} else if (block instanceof IFluidBlock || block instanceof BlockLiquid) {
			return !(meta == 0);
		}
		return false;
	}
	
	private static IFluidBlock getFluidBlock(Block b) {
		if (b instanceof IFluidBlock) return (IFluidBlock)b;
		else return null;
	}
	
	public static int fillBlock(World world, BlockPos pos, FluidStack stack, boolean doFill)
	{
		if ((isFillableBlock(world, pos) || isFillableFluid(world, pos)) && stack != null && stack.amount >= Fluid.BUCKET_VOLUME) {
			if (doFill)	{
				IBlockState blockState = world.getBlockState(pos);
				Block block = blockState.getBlock();

				Block bfb = stack.getFluid().getBlock();
				IFluidBlock fb = getFluidBlock(bfb);
				IBlockState fluidState = bfb.getDefaultState();
				
				if (block != null) {
					if (fb != null || world.isAirBlock(pos)) { 
						block.dropBlockAsItem(world, pos, blockState, 1);
						block.breakBlock(world, pos, blockState);
					}
				}

				world.setBlockState(pos, fluidState, 1|2);
			}
			return Fluid.BUCKET_VOLUME;
		}
		return 0;
	}

	public static boolean fluidsMatch(String mainName, String matchName) {
		FluidStack main = FluidRegistry.getFluidStack(mainName, 1000);
		FluidStack match = FluidRegistry.getFluidStack(matchName, 1000);
		return (main != null && match != null) && fluidsMatch(main, match);
	}
	
	public static boolean fluidsMatch(Fluid main, Fluid match) {
		if (main == null && match == null) {
			return true; // null matches null
		} else if( main == null || match == null ){
			// if we got here only one fluid is null and null never matches a fluid
			return false;
		} else {
			return fluidsMatch(main.getName(), match.getName());
		}
	}

	public static boolean fluidsMatch(FluidStack main, FluidStack match) {
		return main.isFluidEqual(match);
	}
	
	public static boolean fluidsMatchExact(FluidStack main, FluidStack match) {
		return fluidsMatch(main, match) && main.amount == match.amount;
	}
	
	public static FluidStack drain(World world, BlockPos pos, int amount) {
		if(drainBlock(world, pos, false).amount == amount) return drainBlock(world, pos, true);
		
		return null;
	}

	public static boolean isFillable(World world, BlockPos pos) {
		return isFillableBlock(world, pos) || isFillableFluid(world, pos);
	}
}
