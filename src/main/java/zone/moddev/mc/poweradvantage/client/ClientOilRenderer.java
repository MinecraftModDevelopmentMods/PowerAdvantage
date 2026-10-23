package zone.moddev.mc.poweradvantage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.fluid.IFluidState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.init.Fluids.PowerFluid;

/** Forge 25's native fluid renderer otherwise uses water sprites for custom fluids. */
@OnlyIn(Dist.CLIENT)
public final class ClientOilRenderer {
    private static volatile TextureAtlasSprite[] crudeSprites;
    private static volatile TextureAtlasSprite[] refinedSprites;

    private ClientOilRenderer() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ClientOilRenderer::onTexturePre);
        MinecraftForge.EVENT_BUS.addListener(ClientOilRenderer::onTexturePost);
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(PowerAdvantage.MODID, "blocks/" + name);
    }

    private static void onTexturePre(TextureStitchEvent.Pre event) {
        for (String name : new String[]{"crude_oil_still", "crude_oil_flow", "refined_oil_still", "refined_oil_flow"}) {
            event.getMap().registerSprite(Minecraft.getInstance().getResourceManager(), texture(name));
        }
    }

    private static void onTexturePost(TextureStitchEvent.Post event) {
        crudeSprites = new TextureAtlasSprite[]{event.getMap().getSprite(texture("crude_oil_still")),
                event.getMap().getSprite(texture("crude_oil_flow"))};
        refinedSprites = new TextureAtlasSprite[]{event.getMap().getSprite(texture("refined_oil_still")),
                event.getMap().getSprite(texture("refined_oil_flow"))};
    }

    /** Avoid the water-overlay branch without putting oil in the lava tag. */
    public static boolean useOilPath(IFluidState state, boolean vanillaValue) {
        return isOil(state) || vanillaValue;
    }

    public static TextureAtlasSprite[] overrideSprites(IFluidState state, TextureAtlasSprite[] vanillaSprites) {
        if (!isOil(state)) return vanillaSprites;
        TextureAtlasSprite[] sprites = ((PowerFluid) state.getFluid()).getKind()
                == zone.moddev.mc.poweradvantage.init.Fluids.Kind.CRUDE ? crudeSprites : refinedSprites;
        return sprites == null ? vanillaSprites : sprites;
    }

    /** The existing oil textures already contain their colour; do not apply biome water tint. */
    public static int overrideColor(IFluidState state, int vanillaColor) {
        return isOil(state) ? 0xFFFFFF : vanillaColor;
    }

    private static boolean isOil(IFluidState state) {
        return state != null && state.getFluid() instanceof PowerFluid;
    }
}
