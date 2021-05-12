package com.mcmoddev.poweradvantage.util;

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.mcmoddev.lib.data.SharedStrings;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/*
 * Simple Breadth-first search across a given distance-range for fluids
 * Thanks to @Robin QueenOfMissiles, @julian and @thecodewarrior in The Lair (private Discord server)
 * for their assistance in checking the logic and identifying the correct algorithm to use for this.
 */
public class FluidPathTracer {
	private static final int WORKING_RANGE = 32;
	private static final EnumFacing[] possibles = { EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP };
	private FluidPathTracer() {
		throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);		
	}

	public static FluidStack trace(World world, BlockPos startingPos, Fluid fluidType, int getAmount) {
		Fluid f = FluidUtils.getFluidFromBlock(world, startingPos);
		List<BlockPos> toDrain;

		if ( f == null ) { // not over a fluid!
			if (fluidType != null) {
				// find us a match to the input type
				toDrain = searchMatchingFluids(world, startingPos, FluidRegistry.getFluidStack(fluidType.getName(), getAmount));
			} else {
				toDrain = searchAnyFluids(world, startingPos.up(), getAmount);
			}
		} else if(fluidType == null) {
			toDrain = searchAnyFluids(world, startingPos.up(), getAmount);
		} else if(FluidUtils.fluidsMatch(fluidType, f)) {
			FluidStack possible = FluidUtils.drainBlock(world, startingPos, false);
			// possible should not be null, as we've already checked that its the right fluid there
			// so the it should return something - unless that block cannot be drained.
			FluidStack full = new FluidStack(fluidType, getAmount);
			if (possible == null || !FluidUtils.fluidsMatchExact(full, possible)) {
				// search out and find enough to drain to meet "getAmount"
				toDrain = searchMatchingFluids(world, startingPos, FluidRegistry.getFluidStack(fluidType.getName(), getAmount));
			} else {
				return FluidUtils.drain(world, startingPos, getAmount);
			}
		} else {
			// fluid wasn't null, but also didn't match - so its not what we're looking for.
			// in this case, we should just return
			return null;
		}

		if (!toDrain.isEmpty()) {
			Fluid returnFluid = FluidUtils.getFluidFromBlock(world, toDrain.get(0));
			int drainedAmount = toDrain.stream().mapToInt( bp -> FluidUtils.drainBlock(world, bp, true).amount ).sum();
			return new FluidStack(returnFluid, drainedAmount);
		}

		return null;
	}

	public static List<BlockPos> searchAnyFluids(World world, BlockPos start, int amount) {
		Deque<BlockPos> workQ = new LinkedList<>();
		Set<BlockPos> seenBlocks = new HashSet<>();

		workQ.push(start);

		while (!workQ.isEmpty()) {
			BlockPos workingPos = workQ.removeFirst();

			if (seenBlocks.contains(workingPos) || start.getDistance(workingPos.getX(), workingPos.getY(), workingPos.getZ()) > WORKING_RANGE) continue;
			seenBlocks.add(workingPos);

			Fluid test = FluidUtils.getFluidFromBlock(world, workingPos);
			if (test != null) {
				return searchMatchingFluids(world, workingPos, new FluidStack(test, amount));
			}

			if (world.isAirBlock(workingPos)) enqueue_facings_any(world, workQ, workingPos);
		}
		return Collections.emptyList();
	}

	public static List<BlockPos> searchMatchingFluids(World world, BlockPos start, FluidStack match) {
		Deque<BlockPos> workQ = new LinkedList<>();
		List<BlockPos> foundFluids = new LinkedList<>();
		Set<BlockPos> seenBlocks = new HashSet<>();
		int foundAmount = 0;
		workQ.add(start);

		while(!workQ.isEmpty() && foundAmount < match.amount) {
			BlockPos workingPos = workQ.removeFirst();

			if (seenBlocks.contains(workingPos)  || start.getDistance(workingPos.getX(), workingPos.getY(), workingPos.getZ()) > WORKING_RANGE) continue;
			seenBlocks.add(workingPos);

			Fluid test = FluidUtils.getFluidFromBlock(world, workingPos);
			if (test != null && test == match.getFluid()) {
				FluidStack temp = FluidUtils.drainBlock(world, workingPos, false);
				if (FluidUtils.fluidsMatch(temp, match)) {
					foundAmount += temp.amount;
					foundFluids.add(workingPos);
				}
				enqueue_facings(world, workQ, workingPos, match.getFluid());
			} else if(world.isAirBlock(workingPos)) {
				enqueue_facings(world, workQ, workingPos, match.getFluid());
			}			
		}

		return foundFluids;
	}

	private static void enqueue_facings_any(World world, Deque<BlockPos> workQueue, BlockPos start) {
		for (EnumFacing facing : possibles)
			if (world.isAirBlock(start.offset(facing)) || FluidUtils.getFluidFromBlock(world, start.offset(facing)) != null) workQueue.add(start.offset(facing));
	}

	private static void enqueue_facings(World world, Deque<BlockPos> workQueue, BlockPos startingPos, Fluid match) {
		for (EnumFacing facing : possibles) {
			Fluid test = FluidUtils.getFluidFromBlock(world, startingPos.offset(facing));
			if (test != null && FluidUtils.fluidsMatch(match, test) || world.isAirBlock(startingPos.offset(facing))) {
				workQueue.add(startingPos.offset(facing));
			}
		}
	}
}
