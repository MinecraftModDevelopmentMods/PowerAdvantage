package com.mcmoddev.poweradvantage.api;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public interface IPaintableItem {
    EnumDyeColor getColor(ItemStack stack);
}
