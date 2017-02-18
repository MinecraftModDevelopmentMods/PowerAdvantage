package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.item.ItemDynamite;
import com.mcmoddev.poweradvantage.item.resource.*;
import com.mcmoddev.poweradvantage.item.tool.*;
import net.minecraft.item.Item;

public class ModItems {
    public static ItemCasing CASING;
    public static ItemPlate PLATE;
    public static ItemDensePlate DENSE_PLATE;
    public static ItemSmallDust SMALLDUST;
    public static ItemCrushed CRUSHED;
    public static ItemPurified PURIFIED;
    public static ItemPaintbrush PAINTBRUSH;
    public static ItemCrystal CRYSTAL;
    public static ItemPaintcan PAINTCAN;
    public static Item WRENCH;
    public static Item SCREWDRIVER;
    public static ItemFluidCell FLUID_CELL;
    public static ItemManual MANUAL;
    public static ItemDynamite DYNAMITE;

    public static void register() {
        MANUAL = new ItemManual();
        FLUID_CELL = new ItemFluidCell();
        CASING = new ItemCasing();
        PLATE = new ItemPlate();
        DENSE_PLATE = new ItemDensePlate();
        PAINTBRUSH = new ItemPaintbrush();
        PAINTCAN = new ItemPaintcan();
        SMALLDUST = new ItemSmallDust();
        CRUSHED = new ItemCrushed();
        PURIFIED = new ItemPurified();
        WRENCH = new ItemWrench();
        CRYSTAL = new ItemCrystal();
        SCREWDRIVER = new ItemScrewdriver();
        DYNAMITE = new ItemDynamite();

    }
}