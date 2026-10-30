package zone.moddev.mc.poweradvantage.recipe;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITagCollection;
import net.minecraft.tags.TagCollectionReader;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.RecipeMode;
import zone.moddev.mc.poweradvantage.init.Items;

/** Config-dependent aliases resolved by the server's native tag reload. */
public final class MaterialTags {
    private MaterialTags() {}

    public static void installReader(AddReloadListenerEvent event) {
        // The event runs during construction, before the first reload starts.
        // Clients receive the server's resolved tags and never apply local policy.
        event.getDataPackRegistries().tagManager.items = new TagCollectionReader<Item>(
                Registry.ITEM::getOptional, "tags/items", "item") {
            @Override public ITagCollection<Item> load(Map<ResourceLocation, ITag.Builder> builders) {
                ITagCollection<Item> tags = super.load(builders);
                if (ModList.get() == null || Items.bioplastic_ingot == null) return tags;
                return applyPolicy(tags, Items.bioplastic_ingot, PowerAdvantage.plasticIsAlsoRubber,
                        PowerAdvantage.recipeMode == RecipeMode.NORMAL
                                && ModList.get().isLoaded("electricadvantage"));
            }
        };
    }

    static ITagCollection<Item> applyPolicy(ITagCollection<Item> tags, Item plastic,
            boolean rubber, boolean solder) {
        if (!rubber && !solder) return tags;
        Map<ResourceLocation, ITag<Item>> result = new LinkedHashMap<>(tags.getAllTags());
        if (rubber) {
            append(result, "forge:rubber", java.util.Collections.singleton(plastic));
            append(result, "forge:ingots/rubber", java.util.Collections.singleton(plastic));
        }
        if (solder) {
            for (String metal : new String[] {"lead", "tin", "silver"}) {
                append(result, "forge:solder", tags.getTagOrEmpty(
                        new ResourceLocation("forge", "ingots/" + metal)).getValues());
            }
        }
        return ITagCollection.of(result);
    }

    private static void append(Map<ResourceLocation, ITag<Item>> tags, String name,
            java.util.Collection<Item> additions) {
        ResourceLocation id = new ResourceLocation(name);
        Set<Item> values = new LinkedHashSet<>();
        ITag<Item> existing = tags.get(id);
        if (existing != null) values.addAll(existing.getValues());
        for (Item item : additions) if (item != null) values.add(item);
        tags.put(id, ITag.fromSet(values));
    }
}
