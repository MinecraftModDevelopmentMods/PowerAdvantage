package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.Tags;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityOreFilter extends TileEntityConveyorFilter {
	public TileEntityOreFilter() { super(Entities.FILTER_ORE, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null || item.isEmpty()) return false;
		return Tags.Items.INGOTS.contains(item.getItem())
				|| Tags.Items.ORES.contains(item.getItem())
				|| Tags.Items.DUSTS.contains(item.getItem())
				|| Tags.Items.NUGGETS.contains(item.getItem());
	}

}
