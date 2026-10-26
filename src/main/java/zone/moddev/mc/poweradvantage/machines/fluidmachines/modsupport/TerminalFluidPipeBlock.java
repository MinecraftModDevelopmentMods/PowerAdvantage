package zone.moddev.mc.poweradvantage.machines.fluidmachines.modsupport;

import cyano.poweradvantage.api.ConduitType;
import zone.moddev.mc.poweradvantage.init.Blocks;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.util.FluidHandlerHelper;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

@SuppressWarnings("deprecation")
public class TerminalFluidPipeBlock extends FluidPipeBlock implements ITileEntityProvider {

	@Override
	public TileEntity createNewTileEntity(IBlockReader worldIn) {
		return new TerminalFluidPipeTileEntity();
	}


	@Override
	public boolean isPowerSink(ConduitType powerType) {
		return true;
	}

	@Override
	public boolean isPowerSource(ConduitType powerType) {
		return true;
	}

	/**
	 * Called when a neighboring block changes.
	 */
	@Override
	public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		pipeCheck(worldIn, pos);
	}

	/**
	 * Called by ItemBlocks after a block is set in the world, to allow post-place logic
	 */
	public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
		pipeCheck(worldIn, pos);
	}

	private void pipeCheck(World w, BlockPos pos) {
		if (!w.isRemote) {
			if (numberOfAdjacentFluidHandlers(w, pos) == 0) {
				w.setBlockState(pos, Blocks.fluid_pipe.getDefaultState());
			}
		}
	}

	/**
	 * This method determines whether to connect to a neighboring block.
	 * Override this method to change block connection behavior.
	 *
	 * @param w          World instance
	 * @param thisBlock  The block that is checking its neighbor
	 * @param bs         Block state of this block
	 * @param face       The face on the first block through which the connection would happen
	 * @param otherBlock Coordinate of neighboring block
	 * @return Default implementation: true if the neighboring block implements
	 * ITypedConductor and has the same energy type as this block. Overriding
	 * the canConnectTo(ConductorType) method will change the results of this
	 * method.
	 */
	@Override
	protected boolean canConnectTo(IBlockReader w, BlockPos thisBlock, BlockState bs, Direction face) {
		BlockPos otherBlock = thisBlock.offset(face);
		return super.canConnectTo(w, thisBlock, bs, face)
				|| FluidHandlerHelper.hasHandler(w.getTileEntity(otherBlock), face.getOpposite());
	}

}
