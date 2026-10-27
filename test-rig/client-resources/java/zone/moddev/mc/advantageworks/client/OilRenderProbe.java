package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.LightType;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.tileentity.TileEntity;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.init.Items;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Exercises the native fluid renderer, not just oil's particle-only block model. */
final class OilRenderProbe {
    private OilRenderProbe() {}

    static void verify(Minecraft minecraft, JsonObject result) {
        // Before a world/server is opened, vanilla's fluid tags have not been synchronized.
        net.minecraft.tags.ITagCollectionSupplier previousTags = net.minecraft.tags.TagCollectionManager.getInstance();
        net.minecraft.tags.ITagCollection<Fluid> previousFluids = net.minecraft.tags.FluidTags.getAllTags();
        net.minecraft.tags.ITagCollectionSupplier restore = net.minecraft.tags.ITagCollectionSupplier.of(
                previousTags.getBlocks(), previousTags.getItems(), previousFluids, previousTags.getEntityTypes());
        net.minecraft.tags.TagRegistry<?> fluidRegistry = net.minecraft.tags.TagRegistryManager.get(new ResourceLocation("fluid"));
        Map<ResourceLocation, net.minecraft.tags.ITag<Fluid>> tags = new HashMap<>(previousFluids.getAllTags());
        tags.put(new ResourceLocation("minecraft", "water"), net.minecraft.tags.ITag.fromSet(
                new java.util.HashSet<>(java.util.Arrays.asList(net.minecraft.fluid.Fluids.WATER, net.minecraft.fluid.Fluids.FLOWING_WATER))));
        tags.put(new ResourceLocation("minecraft", "lava"), net.minecraft.tags.ITag.fromSet(
                new java.util.HashSet<>(java.util.Arrays.asList(net.minecraft.fluid.Fluids.LAVA, net.minecraft.fluid.Fluids.FLOWING_LAVA))));
        net.minecraft.tags.ITagCollectionSupplier temporary = net.minecraft.tags.ITagCollectionSupplier.of(
                previousTags.getBlocks(), previousTags.getItems(), net.minecraft.tags.ITagCollection.of(tags), previousTags.getEntityTypes());
        net.minecraft.tags.TagCollectionManager.bind(temporary);
        // The initial manager contains named wrappers, not resolved collections. Resetting
        // every registry would bind those wrappers to themselves and recurse indefinitely.
        fluidRegistry.reset(temporary);
        try {
        requireBucket(Items.crude_oil_bucket, Fluids.crude_oil);
        requireBucket(Items.refined_oil_bucket, Fluids.refined_oil);
        result.addProperty("oil_bucket_identities", true);

        JsonArray cases = new JsonArray();
        result.add("fluid_rendering", cases);
        boolean valid = true;
        valid &= check(minecraft, Fluids.crude_oil.defaultFluidState(), "poweradvantage:blocks/crude_oil_still", false, cases);
        valid &= check(minecraft, Fluids.flowing_crude_oil.getFlowing(5, false), "poweradvantage:blocks/crude_oil_flow", true, cases);
        valid &= check(minecraft, Fluids.refined_oil.defaultFluidState(), "poweradvantage:blocks/refined_oil_still", false, cases);
        valid &= check(minecraft, Fluids.flowing_refined_oil.getFlowing(5, false), "poweradvantage:blocks/refined_oil_flow", true, cases);
        valid &= check(minecraft, net.minecraft.fluid.Fluids.WATER.defaultFluidState(), "minecraft:block/water_still", false, cases);
        valid &= check(minecraft, net.minecraft.fluid.Fluids.LAVA.defaultFluidState(), "minecraft:block/lava_still", false, cases);
        if (!valid) throw new IllegalStateException("Native fluid renderer used the wrong sprite or tint: " + cases);
        } finally {
            fluidRegistry.reset(restore);
            net.minecraft.tags.TagCollectionManager.bind(previousTags);
            require(net.minecraft.tags.FluidTags.getAllTags() == previousFluids, "Synthetic fluid tags were not restored");
        }
    }

    private static void requireBucket(net.minecraft.item.Item bucket, Fluid expected) {
        net.minecraftforge.fluids.FluidStack contents = net.minecraftforge.fluids.FluidUtil.getFluidContained(new ItemStack(bucket))
                .orElseThrow(() -> new IllegalStateException("Bucket has no native fluid capability: " + bucket.getRegistryName()));
        if (!(bucket instanceof BucketItem) || ((BucketItem) bucket).getFluid() != expected
                || expected.getBucket() != bucket || contents.getFluid() != expected || contents.getAmount() != 1000) {
            throw new IllegalStateException("Wrong bucket fluid: " + bucket.getRegistryName());
        }
    }

    private static boolean check(Minecraft minecraft, FluidState state, String texture, boolean flowing, JsonArray cases) {
        // A tiny read-only neighbourhood supplies a slope for flowing sprites; no save is opened.
        IBlockDisplayReader world = new IBlockDisplayReader() {
            @Override public float getShade(Direction side, boolean shade) { return 1.0F; }
            @Override public TileEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) { return blockAt(state, flowing, pos); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getBrightness(LightType type, BlockPos pos) { return 15; }
            @Override public WorldLightManager getLightEngine() { throw new UnsupportedOperationException("Synthetic light is supplied by getLightFor"); }
            @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) {
                return resolver.getColor(net.minecraft.util.registry.WorldGenRegistries.BIOME.get(
                        net.minecraft.world.biome.Biomes.PLAINS), pos.getX(), pos.getZ());
            }
        };
        BufferBuilder buffer = new BufferBuilder(4096);
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        boolean rendered = minecraft.getBlockRenderer().renderLiquid(BlockPos.ZERO, world, buffer, state);
        buffer.end();
        com.mojang.datafixers.util.Pair<BufferBuilder.DrawState, ByteBuffer> built = buffer.popNextBuffer();
        int vertices = built.getFirst().vertexCount();
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(AtlasTexture.LOCATION_BLOCKS).apply(new ResourceLocation(texture));
        require(rendered && vertices >= 4, "Native fluid renderer emitted no top face for " + state);
        ByteBuffer data = built.getSecond();
        int uvOffset = elementOffset(VertexFormatElement.Usage.UV, 0);
        float u = data.getFloat(uvOffset);
        float v = data.getFloat(uvOffset + 4);
        int colorOffset = elementOffset(VertexFormatElement.Usage.COLOR, 0);
        int rgb = (data.get(colorOffset) & 255) << 16 | (data.get(colorOffset + 1) & 255) << 8 | data.get(colorOffset + 2) & 255;
        boolean correctSprite = !"missingno".equals(sprite.getName().getPath())
                && u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1();
        boolean correctTint = !(state.getType() instanceof Fluids.PowerFluid) || rgb == 0xFFFFFF;
        JsonObject entry = new JsonObject();
        entry.addProperty("fluid", state.getType().getRegistryName().toString());
        entry.addProperty("block", state.createLegacyBlock().getBlock().getRegistryName().toString());
        entry.addProperty("expected_sprite", texture);
        entry.addProperty("correct_sprite", correctSprite);
        entry.addProperty("vertex_rgb", String.format("%06X", rgb));
        entry.addProperty("vertices", vertices);
        cases.add(entry);
        return rendered && vertices > 0 && correctSprite && correctTint;
    }

    private static int elementOffset(VertexFormatElement.Usage usage, int index) {
        for (int i = 0; i < DefaultVertexFormats.BLOCK.getElements().size(); i++) {
            VertexFormatElement element = DefaultVertexFormats.BLOCK.getElements().get(i);
            if (element.getUsage() == usage && element.getIndex() == index) return DefaultVertexFormats.BLOCK.getOffset(i);
        }
        throw new IllegalStateException("Native block vertex format lacks " + usage + " " + index);
    }

    private static BlockState blockAt(FluidState state, boolean flowing, BlockPos pos) {
        if (BlockPos.ZERO.equals(pos)) return state.createLegacyBlock();
        if (flowing && BlockPos.ZERO.east().equals(pos)) {
            return ((FlowingFluid) state.getType()).getFlowing(3, false).createLegacyBlock();
        }
        return (pos.getY() < 0 ? net.minecraft.block.Blocks.STONE : net.minecraft.block.Blocks.AIR).defaultBlockState();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
