package cyano.poweradvantage.init;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cyano.poweradvantage.registry.FuelRegistry;

public abstract class Blocks {

	
	private static boolean initDone = false;
	public static void init(){
		if(initDone) return;
		
		// TODO: add Power Gauge block that displays the power in a neighboring conductor
		// TODO: add fluid pipes
		
		initDone = true;
	}
	
}