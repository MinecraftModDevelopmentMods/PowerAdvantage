package com.mcmoddev.poweradvantage.item.tool;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import com.mcmoddev.poweradvantage.init.ModItems;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemPaintbrush extends Item implements IItemColor {
    public ItemPaintbrush() {
        setUnlocalizedName("poweradvantage.paintbrush");
        setRegistryName("paintbrush");
        setCreativeTab(PowerAdvantageTab.TAB);
        setMaxStackSize(1);
        GameRegistry.register(this);
    }

    @Override
    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return EnumActionResult.SUCCESS;
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (stack.hasTagCompound() && tintIndex == 1)
            return EnumDyeColor.byMetadata(stack.getTagCompound().getInteger("color")).getMapColor().colorValue;
        return 0xFFFFFF;
    }

    public static ItemStack getItemWithDye(EnumDyeColor color) {
        ItemStack stack = new ItemStack(ModItems.PAINTBRUSH);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("color", color.getMetadata());
        stack.setTagCompound(tag);
        return stack;
    }


    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        for (EnumDyeColor color : EnumDyeColor.values())
            subItems.add(getItemWithDye(color));
    }
}