package com.mcmoddev.poweradvantage.util;

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.mcmoddev.lib.data.SharedStrings;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class FillablePathTracer {
	private static final int WORKING_RANGE = 32;
	private static final EnumFacing[] possibles = { EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.DOWN };

	private FillablePathTracer() {
		throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);		
	}

	public static List<BlockPos> searchFillableBlocks(World world, BlockPos start, Fluid fillFluid, int numBlocks) {
		Fluid ff = FluidUtils.getFluidFromBlock(world, start);
		if ((ff  != null && !FluidUtils.fluidsMatch(ff, fillFluid)) && !FluidUtils.isFillable(world, start) ) return Collections.emptyList();
		
		Deque<BlockPos> workQ = new LinkedList<>();
		Set<BlockPos> seenBlocks = new HashSet<>();
		List<BlockPos> blocks = new LinkedList<>();
		
		workQ.push(start);

		while (!workQ.isEmpty() && blocks.size() <= numBlocks) {
			BlockPos workingPos = workQ.removeFirst();

			seenBlocks.add(workingPos);
			
			blocks.add(workingPos);
			
			for(EnumFacing facing : possibles) {
				BlockPos temp = workingPos.offset(facing);
				double distance = start.getDistance(temp.getX(), temp.getY(), temp.getZ());
				if (!seenBlocks.contains(temp) && distance <= WORKING_RANGE && FluidUtils.isFillableBlock(world, temp)) workQ.add(temp);
			}
		}
		
		return blocks;
	}
	
	public static int fillBlocks(World world, BlockPos start, Fluid fillFluid, int fillBucketCount) {
		// get fillable blocks, remove teh ones that are the fluid we're trying to fill with
		List<BlockPos> blocksToFill = searchFillableBlocks(world, start, fillFluid, fillBucketCount).stream().filter(bp -> !FluidUtils.fluidsMatch(fillFluid, FluidUtils.getFluidFromBlock(world, bp))).collect(Collectors.toList());
		int fillAmountRemaining = fillBucketCount*1000;
		
		for(BlockPos pos : blocksToFill) {
			int fillAmount = fillAmountRemaining >= 1000 ? 1000 : fillAmountRemaining;
			FluidStack fillStack = FluidRegistry.getFluidStack(fillFluid.getName(), fillAmount);
			fillAmountRemaining -= FluidUtils.fillBlock(world, pos, fillStack, true);
			if (fillAmountRemaining < 0) {
				FluidUtils.drainBlock(world, pos, true);
				return fillAmountRemaining;
			} else {
				return 0;
			}
		}
		
		return 0;
	}
}
