package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import cyano.poweradvantage.api.simple.BlockSimpleFluidConduit;
import zone.moddev.mc.poweradvantage.init.Blocks;
import zone.moddev.mc.poweradvantage.util.FluidHandlerHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("deprecation")
public class FluidPipeBlock extends BlockSimpleFluidConduit {

	public FluidPipeBlock() {
		super(Material.PISTON, 0.75f, 4f / 16f);
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
			if (numberOfAdjacentFluidHandlers(w, pos) > 0) {
				w.setBlockState(pos, Blocks.fluid_pipe_terminal.getDefaultState());
			}
		}
	}


	protected static int numberOfAdjacentFluidHandlers(World w, BlockPos pos) {
		int sum = 0;
		for (Direction f : Direction.values()) {
			TileEntity e = w.getTileEntity(pos.offset(f));
			if (FluidHandlerHelper.hasHandler(e, f.getOpposite()) && !(e instanceof cyano.poweradvantage.api.PoweredEntity)) {
				sum++;
			}
		}
		return sum;
	}
}
