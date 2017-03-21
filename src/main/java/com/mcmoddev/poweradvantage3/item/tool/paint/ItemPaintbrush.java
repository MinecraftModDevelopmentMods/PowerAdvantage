package com.mcmoddev.poweradvantage3.item.tool.paint;

import com.mcmoddev.poweradvantage3.PowerAdvantageTab;
import com.mcmoddev.poweradvantage3.api.IPaintable;
import com.mcmoddev.poweradvantage3.api.IPaintableItem;
import com.mcmoddev.poweradvantage3.init.ModItems;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemPaintbrush extends Item implements IItemColor, IPaintableItem {
    public ItemPaintbrush() {
        setUnlocalizedName("poweradvantage3.paintbrush");
        setRegistryName("paintbrush");
        setCreativeTab(PowerAdvantageTab.TAB);
        setMaxStackSize(1);
        GameRegistry.register(this);
    }

    public static ItemStack getItemWithDye(EnumDyeColor color) {
        return new ItemStack(ModItems.PAINTBRUSH, 1, color.getMetadata());
    }

    @Override
    public EnumDyeColor getColor(ItemStack stack) {
        return EnumDyeColor.byMetadata(stack.getMetadata());
    }

    @Override
    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        IBlockState state = worldIn.getBlockState(pos);
        if (state.getBlock() instanceof BlockColored) {
            if (state.getValue(BlockColored.COLOR) != getColor(stack)) {
                worldIn.setBlockState(pos, state.withProperty(BlockColored.COLOR, getColor(stack)));
                return EnumActionResult.SUCCESS;
            }
        } else if (state.getBlock() instanceof IPaintable) {
            if (((IPaintable) state.getBlock()).getColor(worldIn, pos) != getColor(stack)) {
                ((IPaintable) state.getBlock()).setColor(getColor(stack), worldIn, pos);
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.FAIL;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return super.getItemStackDisplayName(stack);
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 1)
            return getColor(stack).getMapColor().colorValue;
        return 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        for (EnumDyeColor color : EnumDyeColor.values())
            subItems.add(getItemWithDye(color));
    }
}