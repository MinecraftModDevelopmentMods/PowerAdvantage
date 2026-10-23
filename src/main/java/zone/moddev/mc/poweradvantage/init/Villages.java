package zone.moddev.mc.poweradvantage.init;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

public abstract class Villages {
	// TODO: add machinist villager
	private static final ResourceLocation[] PROFESSION_LIST = {
			new ResourceLocation("minecraft:farmer"),
			new ResourceLocation("minecraft:librarian"),
			new ResourceLocation("minecraft:priest"),
			new ResourceLocation("minecraft:smith"),
			new ResourceLocation("minecraft:butcher")
	};

	private static boolean initDone = false;

	public static void init() {
		if (initDone) return;

		try {
			insertTrades(3, 3, 1, new EntityVillager.ListItemForEmeralds(Items.sprocket, new EntityVillager.PriceInfo(-4, -1)));
			insertTrades(0, 1, 1, new EntityVillager.ListItemForEmeralds(Items.bioplastic_ingot, new EntityVillager.PriceInfo(-8, -4)));
		} catch (RuntimeException e) {
			PowerAdvantage.LOGGER.error("Failed to add trades to villagers", e);
		}

		initDone = true;
	}

	private static void insertTrades(int professionID, int careerID, int tradeLevel, EntityVillager.ITradeList... trades) {
		VillagerRegistry.VillagerProfession profession = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(PROFESSION_LIST[professionID]);
		if (profession == null) {
			throw new IllegalStateException("No villager profession registered for " + PROFESSION_LIST[professionID]);
		}
		VillagerRegistry.VillagerCareer career = profession.getCareer(careerID - 1);
		career.addTrade(tradeLevel, trades);
	}
}
