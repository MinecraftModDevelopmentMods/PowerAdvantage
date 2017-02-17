package com.mcmoddev.poweradvantage.block;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class BlockPneumaticController extends Block {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public BlockPneumaticController() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
        setUnlocalizedName("poweradvantage.pneumatic_controller");
        setRegistryName("pneumatic_controller");
        setCreativeTab(PowerAdvantageTab.TAB);
        GameRegistry.register(this);
        GameRegistry.register(new ItemBlock(this).setHasSubtypes(true).setRegistryName(getRegistryName()));
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getFront(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        if (placer.isSneaking())
            return getDefaultState().withProperty(FACING, facing);
        return getDefaultState().withProperty(FACING, facing.getOpposite());
    }
}