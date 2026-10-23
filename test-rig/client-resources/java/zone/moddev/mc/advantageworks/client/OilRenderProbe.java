package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.ItemBucket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldReader;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.init.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

/** Exercises the native fluid renderer, not just oil's particle-only block model. */
final class OilRenderProbe {
    private OilRenderProbe() {}

    static void verify(Minecraft minecraft, JsonObject result) {
        // Before a world/server is opened, vanilla's fluid tags have not been synchronized.
        net.minecraft.tags.TagCollection<Fluid> tags = new net.minecraft.tags.TagCollection<>(id -> true,
                net.minecraft.util.registry.IRegistry.field_212619_h::func_212608_b, "tags/fluids", false, "fluid");
        tags.register(net.minecraft.tags.Tag.Builder.<Fluid>create().add(net.minecraft.init.Fluids.WATER,
                net.minecraft.init.Fluids.FLOWING_WATER).build(new ResourceLocation("minecraft", "water")));
        tags.register(net.minecraft.tags.Tag.Builder.<Fluid>create().add(net.minecraft.init.Fluids.LAVA,
                net.minecraft.init.Fluids.FLOWING_LAVA).build(new ResourceLocation("minecraft", "lava")));
        net.minecraft.tags.FluidTags.setCollection(tags);
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
        valid &= check(minecraft, net.minecraft.init.Fluids.WATER.getDefaultState(), "minecraft:block/water_still", false, cases);
        valid &= check(minecraft, net.minecraft.init.Fluids.LAVA.getDefaultState(), "minecraft:block/lava_still", false, cases);
        if (!valid) throw new IllegalStateException("Native fluid renderer used the wrong sprite or tint: " + cases);
    }

    private static void requireBucket(net.minecraft.item.Item bucket, Fluid expected) {
        try {
            for (Field field : ItemBucket.class.getDeclaredFields()) {
                if (field.getType() != Fluid.class) continue;
                field.setAccessible(true);
                if (field.get(bucket) == expected && expected.getFilledBucket() == bucket) return;
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect native bucket fluid", failure);
        }
        throw new IllegalStateException("Wrong bucket fluid: " + bucket.getRegistryName());
    }

    private static boolean check(Minecraft minecraft, IFluidState state, String texture, boolean flowing, JsonArray cases) {
        // A tiny read-only neighbourhood supplies a slope for flowing sprites; no save is opened.
        IWorldReader world = (IWorldReader) Proxy.newProxyInstance(OilRenderProbe.class.getClassLoader(),
                new Class<?>[]{IWorldReader.class}, (proxy, method, args) -> {
                    BlockPos pos = args != null && args.length > 0 && args[0] instanceof BlockPos ? (BlockPos) args[0] : null;
                    if (method.getName().equals("getBlockState")) return blockAt(state, flowing, pos);
                    if (method.getName().equals("getFluidState")) return blockAt(state, flowing, pos).getFluidState();
                    if (method.getName().equals("getCombinedLight")) return 0x00F000F0;
                    if (method.getName().equals("getBiome")) return net.minecraft.init.Biomes.PLAINS;
                    throw new UnsupportedOperationException(method.toString());
                });
        BufferBuilder buffer = new BufferBuilder(4096);
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        boolean rendered = new BlockFluidRenderer().render(world, BlockPos.ORIGIN, buffer, state);
        buffer.finishDrawing();
        TextureAtlasSprite sprite = minecraft.getTextureMap().getSprite(new ResourceLocation(texture));
        ByteBuffer data = buffer.getByteBuffer();
        float u = data.getFloat(DefaultVertexFormats.BLOCK.getUvOffsetById(0));
        float v = data.getFloat(DefaultVertexFormats.BLOCK.getUvOffsetById(0) + 4);
        int colorOffset = DefaultVertexFormats.BLOCK.getColorOffset();
        int rgb = (data.get(colorOffset) & 255) << 16 | (data.get(colorOffset + 1) & 255) << 8 | data.get(colorOffset + 2) & 255;
        boolean correctSprite = !"missingno".equals(sprite.getName().getPath())
                && u >= sprite.getMinU() && u <= sprite.getMaxU() && v >= sprite.getMinV() && v <= sprite.getMaxV();
        boolean correctTint = !(state.getFluid() instanceof Fluids.PowerFluid) || rgb == 0xFFFFFF;
        JsonObject entry = new JsonObject();
        entry.addProperty("fluid", net.minecraft.util.registry.IRegistry.field_212619_h.getKey(state.getFluid()).toString());
        entry.addProperty("block", state.getBlockState().getBlock().getRegistryName().toString());
        entry.addProperty("expected_sprite", texture);
        entry.addProperty("correct_sprite", correctSprite);
        entry.addProperty("vertex_rgb", String.format("%06X", rgb));
        entry.addProperty("vertices", buffer.getVertexCount());
        cases.add(entry);
        return rendered && buffer.getVertexCount() > 0 && correctSprite && correctTint;
    }

    private static IBlockState blockAt(IFluidState state, boolean flowing, BlockPos pos) {
        if (BlockPos.ORIGIN.equals(pos)) return state.getBlockState();
        if (flowing && BlockPos.ORIGIN.east().equals(pos)) {
            return ((FlowingFluid) state.getFluid()).getFlowingFluidState(3, false).getBlockState();
        }
        return (pos.getY() < 0 ? net.minecraft.init.Blocks.STONE : net.minecraft.init.Blocks.AIR).getDefaultState();
    }
}
