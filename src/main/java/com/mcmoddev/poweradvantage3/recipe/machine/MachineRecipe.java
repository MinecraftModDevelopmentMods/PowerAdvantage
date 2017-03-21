package com.mcmoddev.poweradvantage3.recipe.machine;

import com.mcmoddev.poweradvantage3.recipe.input.MachineInput;
import com.mcmoddev.poweradvantage3.recipe.output.MachineOutput;

public abstract class MachineRecipe<INPUT extends MachineInput, OUTPUT extends MachineOutput, THIS extends MachineRecipe<INPUT, OUTPUT, THIS>> {
    public INPUT input;
    public OUTPUT output;

    public MachineRecipe(INPUT input, OUTPUT output) {
        this.input = input;
        this.output = output;
    }

    public INPUT getInput() {
        return input;
    }

    public OUTPUT getOutput() {
        return output;
    }

    public abstract THIS copy();
}