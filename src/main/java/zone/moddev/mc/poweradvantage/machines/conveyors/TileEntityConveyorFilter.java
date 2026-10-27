package zone.moddev.mc.poweradvantage.machines.conveyors;

import zone.moddev.mc.poweradvantage.util.InventoryWrapper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.HopperTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.init.Entities;

public abstract class TileEntityConveyorFilter extends TileEntityConveyor {

	public TileEntityConveyorFilter() {
		this(Entities.FILTER_BLOCK, 1);
	}

	protected TileEntityConveyorFilter(TileEntityType<?> type, int slots) {
		super(type, slots);
	}

	public abstract boolean matchesFilter(ItemStack item);

	@Override
	public void tick() {
		World w = getLevel();
		if (w.isClientSide) return;
		if (transferCooldown > 0) transferCooldown--;
		if (transferCooldown == 0) {
			// do tick update
			Direction dir = this.getFacing();
			if (getInventory()[0] == null) {
				// not holding item, get item
				Direction myDir = dir.getOpposite();
				Direction theirDir = dir;
				BlockPos targetPos = getBlockPos().relative(myDir);
				TileEntity target = w.getBlockEntity(targetPos);
				if (target != null) {
					if (target instanceof IInventory) {
						ISidedInventory them = InventoryWrapper.wrap(HopperTileEntity.getContainerAt(getLevel(), targetPos.getX(), targetPos.getY(), targetPos.getZ()));
						if (transferItem(them, theirDir, this, myDir)) {
							this.setChanged();
						}
					}
				}
			} else {
				Direction myDir = dir;
				Direction theirDir = dir.getOpposite();
				BlockPos targetPos = getBlockPos().relative(myDir);
				TileEntity target = w.getBlockEntity(targetPos);
				TileEntity dropTarget = w.getBlockEntity(getBlockPos().relative(Direction.DOWN));
				if (matchesFilter(this.getInventory()[0])) {
					if (isValidItemFor(getInventory()[0], dropTarget, Direction.UP)) {
						dropItem(dropTarget);
						return;
					}
				} else if (target instanceof IInventory) {
					ISidedInventory them = InventoryWrapper.wrap(HopperTileEntity.getContainerAt(getLevel(), targetPos.getX(), targetPos.getY(), targetPos.getZ()));
					if (transferItem(this, myDir, them, theirDir)) {
						this.setChanged();
					}
				}
			}

			transferCooldown = transferInvterval;
		}
	}

	protected boolean isValidItemFor(ItemStack item, TileEntity target, Direction side) {
		if (item == null) return false;
		if (target instanceof IInventory) {
			BlockPos targetPos = target.getBlockPos();
			ISidedInventory dt = InventoryWrapper.wrap(HopperTileEntity.getContainerAt(getLevel(), targetPos.getX(), targetPos.getY(), targetPos.getZ()));
			int[] slots = dt.getSlotsForFace(side);
			for (int i = 0; i < slots.length; i++) {
				int slot = slots[i];
				if (dt.canPlaceItemThroughFace(slot, item, side)) {
					return true;
				}
			}
			return false;
		} else {
			return true;
		}
	}

	private void dropItem(TileEntity target) {
		World w = getLevel();
		BlockState bs = w.getBlockState(getBlockPos().below());
		if (target == null && !(bs.getMaterial().blocksMotion())) {
			// drop item in the air
			ItemEntity ie = new ItemEntity(w, getBlockPos().getX() + 0.5, getBlockPos().getY() - 0.5, getBlockPos().getZ() + 0.5, getInventory()[0]);
			ie.setDeltaMovement(0, 0, 0);
			w .addFreshEntity(ie);
			getInventory()[0] = null;
			transferCooldown = transferInvterval;
			this.setChanged();
		} else if (target instanceof IInventory) {
			// add item to inventory
			Direction myDir = Direction.DOWN;
			Direction theirDir = Direction.UP;
			BlockPos targetPos = target.getBlockPos();
			ISidedInventory targetInv = InventoryWrapper.wrap(HopperTileEntity.getContainerAt(getLevel(), targetPos.getX(), targetPos.getY(), targetPos.getZ()));
			if (transferItem(this, myDir, targetInv, theirDir)) {
				transferCooldown = transferInvterval;
				this.setChanged();
			}
		}
	}

	/**
	 * Gets the direction that the blockstate is facing
	 *
	 * @return an EnumfFacing
	 */
	@Override
	public Direction getFacing() {
		BlockState state = getLevel().getBlockState(getBlockPos());
		if (state.hasProperty(BlockConveyorFilter.FACING)) {
			return state.getValue(BlockConveyorFilter.FACING);
		} else {
			PowerAdvantage.LOGGER.warn("{}.getFacing(): blockstate at {} does not have BlockConveyorFilter.FACING", this.getClass().getName(), getBlockPos());
			return Direction.NORTH;
		}
	}

	/**
	 * Determines whether another block (such as a Hopper) is allowed to pull an
	 * item from this block out through a given face
	 *
	 * @param slot       The inventory slot (index) of the item in question
	 * @param targetItem The item to be pulled
	 * @param side       The side of the block through which to pull the item
	 * @return true if the item is allowed to be pulled, false otherwise
	 */
	@Override
	public boolean canTakeItemThroughFace(final int slot, final ItemStack targetItem, final Direction side) {
		boolean filterMatch = matchesFilter(targetItem);
		if (slot != 0) return false;
		if (side == Direction.DOWN) {
			return filterMatch;
		} else {
			return (!filterMatch) && super.canTakeItemThroughFace(slot, targetItem, side);
		}
	}
}
