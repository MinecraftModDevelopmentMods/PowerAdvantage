package zone.moddev.mc.poweradvantage.machines.conveyors;

import zone.moddev.mc.poweradvantage.util.InventoryWrapper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.HopperTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import zone.moddev.mc.poweradvantage.init.Entities;

public class TileEntityConveyor extends TileEntity implements ITickableTileEntity, ISidedInventory, net.minecraft.util.INameable {
    private final zone.moddev.mc.poweradvantage.util.MachineCapabilities machineCapabilities =
            new zone.moddev.mc.poweradvantage.util.MachineCapabilities(this);

    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> capability, Direction face) {
        net.minecraftforge.common.util.LazyOptional<T> local = machineCapabilities.get(capability, face);
        return local.isPresent() ? local : super.getCapability(capability, face);
    }

    @Override protected void invalidateCaps() {
        super.invalidateCaps();
        machineCapabilities.invalidate();
    }

    @Override public void validate() {
        super.validate();
        reviveCaps();
    }
	
	public static String unlocalizedName = TileEntityConveyorFilter.class.getName();
	
	protected static final Random globalRand = new Random();
	
	private final ItemStack[] inventory;

	protected int transferCooldown = 0;
	protected final int transferInvterval = 7;
	
	
	public TileEntityConveyor(){
		this(Entities.CONVEYOR, 1);
	}
	
	protected TileEntityConveyor(TileEntityType<?> type, int inventorySize){
		super(type);
		inventory = new ItemStack[inventorySize];
	}
	
	
	@Override
	public void tick() {
		World w = getWorld();
		if(w.isRemote) return;
		if(transferCooldown > 0) transferCooldown--;
		if(transferCooldown == 0){
			// do tick update
			Direction dir =this.getFacing();
			
			// deposit item
			Direction myDir = dir;
			Direction theirDir = dir.getOpposite();
			BlockPos targetPos = getPos().offset(myDir);
			TileEntity target = w.getTileEntity(targetPos);
			if(target != null) {
				if( target instanceof IInventory){
					ISidedInventory them = InventoryWrapper.wrap(HopperTileEntity.getInventoryAtPosition(getWorld(),targetPos.getX(), targetPos.getY(), targetPos.getZ()));

					if(transferItem(this,myDir,them,theirDir)){
						transferCooldown = transferInvterval;
						this.markDirty();
					}
				}
			}
			
			// get item
			myDir = dir.getOpposite();
			theirDir = dir;
			BlockPos upstreamBlock = getPos().offset(myDir);
			if(w.isAirBlock(upstreamBlock)){
				// grab items sitting on the ground behind the conveyor
				List<ItemEntity> list = w.getEntitiesWithinAABB(ItemEntity.class, new AxisAlignedBB(
						upstreamBlock.getX(), upstreamBlock.getY(), upstreamBlock.getZ(), 
						upstreamBlock.getX()+1, upstreamBlock.getY()+1, upstreamBlock.getZ()+1));
				if(!list.isEmpty()){
					ItemEntity e = list.get(0);
					if(!e.isAirBorne){
						ItemStack entityStack = e.getItem();
						if(canInsertItemInto(entityStack,this,myDir)){
							ItemStack newItem = entityStack.copy();
							newItem.setCount(1);
							entityStack.shrink(1);
							if(getInventory()[0] == null){
								getInventory()[0] = newItem;
							} else {
								getInventory()[0].grow(1);
							}
							if(entityStack.isEmpty()){
								e.remove();
							} else {
								e.setItem(entityStack);
							}
							this.markDirty();
						}
					}
				}
			} else {
				target = w.getTileEntity(upstreamBlock);
				if(target != null){
					if( target instanceof IInventory){
						ISidedInventory them = InventoryWrapper.wrap(HopperTileEntity.getInventoryAtPosition(getWorld(),upstreamBlock.getX(), upstreamBlock.getY(), upstreamBlock.getZ()));
						if(transferItem(them,theirDir,this,myDir)){
							this.markDirty();
						}
					}
				}
			}
			transferCooldown = transferInvterval;
		}
	}
	
	protected static boolean isLocked(Object o){
		return zone.moddev.mc.poweradvantage.util.InventoryLocks.isLocked(o);
	}

	
	protected static boolean canInsertItemInto(ItemStack item, ISidedInventory dest, Direction destFace){
		if(item == null || item.isEmpty() || item.getItem() == null || isLocked(dest)){
			return false;
		}
		int[] slots = dest.getSlotsForFace(destFace);
		for(int i = 0; i < slots.length; i++){
			int slot = slots[i];
			if(dest.canInsertItem(slot, item, destFace)){
				ItemStack destItem = dest.getStackInSlot(slot);
				if(destItem == null || destItem.isEmpty()) {
					return true;
				} else {
					return ItemStack.areItemsEqual(item, destItem);
				}
			}
		}
		return false;
	}
	
	protected static boolean transferItem(ISidedInventory src, Direction srcFace, ISidedInventory dest, Direction destFace){
		if(isLocked(src) || isLocked(dest)){
			return false;
		}
		int[] srcValidSlots = src.getSlotsForFace(srcFace);
		for(int i = 0; i < srcValidSlots.length; i++){
			ItemStack item = src.getStackInSlot(srcValidSlots[i]);
			if(item == null || item.isEmpty()) continue;
			if(src.canExtractItem(srcValidSlots[i], item, srcFace)){
					int[] destValidSlots = dest.getSlotsForFace(destFace);
					// First look for stackable items
					for(int j = 0; j < destValidSlots.length; j++){
						if(dest.canInsertItem(destValidSlots[j], item, destFace)){
							ItemStack otherItem = dest.getStackInSlot(destValidSlots[j]);
							if(otherItem != null && !otherItem.isEmpty()
									&& ItemStack.areItemsEqual(item, otherItem)
									&& otherItem.getCount() < dest.getInventoryStackLimit()
									&& otherItem.getCount() < otherItem.getItem().getItemStackLimit(otherItem)){
								src.decrStackSize(srcValidSlots[i], 1);
								otherItem.grow(1);
								return true;
							}
						}
					}
					// then look for empty slots
					for(int j = 0; j < destValidSlots.length; j++){
						if(dest.canInsertItem(destValidSlots[j], item, destFace)){
							ItemStack otherItem = dest.getStackInSlot(destValidSlots[j]);
							if(otherItem == null || otherItem.isEmpty()){
								dest.setInventorySlotContents(destValidSlots[j], src.decrStackSize(srcValidSlots[i], 1));
								return true;
							}
						}
					}
			}
		}
		return false;
	}
	
	
	
	/**
	 * Gets the direction that the blockstate is facing
	 * @return an EnumfFacing
	 */
	public Direction getFacing(){
		BlockState state = getWorld().getBlockState(getPos());
		if(state.has(BlockConveyor.FACING)){
			return state.get(BlockConveyor.FACING);
		}else{
			PowerAdvantage.LOGGER.warn("{}.getFacing(): blockstate at {} does not have BlockConveyor.FACING", this.getClass().getName(), getPos());
			return Direction.NORTH;
		}
	}
	
	protected ItemStack[] getInventory(){
		return inventory;
	}

	
	/**
	 * You must override this method and call super.read(...).<br><br>
	 * This method reads data from an NBT data tag (either from a data packet 
	 * or loaded from the Chunk).
	 * @param tagRoot The root of the NBT
	 */
	@Override
	public void read(final CompoundNBT tagRoot) {
		super.read(tagRoot);
		ItemStack[] inventory = this.getInventory();
		if(inventory != null ){
			Arrays.fill(inventory, null);
			final ListNBT nbttaglist = tagRoot.getList("Items", 10);
			for (int i = 0; i < nbttaglist.size() && i < inventory.length; ++i) {
				final CompoundNBT nbttagcompound1 = nbttaglist .getCompound(i);
				final byte n = nbttagcompound1.getByte("Slot");
				if (n >= 0 && n < inventory.length) {
					ItemStack stack = ItemStack.read(nbttagcompound1);
					inventory[n] = stack.isEmpty() ? null : stack;
				}
			}
		}
		if (tagRoot .contains("CustomName", 8)) {
			this.customName = tagRoot.getString("CustomName");
		}
	}
	
	/**
	 * You must override this method and call super .write(...).<br><br>
	 * This method writes data to an NBT data tag (either to a data packet 
	 * or saved to the Chunk).
	 * @param tagRoot The root of the NBT
	 */
	@Override
	public CompoundNBT write(final CompoundNBT tagRoot) {
		super .write(tagRoot);
		ItemStack[] inventory = this.getInventory();
		if(inventory != null ){
			final ListNBT nbttaglist = new ListNBT();
			for (int i = 0; i < inventory.length; ++i) {
				if (inventory[i] != null && !inventory[i].isEmpty()) {
					final CompoundNBT nbttagcompound1 = new CompoundNBT();
					nbttagcompound1.putByte("Slot", (byte)i);
					inventory[i] .write(nbttagcompound1);
					nbttaglist .add(nbttagcompound1);
				}
			}
			tagRoot.put("Items", nbttaglist);
		}
		if (this.hasCustomName()) {
			tagRoot.putString("CustomName", this.customName);
		}
		return tagRoot;
	}

	/**
	 * Override to keep the tile entity from being deleted each time the blockstate is updated
	 * @param world world instance
	 * @param pos coordinate
	 * @param oldState State before change
	 * @param newSate State after change
	 * @return true if this TileEntity should be invalidated (deleted and recreated), false otherwise
	 */
	public boolean shouldRefresh(World world, BlockPos pos, BlockState oldState, BlockState newSate) {
		return oldState.getBlock() != newSate.getBlock();
	}
	
	///// ISidedInventory METHODS /////
	@Override
	public void clear() {
		Arrays.fill(inventory, null);
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : inventory) {
			if (stack != null && !stack.isEmpty()) return false;
		}
		return true;
	}

	@Override
	public void closeInventory(PlayerEntity p) {
		// do nothing
	}

	@Override
	public ItemStack decrStackSize(int slot, int decrement) {
		if (this.getInventory()[slot] == null || this.getInventory()[slot].isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (this.getInventory()[slot].getCount() <= decrement) {
			final ItemStack itemstack = this.getInventory()[slot];
			this.getInventory()[slot] = null;
			return itemstack;
		}
		final ItemStack itemstack = this.getInventory()[slot] .split(decrement);
		if (this.getInventory()[slot].isEmpty()) {
			this.getInventory()[slot] = null;
		}
		return itemstack;
	}

	public int getField(int n) {
		return 0;
	}

	public int getFieldCount() {
		return 0;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public int getSizeInventory() {
		return getInventory().length;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		ItemStack stack = this.getInventory()[slot];
		return stack == null ? ItemStack.EMPTY : stack;
	}
	
	@Override
	public ItemStack removeStackFromSlot(int slot) {
		ItemStack i = this.getInventory()[slot];
		this.getInventory()[slot] = null;
		return i == null ? ItemStack.EMPTY : i;
	}



	@Override
	public boolean isItemValidForSlot(final int slot, final ItemStack item) {
		return slot < getInventory().length && getInventory()[0] == null; 
	}

	@Override
	public boolean isUsableByPlayer(final PlayerEntity p_isUseableByPlayer_1_) {
		return this.world.getTileEntity(this.pos) 
				== this && p_isUseableByPlayer_1_.getDistanceSq(
						(double)this.pos.getX() + 0.5, 
						(double)this.pos.getY() + 0.5, 
						(double)this.pos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void openInventory(PlayerEntity arg0) {
		// do nothing
	}

	public void setField(int arg0, int arg1) {
		// do nothing
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack item) {
		ItemStack existing = this.getInventory()[slot];
		final boolean flag = item != null && !item.isEmpty() && existing != null && !existing.isEmpty()
				&& item.isItemEqual(existing) && ItemStack.areItemStackTagsEqual(item, existing);
		this.getInventory()[slot] = (item == null || item.isEmpty()) ? null : item;
		if (item != null && item.getCount() > this.getInventoryStackLimit()) {
			item.setCount(this.getInventoryStackLimit());
		}
		if (slot == 0 && !flag) {
			this.markDirty();
		}
	}

	@Override
	public TextComponent getDisplayName() {
		return (TextComponent) getName();
	}

	private String customName = null;
	/**
	 * boilerplate inventory code
	 * @return either the unlocalized name for this entity or a custom name 
	 * given by the player (i.e. with an anvil)
	 */
	@Override
	public ITextComponent getName() {
		return this.hasCustomName() ? new StringTextComponent(this.customName)
				: new TranslationTextComponent(this.getUnlocalizedName());
	}

	@Override
	public ITextComponent getCustomName() {
		return this.hasCustomName() ? new StringTextComponent(this.customName) : null;
	}
	
	/**
	 * Gets the unlocalized String passed to the constructor
	 * @return A String used in name localization by client GUI 
	 */
	public String getUnlocalizedName(){
		return unlocalizedName;
	}

	/**
	 * boilerplate inventory code
	 * @return true if this block has been renamed, false otherwise
	 */
	@Override
	public boolean hasCustomName() {
		return customName != null;
	}


	/**
	 * Determines whether another block (such as a Hopper) is allowed to pull an 
	 * item from this block out through a given face
	 * @param slot The inventory slot (index) of the item in question
	 * @param targetItem The item to be pulled
	 * @param side The side of the block through which to pull the item
	 * @return true if the item is allowed to be pulled, false otherwise
	 */
	@Override
	public boolean canExtractItem(final int slot, final ItemStack targetItem, final Direction side) {
		return slot == 0;
	}
	/**
	 * Determines whether another block (such as a Hopper) is allowed to put an 
	 * item into this block through a given face
	 * @param slot The inventory slot (index) of the item in question
	 * @param srcItem The item to be inserted
	 * @param side The side of the block through which to insertt the item
	 * @return true if the item is allowed to be inserted, false otherwise
	 */
	@Override
	public boolean canInsertItem(final int slot, final ItemStack srcItem, final Direction side) {
		return slot == 0 && this.isItemValidForSlot(slot, srcItem);
	}

	/** cache for default implementation of getSlotsForFace(...)*/
	private int[] slotAccessCache = {0};
	/**
	 * This method is used to tell other blocks (such as a hopper) which 
	 * inventory slots they are allowed to interact with, based on which face of 
	 * this block they are touching. Override this method to make output slots 
	 * go out the bottom of this block and input slots go in the top (like a 
	 * furnace).<br><br>
	 * The default implementation here makes all inventory slots accessible from 
	 * all sides.
	 * @param side The side of this block through which the other entity wants to 
	 * access the inventory 
	 * @return An array listing all of the inventory indices that are accessible 
	 * through the given side of this block
	 */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return slotAccessCache;
	}
	//////////  //////////

}
