package com.mcmoddev.poweradvantage.fluid;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

public class PowerAdvantageFluid extends Fluid {
    public PowerAdvantageFluid(String fluidName) {
        super(fluidName,
                new ResourceLocation("poweradvantage:blocks/fluid/fluid_" + fluidName.replaceFirst("fluid", "") + "_still"),
                new ResourceLocation("poweradvantage:blocks/fluid/fluid_" + fluidName.replaceFirst("fluid", "") + "_flow")
        );
        FluidRegistry.addBucketForFluid(this);
    }
}