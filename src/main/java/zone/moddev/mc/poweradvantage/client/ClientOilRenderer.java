package zone.moddev.mc.poweradvantage.client;

import net.minecraft.client.renderer.texture.AtlasTexture;
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

    private static void stitch(TextureStitchEvent.Pre event) {
        if (event.getMap() != net.minecraft.client.Minecraft.getInstance().getTextureMap()) return;
        for (String name : new String[] {"crude_oil_still", "crude_oil_flow", "refined_oil_still", "refined_oil_flow"}) {
            event.addSprite(new ResourceLocation(PowerAdvantage.MODID, "blocks/" + name));
        }
    }
}
