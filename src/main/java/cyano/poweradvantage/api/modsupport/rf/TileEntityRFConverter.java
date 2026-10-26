package cyano.poweradvantage.api.modsupport.rf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.modsupport.TileEntityConverter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Forge Energy bridge retained behind the historical RF-labelled block IDs. */
public class TileEntityRFConverter extends TileEntityConverter implements IEnergyStorage {
    private final int forgeBufferSize;
    private final int halfBuffer;
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> this);
    private int forgeBuffer;

    protected TileEntityRFConverter(TileEntityType<?> tileType, ConduitType type,
            float advantageBuffer, int forgeBufferSize, String translationKey) {
        super(tileType, type, advantageBuffer, translationKey);
        this.forgeBufferSize = forgeBufferSize;
        this.halfBuffer = forgeBufferSize / 2;
    }

    @Override
    public void tickUpdate(boolean server) {
        if (!server) return;
        int index = (int) (getWorld().getGameTime() % Direction.values().length);
        Direction side = Direction.values()[index];
        TileEntity neighbor = getWorld().getTileEntity(getPos().offset(side));
        if (neighbor == null) return;
        neighbor.getCapability(CapabilityEnergy.ENERGY, side.getOpposite()).ifPresent(storage -> {
            int delta = forgeBuffer - halfBuffer;
            if (delta < 0 && storage.canExtract()) {
                int extracted = storage.extractEnergy(-delta, true);
                int accepted = receiveEnergy(extracted, true);
                if (accepted > 0) receiveEnergy(storage.extractEnergy(accepted, false), false);
            } else if (delta > 0 && storage.canReceive()) {
                int accepted = storage.receiveEnergy(delta, true);
                if (accepted > 0) storage.receiveEnergy(extractEnergy(accepted, false), false);
            }
        });
    }

    @Override public int receiveEnergy(int maxReceive, boolean simulate) {
        int accepted = Math.min(Math.max(0, maxReceive), forgeBufferSize - forgeBuffer);
        if (!simulate && accepted > 0) { forgeBuffer += accepted; markDirty(); }
        return accepted;
    }
    @Override public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = Math.min(Math.max(0, maxExtract), forgeBuffer);
        if (!simulate && extracted > 0) { forgeBuffer -= extracted; markDirty(); }
        return extracted;
    }
    @Override public int getEnergyStored() { return forgeBuffer; }
    @Override public int getMaxEnergyStored() { return forgeBufferSize; }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return true; }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return !isRemoved() && capability == CapabilityEnergy.ENERGY ? energyCapability.cast() : super.getCapability(capability, side);
    }

    @Override protected void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    @Override public void validate() {
        super.validate();
        if (!energyCapability.isPresent()) energyCapability = LazyOptional.of(() -> this);
    }

    @Override public double getOtherEnergy() { return forgeBuffer; }
    @Override public double getOtherEnergyCapacity() { return forgeBufferSize; }
    @Override public void setOtherEnergy(double value) { forgeBuffer = Math.max(0, Math.min(forgeBufferSize, (int) value)); }
    @Override public boolean canConvertEnergy(ConduitType type) { return PowerAdvantage.forgeEnergyConversionTable.containsKey(type); }

    @Override
    public float addEnergyToOther(float amount, ConduitType type) {
        if (!canConvertEnergy(type)) return 0;
        int accepted = receiveEnergy((int) convertEnergyToOther(amount, type), false);
        return convertOtherToEnergy(accepted, type);
    }

    @Override
    public float subtractEnergyFromOther(float amount, ConduitType type) {
        if (!canConvertEnergy(type)) return 0;
        int extracted = extractEnergy((int) convertEnergyToOther(amount, type), false);
        return convertOtherToEnergy(extracted, type);
    }

    @Override public float convertOtherToEnergy(double amount, ConduitType type) {
        return (float) (amount / PowerAdvantage.forgeEnergyConversionTable.get(type));
    }
    @Override public double convertEnergyToOther(float amount, ConduitType type) {
        return amount * PowerAdvantage.forgeEnergyConversionTable.get(type);
    }
}
