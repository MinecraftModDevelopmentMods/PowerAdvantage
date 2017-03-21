package com.mcmoddev.poweradvantage3.api;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;

public interface IPaintableItem {
    EnumDyeColor getColor(ItemStack stack);
}
