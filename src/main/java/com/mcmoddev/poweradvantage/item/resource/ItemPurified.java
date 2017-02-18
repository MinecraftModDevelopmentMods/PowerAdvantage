package com.mcmoddev.poweradvantage.item.resource;

import com.mcmoddev.poweradvantage.PowerAdvantageResourceTab;
import com.mcmoddev.poweradvantage.misc.Material;
import com.mcmoddev.poweradvantage.misc.MaterialType;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemPurified extends Item implements IItemColor {
    public ItemPurified() {
        setUnlocalizedName("poweradvantage.purified");
        setRegistryName("purified");
        setCreativeTab(PowerAdvantageResourceTab.TAB);
        setHasSubtypes(true);
        GameRegistry.register(this);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return String.format(super.getItemStackDisplayName(stack), Material.getMaterial(stack.getMetadata()).translateServer());
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 0)
            return Material.getMaterial(stack.getMetadata()).getRGB();
        return 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        for (Material material : Material.values())
            if (material.canBe(MaterialType.PURIFIED))
                subItems.add(new ItemStack(itemIn, 1, material.ordinal()));
    }
}
