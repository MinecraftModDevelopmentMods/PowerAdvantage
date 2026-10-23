package zone.moddev.mc.poweradvantage.recipe;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.RecipeMode;

/** Registers the data-pack conditions used by PowerAdvantage recipe modes. */
public final class RecipeConditions {
    private static boolean registered;

    private RecipeConditions() {}

    public static synchronized void register() {
        if (registered) return;
        CraftingHelper.register(new ResourceLocation(PowerAdvantage.MODID, "recipe_mode"), json -> {
            RecipeMode expected = RecipeMode.valueOf(JsonUtils.getString(json, "mode").toUpperCase());
            return () -> PowerAdvantage.recipeMode == expected;
        });
        CraftingHelper.register(new ResourceLocation(PowerAdvantage.MODID, "not_recipe_mode"), json -> {
            RecipeMode excluded = RecipeMode.valueOf(JsonUtils.getString(json, "mode").toUpperCase());
            return () -> PowerAdvantage.recipeMode != excluded;
        });
        CraftingHelper.register(new ResourceLocation(PowerAdvantage.MODID, "tag_empty"), json -> {
            ResourceLocation tagId = new ResourceLocation(JsonUtils.getString(json, "tag"));
            return () -> ItemTags.getCollection().getOrCreate(tagId).getAllElements().isEmpty();
        });
        CraftingHelper.register(new ResourceLocation(PowerAdvantage.MODID, "tag_not_empty"), json -> {
            ResourceLocation tagId = new ResourceLocation(JsonUtils.getString(json, "tag"));
            return () -> !ItemTags.getCollection().getOrCreate(tagId).getAllElements().isEmpty();
        });
        registered = true;
    }
}
