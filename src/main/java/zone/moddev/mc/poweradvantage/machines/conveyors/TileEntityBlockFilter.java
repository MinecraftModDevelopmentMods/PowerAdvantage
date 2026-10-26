package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityBlockFilter extends TileEntityConveyorFilter {
	public TileEntityBlockFilter() { super(Entities.FILTER_BLOCK, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null) return false;
		return item.getItem() instanceof BlockItem;
	}

}
