package cyano.poweradvantage.api.fluid;

import java.util.function.BiConsumer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/** A native 1.13 fluid block which can affect immersed living entities. */
public class InteractiveFluidBlock extends BlockFlowingFluid {
    private final BiConsumer<World, EntityLivingBase> immersionEffect;
    private final boolean flammable;

    public InteractiveFluidBlock(FlowingFluid fluid, Block.Properties properties, boolean flammable,
            BiConsumer<World, EntityLivingBase> immersionEffect) {
        super(fluid, properties);
        this.flammable = flammable;
        this.immersionEffect = immersionEffect;
    }

    @Override
    public void onEntityCollision(IBlockState state, World world, BlockPos pos, Entity entity) {
        if (immersionEffect != null && entity instanceof EntityLivingBase) {
            immersionEffect.accept(world, (EntityLivingBase) entity);
        }
    }

    @Override
    public int getFlammability(IBlockState state, IBlockReader world, BlockPos pos, EnumFacing face) {
        return flammable ? 60 : 0;
    }

    @Override
    public int getFireSpreadSpeed(IBlockState state, IBlockReader world, BlockPos pos, EnumFacing face) {
        return flammable ? 30 : 0;
    }
}
