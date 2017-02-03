package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.item.energy.ItemBattery;
import com.mcmoddev.poweradvantage.item.energy.ItemBatteryLarge;
import com.mcmoddev.poweradvantage.item.energy.ItemBatteryMed;
import com.mcmoddev.poweradvantage.item.resource.ItemCasing;
import com.mcmoddev.poweradvantage.item.resource.ItemDensePlate;
import com.mcmoddev.poweradvantage.item.resource.ItemPlate;
import com.mcmoddev.poweradvantage.item.resource.ItemSmallDust;
import com.mcmoddev.poweradvantage.item.tool.ItemScrewdriver;
import com.mcmoddev.poweradvantage.item.tool.ItemWrench;
import net.minecraft.item.Item;

public class ModItems {
    public static ItemCasing CASING;
    public static ItemPlate PLATE;
    public static ItemDensePlate DENSE_PLATE;
    public static ItemSmallDust SMALLDUST;
    public static Item WRENCH;
    public static Item SCREWDRIVER;
    public static Item BATTERY;
    public static Item BATTERY_MED;
    public static Item BATTERY_LARGE;

    public static void register() {
        CASING = new ItemCasing();
        PLATE = new ItemPlate();
        DENSE_PLATE = new ItemDensePlate();
        SMALLDUST = new ItemSmallDust();
        WRENCH = new ItemWrench();
        SCREWDRIVER = new ItemScrewdriver();
        BATTERY = new ItemBattery();
        BATTERY_MED = new ItemBatteryMed();
        BATTERY_LARGE = new ItemBatteryLarge();
    }
}