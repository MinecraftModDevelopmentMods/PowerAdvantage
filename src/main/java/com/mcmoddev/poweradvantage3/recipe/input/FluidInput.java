package com.mcmoddev.poweradvantage3.recipe.input;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class FluidInput extends MachineInput<FluidInput> {
    public FluidStack stack;

    public FluidInput(FluidStack stack) {
        this.stack = stack;
    }

    @Override
    public boolean isValid() {
        return stack != null && !stack.getFluid().isGaseous();
    }

    @Override
    public FluidInput copy() {
        return new FluidInput(stack.copy());
    }

    @Override
    public void load(NBTTagCompound nbt) {
        stack = FluidStack.loadFluidStackFromNBT(nbt);
    }

    @Override
    public boolean doesEqual(FluidInput other) {
        return other != null && other.stack.equals(stack);
    }

    @Override
    public boolean instanceOf(Object other) {
        return other instanceof FluidInput;
    }
}