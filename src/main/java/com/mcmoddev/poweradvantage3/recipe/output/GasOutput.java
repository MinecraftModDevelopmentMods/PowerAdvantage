package com.mcmoddev.poweradvantage3.recipe.output;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class GasOutput extends MachineOutput<GasOutput> {
    public FluidStack stack;

    public GasOutput(FluidStack stack) {
        this.stack = stack;
    }

    public GasOutput(NBTTagCompound tag) {
        this.load(tag);
    }

    @Override
    public GasOutput copy() {
        return new GasOutput(stack.copy());
    }

    @Override
    public void load(NBTTagCompound tag) {
        stack = FluidStack.loadFluidStackFromNBT(tag);
    }
}