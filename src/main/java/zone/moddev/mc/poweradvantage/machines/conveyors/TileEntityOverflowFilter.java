package zone.moddev.mc.poweradvantage.machines.conveyors;

import zone.moddev.mc.poweradvantage.util.InventoryWrapper;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityOverflowFilter extends TileEntityConveyorFilter {
	public TileEntityOverflowFilter() { super(Entities.FILTER_OVERFLOW, 1); }

	@Override
	public boolean matchesFilter(ItemStack item) {
		if (item == null || item.getItem() == null) return false;
		boolean canDrop = canInsert(item, Direction.DOWN);
		boolean canPass = canInsert(item, this.getFacing());
		if (canPass) {
			return canDrop;
		} else {
			return true;
		}
	}

	private boolean canInsert(ItemStack item, Direction f) {
		TileEntity target = getWorld().getTileEntity(getPos().offset(f));
		if (target instanceof IInventory) {
			Direction side = f.getOpposite();
			ISidedInventory dt = InventoryWrapper.wrap((IInventory) target);
			return canInsertItemInto(item, dt, side);
		} else {
			return false;
		}
	}

	@Override
	protected boolean isValidItemFor(ItemStack item, TileEntity target, Direction side) {
		// do all of the logic in the matchesFilter method
		return true;
	}

}
