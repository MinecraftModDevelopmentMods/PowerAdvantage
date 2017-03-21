package com.mcmoddev.poweradvantage3.fluid;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

public class PowerAdvantageFluid extends Fluid {
    public PowerAdvantageFluid(String fluidName) {
        super(fluidName,
                new ResourceLocation("poweradvantage3:blocks/fluid/fluid_" + fluidName.replaceFirst("fluid", "") + "_still"),
                new ResourceLocation("poweradvantage3:blocks/fluid/fluid_" + fluidName.replaceFirst("fluid", "") + "_flow")
        );
        FluidRegistry.addBucketForFluid(this);
    }
}