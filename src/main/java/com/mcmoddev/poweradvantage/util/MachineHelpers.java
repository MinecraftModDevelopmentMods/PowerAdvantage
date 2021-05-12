package com.mcmoddev.poweradvantage.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mcmoddev.lib.data.SharedStrings;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class MachineHelpers {

	private MachineHelpers() {
		throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);		
	}

	/**
	 * Gets the IEnergyStorage capability of a specified facing of a TE, if it exists.
	 * @param tile The TileEntity to get the Capability from - if this parameter is null, the result is null
	 * @param facing The facing to check for the capability - this may be null, with results as noted for TileEntity.getCapability
	 * @return the IEnergyStorage capability of the TileEntity or null
	 */
	@Nullable
	public static IEnergyStorage getEnergyCapability(@Nullable final TileEntity tile, @Nullable final EnumFacing facing) {
		if (tile != null && hasCapability(tile, CapabilityEnergy.ENERGY, facing)) {
			return getEnergyNoChecks(tile, facing);
		}

		return null;
	}

	@Nonnull
	private static IEnergyStorage getEnergyNoChecks(@Nonnull final TileEntity tile, @Nullable final EnumFacing facing) {
		return (IEnergyStorage) tile.getCapability(CapabilityEnergy.ENERGY, facing);
	}

	/**
	 * Get the IEnergyStorage capability of the specified facing of a TE, if it exists and can send/extract/transmit energy
	 * @param tile The TileEntity to look at - if this parameter is null, the result of the call will be null.
	 * @param facing The facing to check for the capability - this may be null, with results as noted for TileEntity.getCapability
	 * @return The TE's IEnergyStorage if it exists and can send power or null
	 */
	@Nullable
	public static IEnergyStorage getEnergyIfSendPossible(@Nullable final TileEntity tile, @Nullable final EnumFacing facing) {
		IEnergyStorage r = getEnergyCapability(tile, facing);
		if (r == null || !r.canExtract()) {
			return null;
		}

		return r;
	}

	/**
	 * Get the IEnergyStorage capability of the specified facing of a TE, if it exists and can receive energy
	 * @param tile The TileEntity to look at - if this parameter is null, the result of the call will be null.
	 * @param facing The facing to check for the capability - this may be null, with results as noted for TileEntity.getCapability
	 * @return The TE's IEnergyStorage if it exists and can receive power or null
	 */
	@Nullable
	public static IEnergyStorage getEnergyIfReceivePossible(TileEntity tile, EnumFacing facing) {
		IEnergyStorage r = getEnergyCapability(tile, facing);
		if (r == null || !r.canReceive()) {
			return null;
		}

		return r;		
	}

	/**
	 * Gets the IFluidHandler capability of a specified facing of a TE, if it exists.
	 * @param tile The TileEntity to get the Capability from - if this parameter is null, the result is null
	 * @param facing The facing to check for the capability - this may be null, with results as noted for TileEntity.getCapability
	 * @return the IFluidHandler capability of the TileEntity or null
	 */
	@Nullable
	public static IFluidHandler getFluidCapability(TileEntity tile, EnumFacing facing) {
		if (hasCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing)) {
			return getFluidNoChecks(tile, facing);
		}

		return null;
	}

	@Nullable
	private static IFluidHandler getFluidNoChecks(TileEntity tile, EnumFacing facing) {
		if (tile == null) {
			return null;
		} 

		return (IFluidHandler) tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
	}

	/**
	 * Get the IFluidHandler capability of the specified facing of a TE, if it exists and can be drained of a fluid
	 * @param tile The TileEntity to look at - if this parameter is null, the result of the call will be null.
	 * @param facing The facing to check for the capability - this may be null, with results as noted for TileEntity.getCapability
	 * @return The TE's IFluidHandler if it exists and can be drained or null
	 */
	@Nullable
	public static IFluidHandler getFluidIfSendPossible(TileEntity tile, EnumFacing facing) {
		IFluidHandler fh = getFluidCapability(tile, facing);
		if (fh == null || fh.drain(1000, false) == null ) {
			return null;
		} else if (fh.drain(1000, false).amount == 0) {
			return null;
		}

		return fh;
	}

	/**
	 * Get the IFluidHandler capability of the specified facing of a TE, if it exists and can store more of a fluid
	 * @param tile The TileEntity to look at - if this parameter is null, the result of the call will be null.
	 * @param facing The facing to check for the capability - this may be null, with results as noted for TileEntity.getCapability
	 * @return The TE's IFluidHandler if it exists and can store more or null
	 */
	@Nullable
	public static IFluidHandler getFluidIfReceivePossible(TileEntity tile, EnumFacing facing) {
		IFluidHandler fh = getFluidCapability(tile, facing);
		if (fh == null) {
			return null;
		} else {
			FluidStack fs = fh.drain(1, false);
			if (fs == null || fh.fill(fs, false) > 0) return fh;
		}

		return null;
	}

	/**
	 * Get the TileEntity of a block next to a given position
	 * @param worldIn The "World" where the check is to happen
	 * @param pos The position in the world of the block to check for a TE in a block touching one of its faces
	 * @param facing The face of the block to offset from to get the TE
	 * @return The found TileEntity or null
	 */
	@Nullable
	public static TileEntity getNeighboringTileEntity(World worldIn, BlockPos pos, EnumFacing facing) {
		return worldIn.getTileEntity(pos.offset(facing));
	}

	/**
	 * Possibly interact with the TE adjacent to the position of the current TileEntity
	 * @param source the TileEntity that is the "source" or "center" of the interaction
	 * @param offsetFacing which face to offset from
	 * @param maxAmount How much fluid to try and send
	 * @return the amount of fluid sent - 0 if it was not possible to send anything or a TE did not exist off that face
	 */
	@Nullable
	public static int doFluidSendInteractionByOffset(@Nonnull final TileEntity source, @Nullable final EnumFacing offsetFacing, final int maxAmount) {
		return doFluidSendInteraction(source, getNeighboringTileEntity(source.getWorld(), source.getPos(), offsetFacing), maxAmount, offsetFacing);
	}

	/**
	 * Does the specified TileEntity have the specified capability for the specified facing ?
	 * @param tile The TileEntity to check
	 * @param capability The Capability to check for
	 * @param facing The facing to check for the capability
	 * @return true if the capability exists, otherwise false
	 */
	@Nonnull
	public static boolean hasCapability(@Nonnull final TileEntity tile, @Nonnull final Capability<?> capability, @Nullable final EnumFacing facing) {
		return tile.hasCapability(capability, facing);
	}

	/**
	 * Does the specified TileEntity have fluid handling capabilities ?
	 * @param tile The TileEntity to check
	 * @param facing The facing to check for the capability
	 * @return true if the capability exists, otherwise false
	 */
	@Nonnull
	public static boolean hasFluidCapability(@Nonnull final TileEntity tile, @Nonnull final EnumFacing facing) {
		return hasCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
	}

	/**
	 * Does the specified TileEntity have energy handling capabilities ?
	 * @param tile The TileEntity to check
	 * @param facing The facing to check for the capability
	 * @return true if the capability exists, otherwise false
	 */
	public static boolean hasEnergyCapability(@Nonnull final TileEntity source, @Nonnull final EnumFacing facing) {
		return hasCapability(source, CapabilityEnergy.ENERGY, facing);
	}

	@Nonnull
	public static int doFluidSendInteraction(@Nullable final TileEntity source, @Nullable final TileEntity target, final int maxAmount, @Nonnull final EnumFacing sourceFacing) {
		if (source == null || target == null) {
			return 0;
		}

		if (hasFluidCapability(source, sourceFacing) && hasFluidCapability(target, sourceFacing.getOpposite())) {
			return doFluidSendInteractionInternal(source, target, maxAmount, sourceFacing);
		}

		return 0;
	}

	@Nonnull
	public static int doFluidSendInteractionInternal(@Nonnull final TileEntity source, @Nonnull final TileEntity target, final int maxAmount, @Nonnull final EnumFacing sourceFacing) {
		IFluidHandler sourceCap = getFluidIfReceivePossible(target, sourceFacing.getOpposite());
		IFluidHandler targetCap = getFluidIfSendPossible(source, sourceFacing);

		if ( sourceCap == null || targetCap == null ) {
			return 0;
		}

		return doFluidTransfer(sourceCap, targetCap, maxAmount);
	}

	private static int doFluidTransfer(@Nonnull final IFluidHandler source, @Nonnull final IFluidHandler target, final int maxAmount) {
		FluidStack possibleSend = target.drain(maxAmount, false);
		int possibleRecv = source.fill(possibleSend, false);
		if (possibleRecv > 0) {
			return source.fill(target.drain(possibleRecv, true), true);
		}

		return 0;
		
	}
	public static int doFluidGetInteractionByOffset(TileEntity source, EnumFacing offsetFacing, int maxAmount, EnumFacing sourceFacing) {
		return doFluidGetInteraction(source, getNeighboringTileEntity(source.getWorld(), source.getPos(), offsetFacing), maxAmount, sourceFacing);
	}

	public static int doFluidGetInteractionWithBlock(TileEntity source, BlockPos target, int maxAmount, EnumFacing sourceFacing) {
		return doFluidGetInteraction(source, source.getWorld().getTileEntity(target), maxAmount, sourceFacing);
	}

	public static int doFluidGetInteraction(TileEntity source, TileEntity target, int maxAmount, EnumFacing sourceFacing) {
		if (source == null || target == null) {
			return 0;
		}

		if (hasFluidCapability(source, sourceFacing) && hasFluidCapability(target, sourceFacing.getOpposite())) {
			return doFluidGetInteractionInternal(source, target, maxAmount, sourceFacing);
		}

		return 0;
	}

	private static int doFluidGetInteractionInternal(TileEntity source, TileEntity target, int maxAmount, EnumFacing sourceFacing) {
		IFluidHandler sourceCap = getFluidIfReceivePossible(source, sourceFacing);
		IFluidHandler targetCap = getFluidIfSendPossible(target, sourceFacing.getOpposite());

		if ( sourceCap == null || targetCap == null ) {
			return 0;
		}

		return doFluidTransfer(targetCap, sourceCap, maxAmount);
	}

	public static int doPowerSendInteractionByOffset(TileEntity source, EnumFacing offsetFacing, int maxAmount, EnumFacing sourceFacing) {
		return doPowerSendInteraction(source, getNeighboringTileEntity(source.getWorld(), source.getPos(), offsetFacing), maxAmount, sourceFacing);
	}

	public static int doPowerSendInteractionWithBlock(TileEntity source, BlockPos target, int maxAmount, EnumFacing sourceFacing) {
		return doPowerSendInteraction(source, source.getWorld().getTileEntity(target), maxAmount, sourceFacing);
	}

	public static int doPowerSendInteraction(TileEntity source, TileEntity target, int maxAmount, EnumFacing sourceFacing) {
		if (source == null || target == null) {
			return 0;
		}

		if (hasEnergyCapability(source, sourceFacing) && hasEnergyCapability(target, sourceFacing.getOpposite())) {
			return doPowerSendInteractionInternal(source, target, maxAmount, sourceFacing);
		}

		return 0;
	}

	private static int powerTransfer(IEnergyStorage source, IEnergyStorage target, int maxAmount) {
		int canSend = source.extractEnergy(maxAmount, true);
		int canTake = target.receiveEnergy(canSend, true);

		if (canSend > 0 && canTake > 0) {
			return source.extractEnergy(target.receiveEnergy(canTake > canSend ? canSend : canTake, false), false);
		}
		
		return 0;
	}

	private static int doPowerSendInteractionInternal(TileEntity source, TileEntity target, int maxAmount, EnumFacing sourceFacing) {
		IEnergyStorage sourceCap = getEnergyIfSendPossible(source, sourceFacing);
		IEnergyStorage targetCap = getEnergyIfReceivePossible(target, sourceFacing.getOpposite());

		if (sourceCap == null || targetCap == null) {
			return 0;
		}

		return powerTransfer(sourceCap, targetCap, maxAmount);
	}

	public static int doPowerGetInteractionByOffset(TileEntity source, EnumFacing offsetFacing, int maxAmount, EnumFacing sourceFacing) {
		return doPowerGetInteraction(source, getNeighboringTileEntity(source.getWorld(), source.getPos(), offsetFacing), maxAmount, sourceFacing);
	}

	public static int doPowerGetInteractionWithBlock(TileEntity source, BlockPos target, int maxAmount, EnumFacing sourceFacing) {
		return doPowerGetInteraction(source, source.getWorld().getTileEntity(target), maxAmount, sourceFacing);
	}

	public static int doPowerGetInteraction(TileEntity source, TileEntity target, int maxAmount, EnumFacing sourceFacing) {
		if (source == null || target == null) return 0;

		if (hasEnergyCapability(source, sourceFacing) && hasEnergyCapability(target, sourceFacing.getOpposite())) {
			return doPowerGetInteractionInternal(source, target, maxAmount, sourceFacing);
		}

		return 0;
	}

	private static int doPowerGetInteractionInternal(TileEntity source, TileEntity target, int maxAmount, EnumFacing sourceFacing) {
		IEnergyStorage sourceCap = getEnergyIfSendPossible(target, sourceFacing.getOpposite());
		IEnergyStorage targetCap = getEnergyIfReceivePossible(source, sourceFacing);

		if (sourceCap == null || targetCap == null) {
			return 0;
		}

		return powerTransfer(sourceCap, targetCap, maxAmount);
	}

}
