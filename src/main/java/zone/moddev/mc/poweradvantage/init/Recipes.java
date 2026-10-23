package zone.moddev.mc.poweradvantage.init;

import zone.moddev.mc.poweradvantage.PowerAdvantage;
import cyano.poweradvantage.api.FluidCategoryRegistry;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import zone.moddev.mc.poweradvantage.registry.still.recipe.DistillationRecipeRegistry;


public abstract class Recipes {

	/*
	 * Normal - can craft all necessary machine parts
	 * Apocalyptic - must find key parts as treasure in chests
	 * Tech-progression - making the first key part is very complicated, but once made, key parts can be duplicated fairly easily
	 */


	public static void initDistillationRecipes(String[] distillRecipes) {
		for (String recipe : distillRecipes) {
			String r = recipe.trim();
			if (r.isEmpty()) continue;
			try {
				int numIn, numOut;
				String fluidIn, fluidOut;
				String inputStr = r.substring(0, r.indexOf("->")).trim();
				numIn = Integer.parseInt(inputStr.substring(0, inputStr.indexOf('*')).trim());
				fluidIn = inputStr.substring(inputStr.indexOf('*') + 1).trim();
				String outputStr = r.substring(inputStr.length() + "->".length()).trim();
				numOut = Integer.parseInt(outputStr.substring(0, outputStr.indexOf('*')).trim());
				fluidOut = outputStr.substring(outputStr.indexOf('*') + 1).trim();
				DistillationRecipeRegistry.addDistillationRecipe(fluidIn, numIn, fluidOut, numOut);
			} catch (Exception ex) {
				PowerAdvantage.LOGGER.error("Failed to add fluid distillation recipe '{}'", r, ex);
			}
		}
		initCrudeOilAliasDistillationRecipes();
	}

	private static void initCrudeOilAliasDistillationRecipes() {
		Fluid refinedOil = Fluids.refined_oil;
		if (refinedOil == null) return;
		ResourceLocation outputId = IRegistry.field_212619_h.getKey(refinedOil);
		if (outputId == null) return;

		for (Fluid input : FluidCategoryRegistry.getRegisteredFluids(FluidCategoryRegistry.CRUDE_OIL)) {
			if (DistillationRecipeRegistry.getInstance().getDistillationRecipeForFluid(input) != null) continue;
			ResourceLocation inputId = IRegistry.field_212619_h.getKey(input);
			if (inputId != null) {
				DistillationRecipeRegistry.addDistillationRecipe(inputId.toString(), 2, outputId.toString(), 1);
			}
		}
		DistillationRecipeRegistry.clearRecipeCache();
	}

}
