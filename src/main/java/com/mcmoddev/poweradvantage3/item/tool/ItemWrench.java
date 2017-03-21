package com.mcmoddev.poweradvantage3.item.tool;

import com.mcmoddev.poweradvantage3.PowerAdvantageTab;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ItemWrench extends Item {
    public ItemWrench() {
        setUnlocalizedName("poweradvantage3.wrench");
        setRegistryName("wrench");
        setCreativeTab(PowerAdvantageTab.TAB);
        GameRegistry.register(this);
    }
}