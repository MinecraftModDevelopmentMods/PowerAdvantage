package mmd.poweradvantage.api.energy.implementation;

import mmd.poweradvantage.api.energy.IPowerConsumer;
import mmd.poweradvantage.api.energy.IPowerHolder;
import mmd.poweradvantage.api.energy.IPowerProducer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

public class PowerContainer implements IPowerConsumer, IPowerProducer, IPowerHolder, INBTSerializable<NBTTagCompound> {

    private long stored;

    private long capacity;

    private long inputRate;

    private long outputRate;

    public PowerContainer() {

        this(5000, 50, 50);
    }

    private PowerContainer(long capacity, long input, long output) {
        this.capacity = capacity;
        this.inputRate = input;
        this.outputRate = output;
    }

    public static PowerContainer create(long capacity, long input, long output) {
        return new PowerContainer(capacity, input, output);
    }

    public static PowerContainer createFromNBT(NBTTagCompound tagCompound) {
        return new PowerContainer(tagCompound);
    }

    private PowerContainer(NBTTagCompound dataTag) {

        this.deserializeNBT(dataTag);
    }

    @Override
    public long getStoredPower() {

        return this.stored;
    }

    @Override
    public long givePower(long Tesla, boolean simulated) {

        final long acceptedTesla = Math.min(this.getCapacity() - this.stored, Math.min(this.getInputRate(), Tesla));

        if (!simulated)
            this.stored += acceptedTesla;

        return acceptedTesla;
    }

    @Override
    public long takePower(long Tesla, boolean simulated) {

        final long removedPower = Math.min(this.stored, Math.min(this.getOutputRate(), Tesla));

        if (!simulated)
            this.stored -= removedPower;

        return removedPower;
    }

    @Override
    public long getCapacity() {

        return this.capacity;
    }

    @Override
    public NBTTagCompound serializeNBT() {

        final NBTTagCompound dataTag = new NBTTagCompound();
        dataTag.setLong("EnergyPower", this.stored);
        dataTag.setLong("EnergyCapacity", this.capacity);
        dataTag.setLong("EnergyInput", this.inputRate);
        dataTag.setLong("EnergyOutput", this.outputRate);

        return dataTag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {

        this.stored = nbt.getLong("EnergyPower");

        if (nbt.hasKey("EnergyCapacity"))
            this.capacity = nbt.getLong("EnergyCapacity");

        if (nbt.hasKey("EnergyInput"))
            this.inputRate = nbt.getLong("EnergyInput");

        if (nbt.hasKey("EnergyOutput"))
            this.outputRate = nbt.getLong("EnergyOutput");

        if (this.stored > this.getCapacity())
            this.stored = this.getCapacity();
    }

    public PowerContainer setCapacity(long capacity) {

        this.capacity = capacity;

        if (this.stored > capacity)
            this.stored = capacity;

        return this;
    }

    public long getInputRate() {

        return this.inputRate;
    }

    public PowerContainer setInputRate(long rate) {

        this.inputRate = rate;
        return this;
    }

    public long getOutputRate() {

        return this.outputRate;
    }

    public PowerContainer setOutputRate(long rate) {
        this.outputRate = rate;
        return this;
    }

    public PowerContainer setTransferRate(long rate) {

        this.setInputRate(rate);
        this.setOutputRate(rate);
        return this;
    }
}