package zone.moddev.mc.poweradvantage.gui;

import cyano.poweradvantage.api.fluid.FluidPoweredEntity;
import cyano.poweradvantage.api.simple.SimpleMachineGUI;
import zone.moddev.mc.poweradvantage.math.Integer2D;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeColors;
import cyano.poweradvantage.api.fluid.FluidStack;


public class FluidTankGUI extends SimpleMachineGUI {



	public FluidTankGUI(ResourceLocation image) {
		super(image,
				new Integer2D[0]);
	}

	@Override
	public void drawGUIDecorations(Object srcEntity, GUIContainer guiContainer, int x, int y, float z) {
		// draw indicator
		if (srcEntity instanceof FluidPoweredEntity) {
			FluidStack fs = ((FluidPoweredEntity) srcEntity).getTank().getFluid();
			if (fs != null && ((FluidPoweredEntity) srcEntity).getTank().getFluidAmount() > 0) {
				drawFluidBar(fs,
						(float) fs.amount / (float) ((FluidPoweredEntity) srcEntity).getTank().getCapacity(),
						80, 9,
						guiDisplayImage, guiContainer, x, y, z);
			}
		}
		GlStateManager.color4f(1f, 1f, 1f, 1f); // reset tint
		guiContainer.getMinecraft().getTextureManager().bindTexture(guiDisplayImage);
		guiContainer.blit(x + 77, y + 6, 176, 0, 22, 66); // x, y, textureOffsetX, textureOffsetY, width, height)
	}


	public static void drawFluidBar(
			FluidStack fs, float barHeight, int xPos, int yPos,
			ResourceLocation displayImage, GUIContainer guiContainer, int x, int y, float zLevel
	) {
		final int w = 16;
		final int barSlotHeight = 60;
		final int h = (int) (barSlotHeight * barHeight);

		FluidTankGUI.drawFluidFilledRectangle(guiContainer, fs, x + xPos, y + yPos + barSlotHeight - h, w, h, zLevel);
		//guiContainer.drawModalRectWithCustomSizedTexture(x+xPos, y+yPos+barSlotHeight-h, 0, 0, w, h, 16, h);//h * texPerPixel); // x, y, u, v, width, height, textureWidth, textureHeight

		guiContainer.getMinecraft().getTextureManager().bindTexture(displayImage);
		guiContainer.blit(x + xPos - 3, y + yPos - 3, 176, 0, 22, 66); // x, y, textureOffsetX, textureOffsetY, width, height)
	}


	public static void drawFluidFilledRectangle(
			GUIContainer guiContainer, FluidStack fs,
			int x, int y, int width, int height,
			float zLevel
	) {
		if (fs == null || fs.getFluid() == null || width <= 0 || height <= 0) return;
		// The atlas supplies the current animation frame; water's sprite is grayscale.
		TextureAtlasSprite sprite = guiContainer.getMinecraft().getAtlasSpriteGetter(AtlasTexture.LOCATION_BLOCKS_TEXTURE)
				.apply(fs.getFluid().getAttributes().getStillTexture());
		guiContainer.getMinecraft().getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
		int color = fs.getFluid().getAttributes().getColor(zone.moddev.mc.poweradvantage.util.ForgeFluidAdapter.toForge(fs));
		if (fs.getFluid().isEquivalentTo(net.minecraft.fluid.Fluids.WATER)) {
			color = guiContainer.getMinecraft().world != null && guiContainer.getMinecraft().player != null
					? BiomeColors.getWaterColor(guiContainer.getMinecraft().world, guiContainer.getMinecraft().player.getPosition()) : 0x3F76E4;
		}
		float red = ((color >>> 16) & 255) / 255F;
		float green = ((color >>> 8) & 255) / 255F;
		float blue = (color & 255) / 255F;
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder vertexbuffer = tessellator.getBuffer();
		vertexbuffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		for (int dy = 0; dy < height; dy += 16) {
			int tileHeight = Math.min(16, height - dy);
			for (int dx = 0; dx < width; dx += 16) {
				int tileWidth = Math.min(16, width - dx);
				float u0 = sprite.getMinU(), u1 = sprite.getInterpolatedU(tileWidth);
				float v0 = sprite.getMinV(), v1 = sprite.getInterpolatedV(tileHeight);
				vertexbuffer.pos(x + dx, y + dy + tileHeight, zLevel).tex(u0, v1).color(red, green, blue, 1F).endVertex();
				vertexbuffer.pos(x + dx + tileWidth, y + dy + tileHeight, zLevel).tex(u1, v1).color(red, green, blue, 1F).endVertex();
				vertexbuffer.pos(x + dx + tileWidth, y + dy, zLevel).tex(u1, v0).color(red, green, blue, 1F).endVertex();
				vertexbuffer.pos(x + dx, y + dy, zLevel).tex(u0, v0).color(red, green, blue, 1F).endVertex();
			}
		}
		tessellator.draw();
	}

}
