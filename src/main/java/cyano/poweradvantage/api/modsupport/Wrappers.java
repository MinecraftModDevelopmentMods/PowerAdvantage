package cyano.poweradvantage.api.modsupport;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.IPowerMachine;
import cyano.poweradvantage.api.PowerConnectorContext;
import cyano.poweradvantage.api.PowerRequest;
import net.minecraftforge.energy.IEnergyStorage;

/** Adapters between Forge Energy and the Advantage request network. */
public final class Wrappers {
    private Wrappers() {}

    public static PowerRequest wrapForgeEnergyRequest(final IEnergyStorage storage,
            final ConduitType powerType, final float conversionFactor) {
        if (storage == null || conversionFactor <= 0 || !storage.canReceive()) return PowerRequest.REQUEST_NOTHING;
        final float inverted = 1.0F / conversionFactor;
        final float requested = (storage.getMaxEnergyStored() - storage.getEnergyStored()) * inverted;
        if (requested <= 0) return PowerRequest.REQUEST_NOTHING;
        IPowerMachine wrapper = new IPowerMachine() {
            @Override public float getEnergyCapacity(ConduitType type) {
                return ConduitType.areSameType(powerType, type) ? storage.getMaxEnergyStored() * inverted : 0;
            }
            @Override public float getEnergy(ConduitType type) {
                return ConduitType.areSameType(powerType, type) ? storage.getEnergyStored() * inverted : 0;
            }
            @Override public void setEnergy(float energy, ConduitType type) {}
            @Override public float addEnergy(float energy, ConduitType type) {
                return ConduitType.areSameType(powerType, type)
                        ? storage.receiveEnergy((int) (energy * conversionFactor), false) * inverted : 0;
            }
            @Override public float subtractEnergy(float energy, ConduitType type) { return 0; }
            @Override public PowerRequest getPowerRequest(ConduitType type) { return PowerRequest.REQUEST_NOTHING; }
            @Override public boolean canAcceptConnection(PowerConnectorContext connection) { return true; }
            @Override public ConduitType[] getTypes() { return new ConduitType[] { powerType }; }
            @Override public boolean isPowerSink(ConduitType type) { return ConduitType.areSameType(powerType, type); }
            @Override public boolean isPowerSource(ConduitType type) { return false; }
        };
        return new PowerRequest(PowerRequest.LOW_PRIORITY, requested, wrapper);
    }

    /** @deprecated Forge Energy replaces the standalone RF API. */
    @Deprecated
    public static PowerRequest wrapRFPowerRequest(IEnergyStorage storage, ConduitType type, float conversionFactor) {
        return wrapForgeEnergyRequest(storage, type, conversionFactor);
    }

    /** @deprecated Forge Energy replaces the Tech Reborn interface used by the legacy converter. */
    @Deprecated
    public static PowerRequest wrapTRPowerRequest(IEnergyStorage storage, ConduitType type, float conversionFactor) {
        return wrapForgeEnergyRequest(storage, type, conversionFactor);
    }
}
