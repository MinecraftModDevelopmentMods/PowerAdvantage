package zone.moddev.mc.advantageworks;

import java.util.Collections;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.FlatChunkGenerator;
import net.minecraft.world.gen.FlatGenerationSettings;
import net.minecraft.world.gen.FlatLayerInfo;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;
import net.minecraftforge.common.world.ForgeWorldType;
import net.minecraftforge.event.RegistryEvent;

final class Native116TestFlat {
    static final String ID = "advantageworkstest:test_flat";

    private Native116TestFlat() {}

    static ForgeWorldType createType() {
        return new ForgeWorldType((biomes, dimensions, seed, settings) -> {
            if (!settings.isEmpty()) throw new IllegalArgumentException("Test flat creation settings must be empty");
            FlatGenerationSettings flat = new FlatGenerationSettings(
                    new DimensionStructuresSettings(Optional.empty(), Collections.emptyMap()), biomes);
            flat.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
            flat.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
            flat.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
            flat.updateLayers();
            return new FlatChunkGenerator(flat);
        }).setRegistryName(new ResourceLocation(ID));
    }

    static void register(RegistryEvent.Register<ForgeWorldType> event) {
        event.getRegistry().register(createType());
    }
}
