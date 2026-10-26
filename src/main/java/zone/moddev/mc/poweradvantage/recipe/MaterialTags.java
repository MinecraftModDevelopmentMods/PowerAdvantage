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

    public static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
        // A remote client's local config must never change tags supplied by its server.
        if (server != null && server.getNetworkTagManager() == event.getTagManager()) finishReload();
    }

    public static void finishReload() {
        // DedicatedServer constructs an initial vanilla resource manager before FML loads mods.
        if (ModList.get() == null || Items.bioplastic_ingot == null) return;
        TagCollection<Item> tags = ItemTags.getCollection();
        applyPolicy(tags, Items.bioplastic_ingot, PowerAdvantage.plasticIsAlsoRubber,
                PowerAdvantage.recipeMode == RecipeMode.NORMAL && ModList.get().isLoaded("electricadvantage"));
        ItemTags.setCollection(tags);
    }

    static void applyPolicy(TagCollection<Item> tags, Item plastic, boolean rubber, boolean solder) {
        if (!rubber && !solder) return;
        // Resolved 1.14 collections are immutable. Rebuild through their native registration API.
        java.util.Map<ResourceLocation, Tag.Builder<Item>> builders = new java.util.LinkedHashMap<>();
        tags.getTagMap().forEach((id, tag) -> {
            Tag.Builder<Item> builder = Tag.Builder.create();
            tag.getAllElements().forEach(builder::add);
            builders.put(id, builder);
        });
        if (rubber) {
            append(builders, "forge:rubber", java.util.Collections.singleton(plastic));
            append(builders, "forge:ingots/rubber", java.util.Collections.singleton(plastic));
        }
        if (solder) {
            for (String metal : new String[] {"lead", "tin", "silver"}) {
                append(builders, "forge:solder", tags.getOrCreate(new ResourceLocation("forge", "ingots/" + metal)).getAllElements());
            }
        }
        tags.registerAll(builders);
    }

    private static void append(java.util.Map<ResourceLocation, Tag.Builder<Item>> builders,
            String id, java.util.Collection<Item> additions) {
        ResourceLocation name = new ResourceLocation(id);
        Tag.Builder<Item> builder = builders.computeIfAbsent(name, key -> Tag.Builder.create());
        for (Item item : additions) if (item != null) builder.add(item);
    }
}
