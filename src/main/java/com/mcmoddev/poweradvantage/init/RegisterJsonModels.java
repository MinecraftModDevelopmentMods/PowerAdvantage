package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.lib.common.MMDLibRegistry;
import com.mcmoddev.poweradvantage.Material;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RegisterJsonModels {

    public static void registerModels() {
        items();
    }

    public static void registerColor() {
        MMDLibRegistry.registerItemColour(ModItems.CASING, ModItems.CASING);
        MMDLibRegistry.registerItemColour(ModItems.DENSE_PLATE, ModItems.DENSE_PLATE);
        MMDLibRegistry.registerItemColour(ModItems.PLATE, ModItems.PLATE);
        MMDLibRegistry.registerItemColour(ModItems.SMALLDUST, ModItems.SMALLDUST);
    }

    private static void items() {
        register(ModItems.SCREWDRIVER, "tool/screwdriver");
        register(ModItems.WRENCH, "tool/wrench");
        register(ModItems.BATTERY, "battery/battery");
        register(ModItems.BATTERY_MED, "battery/battery_medium");
        register(ModItems.BATTERY_LARGE, "battery/battery_large");
        for (Material material : Material.values())
            register(ModItems.CASING, material.ordinal(), "resource/casing");
        for (Material material : Material.values())
            register(ModItems.DENSE_PLATE, material.ordinal(), "resource/dense_plate");
        for (Material material : Material.values())
            register(ModItems.PLATE, material.ordinal(), "resource/plate");
        for (Material material : Material.values())
            register(ModItems.SMALLDUST, material.ordinal(), "resource/small_dust");
    }

    private static void register(Item item, String name) {
        register(item, 0, name);
    }

    private static void register(Item item, int meta, String name) {
        ModelLoader.setCustomModelResourceLocation(item, meta,
                new ModelResourceLocation("poweradvantage:" + name, "inventory"));
    }
}