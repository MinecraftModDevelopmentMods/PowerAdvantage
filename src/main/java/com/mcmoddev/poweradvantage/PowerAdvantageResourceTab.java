package com.mcmoddev.poweradvantage;

import com.mcmoddev.poweradvantage.init.ModItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public class PowerAdvantageResourceTab extends CreativeTabs {

    public static PowerAdvantageResourceTab TAB = new PowerAdvantageResourceTab();

    public PowerAdvantageResourceTab() {
        super("poweradvantage.resource.name");
        setBackgroundImageName("item_search.png");
    }

    @Override
    public boolean hasSearchBar() {
        return true;
    }

    @Override
    public Item getTabIconItem() {
        return ModItems.DENSE_PLATE;
    }
}