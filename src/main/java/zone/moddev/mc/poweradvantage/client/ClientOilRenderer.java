package zone.moddev.mc.poweradvantage.client;

import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Forge FluidAttributes handle rendering; only the custom atlas sprites need stitching. */
@OnlyIn(Dist.CLIENT)
public final class ClientOilRenderer {
    private ClientOilRenderer() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ClientOilRenderer::stitch);
    }

    public static void configureRenderLayers() {
        RenderTypeLookup.setRenderLayer(zone.moddev.mc.poweradvantage.init.Blocks.steel_frame,
                RenderType.cutout());
        for (net.minecraft.fluid.Fluid fluid : new net.minecraft.fluid.Fluid[] {
                zone.moddev.mc.poweradvantage.init.Fluids.crude_oil,
                zone.moddev.mc.poweradvantage.init.Fluids.flowing_crude_oil,
                zone.moddev.mc.poweradvantage.init.Fluids.refined_oil,
                zone.moddev.mc.poweradvantage.init.Fluids.flowing_refined_oil}) {
            RenderTypeLookup.setRenderLayer(fluid, RenderType.translucent());
        }
        RenderTypeLookup.setRenderLayer(zone.moddev.mc.poweradvantage.init.Blocks.crude_oil_block,
                RenderType.translucent());
        RenderTypeLookup.setRenderLayer(zone.moddev.mc.poweradvantage.init.Blocks.refined_oil_block,
                RenderType.translucent());
    }

    private static void stitch(TextureStitchEvent.Pre event) {
        if (!AtlasTexture.LOCATION_BLOCKS.equals(event.getMap().location())) return;
        for (String name : new String[] {"crude_oil_still", "crude_oil_flow", "refined_oil_still", "refined_oil_flow"}) {
            event.addSprite(new ResourceLocation(PowerAdvantage.MODID, "blocks/" + name));
        }
    }
}
