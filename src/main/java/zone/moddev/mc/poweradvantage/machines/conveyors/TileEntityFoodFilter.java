package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.item.ItemStack;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityFoodFilter extends TileEntityConveyorFilter {
	public TileEntityFoodFilter() { super(Entities.FILTER_FOOD, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null) return false;
		return !item.isEmpty() && item.getItem().isEdible();
	}

}
