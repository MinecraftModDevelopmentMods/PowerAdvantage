package zone.moddev.mc.poweradvantage.machines.conveyors;

import cyano.poweradvantage.api.GUIBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
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
        setDefaultState(getStateContainer().getBaseState().with(FACING, EnumFacing.NORTH));
    }

    @Override public TileEntity createNewTileEntity(IBlockReader world) { return new TileEntityConveyor(); }
    @Override public IBlockState rotate(IBlockState state, Rotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public IBlockState mirror(IBlockState state, Mirror mirror) { return state.rotate(mirror.toRotation(state.get(FACING))); }
    @Override public IBlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState().with(FACING, context.getFace().getOpposite());
    }
    @Override protected void fillStateContainer(StateContainer.Builder<Block, IBlockState> builder) { builder.add(FACING); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityConveyor && !((TileEntityConveyor) tile).getStackInSlot(0).isEmpty() ? 7 : 0;
    }

    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        return getDefaultState().with(FACING, facing == null ? EnumFacing.NORTH : facing);
    }
    public int getMetaFromState(IBlockState state) { return state.get(FACING).getIndex(); }
}
