package com.mcmoddev.poweradvantage;

import com.mcmoddev.poweradvantage.init.ModItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class PowerAdvantageTab extends CreativeTabs {

    public static PowerAdvantageTab TAB = new PowerAdvantageTab();

    public PowerAdvantageTab() {
        super("poweradvantage.name");
    }

    @Override
    public Item getTabIconItem() {
        return ModItems.WRENCH;
    }
}