package com.mcmoddev.poweradvantage.init;

import java.util.HashMap;
import java.util.Map;

import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.api.ConduitType;
import com.mcmoddev.poweradvantage.api.modsupport.rf.BlockRFConverter;
import com.mcmoddev.poweradvantage.api.modsupport.rf.TileEntityRFElectricityConverter;
import com.mcmoddev.poweradvantage.api.modsupport.rf.TileEntityRFQuantumConverter;
import com.mcmoddev.poweradvantage.api.modsupport.rf.TileEntityRFSteamConverter;
import com.mcmoddev.poweradvantage.api.modsupport.techreborn.BlockTRConverter;
import com.mcmoddev.poweradvantage.api.modsupport.techreborn.TileEntityTRElectricityConverter;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;

public abstract class ModSupport {


	public static Block converter_rf_steam;
	public static Block converter_rf_electricity;
	public static Block converter_rf_quantum;
	public static Block converter_tr_electricity;

	private static final Map<String, Block> allBlocks = new HashMap<>();

	private static boolean initDone = false;

	public static void init(boolean rfSupport, boolean techRebornSupport) {
		if (initDone) return;

		final float defaultMachineHardness = 0.75f;
		final Material defaultMachineMaterial = Material.PISTON;

		if (rfSupport) {
			FMLLog.info("Initializing RF interface content");
			converter_rf_steam = addBlock(new BlockRFConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("steam"), TileEntityRFSteamConverter.class), "converter_rf_steam");
			converter_rf_electricity = addBlock(new BlockRFConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("electricity"), TileEntityRFElectricityConverter.class), "converter_rf_electricity");
			converter_rf_quantum = addBlock(new BlockRFConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("quantum"), TileEntityRFQuantumConverter.class), "converter_rf_quantum");

			GameRegistry.registerTileEntity(TileEntityRFSteamConverter.class, PowerAdvantage.MODID + "." + "rf_steam_converter_tileentity");
			GameRegistry.registerTileEntity(TileEntityRFElectricityConverter.class, PowerAdvantage.MODID + "." + "rf_electricity_converter_tileentity");
			GameRegistry.registerTileEntity(TileEntityRFQuantumConverter.class, PowerAdvantage.MODID + "." + "rf_quantum_converter_tileentity");

//			addRecipe(event, new ItemStack(converter_rf_steam, 1),
//					"xyz",
//					'x', "governor", 'y', "frameSteel", 'z', "blockRedstone"));
//			addRecipe(event, new ItemStack(converter_rf_electricity, 1),
//					"xyz",
//					'x', "PSU", 'y', "frameSteel", 'z', "blockRedstone"));
//			addRecipe(event, new ItemStack(converter_rf_quantum, 1),
//					"xyz",
//					'x', net.minecraft.init.Items.ENDER_PEARL, 'y', "frameSteel", 'z', "blockRedstone"));
		}
		if (techRebornSupport) {
			FMLLog.info("Initializing Tech Reborn interface content");
			// first, register transformers in Ore Dictionary

			converter_tr_electricity = addBlock(new BlockTRConverter(defaultMachineMaterial, defaultMachineHardness, new ConduitType("electricity"), TileEntityTRElectricityConverter.class), "converter_tr_electricity");
			GameRegistry.registerTileEntity(TileEntityTRElectricityConverter.class, PowerAdvantage.MODID + "." + "tr_electricity_converter_tileentity");
//			addRecipe(event, new ItemStack(converter_tr_electricity, 1), "XXX", "XZX", "XXX",
//					'Z', "PSU", 'X', "ingotRefinedIron"));
		}
		initDone = true;
	}

	private static Block addBlock(Block block, String name) {
		block.setTranslationKey(PowerAdvantage.MODID + "." + name);
		block.setRegistryName(name);
//		GameRegistry.register(block);
		if ((block instanceof BlockFluidBase) == false) block.setCreativeTab(ItemGroups.tab_powerAdvantage);
		allBlocks.put(name, block);
		return block;
	}


	@SideOnly(Side.CLIENT)
	public static void registerItemRenders(FMLInitializationEvent event) {
		for (Map.Entry<String, Block> e : allBlocks.entrySet()) {
			String name = e.getKey();
			Block block = e.getValue();
			Minecraft.getMinecraft().getRenderItem().getItemModelMesher()
					.register(net.minecraft.item.Item.getItemFromBlock(block), 0,
							new ModelResourceLocation(PowerAdvantage.MODID + ":" + name, "inventory"));
		}
	}
}