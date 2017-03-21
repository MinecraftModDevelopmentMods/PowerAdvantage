package com.mcmoddev.poweradvantage3.recipe.machine;

import com.mcmoddev.poweradvantage3.recipe.input.AdvancedInput;
import com.mcmoddev.poweradvantage3.recipe.output.ItemStackOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public abstract class AdvancedMachineRecipe<RECIPE extends AdvancedMachineRecipe<RECIPE>> extends MachineRecipe<AdvancedInput, ItemStackOutput, RECIPE> {
    public AdvancedMachineRecipe(AdvancedInput input, ItemStackOutput output) {
        super(input, output);
    }

    public AdvancedMachineRecipe(ItemStack input, FluidStack gas, ItemStack output) {
        this(new AdvancedInput(input, gas), new ItemStackOutput(output));
    }
}