package cyano.poweradvantage.init;

import com.mcmoddev.basemetals.BaseMetals;
import com.mcmoddev.basemetals.data.MaterialNames;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.registry.CrusherRecipeRegistry;
import com.mcmoddev.lib.util.Config.Options;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import cyano.poweradvantage.PowerAdvantage;
import cyano.poweradvantage.RecipeMode;
import cyano.poweradvantage.registry.still.recipe.DistillationRecipeRegistry;

@Mod.EventBusSubscriber
public abstract class Recipes {

	/*
     * Normal - can craft all necessary machine parts
	 * Apocalyptic - must find key parts as treasure in chests
	 * Tech-progression - making the first key part is very complicated, but once made, key parts can be duplicated fairly easily
	 */

    private static boolean initDone = false;

    static void addRecipe(RegistryEvent.Register<IRecipe> event, ItemStack stack, Object... recipe) {
        event.getRegistry().register(new ShapedOreRecipe(stack.getItem().getRegistryName(), stack, recipe)
                .setRegistryName(stack.getItem().getRegistryName()));
    }

    static void addRecipe(RegistryEvent.Register<IRecipe> event, ItemStack stack, ResourceLocation location, Object... recipe) {
        event.getRegistry().register(new ShapedOreRecipe(stack.getItem().getRegistryName(), stack, recipe)
                .setRegistryName(location));
    }

    @SubscribeEvent
    public static void init(RegistryEvent.Register<IRecipe> event) {
        if (initDone) return;
        Blocks.init();
        System.out.println("Registering recipes");

        OreDictionary.registerOre("bread", net.minecraft.init.Items.BREAD);
        OreDictionary.registerOre("coal", net.minecraft.init.Items.COAL);
        OreDictionary.registerOre("furnace", net.minecraft.init.Blocks.FURNACE);

        OreDictionary.registerOre("potato", net.minecraft.init.Items.POISONOUS_POTATO);
        OreDictionary.registerOre("potato", net.minecraft.init.Items.POTATO);
        CrusherRecipeRegistry.addNewCrusherRecipe("potato", new ItemStack(Items.starch, 1));
        GameRegistry.addSmelting(Items.starch, new ItemStack(Items.bioplastic_ingot, 1), 0.1f);

        addRecipe(event, new ItemStack(Items.rotator_tool, 1), "xx", "x*", " x", 'x', net.minecraft.init.Items.IRON_INGOT, '*', "sprocket");

        addRecipe(event, new ItemStack(Blocks.item_conveyor, 5), "xxx", "ghg", "xxx", 'x', com.mcmoddev.basemetals.init.Blocks.getBlockByName("steel_plate"), 'g', "sprocket", 'h', net.minecraft.init.Blocks.HOPPER);
        addRecipe(event, new ItemStack(Blocks.item_filter_block, 1), "x", "y", "z", 'y', net.minecraft.init.Blocks.STONE, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_food, 1), "x", "y", "z", 'y',net.minecraft.init.Items.BREAD, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_fuel, 1), "x", "y", "z", 'y', net.minecraft.init.Items.COAL, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_inventory, 1), "x", "y", "z", 'y', net.minecraft.init.Blocks.CHEST, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_ore, 1), "x", "y", "z", 'y', net.minecraft.init.Items.GOLD_INGOT, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_plant, 1), "x", "y", "z", 'y', net.minecraft.init.Blocks.SAPLING, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_smelt, 1), "x", "y", "z", 'y', net.minecraft.init.Blocks.FURNACE, 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        addRecipe(event, new ItemStack(Blocks.item_filter_overflow, 1), "x", "z", 'x', Blocks.item_conveyor, 'z', net.minecraft.init.Blocks.WOODEN_PRESSURE_PLATE);
        Block steelBars = com.mcmoddev.basemetals.init.Blocks.getBlockByName("steel_bars");
        System.out.println(String.format("Steel bars block is: %s", steelBars.getRegistryName()));
        addRecipe(event, new ItemStack(Blocks.steel_frame, 1), "xxx", "x x", "xxx", 'x', steelBars);

        addRecipe(event, new ItemStack(Blocks.storage_tank, 1), "xxx", "xpx", "xxx", 'x', Items.bioplastic_ingot, 'p', Blocks.fluid_pipe);
        addRecipe(event, new ItemStack(Blocks.metal_storage_tank, 1), new ResourceLocation(PowerAdvantage.MODID, "metal_tank_steel"), "xxx", "xpx", "xxx",
                'x', Materials.getMaterialByName(MaterialNames.STEEL).getItem(Names.INGOT),
                'p', Blocks.fluid_pipe);
        addRecipe(event, new ItemStack(Blocks.fluid_drain, 1), " x ", "w#w", "ppp", 'x', com.mcmoddev.basemetals.init.Blocks.getBlockByName("brass_bars"), 'w', com.mcmoddev.basemetals.init.Blocks.getBlockByName("steel_plate"), '#', Blocks.steel_frame, 'p', Blocks.fluid_pipe);
        addRecipe(event, new ItemStack(Blocks.fluid_discharge, 1), "ppp", "w#w", " x ", 'x', steelBars, 'w', com.mcmoddev.basemetals.init.Blocks.getBlockByName("steel_plate"), '#', Blocks.steel_frame, 'p', Blocks.fluid_pipe);
        addRecipe(event, new ItemStack(Blocks.still, 1), "bpb", " f ", 'b', net.minecraft.init.Items.BUCKET, 'p', Blocks.fluid_pipe, 'f', net.minecraft.init.Blocks.FURNACE);
        addRecipe(event, new ItemStack(Blocks.fluid_switch, 1), " l ", "pfp", 'l', net.minecraft.init.Blocks.LEVER, 'p', Blocks.fluid_pipe, 'f', Blocks.steel_frame);


        // recipe modes
        if (PowerAdvantage.recipeMode == RecipeMode.NORMAL) {
            // normal means easy
            addRecipe(event, new ItemStack(Items.sprocket, 4), " x ", "x/x", " x ", 'x', com.mcmoddev.basemetals.init.Items.getItemByName("steel_ingot"), '/', net.minecraft.init.Items.STICK);
//            addRecipe(event, new ItemStack(Items.sprocket, 4), " x ", "x/x", " x ", 'x', com.mcmoddev.basemetals.init.Items.getItemByName("steel_ingot"), '/', net.minecraft.init.);


            addRecipe(event, new ItemStack(Blocks.fluid_pipe, 6), new ResourceLocation(PowerAdvantage.MODID, "fluid_pipe_copper"),
                    "xxx", "   ", "xxx", 'x', com.mcmoddev.basemetals.init.Items.getItemByName("copper_ingot"));
            addRecipe(event, new ItemStack(Blocks.fluid_pipe, 6), new ResourceLocation(PowerAdvantage.MODID, "fluid_pipe_iron"),
                    "yyy", "   ", "yyy", 'y', net.minecraft.init.Items.IRON_INGOT);
            addRecipe(event, new ItemStack(Blocks.metal_storage_tank, 1), new ResourceLocation(PowerAdvantage.MODID, "metal_tank_iron"), "xxx", "xpx", "xxx", 'x', net.minecraft.init.Items.IRON_INGOT, 'p', Blocks.fluid_pipe);
        } else if (PowerAdvantage.recipeMode == RecipeMode.TECH_PROGRESSION) {
            // make things a little more complicated with tech-progression mode
            //TODO We need a setStrongHammers(boolean b) method in MMDLib Config File to fix this.
            //TODO Fixed acording to Jas. ProxyNeko.
            Options.strongHammers();
            addRecipe(event, new ItemStack(Items.sprocket, 4), " x ", "x/x", " x ", 'x', "ingotSteel", '/', "rod");
            if (OreDictionary.getOres("rod").isEmpty())
                addRecipe(event, new ItemStack(Items.sprocket, 4), " x ", "x/x", " x ", 'x', "ingotSteel", '/', "nuggetBronze");
            addRecipe(event, new ItemStack(Blocks.fluid_pipe, 6), "xxx", "   ", "xxx", 'x', "ingotIron");
        } else if (PowerAdvantage.recipeMode == RecipeMode.APOCALYPTIC) {
            // apocalyptic means some things are not craftable, but some stuff can be recycled
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_conveyor, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_block, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_food, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_fuel, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_inventory, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_ore, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_plant, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_smelt, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.item_filter_overflow, new ItemStack(Materials.getMaterialByName(MaterialNames.STEEL).getBlock(Names.PLATE), 1));

            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.storage_tank, new ItemStack(Blocks.fluid_pipe, 1));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.fluid_discharge, new ItemStack(Blocks.fluid_pipe, 2));
            CrusherRecipeRegistry.addNewCrusherRecipe(Blocks.fluid_drain, new ItemStack(Blocks.fluid_pipe, 2));

            addRecipe(event, new ItemStack(Blocks.fluid_pipe, 3), "xxx", "   ", "xxx", 'x', "ingotIron");
            addRecipe(event, new ItemStack(Blocks.fluid_pipe, 3), "xxx", "   ", "xxx", 'x', "ingotCopper");
            addRecipe(event, new ItemStack(Blocks.fluid_pipe, 3), "xxx", "   ", "xxx", 'x', "ingotLead");
        }


        initDone = true;
    }

    public static void initDistillationRecipes(String[] distillRecipes) {
        for (String recipe : distillRecipes) {
            String r = recipe.trim();
            if (r.isEmpty()) continue;
            try {
                int numIn, numOut;
                String fluidIn, fluidOut;
                String inputStr = r.substring(0, r.indexOf("->")).trim();
                numIn = Integer.parseInt(inputStr.substring(0, inputStr.indexOf('*')).trim());
                fluidIn = inputStr.substring(inputStr.indexOf('*') + 1).trim();
                String outputStr = r.substring(inputStr.length() + "->".length()).trim();
                numOut = Integer.parseInt(outputStr.substring(0, outputStr.indexOf('*')).trim());
                fluidOut = outputStr.substring(outputStr.indexOf('*') + 1).trim();
                DistillationRecipeRegistry.addDistillationRecipe(fluidIn, numIn, fluidOut, numOut);
            } catch (Exception ex) {
                FMLLog.severe("%s: Failed to add fluid distillation recipe \"%s\". %s", PowerAdvantage.MODID, r, ex);
            }
        }
    }

}