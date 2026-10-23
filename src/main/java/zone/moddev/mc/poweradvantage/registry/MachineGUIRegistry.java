package zone.moddev.mc.poweradvantage.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The MachineGUIRegistry registry provides a simple way to add GUIs for your 
 * machines. Just implement the ITileEntityGUI (usually by extending the 
 * SimpleMachineGUI class) and pass it to the <code>addGUI(...)</code> method. 
 * This method returns a number which is the unique ID for that GUI. Blocks that 
 * are supposed to show that GUI will call 
 * <code>player.openGui(PowerAdvantage.getInstance(), guiID, world, x, y, z);</code> 
 * in their override of the <code>onBlockActivated(...)</code> method (guiID is 
 * the number returned by <code>MachineGUIRegistry.addGUI(...)</code>).
 * @author DrCyano
 *
 */
public class MachineGUIRegistry {
	private static final AtomicInteger guiIDCounter = new AtomicInteger(1);
	private static final Map<Integer, ITileEntityGUI> guiTable = new HashMap<Integer, ITileEntityGUI>();

	private MachineGUIRegistry() {
		// using singleton instantiation
	}

	private static final Lock initLock = new ReentrantLock();
	private static MachineGUIRegistry instance = null;

	/**
	 * Gets a singleton instance of MachineGUIRegistry
	 *
	 * @return A global instance of MachineGUIRegistry
	 */
	public static MachineGUIRegistry getInstance() {
		if (instance == null) {
			initLock.lock();
			try {
				if (instance == null) {
					// thread-safe singleton instantiation
					instance = new MachineGUIRegistry();
				}
			} finally {
				initLock.unlock();
			}
		}
		return instance;
	}

	/**
	 * Adds a new GUI to the registry and returns its GUI ID number. The blocks
	 * that are intended to show this GUI will do so by calling
	 * <code>player.openGui(PowerAdvantage.getInstance(), guiID, world, x, y, z);</code>
	 * where guiID is the GUI ID number returned by this method.
	 *
	 * @param gui An implementation of ITileEntityGUI (typically an extension of
	 *            the SimpleMachineGUI class).
	 * @return The gui id that the gui was registered to. This number is needed
	 * to show the GUI to the player.
	 */
	public static int addGUI(ITileEntityGUI gui) {
		int id = guiIDCounter.getAndIncrement();
		guiTable.put(id, gui);
		return id;
	}

	/**
	 * Removes a GUI by its ID that was returned by the addGUI(...) method.
	 *
	 * @param guiID The ID of the GUI to remove
	 */
	public void removeGUI(int guiID) {
		guiTable.remove(guiID);
	}

	/**
	 * Implementation of net.minecraftforge.fml.common.network.IGuiHandler
	 */
	@OnlyIn(Dist.CLIENT)
	public Object getClientGuiElement(
			int id, EntityPlayer player, World world,
			int x, int y, int z
	) {
		TileEntity tileEntity = world.getTileEntity(new BlockPos(x, y, z));
		if (guiTable.containsKey(id)) {
			return guiTable.get(id).getContainerGUI(tileEntity, player);
		}
		return null;
	}

	/**
	 * Implementation of net.minecraftforge.fml.common.network.IGuiHandler
	 */
	public Object getServerGuiElement(
			int id, EntityPlayer player, World world,
			int x, int y, int z
	) {
		TileEntity tileEntity = world.getTileEntity(new BlockPos(x, y, z));
		if (guiTable.containsKey(id)) {
			return guiTable.get(id).getContainer(tileEntity, player);
		}
		return null;
	}

	public static void open(EntityPlayerMP player, World world, BlockPos pos, int guiId) {
		ITileEntityGUI definition = guiTable.get(guiId);
		if (definition == null) return;
		TileEntity tile = world.getTileEntity(pos);
		if (tile == null) return;
		NetworkHooks.openGui(player, new IInteractionObject() {
			@Override
			public Container createContainer(InventoryPlayer inventory, EntityPlayer entityPlayer) {
				return definition.getContainer(tile, entityPlayer);
			}

			@Override
			public String getGuiID() {
				return "poweradvantage:machine";
			}

			@Override public ITextComponent getName() { return new TextComponentString("Power Advantage"); }
			@Override public boolean hasCustomName() { return false; }
			@Override public ITextComponent getCustomName() { return null; }
		}, buffer -> {
			buffer.writeBlockPos(pos);
			buffer.writeVarInt(guiId);
		});
	}

	@OnlyIn(Dist.CLIENT)
	public static GuiScreen createClientGui(FMLPlayMessages.OpenContainer message) {
		PacketBuffer data = message.getAdditionalData();
		BlockPos pos = data.readBlockPos();
		int guiId = data.readVarInt();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.world == null || minecraft.player == null) return null;
		TileEntity tile = minecraft.world.getTileEntity(pos);
		ITileEntityGUI definition = guiTable.get(guiId);
		if (tile == null || definition == null) return null;
		GuiContainer screen = definition.getContainerGUI(tile, minecraft.player);
		screen.inventorySlots.windowId = message.getWindowId();
		return screen;
	}
}
