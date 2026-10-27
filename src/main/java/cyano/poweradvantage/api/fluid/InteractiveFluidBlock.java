package cyano.poweradvantage.api.fluid;

import java.util.function.BiConsumer;

import net.minecraft.block.Block;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/** A native 1.13 fluid block which can affect immersed living entities. */
public class InteractiveFluidBlock extends FlowingFluidBlock {
    private final BiConsumer<World, LivingEntity> immersionEffect;
    private final boolean flammable;

    public InteractiveFluidBlock(FlowingFluid fluid, Block.Properties properties, boolean flammable,
            BiConsumer<World, LivingEntity> immersionEffect) {
        super(fluid, properties);
        this.flammable = flammable;
        this.immersionEffect = immersionEffect;
    }

    @Override
    public void entityInside(BlockState state, World world, BlockPos pos, Entity entity) {
        if (immersionEffect != null && entity instanceof LivingEntity) {
            immersionEffect.accept(world, (LivingEntity) entity);
        }
    }

    @Override
    public int getFlammability(BlockState state, IBlockReader world, BlockPos pos, Direction face) {
        return flammable ? 60 : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, IBlockReader world, BlockPos pos, Direction face) {
        return flammable ? 30 : 0;
    }
}
