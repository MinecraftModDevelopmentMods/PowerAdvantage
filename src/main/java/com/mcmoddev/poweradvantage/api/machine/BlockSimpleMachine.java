package com.mcmoddev.poweradvantage.api.machine;

import java.util.function.Supplier;

import com.mcmoddev.lib.block.MMDBlockWithTile;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.tile.MMDTileEntity;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;

public class BlockSimpleMachine<T extends MMDTileEntity> extends MMDBlockWithTile<T> {
	public static final PropertyDirection FACING = PropertyDirection.create("facing");

	public BlockSimpleMachine(MMDMaterial baseMaterial, Class<T> tileClass, Supplier<T> tileClassCreator, Material materialIn) {
		super(tileClass, tileClassCreator, materialIn);
		blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH);
	}

	@Override
	public BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, FACING);
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		EnumFacing enumfacing = facing.getOpposite();

		if(enumfacing == EnumFacing.DOWN || enumfacing == EnumFacing.UP) {
			enumfacing = placer.getHorizontalFacing().getOpposite();
		}

		return getDefaultState().withProperty(FACING, enumfacing);
	}

	/**
	 * Destroys the TileEntity associated with this block when this block 
	 * breaks.
	 */
	@Override
	public void breakBlock(final World world, final BlockPos coord, final IBlockState bs) {
		final TileEntity tileEntity = world.getTileEntity(coord);
		if (tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
			InventoryHelper.dropInventoryItems(world, coord, (IInventory)tileEntity);
			world.updateComparatorOutputLevel(coord, this);
		}
		super.breakBlock(world, coord, bs);
	}

	@Override
	public int getMetaFromState(IBlockState state)
	{
		return ((EnumFacing)state.getValue(FACING)).getIndex();
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return this.getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta));
	}

	@Override
	public IBlockState withRotation(IBlockState state, Rotation rot) {
		return state.withProperty(FACING, rot.rotate((EnumFacing)state.getValue(FACING)));
	}

	@Override
	public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
		return state.withRotation(mirrorIn.toRotation((EnumFacing)state.getValue(FACING)));
	}
}
