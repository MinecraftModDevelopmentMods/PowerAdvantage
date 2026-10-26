package zone.moddev.mc.poweradvantage.recipe;

import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.RecipeMode;

/** Registers the data-pack conditions used by PowerAdvantage recipe modes. */
public final class RecipeConditions {
    private static boolean registered;

    private RecipeConditions() {}

    public static synchronized void register() {
        if (registered) return;
        for (String name : new String[] {"recipe_mode", "not_recipe_mode", "tag_empty", "tag_not_empty"}) {
            CraftingHelper.register(new Serializer(name));
        }
        registered = true;
    }

    private static final class Condition implements ICondition {
        private final ResourceLocation id;
        private final String value;

        private Condition(ResourceLocation id, String value) {
            this.id = id;
            this.value = value;
        }

        @Override public ResourceLocation getID() { return id; }

        @Override public boolean test() {
            switch (id.getPath()) {
                case "recipe_mode": return PowerAdvantage.recipeMode == RecipeMode.valueOf(value.toUpperCase(Locale.ROOT));
                case "not_recipe_mode": return PowerAdvantage.recipeMode != RecipeMode.valueOf(value.toUpperCase(Locale.ROOT));
                case "tag_empty": return ItemTags.getCollection().getOrCreate(new ResourceLocation(value)).getAllElements().isEmpty();
                case "tag_not_empty": return !ItemTags.getCollection().getOrCreate(new ResourceLocation(value)).getAllElements().isEmpty();
                default: throw new IllegalStateException("Unknown Advantage condition " + id);
            }
        }
    }

    private static final class Serializer implements IConditionSerializer<Condition> {
        private final ResourceLocation id;
        private final String key;

        private Serializer(String name) {
            id = new ResourceLocation(PowerAdvantage.MODID, name);
            key = name.contains("recipe_mode") ? "mode" : "tag";
        }

        @Override public ResourceLocation getID() { return id; }
        @Override public Condition read(JsonObject json) { return new Condition(id, JSONUtils.getString(json, key)); }
        @Override public void write(JsonObject json, Condition condition) { json.addProperty(key, condition.value); }
    }
}
