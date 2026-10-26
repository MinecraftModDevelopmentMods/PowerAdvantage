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
    public static final DirectionProperty FACING = HorizontalBlock.HORIZONTAL_FACING;
    private final Class<? extends TileEntityConveyor> tileEntityClass;

    public BlockConveyorFilter(Material material, float hardness,
            Class<? extends TileEntityConveyor> tileEntityClass) {
        super(Block.Properties.create(material).hardnessAndResistance(hardness)
                .sound(SoundType.METAL).notSolid().variableOpacity());
        this.tileEntityClass = tileEntityClass;
        setDefaultState(getStateContainer().getBaseState().with(FACING, Direction.NORTH));
    }

    @Override
    public TileEntity createNewTileEntity(IBlockReader world) {
        try {
            return tileEntityClass.newInstance();
        } catch (ReflectiveOperationException exception) {
            PowerAdvantage.LOGGER.error("Could not create conveyor filter {}", tileEntityClass.getName(), exception);
            return null;
        }
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.toRotation(state.get(FACING))); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState().with(FACING, context.getPlacementHorizontalFacing());
    }
    @Override protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public boolean isOpaqueCube(BlockState state) { return false; }
    public boolean isFullCube(BlockState state) { return false; }
    @Override public boolean hasComparatorInputOverride(BlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(BlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityConveyorFilter)) return 0;
        TileEntityConveyorFilter filter = (TileEntityConveyorFilter) tile;
        if (filter.getStackInSlot(0).isEmpty()) return 0;
        return filter.matchesFilter(filter.getStackInSlot(0)) ? 15 : 7;
    }

    public BlockState getStateFromMeta(int meta) {
        Direction facing = Direction.byIndex(meta);
        return getDefaultState().with(FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
    }
    public int getMetaFromState(BlockState state) { return state.get(FACING).getIndex(); }
}
