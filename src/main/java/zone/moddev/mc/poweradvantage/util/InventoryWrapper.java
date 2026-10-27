package zone.moddev.mc.poweradvantage.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.INameable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** Gives ordinary inventories the sided interface used by conveyors. */
public class InventoryWrapper implements ISidedInventory, INameable {
    private static final int[] NO_SLOTS = new int[0];
    private static final InventoryWrapper NULL_INVENTORY = new InventoryWrapper();
    private final IInventory inventory;
    private final int[] slots;

    protected InventoryWrapper() { this(new Inventory(0)); }

    protected InventoryWrapper(IInventory inventory) {
        this.inventory = inventory;
        slots = new int[inventory.getContainerSize()];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
    }

    public static ISidedInventory wrap(IInventory inventory) {
        if (inventory == null) return NULL_INVENTORY;
        return inventory instanceof ISidedInventory ? (ISidedInventory) inventory : new InventoryWrapper(inventory);
    }

    public boolean isLocked() { return InventoryLocks.isLocked(inventory); }
    @Override public ITextComponent getName() {
        return inventory instanceof INameable ? ((INameable) inventory).getName() : new StringTextComponent("Inventory");
    }
    @Override public ITextComponent getCustomName() {
        return inventory instanceof INameable ? ((INameable) inventory).getCustomName() : null;
    }
    @Override public void clearContent() { inventory.clearContent(); }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public int getContainerSize() { return inventory.getContainerSize(); }
    @Override public int getMaxStackSize() { return inventory.getMaxStackSize(); }
    @Override public ItemStack getItem(int slot) { return inventory.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return inventory.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return inventory.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return inventory.canPlaceItem(slot, stack); }
    @Override public boolean stillValid(PlayerEntity player) {
        return slots.length != 0 && inventory.stillValid(player);
    }
    @Override public void startOpen(PlayerEntity player) { inventory.startOpen(player); }
    @Override public void stopOpen(PlayerEntity player) { inventory.stopOpen(player); }
    @Override public void setChanged() { inventory.setChanged(); }
    @Override public int[] getSlotsForFace(Direction face) { return isLocked() ? NO_SLOTS : slots; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
        return !isLocked() && inventory.canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return !isLocked(); }
}
