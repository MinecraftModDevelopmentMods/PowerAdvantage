package com.mcmoddev.poweradvantage3.block;

import com.mcmoddev.poweradvantage3.PowerAdvantageTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class BlockScaffolding extends Block {

    public static final PropertyEnum<Type> TYPE = PropertyEnum.create("type", Type.class);

    public BlockScaffolding() {
        super(Material.WOOD);
        setUnlocalizedName("poweradvantage3.scaffolding");
        setRegistryName("scaffolding");
        setCreativeTab(PowerAdvantageTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(TYPE, Type.WOOD));
        GameRegistry.register(this);
        GameRegistry.register(new ScaffItemBlock(this));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item itemIn, CreativeTabs tab, List<ItemStack> list) {
        for (Type type : Type.values())
            list.add(new ItemStack(itemIn, 1, type.ordinal()));
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn) {
        super.neighborChanged(state, worldIn, pos, blockIn);
    }

    @Override
    public float getBlockHardness(IBlockState blockState, World worldIn, BlockPos pos) {
        return blockState.getValue(TYPE).getResistance();
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        return world.getBlockState(pos).getValue(TYPE).getResistance() / 5.0F;
    }

    @Override
    public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) {
        return (state.getValue(TYPE) == Type.IRON && entity instanceof EntityPlayer) || (state.getValue(TYPE) == Type.WOOD);
    }

    @Override
    public Material getMaterial(IBlockState state) {
        return state.getValue(TYPE).getMaterial();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TYPE, Type.get(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TYPE).ordinal();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TYPE);
    }

    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean isFullCube(IBlockState state) {
        return false;
    }

    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    public enum Type implements IStringSerializable {
        WOOD(Material.WOOD, 2.0F, 4.0F),
        IRON(Material.IRON, 3.0F, 6.0F);
        private final Material material;
        private final float hardness;
        private final float resistance;

        Type(Material material, float hardness, float resistance) {
            this.material = material;
            this.hardness = hardness;
            this.resistance = resistance;
        }

        public static Type get(int i) {
            if (i >= values().length)
                i = 0;
            return values()[i];
        }

        public Material getMaterial() {
            return material;
        }

        public float getHardness() {
            return hardness;
        }

        public float getResistance() {
            return resistance;
        }

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    public class ScaffItemBlock extends ItemBlock {
        public ScaffItemBlock(Block block) {
            super(block);
            setRegistryName(block.getRegistryName());
            setHasSubtypes(true);
        }

        @Override
        public int getMetadata(int damage) {
            return damage;
        }

        @Override
        public int getMetadata(ItemStack stack) {
            return super.getMetadata(stack);
        }

        @Override
        public EnumActionResult onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            IBlockState iblockstate = worldIn.getBlockState(pos);
            Block block = iblockstate.getBlock();
            if (!playerIn.isSneaking() && block == this.block) {
                if (hitY > 0.7) {
                    int heightLeft = 255 - pos.getY();
                    for (int i = 0; i < heightLeft; i++) {
                        IBlockState uState = worldIn.getBlockState(pos.up(i));
                        if (uState.getBlock().isReplaceable(worldIn, pos.up(i))) {
                            worldIn.setBlockState(pos.up(i), this.block.getStateFromMeta(stack.getMetadata()));
                            return EnumActionResult.SUCCESS;
                        } else if (uState.getBlock() != this.block)
                            break;
                    }
                } else if (hitY < 0.3) {
                    int heightLeft = pos.getY();
                    for (int i = 0; i < heightLeft; i++) {
                        IBlockState dState = worldIn.getBlockState(pos.down(i));
                        if (dState.getBlock().isReplaceable(worldIn, pos.down(i))) {
                            worldIn.setBlockState(pos.down(i), this.block.getStateFromMeta(stack.getMetadata()));
                            return EnumActionResult.SUCCESS;
                        } else if (dState.getBlock() != this.block) {
                            int heightLeft2 = 255 - pos.getY();
                            for (int i2 = 0; i2 < heightLeft2; i2++) {
                                IBlockState uState = worldIn.getBlockState(pos.up(i2));
                                if (uState.getBlock().isReplaceable(worldIn, pos.up(i2))) {
                                    worldIn.setBlockState(pos.up(i2), this.block.getStateFromMeta(stack.getMetadata()));
                                    return EnumActionResult.SUCCESS;
                                }
                            }
                        }

                    }
                }
            }
            return super.onItemUse(stack, playerIn, worldIn, pos, hand, facing, hitX, hitY, hitZ);
        }
    }

}