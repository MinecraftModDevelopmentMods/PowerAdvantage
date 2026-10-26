package zone.moddev.mc.poweradvantage.registry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * This interface is used to combine the two different classes of a block GUI 
 * into a single object, which can then be added to the MachineGUIRegistry.
 * @author DrCyano
 *
 */
public interface ITileEntityGUI {
	/**
	 * Gets an instance of net.minecraft.inventory.container.Container for the
	 * server-side portion of a GUI interaction.
	 *
	 * @param e      The TileEntity that is presenting this GUI
	 * @param player The player that is going to use the GUI
	 * @return An instance of net.minecraft.inventory.container.Container
	 */
	public abstract net.minecraft.inventory.container.Container getContainer(TileEntity e, PlayerEntity player);

	/**
	 * Gets an instance of net.minecraft.client.gui.screen.inventory.ContainerScreen for
	 * the client-side portion of a GUI interaction.
	 *
	 * @param e      The TileEntity that is presenting this GUI
	 * @param player The player that is going to use the GUI
	 * @return An instance of net.minecraft.client.gui.screen.inventory.ContainerScreen
	 */
	@OnlyIn(Dist.CLIENT)
	public abstract net.minecraft.client.gui.screen.inventory.ContainerScreen getContainerGUI(TileEntity e, PlayerEntity player);
}