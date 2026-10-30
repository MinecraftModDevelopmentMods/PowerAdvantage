package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.item.crafting.IRecipeType;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntitySmeltableFilter extends TileEntityConveyorFilter {
	public TileEntitySmeltableFilter() { super(Entities.FILTER_SMELT, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null || item.isEmpty() || level == null) return false;
		Inventory probe = new Inventory(1);
		probe.setItem(0, item.copy());
		return level.getRecipeManager().getRecipeFor(IRecipeType.SMELTING, probe, level).isPresent();
	}

}
