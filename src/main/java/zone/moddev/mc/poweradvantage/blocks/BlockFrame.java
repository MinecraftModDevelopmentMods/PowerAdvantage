package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IWorldReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * This class is for blocks that should act like glass (renderring-wise), but can be harvested 
 * normally.
 * @author DrCyano
 *
 */
public class BlockFrame extends Block {

	public BlockFrame(Material material, float hardness, float resistance) {
		super(Block.Properties.create(material).sound(SoundType.METAL)
				.hardnessAndResistance(hardness, resistance).variableOpacity());
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public BlockRenderLayer getRenderLayer() {
		return BlockRenderLayer.CUTOUT;
	}

	@Override
	public boolean isFullCube(IBlockState bs) {
		return false;
	}

	@Override
	public boolean isSolid(IBlockState state) {
		return false;
	}

	@Override
	public boolean canSilkHarvest(IBlockState state, IWorldReader world, BlockPos pos, EntityPlayer player) {
		return true;
	}


	public boolean isOpaqueCube(IBlockState bs) {
		return false;
	}

}
