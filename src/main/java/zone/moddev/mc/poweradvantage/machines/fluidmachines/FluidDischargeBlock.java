package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.simple.BlockSimpleFluidMachine;
import cyano.poweradvantage.api.simple.TileEntitySimplePowerMachine;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class FluidDischargeBlock extends BlockSimpleFluidMachine {

	public FluidDischargeBlock() {
		super(Material.PISTON, 3f);
	}

	@Override
	public PoweredEntity newBlockEntity(IBlockReader w) {
		return new FluidDischargeTileEntity();
	}

	/**
	 * Destroys the TileEntity associated with this block when this block
	 * breaks.
	 */
	@Override
	public void onRemove(BlockState state, World world, BlockPos coord, BlockState newState, boolean isMoving) {
		if (state.getBlock() != newState.getBlock()) {
			final TileEntity tileEntity = world.getBlockEntity(coord);
			if (tileEntity instanceof TileEntitySimplePowerMachine) {
				InventoryHelper.dropContents(world, coord, (IInventory) tileEntity);
				world .updateNeighbourForOutputSignal(coord, this);
			}
		}
		super.onRemove(state, world, coord, newState, isMoving);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, World world, BlockPos coord) {
		TileEntity te = world.getBlockEntity(coord);
		if (te != null && te instanceof FluidDischargeTileEntity) {
			return ((FluidDischargeTileEntity) te).getRedstoneOutput();
		}
		return 0;
	}

	/**
	 * Determines whether this block/entity should receive energy. If this is not a sink, then it
	 * will never be given power by a power source.
	 *
	 * @param powerType Type of power
	 * @return true if this block/entity should receive energy
	 */
	@Override
	public boolean isPowerSink(ConduitType powerType) {
		return true;
	}

	/**
	 * Determines whether this block/entity can provide energy.
	 *
	 * @param powerType Type of power
	 * @return true if this block/entity can provide energy
	 */
	@Override
	public boolean isPowerSource(ConduitType powerType) {
		return false;
	}
}
