package com.mcmoddev.poweradvantage3;

import com.mcmoddev.poweradvantage3.init.ModItems;
import com.mcmoddev.poweradvantage3.item.resource.ItemFluidCell;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class PowerAdvantageFluidTab extends CreativeTabs {

    public static PowerAdvantageFluidTab TAB = new PowerAdvantageFluidTab();

    public PowerAdvantageFluidTab() {
        super("poweradvantage3.fluid.name");
        setBackgroundImageName("item_search.png");
    }

    @Override
    public boolean hasSearchBar() {
        return true;
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(ModItems.FLUID_CELL);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack getIconItemStack() {
        return ItemFluidCell.getCellWithFluid(FluidRegistry.LAVA);
    }

    @Override
    public void displayAllRelevantItems(NonNullList<ItemStack> p_78018_1_) {
        p_78018_1_.add(ItemFluidCell.getEmptyCell(1));
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values())
            p_78018_1_.add(ItemFluidCell.getCellWithFluid(fluid));
    }
}