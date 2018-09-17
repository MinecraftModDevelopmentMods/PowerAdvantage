package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.lib.init.MMDCreativeTab;
import net.minecraft.item.ItemStack;

public class ItemGroups extends com.mcmoddev.lib.init.ItemGroups {
	private static boolean initDone = false;
	
	public static final MMDCreativeTab tab_powerAdvantage = addTab("blocks", true);
	public static final MMDCreativeTab itemsTab = addTab("items", true);


	public static void init() {
		if (initDone) return;
	}
	
	public static void setupIcons() {
		itemsTab.setTabIconItem(new ItemStack(Items.sprocket));
	}

	//TODO Don't  think we need
	/*
	private static int itemSort(ItemStack A, ItemStack B) {
		String catA = catagorize(A);
		String catB = catagorize(B);
		return catA.compareToIgnoreCase(catB);
	}

	private static String catagorize(ItemStack i) {
		StringBuilder sb = new StringBuilder();
		Item item = i.getItem();
		sb.append(GameData.getItemRegistry().getNameForObject(item).getResourceDomain());
		if (item instanceof ItemBlock) {
			if (((ItemBlock) item).getBlock() instanceof ITileEntityProvider) {
				sb.append("A");
			} else {
				sb.append("B");
			}
		} else {
			sb.append("I");
		}
		sb.append(item.getUnlocalizedName());
		sb.append(i.getMetadata());

		return sb.toString();
	}
	*/
}