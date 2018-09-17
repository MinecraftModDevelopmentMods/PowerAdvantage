package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.basemetals.util.VillagerTradeHelper;
import net.minecraft.entity.passive.EntityVillager;

public abstract class Villages {
	// TODO: add machinist villager

	private static boolean initDone = false;

	public static void init() {
		if (initDone) return;

		Entities.init();
//		try {
			VillagerTradeHelper.insertTrades(3, 3, 1, new EntityVillager.ListItemForEmeralds(Items.sprocket, new EntityVillager.PriceInfo(-4, -1)));
			VillagerTradeHelper.insertTrades(0, 1, 1, new EntityVillager.ListItemForEmeralds(Items.bioplastic_ingot, new EntityVillager.PriceInfo(-8, -4)));
//		} catch (NoSuchFieldException | IllegalAccessException e) {
//			FMLLog.log(Level.ERROR, e, "Failed to add trades to villagers");
//		}

		initDone = true;
	}
}
