package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.IPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.RecipeMode;

/** Manufacture from declared natural resources; no pre-supplied Advantage parts. */
final class ProgressionProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File MARKER = new File("advantage-progression.allow");

    static void register() {
        if (MARKER.isFile()) MinecraftForge.EVENT_BUS.addListener(new ProgressionProbe()::starting);
    }

    private void starting(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advprogression").requires(source -> source.hasPermissionLevel(4))
                .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                    String label = StringArgumentType.getString(context, "label");
                    try { audit(context.getSource().getServer(), context.getSource().getWorld(), label); }
                    catch (Throwable error) {
                        System.err.println("ADVPROGRESSION FAIL " + error);
                        error.printStackTrace();
                        throw new IllegalStateException(error);
                    }
                    System.out.println("ADVPROGRESSION COMPLETE " + label);
                    return 1;
                })));
    }

    private void audit(MinecraftServer server, ServerWorld world, String label) throws Exception {
        require(MARKER.isFile() && world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")
                && label.matches("[A-Za-z0-9_-]+"), "Unmarked world or unsafe label");
        JsonObject fixture = JSON.fromJson(new String(Files.readAllBytes(new File("progression-fixture.json").toPath()),
                StandardCharsets.UTF_8), JsonObject.class);
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, new GameProfile(
                UUID.fromString("3ea512a5-4c41-42ac-901f-7fabd2b1cbde"), "ProgressionProbe"), new PlayerInteractionManager(world));
        player.connection = new ServerPlayNetHandler(server, new NetworkManager(PacketDirection.SERVERBOUND), player) {
            @Override public void sendPacket(IPacket<?> packet) {}
        };
        for (Advancement advancement : server.getAdvancementManager().getAllAdvancements()) {
            if (owned(advancement.getId())) for (String criterion : advancement.getCriteria().keySet())
                player.getAdvancements().revokeCriterion(advancement, criterion);
        }
        List<IRecipe<?>> oldBook = new ArrayList<>();
        for (IRecipe<?> recipe : server.getRecipeManager().getRecipes()) if (owned(recipe.getId())) oldBook.add(recipe);
        player.resetRecipes(oldBook);
        ProcessProgression.reset();
        Map<String, JsonObject> known = new LinkedHashMap<>();
        Set<String> executed = new LinkedHashSet<>();
        for (JsonElement seed : fixture.getAsJsonArray("naturalResources")) {
            String id = seed.getAsString();
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null) acquire(known, player, new ItemStack(item), "natural_resource", Collections.emptyList());
        }
        JsonArray loot = new JsonArray();
        for (int seed = 0; seed < 8; seed++) {
            List<ItemStack> overridden = server.getLootTableManager().getLootTableFromLocation(
                    new ResourceLocation("minecraft:chests/simple_dungeon")).generate(chestContext(world, seed));
            require(overridden.size() == 1 && id(overridden.get(0)).equals("minecraft:dirt")
                    && overridden.get(0).getCount() == 1, "Player loot data-pack override was changed");
        }
        if (PowerAdvantage.recipeMode == RecipeMode.APOCALYPTIC) {
            for (int i = 0; i < 4096; i++) {
                for (ItemStack stack : server.getLootTableManager().getLootTableFromLocation(
                        new ResourceLocation("minecraft:chests/abandoned_mineshaft")).generate(chestContext(world, i))) {
                    if (advantage(stack)) {
                        if (acquire(known, player, stack, "vanilla_chest_loot", Collections.emptyList())) loot.add(id(stack));
                    }
                }
            }
        }
        List<IRecipe<?>> recipes = new ArrayList<>(server.getRecipeManager().getRecipes());
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        boolean changed = true;
        int rounds = 0;
        while (changed) {
            require(++rounds <= 100, "Progression closure did not converge");
            changed = false;
            for (IRecipe<?> recipe : recipes) {
                if (executed.contains(recipe.getId().toString()) || recipe.getIngredients().isEmpty()) continue;
                String type = net.minecraft.util.registry.Registry.RECIPE_TYPE.getKey(recipe.getType()).toString();
                boolean crafting = recipe.getType() == IRecipeType.CRAFTING;
                boolean smelting = recipe.getType() == IRecipeType.SMELTING;
                boolean crushing = type.equals("basemetals:crushing");
                if (!crafting && !smelting && !crushing) continue;
                if (smelting && (!known.containsKey("minecraft:furnace") || !known.containsKey("minecraft:coal"))) continue;
                if (crushing && known.keySet().stream().noneMatch(key -> key.startsWith("basemetals:") && key.endsWith("_crackhammer"))) continue;
                List<ItemStack> ingredients = new ArrayList<>();
                boolean possible = true;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) { ingredients.add(ItemStack.EMPTY); continue; }
                    ItemStack chosen = ItemStack.EMPTY;
                    for (ItemStack candidate : ingredient.getMatchingStacks()) {
                        if (!candidate.isEmpty() && known.containsKey(id(candidate))) { chosen = candidate.copy(); chosen.setCount(1); break; }
                    }
                    if (chosen.isEmpty()) { possible = false; break; }
                    ingredients.add(chosen);
                }
                if (!possible) continue;
                if (crushing && !ProcessProgression.canCrush(known, ingredients.get(0))) continue;
                ItemStack result = ItemStack.EMPTY;
                if (crafting) {
                    Container container = new Container(null, 0) {
                        @Override public boolean canInteractWith(net.minecraft.entity.player.PlayerEntity p) { return true; }
                    };
                    for (int width = 1; width <= 3 && result.isEmpty(); width++) {
                        if (ingredients.size() > width * 3) continue;
                        CraftingInventory grid = new CraftingInventory(container, 3, 3);
                        for (int i = 0; i < ingredients.size(); i++) grid.setInventorySlotContents((i / width) * 3 + i % width, ingredients.get(i));
                        result = matchedResult(recipe, grid, world);
                    }
                } else {
                    Inventory grid = new Inventory(1);
                    grid.setInventorySlotContents(0, ingredients.get(0));
                    result = matchedResult(recipe, grid, world);
                }
                if (result.isEmpty()) continue;
                if (owned(recipe.getId()) && !crushing) {
                    Advancement unlock = server.getAdvancementManager().getAdvancement(new ResourceLocation(
                            recipe.getId().getNamespace(), "recipes/" + recipe.getId().getPath()));
                    require(unlock != null && player.getAdvancements().getProgress(unlock).isDone()
                            && player.getRecipeBook().isUnlocked(recipe), "Manufacture lacks ingredient discovery: " + recipe.getId());
                }
                if (owned(recipe.getId()) && smelting)
                    ProcessProgression.smelt(world, fixture, recipe.getId().toString(), ingredients.get(0), result);
                if (owned(recipe.getId()) && crushing && !(ingredients.get(0).getItem() instanceof net.minecraft.item.BlockItem))
                    ProcessProgression.hammer(world, player, known, fixture, recipe.getId().toString(), ingredients.get(0), result);
                executed.add(recipe.getId().toString());
                List<String> inputs = new ArrayList<>();
                for (ItemStack ingredient : ingredients) if (!ingredient.isEmpty()) inputs.add(id(ingredient));
                changed |= acquire(known, player, result, recipe.getId().toString(), inputs);
            }
            changed |= ProcessProgression.advance(world, player, known, fixture);
        }
        JsonArray targets = new JsonArray();
        JsonObject exclusions = fixture.getAsJsonObject("exclusions");
        int blocked = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!owned(item.getRegistryName())) continue;
            String id = item.getRegistryName().toString();
            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            if (exclusions.has(id)) {
                row.addProperty("status", "INTENTIONAL_EXCLUSION");
                row.addProperty("reason", exclusions.get(id).getAsString());
            } else if (PowerAdvantage.recipeMode == RecipeMode.APOCALYPTIC && id.equals("electricadvantage:photovoltaic_generator")) {
                row.addProperty("status", "INTENTIONAL_MODE_RESTRICTION");
                row.addProperty("reason", "Legacy Apocalyptic mode forbids new photovoltaic generators; use hydro or steam generation");
            } else if (known.containsKey(id)) {
                row.addProperty("status", "PASS");
                row.add("acquisition", known.get(id));
            } else { row.addProperty("status", "BLOCKED"); blocked++; }
            targets.add(row);
        }
        JsonObject report = new JsonObject();
        JsonArray missingRecipes = new JsonArray();
        for (IRecipe recipe : recipes) {
            if (!owned(recipe.getId()) || (recipe.getType() != IRecipeType.CRAFTING
                    && recipe.getType() != IRecipeType.SMELTING)) continue;
            if (!executed.contains(recipe.getId().toString())) missingRecipes.add(recipe.getId().toString());
        }
        if (missingRecipes.size() > 0) blocked += missingRecipes.size();
        report.addProperty("status", blocked == 0 ? "PASS" : "FAIL");
        report.addProperty("mode", PowerAdvantage.recipeMode.name());
        report.addProperty("plasticEqualsRubber", PowerAdvantage.plasticIsAlsoRubber);
        report.addProperty("rounds", rounds);
        report.addProperty("blocked", blocked);
        report.add("targets", targets);
        report.add("reachable", JSON.toJsonTree(known));
        report.add("executedRecipes", JSON.toJsonTree(executed));
        report.add("blockedRecipes", missingRecipes);
        report.add("lootSeeds", loot);
        report.addProperty("lootDataPackOverrideVerified", true);
        report.add("processWitnesses", ProcessProgression.witnesses());
        JsonObject tags = new JsonObject();
        for (String name : new String[] {"wires", "dusts/carbon", "rubber", "plastic", "circuit_boards", "solder"}) {
            List<String> members = new ArrayList<>();
            for (Item item : ItemTags.getCollection().getOrCreate(new ResourceLocation("forge", name)).getAllElements())
                members.add(item.getRegistryName().toString());
            Collections.sort(members);
            tags.add(name, JSON.toJsonTree(members));
        }
        report.add("tags", tags);
        Files.write(new File("progression-" + label + ".json").toPath(), JSON.toJson(report).getBytes(StandardCharsets.UTF_8));
    }

    static boolean acquire(Map<String, JsonObject> known, ServerPlayerEntity player, ItemStack output, String route, List<String> inputs) {
        if (output == null || output.isEmpty() || known.containsKey(id(output))) return false;
        JsonObject node = new JsonObject();
        node.addProperty("via", route);
        node.addProperty("outputCount", output.getCount());
        node.add("inputs", JSON.toJsonTree(inputs));
        known.put(id(output), node);
        player.inventory.clear();
        player.inventory.setInventorySlotContents(0, output.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.inventory);
        return true;
    }

    static ItemStack stack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static LootContext chestContext(ServerWorld world, int seed) {
        return new LootContext.Builder(world).withRandom(new Random(seed))
                .withParameter(net.minecraft.world.storage.loot.LootParameters.POSITION, new BlockPos(0, 80, 0))
                .build(net.minecraft.world.storage.loot.LootParameterSets.CHEST);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack matchedResult(IRecipe<?> recipe, net.minecraft.inventory.IInventory inventory, ServerWorld world) {
        IRecipe<net.minecraft.inventory.IInventory> typed = (IRecipe<net.minecraft.inventory.IInventory>) recipe;
        return typed.matches(inventory, world) ? typed.getCraftingResult(inventory) : ItemStack.EMPTY;
    }

    private static boolean advantage(ItemStack stack) { return !stack.isEmpty() && owned(stack.getItem().getRegistryName()); }
    private static boolean owned(ResourceLocation id) {
        return id != null && (id.getNamespace().equals("poweradvantage") || id.getNamespace().equals("steamadvantage")
                || id.getNamespace().equals("electricadvantage"));
    }
    static String id(ItemStack stack) { return stack.getItem().getRegistryName().toString(); }
    static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
}
