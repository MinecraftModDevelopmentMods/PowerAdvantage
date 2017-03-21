package com.mcmoddev.poweradvantage3.init;

import com.mcmoddev.poweradvantage3.fluid.BlockFluidBase;
import com.mcmoddev.poweradvantage3.fluid.BlockFluidPowerAdvantage;
import com.mcmoddev.poweradvantage3.fluid.PowerAdvantageFluid;
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
    public static Fluid STEAM = new PowerAdvantageFluid("fluidsteam").setGaseous(true);
    public static BlockFluidBase BLOCK_STEAM;
    public static Fluid OXYGEN = new PowerAdvantageFluid("gasoxygen").setGaseous(true);
    public static BlockFluidBase BLOCK_OXYGEN;

    public static void register() {
        FluidRegistry.registerFluid(OIL);
        BLOCK_OIL = registerBlock(new BlockFluidPowerAdvantage(OIL, Material.WATER, "poweradvantage3.oil"), "poweradvantage3.oil");

        FluidRegistry.registerFluid(FUEL);
        BLOCK_FUEL = registerBlock(new BlockFluidPowerAdvantage(FUEL, Material.WATER, "poweradvantage3.fuel"), "poweradvantage3.fuel");

        FluidRegistry.registerFluid(STEAM);
        BLOCK_STEAM = registerBlock(new BlockFluidPowerAdvantage(STEAM, Material.WATER, "poweradvantage3.steam"), "poweradvantage3.steam");
    }

    public static <T extends Block> T registerBlock(T block, String name) {
        block.setRegistryName(name);
        GameRegistry.register(block);
        GameRegistry.register(new ItemBlock(block), block.getRegistryName());
        return block;
    }
}
