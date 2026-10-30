package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenSettingsExport;
import net.minecraft.world.gen.FlatChunkGenerator;
import net.minecraft.world.gen.FlatGenerationSettings;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class Native116TestFlatTest {
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void constructorHasExactLayersAndNoStructures() {
        DynamicRegistries registries = DynamicRegistries.builtin();
        FlatChunkGenerator generator = (FlatChunkGenerator) Native116TestFlat.createType().createChunkGenerator(
                registries.registryOrThrow(Registry.BIOME_REGISTRY),
                registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY), 42L, "");
        FlatGenerationSettings flat = generator.settings();
        assertEquals(3, flat.getLayersInfo().size());
        BlockState[] layers = flat.getLayers();
        assertEquals(Blocks.BEDROCK.defaultBlockState(), layers[0]);
        assertEquals(Blocks.DIRT.defaultBlockState(), layers[1]);
        assertEquals(Blocks.DIRT.defaultBlockState(), layers[2]);
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), layers[3]);
        for (int y = 4; y < layers.length; y++) assertNull(layers[y]);
        assertTrue(flat.structureSettings().structureConfig().isEmpty());
        assertNull(flat.structureSettings().stronghold());
    }

    @Test public void fullSaveCodecRetainsVanillaFlatIdentityAndAllDimensions() {
        DynamicRegistries registries = DynamicRegistries.builtin();
        DimensionGeneratorSettings settings = Native116TestFlat.createType().createSettings(registries, 42L, false, false, "");
        JsonObject saved = DimensionGeneratorSettings.CODEC.encodeStart(WorldGenSettingsExport.create(JsonOps.INSTANCE, registries), settings)
                .result().orElseThrow(() -> new AssertionError("Native settings codec did not encode")).getAsJsonObject();
        assertEquals(42L, saved.get("seed").getAsLong());
        assertFalse(saved.get("generate_features").getAsBoolean());
        assertFalse(saved.get("bonus_chest").getAsBoolean());
        JsonObject dimensions = saved.getAsJsonObject("dimensions");
        assertEquals(3, dimensions.entrySet().size());
        assertTrue(dimensions.has("minecraft:the_nether"));
        assertTrue(dimensions.has("minecraft:the_end"));
        JsonObject generator = dimensions.getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("minecraft:flat", generator.get("type").getAsString());
        JsonObject flat = generator.getAsJsonObject("settings");
        assertEquals("minecraft:plains", flat.get("biome").getAsString());
        assertEquals(3, flat.getAsJsonArray("layers").size());
        assertEquals(0, flat.getAsJsonObject("structures").getAsJsonObject("structures").entrySet().size());
        assertFalse(flat.getAsJsonObject("structures").has("stronghold"));
        assertFalse(saved.toString().contains(Native116TestFlat.ID));
    }

    @Test public void flatJsonCreationSettingsAreRefused() {
        DynamicRegistries registries = DynamicRegistries.builtin();
        try {
            Native116TestFlat.createType().createChunkGenerator(registries.registryOrThrow(Registry.BIOME_REGISTRY),
                    registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY), 42L, "{}");
            fail("Flat JSON must not enter the vanilla JsonOps path");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must be empty"));
        }
    }
}
