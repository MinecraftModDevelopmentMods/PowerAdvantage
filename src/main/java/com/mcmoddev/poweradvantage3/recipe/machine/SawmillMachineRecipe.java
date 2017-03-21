package com.mcmoddev.poweradvantage3.recipe.machine;

import com.mcmoddev.poweradvantage3.recipe.input.ItemStackInput;
import com.mcmoddev.poweradvantage3.recipe.output.ChanceOutput;
import net.minecraft.item.ItemStack;

public class SawmillMachineRecipe extends ChanceMachineRecipe<SawmillMachineRecipe> {
    public SawmillMachineRecipe(ItemStackInput input, ChanceOutput output) {
        super(input, output);
    }

    public SawmillMachineRecipe(ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        this(new ItemStackInput(input), new ChanceOutput(primaryOutput, secondaryOutput, chance));
    }

    public SawmillMachineRecipe(ItemStack input, ItemStack primaryOutput) {
        this(new ItemStackInput(input), new ChanceOutput(primaryOutput));
    }

    @Override
    public SawmillMachineRecipe copy() {
        return new SawmillMachineRecipe(getInput().copy(), getOutput().copy());
    }
}