package zone.moddev.mc.poweradvantage.items;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUseContext;

public class RotationTool extends net.minecraft.item.Item {

	public RotationTool(Item.Properties properties) {
		super(properties);
	}

	@Override
	public EnumActionResult onItemUse(ItemUseContext context) {
		final EntityPlayer player = context.getPlayer();
		final World w = context.getWorld();
		final BlockPos coord = context.getPos();
		final EnumFacing facing = context.getFace();
		IBlockState bs = w.getBlockState(coord);
		/*  // comment this line to turn the wrench into a hacker tool
		try { // hacker info
			FMLLog.info("%s",new StringBuilder()
					.append("Hacker output:\n")
					.append("\nBlockstate = ").append(block)
					.append("\n\nBlock class dump:\n").append(superDump(block.getBlock()))
					.append("\n\nTileEntity class dump:\n")
					.append(w.getTileEntity(coord) == null ? "null" : superDump(w.getTileEntity(coord)))
			.toString());
		} catch (IllegalAccessException e) {
			FMLLog.log(Level.WARN,e,"Hacker fail!");
		}
		//*/

		IBlockState rotated = bs.rotate(w, coord, Rotation.COUNTERCLOCKWISE_90);
		if (rotated != bs) {
			w.setBlockState(coord, rotated);
			return EnumActionResult.SUCCESS;
		}


		return EnumActionResult.PASS;
	}
}
