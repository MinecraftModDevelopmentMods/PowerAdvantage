package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.simple.BlockSimpleFluidMachine;
import net.minecraft.block.material.Material;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
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
	protected void fillStateContainer(StateContainer.Builder<net.minecraft.block.Block, IBlockState> builder) {
		super.fillStateContainer(builder);
		builder.add(ACTIVE);
	}

	/**
	 * Converts metadata into blockstate
	 */
	@Override
	public IBlockState getStateFromMeta(final int metaValue) {
		EnumFacing enumFacing = metaToFacing(metaValue);
		if (enumFacing.getAxis() == EnumFacing.Axis.Y) {
			enumFacing = EnumFacing.NORTH;
		}
		return this.getDefaultState().with(FACING, enumFacing)
				.with(ACTIVE, (metaValue & 0x4) != 0);
	}

	/**
	 * Converts blockstate into metadata
	 */
	@Override
	public int getMetaFromState(final IBlockState bs) {
		int extraBit;
		if ((Boolean) (bs .get(ACTIVE))) {
			extraBit = 0x4;
		} else {
			extraBit = 0;
		}
		return facingToMeta((EnumFacing) bs .get(FACING)) | extraBit;
	}

	private int facingToMeta(EnumFacing f) {
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

	private EnumFacing metaToFacing(int i) {
		int f = i & 0x03;
		switch (f) {
			case 0:
				return EnumFacing.NORTH;
			case 1:
				return EnumFacing.WEST;
			case 2:
				return EnumFacing.SOUTH;
			case 3:
				return EnumFacing.EAST;
			default:
				return EnumFacing.NORTH;
		}
	}

	@Override
	public int getLightValue(IBlockState bs, IWorldReader world, BlockPos coord) {
		return bs.get(ACTIVE) ? 12 : 0;
	}

	@Override
	public PoweredEntity createNewTileEntity(IBlockReader world) {
		return new StillTileEntity();
	}

	@Override
	public boolean hasComparatorInputOverride(IBlockState bs) {
		return true;
	}

	@Override
	public int getComparatorInputOverride(IBlockState bs, World world, BlockPos coord) {
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
