package com.mcmoddev.poweradvantage3;

import com.mcmoddev.poweradvantage3.init.ModBlocks;
import com.mcmoddev.poweradvantage3.init.ModFluids;
import com.mcmoddev.poweradvantage3.init.ModItems;
import com.mcmoddev.poweradvantage3.proxy.CommonProxy;
import com.mcmoddev.poweradvantage3.proxy.GuiHandler;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.UniversalBucket;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = PowerAdvantage.MODID, version = PowerAdvantage.VERSION, name = PowerAdvantage.NAME)
public class PowerAdvantage {

    public static final String MODID = "poweradvantage3";
    public static final String NAME = "Power Advantage 3";
    public static final String VERSION = "3.0.0";

    @Instance
    public static PowerAdvantage INSTANCE;

    @SidedProxy(clientSide = "com.mcmoddev.poweradvantage3.proxy.ClientProxy", serverSide = "com.mcmoddev.poweradvantage3.proxy.ServerProxy")
    public static CommonProxy PROXY;

    static {
        FluidRegistry.enableUniversalBucket();
    }

    @EventHandler
    public void onConstruct(FMLConstructionEvent e) {

    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        ModBlocks.register();
        ModItems.register();
        ModFluids.register();
        PROXY.preInit();
        PowerAdvantageFluidTab.TAB.getTabIconItem();
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, new GuiHandler());
        PROXY.init();
    }

}