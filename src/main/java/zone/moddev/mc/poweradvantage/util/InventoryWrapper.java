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
        slots = new int[inventory.getSizeInventory()];
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
    @Override public void clear() { inventory.clear(); }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public int getSizeInventory() { return inventory.getSizeInventory(); }
    @Override public int getInventoryStackLimit() { return inventory.getInventoryStackLimit(); }
    @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
    @Override public ItemStack decrStackSize(int slot, int amount) { return inventory.decrStackSize(slot, amount); }
    @Override public ItemStack removeStackFromSlot(int slot) { return inventory.removeStackFromSlot(slot); }
    @Override public void setInventorySlotContents(int slot, ItemStack stack) { inventory.setInventorySlotContents(slot, stack); }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return inventory.isItemValidForSlot(slot, stack); }
    @Override public boolean isUsableByPlayer(PlayerEntity player) {
        return slots.length != 0 && inventory.isUsableByPlayer(player);
    }
    @Override public void openInventory(PlayerEntity player) { inventory.openInventory(player); }
    @Override public void closeInventory(PlayerEntity player) { inventory.closeInventory(player); }
    @Override public void markDirty() { inventory.markDirty(); }
    @Override public int[] getSlotsForFace(Direction face) { return isLocked() ? NO_SLOTS : slots; }
    @Override public boolean canInsertItem(int slot, ItemStack stack, Direction face) {
        return !isLocked() && inventory.isItemValidForSlot(slot, stack);
    }
    @Override public boolean canExtractItem(int slot, ItemStack stack, Direction face) { return !isLocked(); }
}
