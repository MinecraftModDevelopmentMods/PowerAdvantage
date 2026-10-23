package zone.moddev.mc.poweradvantage.registry.still.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zone.moddev.mc.poweradvantage.PowerAdvantage;
import net.minecraft.fluid.Fluid;
import cyano.poweradvantage.api.fluid.FluidStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import zone.moddev.mc.poweradvantage.init.Fluids;

/**
 * This class handles all of the recipes for machines that "smelt" fluids.
 * @author DrCyano
 *
 */
public class DistillationRecipeRegistry {
	
	private final List<DistillationRecipe> recipes = new ArrayList<>(); 

	private final Map<Fluid,DistillationRecipe> recipeCache = new HashMap<>();
	
	
	
	private static final Lock initLock = new ReentrantLock();
	private static DistillationRecipeRegistry instance = null;
	
	/**
	 * Gets a singleton instance of DistillationRecipeRegistry
	 * @return A global instance of DistillationRecipeRegistry
	 */
	public static DistillationRecipeRegistry getInstance(){
		if(instance == null){
			initLock.lock();
			try{
				if(instance == null){
					// thread-safe singleton instantiation
					instance = new DistillationRecipeRegistry();
				}
			} finally{
				initLock.unlock();
			}
		}
		return instance;
	}

	
	/**
	 * This is the universal method for adding new Distillation recipes
	 * @param recipe A DistillationRecipe object. 
	 */
	public void addRecipe(DistillationRecipe recipe){
		recipes.add(recipe);
	}
	/**
	 * Clears the cache intended for speeding-up lookup operations. If you add recipes during 
	 * gameplay (in-world), then invoke this method to reset the cache or your new recipe may not 
	 * be used.
	 */
	public void clearCache(){
		recipeCache.clear();
	}
	/**
	 * Clears the cache intended for speeding-up lookup operations. If you add recipes during 
	 * gameplay (in-world), then invoke this method to reset the cache or your new recipe may not 
	 * be used. This method simply invokes <code>getInstance().clearCache();</code>
	 */
	public static void clearRecipeCache(){
		getInstance().clearCache();
	}
	
	/**
	 * This is the preferred method for adding a distillation recipe.
	 * @param inputFluidName The registry name (e.g. "water") of the input fluid
	 * @param inputFluidVolume The volume of input fluid consumed per application of this recipe
	 * @param outputFluidName The registry name (e.g. "lava") of the output fluid
	 * @param outputFluidVolume The volume of output fluid produced per application of this recipe
	 */
	public static void addDistillationRecipe(String inputFluidName, int inputFluidVolume, 
			String outputFluidName, int outputFluidVolume){
		if (Fluids.byName(inputFluidName) == null || Fluids.byName(outputFluidName) == null) {
			PowerAdvantage.LOGGER.warn("Skipping distillation recipe {}x{} -> {}x{} because a fluid is unavailable",
					inputFluidVolume, inputFluidName, outputFluidVolume, outputFluidName);
			return;
		}
		DistillationRecipe recipe = new DistillationRecipe(
				new RegistryNameFluidReference(inputFluidName),inputFluidVolume,
				new RegistryNameFluidReference(outputFluidName),outputFluidVolume);
		getInstance().addRecipe(recipe);
		PowerAdvantage.LOGGER.info("Added distillation recipe {}x{} -> {}x{}",
				inputFluidVolume,inputFluidName,outputFluidVolume,outputFluidName);
	}
	
	/**
	 * This method adds a distillation recipe that will only work on the specific fluids provided 
	 * from those specific mods. Equivalent fluids from other mods will not work. Normally, you 
	 * would not use this method to specify a recipe, but use the 
	 * <code>addDistillationRecipe(...)</code> method instead.
	 * @param inputFluidInstance The fluid instance of the input fluid
	 * @param inputFluidVolume The volume of input fluid consumed per application of this recipe
	 * @param outputFluidInstance The fluid instance of the output fluid
	 * @param outputFluidVolume The volume of output fluid produced per application of this recipe
	 */
	public static void addExplicitDistillationRecipe(Fluid inputFluidInstance, int inputFluidVolume, 
			Fluid outputFluidInstance, int outputFluidVolume){
		DistillationRecipe recipe = new DistillationRecipe(
				new ExplicitFluidReference(inputFluidInstance),inputFluidVolume,
				new ExplicitFluidReference(outputFluidInstance),outputFluidVolume);
		getInstance().addRecipe(recipe);
		PowerAdvantage.LOGGER.info("Added explicit distillation recipe {}x{} -> {}x{}",
				inputFluidVolume, id(inputFluidInstance), outputFluidVolume, id(outputFluidInstance));
	}
	
	/**
	 * Adds a distillation recipe by fluid stacks. Note that the recipe will use the names of the 
	 * fluids rather than their particular instance objects so that fluids of the same name from 
	 * different mods will function equally well.
	 * @param input The input fluid and amount
	 * @param output The output fluid and amount
	 */
	public static void addDistillationRecipe(FluidStack input, FluidStack output){
		String inputFluidName; int inputFluidVolume; 
		String outputFluidName; int outputFluidVolume;
		inputFluidName = id(input.getFluid());
		outputFluidName = id(output.getFluid());
		// factorize to simplify recipe to smallest ratio
		int gcf = factor(input.amount, output.amount);
		inputFluidVolume = input.amount / gcf;
		outputFluidVolume = output.amount / gcf;
		addDistillationRecipe(inputFluidName, inputFluidVolume, 
				outputFluidName, outputFluidVolume);
	}

	private static String id(Fluid fluid) {
		ResourceLocation id = IRegistry.field_212619_h.getKey(fluid);
		return id == null ? "unknown" : id.toString();
	}
	
	private static int factor(int a, int b){
		for(int f = Math.min(a, b); f > 1; f--){
			if(a % f == 0 && b % f == 0) return f;
		}
		return 1;
	}


	/**
	 * Looks-up the appropriate distillation recipe for a given input fluid.
	 * @param fluid The fluid that may or may not have a distillation recipe
	 * @return A DistillationRecipe, or null if there is no recipe for this fluid
	 */
	public DistillationRecipe getDistillationRecipeForFluid(Fluid fluid) {
		if(recipeCache.containsKey(fluid)){
			return recipeCache.get(fluid);
		} else {
			DistillationRecipe recipe = null;
			scan:{
				for(DistillationRecipe r : recipes){
					if(r.isValidInput(fluid)){
						recipe = r;
						break scan;
					}
				}
			}
			recipeCache.put(fluid, recipe);
			return recipe;
		}
	}
}
