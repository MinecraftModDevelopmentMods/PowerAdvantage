package zone.moddev.mc.poweradvantage.recipe;

import net.minecraft.item.Item;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.RecipeMode;
import zone.moddev.mc.poweradvantage.init.Items;

/** Config-dependent legacy aliases, applied before recipes and advancements read tags. */
public final class MaterialTags {
    private MaterialTags() {}

    public static void finishReload() {
        // DedicatedServer constructs an initial vanilla resource manager before FML loads mods.
        if (ModList.get() == null || Items.bioplastic_ingot == null) return;
        TagCollection<Item> tags = ItemTags.getCollection();
        if (PowerAdvantage.plasticIsAlsoRubber) {
            append(tags, "forge:rubber", java.util.Collections.singleton(Items.bioplastic_ingot));
            append(tags, "forge:ingots/rubber", java.util.Collections.singleton(Items.bioplastic_ingot));
        }
        if (PowerAdvantage.recipeMode == RecipeMode.NORMAL && ModList.get().isLoaded("electricadvantage")) {
            for (String metal : new String[] {"lead", "tin", "silver"}) {
                append(tags, "forge:solder", tags.getOrCreate(new ResourceLocation("forge", "ingots/" + metal)).getAllElements());
            }
        }
        // Refresh wrappers after replacing immutable tags. The same collection is
        // also serialized by NetworkTagManager, so clients receive the server policy.
        ItemTags.setCollection(tags);
    }

    private static void append(TagCollection<Item> tags, String id, java.util.Collection<Item> additions) {
        ResourceLocation name = new ResourceLocation(id);
        Tag.Builder<Item> builder = Tag.Builder.create();
        for (Item item : tags.getOrCreate(name).getAllElements()) builder.add(item);
        for (Item item : additions) if (item != null) builder.add(item);
        tags.getTagMap().put(name, builder.build(name));
    }
}
