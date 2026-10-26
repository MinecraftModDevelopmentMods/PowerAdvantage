package cyano.poweradvantage.api.modsupport.rf;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.simple.BlockSimplePowerMachine;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockReader;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Historical RF-labelled converter block, backed by Forge Energy in 1.13. */
public class BlockRFConverter extends BlockSimplePowerMachine {
    private final Class<? extends TileEntityRFConverter> tileEntityClass;

    public BlockRFConverter(Material material, float hardness, ConduitType type,
            Class<? extends TileEntityRFConverter> tileEntityClass) {
        super(material, hardness, type);
        this.tileEntityClass = tileEntityClass;
    }

    @Override
    public TileEntityRFConverter createNewTileEntity(IBlockReader world) {
        try {
            return tileEntityClass.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create converter tile " + tileEntityClass.getName(), exception);
        }
    }

    @Override public boolean isPowerSink(ConduitType powerType) { return true; }
    @Override public boolean isPowerSource(ConduitType powerType) { return true; }
    @Override public boolean hasComparatorInputOverride(BlockState state) { return false; }
    @Override public int getComparatorInputOverride(BlockState state, World world, BlockPos pos) { return 0; }
}
