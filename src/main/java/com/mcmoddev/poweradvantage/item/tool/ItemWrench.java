package com.mcmoddev.poweradvantage.item.tool;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ItemWrench extends Item {
    public ItemWrench() {
        setUnlocalizedName("poweradvantage.wrench");
        setRegistryName("wrench");
        setCreativeTab(PowerAdvantageTab.TAB);
        GameRegistry.register(this);
    }
}