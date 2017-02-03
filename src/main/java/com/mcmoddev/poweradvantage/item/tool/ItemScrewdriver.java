package com.mcmoddev.poweradvantage.item.tool;

import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ItemScrewdriver extends Item {
    public ItemScrewdriver() {
        setUnlocalizedName("poweradvantage.screwdriver");
        setRegistryName("screwdriver");
        setCreativeTab(PowerAdvantageTab.TAB);
        GameRegistry.register(this);
    }
}