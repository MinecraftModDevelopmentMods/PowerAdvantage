package com.mcmoddev.poweradvantage3.recipe.input;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemStackInput extends MachineInput<ItemStackInput> {
    public ItemStack stack;

    public ItemStackInput(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public boolean isValid() {
        return stack != null;
    }

    @Override
    public ItemStackInput copy() {
        return new ItemStackInput(stack.copy());
    }

    @Override
    public boolean doesEqual(ItemStackInput other) {
        return other != null && other.stack.isItemEqual(stack);
    }

    @Override
    public boolean instanceOf(Object other) {
        return other instanceof ItemStackInput;
    }

    @Override
    public void load(NBTTagCompound nbt) {
        stack = new ItemStack(nbt);
    }
}