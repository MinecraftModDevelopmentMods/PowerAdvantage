package cyano.poweradvantage.api;

import zone.moddev.mc.poweradvantage.conduitnetwork.ConduitRegistry;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * 
 * This is the superclass for blocks that conduct power from power generators to 
 * the machines that need power. If you are making an add-on mod, you probably 
 * want to extend the 
 * <b>cyano.poweradvantage.api.simple.BlockSimplePowerConduit</b> class 
 * instead of this class.
 * <br><br>
 * Note that the conduit blocks do not have tile entities. They do not store data for energy 
 * transmission. Simply implementing the ITypedConduit interface adds them to the energy 
 * transmission algorithm that finds the connections between generators and machines. 
 * 
 * 
 * @author DrCyano
 *
 */
public abstract class ConduitBlock extends net.minecraft.block.Block implements  ITypedConduit {
	/**
	 * Block constructor
	 *
	 * @param mat Block material, typically Material.iron
	 */
	protected ConduitBlock(Material mat) {
		this(mat, 1.0F);
	}

	protected ConduitBlock(Material mat, float hardness) {
		this(Properties.create(mat).hardnessAndResistance(hardness));
	}

	protected ConduitBlock(Properties properties) {
		super(properties);
	}

	/**
	 * This method is called whenever the block is placed into the world
	 */
	@Override
	public void onBlockAdded(BlockState state, World w, BlockPos coord, BlockState oldState, boolean isMoving) {
		super.onBlockAdded(state, w, coord, oldState, isMoving);
		ConduitRegistry.getInstance().conduitBlockPlacedEvent(w, w.dimension.getType().getId(), coord, getTypes());
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

}
