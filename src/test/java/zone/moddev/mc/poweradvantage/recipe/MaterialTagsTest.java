package zone.moddev.mc.poweradvantage.recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class MaterialTagsTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.register(); }

    @Test public void updatesNativeImmutableCollectionWithoutLosingUserEntries() {
        TagCollection<Item> tags = fixture();
        try {
            tags.getTagMap().put(new ResourceLocation("test", "must_be_immutable"), null);
            fail("Fixture must reproduce native immutable tag maps");
        } catch (UnsupportedOperationException expected) { }
        MaterialTags.applyPolicy(tags, Items.APPLE, true, true);
        assertTrue(tag(tags, "forge:rubber").contains(Items.STICK));
        assertTrue(tag(tags, "forge:rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:ingots/rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:solder").contains(Items.IRON_INGOT));
        assertTrue(tag(tags, "forge:solder").contains(Items.GOLD_INGOT));
        assertTrue(tag(tags, "test:unrelated").contains(Items.REDSTONE));
        MaterialTags.applyPolicy(tags, Items.APPLE, true, true);
        assertEquals(2, tag(tags, "forge:rubber").getAllElements().size());
        assertEquals(2, tag(tags, "forge:solder").getAllElements().size());
    }

    @Test public void disabledOptionsDoNotAddAliases() {
        TagCollection<Item> tags = fixture();
        MaterialTags.applyPolicy(tags, Items.APPLE, false, false);
        assertFalse(tag(tags, "forge:rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:solder").getAllElements().isEmpty());
        assertTrue(tag(tags, "test:unrelated").contains(Items.REDSTONE));
    }

    private static Tag<Item> tag(TagCollection<Item> tags, String name) { return tags.getOrCreate(new ResourceLocation(name)); }

    private static TagCollection<Item> fixture() {
        TagCollection<Item> tags = new TagCollection<>(id -> Optional.empty(), "tags/items", false, "item");
        Map<ResourceLocation, Tag.Builder<Item>> builders = new LinkedHashMap<>();
        builders.put(new ResourceLocation("forge:rubber"), Tag.Builder.<Item>create().add(Items.STICK));
        builders.put(new ResourceLocation("forge:ingots/lead"), Tag.Builder.<Item>create().add(Items.IRON_INGOT));
        builders.put(new ResourceLocation("forge:ingots/tin"), Tag.Builder.<Item>create().add(Items.GOLD_INGOT));
        builders.put(new ResourceLocation("test:unrelated"), Tag.Builder.<Item>create().add(Items.REDSTONE));
        tags.registerAll(builders);
        return tags;
    }
}
