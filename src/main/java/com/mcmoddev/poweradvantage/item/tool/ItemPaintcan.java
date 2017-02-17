package com.mcmoddev.poweradvantage.item.tool;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import com.mcmoddev.poweradvantage.init.ModItems;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemPaintcan extends Item implements IItemColor {
    public ItemPaintcan() {
        setUnlocalizedName("poweradvantage.paintcan");
        setRegistryName("paintcan");
        setCreativeTab(PowerAdvantageTab.TAB);
        setMaxStackSize(4);
        GameRegistry.register(this);
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (stack.hasTagCompound() && tintIndex == 1)
            return EnumDyeColor.byMetadata(stack.getTagCompound().getInteger("color")).getMapColor().colorValue;
        return 0xFFFFFF;
    }

    public static ItemStack getItemWithDye(EnumDyeColor color) {
        ItemStack stack = new ItemStack(ModItems.PAINTCAN);
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