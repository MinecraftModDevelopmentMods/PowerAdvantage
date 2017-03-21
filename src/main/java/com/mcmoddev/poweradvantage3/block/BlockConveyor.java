package com.mcmoddev.poweradvantage3.block;

import com.mcmoddev.lib.util.TileHelper;
import com.mcmoddev.poweradvantage3.tile.TileConveyor;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockConveyor extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool SIDE_L = PropertyBool.create("side_l");
    public static final PropertyBool SIDE_R = PropertyBool.create("side_r");

    public BlockConveyor() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(SIDE_L, true)
                .withProperty(SIDE_R, true)
        );
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, SIDE_L, SIDE_R);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        TileConveyor tile = TileHelper.getTileEntity(worldIn, pos, TileConveyor.class);
        if (tile == null)
            return getDefaultState();
        return state
                .withProperty(FACING, tile.getFacing())
                .withProperty(SIDE_L, tile.connectedLeft())
                .withProperty(SIDE_R, tile.connectedRight());
    }
}