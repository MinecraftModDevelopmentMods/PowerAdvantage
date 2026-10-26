package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.recipebook.RecipeWidget;
import net.minecraft.client.gui.recipebook.RecipeList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.util.ClientRecipeBook;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.RecipeBook;
import net.minecraft.item.crafting.RecipeItemHelper;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SUpdateRecipesPacket;

/** Decodes actual server recipe packets and exercises Minecraft's real cell grouping. */
final class RecipeBookGroupingProbe {
    private static final int GL_PROJECTION = 5889, GL_MODELVIEW = 5888;
    private static final int GL_COLOR_BUFFER_BIT = 16384, GL_DEPTH_BUFFER_BIT = 256, GL_RGBA = 6408, GL_UNSIGNED_BYTE = 5121;
    static void verify(Minecraft minecraft, JsonObject report) {
        Path root = Paths.get("recipe-book-fixtures");
        if (!Files.isDirectory(root)) {
            report.addProperty("recipe_book_grouping_status", "NOT_RUN_NO_NATIVE_FIXTURES");
            return;
        }
        JsonArray checks = new JsonArray();
        report.add("recipe_book_grouping", checks);
        try (Stream<Path> files = Files.list(root)) {
            // A separate main-menu client has not performed Forge's registry handshake.
            // Import only the generated server fixture's ID snapshot, never a user world.
            Map<net.minecraft.util.ResourceLocation, net.minecraftforge.registries.ForgeRegistry.Snapshot> registry =
                    net.minecraftforge.registries.RegistryManager.ACTIVE.takeSnapshot(false);
            net.minecraft.nbt.CompoundNBT saved;
            try (java.io.InputStream input = Files.newInputStream(root.resolve("server-registries.dat"))) {
                net.minecraft.nbt.CompoundNBT level = net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
                require("1.15.2".equals(level.getCompound("Data").getCompound("Version").getString("Name")),
                        "Recipe fixture is not a native 1.15.2 server export");
                net.minecraft.nbt.ListNBT mods = level.getCompound("fml").getList("LoadingModList", 10);
                boolean nativeForge = false;
                for (int i = 0; i < mods.size(); i++) {
                    net.minecraft.nbt.CompoundNBT mod = mods.getCompound(i);
                    if ("forge".equals(mod.getString("ModId"))) nativeForge = "31.2.57".equals(mod.getString("ModVersion"));
                }
                require(nativeForge, "Recipe fixture was not saved by Forge 31.2.57");
                saved = level.getCompound("fml").getCompound("Registries");
            }
            String itemRegistry = net.minecraftforge.registries.ForgeRegistries.ITEMS.getRegistryName().toString();
            String blockRegistry = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getRegistryName().toString();
            require(saved.contains(itemRegistry, 10) && saved.contains(blockRegistry, 10)
                    && !saved.getCompound(itemRegistry).getList("ids", 10).isEmpty()
                    && !saved.getCompound(blockRegistry).getList("ids", 10).isEmpty(), "Missing native server registry snapshot");
            for (String key : saved.keySet()) registry.put(new net.minecraft.util.ResourceLocation(key),
                    net.minecraftforge.registries.ForgeRegistry.Snapshot.read(saved.getCompound(key)));
            require(net.minecraftforge.registries.GameData.injectSnapshot(registry, false, false).isEmpty(),
                    "Client lacks a server registry entry");
            List<Path> packets = files.filter(file -> file.toString().endsWith(".bin")).sorted().collect(Collectors.toList());
            require(packets.size() == 3, "Normal, Tech and Apocalyptic recipe-book packets are required");
            for (Path file : packets) {
                JsonObject manifest = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(
                        file.toString().replace(".bin", ".json"))), StandardCharsets.UTF_8)).getAsJsonObject();
                require("PASS".equals(manifest.get("status").getAsString()), "Server recipe fixture did not pass");
                Map<String, JsonObject> expected = new HashMap<>();
                manifest.getAsJsonArray("bookRecipes").forEach(row -> require(expected.put(row.getAsJsonObject().get("id").getAsString(),
                        row.getAsJsonObject()) == null, "Duplicate recipe in packet manifest"));
                SUpdateRecipesPacket packet = new SUpdateRecipesPacket();
                PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(Files.readAllBytes(file)));
                try { packet.readPacketData(buffer); require(!buffer.isReadable(), "Trailing recipe packet bytes"); }
                finally { buffer.release(); }
                RecipeManager manager = new RecipeManager();
                Set<String> cellKeys = new HashSet<>();
                for (IRecipe<?> recipe : packet.getRecipes()) {
                    JsonObject row = expected.get(recipe.getId().toString());
                    require(row != null, "Unexpected network recipe " + recipe.getId());
                    String output = recipe.getRecipeOutput().getItem().getRegistryName().toString();
                    require(output.equals(row.get("output").getAsString()) && recipe.getRecipeOutput().getCount() == row.get("count").getAsInt(),
                            "Client/server item registry mismatch for " + recipe.getId());
                    require(recipe.getGroup().equals(row.get("group").getAsString()) && recipe.getGroup().equals(output), "Group changed in recipe packet");
                    String type = net.minecraft.util.registry.Registry.RECIPE_TYPE.getKey(recipe.getType()).toString();
                    require(type.equals(row.get("type").getAsString()), "Recipe type changed in packet");
                    cellKeys.add(type + "|" + output);
                }
                manager.func_223389_a(packet.getRecipes());
                require(manager.getRecipes().size() == expected.size(), "Missing network recipes");
                ClientRecipeBook book = new ClientRecipeBook(manager);
                manager.getRecipes().forEach(book::unlock);
                book.rebuildTable();
                int counted = 0;
                for (RecipeList cell : book.getRecipes()) {
                    Set<String> outputs = cell.getRecipes().stream().map(recipe -> recipe.getRecipeOutput().getItem().getRegistryName().toString())
                            .collect(Collectors.toSet());
                    require(outputs.size() == 1 && cell.hasSingleResultItem(), "Mixed items share a native recipe-book cell: " + outputs);
                    counted += cell.getRecipes().size();
                    cell.updateKnownRecipes(book);
                    cell.canCraft(new RecipeItemHelper(), 3, 3, book);
                    require(cell.getRecipes(false).size() == cell.getRecipes().size(), "Unlocked variants hidden in native cell");
                }
                require(counted == expected.size() && book.getRecipes().size() == cellKeys.size(), "Missing or split native cells");
                JsonObject check = new JsonObject();
                check.addProperty("packet", file.getFileName().toString());
                check.addProperty("recipes", counted);
                check.addProperty("cells", book.getRecipes().size());
                check.addProperty("status", "PASS");
                checks.add(check);
                if (file.getFileName().toString().equals("recipes-normal-sweep.bin")) render(minecraft, book);
            }
        } catch (Exception failure) { throw new IllegalStateException("Native recipe-book validation failed", failure); }
        report.addProperty("recipe_book_grouping_status", "PASS");
    }

    private static void render(Minecraft minecraft, ClientRecipeBook book) throws Exception {
        List<RecipeList> shown = new ArrayList<>();
        Set<String> selected = new HashSet<>(java.util.Arrays.asList("electricadvantage:blank_circuit_board",
                "electricadvantage:integrated_circuit", "electricadvantage:control_circuit", "electricadvantage:psu", "poweradvantage:item_conveyor"));
        for (RecipeList cell : book.getRecipes()) {
            IRecipe<?> recipe = cell.getRecipes().get(0);
            String id = recipe.getRecipeOutput().getItem().getRegistryName().toString();
            if (selected.contains(id) || id.startsWith("poweradvantage:item_filter_")) shown.add(cell);
        }
        shown.sort(java.util.Comparator.comparing(cell -> cell.getRecipes().get(0).getRecipeOutput().getItem().getRegistryName().toString()));
        require(shown.size() == 13, "The thirteen reported circuit/conveyor cells must be separate");
        int width = 1000, height = 180;
        Framebuffer target = new Framebuffer(width, height, true, Minecraft.IS_RUNNING_ON_MAC);
        target.bindFramebuffer(true);
        RenderSystem.matrixMode(GL_PROJECTION);
        RenderSystem.pushMatrix(); RenderSystem.loadIdentity();
        RenderSystem.ortho(0, width, height, 0, -1000, 1000);
        RenderSystem.matrixMode(GL_MODELVIEW);
        RenderSystem.pushMatrix(); RenderSystem.loadIdentity();
        RenderSystem.disableDepthTest();
        try {
            RenderSystem.clearColor(0.12F, 0.12F, 0.12F, 1F);
            RenderSystem.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
            for (int i = 0; i < shown.size(); i++) {
                int x = 10 + (i % 5) * 198, y = 12 + (i / 5) * 55;
                RecipeWidget button = new RecipeWidget();
                // Native page initialization needs a connected player. Supply only its two
                // cell/book inputs; null means the ordinary crafting filter in RecipeBook.
                for (Field field : RecipeWidget.class.getDeclaredFields()) {
                    if (field.getType() == RecipeList.class || field.getType() == RecipeBook.class) {
                        field.setAccessible(true);
                        field.set(button, field.getType() == RecipeList.class ? shown.get(i) : book);
                    }
                }
                button.setPosition(x, y);
                button.render(-1, -1, 1F);
                RenderSystem.disableLighting();
                minecraft.fontRenderer.drawString(shown.get(i).getRecipes().get(0).getRecipeOutput().getDisplayName().getFormattedText(), x + 30, y + 8, 0xFFFFFF);
            }
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            RenderSystem.readPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int colored = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int index = ((height - y - 1) * width + x) * 4;
                int rgb = (pixels.get(index) & 255) << 16 | (pixels.get(index + 1) & 255) << 8 | pixels.get(index + 2) & 255;
                image.setRGB(x, y, rgb);
                if ((rgb & 255) > 80 || ((rgb >>> 16) & 255) > 80) colored++;
            }
            ImageIO.write(image, "png", new File("recipe-book-cells.png"));
            require(colored > 1000, "Native recipe buttons rendered blank");
        } finally {
            RenderSystem.popMatrix(); RenderSystem.matrixMode(GL_PROJECTION);
            RenderSystem.popMatrix(); RenderSystem.matrixMode(GL_MODELVIEW);
            target.deleteFramebuffer(); minecraft.getFramebuffer().bindFramebuffer(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
