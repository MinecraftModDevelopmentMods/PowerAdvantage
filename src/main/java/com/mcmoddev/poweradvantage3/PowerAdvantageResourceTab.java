package com.mcmoddev.poweradvantage3;

import com.mcmoddev.poweradvantage3.init.ModItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class PowerAdvantageResourceTab extends CreativeTabs {

    public static PowerAdvantageResourceTab TAB = new PowerAdvantageResourceTab();

    public PowerAdvantageResourceTab() {
        super("poweradvantage3.resource.name");
        setBackgroundImageName("item_search.png");
    }

    @Override
    public boolean hasSearchBar() {
        return true;
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(ModItems.DENSE_PLATE);
    }
}