package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.crafting.VanillaRecipeTypes;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntitySmeltableFilter extends TileEntityConveyorFilter {
	public TileEntitySmeltableFilter() { super(Entities.FILTER_SMELT, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null || item.isEmpty() || world == null) return false;
		InventoryBasic probe = new InventoryBasic(new TextComponentString("smelting probe"), 1);
		probe.setInventorySlotContents(0, item.copy());
		return world.getRecipeManager().getRecipe(probe, world, VanillaRecipeTypes.SMELTING) != null;
	}

}
