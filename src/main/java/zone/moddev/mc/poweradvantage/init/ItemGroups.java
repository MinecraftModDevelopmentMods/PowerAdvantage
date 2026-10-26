package zone.moddev.mc.poweradvantage.init;

import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.gui.FunctionalCreativeTab;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemGroups {

	public static ItemGroup tab_powerAdvantage;

	private static boolean initDone = false;

	public static synchronized void init() {
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
		if (item instanceof BlockItem) {
			if (((BlockItem) item).getBlock().hasTileEntity(((BlockItem) item).getBlock().getDefaultState())) {
				sb.append("A");
			} else {
				sb.append("B");
			}
		} else {
			sb.append("I");
		}
		sb.append(item.getTranslationKey());
		return sb.toString();
	}
}
