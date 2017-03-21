package com.mcmoddev.poweradvantage3.api;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public interface IPaintable {
    EnumDyeColor getColor(IBlockAccess world, BlockPos pos);

    void setColor(EnumDyeColor color, IBlockAccess world, BlockPos pos);
}
