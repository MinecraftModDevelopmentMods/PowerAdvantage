package com.mcmoddev.poweradvantage3.recipe.output;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemStackOutput extends MachineOutput<ItemStackOutput> {
    public ItemStack stack;

    public ItemStackOutput(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStackOutput(NBTTagCompound tag) {
        this.load(tag);
    }

    @Override
    public ItemStackOutput copy() {
        return new ItemStackOutput(stack.copy());
    }

    @Override
    public void load(NBTTagCompound tag) {
        stack = ItemStack.loadItemStackFromNBT(tag);
    }
}