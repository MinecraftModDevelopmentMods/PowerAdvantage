package mmd.poweradvantage.api.energy.implementation;

import mmd.poweradvantage.mod.capability.PowerCapabilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;

public class PowerContainerProvider implements INBTSerializable<NBTTagCompound>, ICapabilityProvider {

    private final PowerContainer container;

    public PowerContainerProvider(PowerContainer container) {

        this.container = container;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {

        return capability == PowerCapabilities.CAPABILITY_CONSUMER || capability == PowerCapabilities.CAPABILITY_PRODUCER || capability == PowerCapabilities.CAPABILITY_HOLDER;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {

        if (capability == PowerCapabilities.CAPABILITY_CONSUMER || capability == PowerCapabilities.CAPABILITY_PRODUCER || capability == PowerCapabilities.CAPABILITY_HOLDER)
            return (T) this.container;

        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {

        return this.container.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {

        this.container.deserializeNBT(nbt);
    }
}