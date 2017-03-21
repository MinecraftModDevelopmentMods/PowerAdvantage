package com.mcmoddev.poweradvantage3.recipe.output;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class FluidOutput extends MachineOutput<FluidOutput> {
    public FluidStack stack;

    public FluidOutput(FluidStack stack) {
        this.stack = stack;
    }

    public FluidOutput(NBTTagCompound tag) {
        this.load(tag);
    }

    @Override
    public FluidOutput copy() {
        return new FluidOutput(stack.copy());
    }

    @Override
    public void load(NBTTagCompound tag) {
        stack = FluidStack.loadFluidStackFromNBT(tag);
    }
}