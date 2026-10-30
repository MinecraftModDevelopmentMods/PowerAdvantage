package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import cyano.poweradvantage.api.GUIBlock;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.simple.SimpleMachineGUI;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.registry.MachineGUIRegistry;

/** Renders every registered machine screen, not just its detached decorations. */
final class MachineGuiLayoutProbe {
    private static final int[][] VIEWPORTS = {{320, 240, 1}, {426, 240, 2}, {640, 360, 1}};

    static void verify(Minecraft minecraft, JsonObject result) {
        if (!Boolean.getBoolean("advantageworks.machineGuiLayouts")) return;
        JsonArray screens = new JsonArray();
        result.add("machine_gui_layouts", screens);
        result.addProperty("machine_gui_text_spec", 2);
        int textChecks = 0, negativeControls = 0;
        List<String> problems = new ArrayList<>();
        ClientPlayerEntity originalPlayer = minecraft.player;
        try {
            JsonObject expectations;
            try (InputStream stream = MachineGuiLayoutProbe.class.getResourceAsStream("/gui-label-expectations.json")) {
                require(stream != null, "Missing independent GUI label expectations");
                expectations = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            require("Inventory".equals(net.minecraft.client.resources.I18n.get("container.inventory")), "GUI label audit requires en_us");
            // ContainerScreen needs a carried-item inventory, but no world or connection.
            Class<?> allocator = Class.forName("sun.misc.Unsafe");
            Field singleton = allocator.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            ClientPlayerEntity player = (ClientPlayerEntity) allocator.getMethod("allocateInstance", Class.class)
                    .invoke(singleton.get(null), ClientPlayerEntity.class);
            PlayerInventory inventory = new PlayerInventory(player);
            Field inventoryField = PlayerEntity.class.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            inventoryField.set(player, inventory);
            Field cooldowns = PlayerEntity.class.getDeclaredField("cooldowns");
            cooldowns.setAccessible(true);
            cooldowns.set(player, new net.minecraft.util.CooldownTracker());
            minecraft.player = player;
            Field definitionsField = MachineGUIRegistry.class.getDeclaredField("DEFINITIONS");
            definitionsField.setAccessible(true);
            Map<?, ?> definitions = (Map<?, ?>) definitionsField.get(null);
            List<Block> blocks = new ArrayList<>();
            ForgeRegistries.BLOCKS.forEach(block -> {
                if (block instanceof GUIBlock && ((GUIBlock) block).getGuiID() > 0) blocks.add(block);
            });
            blocks.sort(Comparator.comparing(block -> block.getRegistryName().toString()));
            for (Block block : blocks) {
                JsonObject row = new JsonObject();
                String id = block.getRegistryName().toString();
                row.addProperty("block", id);
                screens.add(row);
                try {
                    Object definition = definitions.get(((GUIBlock) block).getGuiID());
                    require(definition instanceof SimpleMachineGUI, "No shared screen definition");
                    Field textureField = SimpleMachineGUI.class.getDeclaredField("guiDisplayImage");
                    textureField.setAccessible(true);
                    net.minecraft.util.ResourceLocation texture = (net.minecraft.util.ResourceLocation) textureField.get(definition);
                    try (net.minecraft.resources.IResource resource = minecraft.getResourceManager().getResource(texture)) {
                        BufferedImage artwork = ImageIO.read(resource.getInputStream());
                        // The GUI UVs use a logical 256-square atlas; Electric supplies 2x artwork.
                        require(artwork != null && artwork.getWidth() >= 256 && artwork.getWidth() % 256 == 0
                                && artwork.getWidth() == artwork.getHeight(),
                                "Missing or incorrectly sized GUI texture: " + texture);
                        row.addProperty("texture_pixels", artwork.getWidth());
                    }
                    row.addProperty("texture", texture.toString());
                    TileEntity tile = block.createTileEntity(block.defaultBlockState(), null);
                    require(tile instanceof IInventory, "GUI block has no inventory tile");
                    GuiWorld world = (GuiWorld) allocator.getMethod("allocateInstance", Class.class)
                            .invoke(singleton.get(null), GuiWorld.class);
                    world.state = block.defaultBlockState();
                    Field clientSide = net.minecraft.world.World.class.getDeclaredField("isClientSide");
                    clientSide.setAccessible(true);
                    clientSide.setBoolean(world, true);
                    tile.setLevelAndPosition(world, BlockPos.ZERO);
                    SimpleMachineGUI.Container menu = ((SimpleMachineGUI) definition).createMenu(1, inventory, (IInventory) tile);
                    require(expectations.getAsJsonObject("machines").has(id), "No human-facing label expectation for " + id);
                    String expectedTitle = expectations.getAsJsonObject("machines").get(id).getAsString();
                    Method titleFactory = MachineGUIRegistry.class.getDeclaredMethod("menuTitle", TileEntity.class, Block.class);
                    titleFactory.setAccessible(true);
                    net.minecraft.util.text.ITextComponent title = (net.minecraft.util.text.ITextComponent) titleFactory.invoke(null, tile, block);
                    RecordingScreen screen = new RecordingScreen((SimpleMachineGUI) definition, menu, inventory, title,
                            expectedTitle, expectations.get("inventory").getAsString());
                    row.addProperty("definition", definition.getClass().getName());
                    row.addProperty("expected_title", expectedTitle);
                    row.addProperty("menu_title", title.getString());
                    row.addProperty("slots", menu.slots.size());
                    int renders = 0;
                    for (int[] viewport : VIEWPORTS) {
                        screen.init(minecraft, viewport[0], viewport[1]);
                        verifySlots(screen, menu);
                        for (int fill = 0; fill <= 2; fill++) {
                            for (net.minecraft.state.Property<?> property : block.defaultBlockState().getProperties()) {
                                if (property instanceof net.minecraft.state.BooleanProperty && "active".equals(property.getName())) {
                                    world.state = block.defaultBlockState().setValue((net.minecraft.state.BooleanProperty) property, fill > 0);
                                }
                            }
                            populate(tile, fill * 0.5F);
                            BufferedImage actual = render(minecraft, screen, viewport, false);
                            String name = id.replace(':', '-') + "-" + viewport[0] + "x" + viewport[1]
                                    + "-scale" + viewport[2] + "-fill" + fill;
                            File target = new File("machine-gui-layouts/" + name + ".png");
                            target.getParentFile().mkdirs();
                            ImageIO.write(actual, "png", target);
                            require(screen.tooltipCalls > 0, "Native item tooltip rendering was not dispatched");
                            screen.verifyDrawBounds();
                            renders++;
                            BufferedImage background = render(minecraft, screen, viewport, true);
                            screen.labels = LabelMode.EXPECTED;
                            BufferedImage expected = render(minecraft, screen, viewport, false);
                            screen.labels = LabelMode.PRODUCTION;
                            String labelError = labelError(actual, expected, background, screen, viewport[2]);
                            require(labelError == null, labelError);
                            textChecks++;
                            if (fill == 0 && viewport == VIEWPORTS[0]) verifyNoTextOverArtwork(actual, background, screen);
                            require(title.getString().equals(expectedTitle), "Menu title differs from the equipment name: " + title.getString());
                            require(screen.getYSize() == expectations.getAsJsonObject("layout").get("height").getAsInt(), "Missing reserved label space");
                            verifyInventoryRows(menu);
                            require(labelError(background, expected, background, screen, viewport[2]) != null,
                                    "Negative control accepted a screen with no labels");
                            negativeControls++;
                        }
                    }
                    inventory.setItem(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 2));
                    screen.init(minecraft, 320, 240);
                    BufferedImage tooltip = render(minecraft, screen, VIEWPORTS[0], false, true);
                    require(screen.itemTooltipCalls == 1, "Hovered inventory item has no native tooltip");
                    require(screen.hovered() == menu.slots.get(menu.slots.size() - 9), "Inventory hitbox is misaligned");
                    ImageIO.write(tooltip, "png", new File("machine-gui-layouts/" + id.replace(':', '-') + "-tooltip.png"));
                    inventory.setItem(0, net.minecraft.item.ItemStack.EMPTY);
                    if (block == zone.moddev.mc.poweradvantage.init.Blocks.metal_storage_tank) {
                        negativeControls += verifyMissingLabelControls(minecraft, screen);
                        verifyLongName(minecraft, (SimpleMachineGUI) definition, menu, inventory);
                    }
                    if (block == zone.moddev.mc.poweradvantage.init.Blocks.metal_storage_tank) {
                        verifyHelperLimits(screen);
                    }
                    row.addProperty("renders", renders);
                    row.addProperty("tooltip_and_inventory_hitbox", "PASS");
                    row.addProperty("required_text", "PASS");
                    row.addProperty("blank_label_controls_rejected", 9);
                    row.addProperty("status", "PASS");
                } catch (Exception | LinkageError failure) {
                    inventory.setItem(0, net.minecraft.item.ItemStack.EMPTY);
                    org.apache.logging.log4j.LogManager.getLogger("advantageclientresources").error("GUI fixture {} failed", id, failure);
                    row.addProperty("status", "FAIL");
                    row.addProperty("error", failure.toString());
                    problems.add(id + ": " + failure);
                }
            }
            result.addProperty("machine_gui_layout_count", screens.size());
            require(!blocks.isEmpty(), "No registered machine screens were audited");
            if (Boolean.getBoolean("advantageworks.requireExtensions")) require(blocks.size() == 39,
                    "Expected all 14 Power, 10 Steam and 15 Electric screens, found " + blocks.size());
            require(problems.isEmpty(), "Machine GUI failures: " + problems);
            result.addProperty("machine_gui_text_status", "PASS");
            result.addProperty("machine_gui_text_checks", textChecks);
            result.addProperty("machine_gui_negative_controls_rejected", negativeControls);
            result.addProperty("machine_gui_layout_status", "PASS");
        } catch (Exception failure) {
            result.addProperty("machine_gui_layout_status", "FAIL");
            throw new IllegalStateException("Machine GUI audit failed", failure);
        } finally {
            minecraft.player = originalPlayer;
        }
    }

    private static void verifySlots(SimpleMachineGUI.GUIContainer screen, SimpleMachineGUI.Container menu) {
        require(screen.getLeft() >= 0 && screen.getTop() >= 0
                && screen.getLeft() + screen.getXSize() <= screen.width
                && screen.getTop() + screen.getYSize() <= screen.height, "Screen is clipped at a supported GUI size");
        require(menu.slots.size() >= 36, "Missing player inventory");
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            require(slot.x >= 0 && slot.y >= 0 && slot.x + 16 <= screen.getXSize()
                    && slot.y + 16 <= screen.getYSize(), "Slot outside the screen: " + i);
            for (int j = 0; j < i; j++) {
                Slot other = menu.slots.get(j);
                require(slot.x + 16 <= other.x || other.x + 16 <= slot.x
                        || slot.y + 16 <= other.y || other.y + 16 <= slot.y, "Overlapping slots: " + i + ", " + j);
            }
        }
    }

    private static void populate(TileEntity tile, float fraction) throws IllegalAccessException {
        if (tile instanceof PoweredEntity) {
            PoweredEntity powered = (PoweredEntity) tile;
            for (cyano.poweradvantage.api.ConduitType type : powered.getTypes()) {
                powered.setEnergy(powered.getEnergyCapacity(type) * fraction, type);
            }
        }
        for (Class<?> type = tile.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !FluidTank.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                FluidTank tank = (FluidTank) field.get(tile);
                if (tank != null) tank.setFluid(fraction == 0 ? null
                        : new FluidStack(net.minecraft.fluid.Fluids.WATER, (int) (tank.getCapacity() * fraction)));
            }
        }
    }

    private static BufferedImage render(Minecraft minecraft, RecordingScreen screen,
            int[] viewport, boolean backgroundOnly) throws Exception {
        return render(minecraft, screen, viewport, backgroundOnly, false);
    }

    private static BufferedImage render(Minecraft minecraft, RecordingScreen screen,
            int[] viewport, boolean backgroundOnly, boolean hover) throws Exception {
        int width = viewport[0] * viewport[2], height = viewport[1] * viewport[2];
        Framebuffer target = new Framebuffer(width, height, true, Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.matrixMode(5889);
        RenderSystem.pushMatrix(); RenderSystem.loadIdentity();
        RenderSystem.ortho(0, viewport[0], viewport[1], 0, 1000, 3000);
        RenderSystem.matrixMode(5888);
        RenderSystem.pushMatrix(); RenderSystem.loadIdentity();
        RenderSystem.translatef(0, 0, -2000);
        RenderSystem.enableAlphaTest();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableBlend();
        RenderSystem.color4f(1, 1, 1, 1);
        try {
            RenderSystem.clearColor(0.12F, 0.18F, 0.23F, 1);
            RenderSystem.clear(16384 | 256, Minecraft.ON_OSX);
            if (backgroundOnly) {
                LabelMode original = screen.labels;
                screen.labels = LabelMode.HIDDEN;
                screen.render(new MatrixStack(), -100, -100, 0F);
                screen.labels = original;
            } else {
                Slot hotbar = screen.getMenu().slots.get(screen.getMenu().slots.size() - 9);
                screen.render(new MatrixStack(), screen.hoverTitle ? screen.width / 2 : hover ? screen.getLeft() + hotbar.x + 1 : -100,
                        screen.hoverTitle ? screen.getTop() + 4 : hover ? screen.getTop() + hotbar.y + 1 : -100, 0F);
            }
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            RenderSystem.readPixels(0, 0, width, height, 6408, 5121, pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int index = ((height - y - 1) * width + x) * 4;
                image.setRGB(x, y, (pixels.get(index) & 255) << 16
                        | (pixels.get(index + 1) & 255) << 8 | pixels.get(index + 2) & 255);
            }
            return image;
        } finally {
            RenderSystem.popMatrix();
            RenderSystem.matrixMode(5889); RenderSystem.popMatrix();
            RenderSystem.matrixMode(5888);
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.color4f(1, 1, 1, 1);
        }
    }

    private static String labelError(BufferedImage actual, BufferedImage expected, BufferedImage background,
            SimpleMachineGUI.GUIContainer screen, int scale) {
        int labelIndex = 0;
        for (int[] rect : new int[][]{{-screen.getLeft() + 4, 1, screen.width - screen.getLeft() - 4, 12}, {8, 140, 168, 150}}) {
            int ink = 0, missing = 0;
            for (int y = rect[1]; y < rect[3]; y++) for (int x = rect[0]; x < rect[2]; x++) {
                int px = (screen.getLeft() + x) * scale, py = (screen.getTop() + y) * scale;
                if (expected.getRGB(px, py) != background.getRGB(px, py)) {
                    ink++;
                    if (actual.getRGB(px, py) != expected.getRGB(px, py)) missing++;
                }
            }
            if (ink < 20 || missing != 0) return (labelIndex == 0 ? "Machine name" : "Inventory label")
                    + " missing, incorrect or clipped: expectedInk=" + ink + ", mismatched=" + missing;
            labelIndex++;
        }
        return null;
    }

    private static void verifyInventoryRows(SimpleMachineGUI.Container menu) {
        int first = menu.slots.size() - 36;
        for (int i = 0; i < 36; i++) {
            Slot slot = menu.slots.get(first + i);
            int expectedY = i < 27 ? 158 + (i / 9) * 18 : 216;
            require(slot.y == expectedY && slot.x == 8 + (i % 9) * 18, "Inventory slots do not align with their labelled panel");
        }
    }

    private static void verifyNoTextOverArtwork(BufferedImage actual, BufferedImage hidden, RecordingScreen screen) {
        for (int y = 0; y < screen.getYSize(); y++) {
            if (y < 12 || (y >= 140 && y < 150)) continue;
            for (int x = 0; x < screen.getXSize(); x++) require(actual.getRGB(screen.getLeft() + x, screen.getTop() + y)
                    == hidden.getRGB(screen.getLeft() + x, screen.getTop() + y), "Text is drawn over artwork or inventory slots");
        }
    }

    private static int verifyMissingLabelControls(Minecraft minecraft, RecordingScreen screen) throws Exception {
        screen.labels = LabelMode.EXPECTED;
        BufferedImage expected = render(minecraft, screen, VIEWPORTS[0], false);
        BufferedImage hidden = render(minecraft, screen, VIEWPORTS[0], true);
        for (LabelMode mode : new LabelMode[]{LabelMode.TITLE_ONLY, LabelMode.INVENTORY_ONLY, LabelMode.WRONG_TEXT, LabelMode.OVER_GAUGE}) {
            screen.labels = mode;
            BufferedImage broken = render(minecraft, screen, VIEWPORTS[0], false);
            ImageIO.write(broken, "png", new File("machine-gui-layouts/negative-control-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
            require(labelError(broken, expected, hidden, screen, 1) != null, "Label detector accepted negative control " + mode);
        }
        screen.labels = LabelMode.PRODUCTION;
        return 4;
    }

    private static void verifyLongName(Minecraft minecraft, SimpleMachineGUI definition, SimpleMachineGUI.Container menu,
            PlayerInventory inventory) throws Exception {
        String name = String.join(" / ", java.util.Collections.nCopies(16, "John's oil reserve"));
        RecordingScreen screen = new RecordingScreen(definition, menu, inventory,
                new net.minecraft.util.text.StringTextComponent(name), name, "Inventory");
        screen.init(minecraft, 320, 240);
        BufferedImage actual = render(minecraft, screen, VIEWPORTS[0], false);
        BufferedImage hidden = render(minecraft, screen, VIEWPORTS[0], true);
        screen.labels = LabelMode.EXPECTED;
        BufferedImage expected = render(minecraft, screen, VIEWPORTS[0], false);
        require(labelError(actual, expected, hidden, screen, 1) == null, "Long custom name is clipped instead of being fitted");
        ImageIO.write(actual, "png", new File("machine-gui-layouts/metal-tank-long-name.png"));
        screen.labels = LabelMode.PRODUCTION;
        screen.hoverTitle = true;
        BufferedImage hover = render(minecraft, screen, VIEWPORTS[0], false);
        require(name.equals(screen.titleTooltip), "Full custom name is not available from its caption tooltip");
        ImageIO.write(hover, "png", new File("machine-gui-layouts/metal-tank-long-name-tooltip.png"));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static void verifyHelperLimits(RecordingScreen screen) throws Exception {
        for (String name : new String[]{"zone.moddev.mc.electricadvantage.gui.GUIHelper",
                "zone.moddev.mc.steamadvantage.gui.GUIHelper"}) {
            if (!net.minecraftforge.fml.ModList.get().isLoaded(name.contains("electricadvantage") ? "electricadvantage" : "steamadvantage")) continue;
            String[] methods = name.contains("electricadvantage") ? new String[]{"drawProgressBar"}
                    : new String[]{"drawDownArrowProgress", "drawFlameProgress"};
            for (String method : methods) {
                Method draw = Class.forName(name).getMethod(method, int.class, int.class, float.class, SimpleMachineGUI.GUIContainer.class);
                for (float value : new float[]{-1F, 0F, 0.5F, 1F, 15F, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
                    screen.draws.clear();
                    draw.invoke(null, screen.getLeft() + 44, screen.getTop() + 46, value, screen);
                    screen.verifyDrawBounds();
                }
            }
        }
    }

    private static final class GuiWorld extends ClientWorld {
        private net.minecraft.block.BlockState state;
        private GuiWorld() {
            super(null, null, net.minecraft.world.World.OVERWORLD, null, 0, () -> net.minecraft.profiler.EmptyProfiler.INSTANCE, null, false, 0);
            throw new AssertionError("Allocate the read-only GUI fixture without a world constructor");
        }
        @Override public net.minecraft.block.BlockState getBlockState(BlockPos pos) { return state; }
    }

    private static final class RecordingScreen extends SimpleMachineGUI.GUIContainer {
        private final List<int[]> draws = new ArrayList<>();
        private int tooltipCalls;
        private int itemTooltipCalls;
        private LabelMode labels = LabelMode.PRODUCTION;
        private final String expectedTitle, expectedInventory;
        private boolean hoverTitle;
        private String titleTooltip;
        RecordingScreen(SimpleMachineGUI definition, SimpleMachineGUI.Container menu, PlayerInventory inventory,
                net.minecraft.util.text.ITextComponent title, String expectedTitle, String expectedInventory) {
            definition.super(menu, inventory, title);
            this.expectedTitle = expectedTitle;
            this.expectedInventory = expectedInventory;
        }
        @Override protected void renderLabels(MatrixStack matrices, int x, int y) {
            if (labels == LabelMode.PRODUCTION) { super.renderLabels(matrices, x, y); return; }
            if (labels == LabelMode.HIDDEN) return;
            if (labels != LabelMode.INVENTORY_ONLY) {
                String text = labels == LabelMode.WRONG_TEXT ? "Untranslated machine.name" : expectedTitle;
                if (font.width(text) > width - 8) {
                    while (font.width(text + "...") > width - 8) text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
                    text += "...";
                }
                int textX = (176 - font.width(text)) / 2;
                font.drawShadow(matrices, text, textX, labels == LabelMode.OVER_GAUGE ? 20 : 1, 0xFFFFFF);
            }
            if (labels != LabelMode.TITLE_ONLY) font.drawShadow(matrices, expectedInventory, 8, 140, 0xFFFFFF);
        }
        @Override public void render(MatrixStack matrices, int x, int y, float ticks) {
            draws.clear(); tooltipCalls = 0; itemTooltipCalls = 0; titleTooltip = null;
            super.render(matrices, x, y, ticks);
        }
        @Override protected void renderTooltip(MatrixStack matrices, int x, int y) {
            tooltipCalls++;
            super.renderTooltip(matrices, x, y);
        }
        @Override public void renderTooltip(MatrixStack matrices, net.minecraft.item.ItemStack item, int x, int y) {
            itemTooltipCalls++;
            super.renderTooltip(matrices, item, x, y);
        }
        @Override public void renderTooltip(MatrixStack matrices, net.minecraft.util.text.ITextComponent text, int x, int y) {
            titleTooltip = text.getString();
            super.renderTooltip(matrices, text, x, y);
        }
        Slot hovered() { return hoveredSlot; }
        @Override public void blit(int x, int y, int u, int v, int width, int height) {
            draws.add(new int[]{x - getLeft(), y - getTop(), u, v, width, height});
            super.blit(x, y, u, v, width, height);
        }
        void verifyDrawBounds() {
            for (int[] draw : draws) {
                require(draw[4] >= 0 && draw[5] >= 0 && draw[0] >= 0 && draw[1] >= 0
                    && draw[0] + draw[4] <= getXSize() && draw[1] + draw[5] <= getYSize()
                    && draw[2] >= 0 && draw[3] >= 0 && draw[2] + draw[4] <= 256 && draw[3] + draw[5] <= 256,
                    "Decoration outside screen or texture: " + java.util.Arrays.toString(draw));
                if (draw[5] > 0) require(draw[1] >= 12 && (draw[1] + draw[5] <= 140 || draw[1] >= 150),
                        "Artwork intrudes into a required label row: " + java.util.Arrays.toString(draw));
            }
        }
    }

    private enum LabelMode { PRODUCTION, HIDDEN, EXPECTED, TITLE_ONLY, INVENTORY_ONLY, WRONG_TEXT, OVER_GAUGE }
}
