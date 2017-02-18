package com.mcmoddev.poweradvantage.fluid;

import com.mcmoddev.poweradvantage.PowerAdvantage;
import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.Fluid;

public class BlockFluidPowerAdvantage extends BlockFluidBase {
    public BlockFluidPowerAdvantage(Fluid fluid, Material material, String name) {
        super(fluid, material);
        setUnlocalizedName(name);
        PowerAdvantage.PROXY.registerFluidRender(this, name);
    }
}
