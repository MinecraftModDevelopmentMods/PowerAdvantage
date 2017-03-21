package com.mcmoddev.poweradvantage3.recipe.input;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class GasInput extends MachineInput<GasInput> {
    public FluidStack stack;

    public GasInput(FluidStack stack) {
        this.stack = stack;
    }

    @Override
    public boolean isValid() {
        return stack != null && stack.getFluid().isGaseous();
    }

    @Override
    public GasInput copy() {
        return new GasInput(stack.copy());
    }

    @Override
    public void load(NBTTagCompound nbt) {
        stack = FluidStack.loadFluidStackFromNBT(nbt);
    }

    @Override
    public boolean doesEqual(GasInput other) {
        return other != null && other.stack.equals(stack);
    }

    @Override
    public boolean instanceOf(Object other) {
        return other instanceof GasInput;
    }
}