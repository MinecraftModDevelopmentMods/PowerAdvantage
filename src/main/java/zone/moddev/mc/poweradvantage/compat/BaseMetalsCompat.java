package zone.moddev.mc.poweradvantage.compat;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/**
 * Compatibility entry points retained for old internal callers. Crushing recipes
 * are data-driven in Base Metals 1.13 and live under data/&lt;modid&gt;/recipes.
 */
public final class BaseMetalsCompat {
    private BaseMetalsCompat() {}

    public static boolean isLoaded() { return ModList.get().isLoaded("basemetals"); }

    public static void setStrongHammers(boolean enabled) {
        PowerAdvantage.LOGGER.debug("Ignoring legacy strong-hammers setting on Base Metals 1.13");
    }

    public static void addCrusherRecipe(String inputTag, ItemStack output) {
        PowerAdvantage.LOGGER.debug("Crushing recipe for {} is supplied as data in 1.13", inputTag);
    }

    public static void addCrusherRecipe(Block input, ItemStack output) {
        PowerAdvantage.LOGGER.debug("Crushing recipe for {} is supplied as data in 1.13", input.getRegistryName());
    }

    public static ItemStack getSteelPlate() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("basemetals", "steel_plate"));
        return item == null ? null : new ItemStack(item);
    }

    public static void registerFuelForOre(String tagName, short burnTime) {
        PowerAdvantage.LOGGER.debug("Fuel tag {} is data-driven in 1.13", tagName);
    }
}
