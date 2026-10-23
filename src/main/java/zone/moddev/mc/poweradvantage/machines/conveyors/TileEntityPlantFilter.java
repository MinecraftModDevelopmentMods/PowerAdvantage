package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.IPlantable;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityPlantFilter extends TileEntityConveyorFilter {
	public TileEntityPlantFilter() { super(Entities.FILTER_PLANT, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null) return false;
		if (item.getItem() instanceof IPlantable) return true;
		if (item.getItem() instanceof ItemBlock) {
			Block b = ((ItemBlock) item.getItem()).getBlock();
			if (b instanceof IPlantable) return true;
			if (b instanceof IGrowable) return true;

		}
		return false;
	}

}
