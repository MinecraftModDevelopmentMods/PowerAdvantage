package zone.moddev.mc.poweradvantage.items;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
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
	public ActionResultType useOn(ItemUseContext context) {
		final PlayerEntity player = context.getPlayer();
		final World w = context.getLevel();
		final BlockPos coord = context.getClickedPos();
		final Direction facing = context.getClickedFace();
		BlockState bs = w.getBlockState(coord);
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

		BlockState rotated = bs.rotate(w, coord, Rotation.COUNTERCLOCKWISE_90);
		if (rotated != bs) {
			w.setBlock(coord, rotated, 3);
			return ActionResultType.SUCCESS;
		}


		return ActionResultType.PASS;
	}
}
