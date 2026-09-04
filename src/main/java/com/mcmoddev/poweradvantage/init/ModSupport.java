package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.PowerAdvantage;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.modsupport.rf.BlockRFConverter;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFElectricityConverter;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFQuantumConverter;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFSteamConverter;
import cyano.poweradvantage.api.modsupport.techreborn.BlockTRConverter;
import cyano.poweradvantage.api.modsupport.techreborn.TileEntityTRElectricityConverter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.util.HashMap;
import java.util.Map;

public abstract class ModSupport {


	public static Block converter_rf_steam;
	public static Block converter_rf_electricity;
	public static Block converter_rf_quantum;
	public static Block converter_tr_electricity;

	private static final Map<String, Block> allBlocks = new HashMap<>();

	private static boolean contentRegistrationDone = false;
	private static boolean recipeRegistrationDone = false;
	private static int generatedRecipeId = 0;

	public static void registerContent(boolean rfSupport, boolean techRebornSupport) {
		if (contentRegistrationDone) return;

		final float defaultMachineHardness = 0.75f;
		final Material defaultMachineMaterial = Material.PISTON;

		if (rfSupport) {
			FMLLog.info("Initializing RF interface content");
			converter_rf_steam = addBlock(new BlockRFConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("steam"), TileEntityRFSteamConverter.class), "converter_rf_steam");
			converter_rf_electricity = addBlock(new BlockRFConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("electricity"), TileEntityRFElectricityConverter.class), "converter_rf_electricity");
			converter_rf_quantum = addBlock(new BlockRFConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("quantum"), TileEntityRFQuantumConverter.class), "converter_rf_quantum");

			Entities.registerTileEntity(TileEntityRFSteamConverter.class, "rf_steam_converter_tileentity");
			Entities.registerTileEntity(TileEntityRFElectricityConverter.class, "rf_electricity_converter_tileentity");
			Entities.registerTileEntity(TileEntityRFQuantumConverter.class, "rf_quantum_converter_tileentity");

		}
		if (techRebornSupport) {
			FMLLog.info("Initializing Tech Reborn interface content");
			converter_tr_electricity = addBlock(new BlockTRConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("electricity"), TileEntityTRElectricityConverter.class), "converter_tr_electricity");
			Entities.registerTileEntity(TileEntityTRElectricityConverter.class, "tr_electricity_converter_tileentity");
		}
		contentRegistrationDone = true;
	}

	public static void registerRecipes() {
		if (recipeRegistrationDone) return;
		if (!contentRegistrationDone) {
			throw new IllegalStateException("Mod support content must be registered before its recipes");
		}

		if (converter_rf_steam != null) {
			ForgeRegistries.RECIPES.register(recipe(new ItemStack(converter_rf_steam, 1),
					"xyz",
					'x', "governor", 'y', "frameSteel", 'z', "blockRedstone"));
			ForgeRegistries.RECIPES.register(recipe(new ItemStack(converter_rf_electricity, 1),
					"xyz",
					'x', "PSU", 'y', "frameSteel", 'z', "blockRedstone"));
			ForgeRegistries.RECIPES.register(recipe(new ItemStack(converter_rf_quantum, 1),
					"xyz",
					'x', net.minecraft.init.Items.ENDER_PEARL, 'y', "frameSteel", 'z', "blockRedstone"));
		}
		if (converter_tr_electricity != null) {
			ForgeRegistries.RECIPES.register(recipe(new ItemStack(converter_tr_electricity, 1), "XXX", "XZX", "XXX",
					'Z', "PSU", 'X', "ingotRefinedIron"));
		}
		recipeRegistrationDone = true;
	}

	private static ShapedOreRecipe recipe(ItemStack output, Object... params) {
		String name = "modsupport_generated_" + generatedRecipeId++;
		ResourceLocation registryName = new ResourceLocation(PowerAdvantage.MODID, name);
		ShapedOreRecipe recipe = new ShapedOreRecipe(registryName, output, params);
		recipe.setRegistryName(registryName);
		return recipe;
	}

	@SuppressWarnings("deprecation")
	private static Block addBlock(Block block, String name) {
		block.setTranslationKey(PowerAdvantage.MODID + "." + name);
		ResourceLocation registryName = new ResourceLocation(PowerAdvantage.MODID, name);
		block.setRegistryName(registryName);
		ForgeRegistries.BLOCKS.register(block);
		ItemBlock itemBlock = new ItemBlock(block);
		itemBlock.setRegistryName(registryName);
		ForgeRegistries.ITEMS.register(itemBlock);
		if ((block instanceof BlockFluidBase) == false) block.setCreativeTab(ItemGroups.tab_powerAdvantage);
		allBlocks.put(name, block);
		return block;
	}


	@SideOnly(Side.CLIENT)
	public static void bakeModels() {
		for (Map.Entry<String, Block> e : allBlocks.entrySet()) {
			String name = e.getKey();
			Block block = e.getValue();
			ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
					new ModelResourceLocation(PowerAdvantage.MODID + ":" + name, "inventory"));
		}
	}
}
