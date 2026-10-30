package zone.moddev.mc.poweradvantage.machines.conveyors;

import cyano.poweradvantage.api.GUIBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/** Six-facing conveyor block; legacy metadata remains the facing index. */
public class BlockConveyor extends GUIBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BlockConveyor(Material material, float hardness) {
        super(Block.Properties.of(material).strength(hardness)
                .sound(SoundType.METAL).noOcclusion().dynamicShape());
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override public TileEntity newBlockEntity(IBlockReader world) { return new TileEntityConveyor(); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public boolean isOpaqueCube(BlockState state) { return false; }
    public boolean isFullCube(BlockState state) { return false; }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    public int getAnalogOutputSignal(BlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getBlockEntity(pos);
        return tile instanceof TileEntityConveyor && !((TileEntityConveyor) tile).getItem(0).isEmpty() ? 7 : 0;
    }

    public BlockState getStateFromMeta(int meta) {
        Direction facing = Direction.from3DDataValue(meta);
        return defaultBlockState().setValue(FACING, facing == null ? Direction.NORTH : facing);
    }
    public int getMetaFromState(BlockState state) { return state.getValue(FACING).get3DDataValue(); }
}
