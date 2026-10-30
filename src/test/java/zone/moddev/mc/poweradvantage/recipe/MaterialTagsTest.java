package zone.moddev.mc.poweradvantage.recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITagCollection;
import net.minecraft.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class MaterialTagsTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.bootStrap(); }

    @Test public void updatesNativeImmutableCollectionWithoutLosingUserEntries() {
        ITagCollection<Item> tags = fixture();
        try {
            tags.getAllTags().put(new ResourceLocation("test", "must_be_immutable"), null);
            fail("Fixture must reproduce native immutable tag maps");
        } catch (UnsupportedOperationException expected) { }
        ITagCollection<Item> original = tags;
        tags = MaterialTags.applyPolicy(tags, Items.APPLE, true, true);
        assertFalse(tag(original, "forge:rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:rubber").contains(Items.STICK));
        assertTrue(tag(tags, "forge:rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:ingots/rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:solder").contains(Items.IRON_INGOT));
        assertTrue(tag(tags, "forge:solder").contains(Items.GOLD_INGOT));
        assertTrue(tag(tags, "test:unrelated").contains(Items.REDSTONE));
        tags = MaterialTags.applyPolicy(tags, Items.APPLE, true, true);
        assertEquals(2, tag(tags, "forge:rubber").getValues().size());
        assertEquals(2, tag(tags, "forge:solder").getValues().size());
    }

    @Test public void disabledOptionsDoNotAddAliases() {
        ITagCollection<Item> tags = fixture();
        assertSame(tags, MaterialTags.applyPolicy(tags, Items.APPLE, false, false));
        assertFalse(tag(tags, "forge:rubber").contains(Items.APPLE));
        assertTrue(tag(tags, "forge:solder").getValues().isEmpty());
        assertTrue(tag(tags, "test:unrelated").contains(Items.REDSTONE));
    }

    private static ITag<Item> tag(ITagCollection<Item> tags, String name) { return tags.getTagOrEmpty(new ResourceLocation(name)); }

    private static ITagCollection<Item> fixture() {
        Map<ResourceLocation, ITag<Item>> tags = new LinkedHashMap<>();
        tags.put(new ResourceLocation("forge:rubber"), singleton(Items.STICK));
        tags.put(new ResourceLocation("forge:ingots/lead"), singleton(Items.IRON_INGOT));
        tags.put(new ResourceLocation("forge:ingots/tin"), singleton(Items.GOLD_INGOT));
        tags.put(new ResourceLocation("test:unrelated"), singleton(Items.REDSTONE));
        return ITagCollection.of(tags);
    }

    private static ITag<Item> singleton(Item item) { return ITag.fromSet(java.util.Collections.singleton(item)); }
}
