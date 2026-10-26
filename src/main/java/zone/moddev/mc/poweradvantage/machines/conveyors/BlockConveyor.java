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
        super(Block.Properties.create(material).hardnessAndResistance(hardness)
                .sound(SoundType.METAL).variableOpacity());
        setDefaultState(getStateContainer().getBaseState().with(FACING, Direction.NORTH));
    }

    @Override public TileEntity createNewTileEntity(IBlockReader world) { return new TileEntityConveyor(); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.toRotation(state.get(FACING))); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState().with(FACING, context.getFace().getOpposite());
    }
    @Override protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public boolean isOpaqueCube(BlockState state) { return false; }
    public boolean isFullCube(BlockState state) { return false; }
    @Override public boolean hasComparatorInputOverride(BlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(BlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityConveyor && !((TileEntityConveyor) tile).getStackInSlot(0).isEmpty() ? 7 : 0;
    }

    public BlockState getStateFromMeta(int meta) {
        Direction facing = Direction.byIndex(meta);
        return getDefaultState().with(FACING, facing == null ? Direction.NORTH : facing);
    }
    public int getMetaFromState(BlockState state) { return state.get(FACING).getIndex(); }
}
