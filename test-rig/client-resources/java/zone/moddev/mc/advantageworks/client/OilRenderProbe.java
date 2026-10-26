package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IEnviromentBlockReader;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
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
        net.minecraft.tags.TagCollection<Fluid> previousTags = net.minecraft.tags.FluidTags.getCollection();
        net.minecraft.tags.TagCollection<Fluid> tags = new net.minecraft.tags.TagCollection<>(id ->
                Optional.ofNullable(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(id)), "tags/fluids", false, "fluid");
        Map<ResourceLocation, net.minecraft.tags.Tag.Builder<Fluid>> builders = new HashMap<>();
        builders.put(new ResourceLocation("minecraft", "water"), net.minecraft.tags.Tag.Builder.<Fluid>create()
                .add(net.minecraft.fluid.Fluids.WATER, net.minecraft.fluid.Fluids.FLOWING_WATER));
        builders.put(new ResourceLocation("minecraft", "lava"), net.minecraft.tags.Tag.Builder.<Fluid>create()
                .add(net.minecraft.fluid.Fluids.LAVA, net.minecraft.fluid.Fluids.FLOWING_LAVA));
        tags.registerAll(builders);
        net.minecraft.tags.FluidTags.setCollection(tags);
        try {
        requireBucket(Items.crude_oil_bucket, Fluids.crude_oil);
        requireBucket(Items.refined_oil_bucket, Fluids.refined_oil);
        result.addProperty("oil_bucket_identities", true);

        JsonArray cases = new JsonArray();
        result.add("fluid_rendering", cases);
        boolean valid = true;
        valid &= check(minecraft, Fluids.crude_oil.getDefaultState(), "poweradvantage:blocks/crude_oil_still", false, cases);
        valid &= check(minecraft, Fluids.flowing_crude_oil.getFlowingFluidState(5, false), "poweradvantage:blocks/crude_oil_flow", true, cases);
        valid &= check(minecraft, Fluids.refined_oil.getDefaultState(), "poweradvantage:blocks/refined_oil_still", false, cases);
        valid &= check(minecraft, Fluids.flowing_refined_oil.getFlowingFluidState(5, false), "poweradvantage:blocks/refined_oil_flow", true, cases);
        valid &= check(minecraft, net.minecraft.fluid.Fluids.WATER.getDefaultState(), "minecraft:block/water_still", false, cases);
        valid &= check(minecraft, net.minecraft.fluid.Fluids.LAVA.getDefaultState(), "minecraft:block/lava_still", false, cases);
        if (!valid) throw new IllegalStateException("Native fluid renderer used the wrong sprite or tint: " + cases);
        } finally { net.minecraft.tags.FluidTags.setCollection(previousTags); }
    }

    private static void requireBucket(net.minecraft.item.Item bucket, Fluid expected) {
        net.minecraftforge.fluids.FluidStack contents = net.minecraftforge.fluids.FluidUtil.getFluidContained(new ItemStack(bucket))
                .orElseThrow(() -> new IllegalStateException("Bucket has no native fluid capability: " + bucket.getRegistryName()));
        if (!(bucket instanceof BucketItem) || ((BucketItem) bucket).getFluid() != expected
                || expected.getFilledBucket() != bucket || contents.getFluid() != expected || contents.getAmount() != 1000) {
            throw new IllegalStateException("Wrong bucket fluid: " + bucket.getRegistryName());
        }
    }

    private static boolean check(Minecraft minecraft, IFluidState state, String texture, boolean flowing, JsonArray cases) {
        // A tiny read-only neighbourhood supplies a slope for flowing sprites; no save is opened.
        IEnviromentBlockReader world = new IEnviromentBlockReader() {
            @Override public TileEntity getTileEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) { return blockAt(state, flowing, pos); }
            @Override public IFluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getLightFor(LightType type, BlockPos pos) { return 15; }
            @Override public Biome getBiome(BlockPos pos) { return net.minecraft.world.biome.Biomes.PLAINS; }
        };
        BufferBuilder buffer = new BufferBuilder(4096);
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        boolean rendered = minecraft.getBlockRendererDispatcher().renderFluid(BlockPos.ZERO, world, buffer, state);
        buffer.finishDrawing();
        TextureAtlasSprite sprite = minecraft.getTextureMap().getSprite(new ResourceLocation(texture));
        require(rendered && buffer.getVertexCount() >= 4, "Native fluid renderer emitted no top face for " + state);
        ByteBuffer data = buffer.getByteBuffer();
        float u = data.getFloat(DefaultVertexFormats.BLOCK.getUvOffsetById(0));
        float v = data.getFloat(DefaultVertexFormats.BLOCK.getUvOffsetById(0) + 4);
        int colorOffset = DefaultVertexFormats.BLOCK.getColorOffset();
        int rgb = (data.get(colorOffset) & 255) << 16 | (data.get(colorOffset + 1) & 255) << 8 | data.get(colorOffset + 2) & 255;
        boolean correctSprite = !"missingno".equals(sprite.getName().getPath())
                && u >= sprite.getMinU() && u <= sprite.getMaxU() && v >= sprite.getMinV() && v <= sprite.getMaxV();
        boolean correctTint = !(state.getFluid() instanceof Fluids.PowerFluid) || rgb == 0xFFFFFF;
        JsonObject entry = new JsonObject();
        entry.addProperty("fluid", state.getFluid().getRegistryName().toString());
        entry.addProperty("block", state.getBlockState().getBlock().getRegistryName().toString());
        entry.addProperty("expected_sprite", texture);
        entry.addProperty("correct_sprite", correctSprite);
        entry.addProperty("vertex_rgb", String.format("%06X", rgb));
        entry.addProperty("vertices", buffer.getVertexCount());
        cases.add(entry);
        return rendered && buffer.getVertexCount() > 0 && correctSprite && correctTint;
    }

    private static BlockState blockAt(IFluidState state, boolean flowing, BlockPos pos) {
        if (BlockPos.ZERO.equals(pos)) return state.getBlockState();
        if (flowing && BlockPos.ZERO.east().equals(pos)) {
            return ((FlowingFluid) state.getFluid()).getFlowingFluidState(3, false).getBlockState();
        }
        return (pos.getY() < 0 ? net.minecraft.block.Blocks.STONE : net.minecraft.block.Blocks.AIR).getDefaultState();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
