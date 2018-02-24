package cyano.poweradvantage.init;

import com.mcmoddev.lib.init.MMDCreativeTab;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class ItemGroups extends com.mcmoddev.lib.init.ItemGroups {

	public static CreativeTabs tab_powerAdvantage;

	private static boolean initDone = false;
	
//	private static final MMDCreativeTab itemsTabId = addTab("items", true);
	public static final MMDCreativeTab itemsTab = addTab("items", true); // getTab(itemsTabId);


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