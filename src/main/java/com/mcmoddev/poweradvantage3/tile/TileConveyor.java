package com.mcmoddev.poweradvantage3.tile;

import com.mcmoddev.lib.tile.TileEntityBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public class TileConveyor extends TileEntityBase {

    private EnumFacing facing = EnumFacing.NORTH;

    public boolean connectedLeft() {
        return false;
    }

    public boolean connectedRight() {
        return true;
    }

    public EnumFacing getFacing() {
        return facing;
    }

    public void setFacing(EnumFacing facing) {
        if (facing == EnumFacing.DOWN || facing == EnumFacing.UP)
            return;
        this.facing = facing;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound) {

    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTagCompound) {
        return null;
    }
}