package com.mcmoddev.poweradvantage.machines.conveyors;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

public class TileEntitySmeltableFilter extends TileEntityConveyorFilter {

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null || item.isEmpty()) return false;
		ItemStack output = FurnaceRecipes.instance().getSmeltingResult(item);
		return output != null && !output.isEmpty();
	}

}
