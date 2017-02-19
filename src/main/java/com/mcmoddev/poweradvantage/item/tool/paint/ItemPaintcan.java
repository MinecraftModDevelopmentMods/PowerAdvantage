package com.mcmoddev.poweradvantage.item.tool.paint;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import com.mcmoddev.poweradvantage.api.IPaintableItem;
import com.mcmoddev.poweradvantage.init.ModItems;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
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

public class ItemPaintcan extends Item implements IItemColor,IPaintableItem {
    public ItemPaintcan() {
        setUnlocalizedName("poweradvantage.paintcan");
        setRegistryName("paintcan");
        setCreativeTab(PowerAdvantageTab.TAB);
        setMaxStackSize(4);
        GameRegistry.register(this);
    }

    @Override
    public EnumDyeColor getColor(ItemStack stack) {
        return EnumDyeColor.byMetadata(stack.getMetadata());
    }

    public static ItemStack getItemWithDye(EnumDyeColor color) {
        return new ItemStack(ModItems.PAINTBRUSH, 1, color.getMetadata());
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