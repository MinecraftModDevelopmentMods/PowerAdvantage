package com.mcmoddev.poweradvantage3.recipe.machine;

import com.mcmoddev.poweradvantage3.recipe.input.ItemStackInput;
import com.mcmoddev.poweradvantage3.recipe.output.ItemStackOutput;
import net.minecraft.item.ItemStack;

public class CompressorRecipe extends BasicMachineRecipe<CompressorRecipe> {
    public CompressorRecipe(ItemStackInput input, ItemStackOutput output) {
        super(input, output);
    }

    public CompressorRecipe(ItemStack input, ItemStack output) {
        super(input, output);
    }

    @Override
    public CompressorRecipe copy() {
        return new CompressorRecipe(getInput().copy(), getOutput().copy());
    }
}