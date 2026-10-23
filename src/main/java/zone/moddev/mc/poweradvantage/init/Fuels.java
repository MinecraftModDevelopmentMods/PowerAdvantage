package zone.moddev.mc.poweradvantage.init;

import zone.moddev.mc.poweradvantage.registry.FuelRegistry;
import net.minecraft.item.ItemStack;



public abstract class Fuels {

	public static final int CRUDE_OIL_FUEL_PER_FLUID_UNIT = 5;
	public static final int REFINED_OIL_FUEL_PER_FLUID_UNIT = 25;

	private static boolean initDone = false;

	public static void init() {
		if (initDone) return;

		Items.init();
		FuelRegistry.getInstance().registerFuel(Items.crude_oil_bucket,
				(short) (1000 * CRUDE_OIL_FUEL_PER_FLUID_UNIT));
		FuelRegistry.getInstance().registerFuel(Items.refined_oil_bucket,
				(short) Math.min(Short.MAX_VALUE, 1000 * REFINED_OIL_FUEL_PER_FLUID_UNIT));
		FuelRegistry.getInstance().registerPostBurnItem(Items.crude_oil_bucket,
				(ItemStack stack) -> new ItemStack(net.minecraft.init.Items.BUCKET));
		FuelRegistry.getInstance().registerPostBurnItem(Items.refined_oil_bucket,
				(ItemStack stack) -> new ItemStack(net.minecraft.init.Items.BUCKET));


		initDone = true;
	}

}
