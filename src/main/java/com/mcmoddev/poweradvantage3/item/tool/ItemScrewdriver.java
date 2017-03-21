package com.mcmoddev.poweradvantage3.item.tool;

import com.mcmoddev.poweradvantage3.PowerAdvantageTab;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ItemScrewdriver extends Item {
    public ItemScrewdriver() {
        setUnlocalizedName("poweradvantage3.screwdriver");
        setRegistryName("screwdriver");
        setCreativeTab(PowerAdvantageTab.TAB);
        GameRegistry.register(this);
    }
}