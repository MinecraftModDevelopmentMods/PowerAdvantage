package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.item.crafting.IRecipeType;
import static zone.moddev.mc.advantageworks.ProgressionProbe.*;

/** Native crafting and server-to-client recipe payload, not a source-JSON simulation. */
final class RecipeBookProbe {
    static void verify(MinecraftServer server, ServerWorld world, String label, JsonObject report) throws Exception {
        JsonArray entries = new JsonArray();
        List<IRecipe<?>> recipes = new ArrayList<>();
        for (IRecipe<?> recipe : server.getRecipeManager().getRecipes()) {
            String namespace = recipe.getId().getNamespace();
            if (!(namespace.equals("poweradvantage") || namespace.equals("steamadvantage") || namespace.equals("electricadvantage"))) continue;
            if (recipe.getType() != IRecipeType.CRAFTING && recipe.getType() != IRecipeType.SMELTING) continue;
            String output = id(recipe.getResultItem());
            require(output.equals(recipe.getGroup()), "Mixed-output recipe group: " + recipe.getId() + " -> " + recipe.getGroup());
            JsonObject entry = new JsonObject();
            entry.addProperty("id", recipe.getId().toString());
            entry.addProperty("group", recipe.getGroup());
            entry.addProperty("type", net.minecraft.util.registry.Registry.RECIPE_TYPE.getKey(recipe.getType()).toString());
            entry.addProperty("output", output);
            entry.addProperty("count", recipe.getResultItem().getCount());
            entries.add(entry);
            recipes.add(recipe);
        }
        report.add("bookRecipes", entries);
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            new SUpdateRecipesPacket(recipes).write(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            Files.write(Paths.get("recipes-" + label + ".bin"), bytes);
        } finally { buffer.release(); }
        IRecipe starch = server.getRecipeManager().byKey(new ResourceLocation("poweradvantage:starch_from_potato")).orElse(null);
        require(starch != null && starch.getType() == IRecipeType.CRAFTING, "Missing native potato crafting recipe");
        int cases = 0;
        for (int width : new int[] {2, 3}) for (String potato : new String[] {"minecraft:potato", "minecraft:poisonous_potato"}) {
            for (int slot = 0; slot < width * width; slot++) for (int count : new int[] {1, 64}) {
                CraftingInventory grid = grid(width);
                ItemStack input = stack(potato);
                input.setCount(count);
                grid.setItem(slot, input);
                require(starch.matches(grid, world), "Potato recipe rejected slot/stack " + slot + "/" + count);
                IRecipe<?> selected = world.getRecipeManager().getRecipeFor(IRecipeType.CRAFTING, grid, world).orElse(null);
                require(selected != null && selected.getId().equals(starch.getId()), "Conflicting native potato crafting recipe");
                ItemStack result = starch.assemble(grid);
                require(id(result).equals("poweradvantage:starch") && result.getCount() == 1, "Starch output changed");
                require(grid.getItem(slot).getCount() == count, "Result preview mutated input");
                ItemStack consumed = grid.removeItem(slot, 1);
                require(consumed.getCount() == 1 && grid.getItem(slot).getCount() == count - 1,
                        "Crafting must consume exactly one potato");
                cases++;
            }
        }
        for (String item : new String[] {null, "minecraft:baked_potato", "minecraft:carrot", "minecraft:dirt"}) {
            CraftingInventory grid = grid(3);
            if (item != null) grid.setItem(0, stack(item));
            require(!starch.matches(grid, world), "Invalid starch input accepted: " + item);
            cases++;
        }
        for (String second : new String[] {"minecraft:potato", "minecraft:poisonous_potato", "minecraft:dirt"}) {
            CraftingInventory grid = grid(3);
            grid.setItem(0, stack("minecraft:potato"));
            grid.setItem(1, stack(second));
            require(!starch.matches(grid, world), "Extra occupied ingredient slot accepted");
            cases++;
        }
        report.addProperty("potatoCraftingCases", cases);
    }

    private static CraftingInventory grid(int width) {
        return new CraftingInventory(new Container(null, 0) {
            @Override public boolean stillValid(net.minecraft.entity.player.PlayerEntity player) { return true; }
        }, width, width);
    }
}
