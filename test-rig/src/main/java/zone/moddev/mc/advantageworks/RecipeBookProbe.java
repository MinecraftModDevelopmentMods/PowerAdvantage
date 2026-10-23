package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketUpdateRecipes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.crafting.VanillaRecipeTypes;
import static zone.moddev.mc.advantageworks.ProgressionProbe.*;

/** Native crafting and server-to-client recipe payload, not a source-JSON simulation. */
final class RecipeBookProbe {
    static void verify(MinecraftServer server, WorldServer world, String label, JsonObject report) throws Exception {
        JsonArray entries = new JsonArray();
        List<IRecipe> recipes = new ArrayList<>();
        for (IRecipe recipe : server.getRecipeManager().getRecipes()) {
            String namespace = recipe.getId().getNamespace();
            if (!(namespace.equals("poweradvantage") || namespace.equals("steamadvantage") || namespace.equals("electricadvantage"))) continue;
            if (recipe.getType() != VanillaRecipeTypes.CRAFTING && recipe.getType() != VanillaRecipeTypes.SMELTING) continue;
            String output = id(recipe.getRecipeOutput());
            require(output.equals(recipe.getGroup()), "Mixed-output recipe group: " + recipe.getId() + " -> " + recipe.getGroup());
            JsonObject entry = new JsonObject();
            entry.addProperty("id", recipe.getId().toString());
            entry.addProperty("group", recipe.getGroup());
            entry.addProperty("type", recipe.getType().getId().toString());
            entry.addProperty("output", output);
            entry.addProperty("count", recipe.getRecipeOutput().getCount());
            entries.add(entry);
            recipes.add(recipe);
        }
        report.add("bookRecipes", entries);
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            new SPacketUpdateRecipes(recipes).writePacketData(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            Files.write(Paths.get("recipes-" + label + ".bin"), bytes);
        } finally { buffer.release(); }
        IRecipe starch = server.getRecipeManager().getRecipe(new ResourceLocation("poweradvantage:starch_from_potato"));
        require(starch != null && starch.getType() == VanillaRecipeTypes.CRAFTING, "Missing native potato crafting recipe");
        int cases = 0;
        for (int width : new int[] {2, 3}) for (String potato : new String[] {"minecraft:potato", "minecraft:poisonous_potato"}) {
            for (int slot = 0; slot < width * width; slot++) for (int count : new int[] {1, 64}) {
                InventoryCrafting grid = grid(width);
                ItemStack input = stack(potato);
                input.setCount(count);
                grid.setInventorySlotContents(slot, input);
                require(starch.matches(grid, world), "Potato recipe rejected slot/stack " + slot + "/" + count);
                IRecipe selected = world.getRecipeManager().getRecipe(grid, world, VanillaRecipeTypes.CRAFTING);
                require(selected != null && selected.getId().equals(starch.getId()), "Conflicting native potato crafting recipe");
                ItemStack result = starch.getCraftingResult(grid);
                require(id(result).equals("poweradvantage:starch") && result.getCount() == 1, "Starch output changed");
                require(grid.getStackInSlot(slot).getCount() == count, "Result preview mutated input");
                ItemStack consumed = grid.decrStackSize(slot, 1);
                require(consumed.getCount() == 1 && grid.getStackInSlot(slot).getCount() == count - 1,
                        "Crafting must consume exactly one potato");
                cases++;
            }
        }
        for (String item : new String[] {null, "minecraft:baked_potato", "minecraft:carrot", "minecraft:dirt"}) {
            InventoryCrafting grid = grid(3);
            if (item != null) grid.setInventorySlotContents(0, stack(item));
            require(!starch.matches(grid, world), "Invalid starch input accepted: " + item);
            cases++;
        }
        for (String second : new String[] {"minecraft:potato", "minecraft:poisonous_potato", "minecraft:dirt"}) {
            InventoryCrafting grid = grid(3);
            grid.setInventorySlotContents(0, stack("minecraft:potato"));
            grid.setInventorySlotContents(1, stack(second));
            require(!starch.matches(grid, world), "Extra occupied ingredient slot accepted");
            cases++;
        }
        report.addProperty("potatoCraftingCases", cases);
    }

    private static InventoryCrafting grid(int width) {
        return new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
        }, width, width);
    }
}
