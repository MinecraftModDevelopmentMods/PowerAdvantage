package com.mcmoddev.poweradvantage3;

import com.mcmoddev.poweradvantage3.init.ModItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class PowerAdvantageTab extends CreativeTabs {

    public static PowerAdvantageTab TAB = new PowerAdvantageTab();

    public PowerAdvantageTab() {
        super("poweradvantage3.name");
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(ModItems.WRENCH);
    }
}