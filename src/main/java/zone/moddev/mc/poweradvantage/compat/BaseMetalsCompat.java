package zone.moddev.mc.poweradvantage.compat;

import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameData;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class BaseMetalsCompat {

	private static final String MODID = "basemetals";
	private static final ResourceLocation STEEL_BLOCK = new ResourceLocation(MODID, "steel_block");
	private static final String[] CRUSHER_REGISTRY_CLASSES = {
			"cyano.basemetals.registry.CrusherRecipeRegistry",
			"com.mcmoddev.lib.registry.CrusherRecipeRegistry"
	};

	private BaseMetalsCompat() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static boolean isLoaded() {
		return Loader.isModLoaded(MODID);
	}

	public static void registerOreDictionaryFallbacks() {
		registerOreDictionaryFallbacks(OreDictionary::getOres, OreDictionary::registerOre,
				id -> GameData.getItemRegistry().getObject(id));
	}

	static void registerOreDictionaryFallbacks(Function<String, List<ItemStack>> ores,
			BiConsumer<String, ItemStack> register, Function<ResourceLocation, Item> items) {
		if (ores.apply("dustCarbon").isEmpty()) {
			for (String source : new String[]{"dustCoal", "dustCharcoal"}) {
				for (ItemStack stack : ores.apply(source)) {
					if (stack != null && stack.stackSize > 0 && stack.getItem() != null) {
						register.accept("dustCarbon", stack.copy());
					}
				}
			}
		}
		if (ores.apply("blockSteel").isEmpty()) {
			Item item = items.apply(STEEL_BLOCK);
			if (item instanceof ItemBlock && STEEL_BLOCK.equals(item.getRegistryName())
					&& STEEL_BLOCK.equals(((ItemBlock)item).getBlock().getRegistryName())) {
				register.accept("blockSteel", new ItemStack(item, 1));
			}
		}
	}

	public static void setStrongHammers(boolean enabled) {
		if (!isLoaded()) return;
		if (setOldStrongHammers(enabled)) return;
		if (setNewStrongHammers(enabled)) return;
		FMLLog.warning("%s: BaseMetals is loaded, but no known strong_hammers option was found", PowerAdvantage.MODID);
	}

	public static void addCrusherRecipe(String oreDictionaryName, ItemStack output) {
		if (!isLoaded()) return;
		invokeCrusherRecipe(new Class<?>[]{String.class, ItemStack.class}, new Object[]{oreDictionaryName, output});
	}

	public static void addCrusherRecipe(Block input, ItemStack output) {
		if (!isLoaded()) return;
		invokeCrusherRecipe(new Class<?>[]{Block.class, ItemStack.class}, new Object[]{input, output});
	}

	public static ItemStack getSteelPlate() {
		if (!isLoaded()) return null;

		ItemStack stack = firstOre("plateSteel", 1);
		if (stack != null) return stack;

		Block block = GameData.getBlockRegistry().getObject(new ResourceLocation(MODID, "steel_plate"));
		if (block != null) {
			Item item = Item.getItemFromBlock(block);
			if (item != null) return new ItemStack(block, 1);
		}

		FMLLog.warning("%s: could not resolve BaseMetals steel plate for crusher output", PowerAdvantage.MODID);
		return null;
	}

	public static void registerFuelForOre(String oreDictionaryName, short burnTime) {
		List<ItemStack> ores = OreDictionary.getOres(oreDictionaryName);
		for (ItemStack ore : ores) {
			if (ore != null && ore.getItem() != null) {
				FuelRegistry.getInstance().registerFuel(ore.getItem(), burnTime);
			}
		}
	}

	private static ItemStack firstOre(String oreDictionaryName, int count) {
		List<ItemStack> ores = OreDictionary.getOres(oreDictionaryName);
		if (ores.isEmpty()) return null;
		ItemStack copy = ores.get(0).copy();
		copy.stackSize = count;
		return copy;
	}

	private static boolean setOldStrongHammers(boolean enabled) {
		try {
			Class<?> baseMetals = Class.forName("cyano.basemetals.BaseMetals");
			Field field = baseMetals.getField("strongHammers");
			field.setBoolean(null, enabled);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			FMLLog.log(Level.WARN, e, "%s: failed to set old BaseMetals strong_hammers", PowerAdvantage.MODID);
			return false;
		}
	}

	private static boolean setNewStrongHammers(boolean enabled) {
		try {
			Class<?> options = Class.forName("com.mcmoddev.lib.util.ConfigBase$Options");
			Method setter = options.getMethod("setStrongHammers", boolean.class);
			setter.invoke(null, enabled);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			FMLLog.log(Level.WARN, e, "%s: failed to set new BaseMetals strong_hammers", PowerAdvantage.MODID);
			return false;
		}
	}

	private static void invokeCrusherRecipe(Class<?>[] parameterTypes, Object[] args) {
		for (String className : CRUSHER_REGISTRY_CLASSES) {
			try {
				Class<?> registry = Class.forName(className);
				Method method = registry.getMethod("addNewCrusherRecipe", parameterTypes);
				method.invoke(null, args);
				return;
			} catch (ClassNotFoundException e) {
				// Try the next known BaseMetals package.
			} catch (ReflectiveOperationException | RuntimeException e) {
				FMLLog.log(Level.WARN, e, "%s: failed to add BaseMetals crusher recipe through %s", PowerAdvantage.MODID, className);
				return;
			}
		}
		FMLLog.warning("%s: BaseMetals is loaded, but no known crusher recipe registry was found", PowerAdvantage.MODID);
	}
}
