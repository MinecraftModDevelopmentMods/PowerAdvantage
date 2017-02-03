package com.mcmoddev.poweradvantage.item.energy;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import com.mcmoddev.poweradvantage.misc.EnergyStorage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemBattery extends Item {
    public ItemBattery() {
        setUnlocalizedName("poweradvantage.battery");
        setRegistryName("battery");
        setCreativeTab(PowerAdvantageTab.TAB);
        setMaxStackSize(1);
        GameRegistry.register(this);
    }

    public static IEnergyStorage getStorage(ItemStack stack) {
        return stack.getCapability(CapabilityEnergy.ENERGY, null);
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getStorage(stack).getEnergyStored() > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        IEnergyStorage storage = getStorage(stack);
        return (double) storage.getEnergyStored() / (double) storage.getMaxEnergyStored();
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable NBTTagCompound nbt) {
        return new Energy();
    }

    public static class Energy implements ICapabilitySerializable<NBTTagCompound> {
        private EnergyStorage storage;

        public Energy() {
            this.storage = new EnergyStorage(6000, 400);
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == CapabilityEnergy.ENERGY;
        }

        @Nullable
        @Override
        public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
            return hasCapability(capability, facing) ? (T) storage : null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return storage.serializeNBT();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            storage.deserializeNBT(nbt);
        }
    }
}