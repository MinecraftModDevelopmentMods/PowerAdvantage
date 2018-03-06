package cyano.poweradvantage.init;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import cyano.poweradvantage.PowerAdvantage;
import cyano.poweradvantage.items.RotationTool;

@Mod.EventBusSubscriber
@GameRegistry.ObjectHolder(PowerAdvantage.MODID)
public abstract class Items {

	public static Item starch;
	public static Item bioplastic_ingot;
	public static Item sprocket;
	public static Item rotator_tool;

	public static final Map<String, Item> allItems = new HashMap<>();


	private static boolean initDone = false;

	@SubscribeEvent
	public static void init(RegistryEvent.Register<Item> event) {
		if (initDone) return;
		Blocks.init();
		Fluids.init();

		starch = addItem("starch", new Item(), "starch");
		bioplastic_ingot = addItem("bioplastic_ingot", new Item(), "plastic", "ingotPlastic");
		if (PowerAdvantage.plasticIsAlsoRubber) {
			OreDictionary.registerOre("rubber", bioplastic_ingot);
			OreDictionary.registerOre("ingotRubber", bioplastic_ingot);
		}
		sprocket = addItem("sprocket", new Item(), "sprocket", "gear", "sprocketSteel", "gearSteel");
		rotator_tool = addItem("rotator_tool", new RotationTool());

		initDone = true;
	}

	private static Item addItem(String unlocalizedName, Item i, String... oreDictNames) {
		Item n = addItem(unlocalizedName, i);
		for (String oreDictName : oreDictNames) {
			OreDictionary.registerOre(oreDictName, n);
		}
		return n;
	}

	private static Item addItem(RegistryEvent.Register<Item> event, String unlocalizedName, Item i) {
		i.setUnlocalizedName(PowerAdvantage.MODID + "." + unlocalizedName);
		i.setRegistryName(PowerAdvantage.MODID, unlocalizedName);
		event.getRegistry().register(i);
		i.setCreativeTab(ItemGroups.tab_powerAdvantage);
		allItems.put(unlocalizedName, i);
		return i;
	}

	public static Item getItemByName(String unlocalizedName) {
		return allItems.get(unlocalizedName);
	}

	public static Set<Map.Entry<String, Item>> getAllRegisteredItems() {
		return allItems.entrySet();
	}

	@SideOnly(Side.CLIENT)
	public static void registerItemRenders(FMLInitializationEvent event) {
		for (Map.Entry<String, Item> e : getAllRegisteredItems()) {
			Minecraft.getMinecraft().getRenderItem().getItemModelMesher()
					.register(e.getValue(), 0,
							new ModelResourceLocation(PowerAdvantage.MODID + ":" + e.getKey(), "inventory"));
		}
	}
}
