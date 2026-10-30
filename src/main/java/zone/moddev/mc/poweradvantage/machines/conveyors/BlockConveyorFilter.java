package zone.moddev.mc.poweradvantage.machines.conveyors;

import cyano.poweradvantage.api.GUIBlock;
import net.minecraft.block.Block;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Horizontal conveyor filter block retaining its legacy metadata codec. */
public class BlockConveyorFilter extends GUIBlock {
    public static final DirectionProperty FACING = HorizontalBlock.FACING;
    private final Class<? extends TileEntityConveyor> tileEntityClass;

    public BlockConveyorFilter(Material material, float hardness,
            Class<? extends TileEntityConveyor> tileEntityClass) {
        super(Block.Properties.of(material).strength(hardness)
                .sound(SoundType.METAL).noOcclusion().dynamicShape());
        this.tileEntityClass = tileEntityClass;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public TileEntity newBlockEntity(IBlockReader world) {
        try {
            return tileEntityClass.newInstance();
        } catch (ReflectiveOperationException exception) {
            PowerAdvantage.LOGGER.error("Could not create conveyor filter {}", tileEntityClass.getName(), exception);
            return null;
        }
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
    @Override protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public boolean isOpaqueCube(BlockState state) { return false; }
    public boolean isFullCube(BlockState state) { return false; }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    public int getAnalogOutputSignal(BlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getBlockEntity(pos);
        if (!(tile instanceof TileEntityConveyorFilter)) return 0;
        TileEntityConveyorFilter filter = (TileEntityConveyorFilter) tile;
        if (filter.getItem(0).isEmpty()) return 0;
        return filter.matchesFilter(filter.getItem(0)) ? 15 : 7;
    }

    public BlockState getStateFromMeta(int meta) {
        Direction facing = Direction.from3DDataValue(meta);
        return defaultBlockState().setValue(FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
    }
    public int getMetaFromState(BlockState state) { return state.getValue(FACING).get3DDataValue(); }
}
