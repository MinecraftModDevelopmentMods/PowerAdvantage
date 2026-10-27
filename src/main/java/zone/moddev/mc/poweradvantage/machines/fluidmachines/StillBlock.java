package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.simple.BlockSimpleFluidMachine;
import net.minecraft.block.material.Material;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;

public class StillBlock extends BlockSimpleFluidMachine {


	/**
	 * Blockstate property
	 */
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public StillBlock() {
		super(Material.PISTON, 3f);
		this.setDefaultState(getDefaultState().with(ACTIVE, false));
	}

	/**
	 * Creates a blockstate
	 */
	@Override
	protected void fillStateContainer(StateContainer.Builder<net.minecraft.block.Block, BlockState> builder) {
		super.fillStateContainer(builder);
		builder.add(ACTIVE);
	}

	/**
	 * Converts metadata into blockstate
	 */
	@Override
	public BlockState getStateFromMeta(final int metaValue) {
		Direction enumFacing = metaToFacing(metaValue);
		if (enumFacing.getAxis() == Direction.Axis.Y) {
			enumFacing = Direction.NORTH;
		}
		return this.getDefaultState().with(FACING, enumFacing)
				.with(ACTIVE, (metaValue & 0x4) != 0);
	}

	/**
	 * Converts blockstate into metadata
	 */
	@Override
	public int getMetaFromState(final BlockState bs) {
		int extraBit;
		if ((Boolean) (bs .get(ACTIVE))) {
			extraBit = 0x4;
		} else {
			extraBit = 0;
		}
		return facingToMeta((Direction) bs .get(FACING)) | extraBit;
	}

	private int facingToMeta(Direction f) {
		switch (f) {
			case NORTH:
				return 0;
			case WEST:
				return 1;
			case SOUTH:
				return 2;
			case EAST:
				return 3;
			default:
				return 0;
		}
	}

	private Direction metaToFacing(int i) {
		int f = i & 0x03;
		switch (f) {
			case 0:
				return Direction.NORTH;
			case 1:
				return Direction.WEST;
			case 2:
				return Direction.SOUTH;
			case 3:
				return Direction.EAST;
			default:
				return Direction.NORTH;
		}
	}

	@Override
	public int getLightValue(BlockState bs, net.minecraft.world.IBlockReader world, BlockPos coord) {
		return bs.get(ACTIVE) ? 12 : 0;
	}

	@Override
	public PoweredEntity createNewTileEntity(IBlockReader world) {
		return new StillTileEntity();
	}

	@Override
	public boolean hasComparatorInputOverride(BlockState bs) {
		return true;
	}

	@Override
	public int getComparatorInputOverride(BlockState bs, World world, BlockPos coord) {
		TileEntity te = world.getTileEntity(coord);
		if (te instanceof StillTileEntity) {
			return ((StillTileEntity) te).getRedstoneOutput();
		}
		return 0;
	}

	/**
	 * Determines whether this block/entity should receive energy
	 *
	 * @param powerType Type of power
	 * @return true if this block/entity should receive energy
	 */
	@Override
	public boolean isPowerSink(ConduitType powerType) {
		return true;
	}

	/**
	 * Determines whether this block/entity can provide energy
	 *
	 * @param powerType Type of power
	 * @return true if this block/entity can provide energy
	 */
	@Override
	public boolean isPowerSource(ConduitType powerType) {
		return true;
	}

}
