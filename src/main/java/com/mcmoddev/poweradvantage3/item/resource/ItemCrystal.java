package com.mcmoddev.poweradvantage3.item.resource;

import com.mcmoddev.poweradvantage3.PowerAdvantageResourceTab;
import com.mcmoddev.poweradvantage3.misc.Crystal;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemCrystal extends Item implements IItemColor {
    public ItemCrystal() {
        setUnlocalizedName("poweradvantage3.crystal");
        setRegistryName("crystal");
        setCreativeTab(PowerAdvantageResourceTab.TAB);
        setHasSubtypes(true);
        GameRegistry.register(this);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return String.format(super.getItemStackDisplayName(stack), Crystal.getMaterial(stack.getMetadata()).translateServer());
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 0)
            return Crystal.getMaterial(stack.getMetadata()).getRGB();
        return 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, NonNullList<ItemStack> subItems) {
        for (Crystal material : Crystal.values())
            subItems.add(new ItemStack(itemIn, 1, material.ordinal()));
    }
}
