package zone.moddev.mc.poweradvantage.init;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.items.RotationTool;

/** Stable item registry for PowerAdvantage 1.13. */
@Mod.EventBusSubscriber(modid = PowerAdvantage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Items {
    public static Item starch;
    public static Item bioplastic_ingot;
    public static Item sprocket;
    public static Item rotator_tool;
    public static Item crude_oil_bucket;
    public static Item refined_oil_bucket;

    public static final Map<String, Item> allItems = new LinkedHashMap<>();
    private static boolean initialized;

    private Items() {}

    public static synchronized void init() {
        if (initialized) return;
        Blocks.init();
        starch = add("starch", new Item(properties()));
        bioplastic_ingot = add("bioplastic_ingot", new Item(properties()));
        sprocket = add("sprocket", new Item(properties()));
        rotator_tool = add("rotator_tool", new RotationTool(properties().maxStackSize(1)));
        crude_oil_bucket = add("crude_oil_bucket", new ItemBucket(Fluids.crude_oil,
                new Item.Properties().group(ItemGroups.tab_powerAdvantage).maxStackSize(1)));
        refined_oil_bucket = add("refined_oil_bucket", new ItemBucket(Fluids.refined_oil,
                new Item.Properties().group(ItemGroups.tab_powerAdvantage).maxStackSize(1)));
        initialized = true;
    }

    private static Item.Properties properties() {
        return new Item.Properties().group(ItemGroups.tab_powerAdvantage);
    }

    private static Item add(String id, Item item) {
        item.setRegistryName(PowerAdvantage.MODID, id);
        allItems.put(id, item);
        return item;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        init();
        event.getRegistry().registerAll(allItems.values().toArray(new Item[0]));
    }

    public static Item getItemByName(String id) { return allItems.get(id); }
    public static Set<Map.Entry<String, Item>> getAllRegisteredItems() { return allItems.entrySet(); }
}
