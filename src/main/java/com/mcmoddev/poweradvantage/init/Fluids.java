package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.events.MMDLibRegisterFluids;
import com.mcmoddev.lib.fluids.CustomFluid;
import com.mcmoddev.poweradvantage.PowerAdvantage;

import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber(modid = PowerAdvantage.MODID)
public class Fluids extends com.mcmoddev.lib.init.Fluids {

    private Fluids() {
            throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
    }

    /**
     *
     */
    @SubscribeEvent
    public static void registerEvent(final MMDLibRegisterFluids event) {
		final Fluid fluid = new CustomFluid("steam",
				new ResourceLocation(PowerAdvantage.MODID, "blocks/steam_still"),
				new ResourceLocation(PowerAdvantage.MODID, "blocks/steam_flow"));

		fluid.setDensity(-1);
		fluid.setViscosity(1);
		fluid.setTemperature(373);
		fluid.setLuminosity(0);
		fluid.setColor(0x89CBDBFC);
		fluid.setUnlocalizedName(PowerAdvantage.MODID + ".steam");
		FluidRegistry.registerFluid(fluid);
		FluidRegistry.addBucketForFluid(fluid);
		BlockFluidClassic fluidBlock = new BlockFluidClassic(fluid, Material.WATER);
		fluidBlock.setRegistryName("steam"); // fullName
		fluidBlock.setTranslationKey(PowerAdvantage.MODID + ".steam");
		fluidBlock.setCreativeTab(ItemGroups.getTab(PowerAdvantage.MODID, "tab"));
		final ItemBlock itemBlock = new ItemBlock(fluidBlock);
		itemBlock.setRegistryName("steam"); // fullName
		Materials.getMaterialByName("pa-machines").addNewBlock("steam", fluidBlock);
		Materials.getMaterialByName("pa-machines").setFluid(fluid);
		Materials.getMaterialByName("pa-machines").setFluidBlock(fluidBlock);
		Materials.getMaterialByName("pa-machines").addNewItem("ItemBlock_steam", itemBlock);
    }    	
}
