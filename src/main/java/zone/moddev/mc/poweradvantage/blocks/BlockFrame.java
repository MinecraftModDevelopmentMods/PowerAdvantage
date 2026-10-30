package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
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
	public boolean isSolid(BlockState bs) {
		return false;
	}

	public boolean canSilkHarvest(BlockState state, IWorldReader world, BlockPos pos, PlayerEntity player) {
		return true;
	}


}
