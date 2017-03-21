package com.mcmoddev.poweradvantage3.recipe.input;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class AdvancedInput extends MachineInput<AdvancedInput> {
    public ItemStack stack;
    public FluidStack fluid;

    public AdvancedInput(ItemStack stack, FluidStack fluid) {
        this.stack = stack;
        this.fluid = fluid;
    }

    @Override
    public boolean isValid() {
        return stack != null && fluid != null;
    }

    @Override
    public AdvancedInput copy() {
        return new AdvancedInput(stack.copy(), fluid.copy());
    }

    @Override
    public boolean doesEqual(AdvancedInput other) {
        return stack.isItemEqual(other.stack) && fluid.isFluidEqual(other.fluid);
    }

    @Override
    public boolean instanceOf(Object other) {
        return other instanceof AdvancedInput;
    }

    @Override
    public void load(NBTTagCompound nbt) {
        stack = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("stack"));
        fluid = FluidStack.loadFluidStackFromNBT(nbt.getCompoundTag("fluid"));
    }
}
