package cyano.poweradvantage.api;

import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

/**
 * This data structure class is used to reduce the parameter list of the ITypedConduit methods to a single pointer.
 * @author DrCyano
 */
public class PowerConnectorContext {

	/**
	 * World instance
	 */
	public final IBlockReader world;
	/**
	 * Type of power
	 */
	public final ConduitType powerType;
	/**
	 * Block that is asking for the connection
	 */
	public final BlockState thisBlock;
	/**
	 * Position of the block that is asking for the connection
	 */
	public final BlockPos thisBlockPosition;
	/**
	 * Face on the block that is asking for the connection
	 */
	public final Direction thisBlockSide;
	/**
	 * Block that is being asked for the connection
	 */
	public final BlockState otherBlock;
	/**
	 * Position of the block that is being asked for the connection
	 */
	public final BlockPos otherBlockPosition;
	/**
	 * Face on the block that is being asked for the connection
	 */
	public final Direction otherBlockSide;

	/**
	 * Constructs a data structure instance
	 *
	 * @param powerType         Power type (e.g. "steam")
	 * @param world             The world instance that can access this block
	 * @param connectorBlock    The block that is testing its neighbors
	 * @param connectorPosition The position of the block that is testing its neighbors
	 * @param connectorSide     The side of the block that is testing its neighbors
	 * @param connecteeBlock    The neighboring block being tested
	 * @param connecteePosition The neighboring block's position
	 * @param connecteeSide     The neighboring block's side
	 */
	public PowerConnectorContext(
			final ConduitType powerType,
			final IBlockReader world,
			final BlockState connectorBlock,
			final BlockPos connectorPosition,
			final Direction connectorSide,
			final BlockState connecteeBlock,
			final BlockPos connecteePosition,
			final Direction connecteeSide
	) {
		this.world = world;
		this.powerType = powerType;
		this.thisBlock = connectorBlock;
		this.thisBlockPosition = connectorPosition;
		this.thisBlockSide = connectorSide;
		this.otherBlock = connecteeBlock;
		this.otherBlockPosition = connecteePosition;
		this.otherBlockSide = connecteeSide;
	}

	public PowerConnectorContext(
			final ConduitType powerType,
			final IBlockReader world,
			final BlockState connectorBlock,
			final BlockPos connectorPosition,
			final Direction connectorSide
	) {
		this.world = world;
		this.powerType = powerType;
		this.thisBlock = connectorBlock;
		this.thisBlockPosition = connectorPosition;
		this.thisBlockSide = connectorSide;
		this.otherBlockPosition = connectorPosition.relative(connectorSide);
		this.otherBlockSide = connectorSide.getOpposite();
		this.otherBlock = world.getBlockState(otherBlockPosition);
	}

	/**
	 * Swaps the connector and connectee blocks. This is the same connection view from the opposite direction
	 *
	 * @return A reversed instance of this PowerConnectorContext
	 */
	public final PowerConnectorContext reverse() {
		return new PowerConnectorContext(powerType, world, otherBlock, otherBlockPosition, otherBlockSide, thisBlock, thisBlockPosition, thisBlockSide);
	}
}
