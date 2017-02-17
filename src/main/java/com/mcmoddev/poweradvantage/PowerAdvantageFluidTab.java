package com.mcmoddev.poweradvantage;

import com.mcmoddev.poweradvantage.init.ModItems;
import com.mcmoddev.poweradvantage.item.resource.ItemFluidCell;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class PowerAdvantageFluidTab extends CreativeTabs {

    public static PowerAdvantageFluidTab TAB = new PowerAdvantageFluidTab();

    public PowerAdvantageFluidTab() {
        super("poweradvantage.fluid.name");
    }

    @Override
    public Item getTabIconItem() {
        return ModItems.FLUID_CELL;
    }

    @Override
    @SideOnly(Side.CLIENT   )
    public ItemStack getIconItemStack() {
        return ItemFluidCell.getCellWithFluid(FluidRegistry.LAVA);
    }

    @Override
    public void displayAllRelevantItems(List<ItemStack> p_78018_1_) {
        p_78018_1_.add(ItemFluidCell.getEmptyCell(1));
        for(Fluid fluid : FluidRegistry.getRegisteredFluids().values())
            p_78018_1_.add(ItemFluidCell.getCellWithFluid(fluid));
    }
}