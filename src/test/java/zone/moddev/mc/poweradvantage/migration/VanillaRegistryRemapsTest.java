package zone.moddev.mc.poweradvantage.migration;

import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import static org.junit.Assert.*;

public class VanillaRegistryRemapsTest {
    @Test public void retainsTheRenamedPigmanEntity() {
        expect("entity_type", "zombie_pigman", "zombified_piglin");
    }

    @Test public void retainsTheRenamedPigmanEgg() {
        expect("item", "zombie_pigman_spawn_egg", "zombified_piglin_spawn_egg");
    }

    @Test public void retainsEachEquivalentPigmanSound() {
        for (String name : new String[] {"ambient", "angry", "death", "hurt"})
            expect("sound_event", "entity.zombie_pigman." + name, "entity.zombified_piglin." + name);
    }

    @Test public void retainsTheFormerNetherBiomeMusic() {
        expect("sound_event", "music.nether", "music.nether.nether_wastes");
    }

    @Test public void retainsTheFormerNetherBiome() {
        expect("worldgen/biome", "nether", "nether_wastes");
    }

    @Test public void leavesOtherModsAndUnrecognizedVanillaEntriesAlone() {
        assertNull(VanillaRegistryRemaps.replacement(id("item"), new ResourceLocation("othermod:zombie_pigman_spawn_egg")));
        assertNull(VanillaRegistryRemaps.replacement(id("item"), id("unknown_item")));
        assertNull(VanillaRegistryRemaps.replacement(id("sound_event"), id("music.unknown")));
    }

    @Test public void neverAppliesAnAliasToTheWrongRegistry() {
        assertNull(VanillaRegistryRemaps.replacement(id("block"), id("zombie_pigman")));
        assertNull(VanillaRegistryRemaps.replacement(id("item"), id("entity.zombie_pigman.hurt")));
    }

    private static void expect(String registry, String from, String to) {
        assertEquals(id(to), VanillaRegistryRemaps.replacement(id(registry), id(from)));
    }

    private static ResourceLocation id(String name) { return new ResourceLocation("minecraft", name); }
}
