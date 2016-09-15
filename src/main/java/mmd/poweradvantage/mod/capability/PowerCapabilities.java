package mmd.poweradvantage.mod.capability;

import mmd.poweradvantage.api.energy.IPowerConsumer;
import mmd.poweradvantage.api.energy.IPowerHolder;
import mmd.poweradvantage.api.energy.IPowerProducer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import net.minecraftforge.common.capabilities.CapabilityInject;

public class PowerCapabilities {

    @CapabilityInject(IPowerConsumer.class)
    public static Capability<IPowerConsumer> CAPABILITY_CONSUMER = null;

    @CapabilityInject(IPowerProducer.class)
    public static Capability<IPowerProducer> CAPABILITY_PRODUCER = null;
    
    @CapabilityInject(IPowerHolder.class)
    public static Capability<IPowerHolder> CAPABILITY_HOLDER = null;
    
    public static class CapabilityEnergyConsumer<T extends IPowerConsumer> implements IStorage<IPowerConsumer> {
        
        @Override
        public NBTBase writeNBT (Capability<IPowerConsumer> capability, IPowerConsumer instance, EnumFacing side) {
            
            return null;
        }
        
        @Override
        public void readNBT (Capability<IPowerConsumer> capability, IPowerConsumer instance, EnumFacing side, NBTBase nbt) {
        
        }
    }
    
    public static class CapabilityEnergyProducer<T extends IPowerProducer> implements IStorage<IPowerProducer> {
        
        @Override
        public NBTBase writeNBT (Capability<IPowerProducer> capability, IPowerProducer instance, EnumFacing side) {
            
            return null;
        }
        
        @Override
        public void readNBT (Capability<IPowerProducer> capability, IPowerProducer instance, EnumFacing side, NBTBase nbt) {
        
        }
    }
    
    public static class CapabilityEnergyHolder<T extends IPowerHolder> implements IStorage<IPowerHolder> {
        
        @Override
        public NBTBase writeNBT (Capability<IPowerHolder> capability, IPowerHolder instance, EnumFacing side) {
            
            return null;
        }
        
        @Override
        public void readNBT (Capability<IPowerHolder> capability, IPowerHolder instance, EnumFacing side, NBTBase nbt) {
        
        }
    }
}