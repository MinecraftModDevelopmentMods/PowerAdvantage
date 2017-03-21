package com.mcmoddev.poweradvantage3.recipe.machine;

import com.mcmoddev.poweradvantage3.recipe.input.ItemStackInput;
import com.mcmoddev.poweradvantage3.recipe.output.ItemStackOutput;
import net.minecraft.item.ItemStack;

public abstract class BasicMachineRecipe<RECIPE extends BasicMachineRecipe<RECIPE>> extends MachineRecipe<ItemStackInput, ItemStackOutput, RECIPE> {
    public BasicMachineRecipe(ItemStackInput input, ItemStackOutput output) {
        super(input, output);
    }

    public BasicMachineRecipe(ItemStack input, ItemStack output) {
        this(new ItemStackInput(input), new ItemStackOutput(output));
    }
}