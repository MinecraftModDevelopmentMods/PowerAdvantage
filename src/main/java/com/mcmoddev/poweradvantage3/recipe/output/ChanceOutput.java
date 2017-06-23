package com.mcmoddev.poweradvantage3.recipe.output;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Random;

public class ChanceOutput extends MachineOutput<ChanceOutput> {
    private static Random rand = new Random();

    public ItemStack primaryOutput;

    public ItemStack secondaryOutput;

    public double secondaryChance;

    public ChanceOutput(ItemStack primaryOutput, ItemStack secondaryOutput, double secondaryChance) {
        this.primaryOutput = primaryOutput;
        this.secondaryOutput = secondaryOutput;
        this.secondaryChance = secondaryChance;
    }

    public ChanceOutput(ItemStack primary) {
        this(primary, null, 0);
    }

    public ChanceOutput(ItemStack primary, ItemStack secondaryOutput) {
        this(primary, secondaryOutput, 1.0);
    }


    @Override
    public ChanceOutput copy() {
        return new ChanceOutput(primaryOutput.copy(), secondaryOutput.copy(), secondaryChance);
    }

    @Override
    public void load(NBTTagCompound tag) {
        primaryOutput = new ItemStack(tag.getCompoundTag("primaryOutput"));
        secondaryOutput = new ItemStack(tag.getCompoundTag("secondaryOutput"));
        secondaryChance = tag.getDouble("secondaryChance");
    }

    public boolean checkSecondary() {
        return rand.nextDouble() <= secondaryChance;
    }

    public boolean hasPrimary() {
        return primaryOutput != null;
    }

    public boolean hasSecondary() {
        return secondaryOutput != null;
    }
}