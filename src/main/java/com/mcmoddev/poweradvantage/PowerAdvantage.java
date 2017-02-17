package com.mcmoddev.poweradvantage;

import com.mcmoddev.poweradvantage.init.ModBlocks;
import com.mcmoddev.poweradvantage.init.ModItems;
import com.mcmoddev.poweradvantage.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = PowerAdvantage.MODID, version = PowerAdvantage.VERSION, name = PowerAdvantage.NAME)
public class PowerAdvantage {

    public static final String MODID = "poweradvantage";
    public static final String NAME = "Power Advantage 3";
    public static final String VERSION = "3.0.0";

    @Instance
    public static PowerAdvantage INSTANCE;

    @SidedProxy(clientSide = "com.mcmoddev.poweradvantage.proxy.ClientProxy")
    public static CommonProxy PROXY;

    @EventHandler
    public void onConstruct(FMLConstructionEvent e) {

    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        ModBlocks.register();
        ModItems.register();
        PROXY.preInit();
        PowerAdvantageFluidTab.TAB.getTabIconItem();
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        PROXY.init();
    }

}