package zone.moddev.mc.poweradvantage.config;

import java.util.Arrays;
import java.util.Locale;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import cyano.poweradvantage.api.FluidCategoryRegistry;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.RecipeMode;

/** Common configuration with the same gameplay defaults as the legacy cfg file. */
public final class PowerAdvantageConfig {
    private static final String DEFAULT_DISTILLATION = "2*crude_oil->1*refined_oil";

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> RECIPE_MODE;
    public static final ForgeConfigSpec.DoubleValue CHEST_LOOT_FACTOR;
    public static final ForgeConfigSpec.BooleanValue PLASTIC_IS_RUBBER;
    public static final ForgeConfigSpec.BooleanValue USE_OTHER_FLUIDS;
    public static final ForgeConfigSpec.ConfigValue<String> DISTILLER_RECIPES;
    public static final ForgeConfigSpec.ConfigValue<String> ADDITIONAL_CRUDE_OIL_ALIASES;
    public static final ForgeConfigSpec.ConfigValue<String> RF_CONVERSIONS;
    public static final ForgeConfigSpec.ConfigValue<String> TECH_REBORN_CONVERSIONS;

    private static boolean loaded;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("options");
        RECIPE_MODE = builder.comment("NORMAL, TECH_PROGRESSION, or APOCALYPTIC. Restart after changing.")
                .worldRestart().define("recipeMode", "NORMAL");
        CHEST_LOOT_FACTOR = builder.defineInRange("treasureChestLootFactor", 0.5D, 0.0D, 1000.0D);
        PLASTIC_IS_RUBBER = builder.define("plasticEqualsRubber", true);
        USE_OTHER_FLUIDS = builder.define("useOtherFluids", false);
        builder.pop();
        builder.push("recipes");
        DISTILLER_RECIPES = builder.define("distillerRecipes", DEFAULT_DISTILLATION);
        builder.pop();
        builder.push("compatibility");
        ADDITIONAL_CRUDE_OIL_ALIASES = builder.comment(
                "Semi-colon delimited fluid registry names to treat as crude oil. Built-in aliases are always retained.")
                .define("additionalCrudeOilFluidAliases", "");
        builder.pop();
        builder.push("energy");
        RF_CONVERSIONS = builder.comment(
                "Forge Energy units per Advantage energy unit for the historical RF-labelled converters.")
                .define("rfConversions", "steam=8;electricity=0.25;quantum=8");
        TECH_REBORN_CONVERSIONS = builder.comment(
                "Forge Energy units per Advantage energy unit for the historical Tech Reborn-labelled converter.")
                .define("techRebornConversions", "electricity=0.25");
        builder.pop();
        SPEC = builder.build();
    }

    private PowerAdvantageConfig() {}

    public static void onConfigLoading(ModConfig.Loading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        loaded = true;
        apply();
    }

    public static void apply() {
        if (!loaded) return;
        String configuredMode = RECIPE_MODE.get().trim().toUpperCase(Locale.ROOT);
        try {
            PowerAdvantage.recipeMode = RecipeMode.valueOf(configuredMode);
        } catch (IllegalArgumentException ex) {
            PowerAdvantage.LOGGER.warn("Unknown recipe mode '{}'; using NORMAL", configuredMode);
            PowerAdvantage.recipeMode = RecipeMode.NORMAL;
        }
        PowerAdvantage.chestLootFactor = CHEST_LOOT_FACTOR.get().floatValue();
        PowerAdvantage.plasticIsAlsoRubber = PLASTIC_IS_RUBBER.get();
        PowerAdvantage.useOtherFluids = USE_OTHER_FLUIDS.get();
        for (String alias : ADDITIONAL_CRUDE_OIL_ALIASES.get().split(";")) {
            if (!alias.trim().isEmpty()) {
                FluidCategoryRegistry.registerAlias(FluidCategoryRegistry.CRUDE_OIL, alias);
            }
        }
    }

    public static String[] distillationRecipes() {
        if (!loaded) return new String[] { DEFAULT_DISTILLATION };
        return Arrays.stream(DISTILLER_RECIPES.get().split(";"))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
    }

    public static String rfConversions() {
        return loaded ? RF_CONVERSIONS.get() : "steam=8;electricity=0.25;quantum=8";
    }

    public static String techRebornConversions() {
        return loaded ? TECH_REBORN_CONVERSIONS.get() : "electricity=0.25";
    }
}
