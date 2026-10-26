package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonObject;
import cyano.poweradvantage.api.simple.SimpleMachineGUI;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.IFluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import zone.moddev.mc.poweradvantage.gui.FilteredFluidTankGUI;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.poweradvantage.registry.MachineGUIRegistry;
import zone.moddev.mc.poweradvantage.init.Blocks;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.MetalTankTileEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Checks native neighbour culling and the actual fluid-bar drawing function. */
final class TankVisualProbe {
    private static final int GL_PROJECTION = 5889, GL_MODELVIEW = 5888;
    private static final int GL_COLOR_BUFFER_BIT = 16384, GL_RGBA = 6408, GL_UNSIGNED_BYTE = 5121;
    private TankVisualProbe() {}

    static void verify(Minecraft minecraft, JsonObject result) {
        boolean metalFloor = floorVisible(Blocks.metal_storage_tank.getDefaultState());
        boolean portableFloor = floorVisible(Blocks.storage_tank.getDefaultState());
        result.addProperty("metal_tank_floor_visible", metalFloor);
        result.addProperty("portable_tank_floor_visible", portableFloor);

        MetalTankTileEntity serverTank = new MetalTankTileEntity();
        IFluidHandler handler = serverTank.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Metal tank has no native Forge fluid capability"));
        require(handler.fill(new FluidStack(net.minecraft.fluid.Fluids.WATER, 10000), FluidAction.SIMULATE) == 10000
                && serverTank.getTank().getFluidAmount() == 0, "Native simulated fill changed the tank");
        require(handler.fill(new FluidStack(net.minecraft.fluid.Fluids.WATER, 10000), FluidAction.EXECUTE) == 10000,
                "Native capability did not fill the tank");
        MetalTankTileEntity clientTank = new MetalTankTileEntity();
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        result.addProperty("synced_tank_amount", clientTank.getTank().getFluidAmount());
        require(clientTank.getTank().getFluidAmount() == 10000, "Filled tank packet lost water");
        require(handler.drain(10000, FluidAction.EXECUTE).getAmount() == 10000, "Native capability did not drain the tank");
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

    private static boolean floorVisible(final BlockState tank) {
        IBlockReader world = new IBlockReader() {
            @Override public TileEntity getTileEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) {
                return pos.equals(BlockPos.ZERO.up()) ? tank : net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState();
            }
            @Override public IFluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        };
        return Block.shouldSideBeRendered(net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState(), world, BlockPos.ZERO, Direction.UP);
    }

    private static int renderWater(Minecraft minecraft, MetalTankTileEntity tile) {
        FilteredFluidTankGUI definition = new FilteredFluidTankGUI(new ResourceLocation("poweradvantage", "textures/gui/container/fluid_metal_tank_gui.png"));
        final int width = 128, height = 96;
        PlayerInventory inventory = new PlayerInventory(null);
        SimpleMachineGUI.Container container = definition.createContainer(1, inventory, tile);
        require(container.getType() == MachineGUIRegistry.TYPE && MachineGUIRegistry.TYPE != null,
                "Tank GUI is not backed by the registered native container type");
        SimpleMachineGUI.GUIContainer screen = container.createScreen(inventory, new net.minecraft.util.text.StringTextComponent("Tank probe"));
        screen.init(minecraft, width, height);
        require(screen.getMinecraft() == minecraft, "Native tank screen was not initialized");
        tile.getTank().setFluid(new cyano.poweradvantage.api.fluid.FluidStack(net.minecraft.fluid.Fluids.WATER, 10000));
        Framebuffer framebuffer = new Framebuffer(width, height, true, Minecraft.IS_RUNNING_ON_MAC);
        framebuffer.bindFramebuffer(true);
        GlStateManager.matrixMode(GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture();
        GlStateManager.color4f(1F, 1F, 1F, 1F);
        try {
            GlStateManager.clearColor(0F, 0F, 0F, 1F);
            GlStateManager.clear(GL_COLOR_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
            definition.drawGUIDecorations(tile, screen, 0, 0, 0F);
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            GlStateManager.readPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
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
            GlStateManager.matrixMode(GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL_MODELVIEW);
            framebuffer.deleteFramebuffer();
            minecraft.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableDepthTest();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
