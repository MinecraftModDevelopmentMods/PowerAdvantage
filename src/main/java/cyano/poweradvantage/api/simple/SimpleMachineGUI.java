package cyano.poweradvantage.api.simple;


import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.math.Integer2D;
import zone.moddev.mc.poweradvantage.registry.ITileEntityGUI;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import zone.moddev.mc.poweradvantage.registry.MachineGUIRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * This class provides a simple way to add a GUI for a machine. Instances of 
 * this class can be added to the MachineGUIRegistry. Simply initializing this 
 * class will create a static GUI with inventory slots, but if you want to have 
 * any animations/indicators (e.g. a progress bar), you will need to extend this 
 * class and override the <b>drawGUIDecorations(...)</b> method to draw the 
 * animated parts of the GUI. 
 * @author DrCyano
 *
 */
public class SimpleMachineGUI implements ITileEntityGUI {

	/**
	 * This is the texture resource for drawing the player's inventory.
	 */
	public final static ResourceLocation playerInventoryBox  = new ResourceLocation(PowerAdvantage.MODID+":"+"textures/gui/container/player_inventory.png");
	/** Texture resource for the machine's GUI */ 
	protected final ResourceLocation guiDisplayImage;
	/** Dimensions of the GUI texture image */ 
	protected final int guiWidth, guiHeight;
	/** List of pixel coordinates for the upper-left corner of each inventory 
	 * slot that should appear on the screen */
	protected final Integer2D[] inventorySlotCoordinates;
	/**
	 * This map is used to specify which slots are not standard slots. The key for the map is the inventory index of the
	 * item slot.
	 */
	protected final Map<Integer,Function<SlotContext,Slot>> specialSlots = new HashMap<>();
	/**
	 * Standard constructor for SimpleMachineGUI. You must provide a texture 
	 * resource location for the GUI background image. The inventory slots are 
	 * positioned besed on the array of coordinates (as Integer2D objects) 
	 * passed in to this constructor.
	 * @param guiImage The location of the texture resource for the GUI
	 * @param inventorySlotCoordinates List of pixel coordinates (relative to 
	 * the top-left corner of the GUI image) to draw the inventory slots. If you 
	 * don't want to show GUI slots, you can provide a null or empty array. It 
	 * is assumed that the index in this list of coordinates corresponds to the 
	 * inventory index of the TileEntity's inventory.  
	 */
	public SimpleMachineGUI(ResourceLocation guiImage, Integer2D... inventorySlotCoordinates){
		this.guiDisplayImage = guiImage;
		this.guiWidth = 176;
		this.guiHeight = 222;
		if(inventorySlotCoordinates == null){
			this.inventorySlotCoordinates = new Integer2D[0];
		} else {
			this.inventorySlotCoordinates = inventorySlotCoordinates;
		}
	}
	
	/**
	 * Standard constructor for SimpleMachineGUI. You must provide a texture 
	 * resource location for the GUI background image. The inventory slots are 
	 * positioned besed on the array of coordinates (as Integer2D objects) 
	 * passed in to this constructor.
	 * @param guiImage The location of the texture resource for the GUI
	 * @param inventorySlotCoordinates List of pixel coordinates (relative to 
	 * the top-left corner of the GUI image) to draw the inventory slots. If you 
	 * don't want to show GUI slots, you can provide a null or empty array. It 
	 * is assumed that the index in this list of coordinates corresponds to the 
	 * inventory index of the TileEntity's inventory.  
	 */
	public SimpleMachineGUI(String guiImage, Integer2D... inventorySlotCoordinates){
		this(new ResourceLocation(guiImage),inventorySlotCoordinates);
	}
	
	

	/**
	 * Override this method to draw on the GUI window.
	 * <br><br>
	 * This method is invoked when drawing the GUI so that you can draw 
	 * animations and other foreground decorations to the GUI.
	 * @param srcEntity This is the TileEntity (or potentially a LivingEntity) 
	 * for whom we are drawing this interface
	 * @param guiContainer This is the instance of GUIContainer that is drawing 
	 * the GUI. You need to use it to draw on the screen. For example:<br>
	   <pre>
guiContainer.minecraft.getTextureManager().bindTexture(arrowTexture);
guiContainer.blit(x+79, y+35, 0, 0, arrowLength, 17); // x, y, textureOffsetX, textureOffsetY, width, height)
	   </pre>
	 * @param x This is the x coordinate (in pixels) from the top-left corner of 
	 * the GUI
	 * @param y This is the y coordinate (in pixels) from the top-left corner of 
	 * the GUI
	 * @param z This is the z coordinate (no units) into the depth of the screen
	 */
	public void drawGUIDecorations(Object srcEntity, GUIContainer guiContainer, int x, int y, float z){}

	/**
	 * Gets an instance of net.minecraft.inventory.container.Container for the
	 * server-side portion of a GUI interaction.
	 * @param e The TileEntity that is presenting this GUI
	 * @param player The player that is going to use the GUI
	 * @return An instance of net.minecraft.inventory.container.Container
	 */
	@Override
	public net.minecraft.inventory.container.Container getContainer(TileEntity e, PlayerEntity player) {
		return new Container(player.inventory,(IInventory)e);
	}

    public Container createMenu(int windowId, PlayerInventory player, IInventory inventory) {
        return new Container(windowId, player, inventory);
    }

    /** @deprecated Use the native menu factory name. */
    @Deprecated public Container createContainer(int windowId, PlayerInventory player, IInventory inventory) {
        return createMenu(windowId, player, inventory);
    }

	/**
	 * Gets an instance of net.minecraft.client.gui.screen.inventory.ContainerScreen for
	 * the client-side portion of a GUI interaction.
	 * @param e The TileEntity that is presenting this GUI
	 * @param player The player that is going to use the GUI
	 * @return An instance of net.minecraft.client.gui.screen.inventory.ContainerScreen
	 */
	@Override
	@OnlyIn(Dist.CLIENT)
	public ContainerScreen getContainerGUI(TileEntity e, PlayerEntity player) {
		return new GUIContainer(player.inventory,(IInventory)e);
	}
	
	
	/**
	 * Member class used to provide an instance of 
	 * net.minecraft.inventory.container.Container to fulfill the requirements of the
	 * interface ITileEntityGUI.
	 */
	public class Container extends net.minecraft.inventory.container.Container{

		private final IInventory entity;
		public Container(PlayerInventory playerItems, IInventory entity){
            this(0, playerItems, entity);
        }

        public Container(int windowId, PlayerInventory playerItems, IInventory entity) {
            super(MachineGUIRegistry.TYPE, windowId);
			this.entity = entity;
			int index = 0;
			for(Integer2D pt : inventorySlotCoordinates){
				if(specialSlots.containsKey(index)){
					SlotContext params = new SlotContext(playerItems,entity,index, pt.X, pt.Y);
					this.addSlot(specialSlots.get(index).apply(params));
				} else {
					this.addSlot(new Slot(entity, index, pt.X, pt.Y));
				}
				index++;
			}
			bindPlayerInventory(playerItems, 140);
		}

        @OnlyIn(Dist.CLIENT)
        public GUIContainer createScreen(PlayerInventory inventory, ITextComponent title) {
            return new GUIContainer(this, inventory, title);
        }
		
		@Override
		public boolean stillValid(PlayerEntity entityplayer) {
			return entity.stillValid(entityplayer);
		}
		
		protected void bindPlayerInventory(PlayerInventory inventoryPlayer, int yOffset) {
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 9; j++) {
					addSlot(new Slot(inventoryPlayer, j + i * 9 + 9, 8 + j * 18, yOffset + i * 18));
				}
			}
			for (int i = 0; i < 9; i++) {
				addSlot(new Slot(inventoryPlayer, i, 8 + i * 18, yOffset+58));
			}
		}
		
		@Override
		public ItemStack quickMoveStack(PlayerEntity player, int slot) {
			int hostSize = inventorySlotCoordinates.length;
			ItemStack stack = ItemStack.EMPTY;
			Slot slotObject = (Slot) slots.get(slot);
			//null checks and checks if the item can be stacked (maxStackSize > 1)
			if (slotObject != null && slotObject.hasItem()) {
				ItemStack stackInSlot = slotObject.getItem();
				stack = stackInSlot.copy();

				//merges the item into player inventory since its in the tileEntity
				if (slot < hostSize) {
					if (!this.moveItemStackTo(stackInSlot, hostSize, 36+hostSize, true)) {
						return ItemStack.EMPTY;
					}
				}
				//places it into the tileEntity if possible since it's in the player inventory
				else if (!this.moveItemStackTo(stackInSlot, 0, hostSize, false)) {
					return ItemStack.EMPTY;
				}

				if (stackInSlot.isEmpty()) {
					slotObject.set(ItemStack.EMPTY);
				} else {
					slotObject.setChanged();
				}

				if (stackInSlot.getCount() == stack.getCount()) {
					return ItemStack.EMPTY;
				}
				slotObject.onTake(player, stackInSlot);
			}
			return stack;
		}
		
	}
	
	/**
	 * Member class used to provide an instance of 
	 * net.minecraft.client.gui.screen.inventory.ContainerScreen to fulfill the
	 * requirements of the interface ITileEntityGUI.
	 */

	@OnlyIn(Dist.CLIENT)
	public class GUIContainer extends net.minecraft.client.gui.screen.inventory.ContainerScreen<Container>{
		private final Object entity;
		private MatrixStack matrices = new MatrixStack();

        public net.minecraft.client.Minecraft getMinecraft() { return minecraft; }
		public GUIContainer(PlayerInventory playerItems, IInventory entity) {
            this(new Container(playerItems, entity), playerItems, new StringTextComponent("Machine"));
        }

        public GUIContainer(Container container, PlayerInventory playerItems, ITextComponent title) {
            super(container, playerItems, title);
//System.out.println("Size: "+this.xSize+"x"+this.ySize+" at coordinate ("+this.guiLeft+","+this.guiTop+")");
			this.imageWidth = guiWidth;
			this.imageHeight = guiHeight;
			this.entity = container.entity;
		}
		@Override
		protected void renderBg(MatrixStack matrices, float f, int i, int j) {
			this.matrices = matrices;
			RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
			this.minecraft.getTextureManager().bind(playerInventoryBox);
			int x = (width - imageWidth) / 2;
			int y = (height - imageHeight) / 2;
			final int playerInventoryWidth = 176;
			final int playerInventoryHeight = 222;
			this.blit(x, y, 0, 0, playerInventoryWidth, playerInventoryHeight);
			this.minecraft.getTextureManager().bind(guiDisplayImage);
			this.blit(x, y, 0, 0, guiWidth, guiHeight);
			drawGUIDecorations(entity, this, x, y, this.getBlitOffset());
		}

		/** Retains the decoration API while using the current screen matrix. */
		public void blit(int x, int y, int u, int v, int width, int height) {
			super.blit(matrices, x, y, u, v, width, height);
		}

		public MatrixStack getMatrices() { return matrices; }
		/**
		 * Gets the z-coordinate of this GUI element
		 * @return screen depth coordinate
		 */
		public float getZLevel(){
			return this.getBlitOffset();
		}
		/**
		 * Gets the width of the GUI
		 * @return size of GUI
		 */
		public int getXSize(){
			return this.imageWidth;
		}

		/**
		 * Gets the height of the GUI
		 * @return size of GUI
		 */
		public int getYSize(){
			return this.imageHeight;
		}
		/**
		 * Gets the GUI offset from left side of screen, in pixels
		 * @return pixels to left of GUI
		 */
		public int getLeft(){
			return this.leftPos;
		}

		/**
		 * Gets the GUI offset from top of screen, in pixels
		 * @return pixels to above the GUI
		 */
		public int getTop(){
			return this.topPos;
		}
		
	}

	/**
	 * This class is a simple data structure used for passing all knowable parameters to the constructor of a custom
	 * inventory slot
	 */
	public static class SlotContext {
		public final PlayerInventory playerInventory;
		public final IInventory machineInventory;
		public final int slotIndex;
		public final int screenPositionX;
		public final int screenPositionY;

		/**
		 * Creates a slot context object for use in making custom inventory slots
		 *
		 * @param playerInventory  player's inventory
		 * @param machineInventory inventory (of the machine) that the player is interacting with
		 * @param slotIndex        index of slot in inventory of the machine
		 * @param screenPositionX  coordinate position on screen
		 * @param screenPositionY  coordinate position on screen
		 */
		public SlotContext(
				final PlayerInventory playerInventory,
				final IInventory machineInventory,
				final int slotIndex,
				final int screenPositionX,
				final int screenPositionY
		) {
			this.playerInventory = playerInventory;
			this.machineInventory = machineInventory;
			this.slotIndex = slotIndex;
			this.screenPositionX = screenPositionX;
			this.screenPositionY = screenPositionY;
		}

	}
}
