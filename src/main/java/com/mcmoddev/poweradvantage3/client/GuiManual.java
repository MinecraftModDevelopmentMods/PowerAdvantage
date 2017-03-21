package com.mcmoddev.poweradvantage3.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GuiManual extends GuiContainer {
    private static final ResourceLocation CRAFTING = new ResourceLocation("poweradvantage3", "textures/gui/manual/crafting.png");
    private static final ResourceLocation INVERTED = new ResourceLocation("poweradvantage3", "textures/gui/manual/inverted.png");
    private static final ResourceLocation NORMAL = new ResourceLocation("poweradvantage3", "textures/gui/manual/normal.png");

    public GuiManual(Container container) {
        super(container);
        xSize = 292;
        ySize = 180;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(INVERTED);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 146, 180);
        this.mc.getTextureManager().bindTexture(NORMAL);
        drawTexturedModalRect(guiLeft + 146, guiTop, 0, 0, 146, 180);
        this.mc.getTextureManager().bindTexture(CRAFTING);
        drawTexturedModalRect(guiLeft + 146, guiTop, 0, 0, 146, 180);

    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        String title = "Bedrock Infinite";
        fontRendererObj.drawString(title, (float) (218 - fontRendererObj.getStringWidth(title) / 2), (float) 18, 0x3B3E4E, false);

        ItemStack stack = new ItemStack(Blocks.BEDROCK);

        drawItem(stack, 210, 32);

        drawItem(stack, 210, 75);
        drawItem(stack, 189, 75);
        drawItem(stack, 231, 75);

        drawItem(stack, 210, 96);
        drawItem(stack, 189, 96);
        drawItem(stack, 231, 96);

        drawItem(stack, 210, 117);
        drawItem(stack, 189, 117);
        drawItem(stack, 231, 117);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        if (stack == null)
            stack = new ItemStack(Blocks.BEDROCK);
        GlStateManager.pushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.popMatrix();
    }
}