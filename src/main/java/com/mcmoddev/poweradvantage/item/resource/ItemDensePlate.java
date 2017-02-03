package com.mcmoddev.poweradvantage.item.resource;

import com.mcmoddev.poweradvantage.Material;
import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemDensePlate extends Item implements IItemColor {
    public ItemDensePlate() {
        setUnlocalizedName("poweradvantage.denseplate");
        setRegistryName("denseplate");
        setCreativeTab(PowerAdvantageTab.TAB);
        setHasSubtypes(true);
        GameRegistry.register(this);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Material.getMaterial(stack.getMetadata()).translateServer() + " " + super.getItemStackDisplayName(stack);
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 0)
            return Material.getMaterial(stack.getMetadata()).getRGB();
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        for (Material material : Material.values())
            subItems.add(new ItemStack(itemIn, 1, material.ordinal()));
    }
}
