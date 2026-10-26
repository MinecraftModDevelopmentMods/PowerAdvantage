package zone.moddev.mc.poweradvantage.blocks;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.ITypedConduit;
import cyano.poweradvantage.api.PowerConnectorContext;
import zone.moddev.mc.poweradvantage.conduitnetwork.ConduitRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/**
 * This block class is a user-friendly power switch for turning power on and off. You do not need to 
 * extend this class, just specify the type of power being switched in the constructor.
 * @author DrCyano
 *
 */
public class BlockPowerSwitch extends Block implements ITypedConduit {
	/**
	 * Blockstate peroperty. If true, conduct power, if false, don't
	 */
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	private final ConduitType[] powerType;

	/**
	 * Constructor for making a power switch
	 *
	 * @param powerType     Type of power to switch
	 * @param blockMaterial The block material (usually piston material)
	 * @param blockHardness The hardness of the block (0.75 is a good value)
	 * @param mapColor      Color on a map
	 */
	public BlockPowerSwitch(ConduitType powerType, Material blockMaterial, float blockHardness) {
		super(Block.Properties.create(blockMaterial).hardnessAndResistance(blockHardness));
		this.powerType = new ConduitType[1];
		this.powerType[0] = powerType;
		this.setDefaultState(getDefaultState().with(ACTIVE, false));
	}

	/**
	 * Constructor for making a power switch
	 *
	 * @param powerType Type of power to switch
	 */
	public BlockPowerSwitch(ConduitType powerType) {
		this(powerType, Material.PISTON, 0.75f);
	}

	/**
	 * Creates a blockstate
	 */
	@Override
	protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	/**
	 * Converts metadata into blockstate
	 */
	public BlockState getStateFromMeta(final int metaValue) {
		return this.getDefaultState()
				.with(ACTIVE, metaValue > 0);
	}

	/**
	 * Converts blockstate into metadata
	 */
	public int getMetaFromState(final BlockState bs) {
		if ((Boolean) (bs .get(ACTIVE))) {
			return 1;
		} else {
			return 0;
		}
	}

	/**
	 * Determines whether this conduit is compatible with an adjacent one
	 *
	 * @param connection A context object that provides the power type and block direction information
	 * @return True if power should be allowed to flow through this connection, false otherwise
	 */
	@Override
	public boolean canAcceptConnection(PowerConnectorContext connection) {
		return (Boolean) connection.thisBlock .get(ACTIVE) && ConduitType.areSameType(powerType[0], connection.powerType);
	}

	@Override
	public ConduitType[] getTypes() {
		return powerType;
	}

	@Override
	public boolean isPowerSink(ConduitType powerType) {
		return false;
	}

	@Override
	public boolean isPowerSource(ConduitType powerType) {
		return false;
	}

	/**
	 * Used to determine ambient occlusion and culling when rebuilding chunks for render
	 */
	public boolean isOpaqueCube(BlockState bs) {
		return true;
	}

	public boolean isFullCube(BlockState bs) {
		return true;
	}

	/**
	 * Override of default block behavior
	 */
	@Override
	public void onBlockAdded(final BlockState state, final World world, final BlockPos coord, final BlockState oldState, final boolean isMoving) {
		super.onBlockAdded(state, world, coord, oldState, isMoving);
		ConduitRegistry.getInstance().conduitBlockPlacedEvent(world, world.dimension.getType().getId(), coord, getTypes());
	}

	/**
	 * This method is called when the block is removed from the world by an entity.
	 */
	@Override
	public void onReplaced(BlockState state, World w, BlockPos coord, BlockState newState, boolean isMoving) {
		if (state.getBlock() != newState.getBlock()) {
			ConduitRegistry.getInstance().conduitBlockRemovedEvent(w, w.dimension.getType().getId(), coord, getTypes());
		}
		super.onReplaced(state, w, coord, newState, isMoving);
	}

	/**
	 * This method is called when the block is destroyed by an explosion.
	 */
	@Override
	public void onExplosionDestroy(World w, BlockPos coord, Explosion boom) {
		super.onExplosionDestroy(w, coord, boom);
		ConduitRegistry.getInstance().conduitBlockRemovedEvent(w, w.dimension.getType().getId(), coord, getTypes());
	}

	/**
	 * Called when a neighboring block changes.
	 */
	public void onNeighborChange(IBlockReader w, BlockPos pos, BlockPos neighbor) {
		if (w instanceof World) {
			World world = (World) w;
			BlockState state = world.getBlockState(pos);
			// redstone power given or taken
			boolean activated = !world.isBlockPowered(pos);
			world.setBlockState(pos, state.with(ACTIVE, activated), 2);
			if (activated) {
				ConduitRegistry.getInstance().conduitBlockPlacedEvent(world, world.dimension.getType().getId(), pos, getTypes());
			} else {
				ConduitRegistry.getInstance().conduitBlockRemovedEvent(world, world.dimension.getType().getId(), pos, getTypes());
			}
		}
	}

	@Override
	public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		onNeighborChange(world, pos, pos);
	}

}
