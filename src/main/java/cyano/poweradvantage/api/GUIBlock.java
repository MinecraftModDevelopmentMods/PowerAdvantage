package cyano.poweradvantage.api;

import cyano.poweradvantage.util.FluidHelper;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

/**
 * <p>
 * The GUIBlock is a convenient abstract class for all blocks that should show a GUI when the player 
 * right-clicks on the block. After creating an instance of the GUIBlock class, get a GUI index 
 * by calling <code>int gui_index = MachineGUIRegistry.addGUI(...)</code> and set the GUI indes and 
 * GUI owner with 
 * <code>myGUIBlock.setGuiID(gui_index); myGUIBlock.setGuiOwner(PowerAdvantage.getInstance())</code>. 
 * Of course, if you are managing the GUIs yourself, then you will provide your own GUI index and 
 * use your mod's class instance as the owner instead of PowerAdvantage.
 * </p> 
 * @author DrCyano
 *
 */
public abstract class GUIBlock extends BlockContainer {

	/**
	 * Constructor for GUI block
	 * @param m Material for the block (determines what tools can break it and how it interacts with 
	 * other Minecraft rules).
	 */
	public GUIBlock(Material m) {
		super(m);
        this.setLightOpacity(0);
	}
	
	private int guiId = 0;
	private Object guiOwner = null;
	/**
	 * Sets the GUI index number for the GUI to show when this block is right-clicked by the player. 
	 * In short, when the player right-clicks this block, the following code is called<br>
	 * <code>player.openGui(this.getGuiOwner(), this.getGuiID(),world,pos);</code>
	 * @param idNumber The number of the GUI to show according to the Forge GUI system.
	 * @param guiOwner This is the object that was used to register the GUI handler (e.g. <i>PowerAdvantage.getInstance()</i> in 
	 * <code>NetworkRegistry.INSTANCE.registerGuiHandler(PowerAdvantage.getInstance(), MachineGUIRegistry.getInstance());</code>
	 * ). This is usually the mod's main class, or if you are using the <b>MachineGUIRegistry</b>, 
	 * then you would set the GUI owner to PowerAdvantage.getInstance().
	 */
	public void setGuiID(int idNumber, Object guiOwner){
		this.guiId = idNumber;
		this.guiOwner = guiOwner;
	}
	/**
	 * Gets the GUI index number for the GUI to show when this block is right-clicked by the player. 
	 * In short, when the player right-clicks this block, the following code is called<br>
	 * <code>player.openGui(this.getGuiOwner(), this.getGuiID(),world,pos);</code>
	 * @return The number of the GUI to show according to the Forge GUI system.
	 */
	public int getGuiID(){
		return guiId;
	}
	/**
	 * Gets the object that was used to register the GUI handler (e.g. <i>PowerAdvantage.getInstance()</i> in 
	 * <code>NetworkRegistry.INSTANCE.registerGuiHandler(PowerAdvantage.getInstance(), MachineGUIRegistry.getInstance());</code>
	 * ). This is usually the mod's main class, or if you are using the <b>MachineGUIRegistry</b>, 
	 * then you would set the GUI owner to PowerAdvantage.getInstance(). 
	 * In short, when the player right-clicks this block, the following code is called<br>
	 * <code>player.openGui(this.getGuiOwner(), this.getGuiID(),world,pos);</code>
	 * @return The owner of the GUI when you registered the GUI handler (not the GUI handler 
	 * itself).
	 */
	public Object getGuiOwner(){
		return this.guiOwner;
	}
	/**
	 * Boilerplate code
	 */
	@Override
    public boolean isFullCube(IBlockState bs) {
        return false;
    }
    
	/**
	 * Boilerplate code
	 */
	@Override
    public boolean isOpaqueCube(IBlockState bs) {
        return false;
    }
	
	/**
	 * 3 = normal block (model specified in assets folder as .json model)<br>
	 * -1 = special renderer
	 */
	@Override
    public EnumBlockRenderType getRenderType(IBlockState bs) {
        return EnumBlockRenderType.MODEL;
    }
	
	/**
     * Override of default block behavior to show the player the GUI for this 
     * block. Calls <code>player.openGui(this.getGuiOwner(), this.getGuiID(),world,pos);</code> on 
     * right-click.
     * @return true if the interaction resulted in opening the GUI, false 
     * otherwise
     */
	
	@Override
	public boolean onBlockActivated(World w, BlockPos coord, IBlockState bs, EntityPlayer player,
			EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (w.isRemote) {
            return true;
        }
        final TileEntity tileEntity = w.getTileEntity(coord);
        if (tileEntity == null || player.isSneaking()) {
        	return false;
        }
        // handle buckets and fluid containers
		ItemStack item = player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);
		if(tileEntity instanceof IFluidHandler && item != ItemStack.EMPTY) {
			// TODO: FIX THIS!
			/// NEW WAY - IFluidContainerItem and the UniversalBucket FTW!
			IFluidHandler target = (IFluidHandler) tileEntity;
			if (item.getItem() instanceof IFluidHandler){
				// fill from bucket
				IFluidHandlerItem container = (IFluidHandlerItem) item.getItem();
				FluidStack fluid = FluidHelper.getContainedFluid(item);
				if (fluid != null && fluid.amount > 0) {
					if (target.fill(container.drain(fluid,false), false)== fluid.amount){
						// simulated fill-drain succeeded, do it for real
						FluidStack drained = container.drain(fluid,!player.capabilities.isCreativeMode);
						target.fill(drained,true);
						return true;
					}
				} else if (fluid == null){
					for (IFluidTankProperties tank : target.getTankProperties()){
						if (tank.getContents() != null && tank.getContents().amount >= Fluid.BUCKET_VOLUME){
							Fluid tankFluid = tank.getContents().getFluid();
							FluidStack tankStack = new FluidStack(tankFluid, Fluid.BUCKET_VOLUME);
							if (container.fill(target.drain(tankStack, false), false) == tankStack.amount){
								FluidStack drained = target.drain(tankStack, !player.capabilities.isCreativeMode);
								container.fill(drained, true);
								return true;
							}
						}
					}
				}
			}else if (item.getItem() == Items.BUCKET || item.getItem() == Items.LAVA_BUCKET || item.getItem() == Items.WATER_BUCKET) {
				// make universal bucket
				for (IFluidTankProperties tank : target.getTankProperties()) {
					if (tank.getContents() != null && item.getItem() == Items.BUCKET) {
						// special handling for water and lava (no universal bucket)
						if (tank.getContents().getFluid() == FluidRegistry.WATER) {
							ItemStack filledBucket = new ItemStack(Items.WATER_BUCKET);
							if (tank.getContents().amount >= 1000) {
								FluidStack drain = tank.getContents().copy();
								drain.amount = 1000;
								if (target.drain(drain, false).amount == drain.amount) {
									target.drain(drain, true);
									if (!player.capabilities.isCreativeMode)
										player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, filledBucket);
								}
							}
							return true;
						}
						if (tank.getContents().getFluid() == FluidRegistry.LAVA) {
							ItemStack filledBucket = new ItemStack(Items.LAVA_BUCKET);
							if (tank.getContents().amount >= 1000) {
								FluidStack drain = tank.getContents().copy();
								drain.amount = 1000;
								if (target.drain(drain, false).amount == drain.amount) {
									target.drain(drain, true);
									if (!player.capabilities.isCreativeMode)
										player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, filledBucket);
								}
							}
							return true;
						} else {
							UniversalBucket bucket = ForgeModContainer.getInstance().universalBucket;
							FluidStack drain = tank.getContents().copy();
							drain.amount = bucket.getCapacity();
							if (target.drain(drain, false).amount == bucket.getCapacity()) {
								FluidStack fluidForbucket = tank.getContents();
								target.drain(drain, true);
								if (!player.capabilities.isCreativeMode)
									player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, FluidUtil.getFilledBucket(fluidForbucket));
//								if (item.getCount() > 1) {
//									player.addItemStackToInventory(new ItemStack(bucket, item.getCount() - 1));
//								}

								return true;
							}
						}
					} else if (tank.getContents() == null && tank.getCapacity() >= Fluid.BUCKET_VOLUME) {

						if (item.getItem() == Items.LAVA_BUCKET) {
							FluidStack toFill = new FluidStack(FluidRegistry.LAVA, Fluid.BUCKET_VOLUME);
							if (target.fill(toFill, false) == toFill.amount) {
								target.fill(toFill, true);
								if (!player.capabilities.isCreativeMode)
									player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
								return true;
							}
						} else if (item.getItem() == Items.WATER_BUCKET) {
							FluidStack toFill = new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME);
							if (target.fill(toFill, false) == toFill.amount) {
								target.fill(toFill, true);
								if (!player.capabilities.isCreativeMode)
									player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
								return true;
							}
						}
				} else if (tank.getContents() != null && (item.getItem() == Items.WATER_BUCKET || item.getItem() == Items.LAVA_BUCKET)){
					Fluid tankFluid = tank.getContents().getFluid();
					if (item.getItem() == Items.LAVA_BUCKET && tankFluid == FluidRegistry.LAVA){
						FluidStack toFill = new FluidStack(FluidRegistry.LAVA, Fluid.BUCKET_VOLUME);
						if (target.fill(toFill, false) == toFill.amount) {
							target.fill(toFill, true);
							if (!player.capabilities.isCreativeMode)
								player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
							return true;
						}
					} else if (item.getItem() == Items.WATER_BUCKET && tankFluid == FluidRegistry.WATER){
						FluidStack toFill = new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME);
						if (target.fill(toFill, false) == toFill.amount) {
							target.fill(toFill, true);
							if (!player.capabilities.isCreativeMode)
								player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
							return true;
						}
					}
					}
				}
			} else if (item.getItem() instanceof UniversalBucket){
				for (IFluidTankProperties tank : target.getTankProperties()) {
						// back to your regularly scheduled algorithm...
					UniversalBucket bucket = ForgeModContainer.getInstance().universalBucket;
					FluidStack containedFluid = FluidUtil.getFluidContained(item);
					if (tank.getContents() != null && tank.getContents().amount >= bucket.getCapacity() && containedFluid == null) {
						FluidStack drain = tank.getContents().copy();
						drain.amount = bucket.getCapacity();
						if (target.drain(drain, false).amount == bucket.getCapacity()) {
							FluidStack fluidForbucket = tank.getContents();
							target.drain(drain, true);
							if (!player.capabilities.isCreativeMode)
								player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, FluidUtil.getFilledBucket(fluidForbucket));
//								if (item.getCount() > 1){
//									player.addItemStackToInventory(new ItemStack(bucket, item.getCount() - 1));
//								}

							return true;
						}
					} else if (containedFluid != null && target.fill(containedFluid, false) == bucket.getCapacity()) {
						target.fill(containedFluid, true);
						if (!player.capabilities.isCreativeMode) {
							player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
						}
						return true;
					}

				}
			}
		}

        // open GUI
        if(this.getGuiOwner() == null) return false;
		// Ensures this player gets accurate data sometimes if a player have join server after the last sync and before
		// another sync machines will show as empty. Can't think of a better fix than this.
		if(tileEntity instanceof PoweredEntity){
			((PoweredEntity) tileEntity).syncOne((EntityPlayerMP)player);
		}
        player.openGui(this.getGuiOwner(), this.getGuiID(), w, coord.getX(), coord.getY(), coord.getZ());
        return true;
    }

    
   
//	/**
//	 * This method is used for filling IFluidContainer instances with liquids from a player's
//	 * bucket.
//	 * @param bucket A bucket (or other registered container item) held by the player
//	 * @param player The player interacting with the block
//	 * @param blockFace The face on the block that the player clicked on
//	 * @param target The IFluidHandler that the player interacted with
//	 * @param world World instance
//	 * @return true if fluids were transferred, false otherwise
//	 * @deprecated this method will no longer work in later versions of Minecraft Forge 1.9.x
//	 */
//	@Deprecated
//    public static boolean handleBucketInteraction(ItemStack bucket,final EntityPlayer player,
//			final EnumFacing blockFace, IFluidHandler target, final World world) {
//		/// OLD WAY - deprecated (but still might be used by other mods)
//		if(FluidContainerRegistry.isEmptyContainer(bucket)){
//			// pull from tank
//			FluidStack practice = target.drain(blockFace, Fluid.BUCKET_VOLUME, false);
//			if(practice != null && practice.amount ==  Fluid.BUCKET_VOLUME
//					&& FluidContainerRegistry.fillFluidContainer(practice, bucket) != null){
//				FluidStack drain = target.drain(blockFace, Fluid.BUCKET_VOLUME, true);
//				ItemStack newBucket = FluidContainerRegistry.fillFluidContainer(drain, bucket);
//				if(bucket.getCount() == 1){
//					player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, newBucket);
//				} else {
//					bucket.shrink(1);
//					if(newBucket != null)
//						world.spawnEntity(new EntityItem(world,player.posX,player.posY,player.posZ, newBucket));
//				}
//				return true;
//			} else {
//				return false;
//			}
//		} else if(FluidHelper.isFluidContainer(bucket)){
//			FluidStack fluid = FluidHelper.getContainedFluid(bucket);
//			int practice = target.fill( fluid, false);
//			if(practice == FluidContainerRegistry.getContainerCapacity(bucket)){
//				// pour into empty tank
//				target.fill(fluid, true);
//				ItemStack newBucket = FluidContainerRegistry.drainFluidContainer(bucket);
//				if(bucket.getCount() == 1) {
//					player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, newBucket);
//				} else {
//					bucket.shrink(1);
//					world.spawnEntity(new EntityItem(world,player.posX,player.posY,player.posZ, newBucket));
//				}
//				return true;
//			} else {
//				return false;
//			}
//		} else {
//			return false;
//		}
//	}
    

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state){
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof IInventory)
		{
			InventoryHelper.dropInventoryItems(world, pos, (IInventory)te);
			((IInventory)te).clear();
			world.updateComparatorOutputLevel(pos, this);
		}
		super.breakBlock(world, pos, state);
	}

}
