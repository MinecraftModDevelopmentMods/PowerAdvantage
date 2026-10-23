package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonObject;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.simple.SimpleMachineGUI;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.fluid.IFluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import zone.moddev.mc.poweradvantage.gui.FilteredFluidTankGUI;
import zone.moddev.mc.poweradvantage.gui.FluidTankGUI;
import zone.moddev.mc.poweradvantage.init.Blocks;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.MetalTankTileEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Checks native neighbour culling and the actual fluid-bar drawing function. */
final class TankVisualProbe {
    private TankVisualProbe() {}

    static void verify(Minecraft minecraft, JsonObject result) {
        boolean metalFloor = floorVisible(Blocks.metal_storage_tank.getDefaultState());
        boolean portableFloor = floorVisible(Blocks.storage_tank.getDefaultState());
        result.addProperty("metal_tank_floor_visible", metalFloor);
        result.addProperty("portable_tank_floor_visible", portableFloor);

        MetalTankTileEntity serverTank = new MetalTankTileEntity();
        serverTank.getTank().fill(new FluidStack(net.minecraft.init.Fluids.WATER, 10000), true);
        MetalTankTileEntity clientTank = new MetalTankTileEntity();
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        result.addProperty("synced_tank_amount", clientTank.getTank().getFluidAmount());
        require(clientTank.getTank().getFluidAmount() == 10000, "Filled tank packet lost water");
        serverTank.getTank().drain(10000, true);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        require(clientTank.getTank().getFluidAmount() == 0, "Empty tank packet retained old water");

        int rgb = renderWater(minecraft, clientTank);
        result.addProperty("water_bar_rgb", String.format("%06X", rgb));
        int red = (rgb >>> 16) & 255;
        int green = (rgb >>> 8) & 255;
        int blue = rgb & 255;
        require(metalFloor && portableFloor, "Inset tank culled the floor beneath its exposed corners");
        require(blue > red + 20 && blue > green + 10, "Water gauge is not blue: " + String.format("%06X", rgb));
    }

    private static boolean floorVisible(final IBlockState tank) {
        IBlockReader world = new IBlockReader() {
            @Override public TileEntity getTileEntity(BlockPos pos) { return null; }
            @Override public IBlockState getBlockState(BlockPos pos) {
                return pos.equals(BlockPos.ORIGIN.up()) ? tank : net.minecraft.init.Blocks.GRASS_BLOCK.getDefaultState();
            }
            @Override public IFluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        };
        return Block.shouldSideBeRendered(net.minecraft.init.Blocks.GRASS_BLOCK.getDefaultState(), world, BlockPos.ORIGIN, EnumFacing.UP);
    }

    private static int renderWater(Minecraft minecraft, MetalTankTileEntity tile) {
        FilteredFluidTankGUI definition = new FilteredFluidTankGUI(new ResourceLocation("poweradvantage", "textures/gui/container/fluid_metal_tank_gui.png"));
        SimpleMachineGUI.GUIContainer screen = definition.new GUIContainer(new InventoryPlayer(null), tile);
        screen.mc = minecraft;
        tile.getTank().setFluid(new FluidStack(net.minecraft.init.Fluids.WATER, 10000));
        final int width = 128, height = 96;
        Framebuffer framebuffer = new Framebuffer(width, height, true);
        framebuffer.bindFramebuffer(true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.color4f(1F, 1F, 1F, 1F);
        try {
            GL11.glClearColor(0F, 0F, 0F, 1F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            definition.drawGUIDecorations(tile, screen, 0, 0, 0F);
            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = ((height - y - 1) * width + x) * 4;
                    image.setRGB(x, y, (pixels.get(index) & 255) << 16
                            | (pixels.get(index + 1) & 255) << 8 | pixels.get(index + 2) & 255);
                }
            }
            ImageIO.write(image, "png", new File("tank-water-gauge.png"));
            return image.getRGB(88, 39) & 0xFFFFFF;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not retain the native tank gauge image", failure);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            framebuffer.deleteFramebuffer();
            minecraft.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableDepthTest();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
