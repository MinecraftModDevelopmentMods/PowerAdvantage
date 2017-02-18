package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.fluid.BlockFluidBase;
import com.mcmoddev.poweradvantage.fluid.BlockFluidPowerAdvantage;
import com.mcmoddev.poweradvantage.fluid.PowerAdvantageFluid;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ModFluids {

    public static Fluid OIL = new PowerAdvantageFluid("fluidoil");
    public static BlockFluidBase BLOCK_OIL;
    public static Fluid FUEL = new PowerAdvantageFluid("fluidfuel");
    public static BlockFluidBase BLOCK_FUEL;
    public static Fluid STEAM = new PowerAdvantageFluid("fluidsteam");
    public static BlockFluidBase BLOCK_STEAM;

    public static void register() {
        FluidRegistry.registerFluid(OIL);
        BLOCK_OIL = registerBlock(new BlockFluidPowerAdvantage(OIL, Material.WATER, "poweradvantage.oil"), "poweradvantage.oil");

        FluidRegistry.registerFluid(FUEL);
        BLOCK_FUEL = registerBlock(new BlockFluidPowerAdvantage(FUEL, Material.WATER, "poweradvantage.fuel"), "poweradvantage.fuel");

        FluidRegistry.registerFluid(STEAM);
        BLOCK_STEAM = registerBlock(new BlockFluidPowerAdvantage(STEAM, Material.WATER, "poweradvantage.steam"), "poweradvantage.steam");
    }

    public static <T extends Block> T registerBlock(T block, String name) {
        block.setRegistryName(name);
        GameRegistry.register(block);
        GameRegistry.register(new ItemBlock(block), block.getRegistryName());
        return block;
    }
}
