package com.mcmoddev.poweradvantage3.recipe.machine;

import com.mcmoddev.poweradvantage3.recipe.input.ItemStackInput;
import com.mcmoddev.poweradvantage3.recipe.output.ChanceOutput;

public abstract class ChanceMachineRecipe<RECIPE extends ChanceMachineRecipe<RECIPE>> extends MachineRecipe<ItemStackInput, ChanceOutput, RECIPE> {
    public ChanceMachineRecipe(ItemStackInput input, ChanceOutput output) {
        super(input, output);
    }
}