package com.mcmoddev.poweradvantage3.tile;

import com.mcmoddev.lib.tile.TileEntityBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class TileTank extends TileEntityBase implements ITickable {

    public FluidTank tank;

    public TileTank() {
        this.tank = new FluidTank(Fluid.BUCKET_VOLUME * 8);
    }

    @Override
    public void update() {
        if (tank.getFluid() != null && tank.getFluidAmount() > 0) {
            TileEntity te = world.getTileEntity(pos.down());
            if (te != null && te instanceof TileTank) {
                IFluidHandler fluidHandler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.UP);
                tank.drain(fluidHandler.fill(tank.drain(Fluid.BUCKET_VOLUME / 2, false), true), true);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound) {
        tank.readFromNBT(nbtTagCompound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTagCompound) {
        tank.writeToNBT(nbtTagCompound);
        return nbtTagCompound;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        return hasCapability(capability, facing) ? (T) tank : null;
    }
}
