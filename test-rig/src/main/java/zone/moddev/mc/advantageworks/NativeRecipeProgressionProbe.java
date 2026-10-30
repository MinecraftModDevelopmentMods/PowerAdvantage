package zone.moddev.mc.advantageworks;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.common.crafting.IShapedRecipe;

/** Bounded native crafting/registration audit, not a live machine or GUI claim. */
final class NativeRecipeProgressionProbe {
    private static final File MARKER = new File("advantage-legacy-recipes.allow");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static void registerCommands(FMLServerStartingEvent event) {
        if (!MARKER.isFile()) return;
        event.registerServerCommand(new CommandBase() {
            @Override public String getName() { return "advlegacyrecipes"; }
            @Override public String getUsage(ICommandSender sender) { return "/advlegacyrecipes <label>"; }
            @Override public int getRequiredPermissionLevel() { return 4; }
            @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if (args.length != 1) throw new CommandException(getUsage(sender));
                try { audit(server, server.getWorld(0), args[0]); }
                catch (Exception failure) { throw new IllegalStateException("Legacy recipe audit failed", failure); }
            }
        });
    }

    private static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static boolean empty(ItemStack stack) { return stack == null || stack.isEmpty(); }
    private static String token(ItemStack stack) {
        return empty(stack) ? null : stack.getItem().getRegistryName() + "@" + stack.getMetadata();
    }
    private static boolean owned(String token) {
        return token != null && (token.startsWith("poweradvantage:") || token.startsWith("steamadvantage:") || token.startsWith("electricadvantage:"));
    }
    private static void add(JsonArray target, String value) { target.add(value == null ? JsonNull.INSTANCE : new JsonPrimitive(value)); }
    private static Object invoke(Object value, String method) throws Exception {
        Method call = value.getClass().getMethod(method); call.setAccessible(true); return call.invoke(value);
    }
    private static Object crusherRecipe(Class<?> registry, ItemStack input) throws Exception {
        Method lookup = registry.getMethod("getRecipeForInputItem", ItemStack.class);
        lookup.setAccessible(true);
        // BaseMetals versions expose either a singleton instance or a static lookup.
        Object receiver = Modifier.isStatic(lookup.getModifiers()) ? null : registry.getMethod("getInstance").invoke(null);
        require(Modifier.isStatic(lookup.getModifiers()) || registry.isInstance(receiver), "Invalid BaseMetals crusher registry singleton");
        return lookup.invoke(receiver, input);
    }
    private static void recordFailure(JsonObject row, String stage, Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        row.addProperty("status", "UNVERIFIED");
        row.addProperty("failureStage", stage);
        row.addProperty("reason", failure.toString());
        row.addProperty("stackTrace", trace.toString());
    }
    private static EmbeddedChannel recipeChannel(NetworkManager network) {
        EmbeddedChannel channel = new EmbeddedChannel(network);
        require(network.channel() == channel && channel.isActive(), "Native recipe player channel was not initialized");
        return channel;
    }
    private static int width(IRecipe recipe) {
        return recipe instanceof IShapedRecipe ? ((IShapedRecipe)recipe).getRecipeWidth() : 3;
    }

    private static List<List<ItemStack>> ingredients(IRecipe recipe) {
        List<List<ItemStack>> result=new ArrayList<>();
        for (Ingredient cell : recipe.getIngredients()) result.add(new ArrayList<>(Arrays.asList(cell.getMatchingStacks())));
        return result;
    }

    private static List<ItemStack> remainders(IRecipe recipe, InventoryCrafting grid) {
        return new ArrayList<>(recipe.getRemainingItems(grid));
    }

    private static ItemStack prototype(List<ItemStack> choices) {
        for (ItemStack choice : choices) if (!empty(choice)) {
            ItemStack selected=choice.copy(); selected.setCount(1);
            if (selected.getMetadata()==OreDictionary.WILDCARD_VALUE) selected.setItemDamage(0);
            return selected;
        }
        return ItemStack.EMPTY;
    }

    private static JsonObject craft(WorldServer world, EntityPlayerMP player, IRecipe recipe, int index) {
        JsonObject row=new JsonObject();
        row.addProperty("id","craft/"+index); row.addProperty("output",token(recipe.getRecipeOutput()));
        row.addProperty("count",empty(recipe.getRecipeOutput()) ? 0 : recipe.getRecipeOutput().getCount());
        row.addProperty("owned",owned(token(recipe.getRecipeOutput())));
        row.addProperty("class",recipe.getClass().getName());
        String stage="ingredients";
        try {
            List<List<ItemStack>> cells=ingredients(recipe);
            require(!cells.isEmpty() && cells.size()<=9,"Unsupported/dynamic recipe ingredients");
            int width=width(recipe);
            require(width>0 && width<=3 && (cells.size()+width-1)/width<=3,"Unsupported grid dimensions");
            Container owner=new Container() {
                @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
            };
            InventoryCrafting grid=new InventoryCrafting(owner,3,3);
            JsonArray inputs=new JsonArray(), providers=new JsonArray();
            int first=-1;
            for (int i=0; i<cells.size(); i++) {
                ItemStack selected=prototype(cells.get(i));
                int slot=(i/width)*3+i%width;
                grid.setInventorySlotContents(slot,selected);
                add(inputs,token(selected));
                JsonArray choices=new JsonArray();
                for (ItemStack choice : cells.get(i)) if (!empty(choice)) add(choices,token(choice));
                providers.add(choices);
                if (!empty(selected) && first<0) first=slot;
            }
            row.add("inputs",inputs); row.add("providers",providers);
            stage="native-match";
            require(first>=0 && recipe.matches(grid,world),"Missing provider or native recipe does not match its concrete grid");
            stage="native-result";
            ItemStack output=recipe.getCraftingResult(grid);
            ItemStack global=CraftingManager.findMatchingResult(grid,world);
            require(!empty(output) && ItemStack.areItemStacksEqual(output,recipe.getRecipeOutput())
                    && ItemStack.areItemStacksEqual(output,global),"Native global craft output/count differs");
            ItemStack saved=grid.getStackInSlot(first);
            stage="negative-controls";
            grid.setInventorySlotContents(first,ItemStack.EMPTY);
            require(!recipe.matches(grid,world),"Missing-ingredient negative control matched");
            grid.setInventorySlotContents(first,new ItemStack(net.minecraft.init.Blocks.BEDROCK));
            require(!recipe.matches(grid,world),"Wrong-ingredient negative control matched");
            grid.setInventorySlotContents(first,saved);
            row.addProperty("negativeControlsRejected",2);
            if (owned(token(output))) {
                stage="native-remainders";
                List<ItemStack> remainders=remainders(recipe,grid);
                stage="player-inventory-clear";
                player.inventory.clear();
                stage="recipe-book-reset";
                player.resetRecipes(Collections.singletonList(recipe));
                stage="recipe-book-locked-control";
                require(!player.getRecipeBook().isUnlocked(recipe),"Recipe-book negative control was not locked");
                stage="result-slot-setup";
                InventoryCraftResult resultInventory=new InventoryCraftResult();
                resultInventory.setInventorySlotContents(0,output.copy());
                resultInventory.setRecipeUsed(recipe);
                SlotCrafting slot=new SlotCrafting(player,grid,resultInventory,0,0,0);
                stage="native-slot-decrement";
                ItemStack taken=slot.decrStackSize(output.getCount());
                stage="native-slot-on-take";
                slot.onTake(player,taken);
                stage="crafted-output-and-remainders";
                require(Objects.equals(token(taken),token(output)) && taken.getCount()==output.getCount(),"Native crafted output changed");
                for (int i=0; i<9; i++) require(ItemStack.areItemStacksEqual(grid.getStackInSlot(i),remainders.get(i)),
                        "Native crafting did not consume inputs/return containers exactly");
                row.addProperty("consumption","NATIVE_SLOT_CRAFTING");
                row.addProperty("actualCrafted",token(taken)); row.addProperty("actualCraftedCount",taken.getCount());
                stage="recipe-book-unlock-check";
                require(player.getRecipeBook().isUnlocked(recipe),"Actual native craft did not unlock its recipe");
                row.addProperty("recipeBookCraftUnlock","PASS");
            } else row.addProperty("consumption","MATCH_AND_RESULT_ONLY");
            row.addProperty("status","PASS");
        } catch (Exception | LinkageError failure) { recordFailure(row,stage,failure); }
        return row;
    }

    private static void audit(MinecraftServer server, WorldServer world, String label) throws Exception {
        require(MARKER.isFile() && new File("advantage-migration.allow").isFile()
                && world.provider.getDimension()==0 && world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")
                && label.matches("[A-Za-z0-9_-]+"),"Unsafe recipe audit runtime");
        File output=new File("advantage-works-results","legacy-recipes-"+label+".json");
        require(!output.exists(),"Never overwrite recipe evidence");
        EntityPlayerMP player=new EntityPlayerMP(server,world,new GameProfile(
                UUID.fromString("3ea512a5-4c41-42ac-901f-7fabd2b1cbde"),"RecipeAudit"),new PlayerInteractionManager(world));
        NetworkManager network=new NetworkManager(EnumPacketDirection.SERVERBOUND);
        EmbeddedChannel channel=recipeChannel(network);
        try {
        player.connection=new NetHandlerPlayServer(server,network,player) {
            @Override public void sendPacket(Packet<?> packet) {}
        };
        player.interactionManager.setGameType(net.minecraft.world.GameType.SURVIVAL);
        Class<?> power=Class.forName("com.mcmoddev.poweradvantage.PowerAdvantage");
        JsonObject report=new JsonObject();
        report.addProperty("minecraft","1.12.2"); report.addProperty("label",label);
        report.addProperty("mode",power.getField("recipeMode").get(null).toString());
        report.addProperty("rubber",power.getField("plasticIsAlsoRubber").getBoolean(null));
        report.addProperty("scope","NATIVE_CRAFT_CONSUMPTION_AND_REGISTERED_PROCESS_FUNCTIONS; NOT_LIVE_MACHINES");
        report.addProperty("recipeBookCraftUnlock","CHECKED_PER_OWNED_CRAFT");
        report.addProperty("ingredientAdvancements","NOT_TESTED");
        report.addProperty("playerTransport","NATIVE_NETWORK_MANAGER_IN_MEMORY_CHANNEL; SERVER_RECIPE_BOOK_ONLY_NOT_CLIENT_HANDSHAKE");
        JsonArray recipes=new JsonArray(), targets=new JsonArray();
        int index=0;
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            if (!empty(recipe.getRecipeOutput())) recipes.add(craft(world,player,recipe,index));
            index++;
        }
        report.add("crafting",recipes);
        JsonObject ores=new JsonObject();
        for (String name : OreDictionary.getOreNames()) {
            JsonArray choices=new JsonArray();
            for (ItemStack stack : OreDictionary.getOres(name)) if (!empty(stack)) add(choices,token(stack));
            ores.add(name,choices);
        }
        report.add("ores",ores);
        List<ItemStack> materials=new ArrayList<>();
        JsonArray blockItems=new JsonArray();
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack=new ItemStack(item);
            if (empty(stack)) continue;
            materials.add(stack);
            if (item instanceof ItemBlock) add(blockItems,token(stack));
            if (owned(token(stack))) add(targets,token(stack));
        }
        report.add("targets",targets);
        report.add("blockItems",blockItems);
        JsonArray smelting=new JsonArray();
        for (Map.Entry<ItemStack,ItemStack> entry : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            ItemStack input=entry.getKey().copy();
            if (input.getMetadata()==OreDictionary.WILDCARD_VALUE) input.setItemDamage(0);
            ItemStack actual=FurnaceRecipes.instance().getSmeltingResult(input);
            if (!empty(actual) && ItemStack.areItemStacksEqual(actual,entry.getValue())) {
                JsonObject edge=new JsonObject(); edge.addProperty("input",token(input)); edge.addProperty("output",token(actual));
                edge.addProperty("count",actual.getCount()); smelting.add(edge);
            }
        }
        report.add("smelting",smelting);
        JsonArray crushing=new JsonArray();
        Class<?> registry=null;
        for (String name : new String[]{"cyano.basemetals.registry.CrusherRecipeRegistry","com.mcmoddev.lib.registry.CrusherRecipeRegistry"}) {
            try { registry=Class.forName(name); break; } catch (ClassNotFoundException missing) {}
        }
        report.addProperty("crusherRegistry",registry==null ? "MISSING" : registry.getName());
        if (registry!=null) for (ItemStack input : materials) {
            try {
                Object recipe=crusherRecipe(registry,input);
                if (recipe==null) continue;
                ItemStack result=(ItemStack)invoke(recipe,"getOutput");
                if (empty(result)) continue;
                JsonObject edge=new JsonObject(); edge.addProperty("input",token(input)); edge.addProperty("output",token(result));
                edge.addProperty("count",result.getCount());
                JsonArray tools=new JsonArray();
                for (ItemStack hammer : materials) if (token(hammer).contains("basemetals:") && token(hammer).contains("crackhammer")) {
                    if (!(input.getItem() instanceof ItemBlock) || hammer.getItem().canHarvestBlock(((ItemBlock)input.getItem()).getBlock().getDefaultState()))
                        add(tools,token(hammer));
                }
                edge.add("tools",tools); crushing.add(edge);
            } catch (Exception failure) { report.addProperty("crusherLookupError",failure.toString()); break; }
        }
        report.add("crushing",crushing);
        JsonArray loot=new JsonArray();
        if (report.get("mode").getAsString().equals("APOCALYPTIC")) {
            LootContext context=new LootContext.Builder(world).build();
            for (String table : new String[]{"abandoned_mineshaft","simple_dungeon","village_blacksmith","stronghold_corridor","stronghold_crossing","end_city_treasure","nether_bridge"}) {
                for (int seed=0; seed<256; seed++) for (ItemStack stack : world.getLootTableManager()
                        .getLootTableFromLocation(new ResourceLocation("minecraft:chests/"+table)).generateLootForPools(new Random(seed),context)) {
                    if (owned(token(stack))) { JsonObject row=new JsonObject(); row.addProperty("item",token(stack));
                        row.addProperty("table",table); row.addProperty("seed",seed); loot.add(row); }
                }
            }
        }
        report.add("sampledNativeLoot",loot);
        report.addProperty("status","EXPORTED_NOT_QUALIFIED");
        output.getParentFile().mkdirs();
        Files.write(output.toPath(),JSON.toJson(report).getBytes(StandardCharsets.UTF_8));
        System.out.println("ADVLEGACYRECIPES COMPLETE "+label);
        } finally { channel.finishAndReleaseAll(); }
    }
}
