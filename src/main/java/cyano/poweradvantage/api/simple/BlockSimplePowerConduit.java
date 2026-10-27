package cyano.poweradvantage.api.simple;

import cyano.poweradvantage.api.ConduitBlock;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PowerConnectorContext;
import zone.moddev.mc.poweradvantage.util.PowerHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
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
        this(Block.Properties.of(material).strength(hardness), pipeRadius, energyTypes);
    }

    protected BlockSimplePowerConduit(Block.Properties properties, float pipeRadius,
            ConduitType... energyTypes) {
        super(properties);
        this.types = energyTypes;
        this.pipeRadius = pipeRadius;
        registerDefaultState(getStateDefinition().any()
                .setValue(WEST, false).setValue(DOWN, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(UP, false).setValue(NORTH, false));
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
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(WEST, DOWN, SOUTH, EAST, UP, NORTH);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return connectedState(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
            IWorld world, BlockPos currentPos, BlockPos facingPos) {
        return state.setValue(property(facing), canConnectTo(world, currentPos, state, facing, facingPos));
    }

    private BlockState connectedState(BlockState state, IBlockReader world, BlockPos pos) {
        return state.setValue(WEST, canConnectTo(world, pos, state, Direction.WEST, pos.west()))
                .setValue(DOWN, canConnectTo(world, pos, state, Direction.DOWN, pos.below()))
                .setValue(SOUTH, canConnectTo(world, pos, state, Direction.SOUTH, pos.south()))
                .setValue(EAST, canConnectTo(world, pos, state, Direction.EAST, pos.east()))
                .setValue(UP, canConnectTo(world, pos, state, Direction.UP, pos.above()))
                .setValue(NORTH, canConnectTo(world, pos, state, Direction.NORTH, pos.north()));
    }

    private static BooleanProperty property(Direction facing) {
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

    protected boolean canConnectTo(IBlockReader world, BlockPos thisBlock, BlockState state,
            Direction face, BlockPos otherBlock) {
        for (ConduitType type : types) {
            if (PowerHelper.areConnectable(new PowerConnectorContext(type, world, state, thisBlock, face))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, net.minecraft.util.math.shapes.ISelectionContext context) {
        double min = 8.0D - pipeRadius * 16.0D;
        double max = 8.0D + pipeRadius * 16.0D;
        VoxelShape shape = Block.box(min, min, min, max, max, max);
        boolean connected = false;
        for (Direction facing : Direction.values()) {
            if (!state.getValue(property(facing))) continue;
            connected = true;
            switch (facing) {
                case DOWN: shape = VoxelShapes.or(shape, Block.box(min, 0, min, max, min, max)); break;
                case UP: shape = VoxelShapes.or(shape, Block.box(min, max, min, max, 16, max)); break;
                case NORTH: shape = VoxelShapes.or(shape, Block.box(min, min, 0, max, max, min)); break;
                case SOUTH: shape = VoxelShapes.or(shape, Block.box(min, min, max, max, max, 16)); break;
                case WEST: shape = VoxelShapes.or(shape, Block.box(0, min, min, min, max, max)); break;
                case EAST: shape = VoxelShapes.or(shape, Block.box(max, min, min, 16, max, max)); break;
            }
        }
        if (!connected) {
            shape = VoxelShapes.or(shape, Block.box(0, min, min, 16, max, max));
            shape = VoxelShapes.or(shape, Block.box(min, min, 0, max, max, 16));
        }
        return shape;
    }

    public boolean isFullCube(BlockState state) { return false; }
    @Override public boolean isPathfindable(BlockState state, IBlockReader world, BlockPos pos, PathType type) { return false; }

    /** Legacy metadata bridge retained for migration tests. */
    public int getMetaFromState(BlockState state) { return 0; }
}
