package cyano.poweradvantage.api.modsupport.techreborn;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFConverter;
import net.minecraft.tileentity.TileEntityType;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/**
 * Compatibility base for the historical Tech Reborn-labelled converter.
 * Forge Energy is the common energy API from 1.13 onward.
 */
public abstract class TileEntityTRConverter extends TileEntityRFConverter {
    protected TileEntityTRConverter(TileEntityType<?> type, ConduitType conduitType, String translationKey) {
        super(type, conduitType, 1024F, 2048, translationKey);
    }

    @Override public boolean canConvertEnergy(ConduitType type) {
        return PowerAdvantage.techRebornForgeEnergyConversionTable.containsKey(type);
    }

    @Override public float convertOtherToEnergy(double amount, ConduitType type) {
        return (float) (amount / PowerAdvantage.techRebornForgeEnergyConversionTable.get(type));
    }

    @Override public double convertEnergyToOther(float amount, ConduitType type) {
        return amount * PowerAdvantage.techRebornForgeEnergyConversionTable.get(type);
    }
}
