package cyano.poweradvantage.api.modsupport.techreborn;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.modsupport.rf.BlockRFConverter;
import net.minecraft.block.material.Material;

/** Historical Tech Reborn-labelled converter block, backed by Forge Energy. */
public class BlockTRConverter extends BlockRFConverter {
    @SuppressWarnings("unchecked")
    public BlockTRConverter(Material material, float hardness, ConduitType type,
            Class<? extends TileEntityTRConverter> tileEntityClass) {
        super(material, hardness, type,
                (Class<? extends cyano.poweradvantage.api.modsupport.rf.TileEntityRFConverter>) tileEntityClass);
    }
}
