package com.mcmoddev.poweradvantage3.recipe.output;

import net.minecraft.nbt.NBTTagCompound;

public abstract class MachineOutput<THIS extends MachineOutput> {
    public abstract THIS copy();

    public abstract void load(NBTTagCompound tag);

}