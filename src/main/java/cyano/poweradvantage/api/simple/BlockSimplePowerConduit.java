package cyano.poweradvantage.api.simple;

import cyano.poweradvantage.api.ConduitBlock;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PowerConnectorContext;
import zone.moddev.mc.poweradvantage.util.PowerHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;

public abstract class BlockSimplePowerConduit extends ConduitBlock {
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private final ConduitType[] types;
    private final double pipeRadius;

    public BlockSimplePowerConduit(Material material, float hardness, float pipeRadius,
            ConduitType... energyTypes) {
        super(material, hardness);
        this.types = energyTypes;
        this.pipeRadius = pipeRadius;
        setDefaultState(getStateContainer().getBaseState()
                .with(WEST, false).with(DOWN, false).with(SOUTH, false)
                .with(EAST, false).with(UP, false).with(NORTH, false));
    }

    @Override public ConduitType[] getTypes() { return types; }

    @Override
    public boolean canAcceptConnection(PowerConnectorContext connection) {
        for (ConduitType type : types) {
            if (ConduitType.areSameType(type, connection.powerType)) return true;
        }
        return false;
    }

    @Override public boolean isPowerSink(ConduitType conduitType) { return false; }
    @Override public boolean isPowerSource(ConduitType conduitType) { return false; }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, IBlockState> builder) {
        builder.add(WEST, DOWN, SOUTH, EAST, UP, NORTH);
    }

    @Override
    public IBlockState getStateForPlacement(BlockItemUseContext context) {
        return connectedState(getDefaultState(), context.getWorld(), context.getPos());
    }

    @Override
    public IBlockState updatePostPlacement(IBlockState state, EnumFacing facing, IBlockState facingState,
            IWorld world, BlockPos currentPos, BlockPos facingPos) {
        return state.with(property(facing), canConnectTo(world, currentPos, state, facing, facingPos));
    }

    private IBlockState connectedState(IBlockState state, IBlockReader world, BlockPos pos) {
        return state.with(WEST, canConnectTo(world, pos, state, EnumFacing.WEST, pos.west()))
                .with(DOWN, canConnectTo(world, pos, state, EnumFacing.DOWN, pos.down()))
                .with(SOUTH, canConnectTo(world, pos, state, EnumFacing.SOUTH, pos.south()))
                .with(EAST, canConnectTo(world, pos, state, EnumFacing.EAST, pos.east()))
                .with(UP, canConnectTo(world, pos, state, EnumFacing.UP, pos.up()))
                .with(NORTH, canConnectTo(world, pos, state, EnumFacing.NORTH, pos.north()));
    }

    private static BooleanProperty property(EnumFacing facing) {
        switch (facing) {
            case DOWN: return DOWN;
            case UP: return UP;
            case NORTH: return NORTH;
            case SOUTH: return SOUTH;
            case WEST: return WEST;
            case EAST: return EAST;
            default: throw new IllegalArgumentException(String.valueOf(facing));
        }
    }

    protected boolean canConnectTo(IBlockReader world, BlockPos thisBlock, IBlockState state,
            EnumFacing face, BlockPos otherBlock) {
        for (ConduitType type : types) {
            if (PowerHelper.areConnectable(new PowerConnectorContext(type, world, state, thisBlock, face))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VoxelShape getShape(IBlockState state, IBlockReader world, BlockPos pos) {
        double min = 8.0D - pipeRadius * 16.0D;
        double max = 8.0D + pipeRadius * 16.0D;
        VoxelShape shape = Block.makeCuboidShape(min, min, min, max, max, max);
        boolean connected = false;
        for (EnumFacing facing : EnumFacing.values()) {
            if (!state.get(property(facing))) continue;
            connected = true;
            switch (facing) {
                case DOWN: shape = VoxelShapes.or(shape, Block.makeCuboidShape(min, 0, min, max, min, max)); break;
                case UP: shape = VoxelShapes.or(shape, Block.makeCuboidShape(min, max, min, max, 16, max)); break;
                case NORTH: shape = VoxelShapes.or(shape, Block.makeCuboidShape(min, min, 0, max, max, min)); break;
                case SOUTH: shape = VoxelShapes.or(shape, Block.makeCuboidShape(min, min, max, max, max, 16)); break;
                case WEST: shape = VoxelShapes.or(shape, Block.makeCuboidShape(0, min, min, min, max, max)); break;
                case EAST: shape = VoxelShapes.or(shape, Block.makeCuboidShape(max, min, min, 16, max, max)); break;
            }
        }
        if (!connected) {
            shape = VoxelShapes.or(shape, Block.makeCuboidShape(0, min, min, 16, max, max));
            shape = VoxelShapes.or(shape, Block.makeCuboidShape(min, min, 0, max, max, 16));
        }
        return shape;
    }

    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean allowsMovement(IBlockState state, IBlockReader world, BlockPos pos, PathType type) { return false; }

    /** Legacy metadata bridge retained for migration tests. */
    public int getMetaFromState(IBlockState state) { return 0; }
}
