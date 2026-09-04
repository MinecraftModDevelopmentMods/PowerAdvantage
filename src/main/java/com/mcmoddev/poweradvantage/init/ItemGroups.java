package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.gui.FunctionalCreativeTab;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class ItemGroups {

	public static CreativeTabs tab_powerAdvantage;

	private static boolean initDone = false;

	public static void init() {
		if (initDone) return;

//		tab_powerAdvantage = FunctionalCreativeTab.create(PowerAdvantage.MODID)
//				.setIcon(Items.sprocket)
//				.setItemSortingAlgorithm(ItemGroups::itemSort)
//				.setSearchable(true);

		tab_powerAdvantage = new FunctionalCreativeTab(PowerAdvantage.MODID,
				()->Items.sprocket,
				ItemGroups::itemSort,
				false);

		initDone = true;
	}

	@SuppressWarnings("unused")
	private static int itemSort(ItemStack A, ItemStack B) {
		String catA = catagorize(A);
		String catB = catagorize(B);
		return catA.compareToIgnoreCase(catB);
	}

	private static String catagorize(ItemStack i) {
		StringBuilder sb = new StringBuilder();
		Item item = i.getItem();
		sb.append(ForgeRegistries.ITEMS.getKey(item).getNamespace());
		if (item instanceof ItemBlock) {
			if (((ItemBlock) item).getBlock() instanceof ITileEntityProvider) {
				sb.append("A");
			} else {
				sb.append("B");
			}
		} else {
			sb.append("I");
		}
		sb.append(item.getTranslationKey());
		sb.append(i.getMetadata());

		return sb.toString();
	}
}
