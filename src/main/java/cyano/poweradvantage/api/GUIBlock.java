package cyano.poweradvantage.api;

import net.minecraft.block.material.Material;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockReader;
import zone.moddev.mc.poweradvantage.registry.MachineGUIRegistry;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import zone.moddev.mc.poweradvantage.util.FluidContainerHelper;

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
public abstract class GUIBlock extends net.minecraft.block.BlockContainer{

	/**
	 * Constructor for GUI block
	 * @param m Material for the block (determines what tools can break it and how it interacts with 
	 * other Minecraft rules).
	 */
	public GUIBlock(Material m) {
		this(m, 1.0F);
	}

	public GUIBlock(Material m, float hardness) {
		this(Block.Properties.create(m).hardnessAndResistance(hardness).variableOpacity());
	}

	protected GUIBlock(Block.Properties properties) {
		super(properties);
	}

	@Deprecated
	protected void setPowerAdvantageHardness(float hardness) {
		// Block properties are immutable after construction in 1.13.
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
    public boolean isOpaqueCube(IBlockState bs) {
        return false;
    }

    /** 1.13 uses this flag, not the legacy opacity overload, to cull neighbours. */
    @Override
    public boolean isSolid(IBlockState state) {
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
	@SuppressWarnings("deprecation")
    @Override
    public boolean onBlockActivated(IBlockState bs, World w, BlockPos coord,
									EntityPlayer player, EnumHand hand, EnumFacing facing,
									float hitX, float hitY, float hitZ) {
        if (w.isRemote) {
            return true;
        }
        final TileEntity tileEntity = w.getTileEntity(coord);
        if (tileEntity == null || player.isSneaking()) {
        	return false;
        }
        // handle buckets and fluid containers
		net.minecraft.item.ItemStack item = player.getHeldItem(hand);
		if(item != null && !item.isEmpty() && tileEntity instanceof IAdvantageFluidHandler
				&& FluidContainerHelper.interact(player, hand, (IAdvantageFluidHandler) tileEntity, facing)) {
			return true;
		}

        // open GUI
        if(this.getGuiOwner() == null) return false;
		if(tileEntity instanceof PoweredEntity && player instanceof EntityPlayerMP){
			((PoweredEntity)tileEntity).syncPowerAdvantageDataTo((EntityPlayerMP)player);
		}
		if (!(player instanceof EntityPlayerMP)) return false;
		MachineGUIRegistry.open((EntityPlayerMP) player, w, coord, this.getGuiID());
        return true;
    }

    
   
	@Override
	public void onReplaced(IBlockState state, World world, BlockPos pos, IBlockState newState,
			boolean isMoving) {
		if (state.getBlock() != newState.getBlock()) {
			TileEntity te = world.getTileEntity(pos);
			if (te instanceof IInventory) {
				InventoryHelper.dropInventoryItems(world, pos, (IInventory) te);
				((IInventory) te).clear();
				world.updateComparatorOutputLevel(pos, this);
			}
			super.onReplaced(state, world, pos, newState, isMoving);
		}
	}

}
