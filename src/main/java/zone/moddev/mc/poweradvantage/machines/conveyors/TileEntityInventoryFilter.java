package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.item.ItemStack;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityInventoryFilter extends TileEntityConveyorFilter {


	public static final int NUM_FILTER_SLOTS = 9;

	public TileEntityInventoryFilter() {
		super(Entities.FILTER_INVENTORY, NUM_FILTER_SLOTS + 1);
	}

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null) return false;
		for (int i = 1; i < this.getInventory().length; i++) {
			if (this.getInventory()[i] != null && ItemStack.isSame(this.getInventory()[i], item)) {
				return true;
			}
		}
		return false;
	}

}
