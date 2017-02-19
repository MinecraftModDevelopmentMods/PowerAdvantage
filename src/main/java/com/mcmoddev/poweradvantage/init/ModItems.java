package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.item.ItemDynamite;
import com.mcmoddev.poweradvantage.item.resource.*;
import com.mcmoddev.poweradvantage.item.tool.*;
import com.mcmoddev.poweradvantage.item.tool.gem.*;
import com.mcmoddev.poweradvantage.item.tool.paint.ItemPaintbrush;
import com.mcmoddev.poweradvantage.item.tool.paint.ItemPaintcan;
import com.mcmoddev.poweradvantage.misc.Crystal;
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

    public static GemAxe RUBY_AXE;
    public static GemAxe SAPPHIRE_AXE;

    public static GemPickaxe RUBY_PICKAXE;
    public static GemPickaxe SAPPHIRE_PICKAXE;

    public static GemSword RUBY_SWORD;
    public static GemSword SAPPHIRE_SWORD;

    public static GemHoe RUBY_HOE;
    public static GemHoe SAPPHIRE_HOE;

    public static GemShovel RUBY_SHOVEL;
    public static GemShovel SAPPHIRE_SHOVEL;

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

        RUBY_AXE = new GemAxe(Crystal.RUBY);
        SAPPHIRE_AXE = new GemAxe(Crystal.SAPPHIRE);

        RUBY_SHOVEL = new GemShovel(Crystal.RUBY);
        SAPPHIRE_SHOVEL = new GemShovel(Crystal.SAPPHIRE);

        RUBY_PICKAXE = new GemPickaxe(Crystal.RUBY);
        SAPPHIRE_PICKAXE = new GemPickaxe(Crystal.SAPPHIRE);

        RUBY_SWORD = new GemSword(Crystal.RUBY);
        SAPPHIRE_SWORD = new GemSword(Crystal.SAPPHIRE);

        RUBY_HOE = new GemHoe(Crystal.RUBY);
        SAPPHIRE_HOE = new GemHoe(Crystal.SAPPHIRE);
    }
}