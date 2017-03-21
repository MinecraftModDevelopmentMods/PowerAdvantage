package com.mcmoddev.poweradvantage3.recipe.input;

import net.minecraft.nbt.NBTTagCompound;

public abstract class MachineInput<THIS extends MachineInput<THIS>> {
    public abstract boolean isValid();

    public abstract THIS copy();

    public abstract boolean doesEqual(THIS other);

    public abstract boolean instanceOf(Object other);

    public abstract void load(NBTTagCompound nbt);

    @Override
    public boolean equals(Object obj) {
        if (instanceOf(obj))
            return doesEqual((THIS) obj);
        return false;
    }
}