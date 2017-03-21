package com.mcmoddev.poweradvantage3.item.tool.paint;

import com.mcmoddev.poweradvantage3.PowerAdvantageTab;
import com.mcmoddev.poweradvantage3.api.IPaintableItem;
import com.mcmoddev.poweradvantage3.init.ModItems;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemPaintcan extends Item implements IItemColor, IPaintableItem {
    public ItemPaintcan() {
        setUnlocalizedName("poweradvantage3.paintcan");
        setRegistryName("paintcan");
        setCreativeTab(PowerAdvantageTab.TAB);
        setMaxStackSize(4);
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